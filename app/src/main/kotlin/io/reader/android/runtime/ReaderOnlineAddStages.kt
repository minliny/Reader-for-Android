package io.reader.android.runtime

import org.json.JSONObject

/** Exact existing Journal operations; the port does not own IO, candidate promotion or recovery. */
internal interface ReaderOnlineAddJournalPort {
    fun write(record: JSONObject)
    fun onlineAddOriginalForQuery(id: String): ReaderOnlineAddJournalSnapshot?
    fun onlineAddActiveForQuery(): List<ReaderOnlineAddJournalSnapshot>
}

/** One immutable child per real Core command. Unknowns never dispatch that child again. */
internal class ReaderOnlineAddStages(private val transport: CoreTransport, private val journal: ReaderOnlineAddJournalPort) {
    constructor(transport: CoreTransport, journal: DurableJournal) : this(transport, object : ReaderOnlineAddJournalPort {
        override fun write(record: JSONObject) = journal.write(record)
        override fun onlineAddOriginalForQuery(id: String) = journal.onlineAddOriginalForQuery(id)
        override fun onlineAddActiveForQuery() = journal.onlineAddActiveForQuery()
    })
    private val warm = linkedMapOf<String, JSONObject>()
    fun records(): List<ReaderOnlineAddJournalSnapshot> {
        val disk = journal.onlineAddActiveForQuery().associateBy { JSONObject(it.recordJson).getString("id") }
        return (disk.keys + warm.keys).distinct().mapNotNull { load(it) }
    }
    fun load(id: String): ReaderOnlineAddJournalSnapshot? {
        val disk = journal.onlineAddOriginalForQuery(id); val memory = warm[id]
        if (disk == null) return memory?.let { ReaderOnlineAddJournalSnapshot(JSONObject(it.toString()).put("completed", false).toString(), false, false) }
        val value = JSONObject(disk.recordJson)
        if (memory != null) {
            check(listOf("id", "kind", "sourceId", "bookId", "sourceVersion").all { value.get(it) == memory.get(it) }) { "ONLINE_ADD_WARM_IDENTITY" }
            if (value.getString("kind") == "onlineAdd") {
                check(value.getString("previewJson") == memory.getString("previewJson") && value.getString("pendingAddJson") == memory.getString("pendingAddJson"))
            }
            if (value.getString("kind") == "onlineAddStage") {
                check(!value.getBoolean("attempted") || memory.getBoolean("attempted")) { "ONLINE_ADD_WARM_DISPATCH_DOWNGRADE" }
                check(value.getString("parametersJson") == memory.getString("parametersJson") && value.getString("method") == memory.getString("method"))
                if (value.has("requestId") && memory.has("requestId")) check(value.getLong("requestId") == memory.getLong("requestId") && value.getString("runtimeSession") == memory.getString("runtimeSession"))
                if (value.has("originalTerminalJson") && memory.has("originalTerminalJson")) check(value.getString("originalTerminalJson") == memory.getString("originalTerminalJson"))
                // Same original warm receipt survives failed journal writes. A disk terminal may arrive only through its matching callback.
                if (!memory.has("originalTerminalJson") && value.has("originalTerminalJson")) {
                    check(value.getLong("requestId") == memory.getLong("requestId") && value.getString("runtimeSession") == memory.getString("runtimeSession"))
                    memory.put("originalTerminalJson", value.getString("originalTerminalJson"))
                }
            }
            val selected = if (value.getBoolean("completed")) value else JSONObject(memory.toString()).put("completed", false)
            return ReaderOnlineAddJournalSnapshot(selected.toString(), disk.candidateRetained, selected.toString() == value.toString())
        }
        warm[id] = value
        return disk
    }
    fun requireNoUnknown(parentId: String) {
        records().filter { JSONObject(it.recordJson).optString("parentId") == parentId }.forEach { snapshot ->
            val record = JSONObject(snapshot.recordJson)
            if (snapshot.candidateRetained || (record.getBoolean("attempted") && !record.has("originalTerminalJson")))
                throw ReaderOnlineAddUnconfirmed(parentId, "existing-original-unconfirmed")
        }
    }
    fun release(parentId: String) { warm.keys.removeAll { it == parentId || it.startsWith(parentId + "-") } }
    fun persist(value: JSONObject) {
        ReaderOnlineAddPolicy.record(value); ReaderOnlineAddPolicy.encode(value)
        warm[value.getString("id")] = JSONObject(value.toString()) // Before disk IO; never lose a newly retained original on write failure.
        journal.write(value)
    }
    fun call(parent: JSONObject, label: String, method: String, params: JSONObject): Pair<JSONObject, JSONObject> {
        val id = parent.getString("id") + "-" + label
        val existing = load(id)
        if (existing?.candidateRetained == true) throw ReaderOnlineAddUnconfirmed(parent.getString("id"), "candidate:$label")
        var record = existing?.let { JSONObject(it.recordJson) }
        if (record == null) {
            record = JSONObject().put("journalSchema", 1).put("kind", "onlineAddStage").put("id", id)
                .put("parentId", parent.getString("id")).put("sourceId", parent.getString("sourceId")).put("bookId", parent.getString("bookId"))
                .put("sourceVersion", parent.getString("sourceVersion")).put("method", method)
                .put("parametersJson", params.toString()).put("attempted", false).put("completed", false)
            persist(record)
        }
        check(record.getString("method") == method && record.getString("parametersJson") == params.toString()) { "ONLINE_ADD_ORIGINAL_PARAMS_CHANGED" }
        if (existing != null && !existing.canonicalCommitted) persist(record)
        if (!record.has("originalTerminalJson")) {
            if (record.getBoolean("attempted")) throw ReaderOnlineAddUnconfirmed(parent.getString("id"), "unknown:$label")
            record.put("attempted", true); persist(record) // Persist reservation before invoking the transport even once.
            val exact = record
            val runtime = transport.session
            try {
                transport.request(method, JSONObject(exact.getString("parametersJson")), 60_000,
                    beforeSend = { request -> exact.put("requestId", request).put("runtimeSession", runtime); persist(exact) },
                    terminal = { event ->
                        check(event.getLong("requestId") == exact.getLong("requestId") && exact.getString("runtimeSession") == runtime)
                        exact.put("originalTerminalJson", event.toString()); persist(exact)
                    })
            } catch (error: Exception) {
                if (error.coreProtocolPending() != null) throw error
                if (error !is CoreCommandException || !exact.has("originalTerminalJson")) throw ReaderOnlineAddUnconfirmed(parent.getString("id"), label, error)
                // A definite Core rejection is consumed from this exact original below, never guessed from status.
            }
            record = exact
        }
        val event = ReaderOnlineAddPolicy.original(record)
        if (!record.getBoolean("completed")) { record.put("completed", true); persist(record) }
        warm.remove(id) // Only after its exact terminal is in canonical Completed and its parent owns the child ID.
        return CoreTerminal.result(method, event) to record
    }
}
