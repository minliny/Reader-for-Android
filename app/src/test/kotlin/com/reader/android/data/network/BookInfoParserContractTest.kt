package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookInfoParserContractTest {

    private val parser = BookInfoParser()

    @Test
    fun `parse valid book info HTML returns expected fields`() {
        val html = """
            <html><body>
            <h1>一剑独尊</h1>
            <div>作者：<a href="/author/1">青鸾峰上</a></div>
            <div class="intro">少年叶玄持剑下山，从此踏上一条无敌之路，纵横三界，剑指苍穹。</div>
            <div>分类：<a href="/kind/xh">玄幻</a></div>
            <div>最新章节：<a href="/ch/1024">第1024章 决战</a></div>
            <a href="/toc/123">目录</a>
            </body></html>
        """.trimIndent()

        val info = parser.parseBookInfoResponse(html, "笔趣阁")

        assertNotNull(info)
        assertEquals("一剑独尊", info!!.name)
        assertEquals("青鸾峰上", info.author)
        assertNotNull(info.intro)
        assertTrue(info.intro!!.contains("少年叶玄"))
        assertEquals("玄幻", info.kind)
        assertEquals("/toc/123", info.tocUrl)
        assertEquals("第1024章 决战", info.latestChapter)
        assertEquals("笔趣阁", info.origin)
    }

    @Test
    fun `parse HTML with og title fallback returns book info`() {
        val html = """
            <html><head>
            <meta property="og:title" content="仙逆"/>
            </head><body>
            作者：耳根
            </body></html>
        """.trimIndent()

        val info = parser.parseBookInfoResponse(html, "test")

        assertNotNull(info)
        assertEquals("仙逆", info!!.name)
        assertEquals("耳根", info.author)
    }

    @Test
    fun `parse HTML without title returns null`() {
        val html = "<html><body><p>Some page without book info</p></body></html>"
        val info = parser.parseBookInfoResponse(html, "test")
        assertNull(info)
    }

    @Test
    fun `parse HTML without author defaults to unknown`() {
        val html = "<html><body><h1>无作者之书</h1></body></html>"
        val info = parser.parseBookInfoResponse(html, "test")
        assertNotNull(info)
        assertEquals("无作者之书", info!!.name)
        assertEquals("未知", info.author)
    }
}
