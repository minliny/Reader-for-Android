package com.reader.ui.reading

import com.reader.api.Book
import com.reader.api.Chapter

sealed class ReadingUiState {
    object Loading : ReadingUiState()
    data class Ready(val book: Book, val chapters: List<Chapter>) : ReadingUiState()
    data class Error(val message: String) : ReadingUiState()
}
