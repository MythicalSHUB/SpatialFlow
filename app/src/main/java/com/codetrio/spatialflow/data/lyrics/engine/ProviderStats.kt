package com.codetrio.spatialflow.data.lyrics.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider
import java.util.ArrayList
import java.util.Locale
import androidx.core.content.edit
import kotlin.math.abs

/**
 * Tracks per-provider success rates and dynamically reorders providers.
 * Language-aware: different ordering for Hindi vs English vs other languages.
 */
class ProviderStats(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ProviderStats"
        private const val PREF_NAME = "lyrics_provider_stats"
    }

    /**
     * Record a successful lyrics fetch.
     */
    fun recordSuccess(providerName: String, language: String, confidence: Float) {
        val key = "${providerName}_$language"
        val total = prefs.getInt("${key}_total", 0) + 1
        val success = prefs.getInt("${key}_success", 0) + 1
        val prevAvgConf = prefs.getFloat("${key}_avgconf", 0f)
        val avgConf = ((prevAvgConf * (success - 1)) + confidence) / success

        prefs.edit {
            putInt("${key}_total", total)
                .putInt("${key}_success", success)
                .putFloat("${key}_avgconf", avgConf)
        }

        Log.d(TAG, "$providerName [$language] success: $success/$total (avg conf=${String.format(Locale.US, "%.2f", avgConf)})")
    }

    /**
     * Record a failed lyrics fetch.
     */
    fun recordFailure(providerName: String, language: String) {
        val key = "${providerName}_$language"
        val total = prefs.getInt("${key}_total", 0) + 1
        prefs.edit { putInt("${key}_total", total) }
    }

    /**
     * Get the success rate for a provider + language combination.
     */
    fun getSuccessRate(providerName: String, language: String): Float {
        val key = "${providerName}_$language"
        val total = prefs.getInt("${key}_total", 0)
        val success = prefs.getInt("${key}_success", 0)
        if (total == 0) return 0.5f // No data — neutral
        return success.toFloat() / total
    }

    /**
     * Get providers ordered by success rate for the given language.
     * Providers with higher success rates come first.
     */
    fun getOrderedProviders(providers: List<LyricsProvider>, language: String): List<LyricsProvider> {
        val ordered = ArrayList(providers)

        ordered.sortWith { a, b ->
            val rateA = getSuccessRate(a.getName(), language)
            val rateB = getSuccessRate(b.getName(), language)

            // If rates are similar, fall back to static priority
            if (abs(rateA - rateB) < 0.1f) {
                a.getPriority().compareTo(b.getPriority())
            } else {
                // Higher rate = higher priority (sort descending)
                rateB.compareTo(rateA)
            }
        }

        return ordered
    }

    /**
     * Reset all stats (for debugging/testing).
     */
    fun resetAll() {
        prefs.edit { clear() }
        Log.d(TAG, "All provider stats reset")
    }
}
