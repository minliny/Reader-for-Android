package io.reader.android

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.reader.android.ui.ReaderEbookUiMount
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Observes the mounted native ebook surface. Each journey supplies its own activity and input. */
internal object EbookJourneyProbe {
    /** Read-only DOM extraction shared by every ebook journey. */
    val DOM_READ_ONLY = """
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

    /** Read-only collapsed-selection swipe anchor shared by the narrative journeys. */
    val SWIPE_READ_ONLY = """
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

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    fun webView(activity: MainActivity, shell: String): WebView {
        fun descendants(view: View): List<WebView> = (if (view is WebView) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList())
        return descendants(activity.window.decorView).filter { it.isAttachedToWindow && it.url == shell }.single()
    }

    fun readyMount(activity: MainActivity, shell: String): ReaderEbookUiMount? {
        val model = activity.readerViewModel
        val reading = model.state.value.reading ?: return null
        val mount = field(model, "ebookMount") as? ReaderEbookUiMount ?: return null
        val prepared = mount.preparation ?: return null
        val ready = mount.ready ?: return null
        if (reading.identity != mount.readingOwner || reading.chapter.sessionToken != mount.chapter.sessionToken ||
            reading.chapter.bookId != mount.chapter.bookId || reading.chapter.index != mount.chapter.index ||
            reading.chapter.bodyVersion != mount.chapter.bodyVersion || reading.chapter.processingVersion != mount.chapter.processingVersion ||
            prepared.view !== mount.view || prepared.publication.lease != ready.lease || ready.scalar != reading.visibleScalarOffset) return null
        prepared.requireCurrent()
        val web = try {
            webView(activity, shell)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: NoSuchElementException) {
            return null
        }
        val owner = field(web.parent, "owner") ?: return null
        val layout = field(owner, "layout") ?: return null
        val visible = Rect()
        return mount.takeIf { field(owner, "view") === web && field(owner, "lease") == prepared.publication.lease &&
            field(owner, "phase").toString() == "Ready" && field(layout, "anchor") == ready.scalar &&
            web.width == ready.widthPx && web.height == ready.heightPx && field(owner, "forceLayout") == false &&
            field(owner, "drawnEpoch") == ready.layoutEpoch && field(owner, "awaitingDraw") == null &&
            field(layout, "epoch") == ready.layoutEpoch && field(layout, "token") == ready.layoutToken &&
            web.isLaidOut && !web.isLayoutRequested && web.isShown && web.getGlobalVisibleRect(visible) &&
            visible.width() == web.width && visible.height() == web.height }
    }

    fun awaitDraw(compose: ComposeTestRule, view: View) {
        val latch = CountDownLatch(1)
        var tree: ViewTreeObserver? = null
        val listener = ViewTreeObserver.OnDrawListener {
            if (view.isAttachedToWindow && view.isShown) view.post { latch.countDown() }
        }
        var primary: Throwable? = null
        try {
            compose.runOnUiThread {
                assertTrue(view.isAttachedToWindow && view.isShown)
                tree = view.viewTreeObserver
                tree!!.addOnDrawListener(listener)
                view.invalidate()
            }
            assertTrue("Attached native draw did not complete", latch.await(5, TimeUnit.SECONDS))
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                compose.runOnUiThread { tree?.takeIf { it.isAlive }?.removeOnDrawListener(listener) }
            } catch (cleanup: Throwable) {
                if (primary == null) throw cleanup
                primary.addSuppressed(cleanup)
            }
        }
    }

    fun glyphTapCoordinates(compose: ComposeTestRule, web: WebView, probe: JSONObject): IntArray = compose.runOnUiThread {
        val location = IntArray(2).also(web::getLocationOnScreen)
        val visible = Rect()
        assertTrue(web.getGlobalVisibleRect(visible))
        assertEquals(Rect(location[0], location[1], location[0] + web.width, location[1] + web.height), visible)
        val density = web.resources.displayMetrics.density.toDouble()
        assertTrue(abs(probe.getDouble("width") * density - web.width) <= 1.1 * density)
        assertTrue(abs(probe.getDouble("height") * density - web.height) <= 1.1 * density)
        val tap = probe.getJSONObject("tap") // Actual Range/image rectangle, not an assumed screen coordinate.
        val frameLeft = probe.getDouble("frameLeft")
        val frameTop = probe.getDouble("frameTop")
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

    fun dom(compose: ComposeTestRule, web: WebView, readOnlyScript: String): JSONObject {
        val latch = CountDownLatch(1)
        var result: String? = null
        compose.runOnUiThread {
            web.evaluateJavascript(readOnlyScript) {
                result = it
                latch.countDown()
            }
        }
        assertTrue("Read-only DOM callback missing", latch.await(5, TimeUnit.SECONDS))
        return JSONObject(result ?: throw AssertionError("DOM callback returned null"))
    }

    fun completedOperationNames(activity: MainActivity): Set<String> =
        File(activity.filesDir, "Reader/Operations/Completed").listFiles().orEmpty()
            .filter { it.name.endsWith(".json") }
            .mapTo(linkedSetOf()) { it.name }
}
