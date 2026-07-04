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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.R
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

object RssRemainingRouteIds {
    const val SOURCE_FEED = "rss-source-feed"
    const val SOURCE_CATEGORY_RELEASES = "rss-source-category-releases"
    const val SOURCE_CATEGORY_ISSUES = "rss-source-category-issues"
    const val SOURCE_CATEGORY_DISCUSSIONS = "rss-source-category-discussions"
    const val FAVORITE_GROUPS = "rss-favorite-groups"
    const val FAVORITE_GROUP_EDIT = "rss-favorite-group-edit"
    const val FAVORITE_CLEAR = "rss-favorite-clear"
    const val EMPTY = "rss-empty"
    const val ERROR = "rss-error"

    val sourceFeedRouteIds: Set<String> = setOf(
        SOURCE_FEED,
        SOURCE_CATEGORY_RELEASES,
        SOURCE_CATEGORY_ISSUES,
        SOURCE_CATEGORY_DISCUSSIONS
    )

    val routeIds: Set<String> = sourceFeedRouteIds + setOf(
        FAVORITE_GROUPS,
        FAVORITE_GROUP_EDIT,
        FAVORITE_CLEAR,
        EMPTY,
        ERROR
    )
}

enum class RssRemainingRouteKind {
    SourceFeed,
    FavoriteGroups,
    FavoriteGroupEdit,
    FavoriteClear,
    Empty,
    Error
}

data class RssRemainingRouteState(
    val routeId: String,
    val title: String,
    val kind: RssRemainingRouteKind
)

data class RssSourceCategoryRouteState(
    val label: String,
    val routeId: String,
    val title: String,
    val meta: String
)

data class RssSourcePreviewState(
    val name: String,
    val group: String,
    val unread: Int,
    val latest: String,
    val status: String,
    val tone: RssRouteTone,
    val categories: Int,
    val articleStyle: String,
    val rule: String,
    val enabled: Boolean
)

data class RssArticlePreviewState(
    val title: String,
    val source: String,
    val time: String,
    val group: String,
    val desc: String,
    val unread: Boolean,
    val starred: Boolean
)

data class RssSourceFeedRouteState(
    val routeId: String,
    val title: String,
    val source: RssSourcePreviewState,
    val activeCategory: RssSourceCategoryRouteState,
    val categories: List<RssSourceCategoryRouteState>,
    val articles: List<RssArticlePreviewState>
)

data class RssFavoriteGroupState(
    val name: String,
    val meta: String,
    val status: String,
    val tone: RssRouteTone
)

data class RssFavoriteGroupEditFieldState(
    val label: String,
    val value: String
)

data class RssStateErrorRowState(
    val title: String,
    val meta: String,
    val tone: RssRouteTone
)

data class RssStateRouteState(
    val routeId: String,
    val title: String,
    val icon: RssStateIcon,
    val heading: String,
    val copy: String,
    val primaryLabel: String,
    val primaryRouteId: String,
    val secondaryLabel: String,
    val secondaryRouteId: String,
    val errors: List<RssStateErrorRowState> = emptyList()
)

enum class RssRouteTone { Good, Warn, Muted }

enum class RssStateIcon { Rss, Warning }

fun rssRemainingDemoRouteIds(): Set<String> = RssRemainingRouteIds.routeIds

fun rssRemainingDemoRouteState(routeId: String): RssRemainingRouteState? = when (routeId) {
    in RssRemainingRouteIds.sourceFeedRouteIds -> {
        val feed = rssSourceFeedRouteState(routeId)
        RssRemainingRouteState(routeId = routeId, title = feed.title, kind = RssRemainingRouteKind.SourceFeed)
    }
    RssRemainingRouteIds.FAVORITE_GROUPS -> RssRemainingRouteState(routeId, "收藏分组", RssRemainingRouteKind.FavoriteGroups)
    RssRemainingRouteIds.FAVORITE_GROUP_EDIT -> RssRemainingRouteState(routeId, "编辑收藏分组", RssRemainingRouteKind.FavoriteGroupEdit)
    RssRemainingRouteIds.FAVORITE_CLEAR -> RssRemainingRouteState(routeId, "清空收藏分组", RssRemainingRouteKind.FavoriteClear)
    RssRemainingRouteIds.EMPTY -> RssRemainingRouteState(routeId, "RSS 空状态", RssRemainingRouteKind.Empty)
    RssRemainingRouteIds.ERROR -> RssRemainingRouteState(routeId, "RSS 错误", RssRemainingRouteKind.Error)
    else -> null
}

fun rssSourceCategoryRoutes(): List<RssSourceCategoryRouteState> = listOf(
    RssSourceCategoryRouteState("全部", RssRemainingRouteIds.SOURCE_FEED, "GitHub Releases", "默认 RSS 解析 · 18 条"),
    RssSourceCategoryRouteState("Releases", RssRemainingRouteIds.SOURCE_CATEGORY_RELEASES, "Releases", "版本发布 · 8 条"),
    RssSourceCategoryRouteState("Issues", RssRemainingRouteIds.SOURCE_CATEGORY_ISSUES, "Issues", "问题讨论 · 6 条"),
    RssSourceCategoryRouteState("Discussions", RssRemainingRouteIds.SOURCE_CATEGORY_DISCUSSIONS, "Discussions", "社区讨论 · 4 条")
)

fun rssSourceFeedRouteState(routeId: String): RssSourceFeedRouteState {
    val categories = rssSourceCategoryRoutes()
    val category = categories.firstOrNull { it.routeId == routeId } ?: categories.first()
    val source = rssRemainingSources().first()
    return RssSourceFeedRouteState(
        routeId = category.routeId,
        title = category.title,
        source = source,
        activeCategory = category,
        categories = categories,
        articles = rssRemainingArticles().filter { it.source == source.name }
    )
}

fun rssFavoriteGroupsRouteState(): List<RssFavoriteGroupState> = listOf(
    RssFavoriteGroupState("默认分组", "2 条收藏 · 首页显示", "显示", RssRouteTone.Good),
    RssFavoriteGroupState("开源项目", "1 条收藏 · 自动归类", "显示", RssRouteTone.Good),
    RssFavoriteGroupState("社区", "1 条收藏 · 手动归类", "隐藏", RssRouteTone.Muted)
)

fun rssFavoriteGroupEditRouteState(): List<RssFavoriteGroupEditFieldState> = listOf(
    RssFavoriteGroupEditFieldState("分组名称", "默认分组"),
    RssFavoriteGroupEditFieldState("首页显示", "开启"),
    RssFavoriteGroupEditFieldState("排序方式", "最近收藏优先"),
    RssFavoriteGroupEditFieldState("包含条目", "Reader UI 前端输入件更新说明、阅读器路线图讨论摘要")
)

fun rssStateRouteState(routeId: String): RssStateRouteState? = when (routeId) {
    RssRemainingRouteIds.EMPTY -> RssStateRouteState(
        routeId = routeId,
        title = "RSS 空状态",
        icon = RssStateIcon.Rss,
        heading = "暂无未读订阅",
        copy = "当前订阅源没有新的未读条目。你可以查看全部、管理订阅源或手动刷新。日常空状态仍保留 RSS 主导航上下文。",
        primaryLabel = "查看全部",
        primaryRouteId = "rss-all",
        secondaryLabel = "订阅管理",
        secondaryRouteId = "rss-subscription-management"
    )
    RssRemainingRouteIds.ERROR -> RssStateRouteState(
        routeId = routeId,
        title = "RSS 错误",
        icon = RssStateIcon.Warning,
        heading = "订阅刷新失败",
        copy = "2 个订阅源刷新失败，已保留最近缓存条目。可以稍后重试、查看错误源，或进入订阅源管理修复登录态和规则。",
        primaryLabel = "重试刷新",
        primaryRouteId = "rss-refreshing",
        secondaryLabel = "订阅管理",
        secondaryRouteId = "rss-subscription-management",
        errors = listOf(
            RssStateErrorRowState("书源维护公告", "登录态失效 · 需要重新登录", RssRouteTone.Warn),
            RssStateErrorRowState("本地系统通知", "源已暂停 · 不参与自动刷新", RssRouteTone.Muted)
        )
    )
    else -> null
}

@Composable
fun RssRemainingDemoRouteScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when (rssRemainingDemoRouteState(routeId)?.kind) {
        RssRemainingRouteKind.SourceFeed -> {
            RssSourceFeedScreen(routeId = routeId, onBack = onBack, onNavigate = onNavigate)
        }
        RssRemainingRouteKind.FavoriteGroups -> {
            RssFavoriteGroupsScreen(
                onBack = onBack,
                onEditGroup = { onNavigate(RssRemainingRouteIds.FAVORITE_GROUP_EDIT) },
                onSave = { onNavigate("rss-starred") }
            )
        }
        RssRemainingRouteKind.FavoriteGroupEdit -> {
            RssFavoriteGroupEditScreen(
                onBack = onBack,
                onCancel = { onNavigate(RssRemainingRouteIds.FAVORITE_GROUPS) },
                onSave = { onNavigate(RssRemainingRouteIds.FAVORITE_GROUPS) }
            )
        }
        RssRemainingRouteKind.FavoriteClear -> {
            RssFavoriteClearScreen(
                onCancel = { onNavigate("rss-starred") },
                onConfirm = { onNavigate("rss-starred") }
            )
        }
        RssRemainingRouteKind.Empty,
        RssRemainingRouteKind.Error -> {
            RssStateScreen(routeId = routeId, onBack = onBack, onNavigate = onNavigate)
        }
        null -> {
            RssStateScreen(routeId = RssRemainingRouteIds.EMPTY, onBack = onBack, onNavigate = onNavigate)
        }
    }
}

@Composable
fun RssSourceFeedScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    RssSourceFeedScreen(
        state = remember(routeId) { rssSourceFeedRouteState(routeId) },
        onBack = onBack,
        onRefresh = { onNavigate("rss-refreshing") },
        onEditSource = { onNavigate("rss-source-edit") },
        onReadRecord = { onNavigate("rss-read-record") },
        onDebugSource = { onNavigate("rss-source-debug") },
        onOpenSourceActions = { onNavigate("rss-source-actions") },
        onOpenArticle = { onNavigate("rss-detail") },
        onOpenCategory = { onNavigate(it.routeId) }
    )
}

@Composable
fun RssSourceFeedScreen(
    state: RssSourceFeedRouteState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEditSource: () -> Unit,
    onReadRecord: () -> Unit,
    onDebugSource: () -> Unit,
    onOpenSourceActions: () -> Unit,
    onOpenArticle: () -> Unit,
    onOpenCategory: (RssSourceCategoryRouteState) -> Unit
) {
    RssRemainingScaffold(title = state.title, onBack = onBack) {
        item {
            RssRemainingSourceHero(source = state.source, category = state.activeCategory)
        }
        item {
            RssSourceToolbar(
                onRefresh = onRefresh,
                onEditSource = onEditSource,
                onReadRecord = onReadRecord,
                onDebugSource = onDebugSource
            )
        }
        item {
            RssCategoryRow(
                categories = state.categories,
                active = state.activeCategory,
                onOpenCategory = onOpenCategory
            )
        }
        item {
            RssRemainingArticleSection(
                title = state.activeCategory.title,
                articles = state.articles,
                actionLabel = "源操作",
                actionIconRes = R.drawable.reader_ic_more,
                onAction = onOpenSourceActions,
                onOpenArticle = onOpenArticle
            )
        }
        item {
            RssBottomLoading()
        }
    }
}

@Composable
fun RssFavoriteGroupsScreen(
    onBack: () -> Unit,
    onEditGroup: () -> Unit,
    onSave: () -> Unit
) {
    RssRemainingScaffold(
        title = "收藏分组",
        onBack = onBack,
        bottom = {
            RssRemainingBottomActions(
                secondary = "取消",
                primary = "保存",
                onSecondary = onBack,
                onPrimary = onSave
            )
        }
    ) {
        item {
            RssFavoriteGroupList(groups = remember { rssFavoriteGroupsRouteState() })
        }
        item {
            RssFavoriteActionRow(onEditGroup = onEditGroup)
        }
    }
}

@Composable
fun RssFavoriteGroupEditScreen(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    RssRemainingScaffold(
        title = "编辑收藏分组",
        onBack = onBack,
        bottom = {
            RssRemainingBottomActions(
                secondary = "取消",
                primary = "保存",
                onSecondary = onCancel,
                onPrimary = onSave
            )
        }
    ) {
        item {
            RssFavoriteEditList(fields = remember { rssFavoriteGroupEditRouteState() })
        }
    }
}

@Composable
fun RssFavoriteClearScreen(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    RssSourceConfirmScreen(
        title = "清空收藏分组",
        iconRes = R.drawable.reader_ic_trash,
        heading = "清空默认分组收藏？",
        copy = "仅移除当前收藏分组里的条目，文章本身和订阅源不会删除。",
        cancelLabel = "取消",
        confirmLabel = "确认清空",
        onCancel = onCancel,
        onConfirm = onConfirm
    )
}

@Composable
fun RssStateScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val state = remember(routeId) { rssStateRouteState(routeId) ?: rssStateRouteState(RssRemainingRouteIds.EMPTY)!! }
    RssRemainingScaffold(title = state.title, onBack = onBack) {
        item {
            RssStateCard(
                state = state,
                onPrimary = { onNavigate(state.primaryRouteId) },
                onSecondary = { onNavigate(state.secondaryRouteId) }
            )
        }
    }
}

@Composable
private fun RssRemainingScaffold(
    title: String,
    onBack: () -> Unit,
    bottom: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    LibraryShellFrame(
        backTopBar = { RssRemainingTopBar(title = title, onBack = onBack) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (bottom == null) 24.dp else 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        },
        bottomActionHost = {
            if (bottom != null) bottom()
        }
    )
}

@Composable
private fun RssRemainingTopBar(title: String, onBack: () -> Unit) {
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
private fun RssRemainingSourceHero(
    source: RssSourcePreviewState,
    category: RssSourceCategoryRouteState
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssRemainingIconCircle(iconRes = R.drawable.reader_ic_rss, size = 32)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = source.name,
                style = rssRemainingTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${source.group} · ${category.meta} · ${source.rule} · ${source.latest}",
                style = rssRemainingMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssRemainingBadge(label = source.status, tone = source.tone)
    }
}

@Composable
private fun RssSourceToolbar(
    onRefresh: () -> Unit,
    onEditSource: () -> Unit,
    onReadRecord: () -> Unit,
    onDebugSource: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RssToolbarButton(label = "刷新", iconRes = R.drawable.reader_ic_refresh, onClick = onRefresh)
        RssToolbarButton(label = "编辑源", iconRes = R.drawable.reader_ic_edit, onClick = onEditSource)
        RssToolbarButton(label = "记录", iconRes = R.drawable.reader_ic_clock, onClick = onReadRecord)
        RssToolbarButton(label = "调试", iconRes = R.drawable.reader_ic_bug, onClick = onDebugSource)
    }
}

@Composable
private fun RssCategoryRow(
    categories: List<RssSourceCategoryRouteState>,
    active: RssSourceCategoryRouteState,
    onOpenCategory: (RssSourceCategoryRouteState) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { category ->
            RssChipButton(
                text = category.label,
                active = category.routeId == active.routeId,
                onClick = { onOpenCategory(category) }
            )
        }
    }
}

@Composable
private fun RssRemainingArticleSection(
    title: String,
    articles: List<RssArticlePreviewState>,
    actionLabel: String,
    @DrawableRes actionIconRes: Int,
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
                style = rssRemainingSectionTitleStyle(),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RssMiniAction(label = actionLabel, iconRes = actionIconRes, onClick = onAction)
        }
        RssRemainingArticleList(articles = articles, onOpenArticle = onOpenArticle)
    }
}

@Composable
private fun RssRemainingArticleList(
    articles: List<RssArticlePreviewState>,
    onOpenArticle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        articles.forEachIndexed { index, article ->
            RssRemainingArticleRow(article = article, onOpenArticle = onOpenArticle)
            if (index != articles.lastIndex) RssRemainingDivider()
        }
    }
}

@Composable
private fun RssRemainingArticleRow(
    article: RssArticlePreviewState,
    onOpenArticle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 84.dp)
            .clickable(onClick = onOpenArticle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (article.unread) colors.primary else extra.hairline.copy(alpha = 0.70f), ReaderShapes.pill)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = article.title,
                style = rssRemainingTitleStyle(),
                color = colors.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${article.source} · ${article.time} · ${article.group}",
                style = rssRemainingMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = article.desc,
                style = rssRemainingMetaStyle().copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = extra.controlInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(id = if (article.starred) R.drawable.reader_ic_bookmark else R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RssBottomLoading() {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), ReaderShapes.pill)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "继续下滑加载下一页",
            style = rssRemainingButtonStyle(),
            color = extra.muted,
            maxLines = 1
        )
    }
}

@Composable
private fun RssFavoriteGroupList(groups: List<RssFavoriteGroupState>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        groups.forEachIndexed { index, group ->
            RssFavoriteGroupRow(group = group)
            if (index != groups.lastIndex) RssRemainingDivider()
        }
    }
}

@Composable
private fun RssFavoriteGroupRow(group: RssFavoriteGroupState) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssRemainingIconCircle(iconRes = R.drawable.reader_ic_bookmark, size = 28)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = group.name,
                style = rssRemainingTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = group.meta,
                style = rssRemainingMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssRemainingBadge(label = group.status, tone = group.tone)
    }
}

@Composable
private fun RssFavoriteActionRow(onEditGroup: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RssToolbarButton(
            label = "新增分组",
            iconRes = R.drawable.reader_ic_add,
            onClick = onEditGroup,
            modifier = Modifier.weight(1f)
        )
        RssToolbarButton(
            label = "排序",
            iconRes = R.drawable.reader_ic_edit,
            onClick = onEditGroup,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RssFavoriteEditList(fields: List<RssFavoriteGroupEditFieldState>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        fields.forEachIndexed { index, field ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "收藏分组",
                    style = rssRemainingMetaStyle(),
                    color = extra.muted,
                    maxLines = 1
                )
                Text(
                    text = field.label,
                    style = rssRemainingTitleStyle(),
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = field.value,
                    style = rssRemainingMetaStyle(),
                    color = extra.infoLayer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (index != fields.lastIndex) RssRemainingDivider()
        }
    }
}

@Composable
private fun RssStateCard(
    state: RssStateRouteState,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 256.dp)
            .background(colors.surface.copy(alpha = 0.94f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RssRemainingIconCircle(iconRes = state.icon.drawableRes(), size = 44)
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.heading,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(900)
            ),
            color = colors.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.copy,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(500)
            ),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (state.errors.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RssStateErrorList(rows = state.errors)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RssStateActionButton(
                label = state.primaryLabel,
                primary = true,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
            RssStateActionButton(
                label = state.secondaryLabel,
                primary = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssStateErrorList(rows: List<RssStateErrorRowState>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            val background = when (row.tone) {
                RssRouteTone.Good -> readerExtraColors().forest.copy(alpha = 0.08f)
                RssRouteTone.Warn -> readerExtraColors().accent.copy(alpha = 0.10f)
                RssRouteTone.Muted -> readerExtraColors().hairline.copy(alpha = 0.32f)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background, ReaderShapes.sm)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = row.title,
                    style = rssRemainingTitleStyle().copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.meta,
                    style = rssRemainingMetaStyle(),
                    color = readerExtraColors().muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RssRemainingBottomActions(
    secondary: String,
    primary: String,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.94f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(extra.hairline.copy(alpha = 0.38f))
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RssBottomActionButton(
                label = secondary,
                primary = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
            RssBottomActionButton(
                label = primary,
                primary = true,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssBottomActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(if (primary) colors.primary else colors.surface.copy(alpha = 0.92f), ReaderShapes.pill)
            .border(1.dp, if (primary) colors.primary else extra.hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight(850)
            ),
            color = if (primary) colors.onPrimary else extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssStateActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .background(if (primary) colors.primary else colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssRemainingButtonStyle(),
            color = if (primary) colors.onPrimary else extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssToolbarButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(extra.metaBackground.copy(alpha = 0.84f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = rssRemainingButtonStyle(),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssChipButton(text: String, active: Boolean, onClick: () -> Unit) {
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
            style = rssRemainingButtonStyle().copy(fontSize = 12.sp, lineHeight = 14.sp),
            color = if (active) colors.onPrimary else extra.navInactive,
            maxLines = 1
        )
    }
}

@Composable
private fun RssMiniAction(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().primaryDark,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = rssRemainingButtonStyle().copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = readerExtraColors().primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssRemainingIconCircle(
    @DrawableRes iconRes: Int,
    size: Int
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(colors.primary.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size((size * 0.56f).dp)
        )
    }
}

@Composable
private fun RssRemainingBadge(label: String, tone: RssRouteTone) {
    val color = rssToneColor(tone)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 22.dp)
            .background(color.copy(alpha = 0.13f), ReaderShapes.pill)
            .border(1.dp, color.copy(alpha = 0.26f), ReaderShapes.pill)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssRemainingMetaStyle().copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight(850)),
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun rssToneColor(tone: RssRouteTone): Color {
    val extra = readerExtraColors()
    return when (tone) {
        RssRouteTone.Good -> extra.forest
        RssRouteTone.Warn -> extra.accent
        RssRouteTone.Muted -> extra.muted
    }
}

@DrawableRes
private fun RssStateIcon.drawableRes(): Int = when (this) {
    RssStateIcon.Rss -> R.drawable.reader_ic_rss
    RssStateIcon.Warning -> R.drawable.reader_ic_warning
}

@Composable
private fun RssRemainingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.48f))
    )
}

private fun rssRemainingSources() = listOf(
    RssSourcePreviewState("GitHub Releases", "开源项目", 6, "10:18", "正常", RssRouteTone.Good, 3, "列表", "默认 RSS", true),
    RssSourcePreviewState("阅读器版本讨论", "社区", 12, "09:42", "有更新", RssRouteTone.Good, 4, "图文", "自定义列表", true),
    RssSourcePreviewState("书源维护公告", "维护", 2, "昨天", "需登录", RssRouteTone.Warn, 2, "紧凑", "正文规则", true),
    RssSourcePreviewState("本地系统通知", "系统", 0, "周二", "暂停", RssRouteTone.Muted, 1, "列表", "单 URL", false)
)

private fun rssRemainingArticles() = listOf(
    RssArticlePreviewState("Reader UI 前端输入件更新说明", "GitHub Releases", "10:18", "开源项目", "新增发现页状态路由、阅读控制层响应式约束，并补充 RSS 页面结构规划。", true, true),
    RssArticlePreviewState("订阅源规则解析失败排查", "书源维护公告", "09:52", "维护", "部分订阅源返回 HTML 而不是 XML，已建议检查 Cookie、登录态和正文提取规则。", true, false),
    RssArticlePreviewState("Legado 订阅源配置经验整理", "阅读器版本讨论", "昨天", "社区", "社区整理了单 URL 源、分类入口、文章样式和 WebView 正文处理的常见配置方式。", true, false),
    RssArticlePreviewState("本地导入完成解析", "本地系统通知", "周二", "系统", "本地 OPML 导入完成，4 个订阅源已启用，1 个订阅源需要补全图标。", false, false),
    RssArticlePreviewState("阅读器路线图讨论摘要", "阅读器版本讨论", "周一", "社区", "围绕 RSS 收藏、源分组、正文阅读和同步备份的交互关系做了讨论。", false, true)
)

private fun rssRemainingSectionTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight(900)
)

private fun rssRemainingTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssRemainingMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun rssRemainingButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
