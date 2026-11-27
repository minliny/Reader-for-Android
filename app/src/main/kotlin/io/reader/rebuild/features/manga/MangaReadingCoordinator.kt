package io.reader.rebuild.features.manga

import com.reader.core.protocol.M2BookTocParams
import com.reader.core.protocol.M3MangaChapterGetParams
import com.reader.core.protocol.M3MangaChapterWindow
import com.reader.core.protocol.M3MangaEntryGetParams
import com.reader.core.protocol.M3MangaLocation
import com.reader.core.protocol.M3MangaManifestPage
import com.reader.core.protocol.M3MangaPagesWindowParams
import com.reader.core.protocol.M3MangaPreparedEntry
import com.reader.core.protocol.M3MangaProgressGetParams
import com.reader.core.protocol.M3MangaProgressState
import com.reader.core.protocol.M3MangaProgressUpdateParams
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.ReaderM3
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.host.network.MangaTransferStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The prepared Core entry owns chapter identity and the compare-and-set token. */
internal class MangaReadingCoordinator(
    private val core: CoreRuntimeActor,
    transfers: MangaTransferStore,
) {
    private val pages = MangaPageAssets(core, transfers)
    private val lock = Mutex()
    private var generation = 0L
    private val projection = MutableStateFlow<M3MangaPreparedEntry?>(null)
    val entry = projection.asStateFlow()

    suspend fun open(sourceId: String, bookId: String, chapterIndex: Long? = null): M3MangaPreparedEntry = lock.withLock {
        require(sourceId.isNotEmpty() && bookId.isNotEmpty() && (chapterIndex == null || chapterIndex >= 0))
        retireLocked()
        val request = M3MangaEntryGetParams(sourceId = sourceId, bookId = bookId, chapterIndex = chapterIndex)
        val direct = core.call(ReaderM3.mangaEntryGet, request).entry
        val current = direct ?: run {
            val location = if (chapterIndex == null) {
                core.call(ReaderM3.readingProgressGetManga,
                    M3MangaProgressGetParams(sourceId = sourceId, bookId = bookId)).location
            } else null
            val index = chapterIndex ?: if (location == null) 0 else
                core.call(ReaderM2.bookToc, M2BookTocParams(sourceId = sourceId, bookId = bookId)).toc
                    .single { it.url == location.chapter.chapterId }.index
            core.call(ReaderM3.mangaChapterGet, M3MangaChapterGetParams(
                sourceId = sourceId, bookId = bookId, chapterIndex = index,
            ))
            checkNotNull(core.call(ReaderM3.mangaEntryGet, request.copy(chapterIndex = index)).entry)
        }
        val chapter = current.chapter
        check(chapter.manifest.chapter.sourceId == sourceId && chapter.manifest.chapter.bookId == bookId)
        check(chapterIndex == null || chapter.chapterIndex == chapterIndex)
        check(current.targetOrdinal in 0 until chapter.totalPages)
        projection.value = current
        current
    }

    suspend fun readWindow(start: Long, limit: Long): M3MangaChapterWindow = lock.withLock {
        val chapter = checkNotNull(projection.value).chapter
        require(start in 0 until chapter.totalPages && limit > 0)
        val identity = chapter.manifest.chapter
        val window = core.call(ReaderM3.mangaPagesWindow, M3MangaPagesWindowParams(
            identity.sourceId, identity.bookId, identity.chapterId, chapter.manifest.manifestVersion, start, limit,
        ))
        check(trustedWindow(chapter, window) && window.pageStart == start)
        check(window.manifest.pages.withIndex().all { (index, page) -> page.ordinal == start + index })
        window
    }

    suspend fun page(window: M3MangaChapterWindow, page: M3MangaManifestPage, width: Int): MangaPageFrame {
        val owner = lock.withLock {
            val current = checkNotNull(projection.value)
            check(trustedPage(current.chapter, window, page))
            generation
        }
        val frame = pages.decode(owner, window.manifest, page, width)
        return try {
            lock.withLock {
                if (generation != owner) throw CancellationException("Manga page owner retired")
                frame
            }
        } catch (failure: CancellationException) {
            frame.close()
            throw failure
        }
    }

    suspend fun save(window: M3MangaChapterWindow, page: M3MangaManifestPage, x: Double, y: Double):
        M3MangaProgressState = lock.withLock {
        val current = checkNotNull(projection.value)
        require(x in 0.0..1.0 && y in 0.0..1.0)
        check(!current.recoveryRequired && trustedPage(current.chapter, window, page))
        val token = current.progress.token
        check(token.revision < Long.MAX_VALUE)
        val location = M3MangaLocation(chapter = window.manifest.chapter,
            manifestVersion = window.manifest.manifestVersion, pageId = page.pageId,
            pageOrdinalFallback = page.ordinal, x = x, y = y, progressRevision = token.revision + 1)
        val stored = core.call(ReaderM3.readingProgressUpdateManga, M3MangaProgressUpdateParams(
            location = location, expectedEpoch = token.epoch, expectedRevision = token.revision,
        ))
        check(stored.location == location && stored.token.epoch == token.epoch &&
            stored.token.revision == location.progressRevision)
        projection.value = current.copy(progress = stored, targetOrdinal = page.ordinal)
        stored
    }

    suspend fun retire() = lock.withLock { retireLocked() }

    private suspend fun retireLocked() {
        generation++
        projection.value = null
        pages.reset(generation)
    }

    private fun trustedWindow(current: M3MangaChapterWindow, window: M3MangaChapterWindow): Boolean =
        current.manifest.chapter == window.manifest.chapter &&
            current.manifest.manifestVersion == window.manifest.manifestVersion &&
            current.manifest.sourceRuleVersion == window.manifest.sourceRuleVersion &&
            current.manifest.decodeRevision == window.manifest.decodeRevision &&
            current.totalPages == window.totalPages

    private fun trustedPage(current: M3MangaChapterWindow, window: M3MangaChapterWindow, page: M3MangaManifestPage): Boolean =
        trustedWindow(current, window) && page in window.manifest.pages &&
            page.ordinal in window.pageStart until (window.pageStart + window.manifest.pages.size)
}
