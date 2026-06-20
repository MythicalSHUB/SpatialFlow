package com.codetrio.spatialflow.model

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.Serializable
import androidx.core.net.toUri

data class SongItem(
    @JvmField val id: Long,
    @JvmField val title: String,
    @JvmField val artist: String,
    @JvmField val albumId: Long,
    @JvmField val path: String?,
    @JvmField val duration: Long,
    @JvmField val dateAdded: Long,
    @JvmField val data: String?
) : Serializable {

    @JvmField var thumbnailUrl: String? = null
    @JvmField var videoId: String? = null
    @JvmField var artistId: String? = null
    @JvmField var lufs: Float? = null

    private var uriString: String? = null

    var contentUri: Uri
        get() = uriString?.toUri() ?: "content://media/external/audio/media/$id".toUri()
        set(value) {
            uriString = value.toString()
        }

    // Main constructor with automatic cleaning fallbacks
    constructor(
        id: Long,
        rawTitle: String?,
        rawArtist: String?,
        albumId: Long,
        path: String?,
        duration: Long,
        dateAdded: Long
    ) : this(
        id,
        cleanTitle(rawTitle, path),
        cleanArtist(rawArtist, path),
        albumId,
        path,
        duration,
        dateAdded,
        path
    )

    // Secondary constructor without duration for backwards compatibility
    constructor(id: Long, title: String?, artist: String?, albumId: Long, path: String?, dateAdded: Long) : 
        this(id, title, artist, albumId, path, 0, dateAdded)

    fun getAlbumArtUri(): Uri? {
        if (thumbnailUrl != null) return enhanceThumbnailUrl(thumbnailUrl!!).toUri()
        if (albumId > 0) {
            return "content://media/external/audio/albumart/$albumId".toUri()
        }
        if (path != null && File(path).exists()) {
            return Uri.fromFile(File(path))
        }
        return contentUri
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getEmbeddedPicture(context: android.content.Context): ByteArray? {
        val currentPath = path ?: return null
        if (currentPath.startsWith("http")) return null
        try {
            context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                android.media.MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(pfd.fileDescriptor)
                    return mmr.embeddedPicture
                }
            }
        } catch (_: Exception) {}
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val songItem = other as SongItem
        return id == songItem.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        private fun cleanTitle(rawTitle: String?, path: String?): String {
            var cleanTitle = rawTitle ?: "Unknown Title"
            if (cleanTitle.trim().isEmpty() || cleanTitle.equals("<unknown>", ignoreCase = true)) {
                cleanTitle = "Unknown Title"
            }
            if (path != null && cleanTitle == "Unknown Title") {
                try {
                    var fileName = File(path).name
                    if (fileName.lowercase().endsWith(".mp3")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    } else if (fileName.lowercase().endsWith(".m4a")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    }
                    if (fileName.contains(" - ")) {
                        val parts = fileName.split(" - ", limit = 2)
                        cleanTitle = parts[0].trim()
                    } else {
                        cleanTitle = fileName.trim()
                    }
                } catch (_: Exception) {}
            }
            return cleanTitle
        }

        private fun cleanArtist(rawArtist: String?, path: String?): String {
            var cleanArtist = rawArtist ?: "Unknown Artist"
            if (cleanArtist.trim().isEmpty() || cleanArtist.equals("<unknown>", ignoreCase = true)) {
                cleanArtist = "Unknown Artist"
            }
            if (path != null && cleanArtist == "Unknown Artist") {
                try {
                    var fileName = File(path).name
                    if (fileName.lowercase().endsWith(".mp3")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    } else if (fileName.lowercase().endsWith(".m4a")) {
                        fileName = fileName.substring(0, fileName.length - 4)
                    }
                    if (fileName.contains(" - ")) {
                        val parts = fileName.split(" - ", limit = 2)
                        cleanArtist = parts[1].trim()
                    }
                } catch (_: Exception) {}
            }
            return cleanArtist
        }

        @JvmStatic
        @JvmOverloads
        fun createOnlineSong(videoId: String?, title: String?, artist: String?, streamUrl: String?, durationMs: Long, thumbnailUrl: String?, artistId: String? = null): SongItem {
            val id = videoId?.hashCode()?.toLong() ?: (streamUrl?.hashCode()?.toLong() ?: System.currentTimeMillis())
            val song = SongItem(id, title ?: "Unknown Title", artist ?: "Unknown Artist", -1, streamUrl, durationMs, System.currentTimeMillis())
            song.contentUri = (streamUrl ?: "").toUri()
            song.thumbnailUrl = thumbnailUrl?.let { enhanceThumbnailUrl(it) }
            song.videoId = videoId
            song.artistId = artistId
            return song
        }

        @JvmStatic
        fun enhanceThumbnailUrl(url: String): String {
            if (url.isEmpty()) return url
            
            // Handle googleusercontent/ggpht dynamic image sizing parameters
            if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
                val regex = "(=[ws]\\d+.*)$".toRegex()
                return if (regex.containsMatchIn(url)) {
                    url.replace(regex, "=w1000-h1000-l90-rj")
                } else {
                    "$url=w1000-h1000-l90-rj"
                }
            }
            
            // Handle standard YouTube video thumbnails (fall back safely to hqdefault)
            if (url.contains("ytimg.com") || url.contains("youtube.com/vi/")) {
                if (url.contains("/default.jpg") || url.contains("/mqdefault.jpg") || url.contains("/hqdefault.jpg")) {
                    return url.replace(Regex("/(default|mqdefault)\\.jpg$"), "/hqdefault.jpg")
                }
            }
            
            return url
        }
    }
}

fun SongItem.toPlaylistSongEntity(playlistId: Long, position: Int = 0): com.codetrio.spatialflow.data.db.PlaylistSongEntity {
    return com.codetrio.spatialflow.data.db.PlaylistSongEntity(
        playlistId = playlistId,
        songId = this.videoId ?: this.id.toString(),
        title = this.title,
        artist = this.artist,
        album = "",
        duration = this.duration,
        thumbnailUrl = this.thumbnailUrl ?: this.path,
        addedAt = System.currentTimeMillis(),
        position = position,
        lufs = this.lufs
    )
}

fun com.codetrio.spatialflow.data.db.PlaylistSongEntity.toSongItem(): SongItem {
    return if (this.songId.toLongOrNull() != null && (this.thumbnailUrl == null || !this.thumbnailUrl.startsWith("http"))) {
        // Local device song
        val localId = this.songId.toLong()
        SongItem(
            localId,
            this.title,
            this.artist,
            -1L, // albumId
            this.thumbnailUrl, // path is stored in thumbnailUrl
            this.duration,
            this.addedAt
        ).apply {
            this.lufs = this@toSongItem.lufs
        }
    } else {
        // Online YouTube Music song
        SongItem.createOnlineSong(
            videoId = this.songId,
            title = this.title,
            artist = this.artist,
            streamUrl = null,
            durationMs = this.duration,
            thumbnailUrl = this.thumbnailUrl
        ).apply {
            this.lufs = this@toSongItem.lufs
        }
    }
}
