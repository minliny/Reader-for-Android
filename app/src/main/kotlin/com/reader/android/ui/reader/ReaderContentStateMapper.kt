package com.reader.android.ui.reader

/**
 * Maps [ContentAdapterShell.ContentResult] → [ReaderRuntimeUiState] updates.
 * Also provides ContentRequest construction from TOC entries.
 */
object ReaderContentStateMapper {

    fun applyContent(state: ReaderRuntimeUiState, result: ContentAdapterShell.ContentResult): ReaderRuntimeUiState =
        when (result) {
            is ContentAdapterShell.ContentResult.Loading ->
                state.copy(contentState = ReaderContentState.Loading)

            is ContentAdapterShell.ContentResult.Ready ->
                state.copy(
                    content = result.content,
                    chapter = result.chapter,
                    pageProgress = state.pageProgress.copy(progress = 0f),
                    contentState = ReaderContentState.Ready(result.content, result.chapter)
                )

            is ContentAdapterShell.ContentResult.Error ->
                state.copy(
                    contentState = ReaderContentState.Error(
                        message = result.error.message,
                        retryable = result.error.retryable
                    )
                )
        }

    /** Build ContentRequest from a TOC entry + book/chapter context. */
    fun contentRequest(
        entry: ReaderTocEntryUiModel,
        entries: List<ReaderTocEntryUiModel>,
        book: ReaderBookUiModel
    ): ContentRequest {
        val index = entries.indexOfFirst { it.url == entry.url }
        return ContentRequest(
            chapterUrl = entry.url,
            chapterTitle = entry.title,
            chapterIndex = if (index >= 0) index else 0,
            totalChapters = entries.size,
            bookTitle = book.bookTitle,
            sourceName = book.sourceName
        )
    }
}
