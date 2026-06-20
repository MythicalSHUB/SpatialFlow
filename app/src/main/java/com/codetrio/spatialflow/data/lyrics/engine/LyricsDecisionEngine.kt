package com.codetrio.spatialflow.data.lyrics.engine

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.ConfidenceScorer
import com.codetrio.spatialflow.data.lyrics.LyricsResult

/**
 * The "brain" of the lyrics system.
 * Decides what action to take based on current state, cached data, and new
 * results.
 */
class LyricsDecisionEngine(private val scorer: ConfidenceScorer) {

    companion object {
        private const val TAG = "LyricsDecision"
    }

    /**
     * Decide what to do with a new lyrics result.
     */
    fun decide(newResult: LyricsResult?, currentlyShowing: LyricsResult?): Decision {
        if (newResult == null || !newResult.hasLyrics()) {
            if (currentlyShowing != null && currentlyShowing.hasLyrics()) {
                return Decision.KEEP_CURRENT // Don't replace good lyrics with nothing
            }
            return Decision.NO_RESULT
        }

        val confidence = newResult.confidence

        // Check for instrumental
        if (newResult.isInstrumental && !newResult.hasLyrics()) {
            return Decision.MARK_INSTRUMENTAL
        }

        // No current lyrics → any valid result is better than nothing
        if (currentlyShowing == null || !currentlyShowing.hasLyrics()) {
            return when {
                confidence >= ConfidenceScorer.THRESHOLD_ACCEPT -> Decision.ACCEPT
                confidence >= ConfidenceScorer.THRESHOLD_SHOW -> Decision.SHOW_AND_CONTINUE
                else -> Decision.REJECT
            }
        }

        // We have current lyrics — should we replace them?
        val currentConfidence = currentlyShowing.confidence

        // Upgrade: synced → word-by-word (highest priority upgrade)
        if (!currentlyShowing.isWordByWord && newResult.isWordByWord && confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
            Log.d(TAG, "Upgrading synced → word-by-word (confidence=$confidence)")
            return Decision.REPLACE_WITH_WORD_BY_WORD
        }

        // Upgrade: unsynced → synced
        if (!currentlyShowing.isSynced && newResult.isSynced && confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
            Log.d(TAG, "Upgrading unsynced → synced (confidence=$confidence)")
            return Decision.REPLACE_UNSYNCED
        }

        // Higher confidence replacement
        if (confidence > currentConfidence + 0.1f) {
            // Protect against downgrading from synced to unsynced
            if (currentlyShowing.isSynced && !newResult.isSynced) {
                if (currentConfidence >= 0.45f) {
                    return Decision.KEEP_CURRENT
                }
            }
            Log.d(TAG, "Upgrading confidence: $currentConfidence → $confidence")
            return Decision.ACCEPT
        }

        // Current is fine, keep it
        return Decision.KEEP_CURRENT
    }

    /**
     * Decide whether to initiate a fetch, based on cache state.
     */
    fun decideFetch(cachedResult: LyricsResult?, isNegativeCacheExpired: Boolean): FetchDecision {
        if (cachedResult != null && cachedResult.hasLyrics()) {
            // Word-by-word + high confidence = best possible, use cache
            if (cachedResult.isWordByWord && cachedResult.confidence >= ConfidenceScorer.THRESHOLD_ACCEPT) {
                return FetchDecision.USE_CACHE
            }
            // Standard synced but not word-by-word → show cache, search for word-by-word upgrade
            if (cachedResult.isSynced && !cachedResult.isWordByWord && cachedResult.confidence >= ConfidenceScorer.THRESHOLD_ACCEPT) {
                return FetchDecision.USE_CACHE_AND_SEARCH_BACKGROUND
            }
            if (!cachedResult.isSynced) {
                return FetchDecision.USE_CACHE_AND_SEARCH_BACKGROUND
            }
            return FetchDecision.USE_CACHE
        }

        if (cachedResult != null && !cachedResult.hasLyrics() && !isNegativeCacheExpired) {
            // Negative cache still valid — don't re-search yet
            return FetchDecision.SKIP_NEGATIVE_CACHE
        }

        return FetchDecision.FETCH
    }

    /**
     * Decisions for what to do with a new result.
     */
    enum class Decision {
        ACCEPT,                     // Show immediately, stop searching
        SHOW_AND_CONTINUE,          // Show but keep searching for better match
        REJECT,                     // Don't show, try next
        REPLACE_UNSYNCED,           // Replace currently shown unsynced with synced
        REPLACE_WITH_WORD_BY_WORD,  // Replace synced with word-by-word (highest upgrade)
        KEEP_CURRENT,               // Current lyrics are better, ignore new result
        MARK_INSTRUMENTAL,          // Track is instrumental, stop searching
        NO_RESULT                   // Provider returned nothing
    }

    /**
     * Decisions for whether to initiate a fetch.
     */
    enum class FetchDecision {
        USE_CACHE,                          // Happy path: cache hit
        USE_CACHE_AND_SEARCH_BACKGROUND,    // Show cached, search for upgrade in background
        FETCH,                              // No cache: do a full search
        SKIP_NEGATIVE_CACHE                 // Negative cache active, don't re-search yet
    }
}
