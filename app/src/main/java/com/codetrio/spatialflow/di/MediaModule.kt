package com.codetrio.spatialflow.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache")
        val prefs = context.getSharedPreferences("spatialflow_settings", Context.MODE_PRIVATE)
        val maxSizeMb = prefs.getInt("song_cache_max_size", 256)
        val maxSizeBytes = (if (maxSizeMb <= 0) 256L else maxSizeMb.toLong()) * 1024L * 1024L
        val evictor = LeastRecentlyUsedCacheEvictor(maxSizeBytes)
        val databaseProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    @Provides
    @Singleton
    fun provideHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setConnectTimeoutMs(5000)
            .setReadTimeoutMs(5000)
            .setAllowCrossProtocolRedirects(true)
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        @ApplicationContext context: Context,
        cache: SimpleCache,
        httpDataSourceFactory: DefaultHttpDataSource.Factory
    ): CacheDataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideStreamRepository(): com.codetrio.spatialflow.domain.repository.StreamRepository {
        return com.codetrio.spatialflow.data.repository.InnerTubeStreamRepository()
    }
}

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaEntryPoint {
    fun cacheDataSourceFactory(): CacheDataSource.Factory
}
