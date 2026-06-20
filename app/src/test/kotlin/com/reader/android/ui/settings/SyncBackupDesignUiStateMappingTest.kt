package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBackupDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes local backup webdav sync status and backup records`() {
        val state = SyncBackupMapper.fromFixture()

        assertEquals(SyncBackupDisplayState.Default, state.displayState)
        assertEquals("同步与备份", state.topBar.title)
        assertEquals("返回", state.topBar.backLabel)
        assertEquals(listOf("本地备份", "WebDAV", "同步状态"), state.sections.map { it.title })

        assertEquals(
            listOf("立即备份", "自动备份", "备份位置", "导出数据", "恢复备份"),
            state.localBackupRows.map { it.title }
        )
        assertTrue(state.localBackupRows.first { it.title == "自动备份" }.enabled)
        assertEquals("内部存储", state.localBackupRows.first { it.title == "备份位置" }.value)

        assertEquals(
            listOf("WebDAV 未配置", "自动同步阅读进度", "同步冲突处理"),
            state.webDavRows.map { it.title }
        )
        assertEquals("去配置", state.webDavRows.first { it.title == "WebDAV 未配置" }.actionLabel)
        assertEquals("询问我", state.webDavRows.first { it.title == "同步冲突处理" }.value)

        assertEquals(
            listOf("上次同步：尚未同步", "同步失败"),
            state.syncStatusRows.map { it.title }
        )
        assertEquals("待配置", state.syncStatusRows.first { it.title == "同步失败" }.status)
        assertEquals(listOf("可恢复", "已校验"), state.records.map { it.status })

        assertEquals("恢复备份？", state.confirm.title)
        assertEquals("确认恢复", state.confirm.confirmLabel)
        assertTrue("Restore copy must state overwrite scope", "覆盖当前书架、阅读进度和设置" in state.confirm.copy)
        assertEquals("保存成功", state.toast.success)
    }

    @Test
    fun `state variants keep page skeleton and expected recovery actions`() {
        val base = SyncBackupMapper.fromFixture()
        val confirm = SyncBackupMapper.confirm()
        val loading = SyncBackupMapper.loading()
        val empty = SyncBackupMapper.empty()
        val error = SyncBackupMapper.error()
        val offline = SyncBackupMapper.offline()
        val permission = SyncBackupMapper.permission()

        assertEquals(SyncBackupDisplayState.Confirm, confirm.displayState)
        assertEquals(base.sections, confirm.sections)
        assertEquals("确认恢复", confirm.confirm.confirmLabel)

        assertEquals(SyncBackupDisplayState.Loading, loading.displayState)
        assertEquals("备份读取态", loading.loading.title)
        assertEquals(base.sections, loading.sections)

        assertEquals(SyncBackupDisplayState.Empty, empty.displayState)
        assertEquals("暂无备份记录", empty.empty.title)
        assertEquals("立即备份", empty.empty.primaryAction)
        assertTrue(empty.records.isEmpty())

        assertEquals(SyncBackupDisplayState.Error, error.displayState)
        assertEquals("同步失败态", error.error.title)
        assertEquals("重试", error.error.primaryAction)

        assertEquals(SyncBackupDisplayState.Offline, offline.displayState)
        assertEquals("离线同步态", offline.offline.title)
        assertEquals("使用本地备份", offline.offline.primaryAction)
        assertEquals(base.records, offline.records)

        assertEquals(SyncBackupDisplayState.Permission, permission.displayState)
        assertEquals("文件权限态", permission.permission.title)
        assertEquals("去授权", permission.permission.primaryAction)
        assertEquals("需要文件访问权限", permission.toast.permission)
    }

    @Test
    fun `sync backup stays scoped to local backup webdav and restore safety`() {
        val state = SyncBackupMapper.fromFixture()
        val visibleText = (
            listOf(state.topBar.title, state.confirm.title, state.confirm.copy, state.empty.title, state.empty.copy) +
                state.sections.flatMap { section ->
                    listOf(section.title) + section.rows.flatMap { row ->
                        listOf(row.title, row.meta, row.value, row.actionLabel, row.status)
                    }
                } +
                state.records.flatMap { record -> listOf(record.title, record.meta, record.status) }
            ).joinToString(" ")

        listOf("备份位置", "立即备份", "恢复备份", "自动备份", "备份记录", "确认恢复").forEach { token ->
            assertTrue("Sync backup fixture must expose $token", token in visibleText || token == state.confirm.confirmLabel)
        }
        assertTrue("Offline state must preserve local backup records", SyncBackupMapper.offline().records.isNotEmpty())
        listOf("账号", "会员", "社区", "推荐流", "广告", "登录入口").forEach { forbidden ->
            assertFalse("Sync backup fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
