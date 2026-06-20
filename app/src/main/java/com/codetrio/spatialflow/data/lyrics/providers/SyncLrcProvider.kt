package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.SyncLrcApi
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata

/**
 * Remote lyrics provider backed by the SyncLRC API (api.synclrc.dev).
 * Prioritized above LrcLib because it can return karaoke (word-by-word)
 * Enhanced LRC lyrics which give a much richer playback experience.
 *
 * Flow:
 *  1. Query the API with track + artist (+ optional album / duration).
 *  2. If karaoke field is present → use it as syncedLyrics and flag isWordByWord.
 *  3. Else fall back to the standard synced field.
 *  4. Plain text is always kept as a secondary fallback.
 */
class SyncLrcProvider(private val api: SyncLrcApi) : LyricsProvider {

    companion object {
        private const val TAG = "SyncLrcProvider"
        /** Regex that matches Enhanced LRC word-level timestamps like <00:27.60> */
        private val KARAOKE_WORD_PATTERN = Regex("""<\d+:\d{2}[.:]\d+>""")
    }

    override fun getName(): String = "SyncLRC"

    // Priority 1 — same tier as LrcLib so they race in parallel,
    // but isBetterResult() in ProviderRouter will always prefer
    // word-by-word over plain synced, so karaoke wins automatically.
    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        try {
            val durationSec = if (track.durationMs > 0) (track.durationMs / 1000).toInt() else null
            var response = api.getLyrics(
                track.cleanedTitle,
                track.cleanedArtist,
                if (track.album.isEmpty()) null else track.album,
                durationSec
            ).execute()

            if (!response.isSuccessful || response.body() == null) {
                Log.d(TAG, "SyncLRC exact match failed. Retrying without duration and album...")
                response = api.getLyrics(
                    track.cleanedTitle,
                    track.cleanedArtist,
                    null,
                    null
                ).execute()
            }

            if (!response.isSuccessful || response.body() == null) return null

            val body = response.body()!!

            // Instrumental detection
            if (body.instrumental == true) {
                return LyricsResult(
                    providerName = getName(),
                    matchedTitle = body.track ?: track.cleanedTitle,
                    matchedArtist = body.artist ?: track.cleanedArtist,
                    matchedDuration = body.duration?.toFloat() ?: (track.durationMs / 1000f),
                    isInstrumental = true
                )
            }

            val result = LyricsResult(
                providerName = getName(),
                matchedTitle = body.track ?: track.cleanedTitle,
                matchedArtist = body.artist ?: track.cleanedArtist,
                matchedDuration = body.duration?.toFloat() ?: (track.durationMs / 1000f)
            )

            // ── Karaoke (word-by-word Enhanced LRC) ──
            if (!body.karaoke.isNullOrEmpty() && KARAOKE_WORD_PATTERN.containsMatchIn(body.karaoke)) {
                result.setSyncedLyrics(body.karaoke)
                result.isWordByWord = true
                Log.d(TAG, "Karaoke (word-by-word) lyrics found for: ${track.cleanedTitle}")
            }
            // ── Standard synced LRC ──
            else if (!body.synced.isNullOrEmpty()) {
                result.setSyncedLyrics(body.synced)
            }

            // Always keep plain as fallback
            if (!body.plain.isNullOrEmpty()) {
                result.plainLyrics = body.plain
            }

            if (result.hasLyrics()) {
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "SyncLRC query failed: ${e.message}")
        }
        return null
    }
}
