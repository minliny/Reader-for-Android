package com.reader.android.ui.reader

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentFacadeAdapterTest {

    @After
    fun tearDown() {
        ContentAdapterShell.resetToFakeMode()
    }

    // ── ContentRequest ──

    @Test
    fun `content request with blank url is invalid`() {
        val req = ContentRequest(chapterUrl = "")
        assertFalse(req.isValid)
    }

    @Test
    fun `content request computes prev and next from index`() {
        val req = ContentRequest(chapterUrl = "url", chapterIndex = 1, totalChapters = 5)
        assertTrue(req.hasPrevChapter)
        assertTrue(req.hasNextChapter)

        val first = ContentRequest(chapterUrl = "url", chapterIndex = 0, totalChapters = 5)
        assertFalse(first.hasPrevChapter)
        assertTrue(first.hasNextChapter)

        val last = ContentRequest(chapterUrl = "url", chapterIndex = 4, totalChapters = 5)
        assertTrue(last.hasPrevChapter)
        assertFalse(last.hasNextChapter)
    }

    // ── Fake mode ──

    @Test
    fun `fake mode returns fixture content`() {
        val result = ContentAdapterShell.contentFake()
        assertTrue(result is ContentAdapterShell.ContentResult.Ready)
    }

    // ── Real mode ──

    @Test
    fun `real mode loads content via getContent`() = runBlocking {
        ContentAdapterShell.enableRealMode()
        val req = ContentRequest(chapterUrl = "https://example.com/ch1", chapterTitle = "第一章")
        val result = ContentAdapterShell.contentReal(req)
        assertTrue(result is ContentAdapterShell.ContentResult.Ready)
    }

    @Test
    fun `real mode blank url returns error`() = runBlocking {
        ContentAdapterShell.enableRealMode()
        val result = ContentAdapterShell.contentReal(ContentRequest(chapterUrl = ""))
        assertTrue(result is ContentAdapterShell.ContentResult.Error)
    }

    @Test
    fun `fake mode ignores real call`() = runBlocking {
        val result = ContentAdapterShell.contentReal(ContentRequest(chapterUrl = "url"))
        assertTrue(result is ContentAdapterShell.ContentResult.Ready)
        assertEquals("第一章：阿长与《山海经》", (result as ContentAdapterShell.ContentResult.Ready).chapter.chapterTitle)
    }

    // ── Boundary ──

    @Test
    fun `content adapter shell does not import parser internals`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/reader/ContentAdapterShell.kt"
        ).readText()
        assertFalse("Must not import ContentParser", "ContentParser" in source)
        assertFalse("Must not import HttpClient", "HttpClient" in source)
    }
}
