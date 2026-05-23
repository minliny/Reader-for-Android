package com.reader.android.ui.bookshelf

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class BookshelfDataIntegrationTest {

    private val bookshelfStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfUiState.kt")))
    }

    private val bookshelfScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfScreen.kt")))
    }

    @Test
    fun `bookshelf state covers required rendering modes and contracts`() {
        listOf(
            "BookshelfUiState",
            "BookshelfBookUiModel",
            "BookshelfLayoutMode",
            "Cover",
            "List",
            "BookshelfCacheState",
            "onBookClickContract",
            "onBookMoreClickContract"
        ).forEach { token ->
            assertTrue("Bookshelf state must contain $token", token in bookshelfStateSource)
        }
    }

    @Test
    fun `bookshelf screen renders cover list and empty states`() {
        listOf(
            "BookshelfCoverMode",
            "BookshelfListMode",
            "ReaderEmptyState",
            "BookCard",
            "BookListItem",
            "切换书架布局",
            "更多"
        ).forEach { token ->
            assertTrue("BookshelfScreen must render $token", token in bookshelfScreenSource)
        }
    }

    @Test
    fun `bookshelf integration does not call forbidden runtime layers`() {
        val combined = bookshelfStateSource + "\n" + bookshelfScreenSource
        listOf(
            "Fake" + "CoreBridge",
            "Reader" + "CoreBridge",
            "Search" + "Parser",
            "BookInfo" + "Parser",
            "Content" + "Parser",
            "Http" + "Client",
            "Web" + "View",
            "normalized-" + "html",
            "bg-" + "surface-container",
            "text-" + "on-surface",
            "shadow-" + "lg",
            "#" + "fdf6ec"
        ).forEach { forbidden ->
            assertTrue("Bookshelf integration must not contain $forbidden", forbidden !in combined)
        }
    }

    @Test
    fun `bookshelf mapper is pure and does not access repository or database`() {
        listOf(
            "Repository",
            "Dao.",
            ".getAll(",
            ".upsert(",
            ".insert(",
            "Room.databaseBuilder",
            "DataStore"
        ).forEach { forbidden ->
            assertTrue("Bookshelf mapper must not access $forbidden", forbidden !in bookshelfStateSource)
        }
    }
}
