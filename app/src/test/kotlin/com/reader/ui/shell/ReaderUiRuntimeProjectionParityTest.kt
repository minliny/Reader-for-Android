package com.reader.ui.shell

import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shadow/pilot evidence for ReaderUIRuntime 2.4 staging semantics.
 *
 * These tests compare stable projections instead of claiming whole-state
 * equality. In particular, shared runtime `book.open` intentionally stops at
 * `immersive-reading + loading` and emits a result-dependent Core sequence,
 * while the legacy Android reducer still has immediate session/page branches.
 * Those pending/effect mismatches stay explicit until paired Pilot promotion.
 */
class ReaderUiRuntimeProjectionParityTest {

    @Test
    fun `book open payload and correlation are parity while route loading remains explicit migration gap`() {
        val requestId = "open-android-1"
        val native = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book One",
                requestId = requestId
            )
        )
        val shadow = ReaderUiRuntimeShadowAdapter()
        val transition = shadow.dispatch(
            event = "book.open",
            payload = mapOf(
                "sourceId" to "source-1",
                "bookId" to "book-1",
                "sourceKind" to "remote"
            ),
            correlationId = requestId
        )

        val nativeContext = requireNotNull(native.readerContext)
        assertEquals(nativeContext.sourceId, transition.effects.first().payload["sourceId"])
        assertEquals(nativeContext.bookUrl, transition.effects.first().payload["bookId"])
        transition.effects.forEach { effect ->
            assertEquals(ReaderUIEffectKind.CORE, effect.kind)
            assertEquals(requestId, effect.correlationId)
            assertEquals(nativeContext.sourceId, effect.payload["sourceId"])
            assertEquals(nativeContext.bookUrl, effect.payload["bookId"])
            assertEquals("remote", effect.payload["sourceKind"])
        }
        // `book.open` is result-dependent: only the first stage is emitted at
        // dispatch; later stages are released by acceptBookOpenResult().
        assertEquals(listOf("source.detail"), transition.effects.map { it.type })
        assertEquals(1, transition.effects.size)

        // Android still has to consume the runtime's result-dependent sequence;
        // matching the entry route alone is not transaction completion proof.
        assertEquals(RouteIds.IMMERSIVE_READING, native.currentRoute.routeId)
        assertEquals(RouteIds.IMMERSIVE_READING, transition.state.routeId)
        assertTrue(transition.state.loading)
    }

    @Test
    fun `reader directory overlay open close projection matches native reducer`() {
        val nativeOpen = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenSheet(SheetContent.ReaderSetting("directory"))
        )
        val shadow = ReaderUiRuntimeShadowAdapter()
        val runtimeOpen = shadow.dispatch("reader.directory.open")

        assertTrue(nativeOpen.overlayState is OverlayState.Sheet)
        assertEquals(
            "directory",
            (nativeOpen.overlayState as OverlayState.Sheet).content
                .let { it as SheetContent.ReaderSetting }
                .module
        )
        assertEquals("directory", runtimeOpen.state.overlay)

        val nativeClosed = ReaderUiReducer.reduce(nativeOpen, ReaderUiIntent.CloseSheet)
        val runtimeClosed = shadow.dispatch("reader.directory.close")
        assertEquals(OverlayState.None, nativeClosed.overlayState)
        assertNull(runtimeClosed.state.overlay)
    }

    @Test
    fun `session shadow records result-dependent TTS and foreground timer migration gap`() {
        val nativeTts = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(
                text = "Reader runtime parity",
                requestId = "tts-1"
            )
        )
        val shadow = ReaderUiRuntimeShadowAdapter(
            ReaderUIState(routeId = RouteIds.IMMERSIVE_READING)
        )
        val runtimeTts = shadow.dispatch(
            event = "reader.tts.start",
            correlationId = "tts-1"
        )

        assertEquals(SessionType.TTS, nativeTts.activeSession?.type)
        // Native remains immediate in Shadow. Shared Runtime is now pending
        // on typed Core plan/queue results and deliberately has no lossy text
        // HostRequest at this stage.
        assertNull(runtimeTts.state.activeSession)
        assertEquals("awaiting-plan", runtimeTts.state.ttsTransaction?.stage)
        val runtimePlan = runtimeTts.effects.single()
        assertEquals(ReaderUIEffectKind.CORE, runtimePlan.kind)
        assertEquals("tts.queue.plan", runtimePlan.type)
        assertTrue(runtimePlan.payload.isEmpty())
        assertEquals("tts-1", runtimePlan.correlationId)

        val nativeAutoPage = ReaderUiReducer.reduce(nativeTts, ReaderUiIntent.StartAutoPageSession)
        val runtimeAutoPage = shadow.dispatch(
            "reader.autoPage.start",
            payload = mapOf("intervalMs" to READER_AUTO_PAGE_DEFAULT_INTERVAL_MS.toString()),
            correlationId = "auto-1"
        )
        assertEquals(SessionType.AUTO_PAGE, nativeAutoPage.activeSession?.type)
        assertEquals("auto-page", runtimeAutoPage.state.activeSession)
        assertEquals(
            "timer.foreground.arm",
            runtimeAutoPage.effects.single { it.kind == ReaderUIEffectKind.HOST }.type
        )
    }
}
