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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ProgressReceiptPolicy
import io.reader.android.runtime.ReaderContentSearchSession
import io.reader.android.runtime.ReaderSearchCloseConfirmation
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.ui.ReaderChapterIndexKey
import io.reader.android.ui.ReaderPanel
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

/** Source-only: first real cursor page, one normal UI close, then one normal exit save. */
@RunWith(AndroidJUnit4::class)
class UiSearchCloseOriginalTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var readingOwner: Long? = null
    private lateinit var bookId: String
    private var expectedChapter = -1
    private var expectedOffset = -1
    @get:Rule val rules: TestRule = RuleChain.outerRule(Timeout.builder().withTimeout(75, TimeUnit.SECONDS).build()).around(compose)

    @Test fun existingRealTxtSearchCursorClosesWithOriginalConfirmation() {
        marker("method-enter")
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            val hash = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required from latest stopped proof")
            expectedChapter = args.getString("expectedChapter")?.toIntOrNull() ?: throw AssertionError("expectedChapter required")
            expectedOffset = args.getString("expectedOffset")?.toIntOrNull() ?: throw AssertionError("expectedOffset required")
            val query = args.getString("contentQuery") ?: "许府"
            assertEquals("aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5", hash)
            assertEquals(1, expectedChapter); assertEquals(210, expectedOffset); assertEquals("许府", query)
            bookId = "local:$hash"
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val current = activity(); val model = current.readerViewModel
            waitHealthyReady(model)
            val original = model.state.value.books.single { it.sourceId == "local" && it.id == bookId }
            assertEquals(expectedChapter, original.progressChapter); assertEquals(expectedOffset, original.progressOffset)
            val before = receiptIds(current)
            phase = "open-existing-native-anchor"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            compose.onNodeWithTag("reader_book_$bookId").performClick()
            compose.waitUntil(10_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model); nativeReady(current); assertNativeAnchor(current)
            val owner = model.state.value.reading!!.identity
            marker("existing-native-anchor-visible", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", owner); putInt("chapter", expectedChapter); putInt("scalarOffset", expectedOffset)
            })
            assertEquals(before, receiptIds(current))

            phase = "one-first-page-search"
            controls(model)
            compose.onNodeWithTag("reader_open_content_search").assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { model.state.value.readerPanel == ReaderPanel.Search }
            compose.onNodeWithTag("reader_content_query").performTextReplacement(query)
            compose.waitUntil(5_000) { model.state.value.searchQuery == query }
            assertNull(model.state.value.lastSearchCloseConfirmation)
            compose.onNodeWithTag("reader_content_search_submit").assertIsEnabled().performClick()
            compose.waitUntil(35_000) {
                val s = model.state.value
                !s.searchBusy && (s.searchReady || failed(model) || s.searchError != null)
            }
            healthy(model)
            val page = model.state.value
            assertNull(page.searchError); assertTrue(page.searchReady && page.searchResults.isNotEmpty() && page.searchHasMore)
            assertEquals(owner, page.reading!!.identity); assertEquals(expectedOffset, page.reading!!.visibleScalarOffset)
            val session = page.searchSession ?: throw AssertionError("Actual first search session absent")
            val cursor = page.searchCursor ?: throw AssertionError("Actual first-page next cursor absent")
            assertTrue(cursor.isNotEmpty() && session.id.isNotEmpty() && session.generation > 0 && session.runtimeSession.isNotEmpty())
            assertEquals("local", session.sourceId); assertEquals(bookId, session.bookId); assertEquals(query, session.query)
            assertTrue(page.searchResults.size in 1..session.pageSize)
            assertTrue(page.searchResults.all { it.sourceId == session.sourceId && it.bookId == session.bookId && it.sessionId == session.id })
            val firstHit = page.searchResults.first()
            compose.waitUntil(5_000) { compose.runOnUiThread {
                val list = current.findViewById<RecyclerView>(R.id.reader_native_search_list)
                val row = list?.findViewHolderForAdapterPosition(0)?.itemView
                val visible = Rect()
                list != null && list.isAttachedToWindow && list.adapter?.itemCount == page.searchResults.size + 1 &&
                    row != null && row.isAttachedToWindow && row.isShown && row.tag == "reader_content_hit_${firstHit.id}" && row.getGlobalVisibleRect(visible)
            } }
            val originalList = compose.runOnUiThread { checkNotNull(current.findViewById<RecyclerView>(R.id.reader_native_search_list)) }
            attachedDraw(originalList)
            marker("first-real-cursor-page-visible", Bundle().apply {
                putString("sessionJson", sessionJson(session).toString()); putString("cursor", cursor)
                putInt("resultCount", page.searchResults.size); putBoolean("hasMore", page.searchHasMore); putLong("readingOwner", owner)
            })
            assertEquals("Search must not write a progress or business journal", before, receiptIds(current))

            phase = "one-normal-ui-close"
            compose.onNodeWithTag("reader_close_panel").assertIsDisplayed().performClick() // Exactly one close dispatch.
            compose.waitUntil(10_000) {
                val s = model.state.value
                s.lastSearchCloseConfirmation?.scope?.session == session || failed(model)
            }
            healthy(model)
            val confirmation = model.state.value.lastSearchCloseConfirmation ?: throw AssertionError("Actual original close confirmation absent")
            assertOriginalClose(confirmation, session, cursor, owner)
            assertNull(model.state.value.searchCloseInFlight); assertNull(model.state.value.readerPanel)
            assertNull(model.state.value.searchSession); assertNull(model.state.value.searchCursor)
            assertTrue(model.state.value.searchResults.isEmpty() && !model.state.value.searchHasMore && !model.state.value.searchBusy)
            assertEquals(owner, model.state.value.reading!!.identity); assertEquals(expectedOffset, model.state.value.reading!!.visibleScalarOffset)
            compose.waitUntil(5_000) { compose.runOnUiThread { !originalList.isAttachedToWindow } && enabledExitPresent() }
            attachedDraw(current.window.decorView)
            val observation = confirmation.observation
            marker("original-search-close-confirmed", Bundle().apply {
                putLong("readingOwner", confirmation.scope.readingOwner); putLong("retirementGeneration", confirmation.scope.searchGeneration)
                putString("sessionJson", sessionJson(observation.session).toString()); putString("cursor", observation.cursor)
                putLong("requestId", observation.requestId); putString("runtimeSession", observation.runtimeSession)
                putString("parametersJson", observation.parametersJson); putString("parsedTerminalJson", observation.parsedTerminalJson)
                putString("terminalRepresentation", "PARSED_JSON_RESERIALIZED_NOT_BYTE_EXACT_NATIVE_RAW")
            })
            assertEquals("Closing the actual cursor is not a progress write", before, receiptIds(current))

            phase = "one-normal-exit-save"
            exitAndAssertReceipt(model)
            healthy(model); assertNull(model.state.value.reading)
            awaitReceipt(current, before, "exit-original-save-ACK")
            assertEquals("Only the normal exit's original save is admitted", 1, (receiptIds(current) - before).size)
            compose.onNodeWithTag("reader_shelf_query").assertIsDisplayed()
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed()
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

    private fun assertOriginalClose(value: ReaderSearchCloseConfirmation, session: ReaderContentSearchSession, cursor: String, owner: Long) {
        assertEquals(owner, value.scope.readingOwner); assertTrue(value.scope.searchGeneration > 0); assertEquals(session, value.scope.session)
        val receipt = value.observation
        assertEquals(session, receipt.session); assertEquals(session.runtimeSession, receipt.runtimeSession)
        assertEquals(cursor, receipt.cursor); assertTrue(receipt.requestId > 0)
        val params = JSONObject(receipt.parametersJson)
        assertEquals(setOf("sourceId", "bookId", "keyword", "maxResults", "cursor", "closeCursor"), params.keys().asSequence().toSet())
        assertEquals(session.sourceId, params.getString("sourceId")); assertEquals(session.bookId, params.getString("bookId"))
        assertEquals(session.query, params.getString("keyword")); assertEquals(session.pageSize, params.getInt("maxResults"))
        assertEquals(cursor, params.getString("cursor")); assertEquals(true, params.get("closeCursor"))
        val event = JSONObject(receipt.parsedTerminalJson)
        assertEquals("result", event.getString("type")); assertEquals(1, event.getInt("protocolVersion"))
        assertEquals(receipt.requestId, event.getLong("requestId")); assertTrue(event.getLong("deliveryId") > 0)
        val data = event.getJSONObject("data")
        assertEquals(0, data.getJSONArray("results").length())
        assertTrue(!data.has("nextCursor") || data.isNull("nextCursor"))
        assertTrue(!data.has("hasMore") || data.get("hasMore") == false)
    }

    private fun sessionJson(session: ReaderContentSearchSession) = JSONObject().put("id", session.id).put("generation", session.generation)
        .put("runtimeSession", session.runtimeSession).put("sourceId", session.sourceId).put("bookId", session.bookId)
        .put("query", session.query).put("pageSize", session.pageSize)

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

    private fun controls(model: ReaderViewModel) {
        val owner = model.state.value.reading!!.identity
        if (!model.state.value.controlsVisible) compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        // Native GestureDetector uses the real main Handler; a model flag alone
        // does not prove that StateFlow collection has committed the controls.
        compose.waitUntil(5_000) {
            model.state.value.reading?.identity == owner && model.state.value.controlsVisible && enabledExitPresent()
        }
        marker("controls-exit-enabled")
        attachedDraw(activity().window.decorView)
        assertEquals(owner, model.state.value.reading!!.identity)
        assertTrue("Actual enabled exit must survive the attached draw", enabledExitPresent())
        marker("controls-draw-ACK")
    }

    private fun enabledExitPresent(): Boolean {
        val nodes = compose.onAllNodes(hasTestTag("reader_exit")).fetchSemanticsNodes()
        return nodes.size == 1 && SemanticsProperties.Disabled !in nodes.single().config &&
            nodes.single().boundsInRoot.width > 0f && nodes.single().boundsInRoot.height > 0f
    }

    private fun exitAndAssertReceipt(model: ReaderViewModel) {
        controls(model)
        val captured = model.state.value.reading!!
        compose.onNodeWithTag("reader_exit").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Bookshelf && !model.state.value.busy }
        assertTrue(model.state.value.pendingPositions.isEmpty() && model.state.value.pendingBookmarks.isEmpty())
        assertEquals(null, model.state.value.pendingSearchClose)
        val saved = model.state.value.books.single { it.id == captured.book.id }
        assertEquals(captured.chapter.index, saved.progressChapter)
        assertEquals(captured.visibleScalarOffset, saved.progressOffset)
        assertEquals(0L, model.state.value.importRevision)
        marker("exit-bookshelf-visible")
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerStep", step); fields.putString("readerMethod", "existingRealTxtSearchCursorClosesWithOriginalConfirmation")
        fields.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, fields)
    }
}
