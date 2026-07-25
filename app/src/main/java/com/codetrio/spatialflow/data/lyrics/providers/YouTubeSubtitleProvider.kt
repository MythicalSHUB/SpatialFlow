package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixLyrics
import com.codetrio.spatialflow.data.lyrics.TrackMetadata

/**
 * YouTube Subtitle & Closed Caption Lyrics Provider.
 * Fetches timed video transcripts / subtitle events from YouTube via Paxsenix API
 * and converts them into clean LRC synced lines.
 */
class YouTubeSubtitleProvider : LyricsProvider {

    companion object {
        private const val TAG = "YTSubtitleProvider"
    }

    override fun getName(): String = "YouTube Subtitle"
    override fun getPriority(): Int = 8

    override fun search(track: TrackMetadata): LyricsResult? {
        val title = track.cleanedTitle
        val artist = track.cleanedArtist
        if (title.isBlank() || artist.isBlank()) return null

        return try {
            val paxsenixYt = PaxsenixLyrics.getYouTubeLyrics(
                context = null,
                title = title,
                artist = artist
            )

            if (!paxsenixYt.isNullOrBlank()) {
                val result = LyricsResult(
                    providerName = getName(),
                    matchedTitle = title,
                    matchedArtist = artist
                )
                if (paxsenixYt.contains("[")) {
                    result.setSyncedLyrics(paxsenixYt)
                    if (PaxsenixLyrics.isWordByWord(paxsenixYt)) {
                        result.setWordByWord(true)
                    }
                } else {
                    result.setPlainLyrics(paxsenixYt)
                }
                Log.d(TAG, "Successfully fetched YouTube transcript subtitle for $title - $artist")
                return result
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "YouTube Subtitle fetch failed for $title: ${e.message}")
            null
        }
    }
}
