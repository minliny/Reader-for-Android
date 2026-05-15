package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSyncProtocolTest {

    private val local = ProgressSyncRecord("url", "Book", "ch5", "Ch5", 4, 0.5f, 1000, "device-a")
    private val remote = ProgressSyncRecord("url", "Book", "ch10", "Ch10", 9, 0.2f, 2000, "device-b")

    @Test
    fun `NEWER_WINS chooses remote when newer`() {
        val result = SyncConflictResolver.resolve(local, remote, ConflictStrategy.NEWER_WINS)
        assertEquals("Ch10", result!!.currentChapterTitle)
    }

    @Test
    fun `LOCAL_WINS chooses local`() {
        val result = SyncConflictResolver.resolve(local, remote, ConflictStrategy.LOCAL_WINS)
        assertEquals("Ch5", result!!.currentChapterTitle)
    }

    @Test
    fun `REMOTE_WINS chooses remote`() {
        val result = SyncConflictResolver.resolve(local, remote, ConflictStrategy.REMOTE_WINS)
        assertEquals("Ch10", result!!.currentChapterTitle)
    }

    @Test
    fun `MANUAL returns null for user intervention`() {
        assertNull(SyncConflictResolver.resolve(local, remote, ConflictStrategy.MANUAL))
    }

    @Test
    fun `needsConflict returns true when chapter differs`() {
        assertTrue(SyncConflictResolver.needsConflict(local, remote))
    }

    @Test
    fun `needsConflict returns false for identical records`() {
        assertFalse(SyncConflictResolver.needsConflict(local, local))
    }
}
