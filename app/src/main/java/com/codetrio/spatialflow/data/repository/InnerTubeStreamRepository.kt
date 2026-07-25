package com.codetrio.spatialflow.data.repository

import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.Result
import com.codetrio.spatialflow.domain.repository.StreamRepository
import kotlinx.coroutines.CancellationException

class InnerTubeStreamRepository : StreamRepository {
    override suspend fun getStreamUrl(videoId: String): Result<String, DataError.Network> {
        return try {
            val result = YouTubeMusic.getStreamUrl(videoId)
            val url = result.getOrNull()
            if (url != null) {
                Result.Success(url)
            } else {
                Result.Error(DataError.Network.NOT_FOUND)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun getPlaybackPosition(videoId: String): Result<Long, DataError.Network> {
        return try {
            val result = YouTubeMusic.getPlaybackPosition(videoId)
            val pos = result.getOrNull()
            if (pos != null) {
                Result.Success(pos)
            } else {
                Result.Error(DataError.Network.NOT_FOUND)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
