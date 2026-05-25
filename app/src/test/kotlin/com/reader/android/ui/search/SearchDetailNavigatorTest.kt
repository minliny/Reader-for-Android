package com.reader.android.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDetailNavigatorTest {

    @Test
    fun `payload from search result carries detailUrl`() {
        val result = SearchResultUiModel(
            id = "id1", title = "测试书", author = "作者",
            sourceName = "来源", category = "分类",
            latestChapter = "章", intro = "", detailTarget = "https://example.com/book/1"
        )
        val payload = SearchDetailNavigator.payload(result)
        assertEquals("https://example.com/book/1", payload.detailUrl)
        assertEquals("测试书", payload.title)
        assertEquals("来源", payload.sourceName)
        assertTrue(payload.isValid)
    }

    @Test
    fun `bookId is stable and deterministic`() {
        val a = SearchDetailPayload("https://example.com/book/1", "A", "Src")
        val b = SearchDetailPayload("https://example.com/book/1", "A", "Src")
        assertEquals(a.bookId, b.bookId)
    }

    @Test
    fun `different urls produce different bookIds`() {
        val a = SearchDetailPayload("url1", "A", "Src")
        val b = SearchDetailPayload("url2", "B", "Src")
        assertTrue(a.bookId != b.bookId)
    }

    @Test
    fun `blank detailUrl is invalid`() {
        val payload = SearchDetailPayload("", "A", "Src")
        assertFalse(payload.isValid)
        assertTrue(payload.bookId.startsWith("detail-"))
        assertTrue(payload.bookId.length > 7) // more than just "detail-"
    }

    @Test
    fun `fallback generates detailUrl from title`() {
        val payload = SearchDetailNavigator.fallback("测试书", "来源")
        assertTrue(payload.detailUrl.startsWith("fallback-"))
        assertTrue(payload.isValid)
    }

    @Test
    fun `no network or parser references`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/search/SearchDetailNavigator.kt"
        ).readText()
        assertFalse("Must not import HttpClient", "HttpClient" in source)
        assertFalse("Must not import parser", "Parser" in source)
    }
}
