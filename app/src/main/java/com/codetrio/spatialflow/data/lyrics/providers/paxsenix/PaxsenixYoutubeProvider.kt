package com.codetrio.spatialflow.data.lyrics.providers.paxsenix

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixApi
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider

class PaxsenixYoutubeProvider(private val api: PaxsenixApi) : LyricsProvider {
    companion object {
        private const val TAG = "PaxsenixYoutube"
    }

    override fun getName(): String = "YouTube (Paxsenix)"

    override fun getPriority(): Int = 3

    override fun search(track: TrackMetadata): LyricsResult? {
        try {
            val query = "${track.cleanedTitle} ${track.cleanedArtist}"
            val searchResponse = api.searchYouTube(query).execute()
            
            if (searchResponse.isSuccessful && searchResponse.body() != null) {
                val searchBody = searchResponse.body()!!
                if (searchBody.isJsonArray && searchBody.asJsonArray.size() > 0) {
                    val firstResult = searchBody.asJsonArray.get(0).asJsonObject
                    val trackId = if (firstResult.has("id")) firstResult.get("id").asString else if (firstResult.has("videoId")) firstResult.get("videoId").asString else null
                    
                    if (trackId != null) {
                        val lyricsResponse = api.getYouTubeLyrics(trackId).execute()
                        if (lyricsResponse.isSuccessful && lyricsResponse.body() != null) {
                            val lyricsStr = lyricsResponse.body()!!.string()
                            if (lyricsStr.isNotBlank() && !lyricsStr.contains("isError\":true")) {
                                val result = LyricsResult(
                                    providerName = getName(),
                                    matchedTitle = track.cleanedTitle,
                                    matchedArtist = track.cleanedArtist
                                )
                                
                                if (lyricsStr.contains("[00:")) {
                                    result.setSyncedLyrics(lyricsStr)
                                } else {
                                    result.setPlainLyrics(lyricsStr)
                                }
                                return result
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
        }
        return null
    }
}
