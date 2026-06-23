package com.reader.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
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
    Permission,
    Directory,
    Refresh,
    SourceSwitch,
    AutoBrightness,
    ChevronLeft,
    AutoScroll,
    ContentReplace,
    NightMode,
    Tts,
    Appearance,
    ReadingSettings,
    Bookmark,
    CurrentLocation,
    Close
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
    ReaderIconToken.Directory -> Icons.AutoMirrored.Filled.MenuBook
    ReaderIconToken.Refresh -> Icons.Filled.Refresh
    ReaderIconToken.SourceSwitch -> Icons.Filled.SwapHoriz
    ReaderIconToken.AutoBrightness -> Icons.Filled.BrightnessAuto
    ReaderIconToken.ChevronLeft -> Icons.Filled.ChevronLeft
    ReaderIconToken.AutoScroll -> Icons.Filled.AutoMode
    ReaderIconToken.ContentReplace -> Icons.Filled.SwapHoriz
    ReaderIconToken.NightMode -> Icons.Filled.DarkMode
    ReaderIconToken.Tts -> Icons.Filled.RecordVoiceOver
    ReaderIconToken.Appearance -> Icons.Filled.Tune
    ReaderIconToken.ReadingSettings -> Icons.Filled.Settings
    ReaderIconToken.Bookmark -> Icons.Filled.Bookmark
    ReaderIconToken.CurrentLocation -> Icons.Filled.MyLocation
    ReaderIconToken.Close -> Icons.Filled.Close
}
