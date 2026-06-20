package com.codetrio.spatialflow.data.lyrics.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.LruCache
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.google.gson.Gson
import androidx.core.content.edit

/**
 * Three-tier cache for lyrics results:
 * 1. Memory (LruCache) — instant
 * 2. SharedPreferences (serialized JSON) — fast disk
 * 3. Negative cache with TTL — prevents hammering providers for missing lyrics
 */
class LyricsCacheManager(context: Context) {

    private val memoryCache: LruCache<String, LyricsResult> = LruCache(MEMORY_CACHE_SIZE)
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val negativePrefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREF_NEGATIVE, Context.MODE_PRIVATE)
    private val gson: Gson = Gson()

    companion object {
        private const val TAG = "LyricsCacheManager"
        private const val PREF_NAME = "lyrics_cache_v2"
        private const val PREF_NEGATIVE = "lyrics_negative_cache"
        private const val MEMORY_CACHE_SIZE = 50
        private const val NEGATIVE_CACHE_TTL_MS = 30L * 60L * 1000L // 30 minutes
    }

    /**
     * Get cached lyrics result for a track.
     * Returns null if not cached.
     */
    fun get(track: TrackMetadata): LyricsResult? {
        val key = track.getCacheKey()

        // Check memory first
        val memResult = memoryCache.get(key)
        if (memResult != null) {
            Log.d(TAG, "Memory cache hit for: $key")
            return memResult
        }

        // Check disk
        val json = prefs.getString(key, null)
        if (json != null) {
            try {
                val diskResult = gson.fromJson(json, LyricsResult::class.java)
                if (diskResult != null && diskResult.hasLyrics()) {
                    memoryCache.put(key, diskResult) // Promote to memory
                    Log.d(TAG, "Disk cache hit for: $key")
                    return diskResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deserialize cache: ${e.message}")
                prefs.edit { remove(key) } // Clean corrupted entry
            }
        }

        return null
    }

    /**
     * Store a lyrics result in cache (both memory and disk).
     */
    fun put(track: TrackMetadata, result: LyricsResult?) {
        val key = track.getCacheKey()

        if (result != null && result.hasLyrics()) {
            memoryCache.put(key, result)
            try {
                val json = gson.toJson(result)
                prefs.edit { putString(key, json) }
                Log.d(TAG, "Cached result for: $key (provider=${result.providerName})")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to serialize cache: ${e.message}")
            }

            // Clear any negative cache for this key
            clearNegativeCache(key)
        }
    }

    /**
     * Check if negative cache is active for this track (search already failed
     * recently).
     */
    fun isNegativeCacheActive(track: TrackMetadata): Boolean {
        val key = track.getCacheKey()
        val timestamp = negativePrefs.getLong(key, 0)
        if (timestamp <= 0) return false

        val age = System.currentTimeMillis() - timestamp
        if (age > NEGATIVE_CACHE_TTL_MS) {
            // Expired — remove and allow re-search
            negativePrefs.edit { remove(key) }
            Log.d(TAG, "Negative cache expired for: $key")
            return false
        }

        Log.d(TAG, "Negative cache active for: $key (age=${age / 1000}s)")
        return true
    }

    /**
     * Mark a track as "no lyrics found" in negative cache.
     */
    fun putNegative(track: TrackMetadata) {
        val key = track.getCacheKey()
        negativePrefs.edit { putLong(key, System.currentTimeMillis()) }
        Log.d(TAG, "Negative cache set for: $key")
    }

    /**
     * Clear negative cache for a specific track (used by Retry).
     */
    fun clearNegativeCache(key: String) {
        negativePrefs.edit { remove(key) }
    }

    /**
     * Evict a specific track from cache (for forcing re-fetch).
     */
    fun evict(track: TrackMetadata) {
        val key = track.getCacheKey()
        memoryCache.remove(key)
        prefs.edit { remove(key) }
        negativePrefs.edit { remove(key) }
        Log.d(TAG, "Evicted cache for: $key")
    }
}
