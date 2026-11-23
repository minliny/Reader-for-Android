package io.reader.android.runtime

import io.reader.android.runtime.ebookdraft.EbookRenderLease
import org.json.JSONObject
import java.util.Collections
import java.util.UUID

/** A real App/Library-issued in-memory capability, never an old operation ACK. */
data class ReaderRetainedUnknownPosition(val id: String, val bookId: String, val requestId: Long,
    val runtimeSession: String, val recordBytes: Int, val recordSha256: String, val originalParametersJson: String)

class ReaderNewPositionIntent internal constructor(
    val id: String, val sourceId: String, val bookId: String, val chapterIndex: Int,
    val sessionToken: String, val bodyVersion: String, val processingVersion: String,
    val runtimeSession: String, val baselineScalarOffset: Int, val observedProgressRevision: String,
    retainedUnknowns: List<ReaderRetainedUnknownPosition>,
) {
    val retainedUnknowns: List<ReaderRetainedUnknownPosition> = Collections.unmodifiableList(ArrayList(retainedUnknowns))
    val retainedUnknownIds: List<String> = Collections.unmodifiableList(retainedUnknowns.map { it.id })
}

sealed interface ReaderPositionSaveResult {
    data class SkippedUnchanged(val retainedUnknownIds: List<String>, val bookId: String,
        val chapterIndex: Int, val scalarOffset: Int, val sourceId: String = "local") : ReaderPositionSaveResult
    /** Serialized parsed JSON, not byte-exact wire. This is only the new original's durable ACK. */
    data class ConfirmedNewOriginal(val originalId: String, val bookId: String, val chapterIndex: Int,
        val scalarOffset: Int, val requestId: Long, val runtimeSession: String,
        val bodyVersion: String, val processingVersion: String, val progressRevision: String,
        val originalParametersJson: String, val parsedTerminalJson: String, val sourceId: String = "local") : ReaderPositionSaveResult
}

internal class ReaderProgressOriginalSnapshot private constructor(private val raw: ByteArray,
    val id: String, val bookId: String, val runtimeSession: String) {
    val sha256: String = ProgressJournalCandidatePolicy.hash(raw)
    val bytes: Int get() = raw.size
    fun rawCopy(): ByteArray = raw.copyOf()
    fun sameBytes(other: ReaderProgressOriginalSnapshot) = id == other.id && raw.contentEquals(other.raw)
    fun identity(): ReaderRetainedUnknownPosition {
        val r = ProgressJournalCandidatePolicy.parse(raw)
        return ReaderRetainedUnknownPosition(id, bookId, r.getLong("requestId"), runtimeSession, bytes, sha256, r.getJSONObject("parameters").toString())
    }
    companion object {
        fun from(bytes: ByteArray): ReaderProgressOriginalSnapshot {
            val record = ProgressJournalCandidatePolicy.parse(bytes)
            return ReaderProgressOriginalSnapshot(bytes.copyOf(), record.getString("id"), record.getString("bookId"), record.getString("runtimeSession"))
        }
    }
}

/** Supported producer schema only. Parsing/hashing reuse the existing mature adapters. */
internal object ReaderProgressNewIntentPolicy {
    private val fields = setOf("journalSchema", "kind", "id", "bookId", "parameters", "completed", "requestId", "runtimeSession")
    private val parameters = setOf("sourceId", "bookId", "chapterIndex", "chapterOffset", "chapterProgress",
        "expectedBodyVersion", "expectedProcessingVersion", "expectedProgressRevision")
    private val hex = Regex("[a-f0-9]{64}")
    fun uuid(value: String): Boolean = try { UUID.fromString(value).toString() == value } catch (_: IllegalArgumentException) { false }
    fun integer(value: JSONObject, field: String): Long = when (val number = value.get(field)) {
        is Int -> number.toLong(); is Long -> number; else -> error("PROGRESS_INTENT_INTEGER:$field")
    }
    fun qualify(original: ReaderProgressOriginalSnapshot, currentRuntime: String, chapter: ReaderChapter) {
        val record = ProgressJournalCandidatePolicy.parse(original.rawCopy())
        check(record.keys().asSequence().toSet() == fields && integer(record, "journalSchema") == 1L &&
            record.get("kind") == "progress" && record.get("completed") == false) { "PROGRESS_INTENT_UNSUPPORTED_ORIGINAL" }
        check(original.id.startsWith("android-progress-") && uuid(original.id.removePrefix("android-progress-")) &&
            uuid(original.runtimeSession) && uuid(currentRuntime) && original.runtimeSession != currentRuntime &&
            integer(record, "requestId") in 1..9_007_199_254_740_991L) { "PROGRESS_INTENT_NOT_COLD_ORIGINAL" }
        val p = record.getJSONObject("parameters")
        check(p.keys().asSequence().toSet() == parameters && p.get("sourceId") == "local" && chapter.sourceId == "local" &&
            record.get("bookId") == original.bookId && p.get("bookId") == chapter.bookId && original.bookId == chapter.bookId &&
            chapter.bookId.matches(Regex("local:[a-f0-9]{64}")) && integer(p, "chapterIndex") == chapter.index.toLong() &&
            integer(p, "chapterOffset") in 0..chapter.scalarCount.toLong()) { "PROGRESS_INTENT_ORIGINAL_SCOPE" }
        check(hex.matches(chapter.bodyVersion) && hex.matches(chapter.processingVersion) &&
            p.get("expectedBodyVersion") == chapter.bodyVersion && p.get("expectedProcessingVersion") == chapter.processingVersion &&
            (p.get("expectedProgressRevision") as? String)?.matches(Regex("reader-progress-v1:[a-f0-9]{64}")) == true) { "PROGRESS_INTENT_ORIGINAL_VERSIONS" }
        val progress = (p.get("chapterProgress") as? Number)?.toDouble() ?: error("PROGRESS_INTENT_FRACTION")
        val offset = integer(p, "chapterOffset")
        val expected = if (chapter.scalarCount == 0) 0.0 else offset.toDouble() / chapter.scalarCount
        check(progress.isFinite() && progress in 0.0..1.0 && progress == expected) { "PROGRESS_INTENT_FRACTION" }
    }
    fun sameOriginals(expected: List<ReaderProgressOriginalSnapshot>, current: List<ReaderProgressOriginalSnapshot>): Boolean {
        val byId = current.associateBy { it.id }
        return expected.size == current.size && byId.size == current.size && expected.all { old -> byId[old.id]?.let(old::sameBytes) == true }
    }
    fun confirmed(record: JSONObject): ReaderPositionSaveResult.ConfirmedNewOriginal {
        check(record.get("completed") == true) { "NEW_PROGRESS_NOT_DURABLE" }
        val event = record.getJSONObject("wireTerminal")
        val revision = ProgressReceiptPolicy.revision(record, event)
        val p = record.getJSONObject("parameters"); val data = event.getJSONObject("data")
        return ReaderPositionSaveResult.ConfirmedNewOriginal(record.getString("id"), record.getString("bookId"),
            p.getInt("chapterIndex"), p.getInt("chapterOffset"), record.getLong("requestId"), record.getString("runtimeSession"),
            data.getString("bodyVersion"), data.getString("processingVersion"), revision, p.toString(), event.toString(), sourceId = p.getString("sourceId"))
    }
}

internal class ReaderProgressColdAuthority(val issuer: Any, val client: ReaderOperationOwner.ClientLease,
    val runtimeSession: String, val originals: List<ReaderProgressOriginalSnapshot>)

internal data class ReaderPositionViewFact(val lease: EbookRenderLease, val epoch: Long, val token: String)
internal data class ReaderPositionIntentState(val intent: ReaderNewPositionIntent, val authority: ReaderProgressColdAuthority,
    val scalarCount: Int, val lastVisible: Int, val everPresented: Boolean = false, val currentReady: ReaderPositionViewFact? = null,
    val hasConfirmedNewOriginal: Boolean = false)
