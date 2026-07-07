package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.AndroidWebViewExecutor
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.WebViewEvaluateJavaScriptHandler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 real-executor proof for `webview.evaluateJavaScript`.
 *
 * Verifies that [AndroidWebViewExecutor] with no Activity Context — the
 * production wiring used by [com.reader.api.ReaderCoreClient.init] — fails
 * closed with `REQUIRES_UI_CONTEXT` (NOT `NOT_IMPLEMENTED`, NOT a raw
 * exception). This distinguishes "host has the capability but needs an
 * Activity-tier binding" from "host never implemented this".
 *
 * Real WebView L1-L5 rendering (load HTML/URL, evaluate JS, capture
 * finalUrl/title) requires an Activity-attached WebView. This proof
 * establishes the fail-closed contract: Core receives a structured error and
 * can mark the source `host_required` without crashing the host adapter.
 *
 * This is an executor-tier proof, NOT an App-level rendering proof.
 */
@RunWith(AndroidJUnit4::class)
class HostWebViewRealExecutorProofTest {

    /**
     * Dispatch `webview.evaluateJavaScript` through the router with the
     * production executor ([AndroidWebViewExecutor] with `context = null`).
     * The reply must be a structured `REQUIRES_UI_CONTEXT` error, not
     * `NOT_IMPLEMENTED` and not a thrown exception.
     */
    @Test
    fun androidWebViewExecutorWithoutContextReturnsRequiresUiContext() {
        val adapter = HostAdapter()
        adapter.register(
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(webView = null))
        )

        val request = HostRequest(
            1L,
            5001L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "html")
                    put("body", "<html><body>proof</body></html>")
                })
                put("javaScript", "document.body.innerText")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "AndroidWebViewExecutor(webView = null) must error with REQUIRES_UI_CONTEXT, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be REQUIRES_UI_CONTEXT (not NOT_IMPLEMENTED)",
            "REQUIRES_UI_CONTEXT",
            error.code()
        )
        assertFalse(
            "REQUIRES_UI_CONTEXT must not be retryable without Activity-tier binding",
            error.retryable()
        )
        assertTrue(
            "error message should mention UI context or WebView binding, got: ${error.message()}",
            error.message().contains("UI context", ignoreCase = true) ||
                error.message().contains("WebView", ignoreCase = true) ||
                error.message().contains("Activity", ignoreCase = true)
        )
    }
}
