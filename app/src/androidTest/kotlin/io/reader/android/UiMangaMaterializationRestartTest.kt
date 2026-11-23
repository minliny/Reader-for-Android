package io.reader.android

import android.content.Context
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ReaderMangaChapterIdentity
import io.reader.android.runtime.ReaderMangaMaterializationCompleteResult
import io.reader.android.runtime.ReaderMangaMaterializationIntent
import io.reader.android.runtime.ReaderMangaMaterializationPrepareResult
import io.reader.android.runtime.ReaderMangaMaterializationStore
import io.reader.android.runtime.ReaderMangaResourceStore
import io.reader.android.runtime.ReaderMangaTransferIdentity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Device-file restart slice for the durable manga protection boundary. */
@RunWith(AndroidJUnit4::class)
class UiMangaMaterializationRestartTest {
    @Test(timeout = 30_000)
    fun seedMangaMaterializationForProcessRestart() {
        activeMethod = SEED_METHOD
        marker("method-enter")
        val context = context()
        val root = probeRoot(context)
        root.deleteRecursively()
        assertTrue(root.mkdirs())
        val resources = ReaderMangaResourceStore(File(root, "resources"))
        val materializations = ReaderMangaMaterializationStore(resources, File(root, "materializations"))
        val chapter = ReaderMangaChapterIdentity("restart-source", "restart-book", "restart-chapter")
        val resourceRef = "manga:restart-page"
        val identity = ReaderMangaTransferIdentity("rmt1:${"b".repeat(64)}", resourceRef)
        val staged = resources.stage(identity, listOf(byteArrayOf(1, 2, 3, 4)), "image/jpeg")
        resources.commit(staged)
        resources.putDecodedByTransferId(identity.wireId, byteArrayOf(9, 8, 7, 6))
        val token = "restart-token"
        materializations.prepare(ReaderMangaMaterializationPrepareResult(token, chapter, "restart-manifest", ReaderMangaMaterializationIntent.CACHE))
        materializations.requireResources(token, chapter, "restart-manifest", listOf(resourceRef))
        materializations.complete(ReaderMangaMaterializationCompleteResult(true, "restart-manifest"), token)
        assertTrue(File(root, "materializations/materialized-$token.json").isFile)
        assertFalse(resources.evictDecodedByTransferId(identity.wireId))
        marker("restart-seeded", Bundle().apply {
            putString("root", root.absolutePath); putString("transferId", identity.wireId)
            putString("resourceRef", resourceRef); putString("token", token)
            putBoolean("evictionBlocked", true)
        })
        marker("method-exit")
    }

    @Test(timeout = 30_000)
    fun reopenMangaMaterializationAfterProcessRestart() {
        activeMethod = REOPEN_METHOD
        marker("method-enter")
        val context = context()
        val root = probeRoot(context)
        val resources = ReaderMangaResourceStore(File(root, "resources"))
        ReaderMangaMaterializationStore(resources, File(root, "materializations"))
        val transferId = "rmt1:${"b".repeat(64)}"
        assertTrue(File(root, "materializations/materialized-restart-token.json").isFile)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), resources.readDecodedByTransferId(transferId))
        assertFalse(resources.evictDecodedByTransferId(transferId))
        marker("restart-reopened", Bundle().apply {
            putString("root", root.absolutePath); putString("transferId", transferId)
            putBoolean("protectionRestored", true); putBoolean("evictionBlocked", true)
        })
        root.deleteRecursively()
        marker("restart-cleaned")
        marker("method-exit")
    }

    private fun context(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("io.reader.minliny", context.packageName)
        assertEquals(context.applicationInfo.uid, Process.myUid())
        return context
    }

    private fun probeRoot(context: Context): File = File(context.filesDir, "A6MangaMaterializationRestart")

    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerMethod", activeMethod)
        fields.putString("readerStep", step)
        fields.putInt("readerProcessPid", Process.myPid())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, fields)
    }

    companion object {
        private const val SEED_METHOD = "seedMangaMaterializationForProcessRestart"
        private const val REOPEN_METHOD = "reopenMangaMaterializationAfterProcessRestart"
        @Volatile private var activeMethod: String = SEED_METHOD
    }
}
