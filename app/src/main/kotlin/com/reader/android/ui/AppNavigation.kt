package com.reader.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.asImageVector
import java.net.URLEncoder

@Suppress("DEPRECATION")
sealed class AppScreen(
    val route: String,
    val label: String,
    val iconToken: ReaderIconToken
) {
    val icon: ImageVector get() = iconToken.asImageVector()

    data object Bookshelf : AppScreen("bookshelf", "书架", ReaderIconToken.Bookshelf)
    data object Discover : AppScreen("discover", "发现", ReaderIconToken.Discover)
    data object Rss : AppScreen("rss", "RSS", ReaderIconToken.Rss)
    data object Settings : AppScreen("settings", "设置", ReaderIconToken.Settings)
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
        ReaderRoutes.BOOKSHELF_EMPTY,
        ReaderRoutes.BOOKSHELF_GROUP_MANAGEMENT,
        ReaderRoutes.BOOKSHELF_LOCAL_IMPORT,
        ReaderRoutes.BOOKSHELF_SORT_FILTER,
        ReaderRoutes.BOOKSHELF_ACTION_SHEET,
        Routes.SEARCH,
        Routes.DETAIL,
        Routes.TOC,
        Routes.READER_CONTENT,
        ReaderRoutes.SOURCE_SWITCH,
        ReaderRoutes.READER_TOC_BOOKMARK,
        ReaderRoutes.READER_APPEARANCE,
        ReaderRoutes.READER_ALOUD,
        ReaderRoutes.READER_SETTINGS,
        ReaderRoutes.READER_AUTO_PAGE,
        ReaderRoutes.READER_CONTENT_SEARCH,
        ReaderRoutes.READER_CONTENT_REPLACEMENT
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
        ReaderRoutes.SOURCE_IMPORT,
        ReaderRoutes.SOURCE_MANAGEMENT_DESIGN
    )

    val settings = setOf(
        ReaderRoutes.SETTINGS,
        ReaderRoutes.MINE,
        ReaderRoutes.GLOBAL_SETTINGS,
        ReaderRoutes.GENERAL_SETTINGS,
        ReaderRoutes.BOOKSHELF_SEARCH_SETTINGS,
        ReaderRoutes.PRIVACY_PERMISSIONS,
        ReaderRoutes.CACHE_MANAGEMENT,
        ReaderRoutes.ABOUT_FEEDBACK,
        ReaderRoutes.SYNC_BACKUP,
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
    const val SOURCE_SWITCH = "source_switch"

    fun detail(detailUrl: String) = "detail/${URLEncoder.encode(detailUrl, "UTF-8")}"
    fun toc(tocUrl: String) = "toc/${URLEncoder.encode(tocUrl, "UTF-8")}"
    fun readerContent(contentUrl: String, chapterTitle: String) =
        "reader_content/${URLEncoder.encode(contentUrl, "UTF-8")}/${URLEncoder.encode(chapterTitle, "UTF-8")}"
}

@Composable
fun AppNavigation() {
    ReaderRouteHost()
}
