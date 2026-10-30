package io.reader.android.ui

import io.reader.android.runtime.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

/** Real decoded leases, controlled original receipts and a narrow issued-directory port. No UI or network simulation. */
class ReaderMangaChapterTransitionTest {
    @Test fun oldOriginalAckPrecedesCandidateAndOnlyRealTargetDrawCreatesNewProgress(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); f.measure(1, 0.4)
            val oldPages = f.owner.state.value.pages
            f.library.nextObservedEpoch = 9
            assertTrue(f.navigate())
            f.presenting()
            val state = f.owner.state.value
            assertEquals(listOf("save:old", "pending", "resolve", "chapter:new", "window:new", "decode:new", "get"),
                f.library.calls.takeLast(7))
            assertEquals("old", state.lastConfirmedProgress?.chapter?.chapterId)
            assertEquals(ReaderMangaProgressToken(9, 1), state.progressToken)
            assertFalse(state.progressDirty)
            assertEquals(ReaderMangaFlushResult.BLOCKED, f.owner.flushVisibleProgress())
            assertEquals(1, f.library.saves.size)
            oldPages.forEach { assertFalse(f.library.evict(it)) }
            val ticket = f.layout()
            val restore = checkNotNull(f.owner.state.value.restoreRequest)
            assertFalse(f.owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, restore.anchor, restore.id, "foreign")))
            assertFalse(f.owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, restore.anchor)))
            f.restore(actualY = 0.23, ticket = ticket)
            assertNull(f.owner.state.value.chapterTransition)
            assertTrue(f.owner.state.value.progressDirty)
            oldPages.forEach { assertTrue(f.library.evict(it)) }
            assertEquals(ReaderMangaFlushResult.CONFIRMED, f.owner.flushVisibleProgress())
            val saved = f.library.saves.last()
            assertEquals("new", saved.location.chapter.chapterId)
            assertEquals(0.23, saved.location.y, 0.0)
            assertEquals(ReaderMangaProgressToken(9, 1), saved.expected)
            assertEquals(saved.location, f.owner.state.value.lastConfirmedProgress)
        } finally { f.close() }
    }

    @Test fun unknownOriginalStopsBeforeResolveAndNeverUsesGetToCompleteIt(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); f.measure(1, 0.4)
            val original = f.owner.state.value
            f.library.saveFailure = f.library.unknown
            assertTrue(f.navigate()); f.settled()
            assertSame(original.pages, f.owner.state.value.pages)
            assertTrue(f.owner.state.value.progressDirty)
            assertEquals("original", f.owner.state.value.progressPending.single().operationId)
            assertEquals(1, f.library.saves.size)
            assertFalse(f.library.calls.contains("resolve"))
            assertEquals(1, f.library.calls.count { it == "get" })
            assertSame(f.library.unknown, f.failures.single())
            assertEquals(ReaderMangaFlushResult.BLOCKED, f.owner.flushVisibleProgress())
        } finally { f.close() }
    }

    @Test fun transitionWaitsForInflightSaveThenFlushesLatestOnceAndFreezesFurtherInputs(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); f.measure(0, 0.2)
            val gate = CompletableDeferred<Unit>(); f.library.saveGate = gate
            val first = async(start = CoroutineStart.UNDISPATCHED) { f.owner.flushVisibleProgress() }
            f.measure(1, 0.6)
            assertTrue(f.navigate()); assertFalse(f.navigate())
            assertEquals(ReaderMangaChapterTransitionPhase.SAVING, f.owner.state.value.chapterTransition?.phase)
            assertFalse(f.measure(2, 0.9))
            f.owner.requestZoom(2f); assertEquals(1f, f.owner.state.value.zoom)
            val windows = f.library.calls.count { it.startsWith("window:") }
            f.owner.jumpToPage(2); f.owner.loadWindow(0, 2)
            assertEquals(windows, f.library.calls.count { it.startsWith("window:") })
            gate.complete(Unit)
            assertEquals(ReaderMangaFlushResult.CONFIRMED, first.await()); f.presenting()
            assertEquals(listOf(0.2, 0.6), f.library.saves.map { it.location.y })
            assertEquals(listOf(0L, 1L), f.library.saves.map { it.expected.revision })
            assertEquals(1, f.library.calls.count { it == "resolve" })
            assertEquals(1, f.library.maxSaves)
        } finally { f.close() }
    }

    @Test fun exactPreResolvedRequestSkipsDuplicateResolveAndForeignProofNeverFreezes(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open()
            val request = f.library.resolved()
            val target = checkNotNull(request.directoryTarget)
            val foreignNode = ReaderMangaDirectoryNode(f.library.directory, "another", "another", "target", 7)
            assertFalse(f.owner.navigateChapter(foreignNode, ReaderMangaChapterLanding.FIRST, request))
            assertNull(f.owner.state.value.chapterTransition)
            val sameUrl = request.copy(directoryTarget = ReaderMangaDirectoryTarget(target.directory, target.nodeId,
                target.chapterIndex, "old", target.sourceRuleVersion, "{}"))
            assertFalse(f.owner.navigateChapter(f.library.node, ReaderMangaChapterLanding.FIRST, sameUrl))
            assertTrue(f.owner.navigateChapter(f.library.node, ReaderMangaChapterLanding.FIRST, request)); f.presenting()
            assertFalse(f.library.calls.contains("resolve"))
            assertTrue(f.library.saves.isEmpty())
        } finally { f.close() }
    }

    @Test fun exitDuringOriginalSaveKeepsItsAckAndNeverDispatchesTheCancelledChapter(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); f.measure(1, 0.6)
            val old = f.owner.state.value
            val release = CompletableDeferred<Unit>(); f.library.saveGate = release
            assertTrue(f.navigate())
            assertTrue(f.owner.state.value.progressBusy)
            val generation = checkNotNull(f.owner.beginExit())
            assertEquals(old.generation, generation)
            val exit = async(start = CoroutineStart.UNDISPATCHED) { f.owner.flushVisibleProgress() }
            release.complete(Unit)
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, exit.await())
            assertEquals("old", f.owner.state.value.lastConfirmedProgress?.chapter?.chapterId)
            assertEquals(ReaderMangaProgressToken(5, 1), f.owner.state.value.progressToken)
            assertFalse(f.library.calls.contains("resolve"))
            assertEquals(1, f.library.saves.size)
            assertSame(old.pages, f.owner.state.value.pages)
        } finally { f.close() }
    }

    @Test fun newlyObservedPendingOriginalBlocksCleanChapterBeforeResolve(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); val old = f.owner.state.value
            f.library.pending = listOf(ReaderMangaProgressPending("another-client", f.library.ref, true, false))
            assertTrue(f.navigate()); f.settled()
            assertSame(old.pages, f.owner.state.value.pages)
            assertEquals(f.library.pending, f.owner.state.value.progressPending)
            assertFalse(f.library.calls.contains("resolve"))
            assertEquals(1, f.library.calls.count { it == "get" })
            assertEquals(ReaderMangaFlushResult.BLOCKED, f.owner.flushVisibleProgress())
            assertTrue(f.library.saves.isEmpty())
        } finally { f.close() }
    }

    @Test fun mismatchedChapterIdentityRulesDecodeOrIndexCannotReplaceOldPages(): Unit = runBlocking {
        val corruptions: List<(ReaderMangaChapterData) -> ReaderMangaChapterData> = listOf(
            { it.copy(manifest = it.manifest.copy(chapter = it.manifest.chapter.copy(chapterId = "foreign"))) },
            { it.copy(manifest = it.manifest.copy(sourceRuleVersion = "rules-2")) },
            { it.copy(manifest = it.manifest.copy(decodeRevision = "decode-2")) },
            { it.copy(chapterIndex = 8) },
        )
        for (corrupt in corruptions) {
            val f = Fixture()
            try {
                f.open(); val old = f.owner.state.value
                f.library.changeChapter = corrupt
                assertTrue(f.navigate()); f.settled()
                assertSame(old.pages, f.owner.state.value.pages)
                assertEquals("MANGA_CHAPTER_TARGET_SCOPE", f.owner.state.value.chapterError)
                assertFalse(f.library.calls.contains("window:new"))
                assertTrue(f.library.saves.isEmpty())
                old.pages.forEach { assertFalse(f.library.evict(it)) }
            } finally { f.close() }
        }
    }

    @Test fun candidateResourceFailureKeepsOldWindowAndDoesNotDiscardItsPosition(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); f.measure(1, 0.65)
            val old = f.owner.state.value
            f.library.decodeFailure = IllegalStateException("candidate unavailable")
            assertTrue(f.navigate()); f.settled()
            assertSame(old.pages, f.owner.state.value.pages)
            assertEquals(old.visibleWindow, f.owner.state.value.visibleWindow)
            assertEquals("candidate unavailable", f.owner.state.value.chapterError)
            assertEquals(0.65, checkNotNull(f.owner.state.value.lastConfirmedProgress).y, 0.0)
            assertFalse(f.owner.state.value.progressDirty)
            assertEquals(1, f.library.saves.size)
            old.pages.forEach { assertFalse(f.library.evict(it)) }
        } finally { f.close() }
    }

    @Test fun resizeRejectsStalePresentationFailureAndExactFailureRollsBackWithoutSavingDesiredTarget(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); f.measure(1, 0.7)
            val old = f.owner.state.value
            f.library.nextObservedEpoch = 11
            assertTrue(f.navigate()); f.presenting()
            val candidatePages = f.owner.state.value.pages
            val stale = f.layout()
            val staleRestore = checkNotNull(f.owner.state.value.restoreRequest)
            val current = f.layout(width = 720, height = 400)
            assertTrue(current.epoch > stale.epoch)
            f.owner.failChapterPresentation(stale, "late error")
            assertNotNull(f.owner.state.value.chapterTransition)
            assertFalse(f.owner.observeVisible(ReaderMangaMeasuredAnchor(stale, staleRestore.anchor,
                staleRestore.id, staleRestore.anchor.pageId)))
            f.owner.failChapterPresentation(current, "image failed")
            assertNull(f.owner.state.value.chapterTransition)
            assertEquals("image failed", f.owner.state.value.chapterError)
            assertSame(old.pages, f.owner.state.value.pages)
            assertTrue(f.owner.state.value.generation > current.window.generation)
            assertEquals(ReaderMangaProgressToken(11, 1), f.owner.state.value.progressToken)
            assertEquals("old", f.owner.state.value.lastConfirmedProgress?.chapter?.chapterId)
            candidatePages.forEach { assertTrue(f.library.evict(it)) }
            old.pages.forEach { assertFalse(f.library.evict(it)) }
            f.restore(actualY = 0.1)
            assertFalse(f.owner.state.value.progressDirty)
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, f.owner.flushVisibleProgress())
            assertEquals(1, f.library.saves.size)
        } finally { f.close() }
    }

    @Test fun changedRevisionOrInconsistentLocationCannotBeAbsorbedAsTheNextCas(): Unit = runBlocking {
        val changes: List<(ReaderMangaProgressState) -> ReaderMangaProgressState> = listOf(
            { it.copy(token = it.token.copy(revision = it.token.revision + 1),
                location = checkNotNull(it.location).copy(progressRevision = it.token.revision + 1)) },
            { it.copy(location = checkNotNull(it.location).copy(progressRevision = it.token.revision + 1)) },
            { it.copy(location = checkNotNull(it.location).copy(y = 0.99)) },
        )
        for (change in changes) {
            val f = Fixture()
            try {
                f.open(); f.measure(1, 0.3)
                val old = f.owner.state.value.pages
                f.library.changeObservation = change
                assertTrue(f.navigate()); f.settled()
                assertSame(old, f.owner.state.value.pages)
                assertEquals("MANGA_CHAPTER_PROGRESS_CHANGED", f.owner.state.value.chapterError)
                assertEquals(ReaderMangaProgressToken(5, 1), f.owner.state.value.progressToken)
                assertEquals(f.library.saves.single().location, f.owner.state.value.lastConfirmedProgress)
                assertTrue(f.library.evict(f.library.candidateHandles.single()))
            } finally { f.close() }
        }
    }

    @Test fun cancellationDuringDecodeReleasesLateCandidateAndCannotCancelNewAttempt(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); val old = f.owner.state.value
            val gate = CompletableDeferred<Unit>(); f.library.decodeGate = gate
            assertTrue(f.navigate()); f.library.decodeEntered.await()
            f.owner.cancelChapterTransition()
            assertSame(old.pages, f.owner.state.value.pages)
            assertTrue(f.navigate())
            gate.complete(Unit); f.presenting()
            assertEquals(2, f.library.candidateHandles.size)
            assertTrue(f.library.evict(f.library.candidateHandles.first()))
            assertFalse(f.library.evict(f.library.candidateHandles.last()))
            assertEquals(2, f.library.calls.count { it == "resolve" })
            assertTrue(f.library.saves.isEmpty())
        } finally { f.close() }
    }

    @Test fun oldAndSinglePageCandidateSharePeakBudgetUntilActualDraw(): Unit = runBlocking {
        val f = Fixture(encodedBudget = ReaderMangaEncodedPageBudget(16, 24, 8))
        try {
            f.library.oldBytes = 4; f.library.newBytes = 8
            f.open(); val old = f.owner.state.value.pages
            assertEquals(16, old.sumOf { it.decoded.bytes.size })
            assertTrue(f.navigate()); f.presenting()
            assertEquals(8, f.owner.state.value.pages.single().decoded.bytes.size)
            old.forEach { assertFalse(f.library.evict(it)) }
            f.restore()
            old.forEach { assertTrue(f.library.evict(it)) }
            assertNull(f.owner.state.value.chapterTransition)
        } finally { f.close() }
    }

    @Test fun oversizedCandidateReleasesItsHandleAndKeepsFullOldBudgetWindow(): Unit = runBlocking {
        val f = Fixture(encodedBudget = ReaderMangaEncodedPageBudget(16, 24, 8))
        try {
            f.library.oldBytes = 4; f.library.newBytes = 9
            f.open(); val old = f.owner.state.value.pages
            assertTrue(f.navigate()); f.settled()
            assertEquals(ReaderMangaReadingOwner.ENCODED_BUDGET_ERROR, f.owner.state.value.chapterError)
            assertSame(old, f.owner.state.value.pages)
            assertTrue(f.library.evict(f.library.candidateHandles.single()))
            old.forEach { assertFalse(f.library.evict(it)) }
        } finally { f.close() }
    }

    @Test fun beginExitRollsBackUndrawnCandidateAndReturnsTheRestoredGeneration(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open(); val old = f.owner.state.value.pages
            assertTrue(f.navigate()); f.presenting()
            val candidateTicket = f.layout()
            val restore = checkNotNull(f.owner.state.value.restoreRequest)
            val exiting = checkNotNull(f.owner.beginExit())
            assertTrue(exiting > candidateTicket.window.generation)
            assertEquals(exiting, f.owner.state.value.generation)
            assertTrue(f.owner.state.value.exitPending)
            assertSame(old, f.owner.state.value.pages)
            assertFalse(f.owner.observeVisible(ReaderMangaMeasuredAnchor(candidateTicket, restore.anchor, restore.id, restore.anchor.pageId)))
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, f.owner.flushVisibleProgress())
            assertTrue(f.library.saves.isEmpty())
        } finally { f.close() }
    }

    @Test fun presentationTimeoutRestoresOldChapterAndDoesNotManufactureAWrite(): Unit = runBlocking {
        val f = Fixture(timeout = 20)
        try {
            f.open(); val old = f.owner.state.value.pages
            assertTrue(f.navigate()); f.settled()
            assertSame(old, f.owner.state.value.pages)
            assertEquals("MANGA_CHAPTER_PRESENTATION_TIMEOUT", f.owner.state.value.chapterError)
            assertTrue(f.library.saves.isEmpty())
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, f.owner.flushVisibleProgress())
        } finally { f.close() }
    }

    @Test fun previousChapterLastLandingSavesActualClampedMeasurementOnly(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.open()
            assertTrue(f.navigate(ReaderMangaChapterLanding.LAST)); f.presenting()
            val restore = checkNotNull(f.owner.state.value.restoreRequest)
            assertEquals(2, restore.anchor.ordinal); assertEquals(1.0, restore.anchor.y, 0.0)
            assertEquals(listOf(2), f.owner.state.value.pages.map { it.ordinal })
            assertTrue(f.library.saves.isEmpty())
            f.restore(actualY = 0.0)
            assertEquals(ReaderMangaFlushResult.CONFIRMED, f.owner.flushVisibleProgress())
            assertEquals(2, f.library.saves.single().location.pageOrdinalFallback)
            assertEquals(0.0, f.library.saves.single().location.y, 0.0)
        } finally { f.close() }
    }

    private class Fixture(encodedBudget: ReaderMangaEncodedPageBudget = ReaderMangaEncodedPageBudget(), timeout: Long = 15_000) : AutoCloseable {
        val library = Library()
        val failures = mutableListOf<Throwable>()
        private val job = SupervisorJob()
        val owner = ReaderMangaReadingOwner(CoroutineScope(job + Dispatchers.Unconfined), onFailure = { failures += it },
            encodedBudget = encodedBudget, chapterPresentationTimeoutMillis = timeout) { library }
        suspend fun open() {
            owner.open(ReaderMangaChapterRequest(library.ref, 0))
            withTimeout(5_000) { while (owner.state.value.phase == ReaderMangaReadingPhase.LOADING) yield() }
            assertEquals(ReaderMangaReadingPhase.READY, owner.state.value.phase)
            restore()
        }
        fun navigate(landing: ReaderMangaChapterLanding = ReaderMangaChapterLanding.FIRST) = owner.navigateChapter(library.node, landing)
        suspend fun presenting() {
            withTimeout(5_000) { while (owner.state.value.chapterTransition?.phase != ReaderMangaChapterTransitionPhase.PRESENTING) {
                check(owner.state.value.chapterTransition != null) { "Transition failed: ${owner.state.value.chapterError}" }; yield()
            } }
        }
        suspend fun settled() { withTimeout(5_000) { while (owner.state.value.chapterTransition != null) yield() } }
        fun layout(width: Int = 400, height: Int = 700): ReaderMangaLayoutTicket =
            checkNotNull(owner.viewportChanged(checkNotNull(owner.state.value.visibleWindow), width, height))
        fun restore(actualY: Double? = null, ticket: ReaderMangaLayoutTicket = layout()) {
            val restore = checkNotNull(owner.state.value.restoreRequest)
            val anchor = restore.anchor.copy(y = actualY ?: restore.anchor.y)
            assertTrue(owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, anchor, restore.id, restore.anchor.pageId)))
        }
        fun measure(ordinal: Int, y: Double): Boolean {
            val page = checkNotNull(owner.state.value.chapter).manifest.pages.single { it.ordinal == ordinal }
            return owner.observeVisible(ReaderMangaMeasuredAnchor(checkNotNull(owner.state.value.layoutTicket),
                ReaderMangaAnchor(page.pageId, ordinal, page.resourceRef, 0.0, y)))
        }
        override fun close() { owner.close(); job.cancel() }
    }

    private class Library : ReaderMangaLibrary {
        val ref = ReaderMangaChapterRef("source", "book")
        private val lease = ReaderOperationOwner.ClientLease(UUID.randomUUID(), UUID.randomUUID(), 1, 1)
        val directory = ReaderMangaDirectory("directory", ref, "rules", "view", "catalog", "structure", Any(), lease, "runtime")
        val node = ReaderMangaDirectoryNode(directory, "next", "Next", "target", 7)
        private fun manifest(id: String, count: Int) = ReaderMangaManifest(ReaderMangaChapterIdentity(ref.sourceId, ref.bookId, id),
            "rules", "manifest-$id", "decode", (0 until count).map { ReaderMangaManifestPage("$id-page-$it", it, "manga:$id-$it") })
        private val old = manifest("old", 4)
        private val next = manifest("new", 3)
        private fun resources(manifest: ReaderMangaManifest) = manifest.pages.map {
            ReaderMangaResource(it.resourceRef, ReaderMangaPage("https://image.test/${it.resourceRef}.jpg"))
        }
        private fun chapter(manifest: ReaderMangaManifest) = ReaderMangaChapterData(manifest,
            if (manifest === old) 0 else 7, manifest.chapter.chapterId, true, resources(manifest))
        private val store = ReaderMangaResourceStore(Files.createTempDirectory("reader-manga-transition-").toFile())
        val calls = mutableListOf<String>()
        val saves = mutableListOf<ReaderMangaProgressSaveRequest>()
        val candidateHandles = mutableListOf<ReaderMangaReadingPage>()
        var observed = ReaderMangaProgressState(ReaderMangaProgressToken(5, 0), null)
        var nextObservedEpoch: Long? = null
        var pending: List<ReaderMangaProgressPending> = emptyList()
        var saveFailure: Throwable? = null
        var decodeFailure: Throwable? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var decodeGate: CompletableDeferred<Unit>? = null
        val decodeEntered = CompletableDeferred<Unit>()
        var oldBytes = 3
        var newBytes = 3
        var maxSaves = 0
        private var activeSaves = 0
        var changeChapter: (ReaderMangaChapterData) -> ReaderMangaChapterData = { it }
        var changeObservation: (ReaderMangaProgressState) -> ReaderMangaProgressState = { it }
        val unknown = CoreProtocolPending(CoreProtocolReference("runtime", "client", 41, "ack"), CoreProtocolStage.ORIGINAL_UNCERTAIN)
        fun resolved() = ReaderMangaChapterRequest(ref, 7, directoryTarget = ReaderMangaDirectoryTarget(directory,
            node.nodeId, 7, "new", "rules", "{}"))
        override suspend fun resolveChapter(node: ReaderMangaDirectoryNode): ReaderMangaChapterRequest {
            assertSame(this.node, node); calls += "resolve"; return resolved()
        }
        override suspend fun progress(owner: ReaderMangaChapterRef): ReaderMangaProgressState {
            assertEquals(ref, owner); calls += "get"
            nextObservedEpoch?.let { observed = observed.copy(token = observed.token.copy(epoch = it)); nextObservedEpoch = null }
            return changeObservation(observed)
        }
        override suspend fun pendingProgress(): List<ReaderMangaProgressPending> { calls += "pending"; return pending }
        override suspend fun saveProgress(request: ReaderMangaProgressSaveRequest): ReaderMangaProgressState {
            calls += "save:${request.location.chapter.chapterId}"; saves += request
            activeSaves++; maxSaves = maxOf(maxSaves, activeSaves)
            try {
                val gate = saveGate; saveGate = null; gate?.await()
                saveFailure?.let { pending = listOf(ReaderMangaProgressPending("original", ref, false, false)); throw it }
                assertEquals(observed.token, request.expected)
                observed = ReaderMangaProgressState(request.expected.copy(revision = request.location.progressRevision), request.location)
                return observed
            } finally { activeSaves-- }
        }
        override suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String): ReaderMangaProgressState = throw unknown
        override suspend fun chapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData {
            calls += if (request.chapterIndex == 0) "chapter:old" else "chapter:new"
            return if (request.chapterIndex == 0) chapter(old) else changeChapter(chapter(next))
        }
        override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow {
            val manifest = if (request.chapter.chapterId == "old") old else next
            calls += "window:${manifest.chapter.chapterId}"
            return ReaderMangaPageWindow(manifest, if (manifest === old) 0 else 7, manifest.chapter.chapterId, true,
                request.start, manifest.pages.size, resources(manifest).drop(request.start).take(request.limit), false)
        }
        override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest): ReaderMangaDecodedResource {
            val isNew = request.chapter.chapterId == "new"
            calls += "decode:${request.chapter.chapterId}"
            if (isNew) {
                val gate = decodeGate; decodeGate = null; decodeEntered.complete(Unit); gate?.await()
                decodeFailure?.let { throw it }
            }
            val transfer = ReaderMangaTransferIdentity.forPrepare(request)
            val staged = store.stage(transfer, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
            store.commit(staged); store.putDecodedByTransferId(transfer.wireId, ByteArray(if (isNew) newBytes else oldBytes) { 3 })
            val decoded = ReaderMangaDecodedResource(request.chapter, request.manifestVersion, request.resourceRef, transfer.wireId,
                store.readDecodedByTransferId(transfer.wireId, transfer.resourceRef), store.acquireDecodedLease(transfer.wireId, transfer.resourceRef))
            if (isNew) {
                val ordinal = next.pages.single { it.resourceRef == request.resourceRef }.ordinal
                candidateHandles += ReaderMangaReadingPage(ordinal, resources(next).single { it.resourceRef == request.resourceRef }, decoded)
            }
            return decoded
        }
        fun evict(page: ReaderMangaReadingPage) = store.evictDecodedByTransferId(page.decoded.transferId)
    }
}
