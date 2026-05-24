package com.reader.android.ui.detail

import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode
import com.reader.android.data.bridge.ReaderFailureStage
import com.reader.android.data.model.BookSource

/**
 * Fake/real boundary for BookDetail.
 *
 * Mode.FAKE (default): fixture data, no CoreBridge call.
 * Mode.REAL: calls [CoreBridge.getBookInfo] + [CoreBridge.getTOC]
 * through public facade only.
 */
object BookDetailAdapterShell {

    private val bridge: CoreBridge by lazy { FakeCoreBridge() }

    enum class Mode { FAKE, REAL }

    var mode: Mode = Mode.FAKE
        private set

    // ── Public API ──

    fun detailLoading(): BookDetailUiState =
        BookDetailUiStateMapper.loading()

    fun detailReady(detail: BookDetailUiModel = BookDetailFixture.detail): BookDetailUiState =
        when (mode) {
            Mode.FAKE -> BookDetailUiStateMapper.fromFixture(detail)
            Mode.REAL -> BookDetailUiStateMapper.fromFixture(detail)
        }

    fun detailError(message: String): BookDetailUiState =
        BookDetailUiStateMapper.error(message)

    fun detailEmpty(): BookDetailUiState =
        BookDetailUiStateMapper.empty()

    // ── Real mode: suspend facade call ──

    /** Calls [CoreBridge.getBookInfo] + [CoreBridge.getTOC] through public facade. */
    suspend fun detailReal(
        detailUrl: String,
        source: BookSource = defaultSource()
    ): BookDetailUiState {
        if (detailUrl.isBlank()) return BookDetailFacadeErrorMapper.emptyDetailUrl()
        if (mode != Mode.REAL) return detailReady()

        return try {
            val bookInfo = bridge.getBookInfo(detailUrl, source)
            val tocItems = try {
                bookInfo.tocUrl?.let { bridge.getTOC(it, source) } ?: emptyList()
            } catch (_: Exception) {
                emptyList() // TOC failure is non-fatal — detail still shows
            }
            val model = BookDetailFacadeResultMapper.map(bookInfo, detailUrl, tocItems)
            BookDetailUiState(detail = model)
        } catch (e: Exception) {
            val error = ReaderError(
                code = ReaderErrorCode.UNKNOWN,
                stage = ReaderFailureStage.BOOK_INFO,
                message = e.message
            )
            BookDetailFacadeErrorMapper.mapError(error)
        }
    }

    // ── Integration level accessor ──

    val integrationLevel: String
        get() = "NEEDS_ADAPTER"

    val isFakeMode: Boolean
        get() = mode == Mode.FAKE

    fun enableRealMode() {
        mode = Mode.REAL
    }

    fun resetToFakeMode() {
        mode = Mode.FAKE
    }

    private fun defaultSource() = BookSource(
        sourceUrl = "",
        sourceName = "默认书源"
    )
}
