package com.reader.android.data.repository

import com.reader.android.data.storage.SearchHistoryDao
import com.reader.android.data.storage.SearchHistoryEntity

/**
 * P2: Search history repository — local fallback until Core lands
 * `search.history.list` / `add` / `clear` protocol methods.
 *
 * Exposed through the Host capability surface
 * ([com.reader.host.SearchHistoryCapabilityHandlers]) so the UI dispatches
 * `search.history.*` through the same `HostRequest → HostAdapter.dispatch →
 * HostReply` round-trip used by source / RSS / WebDAV handlers.
 */
interface SearchHistoryRepository {
    suspend fun list(limit: Int = 20): List<String>
    suspend fun add(keyword: String)
    suspend fun delete(keyword: String)
    suspend fun clear()
}

/**
 * Room-backed implementation. Wired in production via
 * [com.reader.android.AppProvider.searchHistoryRepository].
 */
class RoomSearchHistoryRepository(private val dao: SearchHistoryDao) : SearchHistoryRepository {
    override suspend fun list(limit: Int): List<String> =
        dao.getAll(limit).map { it.keyword }

    override suspend fun add(keyword: String) {
        val existing = dao.getAll(100).find { it.keyword == keyword }
        if (existing != null) dao.touch(keyword, System.currentTimeMillis())
        else dao.upsert(SearchHistoryEntity(keyword = keyword))
    }

    override suspend fun delete(keyword: String) = dao.delete(keyword)
    override suspend fun clear() = dao.clear()
}

/**
 * Pure-JVM fake backed by an in-memory list. Used by JVM tests so the
 * capability handlers can be exercised without an Android Context / Room.
 */
class FakeSearchHistoryRepository : SearchHistoryRepository {
    private val items = mutableListOf<Pair<String, Long>>()

    override suspend fun list(limit: Int): List<String> =
        items.sortedByDescending { it.second }.take(limit).map { it.first }

    override suspend fun add(keyword: String) {
        items.removeAll { it.first == keyword }
        items.add(keyword to System.currentTimeMillis())
    }

    override suspend fun delete(keyword: String) {
        items.removeAll { it.first == keyword }
    }

    override suspend fun clear() {
        items.clear()
    }
}
