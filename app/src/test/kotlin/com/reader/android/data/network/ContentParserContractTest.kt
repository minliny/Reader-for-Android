package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentParserContractTest {

    private val parser = ContentParser()

    @Test
    fun `parse valid content HTML returns title and cleaned paragraphs`() {
        val html = """
            <html><body>
            <h1>第一章 下山</h1>
            <div id="content">
                秋风萧瑟，落叶漫天。<br/>
                少年背着长剑，独自走在山间小道上。<br/><br/>
                远处传来钟声，一座古城渐渐浮现在地平线上。
            </div>
            <a href="/ch/2">下一章</a>
            </body></html>
        """.trimIndent()

        val page = parser.parseContentResponse(html)

        assertNotNull(page)
        assertEquals("第一章 下山", page!!.title)
        assertTrue(page.content.contains("秋风萧瑟"))
        assertTrue(page.content.contains("少年背着长剑"))
        assertTrue(page.content.contains("远处传来钟声"))
        assertEquals("/ch/2", page.nextPageUrl)
    }

    @Test
    fun `parse content HTML without title returns content with null title`() {
        val html = """
            <html><body>
            <div class="content">
                剑气纵横三万里，一剑光寒十九洲。<br/>
                顺为凡，逆则仙。
            </div>
            </body></html>
        """.trimIndent()

        val page = parser.parseContentResponse(html)

        assertNotNull(page)
        assertNull(page!!.title)
        assertTrue(page.content.contains("剑气纵横"))
        assertTrue(page.content.contains("顺为凡"))
    }

    @Test
    fun `parse HTML without content container returns null`() {
        val html = "<html><body><p>Welcome to the reading page</p></body></html>"
        val page = parser.parseContentResponse(html)
        assertNull(page)
    }

    @Test
    fun `parse content HTML cleans nbsp and br tags`() {
        val html = """
            <html><body>
            <div id="content">
                第一章&nbsp;开始<br/>
                第一段内容<br/>
                第二段内容
            </div>
            </body></html>
        """.trimIndent()

        val page = parser.parseContentResponse(html)

        assertNotNull(page)
        assertTrue(page!!.content.contains("第一章 开始"))
        assertTrue(page.content.contains("第一段内容"))
        // &nbsp; should be replaced with space, <br/> with newline
        assertTrue(!page.content.contains("&nbsp;"))
        assertTrue(!page.content.contains("<br"))
    }
}
