@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.codetrio.spatialflow.data.lyrics.engine

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.ConfidenceScorer
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Dispatches lyrics search to all providers.
 * Uses an "async race" strategy with Kotlin Coroutines:
 * - Try top-priority provider first (sequential checking).
 * - If it fails or returns low confidence, query remaining enabled providers concurrently.
 * - First result with confidence >= 0.85 and word-by-word wins immediately.
 * - Else best result after all complete / timeout wins.
 */
class ProviderRouter(
    providers: List<LyricsProvider>,
    private val scorer: ConfidenceScorer,
    private val stats: ProviderStats
) {

    private val providers: List<LyricsProvider>

    companion object {
        private const val TAG = "ProviderRouter"
        private const val TIMEOUT_SECONDS = 12L
    }

    init {
        // Sort providers by dynamic priority (stats-influenced)
        val sortedProviders = ArrayList(providers)
        sortedProviders.sortBy { it.getPriority() }
        this.providers = sortedProviders
    }

    /**
     * Search all providers using coroutines and return the best result.
     *
     * @param track    Normalized track metadata
     * @param language Detected language for stats tracking
     * @return Best LyricsResult, or null if nothing found
     */
    suspend fun searchAll(
        track: TrackMetadata?,
        language: String,
        cancelOnEarlyWin: Boolean = true,
        onProviderResult: (String, LyricsResult?) -> Unit = { _, _ -> }
    ): LyricsResult? = withContext(Dispatchers.IO) {
        if (providers.isEmpty() || track == null) return@withContext null

        Log.d(TAG, "Starting 100% parallel async race across ${providers.size} providers for: $track")

        // Get provider order from stats (dynamic reordering)
        val orderedProviders = stats.getOrderedProviders(providers, language)
        val bestResult = AtomicReference<LyricsResult>(null)

        val resultFromRace = withTimeoutOrNull(TIMEOUT_SECONDS * 1000) {
            supervisorScope {
                val deferreds = orderedProviders.map { provider ->
                    val isTtmlProvider = provider.getName().contains("Apple Music", ignoreCase = true) ||
                            provider.getName().contains("YouLyPlus", ignoreCase = true) ||
                            provider.getName().contains("BetterLyrics", ignoreCase = true)

                    provider to async(Dispatchers.IO) {
                        val start = System.currentTimeMillis()
                        try {
                            val result = provider.search(track)
                            val elapsed = System.currentTimeMillis() - start

                            if (result != null && result.hasLyrics()) {
                                val confidence = scorer.score(result, track)
                                result.confidence = confidence

                                Log.d(TAG, "${provider.getName()} returned result (confidence=$confidence, synced=${result.isSynced}, karaoke=${result.isWordByWord}) in ${elapsed}ms")

                                stats.recordSuccess(provider.getName(), language, confidence)
                                onProviderResult(provider.getName(), result)
                                Triple(provider.getName(), result, isTtmlProvider)
                            } else {
                                Log.d(TAG, "${provider.getName()} returned no results in ${elapsed}ms")
                                stats.recordFailure(provider.getName(), language)
                                onProviderResult(provider.getName(), null)
                                Triple(provider.getName(), null, isTtmlProvider)
                            }
                        } catch (e: Exception) {
                            val elapsed = System.currentTimeMillis() - start
                            Log.w(TAG, "${provider.getName()} failed in ${elapsed}ms: ${e.message}")
                            stats.recordFailure(provider.getName(), language)
                            onProviderResult(provider.getName(), null)
                            Triple(provider.getName(), null, isTtmlProvider)
                        }
                    }
                }

                val pending = deferreds.map { it.second }.toMutableSet()
                var earlyWinner: LyricsResult? = null

                while (pending.isNotEmpty() && earlyWinner == null) {
                    val (completedDeferred, triple) = select<Pair<Deferred<Triple<String, LyricsResult?, Boolean>>, Triple<String, LyricsResult?, Boolean>>> {
                        pending.forEach { deferred ->
                            deferred.onAwait { result -> deferred to result }
                        }
                    }
                    pending.remove(completedDeferred)

                    val (providerName, result, isTtmlProvider) = triple

                    if (result != null) {
                        synchronized(bestResult) {
                            val current = bestResult.get()
                            if (current == null || isBetterResult(result, result.confidence, current)) {
                                bestResult.set(result)
                            }

                            // Early win logic: Karaoke (word-by-word) or high-confidence TTML result wins immediately
                            val shouldEarlyWin = (result.isWordByWord && result.confidence >= ConfidenceScorer.THRESHOLD_SHOW) ||
                                    (isTtmlProvider && result.isSynced && result.confidence >= 0.85f)

                            if (shouldEarlyWin && cancelOnEarlyWin) {
                                Log.d(TAG, "Early win achieved by $providerName in parallel race!")
                                earlyWinner = result
                                pending.forEach { it.cancel() }
                            }
                        }
                    }
                }
                earlyWinner ?: bestResult.get()
            }
        }

        val finalResult = resultFromRace ?: bestResult.get()
        if (finalResult != null) {
            Log.d(TAG, "Best result from ${finalResult.providerName} (confidence=${finalResult.confidence}, synced=${finalResult.isSynced})")
        } else {
            Log.d(TAG, "No results from any provider")
        }

        finalResult
    }

    private fun isBetterResult(newResult: LyricsResult, newConfidence: Float, current: LyricsResult): Boolean {
        val currentConfidence = current.confidence
        
        val isNewAppleMusic = newResult.providerName?.contains("Apple Music", ignoreCase = true) == true
        val isCurrentAppleMusic = current.providerName?.contains("Apple Music", ignoreCase = true) == true

        // ✨ Apple Music Word-by-Word is the absolute gold standard
        if (isNewAppleMusic && newResult.isWordByWord) {
            return true
        }
        if (isCurrentAppleMusic && current.isWordByWord) {
            return false
        }

        // Word-by-word always beats non-word-by-word
        if (newResult.isWordByWord && !current.isWordByWord) {
            return true
        }
        if (!newResult.isWordByWord && current.isWordByWord) {
            return false
        }

        // Synced beats unsynced
        if (newResult.isSynced && !current.isSynced) {
            return true
        }
        if (!newResult.isSynced && current.isSynced) {
            return currentConfidence < 0.45f && newConfidence > currentConfidence + 0.2f
        }

        // Both are synced, or both are unsynced:
        // Prioritize Apple Music if one of them is Apple Music
        if (isNewAppleMusic && !isCurrentAppleMusic) {
            return newConfidence >= currentConfidence - 0.1f // Slightly lower confidence is okay for Apple Music
        }
        if (!isNewAppleMusic && isCurrentAppleMusic) {
            return newConfidence > currentConfidence + 0.1f // Needs significantly higher confidence to beat Apple Music
        }

        // Same tier: higher confidence wins
        return newConfidence > currentConfidence
    }

    fun shutdown() {
        // No-op: Coroutines managed by calling scope
    }
}
