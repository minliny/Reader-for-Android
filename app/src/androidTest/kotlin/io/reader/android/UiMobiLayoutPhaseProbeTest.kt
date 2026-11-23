package io.reader.android

import android.app.KeyguardManager
import android.os.Bundle
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
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
import io.reader.android.ui.ReaderEbookUiMount
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import io.reader.android.ui.ebookdraft.ReaderEbookLayoutDiagnostics
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
import java.util.UUID

/** Observe this one layout phase only; the exact unknown original inhibits every save. */
@RunWith(AndroidJUnit4::class)
class UiMobiLayoutPhaseProbeTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(60, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test fun existingMobiUnknownPendingOpensOnlyToObserveLayoutPhaseWithoutSaving() {
        marker("method-enter")
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            assertEquals(BASELINE_SHA, args.getString("baselineProofSha256"))
            assertEquals(PENDING_ID, args.getString("expectedPendingId")); assertEquals(SOURCE_SHA, args.getString("expectedSHA"))
            scenario = ActivityScenario.launch(MainActivity::class.java); scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || model.state.value.error != null) }
            val initial = model.state.value
            assertTrue(initial.ready && initial.pendingReady && !initial.busy)
            assertNull(initial.error); assertNull(initial.protocolRecoveryReference); assertNull(initial.reading)
            assertEquals(ReaderRoute.Bookshelf, initial.route); assertEquals(0L, initial.importRevision)
            assertTrue(initial.pendingImports.isEmpty() && initial.pendingBookmarks.isEmpty()); assertNull(initial.pendingSearchClose)
            assertPending(model)
            val book = initial.books.single { it.id == bookId }
            assertEquals(560, book.chapterCount); assertEquals(3, book.progressChapter); assertEquals(0, book.progressOffset)
            val activeFile = File(current.filesDir, "Reader/Operations/$PENDING_ID.json")
            val activeRaw = activeFile.readBytes(); assertEquals(739, activeRaw.size); assertEquals(ACTIVE_SHA, sha(activeRaw))
            val active = JSONObject(activeRaw.toString(Charsets.UTF_8))
            assertEquals(PENDING_ID, active.getString("id")); assertEquals("progress", active.getString("kind"))
            assertEquals(bookId, active.getString("bookId")); assertFalse(active.getBoolean("completed"))
            assertEquals(1011L, active.getLong("requestId")); assertEquals(ORIGINAL_RUNTIME, active.getString("runtimeSession"))
            assertFalse(active.has("wireTerminal")); assertFalse(File(activeFile.path + ".new").exists())
            assertFalse(File(current.filesDir, "Reader/Operations/Completed/$PENDING_ID.json").exists())
            val completedBefore = completedNames()
            val delegate = field(model, "libraryDelegate") as Lazy<*>; assertTrue(delegate.isInitialized())
            val library = checkNotNull(delegate.value); val client = checkNotNull(field(library, "client"))
            val appOwner = checkNotNull(field(client, "owner")); val actorDelegate = field(appOwner, "actorDelegate") as Lazy<*>
            assertTrue(actorDelegate.isInitialized())
            val runtime = field(checkNotNull(actorDelegate.value), "runtimeSession") as String
            assertNotEquals(ORIGINAL_RUNTIME, runtime)
            marker("mobi-phase-pending-original-bound", Bundle().apply {
                identity(activeRaw); putBoolean("noWire", true); putBoolean("noCandidate", true); putLong("importRevision", 0)
            })
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue(UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse(checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            phase = "one-bookshelf-open-no-save"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            marker("mobi-phase-book-continue-click")
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed().performClick()
            var observed: ReaderEbookLayoutDiagnostics? = null
            var observedRender: String? = null
            phase = "observe-existing-owner-phase-only"
            compose.waitUntil(35_000) {
                compose.runOnUiThread {
                    if (observed == null) ownerDiagnostics(model)?.let { (trace, render) -> observed = trace; observedRender = render }
                    val outcome = observed?.snapshot()?.optString("outcome", "NONE")
                    outcome in setOf("NATIVE_READY", "LAYOUT_DEADLINE", "EARLIER_FAILURE") ||
                        model.state.value.let { state ->
                            val reading = state.reading
                            state.error != null || state.protocolRecoveryReference != null ||
                                (reading != null && state.ebookFailureOwner == reading.identity)
                        }
                }
            }
            assertPending(model); assertEquals(0L, model.state.value.importRevision)
            val snapshot: JSONObject
            val outcome: String
            val render: String
            val epoch: Long
            val token: String
            if (observed != null) {
                snapshot = observed!!.snapshot()
                outcome = snapshot.getString("outcome")
                assertTrue(outcome in setOf("NATIVE_READY", "LAYOUT_DEADLINE", "EARLIER_FAILURE"))
                render = snapshot.getString("renderId"); assertEquals(observedRender, render); assertEquals(render, UUID.fromString(render).toString())
                epoch = snapshot.getLong("layoutEpoch"); token = snapshot.getString("layoutToken")
                assertTrue("An observed Owner without a begun layout is retained as an unqualified probe failure", epoch > 0)
                assertEquals(token, UUID.fromString(token).toString())
            } else {
                val state = model.state.value
                assertTrue(state.error != null || state.protocolRecoveryReference != null || state.ebookFailureOwner != null)
                outcome = "EARLIER_FAILURE"; render = ""; epoch = 0; token = ""
                snapshot = JSONObject().put("available", false).put("reason", "OWNER_NOT_CREATED")
            }
            assertEquals(completedBefore, completedNames()); assertArrayEquals(activeRaw, activeFile.readBytes())
            assertFalse(File(activeFile.path + ".new").exists())
            assertFalse(File(current.filesDir, "Reader/Operations/Completed/$PENDING_ID.json").exists())
            val encoded = snapshot.toString(); assertTrue(encoded.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            marker("mobi-layout-phase-observed", Bundle().apply {
                identity(activeRaw); putString("currentRuntimeSession", runtime)
                putBoolean("renderObserved", observed != null); putString("renderId", render)
                putLong("layoutEpoch", epoch); putString("layoutToken", token)
                putString("diagnosticSnapshotJson", encoded); putString("outcome", outcome)
                putBoolean("noPan", true); putBoolean("noSave", true)
            })
            // No JS configure/probe, scroll, bodytap, normal-save exit, reconciliation or Library business call.
            phase = "standard-finish-with-exact-pending-guard"
            finishObservedActivity()
            assertEquals(completedBefore, completedNames()); assertArrayEquals(activeRaw, activeFile.readBytes())
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            try { if (scenario?.state != Lifecycle.State.DESTROYED && ::current.isInitialized) finishObservedActivity() }
            catch (cleanup: Throwable) { if (primary == null) throw cleanup else if (primary !== cleanup) primary.addSuppressed(cleanup) }
        }
        marker("method-exit")
    }

    /** Same read-only layout observation when an unrelated TXT progress journal is also pending. */
    @Test fun existingMobiUnknownPendingWithTxtPendingRetainsBothWithoutSaving() {
        marker("method-enter", method = METHOD_WITH_TXT)
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            assertEquals(BASELINE_WITH_TXT_SHA, args.getString("baselineProofSha256"))
            assertEquals(PENDING_ID, args.getString("expectedPendingId")); assertEquals(SOURCE_SHA, args.getString("expectedSHA"))
            scenario = ActivityScenario.launch(MainActivity::class.java); scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            val txtBookId = TXT_BOOK_ID
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || model.state.value.error != null) }
            val initial = model.state.value
            assertTrue(initial.ready && initial.pendingReady && !initial.busy)
            assertNull(initial.error); assertNull(initial.protocolRecoveryReference); assertNull(initial.reading)
            assertEquals(ReaderRoute.Bookshelf, initial.route); assertEquals(0L, initial.importRevision)
            assertTrue(initial.pendingImports.isEmpty() && initial.pendingBookmarks.isEmpty()); assertNull(initial.pendingSearchClose)
            assertPendingMobiAndTxt(model)
            val book = initial.books.single { it.id == bookId }
            val txtBook = initial.books.single { it.id == txtBookId }
            assertEquals(560, book.chapterCount); assertEquals(3, book.progressChapter); assertEquals(104, book.progressOffset)
            assertEquals(1, txtBook.progressChapter); assertEquals(210, txtBook.progressOffset)
            val activeFile = File(current.filesDir, "Reader/Operations/$PENDING_ID.json")
            val activeRaw = activeFile.readBytes(); assertEquals(739, activeRaw.size); assertEquals(ACTIVE_SHA, sha(activeRaw))
            val txtActiveFile = File(current.filesDir, "Reader/Operations/$TXT_PENDING_ID.json")
            val txtActiveRaw = txtActiveFile.readBytes(); assertEquals(760, txtActiveRaw.size); assertEquals(TXT_ACTIVE_SHA, sha(txtActiveRaw))
            val active = JSONObject(activeRaw.toString(Charsets.UTF_8))
            val txtActive = JSONObject(txtActiveRaw.toString(Charsets.UTF_8))
            assertEquals(PENDING_ID, active.getString("id")); assertFalse(active.getBoolean("completed"))
            assertEquals(bookId, active.getString("bookId")); assertEquals(1011L, active.getLong("requestId"))
            assertEquals(ORIGINAL_RUNTIME, active.getString("runtimeSession")); assertFalse(active.has("wireTerminal"))
            assertEquals(TXT_PENDING_ID, txtActive.getString("id")); assertFalse(txtActive.getBoolean("completed"))
            assertEquals(txtBookId, txtActive.getString("bookId")); assertEquals(1012L, txtActive.getLong("requestId"))
            assertEquals(TXT_ORIGINAL_RUNTIME, txtActive.getString("runtimeSession")); assertFalse(txtActive.has("wireTerminal"))
            assertFalse(File(activeFile.path + ".new").exists()); assertFalse(File(txtActiveFile.path + ".new").exists())
            assertFalse(File(current.filesDir, "Reader/Operations/Completed/$PENDING_ID.json").exists())
            assertFalse(File(current.filesDir, "Reader/Operations/Completed/$TXT_PENDING_ID.json").exists())
            val completedBefore = completedNames()
            val delegate = field(model, "libraryDelegate") as Lazy<*>; assertTrue(delegate.isInitialized())
            val library = checkNotNull(delegate.value); val client = checkNotNull(field(library, "client"))
            val appOwner = checkNotNull(field(client, "owner")); val actorDelegate = field(appOwner, "actorDelegate") as Lazy<*>
            assertTrue(actorDelegate.isInitialized())
            val runtime = field(checkNotNull(actorDelegate.value), "runtimeSession") as String
            assertNotEquals(ORIGINAL_RUNTIME, runtime); assertNotEquals(TXT_ORIGINAL_RUNTIME, runtime)
            marker("mobi-and-txt-pending-bound", Bundle().apply {
                putString("mobiBookId", bookId); putString("txtBookId", txtBookId)
                putString("mobiOriginalId", PENDING_ID); putString("txtOriginalId", TXT_PENDING_ID)
                putString("mobiOriginalSha256", sha(activeRaw)); putString("txtOriginalSha256", sha(txtActiveRaw))
                putBoolean("noWire", true); putBoolean("noCandidate", true); putLong("importRevision", 0)
            }, method = METHOD_WITH_TXT)
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue(UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse(checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            phase = "one-bookshelf-open-with-unrelated-pending"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            marker("mobi-book-continue-click", method = METHOD_WITH_TXT)
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed().performClick()
            var observed: ReaderEbookLayoutDiagnostics? = null
            var observedRender: String? = null
            phase = "observe-existing-owner-phase-with-two-pending"
            compose.waitUntil(35_000) {
                compose.runOnUiThread {
                    if (observed == null) ownerDiagnostics(model)?.let { (trace, render) -> observed = trace; observedRender = render }
                    val outcome = observed?.snapshot()?.optString("outcome", "NONE")
                    outcome in setOf("NATIVE_READY", "LAYOUT_DEADLINE", "EARLIER_FAILURE") ||
                        model.state.value.let { state ->
                            val reading = state.reading
                            state.error != null || state.protocolRecoveryReference != null ||
                                (reading != null && state.ebookFailureOwner == reading.identity)
                        }
                }
            }
            assertPendingMobiAndTxt(model); assertEquals(0L, model.state.value.importRevision)
            val snapshot: JSONObject
            val outcome: String
            val render: String
            val epoch: Long
            val token: String
            if (observed != null) {
                snapshot = observed!!.snapshot(); outcome = snapshot.getString("outcome")
                assertTrue(outcome in setOf("NATIVE_READY", "LAYOUT_DEADLINE", "EARLIER_FAILURE"))
                render = snapshot.getString("renderId"); assertEquals(observedRender, render); assertEquals(render, UUID.fromString(render).toString())
                epoch = snapshot.getLong("layoutEpoch"); token = snapshot.getString("layoutToken")
                assertTrue(epoch > 0); assertEquals(token, UUID.fromString(token).toString())
            } else {
                val state = model.state.value
                assertTrue(state.error != null || state.protocolRecoveryReference != null || state.ebookFailureOwner != null)
                outcome = "EARLIER_FAILURE"; render = ""; epoch = 0; token = ""
                snapshot = JSONObject().put("available", false).put("reason", "OWNER_NOT_CREATED")
            }
            assertEquals(completedBefore, completedNames())
            assertArrayEquals(activeRaw, activeFile.readBytes()); assertArrayEquals(txtActiveRaw, txtActiveFile.readBytes())
            assertFalse(File(activeFile.path + ".new").exists()); assertFalse(File(txtActiveFile.path + ".new").exists())
            val encoded = snapshot.toString(); assertTrue(encoded.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            marker("mobi-layout-phase-observed-with-txt-pending", Bundle().apply {
                putString("mobiBookId", bookId); putString("txtBookId", txtBookId)
                putBoolean("renderObserved", observed != null); putString("renderId", render)
                putLong("layoutEpoch", epoch); putString("layoutToken", token); putString("outcome", outcome)
                putString("diagnosticSnapshotJson", encoded); putBoolean("noSave", true); putBoolean("noReconcile", true)
            }, method = METHOD_WITH_TXT)
            phase = "standard-finish-with-two-pending-guard"
            finishObservedActivityWithMobiAndTxt()
            assertPendingMobiAndTxt(model); assertEquals(completedBefore, completedNames())
            assertArrayEquals(activeRaw, activeFile.readBytes()); assertArrayEquals(txtActiveRaw, txtActiveFile.readBytes())
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }, method = METHOD_WITH_TXT) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            try { if (scenario?.state != Lifecycle.State.DESTROYED && ::current.isInitialized) finishObservedActivityWithMobiAndTxt() }
            catch (cleanup: Throwable) { if (primary == null) throw cleanup else if (primary !== cleanup) primary.addSuppressed(cleanup) }
        }
        marker("method-exit", method = METHOD_WITH_TXT)
    }

    /** Actual prepared VM/mount and already-created WebView only; never mints a client or runs JS. */
    private fun ownerDiagnostics(model: ReaderViewModel): Pair<ReaderEbookLayoutDiagnostics, String>? {
        val reading = model.state.value.reading ?: return null
        val mount = field(model, "ebookMount") as? ReaderEbookUiMount ?: return null
        val prepared = mount.preparation ?: return null
        if (mount.readingOwner != reading.identity || mount.chapter.sessionToken != reading.chapter.sessionToken ||
            reading.chapter.bookId != bookId || reading.chapter.index != 3 || prepared.view !== mount.view ||
            prepared.publication.lease.renderId != mount.view.physicalViewId) return null
        fun descendants(view: View): List<WebView> = (if (view is WebView) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList())
        val web = descendants(current.window.decorView).filter { it.isAttachedToWindow }.singleOrNull() ?: return null
        val owner = field(web.parent, "owner") ?: return null
        if (field(owner, "view") !== web || field(owner, "lease") != prepared.publication.lease) return null
        return (field(owner, "diagnostics") as ReaderEbookLayoutDiagnostics) to mount.view.physicalViewId.toString()
    }
    private fun assertPending(model: ReaderViewModel) {
        val pending = model.state.value.pendingPositions.single()
        assertEquals(PENDING_ID, pending.id); assertEquals(bookId, pending.bookId)
    }
    private fun assertPendingMobiAndTxt(model: ReaderViewModel) {
        val pending = model.state.value.pendingPositions
        assertEquals(setOf(PENDING_ID, TXT_PENDING_ID), pending.map { it.id }.toSet())
        assertEquals(bookId, pending.single { it.id == PENDING_ID }.bookId)
        assertEquals(TXT_BOOK_ID, pending.single { it.id == TXT_PENDING_ID }.bookId)
    }
    private fun Bundle.identity(raw: ByteArray) {
        putString("bookId", bookId); putString("originalId", PENDING_ID); putString("originalRecordSha256", sha(raw))
        putInt("originalRecordBytes", raw.size); putLong("requestId", 1011L); putString("runtimeSession", ORIGINAL_RUNTIME)
    }
    private fun finishObservedActivity() {
        val owned = scenario ?: return
        if (owned.state != Lifecycle.State.DESTROYED) {
            assertPending(current.readerViewModel) // This existing guard suppresses onBackground and any exit persistence.
            val latch = CountDownLatch(1)
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) latch.countDown() }
            var primary: Throwable? = null
            try {
                instrumentation.runOnMainSync { current.lifecycle.addObserver(observer); current.finish() }
                assertTrue(latch.await(5, TimeUnit.SECONDS)); compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                try { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
                catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
            }
        }
        owned.close(); marker("activity-destroyed")
    }
    private fun finishObservedActivityWithMobiAndTxt() {
        val owned = scenario ?: return
        if (owned.state != Lifecycle.State.DESTROYED) {
            assertPendingMobiAndTxt(current.readerViewModel)
            val latch = CountDownLatch(1)
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) latch.countDown() }
            var primary: Throwable? = null
            try {
                instrumentation.runOnMainSync { current.lifecycle.addObserver(observer); current.finish() }
                assertTrue(latch.await(5, TimeUnit.SECONDS)); compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                try { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
                catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
            }
        }
        owned.close(); marker("activity-destroyed", method = METHOD_WITH_TXT)
    }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun completedNames() = File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty()
        .filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }
    private fun sha(raw: ByteArray) = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
    private fun marker(step: String, details: Bundle = Bundle(), method: String = METHOD) {
        details.putString("readerStep", step); details.putString("readerMethod", method)
        details.putInt("readerProcessPid", AndroidProcess.myPid()); details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, details)
    }
    companion object {
        private const val METHOD = "existingMobiUnknownPendingOpensOnlyToObserveLayoutPhaseWithoutSaving"
        private const val SOURCE_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
        private const val BASELINE_SHA = "63d71374b15b42c703b481454c0b5084a23710ed8eefcba387bcc55d41dd16e7"
        private const val PENDING_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val ACTIVE_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
        private const val ORIGINAL_RUNTIME = "aa519b17-bc13-4f47-841b-a31f9de9a1cc"
        private const val METHOD_WITH_TXT = "existingMobiUnknownPendingWithTxtPendingRetainsBothWithoutSaving"
        private const val BASELINE_WITH_TXT_SHA = "c887272a5361ff1722ca5b90f4bad3424a3513782d3585159d7a1acfc85e3a04"
        private const val TXT_PENDING_ID = "android-progress-20565269-a395-4ac4-be55-bdc9157d994e"
        private const val TXT_BOOK_ID = "local:aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val TXT_ACTIVE_SHA = "a26c7e82d3cec2ae66a7452df0247ca8589469eef9a4a936cfc505f48ebee063"
        private const val TXT_ORIGINAL_RUNTIME = "53c9e5c5-d75b-41e1-b362-dcd7bc2b2707"
    }
}
