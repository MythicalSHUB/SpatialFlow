@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.codetrio.spatialflow.ui.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlaybackCache
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlayer
import com.codetrio.spatialflow.ui.player.canvas.resolveCanvasArtworkForPlayback
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import androidx.compose.runtime.collectAsState
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtworkPager(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    songList: List<SongItem>,
    currentSongIndex: Int,
    context: Context,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    allowCanvas: Boolean = true
) {
    val pagerState = rememberPagerState(
        initialPage = currentSongIndex.coerceAtLeast(0)
    ) {
        songList.size.coerceAtLeast(1)
    }

    // Sync Pager Page with VM when active song changes externally
    LaunchedEffect(currentSongIndex) {
        if (currentSongIndex >= 0 && currentSongIndex < pagerState.pageCount && pagerState.currentPage != currentSongIndex) {
            pagerState.animateScrollToPage(currentSongIndex)
        }
    }

    // Sync VM when swiped in Pager
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && currentSongIndex >= 0 && pagerState.currentPage != currentSongIndex) {
            viewModel.playSongAtIndex(pagerState.currentPage)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val song = if (page == currentSongIndex) currentSong else (songList.getOrNull(page) ?: currentSong)
            val isCurrentPage = page == currentSongIndex
            
            // Phase 1: Local State Isolation
            val activeCanvasArtwork by viewModel.canvasArtwork.collectAsState()

            val rawUri = song.getAlbumArtUri()
            val videoId = song.videoId
            val artworkUrl = if (rawUri != null && rawUri.toString().isNotEmpty()) {
                SongItem.enhanceThumbnailUrl(rawUri.toString())
            } else if (!videoId.isNullOrEmpty()) {
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            } else {
                null
            }

            var isError by remember(artworkUrl) { mutableStateOf(false) }

            // Canvas artwork state — resolved asynchronously per song
            val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val showAnimatedArt = prefs.getBoolean("show_animated_art", true) && allowCanvas

            var canvasArtworkState by remember(song.id) { mutableStateOf<CanvasArtwork?>(null) }

            // Attempt to resolve canvas artwork
            LaunchedEffect(song.id, isCurrentPage, showAnimatedArt, activeCanvasArtwork) {
                if (!showAnimatedArt) {
                    canvasArtworkState = null
                    return@LaunchedEffect
                }

                // If this is the active page, prioritize the VM's resolved artwork
                if (isCurrentPage && activeCanvasArtwork != null) {
                    canvasArtworkState = activeCanvasArtwork
                    return@LaunchedEffect
                }

                // Fast path: check if the song already has an animatedThumbnailUrl from InnerTube
                val ytAnimatedUrl = song.animatedThumbnailUrl
                if (!ytAnimatedUrl.isNullOrEmpty()) {
                    canvasArtworkState = CanvasArtwork(
                        animated = ytAnimatedUrl,
                        videoUrl = ytAnimatedUrl,
                    )
                    return@LaunchedEffect
                }

                // Slow path: resolve from network / cache
                val mediaId = song.id.toString()
                val songTitle = song.title ?: return@LaunchedEffect
                val artistName = song.artist ?: return@LaunchedEffect

                val resolved = withContext(Dispatchers.IO) {
                    resolveCanvasArtworkForPlayback(
                        mediaId = mediaId,
                        songTitleRaw = songTitle,
                        artistNameRaw = artistName,
                        albumTitleRaw = null,
                        requireVertical = true,
                        allowNetwork = isCurrentPage,
                    )
                }
                canvasArtworkState = resolved
            }

            val canvasPrimaryUrl = canvasArtworkState?.preferredVerticalAnimationUrl ?: canvasArtworkState?.preferredAnimationUrl
            val canvasFallbackUrl = canvasArtworkState?.videoUrlVertical ?: canvasArtworkState?.videoUrl

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 1. Static album art as base layer
                if (!artworkUrl.isNullOrEmpty() && !isError) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkUrl.toUri())
                            .crossfade(true)
                            .precision(Precision.EXACT)
                            .build(),
                        contentDescription = null,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) isError = true
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (artworkUrl.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // 2. Canvas motion artwork overlay (crossfades in when ready)
                AnimatedVisibility(
                    visible = showAnimatedArt && isCurrentPage &&
                        (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()),
                    enter = fadeIn(tween(1500)),
                    exit = fadeOut(tween(800)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasPrimaryUrl,
                        fallbackUrl = canvasFallbackUrl,
                        isPlaying = isCurrentPage,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
