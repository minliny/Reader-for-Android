package io.reader.android.ui.ebookdraft

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.reader.android.runtime.ebookdraft.EbookLeaseGuard
import io.reader.android.runtime.ebookdraft.ReaderEbookWebViewBoundary
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** One physical WebView and one prepared render capability. All UI callbacks retain this exact lease. */
internal class ReaderEbookWebViewOwner(context: Context, private val original: ReaderEbookRenderInput,
    private val guard: EbookLeaseGuard, private val phaseChanged: (ReaderEbookPhase) -> Unit,
    private val onReady: (ReaderEbookReady) -> Unit, private val onVisible: (ReaderEbookVisibleAnchor) -> Unit,
    private val onFailure: (ReaderEbookRenderFailure) -> Unit,
    private val onBodyTap: (ReaderEbookBodyTap) -> Unit,
    private val onSelectionChanged: (ReaderEbookSelectionState) -> Unit) {
    val view = WebView(context)
    private val lease = original.prepared.lease
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val resourceFailure = AtomicReference<Exception?>()
    @Volatile private var terminal = false
    @Volatile private var layout: ReaderEbookLayoutSnapshot? = null
    private lateinit var resources: ReaderEbookLocalResources
    private var input = original
    private var desiredAnchor = original.currentAnchor
    private var lastVisible: Int? = null
    private var selectionActive = false
    private var epoch = 0L
    private var shellLoaded = false
    private var shellStarts = 0
    private var queued = false
    private var forceLayout = true
    private var phase = ReaderEbookPhase.Restoring
    private var awaitingDraw: ReaderEbookLayoutSnapshot? = null
    private var drawnEpoch = -1L
    private var drawObserver: ViewTreeObserver? = null
    private val deadline = Runnable { fail("layout-deadline", IllegalStateException("EBOOK_LAYOUT_NOT_READY_WITHIN_20S")) }
    private val draw = ViewTreeObserver.OnDrawListener {
        val captured = awaitingDraw
        if (captured != null && layout === captured && phase == ReaderEbookPhase.Restoring) {
            awaitingDraw = null
            view.post {
                if (live() && !forceLayout && layout === captured && nativeViewport(captured)) {
                    drawnEpoch = captured.epoch
                    script("probe", captured)
                }
            }
        }
    }
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { installDraw(); requestLayout(false) }
        override fun onViewDetachedFromWindow(v: View) { removeDraw() }
    }
    private val resized = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val captured = layout
        if (captured == null || captured.widthPx != view.width || captured.heightPx != view.height) requestLayout(true)
    }

    fun start() {
        checkMain(); guard.requireCurrent(lease)
        ReaderEbookWebViewBoundary.hardenTrustedShell(view)
        view.settings.apply { textZoom = 100; setSupportZoom(false); builtInZoomControls = false; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = false }
        resources = ReaderEbookLocalResources(view.context, original, { layout }, { requireLive() }) { error ->
            resourceFailure.compareAndSet(null, error)
            main.post { fail("publication-resource", resourceFailure.get() ?: error) } // No UI/gate wait on IO.
        }
        view.webViewClient = if (Build.VERSION.SDK_INT >= 26) ModernClient() else Client()
        view.setDownloadListener { _, _, _, _, _ -> fail("download", IllegalStateException("DOWNLOAD_NOT_AUTHORIZED")) }
        ReaderEbookWebViewBoundary.installMessages(view, lease, ReaderEbookLocalResources.SHELL_URL,
            EbookLeaseGuard { candidate -> check(candidate == lease); requireLive() }, { if (forceLayout) null else layout?.token }) { message ->
            try { receive(message) } catch (error: Exception) { fail("shell-message", error) }
        }
        view.addOnAttachStateChangeListener(attach); view.addOnLayoutChangeListener(resized)
        if (view.isAttachedToWindow) installDraw()
        main.postDelayed(deadline, 20_000); requestLayout(true)
    }

    fun bind(next: ReaderEbookRenderInput) {
        checkMain(); if (!live()) return
        try {
            require(next.prepared === original.prepared && next.prepared.lease == lease && next.chapter.text == original.chapter.text) { "FOREIGN_RENDER_INPUT" }
            val externalAnchor = next.currentAnchor != input.currentAnchor && next.currentAnchor != lastVisible
            val changed = next.paper != input.paper || next.fontSizeSp != input.fontSizeSp || next.geometry != input.geometry || externalAnchor
            if (externalAnchor) desiredAnchor = next.currentAnchor
            else if (changed) desiredAnchor = lastVisible ?: next.currentAnchor
            val policyChanged = next.userScrollEnabled != input.userScrollEnabled
            input = next
            if (changed) requestLayout(true)
            else if (policyChanged) layout?.let { scrollPolicy(it) }
        } catch (error: Exception) { fail("bind", error) }
    }

    private fun requestLayout(force: Boolean) {
        checkMain(); if (!live()) return
        forceLayout = forceLayout || force
        if (force) {
            awaitingDraw = null; drawnEpoch = -1
            setPhase(ReaderEbookPhase.Restoring) // Revoke old admission before posted beginLayout can run.
        }
        if (queued) return
        queued = true
        view.post { queued = false; beginLayout() }
    }
    private fun beginLayout() {
        checkMain(); if (!live() || !view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        val old = layout
        if (!forceLayout && old != null && old.widthPx == view.width && old.heightPx == view.height) return
        try {
            check(epoch < 9_007_199_254_740_991L) { "LAYOUT_EPOCH_EXHAUSTED" }
            val captured = ReaderEbookLocalResources.snapshot(input, ++epoch, UUID.randomUUID().toString(), desiredAnchor, view.width, view.height)
            layout = captured; forceLayout = false; awaitingDraw = null; drawnEpoch = -1
            setPhase(ReaderEbookPhase.Restoring); main.removeCallbacks(deadline); main.postDelayed(deadline, 20_000)
            if (!shellLoaded) { shellLoaded = true; view.loadUrl(ReaderEbookLocalResources.SHELL_URL) }
            else script("configure", captured)
        } catch (error: Exception) { fail("layout", error) }
    }

    private fun receive(message: ReaderEbookWebViewBoundary.Message) {
        checkMain(); if (!live() || message.lease != lease) return
        if (forceLayout) return
        val captured = layout ?: return
        val value = JSONObject(message.valueJson)
        if (message.layoutToken != captured.token || exact(value, "layoutEpoch") != captured.epoch) return
        when (message.kind) {
            "failure" -> fail("trusted-dom", IllegalStateException(value.getString("code")))
            "ready" -> when (value.getString("phase")) {
                "invalidated" -> if (phase == ReaderEbookPhase.Ready) { desiredAnchor = lastVisible ?: input.currentAnchor; requestLayout(true) }
                "dom" -> {
                    checkMeasurement(value, captured, captured.anchor)
                    view.postVisualStateCallback(captured.epoch, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (live() && !forceLayout && requestId == captured.epoch && layout === captured && nativeViewport(captured)) {
                                awaitingDraw = captured; view.invalidate()
                            }
                        }
                    })
                }
                "paint" -> {
                    check(phase == ReaderEbookPhase.Restoring && drawnEpoch == captured.epoch && nativeViewport(captured))
                    checkMeasurement(value, captured, captured.anchor)
                    main.removeCallbacks(deadline); setPhase(ReaderEbookPhase.Ready)
                    onReady(ReaderEbookReady(lease, captured.epoch, captured.token, captured.anchor, captured.widthPx, captured.heightPx))
                    if (live() && layout === captured) script("confirm", captured)
                }
                else -> error("UNKNOWN_READY_PHASE")
            }
            "position" -> {
                if (phase != ReaderEbookPhase.Ready || selectionActive || !input.userScrollEnabled || !nativeViewport(captured)) return
                val valueScalar = exact(value, "scalar")
                require(valueScalar <= original.chapter.scalarCount.toLong())
                val scalar = valueScalar.toInt()
                checkMeasurement(value, captured, scalar)
                if (lastVisible != scalar) {
                    lastVisible = scalar; desiredAnchor = scalar
                    onVisible(ReaderEbookVisibleAnchor(lease, captured.epoch, captured.token, scalar))
                }
            }
            "selection" -> {
                if (phase != ReaderEbookPhase.Ready || !nativeViewport(captured)) return
                selectionActive = value.get("active") as? Boolean ?: error("SELECTION_BOOLEAN_REQUIRED")
                onSelectionChanged(ReaderEbookSelectionState(lease, captured.epoch, captured.token, selectionActive))
            }
            "tap" -> {
                if (phase != ReaderEbookPhase.Ready || selectionActive || !nativeViewport(captured)) return
                onBodyTap(ReaderEbookBodyTap(lease, captured.epoch, captured.token))
            }
            else -> error("UNKNOWN_SHELL_KIND")
        }
    }

    private fun checkMeasurement(value: JSONObject, captured: ReaderEbookLayoutSnapshot, expectedScalar: Int) {
        check(exact(value, "scalar") == expectedScalar.toLong())
        val rect = value.getJSONObject("rect")
        fun number(key: String): Double = (rect.get(key) as? Number ?: error("RECT_NUMBER_REQUIRED")).toDouble().also { check(it.isFinite()) }
        val left = number("left"); val right = number("right"); val top = number("top"); val bottom = number("bottom")
        val width = number("viewportWidth"); val height = number("viewportHeight"); val g = captured.input.geometry
        check(right > left && bottom > top && number("width") > 0 && number("height") > 0)
        check(abs(width - captured.widthPx / g.density.toDouble()) <= 1.1 && abs(height - captured.heightPx / g.density.toDouble()) <= 1.1)
        check(bottom > g.topPx / g.density && top < height - g.bottomPx / g.density && right > 0 && left < width) { "ANCHOR_NOT_IN_REAL_VIEWPORT" }
    }
    private fun nativeViewport(captured: ReaderEbookLayoutSnapshot): Boolean {
        val visible = Rect()
        return view.isAttachedToWindow && view.isShown && view.isLaidOut && !view.isLayoutRequested &&
            view.width == captured.widthPx && view.height == captured.heightPx && view.scrollX == 0 && view.scrollY == 0 &&
            abs(view.resources.displayMetrics.density - captured.input.geometry.density) < 0.01f &&
            view.getGlobalVisibleRect(visible) && visible.width() == view.width && visible.height() == view.height
    }
    private fun script(method: String, captured: ReaderEbookLayoutSnapshot) {
        if (!live() || layout !== captured || view.url != ReaderEbookLocalResources.SHELL_URL) return
        val policy = if (method == "confirm") ",${input.userScrollEnabled}" else ""
        view.evaluateJavascript("window.ReaderEbookRuntime?.$method(${captured.epoch},${JSONObject.quote(captured.token)}$policy)", null)
    }

    private fun scrollPolicy(captured: ReaderEbookLayoutSnapshot) {
        if (!live() || forceLayout || layout !== captured || view.url != ReaderEbookLocalResources.SHELL_URL) return
        view.evaluateJavascript("window.ReaderEbookRuntime?.setScrollEnabled(${captured.epoch},${JSONObject.quote(captured.token)},${input.userScrollEnabled})", null)
    }

    private open inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = resources.intercept(request)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame || request.url.toString() != resources.entryUrl
        override fun onPageStarted(view: WebView, url: String, icon: android.graphics.Bitmap?) {
            if (url != ReaderEbookLocalResources.SHELL_URL || ++shellStarts != 1) fail("navigation", IllegalStateException("MAIN_NAVIGATION_DENIED"))
        }
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (resources.activeResource(request.url)) fail("web-resource", resourceFailure.get() ?: IllegalStateException("${error.errorCode}:${error.description}"))
        }
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (resources.activeResource(request.url)) fail("web-http", resourceFailure.get() ?: IllegalStateException("RESOURCE_HTTP_${response.statusCode}"))
        }
    }
    @android.annotation.TargetApi(26)
    private inner class ModernClient : Client() {
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            fail("renderer-process", IllegalStateException("WEBVIEW_RENDERER_GONE:${detail.didCrash()}")); return true
        }
    }

    private fun live(): Boolean {
        if (closed.get() || terminal) return false
        return try { guard.requireCurrent(lease); true } catch (_: IllegalStateException) { false }
    }
    private fun requireLive() { check(live()) { "RETIRED_WEBVIEW_OWNER" } }
    private fun fail(stage: String, error: Exception) {
        checkMain(); if (!live()) return
        terminal = true; main.removeCallbacks(deadline); awaitingDraw = null; original.prepared.retire()
        if (view.url == ReaderEbookLocalResources.SHELL_URL) view.evaluateJavascript("window.ReaderEbookRuntime?.retire()", null)
        view.stopLoading()
        setPhase(ReaderEbookPhase.Failed)
        onFailure(ReaderEbookRenderFailure(lease, layout?.epoch ?: 0, layout?.token, stage, error))
    }
    private fun setPhase(next: ReaderEbookPhase) { if (phase != next) { phase = next; phaseChanged(next) } }
    private fun installDraw() { removeDraw(); view.viewTreeObserver.also { drawObserver = it; it.addOnDrawListener(draw) } }
    private fun removeDraw() { drawObserver?.let { if (it.isAlive) it.removeOnDrawListener(draw) }; drawObserver = null }
    fun close() {
        checkMain(); if (!closed.compareAndSet(false, true)) return
        original.prepared.retire(); main.removeCallbacks(deadline); awaitingDraw = null; removeDraw()
        view.removeOnAttachStateChangeListener(attach); view.removeOnLayoutChangeListener(resized)
        ReaderEbookWebViewBoundary.removeMessages(view); view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view); view.destroy()
    }
    companion object {
        private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) }
        private fun exact(value: JSONObject, key: String): Long = when (val number = value.get(key)) {
            is Int -> number.toLong(); is Long -> number; else -> error("EXACT_INTEGER_REQUIRED:$key")
        }.also { require(it in 0..9_007_199_254_740_991L) }
    }
}
