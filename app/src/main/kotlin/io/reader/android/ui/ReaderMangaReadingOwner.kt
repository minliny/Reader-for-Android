package io.reader.android.ui

import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderMangaChapterRef
import io.reader.android.runtime.ReaderMangaLocation
import io.reader.android.runtime.ReaderMangaProgressPending
import io.reader.android.runtime.ReaderMangaProgressRejected
import io.reader.android.runtime.ReaderMangaProgressSaveRequest
import io.reader.android.runtime.ReaderMangaProgressState
import io.reader.android.runtime.ReaderMangaProgressToken
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The narrow Library surface a Manga reading owner is allowed to use. */
internal interface ReaderMangaLibrary {
    suspend fun progress(owner: ReaderMangaChapterRef): ReaderMangaProgressState
    suspend fun saveProgress(request: ReaderMangaProgressSaveRequest): ReaderMangaProgressState
    suspend fun pendingProgress(): List<ReaderMangaProgressPending>
    suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String): ReaderMangaProgressState
    suspend fun chapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData
    suspend fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow
    suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest): ReaderMangaDecodedResource
}

internal class ReaderMangaLibraryAdapter(private val library: ReaderLibrary) : ReaderMangaLibrary {
    override suspend fun progress(owner: ReaderMangaChapterRef) = library.mangaProgress(owner)
    override suspend fun saveProgress(request: ReaderMangaProgressSaveRequest) = library.saveMangaProgress(request)
    override suspend fun pendingProgress() = library.pendingMangaProgress()
    override suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String) = library.reconcileMangaProgress(owner, operationId)
    override suspend fun chapter(request: ReaderMangaChapterRequest) = library.mangaChapter(request)
    override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest) = library.mangaPagesWindow(request)
    override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest) = library.mangaDecodedResource(request)
}

internal enum class ReaderMangaFlushResult { NO_CHANGE, CONFIRMED, BLOCKED }

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
    val failedWindow: ReaderMangaWindowTicket? = null,
    val currentOrdinal: Int = 0,
    val zoom: Float = 1f,
    val layoutTicket: ReaderMangaLayoutTicket? = null,
    val restoreRequest: ReaderMangaRestoreRequest? = null,
    val visibleAnchor: ReaderMangaAnchor? = null,
    val progressToken: ReaderMangaProgressToken? = null,
    /** Only an actual save/reconcile original ACK advances this field; get is an opening observation. */
    val lastConfirmedProgress: ReaderMangaLocation? = null,
    val progressDirty: Boolean = false,
    val progressBusy: Boolean = false,
    val progressPending: List<ReaderMangaProgressPending> = emptyList(),
    val progressError: String? = null,
    val knownRejection: ReaderMangaProgressRejected? = null,
    val restoreBlocked: String? = null,
    val exitPending: Boolean = false,
    val error: String? = null,
)

/** Owns decoded windows, measured image locations and serialized original progress writes.
 * Layout/restore targets are never themselves saveable; only accepted drawn idle measurements are.
 */
internal class ReaderMangaReadingOwner(
    private val scope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit = {},
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
    private val progressMutex = Mutex()
    private var activeOwner: ReaderMangaChapterRef? = null
    private var nextLayoutEpoch = 0L
    private var nextRestoreId = 0L
    private var lastStableAnchor: ReaderMangaAnchor? = null
    /** Kept separately from reflow baselines: a zoom/resize cannot invent or erase a genuine user position. */
    private var dirtyAnchor: ReaderMangaAnchor? = null
    private var restoreTarget: ReaderMangaAnchor? = null
    private var writeBlocked = false
    private var rejectionGeneration: Long? = null

    fun open(request: ReaderMangaChapterRequest) {
        if (closed) return
        if (mutable.value.progressBusy || dirtyAnchor != null) {
            mutable.value = mutable.value.copy(progressError = "MANGA_PROGRESS_EXIT_REQUIRED")
            return
        }
        val generation = ++nextGeneration
        retirePages()
        activeGeneration = generation
        activeOwner = request.chapter
        activeChapter = null
        inFlightWindow = null
        lastStableAnchor = null; dirtyAnchor = null; restoreTarget = null; writeBlocked = false; rejectionGeneration = null
        mutable.value = ReaderMangaReadingUiState(generation = generation, phase = ReaderMangaReadingPhase.LOADING)
        scope.launch {
            try {
                // A prior save owns the mutex until its exact result is consumed; get never settles that write.
                val progress = progressMutex.withLock {
                    if (!current(generation)) return@launch
                    val pending = library().pendingProgress()
                    if (!current(generation)) return@launch
                    mutable.value = mutable.value.copy(progressPending = pending)
                    val observed = library().progress(request.chapter)
                    if (!current(generation)) return@launch
                    mutable.value = mutable.value.copy(progressToken = observed.token)
                    check(pending.isEmpty()) { "MANGA_PROGRESS_ORIGINAL_PENDING" }
                    observed
                }
                val chapter = library().chapter(request)
                if (!current(generation)) return@launch
                check(chapter.manifest.chapter.sourceId == request.chapter.sourceId &&
                    chapter.manifest.chapter.bookId == request.chapter.bookId && chapter.chapterIndex == request.chapterIndex &&
                    (request.expectedManifestVersion == null || request.expectedManifestVersion == chapter.manifest.manifestVersion)) {
                    "MANGA_UI_CHAPTER_SCOPE"
                }
                activeChapter = chapter
                mutable.value = mutable.value.copy(chapter = chapter)
                val saved = progress.location
                if (saved != null) {
                    val page = chapter.manifest.pages.singleOrNull { it.pageId == saved.pageId && it.ordinal == saved.pageOrdinalFallback }
                    if (saved.chapter != chapter.manifest.chapter || saved.manifestVersion != chapter.manifest.manifestVersion || page == null) {
                        mutable.value = mutable.value.copy(phase = ReaderMangaReadingPhase.ERROR,
                            restoreBlocked = "MANGA_SAVED_POSITION_REQUIRES_EXACT_CHAPTER_MANIFEST", error = "MANGA_SAVED_POSITION_REQUIRES_EXACT_CHAPTER_MANIFEST")
                        return@launch
                    }
                    restoreTarget = ReaderMangaAnchor(page.pageId, page.ordinal, page.resourceRef, saved.x, saved.y)
                } else {
                    val page = chapter.manifest.pages.firstOrNull() ?: error("MANGA_UI_EMPTY_MANIFEST")
                    restoreTarget = ReaderMangaAnchor(page.pageId, page.ordinal, page.resourceRef, 0.0, 0.0)
                }
                val start = (checkNotNull(restoreTarget).ordinal - 1).coerceAtLeast(0)
                loadWindow(generation, chapter, start, DEFAULT_WINDOW_LIMIT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (current(generation)) onFailure(error)
                fail(generation, error)
            }
        }
    }

    fun loadWindow(start: Int, limit: Int = DEFAULT_WINDOW_LIMIT) {
        val generation = activeGeneration ?: return
        val chapter = activeChapter ?: return
        if (mutable.value.exitPending || mutable.value.restoreBlocked != null) return
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
            pendingWindow = ticket, failedWindow = null, windowError = null, error = null,
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
                    window.pageStart == start && window.resources.size <= limit && window.resources.isNotEmpty() &&
                    window.resources.withIndex().all { (index, resource) ->
                        window.manifest.pages.singleOrNull { it.ordinal == start + index }?.resourceRef == resource.resourceRef
                    }) {
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
                val target = (lastStableAnchor ?: restoreTarget)?.takeIf { anchor -> pages.any { it.ordinal == anchor.ordinal && it.resource.resourceRef == anchor.resourceRef } }
                    ?: window.manifest.pages.first { it.ordinal == pages.first().ordinal }.let {
                        ReaderMangaAnchor(it.pageId, it.ordinal, it.resourceRef, 0.0, 0.0)
                    }
                restoreTarget = target
                mutable.value = mutable.value.copy(
                    phase = ReaderMangaReadingPhase.READY,
                    chapter = chapter,
                    window = window,
                    pages = pages,
                    visibleWindow = ticket,
                    pendingWindow = null, failedWindow = null,
                    layoutTicket = null, visibleAnchor = null, restoreRequest = newRestore(target),
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

    /** The owner alone issues layout epochs; remounting Compose cannot reuse an old ticket. */
    fun viewportChanged(window: ReaderMangaWindowTicket, width: Int, height: Int): ReaderMangaLayoutTicket? {
        val snapshot = mutable.value
        if (closed || snapshot.exitPending || snapshot.phase != ReaderMangaReadingPhase.READY ||
            snapshot.visibleWindow != window || width <= 0 || height <= 0) return null
        snapshot.layoutTicket?.takeIf { it.window == window && it.viewportWidthPx == width &&
            it.viewportHeightPx == height && it.zoom == snapshot.zoom }?.let { return it }
        check(nextLayoutEpoch < Long.MAX_VALUE) { "MANGA_LAYOUT_EPOCH_EXHAUSTED" }
        val ticket = ReaderMangaLayoutTicket(window, ++nextLayoutEpoch, width, height, snapshot.zoom)
        val target = lastStableAnchor?.takeIf(::loadedAnchor) ?: restoreTarget?.takeIf(::loadedAnchor)
            ?: return null
        restoreTarget = target
        mutable.value = snapshot.copy(layoutTicket = ticket, visibleAnchor = null, restoreRequest = newRestore(target))
        return ticket
    }

    fun requestZoom(value: Float) {
        val snapshot = mutable.value
        if (closed || snapshot.exitPending || !value.isFinite()) return
        val zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (zoom == snapshot.zoom) return
        restoreTarget = lastStableAnchor?.takeIf(::loadedAnchor) ?: restoreTarget
        mutable.value = snapshot.copy(zoom = zoom, layoutTicket = null, visibleAnchor = null,
            restoreRequest = restoreTarget?.let(::newRestore))
    }

    fun setZoom(value: Float) = requestZoom(value)

    /** Screen supplies this only after exact-ticket draw and native scrolling/transform controls are idle. */
    fun observeVisible(measured: ReaderMangaMeasuredAnchor): Boolean {
        val snapshot = mutable.value
        if (closed || snapshot.exitPending || snapshot.phase != ReaderMangaReadingPhase.READY ||
            snapshot.layoutTicket != measured.ticket || snapshot.visibleWindow != measured.ticket.window ||
            !loadedAnchor(measured.anchor)) return false
        val restore = snapshot.restoreRequest
        if (restore != null) {
            if (measured.restoredRequestId != restore.id || measured.restoredTargetPageId != restore.anchor.pageId ||
                !loadedAnchor(restore.anchor)) return false
            // Native end clamping may put an earlier page at the top. The separately drawn target proves restoration.
            // Restore establishes a measurement baseline only; a pre-layout genuine dirty snapshot stays intact.
        } else {
            if (measured.restoredRequestId != null || measured.restoredTargetPageId != null) return false
            if (measured.anchor != lastStableAnchor) dirtyAnchor = measured.anchor
        }
        lastStableAnchor = measured.anchor
        restoreTarget = measured.anchor
        mutable.value = snapshot.copy(visibleAnchor = measured.anchor, currentOrdinal = measured.anchor.ordinal,
            restoreRequest = null, progressDirty = dirtyAnchor != null)
        return true
    }

    private fun loadedAnchor(anchor: ReaderMangaAnchor): Boolean {
        if (!anchor.x.isFinite() || !anchor.y.isFinite() || anchor.x !in 0.0..1.0 || anchor.y !in 0.0..1.0) return false
        val manifest = activeChapter?.manifest ?: return false
        return manifest.pages.count { it.pageId == anchor.pageId && it.ordinal == anchor.ordinal && it.resourceRef == anchor.resourceRef } == 1 &&
            activePages.any { it.ordinal == anchor.ordinal && it.resource.resourceRef == anchor.resourceRef }
    }

    private fun newRestore(anchor: ReaderMangaAnchor): ReaderMangaRestoreRequest {
        check(nextRestoreId < Long.MAX_VALUE) { "MANGA_RESTORE_ID_EXHAUSTED" }
        return ReaderMangaRestoreRequest(++nextRestoreId, anchor)
    }

    fun retryWindow() {
        val failed = mutable.value.failedWindow ?: return
        val chapter = activeChapter ?: return
        if (!current(failed.generation) || mutable.value.exitPending || chapter.manifest.chapter != failed.chapter ||
            chapter.manifest.manifestVersion != failed.manifestVersion || chapter.manifest.sourceRuleVersion != failed.sourceRuleVersion ||
            chapter.manifest.decodeRevision != failed.decodeRevision) return
        loadWindow(failed.generation, chapter, failed.start, failed.limit)
    }

    /** Freeze synchronously before the caller queues its exit flush behind any current original. */
    fun beginExit(): Long? {
        val generation = activeGeneration ?: return null
        if (closed || mutable.value.exitPending) return null
        mutable.value = mutable.value.copy(exitPending = true)
        return generation
    }

    fun cancelExit(generation: Long) {
        if (current(generation)) mutable.value = mutable.value.copy(exitPending = false)
    }

    /** One latest genuine measurement per admission. NO_CHANGE is not an acknowledgement. */
    suspend fun flushVisibleProgress(): ReaderMangaFlushResult = progressMutex.withLock {
        val generation = activeGeneration ?: return@withLock ReaderMangaFlushResult.NO_CHANGE
        val snapshot = mutable.value
        if (writeBlocked || snapshot.progressPending.isNotEmpty()) return@withLock ReaderMangaFlushResult.BLOCKED
        val anchor = dirtyAnchor ?: return@withLock ReaderMangaFlushResult.NO_CHANGE
        if (snapshot.restoreBlocked != null) return@withLock ReaderMangaFlushResult.BLOCKED
        val chapter = activeChapter ?: return@withLock ReaderMangaFlushResult.BLOCKED
        val token = snapshot.progressToken ?: return@withLock ReaderMangaFlushResult.BLOCKED
        val location = ReaderMangaLocation(chapter.manifest.chapter, chapter.manifest.manifestVersion,
            anchor.pageId, anchor.ordinal, anchor.x, anchor.y, token.revision + 1)
        val request = ReaderMangaProgressSaveRequest(chapter.manifest, location, token)
        mutable.value = snapshot.copy(progressBusy = true, progressError = null)
        withContext(NonCancellable) {
            try {
                val result = library().saveProgress(request)
                check(result.location == location && result.token.epoch == token.epoch && result.token.revision == location.progressRevision) {
                    "MANGA_UI_PROGRESS_ACK_SCOPE"
                }
                if (!current(generation)) return@withContext ReaderMangaFlushResult.BLOCKED
                if (dirtyAnchor == anchor) dirtyAnchor = null
                mutable.value = mutable.value.copy(progressBusy = false, progressToken = result.token,
                    lastConfirmedProgress = result.location, progressDirty = dirtyAnchor != null, progressError = null)
                ReaderMangaFlushResult.CONFIRMED
            } catch (error: Throwable) {
                if (current(generation)) retainProgressFailure(error, generation, request = request)
                ReaderMangaFlushResult.BLOCKED
            }
        }
    }

    /** Explicitly consume the selected original. Never reads current progress to infer an ACK or replays an update. */
    suspend fun reconcileProgress(operationId: String): Boolean = progressMutex.withLock {
        val generation = activeGeneration ?: return@withLock false
        val owner = activeOwner ?: return@withLock false
        val original = mutable.value.progressPending.singleOrNull { it.operationId == operationId && it.chapter == owner }
            ?: return@withLock false
        mutable.value = mutable.value.copy(progressBusy = true)
        withContext(NonCancellable) {
            try {
                val result = library().reconcileProgress(original.chapter, original.operationId)
                val location = result.location ?: error("MANGA_UI_RECONCILE_LOCATION_MISSING")
                check(location.chapter.sourceId == owner.sourceId && location.chapter.bookId == owner.bookId &&
                    location.progressRevision == result.token.revision) { "MANGA_UI_RECONCILE_OWNER" }
                if (!current(generation)) return@withContext false
                val pending = library().pendingProgress()
                if (!current(generation)) return@withContext false
                dirtyAnchor?.let { if (it.pageId == location.pageId && it.ordinal == location.pageOrdinalFallback &&
                    it.x == location.x && it.y == location.y && activeChapter?.manifest?.chapter == location.chapter &&
                    activeChapter?.manifest?.manifestVersion == location.manifestVersion) dirtyAnchor = null }
                writeBlocked = pending.isNotEmpty()
                rejectionGeneration = null
                mutable.value = mutable.value.copy(progressBusy = false, progressPending = pending, progressToken = result.token,
                    lastConfirmedProgress = location, progressDirty = dirtyAnchor != null, progressError = null, knownRejection = null)
                true
            } catch (error: Throwable) {
                if (current(generation)) retainProgressFailure(error, generation, original = original)
                false
            }
        }
    }

    private suspend fun retainProgressFailure(error: Throwable, generation: Long,
        request: ReaderMangaProgressSaveRequest? = null, original: ReaderMangaProgressPending? = null) {
        writeBlocked = true // Even a failing journal query cannot authorize an automatic new write.
        val rejection = (error as? ReaderMangaProgressRejected)?.takeIf { rejected ->
            rejected.chapter == activeOwner && when {
                request != null -> rejected.location == request.location && rejected.expected == request.expected
                original != null -> rejected.operationId == original.operationId && rejected.chapter == original.chapter
                else -> false
            }
        }
        onFailure(error)
        var pending = mutable.value.progressPending
        try { pending = library().pendingProgress() }
        catch (queryFailure: Throwable) { onFailure(queryFailure) }
        if (current(generation)) {
            rejectionGeneration = generation.takeIf { rejection != null }
            mutable.value = mutable.value.copy(progressBusy = false, progressPending = pending, knownRejection = rejection,
                progressError = error.message ?: error::class.java.simpleName, progressDirty = dirtyAnchor != null)
        }
    }

    /** Explicit "do not save and return" only. This drops local unsaved intent, never Core data or an unknown original. */
    suspend fun discardRejectedProgress(operationId: String): Boolean = progressMutex.withLock {
        val generation = activeGeneration ?: return@withLock false
        val rejected = mutable.value.knownRejection ?: return@withLock false
        if (!mutable.value.exitPending || mutable.value.progressBusy || rejectionGeneration != generation ||
            rejected.operationId != operationId || rejected.chapter != activeOwner) return@withLock false
        mutable.value = mutable.value.copy(progressBusy = true)
        val pending = try { library().pendingProgress() }
        catch (error: Throwable) {
            onFailure(error)
            if (current(generation)) mutable.value = mutable.value.copy(progressBusy = false,
                progressError = error.message ?: error::class.java.simpleName)
            return@withLock false
        }
        if (!current(generation) || mutable.value.knownRejection !== rejected || !mutable.value.exitPending) return@withLock false
        if (pending.isNotEmpty()) {
            mutable.value = mutable.value.copy(progressBusy = false, progressPending = pending)
            return@withLock false
        }
        dirtyAnchor = null; writeBlocked = false; rejectionGeneration = null
        mutable.value = mutable.value.copy(progressBusy = false, progressDirty = false, progressPending = emptyList(),
            progressError = null, knownRejection = null)
        true // No get, update, token advance, successful-save claim, or navigation.
    }

    /** Retire the visible chapter while keeping this ViewModel-owned owner reusable. */
    fun retire() {
        if (closed) return
        ++nextGeneration
        activeGeneration = null
        activeOwner = null
        activeChapter = null
        inFlightWindow = null
        lastStableAnchor = null; dirtyAnchor = null; restoreTarget = null; writeBlocked = false; rejectionGeneration = null
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
        onFailure(error)
        inFlightWindow = null
        val message = error.message ?: error::class.java.simpleName
        val hasVisiblePages = activePages.isNotEmpty()
        mutable.value = mutable.value.copy(
            phase = if (hasVisiblePages) ReaderMangaReadingPhase.READY else ReaderMangaReadingPhase.ERROR,
            pendingWindow = null, failedWindow = ticket, windowError = message, error = if (hasVisiblePages) null else message,
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
