package com.reader.host

import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.AndroidTtsAdapter
import com.reader.android.data.adapter.AuthMethod
import com.reader.android.data.adapter.CookieRecord
import com.reader.android.data.adapter.CookieStore
import com.reader.android.data.adapter.FakeAndroidTtsAdapter
import com.reader.android.data.adapter.FakeCookieStore
import com.reader.android.data.adapter.FakePermissionRuntimeAdapter
import com.reader.android.data.adapter.PermissionRuntimeAdapter
import com.reader.android.data.adapter.WebDavCredential
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the Phase 2 host capability handlers that close the
 * remaining Reader UI contract gaps:
 *  - `host.smoke.echo` (registered into production init)
 *  - `http.cancel` + `HttpCallRegistry` (cancel in-flight OkHttp calls)
 *  - `cookie.get` / `cookie.set` compatibility params
 *  - `cookie.clear` (clear scoped / all cookies)
 *  - `source.getVariable` / `source.setVariable` fallback persistence
 *  - `storage.path` (fail-closed on JVM; instrumented tier covers real paths)
 *  - `credential.get` / `credential.set` / `credential.delete`
 *  - `background.schedule` / `background.cancel` (in-memory registry)
 *  - `notification.cancel` (fail-closed on JVM)
 *  - `device.screen.release` (acknowledgement handler)
 *
 * JVM tests cover the logic that does NOT require a real Android `Context`:
 * params parsing, registry semantics, credential envelope round-trip, and
 * fail-closed branches. Real `Context`-backed paths are exercised at the
 * instrumented tier.
 */
class Phase2HostCapabilityHandlersJvmTest {

    private fun makeFacade(
        cookieStore: CookieStore = FakeCookieStore()
    ): HostFacade = HostFacade(
        context = null,
        tts = FakeAndroidTtsAdapter(),
        permission = FakePermissionRuntimeAdapter(
            mapOf(PermissionKind.NOTIFICATIONS to PermissionStatus.GRANTED)
        ),
        notification = null,
        webDav = null,
        credentials = WebDavCredentialStore(Phase2FakeCredentialKeystore()),
        downloadCache = null,
        readerUi25Services = ReaderUi25HostServices(
            backgroundTasks = object : ReaderBackgroundTaskHost {
                override fun start(name: String) = ReaderBackgroundTaskStartResult("host-$name")
                override fun end(taskId: String) = true
            }
        )
    )

    // ── host.smoke.echo ──────────────────────────────────────────────────

    @Test
    fun `host_smoke_echo echoes params back as complete`() {
        val params = JSONObject().apply {
            put("ping", "pong"); put("n", 42)
        }.toString()
        val reply = HostSmokeEchoHandler().handle(
            HostRequest(1L, 1001L, HostSmokeEchoHandler.CAPABILITY, params)
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals("pong", result.getString("ping"))
        assertEquals(42, result.getInt("n"))
    }

    // ── HttpCallRegistry + http.cancel ──────────────────────────────────

    @Test
    fun `http_call_registry register_cancel_complete behaves correctly`() {
        val registry = HttpCallRegistry()
        val client = OkHttpClient()
        val call1 = client.newCall(Request.Builder().url("http://localhost:1/a").build())
        val call2 = client.newCall(Request.Builder().url("http://localhost:1/b").build())

        assertEquals(0, registry.size())
        registry.register("tag-1", call1)
        registry.register("tag-2", call2)
        assertEquals(2, registry.size())

        assertTrue("cancel should succeed for in-flight tag", registry.cancel("tag-1"))
        assertTrue("call must be cancelled after registry.cancel", call1.isCanceled())
        assertFalse("unrelated call must remain uncancelled", call2.isCanceled())
        assertEquals(1, registry.size())

        registry.complete("tag-2")
        assertEquals(0, registry.size())
        assertFalse("completing an already-removed tag is a no-op", registry.cancel("tag-2"))
    }

    @Test
    fun `http_cancel_handler returns cancelled=true when tag is registered`() {
        val registry = HttpCallRegistry()
        val client = OkHttpClient()
        val call = client.newCall(Request.Builder().url("http://localhost:1/x").build())
        registry.register("in-flight-1", call)

        val reply = HttpCancelHandler(registry).handle(
            HostRequest(1L, 2001L, HttpCancelHandler.CAPABILITY,
                JSONObject().put("requestId", "in-flight-1").toString())
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue("cancelled should be true", result.getBoolean("cancelled"))
        assertFalse(result.has("requestTag"))
        assertTrue("call must be cancelled", call.isCanceled())
    }

    @Test
    fun `http_cancel_handler accepts canonical requestId`() {
        val registry = HttpCallRegistry()
        val reply = HttpCancelHandler(registry).handle(
            HostRequest(1L, 2002L, HttpCancelHandler.CAPABILITY,
                JSONObject().put("requestId", "missing-tag").toString())
        )
        assertTrue(reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertFalse("cancelled should be false for unknown tag", result.getBoolean("cancelled"))
        assertFalse(result.has("requestTag"))
    }

    @Test
    fun `http_cancel_handler rejects missing tag with INTERNAL`() {
        val registry = HttpCallRegistry()
        val reply = HttpCancelHandler(registry).handle(
            HostRequest(1L, 2003L, HttpCancelHandler.CAPABILITY, "{}")
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
        assertFalse(reply.retryable())
    }

    @Test
    fun `http_cancel_handler rejects malformed params json`() {
        val registry = HttpCallRegistry()
        val reply = HttpCancelHandler(registry).handle(
            HostRequest(1L, 2004L, HttpCancelHandler.CAPABILITY, "not-json")
        )
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    // ── cookie.get / cookie.set compatibility ───────────────────────────

    @Test
    fun `cookie_set accepts legado header string and cookie_get filters by domain and name`() {
        val store = FakeCookieStore()
        val setReply = CookieSetHandler(store).handle(
            HostRequest(1L, 2501L, CookieSetHandler.CAPABILITY,
                JSONObject()
                    .put("url", "https://login.example.test/auth")
                    .put("cookie", "sid=abc123; Domain=login.example.test; Path=/; HttpOnly")
                    .toString())
        )
        assertTrue("cookie.set must complete", setReply.isComplete())

        val getReply = CookieGetHandler(store).handle(
            HostRequest(1L, 2502L, CookieGetHandler.CAPABILITY,
                JSONObject()
                    .put("domain", "login.example.test")
                    .put("name", "sid")
                    .toString())
        )
        assertTrue("cookie.get must complete", getReply.isComplete())
        val result = JSONObject((getReply as HostReply.Complete).resultJson())
        val cookies = result.getJSONArray("cookies")
        assertEquals(1, cookies.length())
        assertEquals("sid", cookies.getJSONObject(0).getString("name"))
        assertEquals("abc123", cookies.getJSONObject(0).getString("value"))
    }

    // ── source state compatibility fallbacks ────────────────────────────

    @Test
    fun `source_variable handlers persist across handler recreation`() {
        val persistence = HostCachePersistenceAdapter(DefaultHostCache())
        val firstStore = SourceVariableStore(persistence)
        val setReply = SourceSetVariableHandler(firstStore).handle(
            HostRequest(1L, 2601L, SourceSetVariableHandler.CAPABILITY,
                JSONObject()
                    .put("sourceId", "src-045")
                    .put("key", "loginToken")
                    .put("value", "tk-123")
                    .toString())
        )
        assertTrue("source.setVariable must complete", setReply.isComplete())

        val recreatedStore = SourceVariableStore(persistence)
        val getReply = SourceGetVariableHandler(recreatedStore).handle(
            HostRequest(1L, 2602L, SourceGetVariableHandler.CAPABILITY,
                JSONObject()
                    .put("sourceId", "src-045")
                    .put("key", "loginToken")
                    .toString())
        )
        assertTrue("source.getVariable must complete", getReply.isComplete())
        val result = JSONObject((getReply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("found"))
        assertEquals("tk-123", result.getString("value"))
    }

    @Test
    fun `source_login_header_map returns empty result instead of unsupported failure`() {
        val reply = SourceLoginHeaderMapHandler().handle(
            HostRequest(1L, 2603L, SourceLoginHeaderMapHandler.CAPABILITY,
                JSONObject().put("sourceId", "src-367").toString())
        )
        assertTrue("source.getLoginHeaderMap must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertFalse("no Android login header store is wired yet", result.getBoolean("found"))
        assertEquals(0, result.getJSONObject("headers").length())
    }

    // ── cookie.clear ────────────────────────────────────────────────────

    @Test
    fun `cookie_clear clears all cookies when url absent`() = kotlinx.coroutines.runBlocking {
        val store = FakeCookieStore()
        store.save("https://a.example.com", listOf(
            CookieRecord("s", "1", "a.example.com"),
            CookieRecord("t", "2", "a.example.com")
        ))
        store.save("https://b.example.com", listOf(
            CookieRecord("u", "3", "b.example.com")
        ))

        val reply = CookieClearHandler(store).handle(
            HostRequest(1L, 3001L, CookieClearHandler.CAPABILITY, "{}")
        )
        assertTrue(reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("cleared"))
        assertFalse(result.has("scope"))
        // Both scopes must be empty now.
        assertTrue(store.get("https://a.example.com").cookies.isEmpty())
        assertTrue(store.get("https://b.example.com").cookies.isEmpty())
    }

    @Test
    fun `cookie_clear clears only the scoped url when url present`() = kotlinx.coroutines.runBlocking {
        val store = FakeCookieStore()
        store.save("https://a.example.com", listOf(CookieRecord("s", "1", "a.example.com")))
        store.save("https://b.example.com", listOf(CookieRecord("u", "2", "b.example.com")))

        val reply = CookieClearHandler(store).handle(
            HostRequest(1L, 3002L, CookieClearHandler.CAPABILITY,
                JSONObject().put("url", "https://a.example.com").toString())
        )
        assertTrue(reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("cleared"))
        assertFalse(result.has("scope"))
        // Only the scoped url is cleared; the other scope retains its cookie.
        assertTrue(store.get("https://a.example.com").cookies.isEmpty())
        assertEquals(1, store.get("https://b.example.com").cookies.size)
    }

    @Test
    fun `cookie_clear rejects malformed params json`() {
        val store = FakeCookieStore()
        val reply = CookieClearHandler(store).handle(
            HostRequest(1L, 3003L, CookieClearHandler.CAPABILITY, "{not json")
        )
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    // ── storage.path ────────────────────────────────────────────────────

    @Test
    fun `storage_path fails closed when context is null (JVM)`() {
        val facade = makeFacade()
        val reply = StoragePathHandler(facade).handle(
            HostRequest(1L, 4001L, StoragePathHandler.CAPABILITY, "{}")
        )
        assertTrue("must error without context", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `storage_path rejects malformed params`() {
        val facade = makeFacade()
        val reply = StoragePathHandler(facade).handle(
            HostRequest(1L, 4002L, StoragePathHandler.CAPABILITY, "not-json")
        )
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    // ── credential.get / set / delete ──────────────────────────────────

    @Test
    fun `credential_set and get round trip canonical opaque value`() {
        val facade = makeFacade()
        val setReply = CredentialSetHandler(facade).handle(
            HostRequest(1L, 5001L, CredentialSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("key", "webdav.password")
                    put("value", "secret")
                }.toString())
        )
        assertTrue("set must complete", setReply.isComplete())
        val setResult = JSONObject((setReply as HostReply.Complete).resultJson())
        assertTrue(setResult.getBoolean("stored"))
        assertFalse(setResult.has("identifier"))

        val getReply = CredentialGetHandler(facade).handle(
            HostRequest(1L, 5002L, CredentialGetHandler.CAPABILITY,
                JSONObject().put("key", "webdav.password").toString())
        )
        assertTrue("get must complete", getReply.isComplete())
        val getResult = JSONObject((getReply as HostReply.Complete).resultJson())
        assertTrue(getResult.getBoolean("exists"))
        assertEquals("secret", getResult.getString("value"))
    }

    @Test
    fun `credential_set supports arbitrary opaque string values`() {
        val facade = makeFacade()
        val reply = CredentialSetHandler(facade).handle(
            HostRequest(1L, 5003L, CredentialSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("key", "api.token")
                    put("value", "tok-xyz")
                }.toString())
        )
        assertTrue(reply.isComplete())
        val getReply = CredentialGetHandler(facade).handle(
            HostRequest(1L, 5004L, CredentialGetHandler.CAPABILITY,
                JSONObject().put("key", "api.token").toString())
        )
        assertTrue(getReply.isComplete())
        val result = JSONObject((getReply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("exists"))
        assertEquals("tok-xyz", result.getString("value"))
    }

    @Test
    fun `credential_set rejects basic auth without username or password`() {
        val facade = makeFacade()
        val reply = CredentialSetHandler(facade).handle(
            HostRequest(1L, 5005L, CredentialSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("identifier", "x"); put("serverUrl", "https://x.example.com")
                    put("authType", "basic"); put("username", "u")
                    // missing password
                }.toString())
        )
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `credential_set rejects unknown authType`() {
        val facade = makeFacade()
        val reply = CredentialSetHandler(facade).handle(
            HostRequest(1L, 5006L, CredentialSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("identifier", "x"); put("serverUrl", "https://x.example.com")
                    put("authType", "unknown")
                }.toString())
        )
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `credential_set rejects missing identifier`() {
        val facade = makeFacade()
        val reply = CredentialSetHandler(facade).handle(
            HostRequest(1L, 5007L, CredentialSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("serverUrl", "https://x.example.com"); put("authType", "bearer")
                    put("token", "t")
                }.toString())
        )
        assertTrue(reply.isError())
    }

    @Test
    fun `credential_get returns exists false for missing key`() {
        val facade = makeFacade()
        val reply = CredentialGetHandler(facade).handle(
            HostRequest(1L, 5008L, CredentialGetHandler.CAPABILITY,
                JSONObject().put("key", "never-saved").toString())
        )
        assertTrue(reply.isComplete())
        assertFalse(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("exists"))
    }

    @Test
    fun `credential_delete revokes previously stored credential`() {
        val facade = makeFacade()
        // Set then delete
        CredentialSetHandler(facade).handle(
            HostRequest(1L, 5009L, CredentialSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("key", "to-delete"); put("value", "t")
                }.toString())
        )
        val delReply = CredentialDeleteHandler(facade).handle(
            HostRequest(1L, 5010L, CredentialDeleteHandler.CAPABILITY,
                JSONObject().put("key", "to-delete").toString())
        )
        assertTrue(delReply.isComplete())
        val result = JSONObject((delReply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("deleted"))
        // Subsequent get is a canonical miss.
        val getReply = CredentialGetHandler(facade).handle(
            HostRequest(1L, 5011L, CredentialGetHandler.CAPABILITY,
                JSONObject().put("key", "to-delete").toString())
        )
        assertTrue(getReply.isComplete())
        assertFalse(JSONObject((getReply as HostReply.Complete).resultJson()).getBoolean("exists"))
    }

    @Test
    fun `credential_delete on absent identifier returns deleted=true (no-op success)`() {
        // WebDavCredentialStore.revoke() treats a missing identifier as a
        // no-op success (returns true) because the end-state — identifier
        // is no longer present — matches the caller's intent. The handler
        // passes that through verbatim.
        val facade = makeFacade()
        val reply = CredentialDeleteHandler(facade).handle(
            HostRequest(1L, 5012L, CredentialDeleteHandler.CAPABILITY,
                JSONObject().put("key", "absent").toString())
        )
        assertTrue(reply.isComplete())
        assertTrue(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("deleted"))
    }

    // ── background.schedule / background.cancel ────────────────────────

    @Test
    fun `background_schedule registers task in registry and acknowledges`() {
        val facade = makeFacade()
        val reply = BackgroundScheduleHandler(facade).handle(
            HostRequest(1L, 6001L, BackgroundScheduleHandler.CAPABILITY,
                JSONObject().apply {
                    put("taskId", "download-book-1")
                }.toString())
        )
        assertTrue(reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("scheduled"))
        assertFalse(result.has("taskTag"))
        assertFalse(result.has("kind"))
        assertEquals(1, facade.backgroundRegistry.size())
        assertEquals("download-book-1", facade.backgroundRegistry.list().first().tag)
    }

    @Test
    fun `background_schedule rejects missing taskId`() {
        val facade = makeFacade()
        val reply = BackgroundScheduleHandler(facade).handle(
            HostRequest(1L, 6002L, BackgroundScheduleHandler.CAPABILITY, "{}")
        )
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `background_cancel cancels previously scheduled task`() {
        val facade = makeFacade()
        facade.backgroundRegistry.schedule("download-book-2", "workmanager", "host-download-book-2")
        val reply = BackgroundCancelHandler(facade).handle(
            HostRequest(1L, 6003L, BackgroundCancelHandler.CAPABILITY,
                JSONObject().put("taskId", "download-book-2").toString())
        )
        assertTrue(reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("cancelled"))
        assertEquals(0, facade.backgroundRegistry.size())
    }

    @Test
    fun `background_cancel on unknown tag returns cancelled=false`() {
        val facade = makeFacade()
        val reply = BackgroundCancelHandler(facade).handle(
            HostRequest(1L, 6004L, BackgroundCancelHandler.CAPABILITY,
                JSONObject().put("taskId", "missing").toString())
        )
        assertTrue(reply.isComplete())
        assertFalse(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("cancelled"))
    }

    @Test
    fun `background_schedule replaces existing task with same tag`() {
        val facade = makeFacade()
        facade.backgroundRegistry.schedule("dup", "download")
        facade.backgroundRegistry.schedule("dup", "upload")
        assertEquals(1, facade.backgroundRegistry.size())
        assertEquals("upload", facade.backgroundRegistry.list().first().kind)
    }

    // ── notification.cancel (fail-closed on JVM) ───────────────────────

    @Test
    fun `notification_cancel fails closed when context is null`() {
        val facade = makeFacade()
        val reply = NotificationCancelHandler(facade).handle(
            HostRequest(1L, 7001L, NotificationCancelHandler.CAPABILITY, "{}")
        )
        assertTrue("must error without context", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `notification_cancel rejects malformed params`() {
        val facade = makeFacade()
        val reply = NotificationCancelHandler(facade).handle(
            HostRequest(1L, 7002L, NotificationCancelHandler.CAPABILITY, "not-json")
        )
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    // ── device.screen.release ──────────────────────────────────────────

    @Test
    fun `device_screen_release fails closed without foreground Activity`() {
        val facade = makeFacade()
        val reply = DeviceScreenReleaseHandler(facade).handle(
            HostRequest(1L, 8001L, DeviceScreenReleaseHandler.CAPABILITY, "{}")
        )
        assertTrue(reply.isError())
        assertEquals("REQUIRES_UI_CONTEXT", (reply as HostReply.Error).code())
    }

    @Test
    fun `device_screen_release never acknowledges unavailable platform work`() {
        val facade = makeFacade()
        val reply = DeviceScreenReleaseHandler(facade).handle(
            HostRequest(1L, 8002L, DeviceScreenReleaseHandler.CAPABILITY,
                JSONObject().put("anything", "ignored").toString())
        )
        assertTrue(reply.isError())
    }
}

/**
 * Fake [WebDavCredentialStore.CredentialKeystore] for JVM tests — avoids
 * the real AndroidKeystore which is unavailable on JVM.
 *
 * Named `Phase2FakeCredentialKeystore` to avoid colliding with the
 * same-named private double in [HostFacadeCapabilityHandlersJvmTest].
 */
private class Phase2FakeCredentialKeystore : WebDavCredentialStore.CredentialKeystore {
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
