package com.codetrio.spatialflow.data.lyrics.providers

import android.media.MediaMetadataRetriever
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata

/**
 * Reads embedded lyrics from ID3 tags (USLT/SYLT) using MediaMetadataRetriever.
 * Highest trust level since lyrics come from the file itself.
 */
class EmbeddedLyricsProvider : LyricsProvider {

    companion object {
        private const val TAG = "EmbeddedLyricsProvider"
    }

    override fun getName(): String = "EmbeddedID3"

    override fun getPriority(): Int = 0 // Absolute highest priority — local file, no network needed

    override fun search(track: TrackMetadata): LyricsResult? {
        if (track.filePath.isEmpty()) return null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(track.filePath)

            // Try to get lyrics from METADATA_KEY_LYRICS (ID3 USLT tag)
            // Note: Not all Android versions/devices support this; as of API 24+
            // we can usually read it if the tag exists.
            // We use reflection-safe approach since the constant value is known (29).
            var lyrics: String? = null
            try {
                // METADATA_KEY_LYRICS is not in all versions, but value is a known constant
                lyrics = retriever.extractMetadata(29) // MediaMetadataRetriever.METADATA_KEY_LYRICS
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract lyrics metadata key: ${e.message}")
            }

            if (!lyrics.isNullOrBlank()) {
                val trimmed = lyrics.trim()
                val result = LyricsResult(
                    providerName = getName(),
                    matchedTitle = track.cleanedTitle,
                    matchedArtist = track.cleanedArtist,
                    matchedDuration = track.durationMs / 1000f
                )

                // Check if embedded lyrics are synced (LRC format)
                if (trimmed.matches("(?s).*\\[\\d{2}:\\d{2}\\.\\d{2,3}].*".toRegex())) {
                    result.setSyncedLyrics(trimmed)
                }
                result.plainLyrics = trimmed.replace("\\[\\d{2}:\\d{2}\\.\\d{2,3}]".toRegex(), "").trim()

                Log.d(TAG, "Found embedded lyrics for: ${track.cleanedTitle} (synced=${result.isSynced})")
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading file: ${track.filePath} — ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        return null
    }
}
