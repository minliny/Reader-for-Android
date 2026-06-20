package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSearchDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes in book search input filters and results`() {
        val state = ContentSearchMapper.fromFixture()

        assertEquals(ContentSearchDisplayState.Default, state.displayState)
        assertEquals("搜索本书内容", state.search.label)
        assertEquals("雨夜", state.search.query)
        assertEquals("输入关键词", state.search.placeholder)
        assertEquals("18 处", state.search.resultCount)
        assertEquals("清空", state.search.clearLabel)
        assertEquals("本书内容搜索", state.panel.title)
        assertEquals("长夜余火", state.panel.bookTitle)
        assertEquals("点击结果跳转并高亮", state.panel.tip)
        assertEquals("正文", state.activeFilter.label)
        assertEquals(listOf("全部", "章节名", "正文", "书签"), state.filters.map { it.label })
        assertEquals(4, state.results.size)

        val first = state.results.first()
        assertEquals("第 32 章 雨夜", first.title)
        assertEquals("第 3 段 · 约 38%", first.meta)
        assertEquals("当前章节", first.progressLabel)
        assertTrue("雨夜" in first.excerpt)
    }

    @Test
    fun `loading empty error and offline states retain query and reading context`() {
        val base = ContentSearchMapper.fromFixture()
        val loading = ContentSearchMapper.loading()
        val empty = ContentSearchMapper.empty()
        val error = ContentSearchMapper.error()
        val offline = ContentSearchMapper.offline()

        assertEquals(ContentSearchDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.loading.title)
        assertEquals("正在检索本书正文，请稍候。", loading.loading.message)
        assertEquals(base.search, loading.search)

        assertEquals(ContentSearchDisplayState.Empty, empty.displayState)
        assertEquals("无匹配结果", empty.empty.title)
        assertEquals("清空关键词", empty.empty.primaryAction)
        assertEquals(base.search.query, empty.search.query)

        assertEquals(ContentSearchDisplayState.Error, error.displayState)
        assertEquals("搜索失败，请重试", error.error.title)
        assertTrue("已保留关键词和当前阅读位置" in error.error.message)
        assertEquals("重试", error.error.primaryAction)
        assertEquals(base.reading, error.reading)

        assertEquals(ContentSearchDisplayState.Offline, offline.displayState)
        assertEquals("网络不可用，请稍后重试", offline.offline.title)
        assertTrue("本地缓存正文仍可搜索" in offline.offline.message)
        assertEquals(base.results, offline.results)
    }

    @Test
    fun `content search remains scoped to current book content`() {
        val state = ContentSearchMapper.fromFixture()
        val visibleText = (
            listOf(state.search.label, state.panel.title, state.panel.bookTitle, state.panel.tip) +
                state.filters.map { it.label } +
                state.results.flatMap { listOf(it.title, it.meta, it.progressLabel, it.excerpt) }
            ).joinToString(" ")

        listOf("全网", "书城", "来源", "换源", "推荐", "广告", "会员", "社区").forEach { forbidden ->
            assertFalse("Content search fixture must not expose $forbidden semantics", forbidden in visibleText)
        }
        assertTrue("Search result semantics must mention in-book jump and highlight", "跳转并高亮" in state.panel.tip)
    }
}
