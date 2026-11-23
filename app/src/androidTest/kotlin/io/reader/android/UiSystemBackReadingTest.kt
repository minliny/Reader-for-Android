package io.reader.android

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.reader.android.runtime.ProgressReceiptPolicy
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.ui.ReaderChapterIndexKey
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderScalarOffsetKey
import io.reader.android.ui.ReaderViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Source-only: one system Back from the unchanged existing TXT anchor, no other daily journey. */
@RunWith(AndroidJUnit4::class)
class UiSystemBackReadingTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private var scenario: ActivityScenario<MainActivity>? = null
    private var readingOwner: Long? = null
    private lateinit var bookId: String
    private var expectedChapter = -1
    private var expectedOffset = -1
    @get:Rule val rules: TestRule = RuleChain.outerRule(Timeout.builder().withTimeout(60, TimeUnit.SECONDS).build()).around(compose)

    @Test fun existingRealTxtSystemBackSavesOriginalPositionOnce() {
        marker("method-enter")
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            val hash = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required from latest stopped proof")
            expectedChapter = args.getString("expectedChapter")?.toIntOrNull() ?: throw AssertionError("expectedChapter required")
            expectedOffset = args.getString("expectedOffset")?.toIntOrNull() ?: throw AssertionError("expectedOffset required")
            assertEquals("aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5", hash)
            assertEquals(1, expectedChapter); assertEquals(210, expectedOffset)
            bookId = "local:$hash"
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val current = activity(); val model = current.readerViewModel
            waitHealthyReady(model)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.reading)
            val originalBooks = model.state.value.books
            val original = originalBooks.single { it.sourceId == "local" && it.id == bookId }
            assertEquals(expectedChapter, original.progressChapter); assertEquals(expectedOffset, original.progressOffset)
            val before = receiptIds(current)
            marker("existing-position-bound", Bundle().apply {
                putString("bookId", bookId); putInt("chapter", expectedChapter); putInt("scalarOffset", expectedOffset)
            })

            phase = "open-existing-anchor"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            compose.onNodeWithTag("reader_book_$bookId").performClick()
            compose.waitUntil(10_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model)
            nativeReady(current); assertNativeAnchor(current)
            assertFalse(model.state.value.controlsVisible)
            assertEquals("Opening the unchanged anchor must not create a progress save", before, receiptIds(current))
            marker("existing-native-anchor-visible")

            phase = "single-system-back"
            marker("system-back-dispatch")
            val dispatchedAt = SystemClock.elapsedRealtime()
            // Locked UiAutomator 2.3.0 returns whether a CONTENT_CHANGED event arrived.
            // This single returned Boolean is an observation, never the original write ACK.
            val returned = device.pressBack()
            marker("system-back-returned", Bundle().apply {
                putBoolean("pressBackReturned", returned)
                putLong("pressBackElapsedMs", SystemClock.elapsedRealtime() - dispatchedAt)
                putInt("systemBackCalls", 1)
            })

            phase = "original-save-and-shelf"
            compose.waitUntil(10_000) {
                !model.state.value.busy && (model.state.value.route == ReaderRoute.Bookshelf || failed(model))
            }
            healthy(model)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.reading)
            val projected = model.state.value.books.single { it.id == bookId && it.sourceId == "local" }
            assertEquals(original, projected)
            // A real lastReadAt update may legitimately reorder the shelf.
            assertEquals(originalBooks.associateBy { it.sourceId to it.id },
                model.state.value.books.associateBy { it.sourceId to it.id })
            awaitReceipt(current, before, "system-back-save-ACK")
            assertEquals("Exactly one original save is admitted", 1, (receiptIds(current) - before).size)
            compose.onNodeWithTag("reader_shelf_query").assertIsDisplayed()
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed()
            marker("bookshelf-original-position-visible")
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("message", failure.message?.take(256)) }) }
            catch (markerFailure: Throwable) { if (markerFailure !== failure) failure.addSuppressed(markerFailure) }
            throw failure
        } finally {
            try { finishOwnedBookshelf() }
            catch (cleanup: Throwable) {
                val failure = primary
                if (failure == null) throw cleanup
                if (failure !== cleanup) failure.addSuppressed(cleanup)
            }
        }
        marker("method-exit")
    }

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

    /** No second Back, model exit, display mutation or finish while a reading write is pending. */
    private fun finishOwnedBookshelf() {
        val owned = scenario ?: return
        if (owned.state == Lifecycle.State.DESTROYED) {
            owned.close(); marker("activity-destroyed"); return
        }
        val current = currentOrNull() ?: throw AssertionError("Owned Activity absent before confirmed destruction")
        val state = current.readerViewModel.state.value
        if (state.busy || state.reading != null || state.route != ReaderRoute.Bookshelf || failed(current.readerViewModel)) {
            marker("activity-retained-for-controller")
            throw AssertionError("Activity retained with original state; controller may stop only after instrumentation exits")
        }
        val destroyed = CountDownLatch(1)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
        var added = false
        var primary: Throwable? = null
        try {
            instrumentation.runOnMainSync {
                val latest = current.readerViewModel.state.value
                assertFalse(latest.busy); assertNull(latest.reading); assertEquals(ReaderRoute.Bookshelf, latest.route)
                current.lifecycle.addObserver(observer); added = true; current.finish()
            }
            assertTrue("Owned bookshelf Activity did not naturally finish", destroyed.await(5, TimeUnit.SECONDS))
            compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            assertEquals(Lifecycle.State.DESTROYED, owned.state)
            owned.close() // Close only the already destroyed scenario; no fallback Activity on failure.
            marker("activity-destroyed")
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            if (added) try { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
            catch (cleanup: Throwable) {
                val failure = primary
                if (failure == null) throw cleanup
                if (failure !== cleanup) failure.addSuppressed(cleanup)
            }
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerStep", step)
        fields.putString("readerMethod", "existingRealTxtSystemBackSavesOriginalPositionOnce")
        fields.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        instrumentation.sendStatus(2, fields)
    }
}
