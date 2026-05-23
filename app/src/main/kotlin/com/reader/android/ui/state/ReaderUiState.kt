package com.reader.android.ui.state

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data object Empty : ReaderUiState
    data class Error(val message: String, val retryable: Boolean = true) : ReaderUiState
    data object Offline : ReaderUiState
    data class Disabled(val reason: String) : ReaderUiState
    data class PermissionRequired(val permission: String) : ReaderUiState
    data class LocalFileError(val message: String) : ReaderUiState
    data class NetworkSourceError(val sourceId: String, val message: String) : ReaderUiState
    data object WebDavAuthError : ReaderUiState
    data class SyncConflict(val localVersion: String, val remoteVersion: String) : ReaderUiState
    data class ImportSuccess(val targetId: String) : ReaderUiState
    data class ImportFailure(val message: String) : ReaderUiState
}
