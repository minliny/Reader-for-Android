package io.reader.rebuild.features.manga

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.features.library.BookReference

@Composable
internal fun MangaReaderTopBar(book: BookReference, chapter: String, onBack: () -> Unit) {
    val colors = LocalReaderColors.current
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        color = colors.panel, shape = RoundedCornerShape(Metrics.pillRadius.dp),
        border = BorderStroke(1.dp, colors.outline), shadowElevation = 6.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderAction(ReaderIconRegistry.back, "退出漫画", onBack)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(book.title, style = readerTextStyle(Metrics.readerControlBookTitle), maxLines = 1)
                Text(" · $chapter", Modifier.padding(start = 6.dp), color = colors.secondaryText,
                    style = readerTextStyle(Metrics.readerControlChapterTitle), maxLines = 1)
            }
            ReaderAction(ReaderIconRegistry.more, "漫画更多", {})
        }
    }
}

@Composable
internal fun MangaReaderBottomBar(page: Long, total: Long, horizontal: Boolean, onPrevious: () -> Unit,
    onNext: () -> Unit, onToggleMode: () -> Unit, onSave: () -> Unit, onCollapse: () -> Unit) {
    val colors = LocalReaderColors.current
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        color = colors.panel, shape = RoundedCornerShape(Metrics.pillRadius.dp),
        border = BorderStroke(1.dp, colors.outline), shadowElevation = 8.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onPrevious, enabled = page > 0) { Text("上一话") }
            Text("${page + 1}/$total", Modifier.weight(1f), color = colors.accent,
                style = readerTextStyle(Metrics.rowLabel))
            OutlinedButton(onNext, enabled = page + 1 < total) { Text("下一话") }
            Button(onToggleMode) { Text(if (horizontal) "条漫" else "翻页") }
            OutlinedButton(onSave) { Text("下载") }
            ReaderAction(ReaderIconRegistry.close, "收起", onCollapse)
        }
    }
}
