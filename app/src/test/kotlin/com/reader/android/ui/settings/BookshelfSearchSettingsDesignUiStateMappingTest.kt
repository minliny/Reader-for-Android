package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfSearchSettingsDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes bookshelf search settings groups preview and danger action`() {
        val state = BookshelfSearchSettingsMapper.fromFixture()

        assertEquals(BookshelfSearchSettingsDisplayState.Default, state.displayState)
        assertEquals("书架与搜索", state.topBar.title)
        assertEquals("返回", state.topBar.backLabel)
        assertEquals("书架", state.bookshelf.title)
        assertEquals("搜索", state.search.title)
        assertEquals(
            listOf("默认展示", "封面列数", "默认分组", "显示更新标记"),
            state.bookshelf.rows.map { it.title }
        )
        assertEquals(
            listOf("搜索范围", "结果排序", "合并同名同作者", "搜索历史", "搜索历史数量"),
            state.search.rows.map { it.title }
        )

        val displayMode = state.bookshelf.rows.first { it.title == "默认展示" }
        assertEquals(BookshelfSearchSettingsRowType.Segment, displayMode.type)
        assertEquals("封面", displayMode.value)
        assertEquals(listOf("封面", "列表"), displayMode.options)

        val columns = state.bookshelf.rows.first { it.title == "封面列数" }
        assertEquals(BookshelfSearchSettingsRowType.Stepper, columns.type)
        assertEquals("3列", columns.value)
        assertEquals("-", columns.minLabel)
        assertEquals("+", columns.maxLabel)

        assertEquals("封面模式预览", state.preview.coverTitle)
        assertEquals("列表模式预览", state.preview.listTitle)
        assertEquals(listOf("长夜余火", "大国科技", "星海征途"), state.preview.books.map { it.title })
        assertTrue("Preview must retain source asset path for frontend input", "bookshelf-cover-assets" in state.preview.books.first().cover)

        assertEquals("搜索范围", state.searchRangeRow.title)
        assertEquals("全局", state.searchRangeRow.value)
        assertEquals(listOf("当前分组", "书架", "全局"), state.searchRangeRow.options)

        assertEquals("清空搜索历史", state.danger.title)
        assertEquals("清空搜索历史？", state.danger.confirmTitle)
        assertEquals("确认清空", state.danger.confirmLabel)
        assertEquals("保存成功", state.toast.success)
    }

    @Test
    fun `state variants keep setting context and required actions`() {
        val base = BookshelfSearchSettingsMapper.fromFixture()
        val optionSheet = BookshelfSearchSettingsMapper.optionSheet()
        val confirm = BookshelfSearchSettingsMapper.confirm()
        val loading = BookshelfSearchSettingsMapper.loading()
        val error = BookshelfSearchSettingsMapper.error()
        val permission = BookshelfSearchSettingsMapper.permission()

        assertEquals(BookshelfSearchSettingsDisplayState.OptionSheet, optionSheet.displayState)
        assertEquals(base.searchRangeRow, optionSheet.searchRangeRow)

        assertEquals(BookshelfSearchSettingsDisplayState.Confirm, confirm.displayState)
        assertEquals("确认清空", confirm.danger.confirmLabel)
        assertEquals(base.search.rows, confirm.search.rows)

        assertEquals(BookshelfSearchSettingsDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.loading.title)
        assertEquals("正在读取书架与搜索设置，请稍候。", loading.loading.message)
        assertEquals(base.bookshelf.rows, loading.bookshelf.rows)

        assertEquals(BookshelfSearchSettingsDisplayState.Error, error.displayState)
        assertEquals("加载失败，请重试", error.error.title)
        assertEquals("重试", error.error.primaryAction)
        assertEquals("操作失败，请重试", error.toast.error)

        assertEquals(BookshelfSearchSettingsDisplayState.Permission, permission.displayState)
        assertEquals("需要权限", permission.permission.title)
        assertEquals("去设置", permission.permission.primaryAction)
        assertEquals("需要存储权限", permission.toast.permission)
    }

    @Test
    fun `bookshelf search settings stays scoped to settings shell`() {
        val state = BookshelfSearchSettingsMapper.fromFixture()
        val visibleText = (
            listOf(
                state.topBar.title,
                state.bookshelf.title,
                state.search.title,
                state.preview.coverTitle,
                state.preview.listTitle,
                state.danger.title,
                state.danger.copy
            ) +
                state.bookshelf.rows.flatMap { listOf(it.title, it.value, it.meta) + it.options } +
                state.search.rows.flatMap { listOf(it.title, it.value, it.meta) + it.options } +
                state.preview.books.flatMap { listOf(it.title, it.meta, it.update, it.badge) }
            ).joinToString(" ")

        listOf("书架与搜索", "默认展示", "搜索范围", "结果排序", "搜索历史", "清空搜索历史").forEach { token ->
            assertTrue("Bookshelf search settings fixture must expose $token", token in visibleText)
        }
        listOf("主导航", "头像", "登录", "账号", "会员", "社区", "推荐流", "广告").forEach { forbidden ->
            assertFalse("Bookshelf search settings fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
