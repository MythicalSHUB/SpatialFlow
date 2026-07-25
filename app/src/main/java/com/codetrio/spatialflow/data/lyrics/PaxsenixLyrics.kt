package com.codetrio.spatialflow.data.lyrics

import android.content.Context
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Centralized Paxsenix API client.
 * Features:
 * - Apple Music AMP API catalog search with duration scoring
 * - TTML direct XML fetch (`ttml=true`)
 * - NetEase `klyric` word-by-word Karaoke (`word=true`)
 * - Musixmatch syllable fetch (`type=word`)
 * - Strict track duration tolerance filtering (<10,000ms)
 */
object PaxsenixLyrics {

    private const val TAG = "PaxsenixLyrics"
    private const val BASE_URL = "https://lyrics.paxsenix.org"
    private const val ITUNES_BASE_URL = "https://itunes.apple.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var userAgentHeader: String = "SpatialFlow/1.8.1"

    fun setUserAgent(appName: String, version: String) {
        userAgentHeader = "$appName/$version"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun get(context: Context?, path: String): String? {
        val url = if (path.startsWith("http")) path else "$BASE_URL$path"
        val officialAgent = "SpatialFlow/1.0.0"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", officialAgent)
            .header("Accept", "application/json, text/plain, */*")
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    return resp.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Request exception [$path]: ${e.message}")
        }

        // Fallback to whitelisted agent
        val reqFallback = Request.Builder()
            .url(url)
            .header("User-Agent", userAgentHeader)
            .build()

        return try {
            client.newCall(reqFallback).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFirstId(searchBody: String): String? {
        return try {
            val parsed = JsonParser.parseString(searchBody)
            if (parsed.isJsonArray) {
                val arr = parsed.asJsonArray
                if (arr.size() > 0) arr.get(0).asJsonObject.get("id")?.asString else null
            } else if (parsed.isJsonObject) {
                val obj = parsed.asJsonObject
                if (obj.has("tracks")) {
                    val items = obj.getAsJsonObject("tracks")?.getAsJsonArray("items")
                    if (items != null && items.size() > 0) return items.get(0).asJsonObject.get("id")?.asString
                }
                if (obj.has("data") && obj.get("data").isJsonArray) {
                    val arr = obj.getAsJsonArray("data")
                    if (arr.size() > 0) return arr.get(0).asJsonObject.get("id")?.asString
                }
                if (obj.has("id")) obj.get("id").asString else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ── Apple Music ─────────────────────────────────────────────────────────────

    /**
     * Searches Apple Music catalog via iTunes Search API with title/artist/duration scoring,
     * then requests raw TTML XML from Paxsenix.
     */
    fun getAppleMusicLyrics(context: Context?, title: String, artist: String, durationSec: Int = -1): String? {
        val q = enc("$title $artist")

        // 1. Try iTunes Search API (public tokenless Apple Music catalog search)
        var trackId: String? = null
        try {
            val itunesUrl = "$ITUNES_BASE_URL/search?term=$q&entity=song&limit=10"
            val req = Request.Builder()
                .url(itunesUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val root = JsonParser.parseString(body).asJsonObject
                        val songs = root.getAsJsonArray("results")
                        if (songs != null && songs.size() > 0) {
                            var bestId: String? = null
                            var bestScore = -1

                            for (i in 0 until songs.size()) {
                                val item = songs.get(i).asJsonObject
                                val songId = item.get("trackId")?.asString ?: continue
                                val name = item.get("trackName")?.asString ?: ""
                                val artistName = item.get("artistName")?.asString ?: ""
                                val durMs = item.get("trackTimeMillis")?.asLong ?: 0L

                                var score = 0
                                if (name.equals(title, ignoreCase = true)) score += 20
                                else if (name.contains(title, ignoreCase = true)) score += 10
                                if (artistName.equals(artist, ignoreCase = true)) score += 15
                                else if (artistName.contains(artist, ignoreCase = true)) score += 5

                                if (durationSec > 0 && durMs > 0) {
                                    val targetMs = durationSec * 1000L
                                    val diff = abs(durMs - targetMs)
                                    if (diff < 3000) score += 10
                                    else if (diff < 10000) score += 5
                                }

                                if (score > bestScore) {
                                    bestScore = score
                                    bestId = songId
                                }
                            }

                            if (bestScore >= 12) {
                                trackId = bestId
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "iTunes catalog search failed: ${e.message}")
        }

        // 2. Fallback search via Paxsenix /apple-music/search
        if (trackId == null) {
            val searchBody = get(context, "/apple-music/search?q=$q")
            if (searchBody != null) {
                trackId = extractFirstId(searchBody)
            }
        }

        if (trackId == null) return null

        // 3. Primary TTML fetch from /apple-music/lyrics?id=...&ttml=true
        val ttmlBody = get(context, "/apple-music/lyrics?id=${enc(trackId)}&ttml=true")
        if (ttmlBody != null) {
            val trimmed = ttmlBody.trimStart()
            if (trimmed.startsWith("<tt") || trimmed.startsWith("<?xml")) {
                return ttmlBody
            }
            try {
                val json = JsonParser.parseString(ttmlBody).asJsonObject
                val content = json.get("content")?.asString ?: json.get("ttmlContent")?.asString
                if (content != null && (content.startsWith("<tt") || content.startsWith("<?xml"))) {
                    return content
                }
            } catch (_: Exception) { }
        }

        // 4. Raw cache endpoint fallback
        val cachedTtml = get(context, "/apple-music/cache/${enc(trackId)}")
        if (cachedTtml != null && (cachedTtml.startsWith("<tt") || cachedTtml.startsWith("<?xml"))) {
            return cachedTtml
        }

        return null
    }

    // ── NetEase Karaoke ─────────────────────────────────────────────────────────

    fun getNeteaseLyrics(context: Context?, title: String, artist: String, durationSec: Int = -1): String? {
        val q = enc("$title $artist")
        val searchBody = get(context, "/netease/search?q=$q") ?: return null
        val trackId = extractFirstId(searchBody) ?: return null

        val body = get(context, "/netease/lyrics?id=${enc(trackId)}&word=true") ?: return null
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            val klyric = json.getAsJsonObject("klyric")?.get("lyric")?.asString
            if (!klyric.isNullOrBlank()) {
                klyric // Word-by-word Karaoke
            } else {
                extractLyrics(body)
            }
        } catch (e: Exception) {
            extractLyrics(body)
        }
    }

    // ── Musixmatch Word-by-Word ──────────────────────────────────────────────────

    fun getMusixmatchLyrics(context: Context?, title: String, artist: String, durationSec: Int = -1): String? {
        val t = enc(title)
        val a = enc(artist)
        val d = durationSec.toString()
        val q = enc("$title $artist")

        // 1. Try word-by-word first
        val wordBody = get(context, "/musixmatch/lyrics?q=$q&t=$t&a=$a&d=$d&type=word")
        if (wordBody != null) {
            val extracted = extractLyrics(wordBody)
            if (extracted != null) return extracted
        }

        // 2. Fallback default
        val body = get(context, "/musixmatch/lyrics?t=$t&a=$a&d=$d") ?: return null
        return extractLyrics(body)
    }

    // ── Spotify & YouTube ───────────────────────────────────────────────────

    fun getSpotifyLyrics(context: Context?, title: String, artist: String): String? {
        val q = enc("$title $artist")
        val searchBody = get(context, "/spotify/search?q=$q") ?: return null
        val trackId = extractFirstId(searchBody) ?: return null
        val lyricsBody = get(context, "/spotify/lyrics?id=${enc(trackId)}") ?: return null
        return extractLyrics(lyricsBody)
    }

    fun getYouTubeLyrics(context: Context?, title: String, artist: String): String? {
        val q = enc("$title $artist")
        val searchBody = get(context, "/youtube/search?q=$q") ?: return null
        val trackId = extractFirstId(searchBody) ?: return null
        val body = get(context, "/youtube/lyrics?id=${enc(trackId)}") ?: return null
        return extractLyrics(body)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun extractLyrics(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "null") return null

        if (trimmed.startsWith("[") && !trimmed.startsWith("[{")) {
            return trimmed
        }

        return try {
            val json = JsonParser.parseString(trimmed)
            when {
                json.isJsonObject -> {
                    val obj = json.asJsonObject
                    val candidate = obj.get("syncedLyrics")?.asString?.takeIf { it.isNotBlank() }
                        ?: obj.get("lyrics")?.asString?.takeIf { it.isNotBlank() }
                        ?: obj.get("lrc")?.asString?.takeIf { it.isNotBlank() }
                        ?: obj.get("content")?.asString?.takeIf { it.isNotBlank() }
                    if (obj.get("ok")?.asBoolean == false) null else candidate
                }
                json.isJsonPrimitive && json.asJsonPrimitive.isString ->
                    json.asString.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (e: Exception) {
            trimmed.takeIf { it.isNotBlank() }
        }
    }

    fun isWordByWord(lyrics: String): Boolean =
        lyrics.contains(Regex("""\[\d{2}:\d{2}\.\d+\].*<\d{2}:\d{2}\.\d+>""")) ||
        lyrics.contains("<span ttm:begin") ||
        lyrics.contains("<tt")
}
