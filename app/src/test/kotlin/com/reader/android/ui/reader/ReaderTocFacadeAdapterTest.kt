package com.reader.android.ui.reader

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTocFacadeAdapterTest {

    @After
    fun tearDown() {
        ReaderDirectoryAdapterShell.resetToFakeMode()
    }

    // ── Fake mode ──

    @Test
    fun `fake mode returns fixture TOC entries`() {
        val state = ReaderDirectoryAdapterShell.tocFake()
        assertEquals(7, state.entries.size)
        assertEquals("目录", state.activeTab)
        assertTrue(state.entries.any { it.isCurrent })
        assertTrue(state.entries.any { it.hasBookmark })
    }

    @Test
    fun `fake mode uses fixture volume info`() {
        val state = ReaderDirectoryAdapterShell.tocFake()
        assertTrue(state.volumeInfo.isNotBlank())
    }

    // ── Real mode ──

    @Test
    fun `real mode calls core bridge getTOC`() = runBlocking {
        ReaderDirectoryAdapterShell.enableRealMode()

        val state = ReaderDirectoryAdapterShell.tocReal("https://example.com/toc")
        assertTrue(state.entries.isNotEmpty())
        assertTrue(state.entries.size >= 3) // FakeCoreBridge returns at least 3
    }

    @Test
    fun `real mode blank url returns empty state`() = runBlocking {
        ReaderDirectoryAdapterShell.enableRealMode()

        val state = ReaderDirectoryAdapterShell.tocReal("")
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `fake mode ignores real call`() = runBlocking {
        val state = ReaderDirectoryAdapterShell.tocReal("https://example.com/toc")
        assertEquals(7, state.entries.size) // fixture, not bridge
    }

    @Test
    fun `resetToFakeMode restores default`() {
        ReaderDirectoryAdapterShell.enableRealMode()
        assertFalse(ReaderDirectoryAdapterShell.isFakeMode)
        ReaderDirectoryAdapterShell.resetToFakeMode()
        assertTrue(ReaderDirectoryAdapterShell.isFakeMode)
    }

    // ── Boundary ──

    @Test
    fun `adapter shell does not import parser internals`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/reader/ReaderDirectoryAdapterShell.kt"
        ).readText()
        assertFalse("Must not import TOCParser", "TOCParser" in source)
        assertFalse("Must not import HttpClient", "HttpClient" in source)
    }

    @Test
    fun `toc mapper does not import parser internals`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/reader/CoreTocToReaderTocMapper.kt"
        ).readText()
        assertFalse("Must not import TOCParser", "TOCParser" in source)
    }
}
