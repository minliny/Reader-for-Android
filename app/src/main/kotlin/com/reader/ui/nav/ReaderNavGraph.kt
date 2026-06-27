package com.reader.ui.nav

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
    object Reading : Route("reading/{bookUrl}") {
        fun build(bookUrl: String) = "reading/$bookUrl"
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
                onOpenBook = { bookUrl -> nav.navigate(Route.Reading.build(bookUrl)) },
                onImportSource = { nav.navigate(Route.ImportSource.route) }
            )
        }
        composable(Route.Search.route) {
            SearchScreen(
                onBookClick = { bookUrl -> nav.navigate(Route.Reading.build(bookUrl)) }
            )
        }
        composable(Route.Reading.route) { backStackEntry ->
            val bookUrl = backStackEntry.arguments?.getString("bookUrl") ?: ""
            ReadingScreen(bookUrl = bookUrl)
        }
        composable(Route.ImportSource.route) {
            ImportBookSourceScreen(onDone = { nav.popBackStack() })
        }
    }
}
