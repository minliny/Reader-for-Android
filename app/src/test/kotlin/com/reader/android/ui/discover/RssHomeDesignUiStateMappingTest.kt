package com.reader.android.ui.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssHomeDesignUiStateMappingTest {

    @Test
    fun `rss home mapper exposes full main tab state matrix`() {
        val states = listOf(
            RssHomeDesignMapper.fromFixture(),
            RssHomeDesignMapper.loading(),
            RssHomeDesignMapper.empty(),
            RssHomeDesignMapper.unreadEmpty(),
            RssHomeDesignMapper.error()
        )

        assertEquals(
            listOf(
                RssHomeDisplayState.Default,
                RssHomeDisplayState.Loading,
                RssHomeDisplayState.Empty,
                RssHomeDisplayState.UnreadEmpty,
                RssHomeDisplayState.Error
            ),
            states.map { it.displayState }
        )
        states.forEach { state ->
            assertEquals(listOf("书架", "发现", "RSS", "设置"), state.bottomNavLabels)
            assertEquals(5, state.statusFilters.size)
            assertEquals(4, state.sourceFilters.size)
        }
    }

    @Test
    fun `rss home default state maps fixture summary and entries`() {
        val state = RssHomeDesignMapper.fromFixture()

        assertEquals("12", state.rssState.summaryFeedCountLabel)
        assertEquals("38", state.rssState.unreadCountLabel)
        assertEquals("刚刚更新", state.rssState.latestUpdateLabel)
        assertEquals("24", state.rssState.visibleCountLabel)
        assertTrue(state.rssState.articles.any { it.title == "《长夜余火》更新到第 33 章" })
        assertTrue(state.rssState.articles.any { it.title == "本周 Android Compose 动画笔记" })
    }

    @Test
    fun `rss home feedback states preserve filter context`() {
        val loading = RssHomeDesignMapper.loading()
        val empty = RssHomeDesignMapper.empty()
        val unreadEmpty = RssHomeDesignMapper.unreadEmpty()
        val error = RssHomeDesignMapper.error()

        assertTrue(loading.rssState.isLoading)
        assertEquals("刷新中", loading.rssState.latestUpdateLabel)
        assertEquals("0", empty.rssState.summaryFeedCountLabel)
        assertEquals("未读", unreadEmpty.statusFilters.single { it.active }.label)
        assertTrue(unreadEmpty.rssState.articles.isEmpty())
        assertEquals("订阅流加载失败", error.rssState.error?.title)
        assertTrue(error.rssState.error?.message.orEmpty().contains("保留上次订阅框架"))
        assertEquals("加载失败", error.rssState.latestUpdateLabel)
    }
}
