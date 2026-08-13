package com.reader.android.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookCard
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSearchBox
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.sync.DiscoverItemUiModel
import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.DiscoverUiState
import com.reader.android.ui.theme.ReaderTheme

private val DiscoveryGoodColor = Color(0xFF1F7A4D)
private val DiscoveryWarnColor = Color(0xFFB54708)

data class DiscoverBook(
    val title: String,
    val author: String,
    val progress: Float? = null
)

@Composable
fun DiscoverScreen(
    books: List<DiscoverBook> = listOf(
        DiscoverBook("深空信号", "科幻 · 本地书籍", 0.25f),
        DiscoverBook("纸上群山", "幻想 · 本地书籍", 0.6f),
        DiscoverBook("雨线手记", "随笔 · UI fixture", null)
    ),
    discoverState: DiscoverUiState = DiscoverRssWebDavMapper.discover(
        books.mapIndexed { index, book ->
            DiscoverItemUiModel(
                id = "discover-$index",
                title = book.title,
                subtitle = book.author,
                progress = book.progress
            )
        }
    ),
    discoveryHomeState: DiscoveryHomeUiState = DiscoveryHomeMapper.fromFixture(),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onMoreClick: () -> Unit = {},
    onSearchOpen: () -> Unit = {},
    onSourceTypeChange: (DiscoverySourceTypeUiModel) -> Unit = {},
    onSourceSwitchOpen: () -> Unit = {},
    onCategoryChange: (DiscoveryCategoryUiModel) -> Unit = {},
    onMoreCategoryOpen: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onBookClick: (Int) -> Unit = {},
    onBookDetailOpen: (DiscoveryBookUiModel) -> Unit = {},
    onAddToBookshelf: (DiscoveryBookUiModel) -> Unit = {},
    onRead: (DiscoveryBookUiModel) -> Unit = {},
    onSourceDetect: () -> Unit = {},
    onRankingMore: () -> Unit = {},
    onRssClick: () -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> DiscoverHomeContent(
                state = discoveryHomeState,
                modifier = modifier,
                legacyDiscoverState = discoverState,
                onMoreClick = onMoreClick,
                onSearchOpen = onSearchOpen,
                onSourceTypeChange = onSourceTypeChange,
                onSourceSwitchOpen = onSourceSwitchOpen,
                onCategoryChange = onCategoryChange,
                onMoreCategoryOpen = onMoreCategoryOpen,
                onRefresh = onRefresh,
                onBookClick = onBookClick,
                onBookDetailOpen = onBookDetailOpen,
                onAddToBookshelf = onAddToBookshelf,
                onRead = onRead,
                onSourceDetect = onSourceDetect,
                onRankingMore = onRankingMore,
                onRssClick = onRssClick
            )
        }
    }
}

@Composable
private fun DiscoverHomeContent(
    state: DiscoveryHomeUiState,
    legacyDiscoverState: DiscoverUiState,
    modifier: Modifier = Modifier,
    onMoreClick: () -> Unit,
    onSearchOpen: () -> Unit,
    onSourceTypeChange: (DiscoverySourceTypeUiModel) -> Unit,
    onSourceSwitchOpen: () -> Unit,
    onCategoryChange: (DiscoveryCategoryUiModel) -> Unit,
    onMoreCategoryOpen: () -> Unit,
    onRefresh: () -> Unit,
    onBookClick: (Int) -> Unit,
    onBookDetailOpen: (DiscoveryBookUiModel) -> Unit,
    onAddToBookshelf: (DiscoveryBookUiModel) -> Unit,
    onRead: (DiscoveryBookUiModel) -> Unit,
    onSourceDetect: () -> Unit,
    onRankingMore: () -> Unit,
    onRssClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReaderTheme.colors.paperBg)
            .semantics { contentDescription = "MainTabShell 发现" }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = state.topBar.title,
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
                    .padding(horizontal = ReaderTheme.spacing.screenPadding)
                    .semantics { contentDescription = "contentRegion" },
                verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                SearchEntry(placeholder = state.search.placeholder, onSearchOpen = onSearchOpen)
                SourceTypeSegment(sourceTypes = state.sourceTypes, onSourceTypeChange = onSourceTypeChange)
                CurrentSourceCard(source = state.currentSource, onSourceSwitchOpen = onSourceSwitchOpen)
                SourceCategoryChips(
                    categories = state.categories,
                    moreLabel = state.categoryMoreLabel,
                    onCategoryChange = onCategoryChange,
                    onMoreCategoryOpen = onMoreCategoryOpen
                )
                when (state.displayState) {
                    DiscoveryHomeDisplayState.Loading -> DiscoveryHomeFeedbackCard(state.feedback.loading)
                    DiscoveryHomeDisplayState.Empty -> DiscoveryHomeFeedbackCard(state.feedback.empty, onPrimary = onRefresh, onSecondary = onSourceSwitchOpen)
                    DiscoveryHomeDisplayState.Error -> DiscoveryHomeFeedbackCard(state.feedback.error, onPrimary = onRefresh, onSecondary = onSourceSwitchOpen)
                    DiscoveryHomeDisplayState.Offline -> DiscoveryHomeFeedbackCard(state.feedback.offline, onPrimary = onRefresh, onSecondary = onRssClick)
                    DiscoveryHomeDisplayState.Default,
                    DiscoveryHomeDisplayState.Subscription -> {
                        DiscoveryContentCard(
                            content = state.content,
                            onRefresh = onRefresh,
                            onBookClick = onBookDetailOpen,
                            onAddToBookshelf = onAddToBookshelf,
                            onRead = onRead
                        )
                        SourceStatusBar(status = state.statusBar, onSourceDetect = onSourceDetect)
                        DiscoveryRankingCard(ranking = state.ranking, onRankingMore = onRankingMore)
                        LegacyRecommendationGrid(state = legacyDiscoverState, onBookClick = onBookClick)
                    }
                }
                ReaderSettingsRow(
                    title = "RSS 订阅",
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        Icon(
                            imageVector = ReaderIconToken.Chevron.asImageVector(),
                            contentDescription = "RSS 订阅",
                            tint = ReaderTheme.colors.controlInk
                        )
                    },
                    onClick = onRssClick
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .semantics { contentDescription = "stateHost" }
        )
    }
}

@Composable
private fun SearchEntry(
    placeholder: String,
    onSearchOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onSearchOpen)
            .semantics { contentDescription = "搜索入口，$placeholder" }
    ) {
        ReaderSearchBox(
            query = "",
            onQueryChange = {},
            placeholder = placeholder,
            contentDescription = "搜索框"
        )
    }
}

@Composable
private fun SourceTypeSegment(
    sourceTypes: List<DiscoverySourceTypeUiModel>,
    onSourceTypeChange: (DiscoverySourceTypeUiModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "来源类型" },
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        sourceTypes.forEach { sourceType ->
            ReaderChip(
                text = sourceType.label,
                selected = sourceType.active,
                onClick = { onSourceTypeChange(sourceType) }
            )
        }
    }
}

@Composable
private fun CurrentSourceCard(
    source: DiscoveryCurrentSourceUiModel,
    onSourceSwitchOpen: () -> Unit
) {
    ReaderCard(
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "${source.title}，${source.meta}，${source.status}",
        onClick = onSourceSwitchOpen
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            Icon(
                imageVector = ReaderIconToken.SourceSwitch.asImageVector(),
                contentDescription = source.iconLabel,
                tint = ReaderTheme.colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(source.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
                Text(source.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                Text(source.status, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
            ReaderChip(text = source.actionLabel, selected = true)
        }
    }
}

@Composable
private fun SourceCategoryChips(
    categories: List<DiscoveryCategoryUiModel>,
    moreLabel: String,
    onCategoryChange: (DiscoveryCategoryUiModel) -> Unit,
    onMoreCategoryOpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        ReaderSectionHeader(title = "来源分类")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            categories.take(4).forEach { category ->
                ReaderChip(
                    text = category.label,
                    selected = category.active,
                    onClick = { onCategoryChange(category) }
                )
            }
            ReaderChip(text = moreLabel, onClick = onMoreCategoryOpen)
        }
    }
}

@Composable
private fun DiscoveryContentCard(
    content: DiscoveryContentUiModel,
    onRefresh: () -> Unit,
    onBookClick: (DiscoveryBookUiModel) -> Unit,
    onAddToBookshelf: (DiscoveryBookUiModel) -> Unit,
    onRead: (DiscoveryBookUiModel) -> Unit
) {
    ReaderCard(modifier = Modifier.fillMaxWidth(), contentDescription = content.title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(content.title, modifier = Modifier.weight(1f), color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
            ReaderSecondaryButton(text = content.refreshLabel, onClick = onRefresh)
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        content.featured.forEach { book ->
            DiscoveryBookRow(
                book = book,
                onClick = { onBookClick(book) },
                onAction = {
                    if (book.inBookshelf) {
                        onRead(book)
                    } else {
                        onAddToBookshelf(book)
                    }
                }
            )
        }
    }
}

@Composable
private fun DiscoveryBookRow(
    book: DiscoveryBookUiModel,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${book.title}，${book.author}，${book.source}，${book.actionLabel}" }
            .padding(vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.floatingControlBgAlt)
                .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ReaderIconToken.Directory.asImageVector(),
                contentDescription = book.cover,
                tint = ReaderTheme.colors.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${book.author} · ${book.source}", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.desc, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (book.inBookshelf) {
            ReaderPrimaryButton(text = book.actionLabel, onClick = onAction)
        } else {
            ReaderSecondaryButton(text = book.actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun SourceStatusBar(
    status: DiscoverySourceStatusUiModel,
    onSourceDetect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .padding(ReaderTheme.spacing.sm)
            .semantics { contentDescription = "${status.sourceCount}，${status.availableCount}，${status.updatedAt}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Check.asImageVector(),
            contentDescription = null,
            tint = DiscoveryGoodColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("${status.sourceCount} · ${status.availableCount}", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(status.updatedAt, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderSecondaryButton(text = status.actionLabel, onClick = onSourceDetect)
    }
}

@Composable
private fun DiscoveryRankingCard(
    ranking: DiscoveryRankingUiModel,
    onRankingMore: () -> Unit
) {
    ReaderCard(modifier = Modifier.fillMaxWidth(), contentDescription = ranking.title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ranking.title, modifier = Modifier.weight(1f), color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
            ReaderChip(text = ranking.moreLabel, onClick = onRankingMore)
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        ranking.items.forEach { item ->
            DiscoveryRankingRow(item = item)
        }
    }
}

@Composable
private fun DiscoveryRankingRow(item: DiscoveryRankingItemUiModel) {
    val tone = when (item.tone) {
        "green" -> DiscoveryGoodColor
        "orange" -> DiscoveryWarnColor
        else -> ReaderTheme.colors.bodyText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "第${item.rank}，${item.title}，${item.state}" }
            .padding(vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(item.rank.toString(), color = tone, style = ReaderTheme.typography.pageTitle)
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text("${item.author} · ${item.source}", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderChip(text = item.state, selected = item.tone == "green")
    }
}

@Composable
private fun LegacyRecommendationGrid(
    state: DiscoverUiState,
    onBookClick: (Int) -> Unit
) {
    if (state.items.isEmpty()) {
        return
    }
    ReaderSectionHeader(title = "推荐")
    state.items.chunked(3).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            rowItems.forEach { item ->
                BookCard(
                    title = item.title,
                    author = item.subtitle,
                    progress = item.progress,
                    onClick = { onBookClick(state.items.indexOf(item)) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(3 - rowItems.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiscoveryHomeFeedbackCard(
    feedback: DiscoveryFeedbackUiModel,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ReaderIconToken.Warning.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        if (feedback.primaryAction != null || feedback.secondaryAction != null) {
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                feedback.secondaryAction?.let { label ->
                    ReaderSecondaryButton(text = label, onClick = onSecondary ?: {})
                }
                feedback.primaryAction?.let { label ->
                    ReaderPrimaryButton(text = label, onClick = onPrimary ?: {})
                }
            }
        }
    }
}
