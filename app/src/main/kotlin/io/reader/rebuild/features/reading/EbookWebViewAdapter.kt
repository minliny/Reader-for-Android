package io.reader.rebuild.features.reading

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.reader.rebuild.host.storage.AuthorizedArchive
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One private shell origin, one native message slot, one currently authorized publication. */
@SuppressLint("SetJavaScriptEnabled")
internal class EbookWebViewAdapter(private val view: WebView, private val publish: (SurfaceEvent) -> Unit) :
    ReadingSurface<EbookChapter> {
    private val shell = WebViewAssetLoader.Builder().setDomain(HOST)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(view.context)).build()
    private val session = UUID.randomUUID().toString()
    @Volatile private var publication: Publication? = null
    private var loaded = false
    private var destroyed = false
    private var pending: (() -> Unit)? = null
    private var ready = false
    private var queuedStyle: Pair<ReadingStyle, Long?>? = null

    init {
        onMain()
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
        with(view.settings) {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        WebViewCompat.addWebMessageListener(view, MESSAGE, setOf(ORIGIN)) { _, message, origin, main, _ ->
            if (main && trusted(origin) && view.url == SHELL) {
                val bytes = message.data
                if (bytes != null && bytes.length <= 64 * 1024) {
                    val event = runCatching { WEB.decodeFromString<SurfaceEvent>(bytes) }.getOrNull()
                    val current = publication
                    if (event != null && current != null && event.version == 1 && event.session == session &&
                        event.documentId == current.id && event.layoutGeneration == current.generation && event.kind in kinds) {
                        if (event.kind == "ready") {
                            ready = true
                            val next = queuedStyle
                            queuedStyle = null
                            if (next != null) {
                                reflow(next.first, next.second)
                                return@addWebMessageListener
                            }
                        }
                        if (event.kind == "failure") queuedStyle = null
                        publish(event)
                    }
                }
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                if (request.isForMainFrame) request.url.toString() != SHELL
                else request.url.toString() !in setOf("about:srcdoc", "about:blank")

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!request.isForMainFrame && request.url.toString() in setOf("about:srcdoc", "about:blank")) return null
                if (request.method != "GET" || !trusted(request.url)) return blocked()
                if (request.isForMainFrame) {
                    if (request.url.toString() != SHELL) return blocked()
                    return shell.shouldInterceptRequest(request.url)?.apply {
                        responseHeaders = mapOf("Content-Security-Policy" to SHELL_CSP,
                            "X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store")
                    } ?: blocked()
                }
                return asset(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (destroyed || loaded || url != SHELL || view.url != SHELL) return
                val scripts = listOf("reader-ebook-dom.js", "reader-ebook-runtime.js").joinToString("\n") { name ->
                    view.context.assets.open("reader_ebook_runtime/$name").bufferedReader().use { it.readText() }
                }
                view.evaluateJavascript("$scripts\nBoolean(window.ReaderEbookRuntime)") { result ->
                    if (!destroyed && view.url == SHELL && result == "true") {
                        loaded = true
                        pending?.also { pending = null }?.invoke()
                    }
                }
            }
        }
        view.loadUrl(SHELL)
    }

    override fun open(chapter: EbookChapter, anchor: Long, style: ReadingStyle) {
        onMain()
        check(!destroyed)
        require(anchor in 0 until chapter.text.scalarCount)
        retire()
        val descriptor = chapter.descriptor
        val current = Publication(UUID.randomUUID().toString(), 0, chapter.archive)
        publication = current
        val config = WEB.encodeToString(Configuration(session, current.id, 0, descriptor.entryPath,
            chapter.xhtml, descriptor.documentCanonicalContent, descriptor.chapterStartScalar, descriptor.chapterEndScalar,
            chapter.text.content.content, chapter.text.scalarCount, anchor, style))
        val prefix = WEB.encodeToString("$ORIGIN/book/$session/${current.id}/")
        val execute = {
            script("open($config, {resourceCSPSource:'$ORIGIN', " +
                "resourceURL:path=>$prefix+path.split('/').map(encodeURIComponent).join('/'), " +
                "postMessage:event=>window.$MESSAGE.postMessage(JSON.stringify(event))})")
        }
        if (loaded) execute() else pending = execute
    }

    override fun reflow(style: ReadingStyle, anchor: Long?) {
        onMain()
        val current = publication ?: return
        if (!ready) {
            queuedStyle = style to anchor
            return
        }
        val next = current.copy(generation = current.generation + 1)
        publication = next
        ready = false
        script("reflow('$session','${next.id}',${next.generation},${WEB.encodeToString(style)},${anchor ?: "undefined"})")
    }

    override fun restore(scalar: Long, completion: (ReadingPosition?) -> Unit) = query("restore($scalar)", completion)
    override fun currentPosition(completion: (ReadingPosition?) -> Unit) = query("position()", completion)
    override fun select(start: Long, end: Long, completion: (ReadingSelection?) -> Unit) = query("select($start,$end)", completion)
    override fun currentSelection(completion: (ReadingSelection?) -> Unit) = query("selection()", completion)

    private inline fun <reified T> query(call: String, crossinline completion: (T?) -> Unit) {
        onMain()
        val current = publication
        if (!loaded || current == null || view.url != SHELL) return completion(null)
        view.evaluateJavascript("window.ReaderEbookRuntime.$call") { result ->
            val decoded = if (publication == current && view.url == SHELL) {
                runCatching { WEB.decodeFromString<T?>(result ?: "null") }.getOrNull()
            } else null
            completion(decoded)
        }
    }

    override fun retire() {
        onMain()
        pending = null
        ready = false
        queuedStyle = null
        val retired = publication
        publication = null
        if (retired != null && loaded) script("retire('$session','${retired.id}')")
    }

    fun destroy() {
        onMain()
        if (destroyed) return
        retire()
        destroyed = true
        WebViewCompat.removeWebMessageListener(view, MESSAGE)
        view.stopLoading()
        view.destroy()
    }

    private fun script(call: String) {
        if (view.url == SHELL) view.evaluateJavascript("window.ReaderEbookRuntime.$call; void 0", null)
    }

    private fun asset(uri: Uri): WebResourceResponse {
        val active = publication ?: return blocked()
        val segments = uri.pathSegments
        if (uri.query != null || uri.fragment != null || segments.size < 4 ||
            segments.take(3) != listOf("book", session, active.id) || uri.encodedPath.orEmpty().contains("%2f", true)) return blocked()
        val entry = segments.drop(3).joinToString("/")
        val mime = mimeTypes[entry.substringAfterLast('.', "").lowercase()] ?: return blocked()
        val result = runCatching { active.archive.read(entry, 16L * 1024 * 1024) }.getOrNull() ?: return blocked()
        if (publication?.id != active.id) return blocked()
        return WebResourceResponse(mime, if (mime == "text/css") "UTF-8" else null, 200, "OK",
            mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"), ByteArrayInputStream(result))
    }

    private data class Publication(val id: String, val generation: Long, val archive: AuthorizedArchive)

    @Serializable
    private data class Configuration(
        val session: String, val documentId: String, val layoutGeneration: Long, val entryPath: String,
        val xhtml: String, val documentCanonicalContent: String, val chapterStartScalar: Long, val chapterEndScalar: Long,
        val content: String, val scalarCount: Long, val anchor: Long, val style: ReadingStyle,
    )

    companion object {
        private const val HOST = "reader-ebook.invalid"
        private const val ORIGIN = "https://$HOST"
        private const val SHELL = "$ORIGIN/assets/reader_ebook_runtime/shell.html"
        private const val MESSAGE = "ReaderNativePort"
        private const val SHELL_CSP = "default-src 'none'; style-src 'unsafe-inline' $ORIGIN; img-src $ORIGIN; font-src $ORIGIN; frame-src about:; base-uri 'none'; form-action 'none'"
        private val WEB = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val kinds = setOf("ready", "position", "selection", "link", "failure")
        private val mimeTypes = mapOf("png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
            "gif" to "image/gif", "webp" to "image/webp", "avif" to "image/avif", "svg" to "image/svg+xml",
            "css" to "text/css", "woff" to "font/woff", "woff2" to "font/woff2", "otf" to "font/otf", "ttf" to "font/ttf")

        private fun onMain() = check(Looper.myLooper() == Looper.getMainLooper())
        private fun trusted(uri: Uri) = uri.scheme == "https" && uri.host == HOST && uri.userInfo == null && uri.port == -1
        private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
    }
}
