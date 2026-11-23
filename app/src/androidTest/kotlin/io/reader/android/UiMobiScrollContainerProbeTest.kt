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
import io.reader.android.runtime.ProgressReceiptPolicy
import io.reader.android.runtime.ReaderLocalBookFormat
import io.reader.android.ui.ReaderEbookUiMount
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import io.reader.android.ui.ebookdraft.ReaderEbookReady
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
import kotlin.math.abs

/** One real swipe under the retained barrier. All new data is observation, never a Core ACK. */
@RunWith(AndroidJUnit4::class)
class UiMobiScrollContainerProbeTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(90, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun existingMobiRetainedUnknownObservesOneRealSwipeScrollContainerWithoutWriting() {
        marker("method-enter")
        var primary: Throwable? = null
        var phase = "actual-stopped-baseline"
        var originalCompleted: Set<String>? = null
        try {
            val args = InstrumentationRegistry.getArguments()
            assertEquals(BASELINE_SHA, args.getString("baselineProofSha256"))
            assertEquals(SOURCE_SHA, args.getString("expectedSHA")); assertEquals(UNKNOWN_ID, args.getString("expectedPendingId"))
            scenario = ActivityScenario.launch(MainActivity::class.java); scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            healthy(model, 0)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.reading)
            assertNull(model.state.value.lastPositionSaveAttempt)
            val book = model.state.value.books.single { it.id == bookId }
            assertEquals(560, book.chapterCount); assertEquals(3, book.progressChapter); assertEquals(0, book.progressOffset)
            val unknownRaw = assertUnknownUnchanged(); val unknown = JSONObject(unknownRaw.toString(Charsets.UTF_8))
            originalCompleted = EbookJourneyProbe.completedOperationNames(current); assertNoRestoreWrite(originalCompleted)
            marker("mobi-scroll-pending-original-bound", Bundle().apply {
                putString("bookId", bookId); putString("originalId", UNKNOWN_ID); putString("originalRecordSha256", sha(unknownRaw))
                putLong("originalRecordBytes", unknownRaw.size.toLong()); putLong("requestId", unknown.getLong("requestId"))
                putString("runtimeSession", unknown.getString("runtimeSession")); putBoolean("noWire", true); putBoolean("noCandidate", true)
                putBoolean("noCompleted", true); putBoolean("newGrantDispatched", false); putBoolean("recoveryDispatched", false); putLong("importRevision", 0)
            })
            phase = "one-bookshelf-continue-to-real-ready"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(30_000) { failed(model) || compose.runOnUiThread { EbookJourneyProbe.readyMount(current, SHELL) != null } }
            healthy(model, 0); compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            val mount = compose.runOnUiThread { checkNotNull(EbookJourneyProbe.readyMount(current, SHELL)) }; val ready = checkNotNull(mount.ready)
            val web = compose.runOnUiThread { EbookJourneyProbe.webView(current, SHELL) }; val reading = checkNotNull(model.state.value.reading)
            assertEquals(ReaderLocalBookFormat.MOBI, reading.format); assertEquals(3, reading.chapter.index); assertEquals(0, reading.visibleScalarOffset)
            assertEquals(UNKNOWN_ID, model.state.value.positionSaveBarrier?.id); assertNull(reading.newPositionIntent)
            assertFalse(reading.newPositionIntentPresented); assertFalse(model.state.value.controlsVisible)
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView); EbookJourneyProbe.awaitDraw(compose, web)
            compose.waitForIdle()
            compose.runOnUiThread { assertSame(mount, EbookJourneyProbe.readyMount(current, SHELL)); assertSame(web, EbookJourneyProbe.webView(current, SHELL)); assertEquals(ready, mount.ready) }
            val before = scrollMetrics(web); assertFalse(before.has("probeError"))
            val gesture = before.getJSONObject("gesture")
            assertTrue(gesture.getBoolean("nonLinkStart")); assertTrue(before.getBoolean("selectionCollapsed"))
            assertEquals("auto", before.getJSONObject("frame").getString("pointerEvents"))
            assertNoRestoreWrite(originalCompleted)
            metricMarker("mobi-scroll-before-metrics", mount, ready, before, nativeState(mount, web))
            phase = "one-real-ui-swipe"
            val points = compose.runOnUiThread {
                checkNotNull(mount.preparation).requireCurrent(); assertSame(mount, field(model, "ebookMount")); assertSame(web, EbookJourneyProbe.webView(current, SHELL))
                val location = IntArray(2).also(web::getLocationOnScreen); val visible = Rect(); assertTrue(web.getGlobalVisibleRect(visible))
                val density = web.resources.displayMetrics.density.toDouble(); val win = before.getJSONObject("window"); val frame = before.getJSONObject("frame")
                assertTrue(abs(win.getDouble("innerWidth") * density - web.width) <= 1.1 * density)
                assertTrue(abs(win.getDouble("innerHeight") * density - web.height) <= 1.1 * density)
                val x = location[0] + ((frame.getDouble("left") + gesture.getDouble("x")) * density).toInt()
                val from = location[1] + ((frame.getDouble("top") + gesture.getDouble("startY")) * density).toInt()
                val to = location[1] + ((frame.getDouble("top") + gesture.getDouble("endY")) * density).toInt()
                assertTrue(visible.contains(x, from) && visible.contains(x, to) && from - to > web.height / 4)
                intArrayOf(x, from, to)
            }
            assertTrue(UiDevice.getInstance(instrumentation).swipe(points[0], points[1], points[0], points[2], 35))
            marker("mobi-scroll-once-dispatched", Bundle().apply {
                putString("bookId", bookId); putBoolean("swipeDispatched", true); putInt("startX", points[0]); putInt("endX", points[0])
                putInt("startY", points[1]); putInt("endY", points[2]); putString("startHitTag", gesture.getString("startHitTag")); putString("endHitTag", if (gesture.isNull("endHitTag")) null else gesture.getString("endHitTag"))
                putBoolean("newGrantDispatched", false); putBoolean("positionSaveDispatched", false)
            })
            phase = "visual-callback-draw-and-read-only-after-metrics"
            visualThenDraw(web)
            val after = scrollMetrics(web); assertFalse(after.has("probeError"))
            metricMarker("mobi-scroll-after-metrics", mount, ready, after, nativeState(mount, web))
            // No assumption that a scroll occurred, settled, or emitted a JS position message.
            assertNoRestoreWrite(originalCompleted)
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            if (primary == null) { finishUnderOriginalBarrier(); assertNoRestoreWrite(checkNotNull(originalCompleted)) }
            else marker("activity-retained-for-controller")
        }
        marker("method-exit")
    }

    private fun scrollMetrics(web: WebView): JSONObject {
        val latch = CountDownLatch(1); var value: String? = null
        compose.runOnUiThread { web.evaluateJavascript(SCROLL_READ_ONLY) { value = it; latch.countDown() } }
        assertTrue("Read-only scroll metrics callback absent", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(value ?: throw AssertionError("Scroll metrics callback returned null"))
    }
    private fun visualThenDraw(web: WebView) {
        val latch = CountDownLatch(1)
        compose.runOnUiThread { web.postVisualStateCallback(SystemClock.uptimeMillis(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { latch.countDown() }
        }) }
        assertTrue("Post-swipe visual callback absent", latch.await(5, TimeUnit.SECONDS)); EbookJourneyProbe.awaitDraw(compose, web)
    }
    private fun nativeState(mount: ReaderEbookUiMount, web: WebView): JSONObject = compose.runOnUiThread {
        val model = current.readerViewModel; val state = model.state.value; val owner = field(web.parent, "owner")
        val layout = owner?.let { field(it, "layout") }; val input = owner?.let { field(it, "input") as io.reader.android.ui.ebookdraft.ReaderEbookRenderInput }
        val anchors = owner?.let { field(it, "anchors") }; val reading = state.reading
        JSONObject().put("observedAtElapsedMs", SystemClock.elapsedRealtime()).put("ownerPresent", owner != null)
            .put("samePhysicalView", owner?.let { field(it, "view") === web } ?: false)
            .put("currentPhase", owner?.let { field(it, "phase").toString() } ?: JSONObject.NULL)
            .put("layoutEpoch", layout?.let { field(it, "epoch") } ?: JSONObject.NULL).put("layoutToken", layout?.let { field(it, "token") } ?: JSONObject.NULL)
            .put("inputUserScrollEnabled", input?.userScrollEnabled ?: JSONObject.NULL).put("inputCurrentAnchor", input?.currentAnchor ?: JSONObject.NULL)
            .put("forceLayout", owner?.let { field(it, "forceLayout") } ?: JSONObject.NULL).put("drawnEpoch", owner?.let { field(it, "drawnEpoch") } ?: JSONObject.NULL)
            .put("awaitingDraw", owner?.let { field(it, "awaitingDraw") != null } ?: JSONObject.NULL)
            .put("anchorsLastVisible", anchors?.let { field(it, "lastVisible") } ?: JSONObject.NULL)
            .put("anchorsDesired", anchors?.let { field(it, "desiredAnchor") } ?: JSONObject.NULL)
            .put("modelScalar", reading?.visibleScalarOffset ?: JSONObject.NULL).put("busy", state.busy).put("controlsVisible", state.controlsVisible)
            .put("positionSaveBarrier", state.positionSaveBarrier?.id ?: JSONObject.NULL).put("newPositionIntentPresent", reading?.newPositionIntent != null)
            .put("lastPositionSaveAttemptPresent", state.lastPositionSaveAttempt != null).put("readingOwner", reading?.identity ?: JSONObject.NULL)
            .put("sameReadingOwner", reading?.identity == mount.readingOwner).put("webScrollY", web.scrollY).put("webScrollX", web.scrollX)
    }
    private fun metricMarker(step: String, mount: ReaderEbookUiMount, ready: ReaderEbookReady, dom: JSONObject, native: JSONObject) {
        val domJson = dom.toString(); val nativeJson = native.toString()
        assertTrue(domJson.toByteArray(Charsets.UTF_8).size in 1..65536); assertTrue(nativeJson.toByteArray(Charsets.UTF_8).size in 1..65536)
        marker(step, Bundle().apply {
            putString("bookId", bookId); putString("originalId", UNKNOWN_ID); putString("currentRuntimeSession", ready.lease.runtimeSession)
            putString("renderId", ready.lease.renderId.toString()); putString("chapterSessionToken", mount.chapter.sessionToken)
            putLong("initialLayoutEpoch", ready.layoutEpoch); putString("initialLayoutToken", ready.layoutToken)
            putString("domMetricsJson", domJson); putString("nativeStateJson", nativeJson)
            putBoolean("noGrant", true); putBoolean("noNewSave", true); putBoolean("noRecovery", true)
        })
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

    companion object {
        private const val METHOD = "existingMobiRetainedUnknownObservesOneRealSwipeScrollContainerWithoutWriting"
        private const val SOURCE_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
        private const val UNKNOWN_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val UNKNOWN_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
        private const val BASELINE_SHA = "130ef937bcd064a861c8668392cb35ec28d1da7cc50417dbc80c73f89d0d8ecf"
        private const val SHELL = "https://reader-ebook.invalid/__reader/shell.html"
        private val SCROLL_READ_ONLY = """
            (()=>{try{
              const f=document.getElementById('publication'),d=f?.contentDocument,w=f?.contentWindow;
              if(!d?.body||!w)return {probeError:'NO_CURRENT_PUBLICATION_DOCUMENT'};
              const element=e=>{if(!e)return null;const c=w.getComputedStyle(e);return {tag:e.localName,scrollTop:e.scrollTop,scrollLeft:e.scrollLeft,
                scrollHeight:e.scrollHeight,scrollWidth:e.scrollWidth,clientHeight:e.clientHeight,clientWidth:e.clientWidth,overflowX:c.overflowX,overflowY:c.overflowY}};
              const x=w.innerWidth*.5,startY=Math.min(w.innerHeight-60,w.innerHeight*.78),endY=Math.max(40,w.innerHeight*.28),
                start=d.elementFromPoint(x,startY),end=d.elementFromPoint(x,endY),r=f.getBoundingClientRect();
              return {observedAtPerformanceMs:performance.now(),compatMode:d.compatMode,scrollingElementTag:d.scrollingElement?.localName??null,
                documentComplete:d.readyState==='complete',selectionCollapsed:w.getSelection()?.isCollapsed!==false,
                window:{scrollY:w.scrollY,scrollX:w.scrollX,innerWidth:w.innerWidth,innerHeight:w.innerHeight},html:element(d.documentElement),body:element(d.body),
                frame:{left:r.left,top:r.top,width:r.width,height:r.height,pointerEvents:getComputedStyle(f).pointerEvents},
                gesture:{x,startY,endY,startHitTag:start?.localName??null,endHitTag:end?.localName??null,
                  nonLinkStart:!!start&&!start.closest('a[href],button,input,textarea,select,[contenteditable]')}};
            }catch(e){return {probeError:String(e).slice(0,256)}}})()
        """.trimIndent()
    }
}
