package com.reader.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit test for the [AndroidWebViewExecutor] headless fail-closed contract
 * (Slice A — WebView real binding).
 *
 * Real WebView L1-L5 rendering requires an instrumented environment
 * (`androidx.test.runner.AndroidJUnitRunner`) and an Activity-attached
 * WebView; that proof lives in
 * `app/src/androidTest/.../HostWebViewP0HeadlessFailClosedProofTest.kt`
 * and `HostWebViewRealExecutorProofTest.kt`. This JVM test only verifies
 * the fail-closed branch of the executor's [WebViewExecutor.evaluate]
 * contract without touching a real WebView.
 *
 * **What is proven**:
 *  - [AndroidWebViewExecutor] constructed without a `WebView` throws
 *    [WebViewExecutorError.RequiresUiContext] (not a raw `NullPointerException`,
 *    not `INTERNAL`, not `NOT_IMPLEMENTED`).
 *  - The error message names the rebinding entry point
 *    (`ReaderCoreClient.get().bindWebViewExecutor(webView)`) so the next
 *    developer can fix the binding from the diagnostic alone.
 *  - The error is non-retryable (Core must mark the source `host_required`,
 *    not retry forever).
 *  - When wrapped in [WebViewEvaluateJavaScriptHandler] and dispatched
 *    through a [HostAdapter], the fail-closed path produces a structured
 *    `host.error` reply with code `REQUIRES_UI_CONTEXT` — matching the
 *    host→Core contract.
 */
class AndroidWebViewExecutorJvmTest {

    @Test
    fun `executor without WebView throws RequiresUiContext`() {
        val executor = AndroidWebViewExecutor(webView = null)
        val request = WebViewEvaluationRequest(
            documentKind = "html",
            body = "<html><body>x</body></html>",
            url = null,
            baseUrl = null,
            javaScript = "1+1",
            timeoutMillis = null,
            profileId = null
        )
        val error = runCatching { kotlinx.coroutines.runBlocking { executor.evaluate(request) } }
            .exceptionOrNull()
        assertNotNull("executor must throw when no WebView is bound", error)
        assertTrue(
            "expected WebViewExecutorError.RequiresUiContext, got: ${error!!::class.java.name}",
            error is WebViewExecutorError.RequiresUiContext
        )
        val msg = error.message ?: ""
        assertTrue(
            "error message must name the rebinding entry point, got: $msg",
            msg.contains("bindWebViewExecutor", ignoreCase = true)
        )
    }

    @Test
    fun `RequiresUiContext is not retryable`() {
        val err = WebViewExecutorError.RequiresUiContext("test")
        // The handler maps RequiresUiContext to HostReply.error(code, msg, retryable = false).
        // Assert the variant itself carries the no-retry semantic by checking the handler mapping.
        val handler = WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(webView = null))
        val reply = handler.handle(
            HostRequest(
                1L,
                9001L,
                WebViewEvaluateJavaScriptHandler.CAPABILITY,
                org.json.JSONObject().apply {
                    put("document", org.json.JSONObject().apply {
                        put("kind", "html")
                        put("body", "<html></html>")
                    })
                    put("javaScript", "1")
                }.toString()
            )
        )
        assertTrue("must error", reply.isError())
        val hostErr = reply as HostReply.Error
        assertEquals("REQUIRES_UI_CONTEXT", hostErr.code())
        assertFalse("REQUIRES_UI_CONTEXT must not be retryable", hostErr.retryable())
    }

    @Test
    fun `dispatch through HostAdapter produces REQUIRES_UI_CONTEXT reply`() {
        val adapter = HostAdapter()
        adapter.register(
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(webView = null))
        )
        val reply = adapter.dispatch(
            HostRequest(
                1L,
                9002L,
                WebViewEvaluateJavaScriptHandler.CAPABILITY,
                org.json.JSONObject().apply {
                    put("document", org.json.JSONObject().apply {
                        put("kind", "url")
                        put("url", "https://example.test/")
                    })
                    put("javaScript", "document.title")
                }.toString()
            )
        )
        assertTrue("must error", reply.isError())
        assertEquals(
            "adapter dispatch must surface REQUIRES_UI_CONTEXT",
            "REQUIRES_UI_CONTEXT",
            (reply as HostReply.Error).code()
        )
    }
}
