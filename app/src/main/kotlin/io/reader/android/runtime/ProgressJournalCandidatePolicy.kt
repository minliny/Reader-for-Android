package io.reader.android.runtime

import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Finite schema1 progress DTO qualification, not an AtomicFile commit or Core write. */
internal object ProgressJournalCandidatePolicy {
    const val MAX_BYTES = 64 * 1024
    private val fields = setOf("journalSchema", "kind", "id", "bookId", "parameters", "completed", "requestId", "runtimeSession")

    class Qualified internal constructor(base: ByteArray, candidate: ByteArray, val id: String, val bookId: String, val sourceId: String) {
        private val baseBytes = base.copyOf()
        private val candidateBytes = candidate.copyOf()
        val activeSha256: String = hash(baseBytes)
        val candidateSha256: String = hash(candidateBytes)
        fun unchanged(base: ByteArray, candidate: ByteArray): Boolean = baseBytes.contentEquals(base) && candidateBytes.contentEquals(candidate)
        fun activeBytesCopy(): ByteArray = baseBytes.copyOf()
        fun candidateBytesCopy(): ByteArray = candidateBytes.copyOf()
        fun record(): JSONObject = parse(candidateBytes) // Still completed=false, no promotion or acknowledgement.
    }

    fun qualify(activeBytes: ByteArray, candidateBytes: ByteArray): Qualified {
        val active = parse(activeBytes)
        val candidate = parse(candidateBytes)
        check(keys(active) == fields && keys(candidate) == fields + "wireTerminal") { "PROGRESS_CANDIDATE_RECORD_FIELDS" }
        check(sameObject(active, JSONObject(candidate.toString()).apply { remove("wireTerminal") })) { "PROGRESS_CANDIDATE_ORIGINAL_CONFLICT" }
        check(integer(active, "journalSchema") == 1L && active.get("kind") == "progress" && active.get("completed") is Boolean && active.get("completed") == false) { "PROGRESS_CANDIDATE_NOT_ACTIVE_PROGRESS" }
        val id = active.get("id") as? String ?: error("PROGRESS_CANDIDATE_ID")
        check(id.matches(Regex("android-progress-[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))) { "PROGRESS_CANDIDATE_ID" }
        check((active.get("runtimeSession") as? String)?.isNotBlank() == true && integer(active, "requestId") > 0) { "PROGRESS_CANDIDATE_DISPATCH" }
        val params = active.getJSONObject("parameters")
        val book = active.get("bookId") as? String ?: error("PROGRESS_CANDIDATE_BOOK")
        val owner = ProgressReceiptPolicy.key(active)
        check((owner.sourceId != "local" || book.matches(Regex("local:[a-f0-9]{64}"))) && params.get("bookId") == book) { "PROGRESS_CANDIDATE_OWNER" }
        val event = candidate.getJSONObject("wireTerminal")
        check(event.get("type") == "result" && integer(event, "protocolVersion") == 1L && integer(event, "requestId") == integer(active, "requestId")
            && integer(event, "deliveryId") > 0) { "PROGRESS_CANDIDATE_TERMINAL_IDENTITY" }
        val data = event.getJSONObject("data")
        check(data.get("stored") is Boolean && data.get("stored") == true && data.get("sourceId") == params.get("sourceId") && data.get("bookId") == book) { "PROGRESS_CANDIDATE_NOT_STORED" }
        check(integer(params, "chapterIndex") in 0..Int.MAX_VALUE.toLong() && integer(params, "chapterOffset") in 0..Int.MAX_VALUE.toLong() && integer(params, "chapterIndex") == integer(data, "chapterIndex")
            && integer(params, "chapterOffset") == integer(data, "chapterOffset")) { "PROGRESS_CANDIDATE_SCALAR" }
        val expectedProgress = number(params, "chapterProgress")
        check(expectedProgress in 0.0..1.0 && expectedProgress == number(data, "chapterProgress")) { "PROGRESS_CANDIDATE_FRACTION" }
        for ((expected, actual) in listOf("expectedBodyVersion" to "bodyVersion", "expectedProcessingVersion" to "processingVersion")) {
            val value = params.get(expected) as? String ?: error("PROGRESS_CANDIDATE_VERSION")
            check(value.isNotBlank() && value == data.get(actual)) { "PROGRESS_CANDIDATE_VERSION" }
        }
        check((params.get("expectedProgressRevision") as? String)?.startsWith("reader-progress-v1:") == true
            && (data.get("progressRevision") as? String)?.startsWith("reader-progress-v1:") == true) { "PROGRESS_CANDIDATE_CAS" }
        integer(data, "updatedAt")
        ProgressReceiptPolicy.revision(candidate, event) // Same business policy as explicit reconcile; no fresh get.
        return Qualified(activeBytes, candidateBytes, id, book, owner.sourceId)
    }

    fun parse(bytes: ByteArray): JSONObject {
        check(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "PROGRESS_CANDIDATE_BYTE_BUDGET" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val tokener = JSONTokener(text)
        val value = tokener.nextValue() as? JSONObject ?: error("PROGRESS_CANDIDATE_OBJECT_REQUIRED")
        check(tokener.nextClean() == '\u0000') { "PROGRESS_CANDIDATE_TRAILING_INPUT" }
        return value
    }

    fun sameObject(left: JSONObject, right: JSONObject): Boolean = keys(left) == keys(right) && keys(left).all { key ->
        val l = left.get(key); val r = right.get(key)
        // Host JSONObject writes retain nested parameter insertion order. A different
        // representation is rejected conservatively; no custom canonical JSON parser.
        when (l) { is JSONObject -> r is JSONObject && l.toString() == r.toString(); else -> l?.javaClass == r?.javaClass && l == r }
    }
    private fun keys(value: JSONObject): Set<String> = value.keys().asSequence().toSet()
    private fun integer(value: JSONObject, key: String): Long = when (val number = value.get(key)) {
        is Int -> number.toLong(); is Long -> number; else -> error("PROGRESS_CANDIDATE_EXACT_INTEGER:$key")
    }
    private fun number(value: JSONObject, key: String): Double = (value.get(key) as? Number)?.toDouble()?.also { check(it.isFinite()) }
        ?: error("PROGRESS_CANDIDATE_NUMBER:$key")
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
