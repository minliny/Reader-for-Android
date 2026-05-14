package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookProgressMapperTest {

    @Test
    fun `toReadingProgress maps local book to progress`() {
        val src = LocalBookSource("content://file.txt", "book.txt", LocalBookFormat.TXT)
        val meta = LocalBookMetadata(
            title = "My Book", author = "Author",
            chapters = listOf(
                LocalChapterRef(0, "Ch1", 0, 100),
                LocalChapterRef(1, "Ch2", 101, 200)
            )
        )
        val progress = LocalBookProgressMapper.toReadingProgress(src, meta)
        assertEquals("My Book", progress.bookName)
        assertEquals("Author", progress.author)
        assertEquals("Ch1", progress.currentChapterTitle)
        assertEquals(2, progress.totalChapters)
    }

    @Test
    fun `toReadingProgress with specific chapter`() {
        val src = LocalBookSource("uri", "name", LocalBookFormat.TXT)
        val meta = LocalBookMetadata("Title", chapters = listOf(
            LocalChapterRef(0, "Ch1"), LocalChapterRef(1, "Ch2")
        ))
        val progress = LocalBookProgressMapper.toReadingProgress(src, meta, meta.chapters[1])
        assertEquals("Ch2", progress.currentChapterTitle)
        assertEquals(1, progress.chapterIndex)
    }

    @Test
    fun `toCachedChapter maps to cache schema`() {
        val src = LocalBookSource("content://file.txt", "book.txt", LocalBookFormat.TXT)
        val ch = LocalChapterRef(0, "第一章", 0, 500)
        val cached = LocalBookProgressMapper.toCachedChapter(src, ch, "第一章内容")
        assertEquals("第一章", cached.title)
        assertTrue(cached.contentUrl.contains("#ch0"))
    }

    @Test
    fun `chapterUri generates consistent key`() {
        val uri = LocalBookProgressMapper.chapterUri("content://book.txt", 5)
        assertEquals("content://book.txt#ch5", uri)
    }
}
