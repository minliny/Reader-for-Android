package com.reader.android.ui.booksource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun SourceImportScreen(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onImportClick: () -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "导入书源",
                onNavigateBack = onBack,
                actions = {
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk)
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(ReaderTheme.spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.FileOpen,
                    contentDescription = null,
                    tint = ReaderTheme.colors.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
                Text(
                    "导入书源",
                    modifier = Modifier.semantics { heading() },
                    color = ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.stateTitle,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                Text(
                    "从 URL、剪贴板或本地文件导入。",
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.stateMessage,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
                ReaderPrimaryButton(
                    text = "选择导入方式",
                    onClick = onImportClick,
                    contentDescription = "选择导入方式"
                )
            }
            }
            }
        }
    }
}
