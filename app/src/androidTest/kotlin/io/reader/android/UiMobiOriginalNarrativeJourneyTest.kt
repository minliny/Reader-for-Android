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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiWatcher
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

/** One real original MOBI import and narrative read. No second Library/client, parser or retry. */
@RunWith(AndroidJUnit4::class)
class UiMobiOriginalNarrativeJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun oneRealFuHanMobiImportsScrollsAutoSavesAndExitsWithOriginalReceipts() {
        marker("method-enter") // Before launch/current Activity access.
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            assertTrue("Root real MOBI baseline is not finalized", BASELINE_PROOF_SHA.matches(Regex("[a-f0-9]{64}")))
            assertEquals(BASELINE_PROOF_SHA, args.getString("baselineProofSha256"))
            assertEquals("覆汉.mobi", args.getString("displayName")); assertEquals(SOURCE_SHA, args.getString("expectedSHA"))
            assertEquals(SOURCE_BYTES, args.getString("expectedBytes")?.toLongOrNull() ?: throw AssertionError("expectedBytes required"))
            assertEquals(PROTECTED_SHA, args.getString("protectedBookSHA")); assertEquals(PROTECTED_EPUB_SHA, args.getString("protectedEpubSHA"))
            assertEquals(560, args.getString("expectedChapterCount")?.toIntOrNull() ?: throw AssertionError("expectedChapterCount required"))
            assertEquals(3, args.getString("chapterIndex")?.toIntOrNull() ?: throw AssertionError("chapterIndex required"))
            assertEquals("第一章 卢龙塞", args.getString("chapterTitle"))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            healthy(model, 0)
            val initial = model.state.value
            assertTrue(initial.pendingReady && initial.reading == null && initial.route == ReaderRoute.Bookshelf)
            val protected = initial.books.single { it.id == "local:$PROTECTED_SHA" }
            val protectedEpub = initial.books.single { it.id == "local:$PROTECTED_EPUB_SHA" }
            assertEquals(1, protected.progressChapter); assertEquals(210, protected.progressOffset)
            assertEquals(2, protectedEpub.progressChapter); assertEquals(69, protectedEpub.progressOffset)
            assertTrue(initial.books.none { it.id == bookId }); assertFalse(File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source").exists())
            val beforeImport = EbookJourneyProbe.completedOperationNames(current)
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue("Physical screen must already be on", UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse("Physical screen must already be unlocked", checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            assertTrue(compose.runOnUiThread { current.window.decorView.hasWindowFocus() })
            marker("mobi-physical-interaction-preflight", Bundle().apply {
                putBoolean("screenOn", true); putBoolean("keyguardLocked", false); putBoolean("windowFocused", true)
            })
            marker("mobi-input-bound", Bundle().apply {
                putString("displayName", "覆汉.mobi"); putString("expectedSHA", SOURCE_SHA); putLong("expectedBytes", SOURCE_BYTES)
                putString("protectedBookSHA", PROTECTED_SHA); putString("protectedEpubSHA", PROTECTED_EPUB_SHA)
                putString("baselineProofSha256", BASELINE_PROOF_SHA)
            })

            phase = "one-saf-mobi-import"
            compose.onNodeWithTag("reader_import").assertIsEnabled().performClick()
            selectRealDocument("覆汉.mobi")
            compose.waitUntil(120_000) { val state = model.state.value; !state.busy && (state.importRevision > 0 || failed(model)) }
            healthy(model, 1); assertEquals(bookId, model.state.value.lastImportedBookId)
            assertEquals(protected, model.state.value.books.single { it.id == protected.id })
            assertEquals(protectedEpub, model.state.value.books.single { it.id == protectedEpub.id })
            val book = model.state.value.books.single { it.id == bookId }; assertEquals(560, book.chapterCount)
            val imported = freshRecords(beforeImport).single(); val importFile = imported.first; val importRaw = imported.third; val record = imported.second
            assertEquals("import", record.getString("kind")); assertTrue(record.getBoolean("completed"))
            assertEquals("finalize", record.getString("terminalStage")); assertEquals("settled", record.getJSONObject("settlement").getString("state"))
            val binding = record.getJSONObject("binding")
            assertEquals(bookId, binding.getString("bookId")); assertEquals(SOURCE_SHA, binding.getString("sourceSha256"))
            assertEquals(SOURCE_BYTES, binding.getLong("sourceBytes")); assertEquals("mobi", binding.getString("format"))
            val original = File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source")
            assertEquals(SOURCE_BYTES, original.length()); assertEquals(SOURCE_SHA, shaFile(original))
            val summary = record.getJSONObject("receipts").getJSONObject("persist").getJSONObject("data").getJSONObject("data")
            assertEquals(560, summary.getInt("chapterCount"))
            val importedToc = summary.getJSONArray("toc"); assertEquals(560, importedToc.length())
            val sourceEntry = (0 until importedToc.length()).map { importedToc.getJSONObject(it) }.single { it.getInt("index") == 3 }
            assertEquals("第一章 卢龙塞", sourceEntry.getString("title"))
            val locatorRaw = sourceEntry.getJSONObject("variables").getString("readerEbookPresentationV1")
            val locator = JSONObject(locatorRaw); assertEquals(1, locator.getInt("version"))
            val selectedEntry = locator.getString("entryPath"); assertTrue(selectedEntry.isNotBlank())
            val sourceStart = locator.getInt("chapterStartScalar"); val sourceEnd = locator.getInt("chapterEndScalar")
            val sourceCanonicalSha = locator.getString("chapterContentSha256")
            assertTrue(sourceStart >= 0 && sourceEnd > sourceStart && sourceCanonicalSha.matches(Regex("[a-f0-9]{64}")))
            marker("mobi-import-finalized-settled-original", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", book.chapterCount)
                putString("originalId", record.getString("id")); putString("originalRecordSha256", sha(imported.third))
                putString("format", binding.getString("format")); putString("authorityEpoch", binding.getString("authorityEpoch"))
                putLong("sequence", binding.getLong("sequence")); putLong("sourceBytes", original.length()); putString("sourceSHA", SOURCE_SHA)
                putLong("importRevision", model.state.value.importRevision)
            })
            val beforeAuto = EbookJourneyProbe.completedOperationNames(current)

            phase = "one-real-core-mobi-directory"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_directory_$bookId"))
            compose.onNodeWithTag("reader_book_directory_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Directory || failed(model)) }
            healthy(model, 1); assertNull(model.state.value.reading)
            val toc = model.state.value.chapters; assertEquals(560, toc.size)
            val selected = toc.single { it.index == 3 }; assertEquals(sourceEntry.getString("title"), selected.title)
            marker("mobi-core-directory-bound", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", toc.size); putInt("selectedChapter", selected.index)
                putString("selectedTitle", selected.title); putString("expectedEntryPath", selectedEntry)
                putInt("chapterStartScalar", sourceStart); putInt("chapterEndScalar", sourceEnd); putString("canonicalSha256", sourceCanonicalSha)
                putString("originalLocatorJson", locatorRaw)
            })
            compose.onNodeWithTag("reader_directory").performScrollToNode(hasTestTag("reader_chapter_${selected.index}"))
            compose.onNodeWithTag("reader_chapter_${selected.index}").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model, 1)
            val reading = model.state.value.reading ?: throw AssertionError("Actual MOBI Core chapter absent")
            assertEquals(ReaderLocalBookFormat.MOBI, reading.format)
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
            assertEquals(sourceCanonicalSha, sha(reading.chapter.text.toByteArray(Charsets.UTF_8)))
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
            marker("mobi-real-webview-ready-draw-observed-dom", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", reading.chapter.index); putInt("scalarOffset", ready.scalar); putInt("scalarCount", reading.chapter.scalarCount)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("ownerId", lease.ownerId.toString()); putString("clientId", lease.clientId.toString()); putLong("ownerGeneration", lease.ownerGeneration)
                putString("runtimeSession", lease.runtimeSession); putString("physicalViewId", mount.view.physicalViewId.toString())
                putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putString("entryPath", presentation.entryPath); putString("origin", probe.getString("origin")); putString("format", "mobi")
                putString("originalLocatorJson", locatorRaw)
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
            assertEquals(bookId, autoRecord.getString("bookId")); assertEquals(3, autoParams.getInt("chapterIndex"))
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
            marker("mobi-narrative-scroll-visible-anchor", Bundle().apply {
                putString("bookId", bookId); putLong("readingOwner", reading.identity); putString("chapterSessionToken", reading.chapter.sessionToken)
                putInt("chapter", 3); putInt("scalarOffset", scrolled.visibleScalarOffset); putInt("ownerLastVisible", scrolled.visibleScalarOffset)
                putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
                putString("ownerId", lease.ownerId.toString()); putString("clientId", lease.clientId.toString()); putLong("ownerGeneration", lease.ownerGeneration)
                putString("runtimeSession", lease.runtimeSession); putString("physicalViewId", mount.view.physicalViewId.toString())
                putLong("layoutEpoch", ready.layoutEpoch); putString("layoutToken", ready.layoutToken)
                putBoolean("swipeDispatched", true); putBoolean("nonLinkStart", true); putBoolean("autoObserverCompleted", true)
                putInt("startX", points[0]); putInt("startY", points[1]); putInt("endX", points[0]); putInt("endY", points[2])
            })
            marker("mobi-auto-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", autoRecord.getString("id")); putLong("requestId", autoRecord.getLong("requestId"))
                putString("runtimeSession", autoRecord.getString("runtimeSession")); putInt("chapter", 3); putInt("scalarOffset", scrolled.visibleScalarOffset)
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
            val screen = File(current.cacheDir, "reader-a3-mobi-story-${lease.renderId}-draw.png")
            try { marker("mobi-screenshot", Bundle().apply { putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name) }) }
            catch (failure: Exception) { marker("mobi-screenshot", Bundle().apply { putBoolean("saved", false); putString("error", failure.javaClass.name) }) }
            assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))

            phase = "trusted-body-tap-and-one-normal-exit"
            assertTrue("Actual DOM-derived non-link visible content tap not dispatched", UiDevice.getInstance(instrumentation).click(metrics[0], metrics[1]))
            compose.waitUntil(5_000) { model.state.value.reading?.identity == reading.identity && model.state.value.controlsVisible && enabledExit() }
            EbookJourneyProbe.awaitDraw(compose, current.window.decorView)
            assertTrue(enabledExit()); healthy(model, 1)
            assertEquals(scrolled.visibleScalarOffset, model.state.value.reading!!.visibleScalarOffset); assertEquals(beforeExit, EbookJourneyProbe.completedOperationNames(current))
            marker("mobi-trusted-body-tap-controls-draw", Bundle().apply { putLong("readingOwner", reading.identity) })
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
            marker("mobi-exit-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", progress.getString("id")); putLong("requestId", progress.getLong("requestId"))
                putString("runtimeSession", progress.getString("runtimeSession")); putInt("chapter", reading.chapter.index); putInt("scalarOffset", scrolled.visibleScalarOffset)
                putString("expectedRevision", parameters.getString("expectedProgressRevision")); putString("ackRevision", revision)
                putString("bodyVersion", parameters.getString("expectedBodyVersion")); putString("processingVersion", parameters.getString("expectedProcessingVersion"))
                putString("originalRecordSha256", sha(saved.third)); putString("originalWire", wire.toString())
            })
            assertEquals(protected, model.state.value.books.single { it.id == protected.id })
            assertEquals(protectedEpub, model.state.value.books.single { it.id == protectedEpub.id })
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
        private const val METHOD = "oneRealFuHanMobiImportsScrollsAutoSavesAndExitsWithOriginalReceipts"
        private const val IMPORT_ID = "android-import-9f0f562e-ce8a-4fb6-88c0-dbac5f3fbc85"
        private const val BASELINE_PROOF_SHA = "a0ef1a1f15aeb134c5cfe274c30205d3a6f61472e2da598be1b6140fcaf4872d"
        private const val PROTECTED_EPUB_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val SOURCE_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
        private const val SOURCE_BYTES = 8522384L
        private const val PROTECTED_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
    }

    // SAF helpers below are byte-for-byte from the frozen OwnedTxt source.
    private fun selectRealDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val watcherName = "reader-original-picker-open-consent"
        val consentPackage = "com.oplus.securitypermission"
        val consentTitle = "“Reader”想要打开“io.reader.minliny.test”"
        var consentClickAttempted = false
        device.registerWatcher(watcherName, UiWatcher {
            val title = By.pkg(consentPackage).res("$consentPackage:id/alertTitle").text(consentTitle)
            val checkbox = By.pkg(consentPackage).res("$consentPackage:id/coui_security_alert_dialog_checkbox")
                .text("始终允许打开").checkable(true).checked(false).enabled(true)
            val open = By.pkg(consentPackage).res("android:id/button1").text("打开").enabled(true)
            if (consentClickAttempted || device.findObjects(title).size != 1 ||
                device.findObjects(checkbox).size != 1 || device.findObjects(open).size != 1) {
                false
            } else {
                val titleSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/alertTitle").text(consentTitle)
                val checkboxSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/coui_security_alert_dialog_checkbox").text("始终允许打开")
                val openSelector = UiSelector().packageName(consentPackage)
                    .resourceId("android:id/button1").text("打开").enabled(true)
                if (!device.findObject(titleSelector).exists() || device.findObject(checkboxSelector).isChecked ||
                    !device.findObject(openSelector).isEnabled) false else {
                    consentClickAttempted = true // At most one dispatch, including a failed click.
                    marker("oem-open-observed", Bundle().apply { putString("dialogTitle", consentTitle); putBoolean("checkboxChecked", false) })
                    val clicked = device.findObject(openSelector).click()
                    val gone = device.findObject(titleSelector).waitUntilGone(2_000)
                    marker("oem-open-ACK", Bundle().apply { putBoolean("clickDispatched", clicked); putBoolean("dialogGone", gone) })
                    clicked
                }
            }
        })
        try {
            val provider = "com\\.(google\\.)?android\\.documentsui"
            val providerSelector = UiSelector().packageNameMatches(provider)
            assertTrue("System document picker must actually open",
                device.findObject(providerSelector).waitForExists(10_000))
            marker("picker-visible")
            val file = providerSelector.text(name)
            val folder = providerSelector.text("Reader")
            val drawer = providerSelector.resourceIdMatches(".*:id/roots_list")
            val downloads = drawer.childSelector(UiSelector().textMatches("Downloads|下载|下载内容"))
            if (!device.findObject(file).exists()) {
                // The previous picker can retain its drawer. Do not toggle an already
                // open drawer, and never carry UiObject2 across its animation.
                if (device.findObject(drawer).exists()) {
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                } else if (!device.findObject(folder).exists()) {
                    val roots = providerSelector.descriptionMatches("Show roots|显示根目录|显示位置|导航抽屉")
                    clickPickerOnce(device, roots, "picker-roots", drawer)
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                }
                if (!device.findObject(file).exists()) clickPickerOnce(device, folder, "picker-folder", file)
            }
            assertTrue("Original input missing: $name", device.findObject(file).waitForExists(5_000))
            marker("picker-file-click")
            // UiObject stores a selector and resolves its live node during this one click.
            assertTrue("Original document click was not dispatched", device.findObject(file).click())
            assertTrue("System picker did not return to Reader", device.findObject(providerSelector).waitUntilGone(10_000))
            assertTrue("Original picker return did not expose Reader's actual window",
                device.findObject(UiSelector().packageName("io.reader.minliny")).waitForExists(10_000))
            marker("picker-returned")
        } finally {
            device.removeWatcher(watcherName)
        }
    }

    private fun clickPickerOnce(device: UiDevice, selector: UiSelector, stage: String, next: UiSelector, gone: UiSelector? = null) {
        assertTrue("$stage target absent", device.findObject(selector).waitForExists(5_000))
        marker("$stage-click")
        // Reacquire from the exact selector immediately before the only click.
        assertTrue("$stage click was not dispatched", device.findObject(selector).click())
        if (gone != null) assertTrue("$stage did not retire its drawer", device.findObject(gone).waitUntilGone(5_000))
        assertTrue("$stage did not expose the next state", device.findObject(next).waitForExists(5_000))
        marker("$stage-ready")
    }
}
