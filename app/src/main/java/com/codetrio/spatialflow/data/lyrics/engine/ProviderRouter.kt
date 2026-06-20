@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.codetrio.spatialflow.data.lyrics.engine

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.ConfidenceScorer
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Dispatches lyrics search to all providers in parallel.
 * Uses an "async race" strategy:
 * - Launch all providers simultaneously
 * - First result with confidence >= 0.85 wins immediately
 * - Else best result after all complete / timeout wins
 */
class ProviderRouter(
    providers: List<LyricsProvider>,
    private val scorer: ConfidenceScorer,
    private val stats: ProviderStats
) {

    private val providers: List<LyricsProvider>
    private val executor: ExecutorService

    companion object {
        private const val TAG = "ProviderRouter"
        private const val TIMEOUT_SECONDS = 12L
    }

    init {
        // Sort providers by dynamic priority (stats-influenced)
        val sortedProviders = ArrayList(providers)
        sortedProviders.sortBy { it.getPriority() }
        this.providers = sortedProviders

        this.executor = Executors.newCachedThreadPool { r ->
            val t = Thread(r, "LyricsProvider")
            t.isDaemon = true
            t
        }
    }

    /**
     * Search all providers in parallel and return the best result.
     * This method blocks until a high-confidence result is found or all providers
     * finish.
     *
     * @param track    Normalized track metadata
     * @param language Detected language for stats tracking
     * @return Best LyricsResult, or null if nothing found
     */
    fun searchAll(
        track: TrackMetadata?,
        language: String,
        cancelOnEarlyWin: Boolean = true,
        onProviderResult: (String, LyricsResult?) -> Unit = { _, _ -> }
    ): LyricsResult? {
        if (providers.isEmpty() || track == null) return null

        Log.d(TAG, "Starting parallel search across ${providers.size} providers for: $track")

        // Get provider order from stats (dynamic reordering)
        val orderedProviders = stats.getOrderedProviders(providers, language)

        val bestResult = AtomicReference<LyricsResult>(null)
        val earlyWin = AtomicBoolean(false)
        val lock = Object()
        val futures = ArrayList<Future<*>>()

        val hasAppleMusicProvider = orderedProviders.any { it.getName().contains("Apple Music", ignoreCase = true) }
        val appleMusicFinished = AtomicBoolean(!hasAppleMusicProvider)

        for (provider in orderedProviders) {
            val future = executor.submit {
                if (earlyWin.get() && cancelOnEarlyWin) {
                    onProviderResult(provider.getName(), null)
                    return@submit
                }

                val isAppleMusic = provider.getName().contains("Apple Music", ignoreCase = true)
                val start = System.currentTimeMillis()
                try {
                    val result = provider.search(track)
                    val elapsed = System.currentTimeMillis() - start

                    if (result != null && result.hasLyrics()) {
                        val confidence = scorer.score(result, track)
                        result.confidence = confidence

                        Log.d(TAG, "${provider.getName()} returned result (confidence=$confidence, synced=${result.isSynced}) in ${elapsed}ms")

                        stats.recordSuccess(provider.getName(), language, confidence)
                        onProviderResult(provider.getName(), result)

                        synchronized(lock) {
                            val current = bestResult.get()

                            if (current == null || isBetterResult(result, confidence, current)) {
                                bestResult.set(result)
                            }

                            if (isAppleMusic) {
                                appleMusicFinished.set(true)
                            }

                            // Early win logic:
                            if (result.isWordByWord && confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                                // If Apple Music provided a high confidence word-by-word, win immediately!
                                if (isAppleMusic) {
                                    earlyWin.set(true)
                                    lock.notifyAll()
                                } else if (appleMusicFinished.get()) {
                                    // If another provider has word-by-word AND Apple Music is already done (and didn't win), we can early win.
                                    earlyWin.set(true)
                                    lock.notifyAll()
                                }
                            } else if (isAppleMusic) {
                                // Apple music finished but wasn't a high confidence word-by-word win.
                                // If we ALREADY have a high confidence word-by-word from someone else, win now!
                                val currentBest = bestResult.get()
                                if (currentBest != null && currentBest.isWordByWord && currentBest.confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                                    earlyWin.set(true)
                                    lock.notifyAll()
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "${provider.getName()} returned no results in ${elapsed}ms")
                        stats.recordFailure(provider.getName(), language)
                        onProviderResult(provider.getName(), null)
                        
                        synchronized(lock) {
                            if (isAppleMusic) {
                                appleMusicFinished.set(true)
                                val currentBest = bestResult.get()
                                if (currentBest != null && currentBest.isWordByWord && currentBest.confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                                    earlyWin.set(true)
                                    lock.notifyAll()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - start
                    Log.w(TAG, "${provider.getName()} failed in ${elapsed}ms: ${e.message}")
                    stats.recordFailure(provider.getName(), language)
                    onProviderResult(provider.getName(), null)
                    
                    synchronized(lock) {
                        if (isAppleMusic) {
                            appleMusicFinished.set(true)
                            val currentBest = bestResult.get()
                            if (currentBest != null && currentBest.isWordByWord && currentBest.confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                                earlyWin.set(true)
                                lock.notifyAll()
                            }
                        }
                    }
                }
            }
            futures.add(future)
        }

        synchronized(lock) {
            val deadline = System.currentTimeMillis() + (TIMEOUT_SECONDS * 1000)
            while (!earlyWin.get() && System.currentTimeMillis() < deadline) {
                var allDone = true
                for (f in futures) {
                    if (!f.isDone) {
                        allDone = false
                        break
                    }
                }
                if (allDone) break

                try {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining > 0) {
                        lock.wait(remaining.coerceAtMost(500))
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        if (cancelOnEarlyWin) {
            for (f in futures) {
                if (!f.isDone) {
                    f.cancel(true)
                }
            }
        }

        val best = bestResult.get()
        if (best != null) {
            Log.d(TAG, "Best result from ${best.providerName} (confidence=${best.confidence}, synced=${best.isSynced})")
        } else {
            Log.d(TAG, "No results from any provider")
        }

        return best
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

        // Same tier: higher confidence wins
        return newConfidence > currentConfidence
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
