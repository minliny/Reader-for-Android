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
