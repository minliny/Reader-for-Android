package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val bookmarkId: Long = 0,
    val bookUrl: String,
    val bookName: String,
    val chapterUrl: String,
    val chapterTitle: String,
    val snippet: String = "",
    val paragraphIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark WHERE bookUrl = :bookUrl ORDER BY createdAt DESC")
    suspend fun getByBook(bookUrl: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmark WHERE bookUrl = :bookUrl AND chapterUrl = :chapterUrl LIMIT 1")
    suspend fun getByChapter(bookUrl: String, chapterUrl: String): BookmarkEntity?

    @Query("SELECT COUNT(*) FROM bookmark WHERE bookUrl = :bookUrl AND chapterUrl = :chapterUrl")
    suspend fun isBookmarked(bookUrl: String, chapterUrl: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE bookmarkId = :bookmarkId")
    suspend fun deleteById(bookmarkId: Long)

    @Query("DELETE FROM bookmark WHERE bookUrl = :bookUrl AND chapterUrl = :chapterUrl")
    suspend fun deleteByChapter(bookUrl: String, chapterUrl: String)

    @Query("SELECT chapterUrl FROM bookmark WHERE bookUrl = :bookUrl")
    suspend fun getBookmarkedUrls(bookUrl: String): List<String>
}
