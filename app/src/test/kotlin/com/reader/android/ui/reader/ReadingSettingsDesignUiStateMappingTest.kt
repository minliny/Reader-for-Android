package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSettingsDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes reader settings groups presets and advanced switches`() {
        val state = ReadingSettingsMapper.fromFixture()

        assertEquals(ReadingSettingsDisplayState.Default, state.displayState)
        assertEquals("阅读设置", state.topBar.title)
        assertEquals("预设", state.topBar.presetLabel)
        assertEquals("settings", state.moduleNav.single { it.active }.type)
        assertEquals(listOf("跟随系统方向", "覆盖翻页", "屏幕常亮关闭"), state.quickPresets.map { it.title })
        assertEquals(listOf("屏幕与显示", "翻页与手势", "阅读辅助", "进度与信息"), state.groups.map { it.title })
        assertEquals("高级设置", state.advancedTitle)
        assertEquals(listOf("自动加载后续章节", "章节失败自动重试", "缓存优先", "误触保护"), state.advanced.map { it.title })
        assertEquals("恢复默认阅读设置", state.restore.title)
        assertTrue("不会删除书籍或阅读进度" in state.restore.copy)

        val visibleText = (state.quickPresets.map { it.title + it.value } +
            state.groups.map { it.title + it.meta } +
            state.advanced.map { it.title + it.meta } +
            listOf(state.restore.title, state.restore.copy)).joinToString(" ")
        listOf("账号", "会员", "社区", "商业").forEach { forbidden ->
            assertFalse("Reader settings fixture must not expose $forbidden entry", forbidden in visibleText)
        }
    }

    @Test
    fun `subpage state preserves group context and mixed controls`() {
        val state = ReadingSettingsMapper.subpage()

        assertEquals(ReadingSettingsDisplayState.Subpage, state.displayState)
        assertEquals("屏幕与显示", state.currentTitle)
        assertEquals("方向 · 亮度 · 常亮 · 间距", state.subpage.subtitle)
        assertEquals(listOf("显示", "预设管理"), state.subpage.sections.map { it.title })

        val displayRows = state.subpage.sections.first { it.title == "显示" }.rows
        assertEquals(ReadingSettingsControlType.Segment, displayRows[0].controlType)
        assertEquals(listOf("跟随系统", "竖屏", "横屏"), displayRows[0].options)
        assertEquals("跟随系统", displayRows[0].activeOption)
        assertEquals(ReadingSettingsControlType.Switch, displayRows[1].controlType)
        assertFalse(displayRows[1].enabled)
        assertEquals(ReadingSettingsControlType.Stepper, displayRows[2].controlType)
        assertEquals(18, displayRows[2].value)

        val presetRows = state.subpage.sections.first { it.title == "预设管理" }.rows
        assertEquals(listOf("日间阅读", "夜间阅读"), presetRows.map { it.title })
        assertTrue(presetRows.first().active)
        assertFalse(presetRows.last().active)
    }

    @Test
    fun `loading and error states retain settings context`() {
        val base = ReadingSettingsMapper.fromFixture()
        val loading = ReadingSettingsMapper.loading()
        val error = ReadingSettingsMapper.error()

        assertEquals(ReadingSettingsDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.loading.title)
        assertEquals("正在读取阅读设置，请稍候。", loading.loading.message)
        assertEquals(base.groups, loading.groups)

        assertEquals(ReadingSettingsDisplayState.Error, error.displayState)
        assertEquals("操作失败，请重试", error.error.title)
        assertEquals("保存失败，已保留当前阅读设置。", error.error.message)
        assertEquals("重试", error.error.primaryAction)
        assertEquals(base.quickPresets, error.quickPresets)
        assertEquals(base.advanced, error.advanced)
    }
}
