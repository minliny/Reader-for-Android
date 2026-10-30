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
import io.reader.android.runtime.ReaderMangaDirectoryNode
import io.reader.android.runtime.ReaderMangaPageWindow
import io.reader.android.runtime.ReaderMangaPageWindowRequest
import io.reader.android.runtime.ReaderMangaResource
import io.reader.android.runtime.ReaderMangaResourcePrepareRequest
import io.reader.android.runtime.ReaderMangaTransferIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.IdentityHashMap

/** The narrow Library surface a Manga reading owner is allowed to use. */
internal interface ReaderMangaLibrary {
    suspend fun progress(owner: ReaderMangaChapterRef): ReaderMangaProgressState
    suspend fun saveProgress(request: ReaderMangaProgressSaveRequest): ReaderMangaProgressState
    suspend fun pendingProgress(): List<ReaderMangaProgressPending>
    suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String): ReaderMangaProgressState
    suspend fun chapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData
    suspend fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow
    suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest): ReaderMangaDecodedResource
    suspend fun resolveChapter(node: ReaderMangaDirectoryNode): ReaderMangaChapterRequest = error("MANGA_DIRECTORY_UNAVAILABLE")
}

internal class ReaderMangaLibraryAdapter(private val library: ReaderLibrary) : ReaderMangaLibrary {
    override suspend fun progress(owner: ReaderMangaChapterRef) = library.mangaProgress(owner)
    override suspend fun saveProgress(request: ReaderMangaProgressSaveRequest) = library.saveMangaProgress(request)
    override suspend fun pendingProgress() = library.pendingMangaProgress()
    override suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String) = library.reconcileMangaProgress(owner, operationId)
    override suspend fun chapter(request: ReaderMangaChapterRequest) = library.mangaChapter(request)
    override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest) = library.mangaPagesWindow(request)
    override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest) = library.mangaDecodedResource(request)
    override suspend fun resolveChapter(node: ReaderMangaDirectoryNode) = library.resolveMangaDirectoryTarget(node)
}

internal enum class ReaderMangaFlushResult { NO_CHANGE, CONFIRMED, BLOCKED }

internal enum class ReaderMangaReadingPhase { IDLE, LOADING, READY, ERROR, CLOSED }

internal enum class ReaderMangaChapterLanding { FIRST, LAST }
internal enum class ReaderMangaChapterTransitionPhase { SAVING, PREPARING, PRESENTING }
internal data class ReaderMangaChapterTransition(
    val id: Long,
    val originGeneration: Long,
    val nodeId: String,
    val targetIndex: Int,
    val landing: ReaderMangaChapterLanding,
    val phase: ReaderMangaChapterTransitionPhase,
)

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
    val navigationTarget: ReaderMangaAnchor? = null,
    val lastVisibleOrdinal: Int? = null,
)

/** Managed encoded handles only. This is not a Host raster, Bitmap/GPU or whole-process memory budget. */
internal data class ReaderMangaEncodedPageBudget(
    val steadyBytes: Long = 32L * 1024 * 1024,
    val peakBytes: Long = 48L * 1024 * 1024,
    val prepareHeadroomBytes: Long = 16L * 1024 * 1024,
) {
    init { require(steadyBytes > 0 && prepareHeadroomBytes > 0 && peakBytes >= steadyBytes + prepareHeadroomBytes) }
}

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
    val chapterTransition: ReaderMangaChapterTransition? = null,
    val chapterError: String? = null,
    val error: String? = null,
)

/** Owns decoded windows, measured image locations and serialized original progress writes.
 * Layout/restore targets are never themselves saveable; only accepted drawn idle measurements are.
 */
internal class ReaderMangaReadingOwner(
    private val scope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit = {},
    private val encodedBudget: ReaderMangaEncodedPageBudget = ReaderMangaEncodedPageBudget(),
    private val chapterPresentationTimeoutMillis: Long = 15_000,
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
    private val resourceMutex = Mutex()
    private val managedReferences = IdentityHashMap<ReaderMangaDecodedResource, Int>()
    private val progressMutex = Mutex()
    private var activeOwner: ReaderMangaChapterRef? = null
    private var nextLayoutEpoch = 0L
    private var nextRestoreId = 0L
    private var lastStableAnchor: ReaderMangaAnchor? = null
    /** Kept separately from reflow baselines: a zoom/resize cannot invent or erase a genuine user position. */
    private var dirtyAnchor: ReaderMangaAnchor? = null
    private var restoreTarget: ReaderMangaAnchor? = null
    private var navigationRestoreTarget: ReaderMangaAnchor? = null
    private var writeBlocked = false
    private var rejectionGeneration: Long? = null
    private var nextChapterTransition = 0L
    private class ChapterAttempt(val identity: ReaderMangaChapterTransition, val origin: ReaderMangaChapterData) {
        var rollback: ChapterRollback? = null
        var publishedGeneration: Long? = null
        var timeout: Job? = null
    }
    private data class ChapterRollback(val state: ReaderMangaReadingUiState, val stable: ReaderMangaAnchor,
        val restore: ReaderMangaAnchor, val navigation: ReaderMangaAnchor?)
    private var chapterAttempt: ChapterAttempt? = null
    init { require(chapterPresentationTimeoutMillis > 0) }

    fun open(request: ReaderMangaChapterRequest) {
        if (closed) return
        if (chapterAttempt != null) return
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
        lastStableAnchor = null; dirtyAnchor = null; restoreTarget = null; navigationRestoreTarget = null
        writeBlocked = false; rejectionGeneration = null
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

    fun loadWindow(start: Int, limit: Int = DEFAULT_WINDOW_LIMIT, lastVisibleOrdinal: Int? = null) {
        val generation = activeGeneration ?: return
        val chapter = activeChapter ?: return
        if (mutable.value.exitPending || mutable.value.restoreBlocked != null || chapterAttempt != null) return
        val anchor = mutable.value.visibleAnchor ?: return
        val last = lastVisibleOrdinal ?: anchor.ordinal
        if (last < anchor.ordinal || activePages.none { it.ordinal == last } || !covers(start, limit, anchor.ordinal, last)) return
        val visible = mutable.value.visibleWindow
        if (inFlightWindow == null && visible?.start == start && visible.limit == limit) return
        loadWindow(generation, chapter, start, limit, lastVisibleOrdinal = last)
    }

    /** Explicit user navigation is single-page first; only its exact later draw may become a new saved position. */
    fun jumpToPage(ordinal: Int) {
        val generation = activeGeneration ?: return
        val chapter = activeChapter ?: return
        if (mutable.value.exitPending || mutable.value.restoreBlocked != null || chapterAttempt != null) return
        val page = chapter.manifest.pages.singleOrNull { it.ordinal == ordinal } ?: return
        val target = ReaderMangaAnchor(page.pageId, page.ordinal, page.resourceRef, 0.0, 0.0)
        loadWindow(generation, chapter, ordinal, 1, navigationTarget = target)
    }

    private fun covers(start: Int, limit: Int, first: Int, last: Int): Boolean = start >= 0 && limit in 1..32 &&
        first >= start && last.toLong() < start.toLong() + limit

    /** One explicit directory intent. The currently displayed chapter survives acquisition and presentation failure. */
    fun navigateChapter(node: ReaderMangaDirectoryNode, landing: ReaderMangaChapterLanding,
        resolvedRequest: ReaderMangaChapterRequest? = null): Boolean {
        val snapshot = mutable.value
        val origin = activeChapter ?: return false
        val generation = activeGeneration ?: return false
        val index = node.chapterIndex ?: return false
        if (closed || chapterAttempt != null || snapshot.exitPending || snapshot.phase != ReaderMangaReadingPhase.READY ||
            snapshot.restoreBlocked != null || snapshot.restoreRequest != null || snapshot.visibleAnchor == null ||
            node.kind != "target" || index == origin.chapterIndex || node.directory.chapter != activeOwner) return false
        if (resolvedRequest != null) {
            val target = resolvedRequest.directoryTarget ?: return false
            if (resolvedRequest.chapter != activeOwner || resolvedRequest.chapterIndex != index ||
                target.directory !== node.directory || target.nodeId != node.nodeId || target.chapterIndex != index ||
                target.url == origin.manifest.chapter.chapterId || target.sourceRuleVersion != origin.manifest.sourceRuleVersion) return false
        }
        check(nextChapterTransition < Long.MAX_VALUE) { "MANGA_CHAPTER_TRANSITION_EXHAUSTED" }
        val identity = ReaderMangaChapterTransition(++nextChapterTransition, generation, node.nodeId, index, landing,
            ReaderMangaChapterTransitionPhase.SAVING)
        val attempt = ChapterAttempt(identity, origin)
        chapterAttempt = attempt
        inFlightWindow = null // A late preserving-window candidate must not replace this frozen origin.
        mutable.value = snapshot.copy(chapterTransition = identity, chapterError = null, pendingWindow = null)
        scope.launch {
            val acquired = ArrayList<ReaderMangaReadingPage>()
            try {
                progressMutex.withLock {
                    if (!current(attempt)) return@withLock
                    if (flushProgressLocked() == ReaderMangaFlushResult.BLOCKED) {
                        if (current(attempt)) rollbackChapter(attempt, "MANGA_CHAPTER_PROGRESS_NOT_CONFIRMED")
                        return@withLock
                    }
                    if (!current(attempt)) return@withLock
                    val pending = library().pendingProgress()
                    if (!current(attempt)) return@withLock
                    mutable.value = mutable.value.copy(progressPending = pending)
                    check(pending.isEmpty()) { "MANGA_PROGRESS_ORIGINAL_PENDING" }
                    mutable.value = mutable.value.copy(chapterTransition = identity.copy(phase = ReaderMangaChapterTransitionPhase.PREPARING))
                    val request = resolvedRequest ?: library().resolveChapter(node)
                    if (!current(attempt)) return@withLock
                    val target = checkNotNull(request.directoryTarget) { "MANGA_CHAPTER_TARGET_REQUIRED" }
                    check(request.chapter == activeOwner && request.chapterIndex == index && target.directory === node.directory &&
                        target.nodeId == node.nodeId && target.chapterIndex == index && target.url != origin.manifest.chapter.chapterId &&
                        target.sourceRuleVersion == origin.manifest.sourceRuleVersion) { "MANGA_CHAPTER_TARGET_SCOPE" }
                    val chapter = library().chapter(request)
                    if (!current(attempt)) return@withLock
                    val manifest = chapter.manifest
                    check(manifest.chapter.sourceId == origin.manifest.chapter.sourceId &&
                        manifest.chapter.bookId == origin.manifest.chapter.bookId && manifest.chapter.chapterId == target.url &&
                        chapter.chapterIndex == index && manifest.sourceRuleVersion == origin.manifest.sourceRuleVersion &&
                        manifest.decodeRevision == origin.manifest.decodeRevision &&
                        (request.expectedManifestVersion == null || request.expectedManifestVersion == manifest.manifestVersion)) {
                        "MANGA_CHAPTER_TARGET_SCOPE"
                    }
                    val page = (if (landing == ReaderMangaChapterLanding.FIRST) manifest.pages.firstOrNull() else manifest.pages.lastOrNull())
                        ?: error("MANGA_UI_EMPTY_MANIFEST")
                    val anchor = ReaderMangaAnchor(page.pageId, page.ordinal, page.resourceRef, 0.0,
                        if (landing == ReaderMangaChapterLanding.LAST) 1.0 else 0.0)
                    check(nextGeneration < Long.MAX_VALUE && nextWindowRevision < Long.MAX_VALUE) { "MANGA_CHAPTER_ID_EXHAUSTED" }
                    val candidateGeneration = ++nextGeneration
                    val ticket = ReaderMangaWindowTicket(candidateGeneration, ++nextWindowRevision, manifest.chapter,
                        manifest.manifestVersion, manifest.sourceRuleVersion, manifest.decodeRevision, page.ordinal, 1,
                        navigationTarget = anchor)
                    val window = acquireWindow(ticket, acquired) { current(attempt) } ?: return@withLock
                    if (!current(attempt)) return@withLock
                    check(acquired.size == 1 && acquired.single().ordinal == anchor.ordinal &&
                        acquired.single().resource.resourceRef == anchor.resourceRef) { "MANGA_UI_TARGET_WINDOW_SCOPE" }
                    // All original writes have finished. This is only a current CAS observation, never a save receipt.
                    val observed = library().progress(request.chapter)
                    if (!current(attempt)) return@withLock
                    val previousToken = checkNotNull(mutable.value.progressToken) { "MANGA_CHAPTER_PROGRESS_TOKEN_MISSING" }
                    val location = observed.location
                    check(observed.token.epoch > 0 && observed.token.revision == previousToken.revision &&
                        (location == null && observed.token.revision == 0L || location != null &&
                            location.chapter.sourceId == request.chapter.sourceId && location.chapter.bookId == request.chapter.bookId &&
                            location.progressRevision == observed.token.revision) &&
                        mutable.value.lastConfirmedProgress?.takeIf { it.progressRevision == previousToken.revision }
                            ?.let { it == location } != false) { "MANGA_CHAPTER_PROGRESS_CHANGED" }
                    val old = mutable.value.copy(progressToken = observed.token)
                    val stable = checkNotNull(lastStableAnchor) { "MANGA_UI_VISIBLE_ANCHOR_MISSING" }
                    val restore = newRestore(anchor)
                    attempt.rollback = ChapterRollback(old, stable, restoreTarget ?: stable, navigationRestoreTarget)
                    attempt.publishedGeneration = candidateGeneration
                    activeGeneration = candidateGeneration
                    activeChapter = chapter
                    activePages = acquired.toList()
                    lastStableAnchor = null; dirtyAnchor = null
                    restoreTarget = anchor; navigationRestoreTarget = anchor
                    mutable.value = old.copy(generation = candidateGeneration, chapter = chapter, window = window,
                        pages = activePages, visibleWindow = ticket, pendingWindow = null, failedWindow = null,
                        windowError = null, layoutTicket = null, visibleAnchor = null, restoreRequest = restore,
                        progressDirty = false, chapterTransition = identity.copy(phase = ReaderMangaChapterTransitionPhase.PRESENTING),
                        chapterError = null, error = null)
                    acquired.clear() // Active candidate owns these references; the rollback bundle still owns the origin.
                    attempt.timeout = scope.launch {
                        delay(chapterPresentationTimeoutMillis)
                        if (current(attempt)) rollbackChapter(attempt, "MANGA_CHAPTER_PRESENTATION_TIMEOUT")
                    }
                }
            } catch (cancelled: CancellationException) {
                if (current(attempt)) rollbackChapter(attempt, null)
                throw cancelled
            } catch (error: Throwable) {
                if (current(attempt)) { onFailure(error); rollbackChapter(attempt, error.message ?: error::class.java.simpleName) }
            } finally { releasePages(acquired) }
        }
        return true
    }

    fun cancelChapterTransition() { chapterAttempt?.let { rollbackChapter(it, null) } }

    /** Native decode/layout failure is scoped to the currently published candidate, never an old Image callback. */
    fun failChapterPresentation(ticket: ReaderMangaLayoutTicket, reason: String) {
        val attempt = chapterAttempt ?: return
        if (attempt.publishedGeneration == ticket.window.generation && mutable.value.layoutTicket == ticket && current(attempt))
            rollbackChapter(attempt, reason)
    }

    private fun current(attempt: ChapterAttempt): Boolean = chapterAttempt === attempt &&
        current(attempt.publishedGeneration ?: attempt.identity.originGeneration) &&
        (attempt.publishedGeneration != null || activeChapter === attempt.origin)

    private fun rollbackChapter(attempt: ChapterAttempt, reason: String?) {
        if (!current(attempt)) return
        val rollback = attempt.rollback
        chapterAttempt = null
        attempt.timeout?.cancel(); attempt.timeout = null
        if (rollback == null) {
            mutable.value = mutable.value.copy(chapterTransition = null, chapterError = reason)
            return
        }
        val candidatePages = activePages
        val currentProgress = mutable.value
        val generation = ++nextGeneration
        val previousWindow = checkNotNull(rollback.state.visibleWindow)
        val window = previousWindow.copy(generation = generation, revision = ++nextWindowRevision)
        activeGeneration = generation; activeChapter = attempt.origin; activePages = rollback.state.pages
        inFlightWindow = null; lastStableAnchor = rollback.stable; dirtyAnchor = null
        restoreTarget = rollback.restore; navigationRestoreTarget = rollback.navigation
        mutable.value = rollback.state.copy(generation = generation, visibleWindow = window, pendingWindow = null,
            layoutTicket = null, visibleAnchor = null, restoreRequest = newRestore(rollback.restore), progressDirty = false,
            progressToken = currentProgress.progressToken, lastConfirmedProgress = currentProgress.lastConfirmedProgress,
            chapterTransition = null, chapterError = reason)
        attempt.rollback = null
        releasePages(candidatePages)
    }

    private fun loadWindow(generation: Long, chapter: ReaderMangaChapterData, start: Int, limit: Int,
        navigationTarget: ReaderMangaAnchor? = null, lastVisibleOrdinal: Int? = null) {
        require(start >= 0 && limit in 1..32) { "MANGA_UI_WINDOW_RANGE" }
        if (!current(generation) || activeChapter !== chapter) return
        val manifest = chapter.manifest
        val pending = inFlightWindow
        if (pending != null && current(pending) && pending.start == start && pending.limit == limit &&
            pending.navigationTarget == navigationTarget && pending.lastVisibleOrdinal == lastVisibleOrdinal) return
        check(nextWindowRevision < Long.MAX_VALUE) { "MANGA_UI_WINDOW_REVISION_EXHAUSTED" }
        val ticket = ReaderMangaWindowTicket(generation, ++nextWindowRevision, manifest.chapter,
            manifest.manifestVersion, manifest.sourceRuleVersion, manifest.decodeRevision, start, limit,
            navigationTarget, lastVisibleOrdinal)
        inFlightWindow = ticket
        mutable.value = mutable.value.copy(
            phase = if (activePages.isEmpty()) ReaderMangaReadingPhase.LOADING else ReaderMangaReadingPhase.READY,
            pendingWindow = ticket, failedWindow = null, windowError = null, error = null,
        )
        scope.launch {
            val acquired = ArrayList<ReaderMangaReadingPage>()
            try {
                if (!current(ticket)) return@launch
                val window = acquireWindow(ticket, acquired) { current(ticket) } ?: return@launch
                if (ticket.navigationTarget == null && lastStableAnchor?.let { anchor ->
                        acquired.none { it.ordinal == anchor.ordinal && it.resource.resourceRef == anchor.resourceRef }
                    } == true) {
                    // The reader moved outside this preserving candidate while it loaded. Leave the current window untouched.
                    inFlightWindow = null
                    mutable.value = mutable.value.copy(pendingWindow = null)
                    return@launch
                }
                val pages = acquired.toList()
                val previousPages = activePages
                val navigation = ticket.navigationTarget ?: navigationRestoreTarget
                val target = navigation ?: (lastStableAnchor ?: restoreTarget)?.takeIf { anchor ->
                    pages.any { it.ordinal == anchor.ordinal && it.resource.resourceRef == anchor.resourceRef }
                } ?: error("MANGA_UI_VISIBLE_ANCHOR_MISSING")
                check(pages.any { it.ordinal == target.ordinal && it.resource.resourceRef == target.resourceRef }) { "MANGA_UI_TARGET_WINDOW_SCOPE" }
                val restore = newRestore(target)
                activePages = pages
                inFlightWindow = null
                navigationRestoreTarget = navigation
                restoreTarget = target
                mutable.value = mutable.value.copy(
                    phase = ReaderMangaReadingPhase.READY,
                    chapter = chapter,
                    window = window,
                    pages = pages,
                    visibleWindow = ticket,
                    pendingWindow = null, failedWindow = null,
                    layoutTicket = null, visibleAnchor = null, restoreRequest = restore,
                    windowError = null, error = null,
                )
                acquired.clear() // The published owner now holds these leases.
                releasePages(previousPages)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failWindow(ticket, error)
            } finally {
                releasePages(acquired)
            }
        }
    }

    /** Shared staging path: each caller owns acquired until publication or finally releases it. */
    private suspend fun acquireWindow(ticket: ReaderMangaWindowTicket, acquired: ArrayList<ReaderMangaReadingPage>,
        isCurrent: () -> Boolean): ReaderMangaPageWindow? {
        val window = library().pagesWindow(ReaderMangaPageWindowRequest(
            chapter = ticket.chapter,
            manifestVersion = ticket.manifestVersion,
            start = ticket.start,
            limit = ticket.limit,
        ))
        if (!isCurrent()) return null
        check(window.manifest.chapter == ticket.chapter &&
            window.manifest.manifestVersion == ticket.manifestVersion &&
            window.manifest.sourceRuleVersion == ticket.sourceRuleVersion &&
            window.manifest.decodeRevision == ticket.decodeRevision &&
            window.pageStart == ticket.start && window.resources.size <= ticket.limit && window.resources.isNotEmpty() &&
            window.resources.withIndex().all { (index, resource) ->
                window.manifest.pages.singleOrNull { it.ordinal == ticket.start + index }?.resourceRef == resource.resourceRef
            }) {
            "MANGA_UI_WINDOW_SCOPE"
        }
        for ((index, resource) in window.resources.withIndex()) {
            if (!isCurrent()) return null
            val ordinal = window.pageStart + index
            resourceMutex.withLock {
                if (!isCurrent()) return@withLock
                try {
                    // Borrow only within the complete immutable identity, including during chapter preparation.
                    val reused = activePages.singleOrNull { activeChapter?.manifest?.sourceRuleVersion == ticket.sourceRuleVersion &&
                        activeChapter?.manifest?.decodeRevision == ticket.decodeRevision && it.ordinal == ordinal && it.resource == resource &&
                        it.decoded.chapter == ticket.chapter && it.decoded.manifestVersion == ticket.manifestVersion &&
                        it.decoded.resourceRef == resource.resourceRef }
                    if (reused != null) {
                        retainPage(reused); acquired += reused
                    } else {
                        // One preparation at a time, including superseded candidates. Account all live unique handles.
                        check(managedBytes() + encodedBudget.prepareHeadroomBytes <= encodedBudget.peakBytes) { ENCODED_BUDGET_ERROR }
                        val request = ReaderMangaResourcePrepareRequest(
                            chapter = ticket.chapter, manifestVersion = ticket.manifestVersion,
                            resourceRef = resource.resourceRef, sourceRuleVersion = ticket.sourceRuleVersion,
                            transferId = "android-manga-ui:${ticket.generation}:${ticket.revision}:${ordinal}:${resource.resourceRef}",
                        )
                        val decoded = library().decodedResource(request)
                        val page = ReaderMangaReadingPage(ordinal, resource, decoded)
                        check(!managedReferences.containsKey(decoded)) { "MANGA_UI_DUPLICATE_DECODED_HANDLE" }
                        managedReferences[decoded] = 1
                        acquired += page // Every returned handle belongs to this candidate, even when late or foreign.
                        check(decoded.bytes.size.toLong() <= encodedBudget.prepareHeadroomBytes &&
                            managedBytes() <= encodedBudget.peakBytes) { ENCODED_BUDGET_ERROR }
                        check(decoded.chapter == ticket.chapter && decoded.manifestVersion == ticket.manifestVersion &&
                            decoded.resourceRef == resource.resourceRef &&
                            decoded.transferId == ReaderMangaTransferIdentity.forPrepare(request).wireId) { "MANGA_UI_RESOURCE_SCOPE" }
                    }
                    check(acquired.sumOf { it.decoded.bytes.size.toLong() } <= encodedBudget.steadyBytes) { ENCODED_BUDGET_ERROR }
                    if (!isCurrent()) { releasePages(acquired); acquired.clear() }
                } catch (error: Throwable) {
                    // Release before unlocking: an awaiting newer candidate must not count a retired candidate's handles.
                    releasePages(acquired); acquired.clear(); throw error
                }
            }
            if (!isCurrent()) return null
        }
        if (!isCurrent()) return null
        return window
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
        val target = navigationRestoreTarget?.takeIf(::loadedAnchor) ?: lastStableAnchor?.takeIf(::loadedAnchor) ?: restoreTarget?.takeIf(::loadedAnchor)
            ?: return null
        restoreTarget = target
        mutable.value = snapshot.copy(layoutTicket = ticket, visibleAnchor = null, restoreRequest = newRestore(target))
        return ticket
    }

    fun requestZoom(value: Float) {
        val snapshot = mutable.value
        if (closed || snapshot.exitPending || chapterAttempt != null || !value.isFinite()) return
        val zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (zoom == snapshot.zoom) return
        restoreTarget = navigationRestoreTarget ?: lastStableAnchor?.takeIf(::loadedAnchor) ?: restoreTarget
        mutable.value = snapshot.copy(zoom = zoom, layoutTicket = null, visibleAnchor = null,
            restoreRequest = restoreTarget?.let(::newRestore))
    }

    fun setZoom(value: Float) = requestZoom(value)

    /** Screen supplies this only after exact-ticket draw and native scrolling/transform controls are idle. */
    fun observeVisible(measured: ReaderMangaMeasuredAnchor): Boolean {
        val snapshot = mutable.value
        val transition = chapterAttempt
        if (transition != null && (transition.publishedGeneration == null || !current(transition))) return false
        if (closed || snapshot.exitPending || snapshot.phase != ReaderMangaReadingPhase.READY ||
            snapshot.layoutTicket != measured.ticket || snapshot.visibleWindow != measured.ticket.window ||
            !loadedAnchor(measured.anchor)) return false
        val restore = snapshot.restoreRequest
        if (transition != null && restore == null) return false
        if (restore != null) {
            if (measured.restoredRequestId != restore.id || measured.restoredTargetPageId != restore.anchor.pageId ||
                !loadedAnchor(restore.anchor)) return false
            // Native end clamping may put an earlier page at the top. The separately drawn target proves restoration.
            // Resume/resize is a baseline. An explicit page navigation earns save eligibility only from this real draw.
            navigationRestoreTarget?.let { target ->
                if (restore.anchor != target) return false
                dirtyAnchor = measured.anchor
                navigationRestoreTarget = null
            }
        } else {
            if (measured.restoredRequestId != null || measured.restoredTargetPageId != null) return false
            if (measured.anchor != lastStableAnchor) dirtyAnchor = measured.anchor
        }
        lastStableAnchor = measured.anchor
        restoreTarget = measured.anchor
        val previousPages = transition?.rollback?.state?.pages
        if (transition != null) {
            transition.timeout?.cancel(); transition.timeout = null
            transition.rollback = null; chapterAttempt = null
        }
        mutable.value = snapshot.copy(visibleAnchor = measured.anchor, currentOrdinal = measured.anchor.ordinal,
            restoreRequest = null, progressDirty = dirtyAnchor != null, chapterTransition = null)
        previousPages?.let(::releasePages) // The real drawn target, not a prepared handle, transfers ownership.
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
        if (chapterAttempt != null) return
        val failed = mutable.value.failedWindow ?: return
        val chapter = activeChapter ?: return
        if (!current(failed.generation) || mutable.value.exitPending || chapter.manifest.chapter != failed.chapter ||
            chapter.manifest.manifestVersion != failed.manifestVersion || chapter.manifest.sourceRuleVersion != failed.sourceRuleVersion ||
            chapter.manifest.decodeRevision != failed.decodeRevision) return
        val anchor = mutable.value.visibleAnchor
        if (failed.navigationTarget == null && anchor != null &&
            !covers(failed.start, failed.limit, anchor.ordinal, maxOf(anchor.ordinal, failed.lastVisibleOrdinal ?: anchor.ordinal))) return
        loadWindow(failed.generation, chapter, failed.start, failed.limit, failed.navigationTarget, failed.lastVisibleOrdinal)
    }

    /** Explicit recovery after an empty initial window exceeded its encoded budget; never automatic shrinking. */
    fun retryWindowSinglePage() {
        if (chapterAttempt != null) return
        val failed = mutable.value.failedWindow ?: return
        val chapter = activeChapter ?: return
        val target = restoreTarget ?: return
        if (activePages.isNotEmpty() || mutable.value.windowError != ENCODED_BUDGET_ERROR || mutable.value.exitPending ||
            !current(failed.generation) || chapter.manifest.chapter != failed.chapter ||
            chapter.manifest.manifestVersion != failed.manifestVersion || chapter.manifest.sourceRuleVersion != failed.sourceRuleVersion ||
            chapter.manifest.decodeRevision != failed.decodeRevision || chapter.manifest.pages.none {
                it.pageId == target.pageId && it.ordinal == target.ordinal && it.resourceRef == target.resourceRef
            }) return
        // Keep the saved target and its x/y. This is resume/reflow, not an explicit page-navigation write intent.
        loadWindow(failed.generation, chapter, target.ordinal, 1)
    }

    /** Freeze synchronously before the caller queues its exit flush behind any current original. */
    fun beginExit(): Long? {
        if (closed || mutable.value.exitPending) return null
        cancelChapterTransition()
        val generation = activeGeneration ?: return null
        mutable.value = mutable.value.copy(exitPending = true)
        return generation
    }

    fun cancelExit(generation: Long) {
        if (current(generation)) mutable.value = mutable.value.copy(exitPending = false)
    }

    /** One latest genuine measurement per admission. NO_CHANGE is not an acknowledgement. */
    suspend fun flushVisibleProgress(): ReaderMangaFlushResult = progressMutex.withLock {
        if (chapterAttempt != null) return@withLock ReaderMangaFlushResult.BLOCKED
        flushProgressLocked()
    }

    private suspend fun flushProgressLocked(): ReaderMangaFlushResult {
        val generation = activeGeneration ?: return ReaderMangaFlushResult.NO_CHANGE
        val snapshot = mutable.value
        if (writeBlocked || snapshot.progressPending.isNotEmpty()) return ReaderMangaFlushResult.BLOCKED
        val anchor = dirtyAnchor ?: return ReaderMangaFlushResult.NO_CHANGE
        if (snapshot.restoreBlocked != null) return ReaderMangaFlushResult.BLOCKED
        val chapter = activeChapter ?: return ReaderMangaFlushResult.BLOCKED
        val token = snapshot.progressToken ?: return ReaderMangaFlushResult.BLOCKED
        val location = ReaderMangaLocation(chapter.manifest.chapter, chapter.manifest.manifestVersion,
            anchor.pageId, anchor.ordinal, anchor.x, anchor.y, token.revision + 1)
        val request = ReaderMangaProgressSaveRequest(chapter.manifest, location, token)
        mutable.value = snapshot.copy(progressBusy = true, progressError = null)
        return withContext(NonCancellable) {
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
        if (chapterAttempt != null) return@withLock false
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
        chapterAttempt?.timeout?.cancel()
        chapterAttempt?.rollback?.state?.pages?.let(::releasePages)
        chapterAttempt?.rollback = null
        chapterAttempt = null
        ++nextGeneration
        activeGeneration = null
        activeOwner = null
        activeChapter = null
        inFlightWindow = null
        lastStableAnchor = null; dirtyAnchor = null; restoreTarget = null; navigationRestoreTarget = null
        writeBlocked = false; rejectionGeneration = null
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

    private fun managedBytes(): Long = managedReferences.keys.sumOf { it.bytes.size.toLong() }

    private fun retainPage(page: ReaderMangaReadingPage) {
        val count = checkNotNull(managedReferences[page.decoded]) { "MANGA_UI_HANDLE_NOT_OWNED" }
        managedReferences[page.decoded] = count + 1
    }

    private fun releasePages(pages: List<ReaderMangaReadingPage>) {
        pages.forEach { page ->
            val count = checkNotNull(managedReferences[page.decoded]) { "MANGA_UI_HANDLE_NOT_OWNED" }
            if (count == 1) { managedReferences.remove(page.decoded); page.decoded.close() }
            else managedReferences[page.decoded] = count - 1
        }
    }

    private fun retirePages() {
        releasePages(activePages)
        activePages = emptyList()
    }

    companion object {
        const val DEFAULT_WINDOW_LIMIT = 8
        const val ENCODED_BUDGET_ERROR = "MANGA_UI_ENCODED_WINDOW_BUDGET"
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 3f
    }
}
