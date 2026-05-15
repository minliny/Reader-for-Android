package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteContentProviderTest {

    @Test
    fun `default mode is CHAPTER_ON_DEMAND`() {
        val provider = RemoteContentProvider("http://server.com/books")
        assertEquals(ContentMode.CHAPTER_ON_DEMAND, provider.mode)
    }

    @Test
    fun `cacheKey includes source and content URL`() {
        val provider = RemoteContentProvider("http://server.com")
        val key = provider.cacheKey("http://server.com/book/ch1")
        assertEquals("remote:http://server.com:http://server.com/book/ch1", key)
    }

    @Test
    fun `custom cache key prefix`() {
        val provider = RemoteContentProvider("url", cacheKeyPrefix = "webdav")
        assertEquals("webdav", provider.cacheKeyPrefix)
    }
}
