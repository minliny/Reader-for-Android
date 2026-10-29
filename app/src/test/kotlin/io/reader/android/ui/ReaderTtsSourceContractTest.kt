package io.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source contract for the first local-only TTS UI entry; no Compose/device is started. */
class ReaderTtsSourceContractTest {
    private fun source(path: String): String {
        val candidates = listOf(File("src/main/kotlin/$path"), File("app/src/main/kotlin/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("source not found: $path")
    }

    @Test
    fun localReadingScreenExposesBoundTtsControlsAndState() {
        val screen = source("io/reader/android/ui/ReaderReadingScreen.kt")
        val model = source("io/reader/android/ui/ReaderViewModel.kt")
        assertTrue("reader_tts_toggle" in screen)
        assertTrue("reader_tts_stop" in screen)
        assertTrue("reader_tts_status" in screen)
        assertTrue("ReaderTextPresentationKind.LOCAL_DOCUMENT" in screen)
        assertTrue("ttsPlayback" in model)
        assertTrue("fun toggleTts" in model)
        assertTrue("fun stopTts" in model)
        assertTrue("AndroidTextToSpeechEngine" in model)
    }
}
