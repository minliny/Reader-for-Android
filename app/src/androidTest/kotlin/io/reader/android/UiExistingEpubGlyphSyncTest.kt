package io.reader.android

import android.app.KeyguardManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.util.AtomicFile
import android.view.View
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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

/** Continue the already finalized real EPUB. No import/SAF, second Library/client, parser or retry. */
@RunWith(AndroidJUnit4::class)
class UiExistingEpubGlyphSyncTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(120, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun existingRealZhongSongDirectoryGlyphAfterComposeReadyExitsWithOriginalReceipt() {
        marker("method-enter") // Before launch/current Activity access.
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            assertEquals("终宋.epub", args.getString("displayName"))
            assertEquals(SOURCE_SHA, args.getString("expectedSHA"))
            assertEquals(SOURCE_BYTES, args.getString("expectedBytes")?.toLongOrNull() ?: throw AssertionError("expectedBytes required"))
            assertEquals(PROTECTED_SHA, args.getString("protectedBookSHA"))
            assertEquals(IMPORT_ID, args.getString("expectedImportId"))
            assertEquals(PENDING_ID, args.getString("expectedPendingId"))
            assertEquals(1, args.getString("chapterIndex")?.toIntOrNull() ?: throw AssertionError("chapterIndex required"))
            assertEquals("目录", args.getString("chapterTitle"))
            assertEquals("OEBPS/Text/toc.xhtml", args.getString("entryPath"))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            val initial = model.state.value
            assertTrue(initial.ready && initial.pendingReady && initial.reading == null && initial.route == ReaderRoute.Bookshelf)
            assertEquals(0L, initial.importRevision)
            assertNull(initial.error); assertNull(initial.protocolRecoveryReference)
            assertTrue(initial.pendingImports.isEmpty() && initial.pendingBookmarks.isEmpty())
            assertNull(initial.pendingSearchClose)
            val pending = initial.pendingPositions.single()
            assertEquals(PENDING_ID, pending.id); assertEquals(bookId, pending.bookId)
            assertEquals("reading.progress.update", pending.stage)
            val protected = initial.books.single { it.id == "local:$PROTECTED_SHA" }
            assertEquals(1, protected.progressChapter); assertEquals(210, protected.progressOffset)
            val book = initial.books.single { it.id == bookId }
            assertEquals(1368, book.chapterCount)
            val importId = IMPORT_ID // This is the complete real Host ID, including android-import- prefix.
            val selectedIndex = args.getString("chapterIndex")?.toIntOrNull() ?: throw AssertionError("chapterIndex required")
            assertTrue(selectedIndex in 0 until book.chapterCount)
            val selectedTitle = args.getString("chapterTitle")?.takeIf { it.isNotBlank() } ?: throw AssertionError("chapterTitle required")
            val selectedEntry = args.getString("entryPath")?.takeIf { it.isNotBlank() } ?: throw AssertionError("entryPath required")
            val importFile = File(current.filesDir, "Reader/Operations/Completed/$importId.json")
            assertTrue(importFile.length() in 1..8L * 1024 * 1024)
            val importRaw = AtomicFile(importFile).openRead().use { it.readBytes() }
            val record = JSONObject(importRaw.toString(Charsets.UTF_8))
            assertEquals(importId, record.getString("id")); assertEquals("import", record.getString("kind"))
            assertTrue(record.getBoolean("completed")); assertEquals("finalize", record.getString("terminalStage"))
            assertEquals("settled", record.getJSONObject("settlement").getString("state"))
            val binding = record.getJSONObject("binding")
            assertEquals(bookId, binding.getString("bookId")); assertEquals(SOURCE_SHA, binding.getString("sourceSha256"))
            assertEquals(SOURCE_BYTES, binding.getLong("sourceBytes")); assertEquals("epub", binding.getString("format"))
            val original = File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source")
            assertEquals(SOURCE_BYTES, original.length()); assertEquals(SOURCE_SHA, shaFile(original))
            val beforeReconcile = EbookJourneyProbe.completedOperationNames(current)
            val activeFile = File(current.filesDir, "Reader/Operations/$PENDING_ID.json")
            assertTrue(activeFile.length() in 1..8L * 1024 * 1024)
            val activeRaw = activeFile.readBytes() // Read-only: do not let AtomicFile cleanup a candidate.
            val active = JSONObject(activeRaw.toString(Charsets.UTF_8))
            assertEquals(PENDING_ID, active.getString("id")); assertEquals("progress", active.getString("kind"))
            assertEquals(bookId, active.getString("bookId")); assertFalse(active.getBoolean("completed"))
            assertEquals(1015L, active.getLong("requestId")); assertEquals(PENDING_RUNTIME, active.getString("runtimeSession"))
            val originalWire = active.getJSONObject("wireTerminal")
            val originalParameters = active.getJSONObject("parameters")
            assertEquals(1, originalParameters.getInt("chapterIndex")); assertEquals(0, originalParameters.getInt("chapterOffset"))
            val originalRevision = ProgressReceiptPolicy.revision(active, originalWire)
            assertEquals(PENDING_ACK_REVISION, originalRevision)
            val priorCompleted = File(current.filesDir, "Reader/Operations/Completed/$PENDING_ID.json")
            assertFalse("Already reconciled is a different precondition; do not silently skip", priorCompleted.exists())
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue("Physical screen must already be on", UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse("Physical screen must already be unlocked", checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            assertTrue(compose.runOnUiThread { current.window.decorView.hasWindowFocus() })
            marker("epub-physical-interaction-preflight", Bundle().apply {
                putBoolean("screenOn", true); putBoolean("keyguardLocked", false); putBoolean("windowFocused", true)
            })
            marker("epub-input-bound", Bundle().apply {
                putString("displayName", "终宋.epub"); putString("expectedSHA", SOURCE_SHA); putLong("expectedBytes", SOURCE_BYTES)
                putString("protectedBookSHA", PROTECTED_SHA)
            })

            marker("epub-existing-import-finalized-settled-original", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", book.chapterCount)
                putString("originalId", importId); putString("originalRecordSha256", sha(importRaw))
                putString("format", binding.getString("format")); putString("authorityEpoch", binding.getString("authorityEpoch"))
                putLong("sequence", binding.getLong("sequence")); putLong("sourceBytes", original.length()); putString("sourceSHA", SOURCE_SHA)
                putLong("importRevision", initial.importRevision); putBoolean("reimportDispatched", false)
            })

            phase = "reconcile-one-prior-original-position"
            compose.onNodeWithTag("reader_reconcile_position").assertIsEnabled().performClick()
            compose.waitUntil(15_000) {
                val state = model.state.value
                !state.busy && (state.pendingPositions.isEmpty() || state.error != null || state.protocolRecoveryReference != null)
            }
            healthy(model, 0)
            assertTrue(model.state.value.pendingPositions.isEmpty())
            assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            assertTrue(priorCompleted.length() in 1..8L * 1024 * 1024)
            val reconciledRaw = priorCompleted.readBytes()
            val reconciled = JSONObject(reconciledRaw.toString(Charsets.UTF_8))
            assertTrue(reconciled.getBoolean("completed")); assertEquals(PENDING_ID, reconciled.getString("id"))
            assertEquals("progress", reconciled.getString("kind")); assertEquals(bookId, reconciled.getString("bookId"))
            assertEquals(1015L, reconciled.getLong("requestId")); assertEquals(PENDING_RUNTIME, reconciled.getString("runtimeSession"))
            // Exact complete serialization also protects nested fields/arrays, not only selected ACK fields.
            assertEquals(originalParameters.toString(), reconciled.getJSONObject("parameters").toString())
            assertEquals(originalWire.toString(), reconciled.getJSONObject("wireTerminal").toString())
            assertEquals(originalRevision, ProgressReceiptPolicy.revision(reconciled, reconciled.getJSONObject("wireTerminal")))
            assertEquals(beforeReconcile + "$PENDING_ID.json", EbookJourneyProbe.completedOperationNames(current))
            assertFalse(activeFile.exists()); assertFalse(File(priorCompleted.parentFile, priorCompleted.name + ".new").exists())
            marker("epub-prior-position-original-reconciled", Bundle().apply {
                putString("bookId", bookId); putString("originalId", PENDING_ID); putLong("requestId", 1015L)
                putString("runtimeSession", PENDING_RUNTIME); putString("activeRecordSha256", sha(activeRaw))
                putString("originalRecordSha256", sha(reconciledRaw)); putString("originalWire", originalWire.toString())
                putString("expectedRevision", originalParameters.getString("expectedProgressRevision")); putString("ackRevision", originalRevision)
                putInt("chapter", 1); putInt("scalarOffset", 0); putBoolean("newSaveDispatched", false)
            })
            val beforeExit = EbookJourneyProbe.completedOperationNames(current) // The old conversion is not counted as the one later exit save.

            phase = "shelf-core-directory-once"
            // Directory while no Reading exists admits no old-session progress save.
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_directory_$bookId"))
            compose.onNodeWithTag("reader_book_directory_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Directory || failed(model)) }
            healthy(model, 0); assertNull(model.state.value.reading)
            val toc = model.state.value.chapters
            assertEquals(book.chapterCount, toc.size)
            val selected = toc.single { it.index == selectedIndex }
            assertEquals(selectedTitle, selected.title) // Root must bind one actual TOC target; no alternate chapter.
            marker("epub-core-directory-bound", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", toc.size); putInt("selectedChapter", selected.index)
                putString("selectedTitle", selected.title); putString("expectedEntryPath", selectedEntry)
            })
            compose.onNodeWithTag("reader_directory").performScrollToNode(hasTestTag("reader_chapter_${selected.index}"))
            compose.onNodeWithTag("reader_chapter_${selected.index}").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model, 0)
            val reading = model.state.value.reading ?: throw AssertionError("Original Core chapter absent")
            assertEquals(ReaderLocalBookFormat.EPUB, reading.format)
            assertEquals(bookId, reading.chapter.bookId); assertEquals(selected.index, reading.chapter.index)
            assertTrue(reading.chapter.text.any { !it.isWhitespace() })
            assertEquals(0, reading.visibleScalarOffset); assertFalse(model.state.value.controlsVisible)
            assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))

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
            val presentation = prepared.publication.presentation
            assertEquals(selectedEntry, presentation.entryPath)
            val canonical = presentation.documentCanonicalContent
            val canonicalScalars = canonical.codePointCount(0, canonical.length)
            assertTrue(presentation.chapterStartScalar >= 0 && presentation.chapterEndScalar in presentation.chapterStartScalar..canonicalScalars)
            assertEquals(reading.chapter.text, canonical.substring(canonical.offsetByCodePoints(0, presentation.chapterStartScalar),
                canonical.offsetByCodePoints(0, presentation.chapterEndScalar)))
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
            val metrics = compose.runOnUiThread {
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
            assertTrue(Build.VERSION.SDK_INT >= 26)
            val provider = WebView.getCurrentWebViewPackage() ?: throw AssertionError("Actual WebView provider absent")
            marker("epub-real-webview-ready-draw-observed-dom", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", reading.chapter.index); putInt("scalarOffset", ready.scalar); putInt("scalarCount", reading.chapter.scalarCount)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("ownerId", lease.ownerId.toString()); putString("clientId", lease.clientId.toString()); putLong("ownerGeneration", lease.ownerGeneration)
                putString("runtimeSession", lease.runtimeSession); putString("physicalViewId", mount.view.physicalViewId.toString())
                putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putString("entryPath", presentation.entryPath); putString("origin", probe.getString("origin"))
                putInt("chapterStartScalar", presentation.chapterStartScalar); putInt("chapterEndScalar", presentation.chapterEndScalar)
                putInt("documentScalarCount", canonicalScalars); putString("documentCanonicalSha256", sha(canonical.toByteArray(Charsets.UTF_8)))
                putString("providerPackage", provider.packageName); putString("providerVersion", provider.versionName)
                putString("canonicalSha256", sha(reading.chapter.text.toByteArray(Charsets.UTF_8)))
                putString("observedDomTextSampleSha256", sha(probe.getString("domText").toByteArray(Charsets.UTF_8)))
                putString("probeMetrics", JSONObject(probe.toString()).apply { remove("domText") }.toString())
                putString("textClaim", "LIVE_DOM_TEXT_SAMPLE_OBSERVATION_ONLY_CANONICAL_AUTHENTICATION_IS_PRODUCTION_ADMIT_NATIVE_VISUAL_DRAW_READY")
            })
            val screen = File(current.cacheDir, "reader-a3-existing-epub-${lease.renderId}-draw.png")
            try { marker("epub-screenshot", Bundle().apply { putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name) }) }
            catch (failure: Exception) { marker("epub-screenshot", Bundle().apply { putBoolean("saved", false); putString("error", failure.javaClass.name) }) }
            assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))

            phase = "trusted-body-tap-and-one-normal-exit"
            assertTrue("Actual DOM-derived non-link visible content tap not dispatched", UiDevice.getInstance(instrumentation).click(metrics[0], metrics[1]))
            compose.waitUntil(5_000) { model.state.value.reading?.identity == reading.identity && model.state.value.controlsVisible && enabledExit() }
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView)
            assertTrue(enabledExit()); healthy(model, 0)
            assertEquals(reading.visibleScalarOffset, model.state.value.reading!!.visibleScalarOffset); assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))
            marker("epub-trusted-body-tap-controls-draw", Bundle().apply { putLong("readingOwner", reading.identity) })
            compose.onNodeWithTag("reader_exit").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.reading == null || failed(model)) }
            healthy(model, 0); assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            val saved = freshRecords(beforeExit).single()
            val progress = saved.second; val parameters = progress.getJSONObject("parameters"); val wire = progress.getJSONObject("wireTerminal")
            assertEquals("progress", progress.getString("kind")); assertEquals(bookId, progress.getString("bookId"))
            assertEquals(reading.chapter.index, parameters.getInt("chapterIndex")); assertEquals(reading.visibleScalarOffset, parameters.getInt("chapterOffset"))
            val revision = ProgressReceiptPolicy.revision(progress, wire)
            marker("epub-exit-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", progress.getString("id")); putLong("requestId", progress.getLong("requestId"))
                putString("runtimeSession", progress.getString("runtimeSession")); putInt("chapter", reading.chapter.index); putInt("scalarOffset", reading.visibleScalarOffset)
                putString("expectedRevision", parameters.getString("expectedProgressRevision")); putString("ackRevision", revision)
                putString("bodyVersion", parameters.getString("expectedBodyVersion")); putString("processingVersion", parameters.getString("expectedProcessingVersion"))
                putString("originalRecordSha256", sha(saved.third)); putString("originalWire", wire.toString())
            })
            assertEquals(protected, model.state.value.books.single { it.id == protected.id })
            assertEquals(sha(importRaw), shaFile(importFile))
            assertEquals(0L, model.state.value.importRevision)
            compose.onNodeWithTag("reader_shelf_query").assertIsDisplayed()
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

    private fun failed(model: ReaderViewModel): Boolean = model.state.value.let {
        it.error != null || it.protocolRecoveryReference != null || it.pendingImports.isNotEmpty() || it.pendingPositions.isNotEmpty() ||
            it.pendingBookmarks.isNotEmpty() || it.pendingSearchClose != null || (it.ebookFailureOwner != null && it.ebookFailureOwner == it.reading?.identity)
    }
    private fun healthy(model: ReaderViewModel, revision: Long) { assertFalse("Original operation failed/pending", failed(model)); assertEquals(revision, model.state.value.importRevision) }
    private fun freshRecords(before: Set<String>) = (EbookJourneyProbe.completedOperationNames(current) - before).map { name ->
        val file = File(current.filesDir, "Reader/Operations/Completed/$name")
        assertTrue(file.length() in 1..8L * 1024 * 1024)
        val raw = AtomicFile(file).openRead().use { it.readBytes() }; Triple(file, JSONObject(raw.toString(Charsets.UTF_8)), raw)
    }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun enabledExit(): Boolean = compose.onAllNodes(hasTestTag("reader_exit")).fetchSemanticsNodes().let {
        it.size == 1 && SemanticsProperties.Disabled !in it.single().config && it.single().boundsInRoot.width > 0 && it.single().boundsInRoot.height > 0
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
    companion object {
        private const val METHOD = "existingRealZhongSongDirectoryGlyphAfterComposeReadyExitsWithOriginalReceipt"
        private const val IMPORT_ID = "android-import-9f0f562e-ce8a-4fb6-88c0-dbac5f3fbc85"
        private const val PENDING_ID = "android-progress-73c9be19-69e5-4f55-a89a-71b11b850772"
        private const val PENDING_RUNTIME = "5dfc90d3-f4ce-4241-a47d-596c407b7763"
        private const val PENDING_ACK_REVISION = "reader-progress-v1:d33705588cdf889293d5c94a76b33a7d5d30fba91289f1d1bc01e8cf74d84cee"
        private const val SOURCE_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val SOURCE_BYTES = 7406795L
        private const val PROTECTED_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
    }
}
