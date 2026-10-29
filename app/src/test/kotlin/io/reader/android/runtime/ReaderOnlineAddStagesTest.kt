package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real Stages + serialized fake Journal port. No Android IO/fsync, book content, network or Native evidence. */
class ReaderOnlineAddStagesTest {
    private class Journal : ReaderOnlineAddJournalPort {
        val committed = linkedMapOf<String, String>()
        val retainedCandidates = linkedSetOf<String>()
        var writes = 0
        override fun write(record: JSONObject) {
            val id = record.getString("id")
            check(id !in retainedCandidates) { "TEST_CANDIDATE_MUST_NOT_BE_WRITTEN" }
            committed[id] = record.toString() // No shared JSONObject references survive a write or load.
            writes++
        }
        override fun onlineAddOriginalForQuery(id: String) = committed[id]?.let {
            ReaderOnlineAddJournalSnapshot(it, id in retainedCandidates)
        }
        override fun onlineAddActiveForQuery() = committed.keys.map { checkNotNull(onlineAddOriginalForQuery(it)) }
    }

    private class Transport(private val journal: Journal, private val childId: String, override val session: String) : CoreTransport {
        val dispatches = mutableListOf<Pair<String, String>>()
        var beforeSendCalls = 0
        var drains = 0
        var closes = 0
        val unknown = CoreProtocolPending(CoreProtocolReference(session, "protocol-original", 41L, "protocol-token"),
            CoreProtocolStage.ORIGINAL_UNCERTAIN)
        override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
            terminal: ((JSONObject) -> Unit)?): JSONObject {
            val reservation = JSONObject(checkNotNull(journal.committed[childId]))
            assertTrue(reservation.getBoolean("attempted"))
            assertFalse(reservation.has("requestId"))
            beforeSendCalls++; beforeSend(41L)
            val original = JSONObject(checkNotNull(journal.committed[childId]))
            assertEquals(41L, original.getLong("requestId")); assertEquals(session, original.getString("runtimeSession"))
            assertEquals(params.toString(), original.getString("parametersJson"))
            assertEquals(method, original.getString("method"))
            dispatches += method to params.toString()
            throw unknown // No original terminal exists; only the beforeSend identity has become durable in this fake port.
        }
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) { drains++; error("NO_RECOVERY_DRAIN") }
        override fun close() { closes++; error("STAGES_MUST_NOT_CLOSE_TRANSPORT") }
    }

    @Test fun unknownAfterBeforeSendSurvivesReloadAndRetainedCandidateNeverDispatches() {
        val parentId = "android-onlineadd-00000000-0000-4000-8000-000000000002"
        val parent = JSONObject().put("id", parentId).put("sourceId", "protocol-source")
            .put("bookId", "protocol-book").put("sourceVersion", "protocol-source-version")
        val params = JSONObject().put("sourceId", "protocol-source").put("bookId", "protocol-book")
            .put("action", "begin").put("reason", "add")
        val childId = "$parentId-prepare"
        val journal = Journal()
        val firstTransport = Transport(journal, childId, "protocol-runtime-before-reload")
        val firstStages = ReaderOnlineAddStages(firstTransport, journal)
        assertSame(firstTransport.unknown, assertThrows(CoreProtocolPending::class.java) {
            firstStages.call(parent, "prepare", "reading.preparation", params)
        })
        assertEquals(listOf("reading.preparation" to params.toString()), firstTransport.dispatches)
        assertEquals(1, firstTransport.beforeSendCalls)
        assertEquals(3, journal.writes) // Planned original, dispatch reservation, then exact beforeSend request/runtime.
        val originalRaw = checkNotNull(journal.committed[childId])
        val original = JSONObject(originalRaw)
        assertEquals(childId, original.getString("id")); assertEquals(parentId, original.getString("parentId"))
        assertEquals(params.toString(), original.getString("parametersJson")); assertTrue(original.getBoolean("attempted"))
        assertEquals(41L, original.getLong("requestId")); assertEquals(firstTransport.session, original.getString("runtimeSession"))
        assertFalse(original.getBoolean("completed")); assertFalse(original.has("originalTerminalJson"))
        val writesAfterUnknown = journal.writes

        // A genuinely new Stages instance has an empty warm map and a different current runtime.
        val nextTransport = Transport(journal, childId, "protocol-runtime-after-reload")
        val reloaded = ReaderOnlineAddStages(nextTransport, journal)
        val loaded = checkNotNull(reloaded.load(childId))
        assertEquals(originalRaw, loaded.recordJson); assertTrue(loaded.canonicalCommitted); assertFalse(loaded.candidateRetained)
        val pending = assertThrows(ReaderOnlineAddUnconfirmed::class.java) {
            reloaded.call(parent, "prepare", "reading.preparation", JSONObject(params.toString()))
        }
        assertEquals(parentId, pending.operationId); assertEquals("unknown:prepare", pending.phase)
        assertThrows(ReaderOnlineAddUnconfirmed::class.java) { reloaded.requireNoUnknown(parentId) }
        assertEquals(originalRaw, journal.committed[childId]); assertEquals(writesAfterUnknown, journal.writes)
        assertTrue(nextTransport.dispatches.isEmpty()); assertEquals(0, nextTransport.beforeSendCalls)

        // A different planned child is not attempted; the retained candidate alone must still block it.
        val candidateId = "$parentId-toc"
        val candidateParams = JSONObject().put("sourceId", "protocol-source").put("bookId", "protocol-book")
            .put("preparationRevision", 7)
        val candidateBase = JSONObject(originalRaw).put("id", candidateId).put("method", "book.toc")
            .put("parametersJson", candidateParams.toString()).put("attempted", false)
        candidateBase.remove("requestId"); candidateBase.remove("runtimeSession")
        journal.write(candidateBase); journal.retainedCandidates += candidateId
        val candidateRaw = checkNotNull(journal.committed[candidateId]); val writesBeforeCandidateCall = journal.writes
        val candidateTransport = Transport(journal, candidateId, "protocol-runtime-candidate")
        val candidateStages = ReaderOnlineAddStages(candidateTransport, journal)
        assertTrue(checkNotNull(candidateStages.load(candidateId)).candidateRetained)
        val blocked = assertThrows(ReaderOnlineAddUnconfirmed::class.java) {
            candidateStages.call(parent, "toc", "book.toc", candidateParams)
        }
        assertEquals(parentId, blocked.operationId); assertEquals("candidate:toc", blocked.phase)
        assertEquals(candidateRaw, journal.committed[candidateId]); assertEquals(writesBeforeCandidateCall, journal.writes)
        assertTrue(candidateId in journal.retainedCandidates); assertTrue(candidateTransport.dispatches.isEmpty())
        assertEquals(0, candidateTransport.beforeSendCalls)
        for (transport in listOf(firstTransport, nextTransport, candidateTransport)) {
            assertEquals(0, transport.drains); assertEquals(0, transport.closes)
        }
    }
}
