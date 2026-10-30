package io.reader.android.ui

import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderMangaChapterData
import io.reader.android.runtime.ReaderMangaChapterRequest
import io.reader.android.runtime.ReaderMangaDecodedResource
import io.reader.android.runtime.ReaderMangaPageWindow
import io.reader.android.runtime.ReaderMangaPageWindowRequest
import io.reader.android.runtime.ReaderMangaResource
import io.reader.android.runtime.ReaderMangaResourcePrepareRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The narrow Library surface a Manga reading owner is allowed to use. */
internal interface ReaderMangaLibrary {
    suspend fun chapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData
    suspend fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow
    suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest): ReaderMangaDecodedResource
}

internal class ReaderMangaLibraryAdapter(private val library: ReaderLibrary) : ReaderMangaLibrary {
    override suspend fun chapter(request: ReaderMangaChapterRequest) = library.mangaChapter(request)
    override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest) = library.mangaPagesWindow(request)
    override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest) = library.mangaDecodedResource(request)
}

internal enum class ReaderMangaReadingPhase { IDLE, LOADING, READY, ERROR, CLOSED }

internal data class ReaderMangaReadingPage(
    val ordinal: Int,
    val resource: ReaderMangaResource,
    val decoded: ReaderMangaDecodedResource,
)

internal data class ReaderMangaReadingUiState(
    val generation: Long = 0,
    val phase: ReaderMangaReadingPhase = ReaderMangaReadingPhase.IDLE,
    val chapter: ReaderMangaChapterData? = null,
    val window: ReaderMangaPageWindow? = null,
    val pages: List<ReaderMangaReadingPage> = emptyList(),
    val currentOrdinal: Int = 0,
    val zoom: Float = 1f,
    val scrollFraction: Float = 0f,
    val error: String? = null,
)

/**
 * Owns Manga page/window lifetimes for a future Compose surface. It never
 * draws bytes or writes progress; it only binds Core descriptors to decoded
 * handles and retires old generations before publishing a new one.
 */
internal class ReaderMangaReadingOwner(
    private val scope: CoroutineScope,
    private val library: () -> ReaderMangaLibrary,
) : AutoCloseable {
    private val mutable = MutableStateFlow(ReaderMangaReadingUiState())
    val state = mutable.asStateFlow()
    private var nextGeneration = 0L
    private var closed = false
    private var activeGeneration: Long? = null
    private var activeChapter: ReaderMangaChapterData? = null
    private var activePages: List<ReaderMangaReadingPage> = emptyList()

    fun open(request: ReaderMangaChapterRequest) {
        if (closed) return
        val generation = ++nextGeneration
        retirePages()
        activeGeneration = generation
        activeChapter = null
        mutable.value = ReaderMangaReadingUiState(generation = generation, phase = ReaderMangaReadingPhase.LOADING)
        scope.launch {
            try {
                val chapter = library().chapter(request)
                if (!current(generation)) return@launch
                activeChapter = chapter
                mutable.value = mutable.value.copy(chapter = chapter)
                loadWindow(generation, chapter, 0, DEFAULT_WINDOW_LIMIT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                fail(generation, error)
            }
        }
    }

    fun loadWindow(start: Int, limit: Int = DEFAULT_WINDOW_LIMIT) {
        val generation = activeGeneration ?: return
        val chapter = activeChapter ?: return
        loadWindow(generation, chapter, start, limit)
    }

    private fun loadWindow(generation: Long, chapter: ReaderMangaChapterData, start: Int, limit: Int) {
        require(start >= 0 && limit in 1..32) { "MANGA_UI_WINDOW_RANGE" }
        scope.launch {
            var acquired = emptyList<ReaderMangaReadingPage>()
            try {
                if (!current(generation)) return@launch
                mutable.value = mutable.value.copy(phase = ReaderMangaReadingPhase.LOADING, error = null)
                val window = library().pagesWindow(ReaderMangaPageWindowRequest(
                    chapter = chapter.manifest.chapter,
                    manifestVersion = chapter.manifest.manifestVersion,
                    start = start,
                    limit = limit,
                ))
                check(window.manifest.chapter == chapter.manifest.chapter &&
                    window.manifest.manifestVersion == chapter.manifest.manifestVersion &&
                    window.manifest.sourceRuleVersion == chapter.manifest.sourceRuleVersion) {
                    "MANGA_UI_WINDOW_SCOPE"
                }
                val pages = ArrayList<ReaderMangaReadingPage>(window.resources.size)
                window.resources.forEachIndexed { index, resource ->
                    val ordinal = window.pageStart + index
                    val request = ReaderMangaResourcePrepareRequest(
                        chapter = window.manifest.chapter,
                        manifestVersion = window.manifest.manifestVersion,
                        resourceRef = resource.resourceRef,
                        sourceRuleVersion = window.manifest.sourceRuleVersion,
                        transferId = "android-manga-ui:${generation}:${ordinal}:${resource.resourceRef}",
                    )
                    val decoded = library().decodedResource(request)
                    val page = ReaderMangaReadingPage(ordinal, resource, decoded)
                    pages += page
                    acquired = pages.toList()
                    check(decoded.chapter == window.manifest.chapter &&
                        decoded.manifestVersion == window.manifest.manifestVersion &&
                        decoded.resourceRef == resource.resourceRef) { "MANGA_UI_RESOURCE_SCOPE" }
                }
                if (!current(generation)) {
                    pages.forEach { it.decoded.close() }
                    return@launch
                }
                acquired = pages
                if (!current(generation)) return@launch
                retirePages()
                activePages = pages
                mutable.value = mutable.value.copy(
                    phase = ReaderMangaReadingPhase.READY,
                    chapter = chapter,
                    window = window,
                    pages = pages,
                    currentOrdinal = pages.firstOrNull()?.ordinal ?: start,
                    error = null,
                )
                acquired = emptyList()
            } catch (cancelled: CancellationException) {
                acquired.forEach { it.decoded.close() }
                throw cancelled
            } catch (error: Throwable) {
                acquired.forEach { it.decoded.close() }
                fail(generation, error)
            }
        }
    }

    fun setCurrentOrdinal(ordinal: Int) {
        if (state.value.phase != ReaderMangaReadingPhase.READY || activePages.none { it.ordinal == ordinal }) return
        mutable.value = mutable.value.copy(currentOrdinal = ordinal)
    }

    fun setZoom(value: Float) {
        if (state.value.phase == ReaderMangaReadingPhase.CLOSED) return
        mutable.value = mutable.value.copy(zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM))
    }

    fun setScrollFraction(value: Float) {
        if (state.value.phase != ReaderMangaReadingPhase.READY) return
        mutable.value = mutable.value.copy(scrollFraction = value.coerceIn(0f, 1f))
    }

    override fun close() {
        if (closed) return
        closed = true
        ++nextGeneration
        activeGeneration = null
        activeChapter = null
        retirePages()
        mutable.value = mutable.value.copy(generation = nextGeneration, phase = ReaderMangaReadingPhase.CLOSED,
            chapter = null, window = null, pages = emptyList(), error = null)
    }

    private fun current(generation: Long): Boolean = !closed && activeGeneration == generation

    private fun fail(generation: Long, error: Throwable) {
        if (!current(generation)) return
        retirePages()
        mutable.value = mutable.value.copy(phase = ReaderMangaReadingPhase.ERROR, pages = emptyList(), error = error.message ?: error::class.java.simpleName)
    }

    private fun retirePages() {
        activePages.forEach { it.decoded.close() }
        activePages = emptyList()
    }

    companion object {
        const val DEFAULT_WINDOW_LIMIT = 8
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 3f
    }
}
