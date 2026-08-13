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

private val CacheDangerColor = Color(0xFFD92D20)
private val CacheSuccessColor = Color(0xFF1F7A4D)

@Composable
fun CacheManagementScreen(
    state: CacheManagementUiState = CacheManagementMapper.fromFixture(),
    onBack: () -> Unit = {},
    onOpenRow: (CacheManagementRowUiModel) -> Unit = {},
    onSwitchChange: (CacheManagementRowUiModel, Boolean) -> Unit = { _, _ -> },
    onClearCacheOpen: () -> Unit = {},
    onClearCacheConfirm: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "SettingsShell 缓存管理" }
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
                    CacheSizeCard(storage = state.storage, loading = state.displayState == CacheManagementDisplayState.Loading)
                    state.sections.forEach { section ->
                        CacheManagementSection(
                            section = section,
                            onOpenRow = onOpenRow,
                            onSwitchChange = onSwitchChange
                        )
                    }
                    CacheDangerActionRow(state.danger, onClearCacheOpen)
                    if (state.displayState == CacheManagementDisplayState.Default) {
                        CacheCleanupResult(state.cleanupResult)
                    }
                    if (state.displayState == CacheManagementDisplayState.Loading) {
                        CacheFeedback(state.loading, onPrimary = onRetry, loading = true)
                    }
                    if (state.displayState == CacheManagementDisplayState.Empty) {
                        CacheEmptyState(state.empty, onRetry)
                    }
                    if (state.displayState == CacheManagementDisplayState.Error) {
                        CacheFeedback(state.error, onPrimary = onRetry)
                    }
                }
            }

            CacheToastHost(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .semantics { contentDescription = "toastHost" }
            )
            if (state.displayState == CacheManagementDisplayState.Confirm) {
                CacheConfirmDialog(
                    danger = state.danger,
                    onCancel = onBack,
                    onConfirm = onClearCacheConfirm,
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
private fun CacheSizeCard(
    storage: CacheStorageUiModel,
    loading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .semantics { contentDescription = "${storage.title}，${storage.value}，${storage.percentLabel}" }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            Icon(
                imageVector = ReaderIconToken.Storage.asImageVector(),
                contentDescription = null,
                tint = ReaderTheme.colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(storage.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
                Text(if (loading) "正在计算" else storage.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
            Text(storage.value, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        }
        StorageBar(percent = storage.percent, contentDescription = "存储占用，${storage.percentLabel}")
    }
}

@Composable
private fun StorageBar(
    percent: Float,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.mutedTrack)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(ReaderTheme.shapes.chip)
                .background(ReaderTheme.colors.primary)
        )
    }
}

@Composable
private fun CacheManagementSection(
    section: CacheManagementSectionUiModel,
    onOpenRow: (CacheManagementRowUiModel) -> Unit,
    onSwitchChange: (CacheManagementRowUiModel, Boolean) -> Unit
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
                CacheCategoryRow(
                    row = row,
                    onOpenRow = onOpenRow,
                    onSwitchChange = onSwitchChange
                )
            }
        }
    }
}

@Composable
private fun CacheCategoryRow(
    row: CacheManagementRowUiModel,
    onOpenRow: (CacheManagementRowUiModel) -> Unit,
    onSwitchChange: (CacheManagementRowUiModel, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = row.type != CacheManagementRowType.Switch, role = Role.Button) { onOpenRow(row) }
            .semantics { contentDescription = listOf(row.title, row.meta, row.value).filter { it.isNotBlank() }.joinToString("，") }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toCacheIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        when (row.type) {
            CacheManagementRowType.Switch -> ReaderSwitch(
                checked = row.enabled,
                onCheckedChange = { onSwitchChange(row, it) }
            )
            CacheManagementRowType.Link,
            CacheManagementRowType.Select -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
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
    }
}

@Composable
private fun CacheDangerActionRow(
    danger: CacheManagementDangerUiModel,
    onClearCacheOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, CacheDangerColor.copy(alpha = 0.35f), ReaderTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClearCacheOpen)
            .semantics { contentDescription = danger.title }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Trash.asImageVector(),
            contentDescription = null,
            tint = CacheDangerColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(danger.title, color = CacheDangerColor, style = ReaderTheme.typography.bookTitle)
            Text(danger.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = CacheDangerColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CacheCleanupResult(result: CacheManagementFeedbackUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(CacheSuccessColor.copy(alpha = 0.10f))
            .border(1.dp, CacheSuccessColor.copy(alpha = 0.35f), ReaderTheme.shapes.small)
            .semantics { contentDescription = "清理结果，${result.title}" }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Storage.asImageVector(),
            contentDescription = null,
            tint = CacheSuccessColor,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, color = CacheSuccessColor, style = ReaderTheme.typography.bookTitle)
            Text(result.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
    }
}

@Composable
private fun CacheEmptyState(
    empty: CacheManagementEmptyUiModel,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
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
        Text(empty.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        ReaderPrimaryButton(text = empty.primaryAction, onClick = onRetry)
    }
}

@Composable
private fun CacheToastHost(
    state: CacheManagementUiState,
    modifier: Modifier = Modifier
) {
    val label = when (state.displayState) {
        CacheManagementDisplayState.Default -> state.toast.success
        CacheManagementDisplayState.Error -> state.toast.error
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
private fun CacheConfirmDialog(
    danger: CacheManagementDangerUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, CacheDangerColor.copy(alpha = 0.45f), ReaderTheme.shapes.card)
            .padding(ReaderTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(danger.confirmTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Text(danger.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = danger.cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
            CacheDangerButton(text = danger.confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CacheDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(CacheDangerColor)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .padding(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun CacheFeedback(
    feedback: CacheManagementFeedbackUiModel,
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
            imageVector = if (loading) ReaderIconToken.Refresh.asImageVector() else ReaderIconToken.Warning.asImageVector(),
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

private fun String.toCacheIcon(): ReaderIconToken = when (this) {
    "clock" -> ReaderIconToken.Clock
    "book" -> ReaderIconToken.Directory
    "image" -> ReaderIconToken.Image
    "search" -> ReaderIconToken.Search
    "source" -> ReaderIconToken.Rss
    "storage" -> ReaderIconToken.Storage
    "download" -> ReaderIconToken.Download
    "list" -> ReaderIconToken.List
    "folder" -> ReaderIconToken.Folder
    "trash" -> ReaderIconToken.Trash
    else -> ReaderIconToken.Settings
}
