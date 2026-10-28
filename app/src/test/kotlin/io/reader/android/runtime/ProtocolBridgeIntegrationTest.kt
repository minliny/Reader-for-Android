package io.reader.android.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** UNAPPLIED JVM draft: real actor/whole-turn gate/bridge/recovery; only the JNI port is synthetic.
 * No Android Context, book, database, socket or Native contract claim. Exactly six wiring risks.
 */
class ProtocolBridgeIntegrationTest {
    @Test fun unknownSendLaterOriginalTerminalUsesRetiredOriginalSinkAndLease(): Unit = runBlocking {
        harness { h ->
            val old = h.client()
            val consumedBy = mutableListOf<ReaderOperationOwner.ClientLease>()
            val originalIntent = "immutable-original-intent"
            val intent = AtomicReference(originalIntent)
            h.port.sendStatus = { throw IOException("fixture after API admission: outcome unknown") }
            val pending = pending {
                h.business(old) {
                    h.bridge.forClient(old).request("runtime.ping", beforeSend = { assertEquals(originalIntent, intent.get()) },
                        terminal = { frame ->
                            assertEquals("result", frame.getString("type"))
                            consumedBy += h.heldLease()
                        })
                }
            }
            assertEquals(CoreProtocolStage.ORIGINAL_UNCERTAIN, pending.stage)
            val current = h.client() // Same local generation, different UUID and owner generation.
            assertEquals(old.localGeneration, current.localGeneration)
            assertNotEquals(old.clientId, current.clientId)
            assertNotEquals(old.ownerGeneration, current.ownerGeneration)
            assertEquals(old, h.bridge.routeLease(pending.original))
            fails<IllegalStateException> { h.business(current) { h.bridge.requireRoute(pending.original) } }
            val wire = result(pending.original.originalRequestId!!, 101)
            h.port.emit(wire)
            val retained = h.waitFor { it.deliveries.any { frame -> frame.delivery.deliveryId == 101L } }
            assertEquals(wire, retained.deliveries.single().delivery.rawEvent)
            h.maintenance(pending.original) {
                val key = h.bridge.requireRoute(pending.original)
                assertEquals(listOf(key), h.bridge.maintenanceKeys(h.actor.snapshot()))
                h.bridge.consumeFirstRetained(key)
            }
            assertEquals(listOf(old), consumedBy)
            assertEquals(listOf(101L), h.port.nativeAcks.toList())
            assertEquals(1, h.port.commands.size) // Never send again to learn the unknown outcome.
            assertEquals(originalIntent, intent.get())
            assertEquals(old, h.bridge.routeLease(pending.original)) // Small real route survives callback release.
            assertTrue(h.actor.snapshot().deliveries.isEmpty())
            fails<ReaderOperationOwner.AdmissionRejected> { h.business(old) { error("old client must not re-enter") } }
        }
    }

    @Test fun writeObserverTimeoutIsVisibleWithoutBlockingLaterFirstWireConsumption(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val consumers = AtomicInteger()
            val original = pending {
                h.business(lease) { h.bridge.forClient(lease).request("runtime.ping", timeoutMs = 1,
                    terminal = { consumers.incrementAndGet() }) }
            }
            h.refresh(original.original)
            val shown = h.feed.view.value.items.single { it.original.originalRequestId == original.original.originalRequestId }
            assertEquals(CoreProtocolStage.ORIGINAL_UNCERTAIN, shown.stage)
            assertEquals(null, shown.action)
            assertTrue(h.port.cancels.isEmpty()) // WritePending is not read expiry or a cancel.
            assertTrue(h.actor.snapshot().faults.isEmpty())
            assertEquals(0, consumers.get())
            h.port.emit(result(original.original.originalRequestId!!, 111))
            h.waitFor { it.deliveries.any { frame -> frame.delivery.deliveryId == 111L } }
            h.maintenance(original.original) {
                val key = h.bridge.requireRoute(original.original)
                assertEquals(listOf(key), h.bridge.maintenanceKeys(h.actor.snapshot()))
                h.bridge.consumeFirstRetained(key)
                h.recovery.refreshOnHeldLane()
            }
            assertEquals(1, consumers.get())
            assertEquals(listOf(111L), h.port.nativeAcks.toList())
            assertEquals(1, h.port.commands.size)
            assertTrue(h.feed.view.value.items.isEmpty())
            assertTrue(h.actor.snapshot().admissionOpen)
        }
    }

    @Test fun oneContinueRetriesOnlyCapturedAckAndPreservesForeignFault(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val consumers = AtomicInteger()
            val original = pending {
                h.business(lease) { h.bridge.forClient(lease).request("runtime.ping", timeoutMs = 1,
                    terminal = { consumers.incrementAndGet() }) }
            }
            // Register both real original routes before any fault blocks new admission.
            val foreignConsumers = AtomicInteger()
            val foreignOriginal = pending {
                h.business(lease) { h.bridge.forClient(lease).request("runtime.ping", timeoutMs = 1,
                    terminal = { foreignConsumers.incrementAndGet() }) }
            }
            h.port.throwBeforeAck.set(122)
            h.port.emit(result(foreignOriginal.original.originalRequestId!!, 122))
            h.waitFor { it.deliveries.any { frame -> frame.delivery.deliveryId == 122L } }
            pending { h.maintenance(foreignOriginal.original) {
                h.bridge.consumeFirstRetained(h.bridge.requireRoute(foreignOriginal.original))
            } }
            h.maintenance(foreignOriginal.original) {
                val frame = h.actor.snapshot().deliveries.single().delivery
                assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, h.actor.retryAcknowledgement(frame))
                h.bridge.acknowledgementCompleted(foreignOriginal.original.copy(deliveryId = 122), frame)
                h.recovery.refreshOnHeldLane()
            }
            val foreign = h.actor.snapshot().faults.single { it.deliveryId == 122L }
            assertTrue(foreign.resolved) // Existing unrelated resolved IDs are still not this presentation's authority.
            h.port.throwBeforeAck.set(121)
            h.port.emit(result(original.original.originalRequestId!!, 121))
            h.waitFor { it.deliveries.any { frame -> frame.delivery.deliveryId == 121L } }
            val failed = pending {
                h.maintenance(original.original) { h.bridge.consumeFirstRetained(h.bridge.requireRoute(original.original)) }
            }
            assertEquals(CoreProtocolStage.CONSUMED_ACK_FAILED, failed.stage)
            assertEquals(1, consumers.get())
            assertEquals(listOf(122L), h.port.nativeAcks.toList()) // Target fixture threw BEFORE its logical Native ACK.
            val before = h.actor.snapshot()
            val offered = before.faults.single { it.kind == "DELIVERY_ACK" && it.deliveryId == 121L }
            h.refresh(original.original)
            val notice = h.feed.view.value.items.single { it.action == CoreProtocolAction.ACK_SAME_DELIVERY }
            assertEquals(121L, notice.original.deliveryId)
            h.click(notice)
            val after = h.actor.snapshot()
            assertEquals(1, consumers.get())
            assertEquals(1, foreignConsumers.get())
            assertEquals(2, h.port.commands.size)
            assertEquals(4, h.port.ackAttempts.get())
            assertEquals(listOf(122L, 121L), h.port.nativeAcks.toList())
            assertTrue(after.deliveries.none { it.delivery.deliveryId == 121L })
            assertTrue(after.faults.none { it.id == offered.id })
            assertEquals(foreign, after.faults.single { it.id == foreign.id })
            assertFalse(after.admissionOpen)
        }
    }

    @Test fun controlErrorIsSeparatelyRecordedThenAckedWithoutOriginalSuccessOrResume(): Unit = runBlocking {
        val hostCalls = AtomicInteger()
        harness(handler = ReaderCoreEventActor.HostHandler {
            hostCalls.incrementAndGet()
            ReaderCoreEventActor.HostReply.Complete("{}")
        }) { h ->
            val lease = h.client()
            val originalConsumers = AtomicInteger()
            h.port.sendStatus = { command ->
                if (command.getString("method") == "host.complete")
                    h.port.emit(error(command.getLong("requestId"), 132, "INVALID_PARAMS"))
                ok("rc_runtime_send")
            }
            val original = pending {
                h.business(lease) { h.bridge.forClient(lease).request("runtime.hostSmoke", timeoutMs = 1,
                    terminal = { originalConsumers.incrementAndGet() }) }
            }
            h.port.emit(host(original.original.originalRequestId!!, 131, 7))
            val retained = h.waitFor { it.deliveries.any { frame -> frame.delivery.origin == ReaderCoreEventActor.Origin.CONTROL_ERROR } }
            val control = retained.deliveries.single { it.delivery.origin == ReaderCoreEventActor.Origin.CONTROL_ERROR }.delivery
            assertNotEquals(original.original.originalRequestId, control.requestId)
            h.refresh(original.original)
            val notice = h.feed.view.value.items.single { it.action == CoreProtocolAction.RECORD_CONTROL_ERROR }
            assertEquals(original.original.requestToken, notice.original.requestToken)
            h.click(notice)
            assertEquals(listOf(control.rawEvent), h.controlRecords.map { it.second.rawEvent })
            assertEquals(original.original.originalRequestId, h.controlRecords.single().first.originalRequestId)
            assertTrue(h.order.indexOf("record-control:132") < h.order.indexOf("ack:132"))
            assertEquals(0, originalConsumers.get())
            assertEquals(1, hostCalls.get())
            assertEquals(listOf("runtime.hostSmoke", "host.complete"), h.port.commands.map { JSONObject(it).getString("method") })
            val after = h.actor.snapshot()
            assertTrue(after.requests.any { it.request.requestId == original.original.originalRequestId })
            assertEquals("CONTROL_ERROR", after.hostOperations.single().phase)
            assertFalse(after.admissionOpen)
            assertTrue(h.nonAdmissionRecords.isEmpty())
        }
    }

    @Test fun knownNonAdmissionMustRecordBeforeRetirementWithoutWireOrIntentMutation(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val intent = JSONObject().put("id", "original-protocol-intent").put("completed", false).toString()
            val retainedIntent = AtomicReference(intent)
            val beforeSendCalls = AtomicInteger()
            val originalConsumers = AtomicInteger()
            h.port.sendStatus = { ReaderCoreEventActor.NativeStatus("rc_runtime_send", 3,
                ReaderCoreEventActor.Diagnostic(5, "invalid command JSON")) }
            h.nonAdmissionRecord = { throw IOException("fixture separate Host record not durable yet") }
            val original = pending {
                h.business(lease) { h.bridge.forClient(lease).request("runtime.ping",
                    beforeSend = { beforeSendCalls.incrementAndGet() }, terminal = { originalConsumers.incrementAndGet() }) }
            }
            assertEquals(CoreProtocolStage.HOST_NON_ADMISSION_UNRECORDED, original.stage)
            assertEquals(ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED, h.actor.snapshot().requests.single().phase)
            h.nonAdmissionRecord = { fact ->
                // Synchronous evidence sink is called before actor retirement, on the held original gate.
                h.requireHeldLane()
                val atRecord = runBlocking { h.actor.snapshot() }
                assertEquals(fact.requestToken, atRecord.requests.single().request.token)
                assertEquals(ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED, atRecord.requests.single().phase)
                assertEquals(intent, retainedIntent.get())
                h.nonAdmissionRecords += fact
            }
            h.refresh(original.original)
            val notice = h.feed.view.value.items.single { it.action == CoreProtocolAction.RECORD_NON_ADMISSION }
            h.click(notice)
            val fact = h.nonAdmissionRecords.single()
            assertEquals(original.original.originalRequestId, fact.requestId)
            assertEquals(original.original.requestToken, fact.requestToken)
            val status = requireNotNull(fact.status)
            assertEquals(3, status.status)
            assertEquals(5, status.diagnostic!!.code)
            assertEquals(1, beforeSendCalls.get())
            assertEquals(0, originalConsumers.get())
            assertEquals(intent, retainedIntent.get())
            assertEquals(1, h.port.commands.size)
            val after = h.actor.snapshot()
            assertTrue(after.requests.isEmpty() && after.deliveries.isEmpty())
            assertTrue(h.port.nativeAcks.isEmpty())
            assertFalse(after.admissionOpen) // Recording is not the later explicit acceptance action.
            assertTrue(after.faults.isNotEmpty() && after.faults.all { it.resolved })
            assertEquals(CoreProtocolAction.ACCEPT_RESOLVED_FAULTS, h.feed.view.value.items.single().action)
        }
    }

    @Test fun presentationRetiredWhileQueuedAtGateCannotAckAgain(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val consumers = AtomicInteger()
            val original = pending {
                h.business(lease) { h.bridge.forClient(lease).request("runtime.ping", timeoutMs = 1,
                    terminal = { consumers.incrementAndGet() }) }
            }
            h.port.throwBeforeAck.set(151)
            h.port.emit(result(original.original.originalRequestId!!, 151))
            h.waitFor { it.deliveries.any { frame -> frame.delivery.deliveryId == 151L } }
            pending { h.maintenance(original.original) { h.bridge.consumeFirstRetained(h.bridge.requireRoute(original.original)) } }
            h.refresh(original.original)
            val stale = h.feed.view.value.items.single { it.action == CoreProtocolAction.ACK_SAME_DELIVERY }
            val queued = CompletableDeferred<Unit>()
            val exited = CompletableDeferred<Unit>()
            h.actionQueued.set(queued); h.actionExited.set(exited)
            h.business(lease) {
                assertTrue(h.feed.requestAction(stale.presentationId))
                queued.await() // Explicit barrier: action has requested this held whole-turn gate.
                assertEquals(1, h.port.ackAttempts.get())
                val frame = h.actor.snapshot().deliveries.single().delivery
                assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, h.actor.retryAcknowledgement(frame))
                h.bridge.acknowledgementCompleted(stale.original, frame)
                h.recovery.refreshOnHeldLane() // Publish accurate new qualification before queued action enters.
                assertTrue(h.feed.view.value.items.none { it.presentationId == stale.presentationId })
            }
            exited.await()
            assertEquals(2, h.port.ackAttempts.get()) // Initial pre-call exception + direct successful ACK; no stale third ACK.
            assertEquals(listOf(151L), h.port.nativeAcks.toList())
            assertEquals(1, consumers.get())
            assertEquals(1, h.port.commands.size)
            assertTrue(h.actor.snapshot().deliveries.isEmpty())
        }
    }

    private class Port(private val order: ConcurrentLinkedQueue<String>) : ReaderCoreEventActor.RuntimePort {
        val commands = ConcurrentLinkedQueue<String>()
        val nativeAcks = ConcurrentLinkedQueue<Long>()
        val cancels = ConcurrentLinkedQueue<Long>()
        val ackAttempts = AtomicInteger()
        val throwBeforeAck = AtomicLong(-1)
        private val events = ConcurrentLinkedQueue<String>()
        @Volatile var sendStatus: (JSONObject) -> ReaderCoreEventActor.NativeStatus = { ok("rc_runtime_send") }
        fun emit(raw: String) { events.add(raw) }
        override fun deliveryCapabilities() = 1
        override fun enableEventAcknowledgements() = ok("rc_runtime_enable_event_ack")
        override fun send(commandJson: String): ReaderCoreEventActor.NativeStatus {
            commands.add(commandJson)
            return sendStatus(JSONObject(commandJson))
        }
        override fun poll(): String? = events.poll()
        override fun cancel(requestId: Long): ReaderCoreEventActor.NativeStatus {
            cancels.add(requestId); return ok("rc_runtime_cancel")
        }
        override fun acknowledge(deliveryId: Long): ReaderCoreEventActor.NativeStatus {
            ackAttempts.incrementAndGet()
            if (throwBeforeAck.compareAndSet(deliveryId, -1)) throw IOException("fixture BEFORE Native ACK invocation")
            // This is the first logical Native invocation, not a fictional status4 -> status0 retry.
            nativeAcks.add(deliveryId); order.add("ack:$deliveryId")
            return ok("rc_runtime_ack_event")
        }
        override fun close() = Unit
    }

    private class Harness(handler: ReaderCoreEventActor.HostHandler?) : CoreTransportLane {
        private data class Held(val lease: ReaderOperationOwner.ClientLease, val business: Boolean, val assertActive: () -> Unit)
        private val held = ThreadLocal<Held?>()
        private val ownerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        private val actorDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        private val appScope = CoroutineScope(SupervisorJob() + ownerDispatcher)
        val order = ConcurrentLinkedQueue<String>()
        val port = Port(order)
        val actor = ReaderCoreEventActor(port, actorDispatcher, handler, pollIntervalMs = 1)
        val owner = ReaderOperationOwner.create(File(System.getProperty("java.io.tmpdir"), "protocol-owner-${UUID.randomUUID()}"),
            ownerDispatcher) { actor.close() } // Identity path only: never mkdir/write a database.
        val nonAdmissionRecords = mutableListOf<HostNonAdmissionFact>()
        val controlRecords = mutableListOf<Pair<CoreProtocolReference, ReaderCoreEventActor.Delivery>>()
        var nonAdmissionRecord: (HostNonAdmissionFact) -> Unit = { nonAdmissionRecords += it }
        val bridge = ActorCoreTransport(this, actor, HostNonAdmissionSink { nonAdmissionRecord(it) })
        val feed = CoreProtocolRecoveryFeed()
        val actionQueued = AtomicReference<CompletableDeferred<Unit>?>()
        val actionExited = AtomicReference<CompletableDeferred<Unit>?>()
        val recovery = CoreProtocolRecoveryOwner(actor, object : ProtocolRecoveryLane {
            override suspend fun <T> exclusive(ref: CoreProtocolReference, block: suspend () -> T): T {
                val queued = actionQueued.getAndSet(null)
                val exited = actionExited.getAndSet(null)
                queued?.complete(Unit)
                try { return maintenance(ref, block) }
                finally { exited?.complete(Unit) }
            }
            override fun assertHeld() = requireHeldLane()
        }, bridge, object : ProtocolEvidenceSink {
            override fun recordControlError(ref: CoreProtocolReference, delivery: ReaderCoreEventActor.Delivery) {
                requireHeldLane(); order.add("record-control:${delivery.deliveryId}"); controlRecords += ref to delivery
            }
            override fun recordNonAdmission(fact: HostNonAdmissionFact) { requireHeldLane(); nonAdmissionRecord(fact) }
        }, appScope, feed)

        fun client() = owner.activateClient(UUID.randomUUID(), 7)
        fun heldLease(): ReaderOperationOwner.ClientLease { requireHeldLane(); return held.get()!!.lease }
        override fun requireHeldLane() { (held.get() ?: error("OUTSIDE_TEST_OWNER_LANE")).assertActive() }
        override fun requireLane(lease: ReaderOperationOwner.ClientLease) {
            requireHeldLane(); check(held.get()!!.lease == lease) { "FOREIGN_LIBRARY_CLIENT_LANE" }
        }
        override fun requireRequestLane(lease: ReaderOperationOwner.ClientLease, method: String, params: JSONObject) {
            requireLane(lease); check(held.get()!!.business) { "MAINTENANCE_CANNOT_DISPATCH_NEW_BUSINESS" }
        }
        override fun protocolReference(): CoreProtocolReference {
            requireHeldLane(); return CoreProtocolReference(actor.runtimeSession, "actor:${actor.runtimeSession}", null, null)
        }
        suspend fun <T> business(lease: ReaderOperationOwner.ClientLease, block: suspend () -> T): T = owner.withTurn(lease) {
            val turn = this
            withContext(held.asContextElement(Held(lease, true) { turn.assertActive() })) { block() }
        }
        suspend fun <T> maintenance(ref: CoreProtocolReference, block: suspend () -> T): T {
            val lease = bridge.routeLease(ref) ?: error("MISSING_EXACT_ORIGINAL_ROUTE")
            return owner.withOriginalMaintenance(lease, ref.originalId) {
                val turn = this
                withContext(held.asContextElement(Held(lease, false) { turn.assertActive() })) {
                    bridge.requireRoute(ref) // Recheck exact session/id/token/lease after obtaining the gate.
                    block()
                }
            }
        }
        suspend fun refresh(ref: CoreProtocolReference) = maintenance(ref) { recovery.refreshOnHeldLane() }
        suspend fun click(notice: CoreProtocolPresentation) {
            val exited = CompletableDeferred<Unit>()
            check(actionExited.compareAndSet(null, exited))
            assertTrue(feed.requestAction(notice.presentationId))
            exited.await()
        }
        suspend fun waitFor(predicate: (ReaderCoreEventActor.Snapshot) -> Boolean): ReaderCoreEventActor.Snapshot = withTimeout(2_000) {
            var snapshot = actor.snapshot()
            while (!predicate(snapshot)) { actor.changes.receive(); snapshot = actor.snapshot() }
            snapshot
        }
        suspend fun close() {
            try { withContext(NonCancellable) { withTimeout(2_000) { owner.close().await() } } }
            finally { appScope.cancel(); ownerDispatcher.close(); actorDispatcher.close() }
        }
    }

    private suspend fun harness(handler: ReaderCoreEventActor.HostHandler? = null, block: suspend (Harness) -> Unit) {
        val h = Harness(handler)
        try { withTimeout(5_000) { h.actor.awaitReady(); block(h) } }
        finally { h.close() }
    }
    private suspend fun pending(block: suspend () -> Unit): CoreProtocolPending {
        try { block() } catch (error: Exception) { return error.coreProtocolPending() ?: throw error }
        throw AssertionError("Expected a real typed CoreProtocolPending")
    }
    private suspend inline fun <reified T : Throwable> fails(noinline block: suspend () -> Unit): T {
        try { block() } catch (error: Throwable) { if (error is T) return error; throw error }
        throw AssertionError("Expected ${T::class.java.name}")
    }
    companion object {
        private fun ok(operation: String) = ReaderCoreEventActor.NativeStatus(operation, 0)
        private fun result(id: Long, delivery: Long) =
            "{\"type\":\"result\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"data\":{\"pong\":true,\"method\":\"runtime.ping\"}}"
        private fun error(id: Long, delivery: Long, code: String) =
            "{\"type\":\"error\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"error\":{\"code\":\"$code\",\"message\":\"diagnostic\",\"retryable\":false}}"
        private fun host(id: Long, delivery: Long, operation: Long) =
            "{\"type\":\"host.request\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"operationId\":$operation,\"capability\":\"host.smoke.echo\",\"params\":{}}"
    }
}
