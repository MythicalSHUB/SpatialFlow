package com.codetrio.spatialflow.data.lyrics.providers.paxsenix

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixApi
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider

class PaxsenixMusixmatchProvider(private val api: PaxsenixApi) : LyricsProvider {
    companion object {
        private const val TAG = "PaxsenixMusixmatch"
    }

    override fun getName(): String = "Musixmatch"

    override fun getPriority(): Int = 1 // Highest priority for word-by-word

    override fun search(track: TrackMetadata): LyricsResult? {
        try {
            val durationSec = if (track.durationMs > 0) (track.durationMs / 1000).toString() else null
            
            // Try word-by-word first
            var response = api.getMusixmatchLyrics(
                query = "${track.cleanedTitle} ${track.cleanedArtist}",
                trackTitle = track.cleanedTitle,
                artistName = track.cleanedArtist,
                duration = durationSec,
                type = "word"
            ).execute()

            if (!response.isSuccessful || response.body() == null) {
                // Fallback to normal
                response = api.getMusixmatchLyrics(
                    query = "${track.cleanedTitle} ${track.cleanedArtist}",
                    trackTitle = track.cleanedTitle,
                    artistName = track.cleanedArtist,
                    duration = durationSec,
                    type = "default"
                ).execute()
            }

            if (response.isSuccessful && response.body() != null) {
                val rawBody = response.body()!!.string()
                val lyricsStr = cleanJsonLyrics(rawBody)
                
                if (lyricsStr.isNotBlank() && !lyricsStr.contains("\"error\"")) {
                    val result = LyricsResult(
                        providerName = getName(),
                        matchedTitle = track.cleanedTitle,
                        matchedArtist = track.cleanedArtist
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
