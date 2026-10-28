package io.reader.android

import android.os.Bundle
import android.os.Process as AndroidProcess
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiWatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.ui.ReaderViewModel
import io.reader.android.ui.ReaderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

/**
 * Selector: io.reader.android.UiLocalReadingJourneyTest#realTxtPickerReadReturnAndRecreateRetainsPosition
 * Requires the already hash-verified original in Download/Reader; never generates
 * text, substitutes a test Uri, clears app data, or retries a business operation.
 * Activity.recreate is explicitly Activity reconstruction, not process cold start.
 */
@RunWith(AndroidJUnit4::class)
class UiLocalReadingJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var currentMethod = "unassigned"
    private val phaseMarker = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                currentMethod = description.methodName
                marker("rule-enter")
                try { base.evaluate() }
                finally { marker("rule-exit") }
            }
        }
    }
    // Unlike @Test(timeout), this deadline also surrounds Compose/Activity
    // setup and teardown. The external controller retains its own client bound.
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phaseMarker).around(compose)

    @Test
    fun realTxtPickerReadReturnAndRecreateRetainsPosition() = withMethodCleanup {
        marker("method-enter") // Before the first potentially blocking compose.activity lookup.
        val args = InstrumentationRegistry.getArguments()
        val inputName = args.getString("readerInputName")
            ?: throw AssertionError("readerInputName must name the existing real TXT in Download/Reader")
        val expectedHash = args.getString("expectedSHA")
            ?: throw AssertionError("expectedSHA must bind the original pre-transfer SHA256")
        assertTrue("A SHA256 identity is required", expectedHash.matches(Regex("[a-f0-9]{64}")))
        val model = compose.activity.readerViewModel
        marker("activity-owner-ready")
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        marker("startup-core-ready")
        val originalImportRevision = model.state.value.importRevision
        marker("picker-request")
        compose.onNodeWithTag("reader_import").performClick()
        selectRealDocument(inputName)
        compose.waitUntil(120_000) { model.state.value.importRevision > originalImportRevision && !model.state.value.busy }
        assertEquals("Original picker import must report no failure", null, model.state.value.error)
        val book = model.state.value.books.singleOrNull { it.id == model.state.value.lastImportedBookId }
            ?: throw AssertionError("Imported original title absent; actual=${model.state.value.books.map { it.title }}")
        assertEquals("Imported Core identity must bind the exact original bytes", "local:$expectedHash", book.id)
        assertTrue("Real Core catalog must contain chapters", book.chapterCount > 1)
        marker("import-ACK", Bundle().apply { putString("bookId", book.id); putInt("chapterCount", book.chapterCount) })
        marker("reader-open-request")
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Reading && !model.state.value.busy }
        waitForNativeRestore()
        marker("reader-native-restored")
        compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        marker("directory-open-request")
        compose.onNodeWithTag("reader_open_directory").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Directory && !model.state.value.busy }
        val targetChapter = model.state.value.chapters.getOrNull(1)
            ?: throw AssertionError("Real original's second catalog entry is absent")
        marker("directory-ready")
        compose.onNodeWithTag("reader_chapter_${targetChapter.index}").performClick()
        compose.waitUntil(20_000) {
            model.state.value.route == ReaderRoute.Reading && model.state.value.reading?.chapter?.index == targetChapter.index && !model.state.value.busy
        }
        waitForNativeRestore()
        marker("directory-selected-reader-restored")
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 550) }
        compose.waitForIdle() // Capture the settled real fling anchor, not an intermediate frame.
        compose.waitUntil(10_000) { (model.state.value.reading?.visibleScalarOffset ?: 0) > 0 }
        val beforeExit = model.state.value.reading ?: throw AssertionError("Missing actual reading session")
        marker("visible-anchor-recorded", Bundle().apply { putInt("scalarOffset", beforeExit.visibleScalarOffset) })
        compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.onNodeWithTag("reader_controls").assertIsDisplayed()
        compose.onNodeWithTag("reader_font_larger").performClick()
        waitForNativeRestore()
        marker("font-reflow-complete")
        marker("exit-request")
        compose.onNodeWithTag("reader_exit").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Bookshelf && !model.state.value.busy }
        val persisted = model.state.value.books.single { it.id == book.id }
        assertEquals(beforeExit.chapter.index, persisted.progressChapter)
        assertTrue("Actual visible text anchor must persist", persisted.progressOffset > 0)
        assertEquals("Reflow must preserve the original scalar anchor", beforeExit.visibleScalarOffset, persisted.progressOffset)
        marker("exit-save-ACK")

        marker("activity-recreate-request")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) { compose.activity.readerViewModel.state.value.ready }
        marker("activity-recreate-ready")
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) {
            val state = compose.activity.readerViewModel.state.value
            state.route == ReaderRoute.Reading && !state.busy
        }
        waitForNativeRestore()
        val restored = compose.activity.readerViewModel.state.value.reading ?: throw AssertionError("Missing reopened reader")
        assertEquals(persisted.progressChapter, restored.chapter.index)
        assertEquals(persisted.progressOffset, restored.restoreScalarOffset)
        assertNativeAnchorVisible(persisted.progressOffset)
        assertNotEquals("Activity reconstruction must create a fresh reader owner", beforeExit.identity, restored.identity)
        val currentOffset = restored.visibleScalarOffset
        compose.runOnUiThread {
            compose.activity.readerViewModel.visibleAnchor(beforeExit.identity, 0)
        }
        assertEquals("Retired reader callback cannot overwrite current position", currentOffset,
            compose.activity.readerViewModel.state.value.reading?.visibleScalarOffset)
        compose.onNodeWithTag("reader_body").assertExists()
        exitToBookshelfAndAwaitSave(compose.activity.readerViewModel, book.id, persisted.progressChapter, persisted.progressOffset)
        marker("method-exit")
    }

    /** Controller force-stops and proves no old PID before launching this separate method. */
    @Test
    fun coldLaunchExistingRealTxtRetainsPosition() = withMethodCleanup {
        marker("method-enter") // The first method marker precedes compose.activity.
        val args = InstrumentationRegistry.getArguments()
        val hash = args.getString("expectedSHA")
            ?: throw AssertionError("expectedSHA must come from the prior stopped-data proof")
        val chapter = args.getString("expectedChapter")?.toIntOrNull()
            ?: throw AssertionError("expectedChapter must come from the prior stopped-data proof")
        val scalar = args.getString("expectedOffset")?.toIntOrNull()
            ?: throw AssertionError("expectedOffset must come from the prior stopped-data proof")
        assertTrue("Expected original SHA256 is invalid", hash.matches(Regex("[a-f0-9]{64}")))
        assertTrue("Expected Core coordinates must be nonnegative", chapter >= 0 && scalar >= 0)
        if (args.containsKey("previousProcessPid")) {
            val previous = args.getString("previousProcessPid")?.toIntOrNull()
                ?: throw AssertionError("previousProcessPid must be an actual positive prior PID")
            assertTrue("Previous PID must be positive", previous > 0)
            assertNotEquals("Cold launch must use a different process", previous, AndroidProcess.myPid())
        }
        marker("cold-process-observed")
        val model = compose.activity.readerViewModel
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        assertEquals("Cold method must not import any input", 0L, model.state.value.importRevision)
        assertTrue("Cold startup must expose no unknown import", model.state.value.pendingImports.isEmpty())
        assertTrue("Cold startup must expose no unknown position", model.state.value.pendingPositions.isEmpty())
        val book = model.state.value.books.singleOrNull { it.id == "local:$hash" }
            ?: throw AssertionError("The original existing book is absent after Core startup")
        assertEquals("Cold Core chapter differs from the prior proof", chapter, book.progressChapter)
        assertEquals("Cold Core scalar differs from the prior proof", scalar, book.progressOffset)
        marker("cold-core-restored", Bundle().apply { putString("bookId", book.id); putInt("chapter", chapter); putInt("scalarOffset", scalar) })
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Reading && !model.state.value.busy }
        waitForNativeRestore()
        val reading = model.state.value.reading ?: throw AssertionError("Cold reader is absent")
        assertEquals(chapter, reading.chapter.index)
        assertEquals(scalar, reading.restoreScalarOffset)
        assertNativeAnchorVisible(scalar)
        marker("cold-native-restored")
        exitToBookshelfAndAwaitSave(model, book.id, chapter, scalar)
        marker("method-exit")
    }

    /** Runs inside the method, before Compose's ActivityScenarioRule.close(). */
    private fun withMethodCleanup(body: () -> Unit) {
        var primaryFailure: Throwable? = null
        try {
            body()
        } catch (failure: Throwable) {
            primaryFailure = failure
            try {
                marker("method-failure", Bundle().apply {
                    putString("failureClass", failure.javaClass.name)
                    putString("failureMessage", failure.message?.take(256))
                })
            } catch (markerFailure: Throwable) { failure.addSuppressed(markerFailure) }
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            try {
                marker("teardown-enter")
                finishOwnedActivity()
            } catch (failure: Throwable) { cleanupFailure = failure }
            try {
                marker("teardown-exit", Bundle().apply { putBoolean("destroyed", cleanupFailure == null) })
            } catch (failure: Throwable) {
                val earlierCleanupFailure = cleanupFailure
                if (earlierCleanupFailure == null) cleanupFailure = failure else earlierCleanupFailure.addSuppressed(failure)
            }
            cleanupFailure?.let { failure ->
                val originalFailure = primaryFailure
                if (originalFailure != null) originalFailure.addSuppressed(failure)
                else throw AssertionError("Business method completed but Activity teardown failed").apply { addSuppressed(failure) }
            }
        }
    }

    private fun finishOwnedActivity() {
        val scenario = compose.activityRule.scenario
        if (scenario.state == Lifecycle.State.DESTROYED) {
            marker("activity-destroyed")
            return
        }
        val destroyed = CountDownLatch(1)
        var owner: MainActivity? = null
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    source.lifecycle.removeObserver(this)
                    destroyed.countDown()
                }
            }
        }
        try {
            // The scenario supplies its actual current instance, including after
            // recreate. Standard finish avoids launching a test-APK bootstrap.
            scenario.onActivity { activity ->
                owner = activity
                activity.lifecycle.addObserver(observer)
                activity.finish()
            }
            assertTrue("Owned MainActivity did not reach ON_DESTROY within 10 seconds",
                destroyed.await(10, TimeUnit.SECONDS))
            // Drain the completed destruction callback before inspecting the
            // scenario's steady state; no Compose idle or second UI owner.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue("The exact finished MainActivity is not destroyed", owner?.isDestroyed == true)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            marker("activity-destroyed")
        } finally {
            owner?.let { activity -> activity.runOnUiThread { activity.lifecycle.removeObserver(observer) } }
        }
    }

    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step)
        details.putString("readerMethod", currentMethod)
        details.putInt("readerProcessPid", AndroidProcess.myPid())
        details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, details)
    }

    private fun exitToBookshelfAndAwaitSave(model: ReaderViewModel, bookId: String, chapter: Int, scalar: Int) {
        if (!model.state.value.controlsVisible) {
            compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        }
        marker("final-exit-request")
        compose.onNodeWithTag("reader_exit").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Bookshelf && !model.state.value.busy }
        assertEquals("Final exit must retain no failure", null, model.state.value.error)
        assertTrue("Final save cannot remain unknown", model.state.value.pendingPositions.none { it.bookId == bookId })
        val saved = model.state.value.books.single { it.id == bookId }
        assertEquals(chapter, saved.progressChapter)
        assertEquals(scalar, saved.progressOffset)
        marker("final-exit-save-ACK", Bundle().apply { putInt("chapter", chapter); putInt("scalarOffset", scalar) })
    }

    /** Confirm the expected content's actual native laid-out line intersects the visible body. */
    private fun assertNativeAnchorVisible(scalar: Int) {
        val reading = compose.activity.readerViewModel.state.value.reading
            ?: throw AssertionError("No reader owns the native anchor")
        val text = reading.chapter.text
        val utf16 = ReaderTextCoordinates.utf16Offset(text, scalar)
        val start = if (utf16 == 0) 0 else text.lastIndexOf('\n', utf16 - 1) + 1
        val end = text.indexOf('\n', utf16).let { if (it < 0) text.length else it }
        val paragraph = text.substring(start, end)
        val viewport = compose.onNodeWithTag("reader_body").fetchSemanticsNode().boundsInRoot
        val nodes = compose.onAllNodes(hasText(paragraph.ifEmpty { "\u200B" }), useUnmergedTree = true).fetchSemanticsNodes()
        val visible = nodes.any { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            val action = if (SemanticsActions.GetTextLayoutResult in node.config)
                node.config[SemanticsActions.GetTextLayoutResult].action else null
            val obtained = compose.runOnUiThread { action?.invoke(layouts) == true }
            val layout = layouts.singleOrNull()
            if (!obtained || layout == null) false else {
                val line = layout.getLineForOffset((utf16 - start).coerceIn(0, paragraph.length))
                val top = node.positionInRoot.y + layout.getLineTop(line)
                val bottom = node.positionInRoot.y + layout.getLineBottom(line)
                bottom > viewport.top && top < viewport.bottom
            }
        }
        assertTrue("Expected original scalar's native text line is outside the actual viewport", visible)
    }

    private fun waitForNativeRestore() {
        compose.waitUntil(8_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("reader_restore_loading"))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun selectRealDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val watcherName = "reader-original-picker-open-consent"
        val consentPackage = "com.oplus.securitypermission"
        val consentTitle = "“Reader”想要打开“io.reader.minliny.test”"
        var consentClickAttempted = false
        device.registerWatcher(watcherName, UiWatcher {
            val title = By.pkg(consentPackage).res("$consentPackage:id/alertTitle").text(consentTitle)
            val checkbox = By.pkg(consentPackage).res("$consentPackage:id/coui_security_alert_dialog_checkbox")
                .text("始终允许打开").checkable(true).checked(false).enabled(true)
            val open = By.pkg(consentPackage).res("android:id/button1").text("打开").enabled(true)
            if (consentClickAttempted || device.findObjects(title).size != 1 ||
                device.findObjects(checkbox).size != 1 || device.findObjects(open).size != 1) {
                false
            } else {
                val titleSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/alertTitle").text(consentTitle)
                val checkboxSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/coui_security_alert_dialog_checkbox").text("始终允许打开")
                val openSelector = UiSelector().packageName(consentPackage)
                    .resourceId("android:id/button1").text("打开").enabled(true)
                if (!device.findObject(titleSelector).exists() || device.findObject(checkboxSelector).isChecked ||
                    !device.findObject(openSelector).isEnabled) false else {
                    consentClickAttempted = true // At most one dispatch, including a failed click.
                    marker("oem-open-observed", Bundle().apply { putString("dialogTitle", consentTitle); putBoolean("checkboxChecked", false) })
                    val clicked = device.findObject(openSelector).click()
                    val gone = device.findObject(titleSelector).waitUntilGone(2_000)
                    marker("oem-open-ACK", Bundle().apply { putBoolean("clickDispatched", clicked); putBoolean("dialogGone", gone) })
                    clicked
                }
            }
        })
        try {
            val provider = "com\\.(google\\.)?android\\.documentsui"
            val providerSelector = UiSelector().packageNameMatches(provider)
            assertTrue("System document picker must actually open",
                device.findObject(providerSelector).waitForExists(10_000))
            marker("picker-visible")
            val file = providerSelector.text(name)
            val folder = providerSelector.text("Reader")
            val drawer = providerSelector.resourceIdMatches(".*:id/roots_list")
            val downloads = drawer.childSelector(UiSelector().textMatches("Downloads|下载|下载内容"))
            if (!device.findObject(file).exists()) {
                // The previous picker can retain its drawer. Do not toggle an already
                // open drawer, and never carry UiObject2 across its animation.
                if (device.findObject(drawer).exists()) {
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                } else if (!device.findObject(folder).exists()) {
                    val roots = providerSelector.descriptionMatches("Show roots|显示根目录|显示位置|导航抽屉")
                    clickPickerOnce(device, roots, "picker-roots", drawer)
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                }
                if (!device.findObject(file).exists()) clickPickerOnce(device, folder, "picker-folder", file)
            }
            assertTrue("Original input missing: $name", device.findObject(file).waitForExists(5_000))
            marker("picker-file-click")
            // UiObject stores a selector and resolves its live node during this one click.
            assertTrue("Original document click was not dispatched", device.findObject(file).click())
            assertTrue("System picker did not return to Reader", device.findObject(providerSelector).waitUntilGone(10_000))
            assertTrue("Original picker return did not expose Reader's actual window",
                device.findObject(UiSelector().packageName("io.reader.minliny")).waitForExists(10_000))
            marker("picker-returned")
        } finally {
            device.removeWatcher(watcherName)
        }
    }

    private fun clickPickerOnce(device: UiDevice, selector: UiSelector, stage: String, next: UiSelector, gone: UiSelector? = null) {
        assertTrue("$stage target absent", device.findObject(selector).waitForExists(5_000))
        marker("$stage-click")
        // Reacquire from the exact selector immediately before the only click.
        assertTrue("$stage click was not dispatched", device.findObject(selector).click())
        if (gone != null) assertTrue("$stage did not retire its drawer", device.findObject(gone).waitUntilGone(5_000))
        assertTrue("$stage did not expose the next state", device.findObject(next).waitForExists(5_000))
        marker("$stage-ready")
    }
}
