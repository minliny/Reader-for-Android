package com.reader.api

import com.reader.android.AppProvider
import com.reader.host.DefaultHostCache
import com.reader.host.DefaultHostLogger
import com.reader.host.HostCachePersistenceAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.HostRuntime
import com.reader.host.HostTransport
import com.reader.host.InMemoryHostFileSystem
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * 阶段 2 — Host capability registry table proof (JVM tier).
 *
 * Asserts the capability set registered by [ReaderCoreClient.init] so the
 * "code exists" vs "Core host bus reachable" gap is explicit and tested.
 * Each row in the table below maps to one assertion: a capability that the
 * production wiring registers MUST be reachable via
 * `ReaderCoreClient.get().hostAdapter().isRegistered(...)`, and a capability
 * that is intentionally NOT registered (gap or host-private-only) MUST
 * return false.
 *
 * **Capability table** (implemented / registered / JVM proof / device proof /
 * app-flow proof):
 *
 * | Capability              | Implemented | Registered | JVM proof | Device proof | App-flow proof |
 * | ----------------------- | :---------: | :--------: | :-------: | :----------: | :------------: |
 * | http.execute            |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | http.cancel             |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | cookie.get              |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | cookie.set              |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | cookie.clear            |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | file.read               |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | file.write              |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | file.delete             |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | cache.get               |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | cache.put               |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | persistence.get         |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | persistence.put         |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | log.emit                |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | time.now                |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | system.info             |     YES     |    YES     |   this    |  Host*Proof  |     阶段 6     |
 * | webview.evaluateJavaScript |  YES     |    YES     |   this    |  Host*Proof  |     阶段 4     |
 * | media.download          |     YES     |    YES     |   this    |  Host*Proof  |     阶段 5     |
 * | anti_bot.execute        |     YES     |    YES (host-private) | this | Host*Proof | 阶段 5 (http.execute interception) |
 * | host.smoke.echo         |     YES     |    YES     |   this    |  Host*Proof  |      N/A       |
 * | source.list/add/remove/set_enabled | YES | YES | this | Host*Proof | P1-5 (UI de-demo) |
 * | source.import/export   |     YES     |    YES     |   this    |  Host*Proof  |     P1-5       |
 * | source.debug.run/detect |    YES    |    YES     |   this    |  Host*Proof  |     P1-5       |
 * | rss.subscription.list/add/delete | YES | YES | this | Host*Proof | P1-5 (UI de-demo) |
 * | rss.refresh             |    YES     |    YES     |   this    |  Host*Proof  |     P1-5       |
 * | webdav.connect/upload/download/list/delete/mkdir | YES | YES | this | Host*Proof | P1-6 (UI de-demo) |
 * | backup.create/restore   |    YES     |    YES     |   this    |  Host*Proof  |     P1-6       |
 * | tts.system.* (5)        |     YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | permission.* (3)        |     YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | notification.* (3)      |     YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | share.invoke            |     YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | clipboard.copy/paste    |     YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | device.* (3)            |     YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | background.schedule/cancel |  YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | credential.get/set/delete |  YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | storage.path            |     YES     |  YES (ctx) |   N/A     |  Host*Proof  |     阶段 6     |
 * | credential.resolve      |     YES     |  YES (ctx) |   N/A     |  see below   |  GAP-D-01 closed |
 * | source.getVariable      |     YES     |    YES     |   this    |      N/A     |  Host/state fallback |
 * | source.setVariable      |     YES     |    YES     |   this    |      N/A     |  Host/state fallback |
 * | source.getLoginHeaderMap|     YES     |    YES     |   this    | login-store blocked | empty map fallback |
 *
 * "YES (ctx)" = registered only when `init(context)` is called with a non-null
 * Context (production / instrumented). JVM `init(null)` skips HostFacade
 * because TTS / Notification / Clipboard etc. require a real Android Context.
 *
 * `credential.resolve` (GAP-D-01 closed): production path now registers it
 * via `WebDavCredentialProvider` over `AppProvider.webDavCredentialStore`,
 * bridging `credentialHandle` → `WebDavCredentialStore.load()` →
 * `Credential{username, password}`. JVM tests pass context=null so it stays
 * unregistered on JVM — the test below asserts that gap-state for JVM only.
 *
 * `anti_bot.execute` is host-private: Core never emits it directly. The Host
 * re-dispatches intercepted `http.execute` requests through it when
 * anti-bot markers are detected. Registered so the host-internal dispatch
 * path is reachable, but not part of the Core-facing capability surface.
 */
class HostCapabilityRegistryJvmTest {

    companion object {
        @JvmStatic
        private lateinit var hostRuntime: HostRuntime

        @BeforeClass
        @JvmStatic
        fun setUp(): Unit {
            AppProvider.initForTesting()
            // JVM path: bypass ReaderCoreRuntime (which loads the native
            // `.so`) by calling buildHostRuntime directly with a fake
            // transport. This still exercises the SAME registration code the
            // production init() uses, so a regression in the capability
            // wiring fails this test.
            // context=null → in-memory fs/cache/persistence, HostFacade
            // (tts/permission/notification/...) NOT registered because those
            // adapters need a real Android Context.
            val fakeTransport = object : HostTransport {
                override fun pollEventJson(timeoutMillis: Long): String? = null
                override fun sendCommand(commandJson: String) {}
            }
            hostRuntime = ReaderCoreClient.buildHostRuntime(
                transport = fakeTransport,
                cookieStore = AppProvider.cookieStore,
                fs = InMemoryHostFileSystem(),
                cache = DefaultHostCache(),
                persistence = HostCachePersistenceAdapter(DefaultHostCache()),
                logger = DefaultHostLogger(),
                context = null
            )
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            if (::hostRuntime.isInitialized) {
                hostRuntime.stop()
            }
        }
    }

    private fun adapter() = hostRuntime.adapter()

    // ── Core Runtime capabilities (registered on JVM + device) ────────────

    @Test
    fun `http execute and cancel are registered`() {
        assertRegistered("http.execute")
        assertRegistered("http.cancel")
    }

    @Test
    fun `cookie get set clear are registered`() {
        assertRegistered("cookie.get")
        assertRegistered("cookie.set")
        assertRegistered("cookie.clear")
    }

    @Test
    fun `file read write delete are registered`() {
        assertRegistered("file.read")
        assertRegistered("file.write")
        assertRegistered("file.delete")
    }

    @Test
    fun `cache get put are registered`() {
        assertRegistered("cache.get")
        assertRegistered("cache.put")
    }

    @Test
    fun `persistence get put are registered`() {
        assertRegistered("persistence.get")
        assertRegistered("persistence.put")
    }

    @Test
    fun `source state compatibility fallbacks are registered`() {
        assertRegistered("source.getVariable")
        assertRegistered("source.setVariable")
        assertRegistered("source.getLoginHeaderMap")
    }

    @Test
    fun `runtime handlers log time system are registered`() {
        assertRegistered("log.emit")
        assertRegistered("time.now")
        assertRegistered("system.info")
    }

    @Test
    fun `webview evaluateJavaScript is registered`() {
        assertRegistered("webview.evaluateJavaScript")
    }

    @Test
    fun `media download is registered`() {
        assertRegistered("media.download")
    }

    @Test
    fun `anti_bot execute is registered as host-private capability`() {
        // Host-private: Core never emits this directly. Registered so the
        // Host can re-dispatch intercepted http.execute requests internally.
        assertRegistered("anti_bot.execute")
    }

    @Test
    fun `host smoke echo is registered`() {
        assertRegistered("host.smoke.echo")
    }

    // ── P1-5: Source / RSS capabilities (pure-JVM, registered on JVM + device) ──

    @Test
    fun `source crud and export capabilities are registered`() {
        assertRegistered("source.list")
        assertRegistered("source.add")
        assertRegistered("source.remove")
        assertRegistered("source.set_enabled")
        assertRegistered("source.import")
        assertRegistered("source.export")
    }

    @Test
    fun `source debug capabilities are registered`() {
        assertRegistered("source.debug.run")
        assertRegistered("source.debug.detect")
    }

    @Test
    fun `rss subscription and refresh capabilities are registered`() {
        assertRegistered("rss.subscription.list")
        assertRegistered("rss.subscription.add")
        assertRegistered("rss.subscription.delete")
        assertRegistered("rss.refresh")
    }

    // ── P1-6: WebDAV / backup capabilities (pure-JVM, registered on JVM + device) ──

    @Test
    fun `webdav file operation capabilities are registered`() {
        assertRegistered("webdav.connect")
        assertRegistered("webdav.upload")
        assertRegistered("webdav.download")
        assertRegistered("webdav.list")
        assertRegistered("webdav.delete")
        assertRegistered("webdav.mkdir")
    }

    @Test
    fun `backup create and restore capabilities are registered`() {
        assertRegistered("backup.create")
        assertRegistered("backup.restore")
    }

    // ── HostFacade capabilities (NOT registered on JVM — require Context) ─

    @Test
    fun `tts permission notification capabilities are NOT registered on JVM`() {
        // init(null) skips HostFacade; these require a real Android Context.
        assertNotRegistered("tts.system.start")
        assertNotRegistered("tts.system.stop")
        assertNotRegistered("permission.check")
        assertNotRegistered("notification.show")
        assertNotRegistered("share.invoke")
        assertNotRegistered("clipboard.copy")
        assertNotRegistered("device.vibrate")
        assertNotRegistered("background.schedule")
        assertNotRegistered("credential.get")
        assertNotRegistered("storage.path")
    }

    // ── credential.resolve: JVM context=null → not registered (production path registers it) ───

    @Test
    fun `credential resolve is NOT registered on JVM because context is null`() {
        // Production path (context != null) now registers credential.resolve
        // via WebDavCredentialProvider over AppProvider.webDavCredentialStore
        // (GAP-D-01 closed). JVM tests pass context=null, so the HostFacade
        // block (which includes the credential.resolve registration) is
        // skipped — this test asserts that gap-state for the JVM path only.
        assertNotRegistered("credential.resolve")
    }

    // ── Dispatch reachability: each registered capability returns a reply ─

    @Test
    fun `dispatch to unregistered capability returns INTERNAL error`() {
        val reply = adapter().dispatch(
            HostRequest(1L, 1L, "totally.unknown.capability", "{}")
        )
        assertTrue("must error", reply.isError())
        val error = reply as HostReply.Error
        assertEquals("INTERNAL", error.code())
        assertTrue(
            "expected 'unsupported capability' in message: ${error.message()}",
            error.message().contains("unsupported capability")
        )
    }

    @Test
    fun `dispatch to registered capability returns a non-null reply`() {
        // Pick a safe, side-effect-free capability: host.smoke.echo.
        val reply = adapter().dispatch(
            HostRequest(1L, 2L, "host.smoke.echo", JSONObject().put("message", "ping").toString())
        )
        assertNotNull(reply)
        // Smoke echo should complete (not error) on JVM.
        assertTrue("smoke.echo must complete: ${reply?.kind()}", reply!!.isComplete())
    }

    @Test
    fun `file delete dispatch round-trips through registered adapter`() {
        // Prove file.delete is reachable via the registered adapter (not just
        // via a direct FileDeleteHandler construction).
        val reply = adapter().dispatch(
            HostRequest(1L, 3L, "file.delete",
                JSONObject().put("path", "proof/nonexistent-${System.nanoTime()}.txt").toString())
        )
        assertTrue("must complete", reply!!.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        // Deleting a non-existent file returns deleted=false (not an error).
        assertFalse("deleted should be false for non-existent file", result.getBoolean("deleted"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun assertRegistered(capability: String) {
        assertTrue(
            "$capability must be registered in ReaderCoreClient.init(context=null)",
            adapter().isRegistered(capability)
        )
    }

    private fun assertNotRegistered(capability: String) {
        assertFalse(
            "$capability must NOT be registered (JVM context=null or gap)",
            adapter().isRegistered(capability)
        )
    }
}
