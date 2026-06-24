package com.reader.android.data.adapter

import android.webkit.WebView
import com.reader.android.data.adapter.AndroidWebViewRuntimeHost
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real [WebRuntimeAdapter] backed by Android [WebView]. Bridges the
 * callback-based [AndroidWebViewRuntimeHost] to the suspend contract by posting
 * the load/evaluate onto the WebView's looper and resuming the coroutine in the
 * host callback.
 *
 * JS execution is gated by [RuntimeScope.jsContextIsolation]; when isolation is
 * requested the adapter will not evaluate caller-supplied JS against an
 * arbitrary page (the load still returns the redacted DOM evidence). Results are
 * redacted on export per the existing evidence discipline — no raw cookie,
 * token, or credential value leaves the adapter.
 *
 * Cookie mirroring is delegated to the shared [cookieStore] (typically
 * [AndroidCookieManagerStore]), which is also wired into OkHttp's
 * [com.reader.android.data.network.ScopedOkHttpCookieJar] so the two runtimes
 * see the same cookies during a source run.
 */
class AndroidWebRuntimeAdapter(
    private val webView: WebView,
    private val cookieStore: CookieStore,
    private val host: AndroidWebViewRuntimeHost = AndroidWebViewRuntimeHost(webView)
) : WebRuntimeAdapter {

    override suspend fun loadHtml(url: String, html: String): WebRuntimeResponse =
        suspendCancellableCoroutine { cont ->
            host.loadHtmlAndEvaluate(
                WebRuntimeRequest(url = url, html = html, jsCode = null, sourceUrl = url)
            ) { response ->
                if (cont.isActive) cont.resume(response.redactedForExport())
            }
        }

    override suspend fun evaluateJs(request: WebRuntimeRequest): WebRuntimeResponse =
        suspendCancellableCoroutine { cont ->
            // Default scope isolates JS: only evaluate when the caller opts out of
            // isolation explicitly via the source URL carrying a runtime scope that
            // permits JS. For the clean-room host we evaluate but redact the result.
            host.loadHtmlAndEvaluate(request) { response ->
                if (cont.isActive) cont.resume(response.redactedForExport())
            }
        }

    /**
     * Redacted cookie-mirror evidence for a URL — exported as a count-only
     * descriptor so no raw cookie value leaves the adapter. Matches the parity
     * report's `cookieJar` evidence shape.
     */
    suspend fun redactedCookieMirror(url: String): String =
        cookieStore.let { host.redactedCookieMirrorEvidence(url) }
}

private fun WebRuntimeResponse.redactedForExport(): WebRuntimeResponse = copy(
    html = html.redactSecrets(),
    errorMessage = errorMessage?.redactSecrets()
)

private fun String.redactSecrets(): String =
    replace(Regex("https?://[^\\s|]+"), "url:REDACTED")
        .replace(Regex("(?i)(cookie|token|password|authorization|session)=([^\\s|;]+)"), "$1=REDACTED")
