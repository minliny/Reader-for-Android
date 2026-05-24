package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookmarkActionFlowTest {

    @Test
    fun `add bookmark and check isBookmarked`() {
        val adapter = ReaderBookmarkActionAdapter()
        adapter.add("book1", "测试书", "ch1", "第一章", "snippet...")
        assertTrue(adapter.isBookmarked("ch1"))
        assertEquals(1, adapter.count())
    }

    @Test
    fun `remove bookmark`() {
        val adapter = ReaderBookmarkActionAdapter()
        adapter.add("book1", "书", "ch1", "章", "")
        adapter.remove("ch1")
        assertFalse(adapter.isBookmarked("ch1"))
        assertEquals(0, adapter.count())
    }

    @Test
    fun `toggle adds then removes`() {
        val adapter = ReaderBookmarkActionAdapter()
        assertTrue(adapter.toggle("b", "书", "ch1", "章", ""))
        assertTrue(adapter.isBookmarked("ch1"))
        assertFalse(adapter.toggle("b", "书", "ch1", "章", ""))
        assertFalse(adapter.isBookmarked("ch1"))
    }

    @Test
    fun `list bookmarks for book`() {
        val adapter = ReaderBookmarkActionAdapter()
        adapter.add("b1", "书1", "ch1", "章1", "")
        adapter.add("b1", "书1", "ch2", "章2", "")
        adapter.add("b2", "书2", "ch3", "章3", "")
        assertEquals(2, adapter.listForBook("b1").size)
        assertEquals(1, adapter.listForBook("b2").size)
    }

    @Test
    fun `adapter reflects bookmark state`() {
        val adapter = ReaderBookmarkActionAdapter()
        adapter.add("b", "书", "ch1", "章", "")
        val state = adapter.adapter
        assertTrue(state.isBookmarked("ch1"))
        assertFalse(state.isBookmarked("ch2"))
    }

    @Test
    fun `snippet generated from content`() {
        val snippet = ReaderBookmarkActionAdapter.snippetFromContent(
            "通讯台的指示灯已经闪烁了三个标准时。在这片被称为寂静航线的星域里"
        )
        assertTrue(snippet.length <= 80)
        assertTrue(snippet.startsWith("通讯台"))
    }

    @Test
    fun `short content returns full text`() {
        val snippet = ReaderBookmarkActionAdapter.snippetFromContent("短文本")
        assertEquals("短文本", snippet)
    }

    @Test
    fun `blank url ignored`() {
        val adapter = ReaderBookmarkActionAdapter()
        assertFalse(adapter.add("b", "书", "", "章", ""))
        assertFalse(adapter.remove(""))
        assertFalse(adapter.isBookmarked(""))
    }
}
