package com.reader.ui.restore

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.reader.ui.shell.SettingsShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun RestoreScreen(
    state: RestoreUiState,
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onScopeToggle: (String) -> Unit = {},
    onConflictChoice: (String, RestoreConflictChoice) -> Unit = { _, _ -> },
    onViewLog: () -> Unit = {}
) {
    val page = restorePageForRoute(routeId, state)
    Box(modifier = modifier) {
        SettingsShellFrame(
            backTopBar = { RestoreTopBar(title = page.title, onBack = onBack) },
            settingsContent = {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth >= 700.dp) {
                        RestoreWideContent(
                            page = page,
                            state = state,
                            onNavigate = onNavigate,
                            onScopeToggle = onScopeToggle,
                            onConflictChoice = onConflictChoice,
                            onViewLog = onViewLog
                        )
                    } else {
                        RestoreLinearContent(
                            page = page,
                            state = state,
                            onNavigate = onNavigate,
                            onScopeToggle = onScopeToggle,
                            onConflictChoice = onConflictChoice,
                            onViewLog = onViewLog
                        )
                    }
                }
            }
        )
    }
}

/**
 * Canonical read-only surface. It reuses native restore primitives while deliberately
 * omitting route actions and conflict choices because current ScreenGraph nodes have no bindings.
 */
@Composable
internal fun RestoreCanonicalReadOnlyContent(page: RestorePageState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RestoreHeader(page = page)
        RestoreHeroCard(page = page)
        if (page.scopes.isNotEmpty()) {
            RestoreScopeCard(
                scopes = page.scopes,
                selectedKeys = page.selectedScopeKeys,
                onScopeToggle = null
            )
        }
        if (page.warningTitle != null && page.warningBody != null) {
            RestoreWarning(title = page.warningTitle, body = page.warningBody)
        }
        if (page.stages.isNotEmpty()) RestoreStageList(stages = page.stages)
        if (page.resultItems.isNotEmpty()) RestoreResultList(items = page.resultItems)
    }
}

@Composable
private fun RestoreLinearContent(
    page: RestorePageState,
    state: RestoreUiState,
    onNavigate: (String) -> Unit,
    onScopeToggle: (String) -> Unit,
    onConflictChoice: (String, RestoreConflictChoice) -> Unit,
    onViewLog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { RestoreHeader(page = page) }
        item { RestoreHeroCard(page = page) }
        if (page.scopes.isNotEmpty()) {
            item {
                RestoreScopeCard(
                    scopes = page.scopes,
                    selectedKeys = page.selectedScopeKeys,
                    onScopeToggle = onScopeToggle
                )
            }
        }
        if (page.warningTitle != null && page.warningBody != null) {
            item {
                RestoreWarning(title = page.warningTitle, body = page.warningBody)
            }
        }
        if (page.stages.isNotEmpty()) {
            item { RestoreStageList(stages = page.stages) }
        }
        if (page.conflicts.isNotEmpty()) {
            item {
                RestoreConflictList(
                    conflicts = page.conflicts,
                    choices = state.conflictChoices,
                    onConflictChoice = onConflictChoice
                )
            }
        }
        if (page.resultItems.isNotEmpty()) {
            item { RestoreResultList(items = page.resultItems) }
        }
        if (page.actions.isNotEmpty()) {
            item {
                RestoreActionRow(
                    actions = page.actions,
                    onNavigate = onNavigate,
                    onViewLog = onViewLog
                )
            }
        }
    }
}

@Composable
private fun RestoreWideContent(
    page: RestorePageState,
    state: RestoreUiState,
    onNavigate: (String) -> Unit,
    onScopeToggle: (String) -> Unit,
    onConflictChoice: (String, RestoreConflictChoice) -> Unit,
    onViewLog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(0.92f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { RestoreHeader(page = page) }
            item { RestoreHeroCard(page = page) }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1.08f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (page.scopes.isNotEmpty()) {
                item {
                    RestoreScopeCard(
                        scopes = page.scopes,
                        selectedKeys = page.selectedScopeKeys,
                        onScopeToggle = onScopeToggle
                    )
                }
            }
            if (page.warningTitle != null && page.warningBody != null) {
                item {
                    RestoreWarning(title = page.warningTitle, body = page.warningBody)
                }
            }
            if (page.stages.isNotEmpty()) {
                item { RestoreStageList(stages = page.stages) }
            }
            if (page.conflicts.isNotEmpty()) {
                item {
                    RestoreConflictList(
                        conflicts = page.conflicts,
                        choices = state.conflictChoices,
                        onConflictChoice = onConflictChoice
                    )
                }
            }
            if (page.resultItems.isNotEmpty()) {
                item { RestoreResultList(items = page.resultItems) }
            }
            if (page.actions.isNotEmpty()) {
                item {
                    RestoreActionRow(
                        actions = page.actions,
                        onNavigate = onNavigate,
                        onViewLog = onViewLog
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreHeroCard(page: RestorePageState) {
    RestoreCard(title = page.heroTitle, body = page.heroBody) {
        if (page.summaryRows.isNotEmpty()) {
            RestoreSummaryGrid(rows = page.summaryRows)
        }
        page.progress?.let { progress ->
            RestoreProgressMeter(progress = progress)
        }
    }
}

@Composable
private fun RestoreTopBar(title: String, onBack: () -> Unit) {
    // Mirrors demo `fd-back-bar` grid (44px 1fr 44px) with start=20dp, gap=10dp.
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
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun RestoreHeader(page: RestorePageState) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.lg)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.lg)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = page.title,
                style = restoreCardTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = page.record,
                style = restoreMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RestoreBadgeView(badge = page.badge)
    }
}

@Composable
private fun RestoreCard(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.lg)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.lg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = title, style = restoreCardTitleStyle(), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = body, style = restoreBodyStyle(), color = extra.muted)
        content()
    }
}

@Composable
private fun RestoreSummaryGrid(rows: List<RestoreSummaryRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    RestoreSummaryCell(row = item, modifier = Modifier.weight(1f))
                }
                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RestoreSummaryCell(row: RestoreSummaryRow, modifier: Modifier = Modifier) {
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .background(extra.metaBackground.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = row.label,
            style = restoreCaptionStyle(),
            color = extra.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.value,
            style = restoreValueStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RestoreScopeCard(
    scopes: List<RestoreScopeSpec>,
    selectedKeys: List<String>,
    onScopeToggle: ((String) -> Unit)?
) {
    RestoreCard(
        title = "选择恢复范围",
        body = "只显示当前备份包含的数据类型。至少保留一项，开始恢复前可在这里调整。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            scopes.forEach { scope ->
                RestoreScopeRow(
                    scope = scope,
                    selected = scope.key in selectedKeys,
                    onClick = onScopeToggle?.let { toggle -> { toggle(scope.key) } }
                )
            }
        }
    }
}

@Composable
private fun RestoreScopeRow(scope: RestoreScopeSpec, selected: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val borderColor = if (selected) colors.primary.copy(alpha = 0.42f) else extra.hairline.copy(alpha = 0.64f)
    val background = if (selected) colors.primary.copy(alpha = 0.07f) else colors.surface.copy(alpha = 0.68f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .background(background, ReaderShapes.md)
            .border(1.dp, borderColor, ReaderShapes.md)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RestoreIconCircle(iconRes = restoreScopeIcon(scope.key))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = scope.title,
                style = restoreRowTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = scope.meta,
                style = restoreTinyMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RestoreTinySwitch(checked = selected)
    }
}

@Composable
private fun RestoreWarning(title: String, body: String) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.lg)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.lg)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RestoreIconCircle(iconRes = R.drawable.reader_ic_warning, tint = extra.accent, background = extra.accent.copy(alpha = 0.10f))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = restoreRowTitleStyle(), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = body, style = restoreBodyStyle(), color = extra.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RestoreProgressMeter(progress: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RestoreProgressBar(progress = progress, modifier = Modifier.weight(1f))
        Text(
            text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
            style = restoreProgressValueStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1
        )
    }
}

@Composable
private fun RestoreStageList(stages: List<RestoreStageItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stages.forEach { stage ->
            RestoreStageRow(stage = stage)
        }
    }
}

@Composable
private fun RestoreStageRow(stage: RestoreStageItem) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val borderColor = when {
        stage.active -> colors.primary.copy(alpha = 0.42f)
        stage.done -> extra.forest.copy(alpha = 0.38f)
        else -> extra.hairline.copy(alpha = 0.72f)
    }
    val background = if (stage.active) colors.primary.copy(alpha = 0.07f) else colors.surface.copy(alpha = 0.92f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 66.dp)
            .background(background, ReaderShapes.lg)
            .border(1.dp, borderColor, ReaderShapes.lg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            RestoreIconCircle(iconRes = restoreStageIcon(stage), size = 24)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stage.title, style = restoreRowTitleStyle(), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = stage.meta, style = restoreBodyStyle(), color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RestoreBadgeView(badge = stage.badge)
        }
        RestoreProgressBar(progress = stage.progress, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RestoreConflictList(
    conflicts: List<RestoreConflictItem>,
    choices: Map<String, RestoreConflictChoice>,
    onConflictChoice: (String, RestoreConflictChoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        conflicts.forEach { conflict ->
            RestoreConflictRow(
                conflict = conflict,
                choice = choices[conflict.id] ?: RestoreConflictChoice.Remote,
                onConflictChoice = onConflictChoice
            )
        }
    }
}

@Composable
private fun RestoreConflictRow(
    conflict: RestoreConflictItem,
    choice: RestoreConflictChoice,
    onConflictChoice: (String, RestoreConflictChoice) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.lg)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.lg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = conflict.title, style = restoreRowTitleStyle(), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = conflict.meta, style = restoreBodyStyle(), color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RestoreChoiceButton(
                label = conflict.localLabel,
                selected = choice == RestoreConflictChoice.Local,
                onClick = { onConflictChoice(conflict.id, RestoreConflictChoice.Local) },
                modifier = Modifier.weight(1f)
            )
            RestoreChoiceButton(
                label = conflict.remoteLabel,
                selected = choice == RestoreConflictChoice.Remote,
                onClick = { onConflictChoice(conflict.id, RestoreConflictChoice.Remote) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RestoreResultList(items: List<RestoreResultItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val icon = if (item.badge.tone == RestoreTone.Good) R.drawable.reader_ic_check else R.drawable.reader_ic_warning
            RestoreResultRow(item = item, iconRes = icon)
        }
    }
}

@Composable
private fun RestoreResultRow(item: RestoreResultItem, @DrawableRes iconRes: Int) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val borderColor = if (item.badge.tone == RestoreTone.Good) extra.forest.copy(alpha = 0.38f) else extra.hairline.copy(alpha = 0.72f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.lg)
            .border(1.dp, borderColor, ReaderShapes.lg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RestoreIconCircle(iconRes = iconRes, size = 24)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = item.title, style = restoreRowTitleStyle(), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = item.meta, style = restoreBodyStyle(), color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RestoreBadgeView(badge = item.badge)
    }
}

@Composable
private fun RestoreActionRow(
    actions: List<RestoreAction>,
    onNavigate: (String) -> Unit,
    onViewLog: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            RestoreActionButton(
                action = action,
                onClick = {
                    if (action.routeId != null) {
                        onNavigate(action.routeId)
                    } else {
                        onViewLog()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        repeat(2 - actions.size.coerceAtMost(2)) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun RestoreActionButton(action: RestoreAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = if (action.primary) colors.primary else colors.surface.copy(alpha = 0.86f)
    val foreground = if (action.primary) colors.onPrimary else extra.primaryDark
    val borderColor = if (action.primary) colors.primary else colors.primary.copy(alpha = 0.24f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .background(background, ReaderShapes.md)
            .border(1.dp, borderColor, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = action.label, style = restoreActionStyle(), color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RestoreChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = if (selected) colors.primary else colors.surface.copy(alpha = 0.86f)
    val foreground = if (selected) colors.onPrimary else extra.primaryDark
    val borderColor = if (selected) colors.primary else colors.primary.copy(alpha = 0.24f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .background(background, ReaderShapes.md)
            .border(1.dp, borderColor, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = restoreActionStyle(), color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RestoreBadgeView(badge: RestoreBadge) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val foreground = when (badge.tone) {
        RestoreTone.Good -> extra.forest
        RestoreTone.Warn -> extra.danger
        RestoreTone.Info -> extra.primaryDark
        RestoreTone.Muted -> extra.muted
    }
    val background = when (badge.tone) {
        RestoreTone.Good -> extra.forest.copy(alpha = 0.10f)
        RestoreTone.Warn -> extra.danger.copy(alpha = 0.10f)
        RestoreTone.Info -> colors.primary.copy(alpha = 0.10f)
        RestoreTone.Muted -> extra.metaBackground.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 22.dp)
            .background(background, ReaderShapes.pill)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = badge.label, style = restoreBadgeStyle(), color = foreground, maxLines = 1)
    }
}

@Composable
private fun RestoreProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(8.dp)
            .background(colors.primary.copy(alpha = 0.12f), ReaderShapes.pill)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .background(colors.primary, ReaderShapes.pill)
        )
    }
}

@Composable
private fun RestoreTinySwitch(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .background(if (checked) colors.primary else extra.hairline.copy(alpha = 0.68f), ReaderShapes.pill)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(colors.surface, ReaderShapes.pill)
        )
    }
}

@Composable
private fun RestoreIconCircle(
    @DrawableRes iconRes: Int,
    size: Int = 28,
    tint: Color = readerExtraColors().primaryDark,
    background: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(background, ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size - 10).coerceAtLeast(14).dp)
        )
    }
}

@DrawableRes
private fun restoreScopeIcon(key: String): Int = when (key) {
    "bookshelf" -> R.drawable.reader_ic_bookshelf
    "progress" -> R.drawable.reader_ic_clock
    "settings" -> R.drawable.reader_ic_settings
    "sources" -> R.drawable.reader_ic_source_stack
    else -> R.drawable.reader_ic_sync
}

@DrawableRes
private fun restoreStageIcon(stage: RestoreStageItem): Int = when {
    stage.done -> R.drawable.reader_ic_check
    stage.active -> R.drawable.reader_ic_refresh
    else -> R.drawable.reader_ic_clock
}

private fun restoreCardTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_TITLE.value,
    lineHeight = 19.sp,
    fontWeight = FontWeight(900)
)

private fun restoreRowTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(900)
)

private fun restoreBodyStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 17.sp,
    fontWeight = FontWeight(500)
)

private fun restoreTinyMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun restoreMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 15.sp,
    fontWeight = FontWeight(700)
)

private fun restoreCaptionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(800)
)

private fun restoreValueStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 15.sp,
    fontWeight = FontWeight(900)
)

private fun restoreProgressValueStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(900)
)

private fun restoreActionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 15.sp,
    fontWeight = FontWeight(900)
)

private fun restoreBadgeStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(900)
)
