package com.reader.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class ReaderIconToken {
    Bookshelf,
    Discover,
    Rss,
    Settings,
    Search,
    More,
    Back
}

fun ReaderIconToken.asImageVector(): ImageVector = when (this) {
    ReaderIconToken.Bookshelf -> Icons.AutoMirrored.Filled.MenuBook
    ReaderIconToken.Discover -> Icons.Filled.Explore
    ReaderIconToken.Rss -> Icons.Filled.RssFeed
    ReaderIconToken.Settings -> Icons.Filled.Settings
    ReaderIconToken.Search -> Icons.Filled.Search
    ReaderIconToken.More -> Icons.Filled.MoreVert
    ReaderIconToken.Back -> Icons.AutoMirrored.Filled.ArrowBack
}
