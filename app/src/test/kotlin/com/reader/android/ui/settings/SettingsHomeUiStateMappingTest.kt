package com.reader.android.ui.settings

import com.reader.android.ui.components.ReaderIconToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomeUiStateMappingTest {

    @Test
    fun `settings home mapper exposes main tab state matrix`() {
        val states = listOf(
            SettingsHomeMapper.fromFixture(),
            SettingsHomeMapper.loadingOverview(),
            SettingsHomeMapper.noBackup(),
            SettingsHomeMapper.permissionNeeded()
        )

        assertEquals(
            listOf(
                SettingsHomeDisplayState.Default,
                SettingsHomeDisplayState.LoadingOverview,
                SettingsHomeDisplayState.NoBackup,
                SettingsHomeDisplayState.PermissionNeeded
            ),
            states.map { it.displayState }
        )
        states.forEach { state ->
            assertEquals(listOf("书架", "发现", "RSS", "设置"), state.bottomNavLabels)
            assertEquals("本地概览", state.overviewTitle)
            assertEquals(4, state.quickEntries.size)
            assertTrue(state.sections.single().rows.size >= 7)
        }
    }

    @Test
    fun `settings home default state maps fixture icons and tones`() {
        val state = SettingsHomeMapper.fromFixture()

        assertEquals(
            listOf("本地书籍", "订阅源", "书源可用", "最近备份"),
            state.overviewItems.map { it.label }
        )
        assertEquals(ReaderIconToken.Bookshelf, state.overviewItems.first().iconToken)
        assertEquals(SettingsHomeTone.Orange, state.overviewItems.single { it.label == "订阅源" }.tone)
        assertEquals(ReaderIconToken.Rss, state.quickEntries.single { it.title == "RSS/订阅管理" }.iconToken)
        assertEquals(ReaderIconToken.Shield, state.sections.single().rows.single { it.title == "隐私与权限" }.iconToken)
    }

    @Test
    fun `settings home no backup and permission states keep acceptance copy`() {
        val noBackup = SettingsHomeMapper.noBackup()
        val permissionNeeded = SettingsHomeMapper.permissionNeeded()

        assertEquals("未设置", noBackup.overviewItems.single { it.label == "最近备份" }.value)
        assertEquals(SettingsHomeDisplayState.PermissionNeeded, permissionNeeded.displayState)
        assertEquals("待授权", permissionNeeded.sections.single().rows.single { it.title == "隐私与权限" }.state)
    }
}
