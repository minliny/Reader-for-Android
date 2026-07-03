package com.reader.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bookshelf data source.
 *
 * Core protocol v1 does not yet expose `bookshelf.list` (rb-bookshelf-protocol-gap), so the
 * Android side has no real local shelf table yet. Real bookshelf data is deferred until the Core
 * protocol exposes `bookshelf.list` or local Room persistence lands. Slice demos can opt in to a
 * local `fixture://` book through [seedFixtureBook], but production/default construction must
 * stay empty so demo data is never mistaken for a user's shelf.
 */
class BookshelfViewModel(
    private val seedFixtureBook: Boolean = false
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _continueReading = MutableStateFlow<Book?>(null)
    val continueReading: StateFlow<Book?> = _continueReading.asStateFlow()

    private val _chromeState = MutableStateFlow(BookshelfChromeState())
    val chromeState: StateFlow<BookshelfChromeState> = _chromeState.asStateFlow()

    init { loadBooks() }

    fun setViewMode(mode: BookshelfViewMode) {
        _chromeState.update { it.copy(viewMode = mode) }
    }

    fun setMoreMenuOpen(open: Boolean) {
        _chromeState.update { it.copy(isMoreMenuOpen = open) }
    }

    fun toggleFilter() {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(isOpen = !state.filter.isOpen))
        }
    }

    fun setFilterGroup(group: String) {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(group = group))
        }
    }

    fun setFilterSort(sort: String) {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(sort = sort))
        }
    }

    fun setFilterValue(filter: String) {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(filter = filter))
        }
    }

    fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            if (!seedFixtureBook) {
                _continueReading.value = null
                _uiState.value = UiState.Empty
                return@launch
            }

            // Minimal opt-in Slice 2 fixture for demo/test surfaces only.
            val fixture = Book(
                bookUrl = "fixture://book/demo",
                name = "示例书籍",
                author = "Reader Demo",
                origin = "fixture://",
                intro = "用于演示书架封面到沉浸阅读的入口。"
            )
            _continueReading.value = fixture
            _uiState.value = UiState.Success(listOf(fixture))
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    object Empty : UiState()
    data class Success(val books: List<Book>) : UiState()
    data class Error(val message: String) : UiState()
}

enum class BookshelfViewMode { COVER, LIST }

data class BookshelfChromeState(
    val viewMode: BookshelfViewMode = BookshelfViewMode.COVER,
    val isMoreMenuOpen: Boolean = false,
    val filter: BookshelfFilterState = BookshelfFilterState()
)

data class BookshelfFilterState(
    val group: String = "全部",
    val sort: String = "最近更新",
    val filter: String = "全部",
    val isOpen: Boolean = false
) {
    val isActive: Boolean
        get() = isOpen || group != "全部" || sort != "最近更新" || filter != "全部"
}
