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
import io.reader.android.ui.ebookdraft.ReaderEbookReady
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
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Continue the already finalized real EPUB. No import/SAF, second Library/client, parser or retry. */
@RunWith(AndroidJUnit4::class)
class UiEpubNarrativeJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(150, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun existingRealZhongSongNarrativeScrollAutoSavesAndExitsWithOriginalReceipts() {
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
            assertEquals(BASELINE_PROGRESS_ID, args.getString("expectedBaselineProgressId"))
            assertEquals(BASELINE_REVISION, args.getString("expectedBaselineRevision"))
            assertEquals(2, args.getString("chapterIndex")?.toIntOrNull() ?: throw AssertionError("chapterIndex required"))
            assertEquals("第1章 牢房", args.getString("chapterTitle"))
            assertEquals("OEBPS/Text/Cha1.xhtml", args.getString("entryPath"))
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
            assertTrue(initial.pendingPositions.isEmpty())
            healthy(model, 0)
            val protected = initial.books.single { it.id == "local:$PROTECTED_SHA" }
            assertEquals(1, protected.progressChapter); assertEquals(210, protected.progressOffset)
            val book = initial.books.single { it.id == bookId }
            assertEquals(1368, book.chapterCount); assertEquals(1, book.progressChapter); assertEquals(0, book.progressOffset)
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
            val baselineFile = File(current.filesDir, "Reader/Operations/Completed/$BASELINE_PROGRESS_ID.json")
            assertTrue(baselineFile.length() in 1..8L * 1024 * 1024)
            val baselineRaw = baselineFile.readBytes()
            val baselineRecord = JSONObject(baselineRaw.toString(Charsets.UTF_8))
            assertEquals(BASELINE_PROGRESS_ID, baselineRecord.getString("id")); assertTrue(baselineRecord.getBoolean("completed"))
            assertEquals("progress", baselineRecord.getString("kind")); assertEquals(bookId, baselineRecord.getString("bookId"))
            val baselineWire = baselineRecord.getJSONObject("wireTerminal")
            assertEquals(BASELINE_REVISION, ProgressReceiptPolicy.revision(baselineRecord, baselineWire))
            assertEquals(1, baselineRecord.getJSONObject("parameters").getInt("chapterIndex"))
            assertEquals(0, baselineRecord.getJSONObject("parameters").getInt("chapterOffset"))
            val beforeAuto = EbookJourneyProbe.completedOperationNames(current)
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

            marker("epub-existing-progress-original-bound", Bundle().apply {
                putString("bookId", bookId); putString("originalId", BASELINE_PROGRESS_ID)
                putString("originalRecordSha256", sha(baselineRaw)); putString("originalWire", baselineWire.toString())
                putLong("requestId", baselineRecord.getLong("requestId")); putString("runtimeSession", baselineRecord.getString("runtimeSession"))
                putString("ackRevision", BASELINE_REVISION); putInt("chapter", 1); putInt("scalarOffset", 0)
            })

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
            assertEquals(beforeAuto, EbookJourneyProbe.completedOperationNames(current))

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
            EbookJourneyProbe.glyphTapCoordinates(compose, web, probe) // Initial real glyph/viewport observation; not clicked.
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
            assertEquals(beforeAuto, EbookJourneyProbe.completedOperationNames(current))

            phase = "one-real-narrative-swipe-and-auto-save"
            val swipe = swipeProbe(web)
            assertFalse(swipe.has("probeError")); assertTrue(swipe.getBoolean("selectionCollapsed"))
            assertTrue(swipe.getBoolean("nonLinkStart")); assertEquals(probe.getString("entryUrl"), swipe.getString("entryUrl"))
            val points = compose.runOnUiThread {
                prepared.requireCurrent(); assertSame(mount, field(model, "ebookMount")); assertSame(web, EbookJourneyProbe.webView(current, SHELL))
                val location = IntArray(2).also(web::getLocationOnScreen); val visible = Rect()
                assertTrue(web.getGlobalVisibleRect(visible)); val density = web.resources.displayMetrics.density.toDouble()
                assertTrue(abs(swipe.getDouble("width") * density - web.width) <= 1.1 * density)
                assertTrue(abs(swipe.getDouble("height") * density - web.height) <= 1.1 * density)
                val x = location[0] + ((swipe.getDouble("frameLeft") + swipe.getDouble("x")) * density).toInt()
                val from = location[1] + ((swipe.getDouble("frameTop") + swipe.getDouble("startY")) * density).toInt()
                val to = location[1] + ((swipe.getDouble("frameTop") + swipe.getDouble("endY")) * density).toInt()
                assertTrue(visible.contains(x, from) && visible.contains(x, to) && from - to > web.height / 4)
                intArrayOf(x, from, to)
            }
            assertTrue(UiDevice.getInstance(instrumentation).swipe(points[0], points[1], points[0], points[2], 35))
            compose.waitUntil(10_000) { failed(model) || compose.runOnUiThread { (capturedVisibleAnchor(mount, web, ready) ?: 0) > 0 } }
            healthy(model, 0)
            compose.waitUntil(15_000) {
                if (failed(model)) true else {
                    val names = EbookJourneyProbe.completedOperationNames(current) - beforeAuto
                    assertTrue("One swipe must not create multiple original autosaves", names.size <= 1)
                    names.size == 1 && compose.runOnUiThread { (field(model, "delayedSave") as? Job)?.isCompleted == true } &&
                        !File(current.filesDir, "Reader/Operations/${names.single()}").exists() &&
                        !File(current.filesDir, "Reader/Operations/Completed/${names.single()}.new").exists()
                }
            }
            healthy(model, 0)
            val auto = freshRecords(beforeAuto).single()
            val autoRecord = auto.second; val autoParams = autoRecord.getJSONObject("parameters"); val autoWire = autoRecord.getJSONObject("wireTerminal")
            assertTrue(autoRecord.getBoolean("completed")); assertEquals("progress", autoRecord.getString("kind"))
            assertEquals(autoRecord.getString("id") + ".json", auto.first.name)
            assertEquals(bookId, autoRecord.getString("bookId")); assertEquals(2, autoParams.getInt("chapterIndex"))
            val autoRevision = ProgressReceiptPolicy.revision(autoRecord, autoWire)
            val autoActiveAbsent = !File(current.filesDir, "Reader/Operations/${autoRecord.getString("id")}.json").exists()
            val autoCandidateAbsent = !File(auto.first.parentFile, auto.first.name + ".new").exists()
            assertTrue(autoActiveAbsent && autoCandidateAbsent)
            assertEquals(BASELINE_REVISION, autoParams.getString("expectedProgressRevision"))
            val scrolled = compose.runOnUiThread {
                assertTrue((field(model, "delayedSave") as? Job)?.isCompleted == true)
                val offset = checkNotNull(capturedVisibleAnchor(mount, web, ready)); assertTrue(offset > 0)
                assertEquals(autoParams.getInt("chapterOffset"), offset)
                checkNotNull(model.state.value.reading)
            }
            assertEquals(lease.runtimeSession, autoRecord.getString("runtimeSession"))
            assertEquals(reading.chapter.bodyVersion, autoParams.getString("expectedBodyVersion"))
            assertEquals(reading.chapter.processingVersion, autoParams.getString("expectedProcessingVersion"))
            marker("epub-narrative-scroll-visible-anchor", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", 2); putInt("scalarOffset", scrolled.visibleScalarOffset); putInt("ownerLastVisible", scrolled.visibleScalarOffset)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("ownerId", lease.ownerId.toString()); putString("clientId", lease.clientId.toString()); putLong("ownerGeneration", lease.ownerGeneration)
                putString("runtimeSession", lease.runtimeSession); putString("physicalViewId", mount.view.physicalViewId.toString())
                putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putBoolean("swipeDispatched", true); putBoolean("nonLinkStart", true); putBoolean("autoObserverCompleted", true)
                putInt("startX", points[0]); putInt("startY", points[1]); putInt("endX", points[0]); putInt("endY", points[2])
            })
            marker("epub-auto-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", autoRecord.getString("id")); putLong("requestId", autoRecord.getLong("requestId"))
                putString("runtimeSession", autoRecord.getString("runtimeSession")); putInt("chapter", 2); putInt("scalarOffset", scrolled.visibleScalarOffset)
                putString("expectedRevision", autoParams.getString("expectedProgressRevision")); putString("ackRevision", autoRevision)
                putString("bodyVersion", autoParams.getString("expectedBodyVersion")); putString("processingVersion", autoParams.getString("expectedProcessingVersion"))
                putString("originalRecordSha256", sha(auto.third)); putString("originalWire", autoWire.toString())
                putBoolean("autoObserverCompleted", true); putBoolean("autoActiveAbsent", autoActiveAbsent); putBoolean("autoCandidateAbsent", autoCandidateAbsent)
            })
            val beforeExit = EbookJourneyProbe.completedOperationNames(current) // Auto original is completely archived before the only exit can cancel its observer.
            compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView); EbookJourneyProbe.awaitDraw(compose, web)
            compose.runOnUiThread { assertEquals(scrolled.visibleScalarOffset, checkNotNull(capturedVisibleAnchor(mount, web, ready))) }
            val scrolledProbe = EbookJourneyProbe.dom(compose, web, EbookJourneyProbe.DOM_READ_ONLY)
            assertFalse(scrolledProbe.has("probeError")); assertEquals(probe.getString("entryUrl"), scrolledProbe.getString("entryUrl"))
            assertTrue(scrolledProbe.getBoolean("selectionCollapsed"))
            val metrics = EbookJourneyProbe.glyphTapCoordinates(compose, web, scrolledProbe)
            val screen = File(current.cacheDir, "reader-a3-epub-story-${lease.renderId}-draw.png")
            try { marker("epub-screenshot", Bundle().apply { putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name) }) }
            catch (failure: Exception) { marker("epub-screenshot", Bundle().apply { putBoolean("saved", false); putString("error", failure.javaClass.name) }) }
            assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))

            phase = "trusted-body-tap-and-one-normal-exit"
            assertTrue("Actual DOM-derived non-link visible content tap not dispatched", UiDevice.getInstance(instrumentation).click(metrics[0], metrics[1]))
            compose.waitUntil(5_000) { model.state.value.reading?.identity == reading.identity && model.state.value.controlsVisible && enabledExit() }
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView)
            assertTrue(enabledExit()); healthy(model, 0)
            assertEquals(scrolled.visibleScalarOffset, model.state.value.reading!!.visibleScalarOffset); assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))
            marker("epub-trusted-body-tap-controls-draw", Bundle().apply { putLong("readingOwner", reading.identity) })
            compose.onNodeWithTag("reader_exit").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.reading == null || failed(model)) }
            healthy(model, 0); assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            val saved = freshRecords(beforeExit).single()
            val progress = saved.second; val parameters = progress.getJSONObject("parameters"); val wire = progress.getJSONObject("wireTerminal")
            assertEquals("progress", progress.getString("kind")); assertEquals(bookId, progress.getString("bookId"))
            assertEquals(reading.chapter.index, parameters.getInt("chapterIndex")); assertEquals(scrolled.visibleScalarOffset, parameters.getInt("chapterOffset"))
            val revision = ProgressReceiptPolicy.revision(progress, wire)
            assertTrue(progress.getBoolean("completed")); assertNotEquals(autoRecord.getString("id"), progress.getString("id"))
            assertEquals(autoRevision, parameters.getString("expectedProgressRevision"))
            assertEquals(lease.runtimeSession, progress.getString("runtimeSession"))
            assertEquals(reading.chapter.bodyVersion, parameters.getString("expectedBodyVersion"))
            assertEquals(reading.chapter.processingVersion, parameters.getString("expectedProcessingVersion"))
            assertFalse(File(current.filesDir, "Reader/Operations/${progress.getString("id")}.json").exists())
            assertFalse(File(saved.first.parentFile, saved.first.name + ".new").exists())
            marker("epub-exit-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", progress.getString("id")); putLong("requestId", progress.getLong("requestId"))
                putString("runtimeSession", progress.getString("runtimeSession")); putInt("chapter", reading.chapter.index); putInt("scalarOffset", scrolled.visibleScalarOffset)
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

    private fun capturedVisibleAnchor(mount: ReaderEbookUiMount, web: WebView, initialReady: ReaderEbookReady): Int? {
        val model = current.readerViewModel; val state = model.state.value; val reading = state.reading ?: return null
        if (field(model, "ebookMount") !== mount || state.route != ReaderRoute.Reading || state.busy || state.controlsVisible ||
            reading.identity != mount.readingOwner || reading.chapter.sessionToken != mount.chapter.sessionToken ||
            reading.chapter.bookId != mount.chapter.bookId || reading.chapter.index != mount.chapter.index ||
            reading.chapter.bodyVersion != mount.chapter.bodyVersion || reading.chapter.processingVersion != mount.chapter.processingVersion) return null
        val prepared = mount.preparation ?: return null
        if (prepared.view !== mount.view || prepared.publication.lease != initialReady.lease || initialReady.lease.renderId != mount.view.physicalViewId) return null
        prepared.requireCurrent()
        if (EbookJourneyProbe.webView(current, SHELL) !== web) return null
        val owner = field(web.parent, "owner") ?: return null; val layout = field(owner, "layout") ?: return null
        if (field(owner, "view") !== web || field(owner, "lease") != initialReady.lease || field(owner, "phase").toString() != "Ready" ||
            field(owner, "forceLayout") != false || field(owner, "awaitingDraw") != null || field(owner, "drawnEpoch") != initialReady.layoutEpoch ||
            field(layout, "epoch") != initialReady.layoutEpoch || field(layout, "token") != initialReady.layoutToken ||
            web.width != initialReady.widthPx || web.height != initialReady.heightPx || !web.isAttachedToWindow || !web.isShown || web.isLayoutRequested) return null
        val anchors = field(owner, "anchors") ?: return null; val observed = field(anchors, "lastVisible") as? Int ?: return null
        return observed.takeIf { it == reading.visibleScalarOffset && it in 0..reading.chapter.scalarCount }
    }
    private fun swipeProbe(web: WebView): JSONObject {
        val latch = CountDownLatch(1); var result: String? = null
        compose.runOnUiThread { web.evaluateJavascript(EbookJourneyProbe.SWIPE_READ_ONLY) { result = it; latch.countDown() } }
        assertTrue("Read-only swipe hit callback missing", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(result ?: throw AssertionError("Swipe callback returned null"))
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
        private const val METHOD = "existingRealZhongSongNarrativeScrollAutoSavesAndExitsWithOriginalReceipts"
        private const val IMPORT_ID = "android-import-9f0f562e-ce8a-4fb6-88c0-dbac5f3fbc85"
        private const val BASELINE_PROGRESS_ID = "android-progress-fb54648e-8ca5-4584-99ed-aef2824d3405"
        private const val BASELINE_REVISION = "reader-progress-v1:bab1309b41fcef1f2fa1a161e9726409149d62d4616fd33fbb94e08e456cadce"
        private const val SOURCE_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val SOURCE_BYTES = 7406795L
        private const val PROTECTED_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
    }
}
