package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MainTabStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/MainTabStateMatrixPreviews.kt")))
    }

    private val discoverScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/discover/DiscoverScreen.kt")))
    }

    private val discoveryHomeStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/discover/DiscoveryHomeDesignUiState.kt")))
    }

    @Test
    fun `main tab compose previews expose default and state matrix entries`() {
        listOf(
            "@Preview",
            "BookshelfMainTabDefaultPreview",
            "BookshelfMainTabEmptyPreview",
            "DiscoverMainTabDefaultPreview",
            "DiscoverMainTabSubscriptionPreview",
            "DiscoverMainTabLoadingPreview",
            "DiscoverMainTabEmptyPreview",
            "DiscoverMainTabErrorPreview",
            "DiscoverMainTabOfflinePreview",
            "RssMainTabDefaultPreview",
            "RssMainTabLoadingPreview",
            "RssMainTabEmptyPreview",
            "RssMainTabUnreadEmptyPreview",
            "SettingsMainTabDefaultPreview",
            "SettingsMainTabLoadingOverviewPreview",
            "SettingsMainTabNoBackupPreview",
            "SettingsMainTabPermissionNeededPreview"
        ).forEach { token ->
            assertTrue("Main tab preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `main tab compose previews use frontend input states`() {
        listOf(
            "BookshelfMapper.fakeFallback",
            "BookshelfMapper.empty",
            "DiscoveryHomeMapper.fromFixture",
            "DiscoveryHomeMapper.subscription",
            "DiscoveryHomeMapper.loading",
            "DiscoveryHomeMapper.empty",
            "DiscoveryHomeMapper.error",
            "DiscoveryHomeMapper.offline",
            "DiscoverRssWebDavMapper.rssList",
            "DiscoverRssWebDavMapper.rssLoading",
            "DiscoverRssWebDavMapper.rssEmpty",
            "SettingsHomeDisplayState.LoadingOverview",
            "SettingsHomeDisplayState.NoBackup",
            "SettingsHomeDisplayState.PermissionNeeded"
        ).forEach { token ->
            assertTrue("Main tab preview source must use $token", token in previewSource)
        }
    }

    @Test
    fun `discover main tab screen keeps discovery home components and main tab scope`() {
        listOf(
            "DiscoverScreen",
            "DiscoveryHomeUiState",
            "SearchEntry",
            "SourceTypeSegment",
            "CurrentSourceCard",
            "SourceCategoryChips",
            "DiscoveryContentCard",
            "SourceStatusBar",
            "DiscoveryRankingCard",
            "ReaderAppTopBar",
            "ReaderSearchBox",
            "ReaderChip",
            "ReaderSettingsRow",
            "BookCard",
            "contentDescription = \"MainTabShell 发现\"",
            "contentDescription = \"contentRegion\"",
            "contentDescription = \"stateHost\""
        ).forEach { token ->
            assertTrue("Discover screen source must contain $token", token in discoverScreenSource)
        }

        listOf("账号", "头像", "登录入口", "会员", "社区").forEach { forbidden ->
            assertTrue("Discover screen must not contain $forbidden", forbidden !in discoverScreenSource)
        }
    }

    @Test
    fun `discover home state model keeps frontend input contract text`() {
        listOf(
            "DiscoveryHomeDisplayState",
            "DiscoveryHomeMapper",
            "fun subscription()",
            "fun loading()",
            "fun empty()",
            "fun error()",
            "fun offline()",
            "发现",
            "搜索书名、作者、关键词",
            "全部",
            "书源",
            "订阅",
            "玄幻书源组",
            "切换来源",
            "更多分类",
            "来源榜单更新",
            "正在切换来源类型",
            "当前来源暂无内容",
            "来源加载失败",
            "网络不可用",
            "书架",
            "RSS",
            "设置"
        ).forEach { token ->
            assertTrue("Discovery home state source must contain $token", token in discoveryHomeStateSource)
        }
    }
}
