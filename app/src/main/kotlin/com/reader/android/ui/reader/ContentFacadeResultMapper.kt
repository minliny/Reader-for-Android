package com.reader.android.ui.reader

import com.reader.android.data.model.ContentPage

/**
 * Maps Core [ContentPage] → UI [ReaderContentUiModel] + [ReaderChapterUiModel].
 */
object ContentFacadeResultMapper {

    fun mapContent(page: ContentPage): ReaderContentUiModel = ReaderContentUiModel(
        text = page.content,
        prevPageAvailable = false, // filled by mapChapterContext
        nextPageAvailable = page.nextPageUrl != null
    )

    fun mapChapter(page: ContentPage, request: ContentRequest): ReaderChapterUiModel =
        ReaderChapterUiModel(
            chapterTitle = page.title?.ifBlank { null } ?: request.chapterTitle.ifBlank { "未命名章节" },
            chapterIndex = request.chapterIndex,
            totalChapters = request.totalChapters
        )

    /** Enrich content model with TOC context (prev/next chapter availability). */
    fun applyChapterContext(
        content: ReaderContentUiModel,
        request: ContentRequest
    ): ReaderContentUiModel = content.copy(
        prevPageAvailable = request.hasPrevChapter,
        nextPageAvailable = request.hasNextChapter || content.nextPageAvailable
    )

    /** Split content text into non-empty paragraphs. */
    fun paragraphs(content: String): List<String> =
        content.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}
