package com.reader.android.ui.reader

/**
 * In-memory bookmark add/delete/list actions.
 * TODO: connect to Room BookmarkDao when DI/runtime is available.
 */
class ReaderBookmarkActionAdapter(
    private val bookmarks: MutableMap<String, BookmarkEntry> = mutableMapOf()
) {
    data class BookmarkEntry(
        val bookUrl: String,
        val bookName: String,
        val chapterUrl: String,
        val chapterTitle: String,
        val snippet: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )

    val adapter: ReaderBookmarkLocalStateAdapter
        get() = ReaderBookmarkLocalStateAdapter(bookmarkedChapterUrls = bookmarks.keys)

    /** Add a bookmark for a chapter. */
    fun add(
        bookUrl: String,
        bookName: String,
        chapterUrl: String,
        chapterTitle: String,
        snippet: String = ""
    ): Boolean {
        if (chapterUrl.isBlank()) return false
        bookmarks[chapterUrl] = BookmarkEntry(bookUrl, bookName, chapterUrl, chapterTitle, snippet)
        return true
    }

    /** Remove bookmark by chapter URL. */
    fun remove(chapterUrl: String): Boolean {
        if (chapterUrl.isBlank()) return false
        return bookmarks.remove(chapterUrl) != null
    }

    /** Toggle bookmark: add if not present, remove if present. Returns new state. */
    fun toggle(
        bookUrl: String, bookName: String,
        chapterUrl: String, chapterTitle: String, snippet: String = ""
    ): Boolean {
        return if (isBookmarked(chapterUrl)) {
            remove(chapterUrl)
            false
        } else {
            add(bookUrl, bookName, chapterUrl, chapterTitle, snippet)
            true
        }
    }

    fun isBookmarked(chapterUrl: String): Boolean = chapterUrl in bookmarks

    fun listForBook(bookUrl: String): List<BookmarkEntry> =
        bookmarks.values.filter { it.bookUrl == bookUrl }

    fun count(): Int = bookmarks.size

    companion object {
        /** Generate a short snippet from content text (first ~100 chars). */
        fun snippetFromContent(content: String, maxLen: Int = 80): String {
            val cleaned = content.take(200).replace("\n", " ").trim()
            return if (cleaned.length <= maxLen) cleaned else cleaned.take(maxLen) + "…"
        }
    }
}
