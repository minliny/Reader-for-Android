package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreManagerTest {

    @Test
    fun `validate returns true when all entries accessible`() = runBlocking {
        val client = FakeWebDavClient()
        client.putContent("/backup/sources.json", "data")
        val manifest = BackupManifest(entries = listOf(BackupEntry("/backup/sources.json", "abc", 4)))
        val manager = BackupRestoreManager(client)
        assertTrue(manager.validate(manifest))
    }

    @Test
    fun `validate returns false when entry missing`() = runBlocking {
        val manifest = BackupManifest(entries = listOf(BackupEntry("/missing.json", "xyz", 0)))
        val manager = BackupRestoreManager()
        assertFalse(manager.validate(manifest))
    }

    @Test
    fun `DRY_RUN returns empty plan`() {
        val manifest = BackupManifest(entries = listOf(BackupEntry("a", "x", 0)))
        val manager = BackupRestoreManager()
        assertEquals(0, manager.planRestore(manifest, RestorePolicy.DRY_RUN).size)
    }

    @Test
    fun `FULL_REPLACE returns all entries`() {
        val entries = listOf(BackupEntry("a", "x", 1), BackupEntry("b", "y", 2))
        val manifest = BackupManifest(entries = entries)
        assertEquals(2, BackupRestoreManager().planRestore(manifest, RestorePolicy.FULL_REPLACE).size)
    }
}
