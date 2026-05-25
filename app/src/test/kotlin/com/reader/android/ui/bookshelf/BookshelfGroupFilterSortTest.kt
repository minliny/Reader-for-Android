package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfGroupFilterSortTest {

    private val books = listOf(
        BookshelfBookUiModel("1", "B书", null, "本地书籍", "章", 0.5f, BookshelfCacheState.None, "", ""),
        BookshelfBookUiModel("2", "A书", "作者A", "网络书源", "章", 0f, BookshelfCacheState.Cached, "", ""),
        BookshelfBookUiModel("3", "C书", null, "网络书源", "章", 0.8f, BookshelfCacheState.None, "", "")
    )

    @Test
    fun `filter all returns all`() {
        assertEquals(3, BookshelfGroupFilterSort.filter(books, "全部").size)
    }

    @Test
    fun `filter reading shows books with progress`() {
        val result = BookshelfGroupFilterSort.filter(books, "正在阅读")
        assertEquals(2, result.size)
        assertTrue(result.all { it.progress > 0f })
    }

    @Test
    fun `filter unread shows books without progress`() {
        val result = BookshelfGroupFilterSort.filter(books, "未读")
        assertEquals(1, result.size)
    }

    @Test
    fun `filter local shows local books only`() {
        val result = BookshelfGroupFilterSort.filter(books, "本地书籍")
        assertEquals(1, result.size)
        assertEquals("本地书籍", result[0].sourceName)
    }

    @Test
    fun `sort by title ascending`() {
        val result = BookshelfGroupFilterSort.sort(books, BookshelfGroupFilterSort.SortBy.TITLE)
        assertEquals("A书", result[0].title)
        assertEquals("B书", result[1].title)
        assertEquals("C书", result[2].title)
    }

    @Test
    fun `sort by progress descending`() {
        val result = BookshelfGroupFilterSort.sort(books, BookshelfGroupFilterSort.SortBy.PROGRESS)
        assertEquals(0.8f, result[0].progress)
    }

    @Test
    fun `apply filter and sort`() {
        val result = BookshelfGroupFilterSort.apply(books, "全部", BookshelfGroupFilterSort.SortBy.TITLE)
        assertEquals(3, result.size)
        assertEquals("A书", result[0].title)
    }

    @Test
    fun `groups returns all group labels`() {
        val groups = BookshelfGroupFilterSort.groups()
        assertTrue(groups.contains("全部"))
        assertTrue(groups.contains("正在阅读"))
    }
}
