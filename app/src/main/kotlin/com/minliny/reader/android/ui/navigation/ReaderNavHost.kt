package com.minliny.reader.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.minliny.reader.android.ui.screens.bookshelf.BookshelfScreen
import com.minliny.reader.android.ui.screens.booksource.BookSourceScreen
import com.minliny.reader.android.ui.screens.reader.ReaderScreen
import com.minliny.reader.android.ui.screens.search.SearchScreen
import com.minliny.reader.android.ui.screens.settings.SettingsScreen

@Composable
fun ReaderNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Bookshelf.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Bookshelf.route) {
            BookshelfScreen()
        }
        composable(Screen.Search.route) {
            SearchScreen()
        }
        composable(Screen.Reader.route) {
            ReaderScreen()
        }
        composable(Screen.BookSource.route) {
            BookSourceScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
