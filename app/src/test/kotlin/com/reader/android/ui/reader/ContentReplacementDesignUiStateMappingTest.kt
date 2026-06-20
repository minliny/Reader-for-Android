package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentReplacementDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes replacement rules preview and display only copy`() {
        val state = ContentReplacementMapper.fromFixture()

        assertEquals(ContentReplacementDisplayState.Default, state.displayState)
        assertEquals("内容替换", state.topControl.title)
        assertEquals("长夜余火", state.topControl.bookTitle)
        assertEquals("设置", state.topControl.settingsLabel)
        assertEquals("当前书规则", state.panel.title)
        assertEquals("全部规则", state.panel.allRulesLabel)
        assertEquals("启用内容替换", state.panel.enableTitle)
        assertEquals("仅影响当前阅读显示，不修改原文", state.panel.enableCopy)
        assertTrue(state.enabled)
        assertEquals(listOf("屏蔽广告段落", "统一标点空格", "替换错别字"), state.rules.map { it.title })
        assertEquals(listOf(true, true, false), state.rules.map { it.enabled })
        assertEquals("替换预览", state.preview.title)
        assertEquals("原文", state.preview.beforeLabel)
        assertEquals("水面反着光", state.preview.beforeText)
        assertEquals("显示", state.preview.afterLabel)
        assertEquals("水面映着光", state.preview.afterText)
    }

    @Test
    fun `edit state keeps pattern fields and save actions`() {
        val state = ContentReplacementMapper.edit()

        assertEquals(ContentReplacementDisplayState.Edit, state.displayState)
        assertEquals("替换前", state.form.beforeLabel)
        assertEquals("反着光", state.form.beforeValue)
        assertEquals("替换后", state.form.afterLabel)
        assertEquals("映着光", state.form.afterValue)
        assertEquals("测试", state.form.testLabel)
        assertEquals("保存", state.form.saveLabel)
        assertEquals("替换预览", state.preview.title)
    }

    @Test
    fun `empty loading and error states preserve current book context`() {
        val base = ContentReplacementMapper.fromFixture()
        val empty = ContentReplacementMapper.empty()
        val loading = ContentReplacementMapper.loading()
        val error = ContentReplacementMapper.error()

        assertEquals(ContentReplacementDisplayState.Empty, empty.displayState)
        assertEquals("暂无替换规则", empty.empty.title)
        assertEquals("新增规则", empty.empty.primaryAction)
        assertTrue(empty.rules.isEmpty())
        assertEquals(base.reading, empty.reading)

        assertEquals(ContentReplacementDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.loading.title)
        assertEquals("正在读取当前书替换规则，请稍候。", loading.loading.message)
        assertEquals(base.topControl, loading.topControl)

        assertEquals(ContentReplacementDisplayState.Error, error.displayState)
        assertEquals("操作失败，请重试", error.error.title)
        assertTrue("规则格式错误" in error.error.message)
        assertTrue("已保留替换前和替换后内容" in error.error.message)
        assertEquals(base.form, error.form)
        assertEquals(base.preview, error.preview)
    }

    @Test
    fun `content replacement does not expose source rule or permanent mutation semantics`() {
        val state = ContentReplacementMapper.fromFixture()
        val visibleText = (
            listOf(
                state.topControl.title,
                state.panel.enableCopy,
                state.empty.message,
                state.preview.beforeText,
                state.preview.afterText,
                state.form.beforeValue,
                state.form.afterValue
            ) + state.rules.flatMap { listOf(it.title, it.meta) }
            ).joinToString(" ")

        listOf("书源规则", "换源", "上传正文", "永久修改", "原始章节已修改", "会员入口", "广告推荐", "社区").forEach { forbidden ->
            assertFalse("Content replacement fixture must not expose $forbidden semantics", forbidden in visibleText)
        }
        assertTrue("Replacement copy must explicitly stay display-only", "不修改原文" in state.panel.enableCopy)
    }
}
