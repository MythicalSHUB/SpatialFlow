package com.codetrio.spatialflow.data.lyrics.engine

import android.util.LruCache
import com.codetrio.spatialflow.data.lyrics.LyricsResult

/**
 * Orchestrator and caching helper for the lyrics engines.
 * Holds dual-level LRU caches for immediate lookup of lyrics and providers results.
 */
object LyricsHelper {
    private const val MAX_CACHE_SIZE = 50

    // Stores lists of LyricsResult outputs mapped by a key derived from track metadata
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    // Stores finalized plain/synced lyrics string outputs mapped by a key derived from title + artist
    private val singleLyricsCache = LruCache<String, String>(MAX_CACHE_SIZE)

    /**
     * Retrieve a cached list of LyricsResult for a given key.
     */
    fun get(key: String): List<LyricsResult>? {
        return cache.get(key)
    }

    /**
     * Store a list of LyricsResult in cache.
     */
    fun put(key: String, results: List<LyricsResult>) {
        cache.put(key, results)
    }

    /**
     * Retrieve a cached finalized lyrics string for a track key.
     */
    fun getSingle(title: String, artist: String): String? {
        val key = getSingleCacheKey(title, artist)
        return singleLyricsCache.get(key)
    }

    /**
     * Store a finalized lyrics string in cache.
     */
    fun putSingle(title: String, artist: String, lyrics: String) {
        val key = getSingleCacheKey(title, artist)
        singleLyricsCache.put(key, lyrics)
    }

    /**
     * Clear all caches.
     */
    fun clear() {
        cache.evictAll()
        singleLyricsCache.evictAll()
    }

    private fun getSingleCacheKey(title: String, artist: String): String {
        return "$title - $artist".lowercase().trim()
    }
}
