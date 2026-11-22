package io.reader.android

import android.app.KeyguardManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.reader.android.runtime.ReaderLocalBookFormat
import io.reader.android.ui.ReaderEbookUiMount
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import io.reader.android.ui.ebookdraft.ReaderEbookRenderInput
import kotlinx.coroutines.Job
import org.json.JSONObject
import org.json.JSONArray
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

/** One actual Mobi104 Ready/draw and disjoint footer frame. Retained barrier; zero grant/pan/save/reconcile. */
@RunWith(AndroidJUnit4::class)
class UiEbookFooterLayoutTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(90, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun existingMobiRealBodyViewportAndFooterAreDisjointWithoutPositionWrites() {
        marker("method-enter")
        var primary: Throwable? = null
        var phase = "actual-post-install-baseline"
        var completed: Set<String>? = null
        try {
            val args = InstrumentationRegistry.getArguments()
            val baselineProofSha = args.getString("baselineProofSha256")
                ?: throw AssertionError("Actual stopped preservation baseline is required")
            assertTrue("Actual preservation baseline is PENDING", baselineProofSha.matches(Regex("[a-f0-9]{64}")))
            assertEquals(SOURCE_SHA, args.getString("expectedSHA")); assertEquals(UNKNOWN_ID, args.getString("expectedPendingId"))
            scenario = ActivityScenario.launch(MainActivity::class.java); scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            healthy(model, 0)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.reading)
            val book = model.state.value.books.single { it.id == bookId }
            assertEquals(560, book.chapterCount); assertEquals(3, book.progressChapter); assertEquals(104, book.progressOffset)
            val unchangedCompleted = EbookJourneyProbe.completedOperationNames(current); completed = unchangedCompleted; assertNoRestoreWrite(unchangedCompleted)
            marker("ebook-footer-layout-input-bound", Bundle().apply {
                putString("baselineProofSha256", baselineProofSha); putString("bookId", bookId); putString("sourceSHA", SOURCE_SHA)
                putInt("chapter", 3); putInt("scalarOffset", 104); putBoolean("newGrantDispatched", false); putBoolean("newSaveDispatched", false)
            })
            val raw = assertUnknownUnchanged(); val unknown = JSONObject(raw.toString(Charsets.UTF_8))
            marker("ebook-footer-layout-pending-original-bound", Bundle().apply {
                putString("bookId", bookId); putString("originalId", UNKNOWN_ID); putString("originalRecordSha256", sha(raw)); putInt("originalRecordBytes", raw.size)
                putLong("requestId", unknown.getLong("requestId")); putString("runtimeSession", unknown.getString("runtimeSession"))
                putBoolean("noWire", true); putBoolean("noCandidate", true); putBoolean("noCompleted", true)
            })
            phase = "one-bookshelf-continue-to-real-ready"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(30_000) { failed(model) || compose.runOnUiThread { EbookJourneyProbe.readyMount(current, SHELL) != null } }
            healthy(model, 0); compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            val mount = compose.runOnUiThread { checkNotNull(EbookJourneyProbe.readyMount(current, SHELL)) }; val ready = checkNotNull(mount.ready)
            val web = compose.runOnUiThread { EbookJourneyProbe.webView(current, SHELL) }; val reading = checkNotNull(model.state.value.reading)
            assertEquals(ReaderLocalBookFormat.MOBI, reading.format); assertEquals(3, reading.chapter.index)
            assertEquals(104, reading.visibleScalarOffset); assertEquals(104, reading.restoreScalarOffset); assertTrue(reading.chapter.text.any { !it.isWhitespace() })
            assertFalse(model.state.value.controlsVisible); assertEquals(UNKNOWN_ID, model.state.value.positionSaveBarrier?.id)
            assertNull(reading.newPositionIntent); assertFalse(reading.newPositionIntentPresented); assertNoRestoreWrite(unchangedCompleted)
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView); EbookJourneyProbe.awaitDraw(compose, web); compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("reader_ebook_viewport").assertIsDisplayed()
            compose.onNodeWithTag("reader_ebook_footer").assertIsDisplayed().assertTextEquals("4 / 560")
            val viewport = compose.onNodeWithTag("reader_ebook_viewport").fetchSemanticsNode().boundsInWindow
            val footer = compose.onNodeWithTag("reader_ebook_footer").fetchSemanticsNode().boundsInWindow
            assertTrue(viewport.width > 0 && viewport.height > 0 && footer.width > 0 && footer.height > 0)
            assertTrue("Footer still overlaps the actual body viewport", viewport.bottom <= footer.top + 1f)
            assertTrue(footer.left >= viewport.left - 1f && footer.right <= viewport.right + 1f)
            val actual = compose.runOnUiThread {
                assertSame(mount, EbookJourneyProbe.readyMount(current, SHELL)); assertEquals(ready, mount.ready); assertSame(web, EbookJourneyProbe.webView(current, SHELL))
                checkNotNull(mount.preparation).requireCurrent()
                val location = IntArray(2).also(web::getLocationInWindow)
                val frame = Rect(location[0], location[1], location[0] + web.width, location[1] + web.height)
                assertTrue(frame.width() > 0 && frame.height() > 0)
                assertTrue(frame.left >= viewport.left - 1f && frame.right <= viewport.right + 1f && frame.top >= viewport.top - 1f && frame.bottom <= viewport.bottom + 1f)
                assertTrue("Native WebView still paints in footer frame", frame.bottom <= footer.top + 1f)
                val owner = checkNotNull(field(web.parent, "owner")); val input = field(owner, "input") as ReaderEbookRenderInput
                assertEquals(ready.lease, field(owner, "lease")); assertEquals("Ready", field(owner, "phase").toString())
                JSONObject().put("webLeft", frame.left).put("webTop", frame.top).put("webRight", frame.right).put("webBottom", frame.bottom)
                    .put("endInsetPx", input.geometry.bottomPx).put("viewportLeft", viewport.left).put("viewportTop", viewport.top)
                    .put("viewportRight", viewport.right).put("viewportBottom", viewport.bottom).put("footerLeft", footer.left)
                    .put("footerTop", footer.top).put("footerRight", footer.right).put("footerBottom", footer.bottom)
            }
            marker("ebook-footer-layout-native-ready-disjoint", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", 3); putInt("scalarOffset", ready.scalar); putString("runtimeSession", ready.lease.runtimeSession)
                putString("ownerId", ready.lease.ownerId.toString()); putString("clientId", ready.lease.clientId.toString()); putLong("ownerGeneration", ready.lease.ownerGeneration)
                putString("physicalViewId", mount.view.physicalViewId.toString()); putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("processedChapterSha256", sha(reading.chapter.text.toByteArray(Charsets.UTF_8))); putString("geometryJson", actual.toString())
                putBoolean("bodyFooterDisjoint", true); putBoolean("newIntentAbsent", true); putBoolean("lastAttemptAbsent", true)
                putBoolean("noPan", true); putBoolean("noSave", true); putLong("importRevision", 0)
            })
            assertNoRestoreWrite(unchangedCompleted)
            phase = "one-current-real-layout-screenshot"
            val screen = File(current.cacheDir, "reader-a7-ebook-footer-${mount.view.physicalViewId}-draw.png")
            marker("ebook-footer-layout-screenshot", Bundle().apply {
                putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name)
            })
            assertNoRestoreWrite(unchangedCompleted)
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            if (primary == null) { finishUnderOriginalBarrier(); assertNoRestoreWrite(checkNotNull(completed)) }
            else marker("activity-retained-for-controller")
        }
        marker("method-exit")
    }

    private fun assertNoRestoreWrite(before: Set<String>) {
        assertEquals(before, EbookJourneyProbe.completedOperationNames(current)); assertUnknownUnchanged()
        assertEquals(setOf("$UNKNOWN_ID.json"), File(current.filesDir, "Reader/Operations").listFiles().orEmpty().filter { it.isFile }.mapTo(linkedSetOf()) { it.name })
        assertTrue(File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty().none { it.name.endsWith(".new") || it.name.endsWith(".bak") })
        val model = current.readerViewModel
        assertNull(model.state.value.lastPositionSaveAttempt)
        model.state.value.reading?.let { assertNull(it.newPositionIntent); assertFalse(it.newPositionIntentPresented) }
        val observer = compose.runOnUiThread { field(model, "delayedSave") as? Job }
        assertTrue(observer == null || observer.isCompleted)
    }

    private fun finishUnderOriginalBarrier() {
        val owned = checkNotNull(scenario); val model = current.readerViewModel; val reading = checkNotNull(model.state.value.reading)
        assertNull(reading.newPositionIntent); assertFalse(reading.newPositionIntentPresented)
        assertEquals(UNKNOWN_ID, model.state.value.positionSaveBarrier?.id)
        assertFalse(model.state.value.busy); healthy(model, 0)
        val destroyed = CountDownLatch(1)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
        var primary: Throwable? = null
        try {
            instrumentation.runOnMainSync { current.lifecycle.addObserver(observer); current.finish() }
            assertTrue("Original Activity did not naturally finish", destroyed.await(5, TimeUnit.SECONDS))
            compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
            catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
        }
        assertEquals(Lifecycle.State.DESTROYED, owned.state); owned.close(); marker("activity-destroyed")
    }

    private fun assertUnknownUnchanged(): ByteArray {
        val root = File(current.filesDir, "Reader/Operations")
        val file = File(root, "$UNKNOWN_ID.json")
        val raw = file.readBytes(); assertEquals(739, raw.size); assertEquals(UNKNOWN_SHA, sha(raw))
        assertFalse(JSONObject(raw.toString(Charsets.UTF_8)).has("wireTerminal"))
        assertFalse(File(root, "$UNKNOWN_ID.json.new").exists()); assertFalse(File(root, "$UNKNOWN_ID.json.bak").exists())
        assertFalse(File(root, "Completed/$UNKNOWN_ID.json").exists()); assertFalse(File(root, "Completed/$UNKNOWN_ID.json.new").exists())
        return raw
    }

    /** Serialize only actual issued immutable DTO values; this never constructs or authorizes a capability. */

    private fun failed(model: ReaderViewModel): Boolean = model.state.value.let {
        it.error != null || it.protocolRecoveryReference != null || it.pendingImports.isNotEmpty() || (it.pendingReady && (it.pendingPositions.map { pending -> pending.id } != listOf(UNKNOWN_ID) || it.pendingPositions.singleOrNull()?.bookId != bookId)) ||
            it.pendingBookmarks.isNotEmpty() || it.pendingSearchClose != null || (it.ebookFailureOwner != null && it.ebookFailureOwner == it.reading?.identity)
    }

    private fun healthy(model: ReaderViewModel, revision: Long) {
        assertTrue(model.state.value.pendingReady); assertFalse("Unexpected operation failed/pending", failed(model))
        assertEquals(listOf(UNKNOWN_ID), model.state.value.pendingPositions.map { it.id }); assertEquals(bookId, model.state.value.pendingPositions.single().bookId)
        assertEquals(revision, model.state.value.importRevision)
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step); details.putString("readerMethod", METHOD)
        details.putInt("readerProcessPid", AndroidProcess.myPid()); details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, details)
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val METHOD = "existingMobiRealBodyViewportAndFooterAreDisjointWithoutPositionWrites"
        private const val SOURCE_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
        private const val UNKNOWN_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val UNKNOWN_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
        private const val SHELL = "https://reader-ebook.invalid/__reader/shell.html"
    }
}
