package io.reader.android

import android.graphics.Rect
import android.os.Bundle
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
import io.reader.android.runtime.*
import io.reader.android.ui.ReaderOnlineReadingUi
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import kotlinx.coroutines.Job
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One real already-added stored-source text chapter. Source-only, no network/preflight or business bypass. */
@RunWith(AndroidJUnit4::class)
class UiRealOnlineReadingJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private lateinit var key: ShelfBookKey
    private lateinit var title: String
    private lateinit var chapterTitle: String
    private lateinit var expectedScope: JSONObject
    private lateinit var sourceVersion: String
    private lateinit var baseline: String
    private lateinit var appOwner: AppCoreOwner
    private lateinit var client: AppCoreOwner.Client
    private lateinit var runtime: String
    private var restoreScalar = -1
    private var readingIdentity: String? = null
    private var nativeScroll: ScrollView? = null
    private val method = "existingRealStoredSourceTextContinuesPansAutoSavesAndExitsWithOriginalReceipts"
    @get:Rule val rules: TestRule = RuleChain.outerRule(Timeout.builder().withTimeout(150, TimeUnit.SECONDS).build()).around(compose)

    @Test fun existingRealStoredSourceTextContinuesPansAutoSavesAndExitsWithOriginalReceipts() {
        marker("method-enter")
        var primary: Throwable? = null
        var phase = "actual-binding-before-launch"
        try {
            val args = InstrumentationRegistry.getArguments()
            fun required(name: String) = args.getString(name)?.takeIf { it.isNotBlank() && !it.contains("PENDING") }
                ?: throw AssertionError("Mandatory actual argument missing: $name")
            baseline = required("baselineProofSha256"); assertTrue(baseline.matches(Regex("[a-f0-9]{64}")))
            key = ShelfBookKey(required("sourceId"), required("bookId")); assertNotEquals("local", key.sourceId)
            title = required("expectedTitle"); chapterTitle = required("expectedChapterTitle")
            sourceVersion = required("expectedSourceVersion")
            expectedScope = JSONObject(required("expectedEntryScope"))
            assertEquals(setOf("sourceId", "bookId", "chapterIndex", "bodyVersion", "processingVersion"), expectedScope.keys().asSequence().toSet())
            assertEquals(key.sourceId, expectedScope.getString("sourceId")); assertEquals(key.bookId, expectedScope.getString("bookId"))
            assertTrue(expectedScope.get("chapterIndex") is Int); assertTrue(expectedScope.getInt("chapterIndex") >= 0)
            for (field in listOf("bodyVersion", "processingVersion")) assertTrue(expectedScope.getString(field).matches(Regex("[a-f0-9]{64}")))
            restoreScalar = required("expectedRestoreScalarOffset").toIntOrNull() ?: throw AssertionError("Actual restore scalar required")
            assertTrue(restoreScalar >= 0)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(15_000) { model.state.value.ready && model.state.value.pendingReady && !model.state.value.busy }
            compose.waitForIdle(); healthy(model)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.onlineReading)
            assertNull(model.state.value.lastOnlineSave)
            appOwner = (current.application as ReaderApplication).coreOwner
            val libraryDelegate = field(model, "libraryDelegate") as Lazy<*>
            assertTrue(libraryDelegate.isInitialized())
            client = field(checkNotNull(libraryDelegate.value), "client") as AppCoreOwner.Client
            assertSame(appOwner, client.owner); runtime = initializedActor().runtimeSession; UUID.fromString(runtime)
            val localBefore = model.state.value.books
            val remoteBefore = model.state.value.remoteBooks.associateBy { it.key }
            val selected = remoteBefore[key] ?: throw AssertionError("Actual admitted source/book is absent from the real shelf")
            assertEquals(title, selected.title)
            val completedBefore = completedNames(); val unknownBefore = unknown()
            val witness = JSONObject().put("witnessSchema", 1).put("baselineProofSha256", baseline)
                .put("sourceId", key.sourceId).put("bookId", key.bookId).put("runtimeSession", runtime)
            marker("online-reading-input-bound", identityBundle().apply {
                putString("baselineProofSha256", baseline); putString("expectedTitle", title)
                putString("expectedChapterTitle", chapterTitle); putString("expectedSourceVersion", sourceVersion)
                putString("expectedEntryScope", expectedScope.toString()); putInt("expectedRestoreScalarOffset", restoreScalar)
            })

            phase = "real-remote-shelf-to-directory"
            val shelfRow = hasTestTag("reader_remote_shelf_metadata") and hasAnyDescendant(hasText(title, substring = false))
            compose.onNodeWithTag("reader_bookshelf", useUnmergedTree = true).performScrollToNode(shelfRow)
            compose.onNode(shelfRow, useUnmergedTree = true).assertIsDisplayed().performTouchInput { click() }
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.onlineDirectory != null || failed(model)) }
            compose.waitForIdle(); healthy(model)
            assertEquals(ReaderRoute.OnlineDirectory, model.state.value.route); assertNull(model.state.value.reading)
            val directory = checkNotNull(model.state.value.onlineDirectory)
            assertSame(selected, directory.book); assertEquals(key, directory.page.directory.key)
            assertEquals(sourceVersion, directory.page.directory.sourceVersion)
            assertEquals(client.lease, directory.page.directory.lease); assertEquals(runtime, directory.page.directory.runtime)
            val directoryOriginal = catalogOriginal("reading.directory.view.open.v2")
            val dirData = JSONObject(directoryOriginal.getString("parsedTerminalJson")).getJSONObject("data")
            assertEquals(directory.page.directory.viewId, dirData.getString("viewId"))
            assertEquals(directory.page.directory.catalogRevision, dirData.getString("catalogRevision"))
            assertEquals(directory.page.directory.structureRevision, dirData.getString("structureRevision"))
            witness.put("directoryOriginal", directoryOriginal)
            marker("online-reading-real-directory-issued", identityBundle().apply {
                putString("directoryIdentity", directory.identity); putString("directoryId", directory.page.directory.id)
                putString("viewId", directory.page.directory.viewId); putString("catalogRevision", directory.page.directory.catalogRevision)
                putString("structureRevision", directory.page.directory.structureRevision); putString("sourceVersion", directory.page.directory.sourceVersion)
                putInt("issuedNodeCount", directory.nodes.size); putString("entryAction", "PREPARED_CONTINUE")
            })

            phase = "real-prepared-continue-and-native-ready"
            compose.onNodeWithTag("reader_online_resume").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.onlineReading != null || failed(model)) }
            compose.waitForIdle(); healthy(model)
            val opened = checkNotNull(model.state.value.onlineReading)
            assertScope(opened); assertEquals(restoreScalar, opened.opened.restoreScalarOffset)
            assertEquals(restoreScalar, opened.visibleScalar); assertFalse(opened.controls)
            val progressRead = catalogOriginal("reading.progress.get")
            val progressData = JSONObject(progressRead.getString("parsedTerminalJson")).getJSONObject("data")
            assertEquals(opened.opened.progressRevision, progressData.getString("progressRevision"))
            witness.put("entryProgressRead", progressRead).put("entryObservationClaim", "ORIGINAL_READ_NOT_AN_ACK_FOR_ANY_WRITE")
            nativeReady(opened); assertNativeAnchor(opened, restoreScalar)
            assertEquals(completedBefore, completedNames()); assertArrayEquals(unknownBefore, unknown())
            marker("online-native-ready-draw-anchor-visible", readingBundle(opened).apply {
                putInt("scalarOffset", restoreScalar); putInt("utf16Length", opened.opened.chapter.text.length)
                putInt("scalarCount", opened.opened.chapter.scalarCount); putString("contentSha256", sha(opened.opened.chapter.text.toByteArray(Charsets.UTF_8)))
                putString("freshObservedProgressRevision", opened.opened.progressRevision); putBoolean("localReadingSessionAbsent", model.state.value.reading == null)
                putString("nativeClaim", "FULL_CORE_ENTRY_UTF16_AND_ACTUAL_SCALAR_LINE_DRAW_NOT_RAW_HTTP_OR_LOCAL_ORIGINAL")
            })

            phase = "one-real-native-pan"
            val viewport = viewport(); assertTrue("Actual chapter must have forward native scroll extent", compose.runOnUiThread { checkNotNull(nativeScroll).canScrollVertically(1) })
            val x = viewport.centerX(); val start = viewport.top + viewport.height() * 4 / 5; val end = viewport.top + viewport.height() / 4
            assertTrue(start > end && viewport.height() > 100)
            val returned = device.swipe(x, start, x, end, 24) // Exactly one real touch stream. Boolean is not an anchor/ACK.
            compose.waitUntil(15_000) {
                val reading = model.state.value.onlineReading
                failed(model) || (reading?.identity == opened.identity && reading.opened === opened.opened && reading.visibleScalar > restoreScalar)
            }
            healthy(model)
            compose.waitUntil(15_000) {
                failed(model) || (freshRecords(completedBefore).size == 1 && (field(model, "delayedSave") as? Job)?.isCompleted == true)
            }
            healthy(model); val afterPan = checkNotNull(model.state.value.onlineReading); assertScope(afterPan)
            assertEquals(opened.identity, afterPan.identity); assertSame(opened.opened, afterPan.opened)
            assertTrue(afterPan.visibleScalar > restoreScalar); assertFalse(afterPan.controls)
            val auto = freshRecords(completedBefore).single()
            assertReceipt(auto, afterPan, opened.opened.progressRevision)
            assertNoActiveFor(auto.second.getString("id")); nativeReady(afterPan); assertNativeAnchor(afterPan, afterPan.visibleScalar)
            marker("online-scroll-visible-anchor", readingBundle(afterPan).apply {
                putInt("scalarOffset", afterPan.visibleScalar); putBoolean("swipeReturned", returned); putInt("swipeCalls", 1)
                putInt("startX", x); putInt("startY", start); putInt("endX", x); putInt("endY", end)
                putBoolean("autoObserverCompleted", true); putBoolean("autoActiveAbsent", true); putBoolean("autoCandidateAbsent", true)
            })
            marker("online-auto-original-save-ACK", receiptBundle(auto, afterPan))
            val autoRevision = ProgressReceiptPolicy.revision(auto.second, auto.second.getJSONObject("wireTerminal"))

            phase = "real-controls-and-normal-exit"
            val tap = viewport(); device.click(tap.left + tap.width() / 2, tap.top + tap.height() / 2)
            compose.waitUntil(5_000) { model.state.value.onlineReading?.controls == true || failed(model) }
            compose.waitForIdle(); healthy(model)
            val beforeExit = checkNotNull(model.state.value.onlineReading)
            assertEquals(afterPan.visibleScalar, beforeExit.visibleScalar); assertEquals(opened.identity, beforeExit.identity)
            assertEquals(1, freshRecords(completedBefore).size)
            compose.onNodeWithTag("reader_online_exit").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Bookshelf || failed(model)) }
            compose.waitForIdle(); healthy(model)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.onlineReading); assertNull(model.state.value.reading)
            val finalRecords = freshRecords(completedBefore); assertEquals(2, finalRecords.size)
            assertEquals(completedBefore + finalRecords.map { it.first.name }, completedNames())
            val exit = finalRecords.single { it.second.getString("id") != auto.second.getString("id") }
            assertReceipt(exit, beforeExit, autoRevision)
            assertNotEquals(auto.second.getLong("requestId"), exit.second.getLong("requestId")); assertNoActiveFor(exit.second.getString("id"))
            marker("online-exit-original-save-ACK", receiptBundle(exit, beforeExit))
            assertEquals(localBefore, model.state.value.books)
            assertEquals(remoteBefore, model.state.value.remoteBooks.associateBy { it.key })
            assertSame(appOwner, (current.application as ReaderApplication).coreOwner); assertEquals(runtime, initializedActor().runtimeSession)
            assertArrayEquals(unknownBefore, unknown())
            val file = writeWitness(witness)
            marker("online-reading-bookshelf-returned", identityBundle().apply {
                putString("readingIdentity", opened.identity); putString("sessionToken", opened.opened.chapter.sessionToken)
                putInt("chapter", beforeExit.opened.chapter.index); putInt("scalarOffset", beforeExit.visibleScalar)
                putInt("newProgressOriginalCount", 2); putBoolean("localReadingSessionAbsent", true)
                putString("witnessCacheFile", file.name); putInt("witnessBytes", file.length().toInt()); putString("witnessSha256", sha(file.readBytes()))
            })
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failed", Bundle().apply { putString("failurePhase", phase); putString("failureClass", failure.javaClass.name) }) }
            catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
            throw failure
        } finally {
            try { finishOnlyBookshelf() } catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
        }
        marker("method-exit")
    }

    private fun failed(model: ReaderViewModel): Boolean {
        val s = model.state.value
        return s.error != null || s.onlineError != null || s.onlineOriginal != null || s.protocolRecoveryReference != null ||
            s.pendingImports.isNotEmpty() || s.pendingBookmarks.isNotEmpty() || s.pendingSearchClose != null ||
            s.pendingPositions.any { it.id != UNKNOWN_ID }
    }
    private fun healthy(model: ReaderViewModel) {
        val s = model.state.value
        assertTrue(s.ready && s.pendingReady); assertFalse(s.busy); assertFalse(failed(model)); assertNull(s.reading)
        assertEquals(listOf(UNKNOWN_ID), s.pendingPositions.map { it.id }); assertEquals("local", s.pendingPositions.single().sourceId)
        assertEquals(0L, s.importRevision); unknown()
        val protocol = (current.application as ReaderApplication).coreProtocolRecovery.view.value
        assertTrue(protocol.initialized && protocol.admissionOpen && protocol.items.isEmpty())
    }
    private fun assertScope(reading: ReaderOnlineReadingUi) {
        val c = reading.opened.chapter
        assertEquals(key, reading.book.key); assertEquals(title, reading.book.title); assertEquals(key, c.key)
        assertEquals(chapterTitle, c.title); assertEquals(sourceVersion, reading.opened.sourceVersion)
        assertEquals(expectedScope.getInt("chapterIndex"), c.index); assertEquals(expectedScope.getString("bodyVersion"), c.bodyVersion)
        assertEquals(expectedScope.getString("processingVersion"), c.processingVersion); assertEquals(ReaderTextPresentationKind.ONLINE_TEXT, c.presentationKind)
        assertTrue(c.text.isNotBlank()); UUID.fromString(c.sessionToken); UUID.fromString(reading.identity)
        if (readingIdentity == null) readingIdentity = reading.identity else assertEquals(readingIdentity, reading.identity)
        assertSame(appOwner, client.owner); assertEquals(runtime, initializedActor().runtimeSession)
    }
    private fun nativeReady(reading: ReaderOnlineReadingUi) {
        assertScope(reading); compose.waitForIdle()
        compose.waitUntil(10_000) {
            val actual = current.readerViewModel.state.value.onlineReading
            actual != null && actual.identity == reading.identity && actual.opened === reading.opened && compose.runOnUiThread {
                val scroll = current.findViewById<ScrollView>(R.id.reader_native_scroll)
                val body = current.findViewById<TextView>(R.id.reader_native_text); val layout = body?.layout
                scroll != null && body != null && scroll.isAttachedToWindow && body.isAttachedToWindow && scroll.isShown && body.isShown &&
                    !scroll.isLayoutRequested && !body.isLayoutRequested && layout != null && reading.opened.chapter.text.contentEquals(body.text) &&
                    layout.width == body.width - body.totalPaddingLeft - body.totalPaddingRight && field(scroll, "restoring") == false &&
                    field(scroll, "released") == false && field(scroll, "logicalScalar") == reading.visibleScalar &&
                    field(scroll, "rawVerifiedEpoch") == field(scroll, "epoch")
            } && compose.onAllNodesWithTag("reader_restore_loading", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        val scroll = current.findViewById<ScrollView>(R.id.reader_native_scroll)
        if (nativeScroll == null) nativeScroll = scroll else assertSame(nativeScroll, scroll)
        val owner = field(scroll, "owner")!!
        assertEquals(reading.nativeOwner, field(owner, "identity")); assertSame(reading.opened.chapter, field(owner, "chapter"))
        attachedDraw(scroll); compose.waitForIdle()
        compose.onNodeWithTag("reader_restore_loading", useUnmergedTree = true).assertDoesNotExist()
    }
    private fun assertNativeAnchor(reading: ReaderOnlineReadingUi, scalar: Int) {
        val text = reading.opened.chapter.text; val utf16 = ReaderTextCoordinates.utf16Offset(text, scalar)
        compose.runOnUiThread {
            val body = current.findViewById<TextView>(R.id.reader_native_text); val layout = body.layout ?: throw AssertionError("Actual native Layout absent")
            assertEquals(text, body.text.toString()); assertTrue(text.contentEquals(layout.text))
            val visible = viewportOnMain(); val origin = IntArray(2).also(body::getLocationOnScreen); val line = layout.getLineForOffset(utf16)
            val top = origin[1] + body.totalPaddingTop - body.scrollY + layout.getLineTop(line)
            val bottom = origin[1] + body.totalPaddingTop - body.scrollY + layout.getLineBottom(line)
            assertTrue("Actual Core scalar's native line must intersect the real viewport", top < visible.bottom && bottom > visible.top)
        }
    }
    private fun viewport(): Rect = compose.runOnUiThread { viewportOnMain() }
    private fun viewportOnMain(): Rect {
        val scroll = current.findViewById<ScrollView>(R.id.reader_native_scroll)
        val visible = Rect(); assertTrue(scroll.isAttachedToWindow && scroll.isShown && scroll.getGlobalVisibleRect(visible))
        val at = IntArray(2).also(scroll::getLocationOnScreen)
        assertTrue(visible.intersect(at[0] + scroll.paddingLeft, at[1] + scroll.paddingTop,
            at[0] + scroll.width - scroll.paddingRight, at[1] + scroll.height - scroll.paddingBottom)); return visible
    }
    private fun attachedDraw(view: View) {
        val drawn = AtomicBoolean(false); lateinit var observer: ViewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener { if (view.isAttachedToWindow && view.isShown) drawn.set(true) }
        compose.runOnUiThread { observer = view.viewTreeObserver; observer.addOnDrawListener(listener); view.invalidate() }
        try { compose.waitUntil(5_000) { drawn.get() }; instrumentation.runOnMainSync { assertTrue(view.isAttachedToWindow && view.isShown) } }
        finally { compose.runOnUiThread { if (observer.isAlive) observer.removeOnDrawListener(listener) } }
    }
    private fun completedNames() = File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty()
        .filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }
    private fun freshRecords(before: Set<String>) = (completedNames() - before).map { name ->
        val file = File(current.filesDir, "Reader/Operations/Completed/$name")
        assertTrue(file.length() in 1..64L * 1024); assertFalse(File(file.path + ".new").exists()); assertFalse(File(file.path + ".bak").exists())
        val raw = file.readBytes(); Triple(file, JSONObject(raw.toString(Charsets.UTF_8)), raw)
    }
    private fun assertReceipt(row: Triple<File, JSONObject, ByteArray>, reading: ReaderOnlineReadingUi, expectedRevision: String) {
        val r = row.second; val p = r.getJSONObject("parameters"); val wire = r.getJSONObject("wireTerminal")
        assertEquals(1, r.getInt("journalSchema")); assertEquals("progress", r.getString("kind")); assertTrue(r.getBoolean("completed"))
        assertEquals(key, ProgressReceiptPolicy.key(r)); assertEquals(runtime, r.getString("runtimeSession"))
        assertEquals(reading.opened.chapter.index, p.getInt("chapterIndex")); assertEquals(reading.visibleScalar, p.getInt("chapterOffset"))
        assertEquals(expectedRevision, p.getString("expectedProgressRevision")); assertEquals(reading.opened.chapter.bodyVersion, p.getString("expectedBodyVersion"))
        assertEquals(reading.opened.chapter.processingVersion, p.getString("expectedProcessingVersion")); assertTrue(r.getLong("requestId") > 0)
        assertEquals(1, wire.getInt("protocolVersion")); assertTrue(wire.getLong("deliveryId") > 0)
        val revision = ProgressReceiptPolicy.revision(r, wire)
        val projection = checkNotNull(current.readerViewModel.state.value.lastOnlineSave)
        assertEquals(reading.identity, projection.readingIdentity); assertEquals(reading.opened.chapter.sessionToken, projection.sessionToken)
        val result = projection.result
        assertEquals(r.getString("id"), result.originalId); assertEquals(runtime, result.runtimeSession); assertEquals(key.sourceId, result.sourceId)
        assertEquals(key.bookId, result.bookId); assertEquals(r.getLong("requestId"), result.requestId); assertEquals(revision, result.progressRevision)
        assertEquals(reading.visibleScalar, result.scalarOffset); assertEquals(reading.opened.chapter.index, result.chapterIndex)
        assertTrue(ProgressJournalCandidatePolicy.sameObject(p, JSONObject(result.originalParametersJson)))
        assertTrue(ProgressJournalCandidatePolicy.sameObject(wire, JSONObject(result.parsedTerminalJson)))
    }
    private fun assertNoActiveFor(id: String) {
        val f = File(current.filesDir, "Reader/Operations/$id.json")
        assertFalse(f.exists()); assertFalse(File(f.path + ".new").exists()); assertFalse(File(f.path + ".bak").exists())
    }
    private fun receiptBundle(row: Triple<File, JSONObject, ByteArray>, reading: ReaderOnlineReadingUi) = readingBundle(reading).apply {
        val r = row.second; val p = r.getJSONObject("parameters"); val wire = r.getJSONObject("wireTerminal")
        putString("originalId", r.getString("id")); putString("originalRecordSha256", sha(row.third)); putInt("originalRecordBytes", row.third.size)
        putLong("requestId", r.getLong("requestId")); putString("runtimeSession", r.getString("runtimeSession")); putInt("scalarOffset", p.getInt("chapterOffset"))
        putString("expectedRevision", p.getString("expectedProgressRevision")); putString("ackRevision", ProgressReceiptPolicy.revision(r, wire))
        putString("originalParametersJson", p.toString()); putString("parsedTerminalJson", wire.toString())
        putString("wireClaim", "SERIALIZED_PARSED_JSON_NOT_BYTE_EXACT_NATIVE_WIRE")
    }
    private fun identityBundle() = Bundle().apply {
        putString("sourceId", key.sourceId); putString("bookId", key.bookId); putString("runtimeSession", runtime)
        putString("ownerId", client.lease.ownerId.toString()); putString("clientId", client.lease.clientId.toString())
        putLong("ownerGeneration", client.lease.ownerGeneration); putLong("localGeneration", client.lease.localGeneration)
    }
    private fun readingBundle(reading: ReaderOnlineReadingUi) = identityBundle().apply {
        putString("readingIdentity", reading.identity); putLong("nativeOwner", reading.nativeOwner)
        putString("sessionToken", reading.opened.chapter.sessionToken); putInt("chapter", reading.opened.chapter.index)
        putString("bodyVersion", reading.opened.chapter.bodyVersion); putString("processingVersion", reading.opened.chapter.processingVersion)
        putString("sourceVersion", reading.opened.sourceVersion)
    }
    /** Inspect only existing warm facts, never call Library, a transport, Native or an actor command. */
    private fun catalogOriginal(method: String): JSONObject = compose.runOnUiThread {
        val delegate = field(appOwner, "onlineCatalogService\$delegate") as Lazy<*>
        assertTrue(delegate.isInitialized()); val originals = field(checkNotNull(delegate.value), "originals")!!
        val calls = field(originals, "calls") as Map<*, *>; assertEquals(1, calls.size); val call = calls.values.single()!!
        assertEquals(method, field(call, "method")); assertEquals(runtime, field(call, "runtime")); assertEquals(true, field(call, "transportAcknowledged"))
        val parameters = field(call, "params") as String; val terminal = field(call, "terminal") as String
        val request = field(call, "requestId") as Long; val wire = JSONObject(terminal); val p = JSONObject(parameters)
        assertEquals(key.sourceId, p.getString("sourceId")); assertEquals(key.bookId, p.getString("bookId"))
        assertEquals(1, wire.getInt("protocolVersion")); assertEquals(request, wire.getLong("requestId")); assertEquals("result", wire.getString("type")); assertTrue(wire.getLong("deliveryId") > 0)
        CoreTerminal.result(method, wire)
        JSONObject().put("method", method).put("runtimeSession", runtime).put("requestId", request)
            .put("originalParametersJson", parameters).put("parsedTerminalJson", terminal).put("transportAcknowledged", true)
    }
    private fun initializedActor(): ReaderCoreEventActor {
        val delegate = field(appOwner, "actorDelegate") as Lazy<*>; assertTrue(delegate.isInitialized()); return delegate.value as ReaderCoreEventActor
    }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun writeWitness(value: JSONObject): File {
        val raw = value.toString().toByteArray(Charsets.UTF_8); assertTrue(raw.size in 1..8 * 1024 * 1024)
        val file = File(current.cacheDir, "reader-a4-reading-originals-${UUID.randomUUID()}.json")
        assertTrue(file.createNewFile()); FileOutputStream(file).use { it.write(raw); it.fd.sync() }; return file
    }
    private fun unknown(): ByteArray {
        val directory = File(current.filesDir, "Reader/Operations"); val base = File(directory, "$UNKNOWN_ID.json")
        assertEquals(739L, base.length()); val raw = base.readBytes(); assertEquals(UNKNOWN_SHA, sha(raw))
        assertFalse(JSONObject(raw.toString(Charsets.UTF_8)).has("wireTerminal")); assertFalse(File(base.path + ".new").exists()); assertFalse(File(base.path + ".bak").exists())
        assertFalse(File(directory, "Completed/$UNKNOWN_ID.json").exists())
        assertFalse(File(directory, "Completed/$UNKNOWN_ID.json.new").exists()); assertFalse(File(directory, "Completed/$UNKNOWN_ID.json.bak").exists()); return raw
    }
    private fun finishOnlyBookshelf() {
        val owned = scenario ?: return
        if (owned.state == Lifecycle.State.DESTROYED) { owned.close(); marker("activity-destroyed"); return }
        if (!::current.isInitialized) return
        val model = current.readerViewModel
        if (model.state.value.route != ReaderRoute.Bookshelf || model.state.value.busy || model.state.value.onlineReading != null || failed(model)) {
            marker("activity-retained-for-controller"); throw AssertionError("Failure retains its actual reading/original state for the sole controller stop/copy")
        }
        val destroyed = CountDownLatch(1)
        val listener = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
        instrumentation.runOnMainSync { healthy(model); current.lifecycle.addObserver(listener); current.finish() }
        assertTrue(destroyed.await(5, TimeUnit.SECONDS)); compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
        instrumentation.runOnMainSync { current.lifecycle.removeObserver(listener) }; owned.close(); marker("activity-destroyed")
    }
    private fun sha(raw: ByteArray) = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerMethod", method); details.putString("readerStep", step)
        details.putInt("readerProcessPid", android.os.Process.myPid()); details.putLong("readerElapsedMs", android.os.SystemClock.elapsedRealtime())
        instrumentation.sendStatus(2, details)
    }
    companion object {
        private const val UNKNOWN_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val UNKNOWN_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
    }
}
