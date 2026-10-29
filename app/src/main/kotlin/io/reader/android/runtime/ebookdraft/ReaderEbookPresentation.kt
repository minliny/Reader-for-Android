package io.reader.android.runtime.ebookdraft

import io.reader.android.runtime.ReaderChapter
import org.json.JSONObject
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Unwired draft: the App owner supplies this port inside its existing admitted turn. */
internal interface EbookReadOnlyCommands {
    val runtimeSession: String
    fun request(method: String, parameters: JSONObject): JSONObject
}

internal data class EbookRenderLease(
    val ownerId: UUID, val clientId: UUID, val ownerGeneration: Long,
    val runtimeSession: String, val renderId: UUID,
    val bookId: String, val chapterIndex: Int, val bodyVersion: String, val processingVersion: String,
) {
    init {
        require(ownerGeneration > 0 && runtimeSession.isNotBlank() && chapterIndex >= 0)
        require(bookId.matches(Regex("local:[a-f0-9]{64}")) && bodyVersion.isNotBlank() && processingVersion.isNotBlank())
    }
}

/** Checks exact live App/client/generation/render identity; never just a local Long. */
internal fun interface EbookLeaseGuard { fun requireCurrent(lease: EbookRenderLease) }

internal data class EbookPresentationKind(val sourceId: String, val bookId: String,
    val kind: String, val format: String, val reimportRequired: Boolean)

internal data class EbookPresentation(
    val sourceId: String, val bookId: String, val chapterIndex: Int,
    val bodyVersion: String, val processingVersion: String, val entryPath: String,
    val documentCanonicalContent: String, val chapterStartScalar: Int, val chapterEndScalar: Int,
    val originalChapter: String, val processedChapter: String, val edits: List<EbookProcessedTextEdit>,
) {
    /** Carry only the qualified Core-confirmed output; no Host rule processing or alternate text authority. */
    fun domConfiguration(chapter: ReaderChapter, scalar: Int, topInset: Int): JSONObject {
        require(chapter.bookId == bookId && chapter.index == chapterIndex && chapter.bodyVersion == bodyVersion
            && chapter.processingVersion == processingVersion && chapter.text == processedChapter)
        require(scalar in 0..chapter.scalarCount && topInset >= 0)
        return JSONObject().put("canonical", documentCanonicalContent).put("start", chapterStartScalar)
            .put("end", chapterEndScalar).put("original", originalChapter).put("content", chapter.text)
            .put("originalIsContent", originalChapter == processedChapter)
            .put("scalarCount", chapter.scalarCount).put("offset", scalar).put("topInset", topInset)
            .put("edits", org.json.JSONArray().also { array ->
                edits.forEach { array.put(JSONObject().put("startScalar", it.startScalar).put("endScalar", it.endScalar).put("text", it.text)) }
            })
    }
}

internal class ReaderEbookPresentationReader(
    private val commands: EbookReadOnlyCommands, private val guard: EbookLeaseGuard,
) {
    fun kind(lease: EbookRenderLease): EbookPresentationKind {
        checkLease(lease)
        val json = commands.request("local_book.presentation.kind.v1", JSONObject().put("bookId", lease.bookId))
        checkLease(lease)
        val value = EbookPresentationKind(json.strictString("sourceId"), json.strictString("bookId"),
            json.strictString("kind"), json.strictString("format"), json.strictBoolean("reimportRequired"))
        check(value.sourceId == "local" && value.bookId == lease.bookId)
        check((value.kind == "text" && value.format == "txt") ||
            (value.kind == "ebook" && value.format in setOf("epub", "mobi", "azw", "azw3", "kf8", "ebook")))
        // The caller chooses a renderer using this Core fact. Never fall back to TXT on an error.
        return value
    }

    fun read(lease: EbookRenderLease, kind: EbookPresentationKind, chapter: ReaderChapter, original: AuthorizedEbookOriginal): EbookPresentation {
        checkLease(lease)
        check(kind.sourceId == "local" && kind.bookId == lease.bookId && kind.kind == "ebook" && !kind.reimportRequired)
        check(chapter.bookId == lease.bookId && chapter.index == lease.chapterIndex && chapter.bodyVersion == lease.bodyVersion
            && chapter.processingVersion == lease.processingVersion && original.bookId == lease.bookId)
        original.requireUnchanged()
        val data = commands.request("local_book.chapter.presentation.v1", JSONObject().put("bookId", lease.bookId)
            .put("chapterIndex", lease.chapterIndex).put("localSourcePath", original.authorizedArchivePath))
        checkLease(lease)
        original.requireUnchanged()
        val canonical = data.strictString("documentCanonicalContent", allowEmpty = true)
        check(StandardCharsets.UTF_8.newEncoder().canEncode(canonical) && canonical.toByteArray(StandardCharsets.UTF_8).size <= 16 * 1024 * 1024)
        val count = canonical.codePointCount(0, canonical.length)
        val start = data.exactNonnegativeInt("chapterStartScalar")
        val end = data.exactNonnegativeInt("chapterEndScalar")
        check(start <= end && end <= count)
        val slice = canonical.substring(canonical.offsetByCodePoints(0, start), canonical.offsetByCodePoints(0, end))
        val verified = EbookPresentation(data.strictString("sourceId"), data.strictString("bookId"), data.exactNonnegativeInt("chapterIndex"),
            data.strictString("bodyVersion"), data.strictString("processingVersion"), data.strictString("entryPath"),
            canonical, start, end, slice, chapter.text, emptyList()).also {
            check(it.sourceId == "local" && it.bookId == lease.bookId && it.chapterIndex == lease.chapterIndex
                && it.bodyVersion == lease.bodyVersion && it.processingVersion == lease.processingVersion)
            requireCanonicalEbookEntry(it.entryPath)
            check(it.originalChapter.isNotEmpty() || it.processedChapter.isEmpty()) { "EBOOK_EMPTY_ORIGINAL_UNPROVEN" }
        }
        val edits = ReaderEbookTextEdits.changes(slice, chapter.text)
        checkLease(lease)
        original.requireUnchanged()
        return verified.copy(edits = edits)
    }

    private fun checkLease(lease: EbookRenderLease) {
        guard.requireCurrent(lease)
        check(commands.runtimeSession == lease.runtimeSession) { "RETIRED_CORE_RUNTIME" }
    }
}

internal fun requireCanonicalEbookEntry(entry: String) {
    require(entry.isNotEmpty() && !entry.startsWith('/') && entry.none { it == '\\' || it == ':' || it.code < 32 || it.code == 127 }
        && StandardCharsets.UTF_8.newEncoder().canEncode(entry)
        && entry.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "CANONICAL_CORE_ENTRY_REQUIRED" }
}

private fun JSONObject.strictString(key: String, allowEmpty: Boolean = false): String =
    (get(key) as? String ?: error("STRING_REQUIRED:$key")).also { check(allowEmpty || it.isNotBlank()) }
private fun JSONObject.strictBoolean(key: String): Boolean = get(key) as? Boolean ?: error("BOOLEAN_REQUIRED:$key")
private fun JSONObject.exactNonnegativeInt(key: String): Int {
    val value = when (val raw = get(key)) {
        is Byte, is Short, is Int, is Long -> (raw as Number).toLong()
        is BigInteger -> raw.longValueExact()
        else -> error("EXACT_INTEGER_REQUIRED:$key")
    }
    check(value in 0..Int.MAX_VALUE.toLong())
    return value.toInt()
}
