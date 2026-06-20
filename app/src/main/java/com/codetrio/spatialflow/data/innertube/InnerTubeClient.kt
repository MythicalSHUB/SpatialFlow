package com.codetrio.spatialflow.data.innertube

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Low-level HTTP client for YouTube Music's InnerTube API.
 * Handles request construction, authentication, and raw HTTP communication.
 */
@OptIn(DelicateCoroutinesApi::class)
object InnerTubeClient {

    private const val TAG = "InnerTubeClient"
    private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
    private const val YOUTUBE_BASE_URL = "https://www.youtube.com/youtubei/v1"
    private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"


    private val gson = Gson()

    private fun buildClient(cacheDir: File? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(20, 10, TimeUnit.MINUTES))
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
        
        // Enable 100MB dynamic disk cache for player manifests & repetitive metadata responses
        cacheDir?.let {
            try {
                val httpCache = File(it, "http_cache_innertube")
                builder.cache(Cache(httpCache, 100L * 1024 * 1024)) // 100 MB
            } catch (_: Exception) { /* non-fatal cache fail */ }
        }
        
        return builder.build()
    }

    var httpClient = buildClient()
        private set

    /**
     * Binds runtime context to initialize core HTTP disk caching and shared state hooks.
     * Recommended execution target: Application.onCreate().
     */
    @JvmStatic
    fun initialize(context: android.content.Context) {
        // Re-instantiate full persistence client with application storage context available
        httpClient = buildClient(context.applicationContext.cacheDir)
        Log.d(TAG, "InnerTube client initialized with localized dynamic disk cache.")
    }

    // ========== Client Configurations ==========

    /**
     * WEB_REMIX client — used for search, browse, next (YouTube Music Web)
     */
    private fun webRemixContext(): JsonObject {
        val client = JsonObject().apply {
            addProperty("clientName", "WEB_REMIX")
            addProperty("clientVersion", "1.20260531.05.00")
            addProperty("hl", Locale.getDefault().language)
            addProperty("gl", Locale.getDefault().country.ifEmpty { "US" })
            visitorData?.let { addProperty("visitorData", it) }
        }
        return JsonObject().apply { add("client", client) }
    }

    /**
     * WEB client — used for standard YouTube Web requests (e.g. nextYoutubeWeb)
     */
    private fun webContext(): JsonObject {
        val client = JsonObject().apply {
            addProperty("clientName", "WEB")
            addProperty("clientVersion", "2.20250310.01.00")
            addProperty("hl", Locale.getDefault().language)
            addProperty("gl", Locale.getDefault().country.ifEmpty { "US" })
            visitorData?.let { addProperty("visitorData", it) }
        }
        return JsonObject().apply { add("client", client) }
    }

    /**
     * ANDROID client — Core modern YouTube client for resilient logged-out streaming.
     */
    private fun androidContext(): JsonObject {
        val client = JsonObject().apply {
            addProperty("clientName", "ANDROID")
            addProperty("clientVersion", "20.10.38")
            addProperty("androidSdkVersion", 30)
            addProperty("osName", "Android")
            addProperty("osVersion", "11")
            addProperty("platform", "MOBILE")
            addProperty("hl", Locale.getDefault().language)
            addProperty("gl", Locale.getDefault().country.ifEmpty { "US" })
            visitorData?.let { addProperty("visitorData", it) }
        }
        return JsonObject().apply { add("client", client) }
    }

    // ========== User Agents ==========

    // Optimized with Zion's explicit compatibility strings
    private const val WEB_REMIX_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val ANDROID_UA = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip"
    private const val IOS_UA = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

    // Restored standard public API key to authorize logged-in requests and bypass Gateway 400 failures
    private const val INNER_TUBE_API_KEY = "AIzaSyAO_JVGg4tq4r2T5Co2t8G3oG1d1dQ"

    // ========== Cookie / Auth ==========

    var cookie: String? = null
        set(value) {
            field = value
            visitorData = null // Auto-reset visitor data when cookie changes to prevent session conflicts
        }
    var visitorData: String? = null


    internal fun buildHeaders(clientType: ClientType, isYoutube: Boolean = false): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = JSON_MEDIA_TYPE
        headers["Accept-Language"] = "${Locale.getDefault().language},en;q=0.9"
        headers["X-Goog-Api-Format-Version"] = "1"
        if (isYoutube) {
            headers["X-Origin"] = "https://www.youtube.com"
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
        } else {
            headers["X-Origin"] = "https://music.youtube.com"
            headers["Origin"] = "https://music.youtube.com"
            headers["Referer"] = "https://music.youtube.com/"
        }

        // Zion Signature: Explicit Internal Numeric IDs mapped into X-YouTube-Client-Name header!
        when (clientType) {
            ClientType.WEB_REMIX -> {
                headers["User-Agent"] = WEB_REMIX_UA
                headers["X-YouTube-Client-Name"] = "67"
                headers["X-YouTube-Client-Version"] = "1.20260531.05.00"
            }
            ClientType.WEB -> {
                headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                headers["X-YouTube-Client-Name"] = "1"
                headers["X-YouTube-Client-Version"] = "2.20250310.01.00"
            }
            ClientType.ANDROID -> {
                headers["User-Agent"] = ANDROID_UA
                headers["X-YouTube-Client-Name"] = "3"
                headers["X-YouTube-Client-Version"] = "20.10.38"
            }
            ClientType.IOS -> {
                headers["User-Agent"] = IOS_UA
                headers["X-YouTube-Client-Name"] = "5"
                headers["X-YouTube-Client-Version"] = "20.10.4"
            }
        }

        // Only WEB and WEB_REMIX clients support Cookie/SAPISIDHASH authentication.
        // Mobile clients (ANDROID, IOS) expect OAuth Bearer tokens, and reject SAPISIDHASH with HTTP 400.
        if (clientType == ClientType.WEB_REMIX || clientType == ClientType.WEB) {
            cookie?.let { rawCookie ->
                headers["Cookie"] = rawCookie
                // Important: Logged-in InnerTube demands the authorization signature hash 
                val cookieMap = parseCookieMap(rawCookie)
                val sapisid = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
                if (sapisid != null) {
                    val timestamp = System.currentTimeMillis() / 1000
                    val origin = if (isYoutube) "https://www.youtube.com" else "https://music.youtube.com"
                    val hashInput = "$timestamp $sapisid $origin"
                    val hash = sha1(hashInput)
                    headers["Authorization"] = "SAPISIDHASH ${timestamp}_$hash"
                }
            }
        }

        visitorData?.let { headers["X-Goog-Visitor-Id"] = it }

        return headers
    }



    // ========== API Endpoints ==========

    /**
     * Search YouTube Music
     */
    suspend fun search(
        query: String,
        filter: String? = null,
        continuation: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            if (continuation != null) {
                addProperty("continuation", continuation)
            } else {
                addProperty("query", query)
                filter?.let { addProperty("params", it) }
            }
        }
        post("$BASE_URL/search", body, ClientType.WEB_REMIX)
    }

    /**
     * Get search suggestions
     */
    suspend fun searchSuggestions(query: String): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            addProperty("input", query)
        }
        post("$BASE_URL/music/get_search_suggestions", body, ClientType.WEB_REMIX)
    }

    /**
     * Browse endpoint — home feed, artist pages, album pages, playlists
     */
    suspend fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val cleanBrowseId = browseId?.trim()
        val cleanContinuation = continuation?.trim()

        val finalContinuation = cleanContinuation.takeUnless {
            it.isNullOrEmpty() || it == "null" || it == "undefined"
        }
        val finalBrowseId = cleanBrowseId.takeUnless {
            it.isNullOrEmpty() || it == "null" || it == "undefined" || it == "VL" || it == "VLnull" || it == "VLundefined"
        }

        if (finalBrowseId == null && finalContinuation == null) {
            Log.e(TAG, "Aborting browse call: invalid or empty browseId ('$browseId') and continuation ('$continuation').")
            return@withContext JsonObject()
        }

        val body = JsonObject().apply {
            add("context", webRemixContext())
            if (finalContinuation != null) {
                addProperty("continuation", finalContinuation)
            } else {
                finalBrowseId?.let { addProperty("browseId", it) }
                params?.let { addProperty("params", it) }
            }
        }
        post("$BASE_URL/browse", body, ClientType.WEB_REMIX)
    }

    /**
     * Player endpoint — get streaming data for a video.
     * Tries: Invidious → Piped → ANDROID (youtube.com) → ANDROID_MUSIC
     */
    /**
     * Player endpoint specifically for fetching music configuration with WEB_REMIX
     * Used for retrieving authentic playback position (watch history resume).
     */
    suspend fun playerWebRemix(videoId: String): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            addProperty("videoId", videoId)
            
            // Add playbackContext containing signatureTimestamp to prevent HTTP 400/403
            val sigTimestamp = try {
                org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
            } catch (_: Exception) {
                null
            }
            sigTimestamp?.let { timestamp ->
                val contentContext = JsonObject().apply {
                    addProperty("signatureTimestamp", timestamp)
                }
                val playbackCtx = JsonObject().apply {
                    add("contentPlaybackContext", contentContext)
                }
                add("playbackContext", playbackCtx)
            }
        }
        post("$BASE_URL/player", body, ClientType.WEB_REMIX)
    }

    /**
     * Player endpoint — get streaming data for a video.
     * Optimized sequence derived from ViMusic: pure ANDROID core -> IOS core fallback.
     * Fully authenticated via Zion headers to prevent HTTP 400 / LOGIN_REQUIRED cascades.
     */
    suspend fun player(videoId: String?, playlistId: String? = null): JsonObject = withContext(Dispatchers.IO) {
        if (videoId == null) return@withContext JsonObject()

        // Attempt 1: Standard YouTube ANDROID Client (Bypasses "Music" sign-in blocks)
        // CRITICAL: Dynamically fetch matching signature timestamp to pair with the cipher engine!
        val sigTimestamp = try {
            org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        } catch (_: Exception) { null }

        val androidResult = playerWithClient(
            videoId, playlistId,
            clientType = ClientType.ANDROID,
            clientName = "ANDROID", clientVersion = "20.10.38",
            userAgent = ANDROID_UA,
            baseUrl = YOUTUBE_BASE_URL,
            signatureTimestamp = sigTimestamp,
            extraContext = { ctx ->
                ctx.addProperty("androidSdkVersion", 30)
                ctx.addProperty("osName", "Android")
                ctx.addProperty("osVersion", "11")
                ctx.addProperty("platform", "MOBILE")
            }
        )
        if (isPlayerResponseValid(androidResult)) {
            Log.d(TAG, "Player success with native ANDROID core client")
            return@withContext androidResult
        }
        logPlayerFailure("ANDROID", androidResult)

        // Attempt 2: Modern IOS core fallback
        val iosResult = playerWithClient(
            videoId, playlistId,
            clientType = ClientType.IOS,
            clientName = "IOS", clientVersion = "20.10.4",
            userAgent = IOS_UA,
            baseUrl = YOUTUBE_BASE_URL,
            signatureTimestamp = sigTimestamp, // Pass to iOS too for resilience
            extraContext = { ctx ->
                ctx.addProperty("deviceMake", "Apple")
                ctx.addProperty("deviceModel", "iPhone16,2")
                ctx.addProperty("osName", "iPhone")
                ctx.addProperty("osVersion", "18.3.2.22D82")
                ctx.addProperty("platform", "MOBILE")
            }
        )
        if (isPlayerResponseValid(iosResult)) {
            Log.d(TAG, "Player success with native IOS core client")
            return@withContext iosResult
        }
        logPlayerFailure("IOS", iosResult)

        Log.w(TAG, "Native InnerTube direct streams failing. Returning original payload for ultimate NewPipe fallback handling.")
        return@withContext if (androidResult.entrySet().isNotEmpty()) androidResult else iosResult
    }

    private suspend fun OkHttpClient.executeSuspended(request: Request): okhttp3.Response =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) { /* mute */ }
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(response))
                    } else {
                        response.close()
                    }
                }
            })
        }

    /**
     * Generic InnerTube player request with configurable client.
     */
    private suspend fun playerWithClient(
        videoId: String,
        playlistId: String?,
        clientType: ClientType,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        baseUrl: String,
        signatureTimestamp: Int? = null,
        extraContext: (JsonObject) -> Unit = {}
    ): JsonObject = withContext(Dispatchers.IO) {
        val clientContext = JsonObject().apply {
            addProperty("clientName", clientName)
            addProperty("clientVersion", clientVersion)
            addProperty("hl", Locale.getDefault().language)
            addProperty("gl", Locale.getDefault().country.ifEmpty { "US" })
            visitorData?.let { addProperty("visitorData", it) }
            extraContext(this)
        }
        val context = JsonObject().apply { add("client", clientContext) }

        val body = JsonObject().apply {
            add("context", context)
            addProperty("videoId", videoId)
            playlistId?.let { addProperty("playlistId", it) }
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
            
            // Inject official signature timestamp if available to generate valid, unblocked cipher URLs
            if (signatureTimestamp != null) {
                val contentContext = JsonObject().apply {
                    addProperty("signatureTimestamp", signatureTimestamp)
                }
                val playbackCtx = JsonObject().apply {
                    add("contentPlaybackContext", contentContext)
                }
                add("playbackContext", playbackCtx)
            }
        }

        val mediaType = JSON_MEDIA_TYPE.toMediaType()
        val requestBody = gson.toJson(body).toRequestBody(mediaType)

        // CRITICAL FIX: Load ALL custom Zion headers established in buildHeaders!
        val headerMap = buildHeaders(clientType)

        val request = Request.Builder()
            .url("$baseUrl/player?prettyPrint=false")
            .post(requestBody)
            .apply {
                headerMap.forEach { (k, v) -> header(k, v) }
                // Explicitly override/set these to match Zion exactly
                header("User-Agent", userAgent)
                cookie?.let { header("Cookie", it) }
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
            }
            .build()

        return@withContext try {
            val response = httpClient.executeSuspended(request)
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                Log.w(TAG, "Player $clientName HTTP ${response.code}")
                JsonObject()
            } else {
                JsonParser.parseString(responseBody).asJsonObject
            }
        } catch (e: Exception) {
            Log.w(TAG, "Player $clientName failed: ${e.message}")
            JsonObject()
        }
    }


    // ========== Validation ==========

    private fun isPlayerResponseValid(response: JsonObject): Boolean {
        if (response.entrySet().isEmpty()) return false
        val status = response.getAsJsonObject("playabilityStatus")?.get("status")?.asString
        val streamingData = response.getAsJsonObject("streamingData")
        val hasAdaptive = streamingData?.has("adaptiveFormats") == true
        val hasFormats = streamingData?.has("formats") == true
        return status == "OK" && (hasAdaptive || hasFormats)
    }

    private fun logPlayerFailure(clientName: String, response: JsonObject) {
        val status = response.getAsJsonObject("playabilityStatus")?.get("status")?.asString
        val reason = response.getAsJsonObject("playabilityStatus")?.get("reason")?.asString
        val hasStreamingData = response.has("streamingData")
        val streamKeys = response.getAsJsonObject("streamingData")?.keySet()?.joinToString() ?: "none"
        Log.w(TAG, "$clientName player failed — status=$status, reason=$reason, hasStreamingData=$hasStreamingData, streamKeys=[$streamKeys]")
    }

    /**
     * Next endpoint — get queue, related songs, lyrics endpoint
     */
    suspend fun next(
        videoId: String?,
        playlistId: String? = null,
        playlistSetVideoId: String? = null,
        index: Int? = null,
        params: String? = null,
        continuation: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            if (continuation != null) {
                addProperty("continuation", continuation)
            } else {
                videoId?.let { addProperty("videoId", it) }
                playlistId?.let { addProperty("playlistId", it) }
                playlistSetVideoId?.let { addProperty("playlistSetVideoId", it) }
                index?.let { addProperty("index", it) }
                params?.let { addProperty("params", it) }
            }
        }
        post("$BASE_URL/next", body, ClientType.WEB_REMIX)
    }

    /**
     * Next endpoint with standard ANDROID client for robust metadata/engagement parsing.
     */
    suspend fun nextAndroid(videoId: String? = null, continuation: String? = null): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", androidContext())
            if (continuation != null) {
                addProperty("continuation", continuation)
            } else {
                videoId?.let { addProperty("videoId", it) }
            }
        }
        post("$YOUTUBE_BASE_URL/next", body, ClientType.ANDROID)
    }

    /**
     * Next endpoint using standard YouTube Web host for un-obfuscated guest likes count extraction.
     */
    suspend fun nextYoutubeWeb(videoId: String? = null, continuation: String? = null): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webContext())
            if (continuation != null) {
                addProperty("continuation", continuation)
            } else {
                videoId?.let { addProperty("videoId", it) }
            }
        }
        post("$YOUTUBE_BASE_URL/next", body, ClientType.WEB)
    }


    /**
     * Account menu endpoint — used to fetch user's profile data
     */
    suspend fun accountMenu(): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
        }
        post("$BASE_URL/account/account_menu", body, ClientType.WEB_REMIX)
    }

    // ========== HTTP Layer ==========
 
    private suspend fun post(url: String, body: JsonObject, clientType: ClientType): JsonObject = withContext(Dispatchers.IO) {
        val mediaType = JSON_MEDIA_TYPE.toMediaType()
        val requestBody = gson.toJson(body).toRequestBody(mediaType)
 
        val requestBuilder = Request.Builder()
            .url("$url?key=$INNER_TUBE_API_KEY&prettyPrint=false")
            .post(requestBody)
 
        val isYoutube = url.contains("www.youtube.com")
        buildHeaders(clientType, isYoutube).forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
 
        val request = requestBuilder.build()
 
        return@withContext try {
            val response = httpClient.executeSuspended(request)
            val responseBody = response.body.string()
 
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for $url [${clientType.name}] (skipping response body parsing)")
                JsonObject()
            } else {
                val parsed = JsonParser.parseString(responseBody).asJsonObject
                // Extract visitor data from response if available
                parsed.getAsJsonObject("responseContext")
                    ?.get("visitorData")?.asString?.let {
                        if (visitorData == null) {
                            visitorData = it
                            Log.d(TAG, "Got visitor data from response")
                        }
                    }
                parsed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed [${clientType.name}]: ${e.message}", e)
            JsonObject()
        }
    }

    /**
     * Like a video/song to influence preferences/recap on YouTube Music
     */
    suspend fun like(videoId: String): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            val target = JsonObject().apply {
                addProperty("videoId", videoId)
            }
            add("target", target)
        }
        post("$BASE_URL/like/like", body, ClientType.WEB_REMIX)
    }

    /**
     * Dislike a video/song on YouTube Music
     */
    suspend fun dislike(videoId: String): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            val target = JsonObject().apply {
                addProperty("videoId", videoId)
            }
            add("target", target)
        }
        post("$BASE_URL/like/dislike", body, ClientType.WEB_REMIX)
    }

    /**
     * Remove a like to sync preference state
     */
    suspend fun removeLike(videoId: String): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            val target = JsonObject().apply {
                addProperty("videoId", videoId)
            }
            add("target", target)
        }
        post("$BASE_URL/like/removelike", body, ClientType.WEB_REMIX)
    }

    // ========== Playlist Management ==========

    /**
     * Create a new playlist on the user's YouTube Music account.
     */
    suspend fun createPlaylist(
        title: String,
        description: String = "",
        privacyStatus: String = "PRIVATE", // PRIVATE, PUBLIC, UNLISTED
        videoIds: List<String> = emptyList()
    ): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            addProperty("title", title)
            addProperty("description", description)
            addProperty("privacyStatus", privacyStatus)
            if (videoIds.isNotEmpty()) {
                val arr = com.google.gson.JsonArray()
                videoIds.forEach { arr.add(it) }
                add("videoIds", arr)
            }
        }
        post("$BASE_URL/playlist/create", body, ClientType.WEB_REMIX)
    }

    /**
     * Delete a playlist from the user's YouTube Music library.
     */
    suspend fun deletePlaylist(playlistId: String): JsonObject = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            add("context", webRemixContext())
            addProperty("playlistId", playlistId)
        }
        post("$BASE_URL/playlist/delete", body, ClientType.WEB_REMIX)
    }

    /**
     * Add songs to an existing playlist on YouTube Music.
     */
    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): JsonObject = withContext(Dispatchers.IO) {
        val actions = com.google.gson.JsonArray()
        videoIds.forEach { videoId ->
            val action = JsonObject().apply {
                addProperty("action", "ACTION_ADD_VIDEO")
                addProperty("addedVideoId", videoId)
            }
            actions.add(action)
        }
        val body = JsonObject().apply {
            add("context", webRemixContext())
            addProperty("playlistId", playlistId)
            add("actions", actions)
        }
        post("$BASE_URL/browse/edit_playlist", body, ClientType.WEB_REMIX)
    }

    // ========== Subscriptions ==========

    /**
     * Subscribe to a YouTube Music artist channel.
     */
    suspend fun subscribe(channelId: String): JsonObject = withContext(Dispatchers.IO) {
        val channelIds = com.google.gson.JsonArray().apply { add(channelId) }
        val body = JsonObject().apply {
            add("context", webRemixContext())
            add("channelIds", channelIds)
        }
        post("$BASE_URL/subscription/subscribe", body, ClientType.WEB_REMIX)
    }

    /**
     * Unsubscribe from a YouTube Music artist channel.
     */
    suspend fun unsubscribe(channelId: String): JsonObject = withContext(Dispatchers.IO) {
        val channelIds = com.google.gson.JsonArray().apply { add(channelId) }
        val body = JsonObject().apply {
            add("context", webRemixContext())
            add("channelIds", channelIds)
        }
        post("$BASE_URL/subscription/unsubscribe", body, ClientType.WEB_REMIX)
    }

    private fun parseCookieMap(cookie: String): Map<String, String> {
        return cookie.split(";").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
    }

    private fun sha1(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    enum class ClientType {
        WEB_REMIX,
        WEB,
        IOS,
        ANDROID
    }
}
