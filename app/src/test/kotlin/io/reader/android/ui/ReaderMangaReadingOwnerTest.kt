package io.reader.android.ui

import io.reader.android.runtime.ReaderMangaChapterData
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
            fixture.owner.setCurrentOrdinal(5)
            val release = CompletableDeferred<Unit>()
            fixture.library.windowGates[2] = release
            fixture.owner.loadWindow(2, 2)
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
            fixture.owner.loadWindow(2, 2)
            fixture.owner.loadWindow(4, 2)
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
            fixture.owner.setCurrentOrdinal(5)
            val original = fixture.owner.state.value
            fixture.library.failedResources += 9
            fixture.owner.loadWindow(8, 2)
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
        } finally { fixture.close() }
    }

    @Test
    fun duplicateInFlightWindowDispatchesOnlyOnce(): Unit = runBlocking {
        val fixture = WindowFixture()
        try {
            fixture.open()
            val release = CompletableDeferred<Unit>()
            fixture.library.windowGates[8] = release
            fixture.owner.loadWindow(8, 2)
            val first = fixture.owner.state.value.pendingWindow
            fixture.owner.loadWindow(8, 2)
            assertEquals(first, fixture.owner.state.value.pendingWindow)
            assertEquals(listOf(0, 8), fixture.library.windowRequests.map { it.start })
            release.complete(Unit)
            awaitWindow(fixture.owner, 8)
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
            fixture.owner.loadWindow(8, 1)
            assertTrue(fixture.library.decodeRequests.any { it.resourceRef == "manga:page-8" })
            fixture.owner.loadWindow(9, 1)
            awaitWindow(fixture.owner, 9)
            val published = fixture.owner.state.value
            release.complete(Unit)
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
                fixture.owner.loadWindow(8, 1)
                withTimeout(5_000) { while (fixture.owner.state.value.pendingWindow != null) yield() }
                assertSame(original.pages, fixture.owner.state.value.pages)
                assertEquals(original.visibleWindow, fixture.owner.state.value.visibleWindow)
                assertEquals("MANGA_UI_WINDOW_SCOPE", fixture.owner.state.value.windowError)
                assertEquals(decodedBefore, fixture.library.decodeRequests.size)
            } finally { fixture.close() }
        }
    }

    private class WindowFixture : AutoCloseable {
        val library = WindowLibrary()
        private val job = SupervisorJob()
        val owner = ReaderMangaReadingOwner(CoroutineScope(job + Dispatchers.Unconfined)) { library }
        suspend fun open() {
            owner.open(ReaderMangaChapterRequest(ReaderMangaChapterRef("source", "book"), 0))
            withTimeout(5_000) {
                while (owner.state.value.phase == ReaderMangaReadingPhase.LOADING) yield()
            }
            check(owner.state.value.phase == ReaderMangaReadingPhase.READY)
        }
        override fun close() { owner.close(); job.cancel() }
    }

    private class WindowLibrary : ReaderMangaLibrary {
        private val store = ReaderMangaResourceStore(Files.createTempDirectory("reader-manga-window-").toFile())
        private val chapterIdentity = ReaderMangaChapterIdentity("source", "book", "chapter-0")
        private val manifest = ReaderMangaManifest(chapterIdentity, "rules-1", "manifest-1", "decode-1",
            (0 until 12).map { ReaderMangaManifestPage("page-$it", it, "manga:page-$it") })
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
            store.putDecodedByTransferId(identity.wireId, byteArrayOf(ordinal.toByte(), 8, 9))
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
