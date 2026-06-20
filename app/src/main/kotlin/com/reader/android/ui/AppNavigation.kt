package com.reader.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder

@Suppress("DEPRECATION")
sealed class AppScreen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Bookshelf : AppScreen("bookshelf", "书架", Icons.Filled.Book)
    data object Discover : AppScreen("discover", "发现", Icons.Filled.Explore)
    data object Rss : AppScreen("rss", "RSS", Icons.Filled.RssFeed)
    data object Settings : AppScreen("settings", "设置", Icons.Filled.Settings)
}

val appScreens = listOf(
    AppScreen.Bookshelf,
    AppScreen.Discover,
    AppScreen.Rss,
    AppScreen.Settings
)

object AppRouteGroups {
    val bookshelf = setOf(
        ReaderRoutes.BOOKSHELF,
        Routes.SEARCH,
        Routes.DETAIL,
        Routes.TOC,
        Routes.READER_CONTENT
    )

    val discover = setOf(
        ReaderRoutes.DISCOVER
    )

    val rss = setOf(
        ReaderRoutes.RSS,
        ReaderRoutes.RSS_LIST,
        ReaderRoutes.RSS_DETAIL,
        ReaderRoutes.RSS_SUBSCRIPTION
    )

    val sourceManagement = setOf(
        ReaderRoutes.SOURCES,
        ReaderRoutes.SOURCE_DETAIL,
        ReaderRoutes.SOURCE_EDIT,
        ReaderRoutes.SOURCE_IMPORT
    )

    val settings = setOf(
        ReaderRoutes.SETTINGS,
        ReaderRoutes.MINE,
        ReaderRoutes.GLOBAL_SETTINGS,
        ReaderRoutes.WEBDAV_CONFIG,
        ReaderRoutes.BACKUP_SETTINGS,
        ReaderRoutes.PROGRESS_SYNC,
        ReaderRoutes.REMOTE_WEBDAV_BOOKS,
        ReaderRoutes.ABOUT,
        ReaderRoutes.STATE_ERROR,
        ReaderRoutes.STATE_OFFLINE
    ) + sourceManagement
}

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
    ReaderRouteHost()
}
