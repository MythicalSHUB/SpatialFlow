package com.codetrio.spatialflow.data.lyrics.providers.paxsenix

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixApi
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider

class PaxsenixSpotifyProvider(private val api: PaxsenixApi) : LyricsProvider {
    companion object {
        private const val TAG = "PaxsenixSpotify"
    }

    override fun getName(): String = "Spotify"

    override fun getPriority(): Int = 1 // Highest priority for word-by-word

    override fun search(track: TrackMetadata): LyricsResult? {
        try {
            val query = "${track.cleanedTitle} ${track.cleanedArtist}"
            val searchResponse = api.searchSpotify(query).execute()
            
            if (searchResponse.isSuccessful && searchResponse.body() != null) {
                val searchBody = searchResponse.body()!!
                if (searchBody.isJsonArray && searchBody.asJsonArray.size() > 0) {
                    val firstResult = searchBody.asJsonArray.get(0).asJsonObject
                    val trackId = if (firstResult.has("trackId")) firstResult.get("trackId").asString else null
                    
                    if (trackId != null) {
                        val lyricsResponse = api.getSpotifyLyrics(trackId).execute()
                        if (lyricsResponse.isSuccessful && lyricsResponse.body() != null) {
                            val rawBody = lyricsResponse.body()!!.string()
                            val lyricsStr = cleanJsonLyrics(rawBody)
                            
                            if (lyricsStr.isNotBlank() && !lyricsStr.contains("\"error\"")) {
                                val result = LyricsResult(
                                    providerName = getName(),
                                    matchedTitle = if (firstResult.has("name")) firstResult.get("name").asString else track.cleanedTitle,
                                    matchedArtist = if (firstResult.has("artistName")) firstResult.get("artistName").asString else track.cleanedArtist
                                )
                                
                                if (lyricsStr.contains("[00:")) {
                                    result.setSyncedLyrics(lyricsStr)
                                    if (PaxsenixProviderUtils.isWordByWord(lyricsStr)) {
                                        result.setWordByWord(true)
                                    }
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

    private fun cleanJsonLyrics(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                com.google.gson.JsonParser.parseString(trimmed).asString
            } catch (e: Exception) {
                trimmed.removeSurrounding("\"").replace("\\n", "\n").replace("\\\"", "\"")
            }
        } else trimmed
    }
}
