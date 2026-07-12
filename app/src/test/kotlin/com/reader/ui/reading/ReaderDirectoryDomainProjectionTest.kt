package com.reader.ui.reading

import com.reader.api.Book
import com.reader.api.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDirectoryDomainProjectionTest {

    @Test
    fun `directory rows come from native Core chapterToc and preserve reading position`() {
        val chapters = listOf(
            Chapter(title = "Core chapter A", url = "chapter-a", index = 4),
            Chapter(title = "Core chapter B", url = "chapter-b", index = 5),
            Chapter(title = "Core chapter C", url = "chapter-c", index = 6)
        )
        val state = ReadingUiState.Ready(
            book = Book(bookUrl = "book-1", name = "Core Book"),
            chapters = chapters
        )

        assertEquals(
            listOf(
                ReaderDirectoryEntry("Core chapter A", "已读", chapterIndex = 4),
                ReaderDirectoryEntry("Core chapter B", "当前", chapterIndex = 5),
                ReaderDirectoryEntry("Core chapter C", "未读", chapterIndex = 6)
            ),
            readerDirectoryEntries(state, currentChapterIndex = 5)
        )
    }

    @Test
    fun `loading error and empty Core toc never fabricate fixture chapters`() {
        assertTrue(readerDirectoryEntries(ReadingUiState.Loading, 0).isEmpty())
        assertTrue(readerDirectoryEntries(ReadingUiState.Error("Core unavailable"), 0).isEmpty())
        assertTrue(
            readerDirectoryEntries(
                ReadingUiState.Ready(Book(bookUrl = "book-1", name = "Core Book"), emptyList()),
                0
            ).isEmpty()
        )
    }
}
