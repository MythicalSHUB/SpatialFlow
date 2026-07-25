package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.BetterLyricsApi
import com.codetrio.spatialflow.data.lyrics.BetterLyricsResponse
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import retrofit2.Call

class BetterLyricsProvider(private val api: BetterLyricsApi) : LyricsProvider {
    companion object {
        private const val TAG = "BetterLyricsProvider"
    }

    override fun getName(): String = "BetterLyrics"

    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        val queries = listOf(
            Pair(track.cleanedArtist, track.cleanedTitle),
            Pair(track.rawArtist, track.rawTitle)
        ).distinct()

        val durationSec = (track.durationMs / 1000).toInt()
        val album = track.album.takeIf { it.isNotBlank() }

        for ((artist, title) in queries) {
            if (artist.isBlank() || title.isBlank()) continue

            val calls: List<Pair<String, Call<BetterLyricsResponse>>> = listOf(
                "Primary" to api.getLyrics(artist = artist, song = title, album = album, duration = durationSec.takeIf { it > 0 }),
                "Kugou Fallback" to api.getKugouLyrics(artist = artist, song = title, album = album, duration = durationSec.takeIf { it > 0 })
            )

            for ((sourceName, call) in calls) {
                try {
                    val response = call.execute()
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val ttml = body.ttml
                        val lyrics = body.lyrics

                        val detailedSource = when {
                            !ttml.isNullOrBlank() && (ttml.contains("itunes") || ttml.contains("apple.com") || ttml.contains("iTunesMetadata")) -> "Apple Music"
                            !lyrics.isNullOrBlank() && (lyrics.contains("QQ Music") || lyrics.contains("[by:QQ Music]") || lyrics.contains("[by: QQ Music]")) -> "QQ Music"
                            !lyrics.isNullOrBlank() && (lyrics.contains("Kugou") || lyrics.contains("[by:Kugou]") || lyrics.contains("[by: Kugou]")) -> "Kugou"
                            !ttml.isNullOrBlank() -> "Apple Music"
                            else -> sourceName
                        }

                        val providerDisplayName = "BetterLyrics ($detailedSource)"

                        val result = LyricsResult(
                            providerName = providerDisplayName,
                            matchedTitle = title,
                            matchedArtist = artist
                        )

                        if (!ttml.isNullOrBlank()) {
                            result.setSyncedLyrics(ttml)
                            result.setWordByWord(true)
                            return result
                        } else if (!lyrics.isNullOrBlank()) {
                            if (lyrics.contains("[00:")) {
                                result.setSyncedLyrics(lyrics)
                            } else {
                                result.setPlainLyrics(lyrics)
                            }
                            return result
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Search ($sourceName) failed for '$title' by '$artist': ${e.message}")
                }
            }
        }
        return null
    }
}
