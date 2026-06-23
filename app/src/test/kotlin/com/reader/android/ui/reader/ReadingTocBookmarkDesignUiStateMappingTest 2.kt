package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTocBookmarkDesignUiStateMappingTest {

    @Test
    fun `default fixture maps directory panel and current chapter weak state`() {
        val state = ReadingTocBookmarkMapper.fromFixture()

        assertEquals(ReadingTocBookmarkDisplayState.Default, state.displayState)
        assertEquals("长夜余火", state.topControl.bookTitle)
        assertEquals("起点中文网 · 第 32 章", state.topControl.sourceLine)
        assertEquals("阅读 38% · 共 128 章", state.panel.meta)
        assertEquals("directory", state.activeTabType)
        assertEquals(10, state.visibleChapters.size)

        val current = state.visibleChapters.single { it.current }
        assertEquals("第 32 章 雨夜", current.title)
        assertEquals("当前 · 38%", current.status)
        assertTrue(current.read)
    }

    @Test
    fun `bookmark and empty states switch only the panel content`() {
        val bookmark = ReadingTocBookmarkMapper.bookmark()
        val empty = ReadingTocBookmarkMapper.empty()

        assertEquals(ReadingTocBookmarkDisplayState.Bookmark, bookmark.displayState)
        assertEquals("bookmark", bookmark.activeTabType)
        assertEquals(2, bookmark.visibleBookmarks.size)
        assertEquals("第 32 章 雨夜", bookmark.visibleBookmarks.first().chapter)
        assertEquals("38%", bookmark.visibleBookmarks.first().location)

        assertEquals(ReadingTocBookmarkDisplayState.Empty, empty.displayState)
        assertEquals("bookmark", empty.activeTabType)
        assertTrue(empty.visibleBookmarks.isEmpty())
        assertEquals("暂无书签", empty.empty.title)
        assertEquals("返回目录", empty.empty.primaryAction)
    }

    @Test
    fun `search filters chapters inside reader context`() {
        val search = ReadingTocBookmarkMapper.search()

        assertEquals(ReadingTocBookmarkDisplayState.Search, search.displayState)
        assertEquals("雨", search.search.query)
        assertEquals("搜索到 3 个章节", search.search.resultsLabel)
        assertEquals(3, search.visibleChapters.size)
        assertTrue(search.visibleChapters.any { it.title == "第 37 章 雨停" })
        assertTrue(search.visibleChapters.any { it.current })
        assertEquals("directory", search.activeTabType)
    }

    @Test
    fun `loading error and more menu preserve reader shell constraints`() {
        val loading = ReadingTocBookmarkMapper.loading()
        val error = ReadingTocBookmarkMapper.error()
        val moreMenu = ReadingTocBookmarkMapper.moreMenu()

        assertEquals(ReadingTocBookmarkDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.loading.title)
        assertEquals(ReadingTocBookmarkDisplayState.Error, error.displayState)
        assertEquals("加载失败，请重试", error.error.title)
        assertTrue(error.error.message.contains("已保留当前阅读位置"))
        assertEquals("重试", error.error.primaryAction)

        assertEquals(ReadingTocBookmarkDisplayState.MoreMenu, moreMenu.displayState)
        assertEquals(listOf("缓存当前卷", "只看未读"), moreMenu.moreMenu.items.map { it.label })
        assertFalse("More menu must not introduce comments or community actions",
            moreMenu.moreMenu.items.any { "评论" in it.label || "社区" in it.label })
    }
}
