package com.reader.android.ui.search

import com.reader.android.ui.adapter.ReaderIntegrationLevel

data class SearchResultUiModel(
    val id: String,
    val title: String,
    val author: String,
    val sourceName: String,
    val category: String,
    val latestChapter: String,
    val intro: String,
    val detailTarget: String,
    val cover: String = "",
    val status: String = "",
    val isInBookshelf: Boolean = false,
    val actionLabel: String = "查看详情"
)

data class SearchUiState(
    val query: String,
    val results: List<SearchResultUiModel>,
    val isLoading: Boolean = false,
    val emptyMessage: String = "输入关键词开始搜索",
    val errorMessage: String? = null,
    val allowRealDataIntegration: Boolean = false,
    val boundaryLevel: ReaderIntegrationLevel = ReaderIntegrationLevel.NEEDS_ADAPTER
) {
    val isEmpty: Boolean get() = results.isEmpty() && !isLoading && errorMessage == null
}

object SearchUiStateMapper {
    fun fromFixture(
        query: String = SearchFixture.sampleQuery,
        results: List<SearchResultUiModel> = SearchFixture.results
    ): SearchUiState =
        SearchUiState(
            query = query,
            results = results,
            emptyMessage = "未找到匹配书籍"
        )

    fun loading(query: String): SearchUiState =
        SearchUiState(
            query = query,
            results = emptyList(),
            isLoading = true,
            emptyMessage = "正在搜索"
        )

    fun empty(query: String): SearchUiState =
        SearchUiState(
            query = query,
            results = emptyList(),
            emptyMessage = "未找到匹配书籍"
        )

    fun error(query: String, message: String): SearchUiState =
        SearchUiState(
            query = query,
            results = emptyList(),
            errorMessage = message,
            emptyMessage = "搜索失败"
        )
}

object SearchFixture {
    const val sampleQuery: String = "群山"

    val results: List<SearchResultUiModel> = listOf(
        SearchResultUiModel(
            id = "fixture-search-paper-mountain",
            title = "纸上群山",
            author = "南溪",
            sourceName = "UI Fixture",
            category = "幻想",
            latestChapter = "第十二章 星河",
            intro = "短小的搜索结果摘要，用于 UI 状态映射。",
            detailTarget = "fixture-detail-paper-mountain"
        ),
        SearchResultUiModel(
            id = "fixture-search-deep-space",
            title = "深空信号",
            author = "林间",
            sourceName = "UI Fixture",
            category = "科幻",
            latestChapter = "第八章 回声",
            intro = "离线 fixture，不代表生产数据。",
            detailTarget = "fixture-detail-deep-space"
        )
    )
}
