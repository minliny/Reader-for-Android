package com.reader.android.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAdapterShellTest {

    @Test
    fun `search home returns empty state`() {
        val state = SearchAdapterShell.searchHome()
        assertTrue(state.isEmpty)
        assertFalse(state.isLoading)
    }

    @Test
    fun `search loading returns loading state`() {
        val state = SearchAdapterShell.searchLoading("群山")
        assertTrue(state.isLoading)
        assertEquals("群山", state.query)
    }

    @Test
    fun `search results returns fixture results`() {
        val state = SearchAdapterShell.searchResults("群山")
        assertFalse(state.results.isEmpty())
        assertEquals(2, state.results.size)
        assertEquals("纸上群山", state.results[0].title)
    }

    @Test
    fun `search results include required fields`() {
        val state = SearchAdapterShell.searchResults("test")
        val result = state.results.first()
        assertNotNull(result.id)
        assertNotNull(result.title)
        assertNotNull(result.author)
        assertNotNull(result.sourceName)
        assertNotNull(result.latestChapter)
        assertNotNull(result.intro)
        assertNotNull(result.detailTarget)
    }

    @Test
    fun `search empty returns empty state`() {
        val state = SearchAdapterShell.searchEmpty("unknown")
        assertTrue(state.isEmpty)
        assertEquals("unknown", state.query)
    }

    @Test
    fun `search error returns error state`() {
        val state = SearchAdapterShell.searchError("test", "网络错误")
        assertNotNull(state.errorMessage)
        assertEquals("网络错误", state.errorMessage)
    }

    @Test
    fun `search adapter shell is in fake mode by default`() {
        assertTrue(SearchAdapterShell.isFakeMode)
    }

    @Test
    fun `search result model has bookshelf and action fields`() {
        val result = SearchFixture.results.first()
        // Fields exist with defaults or values
        assertNotNull(result.actionLabel)
        // isInBookshelf default is false
        assertFalse(result.isInBookshelf)
    }

    @Test
    fun `search adapter shell does not access network`() {
        // Verify the shell only produces fixture-based states
        val home = SearchAdapterShell.searchHome()
        assertFalse(home.allowRealDataIntegration)

        val results = SearchAdapterShell.searchResults("test")
        assertFalse(results.allowRealDataIntegration)
    }
}
