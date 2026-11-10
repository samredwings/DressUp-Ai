package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for wardrobe operations.
 * All queries return Flow for reactive UI updates.
 */
@Dao
interface WardrobeDao {

    @Query("SELECT * FROM wardrobe_items ORDER BY createdAt DESC")
    fun getAllItemsFlow(): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items WHERE category = :category ORDER BY createdAt DESC")
    fun getItemsByCategoryFlow(category: String): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items ORDER BY createdAt DESC")
    suspend fun getAllItems(): List<WardrobeItemEntity>

    @Query("SELECT * FROM wardrobe_items WHERE id = :id")
    suspend fun getItemById(id: Long): WardrobeItemEntity?

    @Query("SELECT COUNT(*) FROM wardrobe_items")
    fun getItemCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: WardrobeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<WardrobeItemEntity>)

    @Update
    suspend fun updateItem(item: WardrobeItemEntity)

    @Delete
    suspend fun deleteItem(item: WardrobeItemEntity)

    @Query("DELETE FROM wardrobe_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM wardrobe_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM wardrobe_items WHERE name LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchItemsFlow(query: String): Flow<List<WardrobeItemEntity>>
}
