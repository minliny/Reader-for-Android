package io.reader.rebuild.features.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.BookCover
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.features.library.BookReference

@Composable
internal fun BookDetailContent(
    book: BookReference,
    inShelf: Boolean,
    onRead: () -> Unit = {},
    onDirectory: () -> Unit = {},
    onShelf: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    onSwitch: (() -> Unit)? = null,
) {
    val colors = LocalReaderColors.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Metrics.screenInset.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.spaceMedium.dp)) {
            Surface(shape = RoundedCornerShape(Metrics.cardRadius.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(Metrics.cardPadding.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BookCover(book.coverUrl, Modifier.size(Metrics.onlineDetailCoverWidth.dp, Metrics.onlineDetailCoverHeight.dp), Metrics.cardRadius)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(book.title, style = readerTextStyle(Metrics.onlineDetailTitle), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.author?.takeIf(String::isNotBlank) ?: "作者未知", color = colors.secondaryText,
                            style = readerTextStyle(Metrics.rowLabel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!book.local) book.lastChapter?.takeIf(String::isNotBlank)?.let {
                            Text("最新 · $it", color = colors.secondaryText, style = readerTextStyle(Metrics.rowDetail), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(book.displaySource, color = colors.accent, style = readerTextStyle(Metrics.rowDetail))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (book.local) "本地" else "在线", color = colors.secondaryText, style = readerTextStyle(Metrics.rowDetail))
                            if (!book.local && onSwitch != null) TextButton(onClick = onSwitch) {
                                ReaderIcon(ReaderIconRegistry.sourceSwitch, "更换书源", size = 17.dp)
                                Text("更换书源")
                            }
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(Metrics.cardRadius.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Metrics.cardPadding.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("内容简介", style = readerTextStyle(Metrics.sectionHeading))
                    Text(book.intro?.takeIf(String::isNotBlank) ?: "暂无简介", color = colors.secondaryText,
                        style = readerTextStyle(Metrics.rowLabel), maxLines = 5, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = {}) { Text("展开 / 收起") }
                }
            }
            Surface(shape = RoundedCornerShape(Metrics.cardRadius.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(top = Metrics.cardPadding.dp, bottom = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Metrics.cardPadding.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("目录预览", style = readerTextStyle(Metrics.sectionHeading))
                        Text(" · ${if (book.local) "本地章节" else "在线目录"}", color = colors.secondaryText, style = readerTextStyle(Metrics.rowDetail))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDirectory) { ReaderIcon(ReaderIconRegistry.directory, "完整目录", size = 18.dp); Text("完整目录") }
                    }
                    val preview = listOf("第一章 · 开始", "第二章 · 风起", book.lastChapter ?: "第三章 · 继续阅读", "第四章 · 远方")
                    preview.forEachIndexed { index, title ->
                        val active = title == book.lastChapter
                        Row(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onDirectory)
                            .background(if (active) colors.accentTint else colors.surface)
                            .padding(horizontal = Metrics.cardPadding.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), color = if (active) colors.accent else colors.primaryText,
                                style = readerTextStyle(Metrics.rowLabel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (active) Text("在读", color = colors.accent, style = readerTextStyle(Metrics.shelfProgress), modifier = Modifier.padding(end = 8.dp))
                            ReaderIcon(ReaderIconRegistry.drillIn, "打开章节", size = 20.dp, tint = colors.secondaryText)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background))).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Metrics.pillRadius.dp), color = colors.elevated,
                border = BorderStroke(1.dp, colors.outline), shadowElevation = 4.dp) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRead, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(Metrics.pillRadius.dp)) {
                        Text(if (inShelf) "继续阅读" else "开始阅读")
                    }
                    OutlinedButton(onClick = onShelf, Modifier.width(94.dp).height(44.dp), shape = RoundedCornerShape(Metrics.pillRadius.dp)) {
                        Text(if (inShelf) "已在书架" else "加入书架", maxLines = 1)
                    }
                }
            }
        }
    }
}
