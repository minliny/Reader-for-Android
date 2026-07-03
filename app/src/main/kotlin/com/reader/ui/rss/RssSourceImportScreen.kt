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
fun RssSourceImportScreen(
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onImport: () -> Unit
) {
    var activeOptions by remember {
        mutableStateOf(setOf("保留名称", "保留分组", "保留启用状态"))
    }
    val entries = remember { rssSourceImportEntries() }

    RssSourceImportScaffold(
        title = "导入订阅源",
        onBack = onBack,
        bottom = {
            RssSourceImportBottomActions(
                secondary = "取消",
                primary = "导入 2 个",
                onSecondary = onBack,
                onPrimary = onImport
            )
        }
    ) {
        item {
            RssSourceImportPanel(
                activeOptions = activeOptions,
                onToggle = { option ->
                    activeOptions = if (option in activeOptions) {
                        activeOptions - option
                    } else {
                        activeOptions + option
                    }
                }
            )
        }
        item {
            RssSourceImportList(entries = entries, onOpenDetail = onOpenDetail)
        }
    }
}

@Composable
fun RssSourceImportDetailScreen(
    onBack: () -> Unit,
    onJoinImport: () -> Unit
) {
    RssSourceImportScaffold(
        title = "导入详情",
        onBack = onBack,
        bottom = {
            RssSourceImportBottomActions(
                secondary = "返回",
                primary = "加入导入",
                onSecondary = onBack,
                onPrimary = onJoinImport
            )
        }
    ) {
        item {
            RssSourceImportDetailPanel()
        }
    }
}

@Composable
fun RssSourceImportResultScreen(
    onBack: () -> Unit,
    onContinueImport: () -> Unit,
    onDone: () -> Unit
) {
    RssSourceImportScaffold(
        title = "导入完成",
        onBack = onBack,
        bottom = {
            RssSourceImportBottomActions(
                secondary = "继续导入",
                primary = "完成",
                onSecondary = onContinueImport,
                onPrimary = onDone
            )
        }
    ) {
        item {
            RssSourceImportResultCard()
        }
    }
}

@Composable
private fun RssSourceImportScaffold(
    title: String,
    onBack: () -> Unit,
    bottom: @Composable () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(Modifier.fillMaxSize()) {
            RssSourceImportTopBar(title = title, onBack = onBack)
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
private fun RssSourceImportTopBar(title: String, onBack: () -> Unit) {
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
private fun RssSourceImportPanel(activeOptions: Set<String>, onToggle: (String) -> Unit) {
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
                painter = painterResource(id = R.drawable.reader_ic_link),
                contentDescription = null,
                tint = extra.muted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "https://example.com/rss-source.json",
                style = rssSourceImportMetaStyle().copy(fontSize = 12.sp, lineHeight = 14.sp),
                color = extra.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("保留名称", "保留分组", "保留启用状态", "加入分组").forEach { option ->
                RssSourceImportChip(
                    text = option,
                    active = option in activeOptions,
                    onClick = { onToggle(option) }
                )
            }
        }
    }
}

@Composable
private fun RssSourceImportList(entries: List<RssImportEntry>, onOpenDetail: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        entries.forEachIndexed { index, entry ->
            RssSourceImportRow(entry = entry, onOpenDetail = onOpenDetail)
            if (index != entries.lastIndex) {
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
private fun RssSourceImportRow(entry: RssImportEntry, onOpenDetail: () -> Unit) {
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
        RssSourceImportIconCircle(
            iconRes = if (entry.checked) R.drawable.reader_ic_check else R.drawable.reader_ic_rss,
            selected = entry.checked
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.name,
                style = rssSourceImportTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.meta,
                style = rssSourceImportMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssSourceImportSmallButton(label = if (entry.checked) "详情" else "查看", onClick = onOpenDetail)
    }
}

@Composable
private fun RssSourceImportDetailPanel() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp)
    ) {
        RssSourceImportDetailHeader()
        rssSourceImportDetailRows().forEachIndexed { index, row ->
            if (index != 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(extra.hairline.copy(alpha = 0.48f))
                )
            }
            RssSourceImportDetailRow(row)
        }
    }
}

@Composable
private fun RssSourceImportDetailHeader() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssSourceImportIconCircle(iconRes = R.drawable.reader_ic_upload, selected = false)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "书源维护公告",
                style = rssSourceImportTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "更新 · 规则版本更高 · 需登录",
                style = rssSourceImportMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RssSourceImportDetailRow(row: RssImportDetailRow) {
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
            style = rssSourceImportTitleStyle(),
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.message,
            style = rssSourceImportMetaStyle(),
            color = extra.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssSourceImportResultCard() {
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
                painter = painterResource(id = R.drawable.reader_ic_check),
                contentDescription = null,
                tint = extra.primaryDark,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "已导入 2 个订阅源",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(850),
                textAlign = TextAlign.Center
            ),
            color = colors.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "新增源已加入 RSS 订阅管理，冲突源保留本地名称、分组和启用状态。",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(500),
                textAlign = TextAlign.Center
            ),
            color = extra.infoLayer,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "需要登录的源不会自动导入 Cookie。",
            style = rssSourceImportMetaStyle().copy(fontSize = 11.sp, lineHeight = 17.sp),
            color = extra.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RssSourceImportBottomActions(
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
            RssSourceImportBottomButton(
                label = secondary,
                primary = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
            RssSourceImportBottomButton(
                label = primary,
                primary = true,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssSourceImportBottomButton(
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
private fun RssSourceImportChip(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(if (active) colors.primary.copy(alpha = 0.12f) else extra.metaBackground.copy(alpha = 0.84f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = rssSourceImportButtonStyle(),
            color = if (active) extra.primaryDark else extra.navInactive,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceImportSmallButton(label: String, onClick: () -> Unit) {
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
            style = rssSourceImportButtonStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceImportIconCircle(@DrawableRes iconRes: Int, selected: Boolean) {
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

private data class RssImportEntry(
    val name: String,
    val meta: String,
    val checked: Boolean
)

private data class RssImportDetailRow(
    val title: String,
    val message: String,
    val warning: Boolean = false
)

private fun rssSourceImportEntries() = listOf(
    RssImportEntry("社区 RSS 源合集", "新增 · 12 个源", checked = true),
    RssImportEntry("GitHub Releases", "已有 · 保留本地名称", checked = false),
    RssImportEntry("书源维护公告", "更新 · 规则版本更高", checked = true)
)

private fun rssSourceImportDetailRows() = listOf(
    RssImportDetailRow("变更摘要", "正文规则从 content:encoded 改为 article.content，新增登录检测 URL。"),
    RssImportDetailRow("冲突处理", "保留本地名称和分组，覆盖规则、请求头和分类入口。"),
    RssImportDetailRow("登录态", "不导入 Cookie。更新后需要在源登录页重新授权。", warning = true)
)

private fun rssSourceImportTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssSourceImportMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun rssSourceImportButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
