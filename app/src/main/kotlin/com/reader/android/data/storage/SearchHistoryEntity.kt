package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * P2: Room entity for persisted search history keywords.
 *
 * Local fallback until Core lands `search.history.*` protocol methods.
 * The keyword is the natural primary key (one row per unique keyword). The
 * `createdAt` timestamp drives recency ordering; `hitCount` records how many
 * times the keyword has been searched (for future ranking / promotion).
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val keyword: String,
    val createdAt: Long = System.currentTimeMillis(),
    val hitCount: Int = 1
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 20): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("UPDATE search_history SET createdAt = :createdAt, hitCount = hitCount + 1 WHERE keyword = :keyword")
    suspend fun touch(keyword: String, createdAt: Long)

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
