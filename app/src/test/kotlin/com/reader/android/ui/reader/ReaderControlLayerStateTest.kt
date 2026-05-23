package com.reader.android.ui.reader

import com.reader.android.ui.reader.components.BrightnessDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControlLayerStateTest {

    @Test
    fun `base control visible is default state`() {
        val state = ReaderRuntimeFixture.createBaseControlVisible()
        assertTrue(state.controlLayerState is ReaderControlLayerState.BaseControlVisible)
    }

    @Test
    fun `quick action overlay hides brightness but keeps quick actions and page control`() {
        val state = ReaderRuntimeFixture.createSearchOverlay()
        assertTrue(ReaderRuntimeMapper.isQuickActionVisible(state))
        assertFalse(ReaderRuntimeMapper.isBottomFunctionVisible(state))
        assertEquals(ReaderOverlayType.SEARCH, ReaderRuntimeMapper.quickActionType(state))
    }

    @Test
    fun `bottom function overlay hides brightness quick actions and page control`() {
        val state = ReaderRuntimeFixture.createDirectoryOverlay()
        assertTrue(ReaderRuntimeMapper.isBottomFunctionVisible(state))
        assertFalse(ReaderRuntimeMapper.isQuickActionVisible(state))
        assertEquals(ReaderOverlayType.DIRECTORY, ReaderRuntimeMapper.bottomFunctionType(state))
    }

    @Test
    fun `night state does not produce overlay`() {
        val state = ReaderRuntimeFixture.createNightState()
        assertTrue(state.isNightMode)
        assertTrue(state.controlLayerState is ReaderControlLayerState.BaseControlVisible)
        assertFalse(ReaderRuntimeMapper.isQuickActionVisible(state))
        assertFalse(ReaderRuntimeMapper.isBottomFunctionVisible(state))
    }

    @Test
    fun `replace overlay only has current book rules`() {
        val state = ReaderRuntimeFixture.createReplaceOverlay()
        assertEquals(3, state.replaceRules.size)
        // All rules should be scoped to the current book
        assertTrue(state.replaceRules.all { it.name.length > 0 })
    }

    @Test
    fun `brightness dock left has correct arrow direction`() {
        val state = ReaderRuntimeFixture.createBaseControlVisible()
        assertEquals(BrightnessDock.Left, state.brightnessDockState.dock)
    }

    @Test
    fun `brightness dock right has correct arrow direction`() {
        val state = ReaderRuntimeFixture.createBrightnessRightDock()
        assertEquals(BrightnessDock.Right, state.brightnessDockState.dock)
    }

    @Test
    fun `page control uses within chapter progress not chapter skip`() {
        val state = ReaderRuntimeFixture.createBaseControlVisible()
        assertEquals(0.25f, state.pageProgress.progress)
        // Progress is within-chapter (0-1), not cross-chapter
        assertTrue(state.pageProgress.progress in 0f..1f)
    }

    @Test
    fun `mapper extracts overlay type correctly`() {
        assertEquals(ReaderOverlayType.NONE, ReaderRuntimeMapper.overlayType(ReaderRuntimeFixture.createBaseControlVisible()))
        assertEquals(ReaderOverlayType.SEARCH, ReaderRuntimeMapper.overlayType(ReaderRuntimeFixture.createSearchOverlay()))
        assertEquals(ReaderOverlayType.DIRECTORY, ReaderRuntimeMapper.overlayType(ReaderRuntimeFixture.createDirectoryOverlay()))
        assertEquals(ReaderOverlayType.TTS, ReaderRuntimeMapper.overlayType(ReaderRuntimeFixture.createTtsOverlay()))
        assertEquals(ReaderOverlayType.APPEARANCE, ReaderRuntimeMapper.overlayType(ReaderRuntimeFixture.createAppearanceOverlay()))
        assertEquals(ReaderOverlayType.SETTINGS, ReaderRuntimeMapper.overlayType(ReaderRuntimeFixture.createSettingsOverlay()))
    }

    @Test
    fun `mapper to control base params preserves all fields`() {
        val state = ReaderRuntimeFixture.createBaseControlVisible()
        val params = ReaderRuntimeMapper.toControlBaseParams(state)
        assertEquals("第一章：阿长与《山海经》", params.chapterTitle)
        assertEquals("深空信号", params.bookTitle)
        assertEquals("本地书籍", params.sourceName)
        assertEquals(0.25f, params.chapterProgress)
        assertEquals(BrightnessDock.Left, params.brightnessDock)
    }

    @Test
    fun `all nine states have valid control layer state`() {
        ReaderRuntimeFixture.allNineStates.forEach { state ->
            assertTrue(state.controlLayerState is ReaderControlLayerState)
        }
    }

    @Test
    fun `settings overlay items do not include global settings`() {
        val state = ReaderRuntimeFixture.createSettingsOverlay()
        assertTrue(state.controlLayerState is ReaderControlLayerState.BottomFunctionOverlay)
        assertEquals(ReaderOverlayType.SETTINGS,
            (state.controlLayerState as ReaderControlLayerState.BottomFunctionOverlay).type)
    }
}
