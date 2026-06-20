package com.codetrio.spatialflow.data.lyrics

/**
 * Normalized track metadata used across all lyrics providers and the decision
 * engine.
 * Created by MetadataRepair from raw SongItem fields.
 */
data class TrackMetadata @JvmOverloads constructor(
    val rawTitle: String = "",
    val rawArtist: String = "",
    val cleanedTitle: String = "",
    val cleanedArtist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val filePath: String = "",
    val videoId: String? = null, // YouTube video ID for InnerTube lyrics
    val version: String = "original", // "original", "remix", "slowed", "live", "cover", "extended", "edit"
    val detectedLanguage: String = "unknown" // "en", "hi", "mr", "ta", "te", "pa", "unknown"
) {
    /**
     * Generates a normalized cache key for this track.
     * Uses cleaned artist + title + duration bucket (10-second granularity).
     */
    fun getCacheKey(): String {
        val artist = cleanedArtist.lowercase().trim()
        val title = cleanedTitle.lowercase().trim()
        val durationBucket = durationMs / 10000 // 10-second granularity
        val raw = "$artist|$title|$durationBucket"
        return "lyr_" + raw.replace("[^a-z0-9|]".toRegex(), "")
    }

    override fun toString(): String {
        return "TrackMetadata(title='$cleanedTitle', artist='$cleanedArtist', version='$version', lang='$detectedLanguage', duration=$durationMs)"
    }

    class Builder {
        private var rawTitle = ""
        private var rawArtist = ""
        private var cleanedTitle = ""
        private var cleanedArtist = ""
        private var album = ""
        private var durationMs = 0L
        private var filePath = ""
        private var videoId: String? = null
        private var version = "original"
        private var detectedLanguage = "unknown"

        fun build(): TrackMetadata {
            return TrackMetadata(
                rawTitle = rawTitle,
                rawArtist = rawArtist,
                cleanedTitle = cleanedTitle,
                cleanedArtist = cleanedArtist,
                album = album,
                durationMs = durationMs,
                filePath = filePath,
                videoId = videoId,
                version = version,
                detectedLanguage = detectedLanguage
            )
        }
    }
}
