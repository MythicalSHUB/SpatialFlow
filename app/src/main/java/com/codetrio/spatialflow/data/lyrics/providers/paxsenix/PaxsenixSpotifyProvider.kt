package com.codetrio.spatialflow.data.lyrics.providers.paxsenix

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixLyrics
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider

class PaxsenixSpotifyProvider(private val context: Context) : LyricsProvider {
    companion object {
        private const val TAG = "PaxsenixSpotify"
    }

    override fun getName(): String = "Paxsenix: Spotify"
    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val lyricsStr = PaxsenixLyrics.getSpotifyLyrics(
                context, track.cleanedTitle, track.cleanedArtist
            ) ?: return null

            val result = LyricsResult(
                providerName = getName(),
                matchedTitle = track.cleanedTitle,
                matchedArtist = track.cleanedArtist
            )
            when {
                lyricsStr.contains("[00:") -> {
                    result.setSyncedLyrics(lyricsStr)
                    if (PaxsenixLyrics.isWordByWord(lyricsStr)) result.setWordByWord(true)
                }
                else -> result.setPlainLyrics(lyricsStr)
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
            null
        }
    }
}
