package io.reader.android.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReaderMangaMaterializationStoreTest {
    @Test
    fun completeRequiresDecodedResourcesAndCommitsOnlyAfterCoreResult() {
        val root = Files.createTempDirectory("reader-manga-materialization-").toFile()
        val resources = ReaderMangaResourceStore(java.io.File(root, "resources"))
        val store = ReaderMangaMaterializationStore(resources, java.io.File(root, "materializations"))
        val chapter = ReaderMangaChapterIdentity("source", "book", "chapter")
        val ref = "manga:page-0"
        val identity = ReaderMangaTransferIdentity("rmt1:${"d".repeat(64)}", ref)
        val staged = resources.stage(identity, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
        resources.commit(staged)
        resources.putDecodedByTransferId(identity.wireId, byteArrayOf(9, 8, 7))
        val token = "token-1"
        store.prepare(ReaderMangaMaterializationPrepareResult(token, chapter, "manifest", ReaderMangaMaterializationIntent.CACHE))
        store.requireResources(token, chapter, "manifest", listOf(ref))
        store.complete(ReaderMangaMaterializationCompleteResult(true, "manifest"), token)
        assertTrue(java.io.File(root, "materializations/materialized-$token.json").isFile)
    }

    @Test
    fun reopeningStoreRestoresMaterializedProtectionBeforeEviction() {
        val root = Files.createTempDirectory("reader-manga-materialization-reopen-").toFile()
        val resourcesRoot = java.io.File(root, "resources")
        val materializationsRoot = java.io.File(root, "materializations")
        val resources = ReaderMangaResourceStore(resourcesRoot)
        val store = ReaderMangaMaterializationStore(resources, materializationsRoot)
        val chapter = ReaderMangaChapterIdentity("source", "book", "chapter")
        val ref = "manga:page-reopen"
        val identity = ReaderMangaTransferIdentity("rmt1:${"f".repeat(64)}", ref)
        val staged = resources.stage(identity, listOf(byteArrayOf(1, 2, 3)), "image/jpeg")
        resources.commit(staged)
        resources.putDecodedByTransferId(identity.wireId, byteArrayOf(9, 8, 7))
        val token = "token-reopen"
        store.prepare(ReaderMangaMaterializationPrepareResult(token, chapter, "manifest", ReaderMangaMaterializationIntent.CACHE))
        store.requireResources(token, chapter, "manifest", listOf(ref))
        store.complete(ReaderMangaMaterializationCompleteResult(true, "manifest"), token)
        assertFalse(resources.evictDecodedByTransferId(identity.wireId))

        val reopenedResources = ReaderMangaResourceStore(resourcesRoot)
        ReaderMangaMaterializationStore(reopenedResources, materializationsRoot)
        assertFalse(reopenedResources.evictDecodedByTransferId(identity.wireId))
    }
}
