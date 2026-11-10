package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules background maintenance tasks.
 */
object WorkScheduler {

    fun scheduleCacheCleanup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(false)
            .build()

        val cleanupWork = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            24, TimeUnit.HOURS,  // Repeat every 24 hours
            6, TimeUnit.HOURS    // Flex interval
        )
            .setConstraints(constraints)
            .addTag(CacheCleanupWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CacheCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork
        )
    }

    fun cancelAllWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
