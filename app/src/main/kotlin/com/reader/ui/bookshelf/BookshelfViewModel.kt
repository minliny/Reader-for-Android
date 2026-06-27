package com.reader.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.Book
import com.reader.api.ReaderCoreClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class BookshelfViewModel : ViewModel() {
    private val client = ReaderCoreClient.get()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadBooks() }

    fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // 尝试通过 Core 拉本地书架列表
                // 注意:bookshelf.list 可能不被 Core 支持,异常时回退到 Empty
                val result = client.sendAndAwait("bookshelf.list", JSONObject(), 10_000)
                val books = parseBooks(result)
                _uiState.value = if (books.isEmpty()) UiState.Empty
                                 else UiState.Success(books)
            } catch (e: Exception) {
                // bookshelf.list 不支持或 Core 未就绪 → 空书架
                // 引导用户去搜索或导入书源
                _uiState.value = UiState.Empty
            }
        }
    }

    private fun parseBooks(data: JSONObject): List<Book> {
        val arr = data.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            Book(
                bookUrl = b.optString("bookUrl"),
                name = b.optString("name"),
                author = b.optString("author"),
                coverUrl = b.optString("coverUrl"),
                intro = b.optString("intro"),
                origin = b.optString("origin")
            )
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    object Empty : UiState()
    data class Success(val books: List<Book>) : UiState()
    data class Error(val message: String) : UiState()
}
