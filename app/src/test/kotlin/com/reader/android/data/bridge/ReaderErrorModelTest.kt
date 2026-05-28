package com.reader.android.data.bridge

import com.reader.android.AppProvider
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.network.FixtureHttpTransport
import com.reader.android.data.network.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Verifies error model: each error type maps to correct ReaderErrorCode and ReaderFailureStage.
 * Uses FixtureHttpTransport to simulate HTTP errors and parse failures.
 */
class ReaderErrorModelTest {

    private lateinit var bridge: RealCoreBridge
    private lateinit var source: BookSource
    private lateinit var transport: FixtureHttpTransport

    @Before
    fun setup() {
        AppProvider.initForTesting()
        AppProvider.enableNetworkForTestingOnly()
        transport = FixtureHttpTransport()
        bridge = RealCoreBridge(transport)
        source = BookSource(
            sourceUrl = "https://www.biquge.com",
            sourceName = "笔趣阁",
            searchUrl = "/search?q=key"
        )
    }

    // ── NETWORK errors ──

    @Test
    fun `404 search throws ReaderException with NETWORK`() = runBlocking {
        transport.default(HttpResponse(code = 404, body = "Not Found", headers = emptyMap(), requestUrl = ""))
        try {
            bridge.search(SearchQuery("剑来"), source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals(ReaderErrorCode.NETWORK, e.error.code)
            assertEquals(ReaderFailureStage.SEARCH, e.error.stage)
            assertNotNull(e.error.message)
            assertTrue(e.error.message!!.contains("404"))
        }
    }

    @Test
    fun `500 search throws ReaderException with NETWORK`() = runBlocking {
        transport.default(HttpResponse(code = 500, body = "Server Error", headers = emptyMap(), requestUrl = ""))
        try {
            bridge.search(SearchQuery("剑来"), source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals(ReaderErrorCode.NETWORK, e.error.code)
            assertEquals(ReaderFailureStage.SEARCH, e.error.stage)
        }
    }

    @Test
    fun `404 bookInfo throws ReaderException with NETWORK`() = runBlocking {
        transport.register("https://www.biquge.com/book/999", HttpResponse(code = 404, body = "", headers = emptyMap(), requestUrl = ""))
        try {
            bridge.getBookInfo("https://www.biquge.com/book/999", source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals(ReaderErrorCode.NETWORK, e.error.code)
            assertEquals(ReaderFailureStage.BOOK_INFO, e.error.stage)
            assertTrue(e.error.message!!.contains("404"))
        }
    }

    @Test
    fun `404 TOC throws ReaderException with NETWORK`() = runBlocking {
        transport.register("https://www.biquge.com/book/123/toc", HttpResponse(code = 404, body = "", headers = emptyMap(), requestUrl = ""))
        try {
            bridge.getTOC("https://www.biquge.com/book/123/toc", source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals(ReaderErrorCode.NETWORK, e.error.code)
            assertEquals(ReaderFailureStage.TOC, e.error.stage)
        }
    }

    @Test
    fun `404 content throws ReaderException with NETWORK`() = runBlocking {
        transport.register("https://www.biquge.com/book/123/ch1", HttpResponse(code = 404, body = "", headers = emptyMap(), requestUrl = ""))
        try {
            bridge.getContent("https://www.biquge.com/book/123/ch1", source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals(ReaderErrorCode.NETWORK, e.error.code)
            assertEquals(ReaderFailureStage.CONTENT, e.error.stage)
        }
    }

    // ── PARSE errors ──

    @Test
    fun `empty bookInfo HTML throws PARSE error`() = runBlocking {
        transport.register("https://www.biquge.com/book/999", FixtureHttpTransport.successHtml("<html><body></body></html>"))
        try {
            bridge.getBookInfo("https://www.biquge.com/book/999", source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals(ReaderErrorCode.PARSE, e.error.code)
            assertEquals(ReaderFailureStage.BOOK_INFO, e.error.stage)
        }
    }

    @Test
    fun `empty TOC HTML returns empty list`() = runBlocking {
        transport.register("https://www.biquge.com/book/123/toc", FixtureHttpTransport.successHtml("<html><body></body></html>"))
        val toc = bridge.getTOC("https://www.biquge.com/book/123/toc", source)
        // TOCParser returns empty list for non-matching HTML, not an exception
        assertEquals(0, toc.size)
    }

    @Test
    fun `empty content HTML throws PARSE error`() = runBlocking {
        transport.register("https://www.biquge.com/book/123/ch1", FixtureHttpTransport.successHtml("<html><body><div id='content'></div></body></html>"))
        try {
            bridge.getContent("https://www.biquge.com/book/123/ch1", source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals(ReaderErrorCode.PARSE, e.error.code)
            assertEquals(ReaderFailureStage.CONTENT, e.error.stage)
        }
    }

    // ── Error message preservation ──

    @Test
    fun `ReaderException preserves sourceName in error`() = runBlocking {
        transport.default(HttpResponse(code = 404, body = "", headers = emptyMap(), requestUrl = ""))
        try {
            bridge.search(SearchQuery("剑来"), source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertEquals("笔趣阁", e.error.sourceName)
        }
    }

    @Test
    fun `ReaderException preserves HTTP status in message`() = runBlocking {
        transport.default(HttpResponse(code = 500, body = "", headers = emptyMap(), requestUrl = ""))
        try {
            bridge.search(SearchQuery("剑来"), source)
            fail("Expected ReaderException")
        } catch (e: ReaderException) {
            assertTrue(e.error.message!!.contains("500"))
        }
    }
}
