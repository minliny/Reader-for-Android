package com.reader.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
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
    ReaderRouteHost()
}
