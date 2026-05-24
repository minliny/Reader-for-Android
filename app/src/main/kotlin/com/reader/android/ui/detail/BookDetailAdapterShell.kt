package com.reader.android.ui.detail

import com.reader.android.data.bridge.FakeCoreBridge

/**
 * Fake/real boundary for BookDetail. In fake mode (default), uses fixture data.
 * Real mode is reserved for future data integration — never called in this slice.
 */
object BookDetailAdapterShell {

    private val bridge: FakeCoreBridge by lazy { FakeCoreBridge() }

    enum class Mode { FAKE, REAL }

    var mode: Mode = Mode.FAKE
        private set

    // ── Public API ──

    fun detailLoading(): BookDetailUiState =
        BookDetailUiStateMapper.loading()

    fun detailReady(detail: BookDetailUiModel = BookDetailFixture.detail): BookDetailUiState =
        when (mode) {
            Mode.FAKE -> BookDetailUiStateMapper.fromFixture(detail)
            Mode.REAL -> BookDetailUiStateMapper.fromFixture(detail) // real hook placeholder
        }

    fun detailError(message: String): BookDetailUiState =
        BookDetailUiStateMapper.error(message)

    fun detailEmpty(): BookDetailUiState =
        BookDetailUiStateMapper.empty()

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
