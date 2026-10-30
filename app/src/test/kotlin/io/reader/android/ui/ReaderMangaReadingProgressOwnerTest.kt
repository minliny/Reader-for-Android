package io.reader.android.ui

import io.reader.android.runtime.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Owner lifecycle tests use real decoded leases and a controlled original-receipt Library port. */
class ReaderMangaReadingProgressOwnerTest {
    @Test fun openingObservationAndClampedRestoreNeverWriteOrClaimAnOriginalAck(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.library.observed = fixture.library.saved(3, 0.8, 7)
            fixture.open()
            assertEquals(listOf("pending", "get", "chapter", "window"), fixture.library.calls.take(4))
            assertEquals(2, fixture.owner.state.value.window?.pageStart)
            assertNull(fixture.owner.state.value.lastConfirmedProgress)
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, fixture.owner.flushVisibleProgress())
            val ticket = fixture.layout()
            val restore = checkNotNull(fixture.owner.state.value.restoreRequest)
            assertEquals(3, restore.anchor.ordinal)
            // A short final target is drawn, while native end clamping places the preceding page at the top.
            assertTrue(fixture.owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, fixture.anchor(2, 0.4), restore.id, restore.anchor.pageId)))
            assertEquals(2, fixture.owner.state.value.currentOrdinal)
            assertFalse(fixture.owner.state.value.progressDirty)
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, fixture.owner.flushVisibleProgress())
            assertTrue(fixture.library.saves.isEmpty()); assertNull(fixture.owner.state.value.lastConfirmedProgress)
        } finally { fixture.close() }
    }

    @Test fun onlyAcceptedIdleUserMeasurementSavesExactImageLocationAndCas(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore()
            val anchor = fixture.anchor(1, 0.37, 0.2)
            assertTrue(fixture.measure(anchor))
            assertEquals(ReaderMangaFlushResult.CONFIRMED, fixture.owner.flushVisibleProgress())
            val request = fixture.library.saves.single()
            assertEquals(ReaderMangaProgressToken(5, 0), request.expected)
            assertEquals(fixture.library.manifest, request.manifest)
            assertEquals(fixture.library.location(anchor, 1), request.location)
            assertEquals(request.location, fixture.owner.state.value.lastConfirmedProgress)
            assertFalse(fixture.owner.state.value.progressDirty)
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, fixture.owner.flushVisibleProgress())
            assertEquals(1, fixture.library.getCount)
        } finally { fixture.close() }
    }

    @Test fun oldLayoutForeignPageAndWrongRestoreProofCannotCreateProgress(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open()
            val old = fixture.layout()
            val restore = checkNotNull(fixture.owner.state.value.restoreRequest)
            assertFalse(fixture.owner.observeVisible(ReaderMangaMeasuredAnchor(old, restore.anchor, restore.id + 1, restore.anchor.pageId)))
            assertFalse(fixture.owner.observeVisible(ReaderMangaMeasuredAnchor(old, restore.anchor, restore.id, "foreign")))
            assertFalse(fixture.owner.observeVisible(ReaderMangaMeasuredAnchor(old, restore.anchor.copy(resourceRef = "foreign"), restore.id, restore.anchor.pageId)))
            fixture.owner.requestZoom(2f)
            assertNull(fixture.owner.state.value.layoutTicket); assertNull(fixture.owner.state.value.visibleAnchor)
            assertFalse(fixture.owner.observeVisible(ReaderMangaMeasuredAnchor(old, restore.anchor, restore.id, restore.anchor.pageId)))
            val next = fixture.layout()
            assertTrue(next.epoch > old.epoch)
            fixture.restore(ticket = next)
            assertFalse(fixture.measure(fixture.anchor(0, Double.NaN)))
            assertFalse(fixture.measure(fixture.anchor(0, 0.2).copy(pageId = fixture.anchor(1, 0.2).pageId)))
            assertFalse(fixture.owner.state.value.progressDirty)
            assertTrue(fixture.library.saves.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun zoomResizeAndOverlapRestorePreserveTheEarlierGenuineDirtySnapshot(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore()
            val genuine = fixture.anchor(2, 0.65)
            assertTrue(fixture.measure(genuine))
            val old = checkNotNull(fixture.owner.state.value.layoutTicket)
            fixture.owner.requestZoom(2f)
            val zoomed = fixture.layout(width = 620, height = 430)
            assertTrue(zoomed.epoch > old.epoch)
            fixture.restore(actual = fixture.anchor(2, 0.2), ticket = zoomed)
            assertTrue(fixture.owner.state.value.progressDirty)
            assertEquals(ReaderMangaFlushResult.CONFIRMED, fixture.owner.flushVisibleProgress())
            assertEquals(genuine.y, fixture.library.saves.single().location.y, 0.0)
            assertEquals(0.2, checkNotNull(fixture.owner.state.value.visibleAnchor).y, 0.0)
            fixture.owner.loadWindow(1, 3)
            fixture.ready()
            assertEquals(2, checkNotNull(fixture.owner.state.value.restoreRequest).anchor.ordinal)
            fixture.restore(actual = fixture.anchor(2, 0.1))
            assertFalse(fixture.owner.state.value.progressDirty)
            assertEquals(ReaderMangaFlushResult.NO_CHANGE, fixture.owner.flushVisibleProgress())
            assertEquals(1, fixture.library.saves.size)
        } finally { fixture.close() }
    }

    @Test fun exitWaitsForOneInflightOriginalThenSavesLatestOnceAndRejectsLateAnchors(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore(); fixture.measure(fixture.anchor(0, 0.3))
            val release = CompletableDeferred<Unit>()
            fixture.library.nextSaveGate = release
            val first = async(start = CoroutineStart.UNDISPATCHED) { fixture.owner.flushVisibleProgress() }
            assertTrue(fixture.owner.state.value.progressBusy)
            assertTrue(fixture.measure(fixture.anchor(1, 0.8)))
            val generation = checkNotNull(fixture.owner.beginExit())
            assertNull(fixture.owner.beginExit()) // Repeated back cannot enqueue another exit intent.
            assertFalse(fixture.measure(fixture.anchor(2, 0.9)))
            val exit = async(start = CoroutineStart.UNDISPATCHED) { fixture.owner.flushVisibleProgress() }
            assertEquals(1, fixture.library.saves.size)
            release.complete(Unit)
            assertEquals(ReaderMangaFlushResult.CONFIRMED, first.await())
            assertEquals(ReaderMangaFlushResult.CONFIRMED, exit.await())
            assertEquals(listOf(0.3, 0.8), fixture.library.saves.map { it.location.y })
            assertEquals(listOf(0L, 1L), fixture.library.saves.map { it.expected.revision })
            assertEquals(1, fixture.library.maxActiveSaves)
            assertFalse(fixture.owner.state.value.progressDirty)
            assertTrue(fixture.owner.state.value.exitPending)
            fixture.owner.cancelExit(generation + 1)
            assertTrue(fixture.owner.state.value.exitPending)
            fixture.owner.cancelExit(generation)
            assertFalse(fixture.owner.state.value.exitPending)
        } finally { fixture.close() }
    }

    @Test fun unknownOriginalBlocksReplayAndOnlyExactReconcileAdvancesCas(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore(); fixture.measure(fixture.anchor(1, 0.4))
            fixture.library.failSave = fixture.library.unknown
            assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
            assertSame(fixture.library.unknown, fixture.failures.single())
            assertTrue(fixture.owner.state.value.progressDirty)
            assertNull(fixture.owner.state.value.lastConfirmedProgress)
            val id = fixture.owner.state.value.progressPending.single().operationId
            fixture.library.observed = fixture.library.saved(1, 0.4, 1) // Matching read data cannot resolve the original.
            assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
            assertFalse(fixture.owner.reconcileProgress("foreign-original"))
            assertFalse(fixture.owner.reconcileProgress(id))
            assertEquals(1, fixture.library.saves.size); assertEquals(1, fixture.library.getCount)
            fixture.library.canReconcile = true
            assertTrue(fixture.owner.reconcileProgress(id))
            assertEquals(listOf(id, id), fixture.library.reconciled)
            assertEquals(ReaderMangaProgressToken(5, 1), fixture.owner.state.value.progressToken)
            assertFalse(fixture.owner.state.value.progressDirty)
            assertTrue(fixture.owner.state.value.progressPending.isEmpty())
            assertNotNull(fixture.owner.state.value.lastConfirmedProgress)
            assertEquals(1, fixture.library.saves.size); assertEquals(1, fixture.library.getCount)
        } finally { fixture.close() }
    }

    @Test fun pendingQueryFailureAndMalformedAckNeverAuthorizeAutomaticResave(): Unit = runBlocking {
        for (queryFails in listOf(false, true)) {
            val fixture = Fixture()
            try {
                fixture.open(); fixture.restore(); fixture.measure(fixture.anchor(1, 0.4))
                if (queryFails) {
                    fixture.library.failSave = fixture.library.unknown
                    fixture.library.failPending = IllegalStateException("JOURNAL_READ_FAILED")
                } else fixture.library.changeAck = { it.copy(token = it.token.copy(epoch = 99)) }
                assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
                assertNull(fixture.owner.state.value.lastConfirmedProgress)
                assertEquals(ReaderMangaProgressToken(5, 0), fixture.owner.state.value.progressToken)
                assertTrue(fixture.owner.state.value.progressDirty)
                assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
                assertEquals(1, fixture.library.saves.size)
            } finally { fixture.close() }
        }
    }

    @Test fun unavailableSavedChapterManifestOrPageBlocksRestoreButAllowsUnchangedExit(): Unit = runBlocking {
        for (change in listOf<(ReaderMangaLocation) -> ReaderMangaLocation>(
            { it.copy(chapter = it.chapter.copy(chapterId = "removed-chapter")) },
            { it.copy(manifestVersion = "old-manifest") },
            { it.copy(pageId = "removed-page") },
        )) {
            val fixture = Fixture()
            try {
                val saved = fixture.library.saved(2, 0.4, 2)
                fixture.library.observed = saved.copy(location = change(checkNotNull(saved.location)))
                fixture.open(expect = ReaderMangaReadingPhase.ERROR)
                assertNotNull(fixture.owner.state.value.restoreBlocked)
                assertTrue(fixture.owner.state.value.pages.isEmpty()); assertFalse(fixture.library.calls.contains("window"))
                assertNotNull(fixture.owner.beginExit())
                assertEquals(ReaderMangaFlushResult.NO_CHANGE, fixture.owner.flushVisibleProgress())
                assertTrue(fixture.library.saves.isEmpty()); assertNull(fixture.owner.state.value.lastConfirmedProgress)
            } finally { fixture.close() }
        }
    }

    @Test fun preexistingOriginalBlocksChapterDispatchAndExitWithoutReadAsAck(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.library.pending = listOf(ReaderMangaProgressPending("old-original", fixture.library.ref, false, true))
            fixture.open(expect = ReaderMangaReadingPhase.ERROR)
            assertEquals(listOf("pending", "get"), fixture.library.calls)
            assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
            assertNull(fixture.owner.state.value.lastConfirmedProgress)
            assertTrue(fixture.library.saves.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun retiredGenerationRejectsLateAckAndOldLayoutWithoutReopening(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore(); fixture.measure(fixture.anchor(1, 0.5))
            val ticket = checkNotNull(fixture.owner.state.value.layoutTicket)
            val release = CompletableDeferred<Unit>()
            fixture.library.nextSaveGate = release
            val save = async(start = CoroutineStart.UNDISPATCHED) { fixture.owner.flushVisibleProgress() }
            fixture.owner.close()
            release.complete(Unit)
            assertEquals(ReaderMangaFlushResult.BLOCKED, save.await())
            assertEquals(ReaderMangaReadingPhase.CLOSED, fixture.owner.state.value.phase)
            assertNull(fixture.owner.state.value.progressToken); assertNull(fixture.owner.state.value.lastConfirmedProgress)
            assertFalse(fixture.owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, fixture.anchor(1, 0.9))))
        } finally { fixture.close() }
    }

    @Test fun exactRejectedOriginalAllowsOnlyExplicitFrozenDiscardWithoutClaimingASave(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore(); fixture.measure(fixture.anchor(0, 0.2))
            assertEquals(ReaderMangaFlushResult.CONFIRMED, fixture.owner.flushVisibleProgress())
            val confirmed = fixture.owner.state.value.lastConfirmedProgress
            val token = fixture.owner.state.value.progressToken
            val rejectedAnchor = fixture.anchor(1, 0.4)
            fixture.measure(rejectedAnchor)
            val rejection = fixture.library.rejection(rejectedAnchor, 1)
            fixture.library.failSave = rejection
            assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
            assertSame(rejection, fixture.owner.state.value.knownRejection)
            assertTrue(fixture.owner.state.value.progressPending.isEmpty())
            fixture.measure(fixture.anchor(2, 0.7)) // The explicit action discards this current unsaved position too.
            assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
            assertFalse(fixture.owner.discardRejectedProgress(rejection.operationId)) // Must freeze exit first.
            assertNotNull(fixture.owner.beginExit())
            assertFalse(fixture.owner.discardRejectedProgress("foreign-original"))
            assertTrue(fixture.owner.discardRejectedProgress(rejection.operationId))
            assertFalse(fixture.owner.state.value.progressDirty)
            assertNull(fixture.owner.state.value.knownRejection)
            assertEquals(confirmed, fixture.owner.state.value.lastConfirmedProgress)
            assertEquals(token, fixture.owner.state.value.progressToken)
            assertEquals(ReaderMangaReadingPhase.READY, fixture.owner.state.value.phase) // Root owns navigation.
            assertEquals(2, fixture.library.saves.size); assertEquals(1, fixture.library.getCount)
        } finally { fixture.close() }
    }

    @Test fun genericErrorsEmptyPendingAndForeignRejectedBindingsNeverAuthorizeDiscard(): Unit = runBlocking {
        for (kind in 0..4) {
            val fixture = Fixture()
            try {
                fixture.open(); fixture.restore()
                val anchor = fixture.anchor(1, 0.4)
                fixture.measure(anchor)
                fixture.library.createPendingOnFailure = false
                fixture.library.failSave = when (kind) {
                    0 -> CoreCommandException("reading.progress.update", org.json.JSONObject().put("code", "INVALID_PARAMS"))
                    1 -> fixture.library.unknown
                    2 -> fixture.library.rejection(anchor.copy(y = 0.9), 0)
                    3 -> fixture.library.rejection(anchor, 8)
                    else -> ReaderMangaProgressRejected("original-progress", ReaderMangaChapterRef("foreign", "book"),
                        fixture.library.location(anchor, 1), ReaderMangaProgressToken(5, 0), "INVALID_PARAMS")
                }
                assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
                assertTrue(fixture.owner.state.value.progressPending.isEmpty())
                assertNull(fixture.owner.state.value.knownRejection)
                assertNotNull(fixture.owner.beginExit())
                assertFalse(fixture.owner.discardRejectedProgress("original-progress"))
                assertTrue(fixture.owner.state.value.progressDirty)
                assertEquals(1, fixture.library.saves.size)
            } finally { fixture.close() }
        }
    }

    @Test fun rejectedDiscardRechecksPendingAndRejectsRetiredGeneration(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore()
            val anchor = fixture.anchor(1, 0.4)
            fixture.measure(anchor)
            val rejection = fixture.library.rejection(anchor, 0)
            fixture.library.failSave = rejection
            assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
            val generation = checkNotNull(fixture.owner.beginExit())
            fixture.library.pending = listOf(ReaderMangaProgressPending("new-unknown", fixture.library.ref, false, false))
            assertFalse(fixture.owner.discardRejectedProgress(rejection.operationId))
            assertTrue(fixture.owner.state.value.progressDirty)
            assertEquals("new-unknown", fixture.owner.state.value.progressPending.single().operationId)
            fixture.library.pending = emptyList()
            fixture.library.failPending = IllegalStateException("JOURNAL_QUERY_FAILED")
            assertFalse(fixture.owner.discardRejectedProgress(rejection.operationId))
            assertSame(rejection, fixture.owner.state.value.knownRejection)
            assertTrue(fixture.owner.state.value.progressDirty)
            fixture.owner.cancelExit(generation)
            fixture.library.failPending = null
            fixture.owner.retire(); fixture.open(); fixture.restore(); fixture.measure(fixture.anchor(2, 0.5))
            assertNotNull(fixture.owner.beginExit())
            assertFalse(fixture.owner.discardRejectedProgress(rejection.operationId))
            assertTrue(fixture.owner.state.value.progressDirty)
            assertEquals(1, fixture.library.saves.size)
        } finally { fixture.close() }
    }

    @Test fun exactReconcileMayReportDefiniteRejectionWithoutInventingSuccessfulProgress(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.open(); fixture.restore()
            val anchor = fixture.anchor(1, 0.4)
            fixture.measure(anchor); fixture.library.failSave = fixture.library.unknown
            assertEquals(ReaderMangaFlushResult.BLOCKED, fixture.owner.flushVisibleProgress())
            val rejection = fixture.library.rejection(anchor, 0)
            fixture.library.canReconcile = true; fixture.library.reconcileFailure = rejection
            assertFalse(fixture.owner.reconcileProgress(rejection.operationId))
            assertSame(rejection, fixture.owner.state.value.knownRejection)
            assertTrue(fixture.owner.state.value.progressPending.isEmpty())
            assertNull(fixture.owner.state.value.lastConfirmedProgress)
            assertEquals(ReaderMangaProgressToken(5, 0), fixture.owner.state.value.progressToken)
            assertNotNull(fixture.owner.beginExit())
            assertTrue(fixture.owner.discardRejectedProgress(rejection.operationId))
            assertEquals(1, fixture.library.saves.size); assertEquals(1, fixture.library.getCount)
        } finally { fixture.close() }
    }

    private class Fixture : AutoCloseable {
        val library = Library()
        val failures = mutableListOf<Throwable>()
        private val job = SupervisorJob()
        val owner = ReaderMangaReadingOwner(CoroutineScope(job + Dispatchers.Unconfined), { failures += it }) { library }
        suspend fun open(expect: ReaderMangaReadingPhase = ReaderMangaReadingPhase.READY) {
            owner.open(ReaderMangaChapterRequest(library.ref, 0))
            withTimeout(5_000) { while (owner.state.value.phase == ReaderMangaReadingPhase.LOADING) yield() }
            assertEquals(expect, owner.state.value.phase)
        }
        suspend fun ready() { withTimeout(5_000) { while (owner.state.value.pendingWindow != null) yield() } }
        fun anchor(ordinal: Int, y: Double, x: Double = 0.0): ReaderMangaAnchor {
            val page = library.manifest.pages.single { it.ordinal == ordinal }
            return ReaderMangaAnchor(page.pageId, ordinal, page.resourceRef, x, y)
        }
        fun layout(width: Int = 400, height: Int = 700): ReaderMangaLayoutTicket =
            checkNotNull(owner.viewportChanged(checkNotNull(owner.state.value.visibleWindow), width, height))
        fun restore(actual: ReaderMangaAnchor? = null, ticket: ReaderMangaLayoutTicket = layout()) {
            val restore = checkNotNull(owner.state.value.restoreRequest)
            check(owner.observeVisible(ReaderMangaMeasuredAnchor(ticket, actual ?: restore.anchor, restore.id, restore.anchor.pageId)))
        }
        fun measure(anchor: ReaderMangaAnchor): Boolean = owner.observeVisible(
            ReaderMangaMeasuredAnchor(checkNotNull(owner.state.value.layoutTicket), anchor))
        override fun close() { owner.close(); job.cancel() }
    }

    private class Library : ReaderMangaLibrary {
        val ref = ReaderMangaChapterRef("source", "book")
        private val identity = ReaderMangaChapterIdentity(ref.sourceId, ref.bookId, "chapter-0")
        val manifest = ReaderMangaManifest(identity, "rules-1", "manifest-1", "decode-1",
            (0..3).map { ReaderMangaManifestPage("mp1:" + it.toString(16).padStart(64, '0'), it, "manga:page-$it") })
        private val resources = manifest.pages.map { ReaderMangaResource(it.resourceRef, ReaderMangaPage("https://img.test/${it.ordinal}.jpg")) }
        private val store = ReaderMangaResourceStore(Files.createTempDirectory("reader-manga-progress-owner-").toFile())
        val calls = mutableListOf<String>()
        val saves = mutableListOf<ReaderMangaProgressSaveRequest>()
        val reconciled = mutableListOf<String>()
        var getCount = 0
        var observed = ReaderMangaProgressState(ReaderMangaProgressToken(5, 0), null)
        var pending: List<ReaderMangaProgressPending> = emptyList()
        var nextSaveGate: CompletableDeferred<Unit>? = null
        var failSave: Throwable? = null
        var createPendingOnFailure = true
        var failPending: Throwable? = null
        var canReconcile = false
        var reconcileFailure: ReaderMangaProgressRejected? = null
        var activeSaves = 0
        var maxActiveSaves = 0
        var changeAck: (ReaderMangaProgressState) -> ReaderMangaProgressState = { it }
        val unknown = CoreProtocolPending(CoreProtocolReference("runtime", "original", 41L, "token"), CoreProtocolStage.ORIGINAL_UNCERTAIN)
        fun location(anchor: ReaderMangaAnchor, revision: Long) = ReaderMangaLocation(identity, manifest.manifestVersion,
            anchor.pageId, anchor.ordinal, anchor.x, anchor.y, revision)
        fun rejection(anchor: ReaderMangaAnchor, expectedRevision: Long) = ReaderMangaProgressRejected("original-progress", ref,
            location(anchor, expectedRevision + 1), ReaderMangaProgressToken(5, expectedRevision), "INVALID_PARAMS")
        fun saved(ordinal: Int, y: Double, revision: Long): ReaderMangaProgressState {
            val page = manifest.pages.single { it.ordinal == ordinal }
            return ReaderMangaProgressState(ReaderMangaProgressToken(5, revision),
                location(ReaderMangaAnchor(page.pageId, ordinal, page.resourceRef, 0.0, y), revision))
        }
        override suspend fun progress(owner: ReaderMangaChapterRef): ReaderMangaProgressState {
            assertEquals(ref, owner); calls += "get"; getCount++; return observed
        }
        override suspend fun pendingProgress(): List<ReaderMangaProgressPending> {
            calls += "pending"; failPending?.let { throw it }; return pending.toList()
        }
        override suspend fun saveProgress(request: ReaderMangaProgressSaveRequest): ReaderMangaProgressState {
            check(pending.isEmpty()); saves += request; calls += "save"
            activeSaves++; maxActiveSaves = maxOf(maxActiveSaves, activeSaves)
            try {
                val gate = nextSaveGate; nextSaveGate = null; gate?.await()
                failSave?.let {
                    if (createPendingOnFailure && it !is ReaderMangaProgressRejected)
                        pending = listOf(ReaderMangaProgressPending("original-progress", ref, false, false))
                    throw it
                }
                return changeAck(ReaderMangaProgressState(ReaderMangaProgressToken(request.expected.epoch,
                    request.location.progressRevision), request.location))
            } finally { activeSaves-- }
        }
        override suspend fun reconcileProgress(owner: ReaderMangaChapterRef, operationId: String): ReaderMangaProgressState {
            assertEquals(ref, owner); assertEquals("original-progress", operationId)
            reconciled += operationId; if (!canReconcile) throw unknown
            pending = emptyList()
            reconcileFailure?.let { throw it }
            val request = saves.single()
            return ReaderMangaProgressState(ReaderMangaProgressToken(request.expected.epoch, request.location.progressRevision), request.location)
        }
        override suspend fun chapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData {
            calls += "chapter"; return ReaderMangaChapterData(manifest, 0, "chapter", false, resources)
        }
        override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow {
            calls += "window"
            return ReaderMangaPageWindow(manifest, 0, "chapter", false, request.start, resources.size,
                resources.drop(request.start).take(request.limit), false)
        }
        override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest): ReaderMangaDecodedResource {
            val transfer = ReaderMangaTransferIdentity.forPrepare(request)
            val staged = store.stage(transfer, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
            store.commit(staged); store.putDecodedByTransferId(transfer.wireId, byteArrayOf(4, 5, 6))
            return ReaderMangaDecodedResource(request.chapter, request.manifestVersion, request.resourceRef, transfer.wireId,
                store.readDecodedByTransferId(transfer.wireId, transfer.resourceRef), store.acquireDecodedLease(transfer.wireId, transfer.resourceRef))
        }
    }
}
