package com.codetrio.spatialflow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_songs")
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: String, // String representation of song ID or YouTube videoId
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val thumbnailUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int = 0,
    val lufs: Float? = null
)
