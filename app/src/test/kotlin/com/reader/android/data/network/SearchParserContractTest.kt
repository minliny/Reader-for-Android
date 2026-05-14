package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchParserContractTest {

    private val parser = SearchParser()

    @Test
    fun `parse valid search HTML returns expected results with name author detailUrl`() {
        val html = """
            <a href="/book/123">一剑独尊</a> 作者：青鸾峰上
            <a href="/book/456">仙逆</a> 作者：耳根
        """.trimIndent()

        val results = parser.parseSearchResponse(html, "笔趣阁")

        assertEquals(2, results.size)
        assertEquals("一剑独尊", results[0].name)
        assertEquals("青鸾峰上", results[0].author)
        assertEquals("/book/123", results[0].detailUrl)
        assertEquals("笔趣阁", results[0].sourceName)
        assertEquals("仙逆", results[1].name)
        assertEquals("耳根", results[1].author)
    }

    @Test
    fun `parse empty HTML returns empty list`() {
        val results = parser.parseSearchResponse("", "test")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse HTML without search result patterns returns empty list`() {
        val html = "<html><body><p>No results found</p></body></html>"
        val results = parser.parseSearchResponse(html, "test")
        assertTrue(results.isEmpty())
    }
}
