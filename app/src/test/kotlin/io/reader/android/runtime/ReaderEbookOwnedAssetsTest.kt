package io.reader.android.runtime

import io.reader.android.runtime.ebookdraft.EbookRenderLease
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** SOURCE-ONLY draft, three precise cases. Port bytes are scheduling fixtures,
 * never an EPUB/MOBI corpus, Native result, parser test or format acceptance. */
class ReaderEbookOwnedAssetsTest {
    @Test fun nativeMaximumUsesRemainingBudgetAndFailedAttemptsStayCounted() {
        val budget = ReaderEbookAssetBudget()
        val mib = 1024L * 1024
        assertEquals(32 * mib, budget.nativeMaximum(32 * mib))
        budget.returned(32 * mib, 32 * mib)
        budget.returned(31 * mib, 32 * mib)
        assertEquals(1 * mib, budget.nativeMaximum(8 * mib)) // Bound is calculated BEFORE calling Native.
        assertThrows(IllegalStateException::class.java) { budget.returned(2 * mib, 1 * mib) }
        budget.returned(1 * mib, 1 * mib)
        assertThrows(IllegalStateException::class.java) { budget.nativeMaximum(8 * mib) }
        assertThrows(IllegalArgumentException::class.java) { budget.nativeMaximum(33 * mib) }
        val failed = ReaderEbookAssetBudget()
        repeat(512) { failed.attempted() } // No successful read/commit is needed to consume attempts.
        assertThrows(IllegalStateException::class.java) { failed.attempted() }
    }

    @Test fun replacedRenderQueuedBehindActorCannotReachNativeAndAbaClientIsRejected(): Unit = runBlocking {
        harness { port, actor ->
            val authority = Authority(actor.runtimeSession)
            val old = authority.request()
            val pollEntered = CountDownLatch(1)
            val releasePoll = CountDownLatch(1)
            port.pollBarrier.set(pollEntered to releasePoll)
            actor.snapshot() // A real actor turn; its following poll takes the deterministic barrier.
            assertTrue(pollEntered.await(2, TimeUnit.SECONDS))
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { actor.readEbookAsset(old) }
            }
            try {
                val current = authority.replaceRender()
                current.requireNativeAdmission(actor.runtimeSession)
                releasePoll.countDown()
                val error = queued.await().exceptionOrNull()
                assertTrue(error is IllegalStateException && error.message == "RETIRED_EBOOK_NATIVE_READ")
                assertEquals(0, port.assetCalls.get())
                val nextLease = authority.ownerLease.copy(clientId = UUID.randomUUID(), ownerGeneration = authority.ownerLease.ownerGeneration + 1)
                authority.currentClient.set(nextLease) // Same localGeneration, different issued client identity/generation.
                assertThrows(IllegalStateException::class.java) { current.requireNativeAdmission(actor.runtimeSession) }
            } finally { releasePoll.countDown() }
        }
    }

    @Test fun cancelledObserverAndCloseWaitForRealSynchronousAssetReturnOnSoleOwner(): Unit = runBlocking {
        harness { port, actor ->
            val request = Authority(actor.runtimeSession).request()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            port.assetBarrier.set(entered to release)
            val read = async(start = CoroutineStart.UNDISPATCHED) { actor.readEbookAsset(request) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val close = async(start = CoroutineStart.UNDISPATCHED) { actor.close() }
            try {
                read.cancel()
                assertFalse(read.isCompleted) // NonCancellable action still owns the admitted call.
                assertFalse(close.isCompleted)
                assertFalse(port.nativeClosed.get())
                release.countDown()
                read.cancelAndJoin()
                val report = close.await()
                assertTrue(port.assetReturned.get())
                assertTrue(port.nativeClosed.get())
                assertEquals(1, port.assetCalls.get())
                assertFalse(report.nativeWorkerJoinConfirmed)
                val threads = port.threads.toList()
                assertTrue(threads.isNotEmpty() && threads.all { it === threads.first() })
                assertEquals(listOf("asset-return", "close"), port.order.toList())
            } finally { release.countDown() }
        }
    }

    private class Authority(runtime: String) {
        val ownerLease = ReaderOperationOwner.ClientLease(UUID.randomUUID(), UUID.randomUUID(), 1, 7)
        val currentClient = AtomicReference<ReaderOperationOwner.ClientLease?>(ownerLease)
        private val retired = AtomicBoolean(false)
        private val registry = ReaderEbookAssetRegistry()
        private var lease = EbookRenderLease(ownerLease.ownerId, ownerLease.clientId, ownerLease.ownerGeneration,
            runtime, UUID.randomUUID(), "local:" + "a".repeat(64), 0, "body", "processing")
        private var permit = registry.issue(lease, ownerLease, currentClient, retired) { check(currentClient.get() == ownerLease && !retired.get()) }
        fun request() = ReaderEbookNativeAssetRead(lease, permit, "/private/authorized.source", "OPS/document.xhtml", 1)
        fun replaceRender(): ReaderEbookNativeAssetRead {
            lease = lease.copy(renderId = UUID.randomUUID())
            permit = registry.issue(lease, ownerLease, currentClient, retired) { check(currentClient.get() == ownerLease && !retired.get()) }
            return request()
        }
    }

    private class Port : ReaderEbookBinaryRuntimePort {
        val threads = ConcurrentLinkedQueue<Thread>()
        val order = ConcurrentLinkedQueue<String>()
        val assetCalls = AtomicInteger()
        val assetReturned = AtomicBoolean(false)
        val nativeClosed = AtomicBoolean(false)
        val pollBarrier = AtomicReference<Pair<CountDownLatch, CountDownLatch>?>()
        val assetBarrier = AtomicReference<Pair<CountDownLatch, CountDownLatch>?>()
        private fun owner() { threads.add(Thread.currentThread()) }
        override fun deliveryCapabilities(): Int { owner(); return 1 }
        override fun enableEventAcknowledgements(): ReaderCoreEventActor.NativeStatus { owner(); return ok("rc_runtime_enable_event_ack") }
        override fun send(commandJson: String): ReaderCoreEventActor.NativeStatus = throw AssertionError("No JSON command belongs to this asset slice")
        override fun cancel(requestId: Long): ReaderCoreEventActor.NativeStatus = throw AssertionError("Synchronous asset calls have no invented cancellation")
        override fun acknowledge(deliveryId: Long): ReaderCoreEventActor.NativeStatus = throw AssertionError("Assets have no JSON delivery ACK")
        override fun poll(): String? {
            owner()
            pollBarrier.getAndSet(null)?.let { (entered, release) ->
                entered.countDown(); check(release.await(2, TimeUnit.SECONDS))
            }
            return null
        }
        override fun readEpubEntry(authorizedArchivePath: String, canonicalEntry: String, maximumBytes: Long): ByteArray {
            owner(); assetCalls.incrementAndGet()
            assetBarrier.getAndSet(null)?.let { (entered, release) ->
                entered.countDown(); check(release.await(2, TimeUnit.SECONDS))
            }
            check(!nativeClosed.get())
            assetReturned.set(true); order.add("asset-return")
            return byteArrayOf(0) // Scheduling fixture only; no archive is opened.
        }
        override fun close() { owner(); nativeClosed.set(true); order.add("close") }
    }

    private suspend fun harness(block: suspend kotlinx.coroutines.CoroutineScope.(Port, ReaderCoreEventActor) -> Unit) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val port = Port()
        val actor = ReaderCoreEventActor(port, dispatcher, pollIntervalMs = 1)
        try { withTimeout(5_000) { actor.awaitReady(); block(port, actor) } }
        finally {
            try { withContext(NonCancellable) { withTimeout(2_000) { actor.close() } } }
            finally { dispatcher.close() }
        }
    }
    companion object { private fun ok(operation: String) = ReaderCoreEventActor.NativeStatus(operation, 0) }
}
