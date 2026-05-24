package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCacheFlowTest {

    @Test
    fun `cache miss returns null`() {
        val cache = ReaderCacheSaveAdapter()
        assertNull(cache.read("https://example.com/ch1"))
    }

    @Test
    fun `cache write then read returns content`() {
        val cache = ReaderCacheSaveAdapter()
        cache.write("https://example.com/ch1", "正文内容", "第一章")
        val hit = cache.read("https://example.com/ch1")
        assertNotNull(hit)
        assertEquals("正文内容", hit!!.content)
        assertEquals("第一章", hit.title)
    }

    @Test
    fun `hasCache true after write`() {
        val cache = ReaderCacheSaveAdapter()
        cache.write("url", "content", "title")
        assertTrue(cache.hasCache("url"))
        assertFalse(cache.hasCache("other"))
    }

    @Test
    fun `evict removes cached content`() {
        val cache = ReaderCacheSaveAdapter()
        cache.write("url", "content", "title")
        cache.evict("url")
        assertNull(cache.read("url"))
        assertFalse(cache.hasCache("url"))
    }

    @Test
    fun `adapter reflects cache state`() {
        val cache = ReaderCacheSaveAdapter()
        cache.write("url", "content", "title")
        val adapter = cache.adapter
        assertEquals(ReaderCacheLocalStateAdapter.CacheStatus.CACHED, adapter.statusFor("url"))
        assertTrue(adapter.isOfflineAvailable("url"))
    }

    @Test
    fun `blank url ignored on write`() {
        val cache = ReaderCacheSaveAdapter()
        cache.write("", "content", "title")
        assertFalse(cache.hasCache(""))
    }

    @Test
    fun `blank url returns null on read`() {
        assertNull(ReaderCacheSaveAdapter().read(""))
    }

    @Test
    fun `clear removes all cached`() {
        val cache = ReaderCacheSaveAdapter()
        cache.write("a", "A", null)
        cache.write("b", "B", null)
        cache.clear()
        assertNull(cache.read("a"))
        assertNull(cache.read("b"))
    }
}
