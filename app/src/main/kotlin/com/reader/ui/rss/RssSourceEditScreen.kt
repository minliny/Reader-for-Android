package com.reader.ui.rss

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
import com.reader.android.R
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun RssSourceEditScreen(
    onBack: () -> Unit,
    onDebug: () -> Unit,
    onSave: () -> Unit
) {
    var activeGroup by remember { mutableStateOf("基础") }
    val fields = remember { rssSourceEditFields() }

    LibraryShellFrame(
        backTopBar = { RssSourceEditTopBar(onBack = onBack, onDebug = onDebug) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    RssSourceEditTabs(
                        activeGroup = activeGroup,
                        onGroup = { activeGroup = it }
                    )
                }
                item {
                    RssSourceEditList(fields = fields, activeGroup = activeGroup)
                }
            }
        },
        bottomActionHost = {
            RssSourceEditBottomActions(
                onDebug = onDebug,
                onSave = onSave
            )
        }
    )
}

@Composable
private fun RssSourceEditTopBar(onBack: () -> Unit, onDebug: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
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
            text = "RSS 源编辑",
            style = ReaderTextStyles.backBarTitle,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 32.dp)
                .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
                .clickable(onClick = onDebug)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "调试",
                style = rssSourceEditButtonStyle(),
                color = extra.primaryDark,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RssSourceEditTabs(activeGroup: String, onGroup: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("基础", "请求", "列表", "WebView").forEach { group ->
            RssSourceEditChip(
                text = group,
                active = activeGroup == group,
                onClick = { onGroup(group) }
            )
        }
    }
}

@Composable
private fun RssSourceEditList(fields: List<RssSourceEditField>, activeGroup: String) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        fields.forEachIndexed { index, field ->
            RssSourceEditFieldRow(field = field, active = field.group == activeGroup)
            if (index != fields.lastIndex) {
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
private fun RssSourceEditFieldRow(field: RssSourceEditField, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(if (active) colors.primary.copy(alpha = 0.045f) else colors.surface.copy(alpha = 0f))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = field.group,
            style = rssSourceEditMetaStyle(),
            color = extra.muted,
            maxLines = 1
        )
        Text(
            text = field.label,
            style = rssSourceEditTitleStyle(),
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = field.value,
            style = rssSourceEditMetaStyle(),
            color = extra.infoLayer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssSourceEditBottomActions(
    onDebug: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = modifier
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
            RssSourceEditBottomButton(
                label = "调试规则",
                primary = false,
                onClick = onDebug,
                modifier = Modifier.weight(1f)
            )
            RssSourceEditBottomButton(
                label = "保存",
                primary = true,
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssSourceEditBottomButton(
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
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceEditChip(text: String, active: Boolean, onClick: () -> Unit) {
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
            style = rssSourceEditButtonStyle().copy(fontSize = 12.sp, lineHeight = 14.sp),
            color = if (active) colors.onPrimary else extra.navInactive,
            maxLines = 1
        )
    }
}

private data class RssSourceEditField(
    val group: String,
    val label: String,
    val value: String
)

private fun rssSourceEditFields() = listOf(
    RssSourceEditField("基础", "源名称", "GitHub Releases"),
    RssSourceEditField("基础", "源地址", "https://github.com/minliny/Reader-UI/releases.atom"),
    RssSourceEditField("基础", "分组", "开源项目"),
    RssSourceEditField("基础", "分类 URL", "Releases::/releases.atom && Issues::/issues.atom"),
    RssSourceEditField("请求", "请求头", "User-Agent: Reader UI"),
    RssSourceEditField("请求", "并发率", "2/1000"),
    RssSourceEditField("列表", "文章列表", "默认 RSS 解析"),
    RssSourceEditField("列表", "下一页", "PAGE"),
    RssSourceEditField("列表", "标题 / 时间 / 链接", "title / pubDate / link"),
    RssSourceEditField("WebView", "正文规则", "content:encoded || article"),
    RssSourceEditField("WebView", "注入 JS / CSS", "图片宽度、夜间样式、跳转拦截"),
    RssSourceEditField("WebView", "白名单 / 黑名单", "过滤广告资源")
)

private fun rssSourceEditTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssSourceEditMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun rssSourceEditButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
