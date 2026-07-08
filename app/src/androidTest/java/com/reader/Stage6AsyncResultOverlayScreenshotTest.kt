package com.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.api.ReaderCoreClient
import com.reader.ui.MainActivity
import com.reader.ui.shell.AppShellViewModel
import com.reader.ui.shell.AsyncResultStateValue
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.shell.RouteIds
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FR-D-5 / FR-D-6 — Stage 6 asyncResult UI visual feedback device screenshot.
 *
 * Captures PNG screenshots of real asyncResult overlay states (CANCELLED,
 * DISCARDED) on the device by launching [MainActivity], obtaining the
 * [AppShellViewModel] from the Activity's ViewModelStore, and dispatching
 * intent sequences that drive the reducer to the target [AsyncResultStateValue].
 *
 * Injection sequence:
 *  1. dispatch [ReaderUiIntent.EnterReaderFromCover] with `fixture://` bookUrl
 *     → enters ImmersiveReading route; ImmersiveReadingViewModel.load() runs
 *     synchronously (fixture:// path) and dispatches PENDING → COMPLETED.
 *  2. Wait for Compose to render + fixture sync completion.
 *  3. dispatch [ReaderUiIntent.StartAsyncRequest] + [ReaderUiIntent.CancelAsyncRequest]
 *     → reducer sets asyncResult to CANCELLED (for CANCELLED screenshot).
 *     OR dispatch [ReaderUiIntent.StartAsyncRequest](req-B) +
 *     [ReaderUiIntent.CompleteAsyncRequest](req-A, stale) → reducer detects
 *     requestId mismatch → DISCARDED (for DISCARDED screenshot).
 *  4. Wait for overlay re-render.
 *  5. captureActivityScreenshot().
 *
 * Screenshots are rendered from [MainActivity]'s own decor view, then written to
 * the app's external files dir under `screenshots/` and copied to
 * `/data/local/tmp/screenshots/` so they survive Gradle's post-test app
 * uninstall. Rendering the app view directly keeps transient system dialogs out
 * of the evidence image.
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

    /**
     * Screenshot baseline — capture the default bookshelf tab (IDLE state, no overlay).
     * Proves the device render pipeline works and provides a "before" reference
     * for the overlay screenshots.
     */
    @Test
    fun captureBookshelfIdleScreenshot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Wait for Compose first frame (viewModel() called, vm in ViewModelStore).
            Thread.sleep(1500)
            captureActivityScreenshot(scenario, "stage6_bookshelf_idle")
        }
    }

    /**
     * FR-D-6 — capture the CANCELLED asyncResult overlay ("加载已取消").
     *
     * Injection: EnterReaderFromCover(fixture://) → wait → StartAsyncRequest(req)
     * + CancelAsyncRequest(req) → reducer sets CANCELLED.
     */
    @Test
    fun captureCancelledOverlayScreenshot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Wait for Compose first frame so AppShellViewModel exists in ViewModelStore.
            Thread.sleep(1500)

            // Step 1: enter ImmersiveReading route with fixture:// bookUrl.
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[AppShellViewModel::class.java]
                vm.dispatch(
                    ReaderUiIntent.EnterReaderFromCover(
                        sourceId = "proof",
                        bookUrl = "fixture://proof",
                        bookName = "Proof",
                        requestId = "entry-cancel-proof"
                    )
                )
            }

            // Wait for Compose re-render + ImmersiveReadingViewModel fixture://
            // sync completion (PENDING → COMPLETED dispatched synchronously in
            // the VM init block during composition).
            Thread.sleep(2000)

            // Step 2: inject CANCELLED by dispatching Start + Cancel with same requestId.
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[AppShellViewModel::class.java]
                val reqId = "req-cancel-${System.nanoTime()}"
                vm.dispatch(
                    ReaderUiIntent.StartAsyncRequest(
                        fromRoute = RouteIds.IMMERSIVE_READING,
                        toRoute = RouteIds.IMMERSIVE_READING,
                        requestId = reqId
                    )
                )
                vm.dispatch(ReaderUiIntent.CancelAsyncRequest(requestId = reqId))
            }

            // Wait for overlay re-render.
            Thread.sleep(1000)

            captureActivityScreenshot(scenario, "stage6_asyncresult_cancelled")
        }
    }

    /**
     * FR-D-5 — capture the DISCARDED asyncResult overlay ("正在切换到最新…").
     *
     * Injection: EnterReaderFromCover(fixture://) → wait → StartAsyncRequest(req-B)
     * + CompleteAsyncRequest(req-A, stale) → reducer detects requestId mismatch
     * (req-A != req-B) → sets DISCARDED.
     */
    @Test
    fun captureDiscardedOverlayScreenshot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Wait for Compose first frame so AppShellViewModel exists in ViewModelStore.
            Thread.sleep(1500)

            // Step 1: enter ImmersiveReading route with fixture:// bookUrl.
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[AppShellViewModel::class.java]
                vm.dispatch(
                    ReaderUiIntent.EnterReaderFromCover(
                        sourceId = "proof",
                        bookUrl = "fixture://proof",
                        bookName = "Proof",
                        requestId = "entry-discard-proof"
                    )
                )
            }

            // Wait for Compose re-render + fixture:// sync completion.
            Thread.sleep(2000)

            // Step 2: inject DISCARDED — start req-B, then complete stale req-A.
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[AppShellViewModel::class.java]
                vm.dispatch(
                    ReaderUiIntent.StartAsyncRequest(
                        fromRoute = RouteIds.IMMERSIVE_READING,
                        toRoute = RouteIds.IMMERSIVE_READING,
                        requestId = "req-current"
                    )
                )
                // Stale completion: requestId "req-stale" != current "req-current"
                // → reducer sets asyncResult.state = DISCARDED.
                vm.dispatch(
                    ReaderUiIntent.CompleteAsyncRequest(
                        requestId = "req-stale",
                        value = "stale-content",
                        currentRoute = RouteIds.IMMERSIVE_READING
                    )
                )
            }

            // Wait for overlay re-render.
            Thread.sleep(1000)

            captureActivityScreenshot(scenario, "stage6_asyncresult_discarded")
        }
    }

    private fun captureActivityScreenshot(
        scenario: ActivityScenario<MainActivity>,
        name: String
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        lateinit var file: File
        scenario.onActivity { activity ->
            val root = activity.window.decorView.rootView
            require(root.width > 0 && root.height > 0) {
                "root view has invalid size: ${root.width}x${root.height}"
            }
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            root.draw(canvas)

            // Use external files dir (no permission needed on app's own dir).
            val dir = File(activity.getExternalFilesDir(null) ?: activity.filesDir, "screenshots")
            dir.mkdirs()
            file = File(dir, "$name.png")
            file.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        val uiAutomation = instrumentation.uiAutomation
        // Copy to /data/local/tmp/screenshots/ via UiAutomation shell access
        // so screenshots survive app uninstall (Gradle uninstalls the app
        // after connectedDebugAndroidTest, cleaning getExternalFilesDir).
        val tmpDir = "/data/local/tmp/screenshots"
        uiAutomation.executeShellCommand("mkdir -p $tmpDir")
        uiAutomation.executeShellCommand("cp ${file.absolutePath} $tmpDir/$name.png")
        println("SCREENSHOT_SAVED:${file.absolutePath}")
        println("SCREENSHOT_TMP:$tmpDir/$name.png")
    }
}
