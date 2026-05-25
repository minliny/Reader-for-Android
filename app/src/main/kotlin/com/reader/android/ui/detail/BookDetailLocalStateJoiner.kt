package com.reader.android.ui.detail

import com.reader.android.ui.reader.ReaderBookmarkLocalStateAdapter
import com.reader.android.ui.reader.ReaderCacheLocalStateAdapter
import com.reader.android.ui.reader.ReaderProgressLocalStateAdapter

/**
 * Enriches BookDetailUiModel with Reader local state (progress, cache, bookmark).
 * In-memory only. TODO: connect to Room DAOs when DI/runtime available.
 */
object BookDetailLocalStateJoiner {

    fun enrich(
        detail: BookDetailUiModel,
        progress: ReaderProgressLocalStateAdapter = ReaderProgressLocalStateAdapter.Empty,
        cache: ReaderCacheLocalStateAdapter = ReaderCacheLocalStateAdapter.Empty,
        bookmark: ReaderBookmarkLocalStateAdapter = ReaderBookmarkLocalStateAdapter.Empty,
        chapterUrl: String = ""
    ): BookDetailUiModel = detail.copy(
        currentChapter = progress.currentChapterTitle ?: detail.currentChapter,
        readingProgress = if (progress.currentChapterUrl != null) progress.scrollPosition else detail.readingProgress,
        cacheStatus = cacheStatusText(chapterUrl, cache),
        isInBookshelf = detail.isInBookshelf,
        availableActions = buildActions(detail.isInBookshelf, progress.currentChapterUrl != null)
    )

    private fun cacheStatusText(chapterUrl: String, cache: ReaderCacheLocalStateAdapter): String =
        when (cache.statusFor(chapterUrl)) {
            ReaderCacheLocalStateAdapter.CacheStatus.CACHED -> "已缓存"
            ReaderCacheLocalStateAdapter.CacheStatus.STALE -> "缓存过期"
            ReaderCacheLocalStateAdapter.CacheStatus.NOT_CACHED -> "未缓存"
        }

    private fun buildActions(isInBookshelf: Boolean, hasProgress: Boolean): List<String> =
        mutableListOf<String>().apply {
            if (hasProgress) add("继续阅读") else add("开始阅读")
            add("查看目录")
            if (isInBookshelf) add("移出书架") else add("加入书架")
        }
}
