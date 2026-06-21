package com.reader.android.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookActionSheetItem
import com.reader.android.ui.components.BookCover
import com.reader.android.ui.components.BookListItem
import com.reader.android.ui.components.BookProgressIndicator
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

private val ActionDangerColor = Color(0xFFBA1A1A)

@Composable
fun BookshelfActionSheetDesignScreen(
    state: BookshelfActionSheetDesignUiState = BookshelfActionSheetMapper.fromFixture(),
    onDismiss: () -> Unit = {},
    onEdit: (BookshelfActionBookUiModel) -> Unit = {},
    onDeleteRequest: (BookshelfActionBookUiModel) -> Unit = {},
    onDeleteCancel: (BookshelfActionBookUiModel) -> Unit = {},
    onDeleteConfirm: (BookshelfActionBookUiModel) -> Unit = {},
    onDeleteRetry: (BookshelfActionBookUiModel) -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "LibraryShell，书籍操作底表" }
        ) {
            BookshelfActionBackdrop(state = state)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ReaderTheme.colors.bodyText.copy(alpha = 0.18f))
                    .semantics { contentDescription = "SheetHost，书籍操作遮罩" }
            )
            BookshelfActionBottomSheet(
                state = state,
                onDismiss = onDismiss,
                onEdit = onEdit,
                onDeleteRequest = onDeleteRequest,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            if (state.displayState != BookshelfActionSheetDisplayState.Default) {
                BookshelfActionConfirmDialog(
                    state = state,
                    onCancel = { onDeleteCancel(state.selectedBook) },
                    onConfirm = { onDeleteConfirm(state.selectedBook) },
                    onRetry = { onDeleteRetry(state.selectedBook) }
                )
            }
        }
    }
}

@Composable
private fun BookshelfActionBackdrop(state: BookshelfActionSheetDesignUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        ReaderAppTopBar(
            title = state.backdropTitle,
            actions = {
                ReaderIconButton(
                    icon = ReaderIconToken.Search.asImageVector(),
                    contentDescription = "搜索书籍",
                    onClick = {}
                )
                ReaderIconButton(
                    icon = ReaderIconToken.More.asImageVector(),
                    contentDescription = "更多书架操作",
                    onClick = {}
                )
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs)
                .semantics { contentDescription = "ContentRegion，书架背景分组" },
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            state.groups.forEach { group ->
                ReaderChip(
                    text = group.label,
                    selected = group.active,
                    contentDescription = "书架分组，${group.label}"
                )
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.backdropBooks, key = { it.title }) { book ->
                BookListItem(
                    title = book.title,
                    author = book.author,
                    latestChapter = book.chapter,
                    progress = book.progress,
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (book.selected) {
                                Modifier.background(ReaderTheme.colors.metaBg)
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun BookshelfActionBottomSheet(
    state: BookshelfActionSheetDesignUiState,
    onDismiss: () -> Unit,
    onEdit: (BookshelfActionBookUiModel) -> Unit,
    onDeleteRequest: (BookshelfActionBookUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.bottomSheet)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.bottomSheet)
            .semantics { contentDescription = "BottomSheet，书籍操作底表" }
            .padding(top = ReaderTheme.spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(ReaderTheme.shapes.chip)
                .background(ReaderTheme.colors.mutedTrack)
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReaderTheme.spacing.screenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "书籍操作",
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.pageTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ReaderSecondaryButton(text = "关闭", onClick = onDismiss)
        }
        BookActionSummary(book = state.selectedBook)
        HorizontalDivider(color = ReaderTheme.colors.controlBorder)
        state.actions.forEach { action ->
            BookActionSheetItem(
                title = action.title,
                subtitle = action.copy,
                leading = {
                    Icon(
                        imageVector = action.iconToken().asImageVector(),
                        contentDescription = null,
                        tint = if (action.tone == BookshelfActionTone.Danger) {
                            ActionDangerColor
                        } else {
                            ReaderTheme.colors.primary
                        }
                    )
                },
                onClick = {
                    if (action.type == "delete") {
                        onDeleteRequest(state.selectedBook)
                    } else {
                        onEdit(state.selectedBook)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
    }
}

@Composable
private fun BookActionSummary(book: BookshelfActionBookUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ReaderTheme.spacing.screenPadding)
            .semantics { contentDescription = "BookSummary，${book.title}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(
            title = book.coverLabel,
            modifier = Modifier.size(width = 48.dp, height = 64.dp)
        )
        Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.bookTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${book.author} · ${book.chapter}",
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.bookMeta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            BookProgressIndicator(progress = book.progress)
        }
    }
}

@Composable
private fun BookshelfActionConfirmDialog(
    state: BookshelfActionSheetDesignUiState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit
) {
    val feedback = state.feedback
    val isLoading = state.displayState == BookshelfActionSheetDisplayState.Loading
    val isError = state.displayState == BookshelfActionSheetDisplayState.Error
    AlertDialog(
        onDismissRequest = if (isLoading) {
            {}
        } else {
            onCancel
        },
        confirmButton = {
            ReaderPrimaryButton(
                text = when {
                    isLoading -> state.confirm.loadingLabel
                    isError -> feedback?.primaryAction ?: "重试"
                    else -> state.confirm.confirmLabel
                },
                onClick = if (isError) onRetry else onConfirm,
                enabled = !isLoading,
                contentDescription = if (isError) "删除失败后重试" else state.confirm.confirmLabel
            )
        },
        dismissButton = {
            ReaderSecondaryButton(
                text = feedback?.secondaryAction ?: state.confirm.cancelLabel,
                onClick = onCancel,
                enabled = !isLoading
            )
        },
        title = {
            Text(
                text = feedback?.title ?: state.confirm.title,
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.sectionTitle
            )
        },
        text = {
            Text(
                text = feedback?.message ?: state.confirm.message,
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.stateMessage
            )
        },
        containerColor = ReaderTheme.colors.paperBg,
        shape = ReaderTheme.shapes.card,
        modifier = Modifier.semantics { contentDescription = "DialogHost，删除书架记录确认" }
    )
}

private fun BookshelfActionItemUiModel.iconToken(): ReaderIconToken =
    when (tone) {
        BookshelfActionTone.Normal -> ReaderIconToken.Edit
        BookshelfActionTone.Danger -> ReaderIconToken.Delete
    }
