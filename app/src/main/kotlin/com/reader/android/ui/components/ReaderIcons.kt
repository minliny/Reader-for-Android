package com.reader.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
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
    Folder,
    FolderOff,
    Badge,
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
    Close,
    Sort,
    People,
    Clock,
    List,
    Trash,
    Bell,
    Battery,
    EyeOff,
    Info,
    Shield,
    Bug,
    Storage,
    Download,
    Image,
    Check,
    Link,
    Message,
    Code,
    Help
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
    ReaderIconToken.Folder -> Icons.Filled.Folder
    ReaderIconToken.FolderOff -> Icons.Filled.FolderOff
    ReaderIconToken.Badge -> Icons.Filled.Bookmark
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
    ReaderIconToken.Sort -> Icons.AutoMirrored.Filled.Sort
    ReaderIconToken.People -> Icons.Filled.Groups
    ReaderIconToken.Clock -> Icons.Filled.History
    ReaderIconToken.List -> Icons.AutoMirrored.Filled.ViewList
    ReaderIconToken.Trash -> Icons.Filled.Delete
    ReaderIconToken.Bell -> Icons.Filled.Notifications
    ReaderIconToken.Battery -> Icons.Filled.BatterySaver
    ReaderIconToken.EyeOff -> Icons.Filled.VisibilityOff
    ReaderIconToken.Info -> Icons.Filled.Info
    ReaderIconToken.Shield -> Icons.Filled.Shield
    ReaderIconToken.Bug -> Icons.Filled.BugReport
    ReaderIconToken.Storage -> Icons.Filled.Storage
    ReaderIconToken.Download -> Icons.Filled.Download
    ReaderIconToken.Image -> Icons.Filled.Image
    ReaderIconToken.Check -> Icons.Filled.CheckCircle
    ReaderIconToken.Link -> Icons.Filled.Link
    ReaderIconToken.Message -> Icons.Filled.ChatBubbleOutline
    ReaderIconToken.Code -> Icons.Filled.Code
    ReaderIconToken.Help -> Icons.AutoMirrored.Filled.HelpOutline
}
