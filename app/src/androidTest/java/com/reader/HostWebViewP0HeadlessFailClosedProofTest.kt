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
 * Step 4b — P0 headless fail-closed proof for [AndroidWebViewExecutor].
 * Verifies that dispatching a real WebView request in a headless
 * instrumented-test environment returns `REQUIRES_UI_CONTEXT` (not a crash,
 * not `INTERNAL`, not `NOT_IMPLEMENTED`) and the error message mentions the
 * UI-context / headless constraint.
 *
 * **Evidence tier**: device-headless (real Android device, no Activity).
 * NOT App-tier. Mirrors the HarmonyOS headless fail-closed proof.
 *
 * **canEnterMainline = false**: WebView needs Phase 4 Activity UI.
 */
@RunWith(AndroidJUnit4::class)
class HostWebViewP0HeadlessFailClosedProofTest {

    private fun adapter(): HostAdapter {
        val adapter = HostAdapter()
        adapter.register(
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(null))
        )
        return adapter
    }

    private fun dispatchRealUrlRequest(adapter: HostAdapter): HostReply {
        val request = HostRequest(
            1L, 401L, WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "url")
                    put("url", "https://example.test/")
                })
                put("javaScript", "document.title")
            }.toString()
        )
        return adapter.dispatch(request)
    }

    /**
     * webview.dispatch: dispatching with a real URL returns REQUIRES_UI_CONTEXT
     * (fail-closed, not a crash).
     */
    @Test
    fun webviewDispatchReturnsRequiresUiContext() {
        val reply = dispatchRealUrlRequest(adapter())
        assertTrue("must error, got: ${reply.kind()}", reply.isError())
        val error = reply as HostReply.Error
        assertEquals("code must be REQUIRES_UI_CONTEXT", "REQUIRES_UI_CONTEXT", error.code())
        assertFalse("must not be retryable (fail-closed)", error.retryable())
    }

    /**
     * webview.errorCode: error reply has non-empty code + message.
     */
    @Test
    fun webviewErrorCodeAndMessageAreNonEmpty() {
        val reply = dispatchRealUrlRequest(adapter())
        assertTrue("must error", reply.isError())
        val error = reply as HostReply.Error
        assertTrue("code must be non-empty", error.code().isNotEmpty())
        assertTrue("message must be non-empty", error.message().isNotEmpty())
    }

    /**
     * webview.errorMentionsUi: error message mentions "UI context" or
     * "headless" or "RequiresUiContext" — confirming the diagnostic carries
     * the fail-closed reason.
     */
    @Test
    fun webviewErrorMessageMentionsUiContext() {
        val reply = dispatchRealUrlRequest(adapter())
        assertTrue("must error", reply.isError())
        val error = reply as HostReply.Error
        val msg = error.message()
        assertTrue(
            "message must mention UI context / headless, got: $msg",
            msg.contains("UI context", ignoreCase = true) ||
                msg.contains("headless", ignoreCase = true) ||
                msg.contains("RequiresUiContext", ignoreCase = true)
        )
    }
}
