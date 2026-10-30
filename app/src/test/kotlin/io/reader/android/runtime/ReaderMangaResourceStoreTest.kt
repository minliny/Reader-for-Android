package io.reader.android.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class ReaderMangaResourceStoreTest {
    @Test
    fun stagedBytesAreInvisibleUntilCommitAndReadBindsDigestAndIdentity() {
        val root = Files.createTempDirectory("reader-manga-store-").toFile()
        val store = ReaderMangaResourceStore(root)
        val identity = ReaderMangaTransferIdentity("rmt1:${"a".repeat(64)}", "manga:page-0")
        val bytes = byteArrayOf(1, 2, 3, 4)
        val staged = store.stage(identity, listOf(bytes.copyOfRange(0, 2), bytes.copyOfRange(2, 4)), "image/png")
        assertTrue(staged.directory.exists())
        val sha = staged.sha256
        try {
            store.read(identity, sha, bytes.size.toLong())
            throw AssertionError("staged resource was visible before commit")
        } catch (_: IllegalStateException) { }
        store.commit(staged)
        val resource = store.read(identity, sha, bytes.size.toLong())
        assertArrayEquals(bytes, resource.encoded)
        assertEquals("image/png", resource.contentType)
        assertFalse(staged.directory.exists())
        assertTrue(store.evict(identity, sha))
    }

    @Test
    fun transferLookupRequiresExactShaAndBytes() {
        val root = Files.createTempDirectory("reader-manga-store-").toFile()
        val store = ReaderMangaResourceStore(root)
        val identity = ReaderMangaTransferIdentity("rmt1:${"b".repeat(64)}", "manga:page-1")
        val staged = store.stage(identity, listOf(byteArrayOf(9, 8, 7)), "image/jpeg")
        store.commit(staged)
        assertArrayEquals(byteArrayOf(9, 8, 7), store.readByTransferId(identity.wireId, staged.sha256, 3).encoded)
        try {
            store.readByTransferId(identity.wireId, staged.sha256, 2)
            throw AssertionError("wrong byte count was admitted")
        } catch (_: IllegalStateException) { }
    }

    @Test
    fun decodedBytesCommitAndReadUnderTheSameTransferIdentity() {
        val root = Files.createTempDirectory("reader-manga-store-").toFile()
        val store = ReaderMangaResourceStore(root)
        val identity = ReaderMangaTransferIdentity("rmt1:${"c".repeat(64)}", "manga:page-2")
        val staged = store.stage(identity, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
        store.commit(staged)
        val decodedSha = store.putDecodedByTransferId(identity.wireId, byteArrayOf(9, 8, 7, 6))
        assertEquals(64, decodedSha.length)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), store.readDecodedByTransferId(identity.wireId))
    }

    @Test
    fun evictionRefusesActiveLeaseAndMaterializedProtection() {
        val root = Files.createTempDirectory("reader-manga-store-").toFile()
        val store = ReaderMangaResourceStore(root)
        val identity = ReaderMangaTransferIdentity("rmt1:${"e".repeat(64)}", "manga:page-evict")
        val staged = store.stage(identity, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
        store.commit(staged)
        store.putDecodedByTransferId(identity.wireId, byteArrayOf(4, 5, 6))
        val lease = store.acquireDecodedLease(identity.wireId)
        assertFalse(store.evictDecodedByTransferId(identity.wireId))
        assertFalse(store.evict(identity, staged.sha256))
        lease.close()
        store.protectResourceRef(identity.resourceRef)
        assertFalse(store.evictDecodedByTransferId(identity.wireId))
        assertFalse(store.evict(identity, staged.sha256))
        store.unprotectResourceRef(identity.resourceRef)
        assertTrue(store.evictDecodedByTransferId(identity.wireId))
        assertTrue(store.evict(identity, staged.sha256))
    }

    @Test
    fun invalidDecodedFileCannotPublishAnEvictionLease() {
        for (invalidKind in listOf("missing", "empty", "directory", "oversized")) {
            val root = Files.createTempDirectory("reader-manga-invalid-lease-").toFile()
            try {
                val store = ReaderMangaResourceStore(root)
                val identity = ReaderMangaTransferIdentity("rmt1:${"d".repeat(64)}", "manga:page-invalid")
                val staged = store.stage(identity, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
                store.commit(staged)
                val directory = root.listFiles().orEmpty().single { it.isDirectory && !it.name.startsWith(".") }
                val decoded = File(directory, "decoded.bin")
                when (invalidKind) {
                    "empty" -> decoded.writeBytes(byteArrayOf())
                    "directory" -> assertTrue(decoded.mkdir())
                    "oversized" -> RandomAccessFile(decoded, "rw").use { it.setLength(16L * 1024 * 1024 + 1) }
                }
                assertThrows(IllegalStateException::class.java) { store.acquireDecodedLease(identity.wireId) }
                assertThrows(IllegalStateException::class.java) {
                    store.acquireDecodedLease(identity.wireId, identity.resourceRef)
                }
                if (decoded.exists()) assertTrue(decoded.delete())
                store.putDecodedByTransferId(identity.wireId, byteArrayOf(4, 5, 6))
                assertTrue(store.evictDecodedByTransferId(identity.wireId))
                assertTrue(store.evict(identity, staged.sha256))
            } finally {
                root.deleteRecursively()
            }
        }
    }
}
