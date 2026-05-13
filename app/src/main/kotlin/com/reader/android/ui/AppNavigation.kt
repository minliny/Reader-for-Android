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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reader.android.ui.bookshelf.BookshelfScreen
import com.reader.android.ui.booksource.BookSourceScreen
import com.reader.android.ui.reader.ReaderScreen
import com.reader.android.ui.settings.SettingsScreen

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
            composable(AppScreen.Bookshelf.route) { BookshelfScreen() }
            composable(AppScreen.BookSource.route) { BookSourceScreen() }
            composable(AppScreen.Reader.route) { ReaderScreen() }
            composable(AppScreen.Settings.route) { SettingsScreen() }
        }
    }
}
