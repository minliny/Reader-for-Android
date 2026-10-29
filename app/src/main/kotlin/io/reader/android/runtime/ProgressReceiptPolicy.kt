package io.reader.android.runtime

import org.json.JSONObject

/** Confirms only the original flat reading.progress.update ACK, never get data. */
internal object ProgressReceiptPolicy {
    /** Original immutable parameters, not a current UI book, own this receipt. */
    fun key(record: JSONObject): ShelfBookKey {
        val p = record.getJSONObject("parameters")
        val source = p.get("sourceId") as? String ?: error("ORIGINAL_PROGRESS_SOURCE_TYPE")
        val book = record.get("bookId") as? String ?: error("ORIGINAL_PROGRESS_BOOK_TYPE")
        val parameterBook = p.get("bookId") as? String ?: error("ORIGINAL_PROGRESS_PARAMETER_BOOK_TYPE")
        return ShelfBookKey(source, book).also {
            check(it.sourceId.isNotBlank() && it.bookId.isNotBlank() && parameterBook == it.bookId) { "ORIGINAL_PROGRESS_OWNER" }
        }
    }
    fun revision(record: JSONObject, event: JSONObject): String {
        check(event.getString("type") == "result" &&
            event.getLong("requestId") == record.getLong("requestId")) { "ORIGINAL_PROGRESS_ACK_IDENTITY" }
        val data = event.getJSONObject("data")
        val parameters = record.getJSONObject("parameters")
        val owner = key(record)
        check(data.getBoolean("stored") && (data.get("sourceId") as? String) == owner.sourceId &&
            (data.get("bookId") as? String) == owner.bookId &&
            data.getInt("chapterIndex") == parameters.getInt("chapterIndex") &&
            data.getLong("chapterOffset") == parameters.getLong("chapterOffset") &&
            data.getString("bodyVersion") == parameters.getString("expectedBodyVersion") &&
            data.getString("processingVersion") == parameters.getString("expectedProcessingVersion")) { "ORIGINAL_PROGRESS_ACK_MISMATCH" }
        return data.getString("progressRevision").also { check(!data.isNull("progressRevision") && it.isNotEmpty()) }
    }
}
