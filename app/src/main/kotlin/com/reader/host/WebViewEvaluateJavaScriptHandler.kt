package com.reader.host

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * `webview.evaluateJavaScript` capability handler: bridges Core's WebView
 * render-lane requests to a host-owned [WebViewExecutor].
 *
 * **Proof tier**: handler/router — mirrors the `login_cookie` proof level
 * ([CookieGetHandler] / [CookieSetHandler]). Core sends a `host.request`
 * whose params match `HostWebViewEvaluateJavaScriptRequest` (see
 * `crates/reader-contract/src/host.rs` lines 679-820):
 * ```
 * { document: { kind: "html" | "url", body?, url?, baseUrl? },
 *   javaScript: string, timeoutMillis?: u64, profileId?: string }
 * ```
 * This handler validates the params, delegates execution to [WebViewExecutor],
 * and returns `host.complete` with `{ value, finalUrl?, title? }` matching
 * `HostWebViewEvaluateJavaScriptResponse`.
 *
 * **Activity-tier/App tier**: real `WebView` execution must be backed by an
 * Activity-attached [AndroidWebViewExecutor]. Headless tests deliberately fail
 * closed with `REQUIRES_UI_CONTEXT`; handler/router proof tests use
 * [StubWebViewExecutor].
 *
 * **Mirrors**: iOS/HarmonyOS proof structure for `webview.evaluateJavaScript`.
 *
 * Like [CookieGetHandler], this handler is non-suspend (implements
 * [CapabilityHandler]); it bridges to the suspend [WebViewExecutor] via
 * [runBlocking] so the existing host adapter dispatch path is unchanged.
 */
class WebViewEvaluateJavaScriptHandler(
    private val executor: WebViewExecutor
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(
                INTERNAL,
                "invalid $CAPABILITY params: ${e.message}",
                false
            )
        }

        // --- document (required object with kind "html" | "url") ---
        val document = params.optJSONObject("document")
        if (document == null) {
            return HostReply.error(
                INTERNAL,
                "$CAPABILITY requires document object",
                false
            )
        }
        val kind = document.optString("kind", "")
        if (kind != "html" && kind != "url") {
            return HostReply.error(
                INTERNAL,
                "$CAPABILITY document.kind must be 'html' or 'url'",
                false
            )
        }
        val body = document.optString("body", "")
        val url = document.optString("url", "")
        val baseUrl = document.optString("baseUrl", "")
        val hasBody = body.isNotBlank()
        val hasUrl = url.isNotBlank()
        val hasBaseUrl = baseUrl.isNotBlank()

        when (kind) {
            "html" -> {
                if (!hasBody) {
                    return HostReply.error(
                        INTERNAL,
                        "$CAPABILITY html document requires non-blank body",
                        false
                    )
                }
                if (hasUrl) {
                    return HostReply.error(
                        INTERNAL,
                        "$CAPABILITY html document must not include url",
                        false
                    )
                }
            }
            "url" -> {
                if (!hasUrl) {
                    return HostReply.error(
                        INTERNAL,
                        "$CAPABILITY url document requires non-blank url",
                        false
                    )
                }
                if (hasBody || hasBaseUrl) {
                    return HostReply.error(
                        INTERNAL,
                        "$CAPABILITY url document must not include body or baseUrl",
                        false
                    )
                }
            }
        }

        // --- javaScript (required non-blank) ---
        val javaScript = params.optString("javaScript", "")
        if (javaScript.isBlank()) {
            return HostReply.error(
                INTERNAL,
                "$CAPABILITY requires non-blank javaScript",
                false
            )
        }

        // --- timeoutMillis (optional, must be > 0 if present) ---
        val timeoutMillis: Long? =
            if (params.has("timeoutMillis") && !params.isNull("timeoutMillis")) {
                val t = params.optLong("timeoutMillis", 0)
                if (t <= 0) {
                    return HostReply.error(
                        INTERNAL,
                        "$CAPABILITY timeoutMillis must be greater than 0",
                        false
                    )
                }
                t
            } else {
                null
            }

        // --- profileId (optional, must be non-blank if present) ---
        val profileId: String? =
            if (params.has("profileId") && !params.isNull("profileId")) {
                val p = params.optString("profileId", "")
                if (p.isBlank()) {
                    return HostReply.error(
                        INTERNAL,
                        "$CAPABILITY profileId must be non-blank",
                        false
                    )
                }
                p
            } else {
                null
            }

        val evalRequest = WebViewEvaluationRequest(
            documentKind = kind,
            body = if (hasBody) body else null,
            url = if (hasUrl) url else null,
            baseUrl = if (hasBaseUrl) baseUrl else null,
            javaScript = javaScript,
            timeoutMillis = timeoutMillis,
            profileId = profileId
        )

        // --- delegate to executor (suspend) via runBlocking, mirroring CookieGetHandler ---
        val result: WebViewEvaluationResult
        try {
            result = runBlocking { executor.evaluate(evalRequest) }
        } catch (e: WebViewExecutorError) {
            return when (e) {
                is WebViewExecutorError.Timeout -> HostReply.error(
                    "TIMEOUT",
                    e.message ?: "$CAPABILITY timed out",
                    true
                )
                is WebViewExecutorError.NotImplemented -> HostReply.error(
                    "NOT_IMPLEMENTED",
                    e.message ?: "$CAPABILITY not implemented",
                    false
                )
                is WebViewExecutorError.ExecutionFailed -> HostReply.error(
                    "EXECUTION_FAILED",
                    e.message ?: "$CAPABILITY execution failed",
                    true
                )
                is WebViewExecutorError.RequiresUiContext -> HostReply.error(
                    "REQUIRES_UI_CONTEXT",
                    e.message ?: "$CAPABILITY requires UI context",
                    false
                )
            }
        } catch (e: Exception) {
            return HostReply.error(
                INTERNAL,
                "$CAPABILITY failed: ${e.message}",
                true
            )
        }

        val resultJson = JSONObject()
        // Core contract: `value` is required (serde_json::Value, any JSON).
        // Use JSONObject.NULL so an absent/null value serializes as `"value":null`
        // rather than dropping the key (which would break deny_unknown_fields).
        resultJson.put("value", result.value ?: JSONObject.NULL)
        if (result.finalUrl != null) {
            resultJson.put("finalUrl", result.finalUrl)
        }
        if (result.title != null) {
            resultJson.put("title", result.title)
        }
        return HostReply.complete(resultJson.toString())
    }

    companion object {
        const val CAPABILITY = "webview.evaluateJavaScript"
        private const val INTERNAL = "INTERNAL"
    }
}

/**
 * Host-owned executor for WebView JavaScript evaluation. Implementations:
 * - [StubWebViewExecutor] — canned results/errors for handler/router proof tests.
 * - [AndroidWebViewExecutor] — Android `WebView` executor that requires an
 *   Activity/UI context and fails closed with `REQUIRES_UI_CONTEXT` when run
 *   headlessly.
 *
 * `suspend` so production implementations can bridge to async WebView
 * callbacks (`WebView.evaluateJavascript` + `ValueCallback`, navigation
 * listeners) without blocking the host adapter thread.
 */
interface WebViewExecutor {
    suspend fun evaluate(request: WebViewEvaluationRequest): WebViewEvaluationResult
}

/**
 * Mirrors Core's `HostWebViewDocument` + `HostWebViewEvaluateJavaScriptRequest`
 * (see `crates/reader-contract/src/host.rs` lines 692-767). All fields are
 * pre-validated by [WebViewEvaluateJavaScriptHandler.handle] before reaching
 * the executor.
 *
 * @property documentKind `"html"` or `"url"` (Core's `HostWebViewDocumentKind`).
 * @property body HTML body when `documentKind = "html"`; null when `"url"`.
 * @property url Navigation URL when `documentKind = "url"`; null when `"html"`.
 * @property baseUrl Optional base URL when `documentKind = "html"`; null otherwise.
 * @property javaScript Non-blank JavaScript to evaluate after the document loads.
 * @property timeoutMillis Optional timeout in millis (> 0) enforced by the executor.
 * @property profileId Optional profile / WebView-pool id.
 */
data class WebViewEvaluationRequest(
    val documentKind: String,
    val body: String?,
    val url: String?,
    val baseUrl: String?,
    val javaScript: String,
    val timeoutMillis: Long?,
    val profileId: String?
)

/**
 * Mirrors Core's `HostWebViewEvaluateJavaScriptResponse` (see
 * `crates/reader-contract/src/host.rs` lines 795-819).
 *
 * @property value Raw JS evaluation result — may be a string, number, boolean,
 *   object, array, or null (any JSON value). Defaults to null.
 * @property finalUrl Final URL after any client-side redirects (optional).
 * @property title Rendered page title (optional).
 */
data class WebViewEvaluationResult(
    val value: Any? = null,
    val finalUrl: String? = null,
    val title: String? = null
)

/**
 * Structured executor errors. [WebViewEvaluateJavaScriptHandler] maps each
 * variant to a distinct `host.error` code so Core can distinguish timeout vs
 * execution failure vs missing host capability, instead of collapsing every
 * failure into a generic INTERNAL.
 */
sealed class WebViewExecutorError(message: String) : Exception(message) {
    /**
     * JS evaluation exceeded the requested timeout. Maps to `host.error`
     * code `TIMEOUT` with `retryable = true`.
     */
    class Timeout(val timeoutMillis: Long) :
        WebViewExecutorError("$CAPABILITY_NAME timed out after ${timeoutMillis}ms")

    /**
     * Host has not implemented real WebView execution. Maps to `host.error`
     * code `NOT_IMPLEMENTED` with `retryable = false` (Core should fail closed).
     */
    class NotImplemented :
        WebViewExecutorError("$CAPABILITY_NAME not implemented on this host")

    /**
     * WebView execution threw (page load failure, JS exception, etc.). Maps to
     * `host.error` code `EXECUTION_FAILED` with `retryable = true`.
     */
    class ExecutionFailed(detail: String) :
        WebViewExecutorError("$CAPABILITY_NAME execution failed: $detail")

    /**
     * WebView execution requires a UI context (Activity / WebView attached to
     * the view hierarchy) that is not available in the current environment
     * (e.g. headless instrumented test). Maps to `host.error` code
     * `REQUIRES_UI_CONTEXT` with `retryable = false` — Core should fail closed
     * and the Host must retry only after obtaining an Activity-tier binding.
     */
    class RequiresUiContext(detail: String) :
        WebViewExecutorError("$CAPABILITY_NAME requires UI context (Activity/WebView): $detail")

    private companion object {
        private const val CAPABILITY_NAME = "webview.evaluateJavaScript"
    }
}

/**
 * Test stub: returns a canned [WebViewEvaluationResult], or throws a canned
 * [WebViewExecutorError]. Used by `HostWebViewRenderProofTest` to isolate the
 * handler/router tier from real WebView execution.
 *
 * Construct with exactly one of [result] / [error]:
 * - `StubWebViewExecutor(result = WebViewEvaluationResult(...))` for happy-path proofs.
 * - `StubWebViewExecutor(error = WebViewExecutorError.Timeout(5000))` for failure proofs.
 */
class StubWebViewExecutor(
    private val result: WebViewEvaluationResult? = null,
    private val error: WebViewExecutorError? = null
) : WebViewExecutor {
    override suspend fun evaluate(request: WebViewEvaluationRequest): WebViewEvaluationResult {
        error?.let { throw it }
        return result ?: WebViewEvaluationResult(value = null)
    }
}

/**
 * Production executor backed by Android `WebView`. Executes Core's
 * `webview.evaluateJavaScript` requests against a caller-supplied [WebView]
 * (typically the Activity-attached one bound in `MainActivity.onCreate`).
 *
 * **Lifecycle (Slice A — WebView real binding)**:
 *  - At `ReaderCoreClient.init()` time no `WebView` exists yet (only the
 *    Application context is available), so the wiring registers an
 *    `AndroidWebViewExecutor(webView = null)`. Dispatch in this state
 *    throws [WebViewExecutorError.RequiresUiContext] — same fail-closed
 *    contract as before, but with a sharper diagnostic that names the
 *    missing binding instead of the missing `Context`.
 *  - `MainActivity.onCreate` creates the host `WebView` and calls
 *    `ReaderCoreClient.get().bindWebViewExecutor(webView)`, which atomically
 *    swaps the registered `webview.evaluateJavaScript` handler to one
 *    backed by the real `WebView`. From that point on, real Core requests
 *    execute JavaScript against the bound WebView and return rendered
 *    results.
 *  - On test/dev tear-down `bindWebViewExecutor(null)` reverts to fail-closed.
 *
 * **Headless proof**: instrumented tests that do not bind a `WebView` (e.g.
 * the existing P0 fail-closed proofs) still receive `REQUIRES_UI_CONTEXT`,
 * which is the correct behavior: headless = no Activity-tier binding = Core
 * fails closed and marks the source `host_required`.
 *
 * **Activity-tier proof**: a real WebView must be attached to a window for
 * `WebView.evaluateJavascript` to drive the renderer; this class is
 * responsible for evaluating JS, not for managing the WebView's view
 * lifecycle. The caller (`MainActivity` or its container) is responsible for
 * the `WebView`'s attachment.
 *
 * @param webView Optional real [WebView] bound to an Activity view hierarchy.
 *   When null, the executor throws [WebViewExecutorError.RequiresUiContext]
 *   immediately (fail-closed) so Core receives a structured error and does
 *   not block waiting on a non-existent WebView.
 */
class AndroidWebViewExecutor(private val webView: WebView? = null) : WebViewExecutor {
    override suspend fun evaluate(request: WebViewEvaluationRequest): WebViewEvaluationResult {
        val wv = webView ?: throw WebViewExecutorError.RequiresUiContext(
            "no WebView bound (headless or pre-MainActivity). " +
                "Call ReaderCoreClient.get().bindWebViewExecutor(webView) in MainActivity.onCreate."
        )
        return withContext(Dispatchers.Main) {
            try {
                when (request.documentKind) {
                    "html" -> {
                        val body = request.body
                            ?: throw WebViewExecutorError.ExecutionFailed(
                                "$CAPABILITY_NAME html document requires non-blank body"
                            )
                        wv.loadDataWithBaseURL(
                            request.baseUrl,
                            body,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                    "url" -> {
                        val url = request.url
                            ?: throw WebViewExecutorError.ExecutionFailed(
                                "$CAPABILITY_NAME url document requires non-blank url"
                            )
                        wv.loadUrl(url)
                    }
                    else -> throw WebViewExecutorError.ExecutionFailed(
                        "$CAPABILITY_NAME unsupported documentKind: ${request.documentKind}"
                    )
                }
                val value = evaluateJs(wv, request.javaScript, request.timeoutMillis)
                WebViewEvaluationResult(
                    value = value,
                    finalUrl = wv.url,
                    title = wv.title
                )
            } catch (e: WebViewExecutorError) {
                throw e
            } catch (e: Exception) {
                throw WebViewExecutorError.ExecutionFailed(e.message ?: "unknown error")
            }
        }
    }

    private companion object {
        private const val CAPABILITY_NAME = "webview.evaluateJavaScript"
    }

    /**
     * Bridge `WebView.evaluateJavascript` (callback-based, fires on the
     * WebView's thread) to a `suspend` function. Honors [timeoutMillis] via a
     * main-thread `Handler` — when the timeout elapses, the continuation
     * resumes with [WebViewExecutorError.Timeout] regardless of whether the
     * callback eventually fires; the timeout runnable is removed from the
     * queue once the callback completes.
     */
    private suspend fun evaluateJs(
        webView: WebView,
        javaScript: String,
        timeoutMillis: Long?
    ): String? = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable: Runnable? = timeoutMillis?.let { ms ->
            Runnable {
                if (cont.isActive) {
                    cont.resumeWithException(WebViewExecutorError.Timeout(ms))
                }
            }.also { mainHandler.postDelayed(it, ms) }
        }
        cont.invokeOnCancellation {
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        }
        webView.evaluateJavascript(javaScript) { value ->
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            if (cont.isActive) cont.resume(value)
        }
    }
}
