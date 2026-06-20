package com.reader.android.ui.booksource

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

data class SourceDetailData(
    val sourceName: String,
    val sourceUrl: String,
    val sourceGroup: String,
    val enabled: Boolean,
    val searchRuleStatus: String = "正常",
    val tocRuleStatus: String = "正常",
    val contentRuleStatus: String = "正常"
)

@Composable
fun SourceDetailScreen(
    source: SourceDetailData,
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "书源详情",
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
                ReaderSectionHeader(title = source.sourceName)

                ReaderCard {
                    Text(
                        source.sourceName,
                        color = ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.pageTitle
                    )
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                    Text(
                        "搜索、目录、正文规则均正常。",
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.stateMessage
                    )
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
                    ReaderChip(
                        text = if (source.enabled) "已启用" else "已禁用",
                        selected = source.enabled
                    )
                }

                Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))

                // Detail info
                SourceDetailRow("书源 URL", source.sourceUrl)
                SourceDetailRow("分组", source.sourceGroup)
                SourceDetailRow("搜索规则", source.searchRuleStatus)
                SourceDetailRow("目录规则", source.tocRuleStatus)
                SourceDetailRow("正文规则", source.contentRuleStatus)
            }
            }
            }
        }
    }
}

@Composable
private fun SourceDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = ReaderTheme.spacing.xs)) {
        Text(label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        Text(value, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
    }
}
