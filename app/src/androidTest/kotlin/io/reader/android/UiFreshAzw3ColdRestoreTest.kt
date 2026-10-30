package io.reader.android

import android.app.KeyguardManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.semantics.SemanticsProperties
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

/** Fresh run f9105a05 only: cold shelf continuation, actual draw/DOM restore, then one ACK-bound normal exit for safe teardown. No import, directory, swipe, reset or recovery. */
@RunWith(AndroidJUnit4::class)
class UiFreshAzw3ColdRestoreTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    private var expectedScalar = -1
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(120, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun freshAliceAzw3ColdRestoresOriginalExitAnchor() {
        marker("method-enter") // Before Activity/library/owner access.
        var primary: Throwable? = null
        var phase = "mandatory-real-predecessor-input"
        var settledCompleted: Set<String>? = null
        var priorFile: File? = null
        var priorSha: String? = null
        var retainedFiles: Map<String, Pair<Long, String>>? = null
        try {
            val args = InstrumentationRegistry.getArguments()
            fun required(name: String): String = args.getString(name)?.takeIf { it.isNotBlank() && !it.contains("PENDING") }
                ?: throw AssertionError("Actual successful predecessor input required: $name")
            val baselineProofSha = required("baselineProofSha256").also { assertTrue(it.matches(Regex("[a-f0-9]{64}"))) }
            val priorId = required("expectedBaselineProgressId").also {
                assertEquals(BASELINE_ID, it)
                assertEquals(it.removePrefix("android-progress-"), java.util.UUID.fromString(it.removePrefix("android-progress-")).toString())
            }
            val baselineSha = required("expectedBaselineProgressRecordSha256").also { assertTrue(it.matches(Regex("[a-f0-9]{64}"))) }
            val priorBytes = required("expectedBaselineProgressRecordBytes").toLong().also { assertTrue(it in 1..8L * 1024 * 1024) }
            val priorRuntime = required("expectedBaselineRuntimeSession").also { assertEquals(it, java.util.UUID.fromString(it).toString()) }
            assertEquals(BASELINE_RUNTIME, priorRuntime)
            val priorRevision = required("expectedBaselineRevision").also { assertTrue(it.matches(Regex("reader-progress-v1:[a-f0-9]{64}"))) }
            expectedScalar = required("expectedBaselineScalar").toInt().also { assertEquals(519, it) }
            assertEquals(BASELINE_SHA, baselineSha); assertEquals(BASELINE_REVISION, priorRevision)
            assertEquals(BODY_VERSION, required("expectedBodyVersion")); assertEquals(PROCESSING_VERSION, required("expectedProcessingVersion"))
            assertEquals(IMPORT_ID, required("expectedImportId")); assertEquals(IMPORT_SHA, required("expectedImportRecordSha256"))
            val previousPid = required("previousProcessPid").toInt().also { assertTrue(it > 0) }
            val previousStart = required("previousProcessStartElapsedMs").toLong().also { assertTrue(it > 0) }
            assertEquals("alice-wonderland.azw3", required("displayName")); assertEquals(SOURCE_SHA, required("expectedSHA"))
            assertEquals(SOURCE_BYTES, required("expectedBytes").toLong())
            assertEquals(26581, previousPid); assertEquals(144762101L, previousStart)
            val start = AndroidProcess.getStartElapsedRealtime()
            assertTrue(AndroidProcess.myPid() > 0 && start > previousStart)
            assertFalse(AndroidProcess.myPid() == previousPid && start == previousStart) // Numeric PID reuse is legal.
            marker("azw3-cold-process-observed", Bundle().apply {
                putInt("previousProcessPid", previousPid); putLong("previousProcessStartElapsedMs", previousStart)
                putString("previousNewAckRuntimeSession", priorRuntime); putBoolean("distinctProcessIdentity", true)
                putString("processReferenceClaim", "FRESH_RUN_F9105A05_ACTUAL_PROCESS_TUPLE_NOT_UUID_ALONE")
            })
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            healthy(model, 0)
            val initial = model.state.value
            assertTrue(initial.pendingReady && initial.reading == null && initial.route == ReaderRoute.Bookshelf)
            assertNull(initial.lastPositionSaveAttempt)
            val otherBooks = initial.books.filter { it.id != bookId }.associateBy { it.key }
            val otherRemoteBooks = initial.remoteBooks
            val book = initial.books.single { it.id == bookId }
            assertEquals(16, book.chapterCount); assertEquals(4, book.progressChapter); assertEquals(expectedScalar, book.progressOffset)
            retainedFiles = retainedNonTargetFiles()
            val baselineFile = File(current.filesDir, "Reader/Operations/Completed/$priorId.json")
            val baselineRaw = safeReadCompleted(baselineFile).third
            assertEquals(priorBytes, baselineRaw.size.toLong()); assertEquals(baselineSha, sha(baselineRaw))
            val baseline = JSONObject(baselineRaw.toString(Charsets.UTF_8))
            assertEquals(priorId, baseline.getString("id")); assertEquals("progress", baseline.getString("kind"))
            assertTrue(baseline.getBoolean("completed")); assertEquals(bookId, baseline.getString("bookId"))
            assertEquals(priorRuntime, baseline.getString("runtimeSession"))
            val baselineWire = baseline.getJSONObject("wireTerminal"); val baselineParameters = baseline.getJSONObject("parameters")
            assertEquals(priorRevision, ProgressReceiptPolicy.revision(baseline, baselineWire))
            assertEquals(4, baselineParameters.getInt("chapterIndex")); assertEquals(expectedScalar, baselineParameters.getInt("chapterOffset"))
            assertFalse(File(current.filesDir, "Reader/Operations/$priorId.json").exists())
            assertFalse(File(baselineFile.parentFile, baselineFile.name + ".new").exists())
            priorFile = baselineFile; priorSha = baselineSha
            val importFile = File(current.filesDir, "Reader/Operations/Completed/$IMPORT_ID.json")
            val importedRecord = safeReadCompleted(importFile)
            assertEquals(IMPORT_SHA, sha(importedRecord.third))
            val imported = importedRecord.second
            assertEquals("import", imported.getString("kind")); assertTrue(imported.getBoolean("completed"))
            val toc = imported.getJSONObject("receipts").getJSONObject("persist").getJSONObject("data").getJSONObject("data").getJSONArray("toc")
            val sourceEntry = (0 until toc.length()).map { toc.getJSONObject(it) }.single { it.getInt("index") == 4 }
            assertEquals("CHAPTER I. Down the Rabbit-Hole", sourceEntry.getString("title"))
            val locatorRaw = sourceEntry.getJSONObject("variables").getString("readerEbookPresentationV1")
            val locator = JSONObject(locatorRaw); assertEquals(1, locator.getInt("version"))
            val selectedEntry = locator.getString("entryPath"); assertEquals("OEBPS/part00004.html", selectedEntry)
            val sourceStart = locator.getInt("chapterStartScalar"); val sourceEnd = locator.getInt("chapterEndScalar")
            val sourceCanonicalSha = locator.getString("chapterContentSha256")
            assertEquals(0, sourceStart); assertEquals(11274, sourceEnd)
            assertEquals("a3e8955d4a568c60f85a781870ac9bc7aa250b9a5d0431655588a0e66dd19bcb", sourceCanonicalSha)
            val completed = completedNames(); assertNoRestoreWrite(completed)
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue(UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse(checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            assertTrue(compose.runOnUiThread { current.window.decorView.hasWindowFocus() })
            marker("azw3-physical-interaction-preflight", Bundle().apply {
                putBoolean("screenOn", true); putBoolean("keyguardLocked", false); putBoolean("windowFocused", true)
            })
            marker("azw3-cold-input-bound", Bundle().apply {
                putString("baselineProofSha256", baselineProofSha); putString("displayName", "alice-wonderland.azw3")
                putString("expectedSHA", SOURCE_SHA); putLong("expectedBytes", SOURCE_BYTES)
                putString("expectedImportId", IMPORT_ID); putString("expectedImportRecordSha256", IMPORT_SHA)
            })
            marker("azw3-cold-confirmed-new-original-bound", Bundle().apply {
                putString("bookId", bookId); putString("originalId", priorId); putString("originalRecordSha256", baselineSha)
                putLong("originalRecordBytes", baselineRaw.size.toLong()); putLong("requestId", baseline.getLong("requestId"))
                putString("runtimeSession", priorRuntime); putString("originalWire", baselineWire.toString())
                putString("originalParameters", baselineParameters.toString()); putString("ackRevision", priorRevision)
                putInt("chapter", 4); putInt("scalarOffset", expectedScalar); putString("bodyVersion", BODY_VERSION); putString("processingVersion", PROCESSING_VERSION)
            })
            marker("azw3-cold-core-stored-position-observed", Bundle().apply {
                putString("bookId", bookId); putInt("chapter", book.progressChapter); putInt("scalarOffset", book.progressOffset)
                putBoolean("noInitialReading", true); putLong("importRevision", initial.importRevision)
            })
            phase = "one-real-bookshelf-continue"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model, 0)
            val reading = checkNotNull(model.state.value.reading)
            assertEquals(ReaderRoute.Reading, model.state.value.route); assertEquals(ReaderLocalBookFormat.AZW, reading.format)
            assertEquals(bookId, reading.chapter.bookId); assertEquals(4, reading.chapter.index); assertEquals(sourceEntry.getString("title"), reading.chapter.title)
            assertEquals(baselineParameters.getString("expectedBodyVersion"), reading.chapter.bodyVersion)
            assertEquals(baselineParameters.getString("expectedProcessingVersion"), reading.chapter.processingVersion)
            assertEquals(expectedScalar, reading.visibleScalarOffset); assertEquals(expectedScalar, reading.restoreScalarOffset)
            assertFalse(model.state.value.controlsVisible); assertNull(reading.newPositionIntent); assertFalse(reading.newPositionIntentPresented)
            assertNull(model.state.value.positionSaveBarrier); assertNoRestoreWrite(completed)
            marker("azw3-cold-bookshelf-continued-without-grant", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", 4); putInt("scalarOffset", reading.visibleScalarOffset)
                putBoolean("newPositionIntentAbsent", true); putBoolean("newPositionIntentPresented", false)
                putBoolean("grantActionDispatched", false); putBoolean("directoryActionDispatched", false)
            })
            phase = "actual-webview-ready-draw-dom"
            compose.waitUntil(30_000) { failed(model) || compose.runOnUiThread { readyMount() != null } }
            healthy(model, 0)
            // Native Ready is synchronous; Compose must commit removal of its covering Surface too.
            compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            val mount = compose.runOnUiThread { checkNotNull(readyMount()) }
            val readyAtDraw = compose.runOnUiThread { checkNotNull(mount.ready) }
            val web = compose.runOnUiThread { actualWebView() }
            attachedDraw(current.window.decorView)
            attachedDraw(web)
            compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnUiThread {
                assertSame(mount, readyMount()); assertSame(web, actualWebView())
                assertEquals(readyAtDraw, mount.ready)
            }
            val probe = domProbe(web)
            assertFalse(probe.has("probeError"))
            assertEquals(ORIGIN, probe.getString("origin")); assertEquals(SHELL, probe.getString("shellUrl"))
            val prepared = checkNotNull(mount.preparation)
            val lease = prepared.publication.lease
            val presentation = prepared.publication.presentation
            assertEquals(selectedEntry, presentation.entryPath)
            assertEquals(sourceStart, presentation.chapterStartScalar); assertEquals(sourceEnd, presentation.chapterEndScalar)
            val canonical = presentation.documentCanonicalContent
            val canonicalScalars = canonical.codePointCount(0, canonical.length)
            assertTrue(presentation.chapterStartScalar >= 0 && presentation.chapterEndScalar in presentation.chapterStartScalar..canonicalScalars)
            val originalWindow = canonical.substring(canonical.offsetByCodePoints(0, presentation.chapterStartScalar),
                canonical.offsetByCodePoints(0, presentation.chapterEndScalar))
            assertEquals(sourceCanonicalSha, sha(originalWindow.toByteArray(Charsets.UTF_8)))
            assertEquals(originalWindow, presentation.originalChapter); assertEquals(reading.chapter.text, presentation.processedChapter)
            val editsJson = JSONArray().apply { presentation.edits.forEach { edit ->
                put(JSONObject().put("startScalar", edit.startScalar).put("endScalar", edit.endScalar).put("text", edit.text))
            } }
            val replay = if (editsJson.length() == 0) originalWindow else replayProcessedEdits(originalWindow, editsJson)
            assertEquals(reading.chapter.text, replay)
            val originalScalarCount = originalWindow.codePointCount(0, originalWindow.length)
            val processedSha = sha(reading.chapter.text.toByteArray(Charsets.UTF_8))
            assertEquals(sourceEnd - sourceStart, originalScalarCount)
            assertEquals(mount.view.physicalViewId, lease.renderId)
            val entryUri = Uri.parse(probe.getString("entryUrl"))
            assertEquals("https", entryUri.scheme); assertEquals("reader-ebook.invalid", entryUri.encodedAuthority)
            assertEquals("/publication/${lease.renderId}/" + presentation.entryPath, entryUri.path)
            assertNull(entryUri.encodedQuery); assertNull(entryUri.encodedFragment)
            assertTrue(probe.getBoolean("documentComplete") && probe.getBoolean("selectionCollapsed"))
            assertEquals("auto", probe.getString("framePointerEvents")); assertEquals("auto", probe.getString("documentOverflowY"))
            val ready = checkNotNull(mount.ready)
            assertEquals(reading.visibleScalarOffset, ready.scalar)
            assertEquals(lease, ready.lease)
            glyphTapCoordinates(web, probe) // Initial real glyph/viewport observation; not clicked.
            assertTrue(Build.VERSION.SDK_INT >= 26)
            val provider = WebView.getCurrentWebViewPackage() ?: throw AssertionError("Actual WebView provider absent")
            assertEquals(expectedScalar, ready.scalar); assertNotEquals(priorRuntime, lease.runtimeSession)
            val ownerFacts = restoredOwnerFacts(mount, web, ready)
            val browser = browserScrollObservation(web)
            assertFalse(browser.has("probeError")); assertTrue(browser.getBoolean("documentComplete"))
            assertEquals(probe.getString("entryUrl"), browser.getString("entryUrl")); assertEquals(0.0, browser.getDouble("scrollX"), 0.0)
            assertTrue(browser.getDouble("scrollY").isFinite() && browser.getDouble("scrollY") > 0)
            assertTrue(browser.getDouble("scrollHeight") > browser.getDouble("viewportHeight"))
            restoredOwnerFacts(mount, web, ready)
            assertNoRestoreWrite(completed)
            marker("azw3-cold-real-webview-ready-draw-restored-dom", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", reading.chapter.index); putInt("scalarOffset", ready.scalar); putInt("scalarCount", reading.chapter.scalarCount)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("ownerId", lease.ownerId.toString()); putString("clientId", lease.clientId.toString()); putLong("ownerGeneration", lease.ownerGeneration)
                putString("runtimeSession", lease.runtimeSession); putString("physicalViewId", mount.view.physicalViewId.toString())
                putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putString("entryPath", presentation.entryPath); putString("origin", probe.getString("origin")); putString("format", "azw")
                putString("originalLocatorJson", locatorRaw)
                putString("originalChapterSha256", sourceCanonicalSha); putInt("originalScalarCount", originalScalarCount)
                putString("processedChapterSha256", processedSha); putInt("processedScalarCount", reading.chapter.scalarCount)
                putString("processedEditsJson", editsJson.toString()); putInt("processedEditsCount", editsJson.length())
                putString("processedEditsReplaySha256", sha(replay.toByteArray(Charsets.UTF_8))); putBoolean("originalEqualsProcessed", originalWindow == reading.chapter.text)
                putInt("chapterStartScalar", presentation.chapterStartScalar); putInt("chapterEndScalar", presentation.chapterEndScalar)
                putInt("documentScalarCount", canonicalScalars); putString("documentCanonicalSha256", sha(canonical.toByteArray(Charsets.UTF_8)))
                putString("providerPackage", provider.packageName); putString("providerVersion", provider.versionName)
                putString("canonicalSha256", sha(reading.chapter.text.toByteArray(Charsets.UTF_8)))
                putString("observedDomTextSampleSha256", sha(probe.getString("domText").toByteArray(Charsets.UTF_8)))
                putString("probeMetrics", JSONObject(probe.toString()).apply { remove("domText") }.toString())
                putBoolean("newPositionIntentAbsent", true); putBoolean("newPositionIntentPresented", false)
                putInt("ownerDesiredAnchor", ownerFacts.getInt("desiredAnchor")); putInt("ownerInputCurrentAnchor", ownerFacts.getInt("currentAnchor"))
                putInt("ownerLayoutAnchor", ownerFacts.getInt("layoutAnchor")); putLong("ownerDrawnEpoch", ownerFacts.getLong("drawnEpoch"))
                putBoolean("ownerLastVisibleObserved", ownerFacts.getBoolean("lastVisibleObserved"))
                if (ownerFacts.has("lastVisible")) putInt("ownerLastVisible", ownerFacts.getInt("lastVisible"))
                putDouble("browserScrollY", browser.getDouble("scrollY")); putString("browserObservation", browser.toString())
                putString("restorationClaim", "PRODUCTION_ANCHOR_MEASUREMENT_DOM_PAINT_NATIVE_DRAW_READY_PLUS_READ_ONLY_CURRENT_DOM_NOT_INDEPENDENT_SCALAR_RECT_EXPORT")
                putString("textClaim", "DOM_SAMPLE_ONLY_ORIGINAL_CANONICAL_AND_CORE_PROCESSED_EDITS_AUTHENTICATED_BY_PRODUCTION_ADMIT_NATIVE_VISUAL_DRAW_READY")
            })
            assertEquals(completed, completedNames())

            assertNoRestoreWrite(completed)
            assertEquals(baselineSha, shaFile(baselineFile))
            assertRetainedFiles(checkNotNull(retainedFiles))
            marker("azw3-cold-no-new-position-write-observed", Bundle().apply {
                putString("bookId", bookId); putInt("chapter", 4); putInt("scalarOffset", expectedScalar)
                putBoolean("noGrant", true); putBoolean("noPan", true); putBoolean("noSave", true)
                putBoolean("newPositionIntentAbsent", true); putBoolean("lastPositionSaveAttemptAbsent", true)
                putBoolean("sameCompletedSet", true); putBoolean("priorOriginalRawUnchanged", true)
            })
            // Restoration is already answered. This one exit is lifecycle cleanup, not an additional reading scenario.
            // Simply destroying a live reader can issue an ON_STOP save without an observed original ACK.
            phase = "one-normal-exit-for-acknowledged-teardown"
            val tap = glyphTapCoordinates(web, domProbe(web))
            assertTrue(UiDevice.getInstance(instrumentation).click(tap[0], tap[1]))
            compose.waitUntil(5_000) { model.state.value.controlsVisible && enabledExit() }
            attachedDraw(current.window.decorView); healthy(model, 0)
            assertNoRestoreWrite(completed)
            assertEquals(expectedScalar, checkNotNull(model.state.value.reading).visibleScalarOffset)
            compose.onNodeWithTag("reader_exit").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.reading == null || failed(model)) }
            healthy(model, 0); assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            val saved = freshRecords(completed).single()
            val record = saved.second; val parameters = record.getJSONObject("parameters"); val wire = record.getJSONObject("wireTerminal")
            assertEquals("progress", record.getString("kind")); assertTrue(record.getBoolean("completed"))
            assertEquals(bookId, record.getString("bookId")); assertNotEquals(priorId, record.getString("id"))
            assertEquals(4, parameters.getInt("chapterIndex")); assertEquals(expectedScalar, parameters.getInt("chapterOffset"))
            assertEquals(priorRevision, parameters.getString("expectedProgressRevision"))
            assertEquals(BODY_VERSION, parameters.getString("expectedBodyVersion")); assertEquals(PROCESSING_VERSION, parameters.getString("expectedProcessingVersion"))
            assertEquals(lease.runtimeSession, record.getString("runtimeSession"))
            val exitRevision = ProgressReceiptPolicy.revision(record, wire)
            assertFalse(File(current.filesDir, "Reader/Operations/${record.getString("id")}.json").exists())
            assertEquals(otherBooks, model.state.value.books.filter { it.id != bookId }.associateBy { it.key })
            assertEquals(otherRemoteBooks, model.state.value.remoteBooks)
            assertEquals(baselineSha, shaFile(baselineFile)); assertRetainedFiles(checkNotNull(retainedFiles))
            assertNoPendingFiles()
            settledCompleted = completedNames()
            marker("azw3-cold-cleanup-exit-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", record.getString("id")); putLong("requestId", record.getLong("requestId"))
                putString("runtimeSession", record.getString("runtimeSession")); putInt("chapter", 4); putInt("scalarOffset", expectedScalar)
                putString("expectedRevision", priorRevision); putString("ackRevision", exitRevision)
                putString("bodyVersion", BODY_VERSION); putString("processingVersion", PROCESSING_VERSION)
                putString("originalRecordSha256", sha(saved.third)); putLong("originalRecordBytes", saved.third.size.toLong())
                putString("originalWire", wire.toString()); putString("scope", "ONE_NORMAL_EXIT_AFTER_RESTORE_PROOF_FOR_ACKNOWLEDGED_ACTIVITY_TEARDOWN")
            })
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            if (primary == null) {
                try {
                    finishOnlyBookshelf()
                    assertEquals(checkNotNull(settledCompleted), completedNames()); assertNoPendingFiles()
                    assertRetainedFiles(checkNotNull(retainedFiles))
                    assertEquals(checkNotNull(priorSha), shaFile(checkNotNull(priorFile)))
                } catch (cleanup: Throwable) { throw cleanup }
            } else marker("activity-retained-for-controller") // No additional UI/business cleanup on failure.
        }
        marker("method-exit")
    }

    private fun assertNoPendingFiles() {
        val root = File(current.filesDir, "Reader/Operations")
        assertTrue(root.listFiles().orEmpty().none { it.isFile })
        assertTrue(File(root, "Completed").listFiles().orEmpty().none { it.name.endsWith(".new") || it.name.endsWith(".bak") })
    }
    /** No restore-caused write before the separately scoped normal exit. */
    private fun assertNoRestoreWrite(before: Set<String>) {
        assertEquals(before, completedNames()); assertNoPendingFiles()
        val model = current.readerViewModel
        assertNull(model.state.value.lastPositionSaveAttempt)
        model.state.value.reading?.let { assertNull(it.newPositionIntent); assertFalse(it.newPositionIntentPresented) }
        val observer = compose.runOnUiThread { field(model, "delayedSave") as? Job }
        assertTrue(observer == null || observer.isCompleted)
    }

    private fun finishOnlyBookshelf() {
        val owned = scenario ?: return // No destroyed claim if launch never returned.
        if (owned.state != Lifecycle.State.DESTROYED) {
            val state = current.readerViewModel.state.value
            if (state.reading != null || state.busy || state.route != ReaderRoute.Bookshelf || failed(current.readerViewModel)) {
                marker("activity-retained-for-controller"); throw AssertionError("Original state retained; no business cleanup/second exit")
            }
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
    private fun retainedNonTargetFiles(): Map<String, Pair<Long, String>> {
        val reader = File(current.filesDir, "Reader")
        return listOf("Operations", "Originals").flatMap { name ->
            val root = File(reader, name)
            if (!root.exists()) emptyList() else root.walkTopDown().filter { it.isFile }.map { file ->
                file.relativeTo(reader).invariantSeparatorsPath to (file.length() to shaFile(file))
            }.toList()
        }.toMap()
    }
    private fun assertRetainedFiles(before: Map<String, Pair<Long, String>>) {
        val reader = File(current.filesDir, "Reader")
        before.forEach { (path, identity) ->
            val file = File(reader, path)
            assertTrue("Non-target retained file missing: $path", file.isFile)
            assertEquals("Non-target retained file length changed: $path", identity.first, file.length())
            assertEquals("Non-target retained file bytes changed: $path", identity.second, shaFile(file))
        }
    }

    /** Serialize only actual issued immutable DTO values; this never constructs or authorizes a capability. */

    private fun replayProcessedEdits(original: String, edits: JSONArray): String {
        val sourceCount = original.codePointCount(0, original.length)
        assertTrue(sourceCount <= 32768 && edits.length() in 1..100000)
        var cursor = 0
        var utf16Cursor = 0
        return buildString {
            for (index in 0 until edits.length()) {
                val edit = edits.getJSONObject(index); val first = edit.getInt("startScalar"); val past = edit.getInt("endScalar")
                val replacement = edit.getString("text")
                assertTrue(first >= cursor && past in first..sourceCount); assertFalse('\uFFFC' in replacement)
                val firstUtf16 = original.offsetByCodePoints(utf16Cursor, first - cursor)
                val pastUtf16 = original.offsetByCodePoints(firstUtf16, past - first)
                append(original, utf16Cursor, firstUtf16)
                append(replacement); cursor = past; utf16Cursor = pastUtf16
            }
            append(original, utf16Cursor, original.length)
        }
    }

    private fun glyphTapCoordinates(web: WebView, probe: JSONObject): IntArray = compose.runOnUiThread {
        val location = IntArray(2).also(web::getLocationOnScreen); val visible = Rect()
        assertTrue(web.getGlobalVisibleRect(visible))
        assertEquals(Rect(location[0], location[1], location[0] + web.width, location[1] + web.height), visible)
        val density = web.resources.displayMetrics.density.toDouble()
        assertTrue(abs(probe.getDouble("width") * density - web.width) <= 1.1 * density)
        assertTrue(abs(probe.getDouble("height") * density - web.height) <= 1.1 * density)
        val tap = probe.getJSONObject("tap") // Actual Range/image rectangle, not an assumed screen coordinate.
        val frameLeft = probe.getDouble("frameLeft"); val frameTop = probe.getDouble("frameTop")
        val left = maxOf(tap.getDouble("left"), 0.0, (visible.left - location[0]) / density - frameLeft)
        val right = minOf(tap.getDouble("right"), probe.getDouble("width"), (visible.right - location[0]) / density - frameLeft)
        val top = maxOf(tap.getDouble("top"), 32.0, (visible.top - location[1]) / density - frameTop)
        val bottom = minOf(tap.getDouble("bottom"), probe.getDouble("height") - 52, (visible.bottom - location[1]) / density - frameTop)
        assertTrue("Actual content has no usable child/native viewport intersection", right > left && bottom > top)
        val raw = JSONObject().put("left", tap.getDouble("left")).put("right", tap.getDouble("right"))
            .put("top", tap.getDouble("top")).put("bottom", tap.getDouble("bottom"))
        val clipped = JSONObject().put("left", left).put("right", right).put("top", top).put("bottom", bottom)
        val cssX = frameLeft + (left + right) * .5
        val cssY = frameTop + (top + bottom) * .5
        val x = location[0] + (cssX * density).toInt()
        val y = location[1] + (cssY * density).toInt()
        assertTrue(visible.contains(x, y))
        tap.put("raw", raw).put("clipped", clipped).put("screenX", x).put("screenY", y)
            .put("nativeVisible", JSONObject().put("left", visible.left).put("right", visible.right)
                .put("top", visible.top).put("bottom", visible.bottom))
        intArrayOf(x, y)
    }

    private fun failed(model: ReaderViewModel): Boolean = model.state.value.let {
        it.error != null || it.protocolRecoveryReference != null || it.pendingImports.isNotEmpty() || it.pendingPositions.isNotEmpty() ||
            it.pendingBookmarks.isNotEmpty() || it.pendingSearchClose != null || (it.ebookFailureOwner != null && it.ebookFailureOwner == it.reading?.identity)
    }
    private fun healthy(model: ReaderViewModel, revision: Long) { assertTrue(model.state.value.pendingReady); assertFalse("Original operation failed/pending", failed(model)); assertEquals(revision, model.state.value.importRevision) }
    private fun completedNames() = File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty().filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }
    private fun freshRecords(before: Set<String>) = (completedNames() - before).map { name ->
        safeReadCompleted(File(current.filesDir, "Reader/Operations/Completed/$name"))
    }
    private fun safeReadCompleted(file: File): Triple<File, JSONObject, ByteArray> {
        assertTrue(file.isFile && file.length() in 1..8L * 1024 * 1024)
        assertFalse(File(file.parentFile, file.name + ".new").exists())
        assertFalse(File(file.parentFile, file.name + ".bak").exists())
        val raw = file.readBytes()
        return Triple(file, JSONObject(raw.toString(Charsets.UTF_8)), raw)
    }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun actualWebView(): WebView {
        fun descendants(view: View): List<WebView> = (if (view is WebView) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList())
        return descendants(current.window.decorView).filter { it.isAttachedToWindow && it.url == SHELL }.single()
    }

    private fun readyMount(): ReaderEbookUiMount? {
        val model = current.readerViewModel; val reading = model.state.value.reading ?: return null
        val mount = field(model, "ebookMount") as? ReaderEbookUiMount ?: return null
        val prepared = mount.preparation ?: return null; val ready = mount.ready ?: return null
        if (reading.identity != mount.readingOwner || reading.chapter.sessionToken != mount.chapter.sessionToken ||
            reading.chapter.bookId != mount.chapter.bookId || reading.chapter.index != mount.chapter.index ||
            reading.chapter.bodyVersion != mount.chapter.bodyVersion || reading.chapter.processingVersion != mount.chapter.processingVersion ||
            prepared.view !== mount.view || prepared.publication.lease != ready.lease || ready.scalar != reading.visibleScalarOffset) return null
        prepared.requireCurrent()
        val web = try { actualWebView() } catch (_: IllegalArgumentException) { return null } catch (_: NoSuchElementException) { return null }
        val owner = field(web.parent, "owner") ?: return null
        val layout = field(owner, "layout") ?: return null
        val visible = Rect()
        return mount.takeIf { field(owner, "view") === web && field(owner, "lease") == prepared.publication.lease &&
            field(owner, "phase").toString() == "Ready" && field(layout, "anchor") == ready.scalar &&
            web.width == ready.widthPx && web.height == ready.heightPx && field(owner, "forceLayout") == false && field(owner, "drawnEpoch") == ready.layoutEpoch && field(owner, "awaitingDraw") == null &&
            field(layout, "epoch") == ready.layoutEpoch && field(layout, "token") == ready.layoutToken &&
            web.isLaidOut && !web.isLayoutRequested && web.isShown && web.getGlobalVisibleRect(visible) &&
            visible.width() == web.width && visible.height() == web.height }
    }

    private fun enabledExit(): Boolean = compose.onAllNodes(hasTestTag("reader_exit")).fetchSemanticsNodes().let {
        it.size == 1 && SemanticsProperties.Disabled !in it.single().config && it.single().boundsInRoot.width > 0 && it.single().boundsInRoot.height > 0
    }
    private fun attachedDraw(view: View) {
        val latch = CountDownLatch(1); var tree: ViewTreeObserver? = null
        val listener = ViewTreeObserver.OnDrawListener { if (view.isAttachedToWindow && view.isShown) view.post { latch.countDown() } }
        var primary: Throwable? = null
        try {
            compose.runOnUiThread { assertTrue(view.isAttachedToWindow && view.isShown); tree = view.viewTreeObserver; tree!!.addOnDrawListener(listener); view.invalidate() }
            assertTrue("Attached native draw did not complete", latch.await(5, TimeUnit.SECONDS))
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try { compose.runOnUiThread { tree?.takeIf { it.isAlive }?.removeOnDrawListener(listener) } }
            catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
        }
    }

    private fun domProbe(web: WebView): JSONObject {
        val latch = CountDownLatch(1); var result: String? = null
        compose.runOnUiThread { web.evaluateJavascript(DOM_READ_ONLY) { result = it; latch.countDown() } }
        assertTrue("Read-only DOM callback missing", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(result ?: throw AssertionError("DOM callback returned null"))
    }

    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step); details.putString("readerMethod", METHOD)
        details.putInt("readerProcessPid", AndroidProcess.myPid()); details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, details)
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun shaFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun restoredOwnerFacts(mount: ReaderEbookUiMount, web: WebView, ready: ReaderEbookReady): JSONObject = compose.runOnUiThread {
        val model = current.readerViewModel; val reading = checkNotNull(model.state.value.reading)
        assertSame(mount, readyMount()); assertSame(web, actualWebView()); assertSame(mount, field(model, "ebookMount"))
        val prepared = checkNotNull(mount.preparation); prepared.requireCurrent(); assertEquals(ready.lease, prepared.publication.lease)
        assertEquals(expectedScalar, reading.visibleScalarOffset); assertEquals(expectedScalar, ready.scalar)
        assertEquals(reading.identity, mount.readingOwner); assertEquals(reading.chapter.sessionToken, mount.chapter.sessionToken)
        val owner = checkNotNull(field(web.parent, "owner")); val layout = checkNotNull(field(owner, "layout"))
        val anchors = checkNotNull(field(owner, "anchors")); val input = checkNotNull(field(owner, "input"))
        assertSame(web, field(owner, "view")); assertEquals(ready.lease, field(owner, "lease"))
        assertEquals("Ready", field(owner, "phase").toString()); assertEquals(false, field(owner, "forceLayout")); assertNull(field(owner, "awaitingDraw"))
        val drawnEpoch = field(owner, "drawnEpoch") as? Long ?: throw AssertionError("Actual drawn epoch absent")
        val layoutAnchor = field(layout, "anchor") as? Int ?: throw AssertionError("Actual layout anchor absent")
        val desiredAnchor = field(anchors, "desiredAnchor") as? Int ?: throw AssertionError("Actual desired anchor absent")
        val currentAnchor = field(input, "currentAnchor") as? Int ?: throw AssertionError("Actual input anchor absent")
        assertEquals(ready.layoutEpoch, drawnEpoch); assertEquals(ready.layoutEpoch, field(layout, "epoch")); assertEquals(ready.layoutToken, field(layout, "token"))
        assertEquals(expectedScalar, layoutAnchor); assertEquals(expectedScalar, desiredAnchor); assertEquals(expectedScalar, currentAnchor)
        val last = field(anchors, "lastVisible") as? Int
        assertTrue(last == null || last == expectedScalar) // Restore need not fabricate a user position observation.
        JSONObject().put("desiredAnchor", desiredAnchor).put("currentAnchor", currentAnchor).put("layoutAnchor", layoutAnchor)
            .put("drawnEpoch", drawnEpoch).put("lastVisibleObserved", last != null).apply { if (last != null) put("lastVisible", last) }
    }

    private fun browserScrollObservation(web: WebView): JSONObject {
        val latch = CountDownLatch(1); var result: String? = null
        compose.runOnUiThread { web.evaluateJavascript("""
            (()=>{try{const f=document.getElementById('publication'),w=f?.contentWindow,d=f?.contentDocument;
              if(!w||!d?.body)return {probeError:'NO_CURRENT_DOCUMENT'};
              return {entryUrl:w.location.href,scrollX:w.scrollX,scrollY:w.scrollY,viewportWidth:w.innerWidth,
                viewportHeight:w.innerHeight,scrollHeight:d.documentElement.scrollHeight,documentComplete:d.readyState==='complete'};
            }catch(e){return {probeError:String(e)}}})()
        """.trimIndent()) { result = it; latch.countDown() } }
        assertTrue("Read-only browser scroll observation missing", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(result ?: throw AssertionError("Browser observation returned null"))
    }

    companion object {
        private const val METHOD = "freshAliceAzw3ColdRestoresOriginalExitAnchor"
        private const val IMPORT_ID = "android-import-7082fe6a-c9e4-426a-8aa9-feb6a6450a99"
        private const val IMPORT_SHA = "6f6f1c5ee77ca2db4f0faa62fd0994c78c2972b94c1eea11af8d558b0e41e5f4"
        private const val BASELINE_ID = "android-progress-3bbf6cd6-ebd7-4a45-a9c8-c6f4ef738020"
        private const val BASELINE_SHA = "baecb9e687fbd6fa1c0691925e24d898c28543e041902996dd70fce82af3479b"
        private const val BASELINE_RUNTIME = "04f412fe-dd08-4ab9-ad82-ceea4a1521e0"
        private const val BASELINE_REVISION = "reader-progress-v1:b5c345e8d25b283bb861c1b9b8dabeea8976874df62fa36fd3d96d96bf2d88bb"
        private const val BODY_VERSION = "eb20d1561b888174fdea8c262e76ff5695aa95eacf2c5073b00005510a5f5049"
        private const val PROCESSING_VERSION = "9b089946e8e913514675507632b9d7240bd9af90f544e08a875206eb99edbc9c"
        private const val SOURCE_SHA = "604874a7afdb98570f4e41a2dc9af8aace13604af77bf14df903e855564f419b"
        private const val SOURCE_BYTES = 1031552L
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
        private val DOM_READ_ONLY = """
            (()=>{try{
              const f=document.getElementById('publication'),d=f?.contentDocument,w=f?.contentWindow;
              if(!d?.body||!w)return {probeError:'NO_CURRENT_DOCUMENT'};
              const rect=q=>({left:q.left,right:q.right,top:q.top,bottom:q.bottom});
              const usable=q=>q.width>0&&q.height>0&&q.bottom>32&&q.top<w.innerHeight-52&&q.right>0&&q.left<w.innerWidth;
              const eligible=e=>e&&!e.closest('a[href],script,style,noscript,template')&&w.getComputedStyle(e).visibility!=='hidden';
              const walker=d.createTreeWalker(d.body,NodeFilter.SHOW_TEXT);let n,tap=null;
              while(n=walker.nextNode()){
                const e=n.parentElement;if(!eligible(e))continue;
                const i=n.data.search(/\S/u);if(i<0)continue;
                const r=d.createRange();r.setStart(n,i);r.setEnd(n,i+(n.data.codePointAt(i)>65535?2:1));
                const q=r.getBoundingClientRect();if(usable(q)){tap={...rect(q),kind:'text-range'};break;}
              }
              if(!tap)for(const image of d.querySelectorAll('img,image')){
                if(!eligible(image))continue;
                const q=image.getBoundingClientRect();if(!usable(q))continue;
                const tag=image.localName.toLowerCase();let loaded=false;
                if(tag==='img')loaded=image.complete&&image.naturalWidth>0;
                else{const src=image.getAttribute('href')||image.getAttributeNS('http://www.w3.org/1999/xlink','href');
                  if(src){const url=new URL(src,d.baseURI).href;loaded=w.performance.getEntriesByName(url).some(e=>e.responseEnd>0&&e.decodedBodySize>0);}}
                if(loaded){tap={...rect(q),kind:tag==='img'?'loaded-img':'resource-complete-svg-image'};break;}
              }
              if(!tap)return {probeError:'NO_VISIBLE_NON_LINK_TEXT_OR_LOADED_IMAGE'};
              const raw=d.body.textContent||'',text=raw.slice(0,1000000),fr=f.getBoundingClientRect();
              return {origin:location.origin,shellUrl:location.href,entryUrl:w.location.href,documentComplete:d.readyState==='complete',
                framePointerEvents:getComputedStyle(f).pointerEvents,documentOverflowY:w.getComputedStyle(d.documentElement).overflowY,
                frameLeft:fr.left,frameTop:fr.top,selectionCollapsed:w.getSelection()?.isCollapsed!==false,imageCount:d.images.length,
                styleSheetCount:d.styleSheets.length,fontStatus:d.fonts?.status,domText:text,domTextTotalUtf16:raw.length,
                domTextSampleUtf16:text.length,domTextTruncated:raw.length>text.length,width:w.innerWidth,height:w.innerHeight,tap};
            }catch(e){return {probeError:String(e)}}})()
        """.trimIndent()
    }

}
