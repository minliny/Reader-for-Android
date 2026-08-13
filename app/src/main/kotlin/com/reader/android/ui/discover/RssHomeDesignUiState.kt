package com.reader.android.ui.discover

import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.RssListUiState
import com.reader.android.ui.sync.SyncErrorUiState

enum class RssHomeDisplayState {
    Default,
    Loading,
    Empty,
    UnreadEmpty,
    Error
}

data class RssHomeDesignUiState(
    val rssState: RssListUiState,
    val statusFilters: List<RssHomeFilter>,
    val sourceFilters: List<RssHomeFilter>,
    val bottomNavLabels: List<String> = listOf("书架", "发现", "RSS", "设置"),
    val displayState: RssHomeDisplayState = RssHomeDisplayState.Default
)

object RssHomeDesignMapper {
    fun fromFixture(): RssHomeDesignUiState =
        RssHomeDesignUiState(
            rssState = DiscoverRssWebDavMapper.rssList(),
            statusFilters = statusFilters(activeType = "all"),
            sourceFilters = sourceFilters(activeType = "all")
        )

    fun loading(): RssHomeDesignUiState =
        fromFixture().copy(
            displayState = RssHomeDisplayState.Loading,
            rssState = fromFixture().rssState.copy(
                articles = emptyList(),
                latestUpdateLabel = "刷新中",
                isLoading = true
            )
        )

    fun empty(): RssHomeDesignUiState =
        fromFixture().copy(
            displayState = RssHomeDisplayState.Empty,
            rssState = RssListUiState(
                feeds = emptyList(),
                articles = emptyList(),
                summaryFeedCountLabel = "0",
                unreadCountLabel = "0",
                latestUpdateLabel = "暂无更新",
                visibleCountLabel = "0",
                emptyMessage = "还没有订阅内容"
            )
        )

    fun unreadEmpty(): RssHomeDesignUiState =
        fromFixture().copy(
            displayState = RssHomeDisplayState.UnreadEmpty,
            statusFilters = statusFilters(activeType = "unread"),
            rssState = fromFixture().rssState.copy(
                articles = emptyList(),
                unreadCountLabel = "0",
                visibleCountLabel = "0"
            )
        )

    fun error(): RssHomeDesignUiState =
        fromFixture().copy(
            displayState = RssHomeDisplayState.Error,
            rssState = fromFixture().rssState.copy(
                articles = emptyList(),
                latestUpdateLabel = "加载失败",
                visibleCountLabel = "0",
                error = SyncErrorUiState(
                    title = "订阅流加载失败",
                    message = "保留上次订阅框架，可以重试刷新。"
                )
            )
        )

    private fun statusFilters(activeType: String): List<RssHomeFilter> =
        listOf(
            RssHomeFilter("全部", "all"),
            RssHomeFilter("未读", "unread"),
            RssHomeFilter("收藏", "favorite"),
            RssHomeFilter("稍后读", "later"),
            RssHomeFilter("书单", "booklist")
        ).map { filter -> filter.copy(active = filter.type == activeType) }

    private fun sourceFilters(activeType: String): List<RssHomeFilter> =
        listOf(
            RssHomeFilter("全部来源", "all"),
            RssHomeFilter("小说更新", "novel"),
            RssHomeFilter("技术文章", "tech"),
            RssHomeFilter("书单推送", "booklist")
        ).map { filter -> filter.copy(active = filter.type == activeType) }
}
