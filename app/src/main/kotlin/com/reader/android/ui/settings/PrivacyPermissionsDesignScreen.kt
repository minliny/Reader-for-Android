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
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSwitch
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

private val PrivacyDangerColor = Color(0xFFD92D20)
private val PrivacyGoodColor = Color(0xFF1F7A4D)
private val PrivacyWarnColor = Color(0xFFD97706)
private val PrivacySystemColor = Color(0xFF536878)

@Composable
fun PrivacyPermissionsScreen(
    state: PrivacyPermissionsUiState = PrivacyPermissionsMapper.fromFixture(),
    onBack: () -> Unit = {},
    onOpenSystemSettings: (PrivacyPermissionsRowUiModel) -> Unit = {},
    onSwitchChange: (PrivacyPermissionsRowUiModel, Boolean) -> Unit = { _, _ -> },
    onOpenLink: (PrivacyPermissionsRowUiModel) -> Unit = {},
    onClearPrivacyOpen: () -> Unit = {},
    onClearPrivacyConfirm: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "SettingsShell 隐私与权限" }
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
                        PrivacyPermissionsSection(
                            section = section,
                            onOpenSystemSettings = onOpenSystemSettings,
                            onSwitchChange = onSwitchChange,
                            onOpenLink = onOpenLink
                        )
                    }
                    PrivacyDangerActionRow(state.danger, onClearPrivacyOpen)
                    if (state.displayState == PrivacyPermissionsDisplayState.Loading) {
                        PrivacyFeedback(state.loading, onPrimary = onRetry, loading = true)
                    }
                    if (state.displayState == PrivacyPermissionsDisplayState.Error) {
                        PrivacyFeedback(state.error, onPrimary = onRetry)
                    }
                    if (state.displayState == PrivacyPermissionsDisplayState.Permission) {
                        PrivacyFeedback(
                            feedback = state.permission,
                            onPrimary = {
                                val row = state.sections.flatMap { it.rows }.first { it.actionLabel == "去设置" }
                                onOpenSystemSettings(row)
                            }
                        )
                    }
                }
            }

            PrivacyToastHost(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .semantics { contentDescription = "toastHost" }
            )
            if (state.displayState == PrivacyPermissionsDisplayState.Confirm) {
                PrivacyConfirmDialog(
                    danger = state.danger,
                    onCancel = onBack,
                    onConfirm = onClearPrivacyConfirm,
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
private fun PrivacyPermissionsSection(
    section: PrivacyPermissionsSectionUiModel,
    onOpenSystemSettings: (PrivacyPermissionsRowUiModel) -> Unit,
    onSwitchChange: (PrivacyPermissionsRowUiModel, Boolean) -> Unit,
    onOpenLink: (PrivacyPermissionsRowUiModel) -> Unit
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
                PrivacyPermissionsRow(
                    row = row,
                    onOpenSystemSettings = onOpenSystemSettings,
                    onSwitchChange = onSwitchChange,
                    onOpenLink = onOpenLink
                )
            }
        }
    }
}

@Composable
private fun PrivacyPermissionsRow(
    row: PrivacyPermissionsRowUiModel,
    onOpenSystemSettings: (PrivacyPermissionsRowUiModel) -> Unit,
    onSwitchChange: (PrivacyPermissionsRowUiModel, Boolean) -> Unit,
    onOpenLink: (PrivacyPermissionsRowUiModel) -> Unit
) {
    val contentLabel = listOf(row.title, row.meta, row.status, row.value, row.actionLabel)
        .filter { it.isNotBlank() }
        .joinToString("，")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = row.type == PrivacyPermissionsRowType.Link, role = Role.Button) {
                if (row.actionLabel == "去设置") onOpenSystemSettings(row) else onOpenLink(row)
            }
            .semantics { contentDescription = contentLabel }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toPrivacyIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        when (row.type) {
            PrivacyPermissionsRowType.Link -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                if (row.status.isNotBlank()) {
                    PrivacyStatusBadge(label = row.status, tone = row.statusTone)
                }
                if (row.value.isNotBlank()) {
                    Text(row.value, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
                if (row.actionLabel.isNotBlank()) {
                    PrivacyOpenSystemSettingsButton(
                        label = row.actionLabel,
                        onClick = { onOpenSystemSettings(row) }
                    )
                }
                Icon(
                    imageVector = ReaderIconToken.Chevron.asImageVector(),
                    contentDescription = null,
                    tint = ReaderTheme.colors.bodyText,
                    modifier = Modifier.size(18.dp)
                )
            }
            PrivacyPermissionsRowType.Switch -> ReaderSwitch(
                checked = row.enabled,
                onCheckedChange = { onSwitchChange(row, it) }
            )
        }
    }
}

@Composable
private fun PrivacyStatusBadge(
    label: String,
    tone: String,
    modifier: Modifier = Modifier
) {
    val color = when (tone) {
        "good" -> PrivacyGoodColor
        "warn" -> PrivacyWarnColor
        else -> PrivacySystemColor
    }
    Box(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.38f), ReaderTheme.shapes.chip)
            .semantics { contentDescription = "权限状态，$label" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun PrivacyOpenSystemSettingsButton(
    label: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(ReaderTheme.shapes.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = ReaderTheme.spacing.xs, vertical = ReaderTheme.spacing.xs),
        color = ReaderTheme.colors.primary,
        style = ReaderTheme.typography.readerControlLabel
    )
}

@Composable
private fun PrivacyDangerActionRow(
    danger: PrivacyPermissionsDangerUiModel,
    onClearPrivacyOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, PrivacyDangerColor.copy(alpha = 0.35f), ReaderTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClearPrivacyOpen)
            .semantics { contentDescription = danger.title }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Trash.asImageVector(),
            contentDescription = null,
            tint = PrivacyDangerColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(danger.title, color = PrivacyDangerColor, style = ReaderTheme.typography.bookTitle)
            Text(danger.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = PrivacyDangerColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PrivacyToastHost(
    state: PrivacyPermissionsUiState,
    modifier: Modifier = Modifier
) {
    val label = when (state.displayState) {
        PrivacyPermissionsDisplayState.Default -> state.toast.success
        PrivacyPermissionsDisplayState.Error -> state.toast.error
        PrivacyPermissionsDisplayState.Permission -> state.toast.permission
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
private fun PrivacyConfirmDialog(
    danger: PrivacyPermissionsDangerUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, PrivacyDangerColor.copy(alpha = 0.45f), ReaderTheme.shapes.card)
            .padding(ReaderTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(danger.confirmTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Text(danger.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = danger.cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
            PrivacyDangerButton(text = danger.confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PrivacyDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(PrivacyDangerColor)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .padding(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun PrivacyFeedback(
    feedback: PrivacyPermissionsFeedbackUiModel,
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

private fun String.toPrivacyIcon(): ReaderIconToken = when (this) {
    "folder" -> ReaderIconToken.Folder
    "bell" -> ReaderIconToken.Bell
    "battery" -> ReaderIconToken.Battery
    "eyeOff" -> ReaderIconToken.EyeOff
    "clock" -> ReaderIconToken.Clock
    "bug" -> ReaderIconToken.Bug
    "info" -> ReaderIconToken.Info
    "file" -> ReaderIconToken.FileOpen
    "shield" -> ReaderIconToken.Shield
    "trash" -> ReaderIconToken.Trash
    else -> ReaderIconToken.Permission
}
