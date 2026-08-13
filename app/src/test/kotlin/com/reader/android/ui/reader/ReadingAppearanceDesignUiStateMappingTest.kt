package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingAppearanceDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes required appearance controls`() {
        val state = ReadingAppearanceMapper.fromFixture()

        assertEquals(ReadingAppearanceDisplayState.Default, state.displayState)
        assertEquals("界面", state.panel.title)
        assertEquals("阅读外观", state.panel.subtitle)
        assertEquals("字号", state.fontSize.title)
        assertEquals(18, state.fontSize.value)
        assertEquals("行距", state.lineSpacing.title)
        assertEquals(listOf("紧凑", "标准", "舒展"), state.lineSpacing.options.map { it.label })
        assertEquals(listOf("纸张", "浅绿", "夜间", "暖黄"), state.themes.map { it.label })
        assertEquals("系统宋体", state.activeFont.label)
        assertEquals("仿真滑动", state.activeAnimation.label)
        assertEquals("appearance", state.moduleNav.single { it.active }.type)
    }

    @Test
    fun `font theme and edit states preserve reader context`() {
        val base = ReadingAppearanceMapper.fromFixture()
        val font = ReadingAppearanceMapper.font()
        val theme = ReadingAppearanceMapper.theme()
        val edit = ReadingAppearanceMapper.edit()

        assertEquals(ReadingAppearanceDisplayState.Font, font.displayState)
        assertEquals(base.reading, font.reading)
        assertEquals(3, font.fonts.size)
        assertEquals(ReadingAppearanceDisplayState.Theme, theme.displayState)
        assertEquals(base.reading, theme.reading)
        assertEquals(3, theme.animations.size)
        assertEquals(ReadingAppearanceDisplayState.Edit, edit.displayState)
        assertEquals("编辑主题", edit.editTheme.title)
        assertEquals("保存主题", edit.panel.saveLabel)
        assertTrue(edit.editTheme.previewCopy.contains("雨，下了一整夜"))
    }

    @Test
    fun `loading and error states replace only appearance panel content`() {
        val base = ReadingAppearanceMapper.fromFixture()
        val loading = ReadingAppearanceMapper.loading()
        val error = ReadingAppearanceMapper.error()

        assertEquals(ReadingAppearanceDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.loading.title)
        assertEquals(base.reading, loading.reading)
        assertEquals(ReadingAppearanceDisplayState.Error, error.displayState)
        assertEquals("加载失败，请重试", error.error.title)
        assertEquals("重试", error.error.primaryAction)
        assertTrue(error.error.message.contains("已保留当前阅读外观"))
        assertEquals(base.themes, error.themes)
        assertEquals(base.fonts, error.fonts)
    }
}
