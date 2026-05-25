package com.reader.android.ui.booksource

import com.reader.android.data.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceLocalAdapterTest {

    private val s1 = BookSource("url1", "源1", enabled = true)
    private val s2 = BookSource("url2", "源2", enabled = false)

    @Test
    fun `add and list sources`() {
        val adapter = BookSourceLocalAdapter()
        adapter.add(s1)
        adapter.add(s2)
        assertEquals(2, adapter.count())
    }

    @Test
    fun `list enabled filters correctly`() {
        val adapter = BookSourceLocalAdapter()
        adapter.add(s1)
        adapter.add(s2)
        assertEquals(1, adapter.listEnabled().size)
        assertEquals(1, adapter.enabledCount())
    }

    @Test
    fun `toggle switches enabled state`() {
        val adapter = BookSourceLocalAdapter()
        adapter.add(s1)
        assertTrue(adapter.toggle("url1"))
        assertFalse(adapter.getByUrl("url1")!!.enabled) // was true → false
        assertTrue(adapter.toggle("url1"))
        assertTrue(adapter.getByUrl("url1")!!.enabled) // false → true
    }

    @Test
    fun `remove by url`() {
        val adapter = BookSourceLocalAdapter()
        adapter.add(s1)
        adapter.remove("url1")
        assertEquals(0, adapter.count())
    }

    @Test
    fun `get by url`() {
        val adapter = BookSourceLocalAdapter()
        adapter.add(s1)
        assertNotNull(adapter.getByUrl("url1"))
        assertNull(adapter.getByUrl("nonexistent"))
    }

    @Test
    fun `blank url ignored on add`() {
        val adapter = BookSourceLocalAdapter()
        assertFalse(adapter.add(BookSource("", "empty")))
    }

    @Test
    fun `adapter shell fake mode has fixtures`() {
        BookSourceAdapterShell.resetToFakeMode()
        val list = BookSourceAdapterShell.sourceList()
        assertTrue(list.isNotEmpty())
        assertTrue(list.any { it.sourceName == "笔趣阁" })
    }

    @Test
    fun `adapter shell toggle works`() {
        BookSourceAdapterShell.resetToFakeMode()
        val first = BookSourceAdapterShell.sourceList().first()
        val initialEnabled = first.enabled
        BookSourceAdapterShell.toggleSource(first.sourceUrl)
        assertEquals(!initialEnabled, BookSourceAdapterShell.sourceList().first().enabled)
    }
}
