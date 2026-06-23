package com.reader.android.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookshelfGroupManagementScreen(
    state: BookshelfGroupManagementUiState = BookshelfGroupManagementMapper.fromFixture(),
    onBack: () -> Unit = {},
    onAddGroupOpen: () -> Unit = {},
    onGroupRenameOpen: (BookshelfGroupRowUiModel) -> Unit = {},
    onGroupDeleteOpen: (BookshelfGroupRowUiModel) -> Unit = {},
    onDialogCancel: () -> Unit = {},
    onDialogSave: (String) -> Unit = {},
    onDeleteConfirm: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReaderAppTopBar(
                    title = state.title,
                    navigationContentDescription = state.backLabel,
                    onNavigateBack = onBack,
                    actions = {
                        ReaderSecondaryButton(text = state.addLabel, onClick = onAddGroupOpen)
                    }
                )
                GroupManagementContent(
                    state = state,
                    onGroupRenameOpen = onGroupRenameOpen,
                    onGroupDeleteOpen = onGroupDeleteOpen,
                    onAddGroupOpen = onAddGroupOpen,
                    modifier = Modifier.weight(1f)
                )
                state.toastMessage?.let { toast ->
                    Text(
                        text = toast,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(ReaderTheme.spacing.sm)
                            .clip(ReaderTheme.shapes.chip)
                            .background(ReaderTheme.colors.metaBg)
                            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
                        color = ReaderTheme.colors.primary,
                        style = ReaderTheme.typography.readerControlLabel
                    )
                }
            }
            if (state.hasDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ReaderTheme.colors.bodyText.copy(alpha = 0.18f))
                        .semantics { contentDescription = "分组管理弹窗遮罩" }
                )
                when (state.displayState) {
                    BookshelfGroupManagementDisplayState.Delete -> GroupDeleteConfirmDialog(
                        confirm = state.deleteConfirm,
                        onCancel = onDialogCancel,
                        onConfirm = onDeleteConfirm,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> GroupNameDialog(
                        state = state,
                        onCancel = onDialogCancel,
                        onSave = onDialogSave,
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupManagementContent(
    state: BookshelfGroupManagementUiState,
    onGroupRenameOpen: (BookshelfGroupRowUiModel) -> Unit,
    onGroupDeleteOpen: (BookshelfGroupRowUiModel) -> Unit,
    onAddGroupOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.visibleGroups, key = { it.id }) { group ->
                GroupRow(
                    group = group,
                    onRename = { onGroupRenameOpen(group) },
                    onDelete = { onGroupDeleteOpen(group) }
                )
            }
        }
        if (state.displayState == BookshelfGroupManagementDisplayState.Empty) {
            GroupEmptyBlock(
                feedback = state.feedback ?: BookshelfGroupManagementFixture.emptyFeedback,
                onAddGroupOpen = onAddGroupOpen
            )
        }
    }
}

@Composable
private fun GroupRow(
    group: BookshelfGroupRowUiModel,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "分组，${group.title}，${group.meta}" }
                .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            Box(
                modifier = Modifier.size(ReaderTheme.spacing.quickCircleSize),
                contentAlignment = Alignment.Center
            ) {
                if (group.canReorder) {
                    Icon(
                        imageVector = ReaderIconToken.ViewList.asImageVector(),
                        contentDescription = "调整排序，${group.title}",
                        tint = ReaderTheme.colors.controlInk
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
                    Text(
                        text = group.title,
                        color = ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.bookTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group.system) {
                        ReaderChip(text = "系统", selected = false, contentDescription = "系统分组")
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = group.meta,
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.bookMeta
                )
            }
            if (group.canRename || group.canDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
                    if (group.canRename) {
                        ReaderSecondaryButton(text = "重命名", onClick = onRename)
                    }
                    if (group.canDelete) {
                        ReaderIconButton(
                            icon = ReaderIconToken.Delete.asImageVector(),
                            contentDescription = "删除分组，${group.title}",
                            onClick = onDelete
                        )
                    }
                }
            } else {
                Text("${group.count} 本", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = ReaderTheme.spacing.screenPadding),
            color = ReaderTheme.colors.controlBorder
        )
    }
}

@Composable
private fun GroupEmptyBlock(
    feedback: BookshelfGroupFeedbackUiModel,
    onAddGroupOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ReaderTheme.spacing.lg)
            .semantics { contentDescription = feedback.title },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.stateTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        ReaderPrimaryButton(text = feedback.primaryAction, onClick = onAddGroupOpen)
    }
}

@Composable
private fun GroupNameDialog(
    state: BookshelfGroupManagementUiState,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRename = state.displayState == BookshelfGroupManagementDisplayState.Rename ||
        state.displayState == BookshelfGroupManagementDisplayState.Error
    val isSaving = state.displayState == BookshelfGroupManagementDisplayState.Loading
    val title = if (isRename) state.dialog.renameTitle else state.dialog.newTitle
    Column(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .clip(ReaderTheme.shapes.large)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.large)
            .semantics { contentDescription = title }
            .padding(ReaderTheme.spacing.md)
    ) {
        Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
                .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
                .padding(ReaderTheme.spacing.sm)
        ) {
            Text(
                text = state.dialogValue.ifBlank { state.dialog.inputPlaceholder },
                color = if (state.dialogValue.isBlank()) ReaderTheme.colors.bodyText else ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.bookTitle
            )
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(state.dialog.helper, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        state.feedback?.let { feedback ->
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
            GroupInlineFeedback(feedback = feedback, isError = state.displayState == BookshelfGroupManagementDisplayState.Error)
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(
                text = state.dialog.cancelLabel,
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            )
            ReaderPrimaryButton(
                text = if (isSaving) state.dialog.savingLabel else state.dialog.saveLabel,
                onClick = {
                    if (state.displayState == BookshelfGroupManagementDisplayState.Error) {
                        onRetry()
                    } else {
                        onSave(state.dialogValue)
                    }
                },
                enabled = !isSaving && (state.dialogValue.isNotBlank() || isRename),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GroupInlineFeedback(
    feedback: BookshelfGroupFeedbackUiModel,
    isError: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(if (isError) ReaderTheme.colors.floatingControlBgAlt else ReaderTheme.colors.metaBg)
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(feedback.title, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookTitle)
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
    }
}

@Composable
private fun GroupDeleteConfirmDialog(
    confirm: BookshelfGroupDeleteConfirmUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .clip(ReaderTheme.shapes.large)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.large)
            .semantics { contentDescription = confirm.title }
            .padding(ReaderTheme.spacing.md)
    ) {
        Text(confirm.title, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(confirm.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = confirm.cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
            ReaderPrimaryButton(
                text = confirm.confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                contentDescription = "危险操作，${confirm.confirmLabel}"
            )
        }
    }
}
