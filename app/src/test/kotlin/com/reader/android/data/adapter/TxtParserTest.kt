package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtParserTest {
    private val parser = TxtParser()

    @Test
    fun `parse text with Chinese chapter markers`() {
        val lines = listOf(
            "我的小说",
            "",
            "第一章 开始",
            "正文内容第一段。",
            "继续正文。",
            "第二章 发展",
            "继续第二章的正文内容。",
            "更多内容。"
        )
        val result = parser.parse(lines)
        assertEquals("我的小说", result.title)
        assertEquals(2, result.chapters.size)
        assertEquals("第一章 开始", result.chapters[0].title)
        assertEquals("第二章 发展", result.chapters[1].title)
    }

    @Test
    fun `parse text with numeric chapters`() {
        val lines = listOf("Title", "第1章 开始", "content", "第2章 继续", "more")
        val result = parser.parse(lines)
        assertEquals(2, result.chapters.size)
    }

    @Test
    fun `parse text with no chapters returns empty`() {
        val result = parser.parse(listOf("Title", "line1", "line2"))
        assertEquals(0, result.chapters.size)
    }

    @Test
    fun `detect UTF-8 BOM`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'a'.code.toByte())
        assertEquals("UTF-8-BOM", parser.detectEncoding(bytes))
    }

    @Test
    fun `detect UTF-16LE BOM`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte())
        assertEquals("UTF-16LE", parser.detectEncoding(bytes))
    }

    @Test
    fun `default encoding is UTF-8`() {
        assertEquals("UTF-8", parser.detectEncoding(byteArrayOf('a'.code.toByte())))
    }
}
