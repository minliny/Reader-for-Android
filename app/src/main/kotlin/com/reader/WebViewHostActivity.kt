package com.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.reader.host.AndroidUiWebViewSession

/**
 * User-visible host for the UI-contract `webview.open` capability. It owns
 * the WebView lifecycle, registers the active session for `webview.evaluate`
 * and `webview.close`, and tears the renderer down when the Activity exits.
 */
class WebViewHostActivity : Activity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Instrumented renderer proofs launch the Activity directly without an
        // intent payload; keep a harmless local document for that harness.
        // Production `webview.open` always supplies a validated http(s) URL.
        val url = intent.getStringExtra(EXTRA_URL).takeUnless { it.isNullOrBlank() }
            ?: "about:blank"

        val host = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
        }
        webView = host
        setContentView(host)
        AndroidUiWebViewSession.attach(this, host)
        host.loadUrl(url)
    }

    override fun onDestroy() {
        AndroidUiWebViewSession.detach(this)
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
