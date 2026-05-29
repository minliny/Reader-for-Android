package com.reader.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
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
    data object Sources : AppScreen("sources", "书源", Icons.Filled.Hub)
    data object Mine : AppScreen("mine", "我的", Icons.Filled.Person)
}

val appScreens = listOf(
    AppScreen.Bookshelf,
    AppScreen.Discover,
    AppScreen.Sources,
    AppScreen.Mine
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
        ReaderRoutes.DISCOVER,
        ReaderRoutes.RSS_LIST,
        ReaderRoutes.RSS_DETAIL,
        ReaderRoutes.RSS_SUBSCRIPTION
    )

    val sources = setOf(
        ReaderRoutes.SOURCES,
        ReaderRoutes.SOURCE_DETAIL,
        ReaderRoutes.SOURCE_EDIT,
        ReaderRoutes.SOURCE_IMPORT
    )

    val mine = setOf(
        ReaderRoutes.MINE,
        ReaderRoutes.GLOBAL_SETTINGS,
        ReaderRoutes.WEBDAV_CONFIG,
        ReaderRoutes.BACKUP_SETTINGS,
        ReaderRoutes.PROGRESS_SYNC,
        ReaderRoutes.REMOTE_WEBDAV_BOOKS,
        ReaderRoutes.ABOUT,
        ReaderRoutes.STATE_ERROR,
        ReaderRoutes.STATE_OFFLINE
    )
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
