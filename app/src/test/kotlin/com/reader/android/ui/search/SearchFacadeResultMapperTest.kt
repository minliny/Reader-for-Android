package com.reader.android.ui.search

import com.reader.android.data.model.SearchResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFacadeResultMapperTest {

    private val sampleItem = SearchResultItem(
        name = "一剑独尊",
        author = "青鸾峰上",
        coverUrl = null,
        detailUrl = "https://example.com/book/1",
        kind = "玄幻",
        wordCount = "320万字",
        latestChapter = "第1024章 决战",
        intro = "剑气纵横三万里",
        sourceName = "笔趣阁"
    )

    @Test
    fun `maps core fields to UI model`() {
        val result = SearchFacadeResultMapper.map(sampleItem)
        assertEquals("一剑独尊", result.title)
        assertEquals("青鸾峰上", result.author)
        assertEquals("笔趣阁", result.sourceName)
        assertEquals("玄幻", result.category)
        assertEquals("第1024章 决战", result.latestChapter)
        assertEquals("剑气纵横三万里", result.intro)
        assertEquals("320万字", result.status)
        assertEquals("https://example.com/book/1", result.detailTarget)
    }

    @Test
    fun `generates stable id from detailUrl`() {
        val a = SearchFacadeResultMapper.map(sampleItem)
        val b = SearchFacadeResultMapper.map(sampleItem)
        assertEquals(a.id, b.id)
        assertTrue(a.id.startsWith("search-"))
    }

    @Test
    fun `different urls produce different ids`() {
        val a = SearchFacadeResultMapper.map(sampleItem)
        val b = SearchFacadeResultMapper.map(sampleItem.copy(detailUrl = "https://example.com/book/2"))
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `null detailUrl produces stable fallback id`() {
        val result = SearchFacadeResultMapper.map(sampleItem.copy(detailUrl = null))
        assertEquals("search-unknown", result.id)
    }

    @Test
    fun `blank author uses fallback`() {
        val result = SearchFacadeResultMapper.map(sampleItem.copy(author = ""))
        assertEquals("未知作者", result.author)
    }

    @Test
    fun `null sourceName uses fallback`() {
        val result = SearchFacadeResultMapper.map(sampleItem.copy(sourceName = null))
        assertEquals("未知书源", result.sourceName)
    }

    @Test
    fun `missing optional fields default to empty string`() {
        val minimal = SearchResultItem(name = "Test", author = "A", detailUrl = "")
        val result = SearchFacadeResultMapper.map(minimal)
        assertEquals("", result.category)
        assertEquals("", result.latestChapter)
        assertEquals("", result.intro)
        assertEquals("", result.cover)
        assertEquals("", result.status)
    }

    @Test
    fun `isInBookshelf defaults to false when no repository`() {
        val result = SearchFacadeResultMapper.map(sampleItem)
        assertFalse(result.isInBookshelf)
    }

    @Test
    fun `maps entire list`() {
        val items = listOf(
            SearchResultItem(name = "A", author = "X", detailUrl = "a"),
            SearchResultItem(name = "B", author = "Y", detailUrl = "b")
        )
        val results = SearchFacadeResultMapper.mapList(items)
        assertEquals(2, results.size)
        assertEquals("A", results[0].title)
        assertEquals("B", results[1].title)
    }
}
