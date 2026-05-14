package com.reader.android.data.adapter

import com.reader.android.data.storage.CachedChapter
import com.reader.android.data.storage.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookCompatTest {

    @Test
    fun `full mapping pipeline produces valid progress`() {
        val src = LocalBookSource("content://book.txt", "book.txt", LocalBookFormat.TXT)
        val meta = LocalBookMetadata(
            title = "Test Book", author = "Author",
            chapters = listOf(LocalChapterRef(0, "Ch1", 0, 100), LocalChapterRef(1, "Ch2", 101, 200))
        )
        val progress = LocalBookProgressMapper.toReadingProgress(src, meta)
        assertEquals("Test Book", progress.bookName)
        assertEquals("content://book.txt", progress.bookUrl)
        assertEquals(2, progress.totalChapters)
    }

    @Test
    fun `full mapping pipeline produces valid cached chapter`() {
        val src = LocalBookSource("content://book.txt", "book.txt", LocalBookFormat.TXT)
        val ch = LocalChapterRef(0, "第一章", 0, 500)
        val cached = LocalBookProgressMapper.toCachedChapter(src, ch, "第一章正文")

        assertEquals("第一章", cached.title)
        assertEquals("第一章正文", cached.content)
        assertTrue(cached.contentUrl.contains("content://book.txt"))
        assertTrue(cached.contentUrl.contains("#ch0"))
    }

    @Test
    fun `chapter URI scheme is consistent between progress and cache`() {
        val expectedUri = LocalBookProgressMapper.chapterUri("content://book.txt", 3)
        assertEquals("content://book.txt#ch3", expectedUri)
    }
}
