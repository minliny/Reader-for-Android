package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real progress service/policy with serialized journal and transport ports; no Native or Android IO proof. */
class ReaderMangaProgressServiceTest {
    private val owner = ReaderMangaChapterRef("manga-source", "manga-book")
    private val chapter = ReaderMangaChapterIdentity(owner.sourceId, owner.bookId, "chapter-7")
    private val page = "mp1:" + "a".repeat(64)
    private val manifest = ReaderMangaManifest(chapter, "source-v1", "manifest-v1", "decode-v1",
        listOf(ReaderMangaManifestPage(page, 0, "manga:$page")))
    private val location = ReaderMangaLocation(chapter, manifest.manifestVersion, page, 0, 0.0, 0.4, 1)
    private val request = ReaderMangaProgressSaveRequest(manifest, location, ReaderMangaProgressToken(3, 0))

    private class Journal : ReaderMangaProgressJournalPort {
        val records = linkedMapOf<String, String>()
        val candidates = linkedSetOf<String>()
        var failTerminal = false
        var failCompletion = false
        var writes = 0
        override fun write(record: JSONObject) {
            val id = record.getString("id")
            check(id !in candidates) { "CANDIDATE_RETAINED" }
            if (failCompletion && record.getBoolean("completed")) throw IllegalStateException("DISK_COMPLETION_FAILED")
            records[id] = record.toString(); writes++
        }
        override fun terminal(id: String, requestId: Long, session: String, event: JSONObject) {
            if (failTerminal) throw IllegalStateException("DISK_TERMINAL_FAILED")
            val record = JSONObject(checkNotNull(records[id]))
            assertEquals(requestId, record.getLong("requestId")); assertEquals(session, record.getString("runtimeSession"))
            write(record.put("wireTerminal", JSONObject(event.toString())))
        }
        override fun original(id: String) = records[id]?.let { ReaderMangaProgressJournalSnapshot(it, id in candidates) }
        override fun active() = records.keys.filter { !JSONObject(records.getValue(it)).getBoolean("completed") }
            .map { checkNotNull(original(it)) }
    }

    private inner class Fixture(val journal: Journal = Journal(), runtimeName: String = "runtime-a") {
        var client = "client-a"
        var runtime = runtimeName
        var beforeWriteCalls = 0
        var sharedWriterBlocked = false
        var mode = "result"
        var transform: (JSONObject) -> Unit = {}
        var reads = JSONObject().put("token", JSONObject().put("epoch", 3).put("revision", 0)).put("location", JSONObject.NULL)
        var drainEvent: JSONObject? = null
        var drainRequiresProtocolRecovery = false
        var terminalSink: ((JSONObject) -> Unit)? = null
        var drains = 0
        val requests = mutableListOf<Pair<String, String>>()
        val unknown = CoreProtocolPending(CoreProtocolReference(runtime, "original", 41L, "token"), CoreProtocolStage.ORIGINAL_UNCERTAIN)
        val transport: CoreTransport = object : CoreTransport {
            override val session get() = runtime
            override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
                terminal: ((JSONObject) -> Unit)?): JSONObject {
                if (method == "reading.progress.get") {
                    requests += method to params.toString()
                    return JSONObject(reads.toString())
                }
                assertEquals(ReaderMangaProgressPolicy.METHOD, method)
                service.requireSharedWriterAdmission(method, params) // The existing App gate checks the exact reserved publisher.
                val original = JSONObject(journal.records.values.single { !JSONObject(it).getBoolean("completed") })
                assertTrue(original.getBoolean("attempted")); assertFalse(original.has("requestId"))
                beforeSend(41L)
                requests += method to params.toString(); terminalSink = terminal
                if (mode == "unknown") throw unknown
                val event = if (mode == "rejected" || mode == "indeterminate") JSONObject().put("protocolVersion", 1)
                    .put("requestId", 41).put("type", "error").put("error", JSONObject()
                        .put("code", if (mode == "rejected") "INVALID_PARAMS" else "INTERNAL").put("message", "probe"))
                else event(params).also(transform)
                deliver(event)
                if (mode == "ack-failed") {
                    drainRequiresProtocolRecovery = true
                    throw unknown
                }
                return CoreTerminal.result(method, event)
            }
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) {
                assertEquals(41L, requestId); drains++
                check(!drainRequiresProtocolRecovery) { "EXPLICIT_ORIGINAL_EVENT_RECOVERY_REQUIRED" }
                drainEvent?.let { drainEvent = null; deliver(it) }
            }
            override fun close() = error("NO_NATIVE_CLOSE")
        }
        val service: ReaderMangaProgressService = ReaderMangaProgressService(transport, journal, {}, { client }, { runtime }, {
            beforeWriteCalls++
            check(!sharedWriterBlocked) { "SHARED_WRITER_ORIGINAL_PENDING" }
        })
        fun event(params: JSONObject = ReaderMangaProgressPolicy.parameters(request)): JSONObject = JSONObject()
            .put("protocolVersion", 1).put("requestId", 41).put("type", "result")
            .put("data", JSONObject().put("token", JSONObject().put("epoch", params.getLong("expectedEpoch"))
                .put("revision", params.getJSONObject("location").getLong("progressRevision")))
                .put("location", JSONObject(params.getJSONObject("location").toString())))
        fun deliver(event: JSONObject) {
            try { checkNotNull(terminalSink).invoke(event) }
            catch (_: Exception) {
                drainRequiresProtocolRecovery = true
                throw unknown // The real transport preserves consumer/ACK failures as typed protocol pending.
            }
        }
        fun id() = journal.records.keys.single()
        fun record() = JSONObject(journal.records.getValue(id()))
    }

    @Test fun exactMangaReadSaveAndOriginalAcknowledgementDoNotUseTextOrShelfCommands() {
        val fixture = Fixture()
        assertEquals(ReaderMangaProgressState(ReaderMangaProgressToken(3, 0), null), fixture.service.get(owner))
        val state = fixture.service.save(request)
        assertEquals(ReaderMangaProgressState(ReaderMangaProgressToken(3, 1), location), state)
        assertEquals(listOf("reading.progress.get", "reading.progress.update"), fixture.requests.map { it.first })
        val read = JSONObject(fixture.requests[0].second)
        assertEquals(setOf("kind", "sourceId", "bookId"), read.keys().asSequence().toSet())
        assertEquals("manga", read.getString("kind")); assertEquals(owner.sourceId, read.getString("sourceId"))
        val write = JSONObject(fixture.requests[1].second)
        assertEquals(setOf("kind", "location", "expectedEpoch", "expectedRevision"), write.keys().asSequence().toSet())
        assertEquals(3L, write.getLong("expectedEpoch")); assertEquals(0L, write.getLong("expectedRevision"))
        assertEquals(location, ReaderMangaProgressPolicy.location(write.getJSONObject("location")))
        assertTrue(fixture.record().getBoolean("completed")); assertTrue(fixture.service.pending().isEmpty())
        assertTrue(fixture.record().getBoolean("transportConfirmed"))
        assertEquals(1, fixture.beforeWriteCalls)
        assertEquals(state, fixture.service.reconcile(owner, fixture.id()))
        assertEquals(0, fixture.drains)
    }

    @Test fun unknownWriteSurvivesClientAndRuntimeReplacementAndFreshReadCannotConfirmIt() {
        val fixture = Fixture().apply { mode = "unknown" }
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) { fixture.service.save(request) })
        val id = fixture.id(); val before = fixture.journal.records.getValue(id)
        fixture.client = "new-library-client"
        fixture.reads = fixture.event().getJSONObject("data") // Even matching current state is not this original's receipt.
        assertEquals(location, fixture.service.get(owner).location)
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) { fixture.service.save(request) }
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) { fixture.service.reconcile(owner, id) })
        assertEquals(0, fixture.drains)
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) {
            fixture.service.requireSharedWriterAdmission("rules.mutation.query", JSONObject())
        }
        val cold = Fixture(fixture.journal, "runtime-after-process-death")
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) { cold.service.reconcile(owner, id) }
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) { cold.service.save(request) }
        assertEquals(before, fixture.journal.records.getValue(id))
        assertEquals(1, fixture.requests.count { it.first == ReaderMangaProgressPolicy.METHOD })
        assertTrue(cold.requests.isEmpty()); assertEquals(0, cold.drains)
        assertEquals(id, cold.service.pending().single().operationId)
        // The serialized original changes 41L to a JSON Int, but changing its value or dispatch owner remains forbidden.
        for ((field, value) in listOf("requestId" to 42L, "runtimeSession" to "foreign-runtime", "clientIdentity" to "foreign-client")) {
            val changed = Fixture().apply { mode = "unknown" }
            assertThrows(CoreProtocolPending::class.java) { changed.service.save(request) }
            changed.journal.records[changed.id()] = changed.record().put(field, value).toString()
            val error = assertThrows(IllegalStateException::class.java) { changed.service.pending() }
            assertEquals("MANGA_PROGRESS_WARM_DISPATCH_CHANGED", error.message)
            assertEquals(1, changed.requests.size); assertEquals(0, changed.drains)
        }
    }

    @Test fun exactLateTerminalRequiresSameClientDrainOrColdDurableRecoveryWithoutReplay() {
        val sameClient = Fixture().apply { mode = "unknown" }
        assertThrows(CoreProtocolPending::class.java) { sameClient.service.save(request) }
        sameClient.drainEvent = sameClient.event()
        assertEquals(location, sameClient.service.reconcile(owner, sameClient.id()).location)
        assertEquals(1, sameClient.drains); assertEquals(1, sameClient.requests.size)
        val newClient = Fixture().apply { mode = "unknown" }
        assertThrows(CoreProtocolPending::class.java) { newClient.service.save(request) }
        newClient.client = "new-library-client"
        newClient.deliver(newClient.event()) // Existing App maintenance invokes the original sink on its original lease.
        assertSame(newClient.unknown, assertThrows(CoreProtocolPending::class.java) {
            newClient.service.reconcile(owner, newClient.id())
        }) // There is no post-ACK callback; sink completion alone cannot prove that maintenance ACK succeeded.
        assertEquals(0, newClient.drains); assertEquals(1, newClient.requests.size)
        assertFalse(newClient.record().getBoolean("transportConfirmed")); assertFalse(newClient.record().getBoolean("completed"))
        assertTrue(newClient.service.pending().single().hasOriginalTerminal)
        val cold = Fixture(newClient.journal, "runtime-after-process-death")
        assertEquals(location, cold.service.reconcile(owner, newClient.id()).location)
        assertTrue(cold.requests.isEmpty()); assertEquals(0, cold.drains); assertTrue(cold.service.pending().isEmpty())
    }

    @Test fun wrongOriginalAcknowledgementsAndInvalidCoordinatesRemainUnconfirmed() {
        val corruptions = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("data").getJSONObject("token").put("epoch", 4) },
            { it.getJSONObject("data").getJSONObject("token").put("revision", 2) },
            { it.getJSONObject("data").getJSONObject("location").getJSONObject("chapter").put("bookId", "foreign") },
            { it.getJSONObject("data").getJSONObject("location").put("manifestVersion", "foreign") },
            { it.getJSONObject("data").getJSONObject("location").put("pageId", "mp1:" + "b".repeat(64)) },
            { it.getJSONObject("data").getJSONObject("location").put("y", 0.7) },
        )
        corruptions.forEach { change ->
            val fixture = Fixture().apply { transform = change }
            assertThrows(ReaderMangaProgressUnconfirmed::class.java) { fixture.service.save(request) }
            assertFalse(fixture.record().getBoolean("completed"))
            assertTrue(fixture.service.pending().single().hasOriginalTerminal)
        }
        for (invalid in listOf(location.copy(x = Double.NaN), location.copy(y = 1.1),
            location.copy(pageId = "mp1:" + "b".repeat(64)), location.copy(progressRevision = 2))) {
            val fixture = Fixture()
            assertThrows(IllegalStateException::class.java) { fixture.service.save(request.copy(location = invalid)) }
            assertTrue(fixture.requests.isEmpty()); assertTrue(fixture.journal.records.isEmpty())
        }
    }

    @Test fun failedTerminalSinkRequiresProtocolRecoveryAndNeverConfirmsThroughNewClient() {
        val fixture = Fixture().apply { journal.failTerminal = true }
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) { fixture.service.save(request) })
        assertFalse(fixture.record().has("wireTerminal"))
        assertTrue(fixture.service.pending().single().hasOriginalTerminal) // Warm event survives the failed existing sink.
        fixture.client = "replacement-client"; fixture.journal.failTerminal = false
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) {
            fixture.service.reconcile(owner, fixture.id())
        })
        assertEquals(0, fixture.drains); assertFalse(fixture.record().getBoolean("completed"))
        assertFalse(fixture.record().has("wireTerminal"))
        val coldBeforeSinkRecovery = Fixture(fixture.journal, "runtime-after-process-death")
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) {
            coldBeforeSinkRecovery.service.reconcile(owner, fixture.id())
        } // A warm-only event cannot survive process death or become a cold durable receipt.
        fixture.client = "client-a"
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) {
            fixture.service.reconcile(owner, fixture.id())
        }) // The actor refuses a second ordinary consumption after a sink or ACK failure.
        assertFalse(fixture.record().getBoolean("completed"))
        // Simulate the existing explicit protocol recovery completing the exact retained sink and its ACK.
        fixture.deliver(fixture.event()); fixture.drainRequiresProtocolRecovery = false
        assertEquals(location, fixture.service.reconcile(owner, fixture.id()).location)
        assertTrue(fixture.record().getBoolean("completed")); assertEquals(1, fixture.requests.size)
        assertTrue(fixture.record().getBoolean("transportConfirmed")); assertEquals(2, fixture.drains)
    }

    @Test fun failedCompletionPersistenceAfterConfirmedTransportCanArchiveAcrossClients() {
        val completion = Fixture().apply { journal.failCompletion = true }
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) { completion.service.save(request) }
        assertFalse(completion.record().getBoolean("completed")); assertTrue(completion.service.pending().isNotEmpty())
        completion.client = "replacement-client"; completion.journal.failCompletion = false
        assertEquals(location, completion.service.reconcile(owner, completion.id()).location)
        assertEquals(1, completion.requests.size)
    }

    @Test fun durableTerminalWithFailedAckRemainsPendingUntilExactProtocolRecoveryAndDrain() {
        val fixture = Fixture().apply { mode = "ack-failed" }
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) { fixture.service.save(request) })
        assertTrue(fixture.record().has("wireTerminal")); assertFalse(fixture.record().getBoolean("transportConfirmed"))
        fixture.client = "replacement-client"
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) {
            fixture.service.reconcile(owner, fixture.id())
        })
        assertEquals(0, fixture.drains); assertFalse(fixture.record().getBoolean("completed"))
        fixture.client = "client-a"
        assertSame(fixture.unknown, assertThrows(CoreProtocolPending::class.java) {
            fixture.service.reconcile(owner, fixture.id())
        })
        fixture.drainRequiresProtocolRecovery = false // Exact existing recovery ACKed the consumed delivery; no sink replay.
        assertEquals(location, fixture.service.reconcile(owner, fixture.id()).location)
        assertTrue(fixture.record().getBoolean("completed")); assertEquals(1, fixture.requests.size)
    }

    @Test fun retainedCandidateAndForeignOwnerNeverPromoteOrDispatch() {
        val fixture = Fixture().apply { mode = "unknown" }
        assertThrows(CoreProtocolPending::class.java) { fixture.service.save(request) }
        fixture.journal.candidates += fixture.id()
        val before = fixture.journal.records.getValue(fixture.id()); val writes = fixture.journal.writes
        assertTrue(fixture.service.pending().single().candidateRetained)
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) { fixture.service.reconcile(owner, fixture.id()) }
        assertThrows(IllegalStateException::class.java) {
            fixture.service.reconcile(ReaderMangaChapterRef(owner.sourceId, "foreign-book"), fixture.id())
        }
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) { fixture.service.save(request) }
        assertEquals(before, fixture.journal.records.getValue(fixture.id())); assertEquals(writes, fixture.journal.writes)
        assertEquals(0, fixture.drains); assertEquals(1, fixture.requests.size)
    }

    @Test fun definiteOriginalRejectionCompletesButInternalErrorAndExistingWriterDoNot() {
        val rejected = Fixture().apply { mode = "rejected" }
        assertThrows(CoreCommandException::class.java) { rejected.service.save(request) }
        assertTrue(rejected.record().getBoolean("completed")); assertTrue(rejected.service.pending().isEmpty())
        val unknown = Fixture().apply { mode = "indeterminate" }
        assertThrows(ReaderMangaProgressUnconfirmed::class.java) { unknown.service.save(request) }
        assertFalse(unknown.record().getBoolean("completed")); assertTrue(unknown.service.pending().isNotEmpty())
        val blocked = Fixture().apply { sharedWriterBlocked = true }
        assertThrows(IllegalStateException::class.java) { blocked.service.save(request) }
        assertTrue(blocked.requests.isEmpty()); assertTrue(blocked.journal.records.isEmpty())
    }
}
