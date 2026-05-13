package com.reader.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader.android.ui.bookshelf.BookshelfScreen
import com.reader.android.ui.booksource.BookSourceScreen
import com.reader.android.ui.detail.BookDetailScreen
import com.reader.android.ui.reader.ReaderScreen
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.settings.SettingsScreen
import com.reader.android.ui.toc.TOCScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Suppress("DEPRECATION")
sealed class AppScreen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Bookshelf : AppScreen("bookshelf", "书架", Icons.Filled.Book)
    data object BookSource : AppScreen("booksource", "书源", Icons.Filled.Search)
    data object Reader : AppScreen("reader", "阅读", Icons.Filled.MenuBook)
    data object Settings : AppScreen("settings", "设置", Icons.Filled.Settings)
}

val appScreens = listOf(
    AppScreen.Bookshelf,
    AppScreen.BookSource,
    AppScreen.Reader,
    AppScreen.Settings
)

object Routes {
    const val SEARCH = "search"
    const val DETAIL = "detail/{detailUrl}"
    const val TOC = "toc/{tocUrl}"
    const val READER_CONTENT = "reader_content/{contentUrl}/{chapterTitle}"

    fun detail(detailUrl: String) = "detail/${URLEncoder.encode(detailUrl, "UTF-8")}"
    fun toc(tocUrl: String) = "toc/${URLEncoder.encode(tocUrl, "UTF-8")}"
    fun readerContent(contentUrl: String, chapterTitle: String) =
        "reader_content/${URLEncoder.encode(contentUrl, "UTF-8")}/${URLEncoder.encode(chapterTitle, "UTF-8")}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
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
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppScreen.Bookshelf.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Tab screens ──
            composable(AppScreen.Bookshelf.route) {
                BookshelfScreen(onSearchClick = {
                    navController.navigate(Routes.SEARCH)
                })
            }
            composable(AppScreen.BookSource.route) { BookSourceScreen() }
            composable(AppScreen.Reader.route) { ReaderScreen() }
            composable(AppScreen.Settings.route) { SettingsScreen() }

            // ── S5 flow: Search → Detail → TOC → Reader ──
            composable(Routes.SEARCH) {
                SearchScreen()
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("detailUrl") { type = NavType.StringType })
            ) { backStackEntry ->
                val detailUrl = URLDecoder.decode(
                    backStackEntry.arguments?.getString("detailUrl") ?: "", "UTF-8"
                )
                BookDetailScreen(
                    detailUrl = detailUrl,
                    onBack = { navController.popBackStack() },
                    onTOC = { tocUrl ->
                        navController.navigate(Routes.toc(tocUrl))
                    }
                )
            }
            composable(
                route = Routes.TOC,
                arguments = listOf(navArgument("tocUrl") { type = NavType.StringType })
            ) { backStackEntry ->
                val tocUrl = URLDecoder.decode(
                    backStackEntry.arguments?.getString("tocUrl") ?: "", "UTF-8"
                )
                TOCScreen(
                    tocUrl = tocUrl,
                    onBack = { navController.popBackStack() },
                    onChapterClick = { contentUrl, title ->
                        navController.navigate(Routes.readerContent(contentUrl, title))
                    }
                )
            }
            composable(
                route = Routes.READER_CONTENT,
                arguments = listOf(
                    navArgument("contentUrl") { type = NavType.StringType },
                    navArgument("chapterTitle") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val contentUrl = URLDecoder.decode(
                    backStackEntry.arguments?.getString("contentUrl") ?: "", "UTF-8"
                )
                val chapterTitle = URLDecoder.decode(
                    backStackEntry.arguments?.getString("chapterTitle") ?: "", "UTF-8"
                )
                ReaderScreen(
                    contentUrl = contentUrl,
                    chapterTitle = chapterTitle,
                    onBack = { navController.popBackStack() },
                    onNextChapter = { nextUrl, nextTitle ->
                        navController.navigate(Routes.readerContent(nextUrl, nextTitle))
                    }
                )
            }
        }
    }
}
