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
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSwitch
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

private val SyncGoodColor = Color(0xFF1F7A4D)
private val SyncWarnColor = Color(0xFFB54708)

@Composable
fun SyncBackupScreen(
    state: SyncBackupUiState = SyncBackupMapper.fromFixture(),
    onBack: () -> Unit = {},
    onRowAction: (SyncBackupRowUiModel) -> Unit = {},
    onSwitchChange: (SyncBackupRowUiModel, Boolean) -> Unit = { _, _ -> },
    onBackupNow: () -> Unit = {},
    onExportData: () -> Unit = {},
    onRestoreOpen: () -> Unit = {},
    onRestoreConfirm: () -> Unit = {},
    onRetry: () -> Unit = {},
    onGrantPermission: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "SettingsShell 同步与备份" }
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
                    state.sections.forEach { section ->
                        SyncBackupSection(
                            section = section,
                            offline = state.displayState == SyncBackupDisplayState.Offline,
                            permissionBlocked = state.displayState == SyncBackupDisplayState.Permission,
                            onRowAction = onRowAction,
                            onSwitchChange = onSwitchChange,
                            onBackupNow = onBackupNow,
                            onExportData = onExportData,
                            onRestoreOpen = onRestoreOpen
                        )
                    }
                    when (state.displayState) {
                        SyncBackupDisplayState.Loading -> SyncBackupFeedback(
                            feedback = state.loading,
                            loading = true,
                            onPrimary = onRetry
                        )
                        SyncBackupDisplayState.Empty -> SyncBackupEmptyState(
                            empty = state.empty,
                            onBackupNow = onBackupNow
                        )
                        else -> BackupRecordSection(records = state.records, onOpenRecord = onRowAction)
                    }
                    when (state.displayState) {
                        SyncBackupDisplayState.Error -> SyncBackupFeedback(
                            feedback = state.error,
                            onPrimary = onRetry
                        )
                        SyncBackupDisplayState.Offline -> SyncBackupFeedback(
                            feedback = state.offline,
                            offline = true,
                            onPrimary = onRetry
                        )
                        SyncBackupDisplayState.Permission -> SyncBackupFeedback(
                            feedback = state.permission,
                            permission = true,
                            onPrimary = onGrantPermission
                        )
                        SyncBackupDisplayState.Default,
                        SyncBackupDisplayState.Confirm,
                        SyncBackupDisplayState.Loading,
                        SyncBackupDisplayState.Empty -> Unit
                    }
                }
            }

            SyncBackupToastHost(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .semantics { contentDescription = "toastHost" }
            )
            if (state.displayState == SyncBackupDisplayState.Confirm) {
                RestoreConfirmDialog(
                    confirm = state.confirm,
                    onCancel = onBack,
                    onConfirm = onRestoreConfirm,
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
private fun SyncBackupSection(
    section: SyncBackupSectionUiModel,
    offline: Boolean,
    permissionBlocked: Boolean,
    onRowAction: (SyncBackupRowUiModel) -> Unit,
    onSwitchChange: (SyncBackupRowUiModel, Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onExportData: () -> Unit,
    onRestoreOpen: () -> Unit
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
                when (row.title) {
                    "立即备份" -> BackupActionRow(
                        row = row,
                        action = "backup",
                        enabled = true,
                        onClick = onBackupNow
                    )
                    "导出数据" -> BackupActionRow(
                        row = row,
                        action = "export",
                        enabled = !permissionBlocked,
                        onClick = onExportData
                    )
                    "恢复备份" -> BackupActionRow(
                        row = row,
                        action = "restore",
                        enabled = !permissionBlocked,
                        onClick = onRestoreOpen
                    )
                    "同步冲突处理" -> ConflictNotice(
                        row = row,
                        onClick = { onRowAction(row) }
                    )
                    else -> SyncBackupSettingRow(
                        row = row,
                        networkBlocked = offline && row.title.contains("同步"),
                        onRowAction = onRowAction,
                        onSwitchChange = onSwitchChange
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupActionRow(
    row: SyncBackupRowUiModel,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = listOf(row.title, row.meta, row.value, action).filter { it.isNotBlank() }.joinToString("，") }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toSyncBackupIcon().asImageVector(),
            contentDescription = null,
            tint = if (enabled) ReaderTheme.colors.primary else ReaderTheme.colors.bodyText,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        if (row.value.isNotBlank()) {
            ReaderChip(text = row.value)
        }
        ReaderChip(
            text = when (action) {
                "backup" -> "立即"
                "export" -> "导出"
                else -> "恢复"
            },
            selected = enabled
        )
    }
}

@Composable
private fun SyncBackupSettingRow(
    row: SyncBackupRowUiModel,
    networkBlocked: Boolean,
    onRowAction: (SyncBackupRowUiModel) -> Unit,
    onSwitchChange: (SyncBackupRowUiModel, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = row.type != SyncBackupRowType.Switch && !networkBlocked, role = Role.Button) { onRowAction(row) }
            .semantics { contentDescription = listOf(row.title, row.meta, row.value, row.actionLabel, row.status).filter { it.isNotBlank() }.joinToString("，") }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toSyncBackupIcon().asImageVector(),
            contentDescription = null,
            tint = if (networkBlocked) ReaderTheme.colors.bodyText else ReaderTheme.colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        when (row.type) {
            SyncBackupRowType.Switch -> ReaderSwitch(
                checked = row.enabled && !networkBlocked,
                onCheckedChange = { onSwitchChange(row, it) }
            )
            SyncBackupRowType.Select -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                ReaderChip(text = row.value, selected = true)
                Icon(
                    imageVector = ReaderIconToken.Chevron.asImageVector(),
                    contentDescription = null,
                    tint = ReaderTheme.colors.bodyText,
                    modifier = Modifier.size(18.dp)
                )
            }
            SyncBackupRowType.Link -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                val label = row.actionLabel.ifBlank { row.status.ifBlank { row.value } }
                if (label.isNotBlank()) {
                    ReaderChip(text = label, selected = row.statusTone != "warn")
                }
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
private fun ConflictNotice(
    row: SyncBackupRowUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "同步冲突，${row.meta}，${row.value}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Warning.asImageVector(),
            contentDescription = null,
            tint = SyncWarnColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderChip(text = row.value, selected = false)
    }
}

@Composable
private fun BackupRecordSection(
    records: List<BackupRecordUiModel>,
    onOpenRecord: (SyncBackupRowUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "设置分组，备份记录" },
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text("备份记录", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
        ) {
            records.forEach { record ->
                BackupRecordRow(
                    record = record,
                    onClick = {
                        onOpenRecord(
                            SyncBackupRowUiModel(
                                type = SyncBackupRowType.Link,
                                icon = record.icon,
                                title = record.title,
                                meta = record.meta,
                                status = record.status
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BackupRecordRow(
    record: BackupRecordUiModel,
    onClick: () -> Unit
) {
    val tone = when (record.tone) {
        "good" -> SyncGoodColor
        "info" -> ReaderTheme.colors.primary
        else -> ReaderTheme.colors.bodyText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${record.title}，${record.meta}，${record.status}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = record.icon.toSyncBackupIcon().asImageVector(),
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(record.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(record.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderChip(text = record.status, selected = record.tone == "good")
    }
}

@Composable
private fun SyncBackupEmptyState(
    empty: SyncBackupEmptyUiModel,
    onBackupNow: () -> Unit
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
        ReaderPrimaryButton(text = empty.primaryAction, onClick = onBackupNow)
    }
}

@Composable
private fun SyncBackupToastHost(
    state: SyncBackupUiState,
    modifier: Modifier = Modifier
) {
    val label = when (state.displayState) {
        SyncBackupDisplayState.Default -> state.toast.success
        SyncBackupDisplayState.Error -> state.toast.error
        SyncBackupDisplayState.Offline -> state.toast.offline
        SyncBackupDisplayState.Permission -> state.toast.permission
        SyncBackupDisplayState.Confirm,
        SyncBackupDisplayState.Loading,
        SyncBackupDisplayState.Empty -> null
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
private fun RestoreConfirmDialog(
    confirm: RestoreConfirmUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, SyncWarnColor.copy(alpha = 0.45f), ReaderTheme.shapes.card)
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
private fun SyncBackupFeedback(
    feedback: SyncBackupFeedbackUiModel,
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

private fun String.toSyncBackupIcon(): ReaderIconToken = when (this) {
    "clock" -> ReaderIconToken.Clock
    "folder" -> ReaderIconToken.Folder
    "upload" -> ReaderIconToken.Upload
    "download" -> ReaderIconToken.Download
    "source" -> ReaderIconToken.SourceSwitch
    "refresh" -> ReaderIconToken.Refresh
    "warning" -> ReaderIconToken.Warning
    "file" -> ReaderIconToken.FileOpen
    else -> ReaderIconToken.Settings
}
