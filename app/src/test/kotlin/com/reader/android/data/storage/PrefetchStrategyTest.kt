package com.reader.android.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchStrategyTest {

    @Test
    fun `default prefetches 2 chapters ahead`() {
        assertEquals(2, PrefetchStrategy.DEFAULT.chaptersAhead)
    }

    @Test
    fun `aggressive prefetches 5 chapters ahead with larger cache`() {
        assertEquals(5, PrefetchStrategy.AGGRESSIVE.chaptersAhead)
        assertEquals(50, PrefetchStrategy.AGGRESSIVE.maxCacheChapters)
    }

    @Test
    fun `OFF strategy has prefetch disabled`() {
        assertFalse(PrefetchStrategy.OFF.enabled)
    }

    @Test
    fun `custom strategy validates range`() {
        val s = PrefetchStrategy(chaptersAhead = 3, maxCacheChapters = 30)
        assertEquals(3, s.chaptersAhead)
        assertEquals(30, s.maxCacheChapters)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `chaptersAhead above 10 throws`() {
        PrefetchStrategy(chaptersAhead = 11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `maxCacheChapters above 100 throws`() {
        PrefetchStrategy(maxCacheChapters = 101)
    }

    @Test
    fun `wifiOnly defaults to false`() {
        assertFalse(PrefetchStrategy.DEFAULT.wifiOnly)
    }
}
