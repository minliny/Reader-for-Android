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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
fun RssDetailScreen(
    onBack: () -> Unit,
    onBackToList: () -> Unit,
    onOpenOriginal: () -> Unit,
    onManageSource: () -> Unit
) {
    var isRead by remember { mutableStateOf(true) }
    var starred by remember { mutableStateOf(true) }

    LibraryShellFrame(
        backTopBar = {
            RssDetailTopBar(
                starred = starred,
                onBack = onBack,
                onToggleStar = { starred = !starred },
                onOpenOriginal = onOpenOriginal
            )
        },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    RssReaderSourceCard(onManageSource = onManageSource)
                }
                item {
                    RssReaderTitleCard()
                }
                item {
                    RssReaderInlineActions(
                        isRead = isRead,
                        starred = starred,
                        onToggleRead = { isRead = !isRead },
                        onToggleStar = { starred = !starred },
                        onManageSource = onManageSource
                    )
                }
                item {
                    RssReaderBodyCard()
                }
                item {
                    RssOriginalLinkCard(onOpenOriginal = onOpenOriginal)
                }
            }
        },
        bottomActionHost = {
            RssReaderBottomActions(
                onBackToList = onBackToList,
                onOpenOriginal = onOpenOriginal
            )
        }
    )
}

@Composable
private fun RssDetailTopBar(
    starred: Boolean,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenOriginal: () -> Unit
) {
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
            text = "RSS 阅读",
            style = ReaderTextStyles.backBarTitle,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        RssReaderTopIconButton(
            iconRes = R.drawable.reader_ic_bookmark,
            tint = if (starred) colors.primary else extra.primaryDark,
            onClick = onToggleStar
        )
        RssReaderTopIconButton(
            iconRes = R.drawable.reader_ic_link,
            tint = extra.primaryDark,
            onClick = onOpenOriginal
        )
    }
}

@Composable
private fun RssReaderTopIconButton(
    @DrawableRes iconRes: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(readerExtraColors().metaBackground.copy(alpha = 0.78f), ReaderShapes.pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun RssReaderSourceCard(onManageSource: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssDetailIconCircle(iconRes = R.drawable.reader_ic_rss, size = 32.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "GitHub Releases",
                style = rssDetailSmallTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "今天 10:18 · 开源项目 · 已解析正文",
                style = rssDetailMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssDetailPillButton(label = "查看源", onClick = onManageSource)
    }
}

@Composable
private fun RssReaderTitleCard() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Reader UI 前端输入件更新说明",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.PAGE_TITLE.value,
                lineHeight = 26.sp,
                fontWeight = FontWeight(900)
            ),
            color = colors.onBackground
        )
        Text(
            text = "本条目汇总最近的阅读体验修复、发现页状态补充和 RSS 页面结构调整。",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
                lineHeight = 21.sp,
                fontWeight = FontWeight(500)
            ),
            color = extra.infoLayer
        )
    }
}

@Composable
private fun RssReaderInlineActions(
    isRead: Boolean,
    starred: Boolean,
    onToggleRead: () -> Unit,
    onToggleStar: () -> Unit,
    onManageSource: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RssReaderActionButton(
            label = if (isRead) "已读" else "未读",
            iconRes = R.drawable.reader_ic_check,
            primary = true,
            onClick = onToggleRead,
            modifier = Modifier.weight(1f)
        )
        RssReaderActionButton(
            label = "收藏",
            iconRes = R.drawable.reader_ic_bookmark,
            primary = false,
            onClick = onToggleStar,
            modifier = Modifier.weight(1f)
        )
        RssReaderActionButton(
            label = "源设置",
            iconRes = R.drawable.reader_ic_source_stack,
            primary = false,
            onClick = onManageSource,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RssReaderActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(if (primary) colors.primary.copy(alpha = 0.12f) else extra.metaBackground.copy(alpha = 0.84f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (primary) extra.primaryDark else extra.controlInk,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = label,
            style = rssDetailButtonStyle(),
            color = if (primary) extra.primaryDark else extra.controlInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssReaderBodyCard() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 15.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(
            "RSS 页面现在以订阅源为一级对象，同时保留常规阅读器里的未读、全部、收藏和刷新工作流。主页负责快速浏览条目，阅读页则专注正文、原文和源相关操作。",
            "如果订阅源提供正文规则，文章应直接进入当前阅读页；如果源只提供链接，则在阅读页保留原文入口，并用 WebView 或外部浏览器作为兜底。",
            "后续实现里，已读状态应在进入阅读页时自动写入，收藏和源设置需要回到订阅源维度同步，不应该散落在主 Tab 的临时按钮里。"
        ).forEach { paragraph ->
            Text(
                text = paragraph,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.SECTION_TITLE.value,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight(500)
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun RssOriginalLinkCard(onOpenOriginal: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssDetailIconCircle(iconRes = R.drawable.reader_ic_link, size = 28.dp, muted = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "原文链接",
                style = rssDetailSmallTitleStyle(),
                color = colors.onBackground,
                maxLines = 1
            )
            Text(
                text = "github.com/minliny/Reader-UI/releases/latest",
                style = rssDetailMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssDetailPillButton(label = "打开", onClick = onOpenOriginal)
    }
}

@Composable
private fun RssReaderBottomActions(
    onBackToList: () -> Unit,
    onOpenOriginal: () -> Unit,
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
            RssBottomButton(
                label = "返回列表",
                primary = false,
                onClick = onBackToList,
                modifier = Modifier.weight(1f)
            )
            RssBottomButton(
                label = "打开原文",
                primary = true,
                onClick = onOpenOriginal,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssBottomButton(
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
            maxLines = 1
        )
    }
}

@Composable
private fun RssDetailPillButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = rssDetailButtonStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RssDetailIconCircle(
    @DrawableRes iconRes: Int,
    size: androidx.compose.ui.unit.Dp,
    muted: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(size)
            .background(if (muted) extra.metaBackground.copy(alpha = 0.82f) else colors.primary.copy(alpha = 0.12f), ReaderShapes.pill),
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

private fun rssDetailSmallTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssDetailMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun rssDetailButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
