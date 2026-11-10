package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing try-on generation history.
 */
@Entity(tableName = "tryon_history")
data class TryOnHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val personImageUri: String,
    val garmentImageUri: String,
    val resultImageUri: String,
    val garmentName: String = "",
    val category: String = "upper_body",
    val backendUsed: String = "local",
    val processingMode: String = "ultra_light",
    val success: Boolean = true,
    val errorMessage: String? = null,
    val processingTimeMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
