package io.reader.android

import android.app.KeyguardManager
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

/** Fresh process restores only the last real new original. Old UNKNOWN remains; no grant, pan, save, recovery or PNG. */
@RunWith(AndroidJUnit4::class)
class UiMobiNewPositionColdRestoreTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    private var expectedScalar = -1
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(120, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun existingMobiConfirmedNewPositionColdRestoresWithoutGrantOrWrites() {
        marker("method-enter") // Before Activity/library/owner access.
        var primary: Throwable? = null
        var phase = "mandatory-real-predecessor-input"
        var beforeCompleted: Set<String>? = null
        var priorFile: File? = null
        var priorSha: String? = null
        try {
            val args = InstrumentationRegistry.getArguments()
            fun required(name: String): String = args.getString(name)?.takeIf { it.isNotBlank() && !it.contains("PENDING") }
                ?: throw AssertionError("Actual successful predecessor input required: $name")
            val baselineProofSha = required("baselineProofSha256").also { assertTrue(it.matches(Regex("[a-f0-9]{64}"))) }
            val priorId = required("expectedBaselineProgressId").also {
                assertTrue(it.matches(Regex("android-progress-[a-f0-9-]{36}"))); assertNotEquals(UNKNOWN_ID, it)
                assertEquals(it.removePrefix("android-progress-"), java.util.UUID.fromString(it.removePrefix("android-progress-")).toString())
            }
            val baselineSha = required("expectedBaselineProgressRecordSha256").also { assertTrue(it.matches(Regex("[a-f0-9]{64}"))) }
            val priorBytes = required("expectedBaselineProgressRecordBytes").toLong().also { assertTrue(it in 1..8L * 1024 * 1024) }
            val priorRuntime = required("expectedBaselineRuntimeSession").also { assertEquals(it, java.util.UUID.fromString(it).toString()) }
            assertNotEquals(UNKNOWN_RUNTIME, priorRuntime)
            val priorRevision = required("expectedBaselineRevision").also { assertTrue(it.matches(Regex("reader-progress-v1:[a-f0-9]{64}"))) }
            expectedScalar = required("expectedBaselineScalar").toInt().also { assertTrue("Prior successful real pan must have saved a positive offset", it > 0) }
            val previousPid = required("previousProcessPid").toInt().also { assertTrue(it > 0) }
            val previousStart = required("previousProcessStartElapsedMs").toLong().also { assertTrue(it > 0) }
            assertEquals("覆汉.mobi", required("displayName")); assertEquals(SOURCE_SHA, required("expectedSHA"))
            assertEquals(SOURCE_BYTES, required("expectedBytes").toLong())
            assertEquals(PROTECTED_SHA, required("protectedBookSHA")); assertEquals(PROTECTED_EPUB_SHA, required("protectedEpubSHA"))
            val start = AndroidProcess.getStartElapsedRealtime()
            assertTrue(AndroidProcess.myPid() > 0 && start > previousStart)
            assertFalse(AndroidProcess.myPid() == previousPid && start == previousStart) // Numeric PID reuse is legal.
            marker("mobi-cold-process-observed", Bundle().apply {
                putInt("previousProcessPid", previousPid); putLong("previousProcessStartElapsedMs", previousStart)
                putString("previousNewAckRuntimeSession", priorRuntime); putBoolean("distinctProcessIdentity", true)
                putString("processReferenceClaim", "PREVIOUS_SUCCESSFUL_NEW_POSITION_SCENE_PROCESS_TUPLE_NOT_UUID_ALONE")
            })
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            healthy(model, 0)
            val initial = model.state.value
            assertTrue(initial.pendingReady && initial.reading == null && initial.route == ReaderRoute.Bookshelf)
            assertNull(initial.lastPositionSaveAttempt)
            val protected = initial.books.single { it.id == "local:$PROTECTED_SHA" }
            val protectedEpub = initial.books.single { it.id == "local:$PROTECTED_EPUB_SHA" }
            assertEquals(1, protected.progressChapter); assertEquals(210, protected.progressOffset)
            assertEquals(2, protectedEpub.progressChapter); assertEquals(69, protectedEpub.progressOffset)
            val book = initial.books.single { it.id == bookId }
            assertEquals(560, book.chapterCount); assertEquals(3, book.progressChapter); assertEquals(expectedScalar, book.progressOffset)
            val unknownRaw = assertUnknownUnchanged()
            val unknown = JSONObject(unknownRaw.toString(Charsets.UTF_8))
            val baselineFile = File(current.filesDir, "Reader/Operations/Completed/$priorId.json")
            val baselineRaw = baselineFile.readBytes()
            assertEquals(priorBytes, baselineRaw.size.toLong()); assertEquals(baselineSha, sha(baselineRaw))
            val baseline = JSONObject(baselineRaw.toString(Charsets.UTF_8))
            assertEquals(priorId, baseline.getString("id")); assertEquals("progress", baseline.getString("kind"))
            assertTrue(baseline.getBoolean("completed")); assertEquals(bookId, baseline.getString("bookId"))
            assertEquals(priorRuntime, baseline.getString("runtimeSession"))
            val baselineWire = baseline.getJSONObject("wireTerminal"); val baselineParameters = baseline.getJSONObject("parameters")
            assertEquals(priorRevision, ProgressReceiptPolicy.revision(baseline, baselineWire))
            assertEquals(3, baselineParameters.getInt("chapterIndex")); assertEquals(expectedScalar, baselineParameters.getInt("chapterOffset"))
            assertFalse(File(current.filesDir, "Reader/Operations/$priorId.json").exists())
            assertFalse(File(baselineFile.parentFile, baselineFile.name + ".new").exists())
            priorFile = baselineFile; priorSha = baselineSha
            val imported = JSONObject(File(current.filesDir, "Reader/Operations/Completed/$IMPORT_ID.json").readText())
            val toc = imported.getJSONObject("receipts").getJSONObject("persist").getJSONObject("data").getJSONObject("data").getJSONArray("toc")
            val sourceEntry = (0 until toc.length()).map { toc.getJSONObject(it) }.single { it.getInt("index") == 3 }
            assertEquals("第一章 卢龙塞", sourceEntry.getString("title"))
            val locatorRaw = sourceEntry.getJSONObject("variables").getString("readerEbookPresentationV1")
            val locator = JSONObject(locatorRaw); assertEquals(1, locator.getInt("version"))
            val selectedEntry = locator.getString("entryPath"); assertTrue(selectedEntry.isNotBlank())
            val sourceStart = locator.getInt("chapterStartScalar"); val sourceEnd = locator.getInt("chapterEndScalar")
            val sourceCanonicalSha = locator.getString("chapterContentSha256")
            assertTrue(sourceStart >= 0 && sourceEnd > sourceStart && sourceCanonicalSha.matches(Regex("[a-f0-9]{64}")))
            val completed = EbookJourneyProbe.completedOperationNames(current); beforeCompleted = completed; assertNoRestoreWrite(completed)
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue(UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse(checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            assertTrue(compose.runOnUiThread { current.window.decorView.hasWindowFocus() })
            marker("mobi-physical-interaction-preflight", Bundle().apply {
                putBoolean("screenOn", true); putBoolean("keyguardLocked", false); putBoolean("windowFocused", true)
            })
            marker("mobi-cold-input-bound", Bundle().apply {
                putString("baselineProofSha256", baselineProofSha); putString("displayName", "覆汉.mobi")
                putString("expectedSHA", SOURCE_SHA); putLong("expectedBytes", SOURCE_BYTES)
                putString("protectedBookSHA", PROTECTED_SHA); putString("protectedEpubSHA", PROTECTED_EPUB_SHA)
            })
            marker("mobi-cold-retained-unknown-original-bound", Bundle().apply {
                putString("bookId", bookId); putString("originalId", UNKNOWN_ID); putString("originalRecordSha256", sha(unknownRaw))
                putLong("originalRecordBytes", unknownRaw.size.toLong()); putString("runtimeSession", unknown.getString("runtimeSession"))
                putLong("requestId", unknown.getLong("requestId")); putBoolean("noWire", true); putBoolean("noCandidate", true)
                putBoolean("noCompleted", true); putBoolean("oldRecoveryDispatched", false); putLong("importRevision", 0L)
            })
            marker("mobi-cold-confirmed-new-original-bound", Bundle().apply {
                putString("bookId", bookId); putString("originalId", priorId); putString("originalRecordSha256", baselineSha)
                putLong("originalRecordBytes", baselineRaw.size.toLong()); putLong("requestId", baseline.getLong("requestId"))
                putString("runtimeSession", priorRuntime); putString("originalWire", baselineWire.toString())
                putString("originalParameters", baselineParameters.toString()); putString("ackRevision", priorRevision)
                putInt("chapter", 3); putInt("scalarOffset", expectedScalar); putBoolean("confirmsOldUnknown", false)
            })
            marker("mobi-cold-core-stored-position-observed", Bundle().apply {
                putString("bookId", bookId); putInt("chapter", book.progressChapter); putInt("scalarOffset", book.progressOffset)
                putBoolean("noInitialReading", true); putLong("importRevision", initial.importRevision)
            })
            phase = "one-real-bookshelf-continue"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_$bookId"))
            compose.onNodeWithTag("reader_book_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model, 0)
            val reading = checkNotNull(model.state.value.reading)
            assertEquals(ReaderRoute.Reading, model.state.value.route); assertEquals(ReaderLocalBookFormat.MOBI, reading.format)
            assertEquals(bookId, reading.chapter.bookId); assertEquals(3, reading.chapter.index); assertEquals(sourceEntry.getString("title"), reading.chapter.title)
            assertEquals(baselineParameters.getString("expectedBodyVersion"), reading.chapter.bodyVersion)
            assertEquals(baselineParameters.getString("expectedProcessingVersion"), reading.chapter.processingVersion)
            assertEquals(expectedScalar, reading.visibleScalarOffset); assertEquals(expectedScalar, reading.restoreScalarOffset)
            assertFalse(model.state.value.controlsVisible); assertNull(reading.newPositionIntent); assertFalse(reading.newPositionIntentPresented)
            assertEquals(UNKNOWN_ID, model.state.value.positionSaveBarrier?.id); assertNoRestoreWrite(completed)
            marker("mobi-cold-bookshelf-continued-without-grant", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", 3); putInt("scalarOffset", reading.visibleScalarOffset)
                putBoolean("newPositionIntentAbsent", true); putBoolean("newPositionIntentPresented", false)
                putBoolean("grantActionDispatched", false); putBoolean("directoryActionDispatched", false)
            })
            phase = "actual-webview-ready-draw-dom"
            compose.waitUntil(30_000) { failed(model) || compose.runOnUiThread { EbookJourneyProbe.readyMount(current, SHELL) != null } }
            healthy(model, 0)
            // Native Ready is synchronous; Compose must commit removal of its covering Surface too.
            compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            val mount = compose.runOnUiThread { checkNotNull(EbookJourneyProbe.readyMount(current, SHELL)) }
            val readyAtDraw = compose.runOnUiThread { checkNotNull(mount.ready) }
            val web = compose.runOnUiThread { EbookJourneyProbe.webView(current, SHELL) }
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView)
            EbookJourneyProbe.awaitDraw(compose, web)
            compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnUiThread {
                assertSame(mount, EbookJourneyProbe.readyMount(current, SHELL)); assertSame(web, EbookJourneyProbe.webView(current, SHELL))
                assertEquals(readyAtDraw, mount.ready)
            }
            val probe = EbookJourneyProbe.dom(compose, web, EbookJourneyProbe.DOM_READ_ONLY)
            assertFalse(probe.has("probeError"))
            assertEquals(ORIGIN, probe.getString("origin")); assertEquals(SHELL, probe.getString("shellUrl"))
            val prepared = checkNotNull(mount.preparation)
            val lease = prepared.publication.lease
            assertNotEquals("Old UNKNOWN runtime must not supply this new reading", UNKNOWN_RUNTIME, lease.runtimeSession)
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
            assertTrue("This scene must exercise confirmed processed edits", editsJson.length() > 0)
            val replay = replayProcessedEdits(originalWindow, editsJson)
            assertEquals(reading.chapter.text, replay); assertNotEquals(originalWindow, reading.chapter.text)
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
            EbookJourneyProbe.glyphTapCoordinates(compose, web, probe) // Initial real glyph/viewport observation; not clicked.
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
            marker("mobi-cold-real-webview-ready-draw-restored-processed-dom", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", reading.chapter.index); putInt("scalarOffset", ready.scalar); putInt("scalarCount", reading.chapter.scalarCount)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("ownerId", lease.ownerId.toString()); putString("clientId", lease.clientId.toString()); putLong("ownerGeneration", lease.ownerGeneration)
                putString("runtimeSession", lease.runtimeSession); putString("physicalViewId", mount.view.physicalViewId.toString())
                putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putString("entryPath", presentation.entryPath); putString("origin", probe.getString("origin")); putString("format", "mobi")
                putString("originalLocatorJson", locatorRaw)
                putString("originalChapterSha256", sourceCanonicalSha); putInt("originalScalarCount", originalScalarCount)
                putString("processedChapterSha256", processedSha); putInt("processedScalarCount", reading.chapter.scalarCount)
                putString("processedEditsJson", editsJson.toString()); putInt("processedEditsCount", editsJson.length())
                putString("processedEditsReplaySha256", sha(replay.toByteArray(Charsets.UTF_8))); putBoolean("originalEqualsProcessed", false)
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
            assertEquals(completed, EbookJourneyProbe.completedOperationNames(current))

            assertNoRestoreWrite(completed)
            assertEquals(baselineSha, shaFile(baselineFile))
            assertEquals(protected, model.state.value.books.single { it.id == protected.id })
            assertEquals(protectedEpub, model.state.value.books.single { it.id == protectedEpub.id })
            marker("mobi-cold-no-new-position-write-observed", Bundle().apply {
                putString("bookId", bookId); putInt("chapter", 3); putInt("scalarOffset", expectedScalar)
                putBoolean("noGrant", true); putBoolean("noPan", true); putBoolean("noSave", true)
                putBoolean("newPositionIntentAbsent", true); putBoolean("lastPositionSaveAttemptAbsent", true)
                putBoolean("sameCompletedSet", true); putBoolean("unknownRawUnchanged", true)
            })
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            if (primary == null) {
                try {
                    finishUnderOriginalBarrier()
                    assertNoRestoreWrite(checkNotNull(beforeCompleted))
                    assertEquals(checkNotNull(priorSha), shaFile(checkNotNull(priorFile)))
                } catch (cleanup: Throwable) { throw cleanup }
            } else marker("activity-retained-for-controller") // No additional UI/business cleanup on failure.
        }
        marker("method-exit")
    }

    /** No new journal/attempt is allowed, including natural Activity ON_STOP/VM retirement. */
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

    private fun shaFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun restoredOwnerFacts(mount: ReaderEbookUiMount, web: WebView, ready: ReaderEbookReady): JSONObject = compose.runOnUiThread {
        val model = current.readerViewModel; val reading = checkNotNull(model.state.value.reading)
        assertSame(mount, EbookJourneyProbe.readyMount(current, SHELL)); assertSame(web, EbookJourneyProbe.webView(current, SHELL)); assertSame(mount, field(model, "ebookMount"))
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
        private const val METHOD = "existingMobiConfirmedNewPositionColdRestoresWithoutGrantOrWrites"
        private const val IMPORT_ID = "android-import-edfcd87b-f962-4830-ba12-7b0bb60f7f55"
        private const val UNKNOWN_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val UNKNOWN_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
        private const val UNKNOWN_RUNTIME = "aa519b17-bc13-4f47-841b-a31f9de9a1cc"
        private const val PROTECTED_EPUB_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val SOURCE_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
        private const val SOURCE_BYTES = 8522384L
        private const val PROTECTED_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
    }

}
