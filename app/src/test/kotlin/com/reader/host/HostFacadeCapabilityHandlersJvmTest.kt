package com.reader.host

import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.AndroidTtsAdapter
import com.reader.android.data.adapter.AndroidWebDavClient
import com.reader.android.data.adapter.DownloadCacheManager
import com.reader.android.data.adapter.FakeAndroidTtsAdapter
import com.reader.android.data.adapter.FakePermissionRuntimeAdapter
import com.reader.android.data.adapter.PermissionRuntimeAdapter
import com.reader.android.data.adapter.TtsInitResult
import com.reader.android.data.adapter.TtsPlaybackState
import com.reader.android.data.adapter.TtsUtterance
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fake [WebDavCredentialStore.CredentialKeystore] for JVM tests — avoids
 * the real AndroidKeystore which is unavailable on JVM.
 */
private class FakeCredentialKeystore : WebDavCredentialStore.CredentialKeystore {
    private val secrets = mutableMapOf<String, String>()
    override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord =
        AndroidEncryptedCredentialRecord(
            identifier = identifier,
            keyAlias = "fake",
            cipherTextBase64 = secret,
            ivBase64 = "",
            valueChecksum = ""
        ).also { secrets[identifier] = secret }

    override fun load(record: AndroidEncryptedCredentialRecord): String =
        secrets[record.identifier] ?: ""

    override fun revoke(record: AndroidEncryptedCredentialRecord): Boolean =
        secrets.remove(record.identifier) != null
}

private class FakeUiWebViewSession(
    var active: Boolean = true,
    var evaluationResult: WebViewEvaluationResult = WebViewEvaluationResult(
        value = "\"Reader\"",
        finalUrl = "https://example.com/final",
        title = "Reader"
    )
) : UiWebViewSession {
    var closeCalls: Int = 0
    var evaluatedUrl: String? = null
    var evaluatedScript: String? = null
    var evaluatedTimeoutMillis: Long? = null

    override fun close(): Boolean {
        closeCalls += 1
        return active
    }

    override suspend fun evaluate(
        url: String,
        script: String,
        timeoutMillis: Long?
    ): WebViewEvaluationResult {
        if (!active) throw WebViewExecutorError.RequiresUiContext("test session inactive")
        evaluatedUrl = url
        evaluatedScript = script
        evaluatedTimeoutMillis = timeoutMillis
        return evaluationResult
    }
}

/**
 * JVM unit tests for the Slice C [HostFacade] UI-facing capability handlers.
 *
 * Tests cover the handlers that do NOT require a real Android [Context]:
 * TTS, permission, notification (null-adapter branch), share. Handlers that
 * need a [Context] (clipboard, device.vibrate) are exercised at the
 * instrumented tier; here we only assert their fail-closed branch when
 * context is null.
 */
class HostFacadeCapabilityHandlersJvmTest {

    private fun makeFacade(
        tts: AndroidTtsAdapter = FakeAndroidTtsAdapter(),
        permission: PermissionRuntimeAdapter = FakePermissionRuntimeAdapter(
            mapOf(PermissionKind.NOTIFICATIONS to PermissionStatus.GRANTED)
        ),
        uiWebViewSession: UiWebViewSession = UnavailableUiWebViewSession
    ): HostFacade = HostFacade(
        context = null,
        tts = tts,
        permission = permission,
        notification = null,
        webDav = null,
        credentials = WebDavCredentialStore(FakeCredentialKeystore()),
        downloadCache = null,
        uiWebViewSession = uiWebViewSession
    )

    // ── TTS handlers ──────────────────────────────────────────────────────

    @Test
    fun `tts_system_start returns started=true`() {
        val facade = makeFacade()
        val reply = TtsSystemStartHandler(facade).handle(
            HostRequest(1L, 101L, TtsSystemStartHandler.CAPABILITY,
                JSONObject().put("text", "hello").toString())
        )
        assertTrue("must complete", reply.isComplete())
        assertTrue(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("started"))
    }

    @Test
    fun `tts_system_start rejects blank text`() {
        val facade = makeFacade()
        val reply = TtsSystemStartHandler(facade).handle(
            HostRequest(1L, 102L, TtsSystemStartHandler.CAPABILITY,
                JSONObject().apply { put("text", "") }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `tts_system_stop returns acknowledged=true`() {
        val facade = makeFacade()
        val reply = TtsSystemStopHandler(facade).handle(
            HostRequest(1L, 103L, TtsSystemStopHandler.CAPABILITY, "{}")
        )
        assertTrue(reply.isComplete())
        assertTrue(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("acknowledged"))
    }

    @Test
    fun `tts_system_status returns available and state`() {
        val facade = makeFacade()
        val reply = TtsSystemStatusHandler(facade).handle(
            HostRequest(1L, 104L, TtsSystemStatusHandler.CAPABILITY, "{}")
        )
        assertTrue(reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("available"))
        assertEquals("IDLE", result.getString("state"))
    }

    // ── Permission handlers ───────────────────────────────────────────────

    @Test
    fun `permission_check returns canonical granted flag`() {
        val facade = makeFacade()
        val reply = PermissionCheckHandler(facade).handle(
            HostRequest(1L, 201L, PermissionCheckHandler.CAPABILITY,
                JSONObject().put("scope", "notifications").toString())
        )
        assertTrue(reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("granted"))
    }

    @Test
    fun `permission_check rejects unknown kind`() {
        val facade = makeFacade()
        val reply = PermissionCheckHandler(facade).handle(
            HostRequest(1L, 202L, PermissionCheckHandler.CAPABILITY,
                JSONObject().put("scope", "unknown_thing").toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `permission_request fails closed when foreground launcher is required`() {
        val facade = makeFacade(
            permission = FakePermissionRuntimeAdapter(
                mapOf(PermissionKind.FILE_ACCESS to PermissionStatus.DENIED)
            )
        )
        val reply = PermissionRequestHandler(facade).handle(
            HostRequest(1L, 203L, PermissionRequestHandler.CAPABILITY,
                JSONObject().put("scope", "storage").toString())
        )
        assertTrue(reply.isError())
        assertEquals("REQUIRES_UI_CONTEXT", (reply as HostReply.Error).code())
    }

    // ── Notification handlers (null adapter = fail-closed) ────────────────

    @Test
    fun `notification_show returns INTERNAL when adapter not wired`() {
        val facade = makeFacade() // notification = null
        val reply = NotificationShowHandler(facade).handle(
            HostRequest(1L, 301L, NotificationShowHandler.CAPABILITY,
                JSONObject().apply {
                    put("id", "download"); put("title", "t"); put("body", "m")
                }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `notification_ensure_channels returns INTERNAL when adapter not wired`() {
        val facade = makeFacade()
        val reply = NotificationEnsureChannelsHandler(facade).handle(
            HostRequest(1L, 302L, NotificationEnsureChannelsHandler.CAPABILITY, "{}")
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    // ── Share handler ─────────────────────────────────────────────────────

    @Test
    fun `share_invoke fails closed without foreground Activity`() {
        val facade = makeFacade()
        val reply = ShareInvokeHandler(facade).handle(
            HostRequest(1L, 401L, ShareInvokeHandler.CAPABILITY,
                JSONObject().apply { put("text", "share me") }.toString())
        )
        assertTrue(reply.isError())
        assertEquals("REQUIRES_UI_CONTEXT", (reply as HostReply.Error).code())
    }

    @Test
    fun `share_invoke rejects blank text`() {
        val facade = makeFacade()
        val reply = ShareInvokeHandler(facade).handle(
            HostRequest(1L, 402L, ShareInvokeHandler.CAPABILITY,
                JSONObject().apply { put("text", "") }.toString())
        )
        assertTrue("must error", reply.isError())
    }

    // ── UI WebView lifecycle / JavaScript execution ─────────────────────

    @Test
    fun `webview_close finishes active session instead of acknowledging a no-op`() {
        val session = FakeUiWebViewSession(active = true)
        val reply = WebViewCloseHandler(makeFacade(uiWebViewSession = session)).handle(
            HostRequest(1L, 450L, WebViewCloseHandler.CAPABILITY, "{}")
        )

        assertTrue(reply.isComplete())
        assertEquals(1, session.closeCalls)
        assertTrue(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("closed"))
    }

    @Test
    fun `webview_close fails closed when no active session exists`() {
        val session = FakeUiWebViewSession(active = false)
        val reply = WebViewCloseHandler(makeFacade(uiWebViewSession = session)).handle(
            HostRequest(1L, 451L, WebViewCloseHandler.CAPABILITY, "{}")
        )

        assertTrue(reply.isError())
        assertEquals("REQUIRES_UI_CONTEXT", (reply as HostReply.Error).code())
        assertFalse(reply.retryable())
        assertEquals(1, session.closeCalls)
    }

    @Test
    fun `webview_evaluate executes contract url and script and returns actual result`() {
        val session = FakeUiWebViewSession()
        val reply = WebViewEvaluateHandler(makeFacade(uiWebViewSession = session)).handle(
            HostRequest(
                1L,
                452L,
                WebViewEvaluateHandler.CAPABILITY,
                JSONObject()
                    .put("url", "https://example.com")
                    .put("script", "document.title")
                    .put("timeoutMs", 2500L)
                    .toString()
            )
        )

        assertTrue(reply.isComplete())
        assertEquals("https://example.com", session.evaluatedUrl)
        assertEquals("document.title", session.evaluatedScript)
        assertEquals(2500L, session.evaluatedTimeoutMillis)
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals("Reader", result.getJSONObject("result").getString("value"))
        assertEquals("https://example.com/final", result.getString("finalUrl"))
        assertEquals("Reader", result.getString("title"))
    }

    @Test
    fun `webview_evaluate rejects old javaScript acknowledgement shape`() {
        val session = FakeUiWebViewSession()
        val reply = WebViewEvaluateHandler(makeFacade(uiWebViewSession = session)).handle(
            HostRequest(
                1L,
                453L,
                WebViewEvaluateHandler.CAPABILITY,
                JSONObject()
                    .put("url", "https://example.com")
                    .put("javaScript", "document.title")
                    .toString()
            )
        )

        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
        assertEquals(null, session.evaluatedScript)
    }

    // ── Clipboard / Device (null context = fail-closed) ──────────────────

    @Test
    fun `clipboard_copy returns INTERNAL when context null`() {
        val facade = makeFacade()
        val reply = ClipboardCopyHandler(facade).handle(
            HostRequest(1L, 501L, ClipboardCopyHandler.CAPABILITY,
                JSONObject().apply { put("text", "copy me") }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `clipboard_paste returns INTERNAL when context null`() {
        val facade = makeFacade()
        val reply = ClipboardPasteHandler(facade).handle(
            HostRequest(1L, 502L, ClipboardPasteHandler.CAPABILITY, "{}")
        )
        assertTrue("must error", reply.isError())
    }

    @Test
    fun `device_vibrate returns INTERNAL when context null`() {
        val facade = makeFacade()
        val reply = DeviceVibrateHandler(facade).handle(
            HostRequest(1L, 601L, DeviceVibrateHandler.CAPABILITY,
                JSONObject().apply { put("durationMs", 50) }.toString())
        )
        assertTrue("must error", reply.isError())
    }

    @Test
    fun `device_screen_keep_on fails closed without foreground Activity`() {
        val facade = makeFacade()
        val reply = DeviceScreenKeepOnHandler(facade).handle(
            HostRequest(1L, 602L, DeviceScreenKeepOnHandler.CAPABILITY,
                JSONObject().apply { put("enabled", true) }.toString())
        )
        assertTrue(reply.isError())
        assertEquals("REQUIRES_UI_CONTEXT", (reply as HostReply.Error).code())
    }
}
