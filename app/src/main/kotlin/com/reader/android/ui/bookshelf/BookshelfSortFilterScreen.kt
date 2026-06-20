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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookListItem
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookshelfSortFilterScreen(
    state: BookshelfSortFilterUiState = BookshelfSortFilterMapper.fromFixture(),
    onDismiss: () -> Unit = {},
    onSortSelect: (BookshelfSortFilterOptionUiModel) -> Unit = {},
    onOrderSelect: (BookshelfSortFilterOptionUiModel) -> Unit = {},
    onFilterToggle: (BookshelfSortFilterOptionUiModel) -> Unit = {},
    onReset: () -> Unit = {},
    onApply: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
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
                SortFilterBackdrop(
                    state = state,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ReaderTheme.colors.bodyText.copy(alpha = 0.18f))
                    .semantics { contentDescription = "排序筛选遮罩" }
            )
            SortFilterBottomSheet(
                state = state,
                onDismiss = onDismiss,
                onSortSelect = onSortSelect,
                onOrderSelect = onOrderSelect,
                onFilterToggle = onFilterToggle,
                onReset = onReset,
                onApply = onApply,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SortFilterBackdrop(
    state: BookshelfSortFilterUiState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs),
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
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.backdropBooks, key = { it.title }) { book ->
                BookListItem(
                    title = book.title,
                    latestChapter = book.meta,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SortFilterBottomSheet(
    state: BookshelfSortFilterUiState,
    onDismiss: () -> Unit,
    onSortSelect: (BookshelfSortFilterOptionUiModel) -> Unit,
    onOrderSelect: (BookshelfSortFilterOptionUiModel) -> Unit,
    onFilterToggle: (BookshelfSortFilterOptionUiModel) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.bottomSheet)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.bottomSheet)
            .semantics { contentDescription = "排序与筛选底表" }
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
                text = state.sheetTitle,
                modifier = Modifier.weight(1f),
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.pageTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ReaderSecondaryButton(text = "关闭", onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        HorizontalDivider(color = ReaderTheme.colors.controlBorder)
        if (state.isFeedbackState) {
            SortFilterFeedbackBlock(
                feedback = state.feedback ?: BookshelfSortFilterFixture.emptyFeedback,
                isError = state.displayState == BookshelfSortFilterDisplayState.Error,
                onPrimary = if (state.displayState == BookshelfSortFilterDisplayState.Error) onRetry else onReset,
                onSecondary = onDismiss
            )
        } else {
            SortFilterSections(
                sections = state.sections,
                onSortSelect = onSortSelect,
                onOrderSelect = onOrderSelect,
                onFilterToggle = onFilterToggle
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ReaderTheme.spacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                ReaderSecondaryButton(
                    text = state.resetLabel,
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                )
                ReaderPrimaryButton(
                    text = state.applyLabel,
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                )
            }
            state.toastMessage?.let { toast ->
                Text(
                    text = toast,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = ReaderTheme.spacing.sm)
                        .clip(ReaderTheme.shapes.chip)
                        .background(ReaderTheme.colors.metaBg)
                        .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
                    color = ReaderTheme.colors.primary,
                    style = ReaderTheme.typography.readerControlLabel
                )
            }
        }
    }
}

@Composable
private fun SortFilterSections(
    sections: List<BookshelfSortFilterSectionUiModel>,
    onSortSelect: (BookshelfSortFilterOptionUiModel) -> Unit,
    onOrderSelect: (BookshelfSortFilterOptionUiModel) -> Unit,
    onFilterToggle: (BookshelfSortFilterOptionUiModel) -> Unit
) {
    Column(modifier = Modifier.padding(top = ReaderTheme.spacing.xs)) {
        sections.forEach { section ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs)
            ) {
                Text(
                    text = section.title,
                    color = ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.sectionTitle
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
                ) {
                    section.options.forEach { option ->
                        ReaderChip(
                            text = option.label,
                            selected = option.active,
                            contentDescription = "${section.title}，${option.label}",
                            onClick = {
                                when (section.title) {
                                    "排序方式" -> onSortSelect(option)
                                    "排序顺序" -> onOrderSelect(option)
                                    else -> onFilterToggle(option)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortFilterFeedbackBlock(
    feedback: BookshelfSortFilterFeedbackUiModel,
    isError: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ReaderTheme.spacing.lg)
            .semantics { contentDescription = feedback.title },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = feedback.title,
            color = if (isError) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.stateTitle
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(
            text = feedback.message,
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.stateMessage
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderPrimaryButton(text = feedback.primaryAction, onClick = onPrimary)
            ReaderSecondaryButton(text = feedback.secondaryAction, onClick = onSecondary)
        }
    }
}
