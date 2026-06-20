package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutFeedbackDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes version update feedback license and privacy links`() {
        val state = AboutFeedbackMapper.fromFixture()

        assertEquals(AboutFeedbackDisplayState.Default, state.displayState)
        assertEquals("关于与反馈", state.topBar.title)
        assertEquals("返回", state.topBar.backLabel)
        assertEquals("当前版本", state.versionMetric.label)
        assertEquals("Reader 1.0.0", state.versionMetric.value)
        assertEquals("更新状态", state.updateMetric.label)
        assertEquals("已是最新版本", state.updateMetric.value)

        assertEquals(listOf("应用信息", "帮助与反馈", "法律与隐私"), state.sections.map { it.title })
        assertEquals(
            listOf("检查更新", "更新日志", "开源许可"),
            state.sections.first { it.title == "应用信息" }.rows.map { it.title }
        )
        assertEquals(
            listOf("使用帮助", "问题反馈", "导出诊断日志"),
            state.sections.first { it.title == "帮助与反馈" }.rows.map { it.title }
        )
        assertEquals(
            listOf("隐私协议", "用户协议"),
            state.sections.first { it.title == "法律与隐私" }.rows.map { it.title }
        )

        assertEquals("导出诊断日志？", state.confirm.title)
        assertEquals("确认导出", state.confirm.confirmLabel)
        assertEquals("已是最新版本", state.toast.success)
        assertEquals("网络不可用，请稍后重试", state.toast.offline)
        assertEquals("本应用不提供账户与登录功能", state.footer)
    }

    @Test
    fun `state variants keep about context and recovery actions`() {
        val base = AboutFeedbackMapper.fromFixture()
        val loading = AboutFeedbackMapper.loading()
        val error = AboutFeedbackMapper.error()
        val confirm = AboutFeedbackMapper.confirm()
        val offline = AboutFeedbackMapper.offline()

        assertEquals(AboutFeedbackDisplayState.Loading, loading.displayState)
        assertEquals("检查更新中", loading.loading.title)
        assertEquals(base.versionMetric, loading.versionMetric)

        assertEquals(AboutFeedbackDisplayState.Error, error.displayState)
        assertEquals("检查更新失败态", error.error.title)
        assertEquals("重试", error.error.primaryAction)

        assertEquals(AboutFeedbackDisplayState.Confirm, confirm.displayState)
        assertEquals("确认导出", confirm.confirm.confirmLabel)
        assertEquals(base.sections, confirm.sections)

        assertEquals(AboutFeedbackDisplayState.Offline, offline.displayState)
        assertEquals("离线反馈态", offline.offline.title)
        assertEquals("重试", offline.offline.primaryAction)
        assertEquals("网络不可用，请稍后重试", offline.toast.offline)
    }

    @Test
    fun `about feedback stays scoped to product information feedback and legal links`() {
        val state = AboutFeedbackMapper.fromFixture()
        val visibleText = (
            listOf(state.topBar.title, state.footer, state.confirm.title, state.confirm.copy) +
                state.metrics.flatMap { metric -> listOf(metric.label, metric.value) } +
                state.sections.flatMap { section ->
                    listOf(section.title) + section.rows.flatMap { row ->
                        listOf(row.title, row.meta, row.value)
                    }
                }
            ).joinToString(" ")

        listOf("当前版本", "检查更新", "问题反馈", "开源许可", "隐私协议", "确认导出").forEach { token ->
            assertTrue("About feedback fixture must expose $token", token in visibleText || token == state.confirm.confirmLabel)
        }
        assertTrue("Footer must explicitly state account and login are not provided", "本应用不提供账户与登录功能" in visibleText)
        listOf("登录入口", "会员", "社区", "推荐流", "广告").forEach { forbidden ->
            assertFalse("About feedback fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
