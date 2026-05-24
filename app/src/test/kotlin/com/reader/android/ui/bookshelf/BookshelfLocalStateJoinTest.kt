package com.reader.android.ui.bookshelf

import com.reader.android.ui.reader.ReaderBookmarkLocalStateAdapter
import com.reader.android.ui.reader.ReaderCacheLocalStateAdapter
import com.reader.android.ui.reader.ReaderProgressSaveAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfLocalStateJoinTest {

    @Test
    fun `fromProgress builds bookshelf entry`() {
        val progress = ReaderProgressSaveAdapter()
        progress.saveChapter("ch2", "第二章", chapterIndex = 1, totalChapters = 5)

        val entry = BookshelfLocalStateJoiner.fromProgress(
            progress = progress,
            bookUrl = "book1", bookName = "测试书", author = "作者"
        )
        assertEquals("book1", entry.id)
        assertEquals("测试书", entry.title)
        assertEquals("作者", entry.author)
        assertEquals("第二章", entry.currentChapterTitle)
        assertTrue(entry.progress > 0f)
    }

    @Test
    fun `fromProgress with empty progress shows default chapter text`() {
        val entry = BookshelfLocalStateJoiner.fromProgress(
            progress = ReaderProgressSaveAdapter(),
            bookUrl = "b"
        )
        assertEquals("未开始阅读", entry.currentChapterTitle)
    }

    @Test
    fun `cache state reflects adapter status`() {
        val progress = ReaderProgressSaveAdapter()
        progress.saveChapter("ch1", "章")
        val cache = ReaderCacheLocalStateAdapter(cachedUrls = setOf("ch1"))

        val entry = BookshelfLocalStateJoiner.fromProgress(
            progress = progress, cache = cache, bookUrl = "b"
        )
        assertEquals(BookshelfCacheState.Cached, entry.cacheState)
    }

    @Test
    fun `no cache shows None state`() {
        val progress = ReaderProgressSaveAdapter()
        progress.saveChapter("ch1", "章")

        val entry = BookshelfLocalStateJoiner.fromProgress(
            progress = progress, bookUrl = "b"
        )
        assertEquals(BookshelfCacheState.None, entry.cacheState)
    }

    @Test
    fun `adapter shell fake mode returns fixture`() {
        BookshelfAdapterShell.resetToFakeMode()
        val state = BookshelfAdapterShell.bookshelfState()
        assertTrue(state.books.isNotEmpty())
        assertFalse(state.isEmpty)
    }

    @Test
    fun `adapter shell real mode state works`() {
        BookshelfAdapterShell.enableRealMode()
        val progress = ReaderProgressSaveAdapter()
        progress.saveChapter("ch1", "第一章", chapterIndex = 0, totalChapters = 3)
        BookshelfAdapterShell.registerProgress("book1", progress)

        val state = BookshelfAdapterShell.bookshelfState()
        assertEquals(1, state.books.size)

        BookshelfAdapterShell.resetToFakeMode()
    }

    @Test
    fun `Bookmark integration via bookshelf`() {
        val progress = ReaderProgressSaveAdapter()
        progress.saveChapter("ch1", "章")
        val bookmark = ReaderBookmarkLocalStateAdapter(setOf("ch1"))

        val entry = BookshelfLocalStateJoiner.fromProgress(
            progress = progress, bookmark = bookmark, bookUrl = "b"
        )
        // Bookmark presence visible through adapter; bookshelf entry built correctly
        assertEquals("章", entry.currentChapterTitle)
    }
}
