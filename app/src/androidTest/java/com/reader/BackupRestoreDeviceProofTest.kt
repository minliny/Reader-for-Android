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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1-6 device-level proof for the `backup.restore` handler's manifest
 * validation + entry download path.
 *
 * **What this proves**: the [BackupRestoreManager.validate] path runs on
 * the Dalvik/ART class loader — checking each manifest entry against the
 * WebDAV store via GET — and the handler correctly returns `success=false`
 * when a referenced file is missing. This is the fail-closed path that
 * prevents restoring a partial / corrupt backup.
 *
 * **Pattern**: direct [HostAdapter.dispatch] with [FakeWebDavClient]
 * (mirrors [WebDavCapabilityDispatchProofTest] but focuses on the
 * validation + reject path).
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreDeviceProofTest {

    /**
     * Pre-populate [FakeWebDavClient] with 2 entry files, then dispatch
     * `backup.restore` with a matching manifest. The handler must:
     *  1. `validate(manifest)` → GET each entry → all 200 → `true`
     *  2. `planRestore(manifest, FULL_REPLACE)` → all entries
     *  3. Download each entry → GET 200 → `restored = 2`
     *  4. Return `success = true, restored = 2`
     */
    @Test
    fun backup_restoreValidatesManifestAndDownloadsEntries() {
        val ctx = fakeWebDavContext()
        val fakeClient = ctx.client as FakeWebDavClient
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()

        val entry1Path = "https://restore.test/bookmarks.json"
        val entry2Path = "https://restore.test/progress.json"
        fakeClient.putContent(entry1Path, """{"bookmarks":[]}""")
        fakeClient.putContent(entry2Path, """{"progress":0.5}""")

        val manifest = BackupManifest(
            entries = listOf(
                BackupEntry(path = entry1Path, checksum = "sha1:a", sizeBytes = 16),
                BackupEntry(path = entry2Path, checksum = "sha1:b", sizeBytes = 16)
            )
        )

        val reply = adapter.dispatch(HostRequest(1L, 1L, "backup.restore", JSONObject()
            .put("manifest", manifest.toJson())
            .put("policy", "FULL_REPLACE")
            .toString()))

        assertTrue("backup.restore must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(
            "backup.restore must report success=true when all entries validate",
            result.getBoolean("success")
        )
        assertEquals("restored count must be 2", 2, result.getInt("restored"))
        assertEquals("skipped count must be 0", 0, result.getInt("skipped"))
    }

    /**
     * Manifest references a non-existent file. The handler must:
     *  1. `validate(manifest)` → GET the missing entry → 404 → `false`
     *  2. Return `success = false` without attempting the restore
     *
     * This is the fail-closed path: a partial / corrupt manifest must NOT
     * silently restore a subset of entries.
     */
    @Test
    fun backup_restoreRejectsInvalidManifest() {
        val ctx = fakeWebDavContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()

        // Only pre-populate one entry; the manifest references a second
        // non-existent file.
        val fakeClient = ctx.client as FakeWebDavClient
        val existingPath = "https://restore.test/exists.json"
        val missingPath = "https://restore.test/missing.json"
        fakeClient.putContent(existingPath, "ok")

        val manifest = BackupManifest(
            entries = listOf(
                BackupEntry(path = existingPath, checksum = "sha1:a", sizeBytes = 2),
                BackupEntry(path = missingPath, checksum = "sha1:b", sizeBytes = 2)
            )
        )

        val reply = adapter.dispatch(HostRequest(1L, 1L, "backup.restore", JSONObject()
            .put("manifest", manifest.toJson())
            .put("policy", "FULL_REPLACE")
            .toString()))

        assertTrue("backup.restore must complete (not crash)", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertFalse(
            "backup.restore must report success=false when manifest validation fails",
            result.getBoolean("success")
        )
        assertEquals(
            "restored must be 0 when validation fails",
            0,
            result.getInt("restored")
        )
    }

    private class NoopTransport : HostTransport {
        override fun pollEventJson(timeoutMillis: Long): String? = null
        override fun sendCommand(commandJson: String) {}
    }
}
