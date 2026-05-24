package com.reader.android.ui.reader

import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode
import com.reader.android.data.bridge.ReaderFailureStage
import com.reader.android.data.model.BookSource

/**
 * Fake/real boundary for Reader content loading.
 *
 * Mode.FAKE (default): returns fixture content.
 * Mode.REAL: calls [CoreBridge.getContent] through public facade.
 */
object ContentAdapterShell {

    private val bridge: CoreBridge by lazy { FakeCoreBridge() }

    enum class Mode { FAKE, REAL }

    var mode: Mode = Mode.FAKE
        private set

    /**
     * Result of content loading via adapter shell.
     */
    sealed class ContentResult {
        data class Ready(
            val content: ReaderContentUiModel,
            val chapter: ReaderChapterUiModel
        ) : ContentResult()

        data class Error(val error: ContentErrorState) : ContentResult()
        data object Loading : ContentResult()
    }

    // ── Fake mode ──

    fun contentFake(): ContentResult = ContentResult.Ready(
        content = ReaderRuntimeFixture.sampleContent,
        chapter = ReaderRuntimeFixture.sampleChapter
    )

    // ── Real mode ──

    suspend fun contentReal(
        request: ContentRequest,
        source: BookSource = defaultSource()
    ): ContentResult {
        if (!request.isValid) return ContentResult.Error(ContentFacadeErrorMapper.missingUrl())
        if (mode != Mode.REAL) return contentFake()

        return try {
            val page = bridge.getContent(request.chapterUrl, source)
            if (page.content.isBlank()) return ContentResult.Error(ContentFacadeErrorMapper.emptyContent())

            val content = ContentFacadeResultMapper.applyChapterContext(
                ContentFacadeResultMapper.mapContent(page),
                request
            )
            val chapter = ContentFacadeResultMapper.mapChapter(page, request)
            ContentResult.Ready(content, chapter)
        } catch (e: Exception) {
            val error = ReaderError(
                code = ReaderErrorCode.UNKNOWN,
                stage = ReaderFailureStage.CONTENT,
                message = e.message
            )
            ContentResult.Error(ContentFacadeErrorMapper.mapError(error))
        }
    }

    // ── Integration level ──

    val integrationLevel: String get() = "NEEDS_ADAPTER"
    val isFakeMode: Boolean get() = mode == Mode.FAKE

    fun enableRealMode() { mode = Mode.REAL }
    fun resetToFakeMode() { mode = Mode.FAKE }

    private fun defaultSource() = BookSource(sourceUrl = "", sourceName = "默认来源")
}
