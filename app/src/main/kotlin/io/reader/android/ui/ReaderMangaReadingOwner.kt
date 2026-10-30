package io.reader.android.ui

import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderMangaChapterData
import io.reader.android.runtime.ReaderMangaChapterIdentity
import io.reader.android.runtime.ReaderMangaChapterRequest
import io.reader.android.runtime.ReaderMangaDecodedResource
import io.reader.android.runtime.ReaderMangaPageWindow
import io.reader.android.runtime.ReaderMangaPageWindowRequest
import io.reader.android.runtime.ReaderMangaResource
import io.reader.android.runtime.ReaderMangaResourcePrepareRequest
import io.reader.android.runtime.ReaderMangaTransferIdentity
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

/** Identity of one window acquisition; layout callbacks can bind to the published ticket. */
internal data class ReaderMangaWindowTicket(
    val generation: Long,
    val revision: Long,
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val sourceRuleVersion: String,
    val decodeRevision: String,
    val start: Int,
    val limit: Int,
)

internal data class ReaderMangaReadingUiState(
    val generation: Long = 0,
    val phase: ReaderMangaReadingPhase = ReaderMangaReadingPhase.IDLE,
    val chapter: ReaderMangaChapterData? = null,
    val window: ReaderMangaPageWindow? = null,
    val pages: List<ReaderMangaReadingPage> = emptyList(),
    /** Remains attached to the visible pages until a new window is atomically published. */
    val visibleWindow: ReaderMangaWindowTicket? = null,
    val pendingWindow: ReaderMangaWindowTicket? = null,
    val windowError: String? = null,
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
    private var nextWindowRevision = 0L
    private var closed = false
    private var activeGeneration: Long? = null
    private var activeChapter: ReaderMangaChapterData? = null
    private var activePages: List<ReaderMangaReadingPage> = emptyList()
    private var inFlightWindow: ReaderMangaWindowTicket? = null

    fun open(request: ReaderMangaChapterRequest) {
        if (closed) return
        val generation = ++nextGeneration
        retirePages()
        activeGeneration = generation
        activeChapter = null
        inFlightWindow = null
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
        if (!current(generation) || activeChapter !== chapter) return
        val manifest = chapter.manifest
        val pending = inFlightWindow
        if (pending != null && current(pending) && pending.start == start && pending.limit == limit) return
        check(nextWindowRevision < Long.MAX_VALUE) { "MANGA_UI_WINDOW_REVISION_EXHAUSTED" }
        val ticket = ReaderMangaWindowTicket(generation, ++nextWindowRevision, manifest.chapter,
            manifest.manifestVersion, manifest.sourceRuleVersion, manifest.decodeRevision, start, limit)
        inFlightWindow = ticket
        mutable.value = mutable.value.copy(
            phase = if (activePages.isEmpty()) ReaderMangaReadingPhase.LOADING else ReaderMangaReadingPhase.READY,
            pendingWindow = ticket, windowError = null, error = null,
        )
        scope.launch {
            val acquired = ArrayList<ReaderMangaReadingPage>()
            try {
                if (!current(ticket)) return@launch
                val window = library().pagesWindow(ReaderMangaPageWindowRequest(
                    chapter = ticket.chapter,
                    manifestVersion = ticket.manifestVersion,
                    start = start,
                    limit = limit,
                ))
                if (!current(ticket)) return@launch
                check(window.manifest.chapter == ticket.chapter &&
                    window.manifest.manifestVersion == ticket.manifestVersion &&
                    window.manifest.sourceRuleVersion == ticket.sourceRuleVersion &&
                    window.manifest.decodeRevision == ticket.decodeRevision &&
                    window.pageStart == start && window.resources.size <= limit) {
                    "MANGA_UI_WINDOW_SCOPE"
                }
                for ((index, resource) in window.resources.withIndex()) {
                    if (!current(ticket)) return@launch
                    val ordinal = window.pageStart + index
                    val request = ReaderMangaResourcePrepareRequest(
                        chapter = ticket.chapter,
                        manifestVersion = ticket.manifestVersion,
                        resourceRef = resource.resourceRef,
                        sourceRuleVersion = ticket.sourceRuleVersion,
                        transferId = "android-manga-ui:${generation}:${ticket.revision}:${ordinal}:${resource.resourceRef}",
                    )
                    val decoded = library().decodedResource(request)
                    val page = ReaderMangaReadingPage(ordinal, resource, decoded)
                    acquired += page // Even a late/foreign handle belongs to this request's finally block.
                    if (!current(ticket)) return@launch
                    check(decoded.chapter == ticket.chapter &&
                        decoded.manifestVersion == ticket.manifestVersion &&
                        decoded.resourceRef == resource.resourceRef &&
                        decoded.transferId == ReaderMangaTransferIdentity.forPrepare(request).wireId) { "MANGA_UI_RESOURCE_SCOPE" }
                }
                if (!current(ticket)) return@launch
                val pages = acquired.toList()
                val previousPages = activePages
                activePages = pages
                inFlightWindow = null
                mutable.value = mutable.value.copy(
                    phase = ReaderMangaReadingPhase.READY,
                    chapter = chapter,
                    window = window,
                    pages = pages,
                    visibleWindow = ticket,
                    pendingWindow = null,
                    currentOrdinal = state.value.currentOrdinal.takeIf { ordinal -> pages.any { it.ordinal == ordinal } }
                        ?: pages.firstOrNull()?.ordinal ?: start,
                    windowError = null, error = null,
                )
                acquired.clear() // The published owner now holds these leases.
                previousPages.forEach { it.decoded.close() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failWindow(ticket, error)
            } finally {
                acquired.forEach { it.decoded.close() }
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

    /** Retire the visible chapter while keeping this ViewModel-owned owner reusable. */
    fun retire() {
        if (closed) return
        ++nextGeneration
        activeGeneration = null
        activeChapter = null
        inFlightWindow = null
        retirePages()
        mutable.value = ReaderMangaReadingUiState(generation = nextGeneration)
    }

    override fun close() {
        if (closed) return
        retire()
        closed = true
        mutable.value = mutable.value.copy(generation = nextGeneration, phase = ReaderMangaReadingPhase.CLOSED,
            chapter = null, window = null, pages = emptyList(), error = null)
    }

    private fun current(generation: Long): Boolean = !closed && activeGeneration == generation

    private fun current(ticket: ReaderMangaWindowTicket): Boolean {
        val manifest = activeChapter?.manifest ?: return false
        return current(ticket.generation) && inFlightWindow == ticket &&
            manifest.chapter == ticket.chapter && manifest.manifestVersion == ticket.manifestVersion &&
            manifest.sourceRuleVersion == ticket.sourceRuleVersion && manifest.decodeRevision == ticket.decodeRevision
    }

    private fun failWindow(ticket: ReaderMangaWindowTicket, error: Throwable) {
        if (!current(ticket)) return
        inFlightWindow = null
        val message = error.message ?: error::class.java.simpleName
        val hasVisiblePages = activePages.isNotEmpty()
        mutable.value = mutable.value.copy(
            phase = if (hasVisiblePages) ReaderMangaReadingPhase.READY else ReaderMangaReadingPhase.ERROR,
            pendingWindow = null, windowError = message, error = if (hasVisiblePages) null else message,
        )
    }

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
