package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.bookshelf.BookshelfLayoutMode
import com.reader.android.ui.bookshelf.BookshelfMapper
import com.reader.android.ui.bookshelf.BookshelfScreen
import com.reader.android.ui.discover.RssHomeFilter
import com.reader.android.ui.discover.RssHomeScreen
import com.reader.android.ui.settings.SettingsHomeDisplayState
import com.reader.android.ui.settings.SettingsHomeState
import com.reader.android.ui.settings.SettingsRootScreen
import com.reader.android.ui.sync.DiscoverRssWebDavMapper

private const val PreviewWidth = 390
private const val PreviewHeight = 844

@Preview(name = "MainTab / Bookshelf / Default", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun BookshelfMainTabDefaultPreview() {
    BookshelfScreen(bookshelfState = BookshelfMapper.fakeFallback(BookshelfLayoutMode.Cover))
}

@Preview(name = "MainTab / Bookshelf / Empty", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun BookshelfMainTabEmptyPreview() {
    BookshelfScreen(bookshelfState = BookshelfMapper.empty(BookshelfLayoutMode.Cover))
}

@Preview(name = "MainTab / RSS / Default", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabDefaultPreview() {
    RssHomeScreen(rssState = DiscoverRssWebDavMapper.rssList())
}

@Preview(name = "MainTab / RSS / Loading", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabLoadingPreview() {
    RssHomeScreen(rssState = DiscoverRssWebDavMapper.rssLoading())
}

@Preview(name = "MainTab / RSS / Empty", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabEmptyPreview() {
    RssHomeScreen(rssState = DiscoverRssWebDavMapper.rssEmpty())
}

@Preview(name = "MainTab / RSS / Unread Empty", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabUnreadEmptyPreview() {
    RssHomeScreen(
        rssState = DiscoverRssWebDavMapper.rssEmpty(),
        statusFilters = listOf(
            RssHomeFilter("全部", "all"),
            RssHomeFilter("未读", "unread", active = true),
            RssHomeFilter("收藏", "favorite"),
            RssHomeFilter("稍后读", "later"),
            RssHomeFilter("书单", "booklist")
        )
    )
}

@Preview(name = "MainTab / Settings / Default", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabDefaultPreview() {
    SettingsRootScreen(settingsState = SettingsHomeState())
}

@Preview(name = "MainTab / Settings / Loading Overview", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabLoadingOverviewPreview() {
    SettingsRootScreen(settingsState = SettingsHomeState(displayState = SettingsHomeDisplayState.LoadingOverview))
}

@Preview(name = "MainTab / Settings / No Backup", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabNoBackupPreview() {
    SettingsRootScreen(settingsState = SettingsHomeState(displayState = SettingsHomeDisplayState.NoBackup))
}

@Preview(name = "MainTab / Settings / Permission Needed", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabPermissionNeededPreview() {
    SettingsRootScreen(settingsState = SettingsHomeState(displayState = SettingsHomeDisplayState.PermissionNeeded))
}
