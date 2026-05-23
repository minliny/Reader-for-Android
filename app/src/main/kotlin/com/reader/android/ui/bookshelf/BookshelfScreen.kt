package com.reader.android.ui.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderOfflineState
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookshelfScreen(
    onSearchClick: () -> Unit = {},
    uiState: ReaderUiState? = null
) {
    ReaderTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReaderAppTopBar(title = "书架")
                when (uiState) {
                    is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.weight(1f))
                    is ReaderUiState.Error -> ReaderErrorState(
                        title = "加载失败",
                        message = uiState.message,
                        modifier = Modifier.weight(1f),
                        onRetryClick = if (uiState.retryable) ({}) else null
                    )
                    is ReaderUiState.Offline -> ReaderOfflineState(modifier = Modifier.weight(1f))
                    else -> ReaderEmptyState(
                        title = "书架为空",
                        message = "点击右下角按钮搜索书籍",
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
                Icon(Icons.Filled.Search, contentDescription = "搜索")
            }
        }
    }
}
