package io.reader.android

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.reader.android.runtime.ProgressReceiptPolicy
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ignored source-only candidate. One existing-book method; no picker/import/copy/bookmark/search/recreate/cold run. */
@RunWith(AndroidJUnit4::class)
class UiDailyReadingLifecycleTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private var scenario: ActivityScenario<MainActivity>? = null
    private var original: Preferences? = null
    private var originalRequestedOrientation: Int? = null
    private var homeIssued = false
    private var frontIssued = false
    private var backIssued = false
    private var orientationRestored = false
    private var readingOwner: Long? = null
    private lateinit var evidence: File
    private lateinit var bookId: String
    private var expectedChapter = -1
    private var expectedOffset = -1
    private val phase = TestRule { base, _ -> object : Statement() {
        override fun evaluate() { marker("rule-enter"); try { base.evaluate() } finally { marker("rule-exit") } }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(110, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phase).around(compose)

    @Test fun existingRealTxtDisplayConfigurationBackgroundAndSystemBackPreservePosition() {
        marker("method-enter")
        var primary: Throwable? = null
        try {
            val args = InstrumentationRegistry.getArguments()
            val sha = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required from latest stopped proof")
            expectedChapter = args.getString("expectedChapter")?.toIntOrNull() ?: throw AssertionError("expectedChapter required")
            expectedOffset = args.getString("expectedOffset")?.toIntOrNull() ?: throw AssertionError("expectedOffset required")
            assertTrue(sha.matches(Regex("[a-f0-9]{64}")) && expectedChapter >= 0 && expectedOffset >= 0)
            bookId = "local:$sha"
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val start = activity()
            originalRequestedOrientation = start.requestedOrientation
            evidence = File(start.cacheDir, "A2DailyReadingLifecycle/${UUID.randomUUID()}")
            assertTrue(evidence.mkdirs())
            val model = start.readerViewModel
            waitHealthyReady(model)
            val book = model.state.value.books.single { it.id == bookId }
            assertEquals(expectedChapter, book.progressChapter); assertEquals(expectedOffset, book.progressOffset)
            val state = model.state.value
            original = Preferences(state.theme, state.paper, state.fontSize)
            val prefs = start.getSharedPreferences("reader.display", 0)
            marker("initial-display-bound", Bundle().apply {
                putString("theme", state.theme.name); putString("paper", state.paper.name); putInt("fontSize", state.fontSize)
                putInt("requestedOrientation", start.requestedOrientation); putInt("configurationOrientation", start.resources.configuration.orientation)
                putBoolean("themeKeyPresent", prefs.contains("appTheme")); putBoolean("paperKeyPresent", prefs.contains("paper"))
                putBoolean("fontKeyPresent", prefs.contains("fontSize")); putString("bookId", bookId)
                putInt("chapter", expectedChapter); putInt("scalarOffset", expectedOffset)
            })
            compose.onNodeWithTag("reader_tab_settings").performClick()
            compose.waitUntil(5_000) { model.state.value.route == ReaderRoute.Settings }
            val beforeNight = resolvedNight(state.theme, start.resources.configuration)
            selectTheme(if (beforeNight) ReaderAppTheme.Light else ReaderAppTheme.Dark, "theme-changed")
            selectTheme(state.theme, "theme-restored")
            compose.onNodeWithTag("reader_tab_library").performClick()
            compose.waitUntil(5_000) { model.state.value.route == ReaderRoute.Bookshelf }
            compose.onNodeWithTag("reader_book_$bookId").performClick()
            compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model)
            nativeReady(activity())
            assertNativeAnchor(activity())
            marker("initial-native-anchor")

            controls()
            val alternatePaper = if (state.paper == ReaderPaper.Night) ReaderPaper.Warm else ReaderPaper.Night
            selectPaper(alternatePaper); selectPaper(state.paper)
            val alternateSize = if (state.fontSize < 32) state.fontSize + 1 else state.fontSize - 1
            changeFont(alternateSize); changeFont(state.fontSize)
            compose.onNodeWithTag("reader_close_controls").performClick()
            compose.waitUntil(5_000) { !model.state.value.controlsVisible && enabledNodes("reader_close_controls") == 0 }
            nativeReady(activity()); assertNativeAnchor(activity())
            marker("paper-font-ui-restored")

            val initialConfiguration = activity().resources.configuration.orientation
            assertTrue(initialConfiguration == Configuration.ORIENTATION_PORTRAIT || initialConfiguration == Configuration.ORIENTATION_LANDSCAPE)
            val opposite = if (initialConfiguration == Configuration.ORIENTATION_PORTRAIT) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            rotateAndAssert(opposite, "orientation-1")
            rotateAndAssert(initialConfiguration, "orientation-2")

            val resumedActivity = activity()
            val resumedModel = resumedActivity.readerViewModel
            val stopped = CountDownLatch(1); val resumed = CountDownLatch(1)
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) stopped.countDown()
                if (event == Lifecycle.Event.ON_RESUME && stopped.count == 0L) resumed.countDown()
            }
            val beforeBackground = receiptIds(resumedActivity)
            scenario!!.onActivity { it.lifecycle.addObserver(observer) }
            try {
                check(!homeIssued); homeIssued = true
                assertTrue(device.pressHome()) // Exactly once, same UiAutomation owner.
                assertTrue("Actual ON_STOP missing", stopped.await(5, TimeUnit.SECONDS))
                assertEquals(Lifecycle.State.CREATED, scenario!!.state)
                marker("background-stopped")
                awaitReceipt(resumedActivity, beforeBackground, "background-save-ACK")
                bringToFrontOnce(resumedActivity)
                assertTrue("Actual ON_RESUME missing", resumed.await(5, TimeUnit.SECONDS))
                compose.waitUntil(5_000) { currentOrNull()?.lifecycle?.currentState == Lifecycle.State.RESUMED }
                assertSame(resumedActivity, activity()); assertSame(resumedModel, activity().readerViewModel)
                healthy(resumedModel); nativeReady(activity()); assertNativeAnchor(activity())
                marker("foreground-native-restored")
            } finally { instrumentation.runOnMainSync { resumedActivity.lifecycle.removeObserver(observer) } }

            val beforeBack = receiptIds(activity())
            assertFalse(activity().readerViewModel.state.value.controlsVisible)
            check(!backIssued); backIssued = true
            assertTrue(device.pressBack()) // The system dispatcher, never model.back/exitReading.
            compose.waitUntil(15_000) { !resumedModel.state.value.busy && (resumedModel.state.value.route == ReaderRoute.Bookshelf || failed(resumedModel)) }
            healthy(resumedModel); assertEquals(ReaderRoute.Bookshelf, resumedModel.state.value.route)
            val saved = resumedModel.state.value.books.single { it.id == bookId }
            assertEquals(expectedChapter, saved.progressChapter); assertEquals(expectedOffset, saved.progressOffset)
            awaitReceipt(activity(), beforeBack, "system-back-save-ACK")
            assertPreferencesRestored()
            restoreRequestedOrientation()
            marker("method-body-complete")
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("message", failure.message?.take(256)) }) }
            catch (markerFailure: Throwable) { failure.addSuppressed(markerFailure) }
            throw failure
        } finally {
            try { restoreThroughUiAndFinish() }
            catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        }
        marker("method-exit")
    }

    private data class Preferences(val theme: ReaderAppTheme, val paper: ReaderPaper, val fontSize: Int)
    private fun activity() = currentOrNull() ?: throw AssertionError("Actual MainActivity absent")
    private fun currentOrNull(): MainActivity? {
        var current: MainActivity? = null
        try { scenario?.onActivity { current = it } } catch (_: IllegalStateException) { }
        return current
    }
    private fun failed(model: ReaderViewModel): Boolean {
        val s = model.state.value
        return s.error != null || s.protocolRecoveryReference != null || s.pendingImports.isNotEmpty() ||
            s.pendingPositions.isNotEmpty() || s.pendingBookmarks.isNotEmpty() || s.pendingSearchClose != null
    }
    private fun healthy(model: ReaderViewModel) { assertFalse("Original operation must not be unresolved/failed", failed(model)); assertEquals(0L, model.state.value.importRevision) }
    private fun waitHealthyReady(model: ReaderViewModel) {
        compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
        healthy(model); assertTrue(model.state.value.ready && model.state.value.pendingReady)
    }
    private fun selected(tag: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Selected in node.config && node.config[SemanticsProperties.Selected])
    }
    private fun enabledNodes(tag: String): Int = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().count {
        SemanticsProperties.Disabled !in it.config && it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0
    }
    private fun controls() {
        val model = activity().readerViewModel
        if (!model.state.value.controlsVisible) compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.waitUntil(5_000) { model.state.value.controlsVisible && enabledNodes("reader_font_larger") + enabledNodes("reader_font_smaller") > 0 }
        attachedDraw(activity().window.decorView)
    }
    private fun resolvedNight(theme: ReaderAppTheme, config: Configuration) = theme == ReaderAppTheme.Dark ||
        (theme == ReaderAppTheme.System && (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    private fun selectTheme(theme: ReaderAppTheme, label: String) {
        compose.onNodeWithTag("reader_theme_${theme.name}").performClick()
        compose.waitUntil(5_000) { activity().readerViewModel.state.value.theme == theme }
        selected("reader_theme_${theme.name}"); compose.waitForIdle(); attachedDraw(activity().window.decorView)
        val bounds = compose.onNodeWithTag("reader_settings").fetchSemanticsNode().boundsInWindow
        val decor = activity().window.decorView
        val origin = compose.runOnUiThread { IntArray(2).also(decor::getLocationOnScreen) }
        val screenshot = File(evidence, "$label.png")
        assertTrue(device.takeScreenshot(screenshot))
        val bitmap = BitmapFactory.decodeFile(screenshot.path) ?: throw AssertionError("Actual theme screenshot absent")
        try {
            val x = origin[0] + bounds.left.roundToInt() + 2
            val y = origin[1] + bounds.top.roundToInt() + 2
            assertTrue(x in 0 until bitmap.width && y in 0 until bitmap.height)
            val actual = bitmap.getPixel(x, y)
            val expected = (if (resolvedNight(theme, activity().resources.configuration)) ReaderNightColors else ReaderDayColors).background.toArgb()
            assertTrue("Actual settings gutter must render selected theme", abs(AndroidColor.red(actual) - AndroidColor.red(expected)) <= 2 &&
                abs(AndroidColor.green(actual) - AndroidColor.green(expected)) <= 2 && abs(AndroidColor.blue(actual) - AndroidColor.blue(expected)) <= 2)
            marker(label, Bundle().apply { putString("theme", theme.name); putInt("sampleArgb", actual); putInt("expectedArgb", expected)
                putString("cacheRelativeEvidence", screenshot.relativeTo(activity().cacheDir).path) })
        } finally { bitmap.recycle() }
    }
    private fun selectPaper(paper: ReaderPaper) {
        compose.onNodeWithTag("reader_paper_${paper.name}").performClick()
        compose.waitUntil(5_000) { activity().readerViewModel.state.value.paper == paper }
        selected("reader_paper_${paper.name}"); nativeReady(activity())
        val current = activity()
        compose.runOnUiThread {
            val scroll = current.findViewById<ScrollView>(R.id.reader_native_scroll)
            val text = current.findViewById<TextView>(R.id.reader_native_text)
            assertEquals(paper.background().toArgb(), (scroll.background as ColorDrawable).color)
            assertEquals(paper.ink().toArgb(), text.currentTextColor)
        }
        marker("paper-native-rendered", Bundle().apply { putString("paper", paper.name) })
    }
    private fun changeFont(size: Int) {
        val current = activity().readerViewModel.state.value.fontSize
        if (current == size) return
        assertEquals("One actual UI font step only", 1, abs(size - current))
        compose.onNodeWithTag(if (size > current) "reader_font_larger" else "reader_font_smaller").performClick()
        compose.waitUntil(5_000) { activity().readerViewModel.state.value.fontSize == size }
        nativeReady(activity())
        assertEquals(expectedOffset, activity().readerViewModel.state.value.reading!!.visibleScalarOffset)
        marker("font-native-rendered", Bundle().apply { putInt("fontSize", size) })
    }

    private fun rotateAndAssert(orientation: Int, label: String) {
        val old = activity(); val model = old.readerViewModel
        val oldView = old.findViewById<ScrollView>(R.id.reader_native_scroll)
        val before = receiptIds(old)
        scenario!!.onActivity { it.requestedOrientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) {
            val current = currentOrNull()
            current != null && current !== old && current.lifecycle.currentState == Lifecycle.State.RESUMED && current.resources.configuration.orientation == orientation
        }
        val current = activity()
        assertSame(model, current.readerViewModel)
        nativeReady(current, oldView); assertNativeAnchor(current)
        marker("$label-native-configured", Bundle().apply { putInt("configurationOrientation", current.resources.configuration.orientation)
            putInt("requestedOrientation", current.requestedOrientation); putBoolean("newNativeView", true) })
        awaitReceipt(current, before, "$label-save-ACK")
    }
    private fun bringToFrontOnce(target: MainActivity) {
        check(!frontIssued); frontIssued = true
        target.applicationContext.startActivity(Intent().setComponent(target.componentName).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        marker("bring-existing-task-requested")
    }
    private fun restoreRequestedOrientation(requireResumed: Boolean = true) {
        if (orientationRestored) return
        val value = originalRequestedOrientation ?: return
        val current = currentOrNull() ?: return // Destroyed Activity has no surviving orientation request; no system setting was changed.
        if (current.requestedOrientation != value) scenario!!.onActivity { it.requestedOrientation = value }
        compose.waitUntil(5_000) {
            val actual = currentOrNull()
            actual?.requestedOrientation == value && (!requireResumed || actual.lifecycle.currentState == Lifecycle.State.RESUMED)
        }
        orientationRestored = true
        marker("requested-orientation-restored", Bundle().apply { putInt("requestedOrientation", value) })
    }

    private fun receiptIds(context: MainActivity): Set<String> = File(context.filesDir, "Reader/Operations/Completed").listFiles().orEmpty()
        .filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }
    private fun awaitReceipt(context: MainActivity, before: Set<String>, stage: String) {
        val directory = File(context.filesDir, "Reader/Operations/Completed")
        var result: JSONObject? = null; var bytes: ByteArray? = null
        compose.waitUntil(10_000) {
            val fresh = receiptIds(context) - before
            val records = fresh.map { name ->
                val raw = AtomicFile(File(directory, name)).openRead().use { it.readBytes() }
                JSONObject(raw.toString(Charsets.UTF_8)) to raw
            }.filter { it.first.optString("kind") == "progress" && it.first.optString("bookId") == bookId }
            assertTrue("Unexpected multiple original saves in $stage", records.size <= 1)
            records.singleOrNull()?.let { (record, raw) -> result = record; bytes = raw }
            result != null || failed(context.readerViewModel)
        }
        healthy(context.readerViewModel)
        val record = result ?: throw AssertionError("No exact completed original progress ACK for $stage")
        assertTrue(record.getBoolean("completed"))
        val parameters = record.getJSONObject("parameters")
        assertEquals(expectedChapter, parameters.getInt("chapterIndex")); assertEquals(expectedOffset, parameters.getInt("chapterOffset"))
        val wire = record.getJSONObject("wireTerminal")
        val revision = ProgressReceiptPolicy.revision(record, wire) // Existing exact original-wire validator, never a progress.get.
        marker(stage, Bundle().apply {
            putString("originalId", record.getString("id")); putLong("requestId", record.getLong("requestId"))
            putString("runtimeSession", record.getString("runtimeSession")); putString("bookId", bookId)
            putInt("chapter", expectedChapter); putInt("scalarOffset", expectedOffset)
            putString("expectedRevision", parameters.getString("expectedProgressRevision")); putString("ackRevision", revision)
            putString("bodyVersion", parameters.getString("expectedBodyVersion")); putString("processingVersion", parameters.getString("expectedProcessingVersion"))
            putString("originalRecordSha256", sha(bytes!!)); putString("originalWire", wire.toString())
        })
    }

    private fun nativeReady(current: MainActivity, oldView: ScrollView? = null) {
        val model = current.readerViewModel
        val state = model.state.value; val reading = state.reading ?: throw AssertionError("No reading owner")
        assertEquals(bookId, reading.book.id); assertEquals(bookId, reading.chapter.bookId)
        assertEquals(expectedChapter, reading.chapter.index); assertEquals(expectedOffset, reading.visibleScalarOffset)
        if (readingOwner == null) readingOwner = reading.identity else assertEquals(readingOwner, reading.identity)
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            val roots = compose.onAllNodes(hasTestTag("reader_reading")).fetchSemanticsNodes()
            val match = roots.size == 1 && ReaderChapterIndexKey in roots.single().config && ReaderScalarOffsetKey in roots.single().config &&
                roots.single().config[ReaderChapterIndexKey] == expectedChapter && roots.single().config[ReaderScalarOffsetKey] == expectedOffset
            val viewReady = compose.runOnUiThread {
                val scroll = current.findViewById<ScrollView>(R.id.reader_native_scroll)
                val body = current.findViewById<TextView>(R.id.reader_native_text); val layout = body?.layout
                scroll != null && body != null && scroll.isAttachedToWindow && body.isAttachedToWindow && scroll.isShown && body.isShown &&
                    scroll.isLaidOut && body.isLaidOut && !scroll.isLayoutRequested && !body.isLayoutRequested && layout != null &&
                    layout.width == body.width - body.totalPaddingLeft - body.totalPaddingRight && reading.chapter.text.contentEquals(body.text) &&
                    abs(body.textSize - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, state.fontSize.toFloat(), body.resources.displayMetrics)) < .5f &&
                    (oldView == null || (scroll !== oldView && !oldView.isAttachedToWindow))
            }
            match && viewReady && model.state.value.reading?.identity == reading.identity &&
                compose.onAllNodes(hasTestTag("reader_restore_loading")).fetchSemanticsNodes().isEmpty()
        }
        attachedDraw(current.findViewById<ScrollView>(R.id.reader_native_scroll))
        assertEquals(expectedOffset, model.state.value.reading!!.visibleScalarOffset)
    }
    private fun assertNativeAnchor(current: MainActivity) {
        assertFalse(current.readerViewModel.state.value.controlsVisible) // No overlay may hide the line counted as visible.
        val text = current.readerViewModel.state.value.reading!!.chapter.text
        val utf16 = ReaderTextCoordinates.utf16Offset(text, expectedOffset)
        compose.runOnUiThread {
            val body = current.findViewById<TextView>(R.id.reader_native_text); val scroll = current.findViewById<ScrollView>(R.id.reader_native_scroll)
            val layout = body.layout ?: throw AssertionError("Native Layout absent")
            assertTrue(text.contentEquals(body.text))
            val visible = Rect(); assertTrue(scroll.getGlobalVisibleRect(visible))
            val location = IntArray(2).also(scroll::getLocationOnScreen)
            assertTrue(visible.intersect(location[0] + scroll.paddingLeft, location[1] + scroll.paddingTop,
                location[0] + scroll.width - scroll.paddingRight, location[1] + scroll.height - scroll.paddingBottom))
            val origin = IntArray(2).also(body::getLocationOnScreen); val line = layout.getLineForOffset(utf16)
            val top = origin[1] + body.totalPaddingTop - body.scrollY + layout.getLineTop(line)
            val bottom = origin[1] + body.totalPaddingTop - body.scrollY + layout.getLineBottom(line)
            assertTrue("Exact original scalar's native line must be visible", top < visible.bottom && bottom > visible.top)
        }
    }
    private fun attachedDraw(view: View) {
        val drawn = AtomicBoolean(false); lateinit var observer: ViewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener { if (view.isAttachedToWindow && view.isShown) drawn.set(true) }
        compose.runOnUiThread { assertTrue(view.isAttachedToWindow && view.isShown); observer = view.viewTreeObserver; observer.addOnDrawListener(listener); view.invalidate() }
        try { compose.waitUntil(5_000) { drawn.get() }; instrumentation.runOnMainSync { assertTrue(view.isAttachedToWindow && view.isShown) } }
        finally { compose.runOnUiThread { if (observer.isAlive) observer.removeOnDrawListener(listener) } }
    }
    private fun assertPreferencesRestored(stage: String = "display-preferences-restored") {
        val expected = original ?: return; val s = activity().readerViewModel.state.value
        assertEquals(expected.theme, s.theme); assertEquals(expected.paper, s.paper); assertEquals(expected.fontSize, s.fontSize)
        marker(stage, Bundle().apply { putString("theme", s.theme.name); putString("paper", s.paper.name); putInt("fontSize", s.fontSize) })
    }
    private fun restoreThroughUiAndFinish() {
        marker("teardown-enter")
        var failure: Throwable? = null
        fun retain(error: Throwable) { if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error) }
        try {
            var current = currentOrNull()
            if (homeIssued && !frontIssued && current != null) {
                bringToFrontOnce(current)
                compose.waitUntil(5_000) { currentOrNull()?.lifecycle?.currentState == Lifecycle.State.RESUMED }
                current = activity()
            }
            val expected = original
            if (current != null && expected != null && !failed(current.readerViewModel) && !current.readerViewModel.state.value.busy) {
                val model = current.readerViewModel
                if (model.state.value.route == ReaderRoute.Reading && (model.state.value.paper != expected.paper || model.state.value.fontSize != expected.fontSize)) {
                    controls()
                    if (model.state.value.paper != expected.paper) selectPaper(expected.paper)
                    if (model.state.value.fontSize != expected.fontSize) changeFont(expected.fontSize)
                }
                if (model.state.value.route == ReaderRoute.Settings && model.state.value.theme != expected.theme)
                    selectTheme(expected.theme, "theme-cleanup-restored")
                assertPreferencesRestored("teardown-display-preferences-confirmed")
            }
        } catch (cleanup: Throwable) { retain(cleanup) }
        finally {
            try { restoreRequestedOrientation(requireResumed = false) } catch (cleanup: Throwable) { retain(cleanup) }
            try {
                val current = currentOrNull()
                if (current != null && scenario!!.state != Lifecycle.State.DESTROYED) {
                    val destroyed = CountDownLatch(1)
                    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
                    try {
                        scenario!!.onActivity { it.lifecycle.addObserver(observer); it.finish() }
                        assertTrue("Owned activity did not naturally finish", destroyed.await(8, TimeUnit.SECONDS))
                        instrumentation.runOnMainSync { assertTrue(current.isDestroyed) }
                    } finally { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
                }
                if (scenario != null) {
                    assertEquals("No EmptyActivity fallback after failed finish", Lifecycle.State.DESTROYED, scenario!!.state)
                    scenario!!.close()
                }
                marker("activity-destroyed")
            } catch (cleanup: Throwable) { retain(cleanup) }
        }
        failure?.let { throw it }
        marker("teardown-exit")
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerMethod", METHOD); fields.putString("readerStep", step); fields.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        fields.putInt("readerProcessPid", android.os.Process.myPid()); fields.putInt("readerProcessUid", android.os.Process.myUid())
        instrumentation.sendStatus(2, fields)
    }
    companion object { private const val METHOD = "existingRealTxtDisplayConfigurationBackgroundAndSystemBackPreservePosition" }
}
