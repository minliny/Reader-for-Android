package io.reader.android

import android.os.Bundle
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import android.os.Process as AndroidProcess
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiWatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.runtime.LibraryBook
import io.reader.android.ui.ReaderViewModel
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderChapterIndexKey
import io.reader.android.ui.ReaderScalarOffsetKey
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

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
    private var observedNativeOwner: Long? = null
    private var observedNativeScroll: ScrollView? = null
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
        compose.waitUntil(120_000) {
            val state = model.state.value
            !state.busy && (state.importRevision > originalImportRevision || state.error != null || state.pendingImports.isNotEmpty())
        }
        val importTerminal = model.state.value
        if (importTerminal.error != null || importTerminal.pendingImports.isNotEmpty()) {
            marker("import-terminal-failure", Bundle().apply {
                putString("reportedError", importTerminal.error)
                putInt("pendingOriginalCount", importTerminal.pendingImports.size)
            })
        }
        assertEquals("Original picker import must report no failure", null, importTerminal.error)
        assertTrue("Original picker import must not remain unknown", importTerminal.pendingImports.isEmpty())
        val book = model.state.value.books.singleOrNull { it.id == model.state.value.lastImportedBookId }
            ?: throw AssertionError("Imported original title absent; actual=${model.state.value.books.map { it.title }}")
        assertEquals("Imported Core identity must bind the exact original bytes", "local:$expectedHash", book.id)
        assertTrue("Real Core catalog must contain chapters", book.chapterCount > 1)
        marker("import-ACK", Bundle().apply { putString("bookId", book.id); putInt("chapterCount", book.chapterCount) })
        readReturnAndRecreateExisting(book, model)
        marker("method-exit")
    }

    /** Continues the already-confirmed original import without invoking SAF or importing again. */
    @Test
    fun existingRealTxtReadReturnAndRecreateRetainsPosition() = withMethodCleanup {
        marker("method-enter")
        val args = InstrumentationRegistry.getArguments()
        val hash = args.getString("expectedSHA")
            ?: throw AssertionError("expectedSHA must bind the already imported original")
        val expectedCount = args.getString("expectedChapterCount")?.toIntOrNull()
            ?: throw AssertionError("expectedChapterCount must come from the retained original persist receipt")
        assertTrue("Expected original SHA256 is invalid", hash.matches(Regex("[a-f0-9]{64}")))
        assertTrue("This journey requires the original catalog's second chapter", expectedCount > 1)
        val model = compose.activity.readerViewModel
        marker("activity-owner-ready")
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        marker("startup-core-ready")
        assertEquals("Existing-book journey must not import any input", 0L, model.state.value.importRevision)
        assertTrue("Existing original import cannot remain unknown", model.state.value.pendingImports.isEmpty())
        assertTrue("Existing position cannot remain unknown", model.state.value.pendingPositions.isEmpty())
        val book = model.state.value.books.singleOrNull { it.id == "local:$hash" }
            ?: throw AssertionError("The exact already imported original is absent")
        assertEquals("Existing original's catalog differs from its persist receipt", expectedCount, book.chapterCount)
        marker("existing-book-admitted", Bundle().apply { putString("bookId", book.id); putInt("chapterCount", book.chapterCount) })
        readReturnAndRecreateExisting(book, model)
        marker("method-exit")
    }

    /** Fresh Alice slice: bind the audited nonempty Core chapter instead of the TOC-only prelude rows. */
    @Test
    fun freshAliceDailyReadingScrollRecreateAndRestore() = withMethodCleanup {
        marker("method-enter")
        val args = InstrumentationRegistry.getArguments()
        val hash = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required")
        val expectedCount = args.getString("expectedChapterCount")?.toIntOrNull()
            ?: throw AssertionError("expectedChapterCount required")
        val targetIndex = args.getString("targetChapterIndex")?.toIntOrNull()
            ?: throw AssertionError("targetChapterIndex required")
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")) && expectedCount > 1 && targetIndex >= 0)
        val model = compose.activity.readerViewModel
        marker("activity-owner-ready")
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        assertEquals(0L, model.state.value.importRevision)
        assertTrue(model.state.value.pendingImports.isEmpty() && model.state.value.pendingPositions.isEmpty())
        val book = model.state.value.books.singleOrNull { it.id == "local:$hash" }
            ?: throw AssertionError("Fresh Alice book is absent")
        assertEquals(expectedCount, book.chapterCount)
        marker("fresh-book-admitted", Bundle().apply { putString("bookId", book.id); putInt("chapterCount", book.chapterCount); putInt("targetChapter", targetIndex) })
        readReturnAndRecreateExisting(book, model, targetIndex)
        marker("method-exit")
    }

    /** Shared reading suffix; import and existing-book admission stay separate. */
    private fun readReturnAndRecreateExisting(book: LibraryBook, model: ReaderViewModel, targetIndex: Int = 1) {
        marker("reader-open-request")
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Reading && !model.state.value.busy }
        waitForNativeRestore()
        marker("reader-native-restored")
        showControlsAndAwaitDraw(model, "reader_open_directory")
        marker("directory-open-request")
        compose.onNodeWithTag("reader_open_directory").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Directory && !model.state.value.busy }
        val targetChapter = model.state.value.chapters.getOrNull(targetIndex)
            ?: throw AssertionError("Real original's second catalog entry is absent")
        marker("directory-ready")
        compose.onNodeWithTag("reader_directory").performScrollToNode(hasTestTag("reader_chapter_${targetChapter.index}"))
        compose.onNodeWithTag("reader_chapter_${targetChapter.index}").performClick()
        compose.waitUntil(20_000) {
            model.state.value.route == ReaderRoute.Reading && model.state.value.reading?.chapter?.index == targetChapter.index && !model.state.value.busy
        }
        waitForNativeRestore()
        marker("directory-selected-reader-restored")
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 550) }
        compose.waitForIdle() // Native control entry below stops the platform fling and captures its actual final line.
        compose.waitUntil(10_000) { (model.state.value.reading?.visibleScalarOffset ?: 0) > 0 }
        showControlsAndAwaitDraw(model, "reader_font_larger")
        val beforeExit = model.state.value.reading ?: throw AssertionError("Missing actual reading session")
        marker("visible-anchor-recorded", Bundle().apply { putInt("scalarOffset", beforeExit.visibleScalarOffset) })
        assertNotEquals("This actual pan must exercise a position newer than the initial restore", beforeExit.restoreScalarOffset,
            beforeExit.visibleScalarOffset)
        val beforeReadingRecreate = compose.runOnUiThread {
            compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll)
                ?: throw AssertionError("The original native viewport is absent before recreation")
        }
        marker("reading-activity-recreate-request")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {
            val state = compose.activity.readerViewModel.state.value
            state.ready && !state.busy && state.route == ReaderRoute.Reading
        }
        assertTrue("Activity recreation must retain this ViewModel", compose.activity.readerViewModel === model)
        waitForNativeRestore(replacedView = beforeReadingRecreate)
        val recreated = model.state.value.reading ?: throw AssertionError("The retained reader is absent after recreation")
        assertEquals("Recreation retains the current reader owner", beforeExit.identity, recreated.identity)
        assertEquals("Recreation must retain the newer logical anchor", beforeExit.visibleScalarOffset, recreated.visibleScalarOffset)
        assertNativeAnchorVisible(beforeExit.visibleScalarOffset)
        marker("reading-activity-new-native-view-ACK", Bundle().apply { putInt("scalarOffset", recreated.visibleScalarOffset) })
        compose.onNodeWithTag("reader_controls").assertIsDisplayed()
        compose.onNodeWithTag("reader_font_larger").performClick()
        waitForNativeRestore()
        assertEquals("Font reflow alone must preserve the logical anchor", beforeExit.visibleScalarOffset,
            model.state.value.reading!!.visibleScalarOffset)
        assertNativeAnchorVisible(beforeExit.visibleScalarOffset)
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
        assertEquals(scalar, reading.visibleScalarOffset)
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
        showControlsAndAwaitDraw(model, "reader_exit")
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
        val utf16 = ReaderTextCoordinates.utf16Offset(reading.chapter.text, scalar)
        compose.runOnUiThread {
            val body = compose.activity.findViewById<TextView>(R.id.reader_native_text)
                ?: throw AssertionError("Actual native body is absent")
            val scroll = compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll)
                ?: throw AssertionError("Actual native viewport is absent")
            assertTrue(body.isAttachedToWindow && scroll.isAttachedToWindow)
            assertEquals("Native text must retain the complete original UTF-16 body", reading.chapter.text, body.text.toString())
            val layout = body.layout ?: throw AssertionError("Actual native Layout is absent")
            val line = layout.getLineForOffset(utf16)
            val viewport = Rect()
            assertTrue(scroll.getGlobalVisibleRect(viewport))
            val scrollLocation = IntArray(2)
            scroll.getLocationOnScreen(scrollLocation)
            assertTrue("Native content clip must exclude ScrollView padding", viewport.intersect(
                scrollLocation[0] + scroll.paddingLeft, scrollLocation[1] + scroll.paddingTop,
                scrollLocation[0] + scroll.width - scroll.paddingRight, scrollLocation[1] + scroll.height - scroll.paddingBottom))
            val location = IntArray(2)
            body.getLocationOnScreen(location)
            val top = location[1] + body.totalPaddingTop - body.scrollY + layout.getLineTop(line)
            val bottom = location[1] + body.totalPaddingTop - body.scrollY + layout.getLineBottom(line)
            assertTrue("Original scalar's actual native line must intersect the viewport: scalar=$scalar utf16=$utf16 " +
                "line=$line top=$top bottom=$bottom viewport=$viewport scrollY=${scroll.scrollY} " +
                "bodyScrollY=${body.scrollY} selection=${body.selectionStart}/${body.selectionEnd}",
                top < viewport.bottom && bottom > viewport.top)
        }
    }

    private fun waitForNativeRestore(replacedView: ScrollView? = null) {
        val model = compose.activity.readerViewModel
        val expected = model.state.value
        val reading = expected.reading ?: throw AssertionError("No current reading session to restore")
        val previousView = replacedView ?: observedNativeScroll
        val mustReplace = replacedView != null || (observedNativeOwner != null && observedNativeOwner != reading.identity)
        var actualView: ScrollView? = null
        compose.waitUntil(10_000) {
            val roots = compose.onAllNodes(hasTestTag("reader_reading")).fetchSemanticsNodes()
            val semanticsMatch = roots.size == 1 && ReaderChapterIndexKey in roots.single().config &&
                ReaderScalarOffsetKey in roots.single().config &&
                roots.single().config[ReaderChapterIndexKey] == reading.chapter.index &&
                roots.single().config[ReaderScalarOffsetKey] == reading.visibleScalarOffset
            val controlsMatch = compose.onAllNodes(hasTestTag("reader_controls")).fetchSemanticsNodes().isNotEmpty() == expected.controlsVisible
            val loadingGone = compose.onAllNodes(hasTestTag("reader_restore_loading")).fetchSemanticsNodes().isEmpty()
            val nativeMatch = compose.runOnUiThread {
                val scroll = compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll)
                val body = compose.activity.findViewById<TextView>(R.id.reader_native_text)
                val layout = body?.layout
                val replaced = !mustReplace || (scroll !== previousView && previousView?.isAttachedToWindow != true)
                val ready = scroll != null && body != null && scroll.isAttachedToWindow && body.isAttachedToWindow &&
                    scroll.isShown && body.isShown && scroll.isLaidOut && body.isLaidOut &&
                    !scroll.isLayoutRequested && !body.isLayoutRequested && layout != null &&
                    layout.width == body.width - body.totalPaddingLeft - body.totalPaddingRight &&
                    reading.chapter.text.contentEquals(body.text) && abs(body.textSize - TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, expected.fontSize.toFloat(), body.resources.displayMetrics)) < .5f && replaced
                if (ready) actualView = scroll
                ready
            }
            model.state.value.reading?.identity == reading.identity && semanticsMatch && controlsMatch && loadingGone && nativeMatch
        }
        val native = actualView ?: throw AssertionError("Current native viewport did not become ready")
        awaitAttachedDraw(native)
        assertEquals("The drawn native view must retain the admitted reader", reading.identity, model.state.value.reading!!.identity)
        observedNativeOwner = reading.identity
        observedNativeScroll = native
        marker("native-session-draw-ACK", Bundle().apply {
            putLong("readingOwner", reading.identity); putInt("chapter", reading.chapter.index)
            putInt("scalarOffset", reading.visibleScalarOffset); putBoolean("replacedView", mustReplace)
        })
    }

    private fun showControlsAndAwaitDraw(model: ReaderViewModel, actionTag: String) {
        val owner = model.state.value.reading!!.identity
        if (!model.state.value.controlsVisible) compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.waitUntil(5_000) {
            model.state.value.reading?.identity == owner && model.state.value.controlsVisible && enabledControlPresent(actionTag)
        }
        awaitAttachedDraw(compose.activity.window.decorView)
        assertEquals(owner, model.state.value.reading!!.identity)
        assertTrue("The actual enabled control must survive the attached draw", enabledControlPresent(actionTag))
        marker("native-control-draw-ACK", Bundle().apply { putString("actionTag", actionTag) })
    }

    private fun enabledControlPresent(tag: String): Boolean {
        val nodes = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
        return nodes.size == 1 && SemanticsProperties.Disabled !in nodes.single().config &&
            nodes.single().boundsInRoot.width > 0f && nodes.single().boundsInRoot.height > 0f
    }

    /** Observe the real attached tree's next draw without sleeping or issuing business input. */
    private fun awaitAttachedDraw(view: View) {
        val drawn = AtomicBoolean(false)
        lateinit var observer: ViewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener {
            if (view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0) drawn.set(true)
        }
        compose.runOnUiThread {
            assertTrue("The draw target must be attached", view.isAttachedToWindow && view.isShown)
            observer = view.viewTreeObserver
            observer.addOnDrawListener(listener)
            view.invalidate()
        }
        try {
            compose.waitUntil(5_000) { drawn.get() }
            compose.runOnUiThread { assertTrue("The acknowledged view must remain attached", view.isAttachedToWindow && view.isShown) }
        } finally {
            compose.runOnUiThread { if (observer.isAlive) observer.removeOnDrawListener(listener) }
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
