package com.codetrio.spatialflow.ui.player

import androidx.compose.runtime.Immutable
import com.codetrio.spatialflow.model.SongItem

@Immutable
data class PlayerUiState(
    val currentSong: SongItem? = null,
    val isPlaying: Boolean = false,
    val duration: Int = 0,
    val isProcessing: Boolean = false,
    val currentSongIndex: Int = -1,
    val isHapticsEnabled: Boolean = false,
    val miniPlayerBlendColor: Int = 0,
    val isCurrentSongFavorite: Boolean = false,
    val isCurrentSongDisliked: Boolean = false,
    val repeatMode: Int = 0,
    val isShuffleEnabled: Boolean = false,
    val playerBackgroundColor: Int = 0xFF0F0F0F.toInt(),
    val likesCount: String = "Like",
    val isCurrentSongDownloaded: Boolean = false,
    val currentSongDownloadProgress: Int? = null
)
