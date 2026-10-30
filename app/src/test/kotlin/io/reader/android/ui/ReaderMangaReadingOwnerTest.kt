package io.reader.android.ui

import io.reader.android.runtime.ReaderMangaChapterData
import io.reader.android.runtime.ReaderMangaProgressPending
import io.reader.android.runtime.ReaderMangaProgressSaveRequest
import io.reader.android.runtime.ReaderMangaProgressState
import io.reader.android.runtime.ReaderMangaProgressToken
import io.reader.android.runtime.ReaderMangaLocation
import io.reader.android.runtime.ReaderMangaChapterIdentity
import io.reader.android.runtime.ReaderMangaChapterRef
import io.reader.android.runtime.ReaderMangaChapterRequest
import io.reader.android.runtime.ReaderMangaDecodedResource
import io.reader.android.runtime.ReaderMangaManifest
import io.reader.android.runtime.ReaderMangaManifestPage
import io.reader.android.runtime.ReaderMangaPage
import io.reader.android.runtime.ReaderMangaPageWindow
import io.reader.android.runtime.ReaderMangaPageWindowRequest
import io.reader.android.runtime.ReaderMangaReadPolicy
import io.reader.android.runtime.ReaderMangaResource
import io.reader.android.runtime.ReaderMangaResourcePrepareRequest
import io.reader.android.runtime.ReaderMangaResourceStore
import io.reader.android.runtime.ReaderMangaTransferIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReaderMangaReadingOwnerTest {
    @Test
    fun openLoadsBoundWindowAndCloseReleasesDecodedHandles(): Unit = runBlocking {
        val fake = FakeMangaLibrary()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val owner = ReaderMangaReadingOwner(scope) { fake }
        owner.open(ReaderMangaChapterRequest(ReaderMangaChapterRef("source", "book"), 0, ReaderMangaReadPolicy.CACHE_FIRST))
        awaitPhase(owner, ReaderMangaReadingPhase.READY)
        assertEquals(ReaderMangaReadingPhase.READY, owner.state.value.phase)
        assertEquals(1, owner.state.value.pages.size)
        assertEquals("manga:page-0", owner.state.value.pages.single().resource.resourceRef)
        assertEquals(0, owner.state.value.currentOrdinal)
        owner.setZoom(9f)
        assertEquals(3f, owner.state.value.zoom)
        owner.setZoom(0f)
        assertEquals(1f, owner.state.value.zoom)
        assertFalse(fake.evictCurrent())
        owner.close()
        assertEquals(ReaderMangaReadingPhase.CLOSED, owner.state.value.phase)
        assertTrue(fake.evictCurrent())
        owner.close(); job.cancel()
    }

    @Test
    fun closeDropsOldGenerationAndClosesLateDecodedHandle(): Unit = runBlocking {
        val fake = FakeMangaLibrary()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fake.decodedEntered = entered
        fake.decodedRelease = release
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val owner = ReaderMangaReadingOwner(scope) { fake }
        owner.open(ReaderMangaChapterRequest(ReaderMangaChapterRef("source", "book"), 0))
        entered.await()
        owner.close()
        release.complete(Unit)
        awaitPhase(owner, ReaderMangaReadingPhase.CLOSED)
        assertEquals(ReaderMangaReadingPhase.CLOSED, owner.state.value.phase)
        assertTrue(fake.evictCurrent())
        job.cancel()
    }

    @Test
    fun foreignDecodedScopeFailsWithoutPublishingPage(): Unit = runBlocking {
        val fake = FakeMangaLibrary(foreignScope = true)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val owner = ReaderMangaReadingOwner(scope) { fake }
        owner.open(ReaderMangaChapterRequest(ReaderMangaChapterRef("source", "book"), 0))
        awaitPhase(owner, ReaderMangaReadingPhase.ERROR)
        assertEquals(ReaderMangaReadingPhase.ERROR, owner.state.value.phase)
        assertTrue(owner.state.value.pages.isEmpty())
        assertNotNull(owner.state.value.error)
        assertTrue(fake.evictCurrent())
        job.cancel()
    }

    @Test
    fun laterResourceFailureReleasesEarlierDecodedHandle(): Unit = runBlocking {
        val fake = FakeMangaLibrary(failAfterFirst = true)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val owner = ReaderMangaReadingOwner(scope) { fake }
        owner.open(ReaderMangaChapterRequest(ReaderMangaChapterRef("source", "book"), 0))
        awaitPhase(owner, ReaderMangaReadingPhase.ERROR)
        assertEquals(ReaderMangaReadingPhase.ERROR, owner.state.value.phase)
        assertTrue(fake.evictCurrent())
        job.cancel()
    }

    @Test
    fun newerWindowPublishesAtomicallyAndLateWindowDoesNotAcquireResources(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open()
            val original = fixture.owner.state.value
            fixture.observe(5)
            val release = CompletableDeferred<Unit>()
            fixture.library.windowGates[2] = release
            fixture.owner.loadWindow(2, 4)
            assertSame(original.pages, fixture.owner.state.value.pages)
            assertEquals(ReaderMangaReadingPhase.READY, fixture.owner.state.value.phase)
            val firstTicket = checkNotNull(fixture.owner.state.value.pendingWindow)
            assertEquals(original.visibleWindow, fixture.owner.state.value.visibleWindow)
            fixture.owner.loadWindow(4, 2)
            awaitWindow(fixture.owner, 4)
            val published = fixture.owner.state.value
            assertEquals(listOf(4, 5), published.pages.map { it.ordinal })
            assertEquals(5, published.currentOrdinal)
            assertTrue(checkNotNull(published.visibleWindow).revision > firstTicket.revision)
            assertTrue(fixture.library.evict(0)) // The replaced window has released its own handles.
            val decodedBefore = fixture.library.decodeRequests.size
            release.complete(Unit)
            yield()
            assertSame(published, fixture.owner.state.value)
            assertEquals(decodedBefore, fixture.library.decodeRequests.size)
            assertEquals(listOf(0, 2, 4), fixture.library.windowRequests.map { it.start })
        } finally { fixture.close() }
    }

    @Test
    fun lateWindowFailureCannotOverwriteNewerVisibleWindow(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open()
            val release = CompletableDeferred<Unit>()
            fixture.library.windowGates[2] = release
            fixture.library.failedWindows += 2
            fixture.owner.jumpToPage(2)
            fixture.owner.jumpToPage(4)
            awaitWindow(fixture.owner, 4)
            val published = fixture.owner.state.value
            release.complete(Unit)
            yield()
            assertSame(published, fixture.owner.state.value)
            assertNull(published.windowError)
            assertFalse(fixture.library.evict(4))
        } finally { fixture.close() }
    }

    @Test
    fun replacementResourceFailureKeepsVisiblePagesAndReleasesPartialCandidate(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open()
            fixture.observe(5)
            val original = fixture.owner.state.value
            fixture.library.failedResources += 9
            fixture.owner.loadWindow(4, 6)
            withTimeout(5_000) { while (fixture.owner.state.value.pendingWindow != null) yield() }
            val failed = fixture.owner.state.value
            assertEquals(ReaderMangaReadingPhase.READY, failed.phase)
            assertSame(original.pages, failed.pages)
            assertSame(original.window, failed.window)
            assertEquals(original.visibleWindow, failed.visibleWindow)
            assertEquals(5, failed.currentOrdinal)
            assertNull(failed.error)
            assertEquals("WINDOW_RESOURCE_FAILED_9", failed.windowError)
            assertFalse(fixture.library.evict(0))
            assertTrue(fixture.library.evict(8))
            assertEquals(4, checkNotNull(failed.failedWindow).start)
            assertTrue(failed.progressDirty)
            fixture.library.failedResources.remove(9)
            fixture.owner.retryWindow()
            awaitWindow(fixture.owner, 4)
            assertNull(fixture.owner.state.value.failedWindow)
            assertTrue(fixture.owner.state.value.progressDirty)
        } finally { fixture.close() }
    }

    @Test
    fun duplicateInFlightWindowDispatchesOnlyOnce(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open()
            val release = CompletableDeferred<Unit>()
            fixture.library.windowGates[0] = release
            fixture.owner.loadWindow(0, 10, 7)
            val first = fixture.owner.state.value.pendingWindow
            fixture.owner.loadWindow(0, 10, 7)
            assertEquals(first, fixture.owner.state.value.pendingWindow)
            assertEquals(listOf(0, 0), fixture.library.windowRequests.map { it.start })
            release.complete(Unit)
            awaitWindow(fixture.owner, 0)
            assertEquals(first, fixture.owner.state.value.visibleWindow)
            assertNull(fixture.owner.state.value.pendingWindow)
        } finally { fixture.close() }
    }

    @Test
    fun lateDecodedHandleIsReleasedWithoutTouchingNewWindowHandles(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open()
            val release = CompletableDeferred<Unit>()
            fixture.library.decodeGates[8] = release
            fixture.owner.jumpToPage(8)
            assertTrue(fixture.library.decodeRequests.any { it.resourceRef == "manga:page-8" })
            fixture.owner.jumpToPage(9)
            assertEquals(9, fixture.owner.state.value.pendingWindow?.start)
            assertEquals(0, fixture.owner.state.value.visibleWindow?.start) // Native preparation is serialized across candidates.
            release.complete(Unit)
            awaitWindow(fixture.owner, 9)
            val published = fixture.owner.state.value
            withTimeout(5_000) { while (!fixture.library.hasDecoded(8)) yield() }
            assertSame(published, fixture.owner.state.value)
            assertTrue(fixture.library.evict(8))
            assertFalse(fixture.library.evict(9))
            fixture.owner.close()
            assertTrue(fixture.library.evict(9))
        } finally { fixture.close() }
    }

    @Test
    fun replacementWindowRequiresExactChapterManifestRulesAndDecodeRevision(): Unit = runBlocking {
        val changes: List<(ReaderMangaManifest) -> ReaderMangaManifest> = listOf(
            { it.copy(chapter = it.chapter.copy(chapterId = "foreign")) },
            { it.copy(manifestVersion = "foreign") },
            { it.copy(sourceRuleVersion = "foreign") },
            { it.copy(decodeRevision = "foreign") },
        )
        for (change in changes) {
            val fixture = WindowFixture()
            try {
                fixture.open()
                val original = fixture.owner.state.value
                val decodedBefore = fixture.library.decodeRequests.size
                fixture.library.changeWindowManifest = change
                fixture.owner.jumpToPage(8)
                withTimeout(5_000) { while (fixture.owner.state.value.pendingWindow != null) yield() }
                assertSame(original.pages, fixture.owner.state.value.pages)
                assertEquals(original.visibleWindow, fixture.owner.state.value.visibleWindow)
                assertEquals("MANGA_UI_WINDOW_SCOPE", fixture.owner.state.value.windowError)
                assertEquals(decodedBefore, fixture.library.decodeRequests.size)
            } finally { fixture.close() }
        }
    }

    @Test fun preservingExpansionReusesHandlesAndKeepsBaselineAcrossEightToThirtyTwoPages(): Unit = runBlocking {
        val fixture = WindowFixture(pageCount = 40)
        try {
            fixture.open()
            val firstHandle = fixture.owner.state.value.pages.first().decoded
            for (limit in listOf(16, 24, 32)) {
                val last = fixture.owner.state.value.pages.last().ordinal
                fixture.owner.loadWindow(0, limit, last)
                awaitWindow(fixture.owner, 0)
                assertEquals(limit, fixture.owner.state.value.pages.size)
                assertEquals(limit, fixture.library.decodeRequests.size)
                assertSame(firstHandle, fixture.owner.state.value.pages.first().decoded)
                fixture.restore()
                assertFalse(fixture.owner.state.value.progressDirty)
                assertEquals(ReaderMangaFlushResult.NO_CHANGE, fixture.owner.flushVisibleProgress())
            }
            val requests = fixture.library.windowRequests.size
            fixture.owner.loadWindow(0, 32, 31)
            fixture.owner.loadWindow(0, 33, 31)
            assertEquals(requests, fixture.library.windowRequests.size)
            assertFalse(fixture.library.evict(0))
            fixture.owner.close()
            assertTrue(fixture.library.evict(0)); assertTrue(fixture.library.evict(31))
        } finally { fixture.close() }
    }

    @Test fun preservingWindowRejectsMissingVisibleSpanAndRechecksAnchorAfterLoading(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open(); fixture.observe(5)
            val original = fixture.owner.state.value
            fixture.owner.loadWindow(4, 2, 7)
            fixture.owner.loadWindow(8, 2, 7)
            assertEquals(1, fixture.library.windowRequests.size)
            val release = CompletableDeferred<Unit>()
            fixture.library.windowGates[4] = release
            fixture.owner.loadWindow(4, 6, 7)
            fixture.observe(3) // The accepted actual position moved outside the requested candidate.
            release.complete(Unit)
            withTimeout(5_000) { while (fixture.owner.state.value.pendingWindow != null) yield() }
            assertSame(original.pages, fixture.owner.state.value.pages)
            assertEquals(original.visibleWindow, fixture.owner.state.value.visibleWindow)
            assertEquals(3, fixture.owner.state.value.currentOrdinal)
            assertNull(fixture.owner.state.value.windowError)
            assertFalse(fixture.library.evict(0)); assertTrue(fixture.library.evict(8))
        } finally { fixture.close() }
    }

    @Test fun steadyEncodedBudgetRejectsCandidateAndReleasesOnlyNewHandles(): Unit = runBlocking {
        val fixture = WindowFixture(pageCount = 20, encodedBytes = 2, budget = ReaderMangaEncodedPageBudget(32, 48, 16))
        try {
            fixture.open()
            val original = fixture.owner.state.value
            fixture.owner.loadWindow(0, 17, 7)
            withTimeout(5_000) { while (fixture.owner.state.value.pendingWindow != null) yield() }
            assertSame(original.pages, fixture.owner.state.value.pages)
            assertEquals(ReaderMangaReadingOwner.ENCODED_BUDGET_ERROR, fixture.owner.state.value.windowError)
            assertTrue(fixture.library.hasDecoded(16)) // Actual bytes crossed steady admission, then the full candidate was released.
            assertTrue(fixture.library.evict(8)); assertTrue(fixture.library.evict(16))
            assertFalse(fixture.library.evict(0)); assertFalse(fixture.library.evict(7))
        } finally { fixture.close() }
    }

    @Test fun prepareHeadroomIncludesOldWindowAndExplicitSinglePageCanReplaceFullBudget(): Unit = runBlocking {
        val fixture = WindowFixture(pageCount = 20, encodedBytes = 4, budget = ReaderMangaEncodedPageBudget(32, 48, 16))
        try {
            fixture.open(); fixture.observe(5)
            val original = fixture.owner.state.value
            fixture.owner.loadWindow(5, 6, 7)
            withTimeout(5_000) { while (fixture.owner.state.value.pendingWindow != null) yield() }
            assertSame(original.pages, fixture.owner.state.value.pages)
            assertEquals(ReaderMangaReadingOwner.ENCODED_BUDGET_ERROR, fixture.owner.state.value.windowError)
            assertTrue(fixture.library.hasDecoded(8)); assertFalse(fixture.library.hasDecoded(9))
            assertTrue(fixture.library.evict(8)); assertFalse(fixture.library.evict(5))
            fixture.owner.jumpToPage(9)
            awaitWindow(fixture.owner, 9)
            assertEquals(listOf(9), fixture.owner.state.value.pages.map { it.ordinal })
            assertNull(fixture.owner.state.value.windowError)
            assertTrue(fixture.library.evict(5)); assertFalse(fixture.library.evict(9))
        } finally { fixture.close() }
    }

    @Test fun supersededCandidateCannotCloseBorrowedHandleUsedByNewPublishedWindow(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open(); fixture.observe(5)
            val reused = fixture.owner.state.value.pages.single { it.ordinal == 4 }.decoded
            val release = CompletableDeferred<Unit>()
            fixture.library.decodeGates[8] = release
            fixture.owner.loadWindow(4, 6, 7) // Borrows 4..7, then awaits a new handle.
            fixture.owner.jumpToPage(4) // Must wait for that one active preparation.
            assertEquals(4, fixture.owner.state.value.pendingWindow?.start)
            release.complete(Unit)
            awaitWindow(fixture.owner, 4)
            assertSame(reused, fixture.owner.state.value.pages.single().decoded)
            assertFalse(fixture.library.evict(4)); assertTrue(fixture.library.evict(8))
            fixture.owner.close()
            assertTrue(fixture.library.evict(4))
        } finally { fixture.close() }
    }

    @Test fun initialBudgetFailureHasExplicitSinglePageResumeAtOriginalSavedTargetWithoutWritingClamp(): Unit = runBlocking {
        val fixture = WindowFixture(pageCount = 20, encodedBytes = 8, budget = ReaderMangaEncodedPageBudget(32, 48, 16))
        try {
            val manifest = fixture.library.manifest
            val savedPage = manifest.pages.single { it.ordinal == 5 }
            val saved = ReaderMangaProgressState(ReaderMangaProgressToken(3, 9), ReaderMangaLocation(manifest.chapter,
                manifest.manifestVersion, savedPage.pageId, 5, 0.25, 0.7, 9))
            fixture.library.progressValue = saved
            fixture.open(ReaderMangaReadingPhase.ERROR)
            assertTrue(fixture.owner.state.value.pages.isEmpty())
            assertEquals(4, fixture.owner.state.value.failedWindow?.start)
            assertEquals(ReaderMangaReadingOwner.ENCODED_BUDGET_ERROR, fixture.owner.state.value.windowError)
            assertEquals(1, fixture.library.windowRequests.size) // No implicit shrinking or retry loop.
            fixture.owner.retryWindowSinglePage()
            awaitWindow(fixture.owner, 5)
            assertEquals(1, fixture.owner.state.value.visibleWindow?.limit)
            val ticket = checkNotNull(fixture.owner.viewportChanged(checkNotNull(fixture.owner.state.value.visibleWindow), 400, 700))
            val target = checkNotNull(fixture.owner.state.value.restoreRequest)
            assertEquals(savedPage.pageId, target.anchor.pageId)
            assertEquals(0.25, target.anchor.x, 0.0); assertEquals(0.7, target.anchor.y, 0.0)
            assertTrue(fixture.owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, target.anchor.copy(x = 0.0, y = 0.0), target.id, target.anchor.pageId)))
            assertFalse(fixture.owner.state.value.progressDirty)
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, fixture.owner.flushVisibleProgress())
            assertEquals(saved.token, fixture.owner.state.value.progressToken)
            assertNull(fixture.owner.state.value.lastConfirmedProgress)
            assertEquals(saved, fixture.library.progressValue)
            fixture.owner.retryWindowSinglePage()
            assertEquals(2, fixture.library.windowRequests.size) // The recovery is unavailable once a window is published.
        } finally { fixture.close() }
    }

    @Test fun failedExplicitJumpRetryKeepsItsTargetAndWaitsForActualDrawBeforeDirty(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open()
            val original = fixture.owner.state.value
            fixture.library.failedResources += 9
            fixture.owner.jumpToPage(9)
            withTimeout(5_000) { while (fixture.owner.state.value.pendingWindow != null) yield() }
            assertSame(original.pages, fixture.owner.state.value.pages)
            assertEquals(9, fixture.owner.state.value.failedWindow?.navigationTarget?.ordinal)
            assertFalse(fixture.owner.state.value.progressDirty)
            fixture.library.failedResources.remove(9)
            fixture.owner.retryWindow()
            awaitWindow(fixture.owner, 9)
            assertFalse(fixture.owner.state.value.progressDirty)
            assertEquals(9, fixture.owner.state.value.restoreRequest?.anchor?.ordinal)
            fixture.restore()
            assertTrue(fixture.owner.state.value.progressDirty)
            assertEquals(9, fixture.owner.state.value.visibleAnchor?.ordinal)
        } finally { fixture.close() }
    }

    private class WindowFixture(pageCount: Int = 12, encodedBytes: Int = 3,
        budget: ReaderMangaEncodedPageBudget = ReaderMangaEncodedPageBudget()) : AutoCloseable {
        val library = WindowLibrary(pageCount, encodedBytes)
        private val job = SupervisorJob()
        val owner = ReaderMangaReadingOwner(CoroutineScope(job + Dispatchers.Unconfined), encodedBudget = budget) { library }
        suspend fun open(expect: ReaderMangaReadingPhase = ReaderMangaReadingPhase.READY) {
            owner.open(ReaderMangaChapterRequest(ReaderMangaChapterRef("source", "book"), 0))
            withTimeout(5_000) {
                while (owner.state.value.phase == ReaderMangaReadingPhase.LOADING) yield()
            }
            check(owner.state.value.phase == expect)
            if (expect == ReaderMangaReadingPhase.READY) restore()
        }
        fun restore() {
            val ticket = checkNotNull(owner.viewportChanged(checkNotNull(owner.state.value.visibleWindow), 400, 700))
            val target = checkNotNull(owner.state.value.restoreRequest)
            check(owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, target.anchor, target.id, target.anchor.pageId)))
        }
        fun observe(ordinal: Int) {
            val state = owner.state.value
            val ticket = checkNotNull(owner.viewportChanged(checkNotNull(state.visibleWindow), 400, 700))
            owner.state.value.restoreRequest?.let { restore ->
                check(owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, restore.anchor, restore.id, restore.anchor.pageId)))
            }
            val page = checkNotNull(state.chapter).manifest.pages.single { it.ordinal == ordinal }
            check(owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, ReaderMangaAnchor(page.pageId, page.ordinal, page.resourceRef, 0.0, 0.2))))
        }
        override fun close() { owner.close(); job.cancel() }
    }

    private class WindowLibrary(pageCount: Int, private val encodedBytes: Int) : ReaderMangaLibrary {
        var progressValue = ReaderMangaProgressState(ReaderMangaProgressToken(1, 0), null)
        override suspend fun progress(owner: ReaderMangaChapterRef) = progressValue
        override suspend fun pendingProgress(): List<ReaderMangaProgressPending> = emptyList()
        override suspend fun saveProgress(request: ReaderMangaProgressSaveRequest): ReaderMangaProgressState = error("UNEXPECTED_PROGRESS_WRITE")
        override suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String): ReaderMangaProgressState = error("UNEXPECTED_PROGRESS_RECONCILE")

        private val store = ReaderMangaResourceStore(Files.createTempDirectory("reader-manga-window-").toFile())
        private val chapterIdentity = ReaderMangaChapterIdentity("source", "book", "chapter-0")
        val manifest = ReaderMangaManifest(chapterIdentity, "rules-1", "manifest-1", "decode-1",
            (0 until pageCount).map { ReaderMangaManifestPage("page-$it", it, "manga:page-$it") })
        private val resources = manifest.pages.map {
            ReaderMangaResource(it.resourceRef, ReaderMangaPage("https://img.test/${it.ordinal}.jpg"))
        }
        val windowRequests = mutableListOf<ReaderMangaPageWindowRequest>()
        val decodeRequests = mutableListOf<ReaderMangaResourcePrepareRequest>()
        val windowGates = mutableMapOf<Int, CompletableDeferred<Unit>>()
        val decodeGates = mutableMapOf<Int, CompletableDeferred<Unit>>()
        val failedWindows = mutableSetOf<Int>()
        val failedResources = mutableSetOf<Int>()
        var changeWindowManifest: (ReaderMangaManifest) -> ReaderMangaManifest = { it }
        private data class Record(val ordinal: Int, val identity: ReaderMangaTransferIdentity, val sha: String)
        private val records = mutableListOf<Record>()

        override suspend fun chapter(request: ReaderMangaChapterRequest) =
            ReaderMangaChapterData(manifest, 0, "chapter", false, resources)

        override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow {
            windowRequests += request
            windowGates[request.start]?.await()
            if (request.start in failedWindows) error("WINDOW_REQUEST_FAILED_${request.start}")
            return ReaderMangaPageWindow(changeWindowManifest(manifest), 0, "chapter", false,
                request.start, resources.size, resources.drop(request.start).take(request.limit), false)
        }

        override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest): ReaderMangaDecodedResource {
            decodeRequests += request
            val ordinal = request.resourceRef.substringAfterLast('-').toInt()
            decodeGates[ordinal]?.await()
            if (ordinal in failedResources) error("WINDOW_RESOURCE_FAILED_$ordinal")
            val identity = ReaderMangaTransferIdentity.forPrepare(request)
            val staged = store.stage(identity, listOf(byteArrayOf(ordinal.toByte(), 1, 2)), "image/jpeg")
            store.commit(staged)
            store.putDecodedByTransferId(identity.wireId, ByteArray(encodedBytes) { ordinal.toByte() })
            val lease = store.acquireDecodedLease(identity.wireId, identity.resourceRef)
            records += Record(ordinal, identity, staged.sha256)
            return ReaderMangaDecodedResource(request.chapter, request.manifestVersion, request.resourceRef, identity.wireId,
                store.readDecodedByTransferId(identity.wireId, identity.resourceRef), lease)
        }

        fun hasDecoded(ordinal: Int) = records.any { it.ordinal == ordinal }
        fun evict(ordinal: Int): Boolean {
            val record = records.last { it.ordinal == ordinal }
            return store.evictDecodedByTransferId(record.identity.wireId) && store.evict(record.identity, record.sha)
        }
    }

    private suspend fun awaitWindow(owner: ReaderMangaReadingOwner, start: Int) {
        withTimeout(5_000) {
            while (owner.state.value.pendingWindow != null || owner.state.value.window?.pageStart != start) yield()
        }
    }

    private class FakeMangaLibrary(
        private val foreignScope: Boolean = false,
        private val failAfterFirst: Boolean = false,
    ) : ReaderMangaLibrary {
        override suspend fun progress(owner: ReaderMangaChapterRef) = ReaderMangaProgressState(ReaderMangaProgressToken(1, 0), null)
        override suspend fun pendingProgress(): List<ReaderMangaProgressPending> = emptyList()
        override suspend fun saveProgress(request: ReaderMangaProgressSaveRequest): ReaderMangaProgressState = error("UNEXPECTED_PROGRESS_WRITE")
        override suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String): ReaderMangaProgressState = error("UNEXPECTED_PROGRESS_RECONCILE")

        private val root = Files.createTempDirectory("reader-manga-owner-").toFile()
        private val store = ReaderMangaResourceStore(root)
        private val chapterIdentity = ReaderMangaChapterIdentity("source", "book", "chapter-0")
        private val pageCount = if (failAfterFirst) 2 else 1
        private val resources = (0 until pageCount).map { index ->
            ReaderMangaResource("manga:page-$index", ReaderMangaPage("https://img.test/$index.jpg"))
        }
        private val manifest = ReaderMangaManifest(
            chapterIdentity, "rules-1", "manifest-1", "decode-1",
            (0 until pageCount).map { index -> ReaderMangaManifestPage("page-$index", index, "manga:page-$index") },
        )
        var decodedEntered: CompletableDeferred<Unit>? = null
        var decodedRelease: CompletableDeferred<Unit>? = null
        private var currentIdentity: ReaderMangaTransferIdentity? = null
        private var currentSha: String? = null
        private var decodedCount = 0

        override suspend fun chapter(request: ReaderMangaChapterRequest) = ReaderMangaChapterData(
            manifest, 0, "第一话", cached = false,
            resources,
        )

        override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest) = ReaderMangaPageWindow(
            manifest, 0, "第一话", cached = false, 0, pageCount,
            resources, false,
        )

        override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest): ReaderMangaDecodedResource {
            decodedEntered?.complete(Unit)
            decodedRelease?.await()
            decodedCount += 1
            if (failAfterFirst && decodedCount == 2) error("MANGA_UI_FAKE_DECODE_FAILURE")
            val resourceRef = if (foreignScope) "manga:foreign" else request.resourceRef
            val identity = ReaderMangaTransferIdentity.forPrepare(request.copy(resourceRef = resourceRef))
            val staged = store.stage(identity, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
            store.commit(staged)
            currentSha = staged.sha256
            store.putDecodedByTransferId(identity.wireId, byteArrayOf(9, 8, 7))
            currentIdentity = identity
            val lease = store.acquireDecodedLease(identity.wireId, identity.resourceRef)
            return ReaderMangaDecodedResource(
                request.chapter, request.manifestVersion, resourceRef, identity.wireId,
                store.readDecodedByTransferId(identity.wireId, identity.resourceRef), lease,
            )
        }

        fun evictCurrent(): Boolean {
            val identity = currentIdentity ?: return true
            val sha = currentSha ?: return true
            return store.evictDecodedByTransferId(identity.wireId) && store.evict(identity, sha)
        }
    }

    private suspend fun awaitPhase(owner: ReaderMangaReadingOwner, phase: ReaderMangaReadingPhase) {
        withTimeout(5_000) {
            while (owner.state.value.phase != phase) yield()
        }
    }
}
