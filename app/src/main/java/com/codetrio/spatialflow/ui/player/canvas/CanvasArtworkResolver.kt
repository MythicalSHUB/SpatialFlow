package com.codetrio.spatialflow.ui.player.canvas

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "CanvasArtworkResolver"

/**
 * Coordinates cache lookups and network resolution for canvas motion artwork.
 *
 * Resolution order:
 * 1. [CanvasArtworkPlaybackCache] (local disk cache)
 * 2. [SongCanvasProvider] by song title + artist (with normalized variants)
 * 3. [SongCanvasProvider] by album ID or album title fallback
 */
internal suspend fun resolveCanvasArtworkForPlayback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    albumId: String? = null,
    albumTitleRaw: String? = null,
    storefront: String = "us",
    requireVertical: Boolean = false,
    allowNetwork: Boolean = true,
): CanvasArtwork? {
    // 1. Cache hit
    withContext(Dispatchers.IO) {
        CanvasArtworkPlaybackCache.get(
            mediaId = mediaId,
            preferCachedOnly = !allowNetwork,
        )
    }?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }
        ?.let { return it }

    if (!allowNetwork || mediaId.isBlank()) {
        Log.d(TAG, "Skipping canvas network lookup for mediaId=$mediaId")
        return null
    }

    return withContext(Dispatchers.IO) {
        val fetched = fetchCanvasArtworkForPlayback(
            songTitleRaw = songTitleRaw,
            artistNameRaw = artistNameRaw,
            storefront = storefront,
            requireVertical = requireVertical,
        ) ?: fetchCanvasArtworkByAlbumFallback(
            albumId = albumId,
            albumTitleRaw = albumTitleRaw,
            artistNameRaw = artistNameRaw,
            storefront = storefront,
            requireVertical = requireVertical,
        )

        if (fetched == null) {
            Log.d(TAG, "No playable canvas resolved for mediaId=$mediaId")
            return@withContext null
        }

        CanvasArtworkPlaybackCache.put(mediaId, fetched)
    }
}

internal suspend fun fetchCanvasArtworkForPlayback(
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    val songTitle = normalizeCanvasSongTitle(songTitleRaw)
    val artistName = normalizeCanvasArtistName(artistNameRaw)

    val candidates = linkedSetOf(
        songTitle to artistName,
        songTitleRaw to artistName,
        songTitle to artistNameRaw,
        songTitleRaw to artistNameRaw,
    ).filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }

    return candidates.firstNotNullOfOrNull { (song, artist) ->
        SongCanvasProvider
            .getBySongArtist(song = song, artist = artist, storefront = storefront)
            ?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }
    }
}

private suspend fun fetchCanvasArtworkByAlbumFallback(
    albumId: String?,
    albumTitleRaw: String?,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    albumId?.trim()?.takeIf { it.isNotBlank() }?.let { nonBlankAlbumId ->
        SongCanvasProvider
            .getByAlbumId(nonBlankAlbumId)
            ?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }
            ?.let { return it }
    }

    val albumTitle = albumTitleRaw?.trim().orEmpty()
    val artistName = artistNameRaw.trim()
    if (albumTitle.isBlank() || artistName.isBlank()) return null

    return SongCanvasProvider
        .getBySongArtist(song = albumTitle, artist = artistName, storefront = storefront)
        ?.takeIf { it.hasRequiredCanvasVariant(requireVertical) }
}

internal fun CanvasArtwork.hasRequiredCanvasVariant(requireVertical: Boolean): Boolean =
    if (requireVertical) !preferredVerticalAnimationUrl.isNullOrBlank()
    else !preferredAnimationUrl.isNullOrBlank()

// ───────────── Query normalization ─────────────

internal fun normalizeCanvasSongTitle(raw: String): String {
    val stripped = raw
        .replace(Regex("\\s*\\[[^]]*]"), "")
        .replace(Regex("\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)", RegexOption.IGNORE_CASE), "")
        .replace(
            Regex(
                "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .replace(
            Regex(
                "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .replace(Regex("\\s+"), " ")
        .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun normalizeCanvasArtistName(raw: String): String {
    val first = raw.split(
        Regex(
            "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
            RegexOption.IGNORE_CASE,
        ),
        limit = 2,
    ).firstOrNull().orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
