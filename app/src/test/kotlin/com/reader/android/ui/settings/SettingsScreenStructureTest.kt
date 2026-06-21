package com.reader.android.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SettingsScreenStructureTest {

    private val screenSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/settings/SettingsScreen.kt")
            )
        )
    }

    private val rootSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/settings/MineScreen.kt")
            )
        )
    }

    private val secondarySettingsSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/settings/WebDavAndBackupScreens.kt")
            )
        )
    }

    @Test
    fun `settings screen uses reader theme and components`() {
        listOf(
            "ReaderTheme",
            "ReaderAppTopBar",
            "ReaderSettingsGroup",
            "ReaderSettingsSwitchRow"
        ).forEach { token ->
            assertTrue("SettingsScreen must use $token", token in screenSource)
        }
    }

    @Test
    fun `settings screen preserves themepreferences data binding`() {
        listOf(
            "ThemePreferences",
            "darkMode",
            "fontSize",
            "lineSpacing",
            "pageMargin",
            "collectAsState"
        ).forEach { token ->
            assertTrue("SettingsScreen must preserve $token", token in screenSource)
        }
    }

    @Test
    fun `settings screen exposes reader settings section`() {
        listOf("阅读设置", "夜间模式", "字号", "行间距", "页边距").forEach { text ->
            assertTrue("SettingsScreen must display '$text'", text in screenSource)
        }
    }

    @Test
    fun `settings root mirrors main tab input structure`() {
        listOf(
            "SettingsRootScreen",
            "SettingsHomeState",
            "SettingsHomeMapper",
            "SettingsHomeDisplayState",
            "SettingsHomeTone",
            "LoadingOverview",
            "NoBackup",
            "PermissionNeeded",
            "本地概览",
            "本地概览加载中",
            "本地书籍",
            "订阅源",
            "书源可用",
            "最近备份",
            "还没有备份记录",
            "需要存储权限",
            "常用管理",
            "书源管理",
            "RSS/订阅管理",
            "阅读偏好",
            "缓存管理",
            "全部设置",
            "通用",
            "书架与搜索",
            "书源与订阅",
            "同步与备份",
            "隐私与权限",
            "关于与反馈"
        ).forEach { token ->
            assertTrue("SettingsRootScreen must preserve $token", token in rootSource)
        }
    }

    @Test
    fun `settings root remains a frontend input not an account page`() {
        listOf(
            "ReaderAppTopBar",
            "ReaderCard",
            "ReaderSettingsGroup",
            "ReaderSettingsRow",
            "ReaderChip",
            "SettingsHomeIcon",
            "ReaderIconToken.Bookshelf",
            "ReaderIconToken.Rss",
            "ReaderIconToken.SourceSwitch",
            "ReaderIconToken.Shield",
            "ReaderLoadingState",
            "ReaderPermissionRequiredState",
            "onSearchClick",
            "onMoreClick",
            "onRssManagementClick"
        ).forEach { token ->
            assertTrue("SettingsRootScreen must preserve input contract token $token", token in rootSource)
        }

        listOf("头像", "登录", "昵称", "账号", "退出登录").forEach { forbidden ->
            assertTrue("SettingsRootScreen must not become an account page with $forbidden", forbidden !in rootSource)
        }
    }

    @Test
    fun `settings secondary screens use icon tokens`() {
        assertTrue("Settings secondary screens must use more icon token", "ReaderIconToken.More" in secondarySettingsSource)
        assertTrue(
            "Settings secondary screens must not import Material Icons directly",
            "import androidx.compose.material.icons" !in secondarySettingsSource
        )
    }

    @Test
    fun `settings screen configures slider colors with reader theme`() {
        listOf("SliderDefaults.colors", "ReaderTheme.colors.primary", "ReaderTheme.colors.mutedTrack").forEach { token ->
            assertTrue("SettingsScreen sliders must use $token", token in screenSource)
        }
    }

    @Test
    fun `settings screen does not reintroduce stitch old tokens`() {
        listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec", "#" + "eae1da", "#" + "f5ece6", "#" + "efe7e0", "#" + "8b5000",
            "MaterialTheme",
            "Scaffold",
            "TopAppBar",
            "ExperimentalMaterial3Api",
            "HorizontalDivider",
            "Web" + "View",
            "normalized-" + "html"
        ).forEach { forbidden ->
            assertTrue(
                "SettingsScreen must not reintroduce $forbidden",
                forbidden !in screenSource
            )
        }
    }
}
