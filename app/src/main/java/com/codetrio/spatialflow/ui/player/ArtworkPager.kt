package com.codetrio.spatialflow.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel

@Composable
fun ArtworkPager(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    songList: List<SongItem>,
    currentSongIndex: Int,
    context: Context,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true
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

    // Sync VM when swiped in Pager (only when settling/finished scrolling to avoid race conditions)
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

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!artworkUrl.isNullOrEmpty() && !isError) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkUrl.toUri())
                            .crossfade(true)
                            .precision(coil.size.Precision.EXACT)
                            .build(),
                        contentDescription = null,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) {
                                isError = true
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }
        }
    }
}
