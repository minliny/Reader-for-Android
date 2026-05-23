package com.reader.android.ui.bookshelf

import com.reader.android.data.storage.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class BookshelfUiStateMappingTest {

    @Test
    fun `existing reading progress maps to bookshelf ui state`() {
        val state = BookshelfMapper.fromReadingProgress(
            progress = listOf(
                ReadingProgress(
                    bookUrl = "content://books/local.txt",
                    bookName = "本地小说",
                    author = "作者",
                    currentChapterUrl = "content://books/local.txt#ch2",
                    currentChapterTitle = "第二章",
                    chapterIndex = 1,
                    totalChapters = 4,
                    scrollPosition = 0.5f
                )
            ),
            cachedBookUrls = setOf("content://books/local.txt")
        )

        assertFalse(state.isEmpty)
        assertEquals(BookshelfLayoutMode.Cover, state.layoutMode)
        assertEquals("本地小说", state.books.single().title)
        assertEquals("本地书籍", state.books.single().sourceName)
        assertEquals(BookshelfCacheState.Cached, state.books.single().cacheState)
        assertEquals(0.375f, state.books.single().progress, 0.0001f)
    }

    @Test
    fun `empty progress maps to empty bookshelf state`() {
        val state = BookshelfMapper.fromReadingProgress(emptyList())

        assertTrue(state.isEmpty)
        assertEquals("书架为空", state.emptyMessage)
        assertTrue(state.books.isEmpty())
    }

    @Test
    fun `layout mode distinguishes cover and list state`() {
        val cover = BookshelfMapper.fromReadingProgress(BookshelfFixture.progressItems, BookshelfLayoutMode.Cover)
        val list = BookshelfMapper.fromReadingProgress(BookshelfFixture.progressItems, BookshelfLayoutMode.List)

        assertEquals(BookshelfLayoutMode.Cover, cover.layoutMode)
        assertEquals(BookshelfLayoutMode.List, list.layoutMode)
        assertEquals(cover.books.map { it.id }, list.books.map { it.id })
    }

    @Test
    fun `fake fallback keeps fixture books available`() {
        val state = BookshelfMapper.fakeFallback()

        assertFalse(state.isEmpty)
        assertTrue(state.availableGroups.contains("UI Fixture"))
        assertTrue(state.books.all { it.sourceName == "UI Fixture" })
    }
}
