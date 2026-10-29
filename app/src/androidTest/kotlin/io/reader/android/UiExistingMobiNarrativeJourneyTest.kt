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
import android.view.ViewGroup
import android.view.ViewTreeObserver
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

/** One existing settled original MOBI narrative read. No SAF, import, recovery, second client or retry. */
@RunWith(AndroidJUnit4::class)
class UiExistingMobiNarrativeJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(150, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun existingRealFuHanMobiNarrativeScrollAutoSavesAndExitsWithOriginalReceipts() {
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
            assertEquals(IMPORT_ID, args.getString("expectedImportId"))
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
            val book = initial.books.single { it.id == bookId }; assertEquals(560, book.chapterCount)
            assertEquals(0, book.progressChapter); assertEquals(0, book.progressOffset)
            val importFile = File(current.filesDir, "Reader/Operations/Completed/$IMPORT_ID.json")
            assertEquals(IMPORT_RECORD_BYTES, importFile.length())
            val importRaw = AtomicFile(importFile).openRead().use { it.readBytes() }
            assertEquals(IMPORT_RECORD_BYTES, importRaw.size.toLong()); assertEquals(IMPORT_RECORD_SHA, sha(importRaw))
            val record = JSONObject(importRaw.toString(Charsets.UTF_8))
            assertEquals(IMPORT_ID, record.getString("id")); assertEquals("import", record.getString("kind")); assertTrue(record.getBoolean("completed"))
            assertEquals("finalize", record.getString("terminalStage")); assertEquals("settled", record.getJSONObject("settlement").getString("state"))
            assertEquals(IMPORT_RUNTIME_SESSION, record.getString("runtimeSession")); assertEquals(1012L, record.getLong("requestId"))
            assertFalse(File(current.filesDir, "Reader/Operations/$IMPORT_ID.json").exists())
            assertFalse(File(importFile.parentFile, importFile.name + ".new").exists())
            val binding = record.getJSONObject("binding")
            assertEquals(bookId, binding.getString("bookId")); assertEquals(SOURCE_SHA, binding.getString("sourceSha256"))
            assertEquals(SOURCE_BYTES, binding.getLong("sourceBytes")); assertEquals("mobi", binding.getString("format"))
            assertEquals("local", binding.getString("sourceId")); assertEquals(6L, binding.getLong("sequence"))
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
                putString("baselineProofSha256", BASELINE_PROOF_SHA); putString("expectedImportId", IMPORT_ID)
            })
            marker("mobi-existing-import-finalized-settled-original", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", book.chapterCount)
                putString("originalId", IMPORT_ID); putString("originalRecordSha256", sha(importRaw)); putLong("originalRecordBytes", importRaw.size.toLong())
                putString("format", binding.getString("format")); putString("authorityEpoch", binding.getString("authorityEpoch"))
                putLong("sequence", binding.getLong("sequence")); putLong("sourceBytes", original.length()); putString("sourceSHA", SOURCE_SHA)
                putLong("importRevision", initial.importRevision); putBoolean("reimportDispatched", false)
                putString("importRuntimeSession", record.getString("runtimeSession")); putLong("importRequestId", record.getLong("requestId"))
            })
            val beforeAuto = completedNames()

            phase = "one-real-core-mobi-directory"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_directory_$bookId"))
            compose.onNodeWithTag("reader_book_directory_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Directory || failed(model)) }
            healthy(model, 0); assertNull(model.state.value.reading)
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
            healthy(model, 0)
            val reading = model.state.value.reading ?: throw AssertionError("Actual MOBI Core chapter absent")
            assertEquals(ReaderLocalBookFormat.MOBI, reading.format)
            assertEquals(bookId, reading.chapter.bookId); assertEquals(selected.index, reading.chapter.index)
            assertTrue(reading.chapter.text.any { !it.isWhitespace() })
            assertEquals(0, reading.visibleScalarOffset); assertFalse(model.state.value.controlsVisible)
            assertEquals(beforeAuto, completedNames())

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
            assertNotEquals("Reading must use its current runtime, not the old import runtime", IMPORT_RUNTIME_SESSION, lease.runtimeSession)
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
            glyphTapCoordinates(web, probe) // Initial real glyph/viewport observation; not clicked.
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
            assertEquals(beforeAuto, completedNames())

            phase = "one-real-narrative-swipe-and-auto-save"
            val swipe = swipeProbe(web)
            assertFalse(swipe.has("probeError")); assertTrue(swipe.getBoolean("selectionCollapsed"))
            assertTrue(swipe.getBoolean("nonLinkStart")); assertEquals(probe.getString("entryUrl"), swipe.getString("entryUrl"))
            val points = compose.runOnUiThread {
                prepared.requireCurrent(); assertSame(mount, field(model, "ebookMount")); assertSame(web, actualWebView())
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
                    val names = completedNames() - beforeAuto
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
            val beforeExit = completedNames() // Auto original is completely archived before the only exit can cancel its observer.
            compose.waitForIdle()
            compose.onNodeWithTag("reader_ebook_restore_loading", useUnmergedTree = true).assertDoesNotExist()
            attachedDraw(current.window.decorView); attachedDraw(web)
            compose.runOnUiThread { assertEquals(scrolled.visibleScalarOffset, checkNotNull(capturedVisibleAnchor(mount, web, ready))) }
            val scrolledProbe = domProbe(web)
            assertFalse(scrolledProbe.has("probeError")); assertEquals(probe.getString("entryUrl"), scrolledProbe.getString("entryUrl"))
            assertTrue(scrolledProbe.getBoolean("selectionCollapsed"))
            val metrics = glyphTapCoordinates(web, scrolledProbe)
            val screen = File(current.cacheDir, "reader-a3-existing-mobi-story-${lease.renderId}-draw.png")
            try { marker("mobi-screenshot", Bundle().apply { putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name) }) }
            catch (failure: Exception) { marker("mobi-screenshot", Bundle().apply { putBoolean("saved", false); putString("error", failure.javaClass.name) }) }
            assertEquals(beforeExit, completedNames())

            phase = "trusted-body-tap-and-one-normal-exit"
            assertTrue("Actual DOM-derived non-link visible content tap not dispatched", UiDevice.getInstance(instrumentation).click(metrics[0], metrics[1]))
            compose.waitUntil(5_000) { model.state.value.reading?.identity == reading.identity && model.state.value.controlsVisible && enabledExit() }
            attachedDraw(current.window.decorView)
            assertTrue(enabledExit()); healthy(model, 0)
            assertEquals(scrolled.visibleScalarOffset, model.state.value.reading!!.visibleScalarOffset); assertEquals(beforeExit, completedNames())
            marker("mobi-trusted-body-tap-controls-draw", Bundle().apply { putLong("readingOwner", reading.identity) })
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

    private fun capturedVisibleAnchor(mount: ReaderEbookUiMount, web: WebView, initialReady: ReaderEbookReady): Int? {
        val model = current.readerViewModel; val state = model.state.value; val reading = state.reading ?: return null
        if (field(model, "ebookMount") !== mount || state.route != ReaderRoute.Reading || state.busy || state.controlsVisible ||
            reading.identity != mount.readingOwner || reading.chapter.sessionToken != mount.chapter.sessionToken ||
            reading.chapter.bookId != mount.chapter.bookId || reading.chapter.index != mount.chapter.index ||
            reading.chapter.bodyVersion != mount.chapter.bodyVersion || reading.chapter.processingVersion != mount.chapter.processingVersion) return null
        val prepared = mount.preparation ?: return null
        if (prepared.view !== mount.view || prepared.publication.lease != initialReady.lease || initialReady.lease.renderId != mount.view.physicalViewId) return null
        prepared.requireCurrent()
        if (actualWebView() !== web) return null
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
        compose.runOnUiThread { web.evaluateJavascript(SWIPE_READ_ONLY) { result = it; latch.countDown() } }
        assertTrue("Read-only swipe hit callback missing", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(result ?: throw AssertionError("Swipe callback returned null"))
    }

    private fun failed(model: ReaderViewModel): Boolean = model.state.value.let {
        it.error != null || it.protocolRecoveryReference != null || it.pendingImports.isNotEmpty() || it.pendingPositions.isNotEmpty() ||
            it.pendingBookmarks.isNotEmpty() || it.pendingSearchClose != null || (it.ebookFailureOwner != null && it.ebookFailureOwner == it.reading?.identity)
    }
    private fun healthy(model: ReaderViewModel, revision: Long) { assertFalse("Original operation failed/pending", failed(model)); assertEquals(revision, model.state.value.importRevision) }
    private fun completedNames() = File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty().filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }
    private fun freshRecords(before: Set<String>) = (completedNames() - before).map { name ->
        val file = File(current.filesDir, "Reader/Operations/Completed/$name")
        assertTrue(file.length() in 1..8L * 1024 * 1024)
        val raw = AtomicFile(file).openRead().use { it.readBytes() }; Triple(file, JSONObject(raw.toString(Charsets.UTF_8)), raw)
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
        private const val METHOD = "existingRealFuHanMobiNarrativeScrollAutoSavesAndExitsWithOriginalReceipts"
        private const val IMPORT_ID = "android-import-edfcd87b-f962-4830-ba12-7b0bb60f7f55"
        private const val IMPORT_RECORD_BYTES = 475956L
        private const val IMPORT_RECORD_SHA = "0d22d033e664add1894c6ffdc676d886c9a42787b2cdc354846b4526ee517561"
        private const val IMPORT_RUNTIME_SESSION = "0c573f73-d524-4737-82d1-2b750c548f33"
        private const val BASELINE_PROOF_SHA = "fb86781b00a716f926ea2c9cda0b8ebfcd7952e751dfcd4d74276f6318a58f64"
        private const val PROTECTED_EPUB_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val SOURCE_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
        private const val SOURCE_BYTES = 8522384L
        private const val PROTECTED_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
        private val SWIPE_READ_ONLY = """
            (()=>{try{
              const f=document.getElementById('publication'),d=f?.contentDocument,w=f?.contentWindow;
              if(!d?.body||!w||w.getSelection()?.isCollapsed===false)return {probeError:'NO_CURRENT_COLLAPSED_DOCUMENT'};
              const x=w.innerWidth*.5,startY=Math.min(w.innerHeight-60,w.innerHeight*.78),endY=Math.max(40,w.innerHeight*.28);
              const hit=d.elementFromPoint(x,startY);
              if(!hit||hit.closest('a[href],button,input,textarea,select,[contenteditable]'))return {probeError:'NO_NON_LINK_SWIPE_START'};
              const r=f.getBoundingClientRect();return {entryUrl:w.location.href,frameLeft:r.left,frameTop:r.top,
                width:w.innerWidth,height:w.innerHeight,x,startY,endY,nonLinkStart:true,selectionCollapsed:true,hitTag:hit.localName};
            }catch(e){return {probeError:String(e)}}})()
        """.trimIndent()
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
