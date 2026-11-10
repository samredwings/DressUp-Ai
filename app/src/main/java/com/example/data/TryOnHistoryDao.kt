package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TryOnHistoryDao {

    @Query("SELECT * FROM tryon_history ORDER BY createdAt DESC")
    fun getAllHistoryFlow(): Flow<List<TryOnHistoryEntity>>

    @Query("SELECT * FROM tryon_history WHERE success = 1 ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentSuccessFlow(limit: Int = 20): Flow<List<TryOnHistoryEntity>>

    @Query("SELECT * FROM tryon_history ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryPaged(limit: Int, offset: Int): List<TryOnHistoryEntity>

    @Query("SELECT COUNT(*) FROM tryon_history")
    fun getHistoryCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tryon_history WHERE success = 1")
    suspend fun getSuccessCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: TryOnHistoryEntity): Long

    @Delete
    suspend fun deleteHistory(item: TryOnHistoryEntity)

    @Query("DELETE FROM tryon_history WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM tryon_history")
    suspend fun deleteAll()
}
