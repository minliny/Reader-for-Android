package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class TxtParserEdgeCaseTest {
    private val parser = TxtParser()

    @Test
    fun `detect English chapter markers`() {
        val lines = listOf("Title", "Chapter 1 Start", "content", "Chapter 2 Continue", "more")
        val result = parser.parse(lines)
        assertEquals(2, result.chapters.size)
        assertEquals("Chapter 1 Start", result.chapters[0].title)
    }

    @Test
    fun `detect volume-style headers`() {
        val lines = listOf("Title", "第一卷 少年游", "text", "第二卷 江湖远", "more")
        val result = parser.parse(lines)
        assertEquals(2, result.chapters.size)
        assertEquals("第一卷 少年游", result.chapters[0].title)
    }

    @Test
    fun `detect prefixed chapters like 第001章`() {
        val lines = listOf("Title", "第001章 觉醒", "content", "第002章 试炼", "more")
        val result = parser.parse(lines)
        assertEquals(2, result.chapters.size)
    }

    @Test
    fun `prologue and epilogue detected as chapters`() {
        val lines = listOf("Book", "序言", "intro content", "第一章", "ch1", "尾声", "end")
        val result = parser.parse(lines)
        assert(result.chapters.size >= 2)
    }

    @Test
    fun `single chapter detected with endLine at last line`() {
        val lines = listOf("Title", "第一章 唯一", "line1", "line2")
        val result = parser.parse(lines)
        assertEquals(1, result.chapters.size)
        assertEquals(3, result.chapters[0].endLine) // last line index
    }
}
