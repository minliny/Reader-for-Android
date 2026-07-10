package com.reader.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfChromeStateTest {
    @Test
    fun `bookshelf chrome defaults to cover view`() {
        val state = BookshelfChromeState()

        assertEquals(BookshelfViewMode.COVER, state.viewMode)
        assertEquals(false, state.filter.isActive)
    }

    @Test
    fun `bookshelf chrome can represent list view`() {
        val state = BookshelfChromeState(viewMode = BookshelfViewMode.LIST)

        assertEquals(BookshelfViewMode.LIST, state.viewMode)
    }

    @Test
    fun `bookshelf filter is active when open or changed from demo defaults`() {
        assertEquals(true, BookshelfFilterState(isOpen = true).isActive)
        assertEquals(true, BookshelfFilterState(group = "追更").isActive)
        assertEquals(true, BookshelfFilterState(sort = "书名").isActive)
        assertEquals(true, BookshelfFilterState(filter = "未读").isActive)
    }
}
