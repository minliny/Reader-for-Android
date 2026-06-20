package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SettingsSecondaryStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/SettingsSecondaryStateMatrixPreviews.kt")))
    }

    private val generalSettingsScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/GeneralSettingsDesignScreen.kt")))
    }

    private val generalSettingsStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/GeneralSettingsDesignUiState.kt")))
    }

    private val bookshelfSearchSettingsScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/BookshelfSearchSettingsDesignScreen.kt")))
    }

    private val bookshelfSearchSettingsStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/BookshelfSearchSettingsDesignUiState.kt")))
    }

    private val privacyPermissionsScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/PrivacyPermissionsDesignScreen.kt")))
    }

    private val privacyPermissionsStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/PrivacyPermissionsDesignUiState.kt")))
    }

    @Test
    fun `settings secondary compose previews expose general webdav backup sync and remote book states`() {
        listOf(
            "SettingsGeneralDefaultPreview",
            "SettingsGeneralOptionSheetPreview",
            "SettingsGeneralLoadingPreview",
            "SettingsGeneralErrorPreview",
            "SettingsGeneralPermissionPreview",
            "SettingsBookshelfSearchDefaultPreview",
            "SettingsBookshelfSearchOptionSheetPreview",
            "SettingsBookshelfSearchConfirmPreview",
            "SettingsBookshelfSearchLoadingPreview",
            "SettingsBookshelfSearchErrorPreview",
            "SettingsBookshelfSearchPermissionPreview",
            "SettingsPrivacyPermissionsDefaultPreview",
            "SettingsPrivacyPermissionsConfirmPreview",
            "SettingsPrivacyPermissionsLoadingPreview",
            "SettingsPrivacyPermissionsErrorPreview",
            "SettingsPrivacyPermissionsPermissionPreview",
            "SettingsWebDavNotConfiguredPreview",
            "SettingsWebDavConfiguredPreview",
            "SettingsWebDavAuthErrorPreview",
            "SettingsBackupEnabledPreview",
            "SettingsBackupDisabledPreview",
            "SettingsProgressSyncRunningPreview",
            "SettingsProgressSyncConflictPreview",
            "SettingsProgressSyncOfflinePreview",
            "SettingsRemoteBooksDefaultPreview",
            "SettingsRemoteBooksLoadingPreview",
            "SettingsRemoteBooksEmptyPreview",
            "SettingsRemoteBooksErrorPreview"
        ).forEach { token ->
            assertTrue("Settings secondary preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `settings secondary previews use setting and sync state mappers and explicit state variants`() {
        listOf(
            "GeneralSettingsMapper.fromFixture",
            "GeneralSettingsMapper.optionSheet",
            "GeneralSettingsMapper.loading",
            "GeneralSettingsMapper.error",
            "GeneralSettingsMapper.permission",
            "BookshelfSearchSettingsMapper.fromFixture",
            "BookshelfSearchSettingsMapper.optionSheet",
            "BookshelfSearchSettingsMapper.confirm",
            "BookshelfSearchSettingsMapper.loading",
            "BookshelfSearchSettingsMapper.error",
            "BookshelfSearchSettingsMapper.permission",
            "PrivacyPermissionsMapper.fromFixture",
            "PrivacyPermissionsMapper.confirm",
            "PrivacyPermissionsMapper.loading",
            "PrivacyPermissionsMapper.error",
            "PrivacyPermissionsMapper.permission",
            "DiscoverRssWebDavMapper.webDavNotConfigured",
            "DiscoverRssWebDavMapper.webDavConfigured",
            "DiscoverRssWebDavMapper.webDavAuthError",
            "DiscoverRssWebDavMapper.backup(enabled = true)",
            "DiscoverRssWebDavMapper.backup(enabled = false)",
            "ProgressSyncState.Running",
            "ProgressSyncState.Conflict",
            "ProgressSyncState.Offline",
            "DiscoverRssWebDavMapper.remoteBooks",
            "RemoteWebDavBooksUiState(books = emptyList(), isLoading = true)",
            "RemoteWebDavBooksUiState(books = emptyList())",
            "SyncErrorUiState"
        ).forEach { token ->
            assertTrue("Settings secondary preview source must use $token", token in previewSource)
        }
    }

    @Test
    fun `general settings screen keeps settingsshell slots and shared components`() {
        listOf(
            "GeneralSettingsScreen",
            "ReaderAppTopBar",
            "ReaderChip",
            "ReaderSwitch",
            "GeneralSettingsGroup",
            "GeneralSettingsRow",
            "GeneralSettingsOptionSheet",
            "GeneralSettingsToastHost",
            "GeneralSettingsConfirmDialog",
            "GeneralSettingsFeedback",
            "contentDescription = \"SettingsShell 通用设置\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"sheetHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("General settings screen source must include $token", token in generalSettingsScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("General settings screen must not include $forbidden", forbidden !in generalSettingsScreenSource)
        }
    }

    @Test
    fun `general settings state model keeps frontend input contract text`() {
        listOf(
            "GeneralSettingsDisplayState",
            "GeneralSettingsMapper",
            "fun optionSheet()",
            "fun loading()",
            "fun error()",
            "fun permission()",
            "通用设置",
            "启动时打开",
            "语言",
            "主题跟随系统",
            "崩溃日志",
            "恢复默认",
            "确认恢复",
            "保存成功",
            "需要系统权限"
        ).forEach { token ->
            assertTrue("General settings state source must include $token", token in generalSettingsStateSource)
        }
    }

    @Test
    fun `bookshelf search settings screen keeps settingsshell slots preview and danger action`() {
        listOf(
            "BookshelfSearchSettingsScreen",
            "ReaderAppTopBar",
            "ReaderChip",
            "ReaderSwitch",
            "BookCover",
            "BookshelfSearchSettingsSection",
            "BookshelfSearchSettingsRow",
            "BookshelfModePreviewCard",
            "BookshelfSearchDangerActionRow",
            "BookshelfSearchOptionSheet",
            "BookshelfSearchConfirmDialog",
            "BookshelfSearchDangerButton",
            "BookshelfSearchFeedback",
            "ReaderIconToken.Trash",
            "contentDescription = \"SettingsShell 书架与搜索\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"sheetHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("Bookshelf search settings screen source must include $token", token in bookshelfSearchSettingsScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("Bookshelf search settings screen must not include $forbidden", forbidden !in bookshelfSearchSettingsScreenSource)
        }
    }

    @Test
    fun `bookshelf search settings state model keeps frontend input contract text`() {
        listOf(
            "BookshelfSearchSettingsDisplayState",
            "BookshelfSearchSettingsMapper",
            "fun optionSheet()",
            "fun confirm()",
            "fun loading()",
            "fun error()",
            "fun permission()",
            "书架与搜索",
            "默认展示",
            "封面模式预览",
            "列表模式预览",
            "搜索范围",
            "结果排序",
            "搜索历史",
            "清空搜索历史",
            "确认清空",
            "需要存储权限"
        ).forEach { token ->
            assertTrue("Bookshelf search settings state source must include $token", token in bookshelfSearchSettingsStateSource)
        }
    }

    @Test
    fun `privacy permissions screen keeps settingsshell slots badges and system settings action`() {
        listOf(
            "PrivacyPermissionsScreen",
            "ReaderAppTopBar",
            "ReaderSwitch",
            "PrivacyPermissionsSection",
            "PrivacyPermissionsRow",
            "PrivacyStatusBadge",
            "PrivacyOpenSystemSettingsButton",
            "PrivacyDangerActionRow",
            "PrivacyConfirmDialog",
            "PrivacyDangerButton",
            "PrivacyFeedback",
            "ReaderIconToken.Bell",
            "ReaderIconToken.Battery",
            "ReaderIconToken.EyeOff",
            "ReaderIconToken.Shield",
            "contentDescription = \"SettingsShell 隐私与权限\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("Privacy permissions screen source must include $token", token in privacyPermissionsScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("Privacy permissions screen must not include $forbidden", forbidden !in privacyPermissionsScreenSource)
        }
    }

    @Test
    fun `privacy permissions state model keeps frontend input contract text`() {
        listOf(
            "PrivacyPermissionsDisplayState",
            "PrivacyPermissionsMapper",
            "fun confirm()",
            "fun loading()",
            "fun error()",
            "fun permission()",
            "隐私与权限",
            "文件访问",
            "通知权限",
            "网络访问说明",
            "去设置",
            "隐私开关",
            "已授权",
            "未授权",
            "受系统管理",
            "清除隐私数据",
            "确认清除",
            "需要系统权限"
        ).forEach { token ->
            assertTrue("Privacy permissions state source must include $token", token in privacyPermissionsStateSource)
        }
    }
}
