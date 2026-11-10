package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for wardrobe items.
 * Reactive persistence with Flow-based queries.
 */
@Entity(tableName = "wardrobe_items")
data class WardrobeItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val category: String,
    val localUri: String,
    val thumbnailUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
