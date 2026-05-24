package com.reader.android.ui.bookshelf

import com.reader.android.ui.reader.ReaderBookmarkLocalStateAdapter
import com.reader.android.ui.reader.ReaderCacheLocalStateAdapter
import com.reader.android.ui.reader.ReaderProgressSaveAdapter

/**
 * Joins Reader local state (progress, cache, bookmark) into Bookshelf book models.
 * In-memory only. TODO: connect to Room DAOs when DI/runtime available.
 */
object BookshelfLocalStateJoiner {

    /** Build a bookshelf entry from saved progress. */
    fun fromProgress(
        progress: ReaderProgressSaveAdapter,
        cache: ReaderCacheLocalStateAdapter = ReaderCacheLocalStateAdapter.Empty,
        bookmark: ReaderBookmarkLocalStateAdapter = ReaderBookmarkLocalStateAdapter.Empty,
        bookUrl: String = "",
        bookName: String = "",
        author: String? = null,
        sourceName: String = "默认来源"
    ): BookshelfBookUiModel {
        val p = progress.current
        val hasBookmark = bookmark.bookmarkedUrls().isNotEmpty()
        return BookshelfBookUiModel(
            id = bookUrl.ifBlank { "unknown" },
            title = bookName.ifBlank { "未知书名" },
            author = author,
            sourceName = sourceName,
            currentChapterTitle = p.currentChapterTitle ?: "未开始阅读",
            progress = progressPercent(p.chapterIndex, p.totalChapters, p.scrollPosition),
            cacheState = if (p.currentChapterUrl != null &&
                cache.statusFor(p.currentChapterUrl!!) == ReaderCacheLocalStateAdapter.CacheStatus.CACHED
            ) BookshelfCacheState.Cached else BookshelfCacheState.None,
            detailTarget = bookUrl,
            readerTarget = p.currentChapterUrl ?: bookUrl
        )
    }

    private fun progressPercent(chapterIndex: Int, totalChapters: Int, scrollPosition: Float): Float {
        if (totalChapters <= 0) return scrollPosition.coerceIn(0f, 1f)
        val chapterBase = chapterIndex.coerceAtLeast(0).toFloat() / totalChapters
        val chapterSize = 1f / totalChapters.toFloat()
        return (chapterBase + scrollPosition.coerceIn(0f, 1f) * chapterSize).coerceIn(0f, 1f)
    }
}
