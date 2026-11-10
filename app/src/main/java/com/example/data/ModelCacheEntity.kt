package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for tracking downloaded AI model files.
 * Used by cache cleanup worker to purge stale models.
 */
@Entity(tableName = "model_cache")
data class ModelCacheEntity(
    @PrimaryKey
    val modelName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val version: Int = 1,
    val downloadDate: Long = System.currentTimeMillis(),
    val lastUsedDate: Long = System.currentTimeMillis(),
    val isDownloaded: Boolean = true
)
