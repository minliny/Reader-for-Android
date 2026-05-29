package com.reader.android.ui.toc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.TOCItem
import com.reader.android.data.network.HttpClient
import com.reader.android.data.network.TOCParser
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.theme.ReaderTheme

class TOCViewModel(private val useRealHttp: Boolean = false) {
    private val bridge = FakeCoreBridge()
    private val httpClient = HttpClient()
    private val parser = TOCParser()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var chapters by mutableStateOf<List<TOCItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load(tocUrl: String) {
        isLoading = true
        error = null
        try {
            if (useRealHttp) {
                val response = httpClient.get(tocUrl)
                chapters = parser.parseTOCResponse(response.body)
            } else {
                chapters = bridge.getTOC(tocUrl, source)
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        isLoading = false
    }
}

@Composable
fun TOCScreen(tocUrl: String, onBack: () -> Unit, onChapterClick: (String, String) -> Unit) {
    val viewModel = remember { TOCViewModel() }

    LaunchedEffect(tocUrl) {
        viewModel.load(tocUrl)
    }

    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "目录",
                onNavigateBack = onBack
            )

            when {
                viewModel.error != null -> {
                    ReaderErrorState(
                        title = "加载失败",
                        message = viewModel.error,
                        modifier = Modifier.weight(1f),
                        onRetryClick = { /* retry deferred */ }
                    )
                }
                viewModel.isLoading -> {
                    ReaderLoadingState(
                        modifier = Modifier.weight(1f),
                        message = "加载中"
                    )
                }
                viewModel.chapters.isEmpty() -> {
                    ReaderEmptyState(
                        title = "暂无章节",
                        message = "目录内容为空",
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(viewModel.chapters) { item ->
                            ChapterItem(
                                item = item,
                                depth = 0,
                                onChapterClick = onChapterClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    item: TOCItem,
    depth: Int,
    onChapterClick: (String, String) -> Unit
) {
    if (item.url.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChapterClick(item.url, item.title) }
                .padding(
                    start = ReaderTheme.spacing.screenPadding + (depth * 20).dp,
                    end = ReaderTheme.spacing.screenPadding,
                    top = ReaderTheme.spacing.sm,
                    bottom = ReaderTheme.spacing.sm
                )
        ) {
            Text(
                text = item.title,
                style = if (depth == 0) ReaderTheme.typography.bookTitle
                        else ReaderTheme.typography.bookMeta,
                color = ReaderTheme.colors.controlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        // Volume header (no url)
        Text(
            text = item.title,
            style = ReaderTheme.typography.sectionTitle,
            color = ReaderTheme.colors.primary,
            modifier = Modifier.padding(
                start = ReaderTheme.spacing.screenPadding + (depth * 20).dp,
                top = ReaderTheme.spacing.md,
                bottom = ReaderTheme.spacing.xs
            )
        )
    }

    item.children.forEach { child ->
        ChapterItem(
            item = child,
            depth = depth + 1,
            onChapterClick = onChapterClick
        )
    }

    if (depth == 0) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = ReaderTheme.spacing.screenPadding),
            color = ReaderTheme.colors.controlBorder
        )
    }
}
