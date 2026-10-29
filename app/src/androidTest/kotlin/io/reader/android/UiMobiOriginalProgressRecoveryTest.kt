package io.reader.android

import android.app.KeyguardManager
import android.os.Bundle
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.reader.android.runtime.ProgressReceiptPolicy
import io.reader.android.ui.ReaderRoute
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

/** One explicit recovery of the retained original ACK. No reading, import, save or Core replay. */
@RunWith(AndroidJUnit4::class)
class UiMobiOriginalProgressRecoveryTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(60, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test fun existingRealMobiOriginalStoredAckArchivesWithoutReadingOrNewSave() {
        marker("method-enter") // Before Activity access.
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            assertEquals(BASELINE_SHA, args.getString("baselineProofSha256"))
            assertEquals(SOURCE_SHA, args.getString("expectedSHA")); assertEquals(PENDING_ID, args.getString("expectedPendingId"))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || model.state.value.error != null || model.state.value.protocolRecoveryReference != null) }
            val initial = model.state.value
            assertTrue(initial.ready && initial.pendingReady && !initial.busy)
            assertNull(initial.error); assertNull(initial.protocolRecoveryReference)
            assertNull(initial.reading); assertEquals(ReaderRoute.Bookshelf, initial.route); assertEquals(0L, initial.importRevision)
            assertTrue(initial.pendingImports.isEmpty() && initial.pendingBookmarks.isEmpty()); assertNull(initial.pendingSearchClose)
            val pending = initial.pendingPositions.single()
            assertEquals(PENDING_ID, pending.id); assertEquals(bookId, pending.bookId); assertEquals("reading.progress.update", pending.stage)
            val book = initial.books.single { it.id == bookId }
            assertEquals(560, book.chapterCount); assertEquals(3, book.progressChapter); assertEquals(0, book.progressOffset)
            val protected = initial.books.single { it.id == "local:$TXT_SHA" }
            val protectedEpub = initial.books.single { it.id == "local:$EPUB_SHA" }
            assertEquals(1, protected.progressChapter); assertEquals(210, protected.progressOffset)
            assertEquals(2, protectedEpub.progressChapter); assertEquals(69, protectedEpub.progressOffset)
            val activeFile = File(current.filesDir, "Reader/Operations/$PENDING_ID.json")
            val candidateFile = File(activeFile.parentFile, activeFile.name + ".new")
            val completedFile = File(current.filesDir, "Reader/Operations/Completed/$PENDING_ID.json")
            val bundlePath = "OriginalWireCandidates/$PENDING_ID-$CANDIDATE_SHA.json"
            val bundleFile = File(current.filesDir, "Reader/Operations/$bundlePath")
            assertFalse(bundleFile.exists())
            assertFalse(completedFile.exists()); assertFalse(File(completedFile.parentFile, completedFile.name + ".new").exists())
            // Read raw evidence only. AtomicFile.openRead must not discard the existing .new candidate.
            val activeRaw = activeFile.readBytes(); val candidateRaw = candidateFile.readBytes()
            assertEquals(739, activeRaw.size); assertEquals(ACTIVE_SHA, sha(activeRaw))
            assertEquals(1300, candidateRaw.size); assertEquals(CANDIDATE_SHA, sha(candidateRaw))
            val active = JSONObject(activeRaw.toString(Charsets.UTF_8)); val candidate = JSONObject(candidateRaw.toString(Charsets.UTF_8))
            for (record in listOf(active, candidate)) {
                assertEquals(1, record.getInt("journalSchema")); assertEquals("progress", record.getString("kind"))
                assertEquals(PENDING_ID, record.getString("id")); assertEquals(bookId, record.getString("bookId")); assertFalse(record.getBoolean("completed"))
                assertEquals(1012L, record.getLong("requestId")); assertEquals(ORIGINAL_RUNTIME, record.getString("runtimeSession"))
            }
            assertFalse(active.has("wireTerminal"))
            val candidateWithoutWire = JSONObject(candidate.toString()).apply { remove("wireTerminal") }
            assertEquals(active.toString(), candidateWithoutWire.toString())
            val parameters = active.getJSONObject("parameters"); val wire = candidate.getJSONObject("wireTerminal")
            assertEquals(3, parameters.getInt("chapterIndex")); assertEquals(0, parameters.getInt("chapterOffset"))
            assertEquals(BODY, parameters.getString("expectedBodyVersion")); assertEquals(PROCESSING, parameters.getString("expectedProcessingVersion"))
            assertEquals(EXPECTED_REVISION, parameters.getString("expectedProgressRevision"))
            assertEquals("result", wire.getString("type")); assertEquals(1012L, wire.getLong("requestId")); assertEquals(13L, wire.getLong("deliveryId"))
            assertTrue(wire.getJSONObject("data").getBoolean("stored")); assertEquals(ACK_REVISION, ProgressReceiptPolicy.revision(candidate, wire))
            val delegate = field(model, "libraryDelegate") as Lazy<*>
            assertTrue(delegate.isInitialized()) // Inspect the already initialized VM client; never mint another Library/runtime.
            val library = checkNotNull(delegate.value)
            val client = checkNotNull(field(library, "client"))
            val appOwner = checkNotNull(field(client, "owner"))
            val actorDelegate = field(appOwner, "actorDelegate") as Lazy<*>
            assertTrue(actorDelegate.isInitialized())
            // Immutable UUID of the existing initialized actor only; no transport.session getter/gate/RPC.
            val currentRuntime = field(checkNotNull(actorDelegate.value), "runtimeSession") as String
            assertNotEquals(ORIGINAL_RUNTIME, currentRuntime)
            assertTrue((field(library, "readingSessions") as Map<*, *>).isEmpty())
            val beforeCompleted = completedNames()
            marker("mobi-original-progress-pending-bound", Bundle().apply {
                putString("bookId", bookId); putString("originalId", PENDING_ID); putLong("requestId", 1012L)
                putString("runtimeSession", ORIGINAL_RUNTIME); putString("currentRuntimeSession", currentRuntime)
                putString("activeRecordSha256", sha(activeRaw)); putString("candidateRecordSha256", sha(candidateRaw))
                putInt("activeRecordBytes", activeRaw.size); putInt("candidateRecordBytes", candidateRaw.size)
                putString("originalParameters", parameters.toString()); putString("originalWire", wire.toString())
                putString("expectedRevision", EXPECTED_REVISION); putString("ackRevision", ACK_REVISION)
                putString("bodyVersion", BODY); putString("processingVersion", PROCESSING)
                putInt("chapter", 3); putInt("scalarOffset", 0); putLong("importRevision", 0)
            })
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue(UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse(checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            compose.onNodeWithTag("reader_reconcile_position").assertIsDisplayed().assertIsEnabled()
            marker("mobi-original-progress-ui-reconcile-click", Bundle().apply { putString("originalId", PENDING_ID) })
            phase = "one-original-position-ui-reconcile"
            compose.onNodeWithTag("reader_reconcile_position").performClick() // The only action; no VM/Library business invocation.
            compose.waitUntil(15_000) {
                val state = model.state.value
                !state.busy && (state.pendingPositions.isEmpty() || state.error != null || state.protocolRecoveryReference != null)
            }
            healthy(model)
            assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            assertEquals(initial.books, model.state.value.books)
            assertTrue((field(library, "readingSessions") as Map<*, *>).isEmpty())
            assertEquals(beforeCompleted + "$PENDING_ID.json", completedNames())
            assertFalse(activeFile.exists()); assertFalse(candidateFile.exists())
            assertFalse(File(completedFile.parentFile, completedFile.name + ".new").exists())
            val completedRaw = completedFile.readBytes(); val completed = JSONObject(completedRaw.toString(Charsets.UTF_8))
            assertEquals(PENDING_ID, completed.getString("id")); assertEquals("progress", completed.getString("kind"))
            assertEquals(bookId, completed.getString("bookId")); assertTrue(completed.getBoolean("completed"))
            assertEquals(1012L, completed.getLong("requestId")); assertEquals(ORIGINAL_RUNTIME, completed.getString("runtimeSession"))
            assertEquals(parameters.toString(), completed.getJSONObject("parameters").toString())
            assertEquals(wire.toString(), completed.getJSONObject("wireTerminal").toString())
            assertEquals(ACK_REVISION, ProgressReceiptPolicy.revision(completed, completed.getJSONObject("wireTerminal")))
            val provenance = completed.getJSONObject("originalWireCandidate")
            assertEquals(setOf("path", "bytes", "sha256", "activeSha256", "candidateSha256"), provenance.keys().asSequence().toSet())
            assertEquals(bundlePath, provenance.getString("path"))
            assertEquals(ACTIVE_SHA, provenance.getString("activeSha256")); assertEquals(CANDIDATE_SHA, provenance.getString("candidateSha256"))
            val bundleRaw = bundleFile.readBytes(); val bundle = JSONObject(bundleRaw.toString(Charsets.UTF_8))
            assertEquals(bundleRaw.size.toLong(), provenance.getLong("bytes")); assertEquals(sha(bundleRaw), provenance.getString("sha256"))
            assertEquals(setOf("schemaVersion", "operationId", "activeSha256", "candidateSha256", "activeBytes", "candidateBytes", "activeBase64", "candidateBase64"), bundle.keys().asSequence().toSet())
            assertEquals(1, bundle.getInt("schemaVersion")); assertEquals(PENDING_ID, bundle.getString("operationId"))
            assertEquals(ACTIVE_SHA, bundle.getString("activeSha256")); assertEquals(CANDIDATE_SHA, bundle.getString("candidateSha256"))
            assertEquals(739, bundle.getInt("activeBytes")); assertEquals(1300, bundle.getInt("candidateBytes"))
            val keptActive = Base64.decode(bundle.getString("activeBase64"), Base64.NO_WRAP)
            val keptCandidate = Base64.decode(bundle.getString("candidateBase64"), Base64.NO_WRAP)
            assertArrayEquals(activeRaw, keptActive); assertArrayEquals(candidateRaw, keptCandidate)
            assertEquals(Base64.encodeToString(keptActive, Base64.NO_WRAP), bundle.getString("activeBase64"))
            assertEquals(Base64.encodeToString(keptCandidate, Base64.NO_WRAP), bundle.getString("candidateBase64"))
            val withoutProvenance = JSONObject(completed.toString()).apply { remove("originalWireCandidate"); put("completed", false) }
            assertEquals(candidate.toString(), withoutProvenance.toString())
            marker("mobi-original-progress-reconciled", Bundle().apply {
                putString("bookId", bookId); putString("originalId", PENDING_ID); putLong("requestId", 1012L)
                putString("runtimeSession", ORIGINAL_RUNTIME); putString("currentRuntimeSession", currentRuntime)
                putString("activeRecordSha256", sha(activeRaw)); putString("candidateRecordSha256", sha(candidateRaw))
                putString("originalRecordSha256", sha(completedRaw)); putInt("originalRecordBytes", completedRaw.size)
                putString("candidateBundlePath", "files/Reader/Operations/$bundlePath"); putString("candidateBundleSha256", sha(bundleRaw)); putInt("candidateBundleBytes", bundleRaw.size)
                putString("originalParameters", completed.getJSONObject("parameters").toString()); putString("originalWire", completed.getJSONObject("wireTerminal").toString())
                putString("expectedRevision", EXPECTED_REVISION); putString("ackRevision", ACK_REVISION)
                putInt("chapter", 3); putInt("scalarOffset", 0); putBoolean("newSaveDispatched", false)
                putBoolean("readingOpened", false); putLong("importRevision", model.state.value.importRevision)
            })
            compose.waitForIdle(); compose.onNodeWithTag("reader_shelf_query").assertIsDisplayed()
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            try { finishOnlyBookshelf() }
            catch (cleanup: Throwable) { if (primary == null) throw cleanup else if (primary !== cleanup) primary.addSuppressed(cleanup) }
        }
        marker("method-exit")
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun healthy(model: ReaderViewModel) {
        val state = model.state.value
        assertNull(state.error); assertNull(state.protocolRecoveryReference)
        assertTrue(state.pendingImports.isEmpty() && state.pendingPositions.isEmpty() && state.pendingBookmarks.isEmpty())
        assertNull(state.pendingSearchClose); assertEquals(0L, state.importRevision)
    }
    private fun completedNames() = File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty()
        .filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }
    private fun finishOnlyBookshelf() {
        val owned = scenario ?: return
        if (owned.state != Lifecycle.State.DESTROYED) {
            val model = current.readerViewModel; val state = model.state.value
            if (state.reading != null || state.busy || state.route != ReaderRoute.Bookshelf || state.pendingPositions.isNotEmpty() || state.error != null || state.protocolRecoveryReference != null) {
                marker("activity-retained-for-controller"); throw AssertionError("Original state retained; no business cleanup/second action")
            }
            healthy(model)
            val destroyed = CountDownLatch(1)
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
            var primary: Throwable? = null
            try {
                instrumentation.runOnMainSync { current.lifecycle.addObserver(observer); current.finish() }
                assertTrue(destroyed.await(5, TimeUnit.SECONDS))
                compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                try { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
                catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
            }
        }
        assertEquals(Lifecycle.State.DESTROYED, owned.state); owned.close(); marker("activity-destroyed")
    }
    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step); details.putString("readerMethod", METHOD)
        details.putInt("readerProcessPid", AndroidProcess.myPid()); details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, details)
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    companion object {
        private const val METHOD = "existingRealMobiOriginalStoredAckArchivesWithoutReadingOrNewSave"
        private const val SOURCE_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
        private const val TXT_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val EPUB_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val BASELINE_SHA = "4f22816be131d2b60de10be5afbe84f17057f8452df47e59a9f817aca8b0e529"
        private const val PENDING_ID = "android-progress-3851fa03-2892-4487-8cda-9e8875409311"
        private const val ACTIVE_SHA = "574b36dff73ad4dbd9c7efcccf3227527e468fe77a9fae837f27a3ce3a90abd2"
        private const val CANDIDATE_SHA = "60c798f57c349bff0a1019f3c33217afac7d2cf16395574eed21cad0b9609200"
        private const val ORIGINAL_RUNTIME = "7eedfbbe-4e17-469c-b8e9-bef9c783afc7"
        private const val EXPECTED_REVISION = "reader-progress-v1:cec125191e9e6313955ec6702403f8ae48acd1839ecbed53efb0036cab1e034b"
        private const val ACK_REVISION = "reader-progress-v1:87d4f15dce0a9abdd5f366a4ed21341ff96b52923e48d5c9dac2d4c4c3007221"
        private const val BODY = "415874d4786a5439c1573e59b44c5d4cfe532881698dc19094c1e7c766671aac"
        private const val PROCESSING = "76a2ba63994809ce07bf9f73e31bcd85ad96b78dcba6ac57b0f5a011e97fcc14"
    }
}
