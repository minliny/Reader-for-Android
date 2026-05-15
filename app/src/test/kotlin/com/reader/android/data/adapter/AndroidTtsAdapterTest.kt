package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTtsAdapterTest {
    private val adapter = FakeAndroidTtsAdapter()

    @Test
    fun `init returns success`() = runBlocking {
        val result = adapter.init()
        assertTrue(result.success)
    }

    @Test
    fun `isAvailable returns true`() {
        assertTrue(adapter.isAvailable())
    }

    @Test
    fun `speak transitions state`() = runBlocking {
        adapter.init()
        adapter.speak(TtsUtterance("text", "id"))
        assertEquals(TtsPlaybackState.PLAYING, adapter.getState())
    }

    @Test
    fun `TtsInitResult with error`() {
        val result = TtsInitResult(success = false, errorMessage = "TTS not supported")
        assertTrue(!result.success)
        assertEquals("TTS not supported", result.errorMessage)
    }
}
