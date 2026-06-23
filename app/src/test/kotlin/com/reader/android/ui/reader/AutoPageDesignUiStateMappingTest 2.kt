package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPageDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes speed modes options and start action`() {
        val state = AutoPageMapper.fromFixture()

        assertEquals(AutoPageDisplayState.Default, state.displayState)
        assertEquals("自动翻页", state.topControl.title)
        assertEquals("长夜余火 · 第 32 章", state.topControl.sourceLine)
        assertEquals("翻页速度", state.speed.title)
        assertEquals("每 8 秒一页", state.speed.valueLabel)
        assertEquals(0.42f, state.speed.value)
        assertEquals("慢", state.speed.slowLabel)
        assertEquals("快", state.speed.fastLabel)
        assertEquals("翻页", state.activeMode.label)
        assertEquals(listOf("滚动", "翻页", "仿真翻页"), state.modes.map { it.label })
        assertEquals(listOf("屏幕常亮", "到章末停止", "音量键调速"), state.options.map { it.title })
        assertEquals(listOf(true, false, false), state.options.map { it.enabled })
        assertEquals("开始自动翻页", state.actions.startLabel)
        assertEquals("取消", state.actions.cancelLabel)
        assertEquals("停止", state.actions.stopLabel)
    }

    @Test
    fun `running and paused states keep separate pause continue and stop actions`() {
        val running = AutoPageMapper.running()
        val paused = AutoPageMapper.paused()

        assertEquals(AutoPageDisplayState.Running, running.displayState)
        assertTrue(running.isRunning)
        assertEquals("正在自动翻页", running.runningCapsule.title)
        assertEquals("暂停", running.runningCapsule.actionLabel)
        assertEquals("停止", running.runningCapsule.stopLabel)
        assertEquals("每 8 秒一页 · 翻页模式", running.runningCapsule.sentence)

        assertEquals(AutoPageDisplayState.Paused, paused.displayState)
        assertTrue(paused.isPaused)
        assertEquals("自动翻页已暂停", paused.runningCapsule.pausedTitle)
        assertEquals("继续", paused.runningCapsule.continueLabel)
        assertEquals(running.runningCapsule.stopLabel, paused.runningCapsule.stopLabel)
        assertEquals(running.speed, paused.speed)
        assertEquals(running.reading, paused.reading)
    }

    @Test
    fun `error state stops auto page and preserves reading position`() {
        val base = AutoPageMapper.fromFixture()
        val state = AutoPageMapper.error()

        assertEquals(AutoPageDisplayState.Error, state.displayState)
        assertEquals("操作失败，请重试", state.error.title)
        assertTrue("当前章节暂时无法继续翻页" in state.error.message)
        assertTrue("已停止自动翻页并保留阅读位置" in state.error.message)
        assertEquals("重试", state.error.primaryAction)
        assertEquals(base.status, state.status)
        assertEquals(base.topControl, state.topControl)
        assertEquals(base.reading, state.reading)
        assertFalse("Error state must not silently keep running", state.isRunning)
    }
}
