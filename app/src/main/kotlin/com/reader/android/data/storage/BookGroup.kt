package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * P0-3: A user-defined book group / category (e.g. "正在读", "已读完", "武侠").
 * Books themselves are owned by the Core bookshelf; this table only records the
 * group definition. Membership is recorded by [BookGroupAssignment] keyed by `bookUrl`
 * so it stays in sync with the Core-owned book list.
 */
@Entity(tableName = "book_group")
data class BookGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * P0-3: Mapping from a Core-owned book (`bookUrl`) to a local [BookGroupEntity].
 * Many-to-many: a book may belong to several groups and a group holds many books.
 * Deleting a group cascades to its assignments.
 */
@Entity(
    tableName = "book_group_assignment",
    primaryKeys = ["groupId", "bookUrl"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = BookGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("bookUrl")]
)
data class BookGroupAssignment(
    val groupId: Long,
    val bookUrl: String,
    val assignedAt: Long = System.currentTimeMillis()
)

@Dao
interface BookGroupDao {

    @Query("SELECT * FROM book_group ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<BookGroupEntity>

    @Query("SELECT * FROM book_group WHERE id = :id")
    suspend fun getById(id: Long): BookGroupEntity?

    @Query("SELECT * FROM book_group WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): BookGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: BookGroupEntity): Long

    @Query("DELETE FROM book_group WHERE id = :id")
    suspend fun delete(id: Long)

    // ── assignment ops ──

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assign(assignment: BookGroupAssignment)

    @Query("DELETE FROM book_group_assignment WHERE groupId = :groupId AND bookUrl = :bookUrl")
    suspend fun unassign(groupId: Long, bookUrl: String)

    @Query("DELETE FROM book_group_assignment WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)

    @Query("DELETE FROM book_group_assignment WHERE bookUrl = :bookUrl")
    suspend fun removeFromAllGroups(bookUrl: String)

    @Query("SELECT bookUrl FROM book_group_assignment WHERE groupId = :groupId")
    suspend fun bookUrlsInGroup(groupId: Long): List<String>

    @Query("SELECT * FROM book_group_assignment WHERE bookUrl = :bookUrl")
    suspend fun groupsForBook(bookUrl: String): List<BookGroupAssignment>
}
