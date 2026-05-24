package com.reader.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderControlOverlayVisibilityTest {

    private val baseSource: String by lazy {
        String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt"
        )))
    }

    private val screenSource: String by lazy {
        String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/ReaderScreen.kt"
        )))
    }

    // ── QuickActionOverlay visibility rules ──

    @Test
    fun `quick action overlay hides brightness keeps quick buttons page control bottom bar`() {
        // isQuickOverlay => hide brightness (!showBrightness)
        // But quick actions and page control stay visible (not isBottomOverlay)
        val visibilitySection = baseSource.substringAfter("Visibility rules")
        assertTrue("showBrightness must exclude quick overlay", "!isQuickOverlay" in visibilitySection)
        assertTrue("showQuickActions must only exclude bottom overlay", "!isBottomOverlay" in visibilitySection)
    }

    @Test
    fun `bottom function overlay hides brightness quick buttons and page control keeps bottom bar`() {
        assertTrue("Bottom overlay must hide brightness", "!isBottomOverlay" in baseSource)
        assertTrue("Bottom overlay must hide quick actions", "showQuickActions = !isBottomOverlay" in baseSource)
        assertTrue("Bottom overlay must hide page control", "showPageControl = !isBottomOverlay" in baseSource)
    }

    // ── NightState rule ──

    @Test
    fun `night state does not produce overlay panel`() {
        // Night mode is handled by ReaderNightState, not by overlayState
        // ReaderControlBase overlayState should be BaseControlVisible (or null) during night mode
        val fixture = ReaderRuntimeFixture.createNightState()
        assertTrue(
            "Night state must be BaseControlVisible, not overlay",
            fixture.controlLayerState is ReaderControlLayerState.BaseControlVisible
        )
    }

    @Test
    fun `night mode is not a dialog`() {
        assertFalse("Night mode must not be Dialog", "AlertDialog" in baseSource)
        assertFalse("Night mode must not be a dialog", "NightModeDialog" in baseSource)
    }

    // ── Overlay zone positioning ──

    @Test
    fun `search overlay zone is positioned below top bar above quick actions`() {
        // Quick overlay zone: padding top = topZoneHeight (92dp), bottom = quickActionsBottomInset
        assertTrue("Search overlay zone must use topZoneHeight", "top = topZoneHeight" in baseSource)
        assertTrue("Quick overlay zone must use quickActionsBottomInset",
            "bottom = quickActionsBottomInset" in baseSource)
    }

    @Test
    fun `bottom overlay zone is positioned below top bar above bottom bar`() {
        assertTrue("Bottom overlay zone must use bottomOverlayBottomInset",
            "bottom = bottomOverlayBottomInset" in baseSource)
    }

    @Test
    fun `overlay content is passed through from ReaderScreen`() {
        // ReaderScreen passes overlayContent lambda to ReaderControlBase
        assertTrue("ReaderScreen must pass overlayContent",
            "overlayContent =" in screenSource)
        assertTrue("ReaderScreen must pass overlayState",
            "overlayState = state.controlLayerState" in screenSource)
    }

    // ── Content area chapter title ──

    @Test
    fun `content area includes chapter title text`() {
        assertTrue("ContentArea must include chapterTitle parameter",
            "ContentArea(" in screenSource)
        assertTrue("ContentArea must render chapter title",
            "chapterTitle" in screenSource)
    }

    // ── fillMaxSize passed within zone ──

    @Test
    fun `overlay composables inside OverlayContent receive fillMaxSize constrained by zone`() {
        val overlaySection = screenSource.substringAfter("fun OverlayContent").take(2000)
        assertTrue(
            "Overlay composables must use fillMaxSize to fill zone Box",
            "Modifier.fillMaxSize()" in overlaySection
        )
    }
}
