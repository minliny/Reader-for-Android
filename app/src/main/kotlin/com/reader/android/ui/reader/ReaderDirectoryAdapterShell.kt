package com.reader.android.ui.reader

import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode
import com.reader.android.data.bridge.ReaderFailureStage
import com.reader.android.data.model.BookSource

/**
 * Fake/real boundary for Reader directory / TOC.
 *
 * Mode.FAKE (default): uses [ReaderRuntimeFixture] TOC entries.
 * Mode.REAL: calls [CoreBridge.getTOC] through public facade,
 * maps via [CoreTocToReaderTocMapper], joins local state via [ReaderTocLocalStateJoiner].
 */
object ReaderDirectoryAdapterShell {

    private val bridge: CoreBridge by lazy { FakeCoreBridge() }
    private val localState = ReaderTocLocalStateJoiner()

    enum class Mode { FAKE, REAL }

    var mode: Mode = Mode.FAKE
        private set

    // ── Fake mode ──

    fun tocFake(volumeInfo: String = "深空信号 · 共 3 卷 7 章"): ReaderTocBookmarkState =
        ReaderTocBookmarkState(
            entries = ReaderRuntimeFixture.sampleTocEntries,
            volumeInfo = volumeInfo,
            activeTab = "目录"
        )

    // ── Real mode ──

    suspend fun tocReal(
        tocUrl: String,
        volumeInfo: String = "目录",
        source: BookSource = defaultSource()
    ): ReaderTocBookmarkState {
        if (tocUrl.isBlank()) return ReaderTocFacadeErrorMapper.emptyToc()
        if (mode != Mode.REAL) return tocFake(volumeInfo)

        return try {
            val tocItems = bridge.getTOC(tocUrl, source)
            if (tocItems.isEmpty()) return ReaderTocFacadeErrorMapper.emptyToc()

            val entries = CoreTocToReaderTocMapper.map(tocItems)
            val joined = localState.join(entries)

            ReaderTocBookmarkState(
                entries = joined,
                volumeInfo = volumeInfo,
                activeTab = "目录"
            )
        } catch (e: Exception) {
            val error = ReaderError(
                code = ReaderErrorCode.UNKNOWN,
                stage = ReaderFailureStage.TOC,
                message = e.message
            )
            ReaderTocFacadeErrorMapper.mapError(error)
        }
    }

    // ── Integration level ──

    val integrationLevel: String get() = "NEEDS_ADAPTER"
    val isFakeMode: Boolean get() = mode == Mode.FAKE

    fun enableRealMode() { mode = Mode.REAL }
    fun resetToFakeMode() { mode = Mode.FAKE }

    private fun defaultSource() = BookSource(sourceUrl = "", sourceName = "默认来源")
}
