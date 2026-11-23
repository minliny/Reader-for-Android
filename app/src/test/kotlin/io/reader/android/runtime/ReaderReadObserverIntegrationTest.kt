package io.reader.android.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Ignored, uncompiled/unrun counterexamples. Real Owner/Actor/bridge; synthetic RuntimePort only. */
class ReaderReadObserverIntegrationTest {
    @Test fun callerCancellationCancelsExactReadStopsNextRpcAndProtectsFinally(): Unit = runBlocking {
        harness { h ->
            val old = h.client()
            val parent = Job()
            val enteredFinally = CompletableDeferred<Unit>()
            val releaseFinally = CompletableDeferred<Unit>()
            val delivered = AtomicInteger()
            val worker = CoroutineScope(parent + Dispatchers.Default).async {
                h.pure(old, protected = {
                    assertTrue(currentCoroutineContext().isActive)
                    assertEquals(old, h.heldLease())
                    enteredFinally.complete(Unit)
                    releaseFinally.await()
                }) {
                    h.transport(old).request("bookshelf.list", timeoutMs = 1_000)
                    h.transport(old).request("reading.progress.get", timeoutMs = 1_000)
                    delivered.incrementAndGet()
                }
            }
            try {
                val original = h.port.sent.receive()
                assertEquals(ReaderCoreEventActor.Kind.READ_ONLY, h.actor.snapshot().requests.single { it.request.requestId == original }.request.kind)
                parent.cancel()
                assertEquals(original, h.port.cancelled.receive())
                enteredFinally.await()
                assertEquals(1, h.port.commands.size)
                assertEquals(0, delivered.get())
                assertTrue(h.actor.snapshot().requests.any { it.request.requestId == original })
                // cancel0 retained the original; only its actual late wire may be consumed.
                releaseFinally.complete(Unit)
                cancelled { worker.await() }
                h.port.emit(error(original, 201))
                h.waitFor { it.deliveries.any { f -> f.delivery.deliveryId == 201L } }
                h.maintenance(old) {
                    val keys = h.bridge.maintenanceKeys(h.actor.snapshot())
                    assertEquals(1, keys.size)
                    h.bridge.consumeFirstRetained(keys.single())
                }
                assertEquals(listOf(201L), h.port.acks.toList())
                assertEquals(1, h.port.commands.size)
            } finally { releaseFinally.complete(Unit); parent.cancel() }
        }
    }

    @Test fun replacementRetiresOnlyOldReadWithoutWaitingForItsGateOrRevivingOldLease(): Unit = runBlocking {
        harness { h ->
            val old = h.client()
            val oldJob = Job()
            val oldRead = CoroutineScope(oldJob + Dispatchers.Default).async {
                h.pure(old) { h.transport(old).request("bookshelf.list", timeoutMs = 1_000) }
            }
            val oldId = h.port.sent.receive()
            val current = h.client() // No old-VM close required: new admission retires its read observer directly.
            assertEquals(oldId, h.port.cancelled.receive())
            cancelled { oldRead.await() }
            val currentJob = Job()
            val currentRead = CoroutineScope(currentJob + Dispatchers.Default).async {
                h.pure(current) { h.transport(current).request("bookshelf.get", timeoutMs = 1_000) }
            }
            val currentId = h.port.sent.receive()
            h.observers.activate(old) // Delayed old activation must not overtake a newer generation.
            h.retire(old)
            h.port.emit(result(currentId, 211))
            currentRead.await()
            assertEquals(listOf(oldId), h.port.cancelledCalls.toList())
            assertEquals(listOf(211L), h.port.acks.toList())
            assertEquals(2, h.port.commands.size)
            oldJob.cancel(); currentJob.cancel()
        }
    }

    @Test fun retainedReadPreludeAndDurableSinkSurviveCallerCancellationAndReplacement(): Unit = runBlocking {
        harness { h ->
            val old = h.client()
            val parent = Job()
            val sink = AtomicInteger()
            val completed = CompletableDeferred<Unit>()
            val worker = CoroutineScope(parent + Dispatchers.Default).async {
                h.retained(old, protected = { assertEquals(old, h.heldLease()); completed.complete(Unit) }) {
                    h.transport(old).request("bookshelf.get", timeoutMs = 1_000) // No sink; still part of a retained write business turn.
                    h.transport(old).request("reading.progress.update", timeoutMs = 1_000, terminal = { sink.incrementAndGet() })
                }
            }
            val prelude = h.port.sent.receive()
            assertEquals(ReaderCoreEventActor.Kind.DURABLE_WRITE, h.actor.snapshot().requests.single { it.request.requestId == prelude }.request.kind)
            parent.cancel(); h.client(); h.retire(old)
            h.port.emit(result(prelude, 221))
            val write = h.port.sent.receive()
            h.port.emit(result(write, 222))
            completed.await()
            cancelled { worker.await() }
            assertTrue(h.port.cancelledCalls.isEmpty())
            assertEquals(1, sink.get())
            assertEquals(2, h.port.commands.size)
            assertEquals(listOf(221L, 222L), h.port.acks.toList())
        }
    }

    @Test fun cancellationBeforeNativeRecordsExactNonAdmissionBeforeReleasingPlan(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val immutableIntent = "separate-business-intent-not-cleared"
            val heldIntent = java.util.concurrent.atomic.AtomicReference(immutableIntent)
            h.record = { fact ->
                h.requireHeldLane()
                val snapshot = runBlocking { h.actor.snapshot() }
                assertEquals(ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED, snapshot.requests.single().phase)
                assertEquals(fact.requestToken, snapshot.requests.single().request.token)
                assertEquals(immutableIntent, heldIntent.get())
                h.facts.add(fact)
            }
            cancelled {
                h.pure(lease) {
                    h.transport(lease).request("bookshelf.list", beforeSend = { h.currentObserver().job.cancel() })
                }
            }
            assertEquals(1, h.facts.size)
            val fact = h.facts.single()
            assertEquals(lease, fact.key.lease)
            assertEquals("BEFORE_SEND_NOT_DISPATCHED", fact.reason)
            assertEquals(null, fact.status)
            assertEquals(immutableIntent, heldIntent.get())
            assertTrue(h.port.commands.isEmpty() && h.port.acks.isEmpty() && h.port.cancelledCalls.isEmpty())
            val snapshot = h.actor.snapshot()
            assertTrue(snapshot.requests.isEmpty() && snapshot.deliveries.isEmpty())
            assertTrue(snapshot.faults.isNotEmpty() && snapshot.faults.all { it.resolved })
            assertFalse(snapshot.admissionOpen) // No automatic acceptance of protocol faults.
        }
    }

    @Test fun cancellationDuringPureTerminalAckDiscardsResultWithoutRepeatingConsumption(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val parent = Job()
            val settled = CompletableDeferred<Unit>()
            val delivered = AtomicInteger()
            val worker = CoroutineScope(parent + Dispatchers.Default).async {
                h.pure(lease, protected = { settled.complete(Unit) }) {
                    h.port.beforeAck = { parent.cancel() }
                    h.transport(lease).request("bookshelf.get", timeoutMs = 1_000)
                    delivered.incrementAndGet()
                    h.transport(lease).request("reading.progress.get", timeoutMs = 1_000)
                }
            }
            val id = h.port.sent.receive(); h.port.emit(result(id, 241))
            cancelled { worker.await() }
            settled.await() // The caller can cancel before the App-owned wire/ACK/finally completes.
            assertEquals(0, delivered.get())
            assertEquals(1, h.port.commands.size)
            assertEquals(listOf(241L), h.port.acks.toList())
            assertTrue(h.port.cancelledCalls.isEmpty()) // Actual terminal ACK already finished; no fabricated cancel completion.
            assertTrue(h.actor.snapshot().deliveries.isEmpty())
        }
    }

    @Test fun cancelledPureObserverRetainsConsumedAckFailureWithoutAutomaticRetry(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val parent = Job()
            val settled = CompletableDeferred<Unit>()
            val worker = CoroutineScope(parent + Dispatchers.Default).async {
                h.pure(lease, protected = { settled.complete(Unit) }) {
                    h.port.beforeAck = { parent.cancel(); throw IOException("fixture before logical Native ACK") }
                    h.transport(lease).request("bookshelf.get", timeoutMs = 1_000)
                }
            }
            val id = h.port.sent.receive(); h.port.emit(result(id, 251))
            cancelled { worker.await() }
            settled.await() // The caller can cancel before the App-owned wire/ACK/finally completes.
            val snapshot = h.actor.snapshot()
            assertEquals(ReaderCoreEventActor.DeliveryPhase.CONSUMED_ACK_FAILED, snapshot.deliveries.single().phase)
            h.maintenance(lease) {
                assertTrue(h.bridge.maintenanceKeys(h.actor.snapshot()).isEmpty())
                assertTrue(h.bridge.problems(h.actor.snapshot()).any { it is ProtocolProblem.AckOnly })
            }
            assertEquals(1, h.port.ackAttempts.get())
            assertTrue(h.port.acks.isEmpty())
            assertEquals(1, h.port.commands.size)
        }
    }

    @Test fun preparedReadRejectionRequiresHostFactAndRejectsDurableOrSentPlans(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            h.retained(lease) {
                val prepared = h.actor.prepare("bookshelf.list", "{}", ReaderCoreEventActor.Kind.READ_ONLY)
                val durable = h.actor.prepare("bookshelf.get", "{}", ReaderCoreEventActor.Kind.DURABLE_WRITE)
                val sent = h.actor.prepare("bookshelf.get", "{}", ReaderCoreEventActor.Kind.READ_ONLY)
                assertTrue(h.actor.dispatch(sent) { } is ReaderCoreEventActor.DispatchOutcome.Accepted)
                assertEquals(1, h.port.commands.size) // The SENT negative case has exactly its one normal original dispatch.
                assertTrue(h.port.cancelledCalls.isEmpty() && h.port.acks.isEmpty())
                try {
                    h.actor.rejectPreparedReadObserver(durable)
                    throw AssertionError("A durable PREPARED plan must not be relabelled cancelled read")
                } catch (expected: IllegalStateException) {
                    assertEquals("READ_OBSERVER_ALREADY_RESERVED_OR_SENT", expected.message)
                }
                assertEquals(ReaderCoreEventActor.Phase.PREPARED,
                    h.actor.snapshot().requests.single { it.request === durable }.phase)
                try {
                    h.actor.rejectPreparedReadObserver(sent)
                    throw AssertionError("An admitted READ_ONLY plan must not be relabelled unadmitted")
                } catch (expected: IllegalStateException) {
                    assertEquals("READ_OBSERVER_ALREADY_RESERVED_OR_SENT", expected.message)
                }
                assertEquals(ReaderCoreEventActor.Phase.SENT,
                    h.actor.snapshot().requests.single { it.request === sent }.phase)
                assertEquals(1, h.port.commands.size)
                assertTrue(h.port.cancelledCalls.isEmpty() && h.port.acks.isEmpty())
                assertEquals(0, h.port.ackAttempts.get())

                val rejected = h.actor.rejectPreparedReadObserver(prepared)
                assertSame(prepared, rejected.request)
                assertEquals("BEFORE_SEND_NOT_DISPATCHED", rejected.reason)
                assertNull(rejected.status)
                val beforeRecord = h.actor.snapshot()
                assertEquals(ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED,
                    beforeRecord.requests.single { it.request === prepared }.phase)
                val fault = beforeRecord.faults.single { it.requestId == prepared.requestId }
                assertEquals("BEFORE_SEND_NOT_DISPATCHED", fault.kind)
                assertFalse(fault.resolved)
                val key = ActorCoreTransport.OriginalKey(lease,
                    "core:${prepared.runtimeSession}:${prepared.requestId}:${prepared.token}")
                val fact = HostNonAdmissionFact.from(key, prepared, rejected)
                h.record = { actual ->
                    h.requireHeldLane()
                    assertSame(fact, actual)
                    assertEquals(prepared.runtimeSession, actual.runtimeSession)
                    assertEquals(prepared.requestId, actual.requestId)
                    assertEquals(prepared.token, actual.requestToken)
                    assertEquals(prepared.method, actual.method)
                    assertEquals(lease, actual.key.lease)
                    val snapshot = runBlocking { h.actor.snapshot() }
                    assertEquals(ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED,
                        snapshot.requests.single { it.request === prepared }.phase)
                    assertFalse(snapshot.faults.single { it.id == fault.id }.resolved)
                    h.facts.add(actual)
                }
                h.record(fact) // Separate Host fact first; this is not an original wire/ACK.
                assertSame(fact, h.facts.single())
                h.actor.admissionRejectionRecorded(prepared)
                val afterRecord = h.actor.snapshot()
                assertTrue(afterRecord.requests.none { it.request === prepared })
                assertTrue(afterRecord.requests.any { it.request === durable && it.phase == ReaderCoreEventActor.Phase.PREPARED })
                assertTrue(afterRecord.requests.any { it.request === sent && it.phase == ReaderCoreEventActor.Phase.SENT })
                assertTrue(afterRecord.faults.single { it.id == fault.id }.resolved)
                assertFalse(afterRecord.admissionOpen) // No implicit resume or acceptance.
                assertTrue(afterRecord.deliveries.isEmpty())
                assertEquals(1, h.port.commands.size)
                assertTrue(h.port.cancelledCalls.isEmpty() && h.port.acks.isEmpty())
                assertEquals(0, h.port.ackAttempts.get())
            }
        }
    }

    private class Port : ReaderCoreEventActor.RuntimePort {
        val commands = ConcurrentLinkedQueue<String>()
        val sent = Channel<Long>(Channel.UNLIMITED)
        val cancelled = Channel<Long>(Channel.UNLIMITED)
        val cancelledCalls = ConcurrentLinkedQueue<Long>()
        val acks = ConcurrentLinkedQueue<Long>()
        val ackAttempts = AtomicInteger()
        private val events = ConcurrentLinkedQueue<String>()
        @Volatile var beforeAck: (() -> Unit)? = null
        fun emit(wire: String) { events.add(wire) }
        override fun deliveryCapabilities() = 1
        override fun enableEventAcknowledgements() = ok("rc_runtime_enable_event_ack")
        override fun send(commandJson: String): ReaderCoreEventActor.NativeStatus {
            commands.add(commandJson); sent.trySend(JSONObject(commandJson).getLong("requestId")); return ok("rc_runtime_send")
        }
        override fun poll() = events.poll()
        override fun cancel(requestId: Long): ReaderCoreEventActor.NativeStatus {
            cancelledCalls.add(requestId); cancelled.trySend(requestId); return ok("rc_runtime_cancel")
        }
        override fun acknowledge(deliveryId: Long): ReaderCoreEventActor.NativeStatus {
            ackAttempts.incrementAndGet(); beforeAck?.invoke(); acks.add(deliveryId); return ok("rc_runtime_ack_event")
        }
        override fun close() = Unit
    }
    private class Harness : CoreTransportLane {
        private data class Held(val lease: ReaderOperationOwner.ClientLease, val observer: ReaderReadObservers.ReadObserver?, val assertActive: () -> Unit)
        private val held = ThreadLocal<Held?>()
        private val ownerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        private val actorDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val port = Port()
        val actor = ReaderCoreEventActor(port, actorDispatcher, pollIntervalMs = 1)
        val owner = ReaderOperationOwner.create(File(System.getProperty("java.io.tmpdir"), "read-observer-${UUID.randomUUID()}"), ownerDispatcher) { actor.close() }
        val observers = ReaderReadObservers()
        val facts = ConcurrentLinkedQueue<HostNonAdmissionFact>()
        var record: (HostNonAdmissionFact) -> Unit = { facts.add(it) }
        val bridge = ActorCoreTransport(this, actor, HostNonAdmissionSink { record(it) }, CoreReadRequestLane { lease ->
            requireLane(lease); held.get()!!.observer
        })
        fun client() = owner.activateClient(UUID.randomUUID(), 0).also(observers::activate)
        fun retire(lease: ReaderOperationOwner.ClientLease) { observers.retire(lease); owner.retireClient(lease) }
        fun transport(lease: ReaderOperationOwner.ClientLease) = bridge.forClient(lease)
        fun heldLease(): ReaderOperationOwner.ClientLease { requireHeldLane(); return held.get()!!.lease }
        fun currentObserver() = held.get()!!.observer ?: error("NO_CURRENT_PURE_OBSERVER")
        override fun requireHeldLane() { (held.get() ?: error("OUTSIDE_HELD_GATE")).assertActive() }
        override fun requireLane(lease: ReaderOperationOwner.ClientLease) { requireHeldLane(); check(held.get()!!.lease == lease) }
        override fun requireRequestLane(lease: ReaderOperationOwner.ClientLease, method: String, params: JSONObject) = requireLane(lease)
        override fun protocolReference() = CoreProtocolReference(actor.runtimeSession, "actor:${actor.runtimeSession}", null, null)
        suspend fun <T> pure(lease: ReaderOperationOwner.ClientLease, protected: suspend () -> Unit = {}, body: suspend () -> T): T {
            val observer = observers.open(lease, currentCoroutineContext()[Job]!!)
            return try { owner.withObservedRead(lease, observer, body = {
                val turn = this
                withContext(held.asContextElement(Held(lease, observer) { turn.assertActive() })) { body() }
            }, protectedFinally = {
                val turn = this
                withContext(held.asContextElement(Held(lease, null) { turn.assertActive() })) { protected() }
            }) } finally { observer.closeObservation() }
        }
        suspend fun <T> retained(lease: ReaderOperationOwner.ClientLease, protected: suspend () -> Unit = {}, body: suspend () -> T): T = owner.withTurn(lease) {
            val turn = this
            withContext(held.asContextElement(Held(lease, null) { turn.assertActive() })) { try { body() } finally { protected() } }
        }
        suspend fun <T> maintenance(lease: ReaderOperationOwner.ClientLease, body: suspend () -> T): T = owner.withOriginalMaintenance(lease, "read-original-maintenance") {
            val turn = this
            withContext(held.asContextElement(Held(lease, null) { turn.assertActive() })) { body() }
        }
        suspend fun waitFor(predicate: (ReaderCoreEventActor.Snapshot) -> Boolean): ReaderCoreEventActor.Snapshot = withTimeout(2_000) {
            var snapshot = actor.snapshot()
            while (!predicate(snapshot)) { actor.changes.receive(); snapshot = actor.snapshot() }
            snapshot
        }
        suspend fun close() {
            try { withContext(NonCancellable) { withTimeout(2_000) { owner.close().await() } } }
            finally { ownerDispatcher.close(); actorDispatcher.close() }
        }
    }
    private suspend fun harness(body: suspend (Harness) -> Unit) {
        val h = Harness(); var primary: Throwable? = null
        try { withTimeout(5_000) { h.actor.awaitReady(); body(h) } }
        catch (failure: Throwable) { primary = failure; throw failure }
        finally { try { h.close() } catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup } }
    }
    private suspend fun cancelled(body: suspend () -> Unit) {
        try { body() } catch (_: CancellationException) { return }
        throw AssertionError("Expected observer/caller cancellation")
    }
    companion object {
        private fun ok(operation: String) = ReaderCoreEventActor.NativeStatus(operation, 0)
        private fun result(id: Long, delivery: Long) = "{\"type\":\"result\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"data\":{}}"
        private fun error(id: Long, delivery: Long) = "{\"type\":\"error\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"error\":{\"code\":\"CANCELLED\",\"message\":\"cancelled\",\"retryable\":false}}"
    }
}
