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

@Composable
fun GeneralSettingsScreen(
    state: GeneralSettingsUiState = GeneralSettingsMapper.fromFixture(),
    onBack: () -> Unit = {},
    onThemeChange: (String) -> Unit = {},
    onSelectOpen: (GeneralSettingsRowUiModel) -> Unit = {},
    onSelectOption: (GeneralSettingsRowUiModel, String) -> Unit = { _, _ -> },
    onSwitchChange: (GeneralSettingsRowUiModel, Boolean) -> Unit = { _, _ -> },
    onRestoreOpen: () -> Unit = {},
    onRestoreConfirm: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenSystemSettings: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "SettingsShell 通用设置" }
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
                    state.groups.forEach { group ->
                        GeneralSettingsGroup(
                            group = group,
                            onThemeChange = onThemeChange,
                            onSelectOpen = onSelectOpen,
                            onSwitchChange = onSwitchChange
                        )
                    }
                    GeneralSettingsRestoreRow(state.restore, onRestoreOpen)
                    if (state.displayState == GeneralSettingsDisplayState.Loading) {
                        GeneralSettingsFeedback(state.loading, onPrimary = onRetry, loading = true)
                    }
                    if (state.displayState == GeneralSettingsDisplayState.Permission) {
                        GeneralSettingsFeedback(state.permission, onPrimary = onOpenSystemSettings)
                    }
                }
            }

            GeneralSettingsToastHost(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .semantics { contentDescription = "toastHost" }
            )
            if (state.displayState == GeneralSettingsDisplayState.OptionSheet) {
                GeneralSettingsOptionSheet(
                    row = state.languageRow,
                    onSelectOption = onSelectOption,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .semantics { contentDescription = "sheetHost" }
                )
            }
            if (state.displayState == GeneralSettingsDisplayState.Error) {
                GeneralSettingsConfirmDialog(
                    restore = state.restore,
                    feedback = state.error,
                    onCancel = onBack,
                    onConfirm = onRestoreConfirm,
                    onRetry = onRetry,
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
private fun GeneralSettingsGroup(
    group: GeneralSettingsGroupUiModel,
    onThemeChange: (String) -> Unit,
    onSelectOpen: (GeneralSettingsRowUiModel) -> Unit,
    onSwitchChange: (GeneralSettingsRowUiModel, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "设置分组，${group.title}" },
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text(group.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
        ) {
            group.rows.forEach { row ->
                GeneralSettingsRow(
                    row = row,
                    onThemeChange = onThemeChange,
                    onSelectOpen = onSelectOpen,
                    onSwitchChange = onSwitchChange
                )
            }
        }
    }
}

@Composable
private fun GeneralSettingsRow(
    row: GeneralSettingsRowUiModel,
    onThemeChange: (String) -> Unit,
    onSelectOpen: (GeneralSettingsRowUiModel) -> Unit,
    onSwitchChange: (GeneralSettingsRowUiModel, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(enabled = row.type == GeneralSettingsRowType.Select, role = Role.Button) { onSelectOpen(row) }
            .semantics { contentDescription = "${row.title}，${row.value.ifBlank { row.meta }}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toGeneralSettingsIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            val secondary = row.meta.ifBlank { row.value }
            if (secondary.isNotBlank()) {
                Text(secondary, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
        }
        when (row.type) {
            GeneralSettingsRowType.Segment -> Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
                row.options.forEach { option ->
                    ReaderChip(
                        text = option,
                        selected = option == row.value,
                        onClick = { onThemeChange(option) }
                    )
                }
            }
            GeneralSettingsRowType.Select -> Row(
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
            GeneralSettingsRowType.Switch -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                row.permission?.let { permission ->
                    ReaderChip(text = permission, selected = permission == "已开启")
                }
                ReaderSwitch(
                    checked = row.enabled,
                    onCheckedChange = { onSwitchChange(row, it) }
                )
            }
        }
    }
}

@Composable
private fun GeneralSettingsRestoreRow(
    restore: GeneralSettingsRestoreUiModel,
    onRestoreOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.primary, ReaderTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onRestoreOpen)
            .semantics { contentDescription = restore.title }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Refresh.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(restore.title, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookTitle)
            Text(restore.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderChip(text = restore.intentLabel)
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.bodyText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun GeneralSettingsOptionSheet(
    row: GeneralSettingsRowUiModel,
    onSelectOption: (GeneralSettingsRowUiModel, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.bottomSheet)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.bottomSheet)
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 38.dp, height = 4.dp)
                .clip(ReaderTheme.shapes.chip)
                .background(ReaderTheme.colors.mutedTrack)
        )
        Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        row.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ReaderTheme.shapes.small)
                    .background(if (option == row.value) ReaderTheme.colors.floatingControlBgAlt else ReaderTheme.colors.metaBg)
                    .clickable(role = Role.Button) { onSelectOption(row, option) }
                    .semantics { contentDescription = "选项，$option" }
                    .padding(ReaderTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(option, modifier = Modifier.weight(1f), color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                if (option == row.value) {
                    ReaderChip(text = "当前", selected = true)
                }
            }
        }
        ReaderSecondaryButton(text = "取消", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GeneralSettingsToastHost(
    state: GeneralSettingsUiState,
    modifier: Modifier = Modifier
) {
    val label = when (state.displayState) {
        GeneralSettingsDisplayState.Default -> state.toast.success
        GeneralSettingsDisplayState.Error -> state.toast.error
        GeneralSettingsDisplayState.Permission -> state.toast.permission
        else -> null
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
private fun GeneralSettingsConfirmDialog(
    restore: GeneralSettingsRestoreUiModel,
    feedback: GeneralSettingsFeedbackUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.card)
            .padding(ReaderTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(restore.confirmTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Text(restore.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Text(feedback.message, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookMeta)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = restore.cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
            ReaderSecondaryButton(text = feedback.primaryAction ?: "重试", onClick = onRetry, modifier = Modifier.weight(1f))
            ReaderPrimaryButton(text = restore.confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GeneralSettingsFeedback(
    feedback: GeneralSettingsFeedbackUiModel,
    onPrimary: () -> Unit,
    loading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (loading) ReaderIconToken.Refresh.asImageVector() else ReaderIconToken.Permission.asImageVector(),
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

private fun String.toGeneralSettingsIcon(): ReaderIconToken = when (this) {
    "palette" -> ReaderIconToken.Appearance
    "globe" -> ReaderIconToken.Discover
    "home" -> ReaderIconToken.Bookshelf
    "refresh" -> ReaderIconToken.Refresh
    "top" -> ReaderIconToken.CurrentLocation
    "motion" -> ReaderIconToken.AutoScroll
    "bug" -> ReaderIconToken.Warning
    "play" -> ReaderIconToken.AutoScroll
    else -> ReaderIconToken.Settings
}
