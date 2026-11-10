package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.example.data.ModelCacheDao
import com.example.data.TryOnHistoryDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * CACHE CLEANUP WORKER
 *
 * Periodically purges:
 * - Try-on history older than 30 days
 * - Model cache files unused for 30 days
 * - Temporary image files in cache directory older than 7 days
 *
 * Scheduled via WorkManager to run daily.
 */
@HiltWorker
class CacheCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val historyDao: TryOnHistoryDao,
    private val modelCacheDao: ModelCacheDao
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CacheCleanupWorker"
        private const val HISTORY_RETENTION_DAYS = 30L
        private const val MODEL_RETENTION_DAYS = 30L
        private const val CACHE_RETENTION_DAYS = 7L
        const val WORK_NAME = "cache_cleanup"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting cache cleanup...")

            val now = System.currentTimeMillis()
            var cleanedCount = 0

            // 1. Clean old try-on history from Room
            val historyCutoff = now - (HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            val oldHistory = historyDao.getHistoryPaged(limit = 10000, offset = 0)
                .filter { it.createdAt < historyCutoff }
            oldHistory.forEach {
                historyDao.deleteHistory(it)
                cleanedCount++
            }
            Log.d(TAG, "Cleaned $cleanedCount history entries")

            // 2. Clean stale model cache
            val modelCutoff = now - (MODEL_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            val staleModels = modelCacheDao.getStaleModels(modelCutoff)
            val modelsRoot = File(applicationContext.filesDir, "models").canonicalFile
            staleModels.forEach { model ->
                // Model metadata is persisted locally; never trust it as an arbitrary
                // filesystem path. Only remove files contained in filesDir/models.
                val candidate = runCatching { File(model.filePath).canonicalFile }.getOrNull()
                if (candidate != null && candidate.toPath().startsWith(modelsRoot.toPath())) {
                    candidate.delete()
                } else {
                    Log.w(TAG, "Skipped model cache path outside models directory")
                }
                modelCacheDao.deleteModel(model)
                cleanedCount++
            }
            Log.d(TAG, "Cleaned ${staleModels.size} stale models")

            // 3. Clean temp cache files
            val cacheDir = applicationContext.cacheDir
            val cacheCutoff = now - (CACHE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cacheCutoff &&
                    (file.name.startsWith("tryon_") || file.name.startsWith("temp_"))
                ) {
                    if (file.delete()) cleanedCount++
                }
            }

            // 4. Clean old files in filesDir
            val filesDir = applicationContext.filesDir
            filesDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name == "models") {
                    // Skip model directory - managed by ModelCacheDao
                    return@forEach
                }
                if (file.lastModified() < cacheCutoff && file.extension in listOf("jpg", "png", "webp")) {
                    if (file.delete()) cleanedCount++
                }
            }

            Log.d(TAG, "Cache cleanup complete. Total items cleaned: $cleanedCount")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Cache cleanup failed", e)
            Result.retry()
        }
    }
}
