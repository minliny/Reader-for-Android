package io.reader.android.runtime

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/** Unapplied JVM failure regression. Synthetic Core DTOs only; no book text, Android storage or Native execution. */
class BookmarkWarmIntentTest {
    @Test fun originalProtocolAckFailuresStayTypedThroughMutationAndSettlement(): Unit = runBlocking {
        for (method in listOf("bookmark.create", "bookmark.operation.settle")) harness { h ->
            val lease = h.client()
            h.core.failAfterTerminalFor = method
            h.turn(lease) {
                val failure = assertThrows(CoreProtocolPending::class.java) { h.service.create(h.chapter, "", "", 0, "", "") }
                assertSame(h.core.protocolFailure, failure)
                assertSame(failure, failure.coreProtocolPending())
                assertEquals(CoreProtocolStage.CONSUMED_ACK_FAILED, failure.stage)
                assertEquals("bookmark-warm-test-runtime", failure.original.runtimeSession)
                assertEquals(h.store.records.values.single().getLong("requestId"), failure.original.originalRequestId)
                assertEquals(method, h.core.commands.last().first)
                assertEquals(1, h.core.mutationCalls)
                val original = h.store.records.values.single()
                assertFalse(original.getBoolean("completed"))
                assertTrue(original.has("wireTerminal"))
                assertEquals(listOf(original.getString("id")), h.service.pending().map { it.id })
                assertEquals(if (method == "bookmark.create") 2 else 4, h.core.commands.size)
            }
        }
    }

    @Test fun firstJournalFailureSurvivesClientReplacementAndCompletedFinalizationClearsWarmOnce(): Unit = runBlocking {
        harness { h ->
            val old = h.client()
            h.store.failBeforeFirstWrite = true
            val service = h.turn(old) { h.service }
            val failure = h.turn(old) { pending { service.create(h.chapter, "", "", 0, "", "") } }
            val original = clone(h.store.firstAttempt!!)
            val literal = original.getJSONObject("parameters").toString()
            assertTrue(h.store.records.isEmpty())
            assertEquals(0, h.core.mutationCalls)
            h.owner.retireClient(old)
            val current = h.client() // Same local counter; this is a new true lease, never a resurrected old lease.
            h.turn(current) {
                assertSame(service, h.service)
                assertEquals(listOf(failure.operationId), service.pending().map { it.id })
                val sentBefore = h.core.commands.size
                assertEquals(failure.operationId, pending { service.create(h.chapter, "", "", 0, "", "") }.operationId)
                assertEquals(sentBefore, h.core.commands.size) // Pending barrier precedes even a new head query.
                assertEquals(failure.operationId, pending { service.reconcile(h.chapter.bookId, failure.operationId) }.operationId)
                assertEquals(literal, h.core.commands.last().second.getJSONObject("expected").getJSONObject("params").toString())
                assertEquals(0, h.core.mutationCalls) // No replay after a known before-send failure and absent Core receipt.
                assertEquals(listOf(failure.operationId), service.pending().map { it.id })
            }
            assertTrue(h.store.records.isEmpty()) // The fake journal cannot secretly supply the missing intent.
            assertTrue(h.core.seenLeases.contains(current))

            val lateDisk = clone(original).put("wireTerminal", wireTerminal(original.getLong("requestId"), JSONObject()))
            val warm = clone(original)
            assertSame(warm, BookmarkWarmIntentPolicy.retainOriginal(lateDisk, warm))
            assertTrue(warm.has("wireTerminal"))
            val newer = clone(original).put("stage", "settle").put("requestId", original.getLong("requestId") + 1)
            BookmarkWarmIntentPolicy.retainOriginal(lateDisk, newer)
            assertEquals("settle", newer.getString("stage"))
            assertFalse(newer.has("wireTerminal")) // Older request cannot become the new stage's wire.
            val foreign = clone(original).also { it.getJSONObject("parameters").put("content", "changed-identity") }
            assertThrows(IllegalStateException::class.java) { BookmarkWarmIntentPolicy.retainOriginal(foreign, clone(original)) }
        }
        // Independent accepted transaction: do not invent a Core receipt for the never-dispatched operation above.
        harness { h ->
            val old = h.client()
            h.store.failCompletedAfterPublish = true // Exact archive/dirsync boundary: bytes exist but durability failed.
            val service = h.turn(old) { h.service }
            h.turn(old) { assertThrows(IOException::class.java) { service.create(h.chapter, "", "", 0, "", "") } }
            val completed = clone(h.store.records.values.single())
            assertTrue(completed.getBoolean("completed"))
            assertEquals("settled", completed.getJSONObject("receipt").getString("phase"))
            assertEquals(1, h.core.mutationCalls)
            h.owner.retireClient(old)
            val current = h.client()
            h.turn(current) {
                assertSame(service, h.service)
                val id = completed.getString("id")
                assertEquals(listOf(id), service.pending().map { it.id }) // Failed Completed finalization is still a barrier.
                val writesBefore = h.store.writeCalls
                val first = service.reconcile(h.chapter.bookId, id)
                assertEquals(writesBefore + 1, h.store.writeCalls)
                assertTrue(service.pending().isEmpty())
                val writesAfter = h.store.writeCalls
                val commandsAfter = h.core.commands.size
                assertEquals(first, service.reconcile(h.chapter.bookId, id))
                assertEquals(writesAfter, h.store.writeCalls) // No duplicate Completed write once warm is truly cleared.
                assertEquals(commandsAfter, h.core.commands.size)
                assertEquals(1, h.core.mutationCalls)
            }
        }
    }

    private class Store(private val assertHeld: () -> Unit) : BookmarkJournalPort {
        val records = linkedMapOf<String, JSONObject>()
        var failBeforeFirstWrite = false
        var failCompletedAfterPublish = false
        var firstAttempt: JSONObject? = null
        var writeCalls = 0
        override fun readAll(): List<JSONObject> { assertHeld(); return records.values.filterNot { it.optBoolean("completed") }.map(::clone) }
        override fun exact(id: String, includeCompleted: Boolean): JSONObject? {
            assertHeld(); return records[id]?.takeIf { includeCompleted || !it.optBoolean("completed") }?.let(::clone)
        }
        override fun write(record: JSONObject) {
            assertHeld(); writeCalls++
            if (firstAttempt == null) firstAttempt = clone(record)
            if (failBeforeFirstWrite) { failBeforeFirstWrite = false; throw IOException("before any durable intent") }
            records[record.getString("id")] = clone(record)
            if (record.optBoolean("completed") && failCompletedAfterPublish) {
                failCompletedAfterPublish = false
                throw IOException("after Completed publish before directory durability")
            }
        }
        override fun terminal(id: String, requestId: Long, session: String, event: JSONObject) {
            assertHeld()
            val record = records[id] ?: throw IllegalStateException("ORIGINAL_JOURNAL_MISSING")
            if (!record.optBoolean("completed") && record.optLong("requestId") == requestId && record.optString("runtimeSession") == session)
                write(clone(record).put("wireTerminal", event))
        }
    }
    private class Core(private val heldLease: () -> ReaderOperationOwner.ClientLease) : CoreTransport {
        val commands = mutableListOf<Pair<String, JSONObject>>()
        val seenLeases = mutableListOf<ReaderOperationOwner.ClientLease>()
        var mutationCalls = 0
        var failAfterTerminalFor: String? = null
        var protocolFailure: CoreProtocolPending? = null
        private var requestId = 1_000L
        private var receipt: JSONObject? = null
        override val session: String get() { seenLeases += heldLease(); return "bookmark-warm-test-runtime" }
        override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
            terminal: ((JSONObject) -> Unit)?): JSONObject {
            heldLease()
            val id = ++requestId
            beforeSend(id) // If this throws, no logical Core dispatch or retained terminal callback exists.
            commands += method to clone(params)
            val result = when (method) {
                "bookmark.operation.query" -> JSONObject().put("head", receipt?.getJSONObject("binding") ?: JSONObject.NULL)
                    .put("receipt", receipt ?: JSONObject.NULL)
                "bookmark.create" -> {
                    mutationCalls++
                    val row = JSONObject().put("positionScope", params.getJSONObject("positionScope")).put("time", 1_800_000_000_001L)
                    for (key in listOf("bookName", "bookAuthor", "chapterIndex", "chapterPos", "chapterName", "bookText", "content")) row.put(key, params.get(key))
                    val data = JSONObject().put("bookmark", row)
                    receipt = JSONObject().put("binding", JSONObject().put("operationId", params.getString("operationId"))
                        .put("sequence", params.getLong("sequence")).put("payloadFingerprint", "b".repeat(64)))
                        .put("phase", "committed").put("outcome", JSONObject().put("action", "create").put("result", data))
                    data
                }
                "bookmark.operation.settle" -> clone(receipt!!).put("phase", "settled").also { receipt = it }
                else -> throw AssertionError("Unexpected business command $method")
            }
            terminal?.invoke(wireTerminal(id, result))
            if (method == failAfterTerminalFor) {
                failAfterTerminalFor = null
                val failure = CoreProtocolPending(CoreProtocolReference("bookmark-warm-test-runtime",
                    "fixture-original-$id", id, "fixture-token-$id", id + 10_000), CoreProtocolStage.CONSUMED_ACK_FAILED)
                protocolFailure = failure
                throw failure
            }
            return clone(result)
        }
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = Unit
        override fun close() = throw AssertionError("Service must not close Native")
    }
    private class Harness {
        private data class Held(val lease: ReaderOperationOwner.ClientLease, val assertActive: () -> Unit)
        private val held = ThreadLocal<Held?>()
        private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val owner = ReaderOperationOwner.create(File(System.getProperty("java.io.tmpdir"), "bookmark-warm-${UUID.randomUUID()}"), dispatcher) {}
        private fun heldLease(): ReaderOperationOwner.ClientLease {
            val lane = held.get() ?: error("OUTSIDE_REAL_OWNER_GATE")
            lane.assertActive(); return lane.lease
        }
        val store = Store { heldLease() }
        val core = Core(::heldLease)
        val service by lazy { heldLease(); ReaderBookmarks(core, store) }
        val chapter = ReaderChapter("local:" + "a".repeat(64), 0, "", "", "body", "processing")
        fun client() = owner.activateClient(UUID.randomUUID(), 7)
        suspend fun <T> turn(lease: ReaderOperationOwner.ClientLease, block: () -> T): T = owner.withTurn(lease) {
            val turn = this
            withContext(held.asContextElement(Held(lease) { turn.assertActive() })) { block() }
        }
        suspend fun close() {
            try { withContext(NonCancellable) { withTimeout(2_000) { owner.close().await() } } }
            finally { dispatcher.close() }
        }
    }
    private suspend fun harness(block: suspend (Harness) -> Unit) {
        val h = Harness()
        try { withTimeout(5_000) { block(h) } } finally { h.close() }
    }
    private fun pending(block: () -> Unit): PendingOperationException {
        try { block() } catch (error: PendingOperationException) { return error }
        throw AssertionError("Expected original bookmark pending")
    }
    companion object {
        private fun clone(value: JSONObject) = JSONObject(value.toString())
        private fun wireTerminal(id: Long, data: JSONObject) = JSONObject().put("protocolVersion", 1).put("requestId", id).put("type", "result").put("data", data)
    }
}
