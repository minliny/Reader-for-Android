package com.reader.android.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SyncOperationLogTest {

    @Test
    fun `SyncOperationLog holds all fields`() {
        val log = SyncOperationLog(
            operationId = "sync-123",
            syncType = "BACKUP",
            state = "SUCCESS",
            completedAt = 1700000000L,
            attemptCount = 2
        )
        assertEquals("sync-123", log.operationId)
        assertEquals("BACKUP", log.syncType)
        assertEquals("SUCCESS", log.state)
        assertEquals(2, log.attemptCount)
    }

    @Test
    fun `errorMessage nullable`() {
        val log = SyncOperationLog("id", "RESTORE", "FAILED", errorMessage = "timeout")
        assertEquals("timeout", log.errorMessage)
    }

    @Test
    fun `completedAt nullable for in-flight operations`() {
        val log = SyncOperationLog("id", "BACKUP", "SYNCING")
        assertEquals(null, log.completedAt)
    }
}
