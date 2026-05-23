package com.reader.android.ui.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPrototypeGalleryTest {

    @Test
    fun `prototype catalog exists and covers required entry count`() {
        assertTrue(ReaderPrototypeCatalog.entries.size >= 38)
        assertEquals(38, ReaderPrototypeCatalog.entries.size)
    }

    @Test
    fun `prototype catalog contains all required groups`() {
        val groups = ReaderPrototypeCatalog.entries.map { it.group }.toSet()

        ReaderPrototypeGroup.entries.forEach { group ->
            assertTrue("Prototype group must exist: ${group.title}", group in groups)
        }
    }

    @Test
    fun `prototype catalog contains required reader entries`() {
        listOf(
            "reader-base",
            "reader-search",
            "reader-auto-scroll",
            "reader-replace",
            "reader-night",
            "reader-directory",
            "reader-tts",
            "reader-appearance",
            "reader-settings"
        ).forEach { id ->
            assertTrue("Prototype entry must exist: $id", ReaderPrototypeCatalog.entries.any { it.id == id })
        }
    }

    @Test
    fun `prototype fixtures expose all state adapter families`() {
        assertTrue(ReaderPrototypeFixtures.bookshelfCover.books.isNotEmpty())
        assertTrue(ReaderPrototypeFixtures.searchResults.results.isNotEmpty())
        assertTrue(ReaderPrototypeFixtures.bookDetail.hasContent)
        assertEquals(9, ReaderPrototypeFixtures.readerStates.size)
        assertTrue(ReaderPrototypeFixtures.discover.items.isNotEmpty())
        assertTrue(ReaderPrototypeFixtures.rssList.feeds.isNotEmpty())
        assertTrue(ReaderPrototypeFixtures.remoteBooks.books.isNotEmpty())
    }
}
