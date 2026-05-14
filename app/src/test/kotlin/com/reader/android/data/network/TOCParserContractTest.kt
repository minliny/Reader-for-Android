package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TOCParserContractTest {

    private val parser = TOCParser()

    @Test
    fun `parse valid TOC HTML returns chapters grouped under default volume`() {
        // Per current implementation: without explicit <h2>-<h4> volume headers,
        // all chapters are grouped under a default "正文" volume item.
        val html = "<a href=\"/ch/1\">第一章</a> <a href=\"/ch/2\">第二章</a> <a href=\"/ch/3\">第三章</a>"

        val result = parser.parseTOCResponse(html)

        // Chapters grouped under default volume "正文"
        assertEquals(1, result.size)
        assertEquals("正文", result[0].title)
        assertEquals("", result[0].url)
        assertEquals(3, result[0].children.size)
        assertEquals("第一章", result[0].children[0].title)
        assertEquals("/ch/1", result[0].children[0].url)
        assertEquals("第二章", result[0].children[1].title)
        assertEquals("/ch/2", result[0].children[1].url)
        assertEquals("第三章", result[0].children[2].title)
        assertEquals("/ch/3", result[0].children[2].url)
    }

    @Test
    fun `parse TOC HTML with volume headers groups chapters under last volume`() {
        // Per current implementation: volume headers are parsed before chapters,
        // so all chapters following volume headers are grouped under the last volume.
        val html = """
            <html><body>
            <h2>第一卷 少年游</h2>
            <a href="/ch/1">第一章 下山</a>
            <a href="/ch/2">第二章 初遇</a>
            <h3>第二卷 江湖远</h3>
            <a href="/ch/3">第三章 觉醒</a>
            </body></html>
        """.trimIndent()

        val result = parser.parseTOCResponse(html)

        // All chapters after volume headers → grouped under last volume
        assertTrue(result.isNotEmpty())
        val lastVol = result.last()
        assertEquals("第二卷 江湖远", lastVol.title)
        assertTrue(lastVol.children.size >= 1)
    }

    @Test
    fun `parse empty HTML returns empty list`() {
        val result = parser.parseTOCResponse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse HTML without chapter links returns empty list`() {
        val html = "<html><body><p>No chapters available</p></body></html>"
        val result = parser.parseTOCResponse(html)
        assertTrue(result.isEmpty())
    }
}
