package com.reader.android.ui.reader

import com.reader.android.data.adapter.ComposeReaderShellHost
import com.reader.android.data.adapter.ReaderContentType
import com.reader.android.data.adapter.ReaderShellHost
import com.reader.android.data.adapter.RenderRequest
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
 * Mode.REAL: calls [CoreBridge.getContent] through the public facade, then
 *   routes the cleaned content through [readerShellHost] (clean-room
 *   pagination + HTML sanitization, no Readium) before mapping to the UI model.
 */
object ContentAdapterShell {

    private val bridge: CoreBridge by lazy { FakeCoreBridge() }
    private val readerShellHost: ReaderShellHost = ComposeReaderShellHost()

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

            // Route through the clean-room reader-shell host: paginate TXT and
            // sanitize HTML (CSP-locked, JS off) before handing to the UI.
            val rendered = readerShellHost.render(
                RenderRequest(
                    content = page.content,
                    contentType = detectContentType(page.content),
                    title = page.title
                )
            )
            val shellText = rendered.paginatedText.joinToString("\n\n")

            val content = ContentFacadeResultMapper.applyChapterContext(
                ContentFacadeResultMapper.mapContent(page).copy(text = shellText),
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

    private fun detectContentType(content: String): ReaderContentType =
        if (content.contains(Regex("<(p|div|span|article|section)\\b"))) ReaderContentType.HTML
        else ReaderContentType.TXT

    // ── Integration level ──

    val integrationLevel: String get() = "READY_HOST_SHELL"
    val isFakeMode: Boolean get() = mode == Mode.FAKE

    fun enableRealMode() { mode = Mode.REAL }
    fun resetToFakeMode() { mode = Mode.FAKE }

    private fun defaultSource() = BookSource(sourceUrl = "", sourceName = "默认来源")
}
