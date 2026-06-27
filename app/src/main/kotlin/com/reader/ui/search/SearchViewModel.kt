package com.reader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.BookApi
import com.reader.api.ReaderCoreClient
import com.reader.api.SearchBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class SearchViewModel : ViewModel() {
    private val bookApi = BookApi(ReaderCoreClient.get())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun updateQuery(q: String) { _query.value = q }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val sources = getSourceIds()
                if (sources.isEmpty()) {
                    _uiState.value = SearchUiState.Error("请先导入书源(右上角 +)")
                    return@launch
                }
                val results = mutableListOf<SearchBook>()
                for (sourceId in sources) {
                    try {
                        results += bookApi.search(sourceId, q, page = 1)
                    } catch (e: Exception) {
                        // 单源失败不影响其他
                    }
                }
                _uiState.value = if (results.isEmpty()) SearchUiState.Empty
                                 else SearchUiState.Success(results)
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun getSourceIds(): List<String> {
        // 尝试通过 Core 获取已导入书源 ID 列表
        return try {
            val result = ReaderCoreClient.get().sendAndAwait("source.list", JSONObject(), 10_000)
            val arr = result.optJSONArray("sources") ?: return emptyList()
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).optString("id")
            }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            // source.list 不支持或 Core 未就绪
            emptyList()
        }
    }
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    object Empty : SearchUiState()
    data class Success(val results: List<SearchBook>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
