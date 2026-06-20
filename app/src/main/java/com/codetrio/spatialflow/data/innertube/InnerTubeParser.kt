package com.codetrio.spatialflow.data.innertube

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Parses InnerTube API JSON responses into our clean data models.
 * Handles the deeply nested, inconsistent response structures from YouTube Music.
 */
object InnerTubeParser {

    private const val TAG = "InnerTubeParser"

    // ========== Search & Explore ==========

    fun parseSectionPage(response: JsonObject): HomeSection? {
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

        val title = header?.path("title.runs.0.text")?.asString ?: "Section"
        
        // Parse items
        var itemContents = response.path(
            "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
        )?.asJsonArray
        if (itemContents == null) {
            itemContents = response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items"
            )?.asJsonArray
        }
        if (itemContents == null) {
            itemContents = response.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items"
            )?.asJsonArray
        }

        val items = mutableListOf<SearchItem>()
        itemContents?.forEach { content ->
            val obj = content.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val twoRowRenderer = obj.getAsJsonObject("musicTwoRowItemRenderer")
            val listRenderer = obj.getAsJsonObject("musicResponsiveListItemRenderer")
            
            if (twoRowRenderer != null) {
                parseTwoRowItem(twoRowRenderer)?.let { items.add(it) }
            } else if (listRenderer != null) {
                parseSearchItem(obj)?.let { items.add(it) }
            }
        }

        if (items.isEmpty() && header == null) return null

        return HomeSection(
            title = title,
            items = items
        )
    }

    fun parseSearchResponse(json: JsonObject): SearchResult {
        val items = mutableListOf<SearchItem>()
        var continuation: String? = null

        try {
            // Initial search response
            val tabs = json.path("contents.tabbedSearchResultsRenderer.tabs")?.asJsonArray
            val tab = tabs?.firstOrNull()?.asJsonObject
            var contents = tab?.path("tabRenderer.content.sectionListRenderer.contents")?.asJsonArray
            if (contents == null) {
                contents = json.path("contents.sectionListRenderer.contents")?.asJsonArray
            }

            contents?.forEach { section ->
                val obj = section.asJsonObject
                val shelf = obj.getAsJsonObject("musicShelfRenderer")
                    ?: obj.getAsJsonObject("musicCardShelfRenderer")
                    ?: obj.getAsJsonObject("itemSectionRenderer")
                    ?: obj.getAsJsonObject("gridRenderer")

                if (shelf != null) {
                    val shelfContents = shelf.getAsJsonArray("contents") ?: shelf.getAsJsonArray("items")
                    shelfContents?.forEach { item ->
                        val itemObj = item.asJsonObject
                        var parsed = parseSearchItem(itemObj)
                        if (parsed == null) {
                            val twoRowRenderer = itemObj.getAsJsonObject("musicTwoRowItemRenderer")
                            if (twoRowRenderer != null) {
                                parsed = parseTwoRowItem(twoRowRenderer)
                            }
                        }
                        parsed?.let { items.add(it) }
                    }
                    if (continuation == null) {
                        continuation = shelf.path("continuations.0.nextContinuationData.continuation")?.asString
                    }
                }
            }

            // Continuation response
            if (items.isEmpty()) {
                var contContents = json.path("continuationContents.musicShelfContinuation.contents")?.asJsonArray
                if (contContents == null) {
                    contContents = json.path("continuationContents.itemSectionContinuation.contents")?.asJsonArray
                }
                contContents?.forEach { item ->
                    val itemObj = item.asJsonObject
                    var parsed = parseSearchItem(itemObj)
                    if (parsed == null) {
                        val twoRowRenderer = itemObj.getAsJsonObject("musicTwoRowItemRenderer")
                        if (twoRowRenderer != null) {
                            parsed = parseTwoRowItem(twoRowRenderer)
                        }
                    }
                    parsed?.let { items.add(it) }
                }
                if (continuation == null) {
                    continuation = json.path("continuationContents.musicShelfContinuation.continuations.0.nextContinuationData.continuation")?.asString
                        ?: json.path("continuationContents.itemSectionContinuation.continuations.0.nextContinuationData.continuation")?.asString
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search response", e)
        }

        return SearchResult(items, continuation)
    }

    private fun parseSearchItem(item: JsonObject): SearchItem? {
        val renderer = item.getAsJsonObject("musicResponsiveListItemRenderer") ?: return null

        return try {
            val flexColumns = renderer.getAsJsonArray("flexColumns")
            val title = flexColumns?.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return null

            // Determine type from overlay/navigation
            val overlay = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint")?.asJsonObject

            val watchEndpoint = overlay?.getAsJsonObject("watchEndpoint")
                ?: renderer.path("navigationEndpoint.watchEndpoint")?.asJsonObject
                ?: flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint")?.asJsonObject
            val browseEndpoint = renderer.path("navigationEndpoint.browseEndpoint")?.asJsonObject
                ?: flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint")?.asJsonObject

            when {
                // Song (has watchEndpoint)
                watchEndpoint != null -> {
                    val videoId = watchEndpoint.get("videoId")?.asString ?: return null
                    val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                        ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
                    
                    var parsedArtist = ""
                    var parsedArtistId: String? = null
                    var parsedAlbumName: String? = null
                    var parsedAlbumId: String? = null
                    var durationText = ""
                    
                    subtitleRuns?.forEach { run ->
                        val runObj = run.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                        val text = runObj.get("text")?.asString ?: ""
                        val endpoint = runObj.path("navigationEndpoint.browseEndpoint")?.asJsonObject
                        
                        if (endpoint != null) {
                            val bId = endpoint.get("browseId")?.asString
                            val pageType = endpoint.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.asString
                            
                            if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL") {
                                if (parsedArtistId == null) {
                                    parsedArtistId = bId
                                    parsedArtist = text
                                }
                            } else if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                                parsedAlbumId = bId
                                parsedAlbumName = text
                            }
                        } else {
                            if (text.trim().matches(Regex("\\d+:\\d+"))) {
                                durationText = text.trim()
                            }
                        }
                    }
                    
                    if (parsedArtist.isBlank()) {
                        val subtitleText = subtitleRuns?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                        parsedArtist = getCleanArtist(subtitleText)
                    }
                    if (durationText.isBlank()) {
                        val fallback = subtitleRuns?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        }?.split(" • ")?.lastOrNull() ?: ""
                        if (fallback.trim().matches(Regex("(\\d+:)?\\d+:\\d+"))) {
                            durationText = fallback.trim()
                        }
                    }
                    
                    val thumbnail = parseThumbnail(renderer)

                    SearchItem.Song(OnlineSong(
                        videoId = videoId,
                        title = title,
                        artist = parsedArtist.trim(),
                        artistId = parsedArtistId,
                        albumName = parsedAlbumName,
                        albumId = parsedAlbumId,
                        duration = durationText.trim().takeIf { it.isNotEmpty() },
                        durationMs = parseDuration(durationText.trim()),
                        thumbnailUrl = thumbnail
                    ))
                }
                // Album or Artist (has browseEndpoint)
                browseEndpoint != null -> {
                    val browseId = browseEndpoint.get("browseId")?.asString ?: return null
                    val pageType = browseEndpoint.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.asString

                    val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                        ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
                    val subtitle = subtitleRuns?.joinToString("") { run ->
                        run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                    } ?: ""
                    val thumbnail = parseThumbnail(renderer)

                    when (pageType) {
                        "MUSIC_PAGE_TYPE_ALBUM" -> {
                            val artistName = subtitle.split(" • ").drop(1).firstOrNull() ?: ""
                            SearchItem.Album(OnlineAlbum(
                                browseId = browseId,
                                title = title,
                                artists = listOf(OnlineArtistRef(artistName)),
                                thumbnailUrl = thumbnail,
                                year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                            ))
                        }
                        "MUSIC_PAGE_TYPE_ARTIST" -> {
                            SearchItem.Artist(OnlineArtist(
                                browseId = browseId,
                                title = title,
                                thumbnailUrl = thumbnail,
                                subscriberCount = subtitle.takeIf { it.isNotBlank() }
                            ))
                        }
                        "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                            SearchItem.Playlist(OnlinePlaylist(
                                playlistId = browseId.removePrefix("VL"),
                                title = title,
                                thumbnailUrl = thumbnail,
                                songCount = subtitle.split(" • ").lastOrNull()
                            ))
                        }
                        else -> {
                            // Try to infer from browseId prefix
                            when {
                                browseId.startsWith("UC") -> {
                                    SearchItem.Artist(OnlineArtist(
                                        browseId = browseId,
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        subscriberCount = subtitle
                                    ))
                                }
                                browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                                    SearchItem.Playlist(OnlinePlaylist(
                                        playlistId = browseId.removePrefix("VL"),
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        songCount = subtitle.split(" • ").lastOrNull()
                                    ))
                                }
                                browseId.startsWith("MPREb") -> {
                                    val artistName = subtitle.split(" • ").drop(1).firstOrNull() ?: ""
                                    SearchItem.Album(OnlineAlbum(
                                        browseId = browseId,
                                        title = title,
                                        artists = listOf(OnlineArtistRef(artistName)),
                                        thumbnailUrl = thumbnail,
                                        year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                                    ))
                                }
                                else -> null
                            }
                        }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse search item: ${e.message}")
            null
        }
    }

    // ========== Player Parsing ==========

    fun parsePlayerResponse(json: JsonObject): PlayerResult? {
        try {
            val playabilityStatus = json.getAsJsonObject("playabilityStatus")
            val status = playabilityStatus?.get("status")?.asString
            if (status != "OK") {
                Log.w(TAG, "Player status: $status — ${playabilityStatus?.get("reason")?.asString}")
                return null
            }

            val videoDetails = json.getAsJsonObject("videoDetails") ?: return null
            val streamingData = json.getAsJsonObject("streamingData") ?: return null

            val videoId = videoDetails.get("videoId")?.asString ?: return null
            val title = videoDetails.get("title")?.asString ?: "Unknown"
            val author = videoDetails.get("author")?.asString ?: "Unknown Artist"
            val durationMs = (videoDetails.get("lengthSeconds")?.asLong ?: 0) * 1000
            val thumbnail = videoDetails.path("thumbnail.thumbnails")?.asJsonArray
                ?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val allFormats = mutableListOf<JsonElement>()
            streamingData.getAsJsonArray("adaptiveFormats")?.let { allFormats.addAll(it) }
            streamingData.getAsJsonArray("formats")?.let { allFormats.addAll(it) }

            var bestStream: StreamData? = null
            var highestBitrate = -1

            allFormats.forEach { format ->
                val obj = format.asJsonObject
                val mimeType = obj.get("mimeType")?.asString ?: ""
                
                // Optimization step: Instant disqualification of non-audio payloads saves huge computation cycles
                if (!mimeType.startsWith("audio/")) {
                    return@forEach
                }

                var url = obj.get("url")?.asString
                val cipher = obj.get("signatureCipher")?.asString ?: obj.get("cipher")?.asString
                val bitrate = obj.get("bitrate")?.asInt ?: 0

                // Logic step: Decipher the raw signature if direct URL is missing
                if (url == null && cipher != null) {
                    try {
                        val params = parseQueryString(cipher)
                        val streamUrl = params["url"]
                        val signature = params["s"]
                        val sp = params["sp"] ?: "sig"
                        if (streamUrl != null && signature != null) {
                            val deobfuscatedSig = org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature)
                            val encodedSig = java.net.URLEncoder.encode(deobfuscatedSig, "UTF-8")
                            val separator = if (streamUrl.contains("?")) "&" else "?"
                            url = "$streamUrl$separator$sp=$encodedSig"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Signature decryption failed for $videoId", e)
                    }
                }

                // Logic step: Deobfuscate the 'n' throttling parameter on EVERY stream to unlock speeds
                if (url != null) {
                    try {
                        url = org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to deobfuscate throttling parameter", e)
                    }
                }

                // Winner takes all architecture: Keep only the highest fidelity stream
                if (url != null && bitrate > highestBitrate) {
                    highestBitrate = bitrate
                    bestStream = StreamData(
                        url = url,
                        mimeType = mimeType,
                        bitrate = bitrate,
                        contentLength = obj.get("contentLength")?.asLong,
                        audioQuality = obj.get("audioQuality")?.asString
                    )
                }
            }

            // Final load: Only output the absolute champion candidate 
            val streams = mutableListOf<StreamData>()
            bestStream?.let { streams.add(it) }

            Log.d(TAG, "Parsed player: $videoId — ${streams.size} audio streams from ${allFormats.size} total formats")

            val tracking = json.getAsJsonObject("playbackTracking")
            val playbackUrl = tracking?.getAsJsonObject("videostatsPlaybackUrl")?.get("baseUrl")?.asString
            val watchtimeUrl = tracking?.getAsJsonObject("videostatsWatchtimeUrl")?.get("baseUrl")?.asString

            return PlayerResult(
                videoId = videoId,
                title = title,
                artist = author,
                thumbnailUrl = thumbnail,
                durationMs = durationMs,
                streams = streams,
                playbackUrl = playbackUrl,
                watchtimeUrl = watchtimeUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing player response", e)
            return null
        }
    }

    // ========== Browse / Home Feed Parsing ==========

    fun parseHomePage(json: JsonObject): HomePage {
        val sections = mutableListOf<HomeSection>()

        try {
            val contents = json.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents"
            )?.asJsonArray

            contents?.forEach { section ->
                parseHomeSection(section.asJsonObject)?.let { sections.add(it) }
            }

            // Handle continuations
            var continuation = json.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.continuations.0.nextContinuationData.continuation"
            )?.asString

            // Parse continuation sections too
            val contSections = json.path("continuationContents.sectionListContinuation.contents")?.asJsonArray
            contSections?.forEach { section ->
                parseHomeSection(section.asJsonObject)?.let { sections.add(it) }
            }
            if (continuation == null) {
                continuation = json.path("continuationContents.sectionListContinuation.continuations.0.nextContinuationData.continuation")?.asString
            }
            // Extract Moods / Categories from Header Chips
            val moods = mutableListOf<String>()
            val chips = json.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.header.chipCloudRenderer.chips"
            )?.asJsonArray
            chips?.forEach { chip ->
                val text = chip.asJsonObject.path("chipCloudChipRenderer.text.runs.0.text")?.asString
                if (!text.isNullOrBlank()) {
                    moods.add(text)
                }
            }

            return HomePage(sections, continuation, moods)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing home page", e)
        }

        return HomePage(sections)
    }

    private fun parseHomeSection(section: JsonObject): HomeSection? {
        val carousel = section.getAsJsonObject("musicCarouselShelfRenderer") ?: return null
        val header = carousel.path(
            "header.musicCarouselShelfBasicHeaderRenderer.title.runs.0.text"
        )?.asString ?: return null

        val items = mutableListOf<SearchItem>()
        val contents = carousel.getAsJsonArray("contents")

        contents?.forEach { content ->
            val obj = content.asJsonObject
            val twoRowRenderer = obj.getAsJsonObject("musicTwoRowItemRenderer")
            val listRenderer = obj.getAsJsonObject("musicResponsiveListItemRenderer")

            if (twoRowRenderer != null) {
                parseTwoRowItem(twoRowRenderer)?.let { items.add(it) }
            } else if (listRenderer != null) {
                parseSearchItem(obj)?.let { items.add(it) }
            }
        }

        if (items.isEmpty()) return null

        val browseEndpoint = carousel.path(
            "header.musicCarouselShelfBasicHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.browseId"
        )?.asString

        val params = carousel.path(
            "header.musicCarouselShelfBasicHeaderRenderer.moreContentButton.buttonRenderer.navigationEndpoint.browseEndpoint.params"
        )?.asString

        return HomeSection(
            title = header,
            items = items,
            browseEndpoint = browseEndpoint,
            params = params
        )
    }

    private fun parseTwoRowItem(renderer: JsonObject): SearchItem? {
        try {
            val title = renderer.path("title.runs.0.text")?.asString ?: return null
            val subtitle = renderer.path("subtitle.runs")?.asJsonArray
                ?.joinToString("") { run ->
                    run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                } ?: ""
            val thumbnail = renderer.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val navEndpoint = renderer.path("navigationEndpoint")?.asJsonObject
            val browseEndpoint = navEndpoint?.getAsJsonObject("browseEndpoint")
            val watchEndpoint = navEndpoint?.getAsJsonObject("watchEndpoint")

            return when {
                watchEndpoint != null -> {
                    val videoId = watchEndpoint.get("videoId")?.asString ?: return null
                    val artistId = renderer.path("subtitle.runs.0.navigationEndpoint.browseEndpoint.browseId")?.asString
                    SearchItem.Song(OnlineSong(
                        videoId = videoId,
                        title = title,
                        artist = getCleanArtist(subtitle),
                        artistId = artistId,
                        thumbnailUrl = thumbnail
                    ))
                }
                browseEndpoint != null -> {
                    val browseId = browseEndpoint.get("browseId")?.asString ?: return null
                    val pageType = browseEndpoint.path(
                        "browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"
                    )?.asString

                    when (pageType) {
                        "MUSIC_PAGE_TYPE_ALBUM" -> {
                            SearchItem.Album(OnlineAlbum(
                                browseId = browseId,
                                title = title,
                                artists = listOf(OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull() ?: "")),
                                thumbnailUrl = thumbnail,
                                year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                            ))
                        }
                        "MUSIC_PAGE_TYPE_ARTIST" -> {
                            SearchItem.Artist(OnlineArtist(
                                browseId = browseId,
                                title = title,
                                thumbnailUrl = thumbnail,
                                subscriberCount = subtitle
                            ))
                        }
                        "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                            SearchItem.Playlist(OnlinePlaylist(
                                playlistId = browseId.removePrefix("VL"),
                                title = title,
                                thumbnailUrl = thumbnail,
                                songCount = subtitle.split(" • ").lastOrNull()
                            ))
                        }
                        else -> {
                            // Try to infer from browseId prefix
                            when {
                                browseId.startsWith("UC") -> {
                                    SearchItem.Artist(OnlineArtist(
                                        browseId = browseId,
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        subscriberCount = subtitle
                                    ))
                                }
                                browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                                    SearchItem.Playlist(OnlinePlaylist(
                                        playlistId = browseId.removePrefix("VL"),
                                        title = title,
                                        thumbnailUrl = thumbnail,
                                        songCount = subtitle.split(" • ").lastOrNull()
                                    ))
                                }
                                browseId.startsWith("MPREb") -> {
                                    SearchItem.Album(OnlineAlbum(
                                        browseId = browseId,
                                        title = title,
                                        artists = listOf(OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull() ?: "")),
                                        thumbnailUrl = thumbnail,
                                        year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                                    ))
                                }
                                else -> null
                            }
                        }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse two-row item: ${e.message}")
            return null
        }
    }

    // ========== Album Page Parsing ==========

    fun parseAlbumPage(json: JsonObject, browseId: String): AlbumPage? {
        try {
            val header = json.path(
                "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicResponsiveHeaderRenderer"
            )?.asJsonObject ?: return null

            val title = header.path("title.runs.0.text")?.asString ?: return null
            val artists = header.path("straplineTextOne.runs")?.asJsonArray
                ?.filterIndexed { i, _ -> i % 2 == 0 }
                ?.map { run ->
                    val obj = run.asJsonObject
                    OnlineArtistRef(
                        name = obj.get("text")?.asString ?: "",
                        id = obj.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                    )
                } ?: emptyList()

            val year = header.path("subtitle.runs")?.asJsonArray
                ?.lastOrNull()?.asJsonObject?.get("text")?.asString?.toIntOrNull()

            val thumbnail = header.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val playlistId = json.path("microformat.microformatDataRenderer.urlCanonical")
                ?.asString?.substringAfterLast("=") ?: ""

            // Parse songs
            val songContents = json.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicShelfRenderer.contents"
            )?.asJsonArray ?: json.path(
                "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.contents"
            )?.asJsonArray

            val songs = songContents?.mapNotNull { item ->
                parseAlbumSong(item.asJsonObject, thumbnail)
            } ?: emptyList()

            return AlbumPage(
                album = OnlineAlbum(
                    browseId = browseId,
                    playlistId = playlistId,
                    title = title,
                    artists = artists,
                    year = year,
                    thumbnailUrl = thumbnail
                ),
                songs = songs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing album page", e)
            return null
        }
    }

    private fun parseAlbumSong(item: JsonObject, fallbackThumbnail: String?): OnlineSong? {
        val renderer = item.getAsJsonObject("musicResponsiveListItemRenderer") ?: return null
        try {
            val flexColumns = renderer.getAsJsonArray("flexColumns")
            val title = flexColumns?.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return null

            val videoId = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint.videoId")?.asString
                ?: flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint.videoId")?.asString
                ?: return null

            val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
            val artist = subtitleRuns?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""

            val fixedColumns = renderer.getAsJsonArray("fixedColumns")
            val duration = fixedColumns?.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFixedColumnRenderer.text.runs.0.text")?.asString

            val thumbnail = parseThumbnail(renderer) ?: fallbackThumbnail

            return OnlineSong(
                videoId = videoId,
                title = title,
                artist = artist.trim(),
                duration = duration,
                durationMs = parseDuration(duration),
                thumbnailUrl = thumbnail
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse album song: ${e.message}")
            return null
        }
    }

    // ========== Account & Library Parsing ==========

    fun parseAccountProfile(json: JsonObject): UserProfile? {
        try {
            val header = json.path("actions.0.openPopupAction.popup.multiPageMenuRenderer.header.activeAccountHeaderRenderer")?.asJsonObject
                ?: return null

            val name = header.path("accountName.runs.0.text")?.asString 
                ?: header.path("accountName.simpleText")?.asString
                ?: "Unknown User"
            val handle = header.path("channelHandle.runs.0.text")?.asString
                ?: header.path("channelHandle.simpleText")?.asString
            val email = header.path("email.runs.0.text")?.asString
                ?: header.path("email.simpleText")?.asString
            val avatarUrl = header.path("accountPhoto.thumbnails")?.asJsonArray
                ?.lastOrNull()?.asJsonObject?.get("url")?.asString

            return UserProfile(
                name = name,
                handle = handle,
                email = email,
                avatarUrl = getHighResThumbnailUrl(avatarUrl)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing account profile", e)
            return null
        }
    }

    fun parseHistory(json: JsonObject): List<OnlineSong> {
        val songs = mutableListOf<OnlineSong>()
        try {
            val sections = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents")?.asJsonArray
            sections?.forEach { section ->
                val shelf = section.asJsonObject.getAsJsonObject("musicShelfRenderer")
                shelf?.getAsJsonArray("contents")?.forEach { item ->
                    val searchItem = parseSearchItem(item.asJsonObject)
                    if (searchItem is SearchItem.Song) {
                        songs.add(searchItem.song)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing history", e)
        }
        return songs
    }

    /**
     * Parse lyrics from a browse response for a lyrics browseId (MPLYt...).
     * Returns the plain text lyrics string.
     */
    fun parseLyrics(json: JsonObject): String {
        try {
            // Primary path: musicDescriptionShelfRenderer
            val description = json.path("contents.sectionListRenderer.contents.0.musicDescriptionShelfRenderer.description.runs")?.asJsonArray
            if (description != null && description.size() > 0) {
                return description.joinToString("") { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                }
            }

            // Fallback: Try plain text description
            val plainText = json.path("contents.sectionListRenderer.contents.0.musicDescriptionShelfRenderer.description.simpleText")?.asString
            if (!plainText.isNullOrEmpty()) return plainText

            // Ultimate fallback: Search for any text content
            val sections = json.path("contents.sectionListRenderer.contents")?.asJsonArray
            sections?.forEach { section ->
                val renderer = section.asJsonObject
                val messageRenderer = renderer.getAsJsonObject("messageRenderer")
                if (messageRenderer != null) {
                    val text = messageRenderer.path("text.runs.0.text")?.asString
                        ?: messageRenderer.path("text.simpleText")?.asString
                    if (!text.isNullOrEmpty()) return text
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing lyrics", e)
        }
        throw IllegalStateException("No lyrics content found in response")
    }



    fun parseLibraryPlaylists(json: JsonObject): List<OnlinePlaylist> {
        val playlists = mutableListOf<OnlinePlaylist>()
        try {
            val items = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray

            if (items != null) {
                return parseLibraryPlaylistsList(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing library playlists", e)
        }
        return playlists
    }

    fun parseLibraryPlaylistsList(items: JsonArray): List<OnlinePlaylist> {
        val playlists = mutableListOf<OnlinePlaylist>()
        items.forEach { itemObj ->
            val item = itemObj.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (item.has("musicTwoRowItemRenderer")) {
                val twoRow = item.getAsJsonObject("musicTwoRowItemRenderer")
                try {
                    val title = twoRow.path("title.runs.0.text")?.asString ?: return@forEach
                    val thumbnail = twoRow.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    val browseId = twoRow.path("navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val subtitle = twoRow.path("subtitle.runs")?.asJsonArray
                        ?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                    playlists.add(OnlinePlaylist(
                        playlistId = browseId.removePrefix("VL"),
                        title = title,
                        thumbnailUrl = thumbnail,
                        songCount = subtitle.split(" • ").lastOrNull()
                    ))
                } catch (_: Exception) {}
            } else if (item.has("musicResponsiveListItemRenderer")) {
                val listItem = item.getAsJsonObject("musicResponsiveListItemRenderer")
                try {
                    val title = listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                    val browseId = listItem.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                        ?: listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val thumbnail = listItem.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    playlists.add(OnlinePlaylist(
                        playlistId = browseId.removePrefix("VL"),
                        title = title,
                        thumbnailUrl = thumbnail,
                        songCount = ""
                    ))
                } catch (_: Exception) {}
            }
        }
        return playlists
    }

    fun parseLibraryArtists(json: JsonObject): List<OnlineArtist> {
        val artists = mutableListOf<OnlineArtist>()
        try {
            val items = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray

            if (items != null) {
                return parseLibraryArtistsList(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing library artists", e)
        }
        return artists
    }

    fun parseLibraryArtistsList(items: JsonArray): List<OnlineArtist> {
        val artists = mutableListOf<OnlineArtist>()
        items.forEach { itemObj ->
            val item = itemObj.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (item.has("musicTwoRowItemRenderer")) {
                val twoRow = item.getAsJsonObject("musicTwoRowItemRenderer")
                try {
                    val title = twoRow.path("title.runs.0.text")?.asString ?: return@forEach
                    val thumbnail = twoRow.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    val browseId = twoRow.path("navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val subtitle = twoRow.path("subtitle.runs")?.asJsonArray
                        ?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                    artists.add(OnlineArtist(
                        browseId = browseId,
                        title = title,
                        thumbnailUrl = thumbnail,
                        subscriberCount = subtitle
                    ))
                } catch (_: Exception) {}
            } else if (item.has("musicResponsiveListItemRenderer")) {
                val listItem = item.getAsJsonObject("musicResponsiveListItemRenderer")
                try {
                    val title = listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                    val browseId = listItem.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                        ?: listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val thumbnail = listItem.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    artists.add(OnlineArtist(
                        browseId = browseId,
                        title = title,
                        thumbnailUrl = thumbnail,
                        subscriberCount = ""
                    ))
                } catch (_: Exception) {}
            }
        }
        return artists
    }

    fun parseLibraryAlbums(json: JsonObject): List<OnlineAlbum> {
        val albums = mutableListOf<OnlineAlbum>()
        try {
            val items = json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.gridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicGridRenderer.items")?.asJsonArray
                ?: json.path("contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray
                ?: json.path("contents.sectionListRenderer.contents.0.musicShelfRenderer.contents")?.asJsonArray

            if (items != null) {
                return parseLibraryAlbumsList(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing library albums", e)
        }
        return albums
    }

    fun parseLibraryAlbumsList(items: JsonArray): List<OnlineAlbum> {
        val albums = mutableListOf<OnlineAlbum>()
        items.forEach { itemObj ->
            val item = itemObj.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (item.has("musicTwoRowItemRenderer")) {
                val twoRow = item.getAsJsonObject("musicTwoRowItemRenderer")
                try {
                    val title = twoRow.path("title.runs.0.text")?.asString ?: return@forEach
                    val thumbnail = twoRow.path("thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    val browseId = twoRow.path("navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val subtitle = twoRow.path("subtitle.runs")?.asJsonArray
                        ?.joinToString("") { run ->
                            run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                        } ?: ""
                    albums.add(OnlineAlbum(
                        browseId = browseId,
                        title = title,
                        artists = listOf(OnlineArtistRef(subtitle.split(" • ").drop(1).firstOrNull() ?: subtitle.split(" • ").firstOrNull() ?: "")),
                        thumbnailUrl = thumbnail,
                        year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                    ))
                } catch (_: Exception) {}
            } else if (item.has("musicResponsiveListItemRenderer")) {
                val listItem = item.getAsJsonObject("musicResponsiveListItemRenderer")
                try {
                    val title = listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return@forEach
                    val browseId = listItem.path("navigationEndpoint.browseEndpoint.browseId")?.asString
                        ?: listItem.path("flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint.browseId")?.asString ?: return@forEach
                    val thumbnail = listItem.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                        ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    albums.add(OnlineAlbum(
                        browseId = browseId,
                        title = title,
                        artists = emptyList(),
                        thumbnailUrl = thumbnail,
                        year = null
                    ))
                } catch (_: Exception) {}
            }
        }
        return albums
    }

    fun extractLibraryContinuation(json: JsonObject): String? {
        val prefix = "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0."
        val prefix2 = "contents.sectionListRenderer.contents.0."
        
        return json.path(prefix + "gridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix + "musicGridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix + "musicShelfRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix2 + "gridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix2 + "musicGridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path(prefix2 + "musicShelfRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents.0.gridRenderer.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0.musicPlaylistShelfRenderer.continuations.0.nextContinuationData.continuation")?.asString
    }

    fun extractContinuationItemsAndNextToken(json: JsonObject): Pair<JsonArray?, String?> {
        val gridItems = json.path("continuationContents.gridContinuation.items")?.asJsonArray
            ?: json.path("continuationContents.musicGridContinuation.items")?.asJsonArray
            ?: json.path("continuationContents.musicShelfContinuation.contents")?.asJsonArray
            ?: json.path("continuationContents.musicPlaylistShelfContinuation.contents")?.asJsonArray
            ?: json.path("continuationContents.musicPlaylistShelfContinuation.items")?.asJsonArray
            
        val nextToken = json.path("continuationContents.gridContinuation.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("continuationContents.musicGridContinuation.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("continuationContents.musicShelfContinuation.continuations.0.nextContinuationData.continuation")?.asString
            ?: json.path("continuationContents.musicPlaylistShelfContinuation.continuations.0.nextContinuationData.continuation")?.asString
            
        return Pair(gridItems, nextToken)
    }

    // ========== Utility ==========

    private fun parseThumbnail(renderer: JsonObject): String? {
        val url = renderer.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
            ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
        return getHighResThumbnailUrl(url)
    }

    /**
     * Replaces YouTube's low-resolution url parameters with high-resolution parameters (1080p)
     */
    fun getHighResThumbnailUrl(url: String?): String? {
        if (url == null) return null

        var finalUrl = url
        if (finalUrl.startsWith("//")) {
            finalUrl = "https:$finalUrl"
        }

        val prefs = try {
            com.codetrio.spatialflow.SpatialFlowApplication.instance.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        } catch (e: Exception) { null }
        val dataSaver = prefs?.getBoolean("data_saver", false) ?: false

        val targetRes = if (dataSaver) "=w540-h540" else "=w1080-h1080"

        // YouTube music album covers usually have a pattern like: =w120-h120-l90-rj
        // We replace any =w... block with =w1080-h1080 or =w540-h540
        if (finalUrl.contains("=w") && finalUrl.contains("-h")) {
            return finalUrl.replace(Regex("=w\\d+-h\\d+"), targetRes)
        }
        
        // Account avatars usually have a pattern like: =s88-c-k-c0x00...
        // We replace any =s88 or =s\d+ with =s1080
        if (finalUrl.contains("=s")) {
            val targetSquareRes = if (dataSaver) "=s540" else "=s1080"
            return finalUrl.replace(Regex("=s\\d+"), targetSquareRes)
        }

        // For ytimg.com video thumbnails, maxresdefault.jpg often 404s if the video isn't 720p+.
        // hqdefault.jpg (480x360) is guaranteed to exist.
        val targetHqRes = "hqdefault.jpg"

        if (finalUrl.contains("sqdefault.jpg")) return finalUrl.replace("sqdefault.jpg", targetHqRes)
        if (finalUrl.contains("mqdefault.jpg")) return finalUrl.replace("mqdefault.jpg", targetHqRes)

        return finalUrl
    }

    /** Parse duration string like "3:45" to milliseconds */
    private fun parseDuration(duration: String?): Long {
        if (duration == null) return 0
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun getCleanArtist(subtitleText: String): String {
        val ignoredTypes = setOf("song", "video", "single", "ep", "album", "artist", "playlist")
        val parts = subtitleText.split(" • ").map { it.trim() }
        val cleanParts = parts.filter { it.lowercase() !in ignoredTypes && it.isNotEmpty() }
        return cleanParts.firstOrNull() ?: "Unknown Artist"
    }

    private fun parseQueryString(query: String): Map<String, String> {
        return try {
            query.split("&").mapNotNull { pairStr ->
                val pair = pairStr.split("=", limit = 2)
                if (pair.size == 2) {
                    val key = java.net.URLDecoder.decode(pair[0], "UTF-8")
                    val value = java.net.URLDecoder.decode(pair[1], "UTF-8")
                    key to value
                } else null
            }.toMap()
        } catch (_: Exception) { emptyMap() }
    }
}

// ========== JSON Navigation Extension ==========

// High-performance statically allocated cache to eliminate thousands of string splits and GC sweeps per parse cycle
private val jsonPathCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

fun JsonElement?.path(path: String): JsonElement? {
    if (this == null) return null
    var current: JsonElement? = this
    
    // Atomically fetch pre-digested path fragments (Instant retrieval, 0 garbage generated)
    val keys = jsonPathCache.computeIfAbsent(path) { it.split(".") }
    
    for (key in keys) {
        if (current == null) return null
        current = when {
            current.isJsonObject -> current.asJsonObject.get(key)
            current.isJsonArray -> {
                val index = key.toIntOrNull()
                if (index != null) current.asJsonArray.getOrNull(index)
                else null
            }
            else -> null
        }
    }
    return current
}

/** Safe getOrNull for JsonArray */
fun JsonArray.getOrNull(index: Int): JsonElement? {
    return if (index in 0 until size()) get(index) else null
}
