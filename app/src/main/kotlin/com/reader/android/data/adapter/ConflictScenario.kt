package com.reader.android.data.adapter

enum class ConflictScenario(
    val description: String,
    val recommendedStrategy: ConflictStrategy
) {
    LOCAL_NEWER("Local progress is newer", ConflictStrategy.LOCAL_WINS),
    REMOTE_NEWER("Remote progress is newer", ConflictStrategy.NEWER_WINS),
    LOCAL_ONLY("Only local has progress", ConflictStrategy.LOCAL_WINS),
    REMOTE_ONLY("Only remote has progress", ConflictStrategy.REMOTE_WINS),
    BOTH_NEW("Never synced before", ConflictStrategy.NEWER_WINS),
    CONCURRENT_EDIT("Both edited concurrently", ConflictStrategy.MANUAL)
}

object ConflictMatrix {
    fun resolve(scenario: ConflictScenario, local: ProgressSyncRecord?, remote: ProgressSyncRecord?): ProgressSyncRecord? {
        return SyncConflictResolver.resolve(local, remote, scenario.recommendedStrategy)
    }
}
