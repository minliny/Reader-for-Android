package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Legacy Room entity retained so existing installations can migrate without
 * destructive schema changes. Slice 11 production does not read or write it
 * as a fallback for Core RSS state.
 *
 * The item GUID is the natural primary key (unique per article within a feed).
 * `feedUrl` links the item back to its parent [RssSubscriptionEntity].
 * `isRead` is the read-state field the subscription entity lacked.
 */
@Entity(tableName = "rss_items")
data class RssItemEntity(
    @PrimaryKey
    val itemGuid: String,
    val feedUrl: String,
    val title: String,
    val link: String,
    val description: String? = null,
    val author: String? = null,
    val pubDate: Long = 0,
    val fetchedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Dao
interface RssItemDao {
    @Query("SELECT * FROM rss_items WHERE feedUrl = :feedUrl ORDER BY pubDate DESC")
    suspend fun getByFeed(feedUrl: String): List<RssItemEntity>

    @Query("SELECT * FROM rss_items ORDER BY pubDate DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 100): List<RssItemEntity>

    @Query("SELECT * FROM rss_items WHERE isRead = 0 ORDER BY pubDate DESC")
    suspend fun getUnread(): List<RssItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RssItemEntity>)

    @Query("UPDATE rss_items SET isRead = :read WHERE itemGuid = :guid")
    suspend fun markRead(guid: String, read: Boolean)

    @Query("DELETE FROM rss_items WHERE feedUrl = :feedUrl")
    suspend fun deleteByFeed(feedUrl: String)

    @Query("DELETE FROM rss_items")
    suspend fun clear()
}
