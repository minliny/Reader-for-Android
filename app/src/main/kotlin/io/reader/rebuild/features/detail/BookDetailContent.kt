package io.reader.rebuild.features.detail

import io.reader.rebuild.features.library.BookReference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import io.reader.rebuild.design.BookCover
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.readerTextStyle

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Metrics.screenInset.dp),
        verticalArrangement = Arrangement.spacedBy(Metrics.spaceMedium.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceMedium.dp)) {
            BookCover(book.coverUrl, Modifier.size(Metrics.onlineDetailCoverWidth.dp, Metrics.onlineDetailCoverHeight.dp),
                Metrics.onlineDetailCoverRadius)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp)) {
                Text(book.title, style = readerTextStyle(Metrics.appTitle))
                Text(book.author?.takeIf(String::isNotBlank) ?: "作者未知",
                    color = colors.secondaryText, style = readerTextStyle(Metrics.rowLabel))
                Text(book.displaySource, color = colors.accent, style = readerTextStyle(Metrics.rowDetail))
                if (!book.local) book.lastChapter?.takeIf(String::isNotBlank)?.let {
                    Text("最新 · $it", color = colors.secondaryText, style = readerTextStyle(Metrics.rowDetail))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
            Button(onRead) { Text(if (inShelf) "继续阅读" else "开始阅读") }
            TextButton(onDirectory) { Text("目录") }
            TextButton(onShelf) { Text(if (inShelf) "移出书架" else "加入书架") }
            if (onSwitch != null) TextButton(onSwitch) { Text("换源") }
            if (onDownload != null) TextButton(onDownload) { Text("下载") }
        }
        if (!book.intro.isNullOrBlank()) {
            Text("简介", style = readerTextStyle(Metrics.sectionHeading))
            Text(book.intro, color = colors.secondaryText, style = readerTextStyle(Metrics.rowLabel))
        }
    }
}
