package com.reader.android.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.reader.android.AppProvider
import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookInfo
import com.reader.android.data.model.BookSource
import com.reader.android.data.network.BookInfoParser
import com.reader.android.data.network.HttpClient
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

class BookDetailViewModel(private val useRealHttp: Boolean = false) {
    private val bridge: CoreBridge = AppProvider.coreBridge
    private val httpClient = HttpClient()
    private val parser = BookInfoParser()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var bookInfo by mutableStateOf<BookInfo?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load(detailUrl: String) {
        isLoading = true
        error = null
        try {
            if (useRealHttp) {
                val response = httpClient.get(detailUrl)
                bookInfo = parser.parseBookInfoResponse(response.body, source.sourceName)
            } else {
                bookInfo = bridge.getBookInfo(detailUrl, source)
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        isLoading = false
    }
}

@Composable
fun BookDetailScreen(
    detailUrl: String,
    onBack: () -> Unit,
    onTOC: (String) -> Unit,
    uiState: ReaderUiState? = null
) {
    val viewModel = remember { BookDetailViewModel() }

    LaunchedEffect(detailUrl) {
        viewModel.load(detailUrl)
    }

    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = viewModel.bookInfo?.name ?: "书籍详情",
                onNavigateBack = onBack
            )

            when (uiState) {
                null -> when {
                    viewModel.isLoading -> ReaderLoadingState(modifier = Modifier.weight(1f), message = "加载中")
                    viewModel.bookInfo != null -> {
                        val info = viewModel.bookInfo!!
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ReaderTheme.spacing.screenPadding)) {
                            Text(info.name, style = ReaderTheme.typography.pageTitle, color = ReaderTheme.colors.controlInk)
                            Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
                            Row {
                                Text("作者：${info.author}", style = ReaderTheme.typography.bookTitle, color = ReaderTheme.colors.controlInk)
                                info.kind?.let { kind ->
                                    Spacer(modifier = Modifier.width(ReaderTheme.spacing.md))
                                    Text("分类：$kind", style = ReaderTheme.typography.bookTitle, color = ReaderTheme.colors.controlInk)
                                }
                            }
                            info.wordCount?.let { wc -> Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs)); Text("字数：$wc", style = ReaderTheme.typography.bookMeta, color = ReaderTheme.colors.bodyText) }
                            info.latestChapter?.let { ch -> Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs)); Text("最新：$ch", style = ReaderTheme.typography.bookMeta, color = ReaderTheme.colors.bodyText) }
                            info.updateTime?.let { time -> Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs)); Text("更新：$time", style = ReaderTheme.typography.bookMeta, color = ReaderTheme.colors.bodyText) }
                            info.intro?.let { intro ->
                                Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
                                ReaderCard { Text(text = intro, style = ReaderTheme.typography.bookMeta, color = ReaderTheme.colors.bodyText) }
                            }
                            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
                            info.tocUrl?.let { tocUrl ->
                                ReaderPrimaryButton(text = "查看目录", onClick = { onTOC(tocUrl) }, modifier = Modifier.fillMaxWidth(), contentDescription = "查看目录")
                            }
                        }
                    }
                }
                is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.weight(1f), message = "加载中")
                is ReaderUiState.Error -> com.reader.android.ui.components.ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.weight(1f))
                else -> {}
            }
        }
    }
}
