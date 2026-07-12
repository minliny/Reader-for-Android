package com.reader.host

import android.app.Activity
import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * Lifecycle seam for the UI-contract `webview.open / close / evaluate`
 * capabilities. The Host facade depends on this interface so JVM tests can
 * prove behavior without constructing Android framework objects.
 */
interface UiWebViewSession {
    /** Returns true only when an active WebView Activity was asked to finish. */
    fun close(): Boolean

    /** Evaluates [script] against [url] in the active user-visible WebView. */
    suspend fun evaluate(
        url: String,
        script: String,
        timeoutMillis: Long?
    ): WebViewEvaluationResult
}

/** Fail-closed default used whenever no Activity-tier session is wired. */
object UnavailableUiWebViewSession : UiWebViewSession {
    override fun close(): Boolean = false

    override suspend fun evaluate(
        url: String,
        script: String,
        timeoutMillis: Long?
    ): WebViewEvaluationResult = throw WebViewExecutorError.RequiresUiContext(
        "no active UI WebView session"
    )
}

/**
 * Process-local registry for the single user-visible [com.reader.WebViewHostActivity].
 * Weak references avoid retaining a destroyed Activity or WebView. Every
 * operation either reaches the real lifecycle/render surface or fails closed;
 * it never acknowledges work that was not performed.
 */
object AndroidUiWebViewSession : UiWebViewSession {
    private val lock = Any()

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var webViewRef: WeakReference<WebView>? = null

    fun attach(activity: Activity, webView: WebView) {
        synchronized(lock) {
            activityRef = WeakReference(activity)
            webViewRef = WeakReference(webView)
        }
    }

    fun detach(activity: Activity) {
        synchronized(lock) {
            if (activityRef?.get() === activity) {
                activityRef = null
                webViewRef = null
            }
        }
    }

    override fun close(): Boolean {
        val activity = activityRef?.get() ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) activity.finish()
        }
        return true
    }

    override suspend fun evaluate(
        url: String,
        script: String,
        timeoutMillis: Long?
    ): WebViewEvaluationResult {
        val webView = webViewRef?.get()
            ?: throw WebViewExecutorError.RequiresUiContext("visible WebView is not attached")
        return AndroidWebViewExecutor(webView).evaluate(
            WebViewEvaluationRequest(
                documentKind = "url",
                body = null,
                url = url,
                baseUrl = null,
                javaScript = script,
                timeoutMillis = timeoutMillis,
                profileId = null
            )
        )
    }
}
