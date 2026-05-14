package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationTest {

    @Test
    fun `PageRef holds url and optional title and index`() {
        val ref = PageRef(url = "http://book.com/ch2", title = "第二章", index = 1)
        assertEquals("http://book.com/ch2", ref.url)
        assertEquals("第二章", ref.title)
        assertEquals(1, ref.index)
    }

    @Test
    fun `PageRef with url only has null title and index`() {
        val ref = PageRef(url = "http://book.com/ch1")
        assertNull(ref.title)
        assertNull(ref.index)
    }

    @Test
    fun `fromCurrentUrl sets current and next page`() {
        val state = PaginationState.fromCurrentUrl("http://ch1", "http://ch2")
        assertEquals("http://ch1", state.currentPage?.url)
        assertEquals("http://ch2", state.nextPage?.url)
        assertTrue(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `fromCurrentUrl with null next sets hasNext false`() {
        val state = PaginationState.fromCurrentUrl("http://last", null)
        assertFalse(state.hasNext)
        assertNull(state.nextPage)
    }

    @Test
    fun `empty state has no pages`() {
        val state = PaginationState.empty()
        assertNull(state.currentPage)
        assertNull(state.nextPage)
        assertFalse(state.hasNext)
    }
}
