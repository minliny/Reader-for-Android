package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreSourceTest {

    @Test
    fun `ExploreSource holds all fields`() {
        val src = ExploreSource("1", "笔趣阁", "中文", "http://feed.com/rss", sortOrder = 2)
        assertEquals("1", src.id)
        assertEquals("中文", src.category)
        assertEquals(2, src.sortOrder)
    }

    @Test
    fun `groupByCategory groups sources by category`() {
        val sources = listOf(
            ExploreSource("1", "A", "中文", "url1", sortOrder = 1),
            ExploreSource("2", "B", "中文", "url2", sortOrder = 0),
            ExploreSource("3", "C", "英文", "url3")
        )
        val categories = ExploreMapping.groupByCategory(sources)
        assertEquals(2, categories.size)
        val chinese = categories.find { it.name == "中文" }
        assertEquals(2, chinese?.sources?.size)
        assertEquals("B", chinese?.sources?.first()?.name) // sorted by sortOrder
    }

    @Test
    fun `empty sources produces empty categories`() {
        assertTrue(ExploreMapping.groupByCategory(emptyList()).isEmpty())
    }
}
