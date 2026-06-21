package com.reader.android.ui.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookCover
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderDivider
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderListItem
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.ReaderSettingsSwitchRow
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.RssArticleUiModel
import com.reader.android.ui.sync.RssFeedUiModel
import com.reader.android.ui.sync.RssListUiState
import com.reader.android.ui.sync.RssSubscriptionUiState
import com.reader.android.ui.theme.ReaderTheme

data class RssSource(
    val name: String,
    val updateCount: Int
)

data class RssDetail(
    val title: String,
    val description: String
)

data class RssHomeFilter(
    val label: String,
    val type: String,
    val active: Boolean = false
)

private val defaultStatusFilters = listOf(
    RssHomeFilter("全部", "all", active = true),
    RssHomeFilter("未读", "unread"),
    RssHomeFilter("收藏", "favorite"),
    RssHomeFilter("稍后读", "later"),
    RssHomeFilter("书单", "booklist")
)

private val defaultSourceFilters = listOf(
    RssHomeFilter("全部来源", "all", active = true),
    RssHomeFilter("小说更新", "novel"),
    RssHomeFilter("技术文章", "tech"),
    RssHomeFilter("书单推送", "booklist")
)

@Composable
fun RssHomeScreen(
    rssState: RssListUiState = DiscoverRssWebDavMapper.rssList(),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    rssHomeState: RssHomeDesignUiState? = null,
    statusFilters: List<RssHomeFilter> = defaultStatusFilters,
    sourceFilters: List<RssHomeFilter> = defaultSourceFilters,
    onRefresh: () -> Unit = {},
    onStatusFilterChange: (String) -> Unit = {},
    onSourceFilterChange: (String) -> Unit = {},
    onEntryClick: (RssArticleUiModel) -> Unit = {},
    onEntryMoreClick: (RssArticleUiModel) -> Unit = {},
    onAddSubscription: () -> Unit = {},
    onRetry: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    val effectiveRssState = rssHomeState?.rssState ?: rssState
    val effectiveStatusFilters = rssHomeState?.statusFilters ?: statusFilters
    val effectiveSourceFilters = rssHomeState?.sourceFilters ?: sourceFilters
    val activeStatusFilterType = effectiveStatusFilters.firstOrNull { it.active }?.type

    ReaderTheme {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "RSS",
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多",
                        onClick = onMoreClick
                    )
                }
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = ReaderTheme.spacing.lg)
            ) {
                item {
                    RssSummaryCard(
                        feedCount = effectiveRssState.summaryFeedCountLabel,
                        unreadCount = effectiveRssState.unreadCountLabel,
                        latestLabel = effectiveRssState.latestUpdateLabel,
                        onRefresh = onRefresh
                    )
                }
                item {
                    RssFilterSection(
                        title = "阅读状态",
                        filters = effectiveStatusFilters,
                        onFilterClick = onStatusFilterChange
                    )
                }
                item {
                    RssFilterSection(
                        title = "来源筛选",
                        filters = effectiveSourceFilters,
                        onFilterClick = onSourceFilterChange
                    )
                }
                item {
                    ReaderSectionHeader(title = "最新订阅")
                }
                when {
                    uiState is ReaderUiState.Loading || effectiveRssState.isLoading -> item {
                        ReaderLoadingState(
                            message = "订阅流刷新中",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                    uiState is ReaderUiState.Error -> item {
                        ReaderErrorState(
                            title = "订阅流加载失败",
                            message = uiState.message,
                            onRetryClick = if (uiState.retryable) onRetry else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                    effectiveRssState.offline -> item {
                        ReaderErrorState(
                            title = "当前离线",
                            message = effectiveRssState.error?.message ?: "保留上次订阅框架，可以稍后重试。",
                            onRetryClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                    effectiveRssState.error != null -> item {
                        ReaderErrorState(
                            title = effectiveRssState.error.title,
                            message = effectiveRssState.error.message,
                            onRetryClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                    effectiveRssState.articles.isEmpty() && activeStatusFilterType == "unread" -> item {
                        ReaderEmptyState(
                            title = "当前没有未读内容",
                            message = "筛选条件仍保留，可以切回全部查看历史订阅。",
                            actionText = "查看全部",
                            onActionClick = { onStatusFilterChange("all") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                    effectiveRssState.articles.isEmpty() -> item {
                        ReaderEmptyState(
                            title = "还没有订阅内容",
                            message = "添加订阅源后，最新内容会显示在这里。",
                            actionText = "添加订阅源",
                            onActionClick = onAddSubscription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                    else -> {
                        items(effectiveRssState.articles, key = { it.id }) { article ->
                            RssEntryRow(
                                article = article,
                                feedTitle = effectiveRssState.feeds.firstOrNull { it.id == article.feedId }?.title ?: article.feedId,
                                unread = true,
                                onClick = { onEntryClick(article) },
                                onMoreClick = { onEntryMoreClick(article) }
                            )
                            ReaderDivider()
                        }
                        item {
                            Text(
                                text = "已显示最新 ${effectiveRssState.visibleCountLabel} 条",
                                modifier = Modifier.padding(
                                    horizontal = ReaderTheme.spacing.screenPadding,
                                    vertical = ReaderTheme.spacing.sm
                                ),
                                color = ReaderTheme.colors.bodyText,
                                style = ReaderTheme.typography.bookMeta
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RssSummaryCard(
    feedCount: String,
    unreadCount: String,
    latestLabel: String,
    onRefresh: () -> Unit
) {
    ReaderCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.sm),
        contentDescription = "RSS 订阅概览"
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("订阅概览", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                    RssSummaryMetric(value = feedCount, label = "个订阅源")
                    RssSummaryMetric(value = unreadCount, label = "条未读")
                    RssSummaryMetric(value = latestLabel, label = "最近更新")
                }
            }
            ReaderSecondaryButton(text = "刷新", onClick = onRefresh)
        }
    }
}

@Composable
private fun RssSummaryMetric(value: String, label: String) {
    Column(modifier = Modifier.width(88.dp)) {
        Text(value, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.sectionTitle, maxLines = 1)
        Text(label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta, maxLines = 1)
    }
}

@Composable
private fun RssFilterSection(
    title: String,
    filters: List<RssHomeFilter>,
    onFilterClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = ReaderTheme.spacing.xs)) {
        ReaderSectionHeader(title = title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ReaderTheme.spacing.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            filters.forEach { filter ->
                ReaderChip(
                    text = filter.label,
                    selected = filter.active,
                    onClick = { onFilterClick(filter.type) }
                )
            }
        }
    }
}

@Composable
private fun RssEntryRow(
    article: RssArticleUiModel,
    feedTitle: String,
    unread: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    ReaderListItem(
        title = article.title,
        subtitle = "$feedTitle · ${article.publishedAt}\n${article.description}",
        contentDescription = "RSS 条目，${article.title}",
        onClick = onClick,
        leading = {
            BookCover(
                title = article.title,
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                contentDescription = "RSS 条目封面，${article.title}"
            )
        },
        trailing = {
            Row {
                if (unread) {
                    ReaderChip(text = "未读", selected = true)
                    Spacer(modifier = Modifier.width(ReaderTheme.spacing.xs))
                }
                ReaderIconButton(
                    icon = ReaderIconToken.More.asImageVector(),
                    contentDescription = "条目更多",
                    onClick = onMoreClick
                )
            }
        }
    )
}

@Composable
fun RssListScreen(
    sources: List<RssSource> = listOf(
        RssSource("订阅源 1", 2),
        RssSource("订阅源 2", 3),
        RssSource("订阅源 3", 4),
        RssSource("订阅源 4", 5)
    ),
    rssState: RssListUiState = DiscoverRssWebDavMapper.rssList(
        feeds = sources.mapIndexed { index, source ->
            RssFeedUiModel("rss-source-$index", source.name, source.updateCount, enabled = true)
        }
    ),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: (() -> Unit)? = null,
    onMoreClick: () -> Unit = {},
    onSourceClick: (Int) -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "RSS 列表",
                onNavigateBack = onBack,
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多",
                        onClick = onMoreClick
                    )
                }
            )
            when {
                rssState.isLoading -> ReaderLoadingState(message = "正在刷新 RSS", modifier = Modifier.weight(1f))
                rssState.offline -> ReaderErrorState(title = "当前离线", message = rssState.error?.message, modifier = Modifier.weight(1f))
                rssState.error != null -> ReaderErrorState(title = rssState.error.title, message = rssState.error.message, modifier = Modifier.weight(1f))
                rssState.isEmpty -> ReaderEmptyState(title = "暂无 RSS", message = rssState.emptyMessage, modifier = Modifier.weight(1f))
                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(rssState.feeds) { index, source ->
                    ReaderListItem(
                        title = source.title,
                        subtitle = "今日更新 ${source.updateCount} 篇",
                        onClick = { onSourceClick(index) }
                    )
                    ReaderDivider()
                }
                }
            }
            }
        }
    }
}
}

@Composable
fun RssDetailScreen(
    detail: RssDetail = RssDetail("深空信号更新", "来自订阅源的章节更新与说明。"),
    article: RssArticleUiModel = DiscoverRssWebDavMapper.rssList().articles.firstOrNull()
        ?: RssArticleUiModel("rss-detail-empty", "rss-feed-empty", detail.title, detail.description, "UI fixture"),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "RSS 详情",
                onNavigateBack = onBack,
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多",
                        onClick = onMoreClick
                    )
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(ReaderTheme.spacing.screenPadding)
            ) {
                ReaderCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        article.title,
                        color = ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.pageTitle
                    )
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                    Text(
                        article.description,
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.stateMessage
                    )
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                    Text(
                        article.publishedAt,
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.bookMeta
                    )
                }
            }
            }
        }
    }
}
}

data class RssSubscription(
    val name: String,
    val enabled: Boolean = true
)

@Composable
fun RssSubscriptionManagementScreen(
    subscriptions: List<RssSubscription> = listOf(
        RssSubscription("订阅源 1"),
        RssSubscription("订阅源 2"),
        RssSubscription("订阅源 3"),
        RssSubscription("订阅源 4")
    ),
    subscriptionState: RssSubscriptionUiState = DiscoverRssWebDavMapper.subscriptions(
        subscriptions.mapIndexed { index, subscription ->
            RssFeedUiModel("rss-subscription-$index", subscription.name, 0, subscription.enabled)
        }
    ),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onSubscriptionToggle: (Int, Boolean) -> Unit = { _, _ -> }
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "订阅管理",
                onNavigateBack = onBack,
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多",
                        onClick = onMoreClick
                    )
                }
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(subscriptionState.feeds) { index, sub ->
                    ReaderSettingsSwitchRow(
                        title = sub.title,
                        checked = sub.enabled,
                        onCheckedChange = { onSubscriptionToggle(index, it) }
                    )
                }
            }
            }
            }
        }
    }
}
