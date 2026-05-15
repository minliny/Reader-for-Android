package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RemoteBookRefTest {

    @Test
    fun `RemoteBookRef holds all fields`() {
        val ref = RemoteBookRef("/books/novel.txt", "novel.txt", "txt", 1024, 1700000000L)
        assertEquals("/books/novel.txt", ref.path)
        assertEquals("txt", ref.format)
        assertEquals(1024, ref.sizeBytes)
    }

    @Test
    fun `default cache policy is CACHE_ON_ACCESS`() {
        val policy = RemoteBookCachePolicy()
        assertEquals(DownloadPolicy.CACHE_ON_ACCESS, policy.policy)
        assertEquals(20, policy.maxCacheChapters)
    }

    @Test
    fun `FULL_DOWNLOAD policy`() {
        val policy = RemoteBookCachePolicy(DownloadPolicy.FULL_DOWNLOAD, evictAfterRead = true)
        assertEquals(DownloadPolicy.FULL_DOWNLOAD, policy.policy)
        assert(policy.evictAfterRead)
    }
}
