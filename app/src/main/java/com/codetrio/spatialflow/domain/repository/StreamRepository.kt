package com.codetrio.spatialflow.domain.repository

import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.Result

interface StreamRepository {
    suspend fun getStreamUrl(videoId: String): Result<String, DataError.Network>
    suspend fun getPlaybackPosition(videoId: String): Result<Long, DataError.Network>
}
