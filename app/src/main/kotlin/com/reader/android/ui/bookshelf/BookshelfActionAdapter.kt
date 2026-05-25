package com.reader.android.ui.bookshelf

/**
 * Local bookshelf add/remove with in-memory state.
 * TODO: connect to Room or local repository when available.
 */
class BookshelfActionAdapter(
    private val bookshelf: MutableMap<String, BookshelfBookUiModel> = mutableMapOf()
) {
    fun isInBookshelf(bookId: String): Boolean = bookId in bookshelf

    fun add(book: BookshelfBookUiModel): Boolean {
        if (book.id.isBlank()) return false
        bookshelf[book.id] = book
        return true
    }

    fun remove(bookId: String): Boolean = bookshelf.remove(bookId) != null

    fun toggle(book: BookshelfBookUiModel): Boolean =
        if (isInBookshelf(book.id)) { remove(book.id); false } else { add(book); true }

    fun listAll(): List<BookshelfBookUiModel> = bookshelf.values.toList()

    fun count(): Int = bookshelf.size

    companion object {
        /** Quick add from minimal info. */
        fun quickAdd(
            id: String, title: String, author: String? = null, sourceName: String = ""
        ): BookshelfBookUiModel = BookshelfBookUiModel(
            id = id, title = title, author = author, sourceName = sourceName,
            currentChapterTitle = "", progress = 0f,
            cacheState = BookshelfCacheState.None,
            detailTarget = id, readerTarget = id
        )
    }
}
