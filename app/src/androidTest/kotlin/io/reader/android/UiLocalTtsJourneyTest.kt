package io.reader.android

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import io.reader.android.runtime.ReaderTtsPlaybackStatus
import io.reader.android.ui.ReaderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One existing local TXT chapter: prove the Android engine emits onStart before PLAYING. */
@RunWith(AndroidJUnit4::class)
class UiLocalTtsJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Test
    fun existingLocalTxtStartsPausesResumesAndStopsSystemTts() {
        val args = InstrumentationRegistry.getArguments()
        val sourceSha = args.getString("expectedSHA") ?: error("expectedSHA required")
        assertTrue(sourceSha.matches(Regex("[a-f0-9]{64}")))
        val bookId = "local:$sourceSha"
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val model = activity().readerViewModel
        compose.waitUntil(15_000) { model.state.value.ready && !model.state.value.busy && model.state.value.error == null }
        val book = model.state.value.books.single { it.id == bookId }
        assertTrue(book.progressChapter >= 0 && book.progressOffset >= 0)
        scenario!!.onActivity { it.readerViewModel.openBook(book) }
        compose.waitUntil(20_000) {
            model.state.value.route == ReaderRoute.Reading && model.state.value.reading != null && !model.state.value.busy
        }
        Log.i("ReaderTtsProbe", "before click pending=${model.state.value.pendingPosition} error=${model.state.value.error} controls=${model.state.value.controlsVisible}")
        compose.onNodeWithTag("reader_body").performTouchInput { click() }
        compose.waitUntil(5_000) { model.state.value.controlsVisible }
        compose.onNodeWithTag("reader_tts_toggle").performClick()
        Log.i("ReaderTtsProbe", "after click pending=${model.state.value.pendingPosition} status=${model.state.value.ttsPlayback.status} error=${model.state.value.error}")
        compose.waitUntil(25_000) {
            model.state.value.ttsPlayback.status in setOf(ReaderTtsPlaybackStatus.PLAYING, ReaderTtsPlaybackStatus.FAILED)
        }
        assertEquals("System TTS did not reach an actual utterance onStart", ReaderTtsPlaybackStatus.PLAYING,
            model.state.value.ttsPlayback.status)

        compose.onNodeWithTag("reader_tts_toggle").performClick()
        compose.waitUntil(10_000) { model.state.value.ttsPlayback.status == ReaderTtsPlaybackStatus.PAUSED }
        compose.onNodeWithTag("reader_tts_toggle").performClick()
        compose.waitUntil(15_000) { model.state.value.ttsPlayback.status == ReaderTtsPlaybackStatus.PLAYING }
        compose.onNodeWithTag("reader_tts_stop").performClick()
        compose.waitUntil(10_000) { model.state.value.ttsPlayback.status == ReaderTtsPlaybackStatus.STOPPED }
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
        scenario!!.close()
        scenario = null
    }

    private fun activity(): MainActivity {
        var value: MainActivity? = null
        scenario!!.onActivity { value = it }
        return checkNotNull(value)
    }
}
