package com.reader.android.ui.booksource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun SourceImportScreen(
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    importState: SourceImportUiState = SourceImportUiState(),
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
                            .padding(ReaderTheme.spacing.lg),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = ReaderIconToken.FileOpen.asImageVector(),
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
                        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                        Text(
                            importState.message,
                            color = if (importState.status == SourceImportStatus.Error || importState.status == SourceImportStatus.BlankJson) {
                                ReaderTheme.colors.controlInk
                            } else {
                                ReaderTheme.colors.bodyText
                            },
                            style = ReaderTheme.typography.bookMeta,
                            textAlign = TextAlign.Center
                        )
                        if (importState.importedCount > 0) {
                            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                            Text(
                                "已导入 ${importState.importedCount} 个书源",
                                color = ReaderTheme.colors.primary,
                                style = ReaderTheme.typography.bookMeta,
                                textAlign = TextAlign.Center
                            )
                        }
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
