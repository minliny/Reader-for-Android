package com.reader.ui.bookshelf

import com.reader.api.Book
import com.reader.api.SearchBook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM Core wire-contract coverage for shelf writes and reads. Core keys
 * shelf entries by `(sourceId, bookId)`; `origin` is presentation metadata
 * and is never a substitute for the root `book.search` source id.
 */
class BookshelfWriteParamsTest {

    @Test
    fun `buildAddParams retains root sourceId in flat Core contract`() {
        val book = SearchBook(
            bookUrl = "https://source.example/book/123",
            name = "三体",
            author = "刘慈欣",
            coverUrl = "https://covers.example/123.jpg",
            intro = "文革后期的一颗红岸工程信号...",
            lastChapter = "第62章 黑暗森林",
            kind = "科幻",
            origin = "优书网",
            sourceId = "source-42"
        )

        val params = BookshelfWriteParams.buildAddParams(book)

        assertEquals("source-42", params.getString("sourceId"))
        assertEquals("https://source.example/book/123", params.getString("bookId"))
        assertEquals("三体", params.getString("title"))
        assertEquals("刘慈欣", params.getString("author"))
        assertEquals("https://covers.example/123.jpg", params.getString("coverUrl"))
        assertEquals("文革后期的一颗红岸工程信号...", params.getString("intro"))
        assertEquals("科幻", params.getString("kind"))
        assertEquals("第62章 黑暗森林", params.getString("lastChapter"))
        assertFalse("Core BookshelfAddParams has no nested book object", params.has("book"))
        assertFalse("display origin is not a Core identity field", params.has("origin"))
    }

    @Test
    fun `buildAddParams rejects missing root sourceId instead of using display origin`() {
        val missingIdentity = SearchBook(
            bookUrl = "book-url-abc",
            name = "测试书",
            author = "",
            coverUrl = "",
            intro = "",
            lastChapter = "",
            kind = "",
            origin = "显示名称"
        )

        var rejected = false
        try {
            BookshelfWriteParams.buildAddParams(missingIdentity)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `buildRemoveParams uses the same composite identity`() {
        val params = BookshelfWriteParams.buildRemoveParams(
            Book(bookUrl = "https://source.example/book/456", name = "三体", sourceId = "source-42")
        )

        assertEquals("source-42", params.getString("sourceId"))
        assertEquals("https://source.example/book/456", params.getString("bookId"))
        assertEquals(2, params.length())
    }

    @Test
    fun `shelf parser retains Core sourceId for later book open`() {
        val books = BookshelfViewModel.parseBookshelfList(
            JSONObject(
                """{"books":[{"sourceId":"local","bookId":"local://book/1","title":"本地书","author":"作者","kind":"txt","lastChapter":"第 3 章"}]}"""
            )
        )

        val book = books.single()
        assertEquals("local", book.sourceId)
        assertEquals("local", book.origin)
        assertEquals("local://book/1", book.bookUrl)
        assertEquals("txt", book.kind)
        assertEquals("第 3 章", book.latestChapterTitle)
    }
}
