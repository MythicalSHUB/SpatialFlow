package com.codetrio.spatialflow.data.lyrics.providers.paxsenix

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixApi
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider
import com.google.gson.JsonParser
import java.util.Locale
import kotlin.math.abs

class PaxsenixAppleMusicProvider(private val api: PaxsenixApi) : LyricsProvider {
    companion object {
        private const val TAG = "PaxsenixAppleMusic"
        private const val AMP_TOKEN = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ" +
                ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3" +
                "MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
                ".4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"
        private const val AMP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
    }

    override fun getName(): String = "Apple Music"

    override fun getPriority(): Int = 2 // Very high priority, usually excellent synced lyrics

    override fun search(track: TrackMetadata): LyricsResult? {
        try {
            val durationMs = if (track.durationMs > 360000) track.durationMs else track.durationMs * 1000L
            val query = "${track.cleanedTitle} ${track.cleanedArtist}"
            val country = Locale.getDefault().country
            val storefront = if (country.length == 2) country.lowercase(Locale.ROOT) else "us"

            Log.d(TAG, "Searching AMP catalog for: $query in $storefront")

            val searchResponse = api.searchAppleMusic(
                storefront = storefront,
                auth = "Bearer $AMP_TOKEN",
                origin = "https://music.apple.com",
                referer = "https://music.apple.com/",
                userAgent = AMP_USER_AGENT,
                term = query
            ).execute()

            if (!searchResponse.isSuccessful || searchResponse.body() == null) {
                Log.w(TAG, "AMP search failed with code ${searchResponse.code()}")
                return null
            }

            val bodyObj = searchResponse.body()!!.asJsonObject
            val results = bodyObj.getAsJsonObject("results")
            if (results == null || !results.has("songs")) return null
            
            val songs = results.getAsJsonObject("songs").getAsJsonArray("data")
            if (songs == null || songs.size() == 0) return null

            var bestId: String? = null
            var bestScore = -1
            var bestName = ""
            var bestArtist = ""

            for (i in 0 until songs.size()) {
                val songObj = songs.get(i).asJsonObject
                val attrs = songObj.getAsJsonObject("attributes")
                val songId = songObj.get("id").asString
                val name = attrs.get("name").asString
                val artistName = attrs.get("artistName").asString
                val dur = if (attrs.has("durationInMillis")) attrs.get("durationInMillis").asLong else 0L

                var score = 0
                if (name.equals(track.cleanedTitle, ignoreCase = true)) score += 20
                else if (name.contains(track.cleanedTitle, ignoreCase = true) || track.cleanedTitle.contains(name, ignoreCase = true)) score += 10
                
                if (artistName.equals(track.cleanedArtist, ignoreCase = true)) score += 15
                else if (artistName.contains(track.cleanedArtist, ignoreCase = true) || track.cleanedArtist.contains(artistName, ignoreCase = true)) score += 5
                
                if (durationMs > 0 && dur > 0) {
                    val diff = abs(dur - durationMs)
                    if (diff < 3000) score += 10
                    else if (diff < 10000) score += 5
                }

                if (score > bestScore) {
                    bestScore = score
                    bestId = songId
                    bestName = name
                    bestArtist = artistName
                }
            }

            Log.d(TAG, "Best AMP match: $bestName by $bestArtist (ID: $bestId, Score: $bestScore)")

            if (bestId == null || bestScore < 12) {
                Log.w(TAG, "Rejecting match - score $bestScore < 12")
                return null
            }

            // Try TTML first
            val lyricsResponseTtml = api.getAppleMusicLyrics(bestId, true).execute()
            if (lyricsResponseTtml.isSuccessful && lyricsResponseTtml.body() != null) {
                val rawBody = lyricsResponseTtml.body()!!.string().trim()
                var ttmlContent: String? = null

                if (rawBody.startsWith("<tt") || rawBody.startsWith("<?xml")) {
                    ttmlContent = rawBody
                } else {
                    try {
                        val json = JsonParser.parseString(rawBody).asJsonObject
                        if (json.has("content")) {
                            val content = json.get("content").asString
                            if (content.contains("<tt") || content.contains("<?xml")) {
                                ttmlContent = content
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse TTML JSON wrapper")
                    }
                }

                if (!ttmlContent.isNullOrBlank()) {
                    Log.d(TAG, "SUCCESS from Apple Music (TTML)")
                    val result = LyricsResult(
                        providerName = getName(),
                        matchedTitle = bestName,
                        matchedArtist = bestArtist
                    )
                    result.setSyncedLyrics(ttmlContent)
                    // Apple Music TTML almost always has word-level syncing
                    result.setWordByWord(true) 
                    return result
                }
            }

            Log.w(TAG, "TTML failed or empty, falling back...")
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
        }
        return null
    }
}
