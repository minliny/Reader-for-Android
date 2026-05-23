package com.reader.android.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookCard
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

data class DiscoverBook(
    val title: String,
    val author: String,
    val progress: Float? = null
)

@Composable
fun DiscoverScreen(
    books: List<DiscoverBook> = listOf(
        DiscoverBook("深空信号", "科幻 · 本地书籍", 0.25f),
        DiscoverBook("深空信号", "科幻 · 本地书籍", 0.25f),
        DiscoverBook("深空信号", "科幻 · 本地书籍", 0.25f)
    ),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onMoreClick: () -> Unit = {},
    onBookClick: (Int) -> Unit = {},
    onRssClick: () -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> { ReaderLoadingState(modifier = Modifier.fillMaxSize()) }
            is ReaderUiState.Error -> { ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize()) }
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "发现",
                actions = {
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk)
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ReaderTheme.spacing.screenPadding)
            ) {
                ReaderSectionHeader(title = "推荐")

                // Book grid: rows of 3
                val rows = books.chunked(3)
                rows.forEach { rowBooks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
                    ) {
                        rowBooks.forEachIndexed { index, book ->
                            BookCard(
                                title = book.title,
                                author = book.author,
                                progress = book.progress,
                                onClick = { onBookClick(books.indexOf(book)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining cells
                        repeat(3 - rowBooks.size) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                ReaderSettingsRow(
                    title = "RSS 订阅",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    trailing = {
                        Icon(Icons.Filled.ChevronRight, "RSS 订阅", tint = ReaderTheme.colors.controlInk)
                    },
                    onClick = onRssClick
                )
            }
            }
        }
    }
}
}
