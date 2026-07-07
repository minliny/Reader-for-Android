package com.reader

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.AndroidWebViewExecutor
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.WebViewEvaluateJavaScriptHandler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Slice A — WebView real binding instrumented proof.
 *
 * Verifies the runtime rebind contract: when a [WebView] is provided to
 * [AndroidWebViewExecutor], dispatch through the standard
 * [WebViewEvaluateJavaScriptHandler] produces a `REQUIRES_UI_CONTEXT` error
 * only when the bound WebView is *null*; when a real WebView is bound, the
 * failure mode changes from `REQUIRES_UI_CONTEXT` to a successful JS
 * evaluation (or a *content* error like `EXECUTION_FAILED`), never the
 * binding-level `REQUIRES_UI_CONTEXT`.
 *
 * **Proof tier**: executor + Activity-bound (real `WebView`). The WebView is
 * created inside an `ActivityScenario`-launched [android.app.Activity] so the
 * `WebView` constructor has a real `Context` and the renderer thread is alive.
 * No user-visible UI is needed — we only need the renderer to be alive so
 * `WebView.evaluateJavascript` can drive the JS engine.
 *
 * **What is NOT proven here**: rendering a real page (a `loadUrl` to
 * `https://www.w3.org/` would be the L1-L5 proof). This test only verifies
 * the JS evaluation *path*: that the executor delegates to the bound
 * WebView and reports execution outcomes (success / EXECUTION_FAILED), not
 * the binding-level `REQUIRES_UI_CONTEXT`.
 */
@RunWith(AndroidJUnit4::class)
class HostWebViewRebindProofTest {

    /**
     * Round 1 — pre-bind dispatch: with no WebView bound, the standard
     * production wiring returns `REQUIRES_UI_CONTEXT`. This is the same
     * fail-closed path proven by `HostWebViewP0HeadlessFailClosedProofTest`;
     * it is repeated here as the baseline for the rebind assertion below.
     */
    @Test
    fun webviewDispatchWithoutBindingFailsClosed() {
        val adapter = HostAdapter()
        adapter.register(
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(webView = null))
        )
        val reply = adapter.dispatch(urlRequest(operationId = 7001L))
        assertTrue("must error", reply.isError())
        assertEquals(
            "pre-bind dispatch must return REQUIRES_UI_CONTEXT",
            "REQUIRES_UI_CONTEXT",
            (reply as HostReply.Error).code()
        )
    }

    /**
     * Round 2 — post-bind dispatch: with a real Activity-attached WebView
     * bound to the executor, the SAME adapter dispatch path no longer
     * returns `REQUIRES_UI_CONTEXT`. Either:
     *  - the executor returns a `host.complete` with a JS result, OR
     *  - the executor returns a *content* error (e.g. `EXECUTION_FAILED`,
     *    `TIMEOUT`) but NEVER `REQUIRES_UI_CONTEXT` (binding is satisfied).
     *
     * The distinction matters: a `REQUIRES_UI_CONTEXT` reply on a post-bind
     * dispatch would mean the rebind is broken (the host runtime still
     * holds the pre-bind executor). Catching that regression is the point
     * of this test.
     */
    @Test
    fun webviewDispatchAfterRebindDoesNotReturnRequiresUiContext() {
        ActivityScenario.launch(WebViewHostActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity ->
                webView = WebView(activity)
                // Sanity: ensure the WebView was created and is on a real Context.
                assertEquals(activity, webView.context)
            }

            val adapter = HostAdapter()
            adapter.register(
                WebViewEvaluateJavaScriptHandler.CAPABILITY,
                WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(webView = webView))
            )

            val reply = adapter.dispatch(urlRequest(operationId = 7002L))
            if (reply.isError()) {
                val code = (reply as HostReply.Error).code()
                assertTrue(
                    "post-bind dispatch must NOT return REQUIRES_UI_CONTEXT (binding is satisfied). " +
                        "Got: $code. Message: ${(reply as HostReply.Error).message()}",
                    code != "REQUIRES_UI_CONTEXT"
                )
            }
            // Happy path: reply.isComplete() is also acceptable — the JS result
            // is renderer-dependent and may not be deterministic on all CI
            // devices. The contract we enforce is the absence of
            // REQUIRES_UI_CONTEXT.
        }
    }

    private fun urlRequest(operationId: Long): HostRequest = HostRequest(
        1L,
        operationId,
        WebViewEvaluateJavaScriptHandler.CAPABILITY,
        JSONObject().apply {
            put("document", JSONObject().apply {
                put("kind", "html")
                put("body", "<html><body>rebind-proof</body></html>")
            })
            put("javaScript", "document.body.innerText")
        }.toString()
    )
}
