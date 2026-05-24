package com.reader.android.ui.reader

/**
 * Joins local reading state (current chapter, bookmarks, progress) into TOC entries.
 * All state is in-memory only — no repository/database access in this slice.
 *
 * TODO: Replace with real ReadingProgress / BookmarkStorage repository when available.
 */
data class ReaderTocLocalStateJoiner(
    val currentChapterUrl: String? = null,
    val bookmarkedUrls: Set<String> = emptySet(),
    val chapterProgress: Map<String, Float> = emptyMap()
) {
    fun join(entries: List<ReaderTocEntryUiModel>): List<ReaderTocEntryUiModel> =
        entries.map { entry -> joinOne(entry) }

    private fun joinOne(entry: ReaderTocEntryUiModel): ReaderTocEntryUiModel {
        // TODO: match by chapterUrl when ReaderTocEntryUiModel has a url field
        return entry.copy(
            isCurrent = false,  // default — real match needs chapterUrl field
            hasBookmark = false, // default — real match needs chapterUrl field
            progress = null      // default — real lookup needs chapterUrl field
        )
    }
}
