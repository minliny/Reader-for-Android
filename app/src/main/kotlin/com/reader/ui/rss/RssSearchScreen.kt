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
fun RssSearchScreen(
    onBack: () -> Unit,
    onManageSources: () -> Unit,
    onOpenArticle: () -> Unit
) {
    var activeScope by remember { mutableStateOf("全部") }
    val results = remember(activeScope) { rssSearchResults(activeScope) }

    LibraryShellFrame(
        backTopBar = { RssSearchTopBar(onBack = onBack) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    RssSearchPanel(
                        activeScope = activeScope,
                        onScope = { activeScope = it }
                    )
                }
                item {
                    RssSearchResultSection(
                        results = results,
                        onManageSources = onManageSources,
                        onOpenArticle = onOpenArticle
                    )
                }
            }
        }
    )
}

@Composable
private fun RssSearchTopBar(onBack: () -> Unit) {
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
            text = "RSS 搜索",
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
private fun RssSearchPanel(activeScope: String, onScope: (String) -> Unit) {
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
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_search),
                contentDescription = null,
                tint = extra.muted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "搜索订阅源、文章标题或分组",
                style = rssSearchMetaStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, lineHeight = 15.sp, fontWeight = FontWeight(750)),
                color = extra.muted,
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
            listOf("全部", "订阅源", "文章", "分组").forEach { scope ->
                RssSearchChip(
                    text = scope,
                    active = activeScope == scope,
                    onClick = { onScope(scope) }
                )
            }
        }
    }
}

@Composable
private fun RssSearchResultSection(
    results: List<RssSearchResult>,
    onManageSources: () -> Unit,
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
                text = "搜索结果",
                style = rssSearchSectionTitleStyle(),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            RssSearchMiniAction(
                label = "管理源",
                iconRes = R.drawable.reader_ic_source_stack,
                onClick = onManageSources
            )
        }
        RssSearchResultList(results = results, onOpenArticle = onOpenArticle)
    }
}

@Composable
private fun RssSearchResultList(results: List<RssSearchResult>, onOpenArticle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        results.forEachIndexed { index, result ->
            RssSearchResultRow(result = result, onClick = onOpenArticle)
            if (index != results.lastIndex) {
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
private fun RssSearchResultRow(result: RssSearchResult, onClick: () -> Unit) {
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
                .background(if (result.unread) colors.primary else extra.hairline, ReaderShapes.pill)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = result.title,
                style = rssSearchTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${result.source} · ${result.time} · ${result.group}",
                style = rssSearchMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.desc,
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
            painter = painterResource(id = R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RssSearchMiniAction(label: String, @DrawableRes iconRes: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
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
            style = rssSearchButtonStyle().copy(fontSize = ReaderTypeToken.ACTION_LABEL.value, lineHeight = 13.sp),
            color = extra.primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSearchChip(text: String, active: Boolean, onClick: () -> Unit) {
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
            style = rssSearchButtonStyle().copy(fontSize = ReaderTypeToken.BOOK_META.value, lineHeight = 14.sp),
            color = if (active) colors.onPrimary else extra.navInactive,
            maxLines = 1
        )
    }
}

private data class RssSearchResult(
    val title: String,
    val source: String,
    val time: String,
    val group: String,
    val desc: String,
    val unread: Boolean
)

private fun rssSearchResults(scope: String): List<RssSearchResult> {
    val all = listOf(
        RssSearchResult("Reader UI 前端输入件更新说明", "GitHub Releases", "10:18", "开源项目", "新增发现页状态路由、阅读控制层响应式约束，并补充 RSS 页面结构规划。", true),
        RssSearchResult("订阅源规则解析失败排查", "书源维护公告", "09:52", "维护", "部分订阅源返回 HTML 而不是 XML，已建议检查 Cookie、登录态和正文提取规则。", true),
        RssSearchResult("Legado 订阅源配置经验整理", "阅读器版本讨论", "昨天", "社区", "社区整理了单 URL 源、分类入口、文章样式和 WebView 正文处理的常见配置方式。", true)
    )
    return when (scope) {
        "订阅源" -> all.filter { it.source != "GitHub Releases" } + all.first()
        "文章" -> all
        "分组" -> all.sortedBy { it.group }
        else -> all
    }
}

private fun rssSearchSectionTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.SECTION_TITLE.value,
    lineHeight = 18.sp,
    fontWeight = FontWeight(900)
)

private fun rssSearchTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssSearchMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun rssSearchButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(850)
)
