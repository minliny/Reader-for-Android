package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressSaveFlowTest {

    @Test
    fun `save chapter updates all progress fields`() {
        val adapter = ReaderProgressSaveAdapter()
        adapter.saveChapter(
            chapterUrl = "https://example.com/ch2",
            chapterTitle = "第二章",
            chapterIndex = 1,
            totalChapters = 10,
            bookName = "测试书",
            scrollPosition = 0f
        )
        val state = adapter.current
        assertTrue(state.isCurrentChapter("https://example.com/ch2"))
        assertEquals("第二章", state.currentChapterTitle)
        assertEquals(1, state.chapterIndex)
        assertEquals(10, state.totalChapters)
        assertTrue(state.lastReadTime > 0)
    }

    @Test
    fun `update position changes scroll and timestamp`() {
        val adapter = ReaderProgressSaveAdapter()
        adapter.saveChapter(chapterUrl = "url", chapterTitle = "Ch1")

        val before = adapter.current.lastReadTime
        Thread.sleep(1)
        adapter.updatePosition(0.5f)

        assertEquals(0.5f, adapter.current.scrollPosition)
        assertTrue(adapter.current.lastReadTime > before)
    }

    @Test
    fun `position clamped to 0-1`() {
        val adapter = ReaderProgressSaveAdapter()
        adapter.saveChapter(chapterUrl = "url", chapterTitle = "Ch1")

        adapter.updatePosition(1.5f)
        assertEquals(1f, adapter.current.scrollPosition)

        adapter.updatePosition(-0.5f)
        assertEquals(0f, adapter.current.scrollPosition)
    }

    @Test
    fun `hasProgress false when empty`() {
        val adapter = ReaderProgressSaveAdapter()
        assertFalse(adapter.hasProgress())
    }

    @Test
    fun `hasProgress true after save`() {
        val adapter = ReaderProgressSaveAdapter()
        adapter.saveChapter(chapterUrl = "url", chapterTitle = "Ch1")
        assertTrue(adapter.hasProgress())
    }

    @Test
    fun `continue reading summary with progress`() {
        val adapter = ReaderProgressSaveAdapter()
        adapter.saveChapter(
            chapterUrl = "url", chapterTitle = "第三章", scrollPosition = 0.45f
        )
        assertEquals("第三章 · 45%", adapter.continueReadingSummary())
    }

    @Test
    fun `continue reading summary empty when no progress`() {
        assertEquals("", ReaderProgressSaveAdapter().continueReadingSummary())
    }

    // ── Regression ──

    @Test
    fun `save adapter does not import Room runtime`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/reader/ReaderProgressSaveAdapter.kt"
        ).readText()
        // Only checks actual Room imports, not TODO comments
        assertFalse("Must not import Room", "import androidx.room" in source)
    }
}
