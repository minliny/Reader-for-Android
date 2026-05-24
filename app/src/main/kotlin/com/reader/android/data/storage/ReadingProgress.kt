package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val bookUrl: String,
    val bookName: String,
    val author: String?,
    val currentChapterUrl: String,
    val currentChapterTitle: String,
    val chapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val scrollPosition: Float = 0f,
    val lastReadTime: Long = System.currentTimeMillis()
)

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress ORDER BY lastReadTime DESC")
    suspend fun getAll(): List<ReadingProgress>

    @Query("SELECT * FROM reading_progress WHERE bookUrl = :bookUrl")
    suspend fun getByUrl(bookUrl: String): ReadingProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgress)

    @Update
    suspend fun update(progress: ReadingProgress)

    @Query("DELETE FROM reading_progress WHERE bookUrl = :bookUrl")
    suspend fun delete(bookUrl: String)
}

@Database(entities = [ReadingProgress::class, CachedChapter::class, SyncOperationLog::class, BookmarkEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun cachedChapterDao(): CachedChapterDao
    abstract fun syncOperationLogDao(): SyncOperationLogDao
    abstract fun bookmarkDao(): BookmarkDao
}
