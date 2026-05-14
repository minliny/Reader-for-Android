package com.reader.android.data.bridge

import com.reader.android.data.model.BookSource
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.model.TOCItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeContractTest {

    private val fakeBridge = FakeCoreBridge()

    // ── FakeCoreBridge method contract ──

    @Test
    fun `FakeCoreBridge search returns non-empty results`() = runBlocking {
        val source = BookSource(sourceUrl = "http://test", sourceName = "test")
        val results = fakeBridge.search(SearchQuery("test"), source)
        assertTrue(results.isNotEmpty())
        results.forEach { item ->
            assertTrue(item.name.isNotBlank())
            assertTrue(item.author.isNotBlank())
            assertEquals("test", item.sourceName)
        }
    }

    @Test
    fun `FakeCoreBridge getBookInfo returns all required fields`() = runBlocking {
        val source = BookSource(sourceUrl = "http://test", sourceName = "test")
        val info = fakeBridge.getBookInfo("http://test/detail", source)
        assertTrue(info.name.isNotBlank())
        assertTrue(info.author.isNotBlank())
        assertEquals("test", info.origin)
    }

    @Test
    fun `FakeCoreBridge getTOC returns hierarchical chapters`() = runBlocking {
        val source = BookSource(sourceUrl = "http://test", sourceName = "test")
        val toc = fakeBridge.getTOC("http://test/toc", source)
        assertTrue(toc.isNotEmpty())
        // At least one chapter with URL
        val chaptersWithUrl = toc.flatMap { collectUrls(it) }
        assertTrue(chaptersWithUrl.isNotEmpty())
        chaptersWithUrl.forEach { url ->
            assertTrue(url.isNotBlank())
        }
    }

    @Test
    fun `FakeCoreBridge getContent returns non-empty content`() = runBlocking {
        val source = BookSource(sourceUrl = "http://test", sourceName = "test")
        val page = fakeBridge.getContent("http://test/ch1", source)
        assertTrue(page.content.isNotBlank())
        assertTrue(page.title != null)
    }

    // ── BridgeResult sealed class ──

    @Test
    fun `BridgeResult Success wraps data`() {
        val result: BridgeResult<String> = BridgeResult.Success("hello")
        assertEquals("hello", (result as BridgeResult.Success).data)
    }

    @Test
    fun `BridgeResult Failure wraps error`() {
        val error = ReaderError(
            code = ReaderErrorCode.NETWORK,
            stage = ReaderFailureStage.SEARCH,
            message = "timeout"
        )
        val result: BridgeResult<String> = BridgeResult.Failure(error)
        assertTrue(result is BridgeResult.Failure)
        val failure = result as BridgeResult.Failure
        assertEquals(ReaderErrorCode.NETWORK, failure.error.code)
        assertEquals(ReaderFailureStage.SEARCH, failure.error.stage)
        assertEquals("timeout", failure.error.message)
    }

    // ── ReaderErrorCode completeness ──

    @Test
    fun `all 7 ReaderErrorCode values are defined`() {
        val codes = ReaderErrorCode.entries
        assertEquals(7, codes.size)
        val expectedCodes = setOf(
            "NETWORK", "PARSE", "TIMEOUT", "NOT_FOUND",
            "UNAUTHORIZED", "FORBIDDEN", "UNKNOWN"
        )
        codes.forEach { code ->
            assertTrue("Missing error code: ${code.name}", expectedCodes.contains(code.name))
        }
    }

    // ── ReaderFailureStage completeness ──

    @Test
    fun `all 4 ReaderFailureStage values are defined`() {
        val stages = ReaderFailureStage.entries
        assertEquals(4, stages.size)
        val expectedStages = setOf("SEARCH", "TOC", "CONTENT", "BOOK_INFO")
        stages.forEach { stage ->
            assertTrue("Missing stage: ${stage.name}", expectedStages.contains(stage.name))
        }
    }

    // ── ReaderError construction ──

    @Test
    fun `ReaderError with all fields`() {
        val error = ReaderError(
            code = ReaderErrorCode.PARSE,
            stage = ReaderFailureStage.CONTENT,
            message = "HTML parse failed",
            sourceName = "笔趣阁"
        )
        assertEquals(ReaderErrorCode.PARSE, error.code)
        assertEquals(ReaderFailureStage.CONTENT, error.stage)
        assertEquals("HTML parse failed", error.message)
        assertEquals("笔趣阁", error.sourceName)
    }

    @Test
    fun `ReaderError with optional fields null`() {
        val error = ReaderError(
            code = ReaderErrorCode.UNKNOWN,
            stage = ReaderFailureStage.BOOK_INFO
        )
        assertEquals(null, error.message)
        assertEquals(null, error.sourceName)
    }
}

private fun collectUrls(item: TOCItem): List<String> {
    val urls = mutableListOf<String>()
    if (item.url.isNotBlank()) urls.add(item.url)
    item.children.forEach { urls.addAll(collectUrls(it)) }
    return urls
}
