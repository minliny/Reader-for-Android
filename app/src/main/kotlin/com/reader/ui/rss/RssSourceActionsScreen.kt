package com.reader.ui.rss

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun RssSourceActionsScreen(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDebug: () -> Unit,
    onReadRecord: () -> Unit,
    onVars: () -> Unit,
    onLogin: () -> Unit,
    onPin: () -> Unit,
    onDisable: () -> Unit,
    onManageAll: () -> Unit
) {
    val source = remember { rssSourceActionSource() }
    val actions = remember {
        listOf(
            RssSourceActionButtonSpec("刷新入口", R.drawable.reader_ic_refresh, onRefresh),
            RssSourceActionButtonSpec("编辑源", R.drawable.reader_ic_edit, onEdit),
            RssSourceActionButtonSpec("规则调试", R.drawable.reader_ic_bug, onDebug),
            RssSourceActionButtonSpec("阅读记录", R.drawable.reader_ic_clock, onReadRecord),
            RssSourceActionButtonSpec("源变量", R.drawable.reader_ic_code, onVars),
            RssSourceActionButtonSpec("登录", R.drawable.reader_ic_shield, onLogin),
            RssSourceActionButtonSpec("置顶", R.drawable.reader_ic_top, onPin),
            RssSourceActionButtonSpec("禁用", R.drawable.reader_ic_offline, onDisable)
        )
    }

    RssSourceActionScaffold(
        title = "源操作",
        onBack = onBack,
        bottom = {
            RssSourceActionBottomActions(
                secondary = "返回源",
                primary = "管理全部",
                onSecondary = onBack,
                onPrimary = onManageAll
            )
        }
    ) {
        item { RssSourceActionSourceCard(source = source) }
        item { RssSourceActionGrid(actions = actions) }
    }
}

@Composable
fun RssRefreshingScreen(
    onBack: () -> Unit,
    onOpenArticle: () -> Unit,
    onManageSources: () -> Unit
) {
    RssSourceActionScaffold(
        title = "刷新订阅",
        onBack = onBack,
        bottom = {}
    ) {
        item { RssRefreshingSearchEntry() }
        item { RssRefreshingModeRow() }
        item { RssRefreshingLine() }
        item { RssRefreshingSourceOverview(onManageSources = onManageSources) }
        item { RssRefreshingArticleSection(onOpenArticle = onOpenArticle) }
    }
}

@Composable
private fun RssRefreshingSearchEntry() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .background(colors.surface.copy(alpha = 0.74f), ReaderShapes.pill)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.pill)
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
            style = rssSourceActionMetaStyle().copy(fontWeight = FontWeight(700), fontSize = 12.sp, lineHeight = 15.sp),
            color = extra.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssRefreshingModeRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("源列表", "全部", "收藏", "规则订阅").forEachIndexed { index, label ->
            RssRefreshingChip(
                label = label,
                active = index == 0,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssRefreshingLine() {
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
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "正在刷新启用订阅源和分类入口",
            style = rssSourceActionMetaStyle().copy(fontSize = 12.sp, fontWeight = FontWeight(800)),
            color = extra.muted
        )
    }
}

@Composable
private fun RssRefreshingSourceOverview(onManageSources: () -> Unit) {
    val sources = listOf(
        RssRefreshingSource("GitHub Releases", "开源项目 · 3 个入口 · 列表 · 默认 RSS", "6", "正常", RssSourceActionTone.Good, true),
        RssRefreshingSource("阅读器版本讨论", "社区 · 4 个入口 · 图文 · 自定义列表", "12", "有更新", RssSourceActionTone.Good, true),
        RssRefreshingSource("书源维护公告", "维护 · 2 个入口 · 紧凑 · 正文规则", "2", "需登录", RssSourceActionTone.Warn, true),
        RssRefreshingSource("本地系统通知", "系统 · 1 个入口 · 列表 · 单 URL", "0", "暂停", RssSourceActionTone.Muted, false)
    )
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
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            RssRefreshingMiniButton(label = "导入", iconRes = R.drawable.reader_ic_upload, onClick = onManageSources)
            Spacer(Modifier.width(6.dp))
            RssRefreshingMiniButton(label = "新建", iconRes = R.drawable.reader_ic_add, onClick = onManageSources)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("全部", "开源项目", "社区", "需登录", "暂停").forEachIndexed { index, label ->
                RssRefreshingChip(label = label, active = index == 0)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
                .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
        ) {
            sources.forEachIndexed { index, source ->
                RssRefreshingSourceRow(source = source)
                if (index != sources.lastIndex) RssSourceActionDivider()
            }
        }
    }
}

@Composable
private fun RssRefreshingSourceRow(source: RssRefreshingSource) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssSourceActionIconCircle(
            iconRes = if (source.enabled) R.drawable.reader_ic_rss else R.drawable.reader_ic_offline,
            size = 32
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = source.name,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.meta,
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = source.unread,
            style = rssSourceActionTitleStyle().copy(fontSize = 16.sp, lineHeight = 20.sp),
            color = extra.primaryDark,
            modifier = Modifier.defaultMinSize(minWidth = 24.dp),
            maxLines = 1
        )
        RssSourceActionBadge(label = source.status, tone = source.tone)
    }
}

@Composable
private fun RssRefreshingArticleSection(onOpenArticle: () -> Unit) {
    val articles = listOf(
        RssRefreshingArticle("Reader UI 前端输入件更新说明", "GitHub Releases · 10:18 · 开源项目", "新增发现页状态路由、阅读控制层响应式约束，并补充 RSS 页面结构规划。", true, true),
        RssRefreshingArticle("订阅源规则解析失败排查", "书源维护公告 · 09:52 · 维护", "部分订阅源返回 HTML 而不是 XML，已建议检查 Cookie、登录态和正文提取规则。", true, false),
        RssRefreshingArticle("Legado 订阅源配置经验整理", "阅读器版本讨论 · 昨天 · 社区", "社区整理了单 URL 源、分类入口、文章样式和 WebView 正文处理的常见配置方式。", true, false)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 30.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最近未读",
                style = rssSourceActionTitleStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            RssRefreshingMiniButton(label = "查看全部", iconRes = R.drawable.reader_ic_nav_list, onClick = {})
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), ReaderShapes.md)
                .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.72f), ReaderShapes.md)
        ) {
            articles.forEachIndexed { index, article ->
                RssRefreshingArticleRow(article = article, onClick = onOpenArticle)
                if (index != articles.lastIndex) RssSourceActionDivider()
            }
        }
    }
}

@Composable
private fun RssRefreshingArticleRow(article: RssRefreshingArticle, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 82.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .background(if (article.unread) colors.primary else extra.muted, ReaderShapes.pill)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = article.title,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = article.meta,
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = article.desc,
                style = rssSourceActionMetaStyle().copy(color = extra.infoLayer),
                color = extra.infoLayer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(id = if (article.starred) R.drawable.reader_ic_bookmark else R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun RssRefreshingMiniButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(readerExtraColors().metaBackground.copy(alpha = 0.84f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().controlInk,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = rssSourceActionButtonStyle(),
            color = readerExtraColors().controlInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssRefreshingChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .background(if (active) colors.primary else extra.metaBackground, ReaderShapes.pill)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssSourceActionButtonStyle(),
            color = if (active) colors.onPrimary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RssSourceVarsScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDebug: () -> Unit,
    onDone: () -> Unit
) {
    val variables = remember { rssSourceVariables() }
    RssSourceActionScaffold(
        title = "源变量",
        onBack = onBack,
        trailing = {
            RssSourceActionTopPill(label = "编辑", onClick = onEdit)
        },
        bottom = {
            RssSourceActionBottomActions(
                secondary = "测试变量",
                primary = "完成",
                onSecondary = onDebug,
                onPrimary = onDone
            )
        }
    ) {
        item {
            RssSourceActionHeaderPanel(
                iconRes = R.drawable.reader_ic_code,
                title = "GitHub Releases",
                meta = "变量作用于请求头、分类 URL、正文规则和 WebView 注入脚本"
            )
        }
        item { RssSourceActionInfoList(rows = variables) }
    }
}

@Composable
fun RssSourceLoginScreen(
    onBack: () -> Unit,
    onWebLogin: () -> Unit,
    onCookie: () -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    val actions = remember {
        listOf(
            RssSourceActionButtonSpec("网页登录", R.drawable.reader_ic_globe, onWebLogin),
            RssSourceActionButtonSpec("提取 Cookie", R.drawable.reader_ic_copy, onCookie),
            RssSourceActionButtonSpec("测试登录态", R.drawable.reader_ic_refresh, onTest),
            RssSourceActionButtonSpec("清除登录", R.drawable.reader_ic_trash, onClear)
        )
    }

    RssSourceActionScaffold(
        title = "源登录",
        onBack = onBack,
        bottom = {
            RssSourceActionBottomActions(
                secondary = "返回操作",
                primary = "完成",
                onSecondary = onBack,
                onPrimary = onDone
            )
        }
    ) {
        item {
            RssSourceActionDebugPanel(
                iconRes = R.drawable.reader_ic_shield,
                title = "书源维护公告",
                meta = "网页登录 · Cookie 保存 · 登录态检测",
                rows = listOf(
                    RssSourceActionInfoRow("登录地址", "https://example.com/login?from=rss"),
                    RssSourceActionInfoRow("Cookie 状态", "reader_session=****** · 2 天后过期 · 已关联当前订阅源"),
                    RssSourceActionInfoRow("检测方式", "刷新前请求个人中心，401/403 时提示重新登录。")
                )
            )
        }
        item { RssSourceActionGrid(actions = actions, compact = true) }
    }
}

@Composable
fun RssSourceLoginWebScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    RssSourceActionScaffold(
        title = "网页登录",
        onBack = onBack,
        bottom = {
            RssSourceActionBottomActions(
                secondary = "返回登录",
                primary = "登录完成",
                onSecondary = onBack,
                onPrimary = onDone
            )
        }
    ) {
        item {
            RssSourceActionWebPreview(
                iconRes = R.drawable.reader_ic_shield,
                host = "example.com/login",
                meta = "来自书源维护公告 · 登录完成后回写 Cookie",
                title = "登录页面预览",
                body = "实际应用中这里打开内置 WebView。登录成功后提取 Cookie、Token 和登录检测结果，返回源登录页。"
            )
        }
    }
}

@Composable
fun RssSourceLoginCookieScreen(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    RssSourceActionScaffold(
        title = "Cookie 提取",
        onBack = onBack,
        bottom = {
            RssSourceActionBottomActions(
                secondary = "返回",
                primary = "保存凭据",
                onSecondary = onBack,
                onPrimary = onSave
            )
        }
    ) {
        item {
            RssSourceActionDebugPanel(
                iconRes = R.drawable.reader_ic_copy,
                title = "已提取登录凭据",
                meta = "只作用于当前 RSS 源，不覆盖其他订阅源",
                rows = listOf(
                    RssSourceActionInfoRow("Cookie", "reader_session=******; expires=2026-06-28; path=/"),
                    RssSourceActionInfoRow("Token", "从 localStorage.reader_token 提取，刷新源时自动附加。"),
                    RssSourceActionInfoRow("检测结果", "个人中心返回 200，下一次刷新不会进入登录错误状态。")
                )
            )
        }
    }
}

@Composable
fun RssReadRecordScreen(
    onBack: () -> Unit,
    onBackToList: () -> Unit,
    onOpenDetail: () -> Unit,
    onClear: () -> Unit
) {
    val records = remember { rssSourceReadRecords() }
    RssSourceActionScaffold(
        title = "阅读记录",
        onBack = onBack,
        bottom = {
            RssSourceActionBottomActions(
                secondary = "返回列表",
                primary = "清空记录",
                onSecondary = onBackToList,
                onPrimary = onClear
            )
        }
    ) {
        item {
            RssSourceActionRecordList(
                records = records,
                iconRes = R.drawable.reader_ic_clock,
                onRowClick = onOpenDetail
            )
        }
    }
}

@Composable
fun RssSourceConfirmScreen(
    title: String,
    @DrawableRes iconRes: Int,
    heading: String,
    copy: String,
    detail: String? = null,
    cancelLabel: String = "取消",
    confirmLabel: String = "确认",
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    RssSourceActionScaffold(
        title = title,
        onBack = onCancel,
        bottom = {
            RssSourceActionBottomActions(
                secondary = cancelLabel,
                primary = confirmLabel,
                onSecondary = onCancel,
                onPrimary = onConfirm
            )
        }
    ) {
        item {
            RssSourceActionConfirmCard(
                iconRes = iconRes,
                heading = heading,
                copy = copy,
                detail = detail
            )
        }
    }
}

@Composable
fun RssSourceBatchScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDisable: () -> Unit,
    onDone: () -> Unit
) {
    val sources = remember { rssSourceBatchItems() }
    RssSourceActionScaffold(
        title = "批量管理",
        onBack = onBack,
        trailing = {
            RssSourceActionTopPill(label = "完成", onClick = onDone)
        },
        bottom = {
            RssSourceActionBottomActions(
                secondary = "导出",
                primary = "禁用",
                onSecondary = onExport,
                onPrimary = onDisable
            )
        }
    ) {
        item { RssSourceBatchSummary() }
        item { RssSourceBatchList(sources = sources) }
    }
}

@Composable
fun RssSourceExportScreen(
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit
) {
    val entries = remember { rssSourceExportEntries() }
    RssSourceActionScaffold(
        title = "导出订阅源",
        onBack = onBack,
        bottom = {
            RssSourceActionBottomActions(
                secondary = "返回",
                primary = "导出",
                onSecondary = onBack,
                onPrimary = onExport
            )
        }
    ) {
        item { RssSourceExportPanel() }
        item { RssSourceExportList(entries = entries, onPreview = onPreview) }
    }
}

@Composable
fun RssSourceExportDetailScreen(
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    RssSourceActionScaffold(
        title = "导出预览",
        onBack = onBack,
        bottom = {
            RssSourceActionBottomActions(
                secondary = "返回",
                primary = "导出此源",
                onSecondary = onBack,
                onPrimary = onExport
            )
        }
    ) {
        item {
            RssSourceActionDebugPanel(
                iconRes = R.drawable.reader_ic_download,
                title = "GitHub Releases",
                meta = "导出项预览 · 不包含 Cookie",
                rows = listOf(
                    RssSourceActionInfoRow("基础字段", "名称、源地址、分组、启用状态、分类入口。"),
                    RssSourceActionInfoRow("解析规则", "列表、下一页、正文、WebView 注入脚本和资源过滤规则。"),
                    RssSourceActionInfoRow("安全字段", "登录 Cookie、Token 和本地账号信息不参与导出。")
                )
            )
        }
    }
}

@Composable
private fun RssSourceActionScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    bottom: @Composable () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(Modifier.fillMaxSize()) {
            RssSourceActionTopBar(title = title, onBack = onBack, trailing = trailing)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            bottom()
        }
    }
}

@Composable
private fun RssSourceBatchSummary() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 38.dp)
            .background(colors.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已选 2 个订阅源",
            style = rssSourceActionTitleStyle().copy(fontSize = 12.sp, lineHeight = 15.sp),
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        RssSourceBatchMiniButton(label = "反选")
        RssSourceBatchMiniButton(label = "全选")
    }
}

@Composable
private fun RssSourceBatchMiniButton(label: String) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssSourceActionButtonStyle(),
            color = extra.primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceBatchList(sources: List<RssSourceBatchItem>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        sources.forEachIndexed { index, source ->
            RssSourceBatchRow(source = source)
            if (index != sources.lastIndex) RssSourceActionDivider()
        }
    }
}

@Composable
private fun RssSourceBatchRow(source: RssSourceBatchItem) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(if (source.enabled) colors.surface.copy(alpha = 0f) else extra.metaBackground.copy(alpha = 0.42f))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssSourceActionIconCircle(
            iconRes = if (source.selected) R.drawable.reader_ic_check else R.drawable.reader_ic_rss,
            size = 28
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = source.name,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${source.group} · ${source.status} · ${source.unread}",
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssSourceActionBadge(
            label = if (source.enabled) "启用" else "暂停",
            tone = if (source.enabled) RssSourceActionTone.Good else RssSourceActionTone.Muted
        )
    }
}

@Composable
private fun RssSourceExportPanel() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 38.dp)
                .background(extra.metaBackground.copy(alpha = 0.70f), ReaderShapes.pill)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_download),
                contentDescription = null,
                tint = extra.muted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "reader-rss-sources-20260626.json",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight(650)
                ),
                color = extra.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssSourceExportChipRow()
    }
}

@Composable
private fun RssSourceExportChipRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("已选源" to true, "启用源" to true, "包含登录配置" to false, "包含分组" to true).forEach { (label, active) ->
            RssSourceExportChip(label = label, active = active)
        }
    }
}

@Composable
private fun RssSourceExportChip(label: String, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(if (active) colors.primary.copy(alpha = 0.12f) else extra.metaBackground.copy(alpha = 0.84f), ReaderShapes.pill)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssSourceActionButtonStyle(),
            color = if (active) extra.primaryDark else extra.navInactive,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssSourceExportList(
    entries: List<RssSourceExportEntry>,
    onPreview: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        entries.forEachIndexed { index, entry ->
            RssSourceExportRow(entry = entry, onPreview = onPreview)
            if (index != entries.lastIndex) RssSourceActionDivider()
        }
    }
}

@Composable
private fun RssSourceExportRow(
    entry: RssSourceExportEntry,
    onPreview: () -> Unit
) {
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
        RssSourceActionIconCircle(iconRes = R.drawable.reader_ic_check, size = 28)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.name,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.meta,
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssSourceExportPreviewButton(onPreview = onPreview)
    }
}

@Composable
private fun RssSourceExportPreviewButton(onPreview: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onPreview)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "预览",
            style = rssSourceActionButtonStyle(),
            color = extra.primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceActionTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)?
) {
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
        if (trailing != null) {
            trailing()
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun RssSourceActionTopPill(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 32.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssSourceActionButtonStyle(),
            color = extra.primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceActionSourceCard(source: RssSourceActionSource) {
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
        RssSourceActionIconCircle(iconRes = R.drawable.reader_ic_rss, size = 32)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = source.name,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${source.group} · ${source.categories} 个入口 · ${source.rule}",
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssSourceActionBadge(label = source.status, tone = source.tone)
    }
}

@Composable
private fun RssSourceActionGrid(
    actions: List<RssSourceActionButtonSpec>,
    compact: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        actions.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                row.forEach { action ->
                    RssSourceActionGridButton(
                        action = action,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RssSourceActionGridButton(
    action: RssSourceActionButtonSpec,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) 54.dp else 58.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.28f), ReaderShapes.md)
            .clickable(onClick = action.onClick)
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = action.iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = action.label,
            style = rssSourceActionButtonStyle(),
            color = extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RssSourceActionHeaderPanel(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssSourceActionIconCircle(iconRes = iconRes, size = 30)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RssSourceActionDebugPanel(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    rows: List<RssSourceActionInfoRow>
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp)
    ) {
        RssSourceActionDebugHeader(iconRes = iconRes, title = title, meta = meta)
        rows.forEachIndexed { index, row ->
            if (index != 0) RssSourceActionDivider()
            RssSourceActionInfoRow(row = row)
        }
    }
}

@Composable
private fun RssSourceActionDebugHeader(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssSourceActionIconCircle(iconRes = iconRes, size = 30)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RssSourceActionInfoList(rows: List<RssSourceActionInfoRow>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        rows.forEachIndexed { index, row ->
            RssSourceActionInfoRow(row = row)
            if (index != rows.lastIndex) RssSourceActionDivider()
        }
    }
}

@Composable
private fun RssSourceActionInfoRow(row: RssSourceActionInfoRow) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(if (row.warning) extra.accent.copy(alpha = 0.10f) else colors.surface.copy(alpha = 0f), ReaderShapes.sm)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        row.group?.let {
            Text(text = it, style = rssSourceActionMetaStyle(), color = extra.muted, maxLines = 1)
        }
        Text(
            text = row.title,
            style = rssSourceActionTitleStyle(),
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.message,
            style = rssSourceActionMetaStyle(),
            color = if (row.group == null) extra.muted else extra.infoLayer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssSourceActionRecordList(
    records: List<RssSourceActionRecord>,
    @DrawableRes iconRes: Int,
    onRowClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        records.forEachIndexed { index, record ->
            RssSourceActionRecordRow(record = record, iconRes = iconRes, onClick = onRowClick)
            if (index != records.lastIndex) RssSourceActionDivider()
        }
    }
}

@Composable
private fun RssSourceActionRecordRow(
    record: RssSourceActionRecord,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssSourceActionIconCircle(iconRes = iconRes, size = 28)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = record.title,
                style = rssSourceActionTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = record.meta,
                style = rssSourceActionMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun RssSourceActionWebPreview(
    @DrawableRes iconRes: Int,
    host: String,
    meta: String,
    title: String,
    body: String
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RssSourceActionIconCircle(iconRes = iconRes, size = 30)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = host,
                    style = rssSourceActionTitleStyle(),
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = rssSourceActionMetaStyle(),
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 210.dp)
                .background(extra.metaBackground.copy(alpha = 0.48f), ReaderShapes.md)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight(900)
                ),
                color = colors.onBackground
            )
            Text(
                text = body,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight(500)
                ),
                color = extra.muted
            )
            Spacer(Modifier.height(4.dp))
            RssSourceActionWebBars()
        }
    }
}

@Composable
private fun RssSourceActionWebBars() {
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1f, 0.72f, 0.52f).forEach { widthFraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(10.dp)
                    .background(extra.hairline.copy(alpha = 0.42f), ReaderShapes.pill)
            )
        }
    }
}

@Composable
private fun RssSourceActionConfirmCard(
    @DrawableRes iconRes: Int,
    heading: String,
    copy: String,
    detail: String?
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
        RssSourceActionIconCircle(iconRes = iconRes, size = 44)
        Spacer(Modifier.height(10.dp))
        Text(
            text = heading,
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
            text = copy,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(500)
            ),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (detail != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = detail,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight(500)
                ),
                color = extra.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RssSourceActionBottomActions(
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
            RssSourceActionBottomButton(
                label = secondary,
                primary = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
            RssSourceActionBottomButton(
                label = primary,
                primary = true,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssSourceActionBottomButton(
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
private fun RssSourceActionIconCircle(@DrawableRes iconRes: Int, size: Int) {
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
private fun RssSourceActionBadge(label: String, tone: RssSourceActionTone) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = when (tone) {
        RssSourceActionTone.Good -> colors.primary.copy(alpha = 0.10f)
        RssSourceActionTone.Warn -> extra.accent.copy(alpha = 0.12f)
        RssSourceActionTone.Muted -> extra.hairline.copy(alpha = 0.42f)
    }
    val foreground = when (tone) {
        RssSourceActionTone.Good -> extra.primaryDark
        RssSourceActionTone.Warn -> extra.accent
        RssSourceActionTone.Muted -> extra.muted
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 22.dp)
            .background(background, ReaderShapes.pill)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssSourceActionBadgeStyle(),
            color = foreground,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceActionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.48f))
    )
}

private data class RssSourceActionSource(
    val name: String,
    val group: String,
    val categories: Int,
    val rule: String,
    val status: String,
    val tone: RssSourceActionTone
)

private data class RssSourceActionButtonSpec(
    val label: String,
    @DrawableRes val iconRes: Int,
    val onClick: () -> Unit
)

private data class RssSourceActionInfoRow(
    val title: String,
    val message: String,
    val group: String? = null,
    val warning: Boolean = false
)

private data class RssSourceActionRecord(
    val title: String,
    val meta: String
)

private data class RssRefreshingSource(
    val name: String,
    val meta: String,
    val unread: String,
    val status: String,
    val tone: RssSourceActionTone,
    val enabled: Boolean
)

private data class RssRefreshingArticle(
    val title: String,
    val meta: String,
    val desc: String,
    val unread: Boolean,
    val starred: Boolean
)

private data class RssSourceBatchItem(
    val name: String,
    val group: String,
    val status: String,
    val unread: String,
    val selected: Boolean,
    val enabled: Boolean
)

private data class RssSourceExportEntry(
    val name: String,
    val meta: String
)

private enum class RssSourceActionTone { Good, Warn, Muted }

private fun rssSourceActionSource() = RssSourceActionSource(
    name = "GitHub Releases",
    group = "开源项目",
    categories = 3,
    rule = "默认 RSS 解析",
    status = "正常",
    tone = RssSourceActionTone.Good
)

private fun rssSourceVariables() = listOf(
    RssSourceActionInfoRow("{{page}}", "当前分页，从 1 开始递增，用于列表和下一页规则。", group = "请求变量"),
    RssSourceActionInfoRow("{{sourceUrl}}", "当前订阅源地址，调试和跳转拦截时可引用。", group = "请求变量"),
    RssSourceActionInfoRow("{{cookie}}", "网页登录后写入，刷新订阅源和打开原文时共用。", group = "登录变量"),
    RssSourceActionInfoRow("{{token}}", "从登录页脚本提取，过期后进入登录子页面刷新。", group = "登录变量"),
    RssSourceActionInfoRow("{{userAgent}}", "Reader UI WebView UA，必要时覆盖为移动端 UA。", group = "设备变量")
)

private fun rssSourceReadRecords() = listOf(
    RssSourceActionRecord("Reader UI 前端输入件更新说明", "GitHub Releases · 今天 09:30 · 已读 86%"),
    RssSourceActionRecord("阅读器路线图讨论摘要", "阅读器版本讨论 · 昨天 21:18 · 已读 42%"),
    RssSourceActionRecord("书源维护公告：登录检测更新", "书源维护公告 · 2 天前 · 已读 100%")
)

private fun rssSourceBatchItems() = listOf(
    RssSourceBatchItem("GitHub Releases", "开源项目", "正常", "2 条未读", selected = true, enabled = true),
    RssSourceBatchItem("阅读器版本讨论", "社区", "正常", "12 条未读", selected = true, enabled = true),
    RssSourceBatchItem("书源维护公告", "维护", "需要登录", "无未读", selected = false, enabled = true),
    RssSourceBatchItem("旧版更新源", "系统", "已暂停", "无未读", selected = false, enabled = false)
)

private fun rssSourceExportEntries() = listOf(
    RssSourceExportEntry("GitHub Releases", "JSON · 保留分组、启用状态和解析规则"),
    RssSourceExportEntry("阅读器版本讨论", "JSON · 保留分组、启用状态和解析规则")
)

private fun rssSourceActionTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssSourceActionMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun rssSourceActionButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)

private fun rssSourceActionBadgeStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight(850)
)
