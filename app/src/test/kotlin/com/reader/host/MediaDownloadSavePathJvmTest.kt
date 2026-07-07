package com.reader.host

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 阶段 5 — JVM proof that [OkHttpMediaDownloadExecutor] persists downloaded
 * bytes to `savePath` (resolved under [rootDir]) and returns the path as
 * [MediaDownloadResult.tempPath].
 *
 * Uses OkHttp's [MockWebServer] to serve a fixed byte payload; the executor
 * downloads it, writes it to `savePath` under [TemporaryFolder], and returns
 * the path. The test then reads the file back and asserts the SHA-256 matches.
 *
 * This closes Gap G3: before 阶段 5, `OkHttpMediaDownloadExecutor` read bytes
 * into memory and returned `tempPath = null` even when `savePath` was
 * provided. Now bytes land on disk and Core can reference the file in later
 * `media.playback` / `media.release` calls.
 */
class MediaDownloadSavePathJvmTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `savePath writes bytes to disk and returns tempPath`() {
        val payload = "media-savepath-proof".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody(okio.Buffer().write(payload))
        )

        val rootDir = tempFolder.newFolder("media-cache")
        val executor = OkHttpMediaDownloadExecutor(
            client = OkHttpHostTransport.defaultClient(),
            rootDir = rootDir
        )
        val request = MediaDownloadRequest(
            url = server.url("/media.bin").toString(),
            savePath = "downloads/media-1.bin"
        )
        val result = executor.execute(request)

        // tempPath must be the savePath string (relative to rootDir).
        assertNotNull("tempPath must not be null when savePath is set", result.tempPath)
        assertEquals("downloads/media-1.bin", result.tempPath)

        // The file must exist on disk under rootDir/tempPath.
        val file = File(rootDir, result.tempPath)
        assertTrue("file must exist at $file", file.exists())
        assertEquals(payload.size.toLong(), file.length())

        // SHA-256 of the on-disk bytes must match the result.
        val fileBytes = file.readBytes()
        val expectedSha = sha256Hex(payload)
        assertEquals(expectedSha, result.sha256)
        assertEquals(expectedSha, sha256Hex(fileBytes))

        // byteLength must reflect actual bytes written.
        assertEquals(payload.size.toLong(), result.byteLength)
    }

    @Test
    fun `savePath null keeps bytes in memory and tempPath null`() {
        val payload = "no-save".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )

        val rootDir = tempFolder.newFolder("media-cache")
        val executor = OkHttpMediaDownloadExecutor(
            client = OkHttpHostTransport.defaultClient(),
            rootDir = rootDir
        )
        val request = MediaDownloadRequest(
            url = server.url("/no-save.bin").toString(),
            savePath = null
        )
        val result = executor.execute(request)

        // Without savePath, tempPath stays null (alpha behavior).
        assertEquals(null, result.tempPath)
        // No file should be written under rootDir.
        assertEquals(0, rootDir.listFiles()?.size ?: 0)
        // SHA-256 still computed from in-memory bytes.
        assertEquals(sha256Hex(payload), result.sha256)
    }

    @Test
    fun `savePath with traversal is rejected`() {
        val payload = "traversal".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )

        val rootDir = tempFolder.newFolder("media-cache")
        val executor = OkHttpMediaDownloadExecutor(
            client = OkHttpHostTransport.defaultClient(),
            rootDir = rootDir
        )
        val request = MediaDownloadRequest(
            url = server.url("/traversal.bin").toString(),
            savePath = "../../escape.bin"
        )
        try {
            executor.execute(request)
            error("expected SecurityException for path traversal")
        } catch (e: SecurityException) {
            assertTrue(
                "message must mention escape: ${e.message}",
                e.message!!.contains("escape")
            )
        }
        // No file should exist outside rootDir.
        assertFalse(File(rootDir.parentFile, "escape.bin").exists())
    }

    @Test
    fun `savePath with rootDir null keeps bytes in memory`() {
        val payload = "no-root".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )

        // rootDir = null: executor must ignore savePath and keep
        // alpha in-memory behavior (this is the JVM test path).
        val executor = OkHttpMediaDownloadExecutor(
            client = OkHttpHostTransport.defaultClient(),
            rootDir = null
        )
        val request = MediaDownloadRequest(
            url = server.url("/no-root.bin").toString(),
            savePath = "downloads/ignored.bin"
        )
        val result = executor.execute(request)
        assertEquals(null, result.tempPath)
        assertEquals(sha256Hex(payload), result.sha256)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
