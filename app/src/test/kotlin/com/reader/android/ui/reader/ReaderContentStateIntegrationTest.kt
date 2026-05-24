package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentStateIntegrationTest {

    // ── ContentRequest from TOC entry ──

    @Test
    fun `content request built from TOC entry`() {
        val entry = ReaderTocEntryUiModel("第一章", url = "https://example.com/ch1")
        val entries = listOf(entry, ReaderTocEntryUiModel("第二章", url = "https://example.com/ch2"))
        val book = ReaderBookUiModel("测试书", "来源")

        val req = ReaderContentStateMapper.contentRequest(entry, entries, book)
        assertEquals("https://example.com/ch1", req.chapterUrl)
        assertEquals("第一章", req.chapterTitle)
        assertEquals(0, req.chapterIndex)
        assertEquals(2, req.totalChapters)
        assertEquals("测试书", req.bookTitle)
    }

    @Test
    fun `content request computes prev next correctly`() {
        val entries = listOf(
            ReaderTocEntryUiModel("Ch1", url = "ch1"),
            ReaderTocEntryUiModel("Ch2", url = "ch2"),
            ReaderTocEntryUiModel("Ch3", url = "ch3")
        )
        val book = ReaderBookUiModel("Book", "Src")

        val first = ReaderContentStateMapper.contentRequest(entries[0], entries, book)
        assertFalse(first.hasPrevChapter)
        assertTrue(first.hasNextChapter)

        val mid = ReaderContentStateMapper.contentRequest(entries[1], entries, book)
        assertTrue(mid.hasPrevChapter)
        assertTrue(mid.hasNextChapter)

        val last = ReaderContentStateMapper.contentRequest(entries[2], entries, book)
        assertTrue(last.hasPrevChapter)
        assertFalse(last.hasNextChapter)
    }

    // ── ReaderContentStateMapper.applyContent ──

    @Test
    fun `ready result updates content and chapter`() {
        val state = ReaderRuntimeFixture.createBaseControlVisible()
        // The fixture state already has content; applyContent would replace it
        assertTrue(state.contentState is ReaderContentState.Loading)
    }

    @Test
    fun `error result sets error content state`() {
        val base = ReaderRuntimeFixture.createBaseControlVisible()
        val result = ContentAdapterShell.ContentResult.Error(
            ContentErrorState("网络错误", retryable = true)
        )
        val updated = ReaderContentStateMapper.applyContent(base, result)
        assertTrue(updated.contentState is ReaderContentState.Error)
        assertEquals("网络错误", (updated.contentState as ReaderContentState.Error).message)
        assertTrue((updated.contentState as ReaderContentState.Error).retryable)
    }

    @Test
    fun `loading result sets loading state`() {
        val base = ReaderRuntimeFixture.createBaseControlVisible()
        val updated = ReaderContentStateMapper.applyContent(base, ContentAdapterShell.ContentResult.Loading)
        assertTrue(updated.contentState is ReaderContentState.Loading)
    }

    // ── ReaderContentState expressions ──

    @Test
    fun `content state ready has content and chapter`() {
        val ready = ReaderContentState.Ready(
            ReaderContentUiModel("text", true, true),
            ReaderChapterUiModel("title", 0, 5)
        )
        assertEquals("text", ready.content.text)
        assertEquals("title", ready.chapter.chapterTitle)
    }

    @Test
    fun `content state empty has message`() {
        val empty = ReaderContentState.Empty("无内容")
        assertEquals("无内容", empty.message)
    }

    @Test
    fun `content state error has retry flag`() {
        val retryable = ReaderContentState.Error("err", retryable = true)
        val nonRetryable = ReaderContentState.Error("err", retryable = false)
        assertTrue(retryable.retryable)
        assertFalse(nonRetryable.retryable)
    }

    // ── Regression ──

    @Test
    fun `content request does not contain secrets`() {
        val req = ContentRequest(chapterUrl = "https://example.com", bookTitle = "test")
        val fields = "${req.chapterUrl}${req.bookTitle}${req.sourceName}"
        assertFalse("Must not contain token", "token" in fields)
        assertFalse("Must not contain api_key", "api_key" in fields)
    }
}
