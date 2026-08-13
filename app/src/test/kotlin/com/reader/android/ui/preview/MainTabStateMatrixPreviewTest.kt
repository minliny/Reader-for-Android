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

    private val rssHomeScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/discover/RssScreens.kt")))
    }

    private val rssHomeStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/discover/RssHomeDesignUiState.kt")))
    }

    private val settingsRootSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/MineScreen.kt")))
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
            "RssMainTabErrorPreview",
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
            "RssHomeDesignMapper.fromFixture",
            "RssHomeDesignMapper.loading",
            "RssHomeDesignMapper.empty",
            "RssHomeDesignMapper.unreadEmpty",
            "RssHomeDesignMapper.error",
            "SettingsHomeMapper.fromFixture",
            "SettingsHomeMapper.loadingOverview",
            "SettingsHomeMapper.noBackup",
            "SettingsHomeMapper.permissionNeeded"
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

    @Test
    fun `rss main tab screen accepts fixture driven home state`() {
        listOf(
            "rssHomeState: RssHomeDesignUiState? = null",
            "effectiveRssState",
            "effectiveStatusFilters",
            "effectiveSourceFilters",
            "订阅概览",
            "阅读状态",
            "来源筛选",
            "最新订阅",
            "还没有订阅内容",
            "当前没有未读内容",
            "订阅流加载失败",
            "保留上次订阅框架",
            "查看全部",
            "添加订阅源"
        ).forEach { token ->
            assertTrue("RssHomeScreen must contain $token", token in rssHomeScreenSource)
        }
    }

    @Test
    fun `rss main tab state model keeps frontend input contract text`() {
        listOf(
            "RssHomeDisplayState",
            "RssHomeDesignMapper",
            "fun loading()",
            "fun empty()",
            "fun unreadEmpty()",
            "fun error()",
            "书架",
            "发现",
            "RSS",
            "设置",
            "全部",
            "未读",
            "收藏",
            "稍后读",
            "书单",
            "全部来源",
            "小说更新",
            "技术文章",
            "书单推送",
            "刷新中",
            "暂无更新",
            "加载失败",
            "订阅流加载失败",
            "保留上次订阅框架"
        ).forEach { token ->
            assertTrue("RSS home state source must contain $token", token in rssHomeStateSource)
        }
    }

    @Test
    fun `settings main tab state model keeps frontend input contract text`() {
        listOf(
            "SettingsHomeMapper",
            "fun loadingOverview()",
            "fun noBackup()",
            "fun permissionNeeded()",
            "SettingsHomeTone",
            "bottomNavLabels",
            "本地概览",
            "本地书籍",
            "订阅源",
            "书源可用",
            "最近备份",
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
            "关于与反馈",
            "待授权",
            "ReaderIconToken.Bookshelf",
            "ReaderIconToken.Rss",
            "ReaderIconToken.SourceSwitch",
            "ReaderIconToken.Shield",
            "SettingsHomeIcon"
        ).forEach { token ->
            assertTrue("Settings root source must contain $token", token in settingsRootSource)
        }
    }
}
