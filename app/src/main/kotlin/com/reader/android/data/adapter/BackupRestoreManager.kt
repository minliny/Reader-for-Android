package com.reader.android.data.adapter

data class RestoreResult(
    val success: Boolean,
    val restoredEntries: Int = 0,
    val skippedEntries: Int = 0,
    val errorMessage: String? = null
)

enum class RestorePolicy { FULL_REPLACE, MERGE, DRY_RUN }

class BackupRestoreManager(private val client: WebDavClient = FakeWebDavClient()) {

    suspend fun validate(manifest: BackupManifest): Boolean {
        return manifest.entries.all { entry ->
            val response = client.execute(WebDavRequest(entry.path, WebDavMethod.GET))
            response.statusCode == 200
        }
    }

    fun planRestore(manifest: BackupManifest, policy: RestorePolicy): List<BackupEntry> {
        return when (policy) {
            RestorePolicy.DRY_RUN -> emptyList()
            RestorePolicy.FULL_REPLACE -> manifest.entries
            RestorePolicy.MERGE -> manifest.entries
        }
    }

    fun result(success: Boolean, restored: Int = 0, skipped: Int = 0, error: String? = null): RestoreResult {
        return RestoreResult(success, restored, skipped, error)
    }
}
