package com.reader.ui.source

import androidx.lifecycle.ViewModel
import com.reader.api.ReaderCoreClient
import com.reader.api.SourceApi
import com.reader.api.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * P3: Side-effect holder for the book-source import screen.
 *
 * State ownership lives in [com.reader.ui.shell.ReaderUiState.sourceImport]
 * (a 6-state machine: Idle → Parsing → Preview → Importing → Done | Error).
 * The screen reads that state and dispatches
 * [com.reader.ui.shell.ReaderUiIntent] transitions; this ViewModel only owns
 * the JSON input field (EphemeralState per PLAN §2) and the
 * [SourceApi.importBookSource] side effect that bridges to Core
 * `source.import`.
 *
 * The screen triggers the side effect by dispatching `ParseSourceImport(json)`
 * → reducer transitions to `Parsing` → a `LaunchedEffect` in the screen
 * observes `Parsing` and calls [importBookSource], dispatching
 * `CompleteSourceImport` or `FailSourceImport` with the result.
 */
class ImportBookSourceViewModel : ViewModel() {
    private val sourceApi = SourceApi(ReaderCoreClient.get())

    private val _json = MutableStateFlow("")
    val json: StateFlow<String> = _json.asStateFlow()

    fun updateJson(s: String) { _json.value = s }

    /**
     * Calls Core `source.import` with [bookSourceJson]. Returns the structured
     * [ImportResult] so the caller can dispatch the matching reducer intent
     * (`CompleteSourceImport` on success, `FailSourceImport` on failure)
     * without re-throwing.
     *
     * Suspends until Core returns (or times out, per `SourceApi`'s timeout).
     */
    suspend fun importBookSource(bookSourceJson: String): ImportResult {
        val s = bookSourceJson.trim()
        if (s.isEmpty()) return ImportResult(success = false, data = "{\"code\":\"EMPTY\",\"message\":\"empty JSON\"}")
        return try {
            sourceApi.importBookSource(s)
        } catch (e: Exception) {
            ImportResult(success = false, data = "{\"code\":\"EXCEPTION\",\"message\":\"${e.message ?: "unknown"}\"}")
        }
    }
}
