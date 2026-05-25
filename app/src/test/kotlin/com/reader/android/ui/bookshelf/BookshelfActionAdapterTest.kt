package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfActionAdapterTest {

    private val sampleBook = BookshelfActionAdapter.quickAdd("book1", "测试书", "作者", "来源")

    @Test
    fun `add book and check isInBookshelf`() {
        val adapter = BookshelfActionAdapter()
        adapter.add(sampleBook)
        assertTrue(adapter.isInBookshelf("book1"))
        assertEquals(1, adapter.count())
    }

    @Test
    fun `remove book`() {
        val adapter = BookshelfActionAdapter()
        adapter.add(sampleBook)
        adapter.remove("book1")
        assertFalse(adapter.isInBookshelf("book1"))
    }

    @Test
    fun `toggle adds then removes`() {
        val adapter = BookshelfActionAdapter()
        assertTrue(adapter.toggle(sampleBook))
        assertFalse(adapter.toggle(sampleBook))
        assertFalse(adapter.isInBookshelf("book1"))
    }

    @Test
    fun `blank id ignored on add`() {
        val adapter = BookshelfActionAdapter()
        assertFalse(adapter.add(sampleBook.copy(id = "")))
        assertEquals(0, adapter.count())
    }

    @Test
    fun `list all returns all books`() {
        val adapter = BookshelfActionAdapter()
        adapter.add(sampleBook)
        adapter.add(BookshelfActionAdapter.quickAdd("book2", "书2"))
        assertEquals(2, adapter.listAll().size)
    }

    @Test
    fun `quickAdd builds minimal entry`() {
        val book = BookshelfActionAdapter.quickAdd("id", "标题")
        assertEquals("id", book.id)
        assertEquals("标题", book.title)
        assertEquals(0f, book.progress)
    }
}
