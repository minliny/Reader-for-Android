package io.reader.android.runtime

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

internal data class ReaderSourceDocumentEntry(val index: Int, val sourceId: String, val name: String?)
internal data class ReaderSourceJsonInput(
    val sourceId: String, val selectedDefinitionJson: String, val documentId: String, val documentBytes: Int,
    val documentSha256: String, val sourceCount: Int, val selectedIndex: Int,
) {
    val definitionClaim: String get() = "SELECTED_COMPLETE_OBJECT_SERIALIZATION_NOT_ORIGINAL_DOCUMENT_BYTES"
}
/** Immutable full input and frozen per-object serialization. No per-element import or rule normalization. */
internal class ReaderSourceDocumentPreview internal constructor(
    originalBytes: ByteArray, entries: List<ReaderSourceDocumentEntry>, definitions: List<String>,
) {
    val id: String = UUID.randomUUID().toString()
    private val raw = originalBytes.copyOf()
    private val definitions = definitions.toList()
    val documentBytes: Int = raw.size
    val documentSha256: String = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
    val entries: List<ReaderSourceDocumentEntry> = Collections.unmodifiableList(entries.toList())
    val sourceCount: Int get() = entries.size
    fun originalDocumentBytes(): ByteArray = raw.copyOf()
    fun select(index: Int): ReaderSourceJsonInput {
        require(index in entries.indices) { "SOURCE_DOCUMENT_SELECTION_INDEX" }
        val entry = entries[index]
        return ReaderSourceJsonInput(entry.sourceId, definitions[index], id, documentBytes, documentSha256, sourceCount, index)
    }
}
internal class ReaderSourceDocumentException(val reason: String) : Exception(reason)
/** Mature JSON envelope decoding only; Core owns rules and the single durable mutation. */
internal object ReaderSourceJsonDocument {
    fun read(resolver: ContentResolver, uri: Uri): ReaderSourceDocumentPreview {
        require(uri.scheme == "content") { "SOURCE_DOCUMENT_CONTENT_URI_REQUIRED" }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val data = ByteArray(SourceRulesReceiptPolicy.MAX_SOURCE_BYTES + 1); var count = 0
            while (count < data.size) {
                val n = input.read(data, count, data.size - count)
                if (n < 0) break
                check(n > 0) { "SOURCE_DOCUMENT_READ_STALLED" }; count += n
            }
            if (count !in 1..SourceRulesReceiptPolicy.MAX_SOURCE_BYTES) throw ReaderSourceDocumentException("SOURCE_DOCUMENT_SIZE")
            data.copyOf(count)
        } ?: throw ReaderSourceDocumentException("SOURCE_DOCUMENT_UNAVAILABLE")
        return decode(bytes)
    }
    internal fun decode(bytes: ByteArray): ReaderSourceDocumentPreview {
        if (bytes.size !in 1..SourceRulesReceiptPolicy.MAX_SOURCE_BYTES) throw ReaderSourceDocumentException("SOURCE_DOCUMENT_SIZE")
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        val tokener = JSONTokener(text); val value = tokener.nextValue()
        if (tokener.nextClean() != '\u0000') throw ReaderSourceDocumentException("SOURCE_DOCUMENT_TRAILING_INPUT")
        val objects = when (value) {
            is JSONObject -> listOf(value)
            is JSONArray -> (0 until value.length()).map { value.opt(it) as? JSONObject ?: throw ReaderSourceDocumentException("SOURCE_DOCUMENT_ENTRY_OBJECT") }
            else -> throw ReaderSourceDocumentException("SOURCE_DOCUMENT_OBJECT_OR_ARRAY")
        }
        if (objects.isEmpty()) throw ReaderSourceDocumentException("SOURCE_DOCUMENT_EMPTY")
        val entries = objects.mapIndexed { index, item ->
            val id = item.opt("bookSourceUrl") as? String
            if (id.isNullOrBlank()) throw ReaderSourceDocumentException("SOURCE_DOCUMENT_IDENTITY")
            ReaderSourceDocumentEntry(index, id, item.opt("bookSourceName") as? String)
        }
        return ReaderSourceDocumentPreview(bytes, entries, objects.map { it.toString() })
    }
}
