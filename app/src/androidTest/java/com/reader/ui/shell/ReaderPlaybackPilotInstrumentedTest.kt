package com.reader.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.BuildConfig
import com.reader.api.Book
import com.reader.api.BookDetailResult
import com.reader.api.BookTocResult
import com.reader.api.Chapter
import com.reader.ui.reading.ReaderReadingSurface
import com.reader.ui.reading.buildReaderPlaybackPageMeasurement
import com.reader.ui.theme.ReaderTheme
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIRuntime
import io.reader.ui.runtime.ReaderUIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** API-35 staging proof; physical-device TTS proof remains a separate gate. */
@RunWith(AndroidJUnit4::class)
class ReaderPlaybackPilotInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultBuildPromotesPlaybackPairsToPilot() {
        assumeTrue(BuildConfig.READER_UI_PLAYBACK_PILOT_ENABLED)
        assertTrue(BuildConfig.READER_UI_PLAYBACK_PILOT_ENABLED)
    }

    @Test
    fun composeTextLayoutAndMeasuredViewportProduceRealAnchor() {
        val content = (1..80).joinToString("\n") { "第 $it 行真实排版正文，用于验证换行后的字符锚点。" }
        var textLayout: TextLayoutResult? = null
        var viewport: IntSize? = null
        composeRule.setContent {
            ReaderTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { viewport = it.size }
                ) {
                    ReaderReadingSurface(
                        title = "真实布局",
                        content = content,
                        onBodyTextLayout = { textLayout = it }
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) { textLayout != null && (viewport?.height ?: 0) > 0 }
        val measurement = buildReaderPlaybackPageMeasurement(
            correlationId = "compose-page",
            direction = "next",
            content = content,
            contentGeneration = 3,
            chapterIndex = 7,
            committedOffset = 0,
            committedPageIndex = 0,
            textLayout = requireNotNull(textLayout),
            viewport = requireNotNull(viewport),
            fontScale = 1.0
        )
        assertTrue(requireNotNull(measurement).chapterOffset > 0)
        assertEquals(requireNotNull(viewport).width, measurement.viewportWidth)
        assertTrue(measurement.anchor.startsWith("chapter:7:char-offset:"))
    }

    @Test
    fun explicitPairedFlagBypassesNativeReducerAndLegacyHostQueueOnApi35() {
        assumeTrue(BuildConfig.READER_UI_PLAYBACK_PILOT_ENABLED)
        assertTrue(BuildConfig.READER_UI_BOOK_OPEN_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading")),
            bookOpenPilotEnabled = true,
            playbackPilotEnabled = true
        )
        val bookStore = readyBookStore()
        val playbackStore = ReaderPlaybackDomainStore()
        val recording = RecordingPlaybackExecutor(playbackStore, coordinator)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val reducerCalls = AtomicInteger(0)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, _ -> reducerCalls.incrementAndGet(); state },
            readerBookOpenDomainStore = bookStore,
            readerPlaybackDomainStore = playbackStore,
            readerPlaybackEffectExecutor = recording,
            readerPlaybackScope = scope
        )
        try {
            vm.dispatch(ReaderUiIntent.StartTtsSession(text = "legacy", requestId = "api35-tts"))
            assertEquals(1, recording.transitions)
            assertEquals(0, reducerCalls.get())
            assertTrue(vm.state.value.pendingHostRequests.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    private class RecordingPlaybackExecutor(
        store: ReaderPlaybackDomainStore,
        runtime: ReaderPlaybackRuntimeDriver
    ) : ReaderPlaybackEffectExecutor(store, runtime) {
        var transitions: Int = 0
        override fun applyTransition(
            event: String,
            state: ReaderUIState,
            effects: List<ReaderUIEffect>,
            cancelledCorrelationIds: List<String>,
            bookOpen: ReaderBookOpenDomainState,
            scope: CoroutineScope
        ) {
            transitions += 1
        }
    }

    private fun readyBookStore(): ReaderBookOpenDomainStore = ReaderBookOpenDomainStore().apply {
        val correlation = "api35-book"
        begin(
            ReaderContext(
                sourceId = "source-api35",
                bookUrl = "book-api35",
                bookName = "API 35 Book",
                entry = ReaderEntry.COVER_TO_IMMERSIVE,
                entryRequestId = correlation
            )
        )
        recordDetail(
            correlation,
            BookDetailResult(
                "source-api35",
                Book("book-api35", name = "API 35 Book", sourceId = "source-api35"),
                "",
                emptyMap()
            )
        )
        recordToc(
            correlation,
            BookTocResult(
                "source-api35",
                "book-api35",
                listOf(Chapter("Chapter", "https://chapter", 0))
            )
        )
        selectChapter(correlation, 0)
        recordContent(correlation, "真实 Core 正文")
        recordCanonicalLocation(
            correlation,
            ReaderBookOpenCanonicalLocation(
                "book-api35",
                0,
                0,
                0.0,
                "rev-0",
                "reader.location.resolve.v1.reflow",
                "char-offset",
                "char-offset",
                "progress",
                true
            )
        )
        complete(correlation)
    }
}
