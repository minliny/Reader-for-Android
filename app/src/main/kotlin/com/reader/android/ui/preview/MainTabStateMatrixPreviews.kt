package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.bookshelf.BookshelfHomeMapper
import com.reader.android.ui.bookshelf.BookshelfScreen
import com.reader.android.ui.discover.DiscoverScreen
import com.reader.android.ui.discover.DiscoveryHomeMapper
import com.reader.android.ui.discover.RssHomeDesignMapper
import com.reader.android.ui.discover.RssHomeScreen
import com.reader.android.ui.settings.SettingsHomeMapper
import com.reader.android.ui.settings.SettingsRootScreen

private const val PreviewWidth = 390
private const val PreviewHeight = 844

@Preview(name = "MainTab / Bookshelf / Default", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun BookshelfMainTabDefaultPreview() {
    BookshelfScreen(bookshelfHomeState = BookshelfHomeMapper.fromFixture())
}

@Preview(name = "MainTab / Bookshelf / Filtering", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun BookshelfMainTabFilteringPreview() {
    BookshelfScreen(bookshelfHomeState = BookshelfHomeMapper.filtering())
}

@Preview(name = "MainTab / Bookshelf / Loading", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun BookshelfMainTabLoadingPreview() {
    BookshelfScreen(bookshelfHomeState = BookshelfHomeMapper.loading())
}

@Preview(name = "MainTab / Bookshelf / Empty", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun BookshelfMainTabEmptyPreview() {
    BookshelfScreen(bookshelfHomeState = BookshelfHomeMapper.empty())
}

@Preview(name = "MainTab / Discover / Default", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun DiscoverMainTabDefaultPreview() {
    DiscoverScreen(discoveryHomeState = DiscoveryHomeMapper.fromFixture())
}

@Preview(name = "MainTab / Discover / Subscription", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun DiscoverMainTabSubscriptionPreview() {
    DiscoverScreen(discoveryHomeState = DiscoveryHomeMapper.subscription())
}

@Preview(name = "MainTab / Discover / Loading", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun DiscoverMainTabLoadingPreview() {
    DiscoverScreen(discoveryHomeState = DiscoveryHomeMapper.loading())
}

@Preview(name = "MainTab / Discover / Empty", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun DiscoverMainTabEmptyPreview() {
    DiscoverScreen(discoveryHomeState = DiscoveryHomeMapper.empty())
}

@Preview(name = "MainTab / Discover / Error", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun DiscoverMainTabErrorPreview() {
    DiscoverScreen(discoveryHomeState = DiscoveryHomeMapper.error())
}

@Preview(name = "MainTab / Discover / Offline", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun DiscoverMainTabOfflinePreview() {
    DiscoverScreen(discoveryHomeState = DiscoveryHomeMapper.offline())
}

@Preview(name = "MainTab / RSS / Default", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabDefaultPreview() {
    RssHomeScreen(rssHomeState = RssHomeDesignMapper.fromFixture())
}

@Preview(name = "MainTab / RSS / Loading", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabLoadingPreview() {
    RssHomeScreen(rssHomeState = RssHomeDesignMapper.loading())
}

@Preview(name = "MainTab / RSS / Empty", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabEmptyPreview() {
    RssHomeScreen(rssHomeState = RssHomeDesignMapper.empty())
}

@Preview(name = "MainTab / RSS / Unread Empty", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabUnreadEmptyPreview() {
    RssHomeScreen(rssHomeState = RssHomeDesignMapper.unreadEmpty())
}

@Preview(name = "MainTab / RSS / Error", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun RssMainTabErrorPreview() {
    RssHomeScreen(rssHomeState = RssHomeDesignMapper.error())
}

@Preview(name = "MainTab / Settings / Default", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabDefaultPreview() {
    SettingsRootScreen(settingsState = SettingsHomeMapper.fromFixture())
}

@Preview(name = "MainTab / Settings / Loading Overview", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabLoadingOverviewPreview() {
    SettingsRootScreen(settingsState = SettingsHomeMapper.loadingOverview())
}

@Preview(name = "MainTab / Settings / No Backup", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabNoBackupPreview() {
    SettingsRootScreen(settingsState = SettingsHomeMapper.noBackup())
}

@Preview(name = "MainTab / Settings / Permission Needed", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
fun SettingsMainTabPermissionNeededPreview() {
    SettingsRootScreen(settingsState = SettingsHomeMapper.permissionNeeded())
}
