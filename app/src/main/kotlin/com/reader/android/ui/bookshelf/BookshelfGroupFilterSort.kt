package com.reader.android.ui.bookshelf

/**
 * Group, filter, and sort operations for bookshelf.
 * Pure functions, no side effects, no network.
 */
object BookshelfGroupFilterSort {

    enum class Group(val label: String) {
        ALL("全部"),
        READING("正在阅读"),
        UNREAD("未读"),
        LOCAL("本地书籍"),
        NETWORK("网络书源")
    }

    enum class SortBy(val label: String) {
        RECENT("最近阅读"),
        TITLE("书名"),
        AUTHOR("作者"),
        PROGRESS("阅读进度")
    }

    fun groups(): List<String> = Group.entries.map { it.label }

    fun filter(books: List<BookshelfBookUiModel>, group: String): List<BookshelfBookUiModel> =
        when (group) {
            Group.READING.label -> books.filter { it.progress > 0f }
            Group.UNREAD.label -> books.filter { it.progress == 0f }
            Group.LOCAL.label -> books.filter { it.sourceName == "本地书籍" }
            Group.NETWORK.label -> books.filter { it.sourceName != "本地书籍" }
            else -> books
        }

    fun sort(books: List<BookshelfBookUiModel>, sortBy: SortBy): List<BookshelfBookUiModel> =
        when (sortBy) {
            SortBy.RECENT -> books.sortedByDescending { it.progress } // proxy — TODO: lastReadTime
            SortBy.TITLE -> books.sortedBy { it.title }
            SortBy.AUTHOR -> books.sortedBy { it.author ?: "" }
            SortBy.PROGRESS -> books.sortedByDescending { it.progress }
        }

    fun apply(books: List<BookshelfBookUiModel>, group: String, sortBy: SortBy): List<BookshelfBookUiModel> =
        sort(filter(books, group), sortBy)
}
