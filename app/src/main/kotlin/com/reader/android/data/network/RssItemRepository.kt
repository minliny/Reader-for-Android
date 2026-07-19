package com.reader.android.data.network

import com.reader.android.data.storage.RssItemDao
import com.reader.android.data.storage.RssItemEntity

/**
 * Legacy Room cache retained for database migration and compatibility tests.
 * Slice 11 production uses Core `rss.subscription.items` and scoped
 * `rss.item.read`; this repository must not be used as a business-data
 * fallback when Core is unavailable.
 */
interface RssItemRepository {
    suspend fun listByFeed(feedUrl: String): List<RssItem>
    suspend fun listAll(limit: Int = 100): List<RssItem>
    suspend fun listUnread(): List<RssItem>
    suspend fun upsertAll(feedUrl: String, items: List<RssItem>)
    suspend fun markRead(guid: String, read: Boolean)
    suspend fun deleteByFeed(feedUrl: String)
    suspend fun clear()
}

/**
 * Room-backed legacy [RssItemRepository]. Maps [RssItem] <-> [RssItemEntity].
 */
class RoomRssItemRepository(
    private val dao: RssItemDao
) : RssItemRepository {

    override suspend fun listByFeed(feedUrl: String): List<RssItem> =
        dao.getByFeed(feedUrl).map { it.toDomain() }

    override suspend fun listAll(limit: Int): List<RssItem> =
        dao.getAll(limit).map { it.toDomain() }

    override suspend fun listUnread(): List<RssItem> =
        dao.getUnread().map { it.toDomain() }

    override suspend fun upsertAll(feedUrl: String, items: List<RssItem>) {
        dao.upsertAll(items.map { it.toEntity(feedUrl) })
    }

    override suspend fun markRead(guid: String, read: Boolean) {
        dao.markRead(guid, read)
    }

    override suspend fun deleteByFeed(feedUrl: String) {
        dao.deleteByFeed(feedUrl)
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun RssItem.toEntity(feedUrl: String): RssItemEntity = RssItemEntity(
        itemGuid = guid?.takeIf { it.isNotEmpty() }
            ?: link.takeIf { it.isNotEmpty() }
            ?: "$feedUrl#$title",
        feedUrl = feedUrl,
        title = title,
        link = link,
        description = description,
        author = author,
        pubDate = parsePubDate(pubDate)
    )

    private fun RssItemEntity.toDomain(): RssItem = RssItem(
        title = title,
        link = link,
        description = description,
        author = author,
        pubDate = if (pubDate > 0) pubDate.toString() else null,
        guid = itemGuid
    )
}

/**
 * In-memory [RssItemRepository] for JVM tests (no Android Context required).
 * Stores read-state alongside the cached item so [markRead] round-trips
 * survive listAll / listUnread / listByFeed.
 */
class FakeRssItemRepository : RssItemRepository {
    private data class Entry(
        val item: RssItem,
        val feedUrl: String,
        val isRead: Boolean = false
    )

    private val entries = mutableListOf<Entry>()

    override suspend fun listByFeed(feedUrl: String): List<RssItem> =
        entries.filter { it.feedUrl == feedUrl }.sortedByDescending { parsePubDate(it.item.pubDate) }
            .map { it.item }

    override suspend fun listAll(limit: Int): List<RssItem> =
        entries.sortedByDescending { parsePubDate(it.item.pubDate) }.take(limit).map { it.item }

    override suspend fun listUnread(): List<RssItem> =
        entries.filter { !it.isRead }.sortedByDescending { parsePubDate(it.item.pubDate) }
            .map { it.item }

    override suspend fun upsertAll(feedUrl: String, items: List<RssItem>) {
        items.forEach { item ->
            val guid = stableGuid(feedUrl, item)
            // remove existing entry with same guid (REPLACE semantics)
            entries.removeAll { stableGuid(it.feedUrl, it.item) == guid }
            entries.add(Entry(item = item, feedUrl = feedUrl))
        }
    }

    override suspend fun markRead(guid: String, read: Boolean) {
        val idx = entries.indexOfFirst { stableGuid(it.feedUrl, it.item) == guid }
        if (idx >= 0) {
            val e = entries[idx]
            entries[idx] = e.copy(isRead = read)
        }
    }

    override suspend fun deleteByFeed(feedUrl: String) {
        entries.removeAll { it.feedUrl == feedUrl }
    }

    override suspend fun clear() {
        entries.clear()
    }

    private fun stableGuid(feedUrl: String, item: RssItem): String =
        item.guid?.takeIf { it.isNotEmpty() }
            ?: item.link.takeIf { it.isNotEmpty() }
            ?: "$feedUrl#${item.title}"
}

/**
 * Best-effort parse of an RSS pubDate string to epoch millis. Accepts RFC-1123
 * (`Wed, 02 Oct 2024 13:37:00 GMT`) and ISO-8601 (`2024-10-02T13:37:00Z`).
 * Returns 0 when the string is null/blank or unparseable — the local cache
 * degrades to insertion order instead of throwing.
 */
internal fun parsePubDate(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    value.toLongOrNull()?.let { return it }
    val formats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss"
    )
    for (pattern in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return sdf.parse(value)?.time ?: continue
        } catch (_: Exception) {
            // try next format
        }
    }
    return 0L
}
