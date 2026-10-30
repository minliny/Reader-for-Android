package io.reader.android.runtime

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
}
