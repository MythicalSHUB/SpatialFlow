package com.codetrio.spatialflow.ui.player.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches canvas motion artwork from primary/fallback REST endpoints, falling back
 * to [AppleMusicCanvasProvider] direct scraping on cache misses from both servers.
 *
 * Thread-safe in-memory TTL cache (60 s) avoids duplicate network calls for the same track.
 */
internal object SongCanvasProvider {

    private const val BASE_URL = "https://artwork-archivetune.koiiverse.cloud/"
    private const val FALLBACK_URL = "https://artwork.boidu.dev/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private fun buildClient(baseUrl: String) = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 12_000
            requestTimeoutMillis = 18_000
            socketTimeoutMillis = 18_000
        }
        install(ContentEncoding) { gzip(); deflate() }
        install(HttpCache)
        defaultRequest { url(baseUrl) }
        expectSuccess = false
    }

    private val primaryClient by lazy { buildClient(BASE_URL) }
    private val fallbackClient by lazy { buildClient(FALLBACK_URL) }

    private data class CacheEntry(val value: CanvasArtwork?, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val TTL_MS = 60_000L

    private fun cacheKey(prefix: String, vararg parts: String): String {
        val normalized = parts.map { it.trim().lowercase(Locale.ROOT) }.joinToString("|")
        return "$prefix|$normalized"
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("sa", song, artist, storefront)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val primary = runCatching {
            val r = primaryClient.get {
                parameter("s", song)
                parameter("a", artist)
                parameter("storefront", storefront)
            }
            if (r.status == HttpStatusCode.OK) r.body<CanvasArtwork>() else null
        }.getOrNull()

        val value = primary
            ?: runCatching {
                val r = fallbackClient.get {
                    parameter("s", song)
                    parameter("a", artist)
                    parameter("storefront", storefront)
                }
                if (r.status == HttpStatusCode.OK) r.body<CanvasArtwork>() else null
            }.getOrNull()
            ?: AppleMusicCanvasProvider.getBySongArtist(song, artist, null, storefront)

        cache[key] = CacheEntry(value = value, expiresAtMs = System.currentTimeMillis() + TTL_MS)
        return value
    }

    suspend fun getByAlbumId(albumId: String): CanvasArtwork? {
        val key = cacheKey("id", albumId)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val primary = runCatching {
            val r = primaryClient.get { parameter("id", albumId) }
            if (r.status == HttpStatusCode.OK) r.body<CanvasArtwork>() else null
        }.getOrNull()

        val value = primary
            ?: runCatching {
                val r = fallbackClient.get { parameter("id", albumId) }
                if (r.status == HttpStatusCode.OK) r.body<CanvasArtwork>() else null
            }.getOrNull()
            ?: AppleMusicCanvasProvider.getByAlbumId(albumId)

        cache[key] = CacheEntry(value = value, expiresAtMs = System.currentTimeMillis() + TTL_MS)
        return value
    }

    suspend fun getByAlbumUrl(url: String): CanvasArtwork? {
        val key = cacheKey("url", url)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val primary = runCatching {
            val r = primaryClient.get { parameter("url", url) }
            if (r.status == HttpStatusCode.OK) r.body<CanvasArtwork>() else null
        }.getOrNull()

        val fallback = primary ?: runCatching {
            val r = fallbackClient.get { parameter("url", url) }
            if (r.status == HttpStatusCode.OK) r.body<CanvasArtwork>() else null
        }.getOrNull()

        val value = fallback ?: parseAppleMusicAlbumUrl(url)?.let { (albumId, storefront) ->
            AppleMusicCanvasProvider.getByAlbumId(albumId, storefront)
        }

        cache[key] = CacheEntry(value = value, expiresAtMs = System.currentTimeMillis() + TTL_MS)
        return value
    }

    private fun parseAppleMusicAlbumUrl(url: String): Pair<String, String>? {
        if (!url.contains("music.apple.com")) return null
        val albumPart = url.substringAfter("/album/", "").substringBefore("?")
        val albumId = albumPart.substringAfterLast("/", "")
        if (albumId.isBlank() || !albumId.all { it.isDigit() }) return null
        val storefront = url.substringAfter("music.apple.com/").substringBefore("/")
        if (storefront.isBlank()) return null
        return albumId to storefront
    }
}
