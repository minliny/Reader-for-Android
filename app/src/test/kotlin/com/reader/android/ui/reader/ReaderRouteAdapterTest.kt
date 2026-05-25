package com.reader.android.ui.reader

import com.reader.android.ui.bookshelf.BookshelfBookUiModel
import com.reader.android.ui.bookshelf.BookshelfCacheState
import com.reader.android.ui.detail.BookDetailFixture
import com.reader.android.ui.search.SearchResultUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRouteAdapterTest {

    // ── From Bookshelf ──

    @Test
    fun `fromBookshelf carries chapter and book info`() {
        val book = BookshelfBookUiModel(
            "b1", "深空信号", null, "本地书籍",
            "第三章 回声", 0.4f, BookshelfCacheState.Cached,
            "detail", "https://example.com/ch3"
        )
        val payload = ReaderRouteAdapter.fromBookshelf(book)
        assertEquals("https://example.com/ch3", payload.contentUrl)
        assertEquals("第三章 回声", payload.chapterTitle)
        assertEquals("深空信号", payload.bookTitle)
        assertTrue(payload.hasProgress)
    }

    @Test
    fun `fromBookshelf without progress`() {
        val book = BookshelfBookUiModel(
            "b1", "新书", null, "", "", 0f, BookshelfCacheState.None, "", "url"
        )
        val payload = ReaderRouteAdapter.fromBookshelf(book)
        assertFalse(payload.hasProgress)
        assertEquals("新书", payload.bookTitle)
    }

    // ── From Detail ──

    @Test
    fun `fromDetail carries current chapter`() {
        val detail = BookDetailFixture.detail
        val payload = ReaderRouteAdapter.fromDetail(detail)
        assertEquals("fixture-reader-paper-mountain", payload.contentUrl)
        assertEquals("第一章 雨线", payload.chapterTitle)
        assertEquals("纸上群山", payload.bookTitle)
    }

    // ── From Search Result ──

    @Test
    fun `fromSearchResult uses first chapter url if provided`() {
        val result = SearchResultUiModel(
            "id", "新书", "作者", "来源", "科幻",
            "最新章", "intro", "detail-url"
        )
        val payload = ReaderRouteAdapter.fromSearchResult(result, firstChapterUrl = "https://example.com/ch1")
        assertEquals("https://example.com/ch1", payload.contentUrl)
        assertFalse(payload.hasProgress) // first-time open
    }

    @Test
    fun `fromSearchResult fallback to detail target`() {
        val result = SearchResultUiModel(
            "id", "新书", "作者", "来源", "科幻",
            "最新章", "intro", "detail-url"
        )
        val payload = ReaderRouteAdapter.fromSearchResult(result)
        assertEquals("detail-url", payload.contentUrl)
    }

    // ── Edge cases ──

    @Test
    fun `blank url is invalid`() {
        assertFalse(ReaderRouteAdapter.fallback("").isValid)
        assertTrue(ReaderRouteAdapter.fallback("url").isValid)
    }

    @Test
    fun `empty payload has safe defaults`() {
        assertEquals("", ReaderRoutePayload.Empty.contentUrl)
        assertFalse(ReaderRoutePayload.Empty.isValid)
    }

    @Test
    fun `no network or parser references`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/reader/ReaderRouteAdapter.kt"
        ).readText()
        assertFalse("Must not import HttpClient", "HttpClient" in source)
    }
}
