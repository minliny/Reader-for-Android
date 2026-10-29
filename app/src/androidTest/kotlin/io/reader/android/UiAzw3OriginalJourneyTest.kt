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
import android.webkit.MimeTypeMap
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
import io.reader.android.runtime.ReaderPositionSaveResult
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** One real AZW3 import, direct shelf directory story read and sole normal exit receipt. No second Library/client, parser or retry. */
@RunWith(AndroidJUnit4::class)
class UiAzw3OriginalJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    private var failureDiagnosticCaptured = false
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun oneRealAliceAzw3ImportsDirectoryStoryRendersAndExitsWithOriginalReceipt() {
        marker("method-enter") // Before launch/current Activity access.
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            val baselineProofSha = args.getString("baselineProofSha256") ?: throw AssertionError("Actual post-MOBI stopped proof required")
            assertTrue("Actual post-MOBI stopped proof is still pending", baselineProofSha.matches(Regex("[a-f0-9]{64}")))
            val protectedMobiScalar = args.getString("protectedMobiScalar")?.toIntOrNull() ?: throw AssertionError("Actual final MOBI scalar required")
            assertTrue(protectedMobiScalar > 0)
            assertEquals("alice-wonderland.azw3", args.getString("displayName")); assertEquals(SOURCE_SHA, args.getString("expectedSHA"))
            assertEquals(SOURCE_BYTES, args.getString("expectedBytes")?.toLongOrNull() ?: throw AssertionError("expectedBytes required"))
            assertEquals(PROTECTED_SHA, args.getString("protectedBookSHA")); assertEquals(PROTECTED_EPUB_SHA, args.getString("protectedEpubSHA"))
            assertEquals(16, args.getString("expectedChapterCount")?.toIntOrNull() ?: throw AssertionError("expectedChapterCount required"))
            assertEquals(4, args.getString("chapterIndex")?.toIntOrNull() ?: throw AssertionError("chapterIndex required"))
            assertEquals("CHAPTER I. Down the Rabbit-Hole", args.getString("chapterTitle"))
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
            val protectedMobi = initial.books.single { it.id == "local:$PROTECTED_MOBI_SHA" }
            assertEquals(3, protectedMobi.progressChapter); assertEquals(protectedMobiScalar, protectedMobi.progressOffset)
            assertUnknownUnchanged()
            assertTrue(initial.books.none { it.id == bookId }); assertFalse(File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source").exists())
            val beforeImport = completedNames()
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue("Physical screen must already be on", UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse("Physical screen must already be unlocked", checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            assertTrue(compose.runOnUiThread { current.window.decorView.hasWindowFocus() })
            marker("azw3-physical-interaction-preflight", Bundle().apply {
                putBoolean("screenOn", true); putBoolean("keyguardLocked", false); putBoolean("windowFocused", true)
            })
            marker("azw3-input-bound", Bundle().apply {
                putString("displayName", "alice-wonderland.azw3"); putString("expectedSHA", SOURCE_SHA); putLong("expectedBytes", SOURCE_BYTES)
                putString("protectedBookSHA", PROTECTED_SHA); putString("protectedEpubSHA", PROTECTED_EPUB_SHA)
                putString("baselineProofSha256", baselineProofSha); putString("protectedMobiSHA", PROTECTED_MOBI_SHA); putInt("protectedMobiScalar", protectedMobiScalar)
            })

            phase = "one-saf-azw3-import"
            compose.onNodeWithTag("reader_import").assertIsEnabled().performClick()
            selectRealDocument("alice-wonderland.azw3")
            compose.waitUntil(120_000) { val state = model.state.value; !state.busy && (state.importRevision > 0 || failed(model)) }
            healthy(model, 1); assertEquals(bookId, model.state.value.lastImportedBookId)
            assertEquals(protected, model.state.value.books.single { it.id == protected.id })
            assertEquals(protectedEpub, model.state.value.books.single { it.id == protectedEpub.id })
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
            val selectedEntry = locator.getString("entryPath"); assertTrue(selectedEntry.isNotBlank())
            val sourceStart = locator.getInt("chapterStartScalar"); val sourceEnd = locator.getInt("chapterEndScalar")
            val sourceCanonicalSha = locator.getString("chapterContentSha256")
            assertTrue(sourceStart >= 0 && sourceEnd > sourceStart && sourceCanonicalSha.matches(Regex("[a-f0-9]{64}")))
            marker("azw3-import-finalized-settled-original", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", book.chapterCount)
                putString("originalId", record.getString("id")); putString("originalRecordSha256", sha(imported.third))
                putString("format", binding.getString("format")); putString("authorityEpoch", binding.getString("authorityEpoch"))
                putLong("sequence", binding.getLong("sequence")); putLong("sourceBytes", original.length()); putString("sourceSHA", SOURCE_SHA)
                putLong("importRevision", model.state.value.importRevision)
            })
            val beforeExit = completedNames()

            phase = "one-real-core-azw3-directory"
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_directory_$bookId"))
            compose.onNodeWithTag("reader_book_directory_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Directory || failed(model)) }
            healthy(model, 1); assertNull(model.state.value.reading)
            val toc = model.state.value.chapters; assertEquals(16, toc.size)
            val selected = toc.single { it.index == 4 }; assertEquals(sourceEntry.getString("title"), selected.title)
            marker("azw3-core-directory-bound", Bundle().apply {
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
            assertNull(reading.newPositionIntent); assertFalse(reading.newPositionIntentPresented)
            assertNull(model.state.value.positionSaveBarrier); assertUnknownUnchanged()
            assertEquals(beforeExit, completedNames())

            phase = "actual-webview-ready-draw-dom"
            compose.waitUntil(30_000) { failed(model) || compose.runOnUiThread { readyMount() != null } }
            healthy(model, 1)
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
            marker("azw3-real-webview-ready-draw-observed-dom", Bundle().apply {
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
                putString("textClaim", "DOM_SAMPLE_ONLY_ORIGINAL_CANONICAL_AND_CORE_PROCESSED_EDITS_AUTHENTICATED_BY_PRODUCTION_ADMIT_NATIVE_VISUAL_DRAW_READY")
            })
            assertEquals(beforeExit, completedNames()); assertUnknownUnchanged()

            phase = "one-real-glyph-and-normal-exit"
            val screen = File(current.cacheDir, "reader-a3-azw3-story-${lease.renderId}-draw.png")
            try { marker("azw3-screenshot", Bundle().apply { putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name) }) }
            catch (failure: Exception) { marker("azw3-screenshot", Bundle().apply { putBoolean("saved", false); putString("error", failure.javaClass.name) }) }
            val metrics = glyphTapCoordinates(web, probe)
            assertEquals(beforeExit, completedNames())
            assertTrue("Actual DOM-derived non-link glyph tap not dispatched", UiDevice.getInstance(instrumentation).click(metrics[0], metrics[1]))
            compose.waitUntil(5_000) { model.state.value.reading?.identity == reading.identity && model.state.value.controlsVisible && enabledExit() }
            attachedDraw(current.window.decorView); assertTrue(enabledExit()); healthy(model, 1)
            assertEquals(0, model.state.value.reading!!.visibleScalarOffset); assertEquals(beforeExit, completedNames())
            marker("azw3-trusted-body-tap-controls-draw", Bundle().apply { putLong("readingOwner", reading.identity) })
            compose.onNodeWithTag("reader_exit").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.reading == null || failed(model)) }
            healthy(model, 1); assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            val saved = freshRecords(beforeExit).single()
            val progress = saved.second; val parameters = progress.getJSONObject("parameters"); val wire = progress.getJSONObject("wireTerminal")
            assertTrue(progress.getBoolean("completed")); assertEquals("progress", progress.getString("kind")); assertEquals(bookId, progress.getString("bookId"))
            assertEquals(4, parameters.getInt("chapterIndex")); assertEquals(0, parameters.getInt("chapterOffset"))
            val revision = ProgressReceiptPolicy.revision(progress, wire)
            assertEquals(lease.runtimeSession, progress.getString("runtimeSession"))
            assertEquals(reading.chapter.bodyVersion, parameters.getString("expectedBodyVersion")); assertEquals(reading.chapter.processingVersion, parameters.getString("expectedProcessingVersion"))
            assertFalse(File(current.filesDir, "Reader/Operations/${progress.getString("id")}.json").exists())
            assertFalse(File(saved.first.parentFile, saved.first.name + ".new").exists())
            assertNewConfirmedAttempt(model, reading.identity, reading.chapter.sessionToken, progress)
            assertUnknownUnchanged()
            marker("azw3-exit-original-save-ACK", Bundle().apply {
                putString("bookId", bookId); putString("originalId", progress.getString("id")); putLong("requestId", progress.getLong("requestId"))
                putString("runtimeSession", progress.getString("runtimeSession")); putInt("chapter", 4); putInt("scalarOffset", 0)
                putString("expectedRevision", parameters.getString("expectedProgressRevision")); putString("ackRevision", revision)
                putString("bodyVersion", parameters.getString("expectedBodyVersion")); putString("processingVersion", parameters.getString("expectedProcessingVersion"))
                putString("originalRecordSha256", sha(saved.third)); putString("originalParameters", parameters.toString()); putString("originalWire", wire.toString())
                putString("saveResult", "ConfirmedNewOriginal")
            })
            assertEquals(protected, model.state.value.books.single { it.id == protected.id })
            assertEquals(protectedEpub, model.state.value.books.single { it.id == protectedEpub.id })
            assertEquals(protectedMobi, model.state.value.books.single { it.id == protectedMobi.id })
            assertEquals(sha(importRaw), shaFile(importFile)); assertEquals(1L, model.state.value.importRevision)
            assertEquals(beforeExit + saved.first.name, completedNames())
            compose.onNodeWithTag("reader_shelf_query").assertIsDisplayed()
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(256)) }) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            try { capturePickerFailure(phase) }
            catch (secondary: Throwable) { failure.addSuppressed(secondary) }
            throw failure
        } finally {
            try { finishOnlyBookshelf() }
            catch (cleanup: Throwable) { if (primary == null) throw cleanup else if (primary !== cleanup) primary.addSuppressed(cleanup) }
        }
        marker("method-exit")
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

    private fun assertNewConfirmedAttempt(model: ReaderViewModel, owner: Long, token: String, record: JSONObject) {
        val attempt = checkNotNull(model.state.value.lastPositionSaveAttempt)
        assertEquals(owner, attempt.readingOwner); assertEquals(token, attempt.sessionToken)
        val confirmed = attempt.result as? ReaderPositionSaveResult.ConfirmedNewOriginal
            ?: throw AssertionError("A skipped/absent attempt is not an original ACK")
        val p = record.getJSONObject("parameters"); val wire = record.getJSONObject("wireTerminal")
        assertNotEquals(UNKNOWN_ID, confirmed.originalId); assertEquals(record.getString("id"), confirmed.originalId)
        assertEquals(record.getLong("requestId"), confirmed.requestId); assertEquals(record.getString("runtimeSession"), confirmed.runtimeSession)
        assertEquals(bookId, confirmed.bookId); assertEquals(p.getInt("chapterIndex"), confirmed.chapterIndex)
        assertEquals(p.getInt("chapterOffset"), confirmed.scalarOffset)
        assertEquals(p.getString("expectedBodyVersion"), confirmed.bodyVersion); assertEquals(p.getString("expectedProcessingVersion"), confirmed.processingVersion)
        assertEquals(ProgressReceiptPolicy.revision(record, wire), confirmed.progressRevision)
        assertEquals(p.toString(), JSONObject(confirmed.originalParametersJson).toString())
        assertEquals(wire.toString(), JSONObject(confirmed.parsedTerminalJson).toString()) // Parsed JSON serialization, not byte raw.
    }

    /** Verify the actual production edit tuple; no diff, HTML projection or rule processing in this test. */

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
        it.error != null || it.protocolRecoveryReference != null || it.pendingImports.isNotEmpty() || (it.pendingReady && (it.pendingPositions.map { pending -> pending.id } != listOf(UNKNOWN_ID) || it.pendingPositions.singleOrNull()?.bookId != "local:$PROTECTED_MOBI_SHA")) ||
            it.pendingBookmarks.isNotEmpty() || it.pendingSearchClose != null || (it.ebookFailureOwner != null && it.ebookFailureOwner == it.reading?.identity)
    }

    private fun healthy(model: ReaderViewModel, revision: Long) {
        assertTrue(model.state.value.pendingReady); assertFalse("Unexpected operation failed/pending", failed(model))
        assertEquals(listOf(UNKNOWN_ID), model.state.value.pendingPositions.map { it.id }); assertEquals("local:$PROTECTED_MOBI_SHA", model.state.value.pendingPositions.single().bookId)
        assertEquals(revision, model.state.value.importRevision)
    }

    private fun completedNames() = File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty().filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }

    private fun freshRecords(before: Set<String>) = (completedNames() - before).map { name ->
        val file = File(current.filesDir, "Reader/Operations/Completed/$name")
        assertTrue(file.length() in 1..8L * 1024 * 1024)
        assertFalse(File(file.parentFile, file.name + ".new").exists())
        assertFalse(File(file.parentFile, file.name + ".bak").exists())
        val raw = file.readBytes(); Triple(file, JSONObject(raw.toString(Charsets.UTF_8)), raw)
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

    // Source-bound SAF document rows, one optional navigation and at most two observed container scrolls.
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
            // android:id/title is a real document-row title. Toolbar and breadcrumb
            // labels can name Reader too; they never authorize folder navigation.
            val file = providerSelector.resourceId("android:id/title").text(name)
            val folder = providerSelector.resourceId("android:id/title").text("Reader")
            val drawer = providerSelector.resourceIdMatches(".*:id/roots_list")
            val downloads = drawer.childSelector(UiSelector().packageNameMatches(provider).textMatches("Downloads|下载|下载内容"))
            val readerBreadcrumb = providerSelector.resourceIdMatches(".*:id/breadcrumb_text").text("Reader")
            val downloadsBreadcrumb = providerSelector.resourceIdMatches(".*:id/breadcrumb_text").textMatches("Downloads|下载|下载内容")
            val exactPreview = providerSelector.resourceIdMatches(".*:id/preview_icon").description("预览“$name”文件")
            if (!device.findObject(file).exists()) {
                // The observed exact preview plus both breadcrumb identities proves
                // this retained Reader folder; the preview itself is never clicked.
                val alreadyInReader = device.findObject(readerBreadcrumb).exists() &&
                    device.findObject(downloadsBreadcrumb).exists() && device.findObject(exactPreview).exists()
                if (!alreadyInReader) {
                    // One roots -> Downloads -> Reader document-row navigation only.
                    if (!device.findObject(drawer).exists()) {
                        val roots = providerSelector.descriptionMatches("Show roots|显示根目录|显示位置|导航抽屉")
                        clickPickerOnce(device, roots, "picker-roots", drawer)
                    }
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                    clickPickerOnce(device, folder, "picker-folder", readerBreadcrumb)
                }
                assertTrue("Current Reader breadcrumb absent", device.findObject(readerBreadcrumb).waitForExists(5_000))
                assertTrue("Downloads ancestor breadcrumb absent", device.findObject(downloadsBreadcrumb).waitForExists(5_000))
                exposeDocumentTitle(device, provider, file)
            }
            assertTrue("Original input missing: $name", device.findObject(file).waitForExists(5_000))
            marker("picker-file-click")
            // UiObject stores a selector and resolves its live node during this one click.
            val clickReturned = device.findObject(file).click()
            marker("picker-file-click-observed", Bundle().apply { putBoolean("clickReturned", clickReturned) })
            val pickerGone = device.findObject(providerSelector).waitUntilGone(10_000)
            assertTrue("System picker disappearance was not observed within 10 seconds", pickerGone)
            val readerWindowObserved = device.findObject(UiSelector().packageName("io.reader.minliny")).waitForExists(10_000)
            assertTrue("Reader's actual window was not observed after file selection", readerWindowObserved)
            marker("picker-returned", Bundle().apply {
                putBoolean("clickReturned", clickReturned); putBoolean("pickerGone", pickerGone)
                putBoolean("readerWindowObserved", readerWindowObserved)
            })
        } finally {
            device.removeWatcher(watcherName)
        }
    }

    /** At most two real scrolls in the unique actual DocumentsUI scroll container. */
    private fun exposeDocumentTitle(device: UiDevice, provider: String, file: UiSelector) {
        val container = UiSelector().packageNameMatches(provider).className("android.widget.ScrollView").scrollable(true)
        for (attempt in 1..2) {
            val beforeVisible = device.findObject(file).waitForExists(750)
            if (beforeVisible) return
            assertTrue("Unique DocumentsUI scroll container absent", device.findObject(container.instance(0)).exists())
            assertFalse("DocumentsUI scroll container ambiguous", device.findObject(container.instance(1)).exists())
            val returned = UiScrollable(container).scrollForward(35)
            val afterVisible = device.findObject(file).waitForExists(1_000)
            marker("picker-document-scroll-$attempt", Bundle().apply {
                putInt("attempt", attempt); putInt("maxScrolls", 2); putInt("scrollSteps", 35)
                putString("containerClass", "android.widget.ScrollView")
                putBoolean("beforeVisible", beforeVisible); putBoolean("scrollReturned", returned); putBoolean("afterVisible", afterVisible)
            })
            if (afterVisible) return // The Boolean alone does not prove the original file became visible.
        }
        throw AssertionError("Exact document-row title absent after two bounded DocumentsUI scrolls")
    }

    /** One read-only failure observation; no picker action, URI injection or provider query. */
    private fun capturePickerFailure(phase: String) {
        if (failureDiagnosticCaptured) return
        failureDiagnosticCaptured = true // Reserve the sole attempt even if its dump/marker fails.
        val details = Bundle().apply {
            putString("failurePhase", phase); putString("mimeExtension", "azw3")
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension("azw3")
            putBoolean("mappedMimePresent", mapped != null)
            if (mapped != null) putString("mappedMime", mapped)
            putString("mimeClaim", "MIMETYPEMAP_LOOKUP_ONLY_NOT_DOCUMENT_PROVIDER_OR_RETURNED_URI")
        }
        val file = File(instrumentation.targetContext.cacheDir, "reader-a3-azw3-picker-failure-${UUID.randomUUID()}.xml")
        details.putString("cacheFile", file.name)
        try {
            UiDevice.getInstance(instrumentation).dumpWindowHierarchy(file)
            val bytes = file.length(); details.putLong("hierarchyBytes", bytes)
            val bounded = file.isFile && bytes in 1..2L * 1024 * 1024
            details.putBoolean("hierarchySaved", bounded)
            if (bounded) details.putString("hierarchySha256", shaFile(file))
        } catch (error: Exception) {
            details.putBoolean("hierarchySaved", false); details.putString("hierarchyFailureClass", error.javaClass.name)
        }
        marker("azw3-picker-failure-diagnostic", details)
    }

    private fun clickPickerOnce(device: UiDevice, selector: UiSelector, stage: String, next: UiSelector, gone: UiSelector? = null) {
        assertTrue("$stage target absent", device.findObject(selector).waitForExists(5_000))
        marker("$stage-click")
        // Reacquire from the exact selector immediately before the only click.
        val clickReturned = device.findObject(selector).click()
        marker("$stage-click-observed", Bundle().apply { putBoolean("clickReturned", clickReturned) })
        if (gone != null) assertTrue("$stage did not retire its drawer", device.findObject(gone).waitUntilGone(5_000))
        assertTrue("$stage did not expose the next state", device.findObject(next).waitForExists(5_000))
        marker("$stage-ready")
    }

    companion object {
        private const val METHOD = "oneRealAliceAzw3ImportsDirectoryStoryRendersAndExitsWithOriginalReceipt"
        private const val UNKNOWN_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val UNKNOWN_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
        private const val UNKNOWN_RUNTIME = "aa519b17-bc13-4f47-841b-a31f9de9a1cc"
        private const val SOURCE_SHA = "604874a7afdb98570f4e41a2dc9af8aace13604af77bf14df903e855564f419b"
        private const val SOURCE_BYTES = 1031552L
        private const val PROTECTED_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val PROTECTED_EPUB_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val PROTECTED_MOBI_SHA = "a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef"
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
