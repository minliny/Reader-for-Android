package com.reader.android.ui.search

import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.SearchQuery
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFacadeAdapterTest {

    @After
    fun tearDown() {
        SearchAdapterShell.resetToFakeMode()
    }

    // ── Fake mode ──

    @Test
    fun `fake mode returns fixture results`() {
        val state = SearchAdapterShell.searchResults("群山")
        assertEquals(2, state.results.size)
        assertEquals("纸上群山", state.results[0].title)
        assertFalse(state.allowRealDataIntegration)
    }

    @Test
    fun `fake mode does not call core bridge`() {
        // Verify by checking fake fixture behavior
        val state = SearchAdapterShell.searchResults("test")
        assertTrue(state.results.all { it.sourceName == "UI Fixture" })
    }

    // ── Real mode — calls CoreBridge.search() public facade ──

    @Test
    fun `real mode calls core bridge search and maps results`() = runBlocking {
        SearchAdapterShell.enableRealMode()

        val state = SearchAdapterShell.searchReal("test")
        assertFalse(state.results.isEmpty())
        assertEquals(2, state.results.size) // FakeCoreBridge returns 2
        // Verify mapping from Core DTO fields
        assertTrue(state.results.all { it.title.isNotBlank() })
        assertTrue(state.results.all { it.author.isNotBlank() })
        assertTrue(state.results.all { it.id.startsWith("search-") })
    }

    @Test
    fun `real mode empty keyword returns empty state`() = runBlocking {
        SearchAdapterShell.enableRealMode()

        val state = SearchAdapterShell.searchReal("")
        assertTrue(state.results.isEmpty())
        assertTrue(state.isEmpty)
    }

    @Test
    fun `real mode maps core DTO fields correctly`() = runBlocking {
        SearchAdapterShell.enableRealMode()

        val state = SearchAdapterShell.searchReal("test")
        val first = state.results.first()
        assertEquals("一剑独尊", first.title)
        assertEquals("青鸾峰上", first.author)
        assertEquals("默认书源", first.sourceName)
        assertEquals("玄幻", first.category)
    }

    // ── Boundary guard ──

    @Test
    fun `adapter shell does not import parser internals`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/search/SearchAdapterShell.kt"
        ).readText()
        assertFalse("Must not import SearchParser", "SearchParser" in source)
        assertFalse("Must not import HttpClient", "HttpClient" in source)
        assertFalse("Must not import BookInfoParser", "BookInfoParser" in source)
    }

    @Test
    fun `result mapper does not import parser internals`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/search/SearchFacadeResultMapper.kt"
        ).readText()
        assertFalse("Must not import SearchParser", "SearchParser" in source)
        assertFalse("Must not import HttpClient", "HttpClient" in source)
    }

    @Test
    fun `resetToFakeMode restores default`() {
        SearchAdapterShell.enableRealMode()
        assertFalse(SearchAdapterShell.isFakeMode)

        SearchAdapterShell.resetToFakeMode()
        assertTrue(SearchAdapterShell.isFakeMode)
    }

    @Test
    fun `fake mode integration level stays NEEDS_ADAPTER`() {
        assertEquals("NEEDS_ADAPTER", SearchAdapterShell.integrationLevel)
    }
}
