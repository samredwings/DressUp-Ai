package com.example.di

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.ModelCacheDao
import com.example.data.TryOnHistoryDao
import com.example.data.WardrobeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI Module for Room Database and DAOs.
 * Provides singleton instances of AppDatabase and all DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideWardrobeDao(database: AppDatabase): WardrobeDao {
        return database.wardrobeDao()
    }

    @Provides
    @Singleton
    fun provideTryOnHistoryDao(database: AppDatabase): TryOnHistoryDao {
        return database.tryOnHistoryDao()
    }

    @Provides
    @Singleton
    fun provideModelCacheDao(database: AppDatabase): ModelCacheDao {
        return database.modelCacheDao()
    }
}
