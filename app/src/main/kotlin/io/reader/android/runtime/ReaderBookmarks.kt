package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class LibrarySort(internal val wire: String) {
    MANUAL("manual"), ADDED("addedAt"), LAST_READ("lastReadAt"), TITLE("title"), AUTHOR("author"),
}

enum class LibrarySortDirection(internal val wire: String) { ASCENDING("ascending"), DESCENDING("descending") }

data class ReaderBookmark(
    val time: Long,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val scalarOffset: Int,
    val bodyVersion: String,
    val processingVersion: String,
    val excerpt: String,
    val note: String,
    val bookTitle: String,
    val bookAuthor: String,
    val sourceId: String = "local",
) {
    val id: String get() = time.toString()
    val createdAt: Long get() = time
}

data class BookmarkDeleteResult(val time: Long, val deleted: Boolean)
data class PendingBookmark(val id: String, val bookId: String, val action: String)
data class BookmarkOperationResult(
    val operationId: String,
    val action: String,
    val bookmark: ReaderBookmark? = null,
    val deletion: BookmarkDeleteResult? = null,
)

class BookmarkTargetStaleException(val bookId: String, val time: Long) : Exception("BOOKMARK_TARGET_STALE")

/** The Core DTO is the position authority; this adapter only checks its exact identity. */
internal object BookmarkReceiptPolicy {
    private const val MAX_SEQUENCE = 9_007_199_254_740_991L
    private val fingerprint = Regex("[a-f0-9]{64}")
    private val operation = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}/[1-9][0-9]*")

    fun nextSequence(query: JSONObject): Long {
        check(query.has("head")) { "BOOKMARK_AUTHORITY_HEAD_MISSING" }
        if (query.isNull("head")) return 1
        val head = query.getJSONObject("head")
        validateBinding(head)
        val sequence = head.getLong("sequence")
        check(sequence < MAX_SEQUENCE) { "BOOKMARK_SEQUENCE_EXHAUSTED" }
        return sequence + 1
    }

    fun scope(chapter: ReaderChapter): JSONObject = JSONObject().put("sourceId", "local")
        .put("bookId", chapter.bookId).put("chapterIndex", chapter.index)
        .put("bodyVersion", chapter.bodyVersion).put("processingVersion", chapter.processingVersion)

    fun sameScope(left: JSONObject, right: JSONObject): Boolean =
        listOf("sourceId", "bookId", "bodyVersion", "processingVersion").all { left.getString(it) == right.getString(it) } &&
            left.getInt("chapterIndex") == right.getInt("chapterIndex")

    fun definiteRejection(record: JSONObject, event: JSONObject): Boolean {
        check(event.getLong("requestId") == record.getLong("requestId")) { "ORIGINAL_BOOKMARK_ACK_IDENTITY" }
        if (record.optString("stage") != "mutation" || event.getString("type") != "error") return false
        val action = record.getString("action")
        if (action != "create" && action != "delete") return false
        val error = event.getJSONObject("error")
        val details = error.optJSONObject("details")
        if (details?.optBoolean("originalPending") == true) return false
        if (details?.optBoolean("nonExecution") == true) return true
        // Fixed Core a8eb: bookmark_create's check_mark_write/check_mark_offsets
        // return this exact INVALID_PARAMS reason before bookmark_create_receipted.
        // It is not a classification for arbitrary INVALID_PARAMS or delete.
        return action == "create" && error.optString("code") == "INVALID_PARAMS" &&
            details?.optString("reason") == "POSITION_CONTEXT_STALE"
    }

    fun validateResult(record: JSONObject, result: JSONObject) {
        val params = record.getJSONObject("parameters")
        when (record.getString("action")) {
            "create" -> {
                val row = result.getJSONObject("bookmark")
                decode(row)
                check(sameScope(row.getJSONObject("positionScope"), params.getJSONObject("positionScope")) &&
                    row.getInt("chapterIndex") == params.getInt("chapterIndex") &&
                    row.getInt("chapterPos") == params.getInt("chapterPos") &&
                    listOf("bookName", "bookAuthor", "chapterName", "bookText", "content").all {
                        row.getString(it) == params.getString(it)
                    }) { "ORIGINAL_BOOKMARK_CREATE_ACK_MISMATCH" }
                if (!params.isNull("time")) check(row.getLong("time") == params.getLong("time"))
                record.optJSONObject("mutationResult")?.let {
                    check(row.getLong("time") == it.getJSONObject("bookmark").getLong("time")) { "ORIGINAL_BOOKMARK_TIME_CHANGED" }
                }
            }
            "delete" -> {
                check(result.getLong("time") == params.getLong("time")) { "ORIGINAL_BOOKMARK_DELETE_ACK_MISMATCH" }
                val deleted = result.getBoolean("deleted")
                record.optJSONObject("mutationResult")?.let {
                    check(deleted == it.getBoolean("deleted")) { "ORIGINAL_BOOKMARK_DELETE_FACT_CHANGED" }
                }
            }
            else -> error("UNSUPPORTED_BOOKMARK_ACTION")
        }
    }

    fun receipt(record: JSONObject, receipt: JSONObject, settled: Boolean = false): JSONObject {
        val params = record.getJSONObject("parameters")
        val binding = receipt.getJSONObject("binding")
        validateBinding(binding)
        check(binding.getString("operationId") == params.getString("operationId") &&
            binding.getLong("sequence") == params.getLong("sequence")) { "ORIGINAL_BOOKMARK_RECEIPT_IDENTITY" }
        val phase = receipt.getString("phase")
        check(phase == "committed" || phase == "settled") { "ORIGINAL_BOOKMARK_RECEIPT_PHASE" }
        if (settled) check(phase == "settled") { "BOOKMARK_SETTLEMENT_UNCONFIRMED" }
        record.optJSONObject("receipt")?.getJSONObject("binding")?.let {
            check(it.getString("operationId") == binding.getString("operationId") &&
                it.getLong("sequence") == binding.getLong("sequence") &&
                it.getString("payloadFingerprint") == binding.getString("payloadFingerprint")) { "ORIGINAL_BOOKMARK_BINDING_CHANGED" }
        }
        val outcome = receipt.getJSONObject("outcome")
        check(outcome.getString("action") == record.getString("action")) { "ORIGINAL_BOOKMARK_RECEIPT_ACTION" }
        val result = outcome.getJSONObject("result")
        validateResult(record, result)
        return result
    }

    private fun validateBinding(binding: JSONObject) {
        val sequence = binding.getLong("sequence")
        val id = binding.getString("operationId")
        check(sequence in 1..MAX_SEQUENCE && operation.matches(id) &&
            id.substringAfterLast('/') == sequence.toString() && fingerprint.matches(binding.getString("payloadFingerprint"))) {
            "INVALID_BOOKMARK_AUTHORITY_BINDING"
        }
    }

    fun decode(row: JSONObject): ReaderBookmark {
        val scope = row.getJSONObject("positionScope")
        val index = row.getLong("chapterIndex")
        val offset = row.getLong("chapterPos")
        check(scope.getString("sourceId") == "local" && scope.getString("bookId").matches(Regex("local:[a-f0-9]{64}")) &&
            index in 0..Int.MAX_VALUE.toLong() && scope.getLong("chapterIndex") == index &&
            offset in 0..Int.MAX_VALUE.toLong() && scope.getString("bodyVersion").isNotEmpty() &&
            scope.getString("processingVersion").isNotEmpty() && row.getLong("time") > 0) { "INVALID_BOOKMARK_POSITION_SCOPE" }
        return ReaderBookmark(row.getLong("time"), scope.getString("bookId"), index.toInt(), row.getString("chapterName"),
            offset.toInt(), scope.getString("bodyVersion"), scope.getString("processingVersion"), row.getString("bookText"),
            row.getString("content"), row.getString("bookName"), row.getString("bookAuthor"))
    }
}

/** Narrow journal seam for deterministic first-write failure coverage; production uses DurableJournal unchanged. */
internal interface BookmarkJournalPort {
    fun readAll(): List<JSONObject>
    fun exact(id: String, includeCompleted: Boolean = false): JSONObject?
    fun write(record: JSONObject)
    fun terminal(id: String, requestId: Long, session: String, event: JSONObject)
}

/** Warm facts may outlive a VM or a failed archive/fsync. They never create a new mutation. */
internal object BookmarkWarmIntentPolicy {
    fun retainOriginal(disk: JSONObject?, retained: JSONObject?): JSONObject? {
        if (disk == null) return retained
        if (retained == null) return disk
        check(listOf("id", "kind", "bookId", "action", "sessionToken").all { disk.getString(it) == retained.getString(it) } &&
            disk.getString("kind") == "bookmark" && disk.getInt("journalSchema") == retained.getInt("journalSchema") &&
            disk.getJSONObject("parameters").toString() == retained.getJSONObject("parameters").toString() &&
            BookmarkReceiptPolicy.sameScope(disk.getJSONObject("ownerScope"), retained.getJSONObject("ownerScope"))) {
            "BOOKMARK_DURABLE_INTENT_CHANGED"
        }
        if (retained.getString("action") == "delete") check(disk.getLong("targetTime") == retained.getLong("targetTime") &&
            BookmarkReceiptPolicy.sameScope(disk.getJSONObject("targetScope"), retained.getJSONObject("targetScope"))) {
            "BOOKMARK_DELETE_TARGET_CHANGED"
        }
        disk.optJSONObject("mutationResult")?.let { incoming ->
            BookmarkReceiptPolicy.validateResult(retained, incoming)
            if (!retained.has("mutationResult")) retained.put("mutationResult", incoming)
        }
        disk.optJSONObject("receipt")?.let { incoming ->
            check(!retained.has("rejection")) { "BOOKMARK_RECEIPT_REJECTION_CONTRADICTION" }
            val result = BookmarkReceiptPolicy.receipt(retained, incoming)
            if (!retained.has("mutationResult")) retained.put("mutationResult", result)
            // A stale committed disk record cannot downgrade an already settled warm receipt.
            if (retained.optJSONObject("receipt")?.optString("phase") != "settled") retained.put("receipt", incoming)
        }
        val sameDispatch = disk.optLong("requestId", -1) == retained.optLong("requestId", -2) &&
            disk.optString("runtimeSession").isNotEmpty() && disk.optString("runtimeSession") == retained.optString("runtimeSession")
        if (sameDispatch) disk.optJSONObject("wireTerminal")?.let { incoming ->
            check(incoming.getLong("requestId") == retained.getLong("requestId")) { "BOOKMARK_LATE_WIRE_IDENTITY" }
            retained.optJSONObject("wireTerminal")?.let { current ->
                check(current.toString() == incoming.toString()) { "BOOKMARK_ORIGINAL_WIRE_CHANGED" }
            } ?: retained.put("wireTerminal", incoming)
        }
        disk.optJSONObject("rejection")?.let { incoming ->
            check(sameDispatch && !retained.has("receipt") && disk.optBoolean("completed") &&
                disk.optJSONObject("wireTerminal")?.let { BookmarkReceiptPolicy.definiteRejection(disk, it) } == true) {
                "BOOKMARK_REJECTION_UNQUALIFIED"
            }
            retained.optJSONObject("rejection")?.let { check(it.toString() == incoming.toString()) }
            retained.put("rejection", incoming)
        }
        if (disk.optBoolean("completed")) {
            if (!disk.has("rejection")) BookmarkReceiptPolicy.receipt(retained, disk.getJSONObject("receipt"), settled = true)
            retained.put("completed", true)
        }
        return retained // Never replace a newer warm stage/request/history with an older disk object.
    }
}

/** App-owned singleton, invoked only on an actually held Library/maintenance lane. */
internal class ReaderBookmarks(private val transport: CoreTransport, private val journal: BookmarkJournalPort) {
    constructor(transport: CoreTransport, journal: DurableJournal) : this(transport, object : BookmarkJournalPort {
        override fun readAll() = journal.readAll()
        override fun exact(id: String, includeCompleted: Boolean) = journal.exact(id, includeCompleted)
        override fun write(record: JSONObject) = journal.write(record)
        override fun terminal(id: String, requestId: Long, session: String, event: JSONObject) = journal.terminal(id, requestId, session, event)
    })
    private val ownerSession = transport.session // Constructor is reached only from a real held client lane.
    private var warm: JSONObject? = null
    private fun checkOwner() { check(transport.session == ownerSession) { "BOOKMARK_RUNTIME_OWNER_CHANGED" } }
    private fun persist(record: JSONObject) {
        checkOwner()
        warm = record // Assign BEFORE the first attempted durable write, including Completed finalization.
        journal.write(record)
        if (record.optBoolean("completed") && warm === record) warm = null
    }

    fun list(bookId: String, title: String, author: String, query: String? = null): List<ReaderBookmark> {
        checkOwner()
        val params = JSONObject().put("bookName", title).put("bookAuthor", author).put("sortBy", "bookLocation")
        query?.takeIf { it.isNotBlank() }?.let { params.put("query", it) }
        val rows = transport.request("bookmark.list", params).getJSONArray("bookmarks")
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val scope = row.optJSONObject("positionScope")
            // A legacy same-title mark is not evidence that it belongs to this book.
            if (scope == null || scope.optString("sourceId") != "local" || scope.optString("bookId") != bookId) null
            else BookmarkReceiptPolicy.decode(row)
        }
    }

    fun create(chapter: ReaderChapter, title: String, author: String, scalarOffset: Int, excerpt: String, note: String): ReaderBookmark {
        require(scalarOffset in 0..chapter.scalarCount)
        val params = JSONObject().put("positionScope", BookmarkReceiptPolicy.scope(chapter))
            .put("bookName", title).put("bookAuthor", author).put("chapterIndex", chapter.index)
            .put("chapterPos", scalarOffset).put("chapterName", chapter.title).put("bookText", excerpt).put("content", note)
        return mutate("create", chapter, params).bookmark ?: error("ORIGINAL_BOOKMARK_CREATE_RESULT_MISSING")
    }

    fun delete(chapter: ReaderChapter, bookmark: ReaderBookmark): BookmarkDeleteResult =
        mutate("delete", chapter, JSONObject().put("time", bookmark.time), bookmark).deletion
            ?: error("ORIGINAL_BOOKMARK_DELETE_RESULT_MISSING")

    fun pending(): List<PendingBookmark> {
        checkOwner()
        val records = journal.readAll().filter { it.getString("kind") == "bookmark" && !it.optBoolean("completed") }.toMutableList()
        warm?.let { original ->
            val index = records.indexOfFirst { it.getString("id") == original.getString("id") }
            if (index < 0) records += original
            else records[index] = BookmarkWarmIntentPolicy.retainOriginal(records[index], original)!!
        }
        // A warm completed record still blocks until its failed durable finalization is repaired.
        return records.map { PendingBookmark(it.getString("id"), it.getString("bookId"), it.getString("action")) }
    }

    fun requireWritable() {
        pending().firstOrNull()?.let { throw PendingOperationException(it.id, "bookmark.${it.action}") }
    }

    private fun mutate(action: String, chapter: ReaderChapter, params: JSONObject, target: ReaderBookmark? = null): BookmarkOperationResult {
        requireWritable()
        val sequence = BookmarkReceiptPolicy.nextSequence(transport.request("bookmark.operation.query"))
        val uuid = UUID.randomUUID().toString()
        params.put("operationId", "$uuid/$sequence").put("sequence", sequence)
        val record = JSONObject().put("journalSchema", 1).put("kind", "bookmark").put("id", "android-bookmark-$uuid")
            .put("bookId", chapter.bookId).put("action", action).put("parameters", params)
            .put("sessionToken", chapter.sessionToken).put("ownerScope", BookmarkReceiptPolicy.scope(chapter))
            .put("completed", false).put("dispatchHistory", JSONArray())
        target?.let { record.put("targetTime", it.time).put("targetScope", JSONObject().put("sourceId", it.sourceId)
            .put("bookId", it.bookId).put("chapterIndex", it.chapterIndex).put("bodyVersion", it.bodyVersion)
            .put("processingVersion", it.processingVersion)) }
        warm = record
        try {
            send(record, "bookmark.$action", params, "mutation")
        } catch (error: Throwable) {
            consumeMutationTerminal(record)
            if (record.optBoolean("completed")) throw error
            throw PendingOperationException(record.getString("id"), "bookmark.$action")
        }
        consumeMutationTerminal(record)
        return finish(record)
    }

    fun reconcile(bookId: String, id: String): BookmarkOperationResult {
        checkOwner()
        pending().firstOrNull { it.id != id }?.let { throw PendingOperationException(it.id, "bookmark.operation.query") }
        val retained = warm?.takeIf { it.getString("id") == id }
        val record = BookmarkWarmIntentPolicy.retainOriginal(journal.exact(id, includeCompleted = true), retained)
            ?: throw PendingOperationException(id, "bookmark.operation.query")
        check(record.getString("kind") == "bookmark" && record.getString("bookId") == bookId) { "ORIGINAL_BOOKMARK_OWNER_MISMATCH" }
        validateOwner(record)
        if (record.optBoolean("completed")) {
            // A prior archive/fsync failure is repaired once; an already durable Completed record is read-only.
            if (retained != null) persist(record)
            record.optJSONObject("rejection")?.let { throw CoreCommandException("bookmark.${record.getString("action")}", it) }
            return result(record, BookmarkReceiptPolicy.receipt(record, record.getJSONObject("receipt"), settled = true))
        }
        warm = record
        if (record.optJSONObject("rejection") != null) throw CoreCommandException("bookmark.${record.getString("action")}", record.getJSONObject("rejection"))
        if (record.optString("stage") == "mutation") consumeMutationTerminal(record)
        // Only exact original query can recover a lost mutation ACK. No mutation replay.
        return finish(record)
    }

    private fun consumeMutationTerminal(record: JSONObject) {
        if (record.optString("stage") != "mutation") return
        val event = record.optJSONObject("wireTerminal") ?: return
        check(event.getLong("requestId") == record.getLong("requestId")) { "ORIGINAL_BOOKMARK_ACK_IDENTITY" }
        if (event.getString("type") == "error") {
            val error = event.getJSONObject("error")
            if (BookmarkReceiptPolicy.definiteRejection(record, event)) {
                record.put("rejection", error).put("completed", true)
                persist(record)
                throw CoreCommandException("bookmark.${record.getString("action")}", error)
            }
            return
        }
        check(event.getString("type") == "result") { "EXPECTED_BOOKMARK_TERMINAL" }
        val data = event.getJSONObject("data")
        BookmarkReceiptPolicy.validateResult(record, data)
        record.put("mutationTerminal", event).put("mutationResult", data)
        persist(record)
    }

    private fun finish(record: JSONObject): BookmarkOperationResult {
        validateOwner(record)
        val id = record.getString("id")
        val query = try {
            transport.request("bookmark.operation.query", JSONObject()
                .put("operationId", record.getJSONObject("parameters").getString("operationId"))
                .put("expected", JSONObject().put("action", record.getString("action")).put("params", record.getJSONObject("parameters"))))
        } catch (_: CoreReadTimeoutException) { throw PendingOperationException(id, "bookmark.operation.query") }
        // query's single poll owner may have just consumed a late original ACK.
        // Merge that exact generation before this older in-memory record is saved.
        val latest = journal.exact(id)
        BookmarkWarmIntentPolicy.retainOriginal(latest, record)
        val receipt = query.optJSONObject("receipt")
        if (receipt == null) {
            consumeMutationTerminal(record)
            throw PendingOperationException(id, "bookmark.${record.getString("action")}")
        }
        if (record.optString("stage") == "mutation") {
            val event = record.optJSONObject("wireTerminal")
            check(event == null || !BookmarkReceiptPolicy.definiteRejection(record, event)) {
                "BOOKMARK_QUERY_CONTRADICTS_ORIGINAL_REJECTION"
            }
            consumeMutationTerminal(record)
        }
        val original = BookmarkReceiptPolicy.receipt(record, receipt)
        // Persist original terminal facts and exact Core fingerprint before metadata settlement.
        record.put("mutationResult", original).put("receipt", receipt)
        persist(record)
        if (receipt.getString("phase") != "settled") {
            try {
                val settled = send(record, "bookmark.operation.settle", receipt.getJSONObject("binding"), "settle")
                BookmarkReceiptPolicy.receipt(record, settled, settled = true)
                record.put("receipt", settled)
            } catch (_: Throwable) { throw PendingOperationException(id, "bookmark.operation.settle") }
        }
        record.put("completed", true)
        persist(record)
        return result(record, original)
    }

    private fun validateOwner(record: JSONObject) {
        val scope = record.getJSONObject("ownerScope")
        val bookId = record.getString("bookId")
        check(record.getString("sessionToken").isNotEmpty() && scope.getString("sourceId") == "local" &&
            scope.getString("bookId") == bookId && scope.getLong("chapterIndex") in 0..Int.MAX_VALUE.toLong() &&
            scope.getString("bodyVersion").isNotEmpty() && scope.getString("processingVersion").isNotEmpty()) {
            "ORIGINAL_BOOKMARK_OWNER_SCOPE_MISMATCH"
        }
        val params = record.getJSONObject("parameters")
        when (record.getString("action")) {
            "create" -> check(BookmarkReceiptPolicy.sameScope(scope, params.getJSONObject("positionScope"))) {
                "ORIGINAL_BOOKMARK_CREATE_OWNER_MISMATCH"
            }
            "delete" -> {
                val target = record.getJSONObject("targetScope")
                check(params.getLong("time") == record.getLong("targetTime") && target.getString("sourceId") == "local" &&
                    target.getString("bookId") == bookId && target.getLong("chapterIndex") in 0..Int.MAX_VALUE.toLong() &&
                    target.getString("bodyVersion").isNotEmpty() && target.getString("processingVersion").isNotEmpty()) {
                    "ORIGINAL_BOOKMARK_DELETE_OWNER_MISMATCH"
                }
            }
            else -> error("UNSUPPORTED_BOOKMARK_ACTION")
        }
    }

    private fun send(record: JSONObject, method: String, params: JSONObject, stage: String): JSONObject {
        record.optJSONObject("wireTerminal")?.let { terminal ->
            val history = record.getJSONArray("dispatchHistory")
            for (index in 0 until history.length()) {
                val dispatch = history.getJSONObject(index)
                if (dispatch.getLong("requestId") == terminal.getLong("requestId") &&
                    dispatch.getString("runtimeSession") == record.getString("runtimeSession")) dispatch.put("terminal", terminal)
            }
        }
        record.put("stage", stage).remove("wireTerminal")
        return transport.request(method, params, beforeSend = { requestId ->
            record.put("requestId", requestId).put("runtimeSession", transport.session)
            record.getJSONArray("dispatchHistory").put(JSONObject().put("requestId", requestId)
                .put("runtimeSession", transport.session).put("method", method).put("stage", stage).put("params", params))
            persist(record)
        }, terminal = { event ->
            if (record.optLong("requestId", -1) == event.getLong("requestId")) record.put("wireTerminal", event)
            journal.terminal(record.getString("id"), event.getLong("requestId"), transport.session, event)
        })
    }

    private fun result(record: JSONObject, data: JSONObject): BookmarkOperationResult = when (record.getString("action")) {
        "create" -> BookmarkOperationResult(record.getString("id"), "create", bookmark = BookmarkReceiptPolicy.decode(data.getJSONObject("bookmark")))
        "delete" -> BookmarkOperationResult(record.getString("id"), "delete", deletion = BookmarkDeleteResult(data.getLong("time"), data.getBoolean("deleted")))
        else -> error("UNSUPPORTED_BOOKMARK_ACTION")
    }
}
