package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.SimpMusicApi
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata

class SimpMusicProvider(private val api: SimpMusicApi) : LyricsProvider {
    companion object {
        private const val TAG = "SimpMusicProvider"
    }

    override fun getName(): String = "SimpMusic"

    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        try {
            val response = api.getLyrics(
                title = track.cleanedTitle,
                artist = track.cleanedArtist,
                videoId = track.videoId
            ).execute()
            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val dataList = apiResponse.data
                if (!dataList.isNullOrEmpty()) {
                    val item = dataList.firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyric.isNullOrBlank() } 
                        ?: dataList.first()
                    
                    val lyrics = item.plainLyric
                    val syncedLyrics = item.syncedLyrics

                    val result = LyricsResult(
                        providerName = getName(),
                        matchedTitle = item.songTitle ?: track.cleanedTitle,
                        matchedArtist = item.artistName ?: track.cleanedArtist
                    )

                    if (!syncedLyrics.isNullOrBlank()) {
                        result.setSyncedLyrics(syncedLyrics)
                        return result
                    } else if (!lyrics.isNullOrBlank()) {
                        if (lyrics.contains("[00:")) {
                            result.setSyncedLyrics(lyrics)
                        } else {
                            result.setPlainLyrics(lyrics)
                        }
                        return result
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
        }
        return null
    }
}
