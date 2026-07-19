package com.reader.android.data.adapter

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CoreLocalBookImportServiceTest {

    @Test
    fun `five supported formats use canonical Core import with original bytes`() = runBlocking {
        val cases = listOf(
            Triple("sample.txt", "text/plain", LocalBookFormat.TXT),
            Triple("sample.epub", "application/epub+zip", LocalBookFormat.EPUB),
            Triple("sample.pdf", "application/pdf", LocalBookFormat.PDF),
            Triple("sample.mobi", "application/x-mobipocket-ebook", LocalBookFormat.MOBI),
            Triple("sample.umd", "application/x-umd", LocalBookFormat.UMD)
        )

        for ((fileName, mimeType, expectedFormat) in cases) {
            val bytes = "real-bytes-for-$fileName".toByteArray()
            var capturedMethod: String? = null
            var capturedParams: JSONObject? = null
            val service = CoreLocalBookImportService(
                documentReader = LocalBookDocumentReader { _, _ -> bytes },
                core = LocalBookCoreCommandClient { method, params ->
                    capturedMethod = method
                    capturedParams = JSONObject(params.toString())
                    canonicalImportResult(params, expectedFormat)
                }
            )

            val outcome = service.import(
                LocalBookSelectedDocument(
                    uri = "content://books/$fileName",
                    displayName = fileName,
                    mimeType = mimeType,
                    declaredSize = bytes.size.toLong()
                )
            )

            assertTrue("$fileName should import: $outcome", outcome is LocalBookImportOutcome.Imported)
            val imported = (outcome as LocalBookImportOutcome.Imported).value
            assertEquals(expectedFormat, imported.format)
            assertEquals(CoreLocalBookImportService.LOCAL_BOOK_IMPORT_METHOD, capturedMethod)
            assertEquals(
                Base64.getEncoder().encodeToString(bytes),
                capturedParams!!.getString("bytesBase64")
            )
            assertEquals(fileName, capturedParams!!.getString("fileName"))
            assertFalse("opaque URI must not enter Core payload", capturedParams!!.has("uri"))
            if (expectedFormat == LocalBookFormat.TXT) {
                assertTrue(imported.readerAdmission is LocalBookReaderAdmission.Text)
            } else {
                val blocked = imported.readerAdmission as LocalBookReaderAdmission.Blocked
                assertEquals(
                    LocalBookImportFailureCode.FORMAT_RENDERER_UNAVAILABLE,
                    blocked.failure.code
                )
            }
        }
    }

    @Test
    fun `scan rejects extension and mime disagreement before reading bytes`() = runBlocking {
        var readCount = 0
        var coreCount = 0
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> readCount++; byteArrayOf(1) },
            core = LocalBookCoreCommandClient { _, _ -> coreCount++; JSONObject() }
        )
        val document = LocalBookSelectedDocument(
            uri = "content://books/wrong.pdf",
            displayName = "wrong.pdf",
            mimeType = "application/epub+zip"
        )

        val scan = service.scan(listOf(document)).single()
        val outcome = service.import(document)

        assertFalse(scan.isCandidate)
        assertEquals(LocalBookImportFailureCode.FORMAT_CONFLICT, scan.failure!!.code)
        assertEquals(
            LocalBookImportFailureCode.FORMAT_CONFLICT,
            (outcome as LocalBookImportOutcome.Failed).failure.code
        )
        assertEquals(0, readCount)
        assertEquals(0, coreCount)
    }

    @Test
    fun `scan accepts supported mime when provider omits extension`() {
        val service = serviceReturning(LocalBookFormat.EPUB)
        val result = service.scan(
            listOf(
                LocalBookSelectedDocument(
                    uri = "content://books/opaque-id",
                    displayName = "opaque-id",
                    mimeType = "application/epub+zip"
                )
            )
        ).single()

        assertTrue(result.isCandidate)
        assertEquals(LocalBookFormat.EPUB, result.format)
    }

    @Test
    fun `unsupported selection fails closed without Core call`() = runBlocking {
        var coreCount = 0
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> byteArrayOf(1) },
            core = LocalBookCoreCommandClient { _, _ -> coreCount++; JSONObject() }
        )

        val outcome = service.import(
            LocalBookSelectedDocument(
                uri = "content://books/archive.zip",
                displayName = "archive.zip",
                mimeType = "application/zip"
            )
        )

        assertEquals(
            LocalBookImportFailureCode.UNSUPPORTED_FORMAT,
            (outcome as LocalBookImportOutcome.Failed).failure.code
        )
        assertEquals(0, coreCount)
    }

    @Test
    fun `empty files fail before Core dispatch`() = runBlocking {
        var coreCount = 0
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> ByteArray(0) },
            core = LocalBookCoreCommandClient { _, _ -> coreCount++; JSONObject() }
        )

        val outcome = service.import(txtDocument())

        assertEquals(
            LocalBookImportFailureCode.EMPTY_FILE,
            (outcome as LocalBookImportOutcome.Failed).failure.code
        )
        assertEquals(0, coreCount)
    }

    @Test
    fun `declared oversize file is rejected without opening provider`() = runBlocking {
        var readCount = 0
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> readCount++; byteArrayOf(1) },
            core = LocalBookCoreCommandClient { _, _ -> JSONObject() },
            maxImportBytes = 8
        )

        val outcome = service.import(txtDocument(declaredSize = 9))

        assertEquals(
            LocalBookImportFailureCode.FILE_TOO_LARGE,
            (outcome as LocalBookImportOutcome.Failed).failure.code
        )
        assertEquals(0, readCount)
    }

    @Test
    fun `provider permission failure is structured and retryable`() = runBlocking {
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> throw SecurityException("denied") },
            core = LocalBookCoreCommandClient { _, _ -> JSONObject() }
        )

        val outcome = service.import(txtDocument()) as LocalBookImportOutcome.Failed

        assertEquals(LocalBookImportFailureCode.PERMISSION_DENIED, outcome.failure.code)
        assertTrue(outcome.failure.retryable)
    }

    @Test
    fun `provider read failure does not expose URI in message`() = runBlocking {
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> throw IOException("secret uri") },
            core = LocalBookCoreCommandClient { _, _ -> JSONObject() }
        )

        val outcome = service.import(txtDocument()) as LocalBookImportOutcome.Failed

        assertEquals(LocalBookImportFailureCode.READ_FAILED, outcome.failure.code)
        assertFalse(outcome.failure.message.contains("content://"))
    }

    @Test
    fun `Core format mismatch is a protocol error not a text fallback`() = runBlocking {
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> "pdf".toByteArray() },
            core = LocalBookCoreCommandClient { _, params ->
                canonicalImportResult(params, LocalBookFormat.TXT)
            }
        )

        val outcome = service.import(
            LocalBookSelectedDocument(
                uri = "content://books/book.pdf",
                displayName = "book.pdf",
                mimeType = "application/pdf"
            )
        ) as LocalBookImportOutcome.Failed

        assertEquals(LocalBookImportFailureCode.CORE_PROTOCOL_MISMATCH, outcome.failure.code)
    }

    @Test
    fun `Core result requires a complete persisted toc`() = runBlocking {
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> "text".toByteArray() },
            core = LocalBookCoreCommandClient { _, params ->
                canonicalImportResult(params, LocalBookFormat.TXT).put("toc", JSONArray())
            }
        )

        val outcome = service.import(txtDocument()) as LocalBookImportOutcome.Failed

        assertEquals(LocalBookImportFailureCode.CORE_PROTOCOL_MISMATCH, outcome.failure.code)
    }

    @Test
    fun `cancellation propagates and cannot become import failure`() = runBlocking {
        val service = CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> throw CancellationException("cancel") },
            core = LocalBookCoreCommandClient { _, _ -> JSONObject() }
        )

        try {
            service.import(txtDocument())
            fail("expected cancellation")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `five contract mime types are exposed to file picker`() {
        assertEquals(
            listOf(
                "text/plain",
                "application/epub+zip",
                "application/pdf",
                "application/x-mobipocket-ebook",
                "application/x-umd"
            ),
            CoreLocalBookImportService.supportedMimeTypes()
        )
    }

    private fun serviceReturning(format: LocalBookFormat): CoreLocalBookImportService =
        CoreLocalBookImportService(
            documentReader = LocalBookDocumentReader { _, _ -> "bytes".toByteArray() },
            core = LocalBookCoreCommandClient { _, params -> canonicalImportResult(params, format) }
        )

    private fun txtDocument(declaredSize: Long? = null) = LocalBookSelectedDocument(
        uri = "content://books/sample.txt",
        displayName = "sample.txt",
        mimeType = "text/plain",
        declaredSize = declaredSize
    )

    private fun canonicalImportResult(
        params: JSONObject,
        format: LocalBookFormat
    ): JSONObject {
        val wireFormat = format.name.lowercase()
        return JSONObject()
            .put(
                "book",
                JSONObject()
                    .put("bookId", params.getString("bookId"))
                    .put("title", params.getString("fileName").substringBeforeLast('.'))
            )
            .put("format", wireFormat)
            .put("encoding", "utf8")
            .put("byteLen", 16)
            .put("charLen", 12)
            .put("chapterCount", 1)
            .put("toc", JSONArray().put(JSONObject().put("index", 0).put("title", "Chapter 1")))
    }
}
