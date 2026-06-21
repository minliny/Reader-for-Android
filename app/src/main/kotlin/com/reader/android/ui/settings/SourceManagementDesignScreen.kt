package com.reader.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSearchBox
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSwitch
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

private val SourceGoodColor = Color(0xFF1F7A4D)
private val SourceWarnColor = Color(0xFFB54708)

@Composable
fun SourceManagementDesignScreen(
    state: SourceManagementDesignUiState = SourceManagementDesignMapper.fromFixture(),
    query: String = "",
    onBack: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onChipClick: (SourceManagementChipUiModel) -> Unit = {},
    onActionRow: (SourceManagementActionRowUiModel) -> Unit = {},
    onSourceClick: (SourceRowUiModel) -> Unit = {},
    onSourceEnabledChange: (SourceRowUiModel, Boolean) -> Unit = { _, _ -> },
    onActionSwitchChange: (SourceManagementActionRowUiModel, Boolean) -> Unit = { _, _ -> },
    onDetect: () -> Unit = {},
    onAdd: () -> Unit = {},
    onSaveSource: () -> Unit = {},
    onConfirmDisable: () -> Unit = {},
    onRetry: () -> Unit = {},
    onGrantPermission: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "SettingsShell 书源管理" }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReaderAppTopBar(
                    title = state.topBar.title,
                    navigationContentDescription = state.topBar.backLabel,
                    onNavigateBack = onBack
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(ReaderTheme.spacing.screenPadding)
                        .semantics { contentDescription = "settingsContent" },
                    verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
                ) {
                    SourceMetricStrip(metrics = state.metrics)
                    ReaderSearchBox(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = state.searchBox.placeholder,
                        contentDescription = "搜索框"
                    )
                    SourceChipRow(chips = state.filters, onChipClick = onChipClick, contentDescription = "筛选")
                    SourceChipRow(chips = state.groups, onChipClick = onChipClick, contentDescription = "分组")
                    state.sections.forEach { section ->
                        SourceManagementSection(
                            section = section,
                            detectBlocked = state.displayState in listOf(
                                SourceManagementDesignDisplayState.Offline,
                                SourceManagementDesignDisplayState.Permission
                            ),
                            onActionRow = onActionRow,
                            onActionSwitchChange = onActionSwitchChange,
                            onDetect = onDetect
                        )
                    }
                    SourceListPanel(
                        state = state,
                        onSourceClick = onSourceClick,
                        onSourceEnabledChange = onSourceEnabledChange,
                        onAdd = onAdd,
                        onRetry = onRetry
                    )
                    when (state.displayState) {
                        SourceManagementDesignDisplayState.Edit -> SourceEditForm(
                            form = state.form,
                            onSaveSource = onSaveSource
                        )
                        SourceManagementDesignDisplayState.Log -> LogPanel(log = state.log)
                        SourceManagementDesignDisplayState.Error -> SourceManagementFeedback(
                            feedback = state.error,
                            onPrimary = onRetry
                        )
                        SourceManagementDesignDisplayState.Offline -> SourceManagementFeedback(
                            feedback = state.offline,
                            offline = true,
                            onPrimary = onRetry
                        )
                        SourceManagementDesignDisplayState.Permission -> SourceManagementFeedback(
                            feedback = state.permission,
                            permission = true,
                            onPrimary = onGrantPermission
                        )
                        SourceManagementDesignDisplayState.Default,
                        SourceManagementDesignDisplayState.Confirm,
                        SourceManagementDesignDisplayState.Loading,
                        SourceManagementDesignDisplayState.Empty -> Unit
                    }
                }
            }
            SourceManagementToastHost(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .semantics { contentDescription = "toastHost" }
            )
            SourceFloatingAddButton(
                fab = state.fab,
                onAdd = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ReaderTheme.spacing.screenPadding)
            )
            if (state.displayState == SourceManagementDesignDisplayState.Confirm) {
                SourceDisableConfirmDialog(
                    confirm = state.confirm,
                    onCancel = onBack,
                    onConfirm = onConfirmDisable,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(ReaderTheme.spacing.screenPadding)
                        .semantics { contentDescription = "dialogHost" }
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = "settingsStateHost" }
            )
        }
    }
}

@Composable
private fun SourceMetricStrip(metrics: List<SourceManagementMetricUiModel>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                rowMetrics.forEach { metric ->
                    SourceMetricItem(metric = metric, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SourceMetricItem(
    metric: SourceManagementMetricUiModel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics { contentDescription = "${metric.value}${metric.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Icon(
            imageVector = metric.icon.toSourceManagementIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(metric.value, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(metric.label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
    }
}

@Composable
private fun SourceChipRow(
    chips: List<SourceManagementChipUiModel>,
    onChipClick: (SourceManagementChipUiModel) -> Unit,
    contentDescription: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        chips.forEach { chip ->
            ReaderChip(
                text = chip.label,
                selected = chip.active,
                onClick = { onChipClick(chip) }
            )
        }
    }
}

@Composable
private fun SourceManagementSection(
    section: SourceManagementSectionUiModel,
    detectBlocked: Boolean,
    onActionRow: (SourceManagementActionRowUiModel) -> Unit,
    onActionSwitchChange: (SourceManagementActionRowUiModel, Boolean) -> Unit,
    onDetect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "设置分组，${section.title}" },
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text(section.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
        ) {
            section.rows.forEach { row ->
                if (row.title == "检测") {
                    DetectButton(row = row, enabled = !detectBlocked, onDetect = onDetect)
                } else {
                    SourceActionRow(
                        row = row,
                        onActionRow = onActionRow,
                        onActionSwitchChange = onActionSwitchChange
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectButton(
    row: SourceManagementActionRowUiModel,
    enabled: Boolean,
    onDetect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onDetect)
            .semantics { contentDescription = "${row.title}，${row.meta}，${row.actionLabel}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Refresh.asImageVector(),
            contentDescription = null,
            tint = if (enabled) ReaderTheme.colors.primary else ReaderTheme.colors.bodyText,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderSecondaryButton(text = row.actionLabel.ifBlank { "检测" }, onClick = onDetect, enabled = enabled)
    }
}

@Composable
private fun SourceActionRow(
    row: SourceManagementActionRowUiModel,
    onActionRow: (SourceManagementActionRowUiModel) -> Unit,
    onActionSwitchChange: (SourceManagementActionRowUiModel, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = row.type == SourceManagementActionType.Link, role = Role.Button) { onActionRow(row) }
            .semantics { contentDescription = listOf(row.title, row.meta, row.actionLabel).filter { it.isNotBlank() }.joinToString("，") }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toSourceManagementIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        when (row.type) {
            SourceManagementActionType.Switch -> ReaderSwitch(
                checked = row.enabled,
                onCheckedChange = { onActionSwitchChange(row, it) }
            )
            SourceManagementActionType.Link -> {
                Icon(
                    imageVector = ReaderIconToken.Chevron.asImageVector(),
                    contentDescription = null,
                    tint = ReaderTheme.colors.bodyText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceListPanel(
    state: SourceManagementDesignUiState,
    onSourceClick: (SourceRowUiModel) -> Unit,
    onSourceEnabledChange: (SourceRowUiModel, Boolean) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit
) {
    when (state.displayState) {
        SourceManagementDesignDisplayState.Loading -> SourceManagementFeedback(
            feedback = state.loading,
            loading = true,
            onPrimary = onRetry
        )
        SourceManagementDesignDisplayState.Empty -> SourceManagementEmptyState(
            empty = state.empty,
            onAdd = onAdd
        )
        else -> SourceDetailPanel(
            title = state.sourceListTitle,
            sources = state.sources,
            onSourceClick = onSourceClick,
            onSourceEnabledChange = onSourceEnabledChange
        )
    }
}

@Composable
private fun SourceDetailPanel(
    title: String,
    sources: List<SourceRowUiModel>,
    onSourceClick: (SourceRowUiModel) -> Unit,
    onSourceEnabledChange: (SourceRowUiModel, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "设置分组，$title" },
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
        ) {
            sources.forEach { source ->
                SourceRow(
                    source = source,
                    onClick = { onSourceClick(source) },
                    onEnabledChange = { onSourceEnabledChange(source, it) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceRowUiModel,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val tone = when (source.tone) {
        "good" -> SourceGoodColor
        "warn" -> SourceWarnColor
        else -> ReaderTheme.colors.bodyText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${source.title}，${source.meta}，${source.status}，${if (source.enabled) "启用" else "禁用"}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.SourceSwitch.asImageVector(),
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(source.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ReaderChip(text = source.status, selected = source.tone == "good")
        ReaderSwitch(checked = source.enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun SourceEditForm(
    form: SourceEditFormUiModel,
    onSaveSource: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = form.title }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(form.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        form.fields.forEach { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ReaderTheme.shapes.small)
                    .background(ReaderTheme.colors.paperBg)
                    .padding(ReaderTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(field.label, modifier = Modifier.weight(1f), color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                Text(field.value, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            }
        }
        ReaderPrimaryButton(text = form.saveLabel, onClick = onSaveSource, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LogPanel(log: SourceLogPanelUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = log.title }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(log.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        log.items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ReaderTheme.shapes.small)
                    .background(ReaderTheme.colors.paperBg)
                    .padding(ReaderTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                ReaderChip(text = item.level, selected = item.level == "WARN")
                Text(item.copy, modifier = Modifier.weight(1f), color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
            }
        }
    }
}

@Composable
private fun SourceManagementEmptyState(
    empty: SourceManagementEmptyUiModel,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = empty.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ReaderIconToken.FolderOff.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(empty.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(empty.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        ReaderPrimaryButton(text = empty.primaryAction, onClick = onAdd)
    }
}

@Composable
private fun SourceManagementToastHost(
    state: SourceManagementDesignUiState,
    modifier: Modifier = Modifier
) {
    val label = when (state.displayState) {
        SourceManagementDesignDisplayState.Default,
        SourceManagementDesignDisplayState.Edit -> state.toast.success
        SourceManagementDesignDisplayState.Error -> state.toast.error
        SourceManagementDesignDisplayState.Offline -> state.toast.offline
        SourceManagementDesignDisplayState.Permission -> state.toast.permission
        SourceManagementDesignDisplayState.Log,
        SourceManagementDesignDisplayState.Confirm,
        SourceManagementDesignDisplayState.Loading,
        SourceManagementDesignDisplayState.Empty -> null
    } ?: return
    Box(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.floatingControlBgAlt)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.chip)
            .padding(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm)
    ) {
        Text(label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun SourceFloatingAddButton(
    fab: SourceManagementFabUiModel,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.primary)
            .clickable(role = Role.Button, onClick = onAdd)
            .semantics { contentDescription = fab.label }
            .padding(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Icon(
            imageVector = fab.icon.toSourceManagementIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.paperBg,
            modifier = Modifier.size(18.dp)
        )
        Text(fab.label, color = ReaderTheme.colors.paperBg, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun SourceDisableConfirmDialog(
    confirm: SourceManagementConfirmUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, SourceWarnColor.copy(alpha = 0.45f), ReaderTheme.shapes.card)
            .padding(ReaderTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(confirm.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Text(confirm.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = confirm.cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
            ReaderPrimaryButton(text = confirm.confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SourceManagementFeedback(
    feedback: SourceManagementFeedbackUiModel,
    onPrimary: () -> Unit,
    loading: Boolean = false,
    offline: Boolean = false,
    permission: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when {
                loading -> ReaderIconToken.Refresh.asImageVector()
                offline -> ReaderIconToken.Offline.asImageVector()
                permission -> ReaderIconToken.Permission.asImageVector()
                else -> ReaderIconToken.Warning.asImageVector()
            },
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        feedback.primaryAction?.let { label ->
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
            ReaderPrimaryButton(text = label, onClick = onPrimary)
        }
    }
}

private fun String.toSourceManagementIcon(): ReaderIconToken = when (this) {
    "source" -> ReaderIconToken.SourceSwitch
    "check" -> ReaderIconToken.Check
    "warning" -> ReaderIconToken.Warning
    "clock" -> ReaderIconToken.Clock
    "refresh" -> ReaderIconToken.Refresh
    "info" -> ReaderIconToken.Info
    "edit" -> ReaderIconToken.Edit
    "log" -> ReaderIconToken.Bug
    "add" -> ReaderIconToken.Add
    else -> ReaderIconToken.SourceSwitch
}
