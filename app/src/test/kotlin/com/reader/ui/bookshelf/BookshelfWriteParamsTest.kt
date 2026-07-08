package com.reader.ui.bookshelf

import com.reader.api.SearchBook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage for [BookshelfWriteParams] — verifies the JSON params contract for
 * `bookshelf.book.add` / `bookshelf.book.remove` CoreCommands without a running Core.
 *
 * Field names mirror the `bookshelf.list` response so the write/read contract is
 * symmetric: `bookId` is the key field, `title`/`author`/`coverUrl`/`intro`/`origin`
 * are the book metadata fields.
 */
class BookshelfWriteParamsTest {

    @Test
    fun `buildAddParams wraps book object with bookId title author coverUrl intro origin`() {
        val book = SearchBook(
            bookUrl = "https://source.example/book/123",
            name = "三体",
            author = "刘慈欣",
            coverUrl = "https://covers.example/123.jpg",
            intro = "文革后期的一颗红岸工程信号...",
            lastChapter = "第62章 黑暗森林",
            kind = "科幻",
            origin = "优书网"
        )

        val params = BookshelfWriteParams.buildAddParams(book)

        val bookObj = params.getJSONObject("book")
        assertEquals("https://source.example/book/123", bookObj.getString("bookId"))
        assertEquals("三体", bookObj.getString("title"))
        assertEquals("刘慈欣", bookObj.getString("author"))
        assertEquals("https://covers.example/123.jpg", bookObj.getString("coverUrl"))
        assertEquals("文革后期的一颗红岸工程信号...", bookObj.getString("intro"))
        assertEquals("优书网", bookObj.getString("origin"))
    }

    @Test
    fun `buildAddParams bookId mirrors bookUrl field`() {
        // The list response uses `bookId` as the key; SearchBook.bookUrl maps to it so
        // the write path is symmetric with the read path (parseBookshelfList).
        val book = SearchBook(
            bookUrl = "book-url-abc",
            name = "测试书",
            author = "",
            coverUrl = "",
            intro = "",
            lastChapter = "",
            kind = "",
            origin = ""
        )

        val params = BookshelfWriteParams.buildAddParams(book)
        val bookObj = params.getJSONObject("book")
        assertEquals("book-url-abc", bookObj.getString("bookId"))
    }

    @Test
    fun `buildRemoveParams uses bookId field matching list response key`() {
        val params = BookshelfWriteParams.buildRemoveParams("https://source.example/book/456")

        assertEquals(
            "https://source.example/book/456",
            params.getString("bookId")
        )
    }

    @Test
    fun `buildAddParams contains only the book wrapper key`() {
        val book = SearchBook(
            bookUrl = "x",
            name = "n",
            author = "a",
            coverUrl = "c",
            intro = "i",
            lastChapter = "",
            kind = "",
            origin = "o"
        )

        val params = BookshelfWriteParams.buildAddParams(book)
        // The top-level params must only contain the `book` wrapper key.
        assertEquals(1, params.length())
        assertTrue(params.has("book"))
    }

    @Test
    fun `buildRemoveParams contains only the bookId key`() {
        val params = BookshelfWriteParams.buildRemoveParams("any-book-url")

        assertEquals(1, params.length())
        assertTrue(params.has("bookId"))
    }
}
