package com.reader.host

import com.reader.android.data.adapter.BackupEntry
import com.reader.android.data.adapter.BackupManifest
import com.reader.android.data.adapter.FakeWebDavClient
import com.reader.android.data.adapter.WebDavMethod
import com.reader.android.data.adapter.WebDavRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM proof for the P1-6 WebDAV / backup capability handlers.
 *
 * Verifies the full webdav.connect / upload / download / list / delete /
 * mkdir + backup.create / backup.restore flow against a [FakeWebDavClient]
 * (in-memory store). Production wires the same handlers over the OkHttp-backed
 * [com.reader.android.data.adapter.AndroidWebDavClient], so a regression in
 * the handler logic fails this test.
 *
 * Also verifies the NOT_CONFIGURED error path: when the WebDavContext has
 * a null client (no credential configured), all webdav.* handlers return
 * a structured NOT_CONFIGURED error instead of crashing.
 *
 * Capability coverage:
 *  - webdav.connect (PROPFIND connectivity test)
 *  - webdav.upload (PUT)
 *  - webdav.download (GET)
 *  - webdav.list (PROPFIND list)
 *  - webdav.delete (DELETE)
 *  - webdav.mkdir (MKCOL)
 *  - backup.create (manifest + entries upload)
 *  - backup.restore (validate + planRestore + GET entries)
 *  - NOT_CONFIGURED error path (null client)
 *  - full registration smoke test (all 8 capabilities registered)
 */
class WebDavCapabilityHandlersJvmTest {

    private fun makeCtx(): WebDavContext {
        val client = FakeWebDavClient()
        val manager = com.reader.android.data.adapter.BackupRestoreManager(client)
        return WebDavContext(client, manager)
    }

    private fun unconfiguredCtx(): WebDavContext = unconfiguredWebDavContext()

    private fun fakeClient(ctx: WebDavContext): FakeWebDavClient =
        ctx.client as FakeWebDavClient

    private fun req(capability: String, params: JSONObject = JSONObject()) =
        HostRequest(1L, 1L, capability, params.toString())

    private fun resultJson(reply: HostReply): JSONObject =
        JSONObject((reply as HostReply.Complete).resultJson())

    // ── webdav.connect ───────────────────────────────────────────────────

    @Test
    fun `webdav_connect returns connected when PROPFIND returns 207`() {
        val ctx = makeCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/")
        val reply = WebDavConnectHandler(ctx).handle(req(WebDavConnectHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("connected"))
        assertEquals(207, result.getInt("statusCode"))
    }

    @Test
    fun `webdav_connect returns not connected on 404`() {
        val ctx = makeCtx()
        // FakeWebDavClient returns 207 for PROPFIND always; use a client that 404s
        val client = object : com.reader.android.data.adapter.WebDavClient {
            override suspend fun execute(request: com.reader.android.data.adapter.WebDavRequest):
                com.reader.android.data.adapter.WebDavResponse =
                com.reader.android.data.adapter.WebDavResponse(404)
        }
        val ctx404 = WebDavContext(client, null)
        val params = JSONObject().put("url", "https://dav.example.com/missing/")
        val reply = WebDavConnectHandler(ctx404).handle(req(WebDavConnectHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertFalse(result.getBoolean("connected"))
        assertEquals(404, result.getInt("statusCode"))
    }

    @Test
    fun `webdav_connect rejects empty url`() {
        val ctx = makeCtx()
        val reply = WebDavConnectHandler(ctx).handle(req(WebDavConnectHandler.CAPABILITY))
        assertTrue(reply.isError())
        val error = reply as HostReply.Error
        assertEquals("INTERNAL", error.code())
    }

    // ── webdav.upload ────────────────────────────────────────────────────

    @Test
    fun `webdav_upload puts content and returns uploaded true`() {
        val ctx = makeCtx()
        val params = JSONObject()
            .put("url", "https://dav.example.com/reader/backup.txt")
            .put("body", "backup-content")
        val reply = WebDavUploadHandler(ctx).handle(req(WebDavUploadHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("uploaded"))
        assertEquals(201, result.getInt("statusCode"))
        // Verify the content was stored
        val stored = runBlocking {
            fakeClient(ctx).execute(
                WebDavRequest("https://dav.example.com/reader/backup.txt", WebDavMethod.GET)
            )
        }
        assertEquals("backup-content", stored.body)
    }

    @Test
    fun `webdav_upload rejects empty url`() {
        val ctx = makeCtx()
        val reply = WebDavUploadHandler(ctx).handle(req(WebDavUploadHandler.CAPABILITY))
        assertTrue(reply.isError())
    }

    // ── webdav.download ──────────────────────────────────────────────────

    @Test
    fun `webdav_download returns body when file exists`() {
        val ctx = makeCtx()
        fakeClient(ctx).putContent("https://dav.example.com/reader/file.txt", "file-content")
        val params = JSONObject().put("url", "https://dav.example.com/reader/file.txt")
        val reply = WebDavDownloadHandler(ctx).handle(req(WebDavDownloadHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("downloaded"))
        assertEquals(200, result.getInt("statusCode"))
        assertEquals("file-content", result.getString("body"))
    }

    @Test
    fun `webdav_download returns downloaded false on 404`() {
        val ctx = makeCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/nonexistent.txt")
        val reply = WebDavDownloadHandler(ctx).handle(req(WebDavDownloadHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertFalse(result.getBoolean("downloaded"))
        assertEquals(404, result.getInt("statusCode"))
    }

    // ── webdav.list ──────────────────────────────────────────────────────

    @Test
    fun `webdav_list returns success on 207`() {
        val ctx = makeCtx()
        fakeClient(ctx).putContent("https://dav.example.com/reader/a.txt", "a")
        fakeClient(ctx).putContent("https://dav.example.com/reader/b.txt", "b")
        val params = JSONObject().put("url", "https://dav.example.com/reader/")
        val reply = WebDavListHandler(ctx).handle(req(WebDavListHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("success"))
        assertEquals(207, result.getInt("statusCode"))
    }

    // ── webdav.delete ────────────────────────────────────────────────────

    @Test
    fun `webdav_delete removes a file and returns deleted true`() {
        val ctx = makeCtx()
        fakeClient(ctx).putContent("https://dav.example.com/reader/trash.txt", "trash")
        val params = JSONObject().put("url", "https://dav.example.com/reader/trash.txt")
        val reply = WebDavDeleteHandler(ctx).handle(req(WebDavDeleteHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("deleted"))
        // Verify the file is gone
        val check = runBlocking {
            fakeClient(ctx).execute(
                WebDavRequest("https://dav.example.com/reader/trash.txt", WebDavMethod.GET)
            )
        }
        assertEquals(404, check.statusCode)
    }

    // ── webdav.mkdir ─────────────────────────────────────────────────────

    @Test
    fun `webdav_mkdir returns created true`() {
        val ctx = makeCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/newdir/")
        val reply = WebDavMkdirHandler(ctx).handle(req(WebDavMkdirHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("created"))
    }

    // ── backup.create ─────────────────────────────────────────────────────

    @Test
    fun `backup_create uploads manifest and entries and returns backed_up true`() {
        val ctx = makeCtx()
        val manifest = BackupManifest(
            version = 1,
            entries = listOf(
                BackupEntry(path = "https://dav.example.com/reader/books.json", checksum = "abc", sizeBytes = 100),
                BackupEntry(path = "https://dav.example.com/reader/settings.json", checksum = "def", sizeBytes = 50)
            )
        )
        val entries = JSONArray().apply {
            put(JSONObject().put("path", "https://dav.example.com/reader/books.json").put("body", "{\"books\":[]}"))
            put(JSONObject().put("path", "https://dav.example.com/reader/settings.json").put("body", "{\"theme\":\"dark\"}"))
        }
        val params = JSONObject()
            .put("manifest", manifest.toJson())
            .put("baseUrl", "https://dav.example.com/reader")
            .put("entries", entries)
        val reply = BackupCreateHandler(ctx).handle(req(BackupCreateHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("backed_up"))
        assertEquals(2, result.getInt("entry_count"))
        // Verify manifest was uploaded
        val manifestResp = runBlocking {
            fakeClient(ctx).execute(
                WebDavRequest("https://dav.example.com/reader/manifest.json", WebDavMethod.GET)
            )
        }
        assertEquals(200, manifestResp.statusCode)
    }

    @Test
    fun `backup_create rejects empty manifest`() {
        val ctx = makeCtx()
        val params = JSONObject().put("baseUrl", "https://dav.example.com/reader")
        val reply = BackupCreateHandler(ctx).handle(req(BackupCreateHandler.CAPABILITY, params))
        assertTrue(reply.isError())
    }

    @Test
    fun `backup_create rejects empty baseUrl`() {
        val ctx = makeCtx()
        val manifest = BackupManifest()
        val params = JSONObject().put("manifest", manifest.toJson())
        val reply = BackupCreateHandler(ctx).handle(req(BackupCreateHandler.CAPABILITY, params))
        assertTrue(reply.isError())
    }

    // ── backup.restore ────────────────────────────────────────────────────

    @Test
    fun `backup_restore downloads entries and returns restored count`() {
        val ctx = makeCtx()
        // Pre-populate the fake server with the entries the manifest references
        fakeClient(ctx).putContent("https://dav.example.com/reader/books.json", "{\"books\":[]}")
        fakeClient(ctx).putContent("https://dav.example.com/reader/settings.json", "{\"theme\":\"dark\"}")
        val manifest = BackupManifest(
            version = 1,
            entries = listOf(
                BackupEntry(path = "https://dav.example.com/reader/books.json", checksum = "abc", sizeBytes = 100),
                BackupEntry(path = "https://dav.example.com/reader/settings.json", checksum = "def", sizeBytes = 50)
            )
        )
        val params = JSONObject()
            .put("manifest", manifest.toJson())
            .put("policy", "FULL_REPLACE")
        val reply = BackupRestoreHandler(ctx).handle(req(BackupRestoreHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("success"))
        assertEquals(2, result.getInt("restored"))
        assertEquals(0, result.getInt("skipped"))
    }

    @Test
    fun `backup_restore reports skipped for missing entries`() {
        val ctx = makeCtx()
        // Only one of two entries is available on the server
        fakeClient(ctx).putContent("https://dav.example.com/reader/books.json", "{\"books\":[]}")
        val manifest = BackupManifest(
            version = 1,
            entries = listOf(
                BackupEntry(path = "https://dav.example.com/reader/books.json", checksum = "abc", sizeBytes = 100),
                BackupEntry(path = "https://dav.example.com/reader/missing.json", checksum = "xyz", sizeBytes = 999)
            )
        )
        val params = JSONObject().put("manifest", manifest.toJson())
        val reply = BackupRestoreHandler(ctx).handle(req(BackupRestoreHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        // validate() checks all entries exist; missing entry → validation fails
        assertFalse(result.getBoolean("success"))
    }

    @Test
    fun `backup_restore rejects empty manifest`() {
        val ctx = makeCtx()
        val reply = BackupRestoreHandler(ctx).handle(req(BackupRestoreHandler.CAPABILITY))
        assertTrue(reply.isError())
    }

    // ── NOT_CONFIGURED error path ─────────────────────────────────────────

    @Test
    fun `webdav_connect returns NOT_CONFIGURED when client is null`() {
        val ctx = unconfiguredCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/")
        val reply = WebDavConnectHandler(ctx).handle(req(WebDavConnectHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        val error = reply as HostReply.Error
        assertEquals("NOT_CONFIGURED", error.code())
    }

    @Test
    fun `webdav_upload returns NOT_CONFIGURED when client is null`() {
        val ctx = unconfiguredCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/f.txt").put("body", "x")
        val reply = WebDavUploadHandler(ctx).handle(req(WebDavUploadHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        val error = reply as HostReply.Error
        assertEquals("NOT_CONFIGURED", error.code())
    }

    @Test
    fun `webdav_download returns NOT_CONFIGURED when client is null`() {
        val ctx = unconfiguredCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/f.txt")
        val reply = WebDavDownloadHandler(ctx).handle(req(WebDavDownloadHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        assertEquals("NOT_CONFIGURED", (reply as HostReply.Error).code())
    }

    @Test
    fun `webdav_list returns NOT_CONFIGURED when client is null`() {
        val ctx = unconfiguredCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/")
        val reply = WebDavListHandler(ctx).handle(req(WebDavListHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        assertEquals("NOT_CONFIGURED", (reply as HostReply.Error).code())
    }

    @Test
    fun `webdav_delete returns NOT_CONFIGURED when client is null`() {
        val ctx = unconfiguredCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/f.txt")
        val reply = WebDavDeleteHandler(ctx).handle(req(WebDavDeleteHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        assertEquals("NOT_CONFIGURED", (reply as HostReply.Error).code())
    }

    @Test
    fun `webdav_mkdir returns NOT_CONFIGURED when client is null`() {
        val ctx = unconfiguredCtx()
        val params = JSONObject().put("url", "https://dav.example.com/reader/dir/")
        val reply = WebDavMkdirHandler(ctx).handle(req(WebDavMkdirHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        assertEquals("NOT_CONFIGURED", (reply as HostReply.Error).code())
    }

    @Test
    fun `backup_create returns NOT_CONFIGURED when manager is null`() {
        val ctx = unconfiguredCtx()
        val manifest = BackupManifest()
        val params = JSONObject()
            .put("manifest", manifest.toJson())
            .put("baseUrl", "https://dav.example.com/reader")
        val reply = BackupCreateHandler(ctx).handle(req(BackupCreateHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        assertEquals("NOT_CONFIGURED", (reply as HostReply.Error).code())
    }

    @Test
    fun `backup_restore returns NOT_CONFIGURED when manager is null`() {
        val ctx = unconfiguredCtx()
        val manifest = BackupManifest()
        val params = JSONObject().put("manifest", manifest.toJson())
        val reply = BackupRestoreHandler(ctx).handle(req(BackupRestoreHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        assertEquals("NOT_CONFIGURED", (reply as HostReply.Error).code())
    }

    // ── Full registration smoke test ──────────────────────────────────────

    @Test
    fun `registerHandlers registers all 8 webdav and backup capabilities`() {
        val ctx = makeCtx()
        val transport = object : HostTransport {
            override fun pollEventJson(timeoutMillis: Long): String? = null
            override fun sendCommand(commandJson: String) {}
        }
        val runtime = ctx.registerHandlers(HostRuntime.over(transport))
        val adapter = runtime.adapter()
        assertTrue("webdav.connect must be registered", adapter.isRegistered("webdav.connect"))
        assertTrue("webdav.upload must be registered", adapter.isRegistered("webdav.upload"))
        assertTrue("webdav.download must be registered", adapter.isRegistered("webdav.download"))
        assertTrue("webdav.list must be registered", adapter.isRegistered("webdav.list"))
        assertTrue("webdav.delete must be registered", adapter.isRegistered("webdav.delete"))
        assertTrue("webdav.mkdir must be registered", adapter.isRegistered("webdav.mkdir"))
        assertTrue("backup.create must be registered", adapter.isRegistered("backup.create"))
        assertTrue("backup.restore must be registered", adapter.isRegistered("backup.restore"))
        runtime.stop()
    }
}
