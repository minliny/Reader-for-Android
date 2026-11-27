package io.reader.rebuild.features.source

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2SourceCheckReportStepsItem
import com.reader.core.protocol.M2SourceCheckReportStepsItemLevel as Level
import com.reader.core.protocol.M2SourceCheckReportStepsItemStatus as Status
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry as Icons
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.ReaderTaskProgress
import io.reader.rebuild.design.ReaderTaskProgressState
import io.reader.rebuild.design.readerTextStyle

@Composable
internal fun SourceReportContent(
    rows: List<SourceReportRow>,
    filter: SourceReportFilter = SourceReportFilter.ALL,
    onFilter: (SourceReportFilter) -> Unit = {},
    onSelect: (SourceReportRow) -> Unit = {},
    progress: ReaderTaskProgressState? = null,
) {
    val colors = LocalReaderColors.current
    val visible = rows.filter { filter == SourceReportFilter.ALL || it.result == filter && !it.running }
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.sourcePageWidth()) {
            LazyColumn(
                Modifier.weight(1f).testTag("source.reports").padding(horizontal = Metrics.screenInset.dp),
                verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = Metrics.spaceExtraSmall.dp)
                            .horizontalScroll(rememberScrollState()).testTag("source.report.filter"),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.sourceStatusChipGap.dp),
                    ) {
                        SourceReportFilter.entries.forEach { option ->
                            FilterChip(selected = option == filter, onClick = { onFilter(option) },
                                label = { Text(option.label, style = readerTextStyle(Metrics.sourceStatusText)) })
                        }
                    }
                }
                if (visible.isEmpty()) item { ReaderPageState(PageState.Empty("该筛选暂无报告")) }
                items(visible, key = { it.sourceId }) { row ->
                    SourceCard(Modifier.clickable { onSelect(row) }.testTag("source.report.row.${row.sourceId}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, Modifier.weight(1f), color = colors.primaryText, maxLines = 2,
                                style = readerTextStyle(Metrics.sourceRowTitle))
                            SourceStatusBadge(row.statusLabel, row.result.tint())
                            ReaderIcon(Icons.drillIn, null, size = Metrics.sourceLoginActionIconSize.dp, tint = colors.secondaryText)
                        }
                        if (row.report != null) SourceReportLevels(row.report.steps)
                        if (row.error) Text("检测请求未完成", style = readerTextStyle(Metrics.rowDetail), color = colors.danger)
                    }
                }
                item { Box(Modifier.padding(bottom = Metrics.screenInset.dp)) }
            }
            progress?.let { ReaderTaskProgress(it, Modifier.padding(Metrics.screenInset.dp)) }
        }
    }
}

@Composable
private fun SourceReportLevels(steps: List<M2SourceCheckReportStepsItem>) {
    val byLevel = steps.associateBy { it.level }
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.sourceStatusChipGap.dp)) {
        Level.entries.forEach { level ->
            val status = byLevel[level]?.status ?: Status.NOT_REQUESTED
            val description = "${level.wireValue} ${status.label}"
            SourceStatusBadge(
                level.wireValue, status.tint(),
                Modifier.widthIn(min = Metrics.sourceCheckStageBadgeWidth.dp)
                    .heightIn(min = Metrics.sourceCheckStageBadgeHeight.dp)
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
internal fun SourceDiagnosticContent(row: SourceReportRow) {
    val colors = LocalReaderColors.current
    val report = row.report
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        if (report == null) {
            ReaderPageState(when {
                row.running -> PageState.Loading
                row.error -> PageState.Failure("检测请求未完成", null)
                else -> PageState.Empty("该书源暂无检测报告")
            })
        } else {
            LazyColumn(
                Modifier.sourcePageWidth().padding(Metrics.screenInset.dp).testTag("source.report.detail"),
                verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
            ) {
                item {
                    Text(row.name, style = readerTextStyle(Metrics.pageTitle), color = colors.primaryText)
                    if (row.stale) Text("规则已变更，重新检测可获得当前结果。",
                        style = readerTextStyle(Metrics.rowDetail), color = colors.secondaryText)
                }
                items(report.steps.sortedBy { it.level.ordinal }, key = { it.level.name }) { step ->
                    SourceCard(Modifier.testTag("source.report.stage.${step.level.wireValue}")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${step.level.wireValue} · ${step.level.label}", Modifier.weight(1f),
                                style = readerTextStyle(Metrics.rowLabel), color = colors.primaryText,
                            )
                            SourceStatusBadge(step.status.label, step.status.tint())
                        }
                        if (step.executed) Text("耗时 ${step.durationMs} ms",
                            style = readerTextStyle(Metrics.rowDetail), color = colors.secondaryText)
                        step.reason?.let { reason ->
                            Text(diagnostic(reason), style = readerTextStyle(Metrics.rowDetail), color = colors.secondaryText)
                        }
                    }
                }
            }
        }
    }
}

private val SourceReportRow.statusLabel: String get() = when {
    running -> "检测中"
    stale -> "已过期"
    else -> result.label
}

internal val Level.label: String get() = when (this) {
    Level.L1 -> "书源规则"
    Level.L2 -> "搜索结果"
    Level.L3 -> "书籍详情"
    Level.L4 -> "章节目录"
    Level.L5 -> "正文内容"
}

private val Status.label: String get() = when (this) {
    Status.PASS -> "通过"
    Status.FAIL -> "失败"
    Status.NOT_REQUESTED -> "未选择"
    Status.NOT_SUPPORTED -> "不支持"
    Status.BLOCKED -> "前置受阻"
    Status.CANCELLED -> "已取消"
}

@Composable
private fun Status.tint(): Color = with(LocalReaderColors.current) {
    when (this@tint) {
        Status.PASS -> success
        Status.FAIL -> danger
        Status.BLOCKED, Status.CANCELLED -> warning
        Status.NOT_REQUESTED, Status.NOT_SUPPORTED -> secondaryText
    }
}

@Composable
private fun SourceReportFilter.tint(): Color = with(LocalReaderColors.current) {
    when (this@tint) {
        SourceReportFilter.PASSED -> success
        SourceReportFilter.FAILED -> danger
        SourceReportFilter.ALL, SourceReportFilter.NOT_EXECUTED -> secondaryText
    }
}

private fun diagnostic(reason: String) = when (reason) {
    "SOURCE_DISABLED" -> "书源已停用"
    "HTTP_ERROR" -> "服务响应失败"
    "TIMEOUT" -> "请求超时"
    "CAPABILITY_UNAVAILABLE" -> "所需平台能力不可用"
    "LOGIN_REQUIRED" -> "需要登录后重新检测"
    "no_search_results" -> "没有找到匹配结果"
    else -> "可检查当前层级规则后重新检测。"
}
