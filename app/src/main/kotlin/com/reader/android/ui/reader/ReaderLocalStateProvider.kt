package com.reader.android.ui.reader

/**
 * Unified local state provider combining progress, cache, and bookmark adapters.
 * Single entry point for all Reader local state queries.
 */
data class ReaderLocalStateProvider(
    val progress: ReaderProgressLocalStateAdapter = ReaderProgressLocalStateAdapter.Empty,
    val cache: ReaderCacheLocalStateAdapter = ReaderCacheLocalStateAdapter.Empty,
    val bookmark: ReaderBookmarkLocalStateAdapter = ReaderBookmarkLocalStateAdapter.Empty
) {
    /** Create a TOC joiner pre-configured with this provider's state. */
    fun tocJoiner(): ReaderTocLocalStateJoiner = ReaderTocLocalStateJoiner(
        progress = progress,
        bookmarkedUrls = bookmark.bookmarkedUrls()
    )

    /** Create a content joiner pre-configured with cache state. */
    fun contentJoiner(): ReaderContentLocalStateJoiner = ReaderContentLocalStateJoiner(
        cache = cache
    )

    /** Join TOC entries with all local state. */
    fun joinToc(entries: List<ReaderTocEntryUiModel>): List<ReaderTocEntryUiModel> =
        tocJoiner().join(entries)

    companion object {
        val Empty = ReaderLocalStateProvider()
    }
}
