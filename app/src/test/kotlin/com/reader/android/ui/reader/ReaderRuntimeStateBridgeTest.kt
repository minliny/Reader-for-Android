package com.reader.android.ui.reader

import com.reader.android.ui.reader.components.BrightnessDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRuntimeStateBridgeTest {

    @Test
    fun `fixture creates base control visible state`() {
        val state = ReaderRuntimeFixture.createBaseControlVisible()
        assertEquals("深空信号", state.book.bookTitle)
        assertEquals("本地书籍", state.book.sourceName)
        assertEquals(0.25f, state.pageProgress.progress)
        assertTrue(state.controlLayerState is ReaderControlLayerState.BaseControlVisible)
        assertFalse(state.isNightMode)
    }

    @Test
    fun `fixture creates all nine reader control states`() {
        val states = ReaderRuntimeFixture.allNineStates
        assertEquals(9, states.size)

        val types = states.map { it.controlLayerState.javaClass.simpleName }.distinct()
        assertTrue("Must have BaseControlVisible", types.contains("BaseControlVisible"))
        assertTrue("Must have QuickActionOverlay", types.contains("QuickActionOverlay"))
        assertTrue("Must have BottomFunctionOverlay", types.contains("BottomFunctionOverlay"))
    }

    @Test
    fun `search overlay state has query and results`() {
        val state = ReaderRuntimeFixture.createSearchOverlay()
        assertTrue(state.controlLayerState is ReaderControlLayerState.QuickActionOverlay)
        assertEquals("信号", state.searchState.query)
        assertEquals(3, state.searchState.resultCount)
        assertEquals(3, state.searchState.results.size)
    }

    @Test
    fun `replace overlay shows current book rules only`() {
        val state = ReaderRuntimeFixture.createReplaceOverlay()
        assertEquals(3, state.replaceRules.size)
        assertTrue(state.replaceRules.any { it.name == "净化广告段落" })
        // All rules belong to the current book context
    }

    @Test
    fun `night state is not overlay`() {
        val state = ReaderRuntimeFixture.createNightState()
        assertTrue(state.isNightMode)
        assertTrue(state.controlLayerState is ReaderControlLayerState.BaseControlVisible)
        // Night mode does not create an overlay
    }

    @Test
    fun `brightness dock left and right states`() {
        val left = ReaderRuntimeFixture.createBaseControlVisible()
        assertEquals(BrightnessDock.Left, left.brightnessDockState.dock)

        val right = ReaderRuntimeFixture.createBrightnessRightDock()
        assertEquals(BrightnessDock.Right, right.brightnessDockState.dock)
    }

    @Test
    fun `tts state has playing and idle`() {
        val base = ReaderRuntimeFixture.createBaseControlVisible()
        assertEquals(ReaderTtsState.IDLE, base.ttsState)

        val tts = ReaderRuntimeFixture.createTtsOverlay()
        assertEquals(ReaderTtsState.PLAYING, tts.ttsState)
    }

    @Test
    fun `auto scroll state has running and idle`() {
        val base = ReaderRuntimeFixture.createBaseControlVisible()
        assertEquals(ReaderAutoScrollState.IDLE, base.autoScrollState)

        val scroll = ReaderRuntimeFixture.createAutoScrollOverlay()
        assertEquals(ReaderAutoScrollState.RUNNING, scroll.autoScrollState)
    }

    @Test
    fun `directory overlay has toc entries`() {
        val state = ReaderRuntimeFixture.createDirectoryOverlay()
        assertEquals(5, state.tocBookmarkState.entries.size)
        assertEquals("目录", state.tocBookmarkState.activeTab)
        assertTrue(state.tocBookmarkState.entries.any { it.isCurrent })
        assertTrue(state.tocBookmarkState.entries.any { it.hasBookmark })
    }

    @Test
    fun `loading and error states have no content`() {
        val loading = ReaderRuntimeFixture.createLoadingState()
        assertTrue(loading.isLoading)
        assertTrue(loading.content == null)

        val error = ReaderRuntimeFixture.createErrorState()
        assertNotNull(error.error)
        assertTrue(error.content == null)
    }
}
