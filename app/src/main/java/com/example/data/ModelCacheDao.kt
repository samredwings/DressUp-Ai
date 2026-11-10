package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelCacheDao {

    @Query("SELECT * FROM model_cache ORDER BY lastUsedDate DESC")
    fun getAllModelsFlow(): Flow<List<ModelCacheEntity>>

    @Query("SELECT * FROM model_cache WHERE modelName = :name")
    suspend fun getModel(name: String): ModelCacheEntity?

    @Query("SELECT * FROM model_cache WHERE lastUsedDate < :timestamp")
    suspend fun getStaleModels(timestamp: Long): List<ModelCacheEntity>

    @Query("SELECT SUM(fileSizeBytes) FROM model_cache")
    fun getTotalCacheSizeFlow(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelCacheEntity)

    @Query("UPDATE model_cache SET lastUsedDate = :timestamp WHERE modelName = :name")
    suspend fun updateLastUsed(name: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteModel(model: ModelCacheEntity)

    @Query("DELETE FROM model_cache WHERE modelName = :name")
    suspend fun deleteByName(name: String)
}
