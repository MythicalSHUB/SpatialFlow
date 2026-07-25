package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Base64
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.KugouApi
import com.codetrio.spatialflow.data.lyrics.KugouLegacyApi
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import kotlin.math.abs

class KugouProvider(
    private val api: KugouApi,
    private val legacyApi: KugouLegacyApi
) : LyricsProvider {
    companion object {
        private const val TAG = "KugouProvider"
    }

    override fun getName(): String = "KuGou"

    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        // Try the new API first (works outside China)
        try {
            val query = "${track.cleanedTitle} ${track.cleanedArtist}"
            val searchUrl = "https://msearchcdn.kugou.com/api/v3/search/song"
            
            val searchResponse = api.search(url = searchUrl, keyword = query).execute()
            if (searchResponse.isSuccessful && searchResponse.body() != null) {
                val bodyObj = searchResponse.body()!!.asJsonObject
                if (bodyObj.has("status") && bodyObj.get("status").asInt == 1) {
                    val dataObj = bodyObj.getAsJsonObject("data")
                    if (dataObj != null && dataObj.has("info") && dataObj.get("info").isJsonArray) {
                        val infoArray = dataObj.getAsJsonArray("info")
                        if (infoArray.size() > 0) {
                            // Find best candidate based on duration match
                            var bestCandidate = infoArray.get(0).asJsonObject
                            var minDiff = Long.MAX_VALUE
                            val trackDurationS = track.durationMs / 1000

                            for (i in 0 until infoArray.size()) {
                                val cand = infoArray.get(i).asJsonObject
                                val candDuration = if (cand.has("duration")) cand.get("duration").asLong else 0L
                                val diff = abs(candDuration - trackDurationS)
                                if (diff < minDiff) {
                                    minDiff = diff
                                    bestCandidate = cand
                                }
                            }

                            val hash = if (bestCandidate.has("hash")) bestCandidate.get("hash").asString else null
                            val albumId = if (bestCandidate.has("album_id")) bestCandidate.get("album_id").asString else null

                            if (!hash.isNullOrBlank()) {
                                val getdataResponse = api.getSongData(hash = hash, albumId = albumId).execute()
                                if (getdataResponse.isSuccessful && getdataResponse.body() != null) {
                                    val getdataObj = getdataResponse.body()!!.asJsonObject
                                    if (getdataObj.has("status") && getdataObj.get("status").asInt == 1) {
                                        val getdataData = getdataObj.getAsJsonObject("data")
                                        if (getdataData != null && getdataData.has("lyrics") && !getdataData.get("lyrics").isJsonNull) {
                                            val lyricsStr = getdataData.get("lyrics").asString.trim()
                                            if (lyricsStr.isNotBlank()) {
                                                val result = LyricsResult(
                                                    providerName = getName(),
                                                    matchedTitle = if (bestCandidate.has("songname")) bestCandidate.get("songname").asString else track.cleanedTitle,
                                                    matchedArtist = if (bestCandidate.has("singername")) bestCandidate.get("singername").asString else track.cleanedArtist
                                                )
                                                if (lyricsStr.contains("[00:")) {
                                                    result.setSyncedLyrics(lyricsStr)
                                                } else {
                                                    result.setPlainLyrics(lyricsStr)
                                                }
                                                return result
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search using new API failed: ${e.message}")
        }

        // Fallback to legacy candidates API
        try {
            val query = "${track.cleanedTitle} ${track.cleanedArtist}"
            val durationMs = track.durationMs

            val searchResponse = legacyApi.search(keyword = query, durationMs = durationMs).execute()
            if (searchResponse.isSuccessful && searchResponse.body() != null) {
                val searchObj = searchResponse.body()!!.asJsonObject
                if (searchObj.has("candidates") && searchObj.get("candidates").isJsonArray) {
                    val candidates = searchObj.getAsJsonArray("candidates")
                    if (candidates.size() > 0) {
                        // Find the best match based on duration
                        var bestCandidate = candidates.get(0).asJsonObject
                        var minDiff = Long.MAX_VALUE
                        
                        for (i in 0 until candidates.size()) {
                            val cand = candidates.get(i).asJsonObject
                            val candDuration = if (cand.has("duration")) cand.get("duration").asLong else 0L
                            val diff = abs(candDuration - durationMs)
                            if (diff < minDiff) {
                                minDiff = diff
                                bestCandidate = cand
                            }
                        }

                        val id = if (bestCandidate.has("id")) bestCandidate.get("id").asString else null
                        val accessKey = if (bestCandidate.has("accesskey")) bestCandidate.get("accesskey").asString else null

                        if (id != null && accessKey != null) {
                            val downloadResponse = legacyApi.download(id = id, accesskey = accessKey).execute()
                            if (downloadResponse.isSuccessful && downloadResponse.body() != null) {
                                val downloadObj = downloadResponse.body()!!.asJsonObject
                                if (downloadObj.has("content") && !downloadObj.get("content").isJsonNull) {
                                    val base64Content = downloadObj.get("content").asString
                                    val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
                                    val lyricsStr = String(decodedBytes, Charsets.UTF_8).trim()

                                    if (lyricsStr.isNotBlank()) {
                                        val result = LyricsResult(
                                            providerName = getName(),
                                            matchedTitle = if (bestCandidate.has("song")) bestCandidate.get("song").asString else track.cleanedTitle,
                                            matchedArtist = if (bestCandidate.has("singer")) bestCandidate.get("singer").asString else track.cleanedArtist
                                        )
                                        if (lyricsStr.contains("[00:")) {
                                            result.setSyncedLyrics(lyricsStr)
                                        } else {
                                            result.setPlainLyrics(lyricsStr)
                                        }
                                        return result
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search using legacy API failed: ${e.message}")
        }
        return null
    }
}
