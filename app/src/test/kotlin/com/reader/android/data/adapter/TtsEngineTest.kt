package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsEngineTest {
    private val engine = FakeTtsEngine()

    @Test
    fun `initial state is IDLE`() {
        assertEquals(TtsPlaybackState.IDLE, engine.getState())
    }

    @Test
    fun `speak transitions to PLAYING`() = runBlocking {
        engine.speak(TtsUtterance("Hello", "1"))
        assertEquals(TtsPlaybackState.PLAYING, engine.getState())
    }

    @Test
    fun `pause transitions to PAUSED`() = runBlocking {
        engine.speak(TtsUtterance("text", "1"))
        engine.pause()
        assertEquals(TtsPlaybackState.PAUSED, engine.getState())
    }

    @Test
    fun `resume returns to PLAYING`() = runBlocking {
        engine.speak(TtsUtterance("text", "1"))
        engine.pause()
        engine.resume()
        assertEquals(TtsPlaybackState.PLAYING, engine.getState())
    }

    @Test
    fun `stop transitions to STOPPED`() = runBlocking {
        engine.speak(TtsUtterance("text", "1"))
        engine.stop()
        assertEquals(TtsPlaybackState.STOPPED, engine.getState())
    }

    @Test
    fun `TtsUtterance holds all fields`() {
        val u = TtsUtterance("测试文本", "ch1-0", "zh-CN", 1.2f, 0.9f)
        assertEquals("测试文本", u.text)
        assertEquals("ch1-0", u.utteranceId)
        assertEquals(1.2f, u.speechRate)
    }
}
