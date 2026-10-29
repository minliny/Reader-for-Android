package io.reader.android.runtime

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Product slice admission. Values are decoded from Core summary, never inferred from extension. */
enum class ReaderLocalBookFormat(val wire: String) {
    TXT("txt"), EPUB("epub"), MOBI("mobi");
    companion object {
        internal fun fromCore(value: String): ReaderLocalBookFormat =
            entries.singleOrNull { it.wire == value } ?: error("LOCAL_FORMAT_NOT_ADMITTED")
    }
}

internal data class ReaderLocalOriginalAuthority(
    val bookId: String, val sha256: String, val sourceBytes: Long, val format: ReaderLocalBookFormat,
)

/** Read the original journal only. No progress scan, derived authority file, or new database.
 * Must run inside the existing App-owned Library turn. OriginalSourcePublisher remains
 * responsible for verification of actual private bytes when the owner authorizes them. */
internal class ReaderLocalOriginalAuthorityReader(private val completed: File) {
    fun forBook(bookId: String): ReaderLocalOriginalAuthority {
        require(bookId.matches(Regex("local:[a-f0-9]{64}"))) { "LOCAL_BOOK_ID_REQUIRED" }
        check(completed.isDirectory) { "FINALIZED_ORIGINAL_AUTHORITY_MISSING" }
        var accepted: ReaderLocalOriginalAuthority? = null
        val candidates = checkNotNull(completed.listFiles()) { "ORIGINAL_JOURNAL_DIRECTORY_UNREADABLE" }
            .filter { it.name.matches(Regex("android-import-[a-fA-F0-9-]+\\.json(?:\\.bak)?")) }
            .map { if (it.name.endsWith(".bak")) File(completed, it.name.removeSuffix(".bak")) else it }
            .distinctBy { it.name }.sortedBy { it.name }
        for (file in candidates) {
            val record = JSONObject(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            check(integer(record, "journalSchema") == 1L && record.get("kind") == "import" &&
                record.get("id") == file.name.removeSuffix(".json") && record.get("completed") == true) {
                "INVALID_COMPLETED_IMPORT_JOURNAL"
            }
            // A confirmed rejection/rollback is retained evidence, not readable-original authority.
            if (record.optString("terminalStage") != "finalize") continue
            val binding = record.getJSONObject("binding")
            if (binding.getString("bookId") != bookId && record.getString("hash") != bookId.removePrefix("local:")) continue
            val value = finalized(record)
            check(value.bookId == bookId) { "ORIGINAL_BOOK_AUTHORITY_CONFLICT" }
            check(accepted == null || accepted == value) { "ORIGINAL_SOURCE_AUTHORITY_CONFLICT" }
            accepted = value // Multiple genuine transactions may agree; their IDs are not a new ownership ledger.
        }
        return checkNotNull(accepted) { "FINALIZED_ORIGINAL_AUTHORITY_MISSING" }
    }

    private fun finalized(record: JSONObject): ReaderLocalOriginalAuthority {
        val binding = record.getJSONObject("binding")
        val hash = record.getString("hash")
        val bookId = binding.getString("bookId")
        val bytes = integer(binding, "sourceBytes")
        check(hash.matches(Regex("[a-f0-9]{64}")) && binding.getString("sourceId") == "local" &&
            bookId == "local:$hash" && binding.getString("sourceSha256") == hash &&
            binding.getString("transactionId") == record.getString("id") &&
            integer(binding, "sequence") > 0 && bytes in 1..MAX_SOURCE_BYTES &&
            integer(record, "sourceBytes") == bytes) { "ORIGINAL_SOURCE_BINDING_CHANGED" }
        val format = ReaderLocalBookFormat.fromCore(binding.getString("format"))
        val receipts = record.getJSONObject("receipts")
        check(receipts.keys().asSequence().toSet() == setOf("persist", "publish", "finalize")) { "FINALIZED_IMPORT_RECEIPTS_REQUIRED" }
        for (stage in listOf("persist", "publish", "finalize")) {
            val receipt = receipts.getJSONObject(stage)
            check(receipt.getString("stage") == stage && receipt.getString("outcome") == "applied" &&
                receipt.getString("semanticFingerprint").isNotEmpty() && receipt.getString("ackFingerprint").isNotEmpty() &&
                receipt.isNull("error") && receipt.get("data") is JSONObject) { "ORIGINAL_APPLIED_RECEIPT_REQUIRED" }
        }
        val persisted = receipts.getJSONObject("persist").getJSONObject("data")
        check(persisted.getString("kind") == "localBook") { "LOCAL_BOOK_PERSIST_REQUIRED" }
        for (summary in listOf(record.getJSONObject("preview").getJSONObject("summary"), persisted.getJSONObject("data"))) {
            check(summary.getString("format") == format.wire && integer(summary, "byteLen") == bytes &&
                summary.getJSONObject("book").getString("bookId") == bookId) { "CORE_ORIGINAL_SUMMARY_CHANGED" }
            val integrity = summary.getJSONObject("integrity")
            check(integer(integrity, "schemaVersion") == 1L &&
                integrity.getString("readability") in setOf("complete", "recoverable")) { "CORE_ORIGINAL_UNREADABLE" }
        }
        val terminal = receipts.getJSONObject("finalize")
        val finalized = terminal.getJSONObject("data")
        check(finalized.getString("transactionId") == record.getString("id") &&
            finalized.getString("bookId") == bookId && finalized.getString("stage") == "finalize") { "FINALIZE_IDENTITY_CHANGED" }
        val sent = record.getJSONObject("sentParameters")
        ImportReceiptPolicy.sameBinding(binding, sent.getJSONObject("binding"))
        check(sent.getString("terminalStage") == "finalize" &&
            sent.getString("terminalReceiptFingerprint") == terminal.getString("ackFingerprint")) { "ORIGINAL_SETTLE_BINDING_CHANGED" }
        val settlement = record.getJSONObject("settlement")
        ImportReceiptPolicy.authority(settlement.getJSONObject("authority"), binding)
        check(integer(settlement.getJSONObject("authority"), "headSequence") >= integer(binding, "sequence") &&
            integer(record, "requestId") > 0 && record.getString("runtimeSession").isNotBlank()) { "ORIGINAL_SETTLE_AUTHORITY_CHANGED" }
        // Fixed Core's retired result is permitted only with this already-durable terminal/fingerprint.
        // It is not invented as an old settle ACK; no query/replay/write is performed here.
        check(settlement.getString("state") in setOf("settled", "retired")) { "ORIGINAL_SETTLEMENT_REQUIRED" }
        return ReaderLocalOriginalAuthority(bookId, hash, bytes, format)
    }

    private fun integer(value: JSONObject, field: String): Long = when (val raw = value.get(field)) {
        is Byte, is Short, is Int, is Long -> (raw as Number).toLong()
        else -> error("ORIGINAL_EXACT_INTEGER_REQUIRED:$field")
    }
    companion object { private const val MAX_SOURCE_BYTES = 64L * 1024 * 1024 }
}
