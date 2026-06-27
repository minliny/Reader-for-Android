package com.reader.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reader.ui.bookshelf.BookshelfScreen
import com.reader.ui.search.SearchScreen
import com.reader.ui.reading.ReadingScreen
import com.reader.ui.source.ImportBookSourceScreen

sealed class Route(val route: String) {
    object Bookshelf : Route("bookshelf")
    object Search : Route("search")
    object Reading : Route("reading?sourceId={sourceId}&bookUrl={bookUrl}&bookName={bookName}") {
        fun build(sourceId: String, bookUrl: String, bookName: String): String {
            return "reading?sourceId=${Uri.encode(sourceId)}&bookUrl=${Uri.encode(bookUrl)}&bookName=${Uri.encode(bookName)}"
        }
    }
    object ImportSource : Route("import_source")
}

@Composable
fun ReaderNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Bookshelf.route) {
        composable(Route.Bookshelf.route) {
            BookshelfScreen(
                onSearch = { nav.navigate(Route.Search.route) },
                onOpenBook = { bookUrl ->
                    // 书架打开书:sourceId 未知,用 bookUrl 作为 sourceId(简化)
                    nav.navigate(Route.Reading.build(bookUrl, bookUrl, ""))
                },
                onImportSource = { nav.navigate(Route.ImportSource.route) }
            )
        }
        composable(Route.Search.route) {
            SearchScreen(
                onBookClick = { searchBook ->
                    // 从搜索结果打开:用 origin 作为 sourceId
                    nav.navigate(Route.Reading.build(searchBook.origin, searchBook.bookUrl, searchBook.name))
                }
            )
        }
        composable(route = Route.Reading.route) { backStackEntry ->
            // query string 占位符 {sourceId}/{bookUrl}/{bookName} 由 Navigation
            // 自动解析为 StringType 并放入 arguments Bundle,无需 navArgument 声明。
            val sourceId = backStackEntry.arguments?.getString("sourceId") ?: ""
            val bookUrl = backStackEntry.arguments?.getString("bookUrl") ?: ""
            val bookName = backStackEntry.arguments?.getString("bookName") ?: ""
            ReadingScreen(sourceId = sourceId, bookUrl = bookUrl, bookName = bookName)
        }
        composable(Route.ImportSource.route) {
            ImportBookSourceScreen(onDone = { nav.popBackStack() })
        }
    }
}
