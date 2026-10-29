package io.reader.android

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.NativeCoreRuntimePort
import io.reader.android.runtime.ReaderCoreEventActor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** SOURCE-ONLY isolated Native probe. Real Core, actor and port; no production Core/book/IO capability. */
@RunWith(AndroidJUnit4::class)
class NativeReadCancellationProbeTest {
    @Test(timeout = 60_000)
    fun realHostReadObserverCancellationKeepsOriginalCancelledReceipt(): Unit = runBlocking {
        marker("method-enter")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("io.reader.minliny", context.packageName)
        assertEquals(context.applicationInfo.uid, Process.myUid())
        assertEquals(PIN, BuildConfig.CORE_COMMIT)
        val parent = File(context.cacheDir, "A0NativeReadCancellationProbes")
        assertTrue(parent.mkdirs() || parent.isDirectory)
        val relative = "A0NativeReadCancellationProbes/${UUID.randomUUID()}"
        val probe = File(context.cacheDir, relative)
        assertTrue("Never reuse a runtime directory", probe.mkdir())
        val data = File(probe, "data")
        val cache = File(probe, "cache")
        assertTrue(data.mkdir() && cache.mkdir())
        assertTrue(probe.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
        marker("private-probe-created", Bundle().apply { putString("probeCacheRelativePath", relative) })

        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        // The wrapper records calls and immediately returns the unchanged real status/raw bytes.
        val port = RecordingPort(NativeCoreRuntimePort(data, cache))
        val entered = CompletableDeferred<ReaderCoreEventActor.HostEnvelope>()
        val exited = CompletableDeferred<Unit>()
        val handlerCalls = AtomicInteger()
        val consumerCalls = AtomicInteger()
        val actor = ReaderCoreEventActor(port, dispatcher, hostHandler = ReaderCoreEventActor.HostHandler { envelope ->
            handlerCalls.incrementAndGet()
            entered.complete(envelope) // Only this real host.request can open the cancellation barrier.
            try { awaitCancellation() } // No Host I/O, synthetic Complete/Error or external client.
            finally { port.order.add("handler-exited"); exited.complete(Unit) }
        }, pollIntervalMs = 5)
        var primary: Throwable? = null
        try {
            withTimeout(10_000) { actor.awaitReady() }
            marker("real-actor-ready", Bundle().apply { putString("runtimeSession", actor.runtimeSession) })
            val params = JSONObject().put("capability", "host.smoke.echo").put("params", JSONObject())
            val plan = actor.prepare("runtime.hostSmoke", params.toString(), ReaderCoreEventActor.Kind.READ_ONLY)
            assertTrue(actor.dispatch(plan) {} is ReaderCoreEventActor.DispatchOutcome.Accepted)
            marker("original-read-dispatched", Bundle().apply {
                putLong("requestId", plan.requestId); putString("runtimeSession", plan.runtimeSession); putString("requestToken", plan.token)
            })
            val observer = async(start = CoroutineStart.UNDISPATCHED) { actor.observe(plan, 45_000) }
            val host = withTimeout(10_000) { entered.await() }
            assertEquals(plan.runtimeSession, host.runtimeSession)
            assertEquals(plan.requestId, host.requestId)
            assertEquals(plan.token, host.requestToken)
            assertEquals("host.smoke.echo", host.capability)
            assertEquals(0, JSONObject(host.parametersJson).length())
            assertTrue(host.operationId > 0)
            marker("real-host-handler-entered", Bundle().apply {
                putLong("requestId", host.requestId); putLong("operationId", host.operationId)
                putString("capability", host.capability); putString("parametersJson", host.parametersJson)
            })
            assertFalse(observer.isCompleted) // Handler is suspended; no response/deadline has completed this request.
            observer.cancelAndJoin() // Actual observe cancellation invokes the actor's READ_ONLY cancel path.
            assertTrue(observer.isCancelled)
            val cancelled = port.cancels.single()
            assertEquals(plan.requestId, cancelled.first)
            assertEquals("rc_runtime_cancel", cancelled.second.operation)
            assertEquals(0, cancelled.second.status)
            marker("original-native-cancel-ACK", Bundle().apply {
                putLong("requestId", cancelled.first); putString("operation", cancelled.second.operation); putInt("status", cancelled.second.status)
            })
            withTimeout(5_000) { exited.await() }
            assertEquals(1, handlerCalls.get())
            marker("original-host-handler-exited")

            val retained = waitFor(actor) { it.deliveries.any { frame -> frame.delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL } }
            val terminal = retained.deliveries.single { it.delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL }.delivery
            val wire = JSONObject(terminal.rawEvent)
            assertEquals(1L, exactLong(wire, "protocolVersion"))
            assertEquals(plan.requestId, exactLong(wire, "requestId"))
            assertEquals(terminal.deliveryId, exactLong(wire, "deliveryId"))
            assertEquals("error", wire.getString("type"))
            assertEquals("CANCELLED", wire.getJSONObject("error").getString("code"))
            assertEquals(plan.runtimeSession, terminal.runtimeSession)
            assertEquals(plan.token, terminal.requestToken)
            assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, actor.consume(terminal) { original ->
                assertSame(terminal, original)
                assertEquals(1, consumerCalls.incrementAndGet())
                port.order.add("terminal-consumed")
                marker("original-cancelled-terminal-consumed", Bundle().apply {
                    putLong("requestId", original.requestId); putLong("deliveryId", original.deliveryId)
                    putString("originalWire", original.rawEvent); putString("originalWireSha256", sha(original.rawEvent))
                })
            })
            val drained = waitFor(actor) { it.requests.isEmpty() && it.deliveries.isEmpty() && it.hostOperations.isEmpty() }
            assertTrue(drained.faults.isEmpty() && drained.unparsedOriginals.isEmpty())
            assertEquals(1, consumerCalls.get())
            val originals = port.rawEvents.map(::JSONObject)
            assertEquals(2, originals.size)
            val hostWire = originals.single { it.getString("type") == "host.request" }
            assertEquals(plan.requestId, exactLong(hostWire, "requestId"))
            assertEquals(host.operationId, exactLong(hostWire, "operationId"))
            assertEquals("host.smoke.echo", hostWire.getString("capability"))
            assertEquals(0, hostWire.getJSONObject("params").length())
            val hostDelivery = exactLong(hostWire, "deliveryId")
            assertTrue(hostDelivery > 0 && hostDelivery < terminal.deliveryId)
            val acknowledgements = port.acknowledgements.toList()
            assertEquals(2, acknowledgements.size)
            assertEquals(setOf(hostDelivery, terminal.deliveryId), acknowledgements.map { it.first }.toSet())
            assertTrue(acknowledgements.all { it.second.operation == "rc_runtime_ack_event" && it.second.status == 0 })
            assertEquals(1, port.commands.size)
            assertEquals("runtime.hostSmoke", JSONObject(port.commands.single()).getString("method"))
            assertEquals(0, drained.controlRoutingIdentityCount)
            assertTrue(port.order.indexOf("handler-exited") < port.order.indexOf("ack:$hostDelivery"))
            assertTrue(port.order.indexOf("terminal-consumed") < port.order.indexOf("ack:${terminal.deliveryId}"))
            marker("exact-two-deliveries-ACK", Bundle().apply {
                putLong("hostDeliveryId", hostDelivery); putLong("terminalDeliveryId", terminal.deliveryId)
                putInt("hostHandlerCalls", handlerCalls.get()); putInt("terminalConsumerCalls", consumerCalls.get())
                putInt("sendCount", port.commands.size); putInt("cancelCount", port.cancels.size)
                putInt("ackCount", acknowledgements.size); putInt("controlCount", drained.controlRoutingIdentityCount)
            })
            marker("method-body-complete")
        } catch (error: Throwable) {
            primary = error
            throw error
        } finally {
            try {
                val closed = withContext(NonCancellable) { withTimeout(5_000) { actor.close() } }
                assertNull(closed.nativeCloseFailure)
                assertFalse(closed.nativeWorkerJoinConfirmed)
                assertTrue(closed.hostHandlersDrained)
                assertEquals(1, port.closes.get())
                assertFalse(port.foreignThread.get())
                val nativeThread = port.thread.get() ?: throw AssertionError("Actual Native owner thread absent")
                marker("native-close-returned", Bundle().apply {
                    putBoolean("nativeWorkerJoinConfirmed", closed.nativeWorkerJoinConfirmed)
                    putBoolean("hostHandlersDrained", closed.hostHandlersDrained)
                    putLong("nativeOwnerThreadId", nativeThread.id); putString("nativeOwnerThreadName", nativeThread.name)
                    putBoolean("oneActualNativeThread", !port.foreignThread.get())
                })
            } catch (cleanupFailure: Throwable) {
                val originalFailure = primary
                if (originalFailure == null) throw cleanupFailure
                if (originalFailure !== cleanupFailure) originalFailure.addSuppressed(cleanupFailure)
            } finally {
                dispatcher.close()
                // Do not delete private data/cache; destroy does not establish a worker join.
                marker("private-probe-retained", Bundle().apply {
                    putString("probeCacheRelativePath", relative); putBoolean("directoryRetained", data.isDirectory && cache.isDirectory)
                    putBoolean("nativeWorkerJoinConfirmed", false)
                })
            }
        }
        marker("method-exit")
    }

    /** Decoration only: no status transformation, injected event, fake Native or extra Core call. */
    private class RecordingPort(private val actual: ReaderCoreEventActor.RuntimePort) : ReaderCoreEventActor.RuntimePort {
        val thread = AtomicReference<Thread?>()
        val foreignThread = AtomicBoolean(false)
        val closes = AtomicInteger()
        val commands = ConcurrentLinkedQueue<String>()
        val rawEvents = ConcurrentLinkedQueue<String>()
        val cancels = ConcurrentLinkedQueue<Pair<Long, ReaderCoreEventActor.NativeStatus>>()
        val acknowledgements = ConcurrentLinkedQueue<Pair<Long, ReaderCoreEventActor.NativeStatus>>()
        val order = ConcurrentLinkedQueue<String>()
        private fun owner() {
            val current = Thread.currentThread()
            thread.compareAndSet(null, current)
            if (thread.get() !== current) foreignThread.set(true)
        }
        override fun deliveryCapabilities(): Int { owner(); return actual.deliveryCapabilities() }
        override fun enableEventAcknowledgements(): ReaderCoreEventActor.NativeStatus { owner(); return actual.enableEventAcknowledgements() }
        override fun send(commandJson: String): ReaderCoreEventActor.NativeStatus {
            owner(); commands.add(commandJson); return actual.send(commandJson)
        }
        override fun poll(): String? { owner(); return actual.poll()?.also { rawEvents.add(it) } }
        override fun cancel(requestId: Long): ReaderCoreEventActor.NativeStatus {
            owner(); return actual.cancel(requestId).also { cancels.add(requestId to it) }
        }
        override fun acknowledge(deliveryId: Long): ReaderCoreEventActor.NativeStatus {
            owner(); return actual.acknowledge(deliveryId).also { acknowledgements.add(deliveryId to it); order.add("ack:$deliveryId") }
        }
        override fun close() { owner(); actual.close(); closes.incrementAndGet() }
    }
    private suspend fun waitFor(actor: ReaderCoreEventActor, predicate: (ReaderCoreEventActor.Snapshot) -> Boolean): ReaderCoreEventActor.Snapshot = withTimeout(10_000) {
        var snapshot = actor.snapshot()
        while (!predicate(snapshot)) { actor.changes.receive(); snapshot = actor.snapshot() }
        snapshot
    }
    private fun exactLong(event: JSONObject, key: String): Long = when (val value = event.get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw AssertionError("Original exact integer required: $key")
    }
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerMethod", METHOD); fields.putString("readerStep", step)
        fields.putInt("readerProcessPid", Process.myPid()); fields.putInt("readerProcessUid", Process.myUid())
        fields.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, fields)
    }
    companion object {
        private const val PIN = "c3109d570dd62a636eff1ffb88f4e242ede0bf3a"
        private const val METHOD = "realHostReadObserverCancellationKeepsOriginalCancelledReceipt"
    }
}
