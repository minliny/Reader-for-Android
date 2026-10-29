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
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** SOURCE-ONLY: one scheduling counterexample, not a Native/format acceptance test.
 * The port/harness are unchanged from the existing three asset methods.
 */
class ReaderEbookActualAdmissionTest {
    @Test fun actualClientRetirementRejectsQueuedNativeReadEvenWhileMirrorStillOld(): Unit = runBlocking {
        harness { port, actor ->
            val gateExecutor = Executors.newSingleThreadExecutor()
            val gateDispatcher = gateExecutor.asCoroutineDispatcher()
            val owner = ReaderOperationOwner.create(
                File(System.getProperty("java.io.tmpdir"), "reader-asset-admission-" + UUID.randomUUID()),
                dispatcher = gateDispatcher, closeAction = {},
            )
            val a = owner.activateClient(UUID.randomUUID(), 7)
            val mirror = AtomicReference<ReaderOperationOwner.ClientLease?>(a)
            val retired = AtomicBoolean(false)
            val lease = EbookRenderLease(a.ownerId, a.clientId, a.ownerGeneration,
                actor.runtimeSession, UUID.randomUUID(), "local:" + "a".repeat(64), 0, "body", "processing")
            val permit = ReaderEbookAssetRegistry().issue(lease, a, mirror, retired) {
                owner.assertCurrentAdmission(a) // Real stateLock authority, never the delayed mirror.
            }
            val request = ReaderEbookNativeAssetRead(lease, permit,
                "/private/authorized.source", "OPS/document.xhtml", 1)
            val pollEntered = CountDownLatch(1)
            val releasePoll = CountDownLatch(1)
            val turnEntered = AtomicBoolean(false)
            try {
                request.requireNativeAdmission(actor.runtimeSession)
                port.pollBarrier.set(pollEntered to releasePoll)
                actor.snapshot()
                assertTrue(pollEntered.await(2, TimeUnit.SECONDS))
                val queued = async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching {
                        owner.withTurn(a) {
                            turnEntered.set(true)
                            actor.readEbookAsset(request)
                        }
                    }
                }
                // withTurn dispatches its original App job before the UNDISTPATCHED
                // observer suspends. FIFO fence proves that job ran through the
                // actor's non-blocking mailbox send and suspended on its reply.
                gateExecutor.submit {}.get(2, TimeUnit.SECONDS)
                assertTrue(turnEntered.get())
                assertFalse(queued.isCompleted)
                val b = owner.activateClient(UUID.randomUUID(), 7)
                assertTrue(b.ownerGeneration > a.ownerGeneration)
                owner.assertCurrentAdmission(b)
                assertSame(a, mirror.get()) // Deliberately reproduce activate -> mirror-publish gap.
                assertFalse(retired.get())
                releasePoll.countDown()
                val failure = queued.await().exceptionOrNull()
                assertTrue(failure is ReaderOperationOwner.AdmissionRejected)
                assertEquals("READER_CLIENT_RETIRED", failure?.message)
                assertEquals(0, port.assetCalls.get())
                assertSame(a, mirror.get())
            } finally {
                releasePoll.countDown()
                try { withContext(NonCancellable) { withTimeout(2_000) { owner.close().await() } } }
                finally { gateDispatcher.close() }
            }
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
