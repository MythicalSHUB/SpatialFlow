package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import kotlinx.coroutines.runBlocking

/**
 * YouTube Music InnerTube lyrics provider.
 * Fetches official lyrics directly from YouTube Music servers using the
 * authenticated InnerTube API. Requires a videoId in the TrackMetadata.
 *
 * This provider is given the highest priority (1) because YouTube's lyrics
 * are the official source and perfectly match the playing track.
 */
class YouTubeMusicLyricsProvider : LyricsProvider {

    companion object {
        private const val TAG = "YTMusicLyricsProvider"
    }

    override fun getName(): String = "YouTube Music"

    override fun getPriority(): Int = 1 // Highest priority — direct source

    override fun search(track: TrackMetadata): LyricsResult? {
        val videoId = track.videoId
        if (videoId.isNullOrEmpty()) {
            Log.d(TAG, "No videoId available, skipping YouTube Music lyrics")
            return null
        }

        return try {
            val lyricsText = runBlocking {
                YouTubeMusic.lyrics(videoId).getOrNull()
            }

            if (lyricsText.isNullOrBlank()) {
                Log.d(TAG, "No lyrics returned from YouTube Music for $videoId")
                return null
            }

            LyricsResult().apply {
                plainLyrics = lyricsText
                providerName = getName()
                confidence = 1.0f // Perfect match — same source as the song
                matchedTitle = track.rawTitle
                matchedArtist = track.rawArtist
                isSynced = false // YouTube Music plain text lyrics are not synced
                isWordByWord = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube Music lyrics fetch failed for $videoId: ${e.message}")
            null
        }
    }
}
