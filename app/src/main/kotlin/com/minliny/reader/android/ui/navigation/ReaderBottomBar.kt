package com.minliny.reader.android.ui.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings

@Composable
fun ReaderBottomBar(
    modifier: Modifier = androidx.compose.ui.Modifier,
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        Screen.Bookshelf to Icons.Default.Home,
        Screen.Search to Icons.Default.Search,
        Screen.Reader to Icons.Default.MenuBook,
        Screen.BookSource to Icons.Default.Book,
        Screen.Settings to Icons.Default.Settings
    )

    NavigationBar(modifier = modifier) {
        items.forEach { (screen, icon) ->
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

            ReaderBottomBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = stringResource(screen.labelResId)) },
                label = { Text(stringResource(screen.labelResId)) }
            )
        }
    }
}

@Composable
fun RowScope.ReaderBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = label
    )
}
