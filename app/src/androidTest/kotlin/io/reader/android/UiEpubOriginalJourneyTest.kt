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
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiWatcher
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

/** One actual original EPUB. No direct VM business call, second Library/client, parser or retry. */
@RunWith(AndroidJUnit4::class)
class UiEpubOriginalJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private val bookId = "local:$SOURCE_SHA"
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test
    fun oneRealZhongSongEpubImportsRendersAndExitsWithOriginalReceipt() {
        marker("method-enter") // Before launch/current Activity access.
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            assertEquals("终宋.epub", args.getString("displayName"))
            assertEquals(SOURCE_SHA, args.getString("expectedSHA"))
            assertEquals(SOURCE_BYTES, args.getString("expectedBytes")?.toLongOrNull() ?: throw AssertionError("expectedBytes required"))
            assertEquals(PROTECTED_SHA, args.getString("protectedBookSHA"))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
            healthy(model, 0)
            val initial = model.state.value
            assertTrue(initial.pendingReady && initial.reading == null && initial.route == ReaderRoute.Bookshelf)
            val protected = initial.books.single { it.id == "local:$PROTECTED_SHA" }
            assertEquals(1, protected.progressChapter); assertEquals(210, protected.progressOffset)
            assertTrue(initial.books.none { it.id == bookId })
            assertFalse(File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source").exists())
            val before = completedNames()
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

            phase = "one-saf-import"
            compose.onNodeWithTag("reader_import").assertIsEnabled().performClick()
            selectRealDocument("终宋.epub")
            compose.waitUntil(120_000) {
                val s = model.state.value
                !s.busy && (s.importRevision > 0 || failed(model))
            }
            healthy(model, 1)
            assertEquals(bookId, model.state.value.lastImportedBookId)
            assertEquals(protected, model.state.value.books.single { it.id == protected.id })
            val book = model.state.value.books.single { it.id == bookId }
            assertTrue(book.chapterCount > 1)
            val imported = freshRecords(before).single()
            val record = imported.second
            assertEquals("import", record.getString("kind")); assertTrue(record.getBoolean("completed"))
            assertEquals("finalize", record.getString("terminalStage"))
            assertEquals("settled", record.getJSONObject("settlement").getString("state"))
            val binding = record.getJSONObject("binding")
            assertEquals(bookId, binding.getString("bookId")); assertEquals(SOURCE_SHA, binding.getString("sourceSha256"))
            assertEquals(SOURCE_BYTES, binding.getLong("sourceBytes")); assertEquals("epub", binding.getString("format"))
            val original = File(current.filesDir, "Reader/Originals/$SOURCE_SHA.source")
            assertEquals(SOURCE_BYTES, original.length()); assertEquals(SOURCE_SHA, shaFile(original))
            marker("epub-import-finalized-settled-original", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", book.chapterCount)
                putString("originalId", record.getString("id")); putString("originalRecordSha256", sha(imported.third))
                putString("format", binding.getString("format")); putString("authorityEpoch", binding.getString("authorityEpoch"))
                putLong("sequence", binding.getLong("sequence")); putLong("sourceBytes", original.length()); putString("sourceSHA", SOURCE_SHA)
            })
            val beforeExit = completedNames()

            phase = "shelf-core-directory-once"
            // Directory while no Reading exists admits no old-session progress save.
            compose.onNodeWithTag("reader_bookshelf").performScrollToNode(hasTestTag("reader_book_directory_$bookId"))
            compose.onNodeWithTag("reader_book_directory_$bookId").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Directory || failed(model)) }
            healthy(model, 1); assertNull(model.state.value.reading)
            val toc = model.state.value.chapters
            assertEquals(book.chapterCount, toc.size)
            val selected = toc.single { it.index == 1 } // One actual Core item. No blank/error alternative chapter.
            marker("epub-core-directory-bound", Bundle().apply {
                putString("bookId", bookId); putInt("chapterCount", toc.size); putInt("selectedChapter", selected.index)
                putString("selectedTitle", selected.title)
            })
            compose.onNodeWithTag("reader_directory").performScrollToNode(hasTestTag("reader_chapter_${selected.index}"))
            compose.onNodeWithTag("reader_chapter_${selected.index}").assertIsDisplayed().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.reading != null || failed(model)) }
            healthy(model, 1)
            val reading = model.state.value.reading ?: throw AssertionError("Original Core chapter absent")
            assertEquals(ReaderLocalBookFormat.EPUB, reading.format)
            assertEquals(bookId, reading.chapter.bookId); assertEquals(selected.index, reading.chapter.index)
            assertTrue(reading.chapter.text.any { !it.isWhitespace() })
            assertEquals(0, reading.visibleScalarOffset); assertFalse(model.state.value.controlsVisible)
            assertEquals(beforeExit, completedNames())

            phase = "actual-webview-ready-draw-dom"
            compose.waitUntil(30_000) { failed(model) || compose.runOnUiThread { readyMount() != null } }
            healthy(model, 1)
            val mount = compose.runOnUiThread { checkNotNull(readyMount()) }
            val web = compose.runOnUiThread { actualWebView() }
            attachedDraw(web)
            val probe = domProbe(web)
            assertFalse(probe.has("probeError"))
            assertEquals(ORIGIN, probe.getString("origin")); assertEquals(SHELL, probe.getString("shellUrl"))
            val prepared = checkNotNull(mount.preparation)
            val lease = prepared.publication.lease
            val presentation = prepared.publication.presentation
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
            assertEquals(significant(reading.chapter.text), significant(probe.getString("domText")))
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
                val glyph = probe.getJSONObject("glyph")
                assertTrue(glyph.getDouble("bottom") > 32.0 && glyph.getDouble("top") < probe.getDouble("height") - 52)
                val tap = probe.getJSONObject("tap")
                val x = location[0] + ((probe.getDouble("frameLeft") + (tap.getDouble("left") + tap.getDouble("right")) * .5) * density).toInt()
                val y = location[1] + ((probe.getDouble("frameTop") + (tap.getDouble("top") + tap.getDouble("bottom")) * .5) * density).toInt()
                assertTrue(visible.contains(x, y)); intArrayOf(x, y)
            }
            assertTrue(Build.VERSION.SDK_INT >= 26)
            val provider = WebView.getCurrentWebViewPackage() ?: throw AssertionError("Actual WebView provider absent")
            marker("epub-real-webview-ready-draw-canonical", Bundle().apply {
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
                putString("significantDomSha256", sha(significant(probe.getString("domText")).toByteArray(Charsets.UTF_8)))
                putString("probeMetrics", JSONObject(probe.toString()).apply { remove("domText") }.toString())
                putString("textClaim", "WHITESPACE_INSENSITIVE_LIVE_CHAPTER_PLUS_REAL_PRODUCTION_FULL_ENTRY_ADMISSION_NOT_RAW_HTML")
            })
            val screen = File(current.cacheDir, "reader-a3-epub-original-draw.png")
            try { marker("epub-screenshot", Bundle().apply { putBoolean("saved", UiDevice.getInstance(instrumentation).takeScreenshot(screen)); putString("cacheFile", screen.name) }) }
            catch (failure: Exception) { marker("epub-screenshot", Bundle().apply { putBoolean("saved", false); putString("error", failure.javaClass.name) }) }
            assertEquals(beforeExit, completedNames())

            phase = "trusted-body-tap-and-one-normal-exit"
            assertTrue("Actual DOM-derived non-link glyph tap not dispatched", UiDevice.getInstance(instrumentation).click(metrics[0], metrics[1]))
            compose.waitUntil(5_000) { model.state.value.reading?.identity == reading.identity && model.state.value.controlsVisible && enabledExit() }
            attachedDraw(current.window.decorView)
            assertTrue(enabledExit()); healthy(model, 1)
            assertEquals(reading.visibleScalarOffset, model.state.value.reading!!.visibleScalarOffset); assertEquals(beforeExit, completedNames())
            marker("epub-trusted-body-tap-controls-draw", Bundle().apply { putLong("readingOwner", reading.identity) })
            compose.onNodeWithTag("reader_exit").assertIsEnabled().performClick()
            compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.reading == null || failed(model)) }
            healthy(model, 1); assertNull(model.state.value.reading); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
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
    private fun significant(text: String): String {
        val value = StringBuilder()
        text.codePoints().forEach { if (!Character.isWhitespace(it) && !Character.isSpaceChar(it)) value.appendCodePoint(it) }
        return value.toString()
    }

    companion object {
        private const val METHOD = "oneRealZhongSongEpubImportsRendersAndExitsWithOriginalReceipt"
        private const val SOURCE_SHA = "5d6d9dd26bbea9ed0db4e4f7a127a538dfe439f5694c3370a30ac33503db9d67"
        private const val SOURCE_BYTES = 7406795L
        private const val PROTECTED_SHA = "aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5"
        private const val ORIGIN = "https://reader-ebook.invalid"
        private const val SHELL = "$ORIGIN/__reader/shell.html"
        private val DOM_READ_ONLY = """
            (()=>{try{
              const f=document.getElementById('publication'),d=f?.contentDocument,w=f?.contentWindow;
              if(!d?.body||!w)return {probeError:'NO_CURRENT_DOCUMENT'};
              const walker=d.createTreeWalker(d.body,NodeFilter.SHOW_TEXT),values=[];let n,glyph=null,tap=null;
              while(n=walker.nextNode()){
                const e=n.parentElement;if(!e||e.closest('script,style,noscript,template'))continue;
                const s=w.getComputedStyle(e);if(s.display==='none'||s.visibility==='hidden')continue;
                values.push(n.data);
                if(!glyph||!tap){const i=n.data.search(/\S/u);if(i>=0){const r=d.createRange();r.setStart(n,i);r.setEnd(n,i+(n.data.codePointAt(i)>65535?2:1));
                  const q=r.getBoundingClientRect(),v={left:q.left,right:q.right,top:q.top,bottom:q.bottom};
                  if(q.width>0&&q.height>0){if(!glyph)glyph=v;if(!tap&&!e.closest('a[href]')&&q.top<w.innerHeight&&q.bottom>0)tap=v;}}}
              }
              const text=values.join('');if(text.length>1000000)return {probeError:'BOUNDED_DOM_TEXT_EXCEEDED'};
              const fr=f.getBoundingClientRect();
              return {origin:location.origin,shellUrl:location.href,entryUrl:w.location.href,documentComplete:d.readyState==='complete',
                framePointerEvents:getComputedStyle(f).pointerEvents,documentOverflowY:w.getComputedStyle(d.documentElement).overflowY,
                frameLeft:fr.left,frameTop:fr.top,selectionCollapsed:w.getSelection()?.isCollapsed!==false,imageCount:d.images.length,styleSheetCount:d.styleSheets.length,fontStatus:d.fonts?.status,domText:text,width:w.innerWidth,height:w.innerHeight,glyph,tap};
            }catch(e){return {probeError:String(e)}}})()
        """.trimIndent()
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
