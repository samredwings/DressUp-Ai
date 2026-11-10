package com.example.di

import android.content.Context
import com.example.engine.DeviceProfiler
import com.example.engine.FreeCloudClient
import com.example.engine.ImageEnhancer
import com.example.engine.ModelManager
import com.example.engine.TryOnPipelineV3
import com.example.engine.autocolab.UnifiedGpuManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI Module for Engine components.
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideDeviceProfiler(@ApplicationContext context: Context): DeviceProfiler {
        return DeviceProfiler(context)
    }

    @Provides
    @Singleton
    fun provideImageEnhancer(@ApplicationContext context: Context): ImageEnhancer {
        return ImageEnhancer(context)
    }

    @Provides
    @Singleton
    fun provideModelManager(@ApplicationContext context: Context): ModelManager {
        return ModelManager(context)
    }

    @Provides
    @Singleton
    fun provideFreeCloudClient(@ApplicationContext context: Context): FreeCloudClient {
        return FreeCloudClient(context)
    }

    @Provides
    @Singleton
    fun provideUnifiedGpuManager(
        @ApplicationContext context: Context,
        freeCloudClient: FreeCloudClient
    ): UnifiedGpuManager {
        return UnifiedGpuManager(context, freeCloudClient)
    }

    @Provides
    fun provideTryOnPipeline(
        @ApplicationContext context: Context,
        unifiedGpuManager: UnifiedGpuManager,
        freeCloudClient: FreeCloudClient
    ): TryOnPipelineV3 {
        return TryOnPipelineV3(context, unifiedGpuManager, freeCloudClient)
    }
}
