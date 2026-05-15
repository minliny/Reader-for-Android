package com.reader.android.data.adapter

import com.reader.android.data.storage.ChapterCacheManager
import com.reader.android.data.storage.FakeCachedChapterDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCacheManagerTest {
    private val fakeDao = FakeCachedChapterDao()
    private val cache = ChapterCacheManager(fakeDao)
    private val provider = RemoteContentProvider("http://server.com")
    private val manager = DownloadCacheManager(cache, provider)

    @Test
    fun `download and cache stores content with cache key`() = runBlocking {
        manager.downloadAndCache("http://server.com/ch1", "content", "Ch1", "/ch2")
        val cached = manager.getCached("http://server.com/ch1")
        assertNotNull(cached)
        assertEquals("content", cached!!.content)
    }

    @Test
    fun `getCached returns null for unknown URL`() = runBlocking {
        assert(manager.getCached("http://unknown") == null)
    }

    @Test
    fun `markTemp and isTemp`() {
        manager.markTemp("key1")
        assertTrue(manager.isTemp("key1"))
        assertFalse(manager.isTemp("key2"))
    }
}
