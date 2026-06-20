package com.reader.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
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
    Back,
    Chevron,
    ChevronDown,
    ViewList,
    Grid,
    Add,
    Delete,
    FileOpen,
    FolderOff,
    Warning,
    Offline,
    Permission
}

fun ReaderIconToken.asImageVector(): ImageVector = when (this) {
    ReaderIconToken.Bookshelf -> Icons.AutoMirrored.Filled.MenuBook
    ReaderIconToken.Discover -> Icons.Filled.Explore
    ReaderIconToken.Rss -> Icons.Filled.RssFeed
    ReaderIconToken.Settings -> Icons.Filled.Settings
    ReaderIconToken.Search -> Icons.Filled.Search
    ReaderIconToken.More -> Icons.Filled.MoreVert
    ReaderIconToken.Back -> Icons.AutoMirrored.Filled.ArrowBack
    ReaderIconToken.Chevron -> Icons.Filled.ChevronRight
    ReaderIconToken.ChevronDown -> Icons.Filled.ArrowDropDown
    ReaderIconToken.ViewList -> Icons.AutoMirrored.Filled.ViewList
    ReaderIconToken.Grid -> Icons.Filled.GridView
    ReaderIconToken.Add -> Icons.Filled.Add
    ReaderIconToken.Delete -> Icons.Filled.Delete
    ReaderIconToken.FileOpen -> Icons.Filled.FileOpen
    ReaderIconToken.FolderOff -> Icons.Filled.FolderOff
    ReaderIconToken.Warning -> Icons.Filled.ErrorOutline
    ReaderIconToken.Offline -> Icons.Filled.CloudOff
    ReaderIconToken.Permission -> Icons.Filled.Lock
}
