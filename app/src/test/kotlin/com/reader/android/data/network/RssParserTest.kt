package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {
    private val parser = RssParser()

    @Test
    fun `parse valid RSS 2_0 feed returns feed with items`() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0">
            <channel>
                <title>笔趣阁更新</title>
                <link>http://biquge.com</link>
                <description>最新小说更新</description>
                <item>
                    <title>一剑独尊 第1024章</title>
                    <link>http://biquge.com/book/1/ch1024</link>
                    <pubDate>2026-05-15</pubDate>
                </item>
                <item>
                    <title>仙逆 第2088章</title>
                    <link>http://biquge.com/book/2/ch2088</link>
                    <author>耳根</author>
                </item>
            </channel>
            </rss>
        """.trimIndent()

        val feed = parser.parse(xml)
        assertNotNull(feed)
        assertEquals("笔趣阁更新", feed!!.title)
        assertEquals(2, feed.items.size)
        assertEquals("一剑独尊 第1024章", feed.items[0].title)
        assertEquals("耳根", feed.items[1].author)
    }

    @Test
    fun `parse CDATA description`() {
        val xml = """
            <rss><channel><title>Feed</title><link>url</link>
            <item><title>Item</title><link>url</link>
            <description><![CDATA[This is a <b>rich</b> description]]></description>
            </item></channel></rss>
        """.trimIndent()

        val feed = parser.parse(xml)
        assertNotNull(feed)
        assertTrue(feed!!.items[0].description?.contains("<b>rich</b>") == true)
    }

    @Test
    fun `parse empty or invalid XML returns null`() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("<html>not rss</html>"))
    }

    @Test
    fun `RssSubscription holds feed metadata`() {
        val sub = RssSubscription(
            feedUrl = "http://feed.com/rss",
            title = "Test Feed",
            lastUpdated = 1700000000L,
            lastItemGuid = "guid-123"
        )
        assertEquals("http://feed.com/rss", sub.feedUrl)
        assertEquals(1700000000L, sub.lastUpdated)
        assertEquals("guid-123", sub.lastItemGuid)
    }
}
