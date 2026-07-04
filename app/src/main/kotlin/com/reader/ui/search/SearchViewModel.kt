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

/**
 * P2 rollback: Search history is DomainState (Core-owned per CONTRACT_FIRST_NATIVE_UI_PLAN
 * §2). Core protocol is missing `search.history.list/add/clear` (Core blocker), so
 * cross-session persistence is NOT available — only session-scoped EphemeralState is
 * kept here. When Core lands `search.history.*`, this session cache should be replaced
 * by Core bridge calls.
 *
 * Search itself goes through Core `book.search` (Core owns source.search DomainState).
 */
class SearchViewModel : ViewModel() {
    private val bookApi = BookApi(ReaderCoreClient.get())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Session-scoped EphemeralState (PLAN §2, Native UI owned). Not persisted across
    // process death. TODO(core-blocker): replace with Core `search.history.*` when landed.
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun updateQuery(q: String) { _query.value = q }

    fun reset() {
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }

    /** Clear session-scoped history. TODO(core-blocker): call Core `search.history.clear`. */
    fun clearHistory() {
        _history.value = emptyList()
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val sources = getSourceIds()
                if (sources.isEmpty()) {
                    _uiState.value = SearchUiState.Idle
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
                // Session-scoped EphemeralState update (dedup, cap 20).
                // TODO(core-blocker): persist via Core `search.history.add` when landed.
                addToSessionHistory(q)
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun addToSessionHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        _history.value = (_history.value.filter { it != trimmed } + trimmed)
            .takeLast(SESSION_HISTORY_MAX)
            .reversed()
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

    companion object {
        private const val SESSION_HISTORY_MAX = 20
    }
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    object Empty : SearchUiState()
    data class Success(val results: List<SearchBook>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
