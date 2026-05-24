package com.reader.android.ui.search

import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode
import com.reader.android.data.bridge.ReaderFailureStage
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.SearchQuery

/**
 * Fake/real boundary for Search.
 *
 * Mode.FAKE (default): fixture data, no CoreBridge call.
 * Mode.REAL: calls [CoreBridge.search] through public facade only.
 * Never touches parser internals, HTTP client, or book source rules directly.
 */
object SearchAdapterShell {

    private val bridge: CoreBridge by lazy { FakeCoreBridge() }

    enum class Mode { FAKE, REAL }

    var mode: Mode = Mode.FAKE
        private set

    // ── Public API ──

    fun searchHome(): SearchUiState =
        SearchUiStateMapper.empty("")

    fun searchLoading(query: String): SearchUiState =
        SearchUiStateMapper.loading(query)

    /** Synchronous result state — used for fixture or pre-fetched results. */
    fun searchResults(query: String): SearchUiState =
        when (mode) {
            Mode.FAKE -> SearchUiStateMapper.fromFixture(query)
            Mode.REAL -> SearchUiStateMapper.empty(query)
        }

    fun searchEmpty(query: String): SearchUiState =
        SearchUiStateMapper.empty(query)

    fun searchError(query: String, message: String): SearchUiState =
        SearchUiStateMapper.error(query, message)

    // ── Real mode: suspend facade call ──

    /** Calls [CoreBridge.search] through public facade. Only active in Mode.REAL. */
    suspend fun searchReal(query: String, source: BookSource = defaultSource()): SearchUiState {
        if (query.isBlank()) return SearchFacadeErrorMapper.emptyKeyword(query)
        if (mode != Mode.REAL) return searchResults(query)

        return try {
            val items = bridge.search(SearchQuery(query), source)
            if (items.isEmpty()) {
                SearchUiStateMapper.empty(query).copy(emptyMessage = "未找到匹配书籍")
            } else {
                SearchUiState(
                    query = query,
                    results = SearchFacadeResultMapper.mapList(items),
                    emptyMessage = "未找到匹配书籍"
                )
            }
        } catch (e: Exception) {
            val error = ReaderError(
                code = ReaderErrorCode.UNKNOWN,
                stage = ReaderFailureStage.SEARCH,
                message = e.message
            )
            SearchFacadeErrorMapper.mapError(query, error)
        }
    }

    // ── Integration level accessor ──

    val integrationLevel: String
        get() = "NEEDS_ADAPTER"

    val isFakeMode: Boolean
        get() = mode == Mode.FAKE

    /** Switches to real mode. CoreBridge.search() must be available. */
    fun enableRealMode() {
        mode = Mode.REAL
    }

    /** Resets to fake mode for testing. */
    fun resetToFakeMode() {
        mode = Mode.FAKE
    }

    private fun defaultSource() = BookSource(
        sourceUrl = "",
        sourceName = "默认书源"
    )
}
