package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLocalStateJoinIntegrationTest {

    // ── Provider ──

    @Test
    fun `empty provider has safe defaults`() {
        val provider = ReaderLocalStateProvider.Empty
        val joined = provider.joinToc(listOf(
            ReaderTocEntryUiModel("Ch1", url = "url1")
        ))
        assertFalse(joined[0].isCurrent)
        assertFalse(joined[0].hasBookmark)
    }

    @Test
    fun `provider combines progress and bookmark`() {
        val provider = ReaderLocalStateProvider(
            progress = ReaderProgressLocalStateAdapter(currentChapterUrl = "ch2"),
            bookmark = ReaderBookmarkLocalStateAdapter(setOf("ch1"))
        )
        val entries = listOf(
            ReaderTocEntryUiModel("Ch1", url = "ch1"),
            ReaderTocEntryUiModel("Ch2", url = "ch2"),
            ReaderTocEntryUiModel("Ch3", url = "ch3")
        )
        val joined = provider.joinToc(entries)
        assertTrue(joined[0].hasBookmark)  // bookmark
        assertFalse(joined[0].isCurrent)
        assertTrue(joined[1].isCurrent)    // current
        assertFalse(joined[1].hasBookmark)
        assertFalse(joined[2].isCurrent)
        assertFalse(joined[2].hasBookmark)
    }

    @Test
    fun `provider content joiner delegates to cache`() {
        val provider = ReaderLocalStateProvider(
            cache = ReaderCacheLocalStateAdapter(setOf("url1"))
        )
        val joiner = provider.contentJoiner()
        assertTrue(joiner.isOfflineAvailable("url1"))
        assertFalse(joiner.isOfflineAvailable("url2"))
    }

    @Test
    fun `provider toc joiner preconfigured`() {
        val provider = ReaderLocalStateProvider(
            progress = ReaderProgressLocalStateAdapter(currentChapterUrl = "url", scrollPosition = 0.5f)
        )
        val joined = provider.joinToc(listOf(ReaderTocEntryUiModel("Ch", url = "url")))
        assertTrue(joined[0].isCurrent)
        assertEquals(0.5f, joined[0].progress)
    }

    // ── Adapter Shell uses provider ──

    @Test
    fun `directory adapter shell uses local state provider`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/reader/ReaderDirectoryAdapterShell.kt"
        ).readText()
        assertTrue("Must use ReaderLocalStateProvider",
            "ReaderLocalStateProvider" in source)
        assertTrue("Must call joinToc", "joinToc" in source)
    }

    // ── Regression ──

    @Test
    fun `app main tab unchanged`() {
        val nav = java.io.File(
            "src/main/kotlin/com/reader/android/ui/AppNavigation.kt"
        ).readText()
        assertTrue("Must have 书架", "书架" in nav)
        assertTrue("Must have 发现", "发现" in nav)
        assertTrue("Must have 书源", "书源" in nav)
        assertTrue("Must have 我的", "我的" in nav)
    }
}
