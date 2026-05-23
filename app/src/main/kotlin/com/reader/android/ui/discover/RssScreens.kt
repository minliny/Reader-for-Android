package com.reader.android.ui.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderDivider
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderListItem
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderSettingsSwitchRow
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
    onBack: () -> Unit = {},
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
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk)
                    }
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
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk)
                    }
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
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk)
                    }
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
