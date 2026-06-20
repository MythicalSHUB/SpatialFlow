@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
 
package com.codetrio.spatialflow.ui.explore
 
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.util.FavoritesManager
import com.codetrio.spatialflow.util.SongDownloader
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel

/**
 * Standard UI Shimmer Modifier to render placeholder skeleton states efficiently.
 */
@Composable
fun ShimmerModifier(
    width: Dp,
    height: Dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .shimmerEffect()
    )
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 3000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerLow
    )

    this
        .onGloballyPositioned { positionInRoot = it.positionInRoot() }
        .background(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translateAnim - positionInRoot.x, translateAnim - positionInRoot.y),
                end = Offset(translateAnim - positionInRoot.x + 600f, translateAnim - positionInRoot.y + 600f)
            )
        )
}

/**
 * Rhythmic audio level indicators used to communicate "now playing" context visually.
 */
@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(350, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(550, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bar3"
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        val barWidth = 3.dp
        Box(modifier = Modifier.width(barWidth).fillMaxHeight(height1).background(color, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(barWidth).fillMaxHeight(height2).background(color, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(barWidth).fillMaxHeight(height3).background(color, RoundedCornerShape(1.dp)))
    }
}

/**
 * Main content skeleton loading structure for the explore home feed.
 */
@Composable
fun HomeFeedSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                ShimmerModifier(width = 160.dp, height = 40.dp, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(20.dp))
                // Chip row shimmer
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) {
                        ShimmerModifier(width = 72.dp, height = 32.dp, shape = CircleShape)
                    }
                }
            }
        }
        items(4) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ShimmerModifier(width = 140.dp, height = 26.dp, shape = RoundedCornerShape(8.dp))
                    ShimmerModifier(width = 64.dp, height = 28.dp, shape = CircleShape)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(4) {
                        Column(horizontalAlignment = Alignment.Start) {
                            ShimmerModifier(width = 170.dp, height = 170.dp, shape = RoundedCornerShape(8.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            ShimmerModifier(width = 120.dp, height = 16.dp, shape = RoundedCornerShape(6.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            ShimmerModifier(width = 80.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contextual welcome banner dynamically responding to internal application time states.
 */
@Composable
fun WelcomeGreetingBanner(userName: String?) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) { in 0..11 -> "Good Morning"; in 12..16 -> "Good Afternoon"; else -> "Good Evening" }
    }
    val iconResId = remember(hour) {
        when (hour) {
            in 0..11 -> com.codetrio.spatialflow.R.drawable.ic_morning
            in 12..16 -> com.codetrio.spatialflow.R.drawable.ic_afternoon
            else -> com.codetrio.spatialflow.R.drawable.ic_evening
        }
    }
    val name = if (!userName.isNullOrBlank()) userName else "Listener"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-1).sp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
// ===== Search Result Item =====

@Composable
fun SearchResultItem(
    item: SearchItem,
    isCurrentlyPlaying: Boolean,
    isLoading: Boolean,
    onSongMenuClick: ((OnlineSong) -> Unit)? = null,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit
) {
    val backgroundColor = if (isCurrentlyPlaying)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else Color.Transparent

    val containerShape = RoundedCornerShape(8.dp)

    val thumbnailUrl = when (item) {
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val isCircle = item is SearchItem.Artist
    val itemId = when (item) {
        is SearchItem.Song -> item.song.videoId
        is SearchItem.Album -> item.album.browseId
        is SearchItem.Artist -> item.artist.browseId
        is SearchItem.Playlist -> item.playlist.playlistId
    }
    val imageShape = if (isCircle) CircleShape else RoundedCornerShape(4.dp) // Match YT Music sharp rounded corner

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp) // Perfect gap between items
            .clip(containerShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            onTouchDown()
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (item is SearchItem.Song && onSongMenuClick != null) {
                        onSongMenuClick(item.song)
                    }
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp), // Tighter inner layout padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(false).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(imageShape).sharedElementIfAvailable("image-$itemId"),
                contentScale = ContentScale.Crop
            )
            if (isLoading) {
                val loadingTransition = rememberInfiniteTransition(label = "loading_shimmer")
                val shimmerAlpha by loadingTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 0.7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "shimmer_alpha"
                )
                Box(
                    modifier = Modifier.fillMaxSize().clip(imageShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Titles and Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (item) {
                    is SearchItem.Song -> item.song.title
                    is SearchItem.Album -> item.album.title
                    is SearchItem.Artist -> item.artist.title
                    is SearchItem.Playlist -> item.playlist.title
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = when (item) {
                    is SearchItem.Song -> "${item.song.artist}${item.song.duration?.let { " • $it" } ?: ""}"
                    is SearchItem.Album -> "Album • ${item.album.artists.joinToString { it.name }}${item.album.year?.let { " • $it" } ?: ""}"
                    is SearchItem.Artist -> "Artist${item.artist.subscriberCount?.let { " • $it" } ?: ""}"
                    is SearchItem.Playlist -> "Playlist${item.playlist.songCount?.let { " • $it songs" } ?: ""}"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing Content
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item is SearchItem.Song && isCurrentlyPlaying && !isLoading) {
                AnimatedEqualizerBars(
                    modifier = Modifier.size(20.dp).height(12.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            when (item) {
                is SearchItem.Song -> {
                    if (onSongMenuClick != null) {
                        IconButton(
                            onClick = { onSongMenuClick(item.song) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }
                }
                is SearchItem.Album, is SearchItem.Playlist -> {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

// ===== Bento Discovery Cells =====

@Composable
fun BentoCell(
    item: SearchItem,
    modifier: Modifier = Modifier,
    isFeature: Boolean = false,
    onClick: () -> Unit
) {
    val title = when (item) {
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val subtitle = when (item) {
        is SearchItem.Song -> item.song.artist
        is SearchItem.Album -> item.album.artists.joinToString { it.name }
        is SearchItem.Artist -> "Artist"
        is SearchItem.Playlist -> item.playlist.author?.name ?: "Playlist"
    }
    val thumbnailUrl = when (item) {
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }

    Card(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (isFeature) 0.9f else 0.7f)),
                        startY = 50f
                    )
                )
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(if (isFeature) 16.dp else 12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (isFeature) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Feature",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = title,
                    style = if (isFeature) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = if (isFeature) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ===== Home Feed Section =====

@Composable
fun HomeSectionRow(
    section: HomeSection,
    currentOnlineSong: OnlineSong?,
    onSongClick: (OnlineSong) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSongTouchDown: (OnlineSong) -> Unit = {},
    onSectionClick: (browseId: String, params: String?, title: String) -> Unit = { _, _, _ -> }
) {
    val hasSongsOnly = section.items.isNotEmpty() && section.items.all { it is SearchItem.Song }

    Column(modifier = Modifier.padding(vertical = 4.dp).animateContentSize()) {
        // Universal Section Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasSongsOnly || section.title.contains("daily discover", ignoreCase = true)) {
                    OutlinedButton(
                        onClick = {
                            val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                            if (songs.isNotEmpty()) onSongClick(songs.first())
                        },
                        modifier = Modifier.height(32.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Text("Play all", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                if (section.browseEndpoint != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onSectionClick(section.browseEndpoint, section.params, section.title) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "See All",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        when {
            // 0. BENTO GRID — High Fidelity Personal Mix
            section.title.contains("personalized mix", ignoreCase = true) && section.items.size >= 5 -> {
                val items = section.items
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.height(260.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Dominant Feature
                        BentoCell(
                            item = items[0],
                            modifier = Modifier.weight(1.25f).fillMaxHeight(),
                            isFeature = true,
                            onClick = {
                                when (val it = items[0]) {
                                    is SearchItem.Song -> onSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                }
                            }
                        )
                        // Column of stacked tiles
                        Column(modifier = Modifier.weight(0.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            BentoCell(item = items[1], modifier = Modifier.weight(1f).fillMaxWidth(), onClick = {
                                when (val it = items[1]) {
                                    is SearchItem.Song -> onSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                }
                            })
                            BentoCell(item = items[2], modifier = Modifier.weight(1f).fillMaxWidth(), onClick = {
                                when (val it = items[2]) {
                                    is SearchItem.Song -> onSongClick(it.song)
                                    is SearchItem.Album -> onAlbumClick(it.album)
                                    is SearchItem.Artist -> onArtistClick(it.artist)
                                    is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                                }
                            })
                        }
                    }
                    Row(modifier = Modifier.height(130.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Lower row wide tiles
                        BentoCell(item = items[3], modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {
                            when (val it = items[3]) {
                                is SearchItem.Song -> onSongClick(it.song)
                                is SearchItem.Album -> onAlbumClick(it.album)
                                is SearchItem.Artist -> onArtistClick(it.artist)
                                is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                            }
                        })
                        BentoCell(item = items[4], modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {
                            when (val it = items[4]) {
                                is SearchItem.Song -> onSongClick(it.song)
                                is SearchItem.Album -> onAlbumClick(it.album)
                                is SearchItem.Artist -> onArtistClick(it.artist)
                                is SearchItem.Playlist -> onPlaylistClick(it.playlist)
                            }
                        })
                    }
                }
            }

            // 1. SPEED DIAL — 3×3 Squircle Grid
            section.title.contains("speed dial", ignoreCase = true) -> {
                val rows = section.items.take(9).chunked(3)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { item ->
                                val title = when (item) {
                                    is SearchItem.Song -> item.song.title
                                    is SearchItem.Album -> item.album.title
                                    is SearchItem.Artist -> item.artist.title
                                    is SearchItem.Playlist -> item.playlist.title
                                }
                                val thumbnailUrl = when (item) {
                                    is SearchItem.Song -> item.song.thumbnailUrl
                                    is SearchItem.Album -> item.album.thumbnailUrl
                                    is SearchItem.Artist -> item.artist.thumbnailUrl
                                    is SearchItem.Playlist -> item.playlist.thumbnailUrl
                                }
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            when (item) {
                                                is SearchItem.Song -> onSongClick(item.song)
                                                is SearchItem.Album -> onAlbumClick(item.album)
                                                is SearchItem.Artist -> onArtistClick(item.artist)
                                                is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Scrim overlay using theme token scrim is always dark by M3 spec
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)
                                                ),
                                                startY = 80f
                                            )
                                        )
                                    )
                                    Text(
                                        text = title,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                    )
                                }
                            }
                            if (rowItems.size < 3) repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // 2. YOUR DAILY DISCOVER â€” Large Immersive Cards
            section.title.contains("daily discover", ignoreCase = true) -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = section.items,
                        key = { index, item ->
                            val baseKey = when (item) {
                                is SearchItem.Song -> "discover-song-${item.song.videoId}"
                                is SearchItem.Album -> "discover-album-${item.album.browseId}"
                                is SearchItem.Artist -> "discover-artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "discover-playlist-${item.playlist.playlistId}"
                            }
                            "$index-$baseKey"
                        }
                    ) { _, item ->
                        val title = when (item) {
                            is SearchItem.Song -> item.song.title
                            is SearchItem.Album -> item.album.title
                            is SearchItem.Artist -> item.artist.title
                            is SearchItem.Playlist -> item.playlist.title
                        }
                        val subtitle = when (item) {
                            is SearchItem.Song -> item.song.artist
                            is SearchItem.Album -> item.album.artists.joinToString { it.name }
                            is SearchItem.Artist -> item.artist.subscriberCount ?: "Artist"
                            is SearchItem.Playlist -> item.playlist.author?.name ?: "Playlist"
                        }
                        val thumbnailUrl = when (item) {
                            is SearchItem.Song -> item.song.thumbnailUrl
                            is SearchItem.Album -> item.album.thumbnailUrl
                            is SearchItem.Artist -> item.artist.thumbnailUrl
                            is SearchItem.Playlist -> item.playlist.thumbnailUrl
                        }
                        val promptText = when (item) {
                            is SearchItem.Song -> "Because you liked ${item.song.artist}"
                            else -> "Handpicked based on your interests"
                        }

                        Box(
                            modifier = Modifier.width(300.dp).height(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    when (item) {
                                        is SearchItem.Song -> onSongClick(item.song)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                        0.35f to Color.Transparent,
                                        1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.88f)
                                    )
                                )
                            )
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = promptText,
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3. FROM THE COMMUNITY â€” Nested Card with Song Preview
            section.title.contains("community", ignoreCase = true) -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = section.items,
                        key = { index, item ->
                            val baseKey = when (item) {
                                is SearchItem.Song -> "community-song-${item.song.videoId}"
                                is SearchItem.Album -> "community-album-${item.album.browseId}"
                                is SearchItem.Artist -> "community-artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "community-playlist-${item.playlist.playlistId}"
                            }
                            "$index-$baseKey"
                        }
                    ) { _, item ->
                        val title = when (item) {
                            is SearchItem.Song -> item.song.title
                            is SearchItem.Album -> item.album.title
                            is SearchItem.Artist -> item.artist.title
                            is SearchItem.Playlist -> item.playlist.title
                        }
                        val author = when (item) {
                            is SearchItem.Song -> item.song.artist
                            is SearchItem.Album -> item.album.artists.firstOrNull()?.name ?: ""
                            is SearchItem.Artist -> "Artist"
                            is SearchItem.Playlist -> item.playlist.author?.name ?: "Community Curator"
                        }
                        val songCount = when (item) {
                            is SearchItem.Playlist -> item.playlist.songCount?.let { "$it songs" } ?: ""
                            is SearchItem.Album -> item.album.let { "" }
                            else -> ""
                        }
                        val thumbnailUrl = when (item) {
                            is SearchItem.Song -> item.song.thumbnailUrl
                            is SearchItem.Album -> item.album.thumbnailUrl
                            is SearchItem.Artist -> item.artist.thumbnailUrl
                            is SearchItem.Playlist -> item.playlist.thumbnailUrl
                        }

                        Card(
                            modifier = Modifier.width(320.dp).padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(author, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (songCount.isNotBlank()) {
                                            Text(songCount, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            when (item) {
                                                is SearchItem.Song -> onSongClick(item.song)
                                                is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                                else -> {}
                                            }
                                        },
                                        modifier = Modifier.size(54.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow, 
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. GENERAL FALLBACK â€” Song List or Carousel
            else -> {
                if (hasSongsOnly) {
                    val songItems = section.items.filterIsInstance<SearchItem.Song>()
                    val chunkedSongs = songItems.chunked(4)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(items = chunkedSongs, key = { idx, _ -> "song-col-$idx-${section.title}" }) { _, songChunk ->
                            Column(modifier = Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                songChunk.forEach { item ->
                                    HomeSongListItem(
                                        song = item.song,
                                        isPlaying = item.song.videoId == currentOnlineSong?.videoId,
                                        onTouchDown = { onSongTouchDown(item.song) },
                                        onClick = { onSongClick(item.song) },
                                        onMenuClick = { onSongMenuClick(item.song) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = section.items,
                            key = { index, item ->
                                val baseKey = when (item) {
                                    is SearchItem.Song -> "carousel-song-${item.song.videoId}-${section.title}"
                                    is SearchItem.Album -> "carousel-album-${item.album.browseId}-${section.title}"
                                    is SearchItem.Artist -> "carousel-artist-${item.artist.browseId}-${section.title}"
                                    is SearchItem.Playlist -> "carousel-playlist-${item.playlist.playlistId}-${section.title}"
                                }
                                "$index-$baseKey"
                            }
                        ) { _, item ->
                            HomeCarouselItem(
                                item = item,
                                isPlaying = when (item) {
                                    is SearchItem.Song -> item.song.videoId == currentOnlineSong?.videoId
                                    else -> false
                                },
                                onTouchDown = {
                                    if (item is SearchItem.Song) onSongTouchDown(item.song)
                                },
                                onClick = {
                                    when (item) {
                                        is SearchItem.Song -> onSongClick(item.song)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== Carousel Item =====

@Composable
fun HomeCarouselItem(
    item: SearchItem,
    isPlaying: Boolean,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(160.dp)
) {
    val isCircle = item is SearchItem.Artist
    val imageShape = if (isCircle) CircleShape else RoundedCornerShape(8.dp)
    val itemId = when (item) {
        is SearchItem.Song -> item.song.videoId
        is SearchItem.Album -> item.album.browseId
        is SearchItem.Artist -> item.artist.browseId
        is SearchItem.Playlist -> item.playlist.playlistId
    }
    val thumbnailUrl = when (item) {
        is SearchItem.Song -> item.song.thumbnailUrl
        is SearchItem.Album -> item.album.thumbnailUrl
        is SearchItem.Artist -> item.artist.thumbnailUrl
        is SearchItem.Playlist -> item.playlist.thumbnailUrl
    }
    val title = when (item) {
        is SearchItem.Song -> item.song.title
        is SearchItem.Album -> item.album.title
        is SearchItem.Artist -> item.artist.title
        is SearchItem.Playlist -> item.playlist.title
    }
    val subtitle = when (item) {
        is SearchItem.Song -> item.song.artist
        is SearchItem.Album -> item.album.artists.joinToString { it.name }
        is SearchItem.Artist -> item.artist.subscriberCount ?: ""
        is SearchItem.Playlist -> item.playlist.author?.name ?: ""
    }

    Column(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            onTouchDown()
                        }
                    }
                }
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.TopStart
        ) {
            Card(
                modifier = Modifier.fillMaxSize().sharedElementIfAvailable("image-$itemId"),
                shape = imageShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(false).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isPlaying) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizerBars(
                                modifier = Modifier.size(32.dp).height(24.dp),
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = subtitle.ifBlank { " " },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ===== Song List Item =====

@Composable
fun HomeSongListItem(
    song: OnlineSong,
    isPlaying: Boolean,
    onTouchDown: () -> Unit = {},
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            onTouchDown()
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(song.thumbnailUrl).crossfade(false).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(
                        modifier = Modifier.size(24.dp).height(16.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactMenuItem(
    text: String,
    icon: ImageVector,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    onClick: () -> Unit
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.primary
        ),
        content = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineSongBottomSheet(
    song: OnlineSong,
    isPinned: Boolean,
    onDismissRequest: () -> Unit,
    playerSharedViewModel: PlayerSharedViewModel,
    exploreViewModel: ExploreViewModel,
    onPlaylistAddClick: (SongItem) -> Unit,
    onViewCreditsClick: (OnlineSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val favoritesManager = remember { FavoritesManager(context) }
    var isFavorite by remember(song.videoId) { mutableStateOf(favoritesManager.isFavorite(song.videoId.hashCode().toLong())) }

    val onlineSongItem = remember(song.videoId) {
        SongItem.createOnlineSong(
            song.videoId,
            song.title,
            song.artist,
            "https://music.youtube.com/watch?v=${song.videoId}",
            song.durationMs,
            song.thumbnailUrl,
            song.artistId
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title/Artist + Like + Close
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        val target = !isFavorite
                        favoritesManager.setFavorite(song.videoId.hashCode().toLong(), target)
                        isFavorite = target
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                            if (target) "Added to library" else "Removed from library"
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.ThumbUp else Icons.Filled.ThumbUpOffAlt,
                        contentDescription = "Like",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Big Square Button Grid: Play next, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Play next
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            playerSharedViewModel.addToQueueNext(onlineSongItem)
                            exploreViewModel.addToQueueNext(song)
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Playing next: ${song.title}")
                            onDismissRequest()
                        }
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1.5f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text("Play next", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }

                // Card 2: Share
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "Check out ${song.title} by ${song.artist}: https://music.youtube.com/watch?v=${song.videoId}")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                            onDismissRequest()
                        }
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1.5f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text("Share", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Optimized 2-Pane Actions for Landscape Width
            val menuItems = listOf(
                Triple("Start mix", Icons.Default.Radio) {
                    exploreViewModel.playOnlineSongWithQueue(song, listOf(song), 0)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Starting mix based on ${song.title}")
                    onDismissRequest()
                },
                Triple("Add to queue", Icons.AutoMirrored.Filled.PlaylistPlay) {
                    playerSharedViewModel.addToQueue(onlineSongItem)
                    exploreViewModel.addToQueueLast(song)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                        "Added to queue",
                        iconVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.PlaylistAdd
                    )
                    onDismissRequest()
                },
                Triple("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                    onPlaylistAddClick(onlineSongItem)
                    onDismissRequest()
                },
                Triple("Download", Icons.Default.Download) {
                    SongDownloader.downloadSong(context, onlineSongItem)
                    onDismissRequest()
                },
                Triple("Go to album", Icons.Default.Album) {
                    val id = song.albumId
                    if (!id.isNullOrBlank()) {
                        exploreViewModel.loadAlbum(id)
                        onDismissRequest()
                    } else {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Album information unavailable")
                    }
                },
                Triple("Go to artist", Icons.Default.Person) {
                    val aId = song.artistId
                    if (!aId.isNullOrBlank()) {
                        exploreViewModel.loadArtist(aId)
                        onDismissRequest()
                    } else {
                        exploreViewModel.search(song.artist)
                        onDismissRequest()
                    }
                },
                Triple("View song credits", Icons.Default.Groups) {
                    onViewCreditsClick(song)
                    onDismissRequest()
                },
                Triple(
                    if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial",
                    Icons.Default.PushPin
                ) {
                    exploreViewModel.pinToSpeedDial(song)
                    val msg = if (isPinned) "Song removed from Speed Dial!" else "Song pinned to Speed Dial!"
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
                    onDismissRequest()
                },
                Triple("Not interested", Icons.Default.Block) {
                    exploreViewModel.setNotInterested(song)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("We will suggest fewer songs like this")
                    onDismissRequest()
                }
            )

            val menuColumns = if (isLandscape) 2 else 1
            val menuChunks = menuItems.chunked(menuColumns)

            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                menuChunks.forEachIndexed { chunkIdx, chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        chunk.forEachIndexed { innerIdx, itemTuple ->
                            val (title, icon, action) = itemTuple
                            val globalIndex = chunkIdx * menuColumns + innerIdx
                            Box(modifier = Modifier.weight(1f)) {
                                CompactMenuItem(
                                    text = title,
                                    icon = icon,
                                    shapes = if (isLandscape) {
                                        ListItemDefaults.shapes()
                                    } else {
                                        ListItemDefaults.segmentedShapes(index = globalIndex, count = menuItems.size)
                                    },
                                    onClick = action
                                )
                            }
                        }
                        if (chunk.size < menuColumns) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

