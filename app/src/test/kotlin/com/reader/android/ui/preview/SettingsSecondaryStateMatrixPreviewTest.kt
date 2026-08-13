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

    private val cacheManagementScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/CacheManagementDesignScreen.kt")))
    }

    private val cacheManagementStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/CacheManagementDesignUiState.kt")))
    }

    private val aboutFeedbackScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/AboutFeedbackDesignScreen.kt")))
    }

    private val aboutFeedbackStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/AboutFeedbackDesignUiState.kt")))
    }

    private val syncBackupScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/SyncBackupDesignScreen.kt")))
    }

    private val syncBackupStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/SyncBackupDesignUiState.kt")))
    }

    private val sourceManagementScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/SourceManagementDesignScreen.kt")))
    }

    private val sourceManagementStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/SourceManagementDesignUiState.kt")))
    }

    @Test
    fun `settings secondary compose previews expose general about sync backup source management webdav and remote book states`() {
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
            "SettingsCacheManagementDefaultPreview",
            "SettingsCacheManagementLoadingPreview",
            "SettingsCacheManagementEmptyPreview",
            "SettingsCacheManagementConfirmPreview",
            "SettingsCacheManagementErrorPreview",
            "SettingsAboutFeedbackDefaultPreview",
            "SettingsAboutFeedbackLoadingPreview",
            "SettingsAboutFeedbackErrorPreview",
            "SettingsAboutFeedbackConfirmPreview",
            "SettingsAboutFeedbackOfflinePreview",
            "SettingsSyncBackupDefaultPreview",
            "SettingsSyncBackupConfirmPreview",
            "SettingsSyncBackupLoadingPreview",
            "SettingsSyncBackupEmptyPreview",
            "SettingsSyncBackupErrorPreview",
            "SettingsSyncBackupOfflinePreview",
            "SettingsSyncBackupPermissionPreview",
            "SettingsSourceManagementDefaultPreview",
            "SettingsSourceManagementEditPreview",
            "SettingsSourceManagementLogPreview",
            "SettingsSourceManagementConfirmPreview",
            "SettingsSourceManagementLoadingPreview",
            "SettingsSourceManagementEmptyPreview",
            "SettingsSourceManagementErrorPreview",
            "SettingsSourceManagementOfflinePreview",
            "SettingsSourceManagementPermissionPreview",
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
            "CacheManagementMapper.fromFixture",
            "CacheManagementMapper.loading",
            "CacheManagementMapper.empty",
            "CacheManagementMapper.confirm",
            "CacheManagementMapper.error",
            "AboutFeedbackMapper.fromFixture",
            "AboutFeedbackMapper.loading",
            "AboutFeedbackMapper.error",
            "AboutFeedbackMapper.confirm",
            "AboutFeedbackMapper.offline",
            "SyncBackupMapper.fromFixture",
            "SyncBackupMapper.confirm",
            "SyncBackupMapper.loading",
            "SyncBackupMapper.empty",
            "SyncBackupMapper.error",
            "SyncBackupMapper.offline",
            "SyncBackupMapper.permission",
            "SourceManagementDesignMapper.fromFixture",
            "SourceManagementDesignMapper.edit",
            "SourceManagementDesignMapper.log",
            "SourceManagementDesignMapper.confirm",
            "SourceManagementDesignMapper.loading",
            "SourceManagementDesignMapper.empty",
            "SourceManagementDesignMapper.error",
            "SourceManagementDesignMapper.offline",
            "SourceManagementDesignMapper.permission",
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

    @Test
    fun `cache management screen keeps settingsshell slots storage card and cleanup feedback`() {
        listOf(
            "CacheManagementScreen",
            "ReaderAppTopBar",
            "ReaderSwitch",
            "CacheSizeCard",
            "StorageBar",
            "CacheManagementSection",
            "CacheCategoryRow",
            "CacheDangerActionRow",
            "CacheCleanupResult",
            "CacheEmptyState",
            "CacheConfirmDialog",
            "CacheDangerButton",
            "CacheFeedback",
            "ReaderIconToken.Storage",
            "ReaderIconToken.Download",
            "ReaderIconToken.Image",
            "contentDescription = \"SettingsShell 缓存管理\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("Cache management screen source must include $token", token in cacheManagementScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("Cache management screen must not include $forbidden", forbidden !in cacheManagementScreenSource)
        }
    }

    @Test
    fun `cache management state model keeps frontend input contract text`() {
        listOf(
            "CacheManagementDisplayState",
            "CacheManagementMapper",
            "fun loading()",
            "fun empty()",
            "fun confirm()",
            "fun error()",
            "缓存管理",
            "缓存占用",
            "正在计算",
            "清理缓存",
            "缓存分类",
            "确认清理",
            "暂无缓存",
            "重新计算",
            "不会删除书架记录",
            "已清理"
        ).forEach { token ->
            assertTrue("Cache management state source must include $token", token in cacheManagementStateSource)
        }
    }

    @Test
    fun `about feedback screen keeps settingsshell slots version links feedback and update controls`() {
        listOf(
            "AboutFeedbackScreen",
            "ReaderAppTopBar",
            "VersionCard",
            "AboutFeedbackSection",
            "AboutLinkRow",
            "FeedbackEntry",
            "UpdateButton",
            "AboutFeedbackConfirmDialog",
            "AboutFeedbackFeedback",
            "AboutToastHost",
            "ReaderIconToken.Check",
            "ReaderIconToken.Link",
            "ReaderIconToken.Message",
            "ReaderIconToken.Help",
            "contentDescription = \"SettingsShell 关于与反馈\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("About feedback screen source must include $token", token in aboutFeedbackScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("About feedback screen must not include $forbidden", forbidden !in aboutFeedbackScreenSource)
        }
    }

    @Test
    fun `about feedback state model keeps frontend input contract text`() {
        listOf(
            "AboutFeedbackDisplayState",
            "AboutFeedbackMapper",
            "fun loading()",
            "fun error()",
            "fun confirm()",
            "fun offline()",
            "关于与反馈",
            "当前版本",
            "检查更新",
            "问题反馈",
            "开源许可",
            "隐私协议",
            "确认导出",
            "网络不可用，请稍后重试",
            "本应用不提供账户与登录功能"
        ).forEach { token ->
            assertTrue("About feedback state source must include $token", token in aboutFeedbackStateSource)
        }
    }

    @Test
    fun `sync backup screen keeps settingsshell slots backup records conflict and restore confirm`() {
        listOf(
            "SyncBackupScreen",
            "ReaderAppTopBar",
            "SyncBackupSection",
            "BackupActionRow",
            "BackupRecordRow",
            "ConflictNotice",
            "RestoreConfirmDialog",
            "SyncBackupSettingRow",
            "SyncBackupToastHost",
            "SyncBackupFeedback",
            "ReaderIconToken.Upload",
            "contentDescription = \"SettingsShell 同步与备份\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("Sync backup screen source must include $token", token in syncBackupScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("Sync backup screen must not include $forbidden", forbidden !in syncBackupScreenSource)
        }
    }

    @Test
    fun `sync backup state model keeps frontend input contract text`() {
        listOf(
            "SyncBackupDisplayState",
            "SyncBackupMapper",
            "fun confirm()",
            "fun loading()",
            "fun empty()",
            "fun error()",
            "fun offline()",
            "fun permission()",
            "同步与备份",
            "备份位置",
            "立即备份",
            "恢复备份",
            "自动备份",
            "备份记录",
            "确认恢复",
            "网络不可用，请稍后重试",
            "需要文件访问权限"
        ).forEach { token ->
            assertTrue("Sync backup state source must include $token", token in syncBackupStateSource)
        }
    }

    @Test
    fun `source management screen keeps settingsshell slots search source rows detect log and edit form`() {
        listOf(
            "SourceManagementDesignScreen",
            "ReaderAppTopBar",
            "ReaderSearchBox",
            "SourceMetricStrip",
            "SourceManagementSection",
            "SourceRow",
            "DetectButton",
            "LogPanel",
            "SourceEditForm",
            "SourceDetailPanel",
            "SourceDisableConfirmDialog",
            "SourceFloatingAddButton",
            "ReaderIconToken.Edit",
            "contentDescription = \"SettingsShell 书源管理\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("Source management screen source must include $token", token in sourceManagementScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("Source management screen must not include $forbidden", forbidden !in sourceManagementScreenSource)
        }
    }

    @Test
    fun `source management state model keeps frontend input contract text`() {
        listOf(
            "SourceManagementDesignDisplayState",
            "SourceManagementDesignMapper",
            "fun edit()",
            "fun log()",
            "fun confirm()",
            "fun loading()",
            "fun empty()",
            "fun error()",
            "fun offline()",
            "fun permission()",
            "书源管理",
            "书源列表",
            "搜索框",
            "启用开关",
            "检测",
            "详情",
            "新增",
            "编辑",
            "错误日志",
            "网络不可用，请稍后重试",
            "需要网络权限"
        ).forEach { token ->
            assertTrue("Source management state source must include $token", token in sourceManagementStateSource)
        }
    }
}
