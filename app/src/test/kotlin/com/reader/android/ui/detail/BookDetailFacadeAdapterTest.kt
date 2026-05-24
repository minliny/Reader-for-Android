package com.reader.android.ui.detail

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailFacadeAdapterTest {

    @After
    fun tearDown() {
        BookDetailAdapterShell.resetToFakeMode()
    }

    // ── Fake mode ──

    @Test
    fun `fake mode returns fixture detail`() {
        val state = BookDetailAdapterShell.detailReady()
        assertTrue(state.hasContent)
        assertEquals("纸上群山", state.detail!!.title)
    }

    @Test
    fun `fake mode does not call core bridge`() {
        val state = BookDetailAdapterShell.detailReady()
        assertEquals("UI Fixture", state.detail!!.sourceName)
    }

    // ── Real mode ──

    @Test
    fun `real mode calls core bridge getBookInfo and getTOC`() = runBlocking {
        BookDetailAdapterShell.enableRealMode()

        val state = BookDetailAdapterShell.detailReal("https://example.com/book/1")
        assertTrue(state.hasContent)
        val detail = state.detail!!
        assertEquals("一剑独尊", detail.title)
        assertEquals("青鸾峰上", detail.author)
        assertEquals("默认书源", detail.sourceName)
    }

    @Test
    fun `real mode includes TOC preview from getTOC`() = runBlocking {
        BookDetailAdapterShell.enableRealMode()

        val state = BookDetailAdapterShell.detailReal("https://example.com/book/1")
        val toc = state.detail!!.tocPreview
        assertTrue(toc.chapterCount > 0)
    }

    @Test
    fun `real mode blank url returns error`() = runBlocking {
        BookDetailAdapterShell.enableRealMode()

        val state = BookDetailAdapterShell.detailReal("")
        assertFalse(state.hasContent)
        assertTrue(state.errorMessage!!.isNotBlank())
    }

    @Test
    fun `fake mode ignores real call and returns fixture`() = runBlocking {
        // Still in fake mode
        val state = BookDetailAdapterShell.detailReal("https://example.com/book/1")
        assertTrue(state.hasContent)
        assertEquals("UI Fixture", state.detail!!.sourceName) // fixture, not bridge
    }

    // ── Boundary guard ──

    @Test
    fun `adapter shell does not import parser internals`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/detail/BookDetailAdapterShell.kt"
        ).readText()
        assertFalse("Must not import BookInfoParser", "BookInfoParser" in source)
        assertFalse("Must not import HttpClient", "HttpClient" in source)
    }

    @Test
    fun `result mapper does not import parser internals`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/detail/BookDetailFacadeResultMapper.kt"
        ).readText()
        assertFalse("Must not import BookInfoParser", "BookInfoParser" in source)
        assertFalse("Must not import HttpClient", "HttpClient" in source)
    }

    @Test
    fun `resetToFakeMode restores default`() {
        BookDetailAdapterShell.enableRealMode()
        assertFalse(BookDetailAdapterShell.isFakeMode)
        BookDetailAdapterShell.resetToFakeMode()
        assertTrue(BookDetailAdapterShell.isFakeMode)
    }
}
