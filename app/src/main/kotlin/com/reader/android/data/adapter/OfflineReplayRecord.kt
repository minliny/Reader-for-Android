package com.reader.android.data.adapter

data class OfflineReplayRecord(
    val sourceUrl: String,
    val requestUrl: String,
    val responseHtml: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isJsResult: Boolean = false
)

object OfflineReplayContract {
    fun validate(record: OfflineReplayRecord): Boolean {
        return record.sourceUrl.isNotBlank() &&
            record.requestUrl.isNotBlank() &&
            record.responseHtml.isNotBlank()
    }

    fun isValidForReplay(record: OfflineReplayRecord, maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L): Boolean {
        val age = System.currentTimeMillis() - record.timestamp
        return validate(record) && age <= maxAgeMs
    }
}
