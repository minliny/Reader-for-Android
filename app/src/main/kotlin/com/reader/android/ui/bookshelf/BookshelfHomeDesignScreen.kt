package com.reader.android.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookCard
import com.reader.android.ui.components.BookCover
import com.reader.android.ui.components.BookListItem
import com.reader.android.ui.components.BookProgressIndicator
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookshelfHomeDesignContent(
    state: BookshelfHomeUiState,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onGroupChange: (BookshelfHomeGroupUiModel) -> Unit = {},
    onRead: (BookshelfHomeBookUiModel) -> Unit = {},
    onSortFilter: () -> Unit = {},
    onBookshelfSettings: () -> Unit = {},
    onOpenBook: (BookshelfHomeBookUiModel) -> Unit = {},
    onRefreshUpdates: () -> Unit = {},
    onImportLocal: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReaderTheme.colors.paperBg)
            .semantics { contentDescription = "MainTabShell 书架" }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = state.topBar.title,
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.Search.asImageVector(),
                        contentDescription = "搜索书籍",
                        onClick = onSearchClick
                    )
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多书架操作",
                        onClick = onMoreClick
                    )
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ReaderTheme.spacing.screenPadding)
                    .semantics { contentDescription = "contentRegion" },
                verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                BookshelfHomeGroups(groups = state.groups, onGroupChange = onGroupChange)
                when (state.displayState) {
                    BookshelfHomeDisplayState.Loading -> ReaderLoadingState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp),
                        message = state.feedback.loading.message
                    )
                    BookshelfHomeDisplayState.Empty -> BookshelfHomeFeedbackCard(
                        feedback = state.feedback.empty,
                        icon = ReaderIconToken.FolderOff,
                        onPrimary = onSearchClick,
                        onSecondary = onImportLocal
                    )
                    BookshelfHomeDisplayState.Default,
                    BookshelfHomeDisplayState.Filtering -> {
                        state.continueReading?.let { book ->
                            ContinueReadingCard(book = book, onRead = onRead)
                        } ?: BookshelfHomeFeedbackCard(
                            feedback = state.feedback.empty,
                            icon = ReaderIconToken.FileOpen,
                            onPrimary = onSearchClick,
                            onSecondary = onImportLocal
                        )
                        RecentUpdatesSection(
                            updates = state.recentUpdates,
                            feedback = state.feedback.noUpdates,
                            onRefreshUpdates = onRefreshUpdates
                        )
                        BookshelfHomeToolbar(
                            displayState = state.displayState,
                            onSortFilter = onSortFilter,
                            onBookshelfSettings = onBookshelfSettings
                        )
                        BookshelfHomeGrid(books = state.books, onOpenBook = onOpenBook)
                    }
                }
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .semantics { contentDescription = "stateHost" }
        )
    }
}

@Composable
private fun BookshelfHomeGroups(
    groups: List<BookshelfHomeGroupUiModel>,
    onGroupChange: (BookshelfHomeGroupUiModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        groups.forEach { group ->
            ReaderChip(
                text = group.label,
                selected = group.active,
                contentDescription = "书架分组，${group.label}",
                onClick = { onGroupChange(group) }
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(
    book: BookshelfHomeBookUiModel,
    onRead: (BookshelfHomeBookUiModel) -> Unit
) {
    ReaderCard(
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "继续阅读，${book.title}",
        onClick = { onRead(book) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            BookCover(
                title = book.title,
                modifier = Modifier.size(width = 56.dp, height = 78.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "继续阅读",
                    color = ReaderTheme.colors.primary,
                    style = ReaderTheme.typography.readerControlLabel
                )
                Text(
                    text = book.title,
                    color = ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.sectionTitle,
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
            ReaderPrimaryButton(text = book.progressLabel, onClick = { onRead(book) })
        }
    }
}

@Composable
private fun RecentUpdatesSection(
    updates: List<BookshelfRecentUpdateUiModel>,
    feedback: BookshelfHomeFeedbackUiModel,
    onRefreshUpdates: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        ReaderSectionHeader(
            title = "最近更新",
            action = {
                ReaderSecondaryButton(text = feedback.primaryAction, onClick = onRefreshUpdates)
            }
        )
        if (updates.isEmpty()) {
            BookshelfHomeFeedbackCard(feedback = feedback, icon = ReaderIconToken.Refresh, onPrimary = onRefreshUpdates)
        } else {
            updates.forEach { update ->
                BookListItem(
                    title = update.title,
                    latestChapter = update.chapter,
                    progress = if (update.unread) 0.08f else null,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun BookshelfHomeToolbar(
    displayState: BookshelfHomeDisplayState,
    onSortFilter: () -> Unit,
    onBookshelfSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (displayState == BookshelfHomeDisplayState.Filtering) "追更分组" else "全部书籍",
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.sectionTitle
        )
        ReaderSecondaryButton(text = "排序筛选", onClick = onSortFilter)
        ReaderSecondaryButton(text = "书架设置", onClick = onBookshelfSettings)
    }
}

@Composable
private fun BookshelfHomeGrid(
    books: List<BookshelfHomeBookUiModel>,
    onOpenBook: (BookshelfHomeBookUiModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        books.chunked(2).forEach { rowBooks ->
            Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                rowBooks.forEach { book ->
                    BookCard(
                        title = book.title,
                        author = "${book.author} · ${book.chapter}",
                        progress = book.progress,
                        onClick = { onOpenBook(book) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowBooks.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BookshelfHomeFeedbackCard(
    feedback: BookshelfHomeFeedbackUiModel,
    icon: ReaderIconToken,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null
) {
    ReaderCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "stateHost，${feedback.title}" },
        contentDescription = feedback.title
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            Icon(
                imageVector = icon.asImageVector(),
                contentDescription = null,
                tint = ReaderTheme.colors.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = feedback.title,
                modifier = Modifier.semantics { heading() },
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.stateTitle,
                textAlign = TextAlign.Center
            )
            Text(
                text = feedback.message,
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.stateMessage,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                if (feedback.secondaryAction.isNotBlank() && onSecondary != null) {
                    ReaderSecondaryButton(text = feedback.secondaryAction, onClick = onSecondary)
                }
                if (feedback.primaryAction.isNotBlank()) {
                    ReaderPrimaryButton(text = feedback.primaryAction, onClick = onPrimary)
                }
            }
        }
    }
}
