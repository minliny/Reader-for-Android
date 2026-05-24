package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderBookmarkStorageFoundationTest {

    // ── BookmarkEntity fields ──

    @Test
    fun `bookmark entity has required fields`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/data/storage/BookmarkEntity.kt"
        )))
        listOf("bookmarkId", "bookUrl", "bookName", "chapterUrl",
            "chapterTitle", "snippet", "paragraphIndex", "createdAt", "note"
        ).forEach { field ->
            assertTrue("BookmarkEntity must have $field", field in source)
        }
    }

    @Test
    fun `bookmark dao has required operations`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/data/storage/BookmarkEntity.kt"
        )))
        listOf("getByBook", "getByChapter", "isBookmarked",
            "insert", "deleteById", "deleteByChapter", "getBookmarkedUrls"
        ).forEach { op ->
            assertTrue("BookmarkDao must have $op", "fun $op" in source)
        }
    }

    // ── AppDatabase includes Bookmark ──

    @Test
    fun `app database includes bookmark entity`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/data/storage/ReadingProgress.kt"
        )))
        assertTrue("AppDatabase must include BookmarkEntity",
            "BookmarkEntity" in source)
        assertTrue("AppDatabase must have bookmarkDao",
            "bookmarkDao" in source)
        assertTrue("AppDatabase version must be >= 4",
            "version = 4" in source)
    }

    // ── Adapter ──

    @Test
    fun `bookmark adapter detects bookmarked urls`() {
        val adapter = ReaderBookmarkLocalStateAdapter(
            bookmarkedChapterUrls = setOf("ch1", "ch2")
        )
        assertTrue(adapter.isBookmarked("ch1"))
        assertFalse(adapter.isBookmarked("ch3"))
    }

    @Test
    fun `bookmark adapter ignores blank url`() {
        val adapter = ReaderBookmarkLocalStateAdapter(setOf("ch1"))
        assertFalse(adapter.isBookmarked(""))
    }

    @Test
    fun `empty bookmark adapter returns false for all`() {
        val empty = ReaderBookmarkLocalStateAdapter.Empty
        assertFalse(empty.isBookmarked("any"))
        assertTrue(empty.bookmarkedUrls().isEmpty())
    }

    // ── TOC joiner with bookmark adapter ──

    @Test
    fun `toc joiner integrates bookmark state`() {
        val joiner = ReaderTocLocalStateJoiner(
            bookmarkedUrls = setOf("https://example.com/ch1")
        )
        val entries = listOf(
            ReaderTocEntryUiModel("Ch1", url = "https://example.com/ch1"),
            ReaderTocEntryUiModel("Ch2", url = "https://example.com/ch2")
        )
        val joined = joiner.join(entries)
        assertTrue(joined[0].hasBookmark)
        assertFalse(joined[1].hasBookmark)
    }

    // ── No secrets ──

    @Test
    fun `bookmark entity does not contain secrets`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/data/storage/BookmarkEntity.kt"
        )))
        listOf("token", "password", "secret").forEach { secret ->
            assertFalse("Must not contain $secret", secret in source)
        }
    }
}
