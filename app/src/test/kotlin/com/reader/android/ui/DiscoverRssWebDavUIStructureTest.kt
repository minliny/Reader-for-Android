package com.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class DiscoverRssWebDavUIStructureTest {

    private fun sourceOf(path: String): String =
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/$path")))

    @Test
    fun `discover screen uses reader components`() {
        val s = sourceOf("discover/DiscoverScreen.kt")
        listOf("ReaderTheme", "ReaderAppTopBar", "ReaderSectionHeader", "BookCard", "ReaderSettingsRow").forEach {
            assertTrue("DiscoverScreen must use $it", it in s)
        }
    }

    @Test
    fun `discover screen has rss subscription link`() {
        val s = sourceOf("discover/DiscoverScreen.kt")
        assertTrue("Must have RSS link", "RSS 订阅" in s)
        assertTrue("Must have onRssClick", "onRssClick" in s)
    }

    @Test
    fun `rss screens expose three composables`() {
        val s = sourceOf("discover/RssScreens.kt")
        listOf("fun RssListScreen", "fun RssDetailScreen", "fun RssSubscriptionManagementScreen").forEach {
            assertTrue("Must expose $it", it in s)
        }
    }

    @Test
    fun `rss screens use reader components`() {
        val s = sourceOf("discover/RssScreens.kt")
        listOf("ReaderTheme", "ReaderAppTopBar", "ReaderListItem", "ReaderCard", "ReaderSettingsSwitchRow").forEach {
            assertTrue("Rss screens must use $it", it in s)
        }
    }

    @Test
    fun `webdav config has form fields and login`() {
        val s = sourceOf("settings/WebDavAndBackupScreens.kt")
        listOf("服务器地址", "用户名", "密码", "登录并测试", "WebDAV 配置").forEach {
            assertTrue("WebDavConfig must have $it", it in s)
        }
    }

    @Test
    fun `backup settings has switch and webdav link`() {
        val s = sourceOf("settings/WebDavAndBackupScreens.kt")
        listOf("备份设置", "自动备份", "备份到 WebDAV", "onAutoBackupToggle", "onWebDavBackupClick").forEach {
            assertTrue("BackupSettings must have $it", it in s)
        }
    }

    @Test
    fun `slice 6 screens do not reintroduce stitch old patterns`() {
        listOf(
            "discover/DiscoverScreen.kt",
            "discover/RssScreens.kt",
            "settings/WebDavAndBackupScreens.kt"
        ).forEach { file ->
            val s = sourceOf(file)
            listOf(
                "bg-surface-container", "text-on-surface", "shadow-lg", "shadow-md",
                "#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000",
                "MaterialTheme.colorScheme", "Scaffold(", "TopAppBar(", "CardDefaults",
                "ExperimentalMaterial3Api", "WebView", "normalized-html"
            ).forEach { forbidden ->
                assertTrue("$file must not have $forbidden", forbidden !in s)
            }
        }
    }
}
