package com.codetrio.spatialflow.di

import com.codetrio.spatialflow.data.cache.FileCacheDataSource
import com.codetrio.spatialflow.domain.repository.LocalCacheDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindLocalCacheDataSource(
        fileCacheDataSource: FileCacheDataSource
    ): LocalCacheDataSource
}
