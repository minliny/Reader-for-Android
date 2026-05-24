package com.reader.android.ui.detail

import com.reader.android.data.model.BookInfo
import com.reader.android.data.model.TOCItem

/**
 * Maps Core DTO [BookInfo] → UI model [BookDetailUiModel].
 * Only depends on CoreBridge public DTOs, never touches parser internals.
 */
object BookDetailFacadeResultMapper {

    fun map(
        bookInfo: BookInfo,
        detailTarget: String,
        tocItems: List<TOCItem> = emptyList()
    ): BookDetailUiModel = BookDetailUiModel(
        id = stableId(detailTarget),
        title = bookInfo.name,
        author = bookInfo.author.ifBlank { "未知作者" },
        sourceName = bookInfo.origin ?: "未知书源",
        category = bookInfo.kind ?: "",
        wordCount = bookInfo.wordCount ?: "",
        latestChapter = bookInfo.latestChapter ?: "",
        updateTime = bookInfo.updateTime ?: "",
        intro = bookInfo.intro ?: "",
        cover = bookInfo.coverUrl ?: "",
        currentChapter = "", // TODO: query local ReadingProgress when available
        readingProgress = 0f, // TODO: query local ReadingProgress when available
        isInBookshelf = false, // TODO: query local BookshelfRepository when available
        cacheStatus = "未缓存", // TODO: query ChapterCache when available
        availableActions = availableActions(isInBookshelf = false),
        detailTarget = detailTarget,
        readerTarget = bookInfo.tocUrl ?: detailTarget,
        tocPreview = tocPreviewFrom(tocItems, bookInfo.tocUrl ?: detailTarget)
    )

    private fun stableId(key: String): String {
        return "detail-${key.hashCode()}"
    }

    private fun availableActions(isInBookshelf: Boolean): List<String> =
        listOf("开始阅读", "查看目录") +
            (if (isInBookshelf) listOf("移出书架") else listOf("加入书架"))

    private fun tocPreviewFrom(items: List<TOCItem>, tocTarget: String): BookDetailTocPreviewUiModel {
        val chapters = flattenChapters(items)
        return BookDetailTocPreviewUiModel(
            chapterCount = chapters.size,
            firstChapterTitle = chapters.firstOrNull()?.title ?: "",
            latestChapterTitle = chapters.lastOrNull()?.title ?: "",
            tocTarget = tocTarget
        )
    }

    /** Flatten nested TOC items for preview count. */
    private fun flattenChapters(items: List<TOCItem>): List<TOCItem> {
        val result = mutableListOf<TOCItem>()
        for (item in items) {
            if (item.url.isNotBlank()) result.add(item)
            result.addAll(flattenChapters(item.children))
        }
        return result
    }
}
