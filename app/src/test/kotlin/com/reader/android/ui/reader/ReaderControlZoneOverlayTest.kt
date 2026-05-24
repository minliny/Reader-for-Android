package com.reader.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderControlZoneOverlayTest {

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

    // ── Zone-based overlay architecture ──

    @Test
    fun `reader control base accepts overlayState parameter`() {
        assertTrue(
            "ReaderControlBase must accept overlayState",
            "overlayState:" in baseSource || "overlayState " in baseSource
        )
    }

    @Test
    fun `reader control base accepts overlayContent lambda`() {
        assertTrue(
            "ReaderControlBase must accept overlayContent",
            "overlayContent:" in baseSource || "overlayContent " in baseSource
        )
    }

    @Test
    fun `quick action overlays do not use fillMaxSize opaque full screen`() {
        // The overlayContent lambda is rendered inside a zoned Box in ReaderControlBase,
        // NOT as fillMaxSize in ReaderScreen
        val screenOverlaySection = screenSource.substringAfter("OverlayContent").take(3000)
        // Overlay composables inside OverlayContent should NOT receive fillMaxSize modifier
        assertFalse(
            "Quick action overlay composables must not receive fillMaxSize",
            "ReaderSearchOverlay(" in screenOverlaySection &&
                "Modifier.fillMaxSize()" in screenOverlaySection.take(
                    screenOverlaySection.indexOf("ReaderSearchOverlay") + 500
                )
        )
    }

    @Test
    fun `bottom function overlays do not use fillMaxSize opaque full screen`() {
        val screenOverlaySection = screenSource.substringAfter("BottomFunctionOverlay").take(3000)
        // Overlay composables must not receive fillMaxSize
        val hasFullScreen = listOf("Modifier.fillMaxSize()", "modifier = Modifier.fillMaxSize()").any {
            it in screenOverlaySection
        }
        assertFalse(
            "Bottom overlay composables must not receive fillMaxSize",
            hasFullScreen
        )
    }

    @Test
    fun `overlay zone uses padding from top zone height`() {
        // The zone Box uses topZoneHeight (92dp = 56dp top bar + 36dp meta row)
        assertTrue("Zone must define top zone padding", "topZoneHeight" in baseSource)
    }

    @Test
    fun `quick overlay zone positions below meta row above quick actions`() {
        // Quick actions bottom inset must be larger than bottom overlay inset
        assertTrue("Must have quickActionsBottomInset", "quickActionsBottomInset" in baseSource)
        assertTrue("Must have bottomOverlayBottomInset", "bottomOverlayBottomInset" in baseSource)
    }

    // ── Visibility rules ──

    @Test
    fun `brightness dock hidden during quick action overlay`() {
        assertTrue("Must check !isQuickOverlay for brightness", "!isQuickOverlay" in baseSource)
        assertTrue("Must check !isBottomOverlay for brightness", "!isBottomOverlay" in baseSource)
    }

    @Test
    fun `quick actions hidden during bottom function overlay`() {
        assertTrue("Must check !isBottomOverlay for quick actions", "showQuickActions" in baseSource)
    }

    @Test
    fun `page control hidden during bottom function overlay`() {
        assertTrue("Must check !isBottomOverlay for page control", "showPageControl" in baseSource)
    }

    @Test
    fun `top bar always visible regardless of overlay`() {
        // Top bar is rendered unconditionally, not wrapped in visibility check
        val topAreaIndex = baseSource.indexOf("ReaderTopArea(")
        val nextBlock = baseSource.substring(topAreaIndex, topAreaIndex + 500)
        assertFalse("Top bar must not be guarded by showX variable", "show" in nextBlock.take(100))
    }

    @Test
    fun `bottom bar always visible regardless of overlay`() {
        // Bottom bar is rendered unconditionally
        val bottomIndex = baseSource.indexOf("ReaderControlBottomBar(")
        val nextBlock = baseSource.substring(bottomIndex, bottomIndex + 300)
        assertFalse("Bottom bar must not be guarded by showX variable", "show" in nextBlock.take(100))
    }

    // ── No fillMaxSize full screen in overlays ──

    @Test
    fun `control base does not render overlays with fillMaxSize opaque full screen`() {
        // The zone Box uses fillMaxSize + padding, not just raw fillMaxSize
        val overlaySection = baseSource.substringAfter("Zone-based overlay panels")
        assertTrue("Overlay zone must use padding", "padding(" in overlaySection.take(500))
    }
}
