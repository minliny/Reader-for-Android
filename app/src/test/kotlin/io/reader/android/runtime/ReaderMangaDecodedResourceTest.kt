package io.reader.android.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReaderMangaDecodedResourceTest {
    @Test
    fun decodedHandleBindsResourceRefAndReleasesEvictionLeaseOnClose() {
        val root = Files.createTempDirectory("reader-manga-handle-").toFile()
        val store = ReaderMangaResourceStore(root)
        val identity = ReaderMangaTransferIdentity("rmt1:${"f".repeat(64)}", "manga:page-handle")
        val staged = store.stage(identity, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
        store.commit(staged)
        store.putDecodedByTransferId(identity.wireId, byteArrayOf(9, 8, 7))
        val lease = store.acquireDecodedLease(identity.wireId, identity.resourceRef)
        val handle = ReaderMangaDecodedResource(
            ReaderMangaChapterIdentity("source", "book", "chapter"),
            "manifest-1", identity.resourceRef, identity.wireId,
            store.readDecodedByTransferId(identity.wireId, identity.resourceRef), lease,
        )
        assertEquals(identity.resourceRef, handle.resourceRef)
        assertArrayEquals(byteArrayOf(9, 8, 7), handle.bytes)
        assertFalse(store.evictDecodedByTransferId(identity.wireId))
        handle.close()
        handle.close()
        assertTrue(store.evictDecodedByTransferId(identity.wireId))
    }

    @Test
    fun decodedHandleRejectsForeignResourceRef() {
        val root = Files.createTempDirectory("reader-manga-handle-scope-").toFile()
        val store = ReaderMangaResourceStore(root)
        val identity = ReaderMangaTransferIdentity("rmt1:${"0".repeat(64)}", "manga:page-owned")
        val staged = store.stage(identity, listOf(byteArrayOf(4, 5, 6)), "image/jpeg")
        store.commit(staged)
        store.putDecodedByTransferId(identity.wireId, byteArrayOf(7, 8, 9))
        assertThrows(IllegalStateException::class.java) {
            store.acquireDecodedLease(identity.wireId, "manga:foreign")
        }
        assertThrows(IllegalStateException::class.java) {
            store.readDecodedByTransferId(identity.wireId, "manga:foreign")
        }
    }
}
