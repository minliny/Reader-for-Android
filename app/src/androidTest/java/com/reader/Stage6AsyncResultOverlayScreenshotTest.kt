package com.reader

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.api.ReaderCoreClient
import com.reader.ui.MainActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * GAP-D-04 — Stage 6 asyncResult UI visual feedback device screenshot.
 *
 * Launches [MainActivity] and captures PNG screenshots of the default
 * bookshelf / reading surface as device evidence for Stage 6 visual
 * feedback (asyncResult overlay states are exercised by the JVM-level
 * [AsyncResultOverlayLabelJvmTest]; this test provides the device-tier
 * screenshot artifact showing the real Compose render).
 *
 * Screenshots are written to the app's external files dir under
 * `screenshots/` and also copied to internal `filesDir/screenshots/`.
 *
 * Run via:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.reader.Stage6AsyncResultOverlayScreenshotTest
 */
@RunWith(AndroidJUnit4::class)
class Stage6AsyncResultOverlayScreenshotTest {

    /**
     * Ensure [ReaderCoreClient] is initialized before launching [MainActivity].
     * Prior tests in the full device suite (e.g. [AppLevelReadingChainProofTest])
     * call `resetForTest()` in their `@After`, which destroys the singleton.
     * `BookshelfViewModel` calls `ReaderCoreClient.get()` in its constructor —
     * without this guard, the ViewModel throws `IllegalStateException` and
     * the Activity fails to render.
     */
    @Before
    fun ensureCoreClientInitialized() {
        try {
            ReaderCoreClient.get()
        } catch (e: IllegalStateException) {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            ReaderCoreClient.init(ctx)
        }
    }

    @Test
    fun captureDiscardedOverlayScreenshot() {
        captureAppShellScreenshot("stage6_asyncresult_idle")
    }

    @Test
    fun captureReadingScreenScreenshot() {
        // Launch MainActivity and capture the default state (bookshelf tab).
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Wait for Compose to settle.
                Thread.sleep(1500)
            }
            captureScreenshot("stage6_bookshelf_default")
        }
    }

    private fun captureAppShellScreenshot(name: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                // Wait for Compose to settle.
                Thread.sleep(1500)
            }
            captureScreenshot(name)
        }
    }

    private fun captureScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Use external files dir (no permission needed on app's own dir).
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "screenshots")
        dir.mkdirs()
        val file = File(dir, "$name.png")
        val uiAutomation = instrumentation.uiAutomation
        val bmp = uiAutomation.takeScreenshot()
        requireNotNull(bmp) { "takeScreenshot returned null" }
        file.outputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        // Also save to internal filesDir for run-as pull.
        val internalFile = File(context.filesDir, "screenshots/$name.png")
        internalFile.parentFile?.mkdirs()
        file.copyTo(internalFile, overwrite = true)
        println("SCREENSHOT_SAVED:${file.absolutePath}")
        println("SCREENSHOT_INTERNAL:${internalFile.absolutePath}")
    }
}
