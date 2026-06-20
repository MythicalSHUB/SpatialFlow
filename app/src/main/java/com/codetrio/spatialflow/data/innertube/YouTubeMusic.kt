@file:OptIn(DelicateCoroutinesApi::class)
@file:Suppress("DeferredResultUnused")

package com.codetrio.spatialflow.data.innertube

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * High-level YouTube Music API facade.
 * Provides clean, type-safe methods for all YouTube Music operations.
 * This is the main entry point for online music features.
 */
object YouTubeMusic {

    private const val TAG = "YouTubeMusic"

    // Intelligent Streaming Performance Caches
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val activeStreamRequests = ConcurrentHashMap<String, Deferred<Result<String>>>()
    private const val CACHE_TTL = 6 * 60 * 60 * 1000L // Standard 6 Hour TTL

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun cleanExpiredCache() {
        val now = System.currentTimeMillis()
        streamUrlCache.entries.removeIf { it.value.second < now }
    }

    /**
     * Proactively resolve and cache the streaming URL in the background.
     * Eliminates click-to-play lag entirely for predicted next tracks.
     */
    @JvmStatic
    fun prefetch(videoId: String?) {
        if (videoId.isNullOrEmpty()) return
        // Fire-and-forget resolution call leveraging our deduplicated cache chain
        appScope.launch {
            try {
                getStreamUrl(videoId)
                Log.d(TAG, "Successfully prefetched stream cache for: $videoId")
            } catch (_: Exception) {
                // Prefetch failures are non-critical, silent ignore
            }
        }
    }

    // ========== Search ==========

    /**
     * Search YouTube Music for songs, albums, artists, playlists.
     * Returns mixed results (all types) when no filter is specified.
     */
    suspend fun search(query: String, filter: SearchFilter? = null): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(7000L.milliseconds) {
                val response = InnerTubeClient.search(query, filter?.value)
                InnerTubeParser.parseSearchResponse(response)
            }
        }
    }

    /**
     * Load more search results using continuation token.
     */
    suspend fun searchContinuation(continuation: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(7000L.milliseconds) {
                val response = InnerTubeClient.search("", continuation = continuation)
                InnerTubeParser.parseSearchResponse(response)
            }
        }
    }

    /**
     * Get search suggestions as user types.
     */
    suspend fun searchSuggestions(query: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(2500L.milliseconds) {
                val response = InnerTubeClient.searchSuggestions(query)
                val suggestions = mutableListOf<String>()

                val contents = response.path("contents")?.asJsonArray
                contents?.forEach { section ->
                    val renderer = section.asJsonObject
                        .getAsJsonObject("searchSuggestionsSectionRenderer")
                    val sectionContents = renderer?.getAsJsonArray("contents")
                    sectionContents?.forEach { item ->
                        val suggestion = item.asJsonObject
                            .getAsJsonObject("searchSuggestionRenderer")
                            ?.path("suggestion.runs")?.asJsonArray
                            ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
                        suggestion?.let { suggestions.add(it) }
                    }
                }
                suggestions
            }
        }
    }

    // ========== Player & Streaming ==========

    suspend fun player(videoId: String?): Result<PlayerResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (videoId.isNullOrEmpty()) throw IllegalArgumentException("Invalid videoId")

            cleanExpiredCache()

            coroutineScope {
                val newPipeDeferred = async(Dispatchers.IO) {
                    try {
                        NewPipeStreamExtractor.getPlayerResult(videoId)
                    } catch (e: Exception) {
                        Log.e("YouTubeTelemetry", "NewPipe stream extraction failed for $videoId: ${e.message}", e)
                        null
                    }
                }

                val trackingDeferred = async(Dispatchers.IO) {
                    var pbUrl: String? = null
                    var wtUrl: String? = null
                    if (!InnerTubeClient.cookie.isNullOrBlank()) {
                        try {
                            val playerJson = InnerTubeClient.playerWebRemix(videoId)
                            Log.d("YouTubeTelemetry", "playerWebRemix keys: ${playerJson.keySet()}")
                            if (playerJson.has("playabilityStatus")) {
                                Log.d("YouTubeTelemetry", "playabilityStatus: ${playerJson.getAsJsonObject("playabilityStatus")}")
                            }
                            val tracking = playerJson.getAsJsonObject("playbackTracking")
                            pbUrl = tracking?.getAsJsonObject("videostatsPlaybackUrl")?.get("baseUrl")?.asString
                            wtUrl = tracking?.getAsJsonObject("videostatsWatchtimeUrl")?.get("baseUrl")?.asString
                            Log.d("YouTubeTelemetry", "Fetched watch history URLs from WebRemix player: playbackUrl=$pbUrl, watchtimeUrl=$wtUrl")
                        } catch (e: Exception) {
                            Log.e("YouTubeTelemetry", "Failed to fetch watch history URLs from WebRemix player: ${e.message}", e)
                        }
                    }
                    Pair(pbUrl, wtUrl)
                }

                val newPipeResult = newPipeDeferred.await()
                val (playbackUrl, watchtimeUrl) = trackingDeferred.await()

                newPipeResult?.copy(
                    playbackUrl = playbackUrl,
                    watchtimeUrl = watchtimeUrl
                ) ?: PlayerResult(
                    videoId = videoId,
                    title = "Unknown",
                    artist = "Unknown Artist",
                    thumbnailUrl = null,
                    durationMs = 0,
                    streams = emptyList(),
                    likesCount = null,
                    playbackUrl = playbackUrl,
                    watchtimeUrl = watchtimeUrl
                )
            }
        }
    }

    /**
     * Atomically acquires the best audio stream URL for a given video ID.
     * Implements absolute deduped parallelism prevention and automatic caching.
     */
    suspend fun getStreamUrl(videoId: String?): Result<String> = coroutineScope {
        if (videoId.isNullOrEmpty()) return@coroutineScope Result.failure(IllegalArgumentException("Video ID null"))

        cleanExpiredCache()
        
        // Check Level 1 Cache: Instant memory map retrieval
        val cached = streamUrlCache[videoId]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            return@coroutineScope Result.success(cached.first)
        }

        // Level 2: Check for in-flight request duplication prevention
        // Atomically compute or retrieve an active coroutine future for this exact stream.
        val activeTask = activeStreamRequests.computeIfAbsent(videoId) { id ->
            appScope.async {
                try {
                    // Directly invoke the high-reliability local extraction engine as requested
                    // Bypasses the volatile direct-player trial cascade which accumulates latency
                    var bestUrl = NewPipeStreamExtractor.getStreamUrl(id)

                    if (bestUrl.isNullOrEmpty()) {
                        Log.d(TAG, "NewPipe extractor returned empty for $id. Falling back to native InnerTube player client...")
                        try {
                            val playerJson = InnerTubeClient.player(id)
                            val parsed = InnerTubeParser.parsePlayerResponse(playerJson)
                            val (audioQuality, dataSaver) = NewPipeStreamExtractor.getPreferencesSnapshot()

                            val audioStreams = parsed?.streams
                                ?.filter { it.mimeType.contains("audio", ignoreCase = true) }
                            
                            val streamsList = audioStreams.orEmpty()
                            val bestStream = when {
                                audioQuality == "Data Saver" || dataSaver -> streamsList.minByOrNull { it.bitrate }
                                audioQuality == "Normal" -> streamsList.elementAtOrNull(streamsList.size / 2)
                                else -> streamsList.maxByOrNull { it.bitrate }
                            }
                            bestUrl = bestStream?.url
                            if (!bestUrl.isNullOrEmpty()) {
                                Log.d(TAG, "Successfully resolved fallback stream via InnerTube player for $id")
                            }
                        } catch (fallbackEx: Exception) {
                            Log.e(TAG, "InnerTube player fallback failed for $id: ${fallbackEx.message}")
                        }
                    }

                    if (!bestUrl.isNullOrEmpty()) {
                        // Commit successfully resolved URL to memory cache
                        streamUrlCache[id] = bestUrl to (System.currentTimeMillis() + CACHE_TTL)
                        Result.success(bestUrl)
                    } else {
                        Result.failure(IllegalStateException("Stream extraction total exhaustion"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    // Release request lock immediately upon resolution boundary
                    activeStreamRequests.remove(id)
                }
            }
        }

        return@coroutineScope activeTask.await()
    }

    /**
     * Extracts YouTube's native 'loudnessDb' parameter for a given video ID.
     * This metric indicates how much the track deviates from YouTube's target of -14 LUFS.
     */
    suspend fun getLoudnessDb(videoId: String): Result<Float> = withContext(Dispatchers.IO) {
        runCatching {
            android.util.Log.d("YouTubeMusic", "Fetching loudnessDb for videoId: $videoId via playerWebRemix")
            // Using playerWebRemix (Client 67) instead of player (Android/iOS) to bypass HTTP 400 blocks
            val playerJson = InnerTubeClient.playerWebRemix(videoId)
            val playerConfig = playerJson.getAsJsonObject("playerConfig")
            val audioConfig = playerConfig?.getAsJsonObject("audioConfig")
            val loudnessDb = audioConfig?.get("loudnessDb")?.asFloat
            
            if (loudnessDb == null) {
                android.util.Log.w("YouTubeMusic", "Loudness DB not found in playerWebRemix response for $videoId")
                throw Exception("Loudness DB not found in player response")
            } else {
                android.util.Log.d("YouTubeMusic", "Successfully fetched loudnessDb: $loudnessDb for $videoId")
                loudnessDb
            }
        }.onFailure { e ->
            android.util.Log.e("YouTubeMusic", "Failed to fetch loudnessDb for $videoId: ${e.message}", e)
        }
    }

    // ========== Home Feed ==========

    /**
     * Get the YouTube Music home page with personalized recommendations.
     */
    suspend fun home(): Result<HomePage> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(7000L.milliseconds) {
                val response = InnerTubeClient.browse(browseId = "FEmusic_home")
                InnerTubeParser.parseHomePage(response)
            }
        }
    }

    /**
     * Load more home page sections.
     */
    suspend fun homeContinuation(continuation: String): Result<HomePage> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(7000L.milliseconds) {
                val response = InnerTubeClient.browse(continuation = continuation)
                InnerTubeParser.parseHomePage(response)
            }
        }
    }

    /**
     * Get the Explore/Charts page.
     */
    suspend fun explore(): Result<HomePage> = withContext(Dispatchers.IO) {
        runCatching {
            kotlinx.coroutines.withTimeout(7000L.milliseconds) {
                val response = InnerTubeClient.browse(browseId = "FEmusic_explore")
                InnerTubeParser.parseHomePage(response)
            }
        }
    }

    // ========== Albums ==========

    /**
     * Get an album's details and songs.
     */
    suspend fun album(browseId: String): Result<AlbumPage> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = browseId)
            InnerTubeParser.parseAlbumPage(response, browseId)
                ?: throw Exception("Failed to parse album $browseId")
        }
    }

    // ========== Artists ==========

    /**
     * Get an artist's page with songs, albums, etc.
     */
    suspend fun artist(browseId: String): Result<ArtistPage> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = browseId)

            // Parse artist header
            val header = response.path("header.musicImmersiveHeaderRenderer")?.asJsonObject
                ?: response.path("header.musicVisualHeaderRenderer")?.asJsonObject
                ?: response.path("header.musicHeaderRenderer")?.asJsonObject

            val title = header?.path("title.runs.0.text")?.asString ?: "Unknown Artist"
            val thumbnail = header?.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: header?.path("foregroundThumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: header?.path("avatar.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: response.path("header.musicHeaderRenderer.thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                ?: response.path("header.musicHeaderRenderer.avatar.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
            val finalThumbnail = InnerTubeParser.getHighResThumbnailUrl(thumbnail)
            val description = header?.path("description.runs.0.text")?.asString

            val subCount = header?.path("subtitle.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                }?.takeIf { it.isNotBlank() }
                ?: header?.path("subscriptionButton.subscribeButtonRenderer.subscriberCountWithConfirm.runs.0.text")?.asString
                ?: header?.path("subscriptionButton.subscribeButtonRenderer.subscriberCountText.runs.0.text")?.asString

            val additionalInfo = header?.path("additionalInfo.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                }?.takeIf { it.isNotBlank() }

            var combinedSubCount = subCount ?: ""
            if (!additionalInfo.isNullOrEmpty()) {
                if (combinedSubCount.isNotEmpty() && !combinedSubCount.contains(additionalInfo)) {
                    combinedSubCount = "$combinedSubCount • $additionalInfo"
                } else if (combinedSubCount.isEmpty()) {
                    combinedSubCount = additionalInfo
                }
            }

            // Regex extraction from description as a solid fallback
            if (description != null && !combinedSubCount.lowercase().contains("monthly")) {
                val regex = Regex("([0-9a-zA-Z.,]+)\\s*(monthly listeners|monthly audience|listeners|audience)", RegexOption.IGNORE_CASE)
                val match = regex.find(description)
                if (match != null) {
                    val count = match.groupValues[1]
                    val label = match.groupValues[2]
                    val suffix = if (label.lowercase().contains("listener")) "Monthly Listeners" else "Monthly Audience"
                    combinedSubCount = if (combinedSubCount.isNotEmpty()) {
                        "$combinedSubCount • $count $suffix"
                    } else {
                        "$count $suffix"
                    }
                }
            }

            // Extract subscription status
            val isSubscribed = header?.path("subscriptionButton.subscribeButtonRenderer.subscribed")?.asBoolean ?: false

            // Parse content sections (reuse home section parser)
            val homePage = InnerTubeParser.parseHomePage(response)

            ArtistPage(
                artist = OnlineArtist(
                    browseId = browseId,
                    title = title,
                    thumbnailUrl = finalThumbnail,
                    subscriberCount = combinedSubCount.takeIf { it.isNotBlank() },
                    isSubscribed = isSubscribed
                ),
                sections = homePage.sections,
                description = description
            )
        }
    }

    // ========== Playlists ==========

    /**
     * Get a playlist's details and songs.
     */
    suspend fun playlist(playlistId: String): Result<PlaylistPage> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "VL$playlistId")

            val header = response.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicResponsiveHeaderRenderer"
            )?.asJsonObject
                ?: response.path(
                    "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicEditablePlaylistDetailHeaderRenderer.header.musicResponsiveHeaderRenderer"
                )?.asJsonObject
                ?: response.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicResponsiveHeaderRenderer"
                )?.asJsonObject
                ?: response.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicEditablePlaylistDetailHeaderRenderer.header.musicResponsiveHeaderRenderer"
                )?.asJsonObject

            val title = header?.path("title.runs.0.text")?.asString ?: "Playlist"
            val authorRun = header?.path("straplineTextOne.runs.0")?.asJsonObject
            val author = authorRun?.let {
                OnlineArtistRef(
                    name = it.get("text")?.asString ?: "",
                    id = it.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                )
            }
            val thumbnail = InnerTubeParser.getHighResThumbnailUrl(
                header?.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
            )
            val songCount = header?.path("secondSubtitle.runs.0.text")?.asString

            // Parse songs
            var songContents = response.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
            )?.asJsonArray
            if (songContents == null) {
                songContents = response.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
                )?.asJsonArray
            }

            val songs = mutableListOf<OnlineSong>()
            songContents?.forEach { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val renderer = obj.getAsJsonObject("musicResponsiveListItemRenderer") ?: return@forEach
                val flexColumns = renderer.getAsJsonArray("flexColumns")
                val songTitle = flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                val videoId = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint.videoId")?.asString
                    ?: renderer.path("navigationEndpoint.watchEndpoint.videoId")?.asString
                    ?: flexColumns.getOrNull(0)?.asJsonObject
                        ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint.videoId")?.asString
                    ?: return@forEach
                val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
                val artistText = subtitleRuns?.joinToString("") { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                } ?: ""
                val fixedColumns = renderer.getAsJsonArray("fixedColumns")
                val duration = fixedColumns?.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFixedColumnRenderer.text.runs.0.text")?.asString
                val songThumbnail = InnerTubeParser.getHighResThumbnailUrl(
                    renderer.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                )

                songs.add(OnlineSong(
                    videoId = videoId,
                    title = songTitle,
                    artist = artistText.trim(),
                    duration = duration,
                    durationMs = parseDuration(duration),
                    thumbnailUrl = songThumbnail
                ))
            }

            var currentContinuation = response.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.continuations.0.nextContinuationData.continuation"
            )?.asString
            if (currentContinuation == null) {
                currentContinuation = response.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.continuations.0.nextContinuationData.continuation"
                )?.asString
            }

            PlaylistPage(
                playlist = OnlinePlaylist(
                    playlistId = playlistId,
                    title = title,
                    author = author,
                    songCount = songCount ?: "${songs.size} songs",
                    thumbnailUrl = thumbnail
                ),
                songs = songs,
                continuation = currentContinuation
            )
        }
    }

    // ========== Lyrics ==========

    /**
     * Fetch lyrics for a song by first getting the lyrics browseId from the next endpoint,
     * then fetching the actual lyrics via browse.
     */
    suspend fun lyrics(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val nextResponse = InnerTubeClient.next(videoId)
            val tabs = nextResponse.path("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs")?.asJsonArray

            // Lyrics tab is typically the second tab (index 1)
            var lyricsBrowseId: String? = null
            tabs?.forEach { tab ->
                val tabRenderer = tab.asJsonObject?.getAsJsonObject("tabRenderer")
                val endpoint = tabRenderer?.path("endpoint.browseEndpoint")?.asJsonObject
                val browseId = endpoint?.get("browseId")?.asString
                if (browseId != null && browseId.startsWith("MPLYt")) {
                    lyricsBrowseId = browseId
                }
            }

            if (lyricsBrowseId == null) throw IllegalStateException("No lyrics available for $videoId")

            val lyricsResponse = InnerTubeClient.browse(lyricsBrowseId)
            InnerTubeParser.parseLyrics(lyricsResponse)
        }
    }

    // ========== Start Radio ==========

    /**
     * Start a radio (mix) based on a song, returning the full auto-generated playlist.
     */
    suspend fun startRadio(videoId: String): Result<List<OnlineSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = "RDAMVM$videoId"
            val response = InnerTubeClient.next(videoId, playlistId = playlistId)
            val songs = mutableListOf<OnlineSong>()

            val playlistContents = response.path("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.0.tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer.contents")?.asJsonArray

            playlistContents?.forEach { content ->
                val renderer = content.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: return@forEach
                val id = renderer.get("videoId")?.asString ?: return@forEach
                val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
                val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
                val lengthText = renderer.path("lengthText.runs.0.text")?.asString
                val thumb = InnerTubeParser.getHighResThumbnailUrl(
                    renderer.path("thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                )

                songs.add(OnlineSong(
                    videoId = id,
                    title = titleText,
                    artist = shortByline.trim(),
                    duration = lengthText,
                    durationMs = parseDuration(lengthText),
                    thumbnailUrl = thumb
                ))
            }
            songs
        }
    }

    // ========== Playlist Management ==========

    /**
     * Create a new playlist on the user's YouTube Music account.
     */
    suspend fun createPlaylist(
        title: String,
        description: String = "",
        privacyStatus: String = "PRIVATE",
        videoIds: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.createPlaylist(title, description, privacyStatus, videoIds)
            response.get("playlistId")?.asString ?: throw IllegalStateException("Failed to create playlist")
        }
    }

    /**
     * Delete a playlist from the user's YouTube Music library.
     */
    suspend fun deletePlaylist(playlistId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.deletePlaylist(playlistId)
            response.has("command") || response.has("responseContext")
        }
    }

    /**
     * Add songs to an existing YouTube Music playlist.
     */
    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.addToPlaylist(playlistId, videoIds)
            response.has("status") || response.has("playlistEditResults") || response.has("responseContext")
        }
    }

    // ========== Subscriptions ==========

    suspend fun subscribeArtist(channelId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.subscribe(channelId)
            if (response.has("error")) {
                val errorMsg = response.getAsJsonObject("error")?.get("message")?.asString ?: "Subscription failed"
                throw Exception(errorMsg)
            }
            if (response.entrySet().isEmpty()) {
                throw Exception("Empty response from subscribe API")
            }
            true
        }
    }

    /**
     * Unsubscribe from an artist on YouTube Music.
     */
    suspend fun unsubscribeArtist(channelId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.unsubscribe(channelId)
            if (response.has("error")) {
                val errorMsg = response.getAsJsonObject("error")?.get("message")?.asString ?: "Unsubscription failed"
                throw Exception(errorMsg)
            }
            if (response.entrySet().isEmpty()) {
                throw Exception("Empty response from unsubscribe API")
            }
            true
        }
    }

    // ========== Related / Next ==========

    /**
     * Get related songs for a given video.
     */
    suspend fun relatedSongs(videoId: String): Result<List<OnlineSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.next(videoId)
            val songs = mutableListOf<OnlineSong>()

            val tabs = response.path("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs")?.asJsonArray
            val upNextTab = tabs?.getOrNull(0)?.asJsonObject
            val playlistContents = upNextTab?.path("tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer.contents")?.asJsonArray

            playlistContents?.forEach { content ->
                val renderer = content.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: return@forEach
                val id = renderer.get("videoId")?.asString ?: return@forEach
                val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
                val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
                val lengthText = renderer.path("lengthText.runs.0.text")?.asString
                val thumb = InnerTubeParser.getHighResThumbnailUrl(
                    renderer.path("thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                )

                songs.add(OnlineSong(
                    videoId = id,
                    title = titleText,
                    artist = shortByline.trim(),
                    duration = lengthText,
                    durationMs = parseDuration(lengthText),
                    thumbnailUrl = thumb
                ))
            }
            songs
        }
    }

    // ========== Library & Account ==========

    /**
     * Browse a specific section using browseId and params.
     * Often returns a list of items similar to a playlist or artist page.
     * For simplicity, we can parse it as a PlaylistPage if it contains items.
     */
    suspend fun section(browseId: String, params: String?): Result<HomeSection> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = browseId, params = params)
            // A section often has the same structure as a playlist (a list of items)
            InnerTubeParser.parseSectionPage(response) ?: throw Exception("Failed to parse section")
        }
    }

    /**
     * Fetch the current user's profile details (name, avatar)
     */
    suspend fun accountProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.accountMenu()
            InnerTubeParser.parseAccountProfile(response)
                ?: throw Exception("Failed to parse user profile")
        }
    }

    /**
     * Fetch user's library playlists
     */
    suspend fun libraryPlaylists(): Result<List<OnlinePlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_liked_playlists")
            val list = InnerTubeParser.parseLibraryPlaylists(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryPlaylistsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching playlist continuation", e)
                    break
                }
            }
            list
        }
    }

    /**
     * Fetch user's library artists (followed/subscribed artists)
     */
    suspend fun libraryArtists(): Result<List<OnlineArtist>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_library_corpus_artists")
            val list = InnerTubeParser.parseLibraryArtists(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryArtistsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching artist continuation", e)
                    break
                }
            }
            list
        }
    }

    suspend fun libraryAlbums(): Result<List<OnlineAlbum>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_library_corpus_albums")
            val list = InnerTubeParser.parseLibraryAlbums(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryAlbumsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching album continuation", e)
                    break
                }
            }
            list
        }
    }

    /**
     * Fetch user's library podcasts (subscribed podcasts)
     */
    suspend fun libraryPodcasts(): Result<List<OnlinePlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse(browseId = "FEmusic_library_corpus_podcasts")
            val list = InnerTubeParser.parseLibraryPlaylists(response).toMutableList()
            var continuation = InnerTubeParser.extractLibraryContinuation(response)
            var iterations = 0
            while (continuation != null && iterations < 25) {
                iterations++
                try {
                    val nextResponse = InnerTubeClient.browse(continuation = continuation)
                    val (items, nextToken) = InnerTubeParser.extractContinuationItemsAndNextToken(nextResponse)
                    if (items != null && items.size() > 0) {
                        list.addAll(InnerTubeParser.parseLibraryPlaylistsList(items))
                    }
                    continuation = nextToken
                } catch (e: Exception) {
                    Log.e("YouTubeMusic", "Error fetching podcast continuation", e)
                    break
                }
            }
            list
        }
    }

    /**
     * Prefetch a stream in background to speed up playback switches
     */
    fun prefetchStream(videoId: String?) {
        if (videoId.isNullOrEmpty()) return
        com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.prefetchBatch(
            listOf(videoId), 
            com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.PrefetchPriority.LOW
        )
    }

    /**
     * High level convenience to kick off pre-caching for upcoming track
     */
    fun preCacheNextSong(song: com.codetrio.spatialflow.model.SongItem) {
        prefetchStream(song.videoId)
    }

    // ========== Auth / Account ==========

    /**
     * Fetch the authenticated user's YouTube Music listening history.
     */
    suspend fun history(): Result<List<OnlineSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.browse("FEmusic_history")
            InnerTubeParser.parseHistory(response)
        }
    }

    /**
     * Set authentication cookie for logged-in features.
     */
    fun setCookie(cookie: String?) {
        InnerTubeClient.cookie = cookie
    }

    // ========== Utility ==========

    private fun parseDuration(duration: String?): Long {
        if (duration == null) return 0
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0
            }
        } catch (_: Exception) { 0 }
    }

    @JvmStatic
    fun fetchAndAppendRelatedSongs(viewModel: com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel, videoId: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = relatedSongs(videoId)
                result.onSuccess { related ->
                    related.forEach { onlineSong ->
                        val songItem = com.codetrio.spatialflow.model.SongItem.createOnlineSong(
                            onlineSong.videoId,
                            onlineSong.title,
                            onlineSong.artist,
                            "", // streamUrl placeholder
                            onlineSong.durationMs,
                            onlineSong.thumbnailUrl,
                            onlineSong.artistId
                        )
                        viewModel.addToQueue(songItem)
                    }
                    Log.d(TAG, "Successfully auto-appended ${related.size} related songs to queue.")
                    viewModel.playNextSong(true)
                }
                result.onFailure { e ->
                    Log.e(TAG, "Failed to fetch related songs for auto-queue", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching related songs", e)
            }
        }
    }

    enum class LikeStatus {
        LIKE, DISLIKE, INDIFFERENT
    }

    /**
     * Update the like status of a song on YouTube Music.
     */
    suspend fun updateLikeStatus(videoId: String, status: LikeStatus): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val response = when (status) {
                LikeStatus.LIKE -> InnerTubeClient.like(videoId)
                LikeStatus.DISLIKE -> InnerTubeClient.dislike(videoId)
                LikeStatus.INDIFFERENT -> InnerTubeClient.removeLike(videoId)
            }
            response.has("actions") || response.has("responseContext")
        }
    }

    /**
     * Fetch the user's authentic last watched position for a track to sync cross-device resumes.
     */
    suspend fun getPlaybackPosition(videoId: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val response = InnerTubeClient.playerWebRemix(videoId)
            val startSeconds = response.path("playerConfig.playbackStartConfig.startSeconds")?.asLong ?: 0L
            startSeconds * 1000L
        }
    }
}
