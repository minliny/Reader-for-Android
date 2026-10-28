package io.reader.android.runtime

import org.json.JSONObject

/** Confirms only the original flat reading.progress.update ACK, never get data. */
internal object ProgressReceiptPolicy {
    fun revision(record: JSONObject, event: JSONObject): String {
        check(event.getString("type") == "result" &&
            event.getLong("requestId") == record.getLong("requestId")) { "ORIGINAL_PROGRESS_ACK_IDENTITY" }
        val data = event.getJSONObject("data")
        val parameters = record.getJSONObject("parameters")
        check(data.getBoolean("stored") && data.getString("sourceId") == "local" &&
            data.getString("bookId") == record.getString("bookId") &&
            data.getInt("chapterIndex") == parameters.getInt("chapterIndex") &&
            data.getLong("chapterOffset") == parameters.getLong("chapterOffset") &&
            data.getString("bodyVersion") == parameters.getString("expectedBodyVersion") &&
            data.getString("processingVersion") == parameters.getString("expectedProcessingVersion")) { "ORIGINAL_PROGRESS_ACK_MISMATCH" }
        return data.getString("progressRevision").also { check(!data.isNull("progressRevision") && it.isNotEmpty()) }
    }
}
