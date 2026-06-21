package com.reader.android.ui.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookCard
import com.reader.android.ui.components.BookListItem
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderOfflineState
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookshelfScreen(
    onSearchClick: () -> Unit = {},
    uiState: ReaderUiState? = null,
    bookshelfState: BookshelfUiState = BookshelfMapper.empty(),
    bookshelfHomeState: BookshelfHomeUiState? = null,
    onLayoutModeChange: (BookshelfLayoutMode) -> Unit = {},
    onBookClick: (BookshelfBookUiModel) -> Unit = {},
    onBookMoreClick: (BookshelfBookUiModel) -> Unit = {},
    onHomeGroupChange: (BookshelfHomeGroupUiModel) -> Unit = {},
    onHomeBookOpen: (BookshelfHomeBookUiModel) -> Unit = {},
    onHomeRead: (BookshelfHomeBookUiModel) -> Unit = {},
    onSortFilterClick: () -> Unit = {},
    onBookshelfSettingsClick: () -> Unit = {},
    onRefreshUpdates: () -> Unit = {},
    onImportLocal: () -> Unit = {}
) {
    ReaderTheme {
        if (bookshelfHomeState != null) {
            BookshelfHomeDesignContent(
                state = bookshelfHomeState,
                onSearchClick = onSearchClick,
                onMoreClick = {},
                onGroupChange = onHomeGroupChange,
                onRead = onHomeRead,
                onSortFilter = onSortFilterClick,
                onBookshelfSettings = onBookshelfSettingsClick,
                onOpenBook = onHomeBookOpen,
                onRefreshUpdates = onRefreshUpdates,
                onImportLocal = onImportLocal
            )
            return@ReaderTheme
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReaderAppTopBar(
                    title = "书架",
                    actions = {
                        ReaderIconButton(
                            icon = if (bookshelfState.layoutMode == BookshelfLayoutMode.Cover) {
                                ReaderIconToken.ViewList.asImageVector()
                            } else {
                                ReaderIconToken.Grid.asImageVector()
                            },
                            contentDescription = "切换书架布局",
                            onClick = {
                                onLayoutModeChange(
                                    if (bookshelfState.layoutMode == BookshelfLayoutMode.Cover) {
                                        BookshelfLayoutMode.List
                                    } else {
                                        BookshelfLayoutMode.Cover
                                    }
                                )
                            }
                        )
                    }
                )
                when (uiState) {
                    is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.weight(1f))
                    is ReaderUiState.Error -> ReaderErrorState(
                        title = "加载失败",
                        message = uiState.message,
                        modifier = Modifier.weight(1f),
                        onRetryClick = if (uiState.retryable) ({}) else null
                    )
                    is ReaderUiState.Offline -> ReaderOfflineState(modifier = Modifier.weight(1f))
                    else -> BookshelfContent(
                        state = bookshelfState,
                        onBookClick = onBookClick,
                        onBookMoreClick = onBookMoreClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            FloatingActionButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = ReaderIconToken.Search.asImageVector(),
                    contentDescription = "搜索"
                )
            }
        }
    }
}

@Composable
private fun BookshelfContent(
    state: BookshelfUiState,
    onBookClick: (BookshelfBookUiModel) -> Unit,
    onBookMoreClick: (BookshelfBookUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isLoading -> ReaderLoadingState(modifier = modifier)
        state.errorMessage != null -> ReaderErrorState(
            title = "加载失败",
            message = state.errorMessage,
            modifier = modifier
        )
        state.isEmpty -> ReaderEmptyState(
            title = "书架为空",
            message = state.emptyMessage.ifBlank { "点击右下角按钮搜索书籍" },
            modifier = modifier
        )
        state.layoutMode == BookshelfLayoutMode.Cover -> BookshelfCoverMode(
            books = state.books,
            onBookClick = onBookClick,
            onBookMoreClick = onBookMoreClick,
            modifier = modifier
        )
        else -> BookshelfListMode(
            books = state.books,
            onBookClick = onBookClick,
            onBookMoreClick = onBookMoreClick,
            modifier = modifier
        )
    }
}

@Composable
private fun BookshelfCoverMode(
    books: List<BookshelfBookUiModel>,
    onBookClick: (BookshelfBookUiModel) -> Unit,
    onBookMoreClick: (BookshelfBookUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.padding(horizontal = ReaderTheme.spacing.screenPadding)) {
        items(books.chunked(2)) { rowBooks ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowBooks.forEach { book ->
                    Column(modifier = Modifier.weight(1f).padding(vertical = ReaderTheme.spacing.xs)) {
                        BookCard(
                            title = book.title,
                            author = listOfNotNull(book.author, book.sourceName, book.cacheState.label).joinToString(" · "),
                            progress = book.progress,
                            onClick = { onBookClick(book) }
                        )
                        ReaderIconButton(
                            icon = ReaderIconToken.More.asImageVector(),
                            contentDescription = "更多，${book.title}",
                            onClick = { onBookMoreClick(book) }
                        )
                    }
                    Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
                }
                if (rowBooks.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BookshelfListMode(
    books: List<BookshelfBookUiModel>,
    onBookClick: (BookshelfBookUiModel) -> Unit,
    onBookMoreClick: (BookshelfBookUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(books, key = { it.id }) { book ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = ReaderTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookListItem(
                    title = book.title,
                    author = book.author,
                    latestChapter = "${book.currentChapterTitle} · ${book.sourceName} · ${book.cacheState.label}",
                    progress = book.progress,
                    onClick = { onBookClick(book) },
                    modifier = Modifier.weight(1f)
                )
                ReaderIconButton(
                    icon = ReaderIconToken.More.asImageVector(),
                    contentDescription = "更多，${book.title}",
                    onClick = { onBookMoreClick(book) }
                )
            }
        }
    }
}
