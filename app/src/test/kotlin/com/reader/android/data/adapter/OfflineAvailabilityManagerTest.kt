package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAvailabilityManagerTest {

    @Test
    fun `markCached makes key available`() {
        val mgr = OfflineAvailabilityManager()
        mgr.markCached("k1", "url1", 1024)
        assertTrue(mgr.isAvailable("k1"))
    }

    @Test
    fun `isAvailable returns false for unknown key`() {
        assertFalse(OfflineAvailabilityManager().isAvailable("unknown"))
    }

    @Test
    fun `evict removes excess entries`() {
        val mgr = OfflineAvailabilityManager()
        mgr.maxEntries = 2
        mgr.markCached("a", "ua", 1)
        mgr.markCached("b", "ub", 1)
        mgr.markCached("c", "uc", 1)
        val removed = mgr.evict()
        assertEquals(1, removed.size)
        assertEquals(2, mgr.size())
    }

    @Test
    fun `evict returns empty when under limit`() {
        val mgr = OfflineAvailabilityManager()
        mgr.markCached("a", "ua", 1)
        assertTrue(mgr.evict().isEmpty())
    }

    @Test
    fun `touch updates last accessed`() {
        val mgr = OfflineAvailabilityManager()
        mgr.markCached("k", "url", 1)
        mgr.touch("k")
        assertTrue(mgr.isAvailable("k"))
    }
}
