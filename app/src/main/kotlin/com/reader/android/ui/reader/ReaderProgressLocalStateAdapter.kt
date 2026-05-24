package com.reader.android.ui.reader

/**
 * In-memory progress state adapter. Backed by [ReaderTocLocalStateJoiner] for now.
 * TODO: connect to Room ReadingProgressDao when DI/runtime is available.
 */
data class ReaderProgressLocalStateAdapter(
    val currentChapterUrl: String? = null,
    val currentChapterTitle: String? = null,
    val chapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val scrollPosition: Float = 0f,
    val lastReadTime: Long = 0L
) {
    /** Check if a given chapter URL matches the current reading position. */
    fun isCurrentChapter(chapterUrl: String): Boolean =
        currentChapterUrl != null && currentChapterUrl == chapterUrl

    /** Progress value for a specific chapter (0-1). */
    fun progressFor(chapterUrl: String): Float =
        if (isCurrentChapter(chapterUrl)) scrollPosition.coerceIn(0f, 1f) else 0f

    companion object {
        val Empty = ReaderProgressLocalStateAdapter()
    }
}
