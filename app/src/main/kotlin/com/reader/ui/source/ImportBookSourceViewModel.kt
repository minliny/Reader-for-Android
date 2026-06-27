package com.reader.ui.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.ReaderCoreClient
import com.reader.api.SourceApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportBookSourceViewModel : ViewModel() {
    private val sourceApi = SourceApi(ReaderCoreClient.get())

    private val _json = MutableStateFlow("")
    val json: StateFlow<String> = _json.asStateFlow()

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun updateJson(s: String) { _json.value = s }

    fun import() {
        val s = _json.value.trim()
        if (s.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading
            try {
                val result = sourceApi.importBookSource(s)
                _uiState.value = if (result.success) ImportUiState.Success
                                 else ImportUiState.Error("Import failed: ${result.data}")
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class ImportUiState {
    object Idle : ImportUiState()
    object Loading : ImportUiState()
    object Success : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}
