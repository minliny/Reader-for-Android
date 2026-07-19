package com.reader.android.data.network

import com.reader.android.data.storage.RssSubscriptionDao
import com.reader.android.data.storage.RssSubscriptionEntity

/**
 * Legacy Room-backed [SubscriptionRepository] retained for schema migration
 * and compatibility tests. Core `rss.subscription.*` is the sole production
 * subscription owner; Core failure must never fall back to this table.
 */
class RoomSubscriptionRepository(
    private val dao: RssSubscriptionDao
) : SubscriptionRepository {

    override suspend fun add(subscription: RssSubscription) {
        dao.upsert(subscription.toEntity())
    }

    override suspend fun remove(feedUrl: String) {
        dao.delete(feedUrl)
    }

    override suspend fun get(feedUrl: String): RssSubscription? {
        return dao.get(feedUrl)?.toDomain()
    }

    override suspend fun getAll(): List<RssSubscription> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun markUpdated(feedUrl: String, lastItemGuid: String?) {
        dao.markUpdated(
            feedUrl = feedUrl,
            lastUpdated = System.currentTimeMillis(),
            lastItemGuid = lastItemGuid
        )
    }

    override suspend fun needsUpdate(feedUrl: String, maxAgeMs: Long): Boolean {
        val sub = dao.get(feedUrl) ?: return true
        return System.currentTimeMillis() - sub.lastUpdated > maxAgeMs
    }

    private fun RssSubscription.toEntity(): RssSubscriptionEntity = RssSubscriptionEntity(
        feedUrl = feedUrl,
        title = title,
        lastUpdated = lastUpdated,
        lastItemGuid = lastItemGuid
    )

    private fun RssSubscriptionEntity.toDomain(): RssSubscription = RssSubscription(
        feedUrl = feedUrl,
        title = title,
        lastUpdated = lastUpdated,
        lastItemGuid = lastItemGuid
    )
}
