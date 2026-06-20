package com.reader.android.ui.reader.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.theme.ReaderTheme

enum class SourceSwitchFlowState {
    Default,
    Loading,
    Empty,
    Error,
    Offline,
    Permission
}

@Immutable
data class SourceSwitchReaderState(
    val title: String,
    val chapter: String,
    val currentSource: String,
    val switchedSource: String,
    val lines: List<String>,
    val progress: String,
    val chapterProgress: String
)

@Immutable
data class SourceCandidateState(
    val name: String,
    val badge: String = "",
    val chapter: String,
    val updated: String,
    val latency: String,
    val status: String,
    val current: Boolean = false,
    val checking: Boolean = false
)

@Immutable
data class SourceCheckStepState(
    val label: String,
    val done: Boolean
)

@Immutable
data class SourceSwitchFeedbackState(
    val title: String,
    val copy: String,
    val primaryAction: String
)

@Immutable
data class SourceSwitchFlowUiState(
    val state: SourceSwitchFlowState = SourceSwitchFlowState.Default,
    val reader: SourceSwitchReaderState = SourceSwitchFlowFixture.reader,
    val filters: List<String> = SourceSwitchFlowFixture.filters,
    val selectedFilter: String = "全部",
    val detectHint: String = SourceSwitchFlowFixture.detectHint,
    val sources: List<SourceCandidateState> = SourceSwitchFlowFixture.sources,
    val checkingTitle: String = "正在检查",
    val checkingSteps: List<SourceCheckStepState> = SourceSwitchFlowFixture.checkingSteps,
    val checkingStatus: String = "检测中",
    val successTitle: String = "已切换到书香阁，阅读位置已保留",
    val closeLabel: String = "关闭",
    val feedback: Map<SourceSwitchFlowState, SourceSwitchFeedbackState> = SourceSwitchFlowFixture.feedback
)

object SourceSwitchFlowFixture {
    val reader = SourceSwitchReaderState(
        title = "长夜余火",
        chapter = "第 32 章",
        currentSource = "起点中文网",
        switchedSource = "书香阁",
        lines = listOf(
            "雨， 下了一整夜。",
            "城市像是被泼了一盆凉水，街灯昏黄，水面反着光，连呼吸都带着潮气。",
            "他站在巷口，手里攥着那封信。纸已经被雨水润湿，边角微微卷起，字迹却依旧清晰。"
        ),
        progress = "38%",
        chapterProgress = "第 32 / 128 章"
    )

    val filters = listOf("全部", "更新快", "已缓存", "可用")

    const val detectHint = "延迟越低越好，内容匹配度将在切换后校验。"

    val sources = listOf(
        SourceCandidateState(
            name = "起点中文网",
            badge = "当前",
            chapter = "第 32 章",
            updated = "刚刚",
            latency = "120ms",
            status = "可用",
            current = true
        ),
        SourceCandidateState(
            name = "书香阁",
            chapter = "第 32 章",
            updated = "2 分钟前",
            latency = "98ms",
            status = "可用",
            checking = true
        ),
        SourceCandidateState(
            name = "笔趣阁",
            chapter = "第 32 章",
            updated = "5 分钟前",
            latency = "156ms",
            status = "可用"
        ),
        SourceCandidateState(
            name = "本地缓存",
            chapter = "第 32 章",
            updated = "昨天 23:15",
            latency = "本地",
            status = "可用"
        ),
        SourceCandidateState(
            name = "旧源备份",
            chapter = "第 31 章",
            updated = "3 天前",
            latency = "超时",
            status = "失效"
        )
    )

    val checkingSteps = listOf(
        SourceCheckStepState("目录匹配", done = true),
        SourceCheckStepState("章节可读", done = true),
        SourceCheckStepState("内容校验", done = false)
    )

    val feedback = mapOf(
        SourceSwitchFlowState.Loading to SourceSwitchFeedbackState("正在加载", "正在刷新来源列表，请稍候。", "取消检测"),
        SourceSwitchFlowState.Empty to SourceSwitchFeedbackState("暂无可用来源", "当前章节没有可切换来源，可以稍后重试。", "重试检测"),
        SourceSwitchFlowState.Error to SourceSwitchFeedbackState("加载失败，请重试", "来源检测失败，已保留当前阅读页和阅读位置。", "重试"),
        SourceSwitchFlowState.Offline to SourceSwitchFeedbackState("网络不可用，请稍后重试", "本地缓存仍可阅读，联网后才能检测和切换来源。", "使用本地缓存"),
        SourceSwitchFlowState.Permission to SourceSwitchFeedbackState("需要网络权限", "检测来源需要访问网络，授权后才能刷新可用结果。", "去授权")
    )

    fun state(state: SourceSwitchFlowState): SourceSwitchFlowUiState =
        SourceSwitchFlowUiState(state = state)
}

@Composable
fun SourceSwitchFlowScreen(
    uiState: SourceSwitchFlowUiState = SourceSwitchFlowUiState(),
    modifier: Modifier = Modifier,
    onBackToReading: () -> Unit = {},
    onOpenSourceSheet: () -> Unit = {},
    onFilterChange: (String) -> Unit = {},
    onStartDetect: () -> Unit = {},
    onCancelDetect: () -> Unit = {},
    onSwitchSource: (SourceCandidateState) -> Unit = {},
    onRetry: () -> Unit = {},
    onGrantPermission: () -> Unit = {}
) {
    ReaderTheme {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .padding(ReaderTheme.spacing.lg)
                .semantics { contentDescription = "FlowShell flowFrame 换源" },
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.md)
        ) {
            FlowRegionCard(
                slotName = "stepRegion",
                title = "换源",
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
            ) {
                SourceSwitchStepRegion(uiState, onBackToReading, onOpenSourceSheet, onFilterChange, onStartDetect, onCancelDetect)
            }
            FlowRegionCard(
                slotName = "comparisonRegion",
                title = "候选来源",
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
            ) {
                SourceSwitchComparisonRegion(uiState, onSwitchSource)
            }
            FlowRegionCard(
                slotName = "resultRegion",
                title = "切换结果",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SourceSwitchResultRegion(uiState, onBackToReading)
            }
            FlowRegionCard(
                slotName = "stateHost",
                title = "状态",
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
            ) {
                SourceSwitchStateHost(uiState, onRetry, onGrantPermission, onCancelDetect)
            }
        }
    }
}

@Composable
private fun FlowRegionCard(
    slotName: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    ReaderCard(
        modifier = modifier.semantics { contentDescription = "FlowShell $slotName" },
        contentDescription = "FlowShell $slotName"
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.sectionTitle
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        content()
    }
}

@Composable
private fun SourceSwitchStepRegion(
    uiState: SourceSwitchFlowUiState,
    onBackToReading: () -> Unit,
    onOpenSourceSheet: () -> Unit,
    onFilterChange: (String) -> Unit,
    onStartDetect: () -> Unit,
    onCancelDetect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(
            text = uiState.reader.title,
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.pageTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ReaderChip(text = "当前来源 ${uiState.reader.currentSource}", selected = true)
        Text(uiState.reader.chapter, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        Text(uiState.detectHint, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        ReaderPrimaryButton(text = "打开换源底表", onClick = onOpenSourceSheet, modifier = Modifier.fillMaxWidth())
        ReaderSecondaryButton(text = "开始检测", onClick = onStartDetect, modifier = Modifier.fillMaxWidth())
        ReaderSecondaryButton(text = "取消检测", onClick = onCancelDetect, modifier = Modifier.fillMaxWidth())
        ReaderSecondaryButton(text = "返回阅读页", onClick = onBackToReading, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            uiState.filters.forEach { filter ->
                ReaderChip(
                    text = filter,
                    selected = filter == uiState.selectedFilter,
                    onClick = { onFilterChange(filter) }
                )
            }
        }
    }
}

@Composable
private fun SourceSwitchComparisonRegion(
    uiState: SourceSwitchFlowUiState,
    onSwitchSource: (SourceCandidateState) -> Unit
) {
    when (uiState.state) {
        SourceSwitchFlowState.Loading -> SourceSwitchFeedbackCard(uiState.feedback.getValue(SourceSwitchFlowState.Loading), onAction = {})
        SourceSwitchFlowState.Empty -> SourceSwitchFeedbackCard(uiState.feedback.getValue(SourceSwitchFlowState.Empty), onAction = {})
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ReaderTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                items(uiState.sources, key = { it.name }) { source ->
                    SourceCandidateRow(source = source, onSwitchSource = onSwitchSource)
                }
            }
        }
    }
}

@Composable
private fun SourceCandidateRow(
    source: SourceCandidateState,
    onSwitchSource: (SourceCandidateState) -> Unit
) {
    val enabled = !source.current && source.status == "可用"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.floatingControlBg)
            .padding(ReaderTheme.spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(source.name, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
                    if (source.current) {
                        CurrentSourceBadge(label = source.badge.ifBlank { "当前" })
                    }
                    if (source.checking) {
                        DetectStatusBadge(status = "检测中")
                    }
                }
                Text("${source.chapter} · ${source.updated} · ${source.latency}", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
            DetectStatusBadge(status = source.status)
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        SwitchSourceButton(
            label = if (source.current) "当前来源" else "切换来源",
            enabled = enabled,
            onClick = { onSwitchSource(source) }
        )
    }
}

@Composable
private fun CurrentSourceBadge(label: String) {
    ReaderChip(text = label, selected = true)
}

@Composable
private fun DetectStatusBadge(status: String) {
    ReaderChip(text = status, selected = status == "可用" || status == "检测中")
}

@Composable
private fun SwitchSourceButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (enabled) {
        ReaderPrimaryButton(text = label, onClick = onClick, modifier = Modifier.fillMaxWidth())
    } else {
        ReaderSecondaryButton(text = label, onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SourceSwitchResultRegion(
    uiState: SourceSwitchFlowUiState,
    onBackToReading: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text("当前来源", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        ReaderChip(text = uiState.reader.currentSource, selected = true)
        Text("切换目标", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        ReaderChip(text = uiState.reader.switchedSource, selected = false)
        Text(uiState.successTitle, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        ReaderPrimaryButton(text = uiState.closeLabel, onClick = onBackToReading, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.card)
                .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.card)
                .background(ReaderTheme.colors.floatingControlBg)
                .padding(ReaderTheme.spacing.sm)
                .semantics { contentDescription = "阅读位置预览" },
            verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            Text(uiState.reader.chapterProgress, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
            Text("阅读进度 ${uiState.reader.progress}", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            uiState.reader.lines.forEach { line ->
                Text(line, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerBody)
            }
        }
    }
}

@Composable
private fun SourceSwitchStateHost(
    uiState: SourceSwitchFlowUiState,
    onRetry: () -> Unit,
    onGrantPermission: () -> Unit,
    onCancelDetect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text("状态区 stateHost", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        if (uiState.state == SourceSwitchFlowState.Default) {
            Text(uiState.checkingTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
            uiState.checkingSteps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReaderChip(text = if (step.done) "完成" else uiState.checkingStatus, selected = step.done)
                    Spacer(modifier = Modifier.width(ReaderTheme.spacing.xs))
                    Text(step.label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
            }
        } else {
            val feedback = uiState.feedback.getValue(uiState.state)
            SourceSwitchFeedbackCard(
                feedback = feedback,
                onAction = when (uiState.state) {
                    SourceSwitchFlowState.Permission -> onGrantPermission
                    SourceSwitchFlowState.Loading -> onCancelDetect
                    else -> onRetry
                }
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
            Text(
                text = "default / loading / empty / error / offline / permission",
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.bookMeta
            )
        }
    }
}

@Composable
private fun SourceSwitchFeedbackCard(
    feedback: SourceSwitchFeedbackState,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.metaBg)
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Text(feedback.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        ReaderPrimaryButton(text = feedback.primaryAction, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "FlowShell / Source Switch / Default", widthDp = 1690, heightDp = 931, showBackground = true)
@Composable
fun SourceSwitchFlowDefaultPreview() {
    SourceSwitchFlowScreen()
}

@Preview(name = "FlowShell / Source Switch / Loading", widthDp = 1690, heightDp = 931, showBackground = true)
@Composable
fun SourceSwitchFlowLoadingPreview() {
    SourceSwitchFlowScreen(uiState = SourceSwitchFlowFixture.state(SourceSwitchFlowState.Loading))
}

@Preview(name = "FlowShell / Source Switch / Empty", widthDp = 1690, heightDp = 931, showBackground = true)
@Composable
fun SourceSwitchFlowEmptyPreview() {
    SourceSwitchFlowScreen(uiState = SourceSwitchFlowFixture.state(SourceSwitchFlowState.Empty))
}

@Preview(name = "FlowShell / Source Switch / Error", widthDp = 1690, heightDp = 931, showBackground = true)
@Composable
fun SourceSwitchFlowErrorPreview() {
    SourceSwitchFlowScreen(uiState = SourceSwitchFlowFixture.state(SourceSwitchFlowState.Error))
}

@Preview(name = "FlowShell / Source Switch / Offline", widthDp = 1690, heightDp = 931, showBackground = true)
@Composable
fun SourceSwitchFlowOfflinePreview() {
    SourceSwitchFlowScreen(uiState = SourceSwitchFlowFixture.state(SourceSwitchFlowState.Offline))
}

@Preview(name = "FlowShell / Source Switch / Permission", widthDp = 1690, heightDp = 931, showBackground = true)
@Composable
fun SourceSwitchFlowPermissionPreview() {
    SourceSwitchFlowScreen(uiState = SourceSwitchFlowFixture.state(SourceSwitchFlowState.Permission))
}
