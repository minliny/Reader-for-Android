package io.reader.rebuild.features.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2RuleSubUpdateChange
import com.reader.core.protocol.M2RuleSubUpdateChangeKind as Kind
import com.reader.core.protocol.M2RuleSubUpdatePreviewData
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle

internal val M2RuleSubUpdateChange.canApply get() = kind == Kind.ADDED || kind == Kind.UPDATE

@Composable
internal fun SourceSubscriptionDiffContent(
    preview: M2RuleSubUpdatePreviewData,
    selected: Set<String>,
    onSelect: (String, Boolean) -> Unit = { _, _ -> },
    onConfirm: () -> Unit = {},
    enabled: Boolean = true,
) {
    val colors = LocalReaderColors.current
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.sourcePageWidth().padding(Metrics.screenInset.dp)) {
            LazyColumn(Modifier.weight(1f).testTag("source.subscription.diff"),
                verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("版本差异", Modifier.weight(1f), style = readerTextStyle(Metrics.pageTitle), color = colors.primaryText)
                        Text("已选 ${selected.size} 项", style = readerTextStyle(Metrics.sourceStatusText), color = colors.secondaryText)
                    }
                }
                if (preview.changes.isEmpty()) item { ReaderPageState(PageState.Empty("没有发现版本差异")) }
                items(preview.changes, key = { it.sourceId }) { change ->
                    SourceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp)) {
                                Text(change.name.ifBlank { change.sourceId }, color = colors.primaryText, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, style = readerTextStyle(Metrics.sourceRowTitle))
                                Text(change.sourceId, color = colors.secondaryText, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = readerTextStyle(Metrics.sourceStatusText))
                                SourceStatusBadge(change.kind.label, change.kind.tint())
                            }
                            if (change.canApply) Checkbox(change.sourceId in selected, { onSelect(change.sourceId, it) },
                                enabled = enabled, modifier = Modifier.testTag("source.subscription.select.${change.sourceId}"))
                        }
                    }
                }
            }
            Button(onClick = onConfirm, enabled = enabled && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("source.subscription.apply")) {
                Text("确认应用所选变更", style = readerTextStyle(Metrics.rowLabel))
            }
        }
    }
}

private val Kind.label: String get() = when (this) {
    Kind.ADDED -> "新增"
    Kind.UPDATE -> "更新"
    Kind.UNCHANGED -> "无变化"
    Kind.LOCAL_ONLY -> "仅本地"
    Kind.CONFLICT -> "有冲突"
    Kind.REMOVED_REMOTE -> "远端移除"
}

@Composable
private fun Kind.tint(): Color = with(LocalReaderColors.current) {
    when (this@tint) {
        Kind.ADDED -> success
        Kind.UPDATE -> accent
        Kind.CONFLICT -> danger
        Kind.REMOVED_REMOTE -> warning
        Kind.UNCHANGED, Kind.LOCAL_ONLY -> secondaryText
    }
}
