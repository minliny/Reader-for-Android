package com.reader.android.ui.search

import com.reader.android.data.bridge.FakeCoreBridge

/**
 * Fake/real boundary for Search. In fake mode (default), uses fixture data.
 * Real mode is reserved for future data integration — never called in this slice.
 */
object SearchAdapterShell {

    private val bridge: FakeCoreBridge by lazy { FakeCoreBridge() }

    enum class Mode { FAKE, REAL }

    var mode: Mode = Mode.FAKE
        private set

    // ── Public API ──

    fun searchHome(): SearchUiState =
        when (mode) {
            Mode.FAKE -> SearchUiStateMapper.empty("")
            Mode.REAL -> SearchUiStateMapper.empty("") // real hook — not implemented
        }

    fun searchLoading(query: String): SearchUiState =
        SearchUiStateMapper.loading(query)

    fun searchResults(query: String): SearchUiState =
        when (mode) {
            Mode.FAKE -> SearchUiStateMapper.fromFixture(query)
            Mode.REAL -> SearchUiStateMapper.fromFixture(query) // real hook placeholder
        }

    fun searchEmpty(query: String): SearchUiState =
        SearchUiStateMapper.empty(query)

    fun searchError(query: String, message: String): SearchUiState =
        SearchUiStateMapper.error(query, message)

    // ── Integration level accessor ──

    val integrationLevel: String
        get() = "NEEDS_ADAPTER"

    val isFakeMode: Boolean
        get() = mode == Mode.FAKE

    /** Reserved. Do not call in this slice. */
    fun enableRealMode() {
        mode = Mode.REAL
    }
}
