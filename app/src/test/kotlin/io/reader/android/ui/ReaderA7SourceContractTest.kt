package io.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A7 source contract: navigation, system-bar ownership, and shared feedback hosts. */
class ReaderA7SourceContractTest {
    private fun source(path: String): String {
        val candidates = listOf(File("src/main/kotlin/$path"), File("app/src/main/kotlin/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("source not found: $path")
    }

    @Test
    fun appHostOwnsBackAndSafeDrawingBoundary() {
        val app = source("io/reader/android/ui/ReaderApp.kt")
        assertTrue("BackHandler(enabled = state.route != ReaderRoute.Bookshelf)" in app)
        assertTrue("WindowInsets.safeDrawing" in app)
        assertTrue("testTagsAsResourceId = true" in app)
    }

    @Test
    fun readingSurfaceKeepsFooterAndExitInTheReadingOwner() {
        val screen = source("io/reader/android/ui/ReaderReadingScreen.kt")
        assertTrue("WindowInsets.safeDrawing" in screen)
        assertTrue("reader_exit" in screen)
        assertTrue("reader_open_directory" in screen)
        assertTrue("reader_ebook_footer" in screen)
    }

    @Test
    fun systemBarsAndFeedbackUseStableLifecycleOwners() {
        val bars = source("io/reader/android/ui/ReaderSystemBars.kt")
        val messages = source("io/reader/android/ui/ReaderMessages.kt")
        assertTrue("controller.isAppearanceLightStatusBars = useDarkIcons" in bars)
        assertTrue("controller.isAppearanceLightNavigationBars = useDarkIcons" in bars)
        assertTrue("Lifecycle.Event.ON_RESUME" in bars)
        assertTrue("previousStatus" in bars && "previousNavigation" in bars)
        assertTrue("reader_transient_feedback" in messages)
        assertTrue("reader_recovery_notice" in messages)
        assertTrue("ReaderNativeConfirmation" in messages)
        assertTrue("AlertDialog" in messages)
    }
}
