package com.reader.android.data.network

data class ExploreSource(
    val id: String,
    val name: String,
    val category: String,
    val feedUrl: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

data class ExploreCategory(
    val name: String,
    val sources: List<ExploreSource> = emptyList()
)

object ExploreMapping {
    fun groupByCategory(sources: List<ExploreSource>): List<ExploreCategory> {
        return sources.groupBy { it.category }.map { (category, srcs) ->
            ExploreCategory(name = category, sources = srcs.sortedBy { it.sortOrder })
        }
    }
}
