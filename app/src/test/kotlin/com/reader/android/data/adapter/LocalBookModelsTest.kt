package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookModelsTest {

    @Test
    fun `LocalBookSource with TXT format`() {
        val src = LocalBookSource(
            uri = "content://files/book.txt",
            displayName = "book.txt",
            format = LocalBookFormat.TXT,
            mimeType = "text/plain",
            fileSize = 1024
        )
        assertEquals("book.txt", src.displayName)
        assertEquals(LocalBookFormat.TXT, src.format)
        assertEquals(1024, src.fileSize)
    }

    @Test
    fun `LocalBookMetadata with chapters`() {
        val meta = LocalBookMetadata(
            title = "我的小说",
            author = "作者名",
            format = LocalBookFormat.TXT,
            chapters = listOf(
                LocalChapterRef(0, "第一章", 0, 500),
                LocalChapterRef(1, "第二章", 501, 1200)
            )
        )
        assertEquals("我的小说", meta.title)
        assertEquals(2, meta.chapters.size)
        assertEquals("第二章", meta.chapters[1].title)
    }

    @Test
    fun `successful import result`() {
        val src = LocalBookSource("uri", "name", LocalBookFormat.TXT)
        val meta = LocalBookMetadata("title", format = LocalBookFormat.TXT)
        val result = LocalBookImportResult(success = true, source = src, metadata = meta)
        assertTrue(result.success)
        assertNotNull(result.metadata)
    }

    @Test
    fun `failed import result`() {
        val src = LocalBookSource("bad-uri", "bad", LocalBookFormat.UNKNOWN)
        val result = LocalBookImportResult(success = false, source = src, errorMessage = "File not readable")
        assertFalse(result.success)
        assertEquals("File not readable", result.errorMessage)
    }
}
