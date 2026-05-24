package com.reader.android.ui.bookshelf

import com.reader.android.ui.reader.ReaderCacheLocalStateAdapter
import com.reader.android.ui.reader.ReaderProgressSaveAdapter

/**
 * Fake/real boundary for Bookshelf state.
 * Mode.FAKE: fixture. Mode.REAL: joined from in-memory adapters.
 */
object BookshelfAdapterShell {

    enum class Mode { FAKE, REAL }
    var mode: Mode = Mode.FAKE
        private set

    private val progressAdapters = mutableMapOf<String, ReaderProgressSaveAdapter>()
    private val cacheAdapter = ReaderCacheLocalStateAdapter.Empty

    fun bookshelfState(layoutMode: BookshelfLayoutMode = BookshelfLayoutMode.Cover): BookshelfUiState =
        when (mode) {
            Mode.FAKE -> BookshelfMapper.fakeFallback(layoutMode)
            Mode.REAL -> BookshelfUiState(
                books = progressAdapters.map { (url, p) ->
                    BookshelfLocalStateJoiner.fromProgress(
                        progress = p, cache = cacheAdapter,
                        bookUrl = url, bookName = ""
                    )
                },
                layoutMode = layoutMode,
                availableGroups = listOf("全部")
            )
        }

    fun registerProgress(bookUrl: String, adapter: ReaderProgressSaveAdapter) {
        progressAdapters[bookUrl] = adapter
    }

    val isFakeMode: Boolean get() = mode == Mode.FAKE
    fun enableRealMode() { mode = Mode.REAL }
    fun resetToFakeMode() { mode = Mode.FAKE }
}
