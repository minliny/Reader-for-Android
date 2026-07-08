package com.reader.android.data.repository

import com.reader.android.data.storage.ReadingProgress
import com.reader.android.data.storage.ReadingProgressDao
import com.reader.api.Chapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM coverage for [ReadingProgressRepository] — verifies the field mapping between
 * the caller-provided reading state (`chapterIndex`, `page`, `progress`) and the Room
 * entity ([ReadingProgress]), plus the restore-on-entry round-trip.
 *
 * Uses a fake in-memory DAO so no Room runtime is required.
 */
class ReadingProgressRepositoryTest {

    @Test
    fun `saveProgress then getProgress round-trips chapterIndex page and progress`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)
        val chapter = Chapter(title = "第32章 雨夜", url = "http://book.example/ch/32", index = 31)

        repo.saveProgress(
            bookUrl = "http://book.example/1",
            bookName = "长夜余火",
            chapterIndex = 31,
            page = 4,
            progress = 0.67f,
            chapter = chapter,
            totalChapters = 120,
            author = "会说话的肘子"
        )

        val restored = repo.getProgress("http://book.example/1")
        assertNotNull(restored)
        assertEquals(31, restored!!.chapterIndex)
        assertEquals(4, restored.page)
        assertEquals(0.67f, restored.scrollPosition, 0.001f)
        assertEquals(120, restored.totalChapters)
    }

    @Test
    fun `saveProgress maps chapter url and title to entity fields`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)
        val chapter = Chapter(title = "第1章 黎明", url = "http://book.example/ch/1", index = 0)

        repo.saveProgress(
            bookUrl = "b1",
            bookName = "书名",
            chapterIndex = 0,
            page = 0,
            progress = 0f,
            chapter = chapter,
            totalChapters = 50,
            author = "作者"
        )

        val restored = repo.getProgress("b1")!!
        assertEquals("http://book.example/ch/1", restored.currentChapterUrl)
        assertEquals("第1章 黎明", restored.currentChapterTitle)
        assertEquals("作者", restored.author)
    }

    @Test
    fun `saveProgress with null chapter leaves url and title blank`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)

        repo.saveProgress(
            bookUrl = "b2",
            bookName = "书名",
            chapterIndex = 5,
            page = 2,
            progress = 0.3f,
            chapter = null,
            totalChapters = 10,
            author = null
        )

        val restored = repo.getProgress("b2")!!
        assertEquals("", restored.currentChapterUrl)
        assertEquals("", restored.currentChapterTitle)
        assertNull(restored.author)
    }

    @Test
    fun `saveProgress upserts on same bookUrl`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)

        repo.saveProgress("b1", "书名", 3, 1, 0.2f, null, 10)
        repo.saveProgress("b1", "书名", 7, 5, 0.8f, null, 10)

        assertEquals(1, dao.storage.size)
        val restored = repo.getProgress("b1")!!
        assertEquals(7, restored.chapterIndex)
        assertEquals(5, restored.page)
        assertEquals(0.8f, restored.scrollPosition, 0.001f)
    }

    @Test
    fun `getProgress returns null for unknown bookUrl`() = runBlocking {
        val repo = ReadingProgressRepository(FakeReadingProgressDao())
        assertNull(repo.getProgress("never-saved"))
    }

    @Test
    fun `delete removes progress`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)
        repo.saveProgress("b1", "书名", 0, 0, 0f, null, 5)

        repo.delete("b1")

        assertNull(repo.getProgress("b1"))
    }

    @Test
    fun `saveProgress sets lastReadTime to approximately now`() = runBlocking {
        val repo = ReadingProgressRepository(FakeReadingProgressDao())
        val before = System.currentTimeMillis()

        repo.saveProgress("b1", "书名", 0, 0, 0f, null, 5)

        val after = System.currentTimeMillis()
        val restored = repo.getProgress("b1")!!
        assert(restored.lastReadTime in before..after) {
            "lastReadTime ${restored.lastReadTime} should be in [$before, $after]"
        }
    }

    @Test
    fun `getRecent returns books ordered by lastReadTime desc`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)

        repo.saveProgress("b1", "书一", 0, 0, 0f, null, 5)
        Thread.sleep(2)
        repo.saveProgress("b2", "书二", 3, 1, 0.4f, null, 10)
        Thread.sleep(2)
        repo.saveProgress("b3", "书三", 1, 0, 0.1f, null, 8)

        val recent = repo.getRecent(limit = 2)
        assertEquals(2, recent.size)
        assertEquals("b3", recent[0].bookUrl)
        assertEquals("b2", recent[1].bookUrl)
    }

    @Test
    fun `getRecent with limit larger than stored returns all`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)
        repo.saveProgress("b1", "一", 0, 0, 0f, null, 1)
        repo.saveProgress("b2", "二", 0, 0, 0f, null, 1)

        val recent = repo.getRecent(limit = 100)
        assertEquals(2, recent.size)
    }

    @Test
    fun `getAll returns all progress ordered by lastReadTime desc`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repo = ReadingProgressRepository(dao)
        repo.saveProgress("b1", "一", 0, 0, 0f, null, 1)
        Thread.sleep(2)
        repo.saveProgress("b2", "二", 0, 0, 0f, null, 1)

        val all = repo.getAll()
        assertEquals(listOf("b2", "b1"), all.map { it.bookUrl })
    }
}

private class FakeReadingProgressDao : ReadingProgressDao {
    val storage = mutableMapOf<String, ReadingProgress>()

    override suspend fun getAll(): List<ReadingProgress> =
        storage.values.sortedByDescending { it.lastReadTime }

    override suspend fun getRecent(limit: Int): List<ReadingProgress> =
        storage.values.sortedByDescending { it.lastReadTime }.take(limit)

    override suspend fun getByUrl(bookUrl: String): ReadingProgress? = storage[bookUrl]

    override suspend fun upsert(progress: ReadingProgress) {
        storage[progress.bookUrl] = progress
    }

    override suspend fun update(progress: ReadingProgress) {
        storage[progress.bookUrl] = progress
    }

    override suspend fun delete(bookUrl: String) {
        storage.remove(bookUrl)
    }
}
