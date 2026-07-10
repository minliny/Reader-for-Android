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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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

@Composable
fun RssSubscriptionManagementScreen(
    onBack: () -> Unit,
    onCreateSource: () -> Unit,
    onImportSource: () -> Unit,
    onRuleSubscription: () -> Unit,
    onManageGroups: () -> Unit,
    onSourceActions: () -> Unit,
    onBatch: () -> Unit,
    onExport: () -> Unit
) {
    var activeFilter by remember { mutableStateOf("全部") }
    var autoRefresh by remember { mutableStateOf(true) }
    var unreadNotice by remember { mutableStateOf(true) }
    val sources = remember { rssManagedSources() }
    val filteredSources = sources.filter { source ->
        when (activeFilter) {
            "全部" -> true
            "已启用" -> source.enabled
            "需登录" -> source.login
            "无分组" -> source.group.isBlank()
            "暂停" -> !source.enabled
            else -> true
        }
    }

    LibraryShellFrame(
        backTopBar = { RssManagementTopBar(onBack = onBack) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    RssManageActionGrid(
                        onCreateSource = onCreateSource,
                        onImportSource = onImportSource,
                        onRuleSubscription = onRuleSubscription,
                        onManageGroups = onManageGroups
                    )
                }
                item {
                    RssManageFilterRow(
                        activeFilter = activeFilter,
                        onFilter = { activeFilter = it }
                    )
                }
                item {
                    RssManageSourceList(
                        sources = filteredSources,
                        onSourceActions = onSourceActions
                    )
                }
                item {
                    RssManageBatchRow(
                        onBatch = onBatch,
                        onDisable = onBatch,
                        onExport = onExport
                    )
                }
                item {
                    RssRefreshSettingsCard(
                        autoRefresh = autoRefresh,
                        unreadNotice = unreadNotice,
                        onAutoRefresh = { autoRefresh = it },
                        onUnreadNotice = { unreadNotice = it }
                    )
                }
            }
        }
    )
}

@Composable
private fun RssManagementTopBar(onBack: () -> Unit) {
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
            text = "RSS 订阅管理",
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
private fun RssManageActionGrid(
    onCreateSource: () -> Unit,
    onImportSource: () -> Unit,
    onRuleSubscription: () -> Unit,
    onManageGroups: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RssManageActionTile("新建", R.drawable.reader_ic_add, onCreateSource, Modifier.weight(1f))
        RssManageActionTile("导入", R.drawable.reader_ic_upload, onImportSource, Modifier.weight(1f))
        RssManageActionTile("规则订阅", R.drawable.reader_ic_sync, onRuleSubscription, Modifier.weight(1f))
        RssManageActionTile("分组", R.drawable.reader_ic_folder, onManageGroups, Modifier.weight(1f))
    }
}

@Composable
private fun RssManageActionTile(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.58f), ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = rssManageButtonStyle(),
            color = extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssManageFilterRow(activeFilter: String, onFilter: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("全部", "已启用", "需登录", "无分组", "暂停").forEach { filter ->
            RssManageChip(
                text = filter,
                active = activeFilter == filter,
                onClick = { onFilter(filter) }
            )
        }
    }
}

@Composable
private fun RssManageSourceList(sources: List<RssManagedSource>, onSourceActions: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        sources.forEachIndexed { index, source ->
            RssManageSourceRow(source = source, onSourceActions = onSourceActions)
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
private fun RssManageSourceRow(source: RssManagedSource, onSourceActions: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .alpha(if (source.enabled) 1f else 0.62f)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssManageIconCircle(if (source.enabled) R.drawable.reader_ic_rss else R.drawable.reader_ic_offline)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = source.name,
                style = rssManageTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${if (source.group.isBlank()) "无分组" else source.group} · ${if (source.unread > 0) "${source.unread} 条未读" else "无未读"} · ${source.latest} · ${source.articleStyle}",
                style = rssManageMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssManageStatusBadge(label = source.status, tone = source.tone)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clickable(onClick = onSourceActions),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_more),
                contentDescription = "更多操作",
                tint = extra.muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RssManageBatchRow(onBatch: () -> Unit, onDisable: () -> Unit, onExport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 38.dp)
            .background(colors.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "已选 2 个",
            style = rssManageMetaStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, lineHeight = 14.sp, fontWeight = FontWeight(850)),
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        RssManageSmallButton(label = "批量", onClick = onBatch)
        RssManageSmallButton(label = "禁用", onClick = onDisable)
        RssManageSmallButton(label = "导出", onClick = onExport)
    }
}

@Composable
private fun RssRefreshSettingsCard(
    autoRefresh: Boolean,
    unreadNotice: Boolean,
    onAutoRefresh: (Boolean) -> Unit,
    onUnreadNotice: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "刷新与提醒",
            style = rssManageSectionTitleStyle(),
            color = colors.onBackground,
            maxLines = 1
        )
        RssRefreshSettingRow(
            iconRes = R.drawable.reader_ic_refresh,
            title = "自动刷新",
            meta = "Wi-Fi 下每 30 分钟刷新一次",
            checked = autoRefresh,
            onChecked = onAutoRefresh
        )
        RssRefreshSettingRow(
            iconRes = R.drawable.reader_ic_bell,
            title = "未读提醒",
            meta = "只提醒重点订阅源",
            checked = unreadNotice,
            onChecked = onUnreadNotice
        )
    }
}

@Composable
private fun RssRefreshSettingRow(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssManageIconCircle(iconRes = iconRes)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = rssManageTitleStyle(),
                color = colors.onBackground,
                maxLines = 1
            )
            Text(
                text = meta,
                style = rssManageMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssManageSwitch(checked = checked, onChecked = onChecked)
    }
}

@Composable
private fun RssManageSwitch(checked: Boolean, onChecked: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .background(if (checked) colors.primary else extra.hairline.copy(alpha = 0.68f), ReaderShapes.pill)
            .clickable { onChecked(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 16.dp else 0.dp)
                .size(18.dp)
                .background(colors.surface, ReaderShapes.pill)
        )
    }
}

@Composable
private fun RssManageSmallButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssManageButtonStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssManageChip(text: String, active: Boolean, onClick: () -> Unit) {
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
            style = rssManageButtonStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, lineHeight = 14.sp),
            color = if (active) colors.onPrimary else extra.navInactive,
            maxLines = 1
        )
    }
}

@Composable
private fun RssManageIconCircle(@DrawableRes iconRes: Int) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(colors.primary.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RssManageStatusBadge(label: String, tone: RssManageTone) {
    val extra = readerExtraColors()
    val color = when (tone) {
        RssManageTone.Good -> extra.forest
        RssManageTone.Warn -> extra.accent
        RssManageTone.Muted -> extra.muted
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
            style = rssManageMetaStyle().copy(fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value, lineHeight = 12.sp, fontWeight = FontWeight(850)),
            color = color,
            maxLines = 1
        )
    }
}

private data class RssManagedSource(
    val name: String,
    val group: String,
    val unread: Int,
    val latest: String,
    val status: String,
    val tone: RssManageTone,
    val enabled: Boolean,
    val articleStyle: String,
    val login: Boolean
)

private enum class RssManageTone { Good, Warn, Muted }

private fun rssManagedSources() = listOf(
    RssManagedSource("GitHub Releases", "开源项目", 6, "10:18", "正常", RssManageTone.Good, true, "列表", false),
    RssManagedSource("阅读器版本讨论", "社区", 12, "09:42", "有更新", RssManageTone.Good, true, "图文", false),
    RssManagedSource("书源维护公告", "维护", 2, "昨天", "需登录", RssManageTone.Warn, true, "紧凑", true),
    RssManagedSource("本地系统通知", "", 0, "周二", "暂停", RssManageTone.Muted, false, "列表", false)
)

private fun rssManageSectionTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.SECTION_TITLE.value,
    lineHeight = 18.sp,
    fontWeight = FontWeight(900)
)

private fun rssManageTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssManageMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun rssManageButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
