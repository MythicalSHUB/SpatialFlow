package com.codetrio.spatialflow.data.lyrics.providers

import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixLyrics
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * YouLyPlus Multi-Mirror Lyrics Provider.
 * Queries TTML and LRC Karaoke word-by-word endpoints across 5 mirror hosts:
 * 1. https://lyricsplus.binimum.org/
 * 2. https://lyricsplus.prjktla.my.id/
 * 3. https://lyricsplus.prjktla.workers.dev/
 * 4. https://lyricsplus.atomix.one/
 * 5. https://lyricsplus-seven.vercel.app/
 */
class YouLyPlusProvider : LyricsProvider {

    companion object {
        private const val TAG = "YouLyPlusProvider"

        private val BASE_URLS = listOf(
            "https://lyricsplus.binimum.org/",
            "https://lyricsplus.prjktla.my.id/",
            "https://lyricsplus.prjktla.workers.dev/",
            "https://lyricsplus.atomix.one/",
            "https://lyricsplus-seven.vercel.app/"
        )

        private val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override fun getName(): String = "YouLyPlus"
    override fun getPriority(): Int = 2

    override fun search(track: TrackMetadata): LyricsResult? {
        val title = track.cleanedTitle
        val artist = track.cleanedArtist
        if (title.isBlank() || artist.isBlank()) return null

        val durationSec = (track.durationMs / 1000).toInt()
        val album = track.album.takeIf { it.isNotBlank() }

        // 1. Try TTML mirror endpoints first (highest priority word-by-word)
        val ttml = fetchFromMirrors("v1/ttml/get", title, artist, album, durationSec) { body ->
            val trimmed = body.trim()
            when {
                trimmed.startsWith("<") -> trimmed
                else -> {
                    runCatching {
                        val obj = JsonParser.parseString(body).asJsonObject
                        obj.get("ttml")?.asString?.trim()
                    }.getOrNull()?.takeIf { it.startsWith("<") }
                }
            }
        }

        if (!ttml.isNullOrBlank()) {
            val result = LyricsResult(
                providerName = getName(),
                matchedTitle = title,
                matchedArtist = artist
            )
            result.setSyncedLyrics(ttml)
            result.setWordByWord(true)
            Log.d(TAG, "Successfully fetched TTML lyrics for $title - $artist")
            return result
        }

        // 2. Try V2 LRC / Syllable mirrors
        val lrc = fetchFromMirrors("v2/lyrics/get", title, artist, album, durationSec) { body ->
            runCatching {
                parseV2LyricsResponse(body)
            }.getOrNull()
        }

        if (!lrc.isNullOrBlank()) {
            val result = LyricsResult(
                providerName = getName(),
                matchedTitle = title,
                matchedArtist = artist
            )
            val trimmed = lrc.trim()
            if (trimmed.startsWith("<") || trimmed.contains("<tt")) {
                result.setSyncedLyrics(lrc)
                result.setWordByWord(true)
            } else if (trimmed.contains("[")) {
                result.setSyncedLyrics(lrc)
                if (PaxsenixLyrics.isWordByWord(lrc)) {
                    result.setWordByWord(true)
                }
            } else {
                result.setPlainLyrics(lrc)
            }
            Log.d(TAG, "Successfully fetched LRC lyrics for $title - $artist")
            return result
        }

        return null
    }

    private fun fetchFromMirrors(
        path: String,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
        decode: (String) -> String?
    ): String? {
        for (baseUrl in BASE_URLS) {
            try {
                val urlBuilder = (baseUrl + path).toHttpUrlOrNull()?.newBuilder() ?: continue
                urlBuilder.addQueryParameter("title", title)
                urlBuilder.addQueryParameter("artist", artist)
                if (!album.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("album", album)
                }
                if (durationSec > 0) {
                    urlBuilder.addQueryParameter("duration", durationSec.toString())
                }

                val req = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", "SpatialFlow/1.0.0")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val decoded = decode(body)
                            if (!decoded.isNullOrBlank()) {
                                return decoded
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror fetch failed from $baseUrl$path: ${e.message}")
            }
        }
        return null
    }

    private fun parseV2LyricsResponse(jsonStr: String): String? {
        val root = JsonParser.parseString(jsonStr).asJsonObject
        val lyricsArr = root.getAsJsonArray("lyrics") ?: return null
        if (lyricsArr.size() == 0) return null

        val isWordType = root.get("type")?.asString?.equals("Word", ignoreCase = true) == true
        val sb = StringBuilder()

        for (i in 0 until lyricsArr.size()) {
            val lineObj = lyricsArr.get(i).asJsonObject
            val timeMs = lineObj.get("time")?.asLong ?: continue
            val timeTag = formatLrcTimestamp(timeMs, bracketed = true)
            sb.append(timeTag)

            val syllables = lineObj.getAsJsonArray("syllabus")
            if (isWordType && syllables != null && syllables.size() > 0) {
                for (j in 0 until syllables.size()) {
                    val sylObj = syllables.get(j).asJsonObject
                    val sylText = sylObj.get("text")?.asString ?: ""
                    val sylTimeMs = sylObj.get("time")?.asLong ?: 0L
                    val sylTag = formatLrcTimestamp(sylTimeMs, bracketed = false)
                    sb.append(sylTag).append(sylText)
                }
            } else {
                val text = lineObj.get("text")?.asString ?: ""
                sb.append(text)
            }
            sb.append("\n")
        }

        return sb.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun formatLrcTimestamp(timeMs: Long, bracketed: Boolean): String {
        val safeTime = timeMs.coerceAtLeast(0L)
        val minutes = safeTime / 60000L
        val seconds = (safeTime % 60000L) / 1000L
        val millis = safeTime % 1000L
        val ts = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        return if (bracketed) "[$ts]" else "<$ts>"
    }
}
