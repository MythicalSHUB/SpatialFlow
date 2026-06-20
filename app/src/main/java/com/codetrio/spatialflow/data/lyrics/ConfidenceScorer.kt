package com.codetrio.spatialflow.data.lyrics

import android.util.Log
import java.util.Locale
import kotlin.math.abs

/**
 * Scores a LyricsResult against a TrackMetadata to determine confidence.
 * 
 * Scoring formula:
 * confidence = titleMatch * 0.25 + artistMatch * 0.25 + durationMatch * 0.2
 * + syncQuality * 0.2 + providerTrust * 0.1
 *
 * Thresholds:
 * > 0.85 → Accept immediately
 * 0.6 – 0.85 → Show but continue searching in background
 * < 0.6 → Reject, try next provider
 */
class ConfidenceScorer {

    companion object {
        private const val TAG = "ConfidenceScorer"
        const val THRESHOLD_ACCEPT = 0.85f
        const val THRESHOLD_SHOW = 0.6f
    }

    /**
     * Score a lyrics result against the expected track metadata.
     * Returns a confidence value between 0.0 and 1.0.
     */
    fun score(result: LyricsResult?, track: TrackMetadata?): Float {
        if (result == null || track == null) return 0f

        val titleScore = scoreTitleMatch(result, track)
        val artistScore = scoreArtistMatch(result, track)
        val durationScore = scoreDurationMatch(result, track)
        val syncScore = scoreSyncQuality(result)
        val providerScore = scoreProviderTrust(result.providerName)

        var confidence = (titleScore * 0.25f) + (artistScore * 0.25f) +
                (durationScore * 0.2f) + (syncScore * 0.2f) +
                (providerScore * 0.1f)

        // Bonus: if we have synced lyrics with good sync quality, bump confidence
        if (result.isSynced && syncScore > 0.8f) {
            confidence = (confidence + 0.05f).coerceAtMost(1.0f)
        }

        // Penalty: if result looks instrumental but we expected lyrics
        if (result.isInstrumental) {
            confidence = confidence.coerceAtMost(0.4f)
        }

        Log.d(TAG, String.format(Locale.US, "Score: %.3f [title=%.2f, artist=%.2f, dur=%.2f, sync=%.2f, prov=%.2f] for %s",
            confidence, titleScore, artistScore, durationScore, syncScore, providerScore,
            result.providerName
        ))

        return confidence
    }

    private fun scoreTitleMatch(result: LyricsResult, track: TrackMetadata): Float {
        val matchedTitle = result.matchedTitle
        if (matchedTitle.isNullOrEmpty()) {
            // Provider didn't return matched title — give partial credit if it has lyrics
            return if (result.hasLyrics()) 0.5f else 0f
        }
        return LyricsNormalizer.similarity(track.cleanedTitle, matchedTitle)
    }

    private fun scoreArtistMatch(result: LyricsResult, track: TrackMetadata): Float {
        val matchedArtist = result.matchedArtist
        if (matchedArtist.isNullOrEmpty()) {
            // Provider didn't return matched artist — give partial credit
            return if (result.hasLyrics()) 0.5f else 0f
        }
        return LyricsNormalizer.similarity(track.cleanedArtist, matchedArtist)
    }

    private fun scoreDurationMatch(result: LyricsResult, track: TrackMetadata): Float {
        val matchedDuration = result.matchedDuration // in seconds
        if (matchedDuration <= 0 || track.durationMs <= 0) {
            // No duration info — neutral score
            return 0.5f
        }

        val trackDurationSec = track.durationMs / 1000f
        val diff = abs(trackDurationSec - matchedDuration)

        return when {
            diff <= 2 -> 1.0f  // Within 2 seconds — perfect
            diff <= 5 -> 0.9f  // Within 5 seconds — great
            diff <= 10 -> 0.7f // Within 10 seconds — acceptable
            diff <= 30 -> 0.4f // Within 30 seconds — suspicious
            else -> 0.1f       // > 30 seconds — likely wrong track
        }
    }

    private fun scoreSyncQuality(result: LyricsResult): Float {
        if (result.isSynced && result.syncedLyrics != null) {
            val lineCount = countSyncedLines(result.syncedLyrics!!)
            return when {
                lineCount >= 20 -> 1.0f // Good density
                lineCount >= 10 -> 0.8f // Moderate
                lineCount >= 5 -> 0.6f  // Sparse
                lineCount > 0 -> 0.4f   // Very sparse
                else -> 0.0f
            }
        }

        if (!result.plainLyrics.isNullOrEmpty()) {
            val lines = result.plainLyrics!!.lines().size
            return when {
                lines >= 10 -> 0.5f // Plain lyrics are less valuable
                lines >= 5 -> 0.3f
                else -> 0.2f
            }
        }

        return 0f
    }

    private fun scoreProviderTrust(providerName: String?): Float {
        return when (providerName) {
            "LRCLIB" -> 0.95f
            "SyncLRC" -> 0.95f
            "EmbeddedID3" -> 0.90f
            "LocalLrc" -> 0.90f
            "YouTube Music" -> 0.85f
            "OVH" -> 0.70f
            "SomeRandom" -> 0.60f
            else -> 0.50f
        }
    }

    private fun countSyncedLines(lyrics: String): Int {
        if (lyrics.isEmpty()) return 0
        return lyrics.lines().count { it.matches("^\\[\\d{2}:\\d{2}\\.\\d{2,3}].*".toRegex()) }
    }

    enum class Action {
        ACCEPT,            // Show immediately, stop searching
        SHOW_AND_CONTINUE, // Show but keep searching for better match
        REJECT             // Don't show, try next provider
    }
}
