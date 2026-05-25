package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSyncLocalStateTest {

    @Test
    fun `backup lifecycle idle to exported`() {
        val adapter = BackupSyncLocalAdapter()
        assertEquals(BackupStatus.IDLE, adapter.state.backupStatus)

        adapter.startBackup()
        assertEquals(BackupStatus.EXPORTING, adapter.state.backupStatus)

        adapter.completeBackup()
        assertEquals(BackupStatus.EXPORTED, adapter.state.backupStatus)
        assertTrue(adapter.state.lastBackupAt.isNotBlank())
    }

    @Test
    fun `backup error transitions`() {
        val adapter = BackupSyncLocalAdapter()
        adapter.startBackup()
        adapter.backupError()
        assertEquals(BackupStatus.ERROR, adapter.state.backupStatus)
    }

    @Test
    fun `sync lifecycle with conflict`() {
        val adapter = BackupSyncLocalAdapter()
        adapter.startSync()
        assertEquals(SyncStatus.SYNCING, adapter.state.syncStatus)

        adapter.syncConflict("v1", "v2")
        assertEquals(SyncStatus.CONFLICT, adapter.state.syncStatus)
        assertTrue(adapter.state.hasConflicts)
        assertEquals(1, adapter.state.conflicts.size)
    }

    @Test
    fun `sync success`() {
        val adapter = BackupSyncLocalAdapter()
        adapter.startSync()
        adapter.syncSuccess()
        assertEquals(SyncStatus.SUCCESS, adapter.state.syncStatus)
        assertTrue(adapter.state.lastSyncAt.isNotBlank())
    }

    @Test
    fun `initial state has no conflicts`() {
        assertFalse(BackupSyncLocalState().hasConflicts)
    }
}
