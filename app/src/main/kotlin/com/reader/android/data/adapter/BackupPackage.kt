package com.reader.android.data.adapter

import org.json.JSONArray
import org.json.JSONObject

data class BackupEntry(
    val path: String,
    val checksum: String,
    val sizeBytes: Long,
    val lastModified: Long = System.currentTimeMillis()
)

data class BackupManifest(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String = "0.1.0",
    val entries: List<BackupEntry> = emptyList()
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("createdAt", createdAt)
        put("appVersion", appVersion)
        put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().apply {
                    put("path", e.path)
                    put("checksum", e.checksum)
                    put("sizeBytes", e.sizeBytes)
                    put("lastModified", e.lastModified)
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): BackupManifest {
            val obj = JSONObject(json)
            val entries = mutableListOf<BackupEntry>()
            val arr = obj.getJSONArray("entries")
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                entries.add(BackupEntry(
                    path = e.getString("path"),
                    checksum = e.getString("checksum"),
                    sizeBytes = e.getLong("sizeBytes"),
                    lastModified = e.optLong("lastModified", 0)
                ))
            }
            return BackupManifest(
                version = obj.getInt("version"),
                createdAt = obj.optLong("createdAt", 0),
                appVersion = obj.optString("appVersion", ""),
                entries = entries
            )
        }
    }
}
