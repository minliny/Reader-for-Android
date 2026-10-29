package io.reader.android.runtime.ebookdraft

import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Trusted shell boundary; publication content retains its separate script-disabled sandbox. */
internal object ReaderEbookWebViewBoundary {
    private const val ORIGIN = ReaderEbookAssetProvider.ORIGIN
    private const val HOST = ReaderEbookAssetProvider.ORIGIN_HOST
    const val PUBLICATION_CSP = "sandbox allow-same-origin; default-src 'none'; script-src 'none'; " +
        "style-src 'self' 'unsafe-inline'; img-src 'self'; font-src 'self'; connect-src 'none'; " +
        "frame-src 'none'; object-src 'none'; media-src 'none'; base-uri 'none'; form-action 'none'"

    fun hardenTrustedShell(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true // Trusted shell only; publication iframe has sandbox/CSP without allow-scripts.
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            domStorageEnabled = false
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
        }
        // No addJavascriptInterface, file://, content://, generic picker URL or network fallback.
    }

    fun denied(status: Int = 403): WebResourceResponse = WebResourceResponse(
        "text/plain", "UTF-8", status, if (status == 404) "Not Found" else "Forbidden",
        mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
        ByteArrayInputStream(ByteArray(0)),
    )

    data class Message(val lease: EbookRenderLease, val sequence: Long, val kind: String,
        val layoutToken: String, val valueJson: String)

    /** Owner validates kind-specific geometry/scalars before changing UI or persisting any progress. */
    fun installMessages(view: WebView, lease: EbookRenderLease, trustedShellUrl: String,
        guard: EbookLeaseGuard, expectedLayoutToken: () -> String?, consume: (Message) -> Unit) {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) { "WEB_MESSAGE_LISTENER_REQUIRED" }
        val shell = Uri.parse(trustedShellUrl)
        require(shell.scheme == "https" && shell.encodedAuthority == HOST && shell.encodedPath == "/__reader/shell.html"
            && shell.encodedQuery == null && shell.encodedFragment == null)
        var lastSequence = 0L
        WebViewCompat.addWebMessageListener(view, "ReaderEbookHost", setOf(ORIGIN),
            WebViewCompat.WebMessageListener { actualView, message, sourceOrigin, isMainFrame, _ ->
                if (!isMainFrame || actualView !== view || actualView.url != trustedShellUrl || sourceOrigin.scheme != "https"
                    || sourceOrigin.encodedAuthority != HOST) return@WebMessageListener
                // Retired traffic is ignored; it must not touch a new frame, UI owner or operation.
                try { guard.requireCurrent(lease) } catch (_: IllegalStateException) { return@WebMessageListener }
                val raw = message.data ?: return@WebMessageListener
                if (raw.length > 8192) return@WebMessageListener
                val event = try { JSONObject(raw) } catch (_: Exception) { return@WebMessageListener }
                if (event.keys().asSequence().toSet() != setOf("protocolVersion", "session", "sequence", "kind", "value")) return@WebMessageListener
                if (event.opt("protocolVersion") != 1 || event.opt("session") != lease.renderId.toString()) return@WebMessageListener
                val sequence = when (val value = event.opt("sequence")) {
                    is Int -> value.toLong()
                    is Long -> value
                    else -> return@WebMessageListener
                }
                val kind = event.opt("kind") as? String ?: return@WebMessageListener
                val value = event.opt("value") as? JSONObject ?: return@WebMessageListener
                val token = value.opt("layoutToken") as? String ?: return@WebMessageListener
                if (sequence <= lastSequence || kind !in setOf("ready", "position", "selection", "tap", "failure") || token != expectedLayoutToken()) return@WebMessageListener
                lastSequence = sequence
                // The trusted wrapper supplies no file/book/path authority. Native lease supplies scope.
                consume(Message(lease, sequence, kind, token, value.toString()))
            })
    }

    fun removeMessages(view: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            WebViewCompat.removeWebMessageListener(view, "ReaderEbookHost")
    }
}
