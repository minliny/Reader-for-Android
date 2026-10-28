package io.reader.android

import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
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
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.ui.ReaderChapterIndexKey
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderScalarOffsetKey
import io.reader.android.ui.ReaderViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Ignored adoption draft. Same instrumentation process, actual VM replacement; no import or write-in-flight claim. */
@RunWith(AndroidJUnit4::class)
class UiAppOwnerLifecycleTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val phase = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            marker("rule-enter")
            try { base.evaluate() } finally { marker("rule-exit") }
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phase).around(compose)

    @Test
    fun existingRealTxtTwoActivitiesRetainAppOwnerAndPosition() {
        marker("method-enter")
        val args = InstrumentationRegistry.getArguments()
        val sha = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required from stopped proof")
        val chapter = args.getString("expectedChapter")?.toIntOrNull() ?: throw AssertionError("expectedChapter required")
        val offset = args.getString("expectedOffset")?.toIntOrNull() ?: throw AssertionError("expectedOffset required")
        assertTrue(sha.matches(Regex("[a-f0-9]{64}")) && chapter >= 0 && offset >= 0)
        val bookId = "local:$sha"
        val pid = Process.myPid()
        val first = runOneActivity("first", bookId, chapter, offset, null, null)
        assertTrue("Actual old Library close must prove VM.onCleared/client retirement", field(first.library, "closed").let { it as AtomicBoolean }.get())
        assertTrue(field(first.client, "retired").let { it as AtomicBoolean }.get())
        marker("first-vm-cleared-client-retired")
        val second = runOneActivity("second", bookId, chapter, offset, first, pid)
        assertSame(first.application, second.application)
        assertSame(first.owner, second.owner)
        assertSame(first.actor, second.actor)
        assertEquals(first.runtimeSession, second.runtimeSession)
        assertNotSame(first.model, second.model)
        assertNotSame(first.library, second.library)
        assertNotSame(first.client, second.client)
        assertNotEquals(first.clientId, second.clientId)
        assertEquals(first.ownerId, second.ownerId)
        assertTrue(second.ownerGeneration > first.ownerGeneration)
        assertNotSame(first.nativeView, second.nativeView)
        assertFalse(first.nativeView.isAttachedToWindow)
        assertEquals(pid, Process.myPid())
        marker("same-process-owner-continuity-ACK", Bundle().apply {
            putInt("processPid", pid); putString("runtimeSession", second.runtimeSession)
            putString("oldClientId", first.clientId); putString("newClientId", second.clientId)
            putLong("oldOwnerGeneration", first.ownerGeneration); putLong("newOwnerGeneration", second.ownerGeneration)
        })
        marker("method-exit")
    }

    private data class Snapshot(val application: Any, val owner: Any, val actor: Any, val runtimeSession: String,
        val model: ReaderViewModel, val library: Any, val client: Any, val ownerId: String, val clientId: String,
        val ownerGeneration: Long, val nativeView: ScrollView)

    private fun runOneActivity(label: String, bookId: String, chapter: Int, offset: Int,
        previous: Snapshot?, expectedPid: Int?): Snapshot {
        var scenario: ActivityScenario<MainActivity>? = null
        var activity: MainActivity? = null
        var primary: Throwable? = null
        try {
            marker("$label-launch-request")
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity = it }
            val current = activity ?: throw AssertionError("Actual MainActivity absent")
            if (expectedPid != null) assertEquals(expectedPid, Process.myPid())
            val model = current.readerViewModel
            compose.waitUntil(20_000) {
                val s = model.state.value
                !s.busy && (s.ready || s.error != null || s.protocolRecoveryReference != null)
            }
            val state = model.state.value
            assertTrue(state.ready)
            assertNull(state.error)
            assertNull(state.protocolRecoveryReference)
            assertEquals(0L, state.importRevision)
            assertTrue(state.pendingReady && state.pendingImports.isEmpty() && state.pendingPositions.isEmpty() && state.pendingBookmarks.isEmpty())
            assertNull(state.pendingSearchClose)
            val book = state.books.singleOrNull { it.id == bookId } ?: throw AssertionError("Original existing book absent")
            assertEquals(chapter, book.progressChapter)
            assertEquals(offset, book.progressOffset)
            // Read only initialized lazy values. Never invoke Application.coreOwner,
            // client.transport.session or another getter that could construct Native.
            val owner = initializedLazy(current.application, "coreOwner\$delegate")
            val actor = initializedLazy(owner, "actorDelegate")
            val library = initializedLazy(model, "libraryDelegate")
            val client = field(library, "client")
            val lease = field(client, "lease")
            val runtimeSession = field(actor, "runtimeSession") as String
            if (previous != null) {
                assertSame(previous.application, current.application)
                assertSame(previous.owner, owner)
                assertSame(previous.actor, actor)
                assertEquals(previous.runtimeSession, runtimeSession)
                assertNotSame(previous.model, model)
                assertNotSame(previous.client, client)
            }
            marker("$label-core-owner-ready", Bundle().apply {
                putInt("processPid", Process.myPid()); putString("runtimeSession", runtimeSession)
            })
            compose.onNodeWithTag("reader_book_$bookId").performClick()
            compose.waitUntil(20_000) {
                val s = model.state.value
                !s.busy && (s.route == ReaderRoute.Reading || s.error != null || s.protocolRecoveryReference != null)
            }
            assertNull(model.state.value.error)
            assertNull(model.state.value.protocolRecoveryReference)
            assertEquals(ReaderRoute.Reading, model.state.value.route)
            val reading = model.state.value.reading ?: throw AssertionError("Actual reader absent")
            assertEquals(chapter, reading.chapter.index)
            assertEquals(offset, reading.restoreScalarOffset)
            assertEquals(offset, reading.visibleScalarOffset)
            val native = nativeReady(current, model, previous?.nativeView)
            assertNativeLine(current, reading.chapter.text, offset)
            marker("$label-native-line-ACK")
            showExitAndSave(current, model, bookId, chapter, offset)
            val saved = model.state.value.books.single { it.id == bookId }
            marker("$label-exit-save-ACK", Bundle().apply {
                putString("bookId", saved.id)
                putInt("chapter", saved.progressChapter)
                putInt("scalarOffset", saved.progressOffset)
            })
            return Snapshot(current.application, owner, actor, runtimeSession, model, library, client,
                field(lease, "ownerId").toString(), field(lease, "clientId").toString(), field(lease, "ownerGeneration") as Long, native)
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", label); putString("message", failure.message?.take(256)) }) }
            catch (markerFailure: Throwable) { failure.addSuppressed(markerFailure) }
            throw failure
        } finally {
            try { scenario?.let { finishAndClose(it, activity, label) } }
            catch (cleanupFailure: Throwable) { if (primary != null) primary.addSuppressed(cleanupFailure) else throw cleanupFailure }
        }
    }

    private fun finishAndClose(scenario: ActivityScenario<MainActivity>, activity: MainActivity?, label: String) {
        marker("$label-teardown-enter")
        val destroyed = CountDownLatch(1)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
        var primary: Throwable? = null
        try {
            if (scenario.state != Lifecycle.State.DESTROYED) {
                scenario.onActivity { it.lifecycle.addObserver(observer); it.finish() }
                assertTrue("Actual Activity did not finish", destroyed.await(10, TimeUnit.SECONDS))
            }
            instrumentation.runOnMainSync {
                if (activity != null) { assertTrue(activity.isDestroyed); assertFalse(activity.isChangingConfigurations) }
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            marker("$label-activity-destroyed")
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try { instrumentation.runOnMainSync { activity?.lifecycle?.removeObserver(observer) } }
            catch (cleanupFailure: Throwable) { if (primary != null) primary.addSuppressed(cleanupFailure) else primary = cleanupFailure }
            try { scenario.close() } // Already naturally destroyed; no extra bootstrap/EmptyActivity is required.
            catch (closeFailure: Throwable) { if (primary != null) primary.addSuppressed(closeFailure) else primary = closeFailure }
            if (primary != null) throw primary
        }
        marker("$label-teardown-exit")
    }

    private fun nativeReady(activity: MainActivity, model: ReaderViewModel, previous: ScrollView?): ScrollView {
        val expected = model.state.value
        val reading = expected.reading ?: throw AssertionError("No native reader owner")
        var result: ScrollView? = null
        compose.waitUntil(10_000) {
            val roots = compose.onAllNodes(hasTestTag("reader_reading")).fetchSemanticsNodes()
            val semantics = roots.size == 1 && ReaderChapterIndexKey in roots.single().config && ReaderScalarOffsetKey in roots.single().config &&
                roots.single().config[ReaderChapterIndexKey] == reading.chapter.index && roots.single().config[ReaderScalarOffsetKey] == reading.visibleScalarOffset
            val ready = compose.runOnUiThread {
                val scroll = activity.findViewById<ScrollView>(R.id.reader_native_scroll)
                val text = activity.findViewById<TextView>(R.id.reader_native_text)
                val layout = text?.layout
                val valid = scroll != null && text != null && scroll.isAttachedToWindow && text.isAttachedToWindow &&
                    scroll.isShown && text.isShown && scroll.isLaidOut && text.isLaidOut && !scroll.isLayoutRequested && !text.isLayoutRequested &&
                    scroll !== previous && previous?.isAttachedToWindow != true && layout != null &&
                    layout.width == text.width - text.totalPaddingLeft - text.totalPaddingRight && reading.chapter.text.contentEquals(text.text) &&
                    abs(text.textSize - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, expected.fontSize.toFloat(), text.resources.displayMetrics)) < .5f
                if (valid) result = scroll
                valid
            }
            semantics && ready && model.state.value.reading?.identity == reading.identity &&
                compose.onAllNodes(hasTestTag("reader_restore_loading")).fetchSemanticsNodes().isEmpty()
        }
        val view = result ?: throw AssertionError("Actual native view absent")
        attachedDraw(view)
        assertEquals(reading.identity, model.state.value.reading!!.identity)
        return view
    }
    private fun assertNativeLine(activity: MainActivity, text: String, scalar: Int) {
        val utf16 = ReaderTextCoordinates.utf16Offset(text, scalar)
        compose.runOnUiThread {
            val body = activity.findViewById<TextView>(R.id.reader_native_text)
            val scroll = activity.findViewById<ScrollView>(R.id.reader_native_scroll)
            assertTrue(body.isAttachedToWindow && scroll.isAttachedToWindow)
            assertTrue(text.contentEquals(body.text))
            val layout = body.layout ?: throw AssertionError("Native Layout absent")
            val viewport = Rect()
            assertTrue(scroll.getGlobalVisibleRect(viewport))
            val scrollLocation = IntArray(2).also(scroll::getLocationOnScreen)
            assertTrue(viewport.intersect(scrollLocation[0] + scroll.paddingLeft, scrollLocation[1] + scroll.paddingTop,
                scrollLocation[0] + scroll.width - scroll.paddingRight, scrollLocation[1] + scroll.height - scroll.paddingBottom))
            val line = layout.getLineForOffset(utf16)
            val location = IntArray(2).also(body::getLocationOnScreen)
            val top = location[1] + body.totalPaddingTop - body.scrollY + layout.getLineTop(line)
            val bottom = location[1] + body.totalPaddingTop - body.scrollY + layout.getLineBottom(line)
            assertTrue("Original scalar's actual native line must be visible", top < viewport.bottom && bottom > viewport.top)
        }
    }
    private fun showExitAndSave(activity: MainActivity, model: ReaderViewModel, bookId: String, chapter: Int, offset: Int) {
        if (!model.state.value.controlsVisible) compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.waitUntil(5_000) { model.state.value.controlsVisible && enabledExit() }
        attachedDraw(activity.window.decorView)
        assertTrue(enabledExit())
        compose.onNodeWithTag("reader_exit").performClick()
        compose.waitUntil(20_000) {
            val s = model.state.value
            !s.busy && (s.route == ReaderRoute.Bookshelf || s.error != null || s.protocolRecoveryReference != null || s.pendingPositions.isNotEmpty())
        }
        val state = model.state.value
        assertEquals(ReaderRoute.Bookshelf, state.route)
        assertNull(state.error)
        assertNull(state.protocolRecoveryReference)
        assertTrue(state.pendingPositions.isEmpty() && state.pendingImports.isEmpty() && state.pendingBookmarks.isEmpty())
        assertNull(state.pendingSearchClose)
        assertEquals(0L, state.importRevision)
        val saved = state.books.single { it.id == bookId }
        assertEquals(chapter, saved.progressChapter)
        assertEquals(offset, saved.progressOffset)
    }
    private fun enabledExit(): Boolean {
        val nodes = compose.onAllNodes(hasTestTag("reader_exit")).fetchSemanticsNodes()
        return nodes.size == 1 && SemanticsProperties.Disabled !in nodes.single().config &&
            nodes.single().boundsInRoot.width > 0f && nodes.single().boundsInRoot.height > 0f
    }
    private fun attachedDraw(view: View) {
        val drawn = AtomicBoolean(false)
        var observer: ViewTreeObserver? = null
        var primary: Throwable? = null
        val listener = ViewTreeObserver.OnDrawListener { if (view.isAttachedToWindow && view.isShown) drawn.set(true) }
        try {
            compose.runOnUiThread {
                assertTrue(view.isAttachedToWindow && view.isShown)
                observer = view.viewTreeObserver
                observer!!.addOnDrawListener(listener)
                view.invalidate()
            }
            compose.waitUntil(5_000) { drawn.get() }
            compose.runOnUiThread { assertTrue(view.isAttachedToWindow && view.isShown) }
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try { compose.runOnUiThread { observer?.takeIf { it.isAlive }?.removeOnDrawListener(listener) } }
            catch (cleanupFailure: Throwable) {
                val failure = primary
                if (failure != null) {
                    if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
                } else throw cleanupFailure
            }
        }
    }
    private fun initializedLazy(instance: Any, name: String): Any {
        val lazy = field(instance, name) as Lazy<*>
        assertTrue("Observation must not initialize $name", lazy.isInitialized())
        return lazy.value ?: throw AssertionError("Initialized $name is null")
    }
    private fun field(instance: Any, name: String): Any = instance.javaClass.getDeclaredField(name).let {
        it.isAccessible = true; it.get(instance) ?: throw AssertionError("Expected initialized field $name")
    }
    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step)
        details.putString("readerMethod", "existingRealTxtTwoActivitiesRetainAppOwnerAndPosition")
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        instrumentation.sendStatus(2, details)
    }
}
