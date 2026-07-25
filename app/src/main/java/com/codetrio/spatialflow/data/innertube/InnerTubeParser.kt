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
                val cardShelf = obj.getAsJsonObject("musicCardShelfRenderer")
                val shelf = obj.getAsJsonObject("musicShelfRenderer")
                    ?: obj.getAsJsonObject("itemSectionRenderer")
                    ?: obj.getAsJsonObject("gridRenderer")

                if (cardShelf != null) {
                    val (topResult, cardItems) = parseCardShelf(cardShelf)
                    topResult?.let { items.add(it) }
                    items.addAll(cardItems)
                } else if (shelf != null) {
                    val shelfTitle = shelf.path("title.runs.0.text")?.asString
                    if (!shelfTitle.isNullOrBlank()) {
                        items.add(SearchItem.Header(shelfTitle))
                    }
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

    private fun parseCardShelf(cardShelf: JsonObject): Pair<SearchItem.TopResult?, List<SearchItem>> {
        val items = mutableListOf<SearchItem>()
        var topResult: SearchItem.TopResult? = null

        try {
            val title = cardShelf.path("title.runs.0.text")?.asString ?: ""
            val subtitleRuns = cardShelf.path("subtitle.runs")?.asJsonArray
            val subtitle = subtitleRuns?.joinToString("") { run ->
                run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
            } ?: ""

            val itemType = subtitle.split(" • ").firstOrNull()?.trim() ?: "Song"

            val thumbnail = cardShelf.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val titleNav = cardShelf.path("title.runs.0.navigationEndpoint")?.asJsonObject
            val playNav = cardShelf.path("buttons.0.musicPlayButtonRenderer.playNavigationEndpoint")?.asJsonObject
            val browseEndpoint = titleNav?.getAsJsonObject("browseEndpoint")
                ?: playNav?.getAsJsonObject("browseEndpoint")
            val watchEndpoint = playNav?.getAsJsonObject("watchEndpoint")
                ?: titleNav?.getAsJsonObject("watchEndpoint")

            var onlineSong: OnlineSong? = null
            var onlineAlbum: OnlineAlbum? = null
            var onlineArtist: OnlineArtist? = null
            var onlinePlaylist: OnlinePlaylist? = null

            if (browseEndpoint != null) {
                val browseId = browseEndpoint.get("browseId")?.asString ?: ""
                val pageType = browseEndpoint.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.asString ?: ""

                when {
                    pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") || itemType.equals("Artist", ignoreCase = true) || subtitle.contains("Artist", ignoreCase = true) -> {
                        onlineArtist = OnlineArtist(
                            browseId = browseId,
                            title = title,
                            thumbnailUrl = thumbnail,
                            subscriberCount = subtitle
                        )
                    }
                    pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb") || itemType.equals("Album", ignoreCase = true) -> {
                        onlineAlbum = OnlineAlbum(
                            browseId = browseId,
                            title = title,
                            artists = listOf(OnlineArtistRef(subtitle.split(" • ").getOrNull(1) ?: "")),
                            thumbnailUrl = thumbnail
                        )
                    }
                    pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL") || browseId.startsWith("PL") || itemType.equals("Playlist", ignoreCase = true) -> {
                        onlinePlaylist = OnlinePlaylist(
                            playlistId = browseId.removePrefix("VL"),
                            title = title,
                            thumbnailUrl = thumbnail
                        )
                    }
                }
            }

            if (onlineArtist == null && onlineAlbum == null && onlinePlaylist == null && watchEndpoint != null) {
                val videoId = watchEndpoint.get("videoId")?.asString
                if (videoId != null) {
                    val artistText = subtitle.split(" • ").getOrNull(1) ?: ""
                    onlineSong = OnlineSong(
                        videoId = videoId,
                        title = title,
                        artist = artistText,
                        thumbnailUrl = thumbnail
                    )
                }
            }

            if (title.isNotEmpty()) {
                topResult = SearchItem.TopResult(
                    title = title,
                    subtitle = subtitle,
                    itemType = itemType,
                    thumbnailUrl = thumbnail,
                    song = onlineSong,
                    album = onlineAlbum,
                    artist = onlineArtist,
                    playlist = onlinePlaylist
                )
            }

            val contents = cardShelf.getAsJsonArray("contents")
            if (contents != null && contents.size() > 0) {
                items.add(SearchItem.Header("MORE FROM YOUTUBE"))
                contents.forEach { child ->
                    val childObj = child.asJsonObject
                    parseSearchItem(childObj)?.let { items.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse musicCardShelfRenderer: ${e.message}")
        }

        return Pair(topResult, items)
    }

    private fun parseSearchItem(item: JsonObject): SearchItem? {
        val renderer = item.getAsJsonObject("musicResponsiveListItemRenderer") ?: return null

        return try {
            val flexColumns = renderer.getAsJsonArray("flexColumns")
            val title = flexColumns?.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString ?: return null

            // Detect inline badges or type indicators
            var detectedBadge: String? = null
            var detectedTypeText: String? = null

            val firstSubtitleRun = flexColumns.getOrNull(1)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")?.asString?.trim()

            if (firstSubtitleRun != null) {
                if (firstSubtitleRun.equals("REMIX", ignoreCase = true) || firstSubtitleRun.contains("REMIX", ignoreCase = true)) {
                    detectedBadge = "REMIX"
                } else if (firstSubtitleRun.equals("Song", ignoreCase = true) || firstSubtitleRun.equals("Video", ignoreCase = true) || firstSubtitleRun.equals("EP", ignoreCase = true)) {
                    detectedTypeText = firstSubtitleRun
                }
            }
            if (detectedBadge == null && (title.contains("Remix", ignoreCase = true) || title.contains("Bass Boosted", ignoreCase = true))) {
                detectedBadge = "REMIX"
            }

            val overlay = renderer.path("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint")?.asJsonObject

            val titleBrowseEndpoint = flexColumns.getOrNull(0)?.asJsonObject
                ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.browseEndpoint")?.asJsonObject
                ?: renderer.path("navigationEndpoint.browseEndpoint")?.asJsonObject

            val browseEndpoint = titleBrowseEndpoint
                ?: renderer.path("navigationEndpoint.browseEndpoint")?.asJsonObject

            val watchEndpoint = overlay?.getAsJsonObject("watchEndpoint")
                ?: renderer.path("navigationEndpoint.watchEndpoint")?.asJsonObject
                ?: flexColumns.getOrNull(0)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint.watchEndpoint")?.asJsonObject

            // 1. First check browseEndpoint to detect Artist, Album, Playlist
            if (browseEndpoint != null) {
                val browseId = browseEndpoint.get("browseId")?.asString ?: ""
                val pageType = browseEndpoint.path("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")?.asString ?: ""

                val subtitleRuns = flexColumns.getOrNull(1)?.asJsonObject
                    ?.path("musicResponsiveListItemFlexColumnRenderer.text.runs")?.asJsonArray
                val subtitle = subtitleRuns?.joinToString("") { run ->
                    run.takeIf { run.isJsonObject }?.asJsonObject?.get("text")?.asString ?: ""
                } ?: ""
                val thumbnail = parseThumbnail(renderer)

                val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" || 
                        pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || 
                        browseId.startsWith("UC") || 
                        browseId.startsWith("FEmusic_artist") ||
                        firstSubtitleRun?.equals("Artist", ignoreCase = true) == true ||
                        subtitle.contains("Artist", ignoreCase = true) ||
                        subtitle.contains("subscriber", ignoreCase = true)

                val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" || 
                        browseId.startsWith("MPREb") ||
                        firstSubtitleRun?.equals("Album", ignoreCase = true) == true ||
                        firstSubtitleRun?.equals("EP", ignoreCase = true) == true ||
                        firstSubtitleRun?.equals("Single", ignoreCase = true) == true

                val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || 
                        browseId.startsWith("VL") || 
                        browseId.startsWith("PL") ||
                        firstSubtitleRun?.equals("Playlist", ignoreCase = true) == true

                when {
                    isArtist -> {
                        val subs = subtitle.split(" • ").firstOrNull { it.contains("subscriber", ignoreCase = true) }
                        return SearchItem.Artist(OnlineArtist(
                            browseId = browseId,
                            title = title,
                            thumbnailUrl = thumbnail,
                            subscriberCount = subs ?: subtitle
                        ))
                    }
                    isAlbum -> {
                        val artistName = subtitle.split(" • ").firstOrNull { it.lowercase() != "album" && !it.matches(Regex("\\d{4}")) } ?: ""
                        return SearchItem.Album(OnlineAlbum(
                            browseId = browseId,
                            title = title,
                            artists = listOf(OnlineArtistRef(artistName)),
                            thumbnailUrl = thumbnail,
                            year = subtitle.split(" • ").lastOrNull()?.toIntOrNull()
                        ))
                    }
                    isPlaylist -> {
                        return SearchItem.Playlist(OnlinePlaylist(
                            playlistId = browseId.removePrefix("VL"),
                            title = title,
                            thumbnailUrl = thumbnail,
                            songCount = subtitle.split(" • ").lastOrNull()
                        ))
                    }
                }
            }

            // 2. Fall back to Song if watchEndpoint is non-null
            if (watchEndpoint != null) {
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

                return SearchItem.Song(
                    song = OnlineSong(
                        videoId = videoId,
                        title = title,
                        artist = parsedArtist.trim(),
                        artistId = parsedArtistId,
                        albumName = parsedAlbumName,
                        albumId = parsedAlbumId,
                        duration = durationText.trim().takeIf { it.isNotEmpty() },
                        durationMs = parseDuration(durationText.trim()),
                        thumbnailUrl = thumbnail
                    ),
                    badge = detectedBadge,
                    typeText = detectedTypeText
                )
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse search item: ${e.message}")
            null
        }
    }

    // ========== Player Parsing ==========

    fun parsePlayerResponse(json: JsonObject, parseStreams: Boolean = true): PlayerResult? {
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

            val animatedThumbnailUrl = findAnimatedThumbnailUrl(json)

            val allFormats = mutableListOf<JsonElement>()
            if (parseStreams) {
                streamingData.getAsJsonArray("adaptiveFormats")?.let { allFormats.addAll(it) }
                streamingData.getAsJsonArray("formats")?.let { allFormats.addAll(it) }
            }

            var bestStream: StreamData? = null
            var highestBitrate = -1
            var fallbackStream: StreamData? = null
            var fallbackBitrate = -1

            allFormats.forEach { format ->
                val obj = format.asJsonObject
                val mimeType = obj.get("mimeType")?.asString ?: ""
                
                val isAudioOnly = mimeType.startsWith("audio/")
                val isVideo = mimeType.startsWith("video/")
                
                // Keep audio and video streams. We'll prioritize audio, but use video as a fallback if no audio exists.
                if (!isAudioOnly && !isVideo) {
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
                if (url != null) {
                    if (isAudioOnly) {
                        if (bitrate > highestBitrate) {
                            highestBitrate = bitrate
                            bestStream = StreamData(
                                url = url,
                                mimeType = mimeType,
                                bitrate = bitrate,
                                contentLength = obj.get("contentLength")?.asLong,
                                audioQuality = obj.get("audioQuality")?.asString
                            )
                        }
                    } else if (bestStream == null && isVideo) {
                        // If we haven't found an audio stream yet, keep track of the best video stream as a fallback
                        if (bitrate > fallbackBitrate) {
                            fallbackBitrate = bitrate
                            fallbackStream = StreamData(
                                url = url,
                                mimeType = mimeType,
                                bitrate = bitrate,
                                contentLength = obj.get("contentLength")?.asLong,
                                audioQuality = obj.get("audioQuality")?.asString
                            )
                        }
                    }
                }
            }

            // Final load: Use best audio stream, or fallback to video stream if absolutely necessary
            val finalStream = bestStream ?: fallbackStream
            val streams = mutableListOf<StreamData>()
            finalStream?.let { streams.add(it) }

            Log.d(TAG, "Parsed player: $videoId — ${streams.size} audio streams from ${allFormats.size} total formats")

            val playbackTracking = json.getAsJsonObject("playbackTracking")

            return PlayerResult(
                videoId = videoId,
                title = title,
                artist = author,
                thumbnailUrl = thumbnail,
                animatedThumbnailUrl = animatedThumbnailUrl,
                durationMs = durationMs,
                streams = streams,
                playbackUrl = bestStream?.url,
                watchtimeUrl = playbackTracking?.path("videostatsWatchtimeUrl.baseUrl")?.asString,
                likesCount = null
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
            var contents = json.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents"
            )?.asJsonArray
            if (contents == null) {
                contents = json.path(
                    "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents"
                )?.asJsonArray
            }
            if (contents == null) {
                contents = json.path(
                    "contents.singleColumnBrowseResultsRenderer.tabs.1.tabRenderer.content.sectionListRenderer.contents"
                )?.asJsonArray
            }
            if (contents == null) {
                contents = json.path(
                    "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents"
                )?.asJsonArray
            }
            if (contents == null) {
                contents = json.path(
                    "contents.singleColumnBrowseResultsRenderer.contents.0.sectionListRenderer.contents"
                )?.asJsonArray
            }
            if (contents == null) {
                contents = json.path("contents.sectionListRenderer.contents")?.asJsonArray
            }

            contents?.forEach { section ->
                parseHomeSection(section.asJsonObject)?.let { sections.add(it) }
            }

            // Handle continuations
            var continuation = json.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.continuations.0.nextContinuationData.continuation"
            )?.asString
            if (continuation == null) {
                continuation = json.path(
                    "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.continuations.0.nextContinuationData.continuation"
                )?.asString
            }

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
            var chips = json.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.header.chipCloudRenderer.chips"
            )?.asJsonArray
            if (chips == null) {
                chips = json.path(
                    "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.header.chipCloudRenderer.chips"
                )?.asJsonArray
            }
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

    fun parseMoodsAndGenresPage(json: JsonObject): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        try {
            val contents = json.path(
                "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents"
            )?.asJsonArray

            contents?.forEach { section ->
                val gridRenderer = section.asJsonObject.getAsJsonObject("gridRenderer")
                val carouselRenderer = section.asJsonObject.getAsJsonObject("musicCarouselShelfRenderer")
                
                val title = section.asJsonObject.path("gridRenderer.header.gridHeaderRenderer.title.runs.0.text")?.asString
                    ?: section.asJsonObject.path("musicCarouselShelfRenderer.header.musicCarouselShelfBasicHeaderRenderer.title.runs.0.text")?.asString
                    ?: "Categories"
                
                val items = mutableListOf<SearchItem>()
                val renderItems = gridRenderer?.getAsJsonArray("items")
                    ?: carouselRenderer?.getAsJsonArray("contents")
                
                renderItems?.forEach { item ->
                    val buttonRenderer = item.asJsonObject.getAsJsonObject("musicNavigationButtonRenderer")
                    if (buttonRenderer != null) {
                        val text = buttonRenderer.path("buttonText.runs.0.text")?.asString ?: return@forEach
                        val browseId = buttonRenderer.path("clickCommand.browseEndpoint.browseId")?.asString ?: return@forEach
                        val params = buttonRenderer.path("clickCommand.browseEndpoint.params")?.asString
                        
                        var thumbnail: String? = null
                        var colorVal: Long? = null
                        
                        // Primary color source: solid.leftStripeColor (ARGB u32)
                        // This is the main field returned by WEB_REMIX for mood/genre buttons
                        colorVal = buttonRenderer.path("solid.leftStripeColor")?.asLong
                        
                        // Fallback: solidColorBackgroundAndImageRenderer (some client versions)
                        val solidColorBgAndImage = buttonRenderer.getAsJsonObject("solidColorBackgroundAndImageRenderer")
                        if (solidColorBgAndImage != null) {
                            thumbnail = solidColorBgAndImage.path("image.musicThumbnailRenderer.thumbnail.thumbnails")
                                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                ?: solidColorBgAndImage.path("image.thumbnail.thumbnails")
                                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                ?: solidColorBgAndImage.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                            
                            if (colorVal == null) {
                                colorVal = solidColorBgAndImage.path("color.color")?.asLong
                                    ?: solidColorBgAndImage.path("solidColor.color")?.asLong
                                    ?: solidColorBgAndImage.path("solidColor")?.asLong
                            }
                        }
                        // Fallback thumbnail paths
                        if (thumbnail == null) {
                            thumbnail = buttonRenderer.path("thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")
                                ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                ?: buttonRenderer.path("thumbnail.thumbnails")
                                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                ?: buttonRenderer.path("icon.thumbnails")
                                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.get("url")?.asString
                        }
                        
                        items.add(SearchItem.Playlist(
                            OnlinePlaylist(
                                playlistId = "GENRE::$browseId::${params ?: ""}",
                                title = text,
                                thumbnailUrl = getHighResThumbnailUrl(thumbnail),
                                color = colorVal
                            )
                        ))
                    }
                }
                
                if (items.isNotEmpty()) {
                    sections.add(HomeSection(title, items))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing moods and genres page", e)
        }
        return sections
    }

    private fun parseHomeSection(section: JsonObject): HomeSection? {
        val carousel = section.getAsJsonObject("musicCarouselShelfRenderer") 
            ?: section.getAsJsonObject("musicImmersiveCarouselShelfRenderer")
            ?: return null
            
        val header = carousel.path(
            "header.musicCarouselShelfBasicHeaderRenderer.title.runs.0.text"
        )?.asString 
            ?: carousel.path("header.musicImmersiveCarouselShelfHeaderRenderer.title.runs.0.text")?.asString
            ?: return null

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

    private fun findAnimatedThumbnailUrl(element: JsonElement): String? {
        if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                val result = findAnimatedThumbnailUrl(item)
                if (result != null) return result
            }
        } else if (element.isJsonObject) {
            val obj = element.asJsonObject
            
            // Specifically look for YouTube Music's animated renderer
            if (obj.has("musicAnimatedThumbnailRenderer")) {
                val animatedRenderer = obj.getAsJsonObject("musicAnimatedThumbnailRenderer")
                val thumbnails = animatedRenderer?.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                if (thumbnails != null && thumbnails.size() > 0) {
                    return thumbnails.get(0).asJsonObject.get("url")?.asString
                }
            }

            // Look for any thumbnail that is an mp4 or webp
            if (obj.has("thumbnails")) {
                val arr = obj.getAsJsonArray("thumbnails")
                for (i in 0 until arr.size()) {
                    val url = arr.get(i).asJsonObject.get("url")?.asString
                    if (url != null && (url.contains(".mp4") || url.contains("sqp="))) {
                        return url
                    }
                }
            }

            for ((_, value) in obj.entrySet()) {
                val result = findAnimatedThumbnailUrl(value)
                if (result != null) return result
            }
        }
        return null
    }

    private fun getCleanArtist(subtitleText: String): String {
        val ignoredTypes = setOf("song", "video", "single", "ep", "album", "artist", "playlist")
        val parts = subtitleText.split(" • ", "   ").map { it.trim() }
        val cleanParts = parts.filter { it.lowercase() !in ignoredTypes && it.isNotEmpty() && !it.matches(Regex("(\\d+:)?\\d+:\\d+")) }
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

private val wHPathRegex = Regex("w\\d+-h\\d+")
private val wHParamRegex = Regex("=w\\d+-h\\d+")
private val sParamRegex = Regex("=s\\d+")
private val brokenSAppendRegex = Regex("-c-k-c0x[0-9a-fA-F]+")

fun String.resize(width: Int? = null, height: Int? = null): String {
    if (width == null && height == null) return this
    val isGoogleCdn = contains("googleusercontent.com") || contains("ggpht.com")

    if (isGoogleCdn) {
        val w = width ?: height!!
        val h = height ?: width!!
        
        if (wHPathRegex.containsMatchIn(this)) {
            return replace(wHPathRegex, "w$w-h$h")
        }
        wHParamRegex.find(this)?.let {
            return "${split("=w")[0]}=w$w-h$h-p-l90-rj"
        }
        sParamRegex.find(this)?.let { match ->
            val before = substring(0, match.range.first)
            val after = substring(match.range.last + 1)
            return "$before=s${maxOf(w, h)}${after.replace(brokenSAppendRegex, "")}"
        }
    }
    return this
}
