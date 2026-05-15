package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictScenarioTest {

    private val local = ProgressSyncRecord("url", "Book", "ch5", "Ch5", 4, 0.5f, 1000, "local")
    private val newerRemote = ProgressSyncRecord("url", "Book", "ch10", "Ch10", 9, 0.2f, 2000, "remote")
    private val olderRemote = ProgressSyncRecord("url", "Book", "ch2", "Ch2", 1, 0.1f, 500, "remote")

    @Test
    fun `NEWER_WINS selects newer when remote is newer`() {
        val result = SyncConflictResolver.resolve(local, newerRemote, ConflictStrategy.NEWER_WINS)
        assertEquals(9, result!!.chapterIndex)
    }

    @Test
    fun `NEWER_WINS selects local when local is newer`() {
        val result = SyncConflictResolver.resolve(local, olderRemote, ConflictStrategy.NEWER_WINS)
        assertEquals(4, result!!.chapterIndex)
    }

    @Test
    fun `null local returns remote`() {
        val result = SyncConflictResolver.resolve(null, newerRemote, ConflictStrategy.NEWER_WINS)
        assertEquals(newerRemote, result)
    }

    @Test
    fun `null remote returns local`() {
        val result = SyncConflictResolver.resolve(local, null, ConflictStrategy.NEWER_WINS)
        assertEquals(local, result)
    }

    @Test
    fun `both null returns null`() {
        assertNull(SyncConflictResolver.resolve(null, null, ConflictStrategy.NEWER_WINS))
    }

    @Test
    fun `identical records have no conflict`() {
        assertFalse(SyncConflictResolver.needsConflict(local, local))
        assertTrue(SyncConflictResolver.needsConflict(local, newerRemote))
    }
}
