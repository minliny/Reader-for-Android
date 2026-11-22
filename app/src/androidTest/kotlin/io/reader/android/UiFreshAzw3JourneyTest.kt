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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiWatcher
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

/** Fresh Alice AZW3 SAF import, one real narrative swipe and original ACK exit.
 * No historical protected-book/pending baseline and no cold-process recovery claim. */
@RunWith(AndroidJUnit4::class)
class UiFreshAzw3JourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun freshAliceAzw3ImportsScrollsAndExitsWithOriginalReceipts() {
        marker("method-enter") // Before launch/current Activity access.
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            val displayName = args.getString("displayName") ?: throw AssertionError("displayName required")
            assertEquals("alice-wonderland.azw3", displayName); assertEquals(SOURCE_SHA, args.getString("expectedSHA"))
            assertEquals(SOURCE_BYTES, args.getString("expectedBytes")?.toLongOrNull() ?: throw AssertionError("expectedBytes required"))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            healthy(model, 0)
            val initial = model.state.value
            assertTrue(initial.pendingReady && initial.reading == null && initial.route == ReaderRoute.Bookshelf)
            val otherBooks = initial.books.associateBy { it.key }
            val otherRemoteBooks = initial.remoteBooks
            assertTrue(initial.books.none { it.id == bookId }); assertFalse(File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source").exists())
            val retainedFiles = retainedNonTargetFiles()
            val beforeImport = EbookJourneyProbe.completedOperationNames(current)
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue("Physical screen must already be on", UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse("Physical screen must already be unlocked", checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            assertTrue(compose.runOnUiThread { current.window.decorView.hasWindowFocus() })
            marker("fresh-azw3-physical-interaction-preflight", Bundle().apply {
                putBoolean("screenOn", true); putBoolean("keyguardLocked", false); putBoolean("windowFocused", true)
            })
            marker("fresh-azw3-input-bound", Bundle().apply {
                putString("displayName", "alice-wonderland.azw3"); putString("expectedSHA", SOURCE_SHA); putLong("expectedBytes", SOURCE_BYTES)
                putInt("retainedLocalBookCount", otherBooks.size); putInt("retainedRemoteBookCount", otherRemoteBooks.size)
                putInt("retainedOperationAndOriginalFiles", retainedFiles.size)
            })

            phase = "one-saf-azw3-import"
            compose.onNodeWithTag("reader_import").assertIsEnabled().performClick()
            selectDocument(displayName)
            compose.waitUntil(120_000) { val state = model.state.value; !state.busy && (state.importRevision > 0 || failed(model)) }
            healthy(model, 1); assertEquals(bookId, model.state.value.lastImportedBookId)
            assertEquals(otherBooks, model.state.value.books.filter { it.id != bookId }.associateBy { it.key })
            assertEquals(otherRemoteBooks, model.state.value.remoteBooks)
            assertRetainedFiles(retainedFiles)
            val book = model.state.value.books.single { it.id == bookId }; assertEquals(16, book.chapterCount)
            val imported = freshRecords(beforeImport).single(); val importFile = imported.first; val importRaw = imported.third; val record = imported.second
            assertEquals("import", record.getString("kind")); assertTrue(record.getBoolean("completed"))
            assertEquals("finalize", record.getString("terminalStage")); assertEquals("settled", record.getJSONObject("settlement").getString("state"))
            val binding = record.getJSONObject("binding")
            assertEquals(bookId, binding.getString("bookId")); assertEquals(SOURCE_SHA, binding.getString("sourceSha256"))
            assertEquals(SOURCE_BYTES, binding.getLong("sourceBytes")); assertEquals("azw", binding.getString("format"))
            val original = File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source")
            assertEquals(SOURCE_BYTES, original.length()); assertEquals(SOURCE_SHA, shaFile(original))
            val summary = record.getJSONObject("receipts").getJSONObject("persist").getJSONObject("data").getJSONObject("data")
            assertEquals(16, summary.getInt("chapterCount"))
            val importedToc = summary.getJSONArray("toc"); assertEquals(16, importedToc.length())
            val sourceEntry = (0 until importedToc.length()).map { importedToc.getJSONObject(it) }.single { it.getInt("index") == 4 }
            assertEquals("CHAPTER I. Down the Rabbit-Hole", sourceEntry.getString("title"))
            val locatorRaw = sourceEntry.getJSONObject("variables").getString("readerEbookPresentationV1")
            val locator = JSONObject(locatorRaw); assertEquals(1, locator.getInt("version"))
            val selectedEntry = locator.getString("entryPath"); assertEquals("OEBPS/part00004.html", selectedEntry)
            val sourceStart = locator.getInt("chapterStartScalar"); val sourceEnd = locator.getInt("chapterEndScalar")
            val sourceCanonicalSha = locator.getString("chapterContentSha256")
            // Fixed Alice original: observed by the earlier real Core/Reader journey, not an invented anchor.
            assertEquals(0, sourceStart); assertEquals(11274, sourceEnd)
            assertEquals("a3e8955d4a568c60f85a781870ac9bc7aa250b9a5d0431655588a0e66dd19bcb", sourceCanonicalSha)
            marker("fresh-azw3-import-finalized-settled-original", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", book.chapterCount)
                putString("originalId", record.getString("id")); putString("originalRecordSha256", sha(imported.third))
                putString("format", binding.getString("format")); putString("authorityEpoch", binding.getString("authorityEpoch"))
                putLong("sequence", binding.getLong("sequence")); putLong("sourceBytes", original.length()); putString("sourceSHA", SOURCE_SHA)
                putLong("importRevision", model.state.value.importRevision)
            })
            val beforeAuto = EbookJourneyProbe.completedOperationNames(current)

            phase = "one-real-core-azw3-directory"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_directory_$bookId"))
            compose.onNodeWithTag("reader_book_directory_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Directory || failed(model)) }
            healthy(model, 1); assertNull(model.state.value.reading)
            val toc = model.state.value.chapters; assertEquals(16, toc.size)
            val selected = toc.single { it.index == 4 }; assertEquals(sourceEntry.getString("title"), selected.title)
            marker("fresh-azw3-core-directory-bound", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", toc.size); putInt("selectedChapter", selected.index)
                putString("selectedTitle", selected.title); putString("expectedEntryPath", selectedEntry)
                putInt("chapterStartScalar", sourceStart); putInt("chapterEndScalar", sourceEnd); putString("canonicalSha256", sourceCanonicalSha)
                putString("originalLocatorJson", locatorRaw)
            })
            compose.onNodeWithTag("reader_directory").performScrollToNode(hasTestTag("reader_chapter_${selected.index}"))
            compose.onNodeWithTag("reader_chapter_${selected.index}").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model, 1)
            val reading = model.state.value.reading ?: throw AssertionError("Actual AZW3 Core chapter absent")
            assertEquals(ReaderLocalBookFormat.AZW, reading.format)
            assertEquals(bookId, reading.chapter.bookId); assertEquals(selected.index, reading.chapter.index)
            assertTrue(reading.chapter.text.any { !it.isWhitespace() })
            assertEquals(0, reading.visibleScalarOffset); assertFalse(model.state.value.controlsVisible)
            assertEquals(beforeAuto, EbookJourneyProbe.completedOperationNames(current))

            phase = "actual-webview-ready-draw-dom"
            compose.waitUntil(30_000) { failed(model) || compose.runOnUiThread { EbookJourneyProbe.readyMount(current, SHELL) != null } }
            healthy(model, 1)
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
            EbookJourneyProbe.glyphTapCoordinates(compose, web, probe) // Initial real glyph/viewport observation; not clicked.
            assertTrue(Build.VERSION.SDK_INT >= 26)
            val provider = WebView.getCurrentWebViewPackage() ?: throw AssertionError("Actual WebView provider absent")
            marker("fresh-azw3-real-webview-ready-draw-observed-dom", Bundle().apply {
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
                putString("processedEditsReplaySha256", sha(replay.toByteArray(Charsets.UTF_8)))
                putBoolean("originalEqualsProcessed", originalWindow == reading.chapter.text)
                putInt("chapterStartScalar", presentation.chapterStartScalar); putInt("chapterEndScalar", presentation.chapterEndScalar)
                putInt("documentScalarCount", canonicalScalars); putString("documentCanonicalSha256", sha(canonical.toByteArray(Charsets.UTF_8)))
                putString("providerPackage", provider.packageName); putString("providerVersion", provider.versionName)
                putString("canonicalSha256", sha(reading.chapter.text.toByteArray(Charsets.UTF_8)))
                putString("observedDomTextSampleSha256", sha(probe.getString("domText").toByteArray(Charsets.UTF_8)))
                putString("probeMetrics", JSONObject(probe.toString()).apply { remove("domText") }.toString())
                putString("textClaim", "DOM_SAMPLE_ONLY_ORIGINAL_CANONICAL_AND_CORE_PROCESSED_EDITS_AUTHENTICATED_BY_PRODUCTION_ADMIT_NATIVE_VISUAL_DRAW_READY")
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
            healthy(model, 1)
            compose.waitUntil(15_000) {
                if (failed(model)) true else {
                    val names = EbookJourneyProbe.completedOperationNames(current) - beforeAuto
                    assertTrue("One swipe must not create multiple original autosaves", names.size <= 1)
                    names.size == 1 && compose.runOnUiThread { (field(model, "delayedSave") as? Job)?.isCompleted == true } &&
                        !File(current.filesDir, "Reader/Operations/${names.single()}").exists() &&
                        !File(current.filesDir, "Reader/Operations/Completed/${names.single()}.new").exists()
                }
            }
            healthy(model, 1)
            val auto = freshRecords(beforeAuto).single()
            val autoRecord = auto.second; val autoParams = autoRecord.getJSONObject("parameters"); val autoWire = autoRecord.getJSONObject("wireTerminal")
            assertTrue(autoRecord.getBoolean("completed")); assertEquals("progress", autoRecord.getString("kind"))
            assertEquals(autoRecord.getString("id") + ".json", auto.first.name)
            assertEquals(bookId, autoRecord.getString("bookId")); assertEquals(4, autoParams.getInt("chapterIndex"))
            val autoRevision = ProgressReceiptPolicy.revision(autoRecord, autoWire)
            val autoActiveAbsent = !File(current.filesDir, "Reader/Operations/${autoRecord.getString("id")}.json").exists()
            val autoCandidateAbsent = !File(auto.first.parentFile, auto.first.name + ".new").exists()
            assertTrue(autoActiveAbsent && autoCandidateAbsent)
            assertTrue(autoParams.getString("expectedProgressRevision").matches(Regex("reader-progress-v1:[a-f0-9]{64}")))
            val scrolled = compose.runOnUiThread {
                assertTrue((field(model, "delayedSave") as? Job)?.isCompleted == true)
                val offset = checkNotNull(capturedVisibleAnchor(mount, web, ready)); assertTrue(offset > 0)
                assertEquals(autoParams.getInt("chapterOffset"), offset)
                checkNotNull(model.state.value.reading)
            }
            assertEquals(lease.runtimeSession, autoRecord.getString("runtimeSession"))
            assertEquals(reading.chapter.bodyVersion, autoParams.getString("expectedBodyVersion"))
            assertEquals(reading.chapter.processingVersion, autoParams.getString("expectedProcessingVersion"))
            marker("fresh-azw3-narrative-scroll-visible-anchor", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", 4); putInt("scalarOffset", scrolled.visibleScalarOffset); putInt("ownerLastVisible", scrolled.visibleScalarOffset)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("ownerId", lease.ownerId.toString()); putString("clientId", lease.clientId.toString()); putLong("ownerGeneration", lease.ownerGeneration)
                putString("runtimeSession", lease.runtimeSession); putString("physicalViewId", mount.view.physicalViewId.toString())
                putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putBoolean("swipeDispatched", true); putBoolean("nonLinkStart", true); putBoolean("autoObserverCompleted", true)
                putInt("startX", points[0]); putInt("startY", points[1]); putInt("endX", points[0]); putInt("endY", points[2])
            })
            marker("fresh-azw3-auto-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", autoRecord.getString("id")); putLong("requestId", autoRecord.getLong("requestId"))
                putString("runtimeSession", autoRecord.getString("runtimeSession")); putInt("chapter", 4); putInt("scalarOffset", scrolled.visibleScalarOffset)
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
            val screen = File(current.cacheDir, "reader-a3-azw3-story-${lease.renderId}-draw.png")
            try { marker("fresh-azw3-screenshot", Bundle().apply { putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name) }) }
            catch (failure: Exception) { marker("fresh-azw3-screenshot", Bundle().apply { putBoolean("saved", false); putString("error", failure.javaClass.name) }) }
            assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))

            phase = "trusted-body-tap-and-one-normal-exit"
            assertTrue("Actual DOM-derived non-link visible content tap not dispatched", UiDevice.getInstance(instrumentation).click(metrics[0], metrics[1]))
            compose.waitUntil(5_000) { model.state.value.reading?.identity == reading.identity && model.state.value.controlsVisible && enabledExit() }
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView)
            assertTrue(enabledExit()); healthy(model, 1)
            assertEquals(scrolled.visibleScalarOffset, model.state.value.reading!!.visibleScalarOffset); assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))
            marker("fresh-azw3-trusted-body-tap-controls-draw", Bundle().apply { putLong("readingOwner", reading.identity) })
            compose.onNodeWithTag("reader_exit").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.reading == null || failed(model)) }
            healthy(model, 1); assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
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
            marker("fresh-azw3-exit-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", progress.getString("id")); putLong("requestId", progress.getLong("requestId"))
                putString("runtimeSession", progress.getString("runtimeSession")); putInt("chapter", reading.chapter.index); putInt("scalarOffset", scrolled.visibleScalarOffset)
                putString("expectedRevision", parameters.getString("expectedProgressRevision")); putString("ackRevision", revision)
                putString("bodyVersion", parameters.getString("expectedBodyVersion")); putString("processingVersion", parameters.getString("expectedProcessingVersion"))
                putString("originalRecordSha256", sha(saved.third)); putString("originalWire", wire.toString())
            })
            assertEquals(otherBooks, model.state.value.books.filter { it.id != bookId }.associateBy { it.key })
            assertEquals(otherRemoteBooks, model.state.value.remoteBooks)
            assertRetainedFiles(retainedFiles)
            assertEquals(sha(importRaw), shaFile(importFile))
            assertEquals(1L, model.state.value.importRevision)
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
    private fun healthy(model: ReaderViewModel, revision: Long) {
        val state = model.state.value
        assertFalse("Original operation failed/pending: error=${state.error}; protocol=${state.protocolRecoveryReference}; " +
            "imports=${state.pendingImports.map { it.id }}; positions=${state.pendingPositions.map { it.id }}; " +
            "bookmarks=${state.pendingBookmarks.map { it.id }}; searchClose=${state.pendingSearchClose}; " +
            "ebookFailureOwner=${state.ebookFailureOwner}; readingOwner=${state.reading?.identity}", failed(model))
        assertEquals(revision, state.importRevision)
    }
    private fun freshRecords(before: Set<String>) = (EbookJourneyProbe.completedOperationNames(current) - before).map { name ->
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
        private const val METHOD = "freshAliceAzw3ImportsScrollsAndExitsWithOriginalReceipts"
        private const val SOURCE_SHA = "604874a7afdb98570f4e41a2dc9af8aace13604af77bf14df903e855564f419b"
        private const val SOURCE_BYTES = 1031552L
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
    }

    private fun selectDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val watcherName = "reader-fresh-azw3-picker-open-consent"
        val consentPackage = "com.oplus.securitypermission"
        val consentTitle = "“Reader”想要打开“io.reader.minliny.test”"
        var consentClickAttempted = false
        device.registerWatcher(watcherName, UiWatcher {
            val title = By.pkg(consentPackage).res("$consentPackage:id/alertTitle").text(consentTitle)
            val checkbox = By.pkg(consentPackage).res("$consentPackage:id/coui_security_alert_dialog_checkbox")
                .text("始终允许打开").checkable(true).checked(false).enabled(true)
            val open = By.pkg(consentPackage).res("android:id/button1").text("打开").enabled(true)
            if (consentClickAttempted || device.findObjects(title).size != 1 ||
                device.findObjects(checkbox).size != 1 || device.findObjects(open).size != 1) false else {
                val selector = UiSelector().packageName(consentPackage).resourceId("android:id/button1")
                    .text("打开").enabled(true)
                if (!device.findObject(selector).exists()) false else {
                    consentClickAttempted = true
                    marker("oem-open-observed", Bundle().apply { putString("dialogTitle", consentTitle) })
                    val clicked = device.findObject(selector).click()
                    val gone = device.findObject(UiSelector().packageName(consentPackage)
                        .resourceId("$consentPackage:id/alertTitle").text(consentTitle)).waitUntilGone(2_000)
                    marker("oem-open-ACK", Bundle().apply { putBoolean("clickDispatched", clicked); putBoolean("dialogGone", gone) })
                    clicked
                }
            }
        })
        try {
            val provider = "com\\.(google\\.)?android.documentsui"
            val root = UiSelector().packageNameMatches(provider)
            assertTrue(device.findObject(root).waitForExists(10_000)); marker("picker-visible")
            val file = root.text(name)
            val folder = root.text("Reader")
            val readerBreadcrumb = root.resourceIdMatches(".*:id/breadcrumb_text").text("Reader")
            val downloadsBreadcrumb = root.resourceIdMatches(".*:id/breadcrumb_text")
                .textMatches("Downloads|下载|下载内容")
            val drawer = root.resourceIdMatches(".*:id/roots_list")
            val downloads = drawer.childSelector(UiSelector().textMatches("Downloads|下载|下载内容"))
            if (!device.findObject(file).waitForExists(2_000)) {
                val alreadyInReader = device.findObject(readerBreadcrumb).waitForExists(2_000) &&
                    device.findObject(downloadsBreadcrumb).waitForExists(2_000)
                if (!alreadyInReader) {
                    if (device.findObject(drawer).exists()) {
                        clickPickerOnce(device, downloads, "picker-downloads", folder, drawer)
                    } else if (!device.findObject(folder).exists()) {
                        val roots = root.descriptionMatches("Show roots|显示根目录|显示位置|导航抽屉")
                        clickPickerOnce(device, roots, "picker-roots", drawer)
                        clickPickerOnce(device, downloads, "picker-downloads", folder, drawer)
                    }
                    if (!device.findObject(readerBreadcrumb).exists()) {
                        clickPickerOnce(device, folder, "picker-folder", readerBreadcrumb)
                    }
                    assertTrue(device.findObject(readerBreadcrumb).waitForExists(5_000))
                    assertTrue(device.findObject(downloadsBreadcrumb).waitForExists(5_000))
                }
                if (!device.findObject(file).waitForExists(2_000)) exposeDocument(device, provider, file)
            }
            assertTrue(device.findObject(file).waitForExists(5_000)); marker("picker-file-click")
            val clickReturned = device.findObject(file).click()
            val pickerGone = device.findObject(root).waitUntilGone(10_000)
            assertTrue(pickerGone)
            assertTrue(device.findObject(UiSelector().packageName("io.reader.minliny")).waitForExists(10_000))
            marker("picker-returned", Bundle().apply {
                putBoolean("clickReturned", clickReturned); putBoolean("pickerGone", pickerGone)
            })
        } finally {
            device.removeWatcher(watcherName)
        }
    }

    private fun clickPickerOnce(device: UiDevice, selector: UiSelector, stage: String,
                                next: UiSelector, gone: UiSelector? = null) {
        assertTrue("$stage target absent", device.findObject(selector).waitForExists(5_000))
        marker("$stage-click")
        assertTrue("$stage click was not dispatched", device.findObject(selector).click())
        if (gone != null) assertTrue("$stage did not retire its drawer", device.findObject(gone).waitUntilGone(5_000))
        assertTrue("$stage did not expose the next state", device.findObject(next).waitForExists(5_000))
        marker("$stage-ready")
    }

    private fun exposeDocument(device: UiDevice, provider: String, file: UiSelector) {
        val container = UiSelector().packageNameMatches(provider).className("android.widget.ScrollView").scrollable(true)
        for (attempt in 1..2) {
            val beforeVisible = device.findObject(file).waitForExists(750)
            if (beforeVisible) return
            assertTrue(device.findObject(container.instance(0)).exists())
            assertTrue(!device.findObject(container.instance(1)).exists())
            val returned = UiScrollable(container).scrollForward(35)
            val afterVisible = device.findObject(file).waitForExists(1_000)
            marker("picker-document-scroll-$attempt", Bundle().apply {
                putInt("attempt", attempt); putBoolean("beforeVisible", beforeVisible)
                putBoolean("scrollReturned", returned); putBoolean("afterVisible", afterVisible)
            })
            if (afterVisible) return
        }
        throw AssertionError("Exact fresh AZW3 document title absent after two bounded picker scrolls")
    }

}
