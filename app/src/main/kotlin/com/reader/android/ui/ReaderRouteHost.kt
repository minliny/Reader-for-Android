package com.reader.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.reader.android.ui.bookshelf.BookshelfScreen
import com.reader.android.ui.booksource.BookSourceScreen
import com.reader.android.ui.booksource.SourceDetailData
import com.reader.android.ui.booksource.SourceDetailScreen
import com.reader.android.ui.booksource.SourceEditScreen
import com.reader.android.ui.booksource.SourceImportScreen
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderOfflineState
import com.reader.android.ui.components.ReaderPermissionRequiredState
import com.reader.android.ui.detail.BookDetailScreen
import com.reader.android.ui.discover.DiscoverScreen
import com.reader.android.ui.discover.RssListScreen
import com.reader.android.ui.reader.ReaderScreen
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.settings.SettingsScreen
import com.reader.android.ui.settings.WebDavConfigScreen
import com.reader.android.ui.toc.TOCScreen
import java.net.URLDecoder

object ReaderRoutes {
    // Tab roots
    const val BOOKSHELF = "bookshelf"
    const val BOOKSOURCE = "booksource"
    const val READER = "reader"
    const val SETTINGS = "settings"

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
    const val DISCOVER = "discover"
    const val RSS_LIST = "rss_list"
    const val RSS_DETAIL = "rss_detail/{rssId}"
    const val RSS_SUBSCRIPTION = "rss_subscription"

    // Settings subscreens
    const val WEBDAV_CONFIG = "webdav_config"
    const val BACKUP_SETTINGS = "backup_settings"
    const val PROGRESS_SYNC = "progress_sync"

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

    Scaffold(
        bottomBar = {
            NavigationBar {
                appScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            backStack.push(screen.route)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ReaderRoutes.BOOKSHELF,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ReaderRoutes.BOOKSHELF) {
                BookshelfScreen(onSearchClick = { navController.navigateAndTrack(ReaderRoutes.SEARCH, backStack) })
            }
            composable(ReaderRoutes.BOOKSOURCE) { BookSourceScreen() }
            composable(ReaderRoutes.READER) { ReaderScreen() }
            composable(ReaderRoutes.SETTINGS) { SettingsScreen() }

            composable(ReaderRoutes.SEARCH) { SearchScreen() }
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

            composable(ReaderRoutes.DISCOVER) {
                DiscoverScreen(onRssClick = { navController.navigateAndTrack(ReaderRoutes.RSS_LIST, backStack) })
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
            ) { entry ->
                ReaderEmptyState(
                    title = "RSS 详情",
                    message = "订阅 ${entry.encodedArg("rssId")} 的详情入口已注册，使用静态状态占位。",
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(ReaderRoutes.RSS_SUBSCRIPTION) {
                ReaderEmptyState(
                    title = "RSS 订阅管理",
                    message = "订阅管理入口已注册，使用静态状态占位。",
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(ReaderRoutes.WEBDAV_CONFIG) {
                WebDavConfigScreen(onBack = { navController.popBackStack() })
            }
            composable(ReaderRoutes.BACKUP_SETTINGS) {
                ReaderEmptyState(
                    title = "备份设置",
                    message = "备份设置入口已注册，使用静态状态占位。",
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(ReaderRoutes.PROGRESS_SYNC) {
                ReaderEmptyState(
                    title = "进度同步",
                    message = "同步状态入口已注册，等待真实同步能力接入。",
                    modifier = Modifier.fillMaxSize()
                )
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
