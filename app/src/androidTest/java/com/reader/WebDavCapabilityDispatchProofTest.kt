package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.data.adapter.BackupEntry
import com.reader.android.data.adapter.BackupManifest
import com.reader.android.data.adapter.FakeWebDavClient
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.HostRuntime
import com.reader.host.HostTransport
import com.reader.host.fakeWebDavContext
import com.reader.host.registerHandlers
import com.reader.host.unconfiguredWebDavContext
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1-6 device-level dispatch proof for the WebDAV / backup capability handlers.
 *
 * **What this proves**: all 8 webdav/backup capabilities are registered on a
 * real Android runtime and round-trip real PUT/GET/PROPFIND/DELETE/MKCOL +
 * backup create/restore operations through the
 * `HostRequest → HostAdapter.dispatch → HostReply` boundary on the Dalvik/ART
 * class loader.
 *
 * **Pattern**: direct [HostAdapter.dispatch] with [FakeWebDavClient] (no
 * network, no Core, no JNI). The [unconfiguredWebDavContext] proof validates
 * the fail-closed "NOT_CONFIGURED" path when the user hasn't set up WebDAV
 * credentials — mirroring the production gap-state.
 */
@RunWith(AndroidJUnit4::class)
class WebDavCapabilityDispatchProofTest {

    private val allCapabilities = listOf(
        "webdav.connect",
        "webdav.upload",
        "webdav.download",
        "webdav.list",
        "webdav.delete",
        "webdav.mkdir",
        "backup.create",
        "backup.restore"
    )

    /**
     * All 8 webdav/backup handlers must be registered after
     * `WebDavContext.registerHandlers(runtime)`. Each capability, when
     * dispatched, must NOT return the "unsupported capability" error.
     */
    @Test
    fun webDav_registerHandlersWiresAll8Capabilities() {
        val ctx = fakeWebDavContext()
        val runtime = ctx.registerHandlers(HostRuntime.over(NoopTransport()))
        val adapter = runtime.adapter()

    for (capability in allCapabilities) {
            val reply = adapter.dispatch(HostRequest(1L, 1L, capability, paramsFor(capability)))
            assertFalse(
                "$capability must dispatch to a handler, got 'unsupported capability'",
                reply.isError() && (reply as HostReply.Error).message().contains("unsupported capability")
            )
        }
    }

    /**
     * `webdav.upload(body="hello")` → `webdav.download` returns body="hello".
     * Proves the PUT→GET round-trip works on device.
     */
    @Test
    fun webdav_uploadDownloadRoundTrip() {
        val ctx = fakeWebDavContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()
        val url = "https://roundtrip.test/file.txt"

        val uploadReply = adapter.dispatch(HostRequest(1L, 1L, "webdav.upload", JSONObject()
            .put("url", url)
            .put("body", "hello")
            .toString()))
        assertTrue("webdav.upload must complete", uploadReply.isComplete())
        val uploadResult = JSONObject((uploadReply as HostReply.Complete).resultJson())
        assertTrue("upload must report uploaded=true", uploadResult.getBoolean("uploaded"))

        val downloadReply = adapter.dispatch(HostRequest(1L, 2L, "webdav.download", JSONObject()
            .put("url", url).toString()))
        assertTrue("webdav.download must complete", downloadReply.isComplete())
        val downloadResult = JSONObject((downloadReply as HostReply.Complete).resultJson())
        assertEquals("download body must match uploaded body", "hello", downloadResult.getString("body"))
    }

    /**
     * `webdav.mkdir` → `webdav.list` returns 207 → `webdav.delete` → list
     * doesn't contain the path. Proves the MKCOL/PROPFIND/DELETE cycle on
     * device.
     */
    @Test
    fun webdav_mkdirListDeleteRoundTrip() {
        val ctx = fakeWebDavContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()
        val url = "https://mkdir.test/dir"

        val mkdirReply = adapter.dispatch(HostRequest(1L, 1L, "webdav.mkdir", JSONObject()
            .put("url", url).toString()))
        assertTrue("webdav.mkdir must complete", mkdirReply.isComplete())
        val mkdirResult = JSONObject((mkdirReply as HostReply.Complete).resultJson())
        assertTrue("mkdir must report created=true", mkdirResult.getBoolean("created"))

        val listReply = adapter.dispatch(HostRequest(1L, 2L, "webdav.list", JSONObject()
            .put("url", url).toString()))
        assertTrue("webdav.list must complete", listReply.isComplete())
        val listResult = JSONObject((listReply as HostReply.Complete).resultJson())
        assertEquals("webdav.list must return 207", 207, listResult.getInt("statusCode"))

        val deleteReply = adapter.dispatch(HostRequest(1L, 3L, "webdav.delete", JSONObject()
            .put("url", url).toString()))
        assertTrue("webdav.delete must complete", deleteReply.isComplete())
        val deleteResult = JSONObject((deleteReply as HostReply.Complete).resultJson())
        assertTrue("delete must report deleted=true", deleteResult.getBoolean("deleted"))

        // After delete, the path must not appear in the PROPFIND body.
        val listAfter = adapter.dispatch(HostRequest(1L, 4L, "webdav.list", JSONObject()
            .put("url", url).toString()))
        val bodyAfter = JSONObject((listAfter as HostReply.Complete).resultJson()).optString("body", "")
        assertFalse(
            "list body must not contain the deleted path",
            bodyAfter.contains(url)
        )
    }

    /**
     * `webdav.connect` returns `connected=true` when PROPFIND returns 207.
     * Proves the connectivity check works on device.
     */
    @Test
    fun webdav_connectReturnsConnectedWhenPropfindReturns207() {
        val ctx = fakeWebDavContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()
        val url = "https://connect.test/"

        val reply = adapter.dispatch(HostRequest(1L, 1L, "webdav.connect", JSONObject()
            .put("url", url).toString()))
        assertTrue("webdav.connect must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(
            "connect must report connected=true (FakeWebDavClient PROPFIND returns 207)",
            result.getBoolean("connected")
        )
        assertEquals("statusCode must be 207", 207, result.getInt("statusCode"))
    }

    /**
     * When WebDAV is not configured (`client = null`), all 8 handlers must
     * return a structured `NOT_CONFIGURED` error — NOT a crash, NOT
     * `INTERNAL`. This is the fail-closed path the UI surfaces as
     * "请先配置 WebDAV 凭据".
     */
    @Test
    fun unconfiguredWebDavContextReturnsNotConfiguredForAll8Handlers() {
        val ctx = unconfiguredWebDavContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()

        for (capability in allCapabilities) {
            val reply = adapter.dispatch(HostRequest(1L, 1L, capability, paramsFor(capability)))
            assertTrue("$capability must error when unconfigured", reply.isError())
            val error = reply as HostReply.Error
            assertEquals(
                "$capability error code must be NOT_CONFIGURED",
                "NOT_CONFIGURED",
                error.code()
            )
        }
    }

    /**
     * `backup.create` uploads the manifest + each entry body to the WebDAV
     * server. After the call, [FakeWebDavClient] must contain both
     * `manifest.json` and the entry files.
     */
    @Test
    fun backup_createUploadsManifestAndEntries() {
        val ctx = fakeWebDavContext()
        val fakeClient = ctx.client as FakeWebDavClient
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()
        val baseUrl = "https://backup.test"
        val entryPath = "$baseUrl/bookmarks.json"

        val manifest = BackupManifest(
            entries = listOf(
                BackupEntry(path = entryPath, checksum = "sha1:abc", sizeBytes = 7)
            )
        )

        val entriesArray = JSONArray().apply {
            put(JSONObject().put("path", entryPath).put("body", "content"))
        }

        val reply = adapter.dispatch(HostRequest(1L, 1L, "backup.create", JSONObject()
            .put("manifest", manifest.toJson())
            .put("baseUrl", baseUrl)
            .put("entries", entriesArray)
            .toString()))

        assertTrue("backup.create must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue("backup.create must report backed_up=true", result.getBoolean("backed_up"))
        assertEquals("entry_count must be 1", 1, result.getInt("entry_count"))

        // Verify FakeWebDavClient received the manifest + entry.
        val manifestContent = runBlocking {
            fakeClient.execute(com.reader.android.data.adapter.WebDavRequest(
                "$baseUrl/manifest.json", com.reader.android.data.adapter.WebDavMethod.GET
            ))
        }
        assertEquals("manifest.json must be uploaded", 200, manifestContent.statusCode)

        val entryContent = runBlocking {
            fakeClient.execute(com.reader.android.data.adapter.WebDavRequest(
                entryPath, com.reader.android.data.adapter.WebDavMethod.GET
            ))
        }
        assertEquals("entry file must be uploaded", 200, entryContent.statusCode)
        assertEquals("entry body must match", "content", entryContent.body)
    }

    /**
     * `backup.restore` pre-populates [FakeWebDavClient] with entry files,
     * then dispatches restore with a matching manifest. The handler must
     * validate the manifest (GET each entry → 200), download them, and
     * return `restored` matching the entry count.
     */
    @Test
    fun backup_restoreDownloadsEntriesAndReturnsRestoredCount() {
        val ctx = fakeWebDavContext()
        val fakeClient = ctx.client as FakeWebDavClient
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()

        val entry1Path = "https://restore.test/file1.txt"
        val entry2Path = "https://restore.test/file2.txt"
        fakeClient.putContent(entry1Path, "body1")
        fakeClient.putContent(entry2Path, "body2")

        val manifest = BackupManifest(
            entries = listOf(
                BackupEntry(path = entry1Path, checksum = "sha1:a", sizeBytes = 5),
                BackupEntry(path = entry2Path, checksum = "sha1:b", sizeBytes = 5)
            )
        )

        val reply = adapter.dispatch(HostRequest(1L, 1L, "backup.restore", JSONObject()
            .put("manifest", manifest.toJson())
            .put("policy", "FULL_REPLACE")
            .toString()))

        assertTrue("backup.restore must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue("backup.restore must report success=true", result.getBoolean("success"))
        assertEquals("restored count must be 2", 2, result.getInt("restored"))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun paramsFor(capability: String): String = when (capability) {
        "webdav.connect" -> JSONObject().put("url", "https://proof.test/").toString()
        "webdav.upload" -> JSONObject()
            .put("url", "https://proof.test/up")
            .put("body", "data")
            .toString()
        "webdav.download" -> JSONObject().put("url", "https://proof.test/down").toString()
        "webdav.list" -> JSONObject().put("url", "https://proof.test/list").toString()
        "webdav.delete" -> JSONObject().put("url", "https://proof.test/del").toString()
        "webdav.mkdir" -> JSONObject().put("url", "https://proof.test/dir").toString()
        "backup.create" -> JSONObject()
            .put("manifest", BackupManifest(entries = listOf(
                BackupEntry(path = "https://proof.test/e", checksum = "x", sizeBytes = 1)
            )).toJson())
            .put("baseUrl", "https://proof.test")
            .put("entries", JSONArray().put(JSONObject().put("path", "https://proof.test/e").put("body", "b")))
            .toString()
        "backup.restore" -> JSONObject()
            .put("manifest", BackupManifest(entries = listOf(
                BackupEntry(path = "https://proof.test/r", checksum = "x", sizeBytes = 1)
            )).toJson())
            .put("policy", "FULL_REPLACE")
            .toString()
        else -> "{}"
    }

    private class NoopTransport : HostTransport {
        override fun pollEventJson(timeoutMillis: Long): String? = null
        override fun sendCommand(commandJson: String) {}
    }
}
