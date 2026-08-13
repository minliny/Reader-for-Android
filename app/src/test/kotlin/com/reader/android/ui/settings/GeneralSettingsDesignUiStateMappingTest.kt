package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralSettingsDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes general settings shell content and controls`() {
        val state = GeneralSettingsMapper.fromFixture()

        assertEquals(GeneralSettingsDisplayState.Default, state.displayState)
        assertEquals("通用设置", state.topBar.title)
        assertEquals("返回", state.topBar.backLabel)
        assertEquals(listOf("基础偏好", "行为与反馈"), state.groups.map { it.title })

        val rows = state.groups.flatMap { it.rows }
        assertEquals(
            listOf("App主题", "语言", "启动时打开", "自动检查更新", "点击当前底栏回顶部", "减少动态效果", "崩溃日志", "动画效果"),
            rows.map { it.title }
        )
        assertEquals(GeneralSettingsRowType.Segment, rows.first { it.title == "App主题" }.type)
        assertEquals("跟随系统", rows.first { it.title == "App主题" }.value)
        assertEquals("主题跟随系统", rows.first { it.title == "App主题" }.meta)

        val language = state.languageRow
        assertEquals(GeneralSettingsRowType.Select, language.type)
        assertEquals("简体中文", language.value)
        assertEquals(listOf("简体中文", "繁體中文", "English"), language.options)

        val crashLog = rows.first { it.title == "崩溃日志" }
        assertEquals(GeneralSettingsRowType.Switch, crashLog.type)
        assertTrue(crashLog.enabled)
        assertEquals("已开启", crashLog.permission)

        assertEquals("恢复通用设置", state.restore.title)
        assertEquals("恢复默认", state.restore.intentLabel)
        assertEquals("确认恢复", state.restore.confirmLabel)
        assertEquals("保存成功", state.toast.success)
    }

    @Test
    fun `state variants keep setting context and feedback actions`() {
        val base = GeneralSettingsMapper.fromFixture()
        val optionSheet = GeneralSettingsMapper.optionSheet()
        val loading = GeneralSettingsMapper.loading()
        val error = GeneralSettingsMapper.error()
        val permission = GeneralSettingsMapper.permission()

        assertEquals(GeneralSettingsDisplayState.OptionSheet, optionSheet.displayState)
        assertEquals(base.languageRow, optionSheet.languageRow)

        assertEquals(GeneralSettingsDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.loading.title)
        assertEquals("正在读取通用设置，请稍候。", loading.loading.message)
        assertEquals(base.groups, loading.groups)

        assertEquals(GeneralSettingsDisplayState.Error, error.displayState)
        assertEquals("加载失败，请重试", error.error.title)
        assertEquals("重试", error.error.primaryAction)
        assertEquals("确认恢复", error.restore.confirmLabel)

        assertEquals(GeneralSettingsDisplayState.Permission, permission.displayState)
        assertEquals("需要系统权限", permission.permission.title)
        assertEquals("去设置", permission.permission.primaryAction)
        assertEquals("需要系统权限", permission.toast.permission)
    }

    @Test
    fun `general settings remains a settings shell input not an account page`() {
        val state = GeneralSettingsMapper.fromFixture()
        val visibleText = (
            listOf(state.topBar.title, state.topBar.backLabel, state.restore.title, state.restore.intentLabel, state.restore.copy) +
                state.groups.flatMap { group ->
                    listOf(group.title) + group.rows.flatMap { row ->
                        listOf(row.title, row.value, row.meta, row.permission.orEmpty()) + row.options
                    }
                }
            ).joinToString(" ")

        listOf("通用设置", "启动时打开", "语言", "主题跟随系统", "崩溃日志", "恢复默认", "保存成功").forEach { token ->
            assertTrue("General settings fixture must expose $token", token in visibleText || token == state.toast.success)
        }
        listOf("主导航", "头像", "登录", "账号", "会员", "社区", "退出登录").forEach { forbidden ->
            assertFalse("General settings fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
