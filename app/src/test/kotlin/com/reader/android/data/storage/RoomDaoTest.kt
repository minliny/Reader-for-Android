package com.reader.android.data.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressEntityTest {

    @Test
    fun `ReadingProgress entity construction and field access`() {
        val progress = ReadingProgress(
            bookUrl = "http://book/1",
            bookName = "一剑独尊",
            author = "青鸾峰上",
            currentChapterUrl = "http://book/1/ch5",
            currentChapterTitle = "第五章",
            chapterIndex = 4,
            totalChapters = 1024,
            scrollPosition = 0.5f,
            lastReadTime = 1700000000000L
        )

        assertEquals("http://book/1", progress.bookUrl)
        assertEquals("一剑独尊", progress.bookName)
        assertEquals("青鸾峰上", progress.author)
        assertEquals("第五章", progress.currentChapterTitle)
        assertEquals(4, progress.chapterIndex)
        assertEquals(1024, progress.totalChapters)
        assertEquals("青鸾峰上", progress.author)
        assertEquals(1700000000000L, progress.lastReadTime)
    }

    @Test
    fun `ReadingProgress copy keeps PK and updates fields`() {
        val p = ReadingProgress(
            bookUrl = "url", bookName = "A",
            author = null, currentChapterUrl = "ch1", currentChapterTitle = "Ch1"
        )
        val p2 = p.copy(currentChapterTitle = "Ch2", chapterIndex = 1)
        assertEquals("url", p2.bookUrl) // PK unchanged
        assertEquals("Ch2", p2.currentChapterTitle)
        assertEquals(1, p2.chapterIndex)
    }

    @Test
    fun `ReadingProgress nullable author`() {
        val p = ReadingProgress(
            bookUrl = "url", bookName = "A",
            currentChapterUrl = "ch1", currentChapterTitle = "Ch1",
            author = null
        )
        assertNull(p.author)
    }
}

class CachedChapterEntityTest {

    @Test
    fun `CachedChapter construction and fields`() {
        val chapter = CachedChapter(
            contentUrl = "http://book/1/ch1",
            content = "正文内容",
            title = "第一章",
            nextPageUrl = "http://book/1/ch2",
            cachedAt = 1700000000000L
        )

        assertEquals("http://book/1/ch1", chapter.contentUrl)
        assertEquals("正文内容", chapter.content)
        assertEquals("第一章", chapter.title)
        assertEquals("http://book/1/ch2", chapter.nextPageUrl)
        assertEquals(1700000000000L, chapter.cachedAt)
    }

    @Test
    fun `CachedChapter nullable title and nextPageUrl`() {
        val chapter = CachedChapter(
            contentUrl = "url", content = "content",
            title = null, nextPageUrl = null
        )
        assertNull(chapter.title)
        assertNull(chapter.nextPageUrl)
    }
}

class ChapterCacheManagerLogicTest {

    @Test
    fun `put stores chapter via fake DAO`() = runBlocking {
        val fakeDao = FakeCachedChapterDao()
        val manager = ChapterCacheManager(fakeDao)
        manager.put("http://ch1", "content", "Title", "http://next")

        val cached = fakeDao.storage["http://ch1"]
        assertNotNull(cached)
        assertEquals("content", cached!!.content)
        assertEquals("Title", cached.title)
        assertEquals("http://next", cached.nextPageUrl)
    }

    @Test
    fun `get returns null for unknown URL`() = runBlocking {
        val fakeDao = FakeCachedChapterDao()
        val manager = ChapterCacheManager(fakeDao)
        assertNull(manager.get("http://nonexistent"))
    }

    @Test
    fun `clear removes all entries`() = runBlocking {
        val fakeDao = FakeCachedChapterDao()
        val manager = ChapterCacheManager(fakeDao)
        manager.put("http://a", "A", null, null)
        manager.put("http://b", "B", null, null)
        assertEquals(2, fakeDao.storage.size)

        manager.clear()
        assertEquals(0, fakeDao.storage.size)
    }
}

class ReadingProgressPayloadContractTest {

    @Test
    fun `scrollPosition default is zero`() {
        val p = ReadingProgress(
            bookUrl = "u", bookName = "n",
            author = null, currentChapterUrl = "c", currentChapterTitle = "t",
            scrollPosition = 0f
        )
        assertEquals(0f, p.scrollPosition)
    }

    @Test
    fun `lastReadTime is set at construction`() {
        val before = System.currentTimeMillis()
        val p = ReadingProgress(
            bookUrl = "u", bookName = "n",
            author = null, currentChapterUrl = "c", currentChapterTitle = "t"
        )
        val after = System.currentTimeMillis()
        assertTrue(p.lastReadTime in before..after)
    }
}

// ── Test doubles ──

class FakeCachedChapterDao : CachedChapterDao {
    val storage = mutableMapOf<String, CachedChapter>()

    override suspend fun get(url: String): CachedChapter? = storage[url]

    override suspend fun put(chapter: CachedChapter) {
        storage[chapter.contentUrl] = chapter
    }

    override suspend fun evictOlderThan(before: Long) {
        storage.entries.removeAll { it.value.cachedAt < before }
    }

    override suspend fun clear() {
        storage.clear()
    }
}
