package com.reader.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader.android.BuildConfig
import com.reader.android.ui.bookshelf.BookshelfAdapterShell
import com.reader.android.ui.bookshelf.BookshelfScreen
import com.reader.android.ui.booksource.BookSourceScreen
import com.reader.android.ui.booksource.SourceDetailData
import com.reader.android.ui.booksource.SourceDetailScreen
import com.reader.android.ui.booksource.SourceEditScreen
import com.reader.android.ui.booksource.SourceImportScreen
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderMainTab
import com.reader.android.ui.components.ReaderMainTabShell
import com.reader.android.ui.components.ReaderOfflineState
import com.reader.android.ui.components.ReaderPermissionRequiredState
import com.reader.android.ui.detail.BookDetailScreen
import com.reader.android.ui.discover.DiscoverScreen
import com.reader.android.ui.discover.RssDetailScreen
import com.reader.android.ui.discover.RssListScreen
import com.reader.android.ui.discover.RssSubscriptionManagementScreen
import com.reader.android.ui.prototype.ReaderPrototypeGallery
import com.reader.android.ui.reader.ReaderScreen
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.settings.BackupSettingsScreen
import com.reader.android.ui.settings.MineScreen
import com.reader.android.ui.settings.ProgressSyncStatusScreen
import com.reader.android.ui.settings.RemoteWebDavBooksScreen
import com.reader.android.ui.settings.SettingsScreen
import com.reader.android.ui.settings.WebDavConfigScreen
import com.reader.android.ui.toc.TOCScreen
import java.net.URLDecoder

object ReaderRoutes {
    // Tab roots
    const val BOOKSHELF = "bookshelf"
    const val DISCOVER = "discover"
    const val RSS = "rss"
    const val SETTINGS = "settings"

    // Legacy secondary roots retained until the settings flow is fully split.
    const val SOURCES = "sources"
    const val MINE = "mine"

    // Secondary reading entry
    const val READER = "reader"

    // S5 flow
    const val SEARCH = "search"
    const val DETAIL = "detail/{detailUrl}"
    const val TOC = "toc/{tocUrl}"
    const val READER_CONTENT = "reader_content/{contentUrl}/{chapterTitle}"

    // Source management
    const val SOURCE_DETAIL = "source_detail/{sourceId}"
    const val SOURCE_EDIT = "source_edit/{sourceId}"
    const val SOURCE_IMPORT = "source_import"

    // Discover / RSS
    const val RSS_LIST = "rss_list"
    const val RSS_DETAIL = "rss_detail/{rssId}"
    const val RSS_SUBSCRIPTION = "rss_subscription"

    // Mine subscreens
    const val GLOBAL_SETTINGS = "global_settings"
    const val WEBDAV_CONFIG = "webdav_config"
    const val BACKUP_SETTINGS = "backup_settings"
    const val PROGRESS_SYNC = "progress_sync"
    const val REMOTE_WEBDAV_BOOKS = "remote_webdav_books"
    const val ABOUT = "about"
    const val PROTOTYPE_GALLERY = "prototype_gallery"

    // State screens
    const val STATE_ERROR = "state/error/{message}"
    const val STATE_OFFLINE = "state/offline"

    // Deep link placeholder
    const val DEEP_LINK = "deep_link/{uri}"

    fun detail(detailUrl: String) = "detail/${java.net.URLEncoder.encode(detailUrl, "UTF-8")}"
    fun toc(tocUrl: String) = "toc/${java.net.URLEncoder.encode(tocUrl, "UTF-8")}"
    fun readerContent(contentUrl: String, chapterTitle: String) =
        "reader_content/${java.net.URLEncoder.encode(contentUrl, "UTF-8")}/${java.net.URLEncoder.encode(chapterTitle, "UTF-8")}"
}

class ReaderBackStack {
    private val _stack = mutableStateListOf<String>()

    val size: Int get() = _stack.size
    val current: String? get() = _stack.lastOrNull()
    val all: List<String> get() = _stack.toList()

    fun push(route: String) {
        _stack.add(route)
    }

    fun pop(): String? {
        if (_stack.isEmpty()) return null
        return _stack.removeAt(_stack.lastIndex)
    }

    fun popTo(route: String): Boolean {
        val index = _stack.indexOfLast { it == route }
        if (index < 0) return false
        while (_stack.size > index + 1) _stack.removeAt(_stack.lastIndex)
        return true
    }

    fun clear() {
        _stack.clear()
    }
}

@Composable
fun rememberReaderBackStack(): ReaderBackStack = remember { ReaderBackStack() }

data class DeepLinkState(
    val pendingUri: String? = null,
    val handled: Boolean = false
)

@Composable
fun rememberDeepLinkState(): DeepLinkState {
    var state by remember { mutableStateOf(DeepLinkState()) }
    return state
}

fun handleDeepLink(uri: String, navController: NavHostController, backStack: ReaderBackStack) {
    when {
        uri.contains("detail") -> {
            val route = ReaderRoutes.detail(uri)
            navController.navigate(route)
            backStack.push(route)
        }
        uri.contains("reader") -> {
            val route = ReaderRoutes.readerContent(uri, "深链章节")
            navController.navigate(route)
            backStack.push(route)
        }
        else -> {
            navController.navigate(ReaderRoutes.BOOKSHELF)
            backStack.push(ReaderRoutes.BOOKSHELF)
        }
    }
}

@Composable
fun ReaderRouteHost(
    navController: NavHostController = rememberNavController(),
    backStack: ReaderBackStack = rememberReaderBackStack()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val mainTabRoutes = appScreens.map { it.route }.toSet()
    val currentRoute = currentDestination?.route
    val showMainBottomBar = currentRoute in mainTabRoutes
    val selectedMainTabIndex = appScreens.indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)
    val mainTabs = appScreens.map { screen ->
        ReaderMainTab(
            label = screen.label,
            contentDescription = screen.label,
            icon = screen.icon
        )
    }
    val startDestination = ReaderRoutes.BOOKSHELF

    ReaderMainTabShell(
        tabs = mainTabs,
        selectedIndex = selectedMainTabIndex,
        showMainNav = showMainBottomBar,
        onTabSelected = { index ->
            val route = appScreens.getOrNull(index)?.route
            if (route != null) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                backStack.push(route)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ReaderRoutes.BOOKSHELF) {
                BookshelfScreen(
                    onSearchClick = { navController.navigateAndTrack(ReaderRoutes.SEARCH, backStack) },
                    bookshelfState = BookshelfAdapterShell.bookshelfState(),
                    onBookClick = { book ->
                        navController.navigateAndTrack(ReaderRoutes.detail(book.detailTarget), backStack)
                    }
                )
            }
            composable(ReaderRoutes.DISCOVER) {
                DiscoverScreen(onRssClick = { navController.navigateAndTrack(ReaderRoutes.RSS, backStack) })
            }
            composable(ReaderRoutes.RSS) {
                RssListScreen(
                    onSourceClick = { navController.navigateAndTrack("rss_detail/$it", backStack) }
                )
            }
            composable(ReaderRoutes.SETTINGS) {
                MineScreen(
                    onSourceManagementClick = { navController.navigateAndTrack(ReaderRoutes.SOURCES, backStack) },
                    onGlobalSettingsClick = { navController.navigateAndTrack(ReaderRoutes.GLOBAL_SETTINGS, backStack) },
                    onWebDavClick = { navController.navigateAndTrack(ReaderRoutes.WEBDAV_CONFIG, backStack) },
                    onBackupClick = { navController.navigateAndTrack(ReaderRoutes.BACKUP_SETTINGS, backStack) },
                    onProgressSyncClick = { navController.navigateAndTrack(ReaderRoutes.PROGRESS_SYNC, backStack) },
                    onRemoteBooksClick = { navController.navigateAndTrack(ReaderRoutes.REMOTE_WEBDAV_BOOKS, backStack) },
                    onAboutClick = { navController.navigateAndTrack(ReaderRoutes.ABOUT, backStack) },
                    onPrototypeGalleryClick = if (BuildConfig.DEBUG) {
                        { navController.navigateAndTrack(ReaderRoutes.PROTOTYPE_GALLERY, backStack) }
                    } else {
                        null
                    }
                )
            }
            composable(ReaderRoutes.SOURCES) { BookSourceScreen() }
            composable(ReaderRoutes.MINE) {
                MineScreen(
                    onSourceManagementClick = { navController.navigateAndTrack(ReaderRoutes.SOURCES, backStack) },
                    onGlobalSettingsClick = { navController.navigateAndTrack(ReaderRoutes.GLOBAL_SETTINGS, backStack) },
                    onWebDavClick = { navController.navigateAndTrack(ReaderRoutes.WEBDAV_CONFIG, backStack) },
                    onBackupClick = { navController.navigateAndTrack(ReaderRoutes.BACKUP_SETTINGS, backStack) },
                    onProgressSyncClick = { navController.navigateAndTrack(ReaderRoutes.PROGRESS_SYNC, backStack) },
                    onRemoteBooksClick = { navController.navigateAndTrack(ReaderRoutes.REMOTE_WEBDAV_BOOKS, backStack) },
                    onAboutClick = { navController.navigateAndTrack(ReaderRoutes.ABOUT, backStack) },
                    onPrototypeGalleryClick = if (BuildConfig.DEBUG) {
                        { navController.navigateAndTrack(ReaderRoutes.PROTOTYPE_GALLERY, backStack) }
                    } else {
                        null
                    }
                )
            }
            composable(ReaderRoutes.GLOBAL_SETTINGS) {
                SettingsScreen(
                    onPrototypeGalleryClick = if (BuildConfig.DEBUG) {
                        { navController.navigateAndTrack(ReaderRoutes.PROTOTYPE_GALLERY, backStack) }
                    } else {
                        null
                    }
                )
            }

            composable(ReaderRoutes.SEARCH) {
                SearchScreen(
                    onResultClick = { detailUrl ->
                        navController.navigateAndTrack(ReaderRoutes.detail(detailUrl), backStack)
                    }
                )
            }
            composable(
                route = ReaderRoutes.DETAIL,
                arguments = listOf(navArgument("detailUrl") { type = NavType.StringType })
            ) { entry ->
                val detailUrl = entry.encodedArg("detailUrl")
                BookDetailScreen(
                    detailUrl = detailUrl,
                    onBack = { navController.popBackStack() },
                    onTOC = { navController.navigateAndTrack(ReaderRoutes.toc(it), backStack) }
                )
            }
            composable(
                route = ReaderRoutes.TOC,
                arguments = listOf(navArgument("tocUrl") { type = NavType.StringType })
            ) { entry ->
                TOCScreen(
                    tocUrl = entry.encodedArg("tocUrl"),
                    onBack = { navController.popBackStack() },
                    onChapterClick = { contentUrl, title ->
                        navController.navigateAndTrack(ReaderRoutes.readerContent(contentUrl, title), backStack)
                    }
                )
            }
            composable(
                route = ReaderRoutes.READER_CONTENT,
                arguments = listOf(
                    navArgument("contentUrl") { type = NavType.StringType },
                    navArgument("chapterTitle") { type = NavType.StringType }
                )
            ) { entry ->
                ReaderScreen(
                    contentUrl = entry.encodedArg("contentUrl"),
                    chapterTitle = entry.encodedArg("chapterTitle"),
                    onBack = { navController.popBackStack() },
                    onNextChapter = { nextUrl, nextTitle ->
                        navController.navigateAndTrack(ReaderRoutes.readerContent(nextUrl, nextTitle), backStack)
                    }
                )
            }

            composable(
                route = ReaderRoutes.SOURCE_DETAIL,
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType })
            ) { entry ->
                SourceDetailScreen(
                    source = SourceDetailData(
                        sourceName = entry.encodedArg("sourceId").ifBlank { "示例书源" },
                        sourceUrl = "https://example.com/source",
                        sourceGroup = "示例",
                        enabled = true
                    ),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = ReaderRoutes.SOURCE_EDIT,
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType })
            ) { entry ->
                SourceEditScreen(
                    initialName = entry.encodedArg("sourceId").ifBlank { "示例书源" },
                    initialUrl = "https://example.com/source",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ReaderRoutes.SOURCE_IMPORT) {
                SourceImportScreen(onBack = { navController.popBackStack() })
            }

            composable(ReaderRoutes.RSS_LIST) {
                RssListScreen(
                    onBack = { navController.popBackStack() },
                    onSourceClick = { navController.navigateAndTrack("rss_detail/$it", backStack) }
                )
            }
            composable(
                route = ReaderRoutes.RSS_DETAIL,
                arguments = listOf(navArgument("rssId") { type = NavType.StringType })
            ) {
                RssDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(ReaderRoutes.RSS_SUBSCRIPTION) {
                RssSubscriptionManagementScreen(onBack = { navController.popBackStack() })
            }

            composable(ReaderRoutes.WEBDAV_CONFIG) {
                WebDavConfigScreen(onBack = { navController.popBackStack() })
            }
            composable(ReaderRoutes.BACKUP_SETTINGS) {
                BackupSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(ReaderRoutes.PROGRESS_SYNC) {
                ProgressSyncStatusScreen(onBack = { navController.popBackStack() })
            }
            composable(ReaderRoutes.REMOTE_WEBDAV_BOOKS) {
                RemoteWebDavBooksScreen(onBack = { navController.popBackStack() })
            }
            composable(ReaderRoutes.ABOUT) {
                ReaderEmptyState(
                    title = "关于 Reader",
                    message = "版本信息、开源许可、隐私与权限入口。",
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (BuildConfig.DEBUG) {
                composable(ReaderRoutes.PROTOTYPE_GALLERY) {
                    ReaderPrototypeGallery()
                }
            }

            composable(
                route = ReaderRoutes.STATE_ERROR,
                arguments = listOf(navArgument("message") { type = NavType.StringType })
            ) { entry ->
                ReaderErrorState(
                    title = "加载失败",
                    message = entry.encodedArg("message"),
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(ReaderRoutes.STATE_OFFLINE) {
                ReaderOfflineState(modifier = Modifier.fillMaxSize())
            }
            composable(
                route = ReaderRoutes.DEEP_LINK,
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { entry ->
                val uri = entry.encodedArg("uri")
                ReaderPermissionRequiredState(
                    title = "深链待处理",
                    message = uri.ifBlank { "未提供深链" },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun NavHostController.navigateAndTrack(route: String, backStack: ReaderBackStack) {
    navigate(route)
    backStack.push(route)
}

private fun androidx.navigation.NavBackStackEntry.encodedArg(name: String): String =
    URLDecoder.decode(arguments?.getString(name).orEmpty(), "UTF-8")
