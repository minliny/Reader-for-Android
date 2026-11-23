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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** One new JVM counterexample. Real actor/gate/bridge/recovery; synthetic JNI-port DTO fixture only.
 * It is not Native search, a real result list, or a device close acceptance claim. */
class ContentSearchCloseObservationTest {
    @Test fun failedAckPreservesOriginalCloseAndOldOwnersCannotPublishConfirmation(): Unit = runBlocking {
        harness { h ->
            val lease = h.client()
            val closeDelivery = AtomicLong(0)
            val closeId = AtomicLong(0)
            val closeWire = AtomicReference<String>()
            val rejectClose = AtomicBoolean(false)
            val originalCursor = "0123456789abcdef0123456789abcdef:1"
            h.port.sendStatus = { command ->
                val id = command.getLong("requestId")
                val delivery = id + 400
                val params = command.optJSONObject("params") ?: JSONObject()
                val data = when (command.getString("method")) {
                    "core.info" -> JSONObject().put("capabilities", org.json.JSONArray().put("search.content.cursor.v1"))
                    "search.content" -> if (params.optBoolean("closeCursor")) {
                        closeDelivery.set(delivery); closeId.set(id)
                        if (!rejectClose.get()) h.port.throwBeforeAck.set(delivery)
                        JSONObject().put("results", org.json.JSONArray())
                    } else {
                        // Structurally valid typed page fixture; no claim about real book content.
                        val book = params.getString("bookId")
                        val row = JSONObject().put("sourceId", "local").put("bookId", book).put("bookName", "fixture")
                            .put("chapterIndex", 0).put("chapterTitle", "fixture").put("chapterOffset", 0)
                            .put("matchLength", 2).put("snippetStart", 0).put("snippet", "许府")
                            .put("positionScope", JSONObject().put("sourceId", "local").put("bookId", book).put("chapterIndex", 0)
                                .put("bodyVersion", "fixture-body").put("processingVersion", "fixture-processing"))
                        JSONObject().put("results", org.json.JSONArray().put(row)).put("hasMore", true).put("nextCursor", if (rejectClose.get()) "malformed-cursor" else originalCursor)
                    }
                    else -> error("Unexpected fixture command")
                }
                val envelope = JSONObject().put("protocolVersion", 1).put("requestId", id).put("deliveryId", delivery)
                val event = if (params.optBoolean("closeCursor") && rejectClose.get()) {
                    // Fixed1fe content_search.rs:63-90 token_parts -> cursor_error shape.
                    envelope.put("type", "error").put("error", JSONObject().put("code", "INVALID_PARAMS")
                        .put("message", "Search session is unavailable; restart the search").put("retryable", true)
                        .put("details", JSONObject().put("reason", "CONTENT_SEARCH_EXPIRED").put("retryable", true))).toString()
                } else envelope.put("type", "result").put("data", data).toString()
                if (params.optBoolean("closeCursor")) closeWire.set(event)
                h.port.emit(event)
                ok("rc_runtime_send")
            }
            val service = h.business(lease) { ReaderContentSearch(h.bridge.forClient(lease)) }
            val page = h.business(lease) { service.start("local:" + "a".repeat(64), "许府", 1) }
            assertTrue(page.hasMore)
            val firstFailure = try {
                h.business(lease) { try { service.close(page.session) } finally { service.finishOwnerTurn() } }
                throw AssertionError("ACK failure must not return a normal close observation")
            } catch (error: ContentSearchPendingException) { error }
            val typed = firstFailure.coreProtocolPending() ?: throw AssertionError("Original typed ACK failure lost")
            assertEquals(CoreProtocolStage.CONSUMED_ACK_FAILED, typed.stage)
            assertEquals(closeId.get(), typed.original.originalRequestId)
            assertTrue(h.actor.snapshot().deliveries.any { it.delivery.deliveryId == closeDelivery.get() &&
                it.phase == ReaderCoreEventActor.DeliveryPhase.CONSUMED_ACK_FAILED })
            val commands = h.port.commands.size
            val stillPending = try {
                h.business(lease) { service.close(page.session) }
                throw AssertionError("Unacknowledged retained terminal cannot become normal success")
            } catch (error: ContentSearchPendingException) { error }
            assertEquals(typed.original, stillPending.coreProtocolPending()?.original)
            assertEquals(commands, h.port.commands.size) // No second close or repeated search.
            h.refresh(typed.original)
            val notice = h.feed.view.value.items.single { it.action == CoreProtocolAction.ACK_SAME_DELIVERY }
            h.click(notice) // Explicit original protocol recovery, never a service retry.
            val observation = h.business(lease) { service.close(page.session) } ?: throw AssertionError("Actual close receipt missing")
            assertEquals(commands, h.port.commands.size)
            assertEquals(page.session, observation.session)
            assertEquals(page.session.runtimeSession, observation.runtimeSession)
            assertEquals(closeId.get(), observation.requestId)
            assertEquals(originalCursor, observation.cursor)
            assertEquals(JSONObject(closeWire.get()).toString(), observation.parsedTerminalJson)
            val sent = JSONObject(observation.parametersJson)
            assertEquals(setOf("sourceId", "bookId", "keyword", "maxResults", "cursor", "closeCursor"), sent.keys().asSequence().toSet())
            assertEquals(page.session.sourceId, sent.getString("sourceId")); assertEquals(page.session.bookId, sent.getString("bookId"))
            assertEquals(page.session.query, sent.getString("keyword")); assertEquals(page.session.pageSize, sent.getInt("maxResults"))
            assertTrue(sent.getBoolean("closeCursor")); assertEquals(observation.cursor, sent.getString("cursor"))
            assertEquals(1, h.port.nativeAcks.count { it == closeDelivery.get() })
            val scope = ReaderSearchCloseScope(71, 4, page.session)
            assertTrue(ReaderSearchClosePublicationPolicy.confirmed(scope, 71, 4, scope, observation) != null)
            assertEquals(null, ReaderSearchClosePublicationPolicy.confirmed(scope, 72, 4, scope, observation))
            assertEquals(null, ReaderSearchClosePublicationPolicy.confirmed(scope, 71, 5, scope, observation))
            assertEquals(null, ReaderSearchClosePublicationPolicy.confirmed(scope, 71, 4,
                scope.copy(session = page.session.copy(id = "other-query")), observation))
            assertEquals(null, ReaderSearchClosePublicationPolicy.confirmed(scope, 71, 4, scope, null))

            // A second actual service session must not reuse the first confirmed receipt.
            rejectClose.set(true)
            val second = h.business(lease) { service.start("local:" + "a".repeat(64), "许府", 1) }
            assertNotEquals(page.session, second.session)
            val rejected = try {
                h.business(lease) { try { service.close(second.session) } finally { service.finishOwnerTurn() } }
                throw AssertionError("ACKed Core close error is not a close confirmation")
            } catch (error: ContentSearchPendingException) { error }
            fun coreError(error: Throwable) = generateSequence(error) { it.cause }.filterIsInstance<CoreCommandException>().firstOrNull()
            assertEquals(second.session, rejected.session)
            assertEquals(null, rejected.coreProtocolPending()) // Delivery ACK succeeded; this is the original Core error.
            assertEquals("INVALID_PARAMS", coreError(rejected)?.error?.getString("code"))
            assertEquals("CONTENT_SEARCH_EXPIRED", coreError(rejected)?.error?.getJSONObject("details")?.getString("reason"))
            val negativeDelivery = closeDelivery.get()
            assertEquals(1, h.port.nativeAcks.count { it == negativeDelivery }) // Normal logical ACK0 occurred.
            val afterRejection = h.port.commands.size
            val rejectedAgain = try {
                h.business(lease) { try { service.close(second.session) } finally { service.finishOwnerTurn() } }
                throw AssertionError("Retained Core rejection cannot become the first session's success")
            } catch (error: ContentSearchPendingException) { error }
            assertEquals(second.session, rejectedAgain.session)
            assertEquals("CONTENT_SEARCH_EXPIRED", coreError(rejectedAgain)?.error?.getJSONObject("details")?.getString("reason"))
            assertEquals(afterRejection, h.port.commands.size) // No hidden closing RPC after maintenance/reconcile.
            assertEquals(1, h.port.nativeAcks.count { it == negativeDelivery })
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
    companion object { private fun ok(operation: String) = ReaderCoreEventActor.NativeStatus(operation, 0) }
}
