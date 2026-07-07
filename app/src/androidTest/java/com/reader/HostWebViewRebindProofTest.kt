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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Slice A / 阶段 4 — WebView real binding + real closure instrumented proof.
 *
 * Verifies two contracts:
 *  1. **Rebind contract** (Slice A): when a [WebView] is provided to
 *     [AndroidWebViewExecutor], dispatch through the standard
 *     [WebViewEvaluateJavaScriptHandler] produces a `REQUIRES_UI_CONTEXT` error
 *     only when the bound WebView is *null*; when a real WebView is bound, the
 *     failure mode changes from `REQUIRES_UI_CONTEXT` to a successful JS
 *     evaluation (or a *content* error like `EXECUTION_FAILED`), never the
 *     binding-level `REQUIRES_UI_CONTEXT`.
 *  2. **Real closure contract** (阶段 4): with a real Activity-attached WebView
 *     bound, dispatching `webview.evaluateJavaScript` with an html document
 *     and a JS expression that reads the DOM returns the *actual evaluated
 *     value* — not just "not REQUIRES_UI_CONTEXT". This proves the end-to-end
 *     path: handler → executor → loadDataWithBaseURL → onPageFinished wait →
 *     evaluateJavascript callback → HostReply.complete with the real value.
 *
 * **Proof tier**: executor + Activity-bound (real `WebView`). The WebView is
 * created inside an `ActivityScenario`-launched [android.app.Activity] so the
 * `WebView` constructor has a real `Context` and the renderer thread is alive.
 * No user-visible UI is needed — we only need the renderer to be alive so
 * `WebView.evaluateJavascript` can drive the JS engine.
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
        }
    }

    /**
     * Round 3 — 阶段 4 real closure: with a real Activity-attached WebView
     * bound, dispatching `webview.evaluateJavaScript` with an html document
     * containing `<body>closure-proof</body>` and JS `document.body.innerText`
     * MUST return a `host.complete` whose `value` field carries the actual
     * evaluated string (JSON-encoded by `WebView.evaluateJavascript`, so the
     * string `"closure-proof"` arrives with surrounding quotes).
     *
     * This is the end-to-end proof: it fails if any link in the chain is
     * broken — WebSettings (JS disabled), onPageFinished wait (DOM not
     * ready), evaluateJavascript callback (renderer dead), or result
     * marshalling (handler dropped the value). Rounds 1+2 only prove the
     * binding contract; this round proves the *execution* contract.
     */
    @Test
    fun webviewDispatchReturnsEvaluatedJsValue() {
        ActivityScenario.launch(WebViewHostActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity ->
                webView = WebView(activity).apply {
                    // Mirror MainActivity WebSettings so the proof exercises
                    // the same configuration production uses.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
            }

            val adapter = HostAdapter()
            adapter.register(
                WebViewEvaluateJavaScriptHandler.CAPABILITY,
                WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(webView = webView))
            )

            val reply = adapter.dispatch(
                HostRequest(
                    1L,
                    7003L,
                    WebViewEvaluateJavaScriptHandler.CAPABILITY,
                    JSONObject().apply {
                        put("document", JSONObject().apply {
                            put("kind", "html")
                            put("body", "<html><body>closure-proof</body></html>")
                        })
                        put("javaScript", "document.body.innerText")
                    }.toString()
                )
            )

            assertNotNull("reply must not be null", reply)
            assertTrue(
                "real-closure dispatch must complete (not error). " +
                    "Got: ${reply?.kind()}",
                reply!!.isComplete()
            )
            val result = JSONObject((reply as HostReply.Complete).resultJson())
            assertTrue(
                "result must contain 'value' field. Got: $result",
                result.has("value")
            )
            // WebView.evaluateJavascript JSON-encodes the JS return value, so
            // a string "closure-proof" arrives as "\"closure-proof\"". We
            // assert the marker substring is present (not exact equality) so
            // renderer-specific whitespace/newline handling does not flake.
            val value = result.optString("value", "")
            assertTrue(
                "value must contain 'closure-proof' marker. Got: '$value'",
                value.contains("closure-proof")
            )
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
