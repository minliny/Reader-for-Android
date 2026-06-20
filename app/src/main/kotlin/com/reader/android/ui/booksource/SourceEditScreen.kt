package com.reader.android.ui.booksource

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun SourceEditScreen(
    initialName: String = "",
    initialUrl: String = "",
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onSave: (String, String) -> Unit = { _, _ -> }
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }

    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "编辑书源",
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
                    .verticalScroll(rememberScrollState())
                    .padding(ReaderTheme.spacing.screenPadding)
            ) {
                ReaderSectionHeader(title = "书源信息")

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ReaderTheme.colors.controlInk,
                    unfocusedTextColor = ReaderTheme.colors.controlInk,
                    focusedContainerColor = ReaderTheme.colors.metaBg,
                    unfocusedContainerColor = ReaderTheme.colors.metaBg,
                    focusedBorderColor = ReaderTheme.colors.primary,
                    unfocusedBorderColor = ReaderTheme.colors.controlBorder,
                    cursorColor = ReaderTheme.colors.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("书源名称", color = ReaderTheme.colors.bodyText) },
                    singleLine = true,
                    textStyle = ReaderTheme.typography.bookTitle,
                    colors = fieldColors,
                    shape = ReaderTheme.shapes.chip
                )

                Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("书源 URL", color = ReaderTheme.colors.bodyText) },
                    singleLine = true,
                    textStyle = ReaderTheme.typography.bookMeta,
                    colors = fieldColors,
                    shape = ReaderTheme.shapes.chip
                )

                Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))

                ReaderPrimaryButton(
                    text = "保存修改",
                    onClick = { onSave(name, url) },
                    modifier = Modifier.fillMaxWidth(),
                    contentDescription = "保存修改"
                )
            }
            }
            }
        }
    }
}
