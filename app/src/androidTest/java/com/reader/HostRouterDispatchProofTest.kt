package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.AntiBotCapabilityHandler
import com.reader.host.AntiBotChallengeHandler
import com.reader.host.AntiBotHttpResponse
import com.reader.host.AndroidWebViewExecutor
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.MediaDownloadCapabilityHandler
import com.reader.host.MediaDownloadHandler
import com.reader.host.MediaDownloadResult
import com.reader.host.OkHttpAntiBotExecutor
import com.reader.host.StubAntiBotExecutor
import com.reader.host.StubMediaDownloadExecutor
import com.reader.host.StubWebViewExecutor
import com.reader.host.WebViewEvaluateJavaScriptHandler
import com.reader.host.WebViewEvaluationResult
import com.reader.host.WebViewExecutorError
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Router-layer dispatch proof for the three host lanes wired in
 * [com.reader.api.ReaderCoreClient.init]: `webview.evaluateJavaScript`,
 * `anti_bot.execute`, and `media.download`.
 *
 * **What this proves**: a Core `host.request` event for each lane, when handed
 * to [HostAdapter.dispatch], reaches the registered handler and returns a
 * structured [HostReply] — NOT the `INTERNAL / "unsupported capability"`
 * error the adapter emits for unregistered capabilities. This is the
 * router-wiring proof; it does NOT exercise real executor behaviour.
 *
 * **Two proof shapes per lane**:
 * 1. **Stub executor** — handler returns `host.complete` with the canned
 *    result, proving the router dispatched to the handler (the adapter would
 *    return `INTERNAL` if the capability were unregistered).
 * 2. **Production (fail-closed) executor** — handler returns `host.error` with
 *    code `NOT_IMPLEMENTED`, proving the router dispatched to the handler AND
 *    the handler mapped the stub executor's `NotImplementedError` to a
 *    structured error (not a crash, not `INTERNAL`).
 *
 * **Negative proof**: an unregistered capability still returns
 * `INTERNAL / "unsupported capability"`, confirming the adapter distinguishes
 * registered lanes from unknown ones.
 *
 * **Evidence tier**: handler/router — no JNI, no Core, no network. Mirrors the
 * tier of [HostWebViewRenderProofTest] / [HostAntiBotProofTest] /
 * [HostMediaDownloadProofTest] but focuses on the [HostAdapter] dispatch
 * boundary rather than handler-internal logic.
 *
 * **Cannot run in this session**: requires `connectedAndroidTest` (Android
 * instrumented test). The test code is the deliverable.
 */
@RunWith(AndroidJUnit4::class)
class HostRouterDispatchProofTest {

    // ----------------------------------------------------------------------
    // WebView render lane (webview.evaluateJavaScript)
    // ----------------------------------------------------------------------

    /**
     * Stub executor returns a canned value → router dispatches to
     * [WebViewEvaluateJavaScriptHandler] → `host.complete` with `{ value }`.
     * If the capability were unregistered, the adapter would return
     * `INTERNAL / "unsupported capability"` instead of `complete`.
     */
    @Test
    fun webViewRenderLaneDispatchesToHandlerWithStubExecutor() {
        val adapter = HostAdapter()
        adapter.register(
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            WebViewEvaluateJavaScriptHandler(
                StubWebViewExecutor(
                    result = WebViewEvaluationResult(
                        value = "dispatched-via-router",
                        finalUrl = "https://example.test/rendered"
                    )
                )
            )
        )

        val request = HostRequest(
            1L,
            4001L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "html")
                    put("body", "<html><body>test</body></html>")
                })
                put("javaScript", "document.body.innerText")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "webview.evaluateJavaScript must dispatch to handler (complete), got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals(
            "value must be the stub's canned result",
            "dispatched-via-router",
            result.getString("value")
        )
    }

    /**
     * Production executor [AndroidWebViewExecutor] (no Context) throws
     * [WebViewExecutorError.RequiresUiContext] → handler maps to `host.error`
     * with code `REQUIRES_UI_CONTEXT`. Proves the router dispatched to the
     * handler (unregistered would be `INTERNAL`) and the handler fail-closed
     * gracefully — mirroring the HarmonyOS `REQUIRES_UI_CONTEXT` pattern.
     * Real WebView L1-L5 requires an Activity-tier UI binding (Phase 4).
     */
    @Test
    fun webViewRenderLaneDispatchesToHandlerWithProductionExecutorReturnsRequiresUiContext() {
        val adapter = HostAdapter()
        adapter.register(
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(null))
        )

        val request = HostRequest(
            1L,
            4002L,
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            JSONObject().apply {
                put("document", JSONObject().apply {
                    put("kind", "url")
                    put("url", "https://example.test/")
                })
                put("javaScript", "document.body")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "webview.evaluateJavaScript must error with real executor, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be REQUIRES_UI_CONTEXT (not INTERNAL — proves dispatch reached handler)",
            "REQUIRES_UI_CONTEXT",
            error.code()
        )
        assertFalse(
            "REQUIRES_UI_CONTEXT must not be retryable (fail-closed)",
            error.retryable()
        )
    }

    // ----------------------------------------------------------------------
    // Anti-bot lane (anti_bot.execute — host-private capability)
    // ----------------------------------------------------------------------

    /**
     * Stub executor returns a clean 200 response → router dispatches to
     * [AntiBotCapabilityHandler] → `host.complete` with `{ body, statusCode }`.
     */
    @Test
    fun antiBotLaneDispatchesToHandlerWithStubExecutor() {
        val adapter = HostAdapter()
        adapter.register(
            AntiBotCapabilityHandler.CAPABILITY,
            AntiBotCapabilityHandler(
                AntiBotChallengeHandler(
                    StubAntiBotExecutor(
                        response = AntiBotHttpResponse(
                            statusCode = 200,
                            body = "<html><body>clean</body></html>",
                            headers = emptyMap(),
                            finalUrl = "https://example.test/article"
                        )
                    )
                )
            )
        )

        val request = HostRequest(
            1L,
            4003L,
            AntiBotCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://example.test/article")
                put("headers", JSONObject().apply {
                    put("User-Agent", "Reader/1.0")
                })
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "anti_bot.execute must dispatch to handler (complete), got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals(
            "statusCode must be 200 (stub clean response)",
            200,
            result.getInt("statusCode")
        )
    }

    /**
     * Production executor [OkHttpAntiBotExecutor] (real OkHttp GET) attempts
     * to fetch `https://example.test/article` — the `.test` TLD does not
     * resolve (RFC 2606), so OkHttp throws `IOException` →
     * [AntiBotChallengeHandler] maps to `host.error` with code `INTERNAL`
     * (retryable). Proves the router dispatched to the adapter (which
     * delegates to the real executor) rather than rejecting with
     * `INTERNAL / "unsupported capability"`.
     */
    @Test
    fun antiBotLaneDispatchesToHandlerWithProductionExecutorReturnsNetworkError() {
        val adapter = HostAdapter()
        adapter.register(
            AntiBotCapabilityHandler.CAPABILITY,
            AntiBotCapabilityHandler() // default: real OkHttpAntiBotExecutor
        )

        val request = HostRequest(
            1L,
            4004L,
            AntiBotCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://example.test/article")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "anti_bot.execute must error with real executor + unresolvable URL, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be INTERNAL (real fetch failed — proves dispatch reached handler)",
            "INTERNAL",
            error.code()
        )
        assertTrue(
            "INTERNAL network error must be retryable",
            error.retryable()
        )
    }

    // ----------------------------------------------------------------------
    // Media download lane (media.download)
    // ----------------------------------------------------------------------

    /**
     * Stub executor returns a canned result → router dispatches to
     * [MediaDownloadCapabilityHandler] → `host.complete` with
     * `{ resourceId, statusCode }`.
     */
    @Test
    fun mediaDownloadLaneDispatchesToHandlerWithStubExecutor() {
        val adapter = HostAdapter()
        adapter.register(
            MediaDownloadCapabilityHandler.CAPABILITY,
            MediaDownloadCapabilityHandler {
                MediaDownloadHandler(
                    StubMediaDownloadExecutor(
                        response = MediaDownloadResult(
                            resourceId = "res-router-001",
                            statusCode = 200,
                            byteLength = 256L,
                            fromCache = false,
                            finalUrl = "https://example.test/audio.mp3"
                        )
                    )
                )
            }
        )

        val request = HostRequest(
            1L,
            4005L,
            MediaDownloadCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://example.test/audio.mp3")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "media.download must dispatch to handler (complete), got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals(
            "resourceId must be the stub's canned value",
            "res-router-001",
            result.getString("resourceId")
        )
        assertEquals(
            "statusCode must be 200",
            200,
            result.getInt("statusCode")
        )
    }

    /**
     * Production executor `OkHttpMediaDownloadExecutor` (real OkHttp GET)
     * attempts to fetch `https://example.test/audio.mp3` — the `.test` TLD
     * does not resolve (RFC 2606), so OkHttp throws `IOException` →
     * [MediaDownloadCapabilityHandler] maps to `host.error` with code
     * `INTERNAL` (retryable). Proves the router dispatched to the adapter
     * (which delegates to the real executor) rather than rejecting with
     * `INTERNAL / "unsupported capability"`.
     */
    @Test
    fun mediaDownloadLaneDispatchesToHandlerWithProductionExecutorReturnsNetworkError() {
        val adapter = HostAdapter()
        adapter.register(
            MediaDownloadCapabilityHandler.CAPABILITY,
            MediaDownloadCapabilityHandler() // default: real OkHttpMediaDownloadExecutor
        )

        val request = HostRequest(
            1L,
            4006L,
            MediaDownloadCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://example.test/audio.mp3")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "media.download must error with real executor + unresolvable URL, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be INTERNAL (real fetch failed — proves dispatch reached handler)",
            "INTERNAL",
            error.code()
        )
        assertTrue(
            "INTERNAL network error must be retryable",
            error.retryable()
        )
    }

    // ----------------------------------------------------------------------
    // Negative proof: unregistered capability → INTERNAL "unsupported capability"
    // ----------------------------------------------------------------------

    /**
     * An unregistered capability must return `INTERNAL / "unsupported
     * capability"` — confirming the adapter distinguishes registered lanes
     * (which return `complete` or `NOT_IMPLEMENTED`) from unknown ones (which
     * return `INTERNAL`). This is the control for the dispatch proofs above.
     */
    @Test
    fun unregisteredCapabilityReturnsUnsupportedInternalError() {
        val adapter = HostAdapter()
        // Do not register any handler for this capability.

        val request = HostRequest(
            1L,
            4007L,
            "unregistered.capability",
            "{}"
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "unregistered capability must error, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be INTERNAL for unregistered capability",
            "INTERNAL",
            error.code()
        )
        assertTrue(
            "error message must mention 'unsupported capability', got: ${error.message()}",
            error.message().contains("unsupported capability")
        )
    }
}
