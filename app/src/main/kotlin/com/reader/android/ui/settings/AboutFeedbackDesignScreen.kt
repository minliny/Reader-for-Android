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
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

private val AboutSuccessColor = Color(0xFF1F7A4D)

@Composable
fun AboutFeedbackScreen(
    state: AboutFeedbackUiState = AboutFeedbackMapper.fromFixture(),
    onBack: () -> Unit = {},
    onOpenRow: (AboutFeedbackRowUiModel) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onSubmitFeedback: () -> Unit = {},
    onExportLogConfirm: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "SettingsShell 关于与反馈" }
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
                    VersionCard(
                        version = state.versionMetric,
                        status = state.updateMetric,
                        loading = state.displayState == AboutFeedbackDisplayState.Loading
                    )
                    state.sections.forEach { section ->
                        AboutFeedbackSection(
                            section = section,
                            checkUpdateEnabled = state.displayState !in listOf(
                                AboutFeedbackDisplayState.Loading,
                                AboutFeedbackDisplayState.Offline
                            ),
                            feedbackEnabled = state.displayState != AboutFeedbackDisplayState.Offline,
                            onOpenRow = onOpenRow,
                            onCheckUpdate = onCheckUpdate,
                            onSubmitFeedback = onSubmitFeedback
                        )
                    }
                    Text(
                        text = state.footer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = state.footer },
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.bookMeta
                    )
                    when (state.displayState) {
                        AboutFeedbackDisplayState.Loading -> AboutFeedbackFeedback(
                            feedback = state.loading,
                            onPrimary = onRetry,
                            loading = true
                        )
                        AboutFeedbackDisplayState.Error -> AboutFeedbackFeedback(
                            feedback = state.error,
                            onPrimary = onRetry
                        )
                        AboutFeedbackDisplayState.Offline -> AboutFeedbackFeedback(
                            feedback = state.offline,
                            onPrimary = onRetry,
                            offline = true
                        )
                        AboutFeedbackDisplayState.Default,
                        AboutFeedbackDisplayState.Confirm -> Unit
                    }
                }
            }

            AboutToastHost(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .semantics { contentDescription = "toastHost" }
            )
            if (state.displayState == AboutFeedbackDisplayState.Confirm) {
                AboutFeedbackConfirmDialog(
                    confirm = state.confirm,
                    onCancel = onBack,
                    onConfirm = onExportLogConfirm,
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
private fun VersionCard(
    version: AboutMetricUiModel,
    status: AboutMetricUiModel,
    loading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .semantics { contentDescription = "${version.label}，${version.value}，${status.label}，${status.value}" }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ReaderTheme.shapes.medium)
                    .background(ReaderTheme.colors.floatingControlBgAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = version.icon.toAboutIcon().asImageVector(),
                    contentDescription = null,
                    tint = ReaderTheme.colors.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Reader", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
                Text("${version.label}：${version.value}", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
            ReaderChip(text = if (loading) "检查中" else status.value, selected = !loading)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(AboutSuccessColor.copy(alpha = 0.10f))
                .padding(ReaderTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            Icon(
                imageVector = status.icon.toAboutIcon().asImageVector(),
                contentDescription = null,
                tint = AboutSuccessColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "${status.label}：${if (loading) "检查更新中" else status.value}",
                color = AboutSuccessColor,
                style = ReaderTheme.typography.bookTitle
            )
        }
    }
}

@Composable
private fun AboutFeedbackSection(
    section: AboutFeedbackSectionUiModel,
    checkUpdateEnabled: Boolean,
    feedbackEnabled: Boolean,
    onOpenRow: (AboutFeedbackRowUiModel) -> Unit,
    onCheckUpdate: () -> Unit,
    onSubmitFeedback: () -> Unit
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
                    "检查更新" -> UpdateButton(
                        row = row,
                        enabled = checkUpdateEnabled,
                        onClick = onCheckUpdate
                    )
                    "问题反馈" -> FeedbackEntry(
                        row = row,
                        enabled = feedbackEnabled,
                        onClick = onSubmitFeedback
                    )
                    else -> AboutLinkRow(
                        row = row,
                        onClick = { onOpenRow(row) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLinkRow(
    row: AboutFeedbackRowUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = listOf(row.title, row.meta, row.value).filter { it.isNotBlank() }.joinToString("，") }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toAboutIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        if (row.value.isNotBlank()) {
            Text(row.value, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.bodyText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FeedbackEntry(
    row: AboutFeedbackRowUiModel,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${row.title}，${row.meta}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Message.asImageVector(),
            contentDescription = null,
            tint = if (enabled) ReaderTheme.colors.primary else ReaderTheme.colors.bodyText,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderChip(text = if (enabled) "提交" else "离线不可用", selected = enabled)
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.bodyText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun UpdateButton(
    row: AboutFeedbackRowUiModel,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = listOf(row.title, row.meta, row.value).filter { it.isNotBlank() }.joinToString("，") }
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
        ReaderSecondaryButton(
            text = row.value.ifBlank { "检查" },
            onClick = onClick,
            enabled = enabled,
            contentDescription = row.title
        )
    }
}

@Composable
private fun AboutToastHost(
    state: AboutFeedbackUiState,
    modifier: Modifier = Modifier
) {
    val label = when (state.displayState) {
        AboutFeedbackDisplayState.Default -> state.toast.success
        AboutFeedbackDisplayState.Error -> state.toast.error
        AboutFeedbackDisplayState.Offline -> state.toast.offline
        AboutFeedbackDisplayState.Loading,
        AboutFeedbackDisplayState.Confirm -> null
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
private fun AboutFeedbackConfirmDialog(
    confirm: AboutFeedbackConfirmUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
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
        Text(confirm.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Text(confirm.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = confirm.cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
            ReaderPrimaryButton(text = confirm.confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AboutFeedbackFeedback(
    feedback: AboutFeedbackMessageUiModel,
    onPrimary: () -> Unit,
    loading: Boolean = false,
    offline: Boolean = false
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

private fun String.toAboutIcon(): ReaderIconToken = when (this) {
    "book" -> ReaderIconToken.Directory
    "check" -> ReaderIconToken.Check
    "refresh" -> ReaderIconToken.Refresh
    "file" -> ReaderIconToken.FileOpen
    "link" -> ReaderIconToken.Link
    "info" -> ReaderIconToken.Help
    "message" -> ReaderIconToken.Message
    "log" -> ReaderIconToken.FileOpen
    "shield" -> ReaderIconToken.Shield
    else -> ReaderIconToken.Info
}
