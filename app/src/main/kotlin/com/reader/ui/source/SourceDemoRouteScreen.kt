package com.reader.ui.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.tokens.ReaderTypeToken
import com.reader.android.R
import com.reader.ui.shell.SettingsShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun SourceDemoRouteScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val state = sourceDemoRouteState(routeId) ?: sourceDetailRouteState()
    SourceDemoRouteScreen(
        state = state,
        onBack = onBack,
        onNavigate = onNavigate
    )
}

/**
 * Content-only entry for canonical ScreenGraph nodes. Shell-level bottom actions,
 * sheets, and dialogs intentionally remain outside this adapter boundary.
 */
@Composable
internal fun SourceDemoCanonicalContent(
    state: SourceDemoRouteState,
    onNavigate: (String) -> Unit
) {
    when (state) {
        is SourceManagementRouteState -> SourceManagementContent(state, onNavigate)
        is SourceRuleEditRouteState -> SourceRuleEditContent(state, onNavigate)
        is SourceDebugRouteState -> SourceDebugContent(state, onNavigate)
    }
}

@Composable
fun SourceDemoRouteScreen(
    state: SourceDemoRouteState,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    SettingsShellFrame(
        backTopBar = {
            SourceDemoTopBar(
                title = state.title,
                trailingAction = state.trailingAction,
                onBack = onBack,
                onAction = { handleSourceDemoAction(it, onBack, onNavigate) }
            )
        },
        settingsContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state) {
                    is SourceManagementRouteState -> SourceManagementContent(state, onNavigate)
                    is SourceRuleEditRouteState -> SourceRuleEditContent(state, onNavigate)
                    is SourceDebugRouteState -> SourceDebugContent(state, onNavigate)
                }
            }
        },
        bottomActionHost = {
            if (state.actions.isNotEmpty()) {
                SourceDemoBottomActions(
                    actions = state.actions,
                    onAction = { handleSourceDemoAction(it, onBack, onNavigate) }
                )
            }
        },
        sheetHost = {
            if (state is SourceManagementRouteState && state.page == SourceManagementPage.ImportOptions) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SourceImportOptionsSheet(
                        actions = state.sheetActions,
                        onAction = { handleSourceDemoAction(it, onBack, onNavigate) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        },
        dialogHost = {
            if (state is SourceManagementRouteState && state.page == SourceManagementPage.DeleteConfirm) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SourceDeleteDialog(
                        actions = state.dialogActions,
                        onAction = { handleSourceDemoAction(it, onBack, onNavigate) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    )
}

@Composable
private fun SourceManagementContent(
    state: SourceManagementRouteState,
    onNavigate: (String) -> Unit
) {
    when (state.page) {
        SourceManagementPage.ImportOptions -> {
            SourceSearchFilters()
            SourceList(rows = state.sourceRows, batch = false, onNavigate = onNavigate)
        }

        SourceManagementPage.Batch -> {
            SourceBatchTop()
            SourceSearchFilters()
            SourceList(rows = state.sourceRows, batch = true, onNavigate = onNavigate)
        }

        SourceManagementPage.Groups -> {
            SourceNotice("分组用于筛选和批量整理书源，删除分组不会删除书源。", SourceDemoIcon.Folder)
            SourceSimpleRowList(rows = state.groupRows, onNavigate = onNavigate)
        }

        SourceManagementPage.Detail -> SourceDetailContent(state)

        SourceManagementPage.Logs -> {
            SourceChipRow(listOf("全部", "异常", "警告", "今日"), active = "全部")
            SourceSearchBox("搜索书源或错误内容")
            SourceStatLine("4 条异常 · 1 条警告")
            SourceSimpleRowList(rows = state.logRows, onNavigate = onNavigate)
        }

        SourceManagementPage.DeleteConfirm -> {
            SourceBatchTop()
            SourceSearchFilters()
            SourceList(rows = state.sourceRows, batch = true, onNavigate = onNavigate, dimmed = true)
        }
    }
}

@Composable
private fun SourceDetailContent(state: SourceManagementRouteState) {
    SourceContextHead(
        title = "笔趣阁",
        meta = "biquge.example · 玄幻书源",
        badge = null,
        switchOn = true
    )
    SourceStatLine("异常 · 最近检测 10:30 · 规则版本 3")
    SourceModuleGrid(state.moduleRows)
    SourceTextCard(
        title = "最近检测结果",
        body = "搜索、详情、目录均可解析；正文模块失败。",
        meta = "失败规则：正文内容规则“#content@text”返回空内容。建议进入规则编辑后调测正文模块。"
    )
    SourceInfoGrid(state.infoRows)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SourceInlineAction(SourceDemoIcon.Copy, "复制书源", Modifier.weight(1f))
        SourceInlineAction(SourceDemoIcon.Upload, "导出书源", Modifier.weight(1f))
    }
}

@Composable
private fun SourceRuleEditContent(
    state: SourceRuleEditRouteState,
    onNavigate: (String) -> Unit
) {
    SourceContextHead(
        title = "笔趣阁",
        meta = "正在编辑：正文规则",
        badge = null,
        switchOn = true
    )
    SourceChipRow(listOf("基本", "搜索", "详情", "目录", "正文", "高级"), active = "正文")
    SourceInfoGrid(state.overviewRows)
    state.sections.forEach { section ->
        SourceSectionTitle(section.title)
        Column(
            modifier = sourceCardModifier(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            section.rows.forEachIndexed { index, row ->
                SourceRuleRow(row = row, onClick = { onNavigate(SourceDemoRouteIds.SourceDebug) })
                if (index != section.rows.lastIndex) SourceDivider()
            }
        }
    }
    SourceTextCard(
        title = "当前规则说明",
        body = "正文规则用于从章节页面中提取正文文本。这里编辑的是解析表达式，不是 UI 显示规则。",
        meta = "规则修改后先调测当前模块，确认解析结果正常后再保存。"
    )
}

/** Content-only rule-edit entry; canonical BackTopBar remains a separate ScreenGraph node. */
@Composable
internal fun SourceRuleEditCanonicalContent(
    state: SourceRuleEditRouteState,
    onNavigate: (String) -> Unit
) {
    SourceDemoCanonicalContent(state = state, onNavigate = onNavigate)
}

@Composable
private fun SourceDebugContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 700.dp) {
            SourceDebugWideContent(state = state, onNavigate = onNavigate)
        } else {
            SourceDebugLinearContent(state = state, onNavigate = onNavigate)
        }
    }
}

/** Content-only entry for the canonical ScreenGraph renderer, whose shell/top bar are separate nodes. */
@Composable
internal fun SourceDebugCanonicalContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceDemoCanonicalContent(state = state, onNavigate = onNavigate)
}

@Composable
private fun SourceDebugLinearContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceDebugContextHead(state)
    when (state.page) {
        SourceDebugPage.Detect -> SourceDetectContent(state, onNavigate)
        SourceDebugPage.CodeView -> SourceCodeContent(state, onNavigate)
        SourceDebugPage.ContentLog -> SourceContentLogContent(state, onNavigate)
        SourceDebugPage.ContentDebug,
        SourceDebugPage.SearchResult,
        SourceDebugPage.DetailResult,
        SourceDebugPage.CatalogResult -> SourceDebugResultContent(state, onNavigate)
    }
}

@Composable
private fun SourceDebugWideContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceDebugContextHead(state)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(0.72f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SourceDebugWideLeftColumn(state = state, onNavigate = onNavigate)
            }
            Column(
                modifier = Modifier.weight(1.28f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SourceDebugWideRightColumn(state = state, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun SourceDebugContextHead(state: SourceDebugRouteState) {
    SourceContextHead(
        title = state.contextTitle,
        meta = state.contextMeta,
        badge = state.contextBadge,
        switchOn = false
    )
}

@Composable
private fun SourceDebugWideLeftColumn(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    when (state.page) {
        SourceDebugPage.Detect -> {
            SourceDetectSummary()
            SourceDetectStepList(state = state, onNavigate = onNavigate)
        }
        SourceDebugPage.CodeView -> Spacer(Modifier.height(1.dp))
        SourceDebugPage.ContentLog -> SourceDebugModuleTabs(
            activeKey = state.activeModuleKey,
            onNavigate = onNavigate,
            stacked = true
        )
        SourceDebugPage.ContentDebug,
        SourceDebugPage.SearchResult,
        SourceDebugPage.DetailResult,
        SourceDebugPage.CatalogResult -> {
            SourceDebugModuleTabs(activeKey = state.activeModuleKey, onNavigate = onNavigate, stacked = true)
            SourceDebugCases(activeKey = state.activeModuleKey)
        }
    }
}

@Composable
private fun SourceDebugWideRightColumn(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    when (state.page) {
        SourceDebugPage.Detect -> SourceSuggestionCard(state)
        SourceDebugPage.CodeView -> SourceCodeDetailContent(state = state, onNavigate = onNavigate)
        SourceDebugPage.ContentLog -> SourceContentLogDetailContent(state = state, onNavigate = onNavigate)
        SourceDebugPage.ContentDebug,
        SourceDebugPage.SearchResult,
        SourceDebugPage.DetailResult,
        SourceDebugPage.CatalogResult -> SourceDebugResultDetailContent(state = state, onNavigate = onNavigate)
    }
}

@Composable
private fun SourceDetectContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceDetectSummary()
    SourceDetectStepList(state = state, onNavigate = onNavigate)
    SourceSuggestionCard(state)
}

@Composable
private fun SourceDetectStepList(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    Column(modifier = sourceCardModifier()) {
        state.detectSteps.forEachIndexed { index, row ->
            SourceDetectStep(row = row, onNavigate = onNavigate)
            if (index != state.detectSteps.lastIndex) SourceDivider()
        }
    }
}

@Composable
private fun SourceDebugResultContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceDebugModuleTabs(activeKey = state.activeModuleKey, onNavigate = onNavigate)
    SourceDebugCases(activeKey = state.activeModuleKey)
    SourceDebugResultDetailContent(state = state, onNavigate = onNavigate)
}

@Composable
private fun SourceDebugResultDetailContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceKeyValuePanel(state.inputs)
    state.request?.let { SourceRequestLine(it) }
    SourceDebugSegment(state, activeLabel = "解析结果", onNavigate = onNavigate)
    SourceKeyValuePanel(state.parsedRows)
    SourceSuggestionCard(state)
}

@Composable
private fun SourceContentLogContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceDebugModuleTabs(activeKey = state.activeModuleKey, onNavigate = onNavigate)
    SourceContentLogDetailContent(state = state, onNavigate = onNavigate)
}

@Composable
private fun SourceContentLogDetailContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceKeyValuePanel(state.inputs)
    state.request?.let { SourceRequestLine(it) }
    SourceDebugSegment(state, activeLabel = "日志", onNavigate = onNavigate)
    SourceSimpleRowList(rows = state.logs, onNavigate = onNavigate)
    SourceSuggestionCard(state)
}

@Composable
private fun SourceCodeContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceCodeDetailContent(state = state, onNavigate = onNavigate)
}

@Composable
private fun SourceCodeDetailContent(
    state: SourceDebugRouteState,
    onNavigate: (String) -> Unit
) {
    SourceKeyValuePanel(state.inputs)
    state.request?.let { SourceRequestLine(it) }
    SourceDebugSegment(state, activeLabel = "源码", onNavigate = onNavigate)
    Column(
        modifier = sourceCardModifier().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        state.codeLines.forEachIndexed { index, line ->
            Text(
                text = "${(index + 1).toString().padStart(2, '0')}  $line",
                style = sourceCodeStyle(),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun SourceDemoTopBar(
    title: String,
    trailingAction: SourceDemoAction?,
    onBack: () -> Unit,
    onAction: (SourceDemoAction) -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
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
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingAction == null) {
            Spacer(Modifier.size(44.dp))
        } else {
            SourceSmallPillButton(label = trailingAction.label, danger = trailingAction.danger) {
                onAction(trailingAction)
            }
        }
    }
}

@Composable
private fun SourceSearchFilters() {
    SourceSearchBox("搜索书源名称或域名")
    SourceStatLine("12 个书源 · 8 个启用 · 4 个异常 · 10:30 检测")
    SourceFilterSummary()
}

@Composable
private fun SourceSearchBox(label: String) {
    Row(
        modifier = sourceCardModifier()
            .defaultMinSize(minHeight = 42.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_search),
            contentDescription = null,
            tint = readerExtraColors().muted,
            modifier = Modifier.size(17.dp)
        )
        Text(text = label, style = sourceMetaStyle(), color = readerExtraColors().muted, maxLines = 1)
    }
}

@Composable
private fun SourceFilterSummary() {
    Column(modifier = sourceCardModifier().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceBadgeView(SourceDemoBadge("筛选", SourceDemoTone.Info))
            Text("全部 · 全部分组", style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        }
        SourceChipRow(listOf("全部", "已启用", "异常", "未检测", "自定义"), active = "全部", compact = true)
        SourceChipRow(listOf("全部分组", "玄幻书源", "起点导入", "测试书源"), active = "全部分组", compact = true)
    }
}

@Composable
private fun SourceList(
    rows: List<SourceDemoRow>,
    batch: Boolean,
    onNavigate: (String) -> Unit,
    dimmed: Boolean = false
) {
    Column(
        modifier = sourceCardModifier().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        rows.forEachIndexed { index, row ->
            SourceRow(row = row, batch = batch, dimmed = dimmed, onNavigate = onNavigate)
            if (index != rows.lastIndex) SourceDivider()
        }
    }
}

@Composable
private fun SourceRow(
    row: SourceDemoRow,
    batch: Boolean,
    dimmed: Boolean,
    onNavigate: (String) -> Unit
) {
    val routeClick = row.routeId?.let { route -> { onNavigate(route) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(if (!batch && routeClick != null) Modifier.clickable(onClick = routeClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (batch) {
            SourceSelectionDot(selected = row.selected)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = row.title,
                style = sourceTitleStyle(),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (dimmed) 0.56f else 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.meta,
                style = sourceMetaStyle(),
                color = readerExtraColors().muted.copy(alpha = if (dimmed) 0.56f else 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        row.badge?.let { SourceBadgeView(it) }
        if (!batch) {
            SourceSmallPillButton(label = "检测", danger = false) {
                onNavigate(SourceDemoRouteIds.SourceDetect)
            }
        }
        SourceTinySwitch(checked = row.enabled)
    }
}

@Composable
private fun SourceBatchTop() {
    Row(
        modifier = sourceCardModifier()
            .defaultMinSize(minHeight = 42.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("取消", style = sourceActionStyle(), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        Text("已选 3 个", style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        Text("全选", style = sourceActionStyle(), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SourceSimpleRowList(
    rows: List<SourceDemoRow>,
    onNavigate: (String) -> Unit
) {
    Column(modifier = sourceCardModifier().padding(horizontal = 10.dp)) {
        rows.forEachIndexed { index, row ->
            SourceSimpleRow(row, onNavigate)
            if (index != rows.lastIndex) SourceDivider()
        }
    }
}

@Composable
private fun SourceSimpleRow(
    row: SourceDemoRow,
    onNavigate: (String) -> Unit
) {
    val clickModifier = row.routeId?.let { route -> Modifier.clickable { onNavigate(route) } } ?: Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .then(clickModifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        row.icon?.let { SourceIconCircle(icon = it, size = 30.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.title, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.meta, style = sourceMetaStyle(), color = readerExtraColors().muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        row.badge?.let { SourceBadgeView(it) }
        if (row.routeId != null) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_chevron),
                contentDescription = null,
                tint = readerExtraColors().muted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SourceModuleGrid(rows: List<SourceDemoRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.chunked(3).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEach { row ->
                    Column(
                        modifier = sourceCardModifier()
                            .weight(1f)
                            .heightIn(min = 66.dp)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(row.title, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
                        row.badge?.let { SourceBadgeView(it) }
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SourceInfoGrid(rows: List<SourceDemoKeyValue>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.chunked(2).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEach { row ->
                    Column(
                        modifier = sourceCardModifier()
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(row.label, style = sourceMetaStyle(), color = readerExtraColors().muted, maxLines = 1)
                        Text(row.value, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (chunk.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SourceKeyValuePanel(rows: List<SourceDemoKeyValue>) {
    if (rows.isEmpty()) return
    Column(modifier = sourceCardModifier().padding(horizontal = 10.dp)) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 42.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(row.label, style = sourceMetaStyle(), color = readerExtraColors().muted, modifier = Modifier.width(86.dp), maxLines = 1)
                Text(row.value, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (index != rows.lastIndex) SourceDivider()
        }
    }
}

@Composable
private fun SourceRuleRow(row: SourceDemoKeyValue, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(row.label, style = sourceMetaStyle(), color = readerExtraColors().muted, modifier = Modifier.width(78.dp))
        Text(row.value, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(painterResource(R.drawable.reader_ic_chevron), null, tint = readerExtraColors().muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SourceDetectSummary() {
    Column(modifier = sourceCardModifier().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("5 项检测 · 4 项通过 · 1 项失败", style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        Text("总耗时 1260ms · 最近检测 10:30", style = sourceMetaStyle(), color = readerExtraColors().muted)
    }
}

@Composable
private fun SourceDetectStep(row: SourceDemoRow, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        row.badge?.let { SourceBadgeView(it) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.title, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
            Text(row.meta, style = sourceMetaStyle(), color = readerExtraColors().muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        SourceSmallPillButton(label = if (row.routeId == SourceDemoRouteIds.SourceCodeView) "源码" else "调测", danger = false) {
            row.routeId?.let(onNavigate)
        }
    }
}

@Composable
private fun SourceDebugModuleTabs(
    activeKey: String,
    onNavigate: (String) -> Unit,
    stacked: Boolean = false
) {
    val content: @Composable () -> Unit = {
        sourceDebugModules().forEach { module ->
            val active = module.key == activeKey
            Column(
                modifier = Modifier
                    .then(if (stacked) Modifier.fillMaxWidth() else Modifier.width(138.dp))
                    .defaultMinSize(minHeight = 76.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ReaderShapes.md
                    )
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.34f) else readerExtraColors().hairline.copy(alpha = 0.72f),
                        ReaderShapes.md
                    )
                    .clickable { onNavigate(module.routeId) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(module.title, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
                Text(module.meta, style = sourceMetaStyle(), color = readerExtraColors().muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SourceBadgeView(module.badge)
            }
        }
    }
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    } else {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SourceDebugCases(activeKey: String) {
    Column(modifier = sourceCardModifier().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sourceDebugCases().forEach { item ->
            val active = item.key == activeKey
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                        ReaderShapes.sm
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    SourceBadgeView(item.badge)
                }
                Text("${item.inputLabel} · ${item.inputValue}", style = sourceMetaStyle(), color = readerExtraColors().muted)
                Text("${item.ruleLabel} · ${item.ruleValue}", style = sourceMetaStyle(), color = readerExtraColors().muted)
                Text(item.result, style = sourceMetaStyle(), color = if (item.badge.tone == SourceDemoTone.Warn) readerExtraColors().danger else readerExtraColors().muted)
            }
        }
    }
}

@Composable
private fun SourceDebugSegment(
    state: SourceDebugRouteState,
    activeLabel: String,
    onNavigate: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        state.segmentActions.forEach { action ->
            val selected = action.label == activeLabel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 34.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), ReaderShapes.pill)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else readerExtraColors().hairline, ReaderShapes.pill)
                    .clickable { action.routeId?.let(onNavigate) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    action.label,
                    style = sourceActionStyle(),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SourceRequestLine(text: String) {
    Text(
        text = text,
        style = sourceMetaStyle().copy(lineHeight = 15.sp),
        color = readerExtraColors().muted,
        modifier = sourceCardModifier().padding(10.dp)
    )
}

@Composable
private fun SourceSuggestionCard(state: SourceDebugRouteState) {
    SourceTextCard(
        title = state.suggestionTitle ?: return,
        body = state.suggestionText.orEmpty(),
        meta = state.suggestionMeta
    )
}

@Composable
private fun SourceTextCard(title: String, body: String, meta: String? = null) {
    Column(modifier = sourceCardModifier().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = sourceSectionStyle(), color = MaterialTheme.colorScheme.onBackground)
        Text(body, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        meta?.let { Text(it, style = sourceMetaStyle(), color = readerExtraColors().muted) }
    }
}

@Composable
private fun SourceNotice(text: String, icon: SourceDemoIcon) {
    Row(
        modifier = sourceCardModifier()
            .defaultMinSize(minHeight = 52.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SourceIconCircle(icon = icon, size = 30.dp)
        Text(text, style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SourceContextHead(
    title: String,
    meta: String,
    badge: SourceDemoBadge?,
    switchOn: Boolean
) {
    Row(
        modifier = sourceCardModifier()
            .defaultMinSize(minHeight = 62.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SourceIconCircle(icon = SourceDemoIcon.Source, size = 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = sourceSectionStyle(), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = sourceMetaStyle(), color = readerExtraColors().muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        badge?.let { SourceBadgeView(it) }
        if (switchOn) SourceTinySwitch(checked = true)
    }
}

@Composable
private fun SourceChipRow(
    labels: List<String>,
    active: String,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            val selected = label == active
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = if (compact) 58.dp else 70.dp, minHeight = 32.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else readerExtraColors().metaBackground.copy(alpha = 0.82f), ReaderShapes.pill)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else readerExtraColors().hairline, ReaderShapes.pill)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = sourceActionStyle(),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SourceSectionTitle(title: String) {
    Text(text = title, style = sourceSectionStyle(), color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun SourceStatLine(text: String) {
    Text(text = text, style = sourceMetaStyle().copy(lineHeight = 15.sp), color = readerExtraColors().muted)
}

@Composable
private fun SourceDemoBottomActions(
    actions: List<SourceDemoAction>,
    onAction: (SourceDemoAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { action ->
            SourceBottomButton(
                action = action,
                onClick = { onAction(action) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SourceBottomButton(action: SourceDemoAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = when {
        action.danger -> extra.danger.copy(alpha = 0.12f)
        action.primary -> colors.primary
        else -> colors.surface.copy(alpha = 0.90f)
    }
    val foreground = when {
        action.danger -> extra.danger
        action.primary -> colors.onPrimary
        else -> colors.primary
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 42.dp)
            .background(background, ReaderShapes.md)
            .border(1.dp, if (action.primary) colors.primary else foreground.copy(alpha = 0.26f), ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        action.icon?.let {
            Icon(painterResource(sourceDemoIconRes(it)), null, tint = foreground, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(action.label, style = sourceActionStyle(), color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SourceImportOptionsSheet(
    actions: List<SourceDemoAction>,
    onAction: (SourceDemoAction) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, ReaderShapes.xl, clip = false)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), ReaderShapes.xl)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.xl)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .background(readerExtraColors().hairline, ReaderShapes.pill)
        )
        Text("添加书源", style = sourceSectionStyle(), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth())
        actions.forEach { action ->
            SourceSheetAction(action = action) { onAction(action) }
        }
    }
}

@Composable
private fun SourceSheetAction(action: SourceDemoAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .background(if (action.routeBack) Color.Transparent else readerExtraColors().metaBackground.copy(alpha = 0.74f), ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        action.icon?.let { SourceIconCircle(icon = it, size = 30.dp) }
        Text(
            action.label,
            style = sourceTitleStyle(),
            color = if (action.routeBack) readerExtraColors().muted else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            textAlign = if (action.routeBack) TextAlign.Center else TextAlign.Start
        )
        if (!action.routeBack) {
            Icon(painterResource(R.drawable.reader_ic_chevron), null, tint = readerExtraColors().muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SourceDeleteDialog(
    actions: List<SourceDemoAction>,
    onAction: (SourceDemoAction) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
    )
    Column(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth()
            .shadow(24.dp, ReaderShapes.lg, clip = false)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), ReaderShapes.lg)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.lg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("删除书源？", style = sourceDialogTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        Text(
            "将删除已选 3 个书源。不会删除书架书籍，但这些书源将不再参与搜索、发现和换源。",
            style = sourceTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = sourceCardModifier().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SourceSelectionDot(selected = false)
            Text("同时清除相关检测日志", style = sourceTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { action ->
                SourceBottomButton(action = action, onClick = { onAction(action) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SourceInlineAction(icon: SourceDemoIcon, label: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 42.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(sourceDemoIconRes(icon)), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = sourceActionStyle(), color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
private fun SourceSmallPillButton(label: String, danger: Boolean, onClick: () -> Unit) {
    val foreground = if (danger) readerExtraColors().danger else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.pill)
            .border(1.dp, foreground.copy(alpha = 0.24f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = sourceActionStyle(), color = foreground, maxLines = 1)
    }
}

@Composable
private fun SourceIconCircle(icon: SourceDemoIcon, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = sourceDemoIconRes(icon)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
private fun SourceSelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, ReaderShapes.pill)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else readerExtraColors().hairline, ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(painterResource(R.drawable.reader_ic_check), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SourceTinySwitch(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(20.dp)
            .background(if (checked) MaterialTheme.colorScheme.primary else readerExtraColors().hairline, ReaderShapes.pill)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(MaterialTheme.colorScheme.surface, ReaderShapes.pill)
        )
    }
}

@Composable
private fun SourceBadgeView(badge: SourceDemoBadge) {
    val color = sourceToneColor(badge.tone)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 38.dp, minHeight = 24.dp)
            .background(color.copy(alpha = 0.11f), ReaderShapes.pill)
            .border(1.dp, color.copy(alpha = 0.28f), ReaderShapes.pill)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(badge.label, style = sourceBadgeStyle(), color = color, maxLines = 1)
    }
}

@Composable
private fun SourceDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.62f))
    )
}

private fun handleSourceDemoAction(
    action: SourceDemoAction,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when {
        action.routeId != null -> onNavigate(action.routeId)
        action.routeBack -> onBack()
    }
}

@Composable
private fun sourceCardModifier(): Modifier = Modifier
    .fillMaxWidth()
    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
    .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.72f), ReaderShapes.md)

@Composable
private fun sourceToneColor(tone: SourceDemoTone): Color {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    return when (tone) {
        SourceDemoTone.Good -> extra.forest
        SourceDemoTone.Warn -> extra.danger
        SourceDemoTone.Muted -> extra.muted
        SourceDemoTone.Info -> colors.primary
    }
}

private fun sourceDemoIconRes(icon: SourceDemoIcon): Int = when (icon) {
    SourceDemoIcon.Activity -> R.drawable.reader_ic_activity
    SourceDemoIcon.Add -> R.drawable.reader_ic_add
    SourceDemoIcon.Check -> R.drawable.reader_ic_check
    SourceDemoIcon.Close -> R.drawable.reader_ic_close
    SourceDemoIcon.Cloud -> R.drawable.reader_ic_cloud
    SourceDemoIcon.Code -> R.drawable.reader_ic_code
    SourceDemoIcon.Copy -> R.drawable.reader_ic_copy
    SourceDemoIcon.Download -> R.drawable.reader_ic_download
    SourceDemoIcon.Edit -> R.drawable.reader_ic_edit
    SourceDemoIcon.File -> R.drawable.reader_ic_file
    SourceDemoIcon.Folder -> R.drawable.reader_ic_folder
    SourceDemoIcon.Log -> R.drawable.reader_ic_log
    SourceDemoIcon.More -> R.drawable.reader_ic_more
    SourceDemoIcon.Refresh -> R.drawable.reader_ic_refresh
    SourceDemoIcon.Search -> R.drawable.reader_ic_search
    SourceDemoIcon.Source -> R.drawable.reader_ic_source_stack
    SourceDemoIcon.Trash -> R.drawable.reader_ic_trash
    SourceDemoIcon.Upload -> R.drawable.reader_ic_upload
    SourceDemoIcon.Warning -> R.drawable.reader_ic_warning
}

private fun sourceTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(800)
)

private fun sourceSectionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.SECTION_TITLE.value,
    lineHeight = 18.sp,
    fontWeight = FontWeight(900)
)

private fun sourceMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun sourceActionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(900),
    textAlign = TextAlign.Center
)

private fun sourceBadgeStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 12.sp,
    fontWeight = FontWeight(800),
    textAlign = TextAlign.Center
)

private fun sourceDialogTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.READER_BODY.value,
    lineHeight = 22.sp,
    fontWeight = FontWeight(900)
)

private fun sourceCodeStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 15.sp,
    fontWeight = FontWeight(500)
)
