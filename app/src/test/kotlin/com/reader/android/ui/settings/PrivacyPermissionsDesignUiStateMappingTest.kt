package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPermissionsDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes permissions switches links and danger action`() {
        val state = PrivacyPermissionsMapper.fromFixture()

        assertEquals(PrivacyPermissionsDisplayState.Default, state.displayState)
        assertEquals("隐私与权限", state.topBar.title)
        assertEquals("返回", state.topBar.backLabel)
        assertEquals(listOf("系统权限", "隐私设置", "数据与说明"), state.sections.map { it.title })

        val permissionRows = state.sections.first { it.title == "系统权限" }.rows
        assertEquals(listOf("文件访问", "通知权限", "电池优化"), permissionRows.map { it.title })
        assertEquals("已授权", permissionRows.first { it.title == "文件访问" }.status)
        assertEquals("good", permissionRows.first { it.title == "文件访问" }.statusTone)
        assertEquals("未授权", permissionRows.first { it.title == "通知权限" }.status)
        assertEquals("warn", permissionRows.first { it.title == "通知权限" }.statusTone)
        assertEquals("去设置", permissionRows.first { it.title == "通知权限" }.actionLabel)
        assertEquals("受系统管理", permissionRows.first { it.title == "电池优化" }.status)
        assertEquals("system", permissionRows.first { it.title == "电池优化" }.statusTone)

        val privacyRows = state.sections.first { it.title == "隐私设置" }.rows
        assertEquals(listOf("隐私开关", "保存搜索历史", "发送崩溃日志"), privacyRows.map { it.title })
        assertFalse(privacyRows.first { it.title == "隐私开关" }.enabled)
        assertTrue(privacyRows.first { it.title == "保存搜索历史" }.enabled)

        val dataRows = state.sections.first { it.title == "数据与说明" }.rows
        assertEquals(listOf("网络访问说明", "本地数据说明", "隐私说明"), dataRows.map { it.title })

        assertEquals("清除隐私数据", state.danger.title)
        assertEquals("清除隐私数据？", state.danger.confirmTitle)
        assertEquals("确认清除", state.danger.confirmLabel)
        assertEquals("保存成功", state.toast.success)
    }

    @Test
    fun `state variants preserve page context and required permission action`() {
        val base = PrivacyPermissionsMapper.fromFixture()
        val confirm = PrivacyPermissionsMapper.confirm()
        val loading = PrivacyPermissionsMapper.loading()
        val error = PrivacyPermissionsMapper.error()
        val permission = PrivacyPermissionsMapper.permission()

        assertEquals(PrivacyPermissionsDisplayState.Confirm, confirm.displayState)
        assertEquals("确认清除", confirm.danger.confirmLabel)
        assertEquals(base.sections, confirm.sections)

        assertEquals(PrivacyPermissionsDisplayState.Loading, loading.displayState)
        assertEquals("权限加载态", loading.loading.title)
        assertTrue("Loading must explain structure preservation", "保留页面结构" in loading.loading.message)

        assertEquals(PrivacyPermissionsDisplayState.Error, error.displayState)
        assertEquals("权限读取失败态", error.error.title)
        assertEquals("重试", error.error.primaryAction)
        assertEquals("操作失败，请重试", error.toast.error)

        assertEquals(PrivacyPermissionsDisplayState.Permission, permission.displayState)
        assertEquals("系统权限态", permission.permission.title)
        assertEquals("去设置", permission.permission.primaryAction)
        assertEquals("需要系统权限", permission.toast.permission)
    }

    @Test
    fun `privacy permissions remains scoped to permissions and privacy settings`() {
        val state = PrivacyPermissionsMapper.fromFixture()
        val visibleText = (
            listOf(state.topBar.title, state.danger.title, state.danger.meta, state.danger.copy) +
                state.sections.flatMap { section ->
                    listOf(section.title) + section.rows.flatMap { row ->
                        listOf(row.title, row.meta, row.value, row.status, row.actionLabel)
                    }
                }
            ).joinToString(" ")

        listOf("文件访问", "通知权限", "网络访问说明", "去设置", "隐私开关", "已授权", "未授权", "受系统管理").forEach { token ->
            assertTrue("Privacy permissions fixture must expose $token", token in visibleText)
        }
        listOf("主导航", "头像", "登录", "账号", "会员", "社区", "推荐流", "广告").forEach { forbidden ->
            assertFalse("Privacy permissions fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
