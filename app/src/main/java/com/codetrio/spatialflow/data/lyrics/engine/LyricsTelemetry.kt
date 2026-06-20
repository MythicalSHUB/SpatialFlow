package com.codetrio.spatialflow.data.lyrics.engine

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import java.util.Locale

/**
 * Logs per-track search telemetry for debugging lyrics failures.
 * Output goes to Logcat with tag "LyricsTelemetry".
 */
class LyricsTelemetry {

    companion object {
        private const val TAG = "LyricsTelemetry"
    }

    /**
     * Log the start of a lyrics search.
     */
    fun logSearchStart(track: TrackMetadata) {
        Log.i(TAG, "═══════════════════════════════════════════════════")
        Log.i(TAG, "LYRICS SEARCH START")
        Log.i(TAG, "  Title (raw):     ${track.rawTitle}")
        Log.i(TAG, "  Title (cleaned): ${track.cleanedTitle}")
        Log.i(TAG, "  Artist (raw):    ${track.rawArtist}")
        Log.i(TAG, "  Artist (cleaned):${track.cleanedArtist}")
        Log.i(TAG, "  Duration:        ${track.durationMs}ms")
        Log.i(TAG, "  Version:         ${track.version}")
        Log.i(TAG, "  Language:        ${track.detectedLanguage}")
        Log.i(TAG, "  File:            ${track.filePath}")
        Log.i(TAG, "───────────────────────────────────────────────────")
    }

    /**
     * Log the queries that will be tried.
     */
    fun logQueries(queries: List<Array<String>>) {
        Log.i(TAG, "  Generated ${queries.size} search queries:")
        for (i in queries.indices) {
            val q = queries[i]
            Log.i(TAG, "    ${i + 1}. artist='${q[0]}' title='${q[1]}'")
        }
    }

    /**
     * Log cache status.
     */
    fun logCacheStatus(status: String, details: String?) {
        Log.i(TAG, "  Cache: $status" + if (details != null) " — $details" else "")
    }

    /**
     * Log the final selected result.
     */
    fun logResult(result: LyricsResult?, decision: String) {
        if (result != null) {
            Log.i(TAG, "───────────────────────────────────────────────────")
            Log.i(TAG, "RESULT: $decision")
            Log.i(TAG, "  Provider:   ${result.providerName}")
            Log.i(TAG, "  Confidence: ${String.format(Locale.US, "%.3f", result.confidence)}")
            Log.i(TAG, "  Synced:     ${result.isSynced}")
            Log.i(TAG, "  Title:      ${result.matchedTitle}")
            Log.i(TAG, "  Artist:     ${result.matchedArtist}")
            Log.i(TAG, "  Duration:   ${result.matchedDuration}s")
        } else {
            Log.i(TAG, "───────────────────────────────────────────────────")
            Log.i(TAG, "RESULT: $decision — No lyrics found")
        }
        Log.i(TAG, "═══════════════════════════════════════════════════")
    }

    /**
     * Log a failure with reason.
     */
    fun logFailure(reason: String) {
        Log.w(TAG, "───────────────────────────────────────────────────")
        Log.w(TAG, "SEARCH FAILED: $reason")
        Log.w(TAG, "═══════════════════════════════════════════════════")
    }
}
