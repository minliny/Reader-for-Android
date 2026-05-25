package com.reader.android.ui.booksource

import com.reader.android.data.model.BookSource

/**
 * Fake/real boundary for book source management.
 */
object BookSourceAdapterShell {

    enum class Mode { FAKE, REAL }
    var mode: Mode = Mode.FAKE
        private set

    private val adapter = BookSourceLocalAdapter()

    init {
        // Seed with fixture sources
        adapter.add(BookSource(sourceUrl = "fixture://source1", sourceName = "笔趣阁", sourceGroup = "网络"))
        adapter.add(BookSource(sourceUrl = "fixture://source2", sourceName = "全本网", sourceGroup = "网络"))
    }

    fun sourceList(): List<BookSource> =
        when (mode) {
            Mode.FAKE -> adapter.listAll()
            Mode.REAL -> adapter.listAll() // TODO: DataStoreBookSourceRepository
        }

    fun toggleSource(url: String): Boolean =
        adapter.toggle(url)

    val isFakeMode: Boolean get() = mode == Mode.FAKE
    fun enableRealMode() { mode = Mode.REAL }
    fun resetToFakeMode() { mode = Mode.FAKE }
}
