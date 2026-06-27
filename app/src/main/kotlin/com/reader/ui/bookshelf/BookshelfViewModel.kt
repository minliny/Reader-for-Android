package com.reader.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookshelfViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadBooks() }

    fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            // Core 协议 v1 未暴露 bookshelf.list / bookshelf.get 命令
            // (rb-bookshelf-protocol-gap)。reader-storage 内部有 BookshelfStore
            // (list_shelf/query_shelf/get_book),但未通过 JSON protocol 暴露;
            // Android 侧也暂无本地书架 Room 表。协议补齐 bookshelf.list 或本地
            // 书架持久化落地前,书架保持空,引导用户去搜索或导入书源。
            _uiState.value = UiState.Empty
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    object Empty : UiState()
    data class Success(val books: List<Book>) : UiState()
    data class Error(val message: String) : UiState()
}
