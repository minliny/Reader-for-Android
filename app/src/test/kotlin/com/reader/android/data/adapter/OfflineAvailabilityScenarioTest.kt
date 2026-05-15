package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAvailabilityScenarioTest {

    @Test
    fun `freshly cached content is available`() {
        val mgr = OfflineAvailabilityManager()
        mgr.markCached("ch1", "url1", 1024)
        assertTrue(mgr.isAvailable("ch1"))
    }

    @Test
    fun `stale content removed by eviction is no longer available`() {
        val mgr = OfflineAvailabilityManager()
        mgr.maxEntries = 1
        mgr.markCached("old", "u1", 1)
        mgr.markCached("new", "u2", 1)
        val removed = mgr.evict()
        assertEquals(listOf("old"), removed)
        assertFalse(mgr.isAvailable("old"))
        assertTrue(mgr.isAvailable("new"))
    }

    @Test
    fun `touch prevents eviction by updating lastAccessed`() {
        val mgr = OfflineAvailabilityManager()
        mgr.maxEntries = 2
        mgr.markCached("a", "ua", 1)
        mgr.markCached("b", "ub", 1)
        mgr.touch("a")
        mgr.markCached("c", "uc", 1)
        val removed = mgr.evict()
        // One entry evicted, size back to max
        assertEquals(1, removed.size)
        assertEquals(2, mgr.size())
    }
}
