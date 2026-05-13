package com.minliny.reader.android.ui.navigation

import androidx.annotation.StringRes
import com.minliny.reader.android.R

sealed class Screen(
    val route: String,
    @StringRes val labelResId: Int
) {
    data object Bookshelf : Screen("bookshelf", R.string.nav_bookshelf)
    data object Search : Screen("search", R.string.nav_search)
    data object Reader : Screen("reader", R.string.nav_reader)
    data object BookSource : Screen("booksource", R.string.nav_booksource)
    data object Settings : Screen("settings", R.string.nav_settings)
}
