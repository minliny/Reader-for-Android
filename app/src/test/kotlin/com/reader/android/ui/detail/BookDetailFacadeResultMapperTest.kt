package com.reader.android.ui.detail

import com.reader.android.data.model.BookInfo
import com.reader.android.data.model.TOCItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailFacadeResultMapperTest {

    private val bookInfo = BookInfo(
        name = "一剑独尊",
        author = "青鸾峰上",
        intro = "剑气纵横三万里",
        kind = "玄幻",
        coverUrl = null,
        tocUrl = "https://example.com/book/1/toc",
        wordCount = "320万字",
        latestChapter = "第1024章 决战",
        updateTime = "2026-05-14",
        origin = "笔趣阁"
    )

    private val tocItems = listOf(
        TOCItem("第一章 下山", "https://example.com/book/1/ch1"),
        TOCItem("第二章 初遇", "https://example.com/book/1/ch2"),
        TOCItem("", "", children = listOf(
            TOCItem("第三章 觉醒", "https://example.com/book/1/ch3")
        ))
    )

    @Test
    fun `maps book info to UI model`() {
        val result = BookDetailFacadeResultMapper.map(bookInfo, "https://example.com/book/1", tocItems)
        assertEquals("一剑独尊", result.title)
        assertEquals("青鸾峰上", result.author)
        assertEquals("笔趣阁", result.sourceName)
        assertEquals("玄幻", result.category)
        assertEquals("320万字", result.wordCount)
        assertEquals("第1024章 决战", result.latestChapter)
        assertEquals("2026-05-14", result.updateTime)
        assertEquals("剑气纵横三万里", result.intro)
    }

    @Test
    fun `generates stable id from detailUrl`() {
        val a = BookDetailFacadeResultMapper.map(bookInfo, "https://example.com/book/1", tocItems)
        val b = BookDetailFacadeResultMapper.map(bookInfo, "https://example.com/book/1", tocItems)
        assertEquals(a.id, b.id)
        assertTrue(a.id.startsWith("detail-"))
    }

    @Test
    fun `maps TOC preview from toc items`() {
        val result = BookDetailFacadeResultMapper.map(bookInfo, "url", tocItems)
        val toc = result.tocPreview
        assertEquals(3, toc.chapterCount) // 2 direct + 1 nested
        assertEquals("第一章 下山", toc.firstChapterTitle)
        assertEquals("第三章 觉醒", toc.latestChapterTitle)
    }

    @Test
    fun `empty TOC produces empty preview`() {
        val result = BookDetailFacadeResultMapper.map(bookInfo, "url", emptyList())
        val toc = result.tocPreview
        assertEquals(0, toc.chapterCount)
        assertEquals("", toc.firstChapterTitle)
    }

    @Test
    fun `blank author uses fallback`() {
        val result = BookDetailFacadeResultMapper.map(bookInfo.copy(author = ""), "url")
        assertEquals("未知作者", result.author)
    }

    @Test
    fun `null fields map to empty defaults`() {
        val minimal = BookInfo(name = "Test", author = "A")
        val result = BookDetailFacadeResultMapper.map(minimal, "url")
        assertEquals("", result.category)
        assertEquals("", result.wordCount)
        assertEquals("", result.latestChapter)
        assertEquals("", result.updateTime)
        assertEquals("", result.intro)
        assertEquals("", result.cover)
    }

    @Test
    fun `local state fields have safe defaults`() {
        val result = BookDetailFacadeResultMapper.map(bookInfo, "url")
        assertEquals("", result.currentChapter) // TODO
        assertEquals(0f, result.readingProgress) // TODO
        assertFalse(result.isInBookshelf) // TODO
        assertEquals("未缓存", result.cacheStatus) // TODO
    }

    @Test
    fun `available actions include core items`() {
        val result = BookDetailFacadeResultMapper.map(bookInfo, "url")
        assertTrue(result.availableActions.contains("开始阅读"))
        assertTrue(result.availableActions.contains("查看目录"))
    }
}
