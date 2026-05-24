package com.reader.android.ui.search

import com.reader.android.data.model.SearchResultItem

/**
 * Maps Core DTO [SearchResultItem] → UI model [SearchResultUiModel].
 * Only depends on CoreBridge public DTO, never touches parser internals.
 */
object SearchFacadeResultMapper {

    fun map(item: SearchResultItem): SearchResultUiModel = SearchResultUiModel(
        id = stableId(item.detailUrl),
        title = item.name,
        author = item.author.ifBlank { "未知作者" },
        sourceName = item.sourceName ?: "未知书源",
        category = item.kind ?: "",
        latestChapter = item.latestChapter ?: "",
        intro = item.intro ?: "",
        detailTarget = item.detailUrl ?: "",
        cover = item.coverUrl ?: "",
        status = item.wordCount ?: "",
        isInBookshelf = false, // TODO: query local bookshelf repository when available
        actionLabel = "查看详情"
    )

    fun mapList(items: List<SearchResultItem>): List<SearchResultUiModel> =
        items.map { map(it) }

    // Deterministic id from detailUrl hash — stable across JVM invocations
    private fun stableId(url: String?): String {
        val source = url ?: return "search-unknown"
        return "search-${source.hashCode()}"
    }
}
