package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Collections

data class ShelfGroup(
    val groupId: Long,
    val groupName: String,
    val order: Int,
    val enableRefresh: Boolean,
    val show: Boolean,
    val cover: String? = null,
)

data class ShelfGroupDeletion(val groupId: Long, val deleted: Boolean)
data class ShelfGroupAssignment(val sourceId: String, val bookId: String, val group: String?)
data class ShelfBookKey(val sourceId: String, val bookId: String)
data class ShelfSingleRemoval(val target: ShelfBookKey, val removed: Boolean)
data class ShelfBatchRemoval(
    val requestedCount: Long,
    val uniqueCount: Long,
    val removedTargets: List<ShelfBookKey>,
    val missingTargets: List<ShelfBookKey>,
    val duplicateTargets: List<ShelfBookKey>,
)

/** Core evidence only. Its presence NEVER authorizes deletion of a Host file. */
data class ShelfRemovalCoreReceipt(
    val hostOperationId: String,
    val coreOperationId: String,
    val sequence: Long,
    val payloadFingerprint: String,
    val ackFingerprint: String,
    val method: String,
    val confirmation: ManagementConfirmation,
    val originalTargetsJson: String,
    val originalParametersJson: String,
    val receiptJson: String,
    val originalDispatchHistoryJson: String,
)
enum class ManagementConfirmation { UNKNOWN, APPLIED, REJECTED }
data class ManagementRejection(val code: String, val message: String, val originalErrorJson: String)
data class PendingManagement(
    val id: String,
    val method: String,
    val confirmation: ManagementConfirmation,
    val canContinueOriginal: Boolean,
)
data class ManagementResult(
    val operationId: String,
    val method: String,
    val confirmation: ManagementConfirmation,
    val canContinueOriginal: Boolean = false,
    val group: ShelfGroup? = null,
    val deletion: ShelfGroupDeletion? = null,
    val assignment: ShelfGroupAssignment? = null,
    val rejection: ManagementRejection? = null,
    val removal: ShelfSingleRemoval? = null,
    val batchRemoval: ShelfBatchRemoval? = null,
    // Attached by the service only AFTER its exact qualified receipt was persisted.
    val coreRemovalReceipt: ShelfRemovalCoreReceipt? = null,
)

/** Future Host owner data only. This service never executes cleanup or marks it completed. */
data class ShelfRemovalOwner(
    val operationId: String,
    val originalTargetsJson: String,
    val coreReceiptJson: String?,
    val cleanupOwnerId: String,
    val hostCleanupCompleted: Boolean,
)

/** Core's management head is one latest receipt, not the bookmark settle/history domain. */
internal object ManagementReceiptPolicy {
    const val KIND = "bookshelfManagement"
    private const val MAX_SEQUENCE = 9_007_199_254_740_991L
    private val supported = setOf("book-group.create", "book-group.update", "book-group.delete", "bookshelf.group.assign",
        "bookshelf.remove", "bookshelf.removeBatch")
    private val fingerprint = Regex("[a-f0-9]{64}")
    private val operation = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}/[1-9][0-9]*")

    fun nextSequence(query: JSONObject): Long {
        check(query.has("head") && query.has("receipt") && query.isNull("receipt") && !boolean(query, "canApplySameOriginal")) {
            "MANAGEMENT_HEAD_QUERY_MISMATCH"
        }
        val head = if (query.isNull("head")) null else query.getJSONObject("head").also { binding(it) }
        val previous = head?.let { integer(it, "sequence") } ?: 0L
        check(previous < MAX_SEQUENCE) { "MANAGEMENT_SEQUENCE_EXHAUSTED" }
        return previous + 1
    }

    /** Returns a new decoded copy. Transport/caller mutations cannot alter the durable intent string. */
    fun parameters(record: JSONObject): JSONObject {
        check(record.getString("kind") == KIND) { "MANAGEMENT_JOURNAL_KIND" }
        val params = JSONObject(record.getString("parametersJson"))
        val sequence = integer(params, "sequence")
        val id = string(params, "operationId")
        check(sequence in 1..MAX_SEQUENCE && operation.matches(id) && id.substringAfterLast('/') == sequence.toString()) {
            "MANAGEMENT_ORIGINAL_IDENTITY"
        }
        val mutation = params.getJSONObject("mutation")
        val method = string(mutation, "method")
        check(method in supported && record.getString("method") == method) { "MANAGEMENT_METHOD_REQUIRES_ITS_OWN_OWNER" }
        val business = mutation.getJSONObject("params")
        if (method == "bookshelf.remove" || method == "bookshelf.removeBatch") removalTargets(method, business)
        return params
    }

    fun wireReceipt(record: JSONObject, event: JSONObject): JSONObject {
        check(integer(event, "protocolVersion") == 1L && integer(event, "requestId") == integer(record, "requestId") &&
            event.getString("type") == "result") { "MANAGEMENT_ORIGINAL_WIRE_UNCONFIRMED" }
        return event.getJSONObject("data").also { receipt(record, it) }
    }

    fun receipt(record: JSONObject, receipt: JSONObject): ManagementResult {
        val params = parameters(record)
        val method = record.getString("method")
        val b = receipt.getJSONObject("binding")
        binding(b)
        check(string(b, "operationId") == string(params, "operationId") && integer(b, "sequence") == integer(params, "sequence") &&
            string(receipt, "method") == method && fingerprint.matches(string(receipt, "ackFingerprint"))) {
            "MANAGEMENT_ORIGINAL_RECEIPT_MISMATCH"
        }
        val outcome = string(receipt, "outcome")
        check(outcome == "applied" || outcome == "rejected") { "MANAGEMENT_RECEIPT_OUTCOME" }
        val data = receipt.getJSONObject("data")
        val business = params.getJSONObject("mutation").getJSONObject("params")
        val result = if (outcome == "rejected") {
            val error = data.getJSONObject("error")
            ManagementResult(record.getString("id"), method, ManagementConfirmation.REJECTED,
                rejection = ManagementRejection(string(error, "code"), string(error, "message"), error.toString()))
        } else when (method) {
            "book-group.create", "book-group.update" -> {
                val group = group(data.getJSONObject("group"))
                if (method == "book-group.update" || business.has("groupId")) {
                    check(group.groupId == integer(business, "groupId")) { "MANAGEMENT_GROUP_ID_CHANGED" }
                }
                if (business.has("groupName")) check(group.groupName == string(business, "groupName")) { "MANAGEMENT_GROUP_NAME_CHANGED" }
                if (business.has("order")) check(group.order.toLong() == integer(business, "order")) { "MANAGEMENT_GROUP_ORDER_CHANGED" }
                if (business.has("enableRefresh")) check(group.enableRefresh == boolean(business, "enableRefresh"))
                if (business.has("show")) check(group.show == boolean(business, "show"))
                if (business.has("cover") && !business.isNull("cover")) check(group.cover == string(business, "cover"))
                ManagementResult(record.getString("id"), method, ManagementConfirmation.APPLIED, group = group)
            }
            "book-group.delete" -> {
                check(integer(data, "groupId") == integer(business, "groupId")) { "MANAGEMENT_DELETE_ID_CHANGED" }
                ManagementResult(record.getString("id"), method, ManagementConfirmation.APPLIED,
                    deletion = ShelfGroupDeletion(integer(data, "groupId"), boolean(data, "deleted")))
            }
            "bookshelf.group.assign" -> {
                val book = data.getJSONObject("book")
                check(string(book, "sourceId") == string(business, "sourceId") && string(book, "bookId") == string(business, "bookId")) {
                    "MANAGEMENT_ASSIGNMENT_ID_CHANGED"
                }
                // Core owns Unicode group normalization. The exact expected DTO
                // query binds the original literal string without recreating it here.
                string(business, "group")
                ManagementResult(record.getString("id"), method, ManagementConfirmation.APPLIED,
                    assignment = ShelfGroupAssignment(string(book, "sourceId"), string(book, "bookId"), optionalString(book, "group")))
            }
            "bookshelf.remove" -> ManagementResult(record.getString("id"), method, ManagementConfirmation.APPLIED,
                removal = ShelfSingleRemoval(removalTargets(method, business).single(), boolean(data, "removed")))
            "bookshelf.removeBatch" -> ManagementResult(record.getString("id"), method, ManagementConfirmation.APPLIED,
                batchRemoval = batchRemoval(business, data))
            else -> error("MANAGEMENT_METHOD_REQUIRES_ITS_OWN_OWNER")
        }
        record.optString("receiptJson").takeIf { it.isNotEmpty() }?.let { originalJson ->
            val original = JSONObject(originalJson)
            val previous = original.getJSONObject("binding")
            check(sameBinding(previous, b) && string(original, "ackFingerprint") == string(receipt, "ackFingerprint") &&
                string(original, "outcome") == outcome) { "MANAGEMENT_ORIGINAL_RECEIPT_CHANGED" }
            val originalData = original.getJSONObject("data")
            if (outcome == "applied") when (method) {
                "book-group.create", "book-group.update" -> check(group(originalData.getJSONObject("group")) == result.group) {
                    "MANAGEMENT_ORIGINAL_GROUP_RESULT_CHANGED"
                }
                "book-group.delete" -> check(ShelfGroupDeletion(integer(originalData, "groupId"), boolean(originalData, "deleted")) == result.deletion) {
                    "MANAGEMENT_ORIGINAL_DELETE_FACT_CHANGED"
                }
                "bookshelf.group.assign" -> {
                    val oldBook = originalData.getJSONObject("book")
                    check(ShelfGroupAssignment(string(oldBook, "sourceId"), string(oldBook, "bookId"), optionalString(oldBook, "group")) == result.assignment) {
                        "MANAGEMENT_ORIGINAL_ASSIGNMENT_CHANGED"
                    }
                }
                "bookshelf.remove" -> check(ShelfSingleRemoval(removalTargets(method, business).single(), boolean(originalData, "removed")) == result.removal) {
                    "MANAGEMENT_ORIGINAL_REMOVED_FACT_CHANGED"
                }
                "bookshelf.removeBatch" -> check(batchRemoval(business, originalData) == result.batchRemoval) {
                    "MANAGEMENT_ORIGINAL_BATCH_REMOVAL_CHANGED"
                }
            }
        }
        return result
    }

    /** Literal request order and duplicates are frozen; Core alone performs the atomic removal. */
    fun removalTargets(method: String, business: JSONObject): List<ShelfBookKey> = when (method) {
        "bookshelf.remove" -> immutable(listOf(target(business)))
        "bookshelf.removeBatch" -> {
            val values = business.getJSONArray("targets")
            check(values.length() > 0) { "MANAGEMENT_REMOVAL_TARGETS_EMPTY" }
            immutable((0 until values.length()).map { target(values.getJSONObject(it)) })
        }
        else -> error("MANAGEMENT_NOT_A_REMOVAL")
    }

    fun targetsJson(targets: List<ShelfBookKey>): String = JSONArray().apply {
        targets.forEach { put(JSONObject().put("sourceId", it.sourceId).put("bookId", it.bookId)) }
    }.toString()

    private fun target(value: JSONObject) = ShelfBookKey(string(value, "sourceId"), string(value, "bookId"))

    private fun batchRemoval(original: JSONObject, data: JSONObject): ShelfBatchRemoval {
        val requested = removalTargets("bookshelf.removeBatch", original)
        val unique = requested.distinct() // Verification only, never a changed dispatch DTO.
        val seen = mutableSetOf<ShelfBookKey>()
        val expectedDuplicates = requested.filter { !seen.add(it) }
        fun targets(key: String): List<ShelfBookKey> {
            val values = data.getJSONArray(key)
            return immutable((0 until values.length()).map { target(values.getJSONObject(it)) })
        }
        val removed = targets("removedTargets")
        val missing = targets("missingTargets")
        val duplicates = targets("duplicateTargets")
        check(integer(data, "requestedCount") == requested.size.toLong() && integer(data, "uniqueCount") == unique.size.toLong()) {
            "MANAGEMENT_REMOVAL_COUNT_CHANGED"
        }
        val removedSet = removed.toSet()
        val missingSet = missing.toSet()
        check(removedSet.size == removed.size && missingSet.size == missing.size && removedSet.intersect(missingSet).isEmpty() &&
            removedSet + missingSet == unique.toSet() && removed == unique.filter { it in removedSet } &&
            missing == unique.filter { it in missingSet } && duplicates == expectedDuplicates) {
            "MANAGEMENT_REMOVAL_EXACT_TARGETS_CHANGED"
        }
        return ShelfBatchRemoval(requested.size.toLong(), unique.size.toLong(), removed, missing, duplicates)
    }

    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

    /** The caller sends this query with expected=parameters(record), never a shelf/group list. */
    fun query(record: JSONObject, queryData: JSONObject): JSONObject? {
        val params = parameters(record)
        check(queryData.has("head") && queryData.has("receipt")) { "MANAGEMENT_QUERY_SHAPE" }
        val allowed = boolean(queryData, "canApplySameOriginal")
        val head = if (queryData.isNull("head")) null else queryData.getJSONObject("head").also { binding(it) }
        val foundReceipt = if (queryData.isNull("receipt")) null else queryData.getJSONObject("receipt").also { receipt(record, it) }
        if (foundReceipt != null) check(head != null && sameBinding(head, foundReceipt.getJSONObject("binding"))) {
            "MANAGEMENT_QUERY_HEAD_RECEIPT_CHANGED"
        }
        if (allowed) {
            val sequence = integer(params, "sequence")
            check(if (foundReceipt == null) (head?.let { integer(it, "sequence") } ?: 0L) == sequence - 1
                else head != null && integer(head, "sequence") == sequence) { "MANAGEMENT_CONTINUATION_HEAD_CHANGED" }
        }
        return foundReceipt
    }

    fun canReplay(record: JSONObject, queryData: JSONObject): Boolean =
        query(record, queryData) == null && record.optString("receiptJson").isEmpty() && boolean(queryData, "canApplySameOriginal")

    fun isActive(record: JSONObject): Boolean = record.getString("kind") == KIND && !boolean(record, "completed")

    /** Keep known in-memory facts across a failed journal write; old disk data cannot downgrade them. */
    fun retainOriginal(disk: JSONObject?, retained: JSONObject?): JSONObject? {
        if (disk == null) return retained
        if (retained == null) return disk
        check(disk.getString("id") == retained.getString("id") &&
            disk.getString("parametersJson") == retained.getString("parametersJson")) { "MANAGEMENT_DURABLE_INTENT_CHANGED" }
        parameters(disk)
        parameters(retained)
        val diskReceipt = disk.optString("receiptJson").takeIf { it.isNotEmpty() }
        if (diskReceipt != null) {
            receipt(retained, JSONObject(diskReceipt))
            if (retained.optString("receiptJson").isEmpty()) retained.put("receiptJson", diskReceipt)
            if (disk.optBoolean("queryQualified")) retained.put("queryQualified", true)
        }
        if (disk.optLong("requestId", -1) == retained.optLong("requestId", -2) &&
            disk.optString("runtimeSession") == retained.optString("runtimeSession")) {
            disk.optJSONObject("wireTerminal")?.let { event ->
                check(event.getLong("requestId") == disk.getLong("requestId")) { "MANAGEMENT_ORIGINAL_WIRE_IDENTITY" }
                if (retained.optJSONObject("wireTerminal") == null) retained.put("wireTerminal", event)
            }
        }
        return retained
    }

    fun group(data: JSONObject): ShelfGroup {
        val order = integer(data, "order")
        check(order in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "MANAGEMENT_GROUP_ORDER_RANGE" }
        return ShelfGroup(integer(data, "groupId"), string(data, "groupName"), order.toInt(),
            boolean(data, "enableRefresh"), boolean(data, "show"), optionalString(data, "cover"))
    }

    private fun binding(data: JSONObject) {
        val sequence = integer(data, "sequence")
        val id = string(data, "operationId")
        check(sequence in 1..MAX_SEQUENCE && operation.matches(id) && id.substringAfterLast('/') == sequence.toString() &&
            fingerprint.matches(string(data, "payloadFingerprint"))) { "MANAGEMENT_BINDING_INVALID" }
    }

    private fun sameBinding(left: JSONObject, right: JSONObject): Boolean = string(left, "operationId") == string(right, "operationId") &&
        integer(left, "sequence") == integer(right, "sequence") && string(left, "payloadFingerprint") == string(right, "payloadFingerprint")

    private fun integer(data: JSONObject, key: String): Long = when (val raw = data.get(key)) {
        is Int -> raw.toLong()
        is Long -> raw
        else -> error("MANAGEMENT_INTEGER_REQUIRED: $key")
    }
    private fun boolean(data: JSONObject, key: String): Boolean = data.get(key) as? Boolean ?: error("MANAGEMENT_BOOLEAN_REQUIRED: $key")
    private fun string(data: JSONObject, key: String): String = data.get(key) as? String ?: error("MANAGEMENT_STRING_REQUIRED: $key")
    private fun optionalString(data: JSONObject, key: String): String? = if (!data.has(key) || data.isNull(key)) null else string(data, key)
}

/** One instance per owner, called under that owner's existing IO gate. No independent poller or mutex. */
internal class ReaderBookshelfManagement(private val transport: CoreTransport, private val journal: DurableJournal) {
    private val ownerSession = transport.session
    private var capability = false
    private var warm: JSONObject? = null

    fun listGroups(showOnly: Boolean? = null): List<ShelfGroup> {
        checkOwner()
        val params = JSONObject().apply { showOnly?.let { put("showOnly", it) } }
        val rows = transport.request("book-group.list", params).getJSONArray("groups")
        val groups = (0 until rows.length()).map { ManagementReceiptPolicy.group(rows.getJSONObject(it)) }
        check(groups.map { it.groupId }.toSet().size == groups.size) { "MANAGEMENT_DUPLICATE_GROUP_ID" }
        return groups
    }

    fun createGroup(groupName: String, order: Int = 0, enableRefresh: Boolean = true, show: Boolean = true, cover: String? = null): ShelfGroup =
        applied(execute("book-group.create", JSONObject().put("groupName", groupName).put("order", order)
            .put("enableRefresh", enableRefresh).put("show", show).apply { cover?.let { put("cover", it) } })).group
            ?: error("MANAGEMENT_GROUP_RESULT_MISSING")

    fun updateGroup(groupId: Long, groupName: String? = null, order: Int? = null, enableRefresh: Boolean? = null,
        show: Boolean? = null, cover: String? = null): ShelfGroup = applied(execute("book-group.update", JSONObject().put("groupId", groupId).apply {
            groupName?.let { put("groupName", it) }; order?.let { put("order", it) }; enableRefresh?.let { put("enableRefresh", it) }
            show?.let { put("show", it) }; cover?.let { put("cover", it) }
        })).group ?: error("MANAGEMENT_GROUP_RESULT_MISSING")

    fun deleteGroup(groupId: Long): ShelfGroupDeletion = applied(execute("book-group.delete", JSONObject().put("groupId", groupId))).deletion
        ?: error("MANAGEMENT_DELETE_RESULT_MISSING")

    fun assignGroup(sourceId: String, bookId: String, group: String): ShelfGroupAssignment = applied(execute("bookshelf.group.assign",
        JSONObject().put("sourceId", sourceId).put("bookId", bookId).put("group", group))).assignment
        ?: error("MANAGEMENT_ASSIGNMENT_RESULT_MISSING")

    /** Core-only single removal. removed=false is the original known idempotent no-op, not a rejection. */
    fun removeBook(sourceId: String, bookId: String): ManagementResult = execute("bookshelf.remove",
        JSONObject().put("sourceId", sourceId).put("bookId", bookId))

    /** One literal batch / one Core transaction; never loops over removeBook or deduplicates its intent. */
    fun removeBooks(targets: List<ShelfBookKey>): ManagementResult {
        require(targets.isNotEmpty()) { "MANAGEMENT_REMOVAL_TARGETS_EMPTY" }
        val literal = targets.map { it.copy() }
        return execute("bookshelf.removeBatch", JSONObject().put("targets", JSONArray(ManagementReceiptPolicy.targetsJson(literal))))
    }

    fun pending(): List<PendingManagement> {
        checkOwner()
        val records = journal.readAll().filter { ManagementReceiptPolicy.isActive(it) }.toMutableList()
        warm?.let { retained ->
            val index = records.indexOfFirst { it.getString("id") == retained.getString("id") }
            if (index < 0) records += retained
            else records[index] = ManagementReceiptPolicy.retainOriginal(records[index], retained)!!
        }
        return records.map { record -> PendingManagement(record.getString("id"), record.getString("method"), confirmation(record), false) }
    }

    fun reconcile(operationId: String, continueOriginal: Boolean = false): ManagementResult {
        checkOwner()
        pending().firstOrNull { it.id != operationId }?.let {
            throw PendingOperationException(it.id, "bookshelf.management.mutation.query")
        }
        val disk = journal.exact(operationId, includeCompleted = true)
        val retained = warm?.takeIf { it.getString("id") == operationId }
        val record = ManagementReceiptPolicy.retainOriginal(disk, retained)
            ?: throw PendingOperationException(operationId, "bookshelf.management.mutation.query")
        ManagementReceiptPolicy.parameters(record)
        if (record.optBoolean("queryQualified") && record.optString("receiptJson").isNotEmpty()) {
            // Re-establish a prior archive/directory-sync failure before admitting another write.
            if (retained != null || !record.optBoolean("completed")) {
                record.put("completed", true)
                persist(record)
            }
            if (warm?.optString("id") == operationId) warm = null
            return durableResult(record, ManagementReceiptPolicy.receipt(record, JSONObject(record.getString("receiptJson"))))
        }
        warm = record
        mergeOriginalWire(record)
        consumeWire(record)
        return qualify(record, continueOriginal)
    }

    private fun execute(method: String, business: JSONObject): ManagementResult {
        checkOwner()
        pending().firstOrNull()?.let { throw PendingOperationException(it.id, "bookshelf.management.mutation.apply") }
        if (!capability) {
            val caps = transport.request("core.info").getJSONArray("capabilities")
            check((0 until caps.length()).any { caps.getString(it) == "bookshelf.managementMutation.v1" }) { "MANAGEMENT_CAPABILITY_UNAVAILABLE" }
            capability = true
        }
        val sequence = ManagementReceiptPolicy.nextSequence(transport.request("bookshelf.management.mutation.query"))
        val uuid = UUID.randomUUID().toString()
        val params = JSONObject().put("operationId", "$uuid/$sequence").put("sequence", sequence)
            .put("mutation", JSONObject().put("method", method).put("params", JSONObject(business.toString())))
        val record = JSONObject().put("journalSchema", 1).put("kind", ManagementReceiptPolicy.KIND)
            .put("id", "android-management-$uuid").put("method", method).put("parametersJson", params.toString())
            .put("completed", false).put("dispatchHistory", JSONArray())
        warm = record
        dispatch(record)
        consumeWire(record)
        return qualify(record, false)
    }

    private fun dispatch(record: JSONObject) {
        val id = record.getString("id")
        record.optJSONObject("wireTerminal")?.let { old ->
            record.getJSONArray("dispatchHistory").put(JSONObject().put("retainedTerminal", old)
                .put("runtimeSession", record.getString("runtimeSession")))
        }
        record.remove("wireTerminal")
        try {
            transport.request("bookshelf.management.mutation.apply", ManagementReceiptPolicy.parameters(record), beforeSend = { requestId ->
                record.put("requestId", requestId).put("runtimeSession", transport.session)
                record.getJSONArray("dispatchHistory").put(JSONObject().put("requestId", requestId).put("runtimeSession", transport.session))
                persist(record)
            }, terminal = { event ->
                if (!record.optBoolean("completed") && record.optLong("requestId", -1) == event.getLong("requestId")) record.put("wireTerminal", event)
                journal.terminal(id, event.getLong("requestId"), transport.session, event)
            })
        } catch (_: Exception) {
            // A callback may have durably captured this exact result before another failure.
            consumeWire(record)
            throw PendingOperationException(id, "bookshelf.management.mutation.apply")
        }
    }

    private fun consumeWire(record: JSONObject) {
        val event = record.optJSONObject("wireTerminal") ?: return
        check(event.getLong("requestId") == record.getLong("requestId")) { "MANAGEMENT_ORIGINAL_WIRE_IDENTITY" }
        if (event.getString("type") != "result") return // Arbitrary errors never become fabricated rejected receipts.
        val receipt = ManagementReceiptPolicy.wireReceipt(record, event)
        record.put("receiptJson", receipt.toString())
        persist(record)
    }

    private fun qualify(record: JSONObject, continueOriginal: Boolean): ManagementResult {
        val id = record.getString("id")
        val params = ManagementReceiptPolicy.parameters(record)
        val query = try {
            transport.request("bookshelf.management.mutation.query", JSONObject().put("operationId", params.getString("operationId"))
                .put("expected", params))
        } catch (_: CoreReadTimeoutException) { throw PendingOperationException(id, "bookshelf.management.mutation.query") }
        mergeOriginalWire(record)
        consumeWire(record)
        val receipt = ManagementReceiptPolicy.query(record, query)
        if (receipt != null) {
            val result = ManagementReceiptPolicy.receipt(record, receipt)
            record.put("receiptJson", receipt.toString()).put("queryQualified", true).put("completed", true)
            persist(record)
            if (warm?.optString("id") == id) warm = null
            return durableResult(record, result)
        }
        val canReplay = ManagementReceiptPolicy.canReplay(record, query)
        if (continueOriginal && canReplay) {
            // Exact DTO and identity only, one dispatch per explicit continuation. No recursive retry.
            dispatch(record)
            consumeWire(record)
            return qualify(record, false)
        }
        return ManagementResult(id, record.getString("method"), ManagementConfirmation.UNKNOWN, canContinueOriginal = canReplay)
    }

    private fun mergeOriginalWire(record: JSONObject) {
        val latest = journal.exact(record.getString("id")) ?: return
        ManagementReceiptPolicy.retainOriginal(latest, record)
    }

    private fun durableResult(record: JSONObject, result: ManagementResult): ManagementResult {
        if (result.method !in setOf("bookshelf.remove", "bookshelf.removeBatch")) return result
        check(record.get("completed") == true && record.get("queryQualified") == true) { "MANAGEMENT_REMOVAL_NOT_DURABLE" }
        val parameters = ManagementReceiptPolicy.parameters(record)
        val receiptJson = record.getString("receiptJson")
        val receipt = JSONObject(receiptJson)
        // Revalidate the exact original saved result; no list/query of current books can substitute.
        ManagementReceiptPolicy.receipt(record, receipt)
        val binding = receipt.getJSONObject("binding")
        val targets = ManagementReceiptPolicy.removalTargets(result.method,
            parameters.getJSONObject("mutation").getJSONObject("params"))
        return result.copy(coreRemovalReceipt = ShelfRemovalCoreReceipt(
            record.getString("id"), binding.getString("operationId"), binding.getLong("sequence"),
            binding.getString("payloadFingerprint"), receipt.getString("ackFingerprint"), result.method,
            result.confirmation,
            ManagementReceiptPolicy.targetsJson(targets), record.getString("parametersJson"), receiptJson,
            record.getJSONArray("dispatchHistory").toString(),
        ))
    }

    private fun persist(record: JSONObject) {
        warm = record // A local durability failure must still block a following management write.
        try { journal.write(record) }
        catch (_: Exception) { throw PendingOperationException(record.getString("id"), "bookshelf.management.journal") }
    }

    private fun confirmation(record: JSONObject): ManagementConfirmation = when (record.optString("receiptJson").takeIf { it.isNotEmpty() }
        ?.let { JSONObject(it).optString("outcome") }) {
        "applied" -> ManagementConfirmation.APPLIED
        "rejected" -> ManagementConfirmation.REJECTED
        else -> ManagementConfirmation.UNKNOWN
    }

    private fun applied(result: ManagementResult): ManagementResult {
        if (result.confirmation == ManagementConfirmation.UNKNOWN) throw PendingOperationException(result.operationId, "bookshelf.management.mutation.apply")
        result.rejection?.let { error -> throw CoreCommandException(result.method, JSONObject(error.originalErrorJson)) }
        return result
    }

    private fun checkOwner() = check(transport.session == ownerSession) { "MANAGEMENT_RUNTIME_RETIRED" }
}
