package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPackageTest {

    @Test
    fun `BackupManifest toJson and fromJson round trip`() {
        val manifest = BackupManifest(
            version = 1,
            entries = listOf(
                BackupEntry("sources.json", "abc123", 1024),
                BackupEntry("progress.json", "def456", 512)
            )
        )
        val json = manifest.toJson()
        val restored = BackupManifest.fromJson(json)
        assertEquals(1, restored.version)
        assertEquals(2, restored.entries.size)
        assertEquals("sources.json", restored.entries[0].path)
        assertEquals("abc123", restored.entries[0].checksum)
        assertEquals(1024, restored.entries[0].sizeBytes)
    }

    @Test
    fun `empty manifest round trip`() {
        val manifest = BackupManifest()
        val json = manifest.toJson()
        val restored = BackupManifest.fromJson(json)
        assertEquals(0, restored.entries.size)
        assertEquals(1, restored.version)
    }

    @Test
    fun `BackupEntry holds all fields`() {
        val entry = BackupEntry("file.txt", "sha256:abc", 2048, 1700000000L)
        assertEquals("file.txt", entry.path)
        assertEquals("sha256:abc", entry.checksum)
        assertEquals(2048, entry.sizeBytes)
    }
}
