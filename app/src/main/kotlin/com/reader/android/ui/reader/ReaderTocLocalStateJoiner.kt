package com.reader.android.ui.reader

/**
 * Joins local reading state (current chapter, bookmarks, progress) into TOC entries.
 * Uses [ReaderProgressLocalStateAdapter] for progress matching.
 * TODO: Connect to Room DAOs and BookmarkStorage when DI/runtime available.
 */
data class ReaderTocLocalStateJoiner(
    val progress: ReaderProgressLocalStateAdapter = ReaderProgressLocalStateAdapter.Empty,
    val bookmarkedUrls: Set<String> = emptySet()
) {
    fun join(entries: List<ReaderTocEntryUiModel>): List<ReaderTocEntryUiModel> =
        entries.map { entry -> joinOne(entry) }

    private fun joinOne(entry: ReaderTocEntryUiModel): ReaderTocEntryUiModel {
        val url = entry.url
        return entry.copy(
            isCurrent = url.isNotBlank() && progress.isCurrentChapter(url),
            hasBookmark = url.isNotBlank() && url in bookmarkedUrls,
            progress = if (url.isNotBlank()) progress.progressFor(url) else null
        )
    }
}
