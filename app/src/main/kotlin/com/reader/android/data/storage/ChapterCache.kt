package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "chapter_cache")
data class CachedChapter(
    @PrimaryKey val contentUrl: String,
    val content: String,
    val title: String?,
    val nextPageUrl: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface CachedChapterDao {
    @Query("SELECT * FROM chapter_cache WHERE contentUrl = :url")
    suspend fun get(url: String): CachedChapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(chapter: CachedChapter)

    @Query("DELETE FROM chapter_cache WHERE cachedAt < :before")
    suspend fun evictOlderThan(before: Long)

    @Query("DELETE FROM chapter_cache")
    suspend fun clear()
}

class ChapterCacheManager(private val dao: CachedChapterDao) {

    suspend fun get(url: String): CachedChapter? = dao.get(url)

    suspend fun put(contentUrl: String, content: String, title: String?, nextPageUrl: String?) {
        dao.put(
            CachedChapter(
                contentUrl = contentUrl,
                content = content,
                title = title,
                nextPageUrl = nextPageUrl
            )
        )
    }

    suspend fun evictOld(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dao.evictOlderThan(cutoff)
    }

    suspend fun clear() = dao.clear()
}
