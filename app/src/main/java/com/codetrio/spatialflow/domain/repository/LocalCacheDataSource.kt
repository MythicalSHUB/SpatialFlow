package com.codetrio.spatialflow.domain.repository

import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.EmptyResult

interface LocalCacheDataSource {
    /**
     * Clears old temporary files (e.g. FFmpeg output, temp audio copies) on startup
     * to prevent app size bloat.
     */
    suspend fun clearOldCache(): EmptyResult<DataError.Local>
}
