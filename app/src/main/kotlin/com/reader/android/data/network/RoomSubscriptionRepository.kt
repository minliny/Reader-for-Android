package com.reader.android.data.network

import com.reader.android.data.storage.RssSubscriptionDao
import com.reader.android.data.storage.RssSubscriptionEntity

/**
 * P4: Room-backed [SubscriptionRepository].
 *
 * Persists RSS subscriptions across process death via Room. The subscription
 * metadata (feed URL, title, last-updated timestamp, last seen item GUID) is
 * the only piece the host owns; the actual feed content (article list) is
 * DomainState owned by Core (`rss.list` / `rss.item.read`) and is fetched
 * on demand through the Core bridge.
 *
 * TODO(core-blocker): Core protocol does not yet expose `rss.list` /
 * `rss.item.read` / `rss.subscription.*` / `rss.source.*`. Until those
 * land, the UI can only manage subscription metadata here; fetching
 * articles requires the Core methods to be implemented.
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
