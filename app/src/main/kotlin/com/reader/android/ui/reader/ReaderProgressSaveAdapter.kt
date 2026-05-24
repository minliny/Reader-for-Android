package com.reader.android.ui.reader

/**
 * Saves/updates reading progress in-memory.
 * TODO: connect to Room ReadingProgressDao.upsert() when DI/runtime is available.
 */
class ReaderProgressSaveAdapter(
    private var state: ReaderProgressLocalStateAdapter = ReaderProgressLocalStateAdapter.Empty
) {
    val current: ReaderProgressLocalStateAdapter get() = state

    /** Save current chapter progress after content load or chapter switch. */
    fun saveChapter(
        chapterUrl: String,
        chapterTitle: String,
        chapterIndex: Int = 0,
        totalChapters: Int = 1,
        bookName: String = "",
        scrollPosition: Float = 0f
    ) {
        state = state.copy(
            currentChapterUrl = chapterUrl,
            currentChapterTitle = chapterTitle,
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            scrollPosition = scrollPosition,
            lastReadTime = System.currentTimeMillis()
        )
    }

    /** Update only scroll position (e.g., on scroll). */
    fun updatePosition(scrollPosition: Float) {
        state = state.copy(
            scrollPosition = scrollPosition.coerceIn(0f, 1f),
            lastReadTime = System.currentTimeMillis()
        )
    }

    /** Check if there's saved progress for a book. */
    fun hasProgress(): Boolean = state.currentChapterUrl != null

    /** Summary for Bookshelf / BookDetail continue-reading display. */
    fun continueReadingSummary(): String {
        if (!hasProgress()) return ""
        val ch = state.currentChapterTitle ?: return ""
        val pct = (state.scrollPosition * 100).toInt()
        return if (pct > 0) "$ch · $pct%" else ch
    }
}
