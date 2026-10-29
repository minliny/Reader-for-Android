package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.UUID

data class ReaderSourceEntry(val sourceId: String, val name: String, val baseUrl: String,
    val enabled: Boolean, val enabledExplore: Boolean, val sourceVersion: String, val bookSourceJson: String?)
enum class SourceImportConfirmation { UNKNOWN, APPLIED, REJECTED }
data class PendingSourceImport(val id: String, val sourceId: String, val confirmation: SourceImportConfirmation,
    val candidateRetained: Boolean)
/** Confirmation is Core evidence; durable=false never admits a following rules mutation. */
data class SourceImportResult(val operationId: String, val sourceId: String, val confirmation: SourceImportConfirmation,
    val durable: Boolean, val candidateRetained: Boolean = false, val coreOperationId: String,
    val sequence: Long, val receiptJson: String? = null, val headJson: String? = null)
class SourceRulesPendingException(val operationId: String, val stage: String, cause: Throwable? = null) :
    Exception("SOURCE_RULES_PENDING:$operationId/$stage", cause)

/** Finite original-source DTO checks. Core alone parses rules and computes payload/ACK fingerprints. */
internal object SourceRulesReceiptPolicy {
    const val KIND = "sourceRules"
    const val MAX_RECORD_BYTES = 8 * 1024 * 1024
    const val MAX_SOURCE_BYTES = 1024 * 1024
    private const val MAX_SEQUENCE = 9_007_199_254_740_991L
    private val operation = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}/[1-9][0-9]*")
    private val fingerprint = Regex("[a-f0-9]{64}")

    fun decodeRecord(bytes: ByteArray): JSONObject {
        check(bytes.size in 1..MAX_RECORD_BYTES) { "SOURCE_JOURNAL_BYTE_BUDGET" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val tokens = JSONTokener(text)
        val record = tokens.nextValue() as? JSONObject ?: error("SOURCE_JOURNAL_OBJECT_REQUIRED")
        check(tokens.nextClean() == '\u0000') { "SOURCE_JOURNAL_TRAILING_INPUT" }
        parameters(record)
        return record
    }

    fun sourceObject(raw: String): JSONObject {
        require(raw.toByteArray(Charsets.UTF_8).size in 1..MAX_SOURCE_BYTES) { "SOURCE_JSON_BYTE_BUDGET" }
        val tokens = JSONTokener(raw)
        val value = tokens.nextValue() as? JSONObject ?: error("SOURCE_JSON_OBJECT_REQUIRED")
        check(tokens.nextClean() == '\u0000') { "SOURCE_JSON_TRAILING_INPUT" }
        return value // No Host rule validation, normalization, source replacement or parser.
    }
    fun parameters(record: JSONObject): JSONObject {
        check(record.getInt("journalSchema") == 1 && record.getString("kind") == KIND &&
            record.getString("method") == "source.import") { "SOURCE_ORIGINAL_KIND" }
        val params = JSONObject(record.getString("parametersJson"))
        val seq = integer(params, "sequence"); val id = string(params, "operationId")
        check(seq in 1..MAX_SEQUENCE && operation.matches(id) && id.substringAfterLast('/') == seq.toString()) { "SOURCE_ORIGINAL_BINDING" }
        val mutation = params.getJSONObject("mutation")
        check(string(mutation, "method") == "source.import") { "SOURCE_METHOD_REQUIRES_ITS_OWN_OWNER" }
        val business = mutation.getJSONObject("params")
        check(business.keys().asSequence().toSet() == setOf("sourceId", "bookSource") &&
            string(business, "sourceId") == string(record, "sourceId") && string(record, "sourceId").isNotBlank()) { "SOURCE_ORIGINAL_IDENTITY" }
        // Compare the same mature org.json wire view as parametersJson, preserving the original raw string.
        val originalWireView = JSONObject(sourceObject(record.getString("originalBookSourceJson")).toString())
        check(same(business.getJSONObject("bookSource"), originalWireView)) { "SOURCE_ORIGINAL_JSON_CHANGED" }
        check(record.getString("id") == "android-source-" + id.substringBeforeLast('/') &&
            record.get("completed") is Boolean) { "SOURCE_JOURNAL_IDENTITY" }
        return params
    }
    fun nextSequence(query: JSONObject): Long {
        check(query.has("head") && query.has("receipt") && query.isNull("receipt")) { "SOURCE_HEAD_QUERY_SHAPE" }
        val previous = if (query.isNull("head")) 0L else binding(query.getJSONObject("head"))
        check(previous < MAX_SEQUENCE) { "SOURCE_SEQUENCE_EXHAUSTED" }
        return previous + 1
    }
    fun receipt(record: JSONObject, value: JSONObject): SourceImportConfirmation {
        val params = parameters(record); val b = value.getJSONObject("binding"); binding(b)
        check(string(b, "operationId") == string(params, "operationId") && integer(b, "sequence") == integer(params, "sequence") &&
            string(value, "method") == "source.import" && fingerprint.matches(string(value, "ackFingerprint"))) { "SOURCE_ORIGINAL_RECEIPT_MISMATCH" }
        val data = value.getJSONObject("data")
        val confirmation = when (string(value, "outcome")) {
            "applied" -> {
                check(data.get("imported") == true && string(data, "sourceId") == string(record, "sourceId") &&
                    string(data, "name").isNotBlank()) { "SOURCE_IMPORT_ACK_MISMATCH" }
                SourceImportConfirmation.APPLIED
            }
            "rejected" -> {
                val error = data.getJSONObject("error")
                check(string(error, "code").isNotBlank() && error.get("message") is String) { "SOURCE_REJECTION_SHAPE" }
                SourceImportConfirmation.REJECTED
            }
            else -> error("SOURCE_RECEIPT_OUTCOME")
        }
        record.optString("receiptJson").takeIf { it.isNotEmpty() }?.let {
            check(same(JSONObject(it), value)) { "SOURCE_ORIGINAL_RECEIPT_CHANGED" }
        }
        return confirmation
    }
    fun wireReceipt(record: JSONObject, event: JSONObject): JSONObject {
        check(integer(event, "protocolVersion") == 1L && integer(event, "requestId") == integer(record, "requestId") &&
            string(event, "type") == "result") { "SOURCE_ORIGINAL_WIRE_MISMATCH" }
        return event.getJSONObject("data").also { receipt(record, it) }
    }
    fun qualifiedQuery(record: JSONObject, value: JSONObject): JSONObject? {
        parameters(record)
        check(value.has("head") && value.has("receipt")) { "SOURCE_QUERY_SHAPE" }
        val head = if (value.isNull("head")) null else value.getJSONObject("head").also { binding(it) }
        return if (value.isNull("receipt")) null else value.getJSONObject("receipt").also { r ->
            receipt(record, r)
            check(head != null && same(head, r.getJSONObject("binding"))) { "SOURCE_QUERY_HEAD_RECEIPT_CHANGED" }
        }
        // There is no canApplySameOriginal field in the fixed RulesMutationQueryData.
    }
    fun retainOriginal(disk: JSONObject?, warm: JSONObject?): JSONObject? {
        if (disk == null) return warm
        if (warm == null) return disk.also { parameters(it) }
        parameters(disk); parameters(warm)
        for (key in listOf("id", "kind", "sourceId", "method", "parametersJson", "originalBookSourceJson"))
            check(disk.get(key) == warm.get(key)) { "SOURCE_DURABLE_INTENT_CHANGED:$key" }
        disk.optString("receiptJson").takeIf { it.isNotEmpty() }?.let { raw ->
            receipt(warm, JSONObject(raw))
            if (warm.optString("receiptJson").isEmpty()) warm.put("receiptJson", raw)
            if (disk.optBoolean("queryQualified")) warm.put("queryQualified", true)
        }
        if (disk.optLong("requestId", -1) == warm.optLong("requestId", -2) &&
            disk.optString("runtimeSession") == warm.optString("runtimeSession")) disk.optJSONObject("wireTerminal")?.let { event ->
            check(integer(event, "requestId") == integer(disk, "requestId"))
            val old = warm.optJSONObject("wireTerminal")
            if (old == null) warm.put("wireTerminal", event) else check(same(old, event)) { "SOURCE_ORIGINAL_WIRE_CHANGED" }
        }
        return warm // Never downgrade warm completion/durability facts from an older base.
    }
    fun confirmation(record: JSONObject): SourceImportConfirmation = record.optString("receiptJson").takeIf { it.isNotEmpty() }
        ?.let { receipt(record, JSONObject(it)) } ?: SourceImportConfirmation.UNKNOWN
    fun integer(value: JSONObject, key: String): Long = when (val raw = value.get(key)) {
        is Int -> raw.toLong(); is Long -> raw; else -> error("SOURCE_INTEGER_REQUIRED:$key")
    }
    fun string(value: JSONObject, key: String): String = value.get(key) as? String ?: error("SOURCE_STRING_REQUIRED:$key")
    private fun binding(value: JSONObject): Long {
        val sequence = integer(value, "sequence"); val id = string(value, "operationId")
        check(sequence in 1..MAX_SEQUENCE && operation.matches(id) && id.substringAfterLast('/') == sequence.toString() &&
            fingerprint.matches(string(value, "payloadFingerprint"))) { "SOURCE_BINDING_INVALID" }
        return sequence
    }
    /** Structural equality of supplied JSON values, not a canonical serializer or a hash implementation. */
    private fun same(left: Any?, right: Any?): Boolean = when (left) {
        is JSONObject -> right is JSONObject && left.keys().asSequence().toSet() == right.keys().asSequence().toSet() &&
            left.keys().asSequence().all { same(left.get(it), right.get(it)) }
        is JSONArray -> right is JSONArray && left.length() == right.length() && (0 until left.length()).all { same(left.get(it), right.get(it)) }
        else -> left?.javaClass == right?.javaClass && left == right
    }
}

/** One instance on the App owner; all calls require its actual held lane. No new poller/runtime/database. */
internal class ReaderSourceRules(private val transport: CoreTransport, private val journal: DurableJournal) {
    private val runtime = transport.session
    private var checkedCapabilities = false
    private var warm: JSONObject? = null

    fun list(sourceId: String? = null, enabledOnly: Boolean? = null): List<ReaderSourceEntry> {
        checkOwner()
        sourceId?.let { require(it.isNotBlank()) }
        val data = transport.request("source.list", JSONObject().apply {
            sourceId?.let { put("sourceId", it) }; enabledOnly?.let { put("enabledOnly", it) }
        })
        val rows = data.getJSONArray("sources")
        check(SourceRulesReceiptPolicy.integer(data, "total") == rows.length().toLong()) { "SOURCE_LIST_COUNT" }
        val result = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val id = SourceRulesReceiptPolicy.string(row, "sourceId")
            check(id.isNotBlank() && (sourceId == null || id == sourceId)) { "SOURCE_LIST_IDENTITY" }
            check(row.get("enabled") is Boolean && row.get("enabledExplore") is Boolean)
            check(enabledOnly != true || row.getBoolean("enabled")) { "SOURCE_LIST_FILTER" }
            ReaderSourceEntry(id, SourceRulesReceiptPolicy.string(row, "name"), SourceRulesReceiptPolicy.string(row, "baseUrl"), row.getBoolean("enabled"),
                row.getBoolean("enabledExplore"), SourceRulesReceiptPolicy.string(row, "sourceVersion"),
                if (!row.has("bookSource") || row.isNull("bookSource")) null else
                    JSONArray().put(row.get("bookSource")).toString().let { it.substring(1, it.lastIndex) })
        }
        check(result.map { it.sourceId }.toSet().size == result.size) { "SOURCE_LIST_DUPLICATE_IDENTITY" }
        return Collections.unmodifiableList(ArrayList(result))
    }
    fun pending(): List<PendingSourceImport> {
        checkOwner()
        val originals = journal.sourceRulesActiveForQuery().associateBy { JSONObject(it.recordJson).getString("id") }.toMutableMap()
        warm?.let { retained ->
            val id = retained.getString("id"); val disk = originals[id]
            val merged = SourceRulesReceiptPolicy.retainOriginal(disk?.let { JSONObject(it.recordJson) }, retained)!!
            originals[id] = SourceRulesJournalOriginal(merged.toString(), disk?.candidateRetained == true)
        }
        return originals.values.map { snapshot ->
            val record = JSONObject(snapshot.recordJson); SourceRulesReceiptPolicy.parameters(record)
            PendingSourceImport(record.getString("id"), record.getString("sourceId"),
                SourceRulesReceiptPolicy.confirmation(record), snapshot.candidateRetained)
        }
    }
    fun importJson(sourceId: String, originalBookSourceJson: String): SourceImportResult {
        checkOwner(); require(sourceId.isNotBlank()) { "EXPLICIT_SOURCE_ID_REQUIRED" }
        // Generic readAll deliberately remains fail-closed for ANY unresolved .new/corruption.
        journal.readAll()
        pending().firstOrNull()?.let { throw SourceRulesPendingException(it.id, "rules.mutation.apply") }
        capabilities()
        val source = SourceRulesReceiptPolicy.sourceObject(originalBookSourceJson)
        val sequence = SourceRulesReceiptPolicy.nextSequence(transport.request("rules.mutation.query"))
        val uuid = UUID.randomUUID().toString()
        val parameters = JSONObject().put("operationId", "$uuid/$sequence").put("sequence", sequence)
            .put("mutation", JSONObject().put("method", "source.import").put("params",
                JSONObject().put("sourceId", sourceId).put("bookSource", source)))
        val record = JSONObject().put("journalSchema", 1).put("kind", SourceRulesReceiptPolicy.KIND)
            .put("id", "android-source-$uuid").put("sourceId", sourceId).put("method", "source.import")
            .put("originalBookSourceJson", originalBookSourceJson).put("parametersJson", parameters.toString())
            .put("completed", false)
        SourceRulesReceiptPolicy.parameters(record) // Known local DTO rejection must precede first persistence.
        warm = record
        persist(record) // Retain intent before even preparing the native request.
        val originalRuntime = transport.session
        try {
            transport.request("rules.mutation.apply", SourceRulesReceiptPolicy.parameters(record), beforeSend = { requestId ->
                record.put("requestId", requestId).put("runtimeSession", originalRuntime)
                persist(record)
            }, terminal = { event ->
                check(transport.session == originalRuntime && event.getLong("requestId") == record.getLong("requestId")) { "SOURCE_ORIGINAL_ROUTE_CHANGED" }
                if (!record.optBoolean("completed")) record.put("wireTerminal", event)
                journal.terminal(record.getString("id"), event.getLong("requestId"), originalRuntime, event)
            })
        } catch (error: Exception) {
            if (error.coreProtocolPending() != null) throw error
            throw SourceRulesPendingException(record.getString("id"), "rules.mutation.apply", error)
        }
        consumeWire(record)
        return qualify(record, candidateRetained = false)
    }
    /** Query-only recovery. There is intentionally no replay or fresh-list-as-ACK path. */
    fun reconcile(operationId: String): SourceImportResult {
        checkOwner()
        warm?.takeIf { it.getString("id") != operationId }?.let {
            throw SourceRulesPendingException(it.getString("id"), "rules.mutation.query")
        }
        val snapshot = journal.sourceRulesOriginalForQuery(operationId)
        val retained = warm?.takeIf { it.getString("id") == operationId }
        val record = SourceRulesReceiptPolicy.retainOriginal(snapshot?.let { JSONObject(it.recordJson) }, retained)
            ?: throw SourceRulesPendingException(operationId, "rules.mutation.query")
        warm = record
        if (snapshot?.candidateRetained != true && record.optBoolean("queryQualified") && record.optString("receiptJson").isNotEmpty()) {
            // A successful prior archive may still have failed directory fsync. Re-establish durability once.
            record.put("completed", true); persist(record); warm = null
            return result(record, true, false, null)
        }
        return qualify(record, snapshot?.candidateRetained == true)
    }
    private fun qualify(record: JSONObject, candidateRetained: Boolean): SourceImportResult {
        val parameters = SourceRulesReceiptPolicy.parameters(record)
        val query = try { transport.request("rules.mutation.query", JSONObject()
            .put("operationId", parameters.getString("operationId")).put("expected", parameters)) }
        catch (error: Exception) {
            if (error.coreProtocolPending() != null) throw error
            throw SourceRulesPendingException(record.getString("id"), "rules.mutation.query", error)
        }
        val receipt = SourceRulesReceiptPolicy.qualifiedQuery(record, query)
        if (receipt != null) {
            record.put("receiptJson", receipt.toString()).put("queryQualified", true)
            if (!candidateRetained) {
                record.put("completed", true); persist(record); warm = null
                return result(record, true, false, query)
            }
        }
        // Existing .new is retained, never trusted/promoted/deleted. Original query facts may be shown,
        // but no durable completion or following mutation is authorized by this finite slice.
        return result(record, false, candidateRetained, query)
    }
    private fun consumeWire(record: JSONObject) {
        val wire = record.optJSONObject("wireTerminal") ?: return
        if (wire.getString("type") != "result") return
        val receipt = SourceRulesReceiptPolicy.wireReceipt(record, wire)
        record.put("receiptJson", receipt.toString()); persist(record)
    }
    private fun result(record: JSONObject, durable: Boolean, candidateRetained: Boolean, query: JSONObject?): SourceImportResult {
        val parameters = SourceRulesReceiptPolicy.parameters(record)
        return SourceImportResult(record.getString("id"), record.getString("sourceId"),
            SourceRulesReceiptPolicy.confirmation(record), durable, candidateRetained,
            parameters.getString("operationId"), parameters.getLong("sequence"),
            record.optString("receiptJson").takeIf { it.isNotEmpty() },
            query?.takeUnless { it.isNull("head") }?.getJSONObject("head")?.toString())
    }
    private fun persist(record: JSONObject) {
        warm = record
        try { journal.write(record) }
        catch (error: Exception) {
            if (error.coreProtocolPending() != null) throw error
            throw SourceRulesPendingException(record.getString("id"), "source.rules.journal", error)
        }
    }
    private fun capabilities() {
        if (checkedCapabilities) return
        val values = transport.request("core.info").getJSONArray("capabilities")
        val caps = (0 until values.length()).map { values.getString(it) }.toSet()
        check(caps.containsAll(setOf("rules.mutation.exact.v1", "rules.mutation.source-management.v1"))) { "SOURCE_MUTATION_CAPABILITY_UNAVAILABLE" }
        checkedCapabilities = true
    }
    private fun checkOwner() = check(transport.session == runtime) { "SOURCE_RULES_RUNTIME_RETIRED" }
}
