package com.reader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.BookApi
import com.reader.api.ReaderCoreClient
import com.reader.api.SearchBook
import com.reader.host.HostReply
import com.reader.host.HostRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * P2: Search history is dispatched through the Host capability surface
 * (`search.history.list` / `add` / `clear`) to a Room-backed local fallback
 * until Core lands these protocol methods (PAGE_REFERENCE.md marks
 * `searchHistory` as [C] Core-owned). When Core lands them, the repository
 * backing swaps but the UI dispatch code stays.
 *
 * Search itself goes through Core `book.search` (Core owns source.search DomainState).
 */
class SearchViewModel : ViewModel() {
    private val bookApi = BookApi(ReaderCoreClient.get())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // UI-reactive history list. Backed by Host `search.history.*` dispatch
    // (Room-backed repository) — refreshed on add/clear/init.
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    fun updateQuery(q: String) { _query.value = q }

    fun reset() {
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }

    /** Clears persisted search history via Host `search.history.clear`. */
    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = HostRequest(1L, 1L, "search.history.clear", JSONObject().toString())
                    ReaderCoreClient.get().hostAdapter().dispatch(request)
                } catch (e: Exception) {
                    // ignore — history clear is best-effort
                }
            }
            _history.value = emptyList()
        }
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
                // Persist via Host `search.history.add`, then refresh the
                // UI-reactive list from the repository.
                addToHistory(q)
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun addToHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val params = JSONObject().put("keyword", trimmed)
                    val request = HostRequest(1L, 1L, "search.history.add", params.toString())
                    ReaderCoreClient.get().hostAdapter().dispatch(request)
                } catch (e: Exception) {
                    // ignore — history add is best-effort
                }
            }
            loadHistory()
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = HostRequest(1L, 1L, "search.history.list", JSONObject().toString())
                    val reply = ReaderCoreClient.get().hostAdapter().dispatch(request)
                    if (reply?.isComplete() == true) {
                        val result = JSONObject((reply as HostReply.Complete).resultJson())
                        val arr = result.optJSONArray("keywords") ?: return@withContext
                        _history.value = (0 until arr.length()).map { arr.getString(it) }
                    }
                } catch (e: Exception) {
                    // ignore — fall back to empty history
                }
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
