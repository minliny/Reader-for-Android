package com.reader.ui.reading

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.Book
import com.reader.api.BookApi
import com.reader.api.Chapter
import com.reader.api.ReaderCoreClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReadingViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val bookApi = BookApi(ReaderCoreClient.get())

    val sourceId: String = savedStateHandle["sourceId"] ?: ""
    val bookUrl: String = savedStateHandle["bookUrl"] ?: ""
    val bookName: String = savedStateHandle["bookName"] ?: ""

    private val _uiState = MutableStateFlow<ReadingUiState>(ReadingUiState.Loading)
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private var book: Book = Book(bookUrl = bookUrl, name = bookName, origin = sourceId)
    private var chapters: List<Chapter> = emptyList()
    private var currentIndex: Int = 0

    init { loadToc() }

    fun loadToc() {
        viewModelScope.launch {
            _uiState.value = ReadingUiState.Loading
            try {
                // 直接用 Navigation 传入的 sourceId + bookUrl 调 toc
                chapters = bookApi.toc(sourceId, book)
                if (chapters.isEmpty()) {
                    _uiState.value = ReadingUiState.Error("无章节")
                    return@launch
                }
                _uiState.value = ReadingUiState.Ready(book, chapters)
                loadChapter(0)
            } catch (e: Exception) {
                _uiState.value = ReadingUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadChapter(index: Int) {
        if (index !in chapters.indices) return
        currentIndex = index
        viewModelScope.launch {
            _content.value = "加载中..."
            try {
                val text = bookApi.content(sourceId, book, chapters[index])
                _content.value = text
            } catch (e: Exception) {
                _content.value = "加载失败: ${e.message}"
            }
        }
    }

    fun nextChapter() = loadChapter(currentIndex + 1)
    fun prevChapter() = loadChapter(currentIndex - 1)
}

sealed class ReadingUiState {
    object Loading : ReadingUiState()
    data class Ready(val book: Book, val chapters: List<Chapter>) : ReadingUiState()
    data class Error(val message: String) : ReadingUiState()
}
