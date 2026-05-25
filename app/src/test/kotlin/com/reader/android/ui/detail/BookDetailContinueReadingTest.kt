package com.reader.android.ui.detail

import com.reader.android.ui.reader.ReaderProgressLocalStateAdapter
import com.reader.android.ui.reader.ReaderCacheLocalStateAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailContinueReadingTest {

    private val fixture = BookDetailFixture.detail

    @Test
    fun `enrich updates current chapter from progress`() {
        val progress = ReaderProgressLocalStateAdapter(currentChapterUrl = "ch3", currentChapterTitle = "第三章")
        val result = BookDetailLocalStateJoiner.enrich(fixture, progress = progress)
        assertEquals("第三章", result.currentChapter)
    }

    @Test
    fun `enrich updates reading progress from adapter`() {
        val progress = ReaderProgressLocalStateAdapter(currentChapterUrl = "ch1", scrollPosition = 0.5f)
        val result = BookDetailLocalStateJoiner.enrich(fixture, progress = progress)
        assertEquals(0.5f, result.readingProgress)
    }

    @Test
    fun `enrich with empty progress keeps fixture defaults`() {
        val result = BookDetailLocalStateJoiner.enrich(fixture)
        assertEquals(fixture.currentChapter, result.currentChapter)
    }

    @Test
    fun `hasProgress shows continue reading action`() {
        val progress = ReaderProgressLocalStateAdapter(currentChapterUrl = "ch1", currentChapterTitle = "章")
        val result = BookDetailLocalStateJoiner.enrich(fixture, progress = progress)
        assertTrue(result.availableActions.contains("继续阅读"))
    }

    @Test
    fun `no progress shows start reading action`() {
        val result = BookDetailLocalStateJoiner.enrich(fixture)
        assertTrue(result.availableActions.contains("开始阅读"))
    }

    @Test
    fun `cache hit shows cached status`() {
        val cache = ReaderCacheLocalStateAdapter(cachedUrls = setOf("https://example.com/ch1"))
        val result = BookDetailLocalStateJoiner.enrich(
            fixture, cache = cache, chapterUrl = "https://example.com/ch1"
        )
        assertEquals("已缓存", result.cacheStatus)
    }
}
