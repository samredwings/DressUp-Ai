package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database for DressUp AI.
 * Contains wardrobe items, try-on history, and model cache metadata.
 */
@Database(
    entities = [
        WardrobeItemEntity::class,
        TryOnHistoryEntity::class,
        ModelCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wardrobeDao(): WardrobeDao
    abstract fun tryOnHistoryDao(): TryOnHistoryDao
    abstract fun modelCacheDao(): ModelCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dressup_database"
                )
                    // Keep user wardrobe/history data. Add explicit Room Migration
                    // objects before increasing the schema version.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
