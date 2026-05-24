package com.reader.android.ui.reader

/**
 * In-memory bookmark state adapter.
 * TODO: connect to Room BookmarkDao when DI/runtime is available.
 */
data class ReaderBookmarkLocalStateAdapter(
    val bookmarkedChapterUrls: Set<String> = emptySet()
) {
    fun isBookmarked(chapterUrl: String): Boolean =
        chapterUrl.isNotBlank() && chapterUrl in bookmarkedChapterUrls

    fun bookmarkedUrls(): Set<String> = bookmarkedChapterUrls

    companion object {
        val Empty = ReaderBookmarkLocalStateAdapter()
    }
}
