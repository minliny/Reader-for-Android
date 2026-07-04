package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * P4: Room entity for persisted RSS subscriptions.
 *
 * Mirrors [com.reader.android.data.network.RssSubscription] 1:1 so the
 * [com.reader.android.data.network.SubscriptionRepository] can persist
 * subscriptions across process death. The feed URL is the natural primary
 * key (one subscription per feed).
 */
@Entity(tableName = "rss_subscriptions")
data class RssSubscriptionEntity(
    @PrimaryKey
    val feedUrl: String,
    val title: String,
    val lastUpdated: Long,
    val lastItemGuid: String?
)

@Dao
interface RssSubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RssSubscriptionEntity)

    @Query("DELETE FROM rss_subscriptions WHERE feedUrl = :feedUrl")
    suspend fun delete(feedUrl: String)

    @Query("SELECT * FROM rss_subscriptions WHERE feedUrl = :feedUrl")
    suspend fun get(feedUrl: String): RssSubscriptionEntity?

    @Query("SELECT * FROM rss_subscriptions ORDER BY lastUpdated DESC")
    suspend fun getAll(): List<RssSubscriptionEntity>

    @Query("UPDATE rss_subscriptions SET lastUpdated = :lastUpdated, lastItemGuid = :lastItemGuid WHERE feedUrl = :feedUrl")
    suspend fun markUpdated(feedUrl: String, lastUpdated: Long, lastItemGuid: String?)
}
