package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LrcLibApi
import com.codetrio.spatialflow.data.lyrics.LrcLibResponse
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import kotlin.math.abs

/**
 * LrcLib provider — primary synced lyrics source.
 * Two-pass strategy:
 * 1. Direct GET with exact title/artist/duration
 * 2. If failed → search API with fuzzy query
 */
class LrcLibProvider(private val api: LrcLibApi) : LyricsProvider {

    companion object {
        private const val TAG = "LrcLibProvider"
    }

    override fun getName(): String = "LRCLIB"

    override fun getPriority(): Int = 1 // Highest priority — best synced lyrics source

    override fun search(track: TrackMetadata): LyricsResult? {
        // Pass 1: Direct GET with exact parameters
        val directResult = tryDirectGet(track)
        if (directResult != null && directResult.hasLyrics()) {
            Log.d(TAG, "Direct GET success for: ${track.cleanedTitle}")
            return directResult
        }

        // Pass 2: Search API with fuzzy query
        val searchResult = trySearchApi(track)
        if (searchResult != null && searchResult.hasLyrics()) {
            Log.d(TAG, "Search API success for: ${track.cleanedTitle}")
            return searchResult
        }

        // Pass 3: Search with title only (no artist)
        val titleOnlyResult = trySearchTitleOnly(track)
        if (titleOnlyResult != null && titleOnlyResult.hasLyrics()) {
            Log.d(TAG, "Title-only search success for: ${track.cleanedTitle}")
            return titleOnlyResult
        }

        Log.d(TAG, "No results for: ${track.cleanedTitle}")
        return null
    }

    private fun tryDirectGet(track: TrackMetadata): LyricsResult? {
        try {
            val durationSec = track.durationMs / 1000f
            var response = api.getLyrics(
                track.cleanedTitle,
                track.cleanedArtist,
                if (track.album.isEmpty()) null else track.album,
                durationSec
            ).execute()

            if (!response.isSuccessful || response.body() == null) {
                Log.d(TAG, "LRCLIB direct GET failed. Retrying without duration and album...")
                response = api.getLyrics(
                    track.cleanedTitle,
                    track.cleanedArtist,
                    null,
                    null
                ).execute()
            }

            if (response.isSuccessful && response.body() != null) {
                return convertResponse(response.body()!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct GET failed: ${e.message}")
        }
        return null
    }

    private fun trySearchApi(track: TrackMetadata): LyricsResult? {
        try {
            val query = "${track.cleanedArtist} ${track.cleanedTitle}"
            val response = api.searchLyrics(query.trim()).execute()

            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                // Find best match from search results
                return findBestMatch(response.body()!!, track)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search API failed: ${e.message}")
        }
        return null
    }

    private fun trySearchTitleOnly(track: TrackMetadata): LyricsResult? {
        try {
            val response = api.searchLyrics(track.cleanedTitle).execute()

            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                return findBestMatch(response.body()!!, track)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Title-only search failed: ${e.message}")
        }
        return null
    }

    private fun findBestMatch(results: List<LrcLibResponse>, track: TrackMetadata): LyricsResult? {
        var bestSynced: LyricsResult? = null
        var bestPlain: LyricsResult? = null

        for (item in results) {
            // Skip if instrumental with no lyrics
            if (item.instrumental && item.syncedLyrics.isNullOrEmpty() && item.plainLyrics.isNullOrEmpty()) {
                continue
            }

            val result = convertResponse(item) ?: continue

            // Duration check — reject if > 60 seconds off (or > 120 seconds off if title similarity is very high)
            if (track.durationMs > 0 && item.duration > 0) {
                val diff = abs((track.durationMs / 1000f) - item.duration)
                if (diff > 60) {
                    val titleSim = com.codetrio.spatialflow.data.lyrics.LyricsNormalizer.similarity(track.cleanedTitle, item.name ?: "")
                    if (titleSim < 0.9f || diff > 120) {
                        continue
                    }
                }
            }

            if (result.isSynced && bestSynced == null) {
                bestSynced = result
            } else if (!result.isSynced && bestPlain == null) {
                bestPlain = result
            }
        }

        // Prefer synced over plain
        return bestSynced ?: bestPlain
    }

    private fun convertResponse(response: LrcLibResponse?): LyricsResult? {
        if (response == null) return null

        val result = LyricsResult(
            providerName = getName(),
            matchedTitle = response.name,
            matchedArtist = response.artistName,
            matchedAlbum = response.albumName,
            matchedDuration = response.duration,
            isInstrumental = response.instrumental
        )

        if (!response.syncedLyrics.isNullOrEmpty()) {
            result.setSyncedLyrics(response.syncedLyrics)
        }
        if (!response.plainLyrics.isNullOrEmpty()) {
            result.plainLyrics = response.plainLyrics
        }

        if (!result.hasLyrics() && !response.instrumental) {
            return null
        }

        return result
    }
}
