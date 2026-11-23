package io.reader.android

import android.os.Bundle
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiWatcher
import org.json.JSONObject
import io.reader.android.runtime.ReaderLocalBookFormat
import io.reader.android.ui.ReaderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fresh empty-shelf EPUB slice; no protected-book or historical receipt prerequisite. */
@RunWith(AndroidJUnit4::class)
class UiFreshEpubJourneyTest {
    private val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var currentMethod = "unassigned"
    @get:Rule val rule = compose

    @Test
    fun freshZhongSongEpubImportsReadsAndExits() {
        currentMethod = "freshZhongSongEpubImportsReadsAndExits"
        marker("rule-enter")
        try {
            marker("method-enter")
            val args = InstrumentationRegistry.getArguments()
            val name = args.getString("displayName") ?: error("displayName required")
            val sha = args.getString("expectedSHA") ?: error("expectedSHA required")
            val bytes = args.getString("expectedBytes")?.toLongOrNull() ?: error("expectedBytes required")
            require(name.endsWith(".epub") && sha.matches(Regex("[a-f0-9]{64}")) && bytes > 0)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val model = activity().readerViewModel
            compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
            assertTrue(model.state.value.books.isEmpty())
            assertTrue(model.state.value.pendingImports.isEmpty() && model.state.value.pendingPositions.isEmpty())
            assertEquals(0L, model.state.value.importRevision)
            marker("fresh-input-admitted", Bundle().apply { putString("displayName", name); putString("expectedSHA", sha); putLong("expectedBytes", bytes) })
            compose.onNodeWithTag("reader_import").performClick()
            selectDocument(name)
            compose.waitUntil(120_000) { !model.state.value.busy && model.state.value.importRevision == 1L }
            assertEquals("local:$sha", model.state.value.lastImportedBookId)
            val book = model.state.value.books.single { it.id == "local:$sha" }
            assertTrue(book.chapterCount > 1)
            assertTrue(model.state.value.pendingImports.isEmpty() && model.state.value.pendingPositions.isEmpty())
            marker("fresh-epub-import-ACK", Bundle().apply { putString("bookId", book.id); putInt("chapterCount", book.chapterCount) })
            compose.onNodeWithTag("reader_book_${book.id}").performClick()
            compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Reading && !model.state.value.busy }
            assertEquals(ReaderLocalBookFormat.EPUB, model.state.value.reading?.format)
            waitForEpubWebView()
            tapVisibleContent()
            compose.waitUntil(5_000) {
                model.state.value.controlsVisible && compose.onAllNodes(hasTestTag("reader_open_directory")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("reader_open_directory").performClick()
            compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Directory && !model.state.value.busy }
            val target = model.state.value.chapters.firstOrNull { it.index == 1 } ?: error("EPUB chapter 1 absent")
            compose.onNodeWithTag("reader_directory").performScrollToNode(hasTestTag("reader_chapter_${target.index}"))
            compose.onNodeWithTag("reader_chapter_${target.index}").performClick()
            compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Reading && model.state.value.reading?.chapter?.index == 1 && !model.state.value.busy }
            waitForEpubWebView()
            val reading = model.state.value.reading ?: error("EPUB reading absent")
            assertEquals(ReaderLocalBookFormat.EPUB, reading.format)
            assertTrue(reading.chapter.text.any { !it.isWhitespace() })
            marker("fresh-epub-reading-ready", Bundle().apply { putString("bookId", book.id); putInt("chapter", reading.chapter.index); putInt("scalarCount", reading.chapter.scalarCount) })
            tapVisibleContent()
            compose.waitUntil(5_000) { model.state.value.controlsVisible && compose.onAllNodes(hasTestTag("reader_exit")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader_exit").performClick()
            compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Bookshelf && !model.state.value.busy }
            assertTrue(model.state.value.pendingPositions.isEmpty())
            marker("fresh-epub-exit-save-ACK", Bundle().apply { putString("bookId", book.id); putInt("chapter", reading.chapter.index) })
            marker("method-exit")
        } finally {
            scenario?.close(); scenario = null
            marker("activity-destroyed")
            marker("rule-exit")
        }
    }

    private fun activity(): MainActivity { var result: MainActivity? = null; scenario!!.onActivity { result = it }; return checkNotNull(result) }

    private fun waitForEpubWebView() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("reader_ebook_restore_loading")).fetchSemanticsNodes().isEmpty() &&
                currentWebViewOrNull() != null
        }
    }

    private fun currentWebViewOrNull(): WebView? {
        var result: WebView? = null
        compose.runOnUiThread {
            fun visit(view: View) {
                if (view is WebView && view.isAttachedToWindow && view.isShown && view.url?.contains("reader-ebook.invalid") == true) result = view
                if (result == null && view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(activity().window.decorView)
        }
        return result
    }

    private fun tapVisibleContent() {
        val web = currentWebViewOrNull() ?: error("EPUB WebView is not attached")
        val latch = CountDownLatch(1)
        var raw: String? = null
        compose.runOnUiThread { web.evaluateJavascript(DOM_TAP) { raw = it; latch.countDown() } }
        assertTrue("EPUB DOM tap callback missing", latch.await(5, TimeUnit.SECONDS))
        val probe = JSONObject(raw ?: error("EPUB DOM tap result missing"))
        assertTrue("EPUB DOM has no visible text tap: ${probe.optString("probeError")}", !probe.has("probeError"))
        val tap = probe.getJSONObject("tap")
        var screen: IntArray? = null
        compose.runOnUiThread {
            val frameLeft = probe.getDouble("frameLeft")
            val frameTop = probe.getDouble("frameTop")
            val density = web.resources.displayMetrics.density.toDouble()
            val location = IntArray(2).also(web::getLocationOnScreen)
            val x = location[0] + ((frameLeft + (tap.getDouble("left") + tap.getDouble("right")) / 2.0) * density).toInt()
            val y = location[1] + ((frameTop + (tap.getDouble("top") + tap.getDouble("bottom")) / 2.0) * density).toInt()
            val visible = Rect()
            assertTrue(web.getGlobalVisibleRect(visible) && visible.contains(x, y))
            screen = intArrayOf(x, y)
        }
        val coordinates = checkNotNull(screen)
        assertTrue("Visible EPUB content tap was not dispatched", UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(coordinates[0], coordinates[1]))
    }

    private fun selectDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val watcherName = "reader-fresh-epub-picker-open-consent"
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
        throw AssertionError("Exact fresh EPUB document title absent after two bounded picker scrolls")
    }

    companion object {
        private val DOM_TAP = """
            (()=>{try{
              const f=document.getElementById('publication'),d=f?.contentDocument,w=f?.contentWindow;
              if(!d?.body||!w)return {probeError:'NO_CURRENT_DOCUMENT'};
              const usable=q=>q.width>0&&q.height>0&&q.bottom>32&&q.top<w.innerHeight-52&&q.right>0&&q.left<w.innerWidth;
              const eligible=e=>e&&!e.closest('a[href],script,style,noscript,template')&&w.getComputedStyle(e).visibility!=='hidden';
              const walker=d.createTreeWalker(d.body,NodeFilter.SHOW_TEXT);let n,tap=null;
              while(n=walker.nextNode()){
                const e=n.parentElement;if(!eligible(e))continue;const i=n.data.search(/\S/u);if(i<0)continue;
                const r=d.createRange();r.setStart(n,i);r.setEnd(n,i+1);const q=r.getBoundingClientRect();if(usable(q)){tap={left:q.left,right:q.right,top:q.top,bottom:q.bottom};break;}
              }
              if(!tap)return {probeError:'NO_VISIBLE_NON_LINK_TEXT'};const fr=f.getBoundingClientRect();
              return {frameLeft:fr.left,frameTop:fr.top,width:w.innerWidth,height:w.innerHeight,tap};
            }catch(e){return {probeError:String(e)}}})()
        """.trimIndent()
    }

    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step); details.putString("readerMethod", currentMethod)
        InstrumentationRegistry.getInstrumentation().sendStatus(2, details)
    }
}
