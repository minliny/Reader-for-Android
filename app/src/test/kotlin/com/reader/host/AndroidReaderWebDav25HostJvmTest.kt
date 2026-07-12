package com.reader.host

import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.AuthMethod
import com.reader.android.data.adapter.FakeWebDavClient
import com.reader.android.data.adapter.WebDavCredential
import com.reader.android.data.adapter.WebDavCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidReaderWebDav25HostJvmTest {
    private class MemoryCredentialKeystore : WebDavCredentialStore.CredentialKeystore {
        private val values = mutableMapOf<String, String>()
        override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord =
            AndroidEncryptedCredentialRecord(identifier, "test", secret, "", "").also {
                values[identifier] = secret
            }
        override fun load(record: AndroidEncryptedCredentialRecord) = values.getValue(record.identifier)
        override fun revoke(record: AndroidEncryptedCredentialRecord) =
            values.remove(record.identifier) != null
    }

    @Test
    fun `stored credential executor performs connect backup and restore round trip`() {
        val credentials = WebDavCredentialStore(MemoryCredentialKeystore())
        credentials.save(
            "webdav.default",
            WebDavCredential(
                serverUrl = "https://dav.example.test/root",
                auth = AuthMethod.Basic("reader", "secret")
            )
        )
        val client = FakeWebDavClient()
        var restoredSnapshot: String? = null
        val dataSource = object : ReaderBackupDataSource {
            override fun exportSnapshot() = """{"schemaVersion":1,"bookSources":[]}"""
            override fun restoreSnapshot(snapshot: String): Int {
                restoredSnapshot = snapshot
                return 4
            }
        }
        val host = AndroidReaderWebDav25Host(credentials, client, dataSource)
        val scope = ReaderUiHostRequestScope(8, 99, 3)

        val connect = host.connect(scope, override = null)
        assertTrue(connect.connected)
        assertEquals(207, connect.statusCode)

        val backup = host.backup(scope, override = null)
        assertTrue(backup.backedUp)
        assertEquals(201, backup.statusCode)
        assertEquals(
            "https://dav.example.test/root/ReaderBackup/ReaderAndroid/reader-backup-v1.json",
            backup.remoteURL
        )

        val restore = host.restore(scope, backup.remoteURL, override = null)
        assertTrue(restore.restored)
        assertEquals(4, restore.importedSources)
        assertEquals(dataSource.exportSnapshot(), restoredSnapshot)
    }

    @Test(expected = ReaderUiHostCapabilityFailure::class)
    fun `missing stored credential fails closed before network`() {
        val host = AndroidReaderWebDav25Host(
            credentials = WebDavCredentialStore(MemoryCredentialKeystore()),
            defaultClient = FakeWebDavClient(),
            backupDataSource = object : ReaderBackupDataSource {
                override fun exportSnapshot() = "{}"
                override fun restoreSnapshot(snapshot: String) = 0
            }
        )
        host.connect(ReaderUiHostRequestScope(1, 1, null), override = null)
    }
}
