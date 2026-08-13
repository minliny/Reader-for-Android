package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingAloudDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes aloud controls and system voices`() {
        val state = ReadingAloudMapper.fromFixture()

        assertEquals(ReadingAloudDisplayState.Default, state.displayState)
        assertEquals("朗读", state.panel.title)
        assertEquals("tts", state.moduleNav.single { it.active }.type)
        assertEquals("雨，下了一整夜。", state.currentSentence.text)
        assertEquals("第 1 / 48 句", state.currentSentence.progressLabel)
        assertEquals("1.0x", state.controls.speed.value)
        assertEquals("清晰女声", state.controls.voice.value)
        assertEquals("当前章节", state.controls.range.value)
        assertEquals("15 分钟", state.controls.timer.value)
        assertEquals("清晰女声", state.activeVoice.label)
        assertTrue(state.voices.all { "系统 TTS" in it.meta })
        state.voices.forEach { voice ->
            assertFalse("Voice labels must not expose member-only choices", "会员" in voice.label || "会员" in voice.meta)
            assertFalse("Voice labels must not expose online voice entry", "在线" in voice.label || "在线" in voice.meta)
        }
    }

    @Test
    fun `running and paused preserve current sentence and running capsule`() {
        val running = ReadingAloudMapper.running()
        val paused = ReadingAloudMapper.paused()

        assertEquals(ReadingAloudDisplayState.Running, running.displayState)
        assertTrue(running.isRunning)
        assertEquals("正在朗读", running.runningCapsule.title)
        assertEquals("暂停", running.runningCapsule.actionLabel)
        assertEquals(running.currentSentence.text, running.runningCapsule.sentence)

        assertEquals(ReadingAloudDisplayState.Paused, paused.displayState)
        assertTrue(paused.isPaused)
        assertEquals("朗读已暂停", paused.runningCapsule.pausedTitle)
        assertEquals("继续", paused.runningCapsule.continueLabel)
        assertEquals(running.currentSentence.text, paused.currentSentence.text)
        assertEquals(running.bottomReadout, paused.bottomReadout)
    }

    @Test
    fun `error state describes system tts unavailable and preserves context`() {
        val base = ReadingAloudMapper.fromFixture()
        val state = ReadingAloudMapper.error()

        assertEquals(ReadingAloudDisplayState.Error, state.displayState)
        assertEquals("加载失败，请重试", state.error.title)
        assertTrue("系统 TTS 暂不可用" in state.error.message)
        assertTrue("已保留当前阅读位置" in state.error.message)
        assertEquals("重试", state.error.primaryAction)
        assertEquals(base.reading, state.reading)
        assertEquals(base.currentSentence, state.currentSentence)
        assertEquals(base.bottomReadout, state.bottomReadout)
    }
}
