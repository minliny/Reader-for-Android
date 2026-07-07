package com.reader.ui.shell

/**
 * Slice D — Result of a completed [HostRequestDispatch].
 *
 * Written to [ReaderUiState.lastHostRequestResult] when the UI layer reports
 * a [ReaderUiIntent.HostRequestComplete] or [ReaderUiIntent.HostRequestError].
 * The UI can observe this field to show toasts / snackbar feedback.
 */
data class HostRequestResult(
    val dispatchId: String,
    val capability: String,
    val success: Boolean,
    val resultJson: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
)
