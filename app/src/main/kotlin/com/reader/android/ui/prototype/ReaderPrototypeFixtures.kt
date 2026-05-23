package com.reader.android.ui.prototype

import com.reader.android.ui.bookshelf.BookshelfLayoutMode
import com.reader.android.ui.bookshelf.BookshelfMapper
import com.reader.android.ui.detail.BookDetailUiStateMapper
import com.reader.android.ui.reader.ReaderRuntimeFixture
import com.reader.android.ui.search.SearchUiStateMapper
import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.ProgressSyncState

object ReaderPrototypeFixtures {
    val bookshelfCover = BookshelfMapper.fakeFallback(BookshelfLayoutMode.Cover)
    val bookshelfList = BookshelfMapper.fakeFallback(BookshelfLayoutMode.List)
    val bookshelfEmpty = BookshelfMapper.empty(BookshelfLayoutMode.Cover)

    val searchHome = SearchUiStateMapper.empty("")
    val searchResults = SearchUiStateMapper.fromFixture()
    val searchEmpty = SearchUiStateMapper.empty("没有这本书")
    val searchError = SearchUiStateMapper.error("群山", "搜索 fixture 错误")

    val bookDetail = BookDetailUiStateMapper.fromFixture()

    val discover = DiscoverRssWebDavMapper.discover()
    val rssList = DiscoverRssWebDavMapper.rssList()
    val rssSubscriptions = DiscoverRssWebDavMapper.subscriptions()

    val webDavNotConfigured = DiscoverRssWebDavMapper.webDavNotConfigured()
    val webDavConfigured = DiscoverRssWebDavMapper.webDavConfigured()
    val webDavAuthError = DiscoverRssWebDavMapper.webDavAuthError()
    val backupEnabled = DiscoverRssWebDavMapper.backup(enabled = true)
    val progressSyncConflict = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Conflict)
    val remoteBooks = DiscoverRssWebDavMapper.remoteBooks()

    val readerStates = listOf(
        ReaderRuntimeFixture.createBaseControlVisible(),
        ReaderRuntimeFixture.createSearchOverlay(),
        ReaderRuntimeFixture.createAutoScrollOverlay(),
        ReaderRuntimeFixture.createReplaceOverlay(),
        ReaderRuntimeFixture.createNightState(),
        ReaderRuntimeFixture.createDirectoryOverlay(),
        ReaderRuntimeFixture.createTtsOverlay(),
        ReaderRuntimeFixture.createAppearanceOverlay(),
        ReaderRuntimeFixture.createSettingsOverlay()
    )
}
