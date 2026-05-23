package com.reader.android.ui.bookshelf

import com.reader.android.data.storage.ReadingProgress
import com.reader.android.ui.fixtures.ReaderUiFixtures

enum class BookshelfLayoutMode {
    Cover,
    List
}

enum class BookshelfCacheState(val label: String) {
    Cached("已缓存"),
    Partial("部分缓存"),
    None("未缓存")
}

data class BookshelfBookUiModel(
    val id: String,
    val title: String,
    val author: String?,
    val sourceName: String,
    val currentChapterTitle: String,
    val progress: Float,
    val cacheState: BookshelfCacheState,
    val detailTarget: String,
    val readerTarget: String
)

data class BookshelfUiState(
    val books: List<BookshelfBookUiModel>,
    val layoutMode: BookshelfLayoutMode = BookshelfLayoutMode.Cover,
    val isLoading: Boolean = false,
    val emptyMessage: String = "点击右下角按钮搜索书籍",
    val errorMessage: String? = null,
    val selectedGroup: String = "全部",
    val availableGroups: List<String> = listOf("全部"),
    val onBookClickContract: String = "onBookClick(book) -> detail or reader",
    val onBookMoreClickContract: String = "onBookMoreClick(book) -> more menu"
) {
    val isEmpty: Boolean get() = books.isEmpty() && !isLoading && errorMessage == null
}

object BookshelfMapper {
    fun fromReadingProgress(
        progress: List<ReadingProgress>,
        layoutMode: BookshelfLayoutMode = BookshelfLayoutMode.Cover,
        cachedBookUrls: Set<String> = emptySet(),
        partialCachedBookUrls: Set<String> = emptySet()
    ): BookshelfUiState {
        val books = progress.map { item ->
            BookshelfBookUiModel(
                id = item.bookUrl,
                title = item.bookName,
                author = item.author,
                sourceName = sourceLabel(item.bookUrl),
                currentChapterTitle = item.currentChapterTitle.ifBlank { "未开始阅读" },
                progress = progressPercent(item.chapterIndex, item.totalChapters, item.scrollPosition),
                cacheState = cacheState(item.bookUrl, cachedBookUrls, partialCachedBookUrls),
                detailTarget = item.bookUrl,
                readerTarget = item.currentChapterUrl.ifBlank { item.bookUrl }
            )
        }
        return BookshelfUiState(
            books = books,
            layoutMode = layoutMode,
            emptyMessage = "书架为空",
            availableGroups = availableGroups(books),
            selectedGroup = "全部"
        )
    }

    fun empty(layoutMode: BookshelfLayoutMode = BookshelfLayoutMode.Cover): BookshelfUiState =
        BookshelfUiState(
            books = emptyList(),
            layoutMode = layoutMode,
            emptyMessage = "点击右下角按钮搜索书籍"
        )

    fun fakeFallback(layoutMode: BookshelfLayoutMode = BookshelfLayoutMode.Cover): BookshelfUiState =
        BookshelfUiState(
            books = ReaderUiFixtures.bookshelfBooks.map {
                BookshelfBookUiModel(
                    id = it.id,
                    title = it.title,
                    author = it.author,
                    sourceName = "UI Fixture",
                    currentChapterTitle = "继续阅读",
                    progress = it.progress,
                    cacheState = BookshelfCacheState.Partial,
                    detailTarget = it.id,
                    readerTarget = it.id
                )
            },
            layoutMode = layoutMode,
            availableGroups = listOf("全部", "UI Fixture")
        )

    private fun sourceLabel(bookUrl: String): String =
        when {
            bookUrl.startsWith("content://") -> "本地书籍"
            bookUrl.startsWith("file://") -> "本地书籍"
            bookUrl.startsWith("http://") || bookUrl.startsWith("https://") -> "网络书源"
            else -> "未知来源"
        }

    private fun progressPercent(chapterIndex: Int, totalChapters: Int, scrollPosition: Float): Float {
        if (totalChapters <= 0) return scrollPosition.coerceIn(0f, 1f)
        val chapterBase = chapterIndex.coerceAtLeast(0).toFloat() / totalChapters.toFloat()
        val chapterSize = 1f / totalChapters.toFloat()
        return (chapterBase + scrollPosition.coerceIn(0f, 1f) * chapterSize).coerceIn(0f, 1f)
    }

    private fun cacheState(
        bookUrl: String,
        cachedBookUrls: Set<String>,
        partialCachedBookUrls: Set<String>
    ): BookshelfCacheState =
        when {
            bookUrl in cachedBookUrls -> BookshelfCacheState.Cached
            bookUrl in partialCachedBookUrls -> BookshelfCacheState.Partial
            else -> BookshelfCacheState.None
        }

    private fun availableGroups(books: List<BookshelfBookUiModel>): List<String> =
        (listOf("全部") + books.map { it.sourceName }).distinct()
}

object BookshelfFixture {
    val progressItems: List<ReadingProgress> = listOf(
        ReadingProgress(
            bookUrl = "content://books/deep-space.txt",
            bookName = "深空信号",
            author = "林间",
            currentChapterUrl = "content://books/deep-space.txt#ch3",
            currentChapterTitle = "第三章 回声",
            chapterIndex = 2,
            totalChapters = 8,
            scrollPosition = 0.4f
        ),
        ReadingProgress(
            bookUrl = "content://books/paper-mountain.txt",
            bookName = "纸上群山",
            author = "南溪",
            currentChapterUrl = "content://books/paper-mountain.txt#ch1",
            currentChapterTitle = "第一章 雨线",
            chapterIndex = 0,
            totalChapters = 5,
            scrollPosition = 0.8f
        )
    )
}
