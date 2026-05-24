package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressCacheLocalStateAdapterTest {

    // ── Progress adapter ──

    @Test
    fun `progress adapter marks matching chapter as current`() {
        val progress = ReaderProgressLocalStateAdapter(
            currentChapterUrl = "https://example.com/ch2"
        )
        assertTrue(progress.isCurrentChapter("https://example.com/ch2"))
        assertFalse(progress.isCurrentChapter("https://example.com/ch1"))
    }

    @Test
    fun `progress adapter returns progress for current chapter`() {
        val progress = ReaderProgressLocalStateAdapter(
            currentChapterUrl = "https://example.com/ch2",
            scrollPosition = 0.35f
        )
        assertEquals(0.35f, progress.progressFor("https://example.com/ch2"))
        assertEquals(0f, progress.progressFor("https://example.com/ch1"))
    }

    @Test
    fun `empty progress adapter never matches`() {
        val empty = ReaderProgressLocalStateAdapter.Empty
        assertFalse(empty.isCurrentChapter("any"))
        assertEquals(0f, empty.progressFor("any"))
    }

    @Test
    fun `progress adapter handles blank url`() {
        val progress = ReaderProgressLocalStateAdapter(currentChapterUrl = "url")
        assertFalse(progress.isCurrentChapter(""))
    }

    // ── Cache adapter ──

    @Test
    fun `cache adapter returns cached for known url`() {
        val cache = ReaderCacheLocalStateAdapter(cachedUrls = setOf("https://example.com/ch1"))
        assertEquals(ReaderCacheLocalStateAdapter.CacheStatus.CACHED, cache.statusFor("https://example.com/ch1"))
    }

    @Test
    fun `cache adapter returns not cached for unknown url`() {
        val cache = ReaderCacheLocalStateAdapter(cachedUrls = setOf("https://example.com/ch1"))
        assertEquals(ReaderCacheLocalStateAdapter.CacheStatus.NOT_CACHED, cache.statusFor("https://example.com/ch2"))
    }

    @Test
    fun `cache adapter returns not cached for blank url`() {
        val cache = ReaderCacheLocalStateAdapter(cachedUrls = setOf("https://example.com/ch1"))
        assertEquals(ReaderCacheLocalStateAdapter.CacheStatus.NOT_CACHED, cache.statusFor(""))
    }

    @Test
    fun `cache adapter offline available only when cached`() {
        val cache = ReaderCacheLocalStateAdapter(cachedUrls = setOf("url"))
        assertTrue(cache.isOfflineAvailable("url"))
        assertFalse(cache.isOfflineAvailable("other"))
    }

    // ── TOC joiner with real matching ──

    @Test
    fun `toc joiner marks current chapter from progress`() {
        val progress = ReaderProgressLocalStateAdapter(
            currentChapterUrl = "https://example.com/ch2"
        )
        val joiner = ReaderTocLocalStateJoiner(progress = progress)
        val entries = listOf(
            ReaderTocEntryUiModel("Ch1", url = "https://example.com/ch1"),
            ReaderTocEntryUiModel("Ch2", url = "https://example.com/ch2"),
            ReaderTocEntryUiModel("Ch3", url = "")
        )
        val joined = joiner.join(entries)
        assertFalse(joined[0].isCurrent)
        assertTrue(joined[1].isCurrent)
        assertFalse(joined[2].isCurrent) // blank url never matches
    }

    @Test
    fun `toc joiner marks bookmark urls`() {
        val joiner = ReaderTocLocalStateJoiner(bookmarkedUrls = setOf("https://example.com/ch1"))
        val entries = listOf(
            ReaderTocEntryUiModel("Ch1", url = "https://example.com/ch1"),
            ReaderTocEntryUiModel("Ch2", url = "https://example.com/ch2")
        )
        val joined = joiner.join(entries)
        assertTrue(joined[0].hasBookmark)
        assertFalse(joined[1].hasBookmark)
    }

    @Test
    fun `toc joiner fills progress from adapter`() {
        val progress = ReaderProgressLocalStateAdapter(
            currentChapterUrl = "https://example.com/ch2",
            scrollPosition = 0.5f
        )
        val joiner = ReaderTocLocalStateJoiner(progress = progress)
        val entries = listOf(
            ReaderTocEntryUiModel("Ch1", url = "https://example.com/ch1"),
            ReaderTocEntryUiModel("Ch2", url = "https://example.com/ch2")
        )
        val joined = joiner.join(entries)
        assertEquals(0f, joined[0].progress)
        assertEquals(0.5f, joined[1].progress)
    }

    @Test
    fun `toc joiner handles empty entries gracefully`() {
        val joiner = ReaderTocLocalStateJoiner()
        val joined = joiner.join(emptyList())
        assertTrue(joined.isEmpty())
    }

    // ── Content joiner ──

    @Test
    fun `content joiner returns cache status`() {
        val cache = ReaderCacheLocalStateAdapter(cachedUrls = setOf("url"))
        val joiner = ReaderContentLocalStateJoiner(cache = cache)
        assertEquals(ReaderCacheLocalStateAdapter.CacheStatus.CACHED, joiner.cacheStatus("url"))
        assertTrue(joiner.isOfflineAvailable("url"))
    }
}
