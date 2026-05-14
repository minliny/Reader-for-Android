package com.reader.android.data.network

interface SubscriptionRepository {
    suspend fun add(subscription: RssSubscription)
    suspend fun remove(feedUrl: String)
    suspend fun get(feedUrl: String): RssSubscription?
    suspend fun getAll(): List<RssSubscription>
    suspend fun markUpdated(feedUrl: String, lastItemGuid: String?)
    suspend fun needsUpdate(feedUrl: String, maxAgeMs: Long = 30 * 60 * 1000): Boolean
}

class FakeSubscriptionRepository : SubscriptionRepository {
    private val subs = mutableMapOf<String, RssSubscription>()

    override suspend fun add(subscription: RssSubscription) {
        subs[subscription.feedUrl] = subscription
    }

    override suspend fun remove(feedUrl: String) { subs.remove(feedUrl) }

    override suspend fun get(feedUrl: String) = subs[feedUrl]

    override suspend fun getAll() = subs.values.toList()

    override suspend fun markUpdated(feedUrl: String, lastItemGuid: String?) {
        subs[feedUrl]?.let {
            subs[feedUrl] = it.copy(
                lastUpdated = System.currentTimeMillis(),
                lastItemGuid = lastItemGuid
            )
        }
    }

    override suspend fun needsUpdate(feedUrl: String, maxAgeMs: Long): Boolean {
        val sub = subs[feedUrl] ?: return true
        return System.currentTimeMillis() - sub.lastUpdated > maxAgeMs
    }
}
