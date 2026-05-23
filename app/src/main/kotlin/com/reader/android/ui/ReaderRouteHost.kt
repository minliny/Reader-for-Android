package com.reader.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController

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
            navController.navigate(ReaderRoutes.DETAIL)
            backStack.push(ReaderRoutes.DETAIL)
        }
        uri.contains("reader") -> {
            navController.navigate(ReaderRoutes.READER_CONTENT)
            backStack.push(ReaderRoutes.READER_CONTENT)
        }
        else -> {
            navController.navigate(ReaderRoutes.BOOKSHELF)
            backStack.push(ReaderRoutes.BOOKSHELF)
        }
    }
}
