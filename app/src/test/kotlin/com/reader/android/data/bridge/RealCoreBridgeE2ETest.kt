package com.reader.android.data.bridge

import com.reader.android.AppProvider
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.network.FixtureHttpTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end test: RealCoreBridge with static HTML fixtures.
 * Proves the full pipeline: search → bookInfo → toc → content → clean text.
 */
class RealCoreBridgeE2ETest {

    private lateinit var transport: FixtureHttpTransport
    private lateinit var bridge: RealCoreBridge
    private lateinit var source: BookSource

    @Before
    fun setup() {
        AppProvider.initForTesting()
        AppProvider.enableNetworkForTestingOnly()
        transport = FixtureHttpTransport()

        // ── Fixture HTML ──

        // 1) Search results page
        transport.register(
            "https://www.biquge.com/search?q=%E5%89%91%E6%9D%A5",
            FixtureHttpTransport.successHtml(SEARCH_HTML)
        )

        // 2) Book detail page
        transport.register(
            "https://www.biquge.com/book/123",
            FixtureHttpTransport.successHtml(BOOK_INFO_HTML)
        )

        // 3) TOC page
        transport.register(
            "https://www.biquge.com/book/123/toc",
            FixtureHttpTransport.successHtml(TOC_HTML)
        )

        // 4) Chapter content page
        transport.register(
            "https://www.biquge.com/book/123/ch1",
            FixtureHttpTransport.successHtml(CONTENT_HTML)
        )

        bridge = RealCoreBridge(transport)
        source = BookSource(
            sourceUrl = "https://www.biquge.com",
            sourceName = "笔趣阁",
            searchUrl = "/search?q=key"
        )
    }

    // ── Pipeline step-by-step ──

    @Test
    fun `search returns expected results`() = runBlocking {
        val results = bridge.search(SearchQuery("剑来"), source)

        assertEquals(2, results.size)
        assertEquals("剑来", results[0].name)
        assertEquals("烽火戏诸侯", results[0].author)
        assertEquals("https://www.biquge.com/book/123", results[0].detailUrl)
        assertEquals("笔趣阁", results[0].sourceName)
    }

    @Test
    fun `getBookInfo returns parsed metadata`() = runBlocking {
        val info = bridge.getBookInfo("https://www.biquge.com/book/123", source)

        assertEquals("剑来", info.name)
        assertEquals("烽火戏诸侯", info.author)
        assertNotNull(info.intro)
        assertTrue(info.intro!!.contains("大千世界"))
        assertEquals("https://www.biquge.com/book/123/toc", info.tocUrl)
    }

    @Test
    fun `getTOC returns chapter list`() = runBlocking {
        val toc = bridge.getTOC("https://www.biquge.com/book/123/toc", source)

        assertTrue(toc.isNotEmpty())
        // Chapters should be grouped under default volume
        val firstVolume = toc.first()
        assertEquals("第一卷 笼中雀", firstVolume.title)
        assertTrue(firstVolume.children.size >= 2)
        assertEquals("第一章 笼中雀", firstVolume.children[0].title)
        assertEquals("https://www.biquge.com/book/123/ch1", firstVolume.children[0].url)
    }

    @Test
    fun `getContent returns cleaned chapter text`() = runBlocking {
        val page = bridge.getContent("https://www.biquge.com/book/123/ch1", source)

        assertEquals("第一章 笼中雀", page.title)
        assertTrue(page.content.contains("小镇名叫泥瓶巷"))
        assertTrue(page.content.contains("陈平安"))
        // Should NOT contain HTML tags
        assertFalse(page.content.contains("<div"))
        assertFalse(page.content.contains("<br"))
        assertFalse(page.content.contains("&nbsp;"))
    }

    // ── Full pipeline end-to-end ──

    @Test
    fun `full pipeline search to content returns readable text`() = runBlocking {
        // Step 1: Search
        val searchResults = bridge.search(SearchQuery("剑来"), source)
        assertTrue(searchResults.isNotEmpty())
        val firstResult = searchResults[0]

        // Step 2: Book Info
        val bookInfo = bridge.getBookInfo(firstResult.detailUrl!!, source)
        assertNotNull(bookInfo.tocUrl)

        // Step 3: TOC
        val toc = bridge.getTOC(bookInfo.tocUrl!!, source)
        assertTrue(toc.isNotEmpty())
        val firstChapter = toc.first().children.first()

        // Step 4: Content
        val content = bridge.getContent(firstChapter.url, source)
        assertNotNull(content.title)
        assertTrue(content.content.isNotBlank())

        // Verify it's clean text ready for Reader consumption
        assertFalse(content.content.contains("<"))
        assertFalse(content.content.contains("&nbsp;"))
        assertTrue(content.content.length > 50)
    }

    // ── Error handling ──

    @Test(expected = ReaderException::class)
    fun `search with 404 transport throws ReaderException`() {
        runBlocking {
            // Use a different search URL not registered in setup(), so transport falls through to default(404)
            val missingSource = source.copy(searchUrl = "/search-nonexistent?q=key")
            bridge.search(SearchQuery("剑来"), missingSource)
        }
    }

    @Test(expected = ReaderException::class)
    fun `getBookInfo with unparseable HTML throws ReaderException`() {
        runBlocking {
            transport.register(
                "https://www.biquge.com/book/999",
                FixtureHttpTransport.successHtml("<html><body>Not a book page</body></html>")
            )
            bridge.getBookInfo("https://www.biquge.com/book/999", source)
        }
    }

    // ── URL resolution ──

    @Test
    fun `relative search URL resolves against source base`() = runBlocking {
        // searchUrl is "/search?q=key", should resolve to "https://www.biquge.com/search?q=剑来"
        // Already covered by transport registration in setup()
        val results = bridge.search(SearchQuery("剑来"), source)
        assertEquals(2, results.size)
    }

    // ── Fixture HTML data ──

    companion object {
        val SEARCH_HTML = """
            <html><body>
            <div class="result-list">
                <div class="result-item">
                    <a href="/book/123">剑来</a> 作者：烽火戏诸侯
                    <span>最新：第1180章 心关</span>
                </div>
                <div class="result-item">
                    <a href="/book/456">雪中悍刀行</a> 作者：烽火戏诸侯
                    <span>最新：完结</span>
                </div>
            </div>
            </body></html>
        """.trimIndent()

        val BOOK_INFO_HTML = """
            <html><body>
            <h1>剑来</h1>
            <div class="info">
                作者：<a href="/author/1">烽火戏诸侯</a>
                分类：<a href="/sort/xianxia">仙侠</a>
            </div>
            <div class="intro">
                大千世界，无奇不有。我陈平安，唯有一剑，可搬山，倒海，降妖，镇魔，敕神，摘星，断江，摧城，开天！
            </div>
            <a href="/book/123/toc">目录</a>
            <div>最新章节：<a href="/book/123/ch1180">第1180章 心关</a></div>
            </body></html>
        """.trimIndent()

        val TOC_HTML = """
            <html><body>
            <h2>第一卷 笼中雀</h2>
            <a href="/book/123/ch1">第一章 笼中雀</a>
            <a href="/book/123/ch2">第二章 泥瓶巷</a>
            <a href="/book/123/ch3">第三章 搬山</a>
            </body></html>
        """.trimIndent()

        val CONTENT_HTML = """
            <html><body>
            <h1>第一章 笼中雀</h1>
            <div id="content">
                小镇名叫泥瓶巷，巷子口常年聚着一群游手好闲的少年。<br/><br/>
                陈平安是这群少年里最不起眼的一个。他长得不高，也不算壮实，唯一能让人记住的，是他总是笑。<br/><br/>
                "陈平安，你爹呢？"有人问。<br/><br/>
                陈平安咧嘴一笑，露出两排白牙："我爹去给我娘摘星星了。"<br/><br/>
                说完这句话，他转身走进了巷子深处。夕阳把他的影子拉得很长，像一把剑。
            </div>
            <a href="/book/123/ch2">下一章</a>
            </body></html>
        """.trimIndent()
    }
}
