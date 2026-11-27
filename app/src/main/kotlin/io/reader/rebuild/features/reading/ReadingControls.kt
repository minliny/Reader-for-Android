package io.reader.rebuild.features.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import com.reader.core.theme.ReaderPaperThemes
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.readerTextStyle

@Composable
internal fun ReadingControlsContent(
    title: String,
    chapter: String,
    appearance: ReadingAppearance,
    expanded: Boolean,
    onExpand: () -> Unit = {},
    onAppearance: (ReadingAppearance) -> Unit = {},
    onDirectory: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onSwitch: (() -> Unit)? = null,
) {
    val colors = LocalReaderColors.current
    Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth()
        .padding(horizontal = Metrics.spaceSmall.dp, vertical = Metrics.spaceExtraSmall.dp),
        color = colors.panel, contentColor = colors.primaryText, shadowElevation = 6.dp,
        shape = RoundedCornerShape(Metrics.sourceSwitchDrawerRadius.dp)) {
        Column(Modifier.padding(Metrics.cardPadding.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReaderAction(ReaderIconRegistry.back, "退出阅读", onBack)
                Column(Modifier.weight(1f)) {
                    Text(title, style = readerTextStyle(Metrics.readerControlBookTitle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapter, style = readerTextStyle(Metrics.readerControlChapterTitle), color = colors.secondaryText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ReaderAction(ReaderIconRegistry.readerControlHeaderChevron, if (expanded) "收起设置" else "展开设置", onExpand)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                ReaderAction(ReaderIconRegistry.previousChapter, "上一章", onPrevious)
                ReaderAction(ReaderIconRegistry.directory, "目录", onDirectory)
                ReaderAction(ReaderIconRegistry.bookmark, "书签", onBookmarks)
                ReaderAction(ReaderIconRegistry.nextChapter, "下一章", onNext)
                if (onRefresh != null) ReaderAction(ReaderIconRegistry.refresh, "刷新正文", onRefresh)
                if (onSwitch != null) ReaderAction(ReaderIconRegistry.sourceSwitch, "换源", onSwitch)
            }
            if (expanded) {
                ReadingControlStepper("字号",
                    { onAppearance(appearance.copy(font = (appearance.font - 1).coerceAtLeast(12.0))) },
                    { onAppearance(appearance.copy(font = (appearance.font + 1).coerceAtMost(36.0))) })
                ReadingControlStepper("行距",
                    { onAppearance(appearance.copy(line = (appearance.line - .1).coerceAtLeast(1.25))) },
                    { onAppearance(appearance.copy(line = (appearance.line + .1).coerceAtMost(2.6))) })
                TextButton({ onAppearance(appearance.copy(scrolling = !appearance.scrolling)) }) {
                    Text(if (appearance.scrolling) "切换分页" else "切换滚动")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                    ReaderPaperThemes.all.forEach { paper ->
                        OutlinedButton({ onAppearance(appearance.copy(paperId = paper.id)) },
                            border = BorderStroke(if (appearance.paper.id == paper.id) 2.dp else 1.dp, colors.outline)) {
                            Surface(Modifier.size(12.dp), color = Color(paper.swatch.toInt()),
                                shape = RoundedCornerShape(3.dp), border = BorderStroke(1.dp, colors.outline)) {}
                            Text(paper.displayName, Modifier.padding(start = 6.dp), style = readerTextStyle(Metrics.rowDetail))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingControlStepper(label: String, decrease: () -> Unit, increase: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp)) {
        Text(label, Modifier.weight(1f), style = readerTextStyle(Metrics.rowDetail))
        TextButton(decrease, Modifier.semantics { contentDescription = "减小$label" }) { Text("−") }
        TextButton(increase, Modifier.semantics { contentDescription = "增大$label" }) { Text("+") }
    }
}
