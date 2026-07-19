package com.reader.android.data.repository

import com.reader.android.data.storage.ReadingProgress
import com.reader.android.data.storage.ReadingProgressDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Room is a confirmed-success projection; these tests contain no Core seam. */
class ReadingProgressRepositoryTest {

    @Test
    fun `confirmed Core progress is projected to legacy Room fields`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repository = ReadingProgressRepository(dao)

        repository.mirrorConfirmedProgress(confirmed())

        val stored = repository.getProgress("book-1")!!
        assertEquals("Book", stored.bookName)
        assertEquals("Author", stored.author)
        assertEquals("chapter://7", stored.currentChapterUrl)
        assertEquals("Chapter 7", stored.currentChapterTitle)
        assertEquals(2, stored.chapterIndex)
        assertEquals(10, stored.totalChapters)
        assertEquals(4, stored.page)
        assertEquals(0.625f, stored.scrollPosition, 0.0f)
        assertEquals(1_700_000_000_000L, stored.lastReadTime)
    }

    @Test
    fun `newer confirmed mirror replaces the same book projection`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repository = ReadingProgressRepository(dao)
        repository.mirrorConfirmedProgress(confirmed())
        repository.mirrorConfirmedProgress(
            confirmed().copy(pageIndex = 5, chapterProgress = 0.75, updatedAt = 1_700_000_001L)
        )

        assertEquals(1, dao.storage.size)
        assertEquals(5, repository.getProgress("book-1")?.page)
        assertEquals(0.75f, repository.getProgress("book-1")?.scrollPosition)
    }

    @Test
    fun `recent and all read only from confirmed mirror`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repository = ReadingProgressRepository(dao)
        repository.mirrorConfirmedProgress(confirmed().copy(bookId = "book-1", updatedAt = 10L))
        repository.mirrorConfirmedProgress(confirmed().copy(bookId = "book-2", updatedAt = 20L))
        repository.mirrorConfirmedProgress(confirmed().copy(bookId = "book-3", updatedAt = 30L))

        assertEquals(listOf("book-3", "book-2"), repository.getRecent(2).map { it.bookUrl })
        assertEquals(listOf("book-3", "book-2", "book-1"), repository.getAll().map { it.bookUrl })
    }

    @Test
    fun `delete local mirror does not imply canonical Core deletion`() = runBlocking {
        val dao = FakeReadingProgressDao()
        val repository = ReadingProgressRepository(dao)
        repository.mirrorConfirmedProgress(confirmed())

        repository.deleteLocalMirror("book-1")

        assertNull(repository.getProgress("book-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unconfirmed invalid canonical row cannot create mirror DTO`() {
        confirmed().copy(locationRevision = "")
    }

    private fun confirmed() = ConfirmedReadingProgress(
        sourceId = "source-1",
        bookId = "book-1",
        bookName = "Book",
        author = "Author",
        chapterUrl = "chapter://7",
        chapterTitle = "Chapter 7",
        chapterPosition = 2,
        totalChapters = 10,
        pageIndex = 4,
        chapterOffset = 25L,
        chapterProgress = 0.625,
        locationRevision = "rev-25",
        updatedAt = 1_700_000_000L
    )
}

private class FakeReadingProgressDao : ReadingProgressDao {
    val storage = mutableMapOf<String, ReadingProgress>()

    override suspend fun getAll(): List<ReadingProgress> =
        storage.values.sortedByDescending { it.lastReadTime }

    override suspend fun getRecent(limit: Int): List<ReadingProgress> =
        getAll().take(limit)

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
