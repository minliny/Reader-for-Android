package com.reader.android.data.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "sync_operation_log")
data class SyncOperationLog(
    @PrimaryKey val operationId: String,
    val syncType: String,
    val state: String,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val attemptCount: Int = 1,
    val errorMessage: String? = null
)

@Dao
interface SyncOperationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncOperationLog)

    @Query("SELECT * FROM sync_operation_log ORDER BY startedAt DESC")
    suspend fun getAll(): List<SyncOperationLog>

    @Query("SELECT * FROM sync_operation_log WHERE syncType = :type ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLastByType(type: String): SyncOperationLog?

    @Query("DELETE FROM sync_operation_log WHERE startedAt < :before")
    suspend fun evictOlderThan(before: Long)
}
