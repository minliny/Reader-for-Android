package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MineLocalStateTest {

    @Test
    fun `fixture has valid defaults`() {
        val a = MineLocalStateAdapter.Fixture
        assertEquals("米色纸张", a.settings.themeName)
        assertEquals(WebDavConfigStatus.NOT_CONFIGURED, a.webDav.status)
    }

    @Test
    fun `update settings persists values`() {
        val a = MineLocalStateAdapter()
        a.updateSettings(MineSettingsLocal(themeName = "夜间模式"))
        assertEquals("夜间模式", a.settings.themeName)
    }

    @Test
    fun `webdav status transitions work`() {
        val a = MineLocalStateAdapter()
        assertEquals(WebDavConfigStatus.NOT_CONFIGURED, a.webDav.status)

        a.setWebDavStatus(WebDavConfigStatus.CONNECTING)
        assertEquals(WebDavConfigStatus.CONNECTING, a.webDav.status)

        a.setWebDavStatus(WebDavConfigStatus.AUTH_ERROR, "401 Unauthorized")
        assertEquals(WebDavConfigStatus.AUTH_ERROR, a.webDav.status)
        assertEquals("401 Unauthorized", a.webDav.errorMessage)
    }

    @Test
    fun `adapter shell returns fixture data`() {
        MineAdapterShell.resetToFakeMode()
        assertEquals("米色纸张", MineAdapterShell.settings().themeName)
    }

    @Test
    fun `no auth secrets in state models`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/settings/MineLocalState.kt"
        ).readText()
        listOf("password", "token", "api_key").forEach {
            assertFalse("Must not contain $it", it in source)
        }
    }
}
