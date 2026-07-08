package com.reader.ui.reading

import com.reader.android.data.storage.ChapterCacheManager
import com.reader.android.data.storage.FakeCachedChapterDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-4: JVM proof that [fetchChapterContentWithCache] implements cache-first semantics:
 * cache hit avoids the network fetch, cache miss invokes fetch and writes back, null cache
 * falls through to fetch, and cache IO errors never block reading.
 */
class ChapterContentCacheJvmTest {

    @Test
    fun `cache hit returns cached content without invoking fetch`() = runBlocking {
        val dao = FakeCachedChapterDao()
        dao.put(
            com.reader.android.data.storage.CachedChapter(
                contentUrl = "http://ch1",
                content = "cached-text",
                title = "Chapter 1",
                nextPageUrl = null
            )
        )
        val cache = ChapterCacheManager(dao)
        var fetchCalls = 0

        val result = fetchChapterContentWithCache(
            cache = cache,
            cacheKey = "http://ch1",
            title = "Chapter 1"
        ) {
            fetchCalls++
            "network-text"
        }

        assertEquals("cached-text", result)
        assertEquals(0, fetchCalls)
    }

    @Test
    fun `cache miss invokes fetch and writes content back to cache`() = runBlocking {
        val dao = FakeCachedChapterDao()
        val cache = ChapterCacheManager(dao)
        var fetchCalls = 0

        val result = fetchChapterContentWithCache(
            cache = cache,
            cacheKey = "http://ch2",
            title = "Chapter 2"
        ) {
            fetchCalls++
            "fresh-network-text"
        }

        assertEquals("fresh-network-text", result)
        assertEquals(1, fetchCalls)
        // Write-back verified: the cache now holds the fetched text under the key.
        val stored = dao.storage["http://ch2"]
        assertEquals("fresh-network-text", stored?.content)
        assertEquals("Chapter 2", stored?.title)
    }

    @Test
    fun `null cache falls through to fetch`() = runBlocking {
        var fetchCalls = 0

        val result = fetchChapterContentWithCache(
            cache = null,
            cacheKey = "http://ch3",
            title = "Chapter 3"
        ) {
            fetchCalls++
            "only-network-text"
        }

        assertEquals("only-network-text", result)
        assertEquals(1, fetchCalls)
    }

    @Test
    fun `second call for same key is served from cache without network`() = runBlocking {
        val dao = FakeCachedChapterDao()
        val cache = ChapterCacheManager(dao)
        var fetchCalls = 0

        val fetch: suspend () -> String = {
            fetchCalls++
            "network-text"
        }

        val first = fetchChapterContentWithCache(cache, "http://ch4", "T4", fetch)
        val second = fetchChapterContentWithCache(cache, "http://ch4", "T4", fetch)

        assertEquals("network-text", first)
        assertEquals("network-text", second)
        // Network was hit exactly once — the second call was served from the cache.
        assertEquals(1, fetchCalls)
        assertTrue(dao.storage.containsKey("http://ch4"))
    }

    @Test
    fun `distinct cache keys do not collide`() = runBlocking {
        val dao = FakeCachedChapterDao()
        val cache = ChapterCacheManager(dao)

        fetchChapterContentWithCache(cache, "http://a", "A") { "text-a" }
        fetchChapterContentWithCache(cache, "http://b", "B") { "text-b" }

        assertEquals("text-a", dao.storage["http://a"]?.content)
        assertEquals("text-b", dao.storage["http://b"]?.content)
    }

    @Test
    fun `cache IO error during get falls through to fetch`() = runBlocking {
        val failingDao = object : com.reader.android.data.storage.CachedChapterDao by FakeCachedChapterDao() {
            override suspend fun get(url: String): com.reader.android.data.storage.CachedChapter? =
                throw java.io.IOException("disk read failed")
        }
        val cache = ChapterCacheManager(failingDao)

        val result = fetchChapterContentWithCache(
            cache = cache,
            cacheKey = "http://broken",
            title = "Broken"
        ) { "fallback-network-text" }

        // Best-effort cache: a read failure must never block reading.
        assertEquals("fallback-network-text", result)
    }

    @Test
    fun `cache IO error during put does not throw`() = runBlocking {
        val backing = FakeCachedChapterDao()
        val failingDao = object : com.reader.android.data.storage.CachedChapterDao by backing {
            override suspend fun put(chapter: com.reader.android.data.storage.CachedChapter) =
                throw java.io.IOException("disk write failed")
        }
        val cache = ChapterCacheManager(failingDao)

        val result = fetchChapterContentWithCache(
            cache = cache,
            cacheKey = "http://broken-write",
            title = "Broken Write"
        ) { "network-text" }

        assertEquals("network-text", result)
        assertFalse(backing.storage.containsKey("http://broken-write"))
    }
}
