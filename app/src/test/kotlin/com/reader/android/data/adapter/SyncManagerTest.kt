package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerTest {
    private val manager = SyncManager()

    @Test
    fun `start transitions to SYNCING`() {
        manager.start(SyncType.BACKUP)
        assertEquals(SyncState.SYNCING, manager.currentState)
    }

    @Test
    fun `complete transitions to SUCCESS when all done`() {
        val op = manager.start(SyncType.PROGRESS_PUSH)
        manager.complete(op.id)
        assertEquals(SyncState.SUCCESS, manager.currentState)
    }

    @Test
    fun `fail increments attempt and allows retry under max`() {
        val op = manager.start(SyncType.BACKUP)
        assertTrue(manager.fail(op.id)) // attempt 1, can retry
        assertTrue(manager.fail(op.id)) // attempt 2
        assertTrue(manager.fail(op.id)) // attempt 3
        assertFalse(manager.fail(op.id)) // attempt 4 > maxRetries=3
        assertEquals(SyncState.FAILED, manager.currentState)
    }

    @Test
    fun `markConflict sets CONFLICT state`() {
        val op = manager.start(SyncType.PROGRESS_PULL)
        manager.markConflict(op.id)
        assertEquals(SyncState.CONFLICT, manager.currentState)
    }

    @Test
    fun `reset returns to IDLE`() {
        manager.start(SyncType.BACKUP)
        manager.reset()
        assertEquals(SyncState.IDLE, manager.currentState)
    }
}
