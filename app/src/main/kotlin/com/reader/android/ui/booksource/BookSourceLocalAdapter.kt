package com.reader.android.ui.booksource

import com.reader.android.data.model.BookSource

/**
 * Local book source state adapter. In-memory fallback.
 * TODO: connect to DataStoreBookSourceRepository when DI/runtime available.
 */
class BookSourceLocalAdapter(
    private val sources: MutableList<BookSource> = mutableListOf()
) {
    fun listAll(): List<BookSource> = sources.toList()

    fun listEnabled(): List<BookSource> = sources.filter { it.enabled }

    fun enabledCount(): Int = sources.count { it.enabled }

    fun add(source: BookSource): Boolean {
        if (source.sourceUrl.isBlank()) return false
        sources.add(source)
        return true
    }

    fun remove(sourceUrl: String): Boolean =
        sources.removeAll { it.sourceUrl == sourceUrl }

    fun toggle(sourceUrl: String): Boolean {
        val idx = sources.indexOfFirst { it.sourceUrl == sourceUrl }
        if (idx < 0) return false
        val s = sources[idx]
        sources[idx] = s.copy(enabled = !s.enabled)
        return true
    }

    fun getByUrl(sourceUrl: String): BookSource? =
        sources.firstOrNull { it.sourceUrl == sourceUrl }

    fun count(): Int = sources.size
}
