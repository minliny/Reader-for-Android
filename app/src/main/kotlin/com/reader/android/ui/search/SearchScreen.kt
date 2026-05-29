package com.reader.android.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.model.SearchResultItem
import com.reader.android.data.network.HttpClient
import com.reader.android.data.network.SearchParser
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderSearchBox
import com.reader.android.ui.components.SearchResultItem as SearchResultItemCard
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme
import kotlinx.coroutines.launch

class SearchViewModel(private val useRealHttp: Boolean = false) {
    private val bridge = FakeCoreBridge()
    private val httpClient = HttpClient()
    private val parser = SearchParser()
    private val defaultSource = BookSource(
        sourceUrl = "https://www.biquge.com",
        sourceName = "笔趣阁",
        searchUrl = "/search?q=key"
    )

    var results by mutableStateOf<List<SearchResultItem>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var hasSearched by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun onQueryChange(newQuery: String) {
        query = newQuery
    }

    suspend fun search() {
        if (query.isBlank()) return
        isSearching = true
        hasSearched = true
        error = null
        try {
            if (useRealHttp) {
                val url = defaultSource.searchUrl?.replace("key", query) ?: return
                val response = httpClient.get(url)
                results = parser.parseSearchResponse(response.body, defaultSource.sourceName)
            } else {
                results = bridge.search(SearchQuery(query), defaultSource)
            }
        } catch (e: Exception) {
            error = e.message ?: "搜索失败"
            results = emptyList()
        }
        isSearching = false
    }
}

@Composable
fun SearchScreen(
    uiState: ReaderUiState? = null,
    onResultClick: ((String) -> Unit)? = null
) {
    val viewModel = remember { SearchViewModel() }
    val scope = rememberCoroutineScope()

    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(title = "搜索")
            ReaderSearchBox(
                query = viewModel.query,
                onQueryChange = { viewModel.onQueryChange(it) },
                modifier = Modifier.padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs)
            )

            when (uiState) {
                null -> when {
                    viewModel.error != null -> {
                        ReaderErrorState(title = "搜索失败", message = viewModel.error, modifier = Modifier.weight(1f), onRetryClick = { scope.launch { viewModel.search() } })
                    }
                    viewModel.isSearching -> {
                        ReaderLoadingState(modifier = Modifier.weight(1f), message = "搜索中")
                    }
                    viewModel.hasSearched && viewModel.results.isEmpty() -> {
                        ReaderEmptyState(title = "未找到结果", message = "请尝试其他关键词", modifier = Modifier.weight(1f))
                    }
                    viewModel.results.isNotEmpty() -> {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(viewModel.results, key = { it.detailUrl ?: it.name }) { item ->
                                SearchResultItemCard(title = item.name, sourceName = item.author, author = item.kind, latestChapter = item.latestChapter, intro = item.intro, onClick = item.detailUrl?.let { url -> { onResultClick?.invoke(url) } })
                            }
                        }
                    }
                    else -> {
                        ReaderEmptyState(title = "搜索书籍", message = "输入关键词开始搜索", modifier = Modifier.weight(1f))
                    }
                }
                is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.weight(1f), message = "搜索中")
                is ReaderUiState.Error -> ReaderErrorState(title = "搜索失败", message = uiState.message, modifier = Modifier.weight(1f), onRetryClick = if (uiState.retryable) {{ scope.launch { viewModel.search() } }} else null)
                is ReaderUiState.Empty -> ReaderEmptyState(title = "未找到结果", message = "请尝试其他关键词", modifier = Modifier.weight(1f))
                is ReaderUiState.Offline -> com.reader.android.ui.components.ReaderOfflineState(modifier = Modifier.weight(1f))
                else -> {}
            }
        }
    }
}
