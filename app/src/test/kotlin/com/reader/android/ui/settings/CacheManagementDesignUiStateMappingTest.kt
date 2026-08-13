package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheManagementDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes storage overview categories strategy and danger action`() {
        val state = CacheManagementMapper.fromFixture()

        assertEquals(CacheManagementDisplayState.Default, state.displayState)
        assertEquals("缓存管理", state.topBar.title)
        assertEquals("返回", state.topBar.backLabel)
        assertEquals("缓存占用", state.storage.title)
        assertEquals("1.28 GB", state.storage.value)
        assertEquals("62%", state.storage.percentLabel)
        assertEquals(0.62f, state.storage.percent)
        assertTrue("Storage copy must mention calculation", "正在计算" in state.storage.copy)

        assertEquals(listOf("缓存分类", "缓存策略"), state.sections.map { it.title })
        assertEquals(
            listOf("总缓存", "书籍缓存", "封面缓存", "搜索缓存", "RSS 缓存"),
            state.sections.first { it.title == "缓存分类" }.rows.map { it.title }
        )
        assertEquals(
            listOf("优先读取缓存", "自动缓存后续章节", "缓存范围", "下载与缓存位置"),
            state.sections.first { it.title == "缓存策略" }.rows.map { it.title }
        )

        val strategy = state.sections.first { it.title == "缓存策略" }.rows
        assertTrue(strategy.first { it.title == "优先读取缓存" }.enabled)
        assertTrue(strategy.first { it.title == "自动缓存后续章节" }.enabled)
        assertEquals("5 章", strategy.first { it.title == "缓存范围" }.value)
        assertEquals("内部存储", strategy.first { it.title == "下载与缓存位置" }.value)

        assertEquals("清理缓存", state.danger.title)
        assertEquals("确认清理缓存？", state.danger.confirmTitle)
        assertEquals("确认清理", state.danger.confirmLabel)
        assertTrue("Danger copy must state bookshelf records are preserved", "不会删除书架记录" in state.danger.copy)
        assertEquals("已清理", state.toast.success)
    }

    @Test
    fun `state variants retain cache context and recovery actions`() {
        val base = CacheManagementMapper.fromFixture()
        val loading = CacheManagementMapper.loading()
        val empty = CacheManagementMapper.empty()
        val confirm = CacheManagementMapper.confirm()
        val error = CacheManagementMapper.error()

        assertEquals(CacheManagementDisplayState.Loading, loading.displayState)
        assertEquals("正在计算", loading.loading.title)
        assertEquals(base.storage, loading.storage)

        assertEquals(CacheManagementDisplayState.Empty, empty.displayState)
        assertEquals("暂无缓存", empty.empty.title)
        assertEquals("重新计算", empty.empty.primaryAction)

        assertEquals(CacheManagementDisplayState.Confirm, confirm.displayState)
        assertEquals("确认清理", confirm.danger.confirmLabel)
        assertEquals(base.sections, confirm.sections)

        assertEquals(CacheManagementDisplayState.Error, error.displayState)
        assertEquals("清理失败态", error.error.title)
        assertEquals("重试", error.error.primaryAction)
        assertEquals("操作失败，请重试", error.toast.error)
    }

    @Test
    fun `cache management stays scoped to cache cleanup not bookshelf deletion`() {
        val state = CacheManagementMapper.fromFixture()
        val visibleText = (
            listOf(state.topBar.title, state.storage.title, state.storage.value, state.storage.copy, state.danger.title, state.danger.copy) +
                state.sections.flatMap { section ->
                    listOf(section.title) + section.rows.flatMap { row ->
                        listOf(row.title, row.meta, row.value)
                    }
                }
            ).joinToString(" ")

        listOf("缓存占用", "正在计算", "清理缓存", "缓存分类", "确认清理").forEach { token ->
            assertTrue("Cache management fixture must expose $token", token in visibleText || token == state.danger.confirmLabel)
        }
        assertTrue("Cache cleanup must explicitly preserve bookshelf records", "不会删除书架记录" in visibleText)
        listOf("将删除书架记录", "删除全部书籍", "删除阅读进度", "账号", "会员", "社区", "推荐流").forEach { forbidden ->
            assertFalse("Cache management fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
