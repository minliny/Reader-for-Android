package com.reader.ui.rss

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.tokens.ReaderTypeToken
import com.reader.android.R
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.tokens.ReaderColorToken
import com.reader.ui.tokens.ReaderTokenAdapter

class RssTabState {
    var activeMode by mutableStateOf("源列表")
    var activeFilter by mutableStateOf("全部")
    var refreshing by mutableStateOf(false)
    // P1-5: real subscriptions loaded from RoomSubscriptionRepository via
    // AppProvider. Falls back to demo data when AppProvider is not
    // initialized (preview / JVM test).
    var subscriptions by mutableStateOf<List<com.reader.android.data.network.RssSubscription>>(emptyList())
        internal set
    val sources = rssDemoSources()
    val articles = rssDemoArticles()

    /**
     * Returns true if real subscription data has been loaded (de-demo'd).
     * When true, the UI should render [subscriptions] instead of [sources].
     */
    fun hasRealSubscriptions(): Boolean = subscriptions.isNotEmpty()
}

@Composable
fun RssScreen(
    state: RssTabState,
    onSearch: () -> Unit,
    onManageSources: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenRuleSubscription: () -> Unit,
    onOpenArticle: () -> Unit
) {
    with(state) {
        val filteredSources = sources.filter { source ->
            when (activeFilter) {
                "全部" -> true
                "需登录" -> source.login
                "暂停" -> !source.enabled
                else -> source.group == activeFilter
            }
        }
        val visibleArticles = when (activeMode) {
            "全部" -> articles
            "收藏" -> articles.filter { it.starred }
            else -> articles.filter { it.unread }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 118.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { RssSearchEntry(onClick = onSearch) }
            item {
                RssModeRow(
                    modes = listOf("源列表", "全部", "收藏", "规则订阅"),
                    activeMode = activeMode,
                    onMode = { mode ->
                        when (mode) {
                            "全部" -> onOpenAll()
                            "收藏" -> onOpenStarred()
                            "规则订阅" -> onOpenRuleSubscription()
                            else -> activeMode = mode
                        }
                    }
                )
            }
            item {
                RssSummaryCard(
                    enabledCount = sources.count { it.enabled },
                    unreadCount = sources.sumOf { it.unread },
                    lastRefresh = "10:18",
                    onRefresh = { refreshing = true }
                )
            }
            if (refreshing) {
                item { RssRefreshLine() }
            }
            when (activeMode) {
                "规则订阅" -> {
                    item { RssRuleSubscriptionList() }
                }
                "源列表" -> {
                    item {
                        RssSourceOverview(
                            sources = filteredSources,
                            activeFilter = activeFilter,
                            onFilter = { activeFilter = it },
                            onImport = onManageSources,
                            onCreate = onManageSources
                        )
                    }
                    item {
                        RssArticleSection(
                            title = "最近未读",
                            articles = visibleArticles.take(3),
                            actionLabel = "查看全部",
                            actionIcon = R.drawable.reader_ic_nav_list,
                            onAction = { activeMode = "全部" },
                            onOpenArticle = onOpenArticle
                        )
                    }
                }
                else -> {
                    item { RssSourceStrip(sources = sources) }
                    item {
                        RssArticleSection(
                            title = activeMode,
                            articles = visibleArticles,
                            actionLabel = "管理源",
                            actionIcon = R.drawable.reader_ic_source_stack,
                            onAction = onManageSources,
                            onOpenArticle = onOpenArticle
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RssArticleHubScreen(
    title: String,
    activeMode: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenSourceList: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenRuleSubscription: () -> Unit,
    onOpenArticle: () -> Unit,
    onManageSources: () -> Unit
) {
    val sources = remember { rssDemoSources() }
    val articles = remember { rssDemoArticles() }
    val visibleArticles = if (activeMode == "收藏") {
        articles.filter { it.starred }
    } else {
        articles
    }

    LibraryShellFrame(
        backTopBar = { RssLibraryTopBar(title = title, onBack = onBack) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { RssSearchEntry(onClick = onSearch) }
                item {
                    RssModeRow(
                        modes = listOf("源列表", "全部", "收藏", "规则订阅"),
                        activeMode = activeMode,
                        onMode = { mode ->
                            when (mode) {
                                "源列表" -> onOpenSourceList()
                                "全部" -> if (activeMode != "全部") onOpenAll()
                                "收藏" -> if (activeMode != "收藏") onOpenStarred()
                                "规则订阅" -> onOpenRuleSubscription()
                            }
                        }
                    )
                }
                item { RssSourceStrip(sources = sources) }
                item {
                    RssArticleSection(
                        title = title,
                        articles = visibleArticles,
                        actionLabel = "管理源",
                        actionIcon = R.drawable.reader_ic_source_stack,
                        onAction = onManageSources,
                        onOpenArticle = onOpenArticle
                    )
                }
            }
        }
    )
}

@Composable
private fun RssLibraryTopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                contentDescription = "返回",
                tint = colors.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
fun RssTabTopBar(
    state: RssTabState,
    onManageSources: () -> Unit
) {
    // Demo .fd-top-bar: min-height 58, padding 6px 20px 0, color --fd-ink, space-between.
    // Trailing .fd-top-actions: gap 10px, each .fd-icon-button 44x44 circle, icon 24x24.
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RSS",
            style = ReaderTextStyles.appBarTitle,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RssTopIconButton(
                iconRes = R.drawable.reader_ic_refresh,
                contentDescription = "刷新",
                onClick = { state.refreshing = !state.refreshing }
            )
            RssTopIconButton(
                iconRes = R.drawable.reader_ic_nav_list,
                contentDescription = "管理",
                onClick = onManageSources
            )
        }
    }
}

@Composable
private fun RssTopIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun RssRefreshPillRemoved() {
    // Removed: RssRefreshPill / RssTopActionPill were non-conformant custom pills.
    // RssTabTopBar now uses RssTopIconButton (44x44 circle, 24 icon, fd-ink) per .fd-icon-button.
}

@Composable
private fun RssSearchEntry(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .background(colors.surface.copy(alpha = 0.74f), ReaderShapes.pill)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_search),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "搜索订阅源、文章标题或分组",
            style = rssMetaStyle().copy(fontWeight = FontWeight(700), fontSize = ReaderTypeToken.BOOK_META.value, lineHeight = 15.sp),
            color = extra.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssModeRow(modes: List<String>, activeMode: String, onMode: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEach { mode ->
            RssChip(text = mode, active = activeMode == mode, onClick = { onMode(mode) })
        }
    }
}

@Composable
private fun RssRefreshLine() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 30.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(2.dp, colors.primary.copy(alpha = 0.28f), ReaderShapes.pill)
                .background(Color.Transparent, ReaderShapes.pill)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "正在刷新启用订阅源和分类入口",
            style = rssMetaStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, fontWeight = FontWeight(800)),
            color = extra.muted
        )
    }
}

@Composable
private fun RssSummaryCard(
    enabledCount: Int,
    unreadCount: Int,
    lastRefresh: String,
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 34px icon circle per demo .fd-rss-summary-card > span
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.primary.copy(alpha = 0.14f), ReaderShapes.circle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_rss),
                contentDescription = null,
                tint = extra.primaryDark,
                modifier = Modifier.size(19.dp)
            )
        }
        // title + meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "订阅中心",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.SECTION_TITLE.value,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight(700)
                ),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$enabledCount 个启用源 · $unreadCount 条未读 · 最近刷新 $lastRefresh",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.ACTION_LABEL.value,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight(400)
                ),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // action button: primary pill per demo .fd-rss-summary-card button
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 30.dp)
                .background(colors.primary, ReaderShapes.pill)
                .clickable(onClick = onRefresh)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_refresh),
                contentDescription = null,
                tint = extra.onPrimary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "刷新",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.ACTION_LABEL.value,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight(850)
                ),
                color = extra.onPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RssSourceOverview(
    sources: List<RssSource>,
    activeFilter: String,
    onFilter: (String) -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
                .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "订阅源",
                style = rssSectionTitleStyle(),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            RssMiniAction(label = "导入", iconRes = R.drawable.reader_ic_upload, onClick = onImport)
            Spacer(Modifier.width(6.dp))
            RssMiniAction(label = "新建", iconRes = R.drawable.reader_ic_add, onClick = onCreate)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("全部", "开源项目", "社区", "需登录", "暂停").forEach { filter ->
                RssChip(text = filter, active = activeFilter == filter, onClick = { onFilter(filter) })
            }
        }
        RssSourceList(sources)
    }
}

@Composable
private fun RssSourceList(sources: List<RssSource>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        sources.forEachIndexed { index, source ->
            RssSourceRow(source)
            if (index != sources.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(extra.hairline.copy(alpha = 0.48f))
                )
            }
        }
    }
}

@Composable
private fun RssSourceRow(source: RssSource) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    // Demo .fd-rss-source-list article: grid 30/1fr/auto/30, gap 8, min-height 58, padding 8 10.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .alpha(if (source.enabled) 1f else 0.62f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssIconCircle(if (source.enabled) R.drawable.reader_ic_rss else R.drawable.reader_ic_offline, 30.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = source.name,
                style = rssTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${source.group} · ${source.categories} 个入口 · ${source.articleStyle} · ${source.rule}",
                style = rssMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = source.unread.toString(),
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.TOP_BAR_TITLE.value,
                lineHeight = 20.sp,
                fontWeight = FontWeight(900)
            ),
            color = extra.primaryDark,
            modifier = Modifier.defaultMinSize(minWidth = 24.dp),
            maxLines = 1
        )
        RssStatusBadge(label = source.status, tone = source.tone)
    }
}

@Composable
private fun RssSourceStrip(sources: List<RssSource>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        sources.forEachIndexed { index, source ->
            RssSourceStripItem(source = source, active = index == 0)
        }
    }
}

@Composable
private fun RssSourceStripItem(source: RssSource, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = 138.dp, minHeight = 58.dp)
            .background(
                if (active) colors.primary.copy(alpha = 0.08f) else colors.surface.copy(alpha = 0.82f),
                ReaderShapes.md
            )
            .border(1.dp, if (active) colors.primary.copy(alpha = 0.42f) else extra.hairline.copy(alpha = 0.66f), ReaderShapes.md)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssIconCircle(if (source.enabled) R.drawable.reader_ic_rss else R.drawable.reader_ic_offline, 26.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = source.name,
                style = rssMetaStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, fontWeight = FontWeight(700)),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(88.dp)
            )
            Text(
                text = "${source.group} · ${if (source.unread > 0) "${source.unread} 未读" else "无未读"}",
                style = rssMetaStyle().copy(fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value, lineHeight = 12.sp),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(88.dp)
            )
        }
    }
}

@Composable
private fun RssArticleSection(
    title: String,
    articles: List<RssArticle>,
    actionLabel: String,
    @DrawableRes actionIcon: Int,
    onAction: () -> Unit,
    onOpenArticle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 30.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = rssSectionTitleStyle(),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            RssMiniAction(label = actionLabel, iconRes = actionIcon, onClick = onAction)
        }
        RssArticleList(articles = articles, onOpenArticle = onOpenArticle)
    }
}

@Composable
private fun RssArticleList(articles: List<RssArticle>, onOpenArticle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        articles.forEachIndexed { index, article ->
            RssArticleRow(article = article, onClick = onOpenArticle)
            if (index != articles.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(extra.hairline.copy(alpha = 0.48f))
                )
            }
        }
    }
}

@Composable
private fun RssArticleRow(article: RssArticle, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 84.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (article.unread) colors.primary else extra.hairline, ReaderShapes.pill)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = article.title,
                style = rssTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${article.source} · ${article.time} · ${article.group}",
                style = rssMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = article.desc,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.ACTION_LABEL.value,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight(500)
                ),
                color = extra.infoLayer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(id = if (article.starred) R.drawable.reader_ic_bookmark else R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = if (article.starred) colors.primary else extra.muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RssRuleSubscriptionList() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "规则订阅", style = rssSectionTitleStyle(), color = colors.onBackground)
        rssRuleSubscriptions().forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RssIconCircle(R.drawable.reader_ic_source_stack, 28.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = item.name, style = rssTitleStyle(), color = colors.onBackground, maxLines = 1)
                    Text(
                        text = "${item.type} · ${item.update}",
                        style = rssMetaStyle(),
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_chevron),
                    contentDescription = null,
                    tint = extra.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RssMiniAction(label: String, @DrawableRes iconRes: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = rssButtonStyle().copy(fontSize = ReaderTypeToken.ACTION_LABEL.value, lineHeight = 13.sp),
            color = extra.primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssChip(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(if (active) colors.primary else extra.metaBackground.copy(alpha = 0.84f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = rssButtonStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, lineHeight = 14.sp),
            color = if (active) colors.onPrimary else extra.muted,
            maxLines = 1
        )
    }
}

@Composable
private fun RssIconCircle(@DrawableRes iconRes: Int, size: androidx.compose.ui.unit.Dp) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(size)
            .background(colors.primary.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
private fun RssStatusBadge(label: String, tone: RssTone) {
    // Demo .fd-rss-badge: 24x18 pill, no border, padding 0, font-size 0 (text hidden),
    // 7px <i> dot provides visible color via currentColor.
    // .is-good: bg rgba(54,121,91,0.12), color #2f6b52.
    // .is-warn: bg rgba(180,110,35,0.14), color #8b5829.
    // .is-muted: bg rgba(127,118,108,0.12), color --fd-muted.
    val extra = readerExtraColors()
    val (bgColor, dotColor) = when (tone) {
        // 语义色：Good/Warn 使用 STATUS_GOOD/STATUS_WARN token，Muted 使用 extra.muted
        RssTone.Good -> ReaderTokenAdapter.color(ReaderColorToken.STATUS_GOOD).copy(alpha = 0.12f) to ReaderTokenAdapter.color(ReaderColorToken.STATUS_GOOD)
        RssTone.Warn -> ReaderTokenAdapter.color(ReaderColorToken.STATUS_WARN).copy(alpha = 0.14f) to ReaderTokenAdapter.color(ReaderColorToken.STATUS_WARN)
        RssTone.Muted -> extra.muted.copy(alpha = 0.12f) to extra.muted
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 18.dp)
            .background(bgColor, ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        // 7px dot per demo .fd-rss-badge i
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, ReaderShapes.circle)
        )
    }
}

data class RssSource(
    val name: String,
    val group: String,
    val unread: Int,
    val latest: String,
    val status: String,
    val tone: RssTone,
    val enabled: Boolean,
    val categories: Int,
    val articleStyle: String,
    val rule: String,
    val login: Boolean
)

data class RssArticle(
    val title: String,
    val source: String,
    val time: String,
    val group: String,
    val desc: String,
    val unread: Boolean,
    val starred: Boolean
)

private data class RssRuleSubscription(
    val name: String,
    val type: String,
    val update: String
)

enum class RssTone { Good, Warn, Muted }

private fun rssDemoSources() = listOf(
    RssSource("GitHub Releases", "开源项目", 6, "10:18", "正常", RssTone.Good, true, 3, "列表", "默认 RSS", false),
    RssSource("阅读器版本讨论", "社区", 12, "09:42", "有更新", RssTone.Good, true, 4, "图文", "自定义列表", false),
    RssSource("书源维护公告", "维护", 2, "昨天", "需登录", RssTone.Warn, true, 2, "紧凑", "正文规则", true),
    RssSource("本地系统通知", "系统", 0, "周二", "暂停", RssTone.Muted, false, 1, "列表", "单 URL", false)
)

private fun rssDemoArticles() = listOf(
    RssArticle("Reader UI 前端输入件更新说明", "GitHub Releases", "10:18", "开源项目", "新增发现页状态路由、阅读控制层响应式约束，并补充 RSS 页面结构规划。", true, true),
    RssArticle("订阅源规则解析失败排查", "书源维护公告", "09:52", "维护", "部分订阅源返回 HTML 而不是 XML，已建议检查 Cookie、登录态和正文提取规则。", true, false),
    RssArticle("Legado 订阅源配置经验整理", "阅读器版本讨论", "昨天", "社区", "社区整理了单 URL 源、分类入口、文章样式和 WebView 正文处理的常见配置方式。", true, false),
    RssArticle("本地导入完成解析", "本地系统通知", "周二", "系统", "本地 OPML 导入完成，4 个订阅源已启用，1 个订阅源需要补全图标。", false, false),
    RssArticle("阅读器路线图讨论摘要", "阅读器版本讨论", "周一", "社区", "围绕 RSS 收藏、源分组、正文阅读和同步备份的交互关系做了讨论。", false, true)
)

private fun rssRuleSubscriptions() = listOf(
    RssRuleSubscription("社区 RSS 源订阅", "RSS 源", "自动更新"),
    RssRuleSubscription("默认书源订阅", "书源", "手动"),
    RssRuleSubscription("替换规则同步", "替换规则", "自动更新")
)

private fun rssSectionTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.SECTION_TITLE.value,
    lineHeight = 18.sp,
    fontWeight = FontWeight(700)
)

private fun rssTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(700)
)

private fun rssMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun rssButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(850)
)
