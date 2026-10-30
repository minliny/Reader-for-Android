package io.reader.android.runtime

import org.json.JSONObject
import java.util.UUID

internal data class ReaderMangaProgressJournalSnapshot(val recordJson: String, val candidateRetained: Boolean,
    val canonicalCommitted: Boolean = true)

/** A narrow adapter over the existing durable journal, not a second persistence implementation. */
internal interface ReaderMangaProgressJournalPort {
    fun write(record: JSONObject)
    fun terminal(id: String, requestId: Long, session: String, event: JSONObject)
    fun original(id: String): ReaderMangaProgressJournalSnapshot?
    fun active(): List<ReaderMangaProgressJournalSnapshot>
}

/** App-owned and gate-confined. An unresolved original survives Library/client replacement. */
internal class ReaderMangaProgressService(
    private val transport: CoreTransport,
    private val journal: ReaderMangaProgressJournalPort,
    private val assertHeld: () -> Unit,
    private val currentClient: () -> String,
    private val currentRuntime: () -> String?,
    private val beforeNewWrite: () -> Unit,
) {
    constructor(transport: CoreTransport, journal: DurableJournal, assertHeld: () -> Unit,
        currentClient: () -> String, currentRuntime: () -> String?, beforeNewWrite: () -> Unit) : this(transport,
        object : ReaderMangaProgressJournalPort {
            override fun write(record: JSONObject) = journal.write(record)
            override fun terminal(id: String, requestId: Long, session: String, event: JSONObject) = journal.terminal(id, requestId, session, event)
            override fun original(id: String) = journal.mangaProgressOriginalForQuery(id)
            override fun active() = journal.mangaProgressActiveForQuery()
        }, assertHeld, currentClient, currentRuntime, beforeNewWrite)

    private val warm = linkedMapOf<String, JSONObject>()
    private val protocolFailures = linkedMapOf<String, CoreProtocolPending>()
    private var dispatching: String? = null

    /** Observation is not an acknowledgement and never changes a pending original. */
    fun get(owner: ReaderMangaChapterRef): ReaderMangaProgressState {
        assertHeld(); ReaderMangaProgressPolicy.reference(owner)
        return ReaderMangaProgressPolicy.state(transport.request("reading.progress.get", JSONObject().put("kind", "manga")
            .put("sourceId", owner.sourceId).put("bookId", owner.bookId)), owner)
    }

    fun pending(): List<ReaderMangaProgressPending> {
        assertHeld()
        val ids = journal.active().map { JSONObject(it.recordJson).getString("id") } + warm.keys
        return ids.distinct().mapNotNull { id ->
            val snapshot = checkNotNull(load(id)) { "MANGA_PROGRESS_ORIGINAL_MISSING" }
            val record = JSONObject(snapshot.recordJson)
            if (record.getBoolean("completed") && snapshot.canonicalCommitted && !snapshot.candidateRetained) null
            else ReaderMangaProgressPending(id, ReaderMangaProgressPolicy.owner(record), snapshot.candidateRetained, record.has("wireTerminal"))
        }
    }

    /** Existing shared writer admission calls this before dispatch; the one reserved original may enter once. */
    fun requireSharedWriterAdmission(method: String, params: JSONObject) {
        assertHeld()
        pending().firstOrNull { pending ->
            val original = warm[pending.operationId]
            pending.operationId != dispatching || method != ReaderMangaProgressPolicy.METHOD || original == null ||
                original.getString("parametersJson") != params.toString()
        }?.let { throw ReaderMangaProgressUnconfirmed(it.operationId) }
    }

    fun save(request: ReaderMangaProgressSaveRequest): ReaderMangaProgressState {
        assertHeld()
        val parameters = ReaderMangaProgressPolicy.parameters(request)
        pending().firstOrNull()?.let { throw ReaderMangaProgressUnconfirmed(it.operationId) }
        beforeNewWrite()
        val id = "android-manga-progress-${UUID.randomUUID()}"
        val record = JSONObject().put("journalSchema", 1).put("kind", ReaderMangaProgressPolicy.KIND).put("id", id)
            .put("method", ReaderMangaProgressPolicy.METHOD).put("sourceId", request.location.chapter.sourceId)
            .put("bookId", request.location.chapter.bookId).put("parametersJson", parameters.toString())
            .put("attempted", false).put("completed", false).put("transportConfirmed", false)
        var failure: Throwable? = null
        var confirmed: ReaderMangaProgressState? = null
        try {
            persist(record)
            record.put("attempted", true); persist(record) // Reservation precedes even the first native admission.
            val runtime = transport.session; val client = currentClient()
            withProgressTerminalCompletion(request = {
                try {
                    check(dispatching == null) { "MANGA_PROGRESS_NESTED_DISPATCH" }
                    dispatching = id
                    transport.request(ReaderMangaProgressPolicy.METHOD, JSONObject(record.getString("parametersJson")),
                        beforeSend = { requestId ->
                            record.put("requestId", requestId).put("runtimeSession", runtime).put("clientIdentity", client)
                            persist(record)
                        }, terminal = { event ->
                            assertHeld(); ReaderMangaProgressPolicy.terminal(record, event)
                            val previous = record.optJSONObject("wireTerminal")
                            check(previous == null || previous.toString() == event.toString()) { "MANGA_PROGRESS_ORIGINAL_TERMINAL_CHANGED" }
                            record.put("wireTerminal", JSONObject(event.toString()))
                            retain(record) // Keep the same actual event if the existing journal sink fails.
                            journal.terminal(id, record.getLong("requestId"), runtime, event)
                        })
                    check(record.has("wireTerminal")) { "MANGA_PROGRESS_TRANSPORT_RETURNED_WITHOUT_ORIGINAL" }
                    record.put("transportConfirmed", true); retain(record)
                } catch (error: Throwable) {
                    failure = error
                    error.coreProtocolPending()?.let { protocolFailures[id] = it }
                    // The existing bridge emits CoreCommandException only after this original's ACK.
                    if (error is CoreCommandException && record.has("wireTerminal")) {
                        record.put("transportConfirmed", true); retain(record)
                    }
                    throw error
                }
                finally { dispatching = null }
            }, complete = {
                // A protocol failure keeps the exact original pending until explicit recovery/reconcile.
                if (failure?.coreProtocolPending() == null && record.has("wireTerminal")) confirmed = complete(record)
            })
            return confirmed ?: throw ReaderMangaProgressUnconfirmed(id)
        } catch (error: Exception) {
            if (error.coreProtocolPending() != null || error is CoreCommandException || error is ReaderMangaProgressUnconfirmed) throw error
            throw ReaderMangaProgressUnconfirmed(id, error)
        }
    }

    /** Exact original only: no get/query-as-receipt, new update, automatic replay or foreign-client drain. */
    fun reconcile(owner: ReaderMangaChapterRef, id: String): ReaderMangaProgressState {
        assertHeld(); ReaderMangaProgressPolicy.reference(owner)
        var snapshot = load(id) ?: throw ReaderMangaProgressUnconfirmed(id)
        var record = JSONObject(snapshot.recordJson)
        check(ReaderMangaProgressPolicy.owner(record) == owner) { "MANGA_PROGRESS_RECONCILE_OWNER" }
        if (snapshot.candidateRetained) throw ReaderMangaProgressUnconfirmed(id)
        if (!record.getBoolean("transportConfirmed") && record.optString("runtimeSession").isNotEmpty() &&
            record.optString("runtimeSession") == currentRuntime() && record.optString("clientIdentity") == currentClient()) {
            try { transport.drainOriginalTerminal(record.getLong("requestId")) }
            catch (error: Exception) {
                error.coreProtocolPending()?.let { protocolFailures[id] = it }
                throw protocolFailures[id] ?: ReaderMangaProgressUnconfirmed(id, error)
            }
            snapshot = load(id) ?: throw ReaderMangaProgressUnconfirmed(id)
            record = JSONObject(snapshot.recordJson)
            if (snapshot.candidateRetained) throw ReaderMangaProgressUnconfirmed(id)
            if (record.has("wireTerminal")) {
                // The exact same-client drain must return normally. A failed sink/ACK throws and never reaches here.
                record.put("transportConfirmed", true); persist(record)
                protocolFailures.remove(id)
                snapshot = checkNotNull(load(id))
            }
        }
        if (!record.has("wireTerminal")) throw protocolFailures[id] ?: ReaderMangaProgressUnconfirmed(id)
        return complete(record, snapshot.canonicalCommitted)
    }

    private fun complete(record: JSONObject, canonicalCommitted: Boolean = false): ReaderMangaProgressState {
        val id = record.getString("id")
        val event = record.getJSONObject("wireTerminal")
        val coldDurableOriginal = canonicalCommitted && record.getString("runtimeSession") != currentRuntime()
        if (!record.getBoolean("transportConfirmed") && !coldDurableOriginal) {
            throw protocolFailures[id] ?: ReaderMangaProgressUnconfirmed(id)
        }
        val result = if (event.getString("type") == "result") ReaderMangaProgressPolicy.acknowledgement(record, event)
        else {
            if (!ReaderMangaProgressPolicy.definiteRejection(record, event)) throw ReaderMangaProgressUnconfirmed(id)
            null
        }
        if (!record.getBoolean("completed") || !canonicalCommitted) {
            record.put("completed", true)
            persist(record)
        }
        warm.remove(id)
        protocolFailures.remove(id)
        if (result == null) CoreTerminal.result(ReaderMangaProgressPolicy.METHOD, event)
        return checkNotNull(result)
    }

    private fun retain(record: JSONObject) {
        ReaderMangaProgressPolicy.encodeRecord(record)
        warm[record.getString("id")] = record
    }
    private fun persist(record: JSONObject) { retain(record); journal.write(record) }

    private fun load(id: String): ReaderMangaProgressJournalSnapshot? {
        check(ReaderMangaProgressPolicy.id.matches(id)) { "MANGA_PROGRESS_ORIGINAL_ID" }
        val disk = journal.original(id)
        val memory = warm[id]
        if (disk == null) return memory?.let { ReaderMangaProgressJournalSnapshot(it.toString(), false, false) }
        val committed = JSONObject(disk.recordJson).also(ReaderMangaProgressPolicy::record)
        if (memory == null) return disk
        for (field in listOf("id", "kind", "method", "sourceId", "bookId", "parametersJson")) {
            check(committed.get(field) == memory.get(field)) { "MANGA_PROGRESS_WARM_ORIGINAL_CHANGED" }
        }
        check(!committed.getBoolean("attempted") || memory.getBoolean("attempted")) { "MANGA_PROGRESS_WARM_DISPATCH_DOWNGRADE" }
        if (committed.has("requestId")) {
            // JSON round-trips may represent a small Long as Int; compare exact integral identity, not boxed types.
            check(ReaderMangaProgressPolicy.integer(committed, "requestId") == ReaderMangaProgressPolicy.integer(memory, "requestId") &&
                listOf("runtimeSession", "clientIdentity").all { committed.getString(it) == memory.getString(it) }) {
                "MANGA_PROGRESS_WARM_DISPATCH_CHANGED"
            }
        }
        committed.optJSONObject("wireTerminal")?.let { event ->
            val previous = memory.optJSONObject("wireTerminal")
            check(previous == null || previous.toString() == event.toString()) { "MANGA_PROGRESS_WARM_TERMINAL_CHANGED" }
            if (previous == null) memory.put("wireTerminal", JSONObject(event.toString()))
        }
        val selected = if (committed.getBoolean("completed")) committed else memory
        return ReaderMangaProgressJournalSnapshot(selected.toString(), disk.candidateRetained,
            disk.canonicalCommitted && selected.toString() == committed.toString())
    }
}
