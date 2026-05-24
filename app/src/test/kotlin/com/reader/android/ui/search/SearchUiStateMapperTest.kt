package com.reader.android.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiStateMapperTest {

    @Test
    fun `fromFixture produces result state`() {
        val state = SearchUiStateMapper.fromFixture("群山")
        assertEquals("群山", state.query)
        assertEquals(2, state.results.size)
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `loading produces loading state`() {
        val state = SearchUiStateMapper.loading("test")
        assertTrue(state.isLoading)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `empty produces empty state`() {
        val state = SearchUiStateMapper.empty("unknown")
        assertTrue(state.isEmpty)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `error produces error state`() {
        val state = SearchUiStateMapper.error("test", "请求失败")
        assertNotNull(state.errorMessage)
        assertEquals("请求失败", state.errorMessage)
    }

    @Test
    fun `search result fields match fixture`() {
        val result = SearchFixture.results.first()
        assertEquals("fixture-search-paper-mountain", result.id)
        assertEquals("纸上群山", result.title)
        assertEquals("南溪", result.author)
        assertEquals("第十二章 星河", result.latestChapter)
    }
}
