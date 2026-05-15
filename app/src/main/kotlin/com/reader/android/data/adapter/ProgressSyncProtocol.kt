package com.reader.android.data.adapter

data class ProgressSyncRecord(
    val bookUrl: String,
    val bookName: String,
    val currentChapterUrl: String,
    val currentChapterTitle: String,
    val chapterIndex: Int,
    val scrollPosition: Float,
    val lastModified: Long = System.currentTimeMillis(),
    val deviceId: String = "local"
)

enum class ConflictStrategy {
    NEWER_WINS,
    LOCAL_WINS,
    REMOTE_WINS,
    MANUAL
}

object SyncConflictResolver {
    fun resolve(local: ProgressSyncRecord?, remote: ProgressSyncRecord?, strategy: ConflictStrategy): ProgressSyncRecord? {
        if (local == null && remote == null) return null
        if (local == null) return remote
        if (remote == null) return local
        return when (strategy) {
            ConflictStrategy.LOCAL_WINS -> local
            ConflictStrategy.REMOTE_WINS -> remote
            ConflictStrategy.NEWER_WINS -> if (remote.lastModified > local.lastModified) remote else local
            ConflictStrategy.MANUAL -> null // requires user intervention
        }
    }

    fun needsConflict(local: ProgressSyncRecord?, remote: ProgressSyncRecord?): Boolean {
        if (local == null || remote == null) return false
        return local.chapterIndex != remote.chapterIndex || local.lastModified != remote.lastModified
    }
}
