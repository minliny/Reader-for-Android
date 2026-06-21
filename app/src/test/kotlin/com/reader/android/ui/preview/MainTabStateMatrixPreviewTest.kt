package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MainTabStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/MainTabStateMatrixPreviews.kt")))
    }

    private val bookshelfScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfScreen.kt")))
    }

    private val bookshelfHomeScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfHomeDesignScreen.kt")))
    }

    private val bookshelfHomeStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfHomeDesignUiState.kt")))
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
            "BookshelfMainTabFilteringPreview",
            "BookshelfMainTabLoadingPreview",
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
            "BookshelfHomeMapper.fromFixture",
            "BookshelfHomeMapper.filtering",
            "BookshelfHomeMapper.loading",
            "BookshelfHomeMapper.empty",
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
    fun `bookshelf main tab screen accepts fixture driven home state`() {
        listOf(
            "bookshelfHomeState: BookshelfHomeUiState? = null",
            "BookshelfHomeDesignContent",
            "onHomeGroupChange",
            "onHomeBookOpen",
            "onHomeRead",
            "onSortFilterClick",
            "onBookshelfSettingsClick",
            "onRefreshUpdates",
            "onImportLocal"
        ).forEach { token ->
            assertTrue("BookshelfScreen must expose home-state injection token $token", token in bookshelfScreenSource)
        }

        listOf(
            "MainTabShell 书架",
            "contentRegion",
            "stateHost",
            "ContinueReadingCard",
            "RecentUpdatesSection",
            "BookshelfHomeToolbar",
            "BookshelfHomeGrid",
            "ReaderAppTopBar",
            "ReaderChip",
            "BookCard",
            "BookListItem",
            "ReaderPrimaryButton",
            "ReaderSecondaryButton",
            "ReaderIconToken.Search",
            "ReaderIconToken.More"
        ).forEach { token ->
            assertTrue("BookshelfHomeDesignScreen must contain $token", token in bookshelfHomeScreenSource)
        }
    }

    @Test
    fun `bookshelf main tab state model keeps frontend input contract text`() {
        listOf(
            "BookshelfHomeDisplayState",
            "BookshelfHomeMapper",
            "fun filtering()",
            "fun loading()",
            "fun empty()",
            "10:30",
            "82%",
            "书架",
            "默认",
            "本地书",
            "追更",
            "长夜余火",
            "诡秘之主",
            "明朝那些事儿",
            "Android 札记",
            "正在加载书架",
            "书架暂无书籍",
            "搜索书籍",
            "导入本地书",
            "发现",
            "RSS",
            "设置"
        ).forEach { token ->
            assertTrue("Bookshelf home state source must contain $token", token in bookshelfHomeStateSource)
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
