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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.tokens.ReaderTypeToken
import com.reader.android.R
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun RssRuleSubscriptionScreen(
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onCreate: () -> Unit
) {
    val subscriptions = remember { rssRuleSubscriptions() }
    RssRuleScaffold(
        title = "规则订阅",
        onBack = onBack,
        bottom = null
    ) {
        item {
            RssRuleModeRow()
        }
        item {
            RssRuleSubscriptionList(subscriptions = subscriptions, onOpenDetail = onOpenDetail)
        }
        item {
            RssRuleActionRow(onOpenDetail = onOpenDetail, onCreate = onCreate)
        }
    }
}

@Composable
fun RssRuleSubscriptionDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onApply: () -> Unit
) {
    RssRuleScaffold(
        title = "订阅详情",
        onBack = onBack,
        bottom = {
            RssRuleBottomActions(
                secondary = "编辑",
                primary = "应用更新",
                onSecondary = onEdit,
                onPrimary = onApply
            )
        }
    ) {
        item {
            RssRuleDetailPanel()
        }
        item {
            RssRuleChangeList()
        }
    }
}

@Composable
fun RssRuleSubscriptionEditScreen(
    onBack: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    val fields = remember { rssRuleEditFields() }
    RssRuleScaffold(
        title = "编辑规则订阅",
        onBack = onBack,
        bottom = {
            RssRuleBottomActions(
                secondary = "测试订阅",
                primary = "保存",
                onSecondary = onTest,
                onPrimary = onSave
            )
        }
    ) {
        item {
            RssRuleEditList(fields = fields)
        }
    }
}

@Composable
fun RssRuleSubscriptionTestScreen(
    onBack: () -> Unit,
    onViewResult: () -> Unit
) {
    RssRuleScaffold(
        title = "测试规则订阅",
        onBack = onBack,
        bottom = {
            RssRuleBottomActions(
                secondary = "返回编辑",
                primary = "查看结果",
                onSecondary = onBack,
                onPrimary = onViewResult
            )
        }
    ) {
        item {
            RssRuleTestPanel()
        }
    }
}

@Composable
fun RssRuleSubscriptionApplyScreen(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    RssRuleScaffold(
        title = "应用订阅更新",
        onBack = onBack,
        bottom = {
            RssRuleBottomActions(
                secondary = "取消",
                primary = "进入导入预览",
                onSecondary = onCancel,
                onPrimary = onConfirm
            )
        }
    ) {
        item {
            RssRuleApplyCard()
        }
    }
}

@Composable
private fun RssRuleScaffold(
    title: String,
    onBack: () -> Unit,
    bottom: (@Composable () -> Unit)?,
    content: LazyListScope.() -> Unit
) {
    LibraryShellFrame(
        backTopBar = { RssRuleTopBar(title = title, onBack = onBack) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (bottom == null) 24.dp else 108.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        },
        bottomActionHost = {
            bottom?.invoke()
        }
    )
}

@Composable
private fun RssRuleTopBar(title: String, onBack: () -> Unit) {
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
private fun RssRuleModeRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("源列表", "全部", "收藏", "规则订阅").forEach { label ->
            RssRuleChip(text = label, active = label == "规则订阅")
        }
    }
}

@Composable
private fun RssRuleSubscriptionList(
    subscriptions: List<RssRuleSubscriptionItem>,
    onOpenDetail: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        subscriptions.forEachIndexed { index, subscription ->
            RssRuleSubscriptionRow(subscription = subscription, onOpenDetail = onOpenDetail)
            if (index != subscriptions.lastIndex) {
                RssRuleDivider()
            }
        }
    }
}

@Composable
private fun RssRuleSubscriptionRow(
    subscription: RssRuleSubscriptionItem,
    onOpenDetail: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onOpenDetail)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssRuleIconCircle(iconRes = subscription.iconRes)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = subscription.name,
                style = rssRuleTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${subscription.type} · ${subscription.url}",
                style = rssRuleMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssRuleBadge(label = subscription.update)
    }
}

@Composable
private fun RssRuleActionRow(onOpenDetail: () -> Unit, onCreate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RssRuleActionButton(
            label = "打开订阅",
            iconRes = R.drawable.reader_ic_upload,
            onClick = onOpenDetail,
            modifier = Modifier.weight(1f)
        )
        RssRuleActionButton(
            label = "新增",
            iconRes = R.drawable.reader_ic_add,
            onClick = onCreate,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RssRuleDetailPanel() {
    val rows = listOf(
        "订阅地址" to "https://example.com/rss-source.json",
        "更新策略" to "Wi-Fi 下自动更新；保留本地启用状态、分组和登录态。",
        "最近变更" to "新增 2 个源，更新 1 个正文规则，跳过 1 个本地冲突。"
    )
    RssRuleDebugPanel(
        iconRes = R.drawable.reader_ic_sync,
        title = "社区 RSS 源订阅",
        meta = "RSS 源 · 自动更新 · 上次同步 10:18",
        rows = rows.map { RssRuleDebugRow(it.first, it.second) }
    )
}

@Composable
private fun RssRuleTestPanel() {
    RssRuleDebugPanel(
        iconRes = R.drawable.reader_ic_bug,
        title = "社区 RSS 源订阅",
        meta = "请求订阅地址 · 校验结构 · 生成导入预览",
        rows = listOf(
            RssRuleDebugRow("1. 请求订阅地址", "https://example.com/rss-source.json 返回 200，内容类型 application/json。"),
            RssRuleDebugRow("2. 解析订阅内容", "12 个 RSS 源、2 个更新项、1 个本地冲突。"),
            RssRuleDebugRow("3. 冲突策略", "保留本地名称、分组、启用状态，不覆盖登录凭据。")
        )
    )
}

@Composable
private fun RssRuleDebugPanel(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    rows: List<RssRuleDebugRow>
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RssRuleIconCircle(iconRes = iconRes)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = rssRuleTitleStyle(),
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = rssRuleMetaStyle(),
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        rows.forEachIndexed { index, row ->
            if (index != 0) RssRuleDivider()
            RssRuleDebugResult(row = row)
        }
    }
}

@Composable
private fun RssRuleDebugResult(row: RssRuleDebugRow) {
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
        Text(
            text = row.title,
            style = rssRuleTitleStyle(),
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.message,
            style = rssRuleMetaStyle(),
            color = extra.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssRuleChangeList() {
    val changes = remember { rssRuleChangeEntries() }
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        changes.forEachIndexed { index, change ->
            RssRuleChangeRow(change)
            if (index != changes.lastIndex) RssRuleDivider()
        }
    }
}

@Composable
private fun RssRuleChangeRow(change: RssRuleChange) {
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
        RssRuleIconCircle(
            iconRes = if (change.selected) R.drawable.reader_ic_check else R.drawable.reader_ic_rss,
            selected = change.selected
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = change.name,
                style = rssRuleTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = change.meta,
                style = rssRuleMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssRuleStatusBadge(label = change.status, tone = change.tone)
    }
}

@Composable
private fun RssRuleEditList(fields: List<RssRuleEditField>) {
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
                Text(text = field.group, style = rssRuleMetaStyle(), color = extra.muted, maxLines = 1)
                Text(
                    text = field.label,
                    style = rssRuleTitleStyle(),
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = field.value,
                    style = rssRuleMetaStyle(),
                    color = extra.infoLayer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (index != fields.lastIndex) RssRuleDivider()
        }
    }
}

@Composable
private fun RssRuleApplyCard() {
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
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.primary.copy(alpha = 0.12f), ReaderShapes.pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_sync),
                contentDescription = null,
                tint = extra.primaryDark,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "应用社区 RSS 源订阅更新？",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(850)
            ),
            color = colors.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "将新增 2 个源、更新 1 个规则，并跳过 1 个本地冲突。登录凭据不会被覆盖。",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
                lineHeight = 22.sp,
                fontWeight = FontWeight(500)
            ),
            color = extra.infoLayer,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RssRuleBottomActions(
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
            RssRuleBottomButton(
                label = secondary,
                primary = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
            RssRuleBottomButton(
                label = primary,
                primary = true,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssRuleBottomButton(
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
                fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
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
private fun RssRuleActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
            style = rssRuleButtonStyle(),
            color = extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssRuleChip(text: String, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(if (active) colors.primary else extra.metaBackground.copy(alpha = 0.84f), ReaderShapes.pill)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = rssRuleButtonStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, lineHeight = 14.sp),
            color = if (active) colors.onPrimary else extra.navInactive,
            maxLines = 1
        )
    }
}

@Composable
private fun RssRuleIconCircle(@DrawableRes iconRes: Int, selected: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(if (selected) colors.primary else colors.primary.copy(alpha = 0.10f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (selected) colors.onPrimary else extra.primaryDark,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun RssRuleBadge(label: String) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 22.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssRuleMetaStyle().copy(fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value, lineHeight = 12.sp, fontWeight = FontWeight(850)),
            color = readerExtraColors().primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssRuleStatusBadge(label: String, tone: RssRuleTone) {
    val extra = readerExtraColors()
    val color = when (tone) {
        RssRuleTone.Good -> extra.forest
        RssRuleTone.Warn -> extra.accent
        RssRuleTone.Muted -> extra.muted
    }
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
            style = rssRuleMetaStyle().copy(fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value, lineHeight = 12.sp, fontWeight = FontWeight(850)),
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun RssRuleDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.48f))
    )
}

private data class RssRuleSubscriptionItem(
    val name: String,
    val type: String,
    val url: String,
    val update: String,
    @DrawableRes val iconRes: Int
)

private data class RssRuleDebugRow(
    val title: String,
    val message: String,
    val warning: Boolean = false
)

private data class RssRuleChange(
    val name: String,
    val meta: String,
    val selected: Boolean,
    val status: String,
    val tone: RssRuleTone
)

private data class RssRuleEditField(
    val group: String,
    val label: String,
    val value: String
)

private enum class RssRuleTone { Good, Warn, Muted }

private fun rssRuleSubscriptions() = listOf(
    RssRuleSubscriptionItem("社区 RSS 源订阅", "RSS 源", "https://example.com/rss-source.json", "自动更新", R.drawable.reader_ic_rss),
    RssRuleSubscriptionItem("默认书源订阅", "书源", "https://example.com/book-source.json", "手动", R.drawable.reader_ic_source_stack),
    RssRuleSubscriptionItem("替换规则同步", "替换规则", "https://example.com/replace-rule.json", "自动更新", R.drawable.reader_ic_replace)
)

private fun rssRuleChangeEntries() = listOf(
    RssRuleChange("社区 RSS 源合集", "新增 · 12 个源", selected = true, status = "新增", tone = RssRuleTone.Good),
    RssRuleChange("GitHub Releases", "已有 · 保留本地名称", selected = false, status = "跳过", tone = RssRuleTone.Muted),
    RssRuleChange("书源维护公告", "更新 · 规则版本更高", selected = true, status = "更新", tone = RssRuleTone.Warn)
)

private fun rssRuleEditFields() = listOf(
    RssRuleEditField("基础", "订阅名称", "社区 RSS 源订阅"),
    RssRuleEditField("基础", "订阅类型", "RSS 源"),
    RssRuleEditField("基础", "订阅地址", "https://example.com/rss-source.json"),
    RssRuleEditField("同步", "自动更新", "Wi-Fi 下自动"),
    RssRuleEditField("同步", "冲突策略", "保留本地名称、分组、启用状态"),
    RssRuleEditField("安全", "登录配置", "不覆盖 Cookie 和账号信息")
)

private fun rssRuleTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssRuleMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun rssRuleButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
