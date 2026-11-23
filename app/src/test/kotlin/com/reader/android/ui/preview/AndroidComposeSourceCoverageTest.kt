package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/** Current Kotlin anchors only. This does not execute a mapper, render Compose, or prove callback behavior. */
class AndroidComposeSourceCoverageTest {
    private data class CoverageEntry(
        val name: String,
        val sourcePaths: List<String>,
        val previewPath: String,
        val sourceTokens: List<String>,
        val previewTokens: List<String>
    )

    @Test
    fun `current Kotlin mapper and preview anchors remain present`() {
        coverageEntries.forEach { entry ->
            val composeSource = entry.sourcePaths.joinToString(separator = "\n") { appSource(it) }
            entry.sourceTokens.forEach { token ->
                assertTrue("${entry.name} Kotlin source must contain $token", token in composeSource)
            }
            val previewSource = appSource(entry.previewPath)
            entry.previewTokens.forEach { token ->
                assertTrue("${entry.name} preview source must contain $token", token in previewSource)
            }
        }
    }

    private fun appSource(path: String): String =
        String(Files.readAllBytes(Paths.get(path)))

    private companion object {
        private const val MainTabPreview = "src/main/kotlin/com/reader/android/ui/preview/MainTabStateMatrixPreviews.kt"
        private const val LibraryPreview = "src/main/kotlin/com/reader/android/ui/preview/LibraryFlowStateMatrixPreviews.kt"
        private const val ReaderPreview = "src/main/kotlin/com/reader/android/ui/preview/ReaderShellStateMatrixPreviews.kt"
        private const val ReaderControlPreview = "src/main/kotlin/com/reader/android/ui/preview/ReaderControlStateMatrixPreviews.kt"
        private const val SettingsPreview = "src/main/kotlin/com/reader/android/ui/preview/SettingsSecondaryStateMatrixPreviews.kt"

        private val coverageEntries = listOf(
            CoverageEntry(
                name = "书架主标签页",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfHomeDesignUiState.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("BookshelfHomeMapper", "fun filtering()", "fun loading()", "fun empty()"),
                previewTokens = listOf("BookshelfMainTabDefaultPreview", "BookshelfMainTabFilteringPreview", "BookshelfMainTabLoadingPreview", "BookshelfMainTabEmptyPreview")
            ),
            CoverageEntry(
                name = "发现主标签页",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/discover/DiscoveryHomeDesignUiState.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("DiscoveryHomeMapper", "fun subscription()", "fun loading()", "fun empty()", "fun error()", "fun offline()"),
                previewTokens = listOf("DiscoverMainTabDefaultPreview", "DiscoverMainTabSubscriptionPreview", "DiscoverMainTabLoadingPreview", "DiscoverMainTabEmptyPreview", "DiscoverMainTabErrorPreview", "DiscoverMainTabOfflinePreview")
            ),
            CoverageEntry(
                name = "RSS 主标签页",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/discover/RssHomeDesignUiState.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("RssHomeDesignMapper", "fun loading()", "fun empty()", "fun unreadEmpty()", "fun error()"),
                previewTokens = listOf("RssMainTabDefaultPreview", "RssMainTabLoadingPreview", "RssMainTabEmptyPreview", "RssMainTabUnreadEmptyPreview", "RssMainTabErrorPreview")
            ),
            CoverageEntry(
                name = "设置主标签页",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/MineScreen.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("SettingsHomeMapper", "fun loadingOverview()", "fun noBackup()", "fun permissionNeeded()"),
                previewTokens = listOf("SettingsMainTabDefaultPreview", "SettingsMainTabLoadingOverviewPreview", "SettingsMainTabNoBackupPreview", "SettingsMainTabPermissionNeededPreview")
            ),
            CoverageEntry(
                name = "书架空状态",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfEmptyDesignUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfEmptyDesignMapper", "fun allEmpty()", "fun loading()", "fun error()", "fun offline()", "fun permission()"),
                previewTokens = listOf("LibraryBookshelfEmptyDefaultPreview", "LibraryBookshelfEmptyAllEmptyPreview", "LibraryBookshelfEmptyLoadingPreview", "LibraryBookshelfEmptyErrorPreview", "LibraryBookshelfEmptyOfflinePreview", "LibraryBookshelfEmptyPermissionPreview")
            ),
            CoverageEntry(
                name = "书籍搜索",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/search/SearchAdapterShell.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("SearchAdapterShell", "searchHome", "searchResults", "searchLoading", "searchEmpty", "searchError"),
                previewTokens = listOf("LibrarySearchHomePreview", "LibrarySearchResultsPreview", "LibrarySearchLoadingPreview", "LibrarySearchEmptyPreview", "LibrarySearchErrorPreview", "LibrarySearchOfflinePreview", "LibrarySearchPermissionPreview")
            ),
            CoverageEntry(
                name = "书籍详情",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/detail/BookDetailAdapterShell.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookDetailAdapterShell", "detailReady", "detailLoading", "detailEmpty", "detailError"),
                previewTokens = listOf("LibraryBookDetailDefaultPreview", "LibraryBookDetailLoadingPreview", "LibraryBookDetailEmptyPreview", "LibraryBookDetailErrorPreview", "LibraryBookDetailOfflinePreview", "LibraryBookDetailPermissionPreview")
            ),
            CoverageEntry(
                name = "书籍目录",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/toc/BookDirectoryUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookDirectoryUiStateMapper", "fromFixture", "loading", "empty", "error"),
                previewTokens = listOf("LibraryBookDirectoryDefaultPreview", "LibraryBookDirectoryLoadingPreview", "LibraryBookDirectoryEmptyPreview", "LibraryBookDirectoryErrorPreview")
            ),
            CoverageEntry(
                name = "排序与筛选",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfSortFilterUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfSortFilterMapper", "fun selected()", "fun empty()", "fun error("),
                previewTokens = listOf("LibrarySortFilterDefaultPreview", "LibrarySortFilterSelectedPreview", "LibrarySortFilterEmptyPreview", "LibrarySortFilterErrorPreview")
            ),
            CoverageEntry(
                name = "书籍操作底表",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfActionSheetDesignUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfActionSheetMapper", "fun danger()", "fun loading()", "fun error()"),
                previewTokens = listOf("LibraryBookActionSheetDefaultPreview", "LibraryBookActionSheetDangerPreview", "LibraryBookActionSheetLoadingPreview", "LibraryBookActionSheetErrorPreview")
            ),
            CoverageEntry(
                name = "分组管理",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfGroupManagementUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfGroupManagementMapper", "fun newGroup()", "fun rename()", "fun delete()", "fun empty()", "fun loading()", "fun error("),
                previewTokens = listOf("LibraryGroupManagementDefaultPreview", "LibraryGroupManagementNewPreview", "LibraryGroupManagementRenamePreview", "LibraryGroupManagementDeletePreview", "LibraryGroupManagementEmptyPreview", "LibraryGroupManagementLoadingPreview", "LibraryGroupManagementErrorPreview")
            ),
            CoverageEntry(
                name = "本地书导入",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfLocalImportUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfLocalImportMapper", "fun importing()", "fun success()", "fun partialFailed()", "fun failed()", "fun pickerCancelled()"),
                previewTokens = listOf("LibraryLocalImportDefaultPreview", "LibraryLocalImportImportingPreview", "LibraryLocalImportSuccessPreview", "LibraryLocalImportPartialFailedPreview", "LibraryLocalImportFailedPreview", "LibraryLocalImportPickerCancelledPreview")
            ),
            CoverageEntry(
                name = "阅读控制层",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/components/ReaderBottomFunctionOverlay.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/components/ReaderQuickActionOverlay.kt"
                ),
                previewPath = ReaderControlPreview,
                sourceTokens = listOf("ReaderControlBase", "ReaderBottomPanel", "ReaderQuickActionPanel"),
                previewTokens = listOf("ReaderControlDefaultPreview", "ReaderControlDirectoryModulePreview", "ReaderControlTtsModulePreview", "ReaderControlAppearanceModulePreview", "ReaderControlSettingsModulePreview")
            ),
            CoverageEntry(
                name = "阅读入口",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReaderShellDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingEntryMapper", "fun loading()", "fun error()", "fun offline()"),
                previewTokens = listOf("ReadingEntryDefaultPreview", "ReadingEntryLoadingPreview", "ReadingEntryErrorPreview", "ReadingEntryOfflinePreview")
            ),
            CoverageEntry(
                name = "沉浸阅读",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReaderShellDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ImmersiveReadingMapper", "fun loading()", "fun error()", "fun offline()"),
                previewTokens = listOf("ImmersiveReadingDefaultPreview", "ImmersiveReadingLoadingPreview", "ImmersiveReadingErrorPreview", "ImmersiveReadingOfflinePreview")
            ),
            CoverageEntry(
                name = "目录与书签",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingTocBookmarkDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingTocBookmarkMapper", "fun bookmark()", "fun search()", "fun empty()", "fun loading()", "fun error()", "fun moreMenu()"),
                previewTokens = listOf("ReadingTocBookmarkDefaultPreview", "ReadingTocBookmarkBookmarkPreview", "ReadingTocBookmarkSearchPreview", "ReadingTocBookmarkEmptyPreview", "ReadingTocBookmarkLoadingPreview", "ReadingTocBookmarkErrorPreview", "ReadingTocBookmarkMoreMenuPreview")
            ),
            CoverageEntry(
                name = "阅读外观",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingAppearanceDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingAppearanceMapper", "fun font()", "fun theme()", "fun edit()", "fun loading()", "fun error()"),
                previewTokens = listOf("ReadingAppearanceDefaultPreview", "ReadingAppearanceFontPreview", "ReadingAppearanceThemePreview", "ReadingAppearanceEditPreview", "ReadingAppearanceLoadingPreview", "ReadingAppearanceErrorPreview")
            ),
            CoverageEntry(
                name = "朗读",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingAloudDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingAloudMapper", "fun running()", "fun paused()", "fun error()"),
                previewTokens = listOf("ReadingAloudDefaultPreview", "ReadingAloudRunningPreview", "ReadingAloudPausedPreview", "ReadingAloudErrorPreview")
            ),
            CoverageEntry(
                name = "阅读设置",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingSettingsDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingSettingsMapper", "fun subpage()", "fun loading()", "fun error()"),
                previewTokens = listOf("ReadingSettingsDefaultPreview", "ReadingSettingsSubpagePreview", "ReadingSettingsLoadingPreview", "ReadingSettingsErrorPreview")
            ),
            CoverageEntry(
                name = "自动翻页",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/AutoPageDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("AutoPageMapper", "fun running()", "fun paused()", "fun error()"),
                previewTokens = listOf("AutoPageDefaultPreview", "AutoPageRunningPreview", "AutoPagePausedPreview", "AutoPageErrorPreview")
            ),
            CoverageEntry(
                name = "内容搜索",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ContentSearchDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ContentSearchMapper", "fun loading()", "fun empty()", "fun error()", "fun offline()"),
                previewTokens = listOf("ContentSearchDefaultPreview", "ContentSearchLoadingPreview", "ContentSearchEmptyPreview", "ContentSearchErrorPreview", "ContentSearchOfflinePreview")
            ),
            CoverageEntry(
                name = "内容替换",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ContentReplacementDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ContentReplacementMapper", "fun edit()", "fun empty()", "fun loading()", "fun error()"),
                previewTokens = listOf("ContentReplacementDefaultPreview", "ContentReplacementEditPreview", "ContentReplacementEmptyPreview", "ContentReplacementLoadingPreview", "ContentReplacementErrorPreview")
            ),
            CoverageEntry(
                name = "换源",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt"),
                previewPath = "src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt",
                sourceTokens = listOf("SourceSwitchFlowMapper", "fun loading()", "fun empty()", "fun error()", "fun offline()", "fun permission()"),
                previewTokens = listOf("SourceSwitchFlowDefaultPreview", "SourceSwitchFlowLoadingPreview", "SourceSwitchFlowEmptyPreview", "SourceSwitchFlowErrorPreview", "SourceSwitchFlowOfflinePreview", "SourceSwitchFlowPermissionPreview")
            ),
            CoverageEntry(
                name = "App 通用设置",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/GeneralSettingsDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("GeneralSettingsMapper", "fun fromFixture()", "fun optionSheet()", "fun loading()", "fun error()", "fun permission()"),
                previewTokens = listOf("SettingsGeneralDefaultPreview", "SettingsGeneralOptionSheetPreview", "SettingsGeneralLoadingPreview", "SettingsGeneralErrorPreview", "SettingsGeneralPermissionPreview")
            ),
            CoverageEntry(
                name = "书架与搜索设置",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/BookshelfSearchSettingsDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("BookshelfSearchSettingsMapper", "fun fromFixture()", "fun optionSheet()", "fun confirm()", "fun loading()", "fun error()", "fun permission()"),
                previewTokens = listOf("SettingsBookshelfSearchDefaultPreview", "SettingsBookshelfSearchOptionSheetPreview", "SettingsBookshelfSearchConfirmPreview", "SettingsBookshelfSearchLoadingPreview", "SettingsBookshelfSearchErrorPreview", "SettingsBookshelfSearchPermissionPreview")
            ),
            CoverageEntry(
                name = "隐私与权限",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/PrivacyPermissionsDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("PrivacyPermissionsMapper", "fun fromFixture()", "fun confirm()", "fun loading()", "fun error()", "fun permission()"),
                previewTokens = listOf("SettingsPrivacyPermissionsDefaultPreview", "SettingsPrivacyPermissionsConfirmPreview", "SettingsPrivacyPermissionsLoadingPreview", "SettingsPrivacyPermissionsErrorPreview", "SettingsPrivacyPermissionsPermissionPreview")
            ),
            CoverageEntry(
                name = "缓存管理",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/CacheManagementDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("CacheManagementMapper", "fun fromFixture()", "fun loading()", "fun empty()", "fun confirm()", "fun error()"),
                previewTokens = listOf("SettingsCacheManagementDefaultPreview", "SettingsCacheManagementLoadingPreview", "SettingsCacheManagementEmptyPreview", "SettingsCacheManagementConfirmPreview", "SettingsCacheManagementErrorPreview")
            ),
            CoverageEntry(
                name = "关于与反馈",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/AboutFeedbackDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("AboutFeedbackMapper", "fun fromFixture()", "fun loading()", "fun error()", "fun confirm()", "fun offline()"),
                previewTokens = listOf("SettingsAboutFeedbackDefaultPreview", "SettingsAboutFeedbackLoadingPreview", "SettingsAboutFeedbackErrorPreview", "SettingsAboutFeedbackConfirmPreview", "SettingsAboutFeedbackOfflinePreview")
            ),
            CoverageEntry(
                name = "同步与备份",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/SyncBackupDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("SyncBackupMapper", "fun fromFixture()", "fun confirm()", "fun loading()", "fun empty()", "fun error()", "fun offline()", "fun permission()"),
                previewTokens = listOf("SettingsSyncBackupDefaultPreview", "SettingsSyncBackupConfirmPreview", "SettingsSyncBackupLoadingPreview", "SettingsSyncBackupEmptyPreview", "SettingsSyncBackupErrorPreview", "SettingsSyncBackupOfflinePreview", "SettingsSyncBackupPermissionPreview")
            ),
            CoverageEntry(
                name = "书源管理",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceManagementUiState.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/BookSourceScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceDetailScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceEditScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceImportScreen.kt"
                ),
                previewPath = "src/main/kotlin/com/reader/android/ui/preview/SourceManagementStateMatrixPreviews.kt",
                sourceTokens = listOf("SourceManagementMapper", "fun fakeFallback()", "fun importPreview", "fun importSuccess", "fun BookSourceScreen", "fun SourceDetailScreen", "fun SourceEditScreen", "fun SourceImportScreen"),
                previewTokens = listOf("SourceManagementListDefaultPreview", "SourceManagementListLoadingPreview", "SourceManagementListEmptyPreview", "SourceManagementListErrorPreview", "SourceManagementDetailEnabledPreview", "SourceManagementDetailDisabledPreview", "SourceManagementEditExistingPreview", "SourceManagementEditBlankPreview", "SourceManagementImportIdlePreview", "SourceManagementImportReadyJsonPreview", "SourceManagementImportImportedPreview", "SourceManagementImportErrorPreview")
            )
        )
    }
}
