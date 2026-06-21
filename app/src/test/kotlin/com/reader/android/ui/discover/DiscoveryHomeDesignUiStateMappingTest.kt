package com.reader.android.ui.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryHomeDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes discovery source categories featured content status and ranking`() {
        val state = DiscoveryHomeMapper.fromFixture()

        assertEquals(DiscoveryHomeDisplayState.Default, state.displayState)
        assertEquals("发现", state.topBar.title)
        assertEquals("搜索书名、作者、关键词", state.search.placeholder)
        assertEquals(listOf("全部", "书源", "订阅"), state.sourceTypes.map { it.label })
        assertTrue(state.sourceTypes.first { it.label == "书源" }.active)
        assertEquals("玄幻书源组", state.currentSource.title)
        assertEquals("切换来源", state.currentSource.actionLabel)
        assertEquals(listOf("全部", "玄幻", "都市", "排行", "新书", "完结"), state.categories.map { it.label })
        assertEquals("更多分类", state.categoryMoreLabel)
        assertEquals("玄幻书源组 · 玄幻", state.content.title)
        assertEquals(listOf("道诡异仙", "大奉打更人"), state.content.featured.map { it.title })
        assertEquals("12 个来源", state.statusBar.sourceCount)
        assertEquals("8 个可用", state.statusBar.availableCount)
        assertEquals("检测", state.statusBar.actionLabel)
        assertEquals("来源榜单更新", state.ranking.title)
        assertEquals(listOf("诡秘之主", "三体", "凡人修仙传"), state.ranking.items.map { it.title })
        assertEquals(listOf("书架", "发现", "RSS", "设置"), state.bottomNavLabels)
    }

    @Test
    fun `state variants keep main tab context and expected feedback`() {
        val base = DiscoveryHomeMapper.fromFixture()
        val subscription = DiscoveryHomeMapper.subscription()
        val loading = DiscoveryHomeMapper.loading()
        val empty = DiscoveryHomeMapper.empty()
        val error = DiscoveryHomeMapper.error()
        val offline = DiscoveryHomeMapper.offline()

        assertEquals(DiscoveryHomeDisplayState.Subscription, subscription.displayState)
        assertTrue(subscription.sourceTypes.first { it.label == "订阅" }.active)
        assertEquals("订阅源聚合", subscription.currentSource.title)
        assertEquals("订阅源聚合 · 未读", subscription.content.title)

        assertEquals(DiscoveryHomeDisplayState.Loading, loading.displayState)
        assertEquals("正在切换来源类型", loading.feedback.loading.title)
        assertEquals(base.currentSource, loading.currentSource)

        assertEquals(DiscoveryHomeDisplayState.Empty, empty.displayState)
        assertEquals("当前来源暂无内容", empty.feedback.empty.title)
        assertTrue(empty.content.featured.isEmpty())
        assertTrue(empty.ranking.items.isEmpty())

        assertEquals(DiscoveryHomeDisplayState.Error, error.displayState)
        assertEquals("来源加载失败", error.feedback.error.title)
        assertEquals("重试", error.feedback.error.primaryAction)

        assertEquals(DiscoveryHomeDisplayState.Offline, offline.displayState)
        assertEquals("网络不可用", offline.feedback.offline.title)
        assertEquals("查看缓存", offline.feedback.offline.secondaryAction)
    }

    @Test
    fun `discovery home stays scoped to discovery content without account surfaces`() {
        val state = DiscoveryHomeMapper.fromFixture()
        val visibleText = (
            listOf(state.topBar.title, state.search.placeholder, state.currentSource.title, state.currentSource.actionLabel, state.categoryMoreLabel, state.content.title, state.ranking.title) +
                state.sourceTypes.map { it.label } +
                state.categories.map { it.label } +
                state.content.featured.flatMap { book -> listOf(book.title, book.author, book.source, book.desc, book.actionLabel) } +
                listOf(state.statusBar.sourceCount, state.statusBar.availableCount, state.statusBar.updatedAt, state.statusBar.actionLabel) +
                state.ranking.items.flatMap { item -> listOf(item.title, item.author, item.source, item.state) } +
                state.bottomNavLabels
            ).joinToString(" ")

        listOf("发现", "搜索书名", "切换来源", "更多分类", "来源榜单更新", "检测").forEach { token ->
            assertTrue("Discovery fixture must expose $token", token in visibleText)
        }
        listOf("账号", "头像", "登录入口", "会员", "社区").forEach { forbidden ->
            assertFalse("Discovery fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
