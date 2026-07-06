package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.StubWebViewExecutor
import com.reader.host.WebViewEvaluateJavaScriptHandler
import com.reader.host.WebViewEvaluationResult
import com.reader.host.WebViewExecutorError
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Host-side proof for the `webview.evaluateJavaScript` render lane
 * (item 2b): verifies [WebViewEvaluateJavaScriptHandler] honours the Core
 * contract (`HostWebViewEvaluateJavaScriptRequest` /
 * `HostWebViewEvaluateJavaScriptResponse` defined in
 * `crates/reader-contract/src/host.rs` lines 679-820) end-to-end at the
 * handler/router tier.
 *
 * **Proof tier**: handler/router — mirrors [HostLoginCookieProofTest]'s proof
 * level. The handler is invoked directly with a [HostRequest]; execution is
 * isolated via [StubWebViewExecutor] so nothing touches a real `WebView`.
 * JNI / Core-event routing is already proven by [CoreEndToEndTest].
 *
 * **Device-headless/App tier**: real WebView L1-L5 rendering (load HTML/URL,
 * evaluate JS via `WebView.evaluateJavascript`, capture finalUrl/title) is
 * pending device proof and is NOT claimed here. `AndroidWebViewExecutor`
 * currently throws [WebViewExecutorError.NotImplemented] (fail-closed).
 *
 * **Mirrors**: iOS/HarmonyOS proof structure for `webview.evaluateJavaScript`.
 */
@RunWith(AndroidJUnit4::class)
class HostWebViewRenderProofTest {

    /**
     * Proof 1: `kind = "html"` — handler accepts an HTML body, delegates to
     * the executor, and returns `host.complete` with `{ value, finalUrl, title }`.
     */
    @Test
    fun webViewEvaluatesJavaScriptReturnsValue() = runBlocking {
        val executor = StubWebViewExecutor(
            result = WebViewEvaluationResult(
                value = "hello from JS",
                finalUrl = "https://example.test/rendered",
                title = "Rendered Page"
            )
        )
        val handler = WebViewEvaluateJavaScriptHandler(executor)

        val request = HostRequest(
            1L,
            3001L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "html")
                    put("body", "<html><body>test</body></html>")
                })
                put("javaScript", "document.body.innerText")
            }.toString()
        )

        val reply = handler.handle(request)
        assertTrue(
            "webview.evaluateJavaScript must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals(
            "value must be hello from JS",
            "hello from JS",
            result.getString("value")
        )
        val finalUrl = result.getString("finalUrl")
        assertTrue(
            "finalUrl must end with /rendered, got: $finalUrl",
            finalUrl.endsWith("/rendered")
        )
        assertEquals(
            "title must be Rendered Page",
            "Rendered Page",
            result.getString("title")
        )
    }

    /**
     * Proof 2: `kind = "url"` — handler accepts a navigation URL, delegates to
     * the executor, and returns `host.complete` with the rendered DOM in
     * `value` and the post-navigation `finalUrl`.
     */
    @Test
    fun webViewLoadsUrlAndReturnsDom() = runBlocking {
        val executor = StubWebViewExecutor(
            result = WebViewEvaluationResult(
                value = "<html>rendered</html>",
                finalUrl = "https://example.test/final"
            )
        )
        val handler = WebViewEvaluateJavaScriptHandler(executor)

        val request = HostRequest(
            1L,
            3002L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "url")
                    put("url", "https://example.test/start")
                })
                put("javaScript", "document.documentElement.outerHTML")
            }.toString()
        )

        val reply = handler.handle(request)
        assertTrue(
            "webview.evaluateJavaScript must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        val value = result.getString("value")
        assertTrue(
            "value must contain 'rendered', got: $value",
            value.contains("rendered")
        )
        val finalUrl = result.getString("finalUrl")
        assertTrue(
            "finalUrl must end with /final, got: $finalUrl",
            finalUrl.endsWith("/final")
        )
    }

    /**
     * Proof 3: timeout fails closed — when the executor throws
     * [WebViewExecutorError.Timeout], the handler returns `host.error` with a
     * `TIMEOUT` code (distinguishable from other failures), not a stale
     * `host.complete`.
     */
    @Test
    fun webViewEvaluatesJavaScriptTimeoutFailsClosed() = runBlocking {
        val executor = StubWebViewExecutor(
            error = WebViewExecutorError.Timeout(timeoutMillis = 5000)
        )
        val handler = WebViewEvaluateJavaScriptHandler(executor)

        val request = HostRequest(
            1L,
            3003L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "url")
                    put("url", "https://slow.test/")
                })
                put("javaScript", "document.body")
                put("timeoutMillis", 5000)
            }.toString()
        )

        val reply = handler.handle(request)
        assertTrue(
            "webview.evaluateJavaScript must error on timeout, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be TIMEOUT (distinguishes timeout from other failures)",
            "TIMEOUT",
            error.code()
        )
        assertTrue(
            "error message should mention timeout, got: ${error.message()}",
            error.message().contains("timed out", ignoreCase = true)
        )
    }

    /**
     * Validation: `kind = "html"` with a `url` field is rejected — Core's
     * `HostWebViewDocument::validate` (host.rs line 719) forbids `url` on html
     * documents, and the host handler mirrors that.
     */
    @Test
    fun webViewRejectsHtmlDocumentWithUrl() = runBlocking {
        val executor = StubWebViewExecutor(
            result = WebViewEvaluationResult(value = "should-not-reach-executor")
        )
        val handler = WebViewEvaluateJavaScriptHandler(executor)

        val request = HostRequest(
            1L,
            3004L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "html")
                    put("body", "<html></html>")
                    put("url", "https://should-not-be-present.test/")
                })
                put("javaScript", "1+1")
            }.toString()
        )

        val reply = handler.handle(request)
        assertTrue(
            "html document with url must error, got: ${reply.kind()}",
            reply.isError()
        )
    }

    /**
     * Validation: `kind = "url"` with a `body` field is rejected — Core's
     * `HostWebViewDocument::validate` (host.rs line 739) forbids `body` on url
     * documents, and the host handler mirrors that.
     */
    @Test
    fun webViewRejectsUrlDocumentWithBody() = runBlocking {
        val executor = StubWebViewExecutor(
            result = WebViewEvaluationResult(value = "should-not-reach-executor")
        )
        val handler = WebViewEvaluateJavaScriptHandler(executor)

        val request = HostRequest(
            1L,
            3005L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "url")
                    put("url", "https://example.test/")
                    put("body", "<html>should-not-be-present</html>")
                })
                put("javaScript", "1+1")
            }.toString()
        )

        val reply = handler.handle(request)
        assertTrue(
            "url document with body must error, got: ${reply.kind()}",
            reply.isError()
        )
    }
}
