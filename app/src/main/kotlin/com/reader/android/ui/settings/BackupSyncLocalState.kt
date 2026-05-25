package com.reader.android.ui.settings

/**
 * Local backup and sync state models. In-memory only.
 * Covers Slice 64-67 (Backup plan/export/import) and 68-70 (Cloud sync plan/conflict/UI).
 */
enum class BackupStatus(val label: String) {
    IDLE("未开始"),
    EXPORTING("导出中"),
    EXPORTED("已导出"),
    IMPORTING("导入中"),
    IMPORTED("已导入"),
    ERROR("失败")
}

enum class SyncStatus(val label: String) {
    IDLE("未同步"),
    SYNCING("同步中"),
    SUCCESS("已同步"),
    CONFLICT("有冲突"),
    ERROR("同步失败")
}

data class SyncConflictLocal(
    val localVersion: String,
    val remoteVersion: String,
    val resolved: Boolean = false
)

data class BackupSyncLocalState(
    val backupStatus: BackupStatus = BackupStatus.IDLE,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val conflicts: List<SyncConflictLocal> = emptyList(),
    val lastBackupAt: String = "",
    val lastSyncAt: String = ""
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty() && conflicts.any { !it.resolved }
}

class BackupSyncLocalAdapter {
    var state = BackupSyncLocalState()
        private set

    fun startBackup() {
        state = state.copy(backupStatus = BackupStatus.EXPORTING)
    }

    fun completeBackup() {
        state = state.copy(backupStatus = BackupStatus.EXPORTED, lastBackupAt = now())
    }

    fun backupError() {
        state = state.copy(backupStatus = BackupStatus.ERROR)
    }

    fun startSync() {
        state = state.copy(syncStatus = SyncStatus.SYNCING)
    }

    fun syncSuccess() {
        state = state.copy(syncStatus = SyncStatus.SUCCESS, lastSyncAt = now())
    }

    fun syncConflict(local: String, remote: String) {
        state = state.copy(
            syncStatus = SyncStatus.CONFLICT,
            conflicts = state.conflicts + SyncConflictLocal(local, remote)
        )
    }

    private fun now(): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date())
}
