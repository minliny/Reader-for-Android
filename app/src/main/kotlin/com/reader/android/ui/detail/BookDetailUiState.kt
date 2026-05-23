package com.reader.android.ui.detail

import com.reader.android.ui.adapter.ReaderIntegrationLevel

data class BookDetailTocPreviewUiModel(
    val chapterCount: Int,
    val firstChapterTitle: String,
    val latestChapterTitle: String,
    val tocTarget: String
)

data class BookDetailUiModel(
    val id: String,
    val title: String,
    val author: String,
    val sourceName: String,
    val category: String,
    val wordCount: String,
    val latestChapter: String,
    val updateTime: String,
    val intro: String,
    val detailTarget: String,
    val readerTarget: String,
    val tocPreview: BookDetailTocPreviewUiModel
)

data class BookDetailUiState(
    val detail: BookDetailUiModel?,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emptyMessage: String = "暂无书籍详情",
    val allowRealDataIntegration: Boolean = false,
    val boundaryLevel: ReaderIntegrationLevel = ReaderIntegrationLevel.NEEDS_ADAPTER
) {
    val hasContent: Boolean get() = detail != null && !isLoading && errorMessage == null
}

object BookDetailUiStateMapper {
    fun fromFixture(detail: BookDetailUiModel = BookDetailFixture.detail): BookDetailUiState =
        BookDetailUiState(detail = detail)

    fun loading(): BookDetailUiState =
        BookDetailUiState(
            detail = null,
            isLoading = true,
            emptyMessage = "正在加载书籍详情"
        )

    fun empty(): BookDetailUiState =
        BookDetailUiState(detail = null)

    fun error(message: String): BookDetailUiState =
        BookDetailUiState(
            detail = null,
            errorMessage = message,
            emptyMessage = "书籍详情加载失败"
        )
}

object BookDetailFixture {
    val tocPreview = BookDetailTocPreviewUiModel(
        chapterCount = 12,
        firstChapterTitle = "第一章 雨线",
        latestChapterTitle = "第十二章 星河",
        tocTarget = "fixture-toc-paper-mountain"
    )

    val detail = BookDetailUiModel(
        id = "fixture-detail-paper-mountain",
        title = "纸上群山",
        author = "南溪",
        sourceName = "UI Fixture",
        category = "幻想",
        wordCount = "18 万字",
        latestChapter = tocPreview.latestChapterTitle,
        updateTime = "UI fixture",
        intro = "这是用于详情页状态映射的短小 fixture，不代表生产数据。",
        detailTarget = "fixture-detail-paper-mountain",
        readerTarget = "fixture-reader-paper-mountain",
        tocPreview = tocPreview
    )
}
