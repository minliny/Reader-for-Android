package com.reader.android.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookCard
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.DiscoverUiState
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
    discoverState: DiscoverUiState = DiscoverRssWebDavMapper.discover(
        books.mapIndexed { index, book ->
            com.reader.android.ui.sync.DiscoverItemUiModel(
                id = "discover-$index",
                title = book.title,
                subtitle = book.author,
                progress = book.progress
            )
        }
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
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多",
                        onClick = onMoreClick
                    )
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ReaderTheme.spacing.screenPadding)
            ) {
                ReaderSectionHeader(title = "推荐")

                if (discoverState.isLoading) {
                    ReaderLoadingState(message = "正在加载推荐", modifier = Modifier.fillMaxWidth())
                } else if (discoverState.error != null) {
                    ReaderErrorState(title = discoverState.error.title, message = discoverState.error.message, modifier = Modifier.fillMaxWidth())
                } else if (discoverState.isEmpty) {
                    ReaderEmptyState(title = "暂无推荐", message = discoverState.emptyMessage, modifier = Modifier.fillMaxWidth())
                } else {
                val rows = discoverState.items.chunked(3)
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
                    ) {
                        rowItems.forEach { item ->
                            BookCard(
                                title = item.title,
                                author = item.subtitle,
                                progress = item.progress,
                                onClick = { onBookClick(discoverState.items.indexOf(item)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining cells
                        repeat(3 - rowItems.size) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                }

                ReaderSettingsRow(
                    title = "RSS 订阅",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    trailing = {
                        Icon(
                            imageVector = ReaderIconToken.Chevron.asImageVector(),
                            contentDescription = "RSS 订阅",
                            tint = ReaderTheme.colors.controlInk
                        )
                    },
                    onClick = onRssClick
                )
            }
            }
        }
    }
}
}
