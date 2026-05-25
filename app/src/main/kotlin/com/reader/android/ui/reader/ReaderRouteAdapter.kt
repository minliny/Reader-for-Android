package com.reader.android.ui.reader

/**
 * Route adapter for opening the Reader from various contexts.
 * Hardens the route payload for Bookshelf, BookDetail, and Search result flows.
 * No network, no parser internals.
 */
data class ReaderRoutePayload(
    val contentUrl: String,
    val chapterTitle: String = "",
    val bookTitle: String = "",
    val sourceName: String = "默认来源",
    val chapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val hasProgress: Boolean = false
) {
    val isValid: Boolean get() = contentUrl.isNotBlank()

    companion object {
        val Empty = ReaderRoutePayload("")
    }
}

object ReaderRouteAdapter {

    /** Build payload from Bookshelf progress. */
    fun fromBookshelf(book: com.reader.android.ui.bookshelf.BookshelfBookUiModel): ReaderRoutePayload =
        ReaderRoutePayload(
            contentUrl = book.readerTarget,
            chapterTitle = book.currentChapterTitle,
            bookTitle = book.title,
            sourceName = book.sourceName,
            hasProgress = book.progress > 0f
        )

    /** Build payload from BookDetail after "continue reading" action. */
    fun fromDetail(detail: com.reader.android.ui.detail.BookDetailUiModel): ReaderRoutePayload =
        ReaderRoutePayload(
            contentUrl = detail.readerTarget.ifBlank { detail.detailTarget },
            chapterTitle = detail.currentChapter,
            bookTitle = detail.title,
            sourceName = detail.sourceName,
            hasProgress = detail.readingProgress > 0f
        )

    /** Build payload from search result — first-time open, TOC first chapter. */
    fun fromSearchResult(
        result: com.reader.android.ui.search.SearchResultUiModel,
        firstChapterUrl: String = ""
    ): ReaderRoutePayload =
        ReaderRoutePayload(
            contentUrl = firstChapterUrl.ifBlank { result.detailTarget },
            chapterTitle = result.latestChapter,
            bookTitle = result.title,
            sourceName = result.sourceName,
            hasProgress = false
        )

    /** Fallback with minimal info. */
    fun fallback(contentUrl: String, title: String = ""): ReaderRoutePayload =
        ReaderRoutePayload(contentUrl = contentUrl, bookTitle = title)
}
