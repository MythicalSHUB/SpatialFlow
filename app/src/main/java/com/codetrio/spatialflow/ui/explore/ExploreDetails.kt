@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.codetrio.spatialflow.ui.explore

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codetrio.spatialflow.data.innertube.AlbumPage
import com.codetrio.spatialflow.data.innertube.ArtistPage
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.PlaylistPage
import com.codetrio.spatialflow.data.innertube.SearchItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel


/**
 * Enhanced premium detail view for an Album using Parallax visual language.
 */
@Composable
fun AlbumDetailView(
    albumPage: AlbumPage,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = albumPage.album.thumbnailUrl,
        title = albumPage.album.title,
        subtitle = "Album • ${albumPage.songs.size} tracks",
        sharedElementKey = albumPage.album.browseId,
        onBack = onBack,
        headerActions = {
            ExpressiveConnectedButtonGroup(
                onShuffleClick = { if (albumPage.songs.isNotEmpty()) onSongClick(albumPage.songs.first(), albumPage.songs.shuffled(), 0) },
                onRadioClick = { if (albumPage.songs.isNotEmpty()) onStartRadioClick(albumPage.songs.first().videoId) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
        itemsIndexed(
            items = albumPage.songs,
            key = { _, song -> "album-song-${song.videoId}" }
        ) { index, song ->
            SearchResultItem(
                item = SearchItem.Song(song),
                isCurrentlyPlaying = song.videoId == currentOnlineSong?.videoId,
                isLoading = isLoadingStream && song.videoId == currentOnlineSong?.videoId,
                onSongMenuClick = onSongMenuClick,
                onClick = { onSongClick(song, albumPage.songs, index) }
            )
        }
    }
}

/**
 * Enhanced premium detail view for a Playlist leveraging Unified Parallax system.
 */
@Composable
fun PlaylistDetailView(
    playlistPage: PlaylistPage,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = playlistPage.playlist.thumbnailUrl,
        title = playlistPage.playlist.title,
        subtitle = "Curated Playlist • ${playlistPage.songs.size} items",
        sharedElementKey = playlistPage.playlist.playlistId,
        onBack = onBack,
        headerActions = {
            ExpressiveConnectedButtonGroup(
                onShuffleClick = { if (playlistPage.songs.isNotEmpty()) onSongClick(playlistPage.songs.first(), playlistPage.songs.shuffled(), 0) },
                onRadioClick = { if (playlistPage.songs.isNotEmpty()) onStartRadioClick(playlistPage.songs.first().videoId) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
        itemsIndexed(
            items = playlistPage.songs,
            key = { _, song -> "playlist-song-${song.videoId}" }
        ) { index, song ->
            SearchResultItem(
                item = SearchItem.Song(song),
                isCurrentlyPlaying = song.videoId == currentOnlineSong?.videoId,
                isLoading = isLoadingStream && song.videoId == currentOnlineSong?.videoId,
                onSongMenuClick = onSongMenuClick,
                onClick = { onSongClick(song, playlistPage.songs, index) }
            )
        }
    }
}

/**
 * High-Fidelity Detail view for an Artist with Parallax scaling and Dynamic Content rendering.
 */
@Composable
fun ArtistDetailView(
    artistPage: ArtistPage,
    currentOnlineSong: OnlineSong?,
    isSubscribed: Boolean = false,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSubscribeClick: ((String) -> Unit)? = null,
    onStartRadioClick: ((String) -> Unit)? = null,
    onSectionClick: ((String, String?, String) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val formattedSubtitle = remember(artistPage.artist.subscriberCount) {
        val raw = artistPage.artist.subscriberCount ?: ""
        if (raw.isBlank()) return@remember ""
        val parts = raw.split(Regex(pattern = " • | •|• |•| · |·|,"))
        parts.joinToString(" • ") { part ->
            val trimmed = part.trim()
            val lower = trimmed.lowercase()
            when {
                lower.contains("subscriber") || lower.contains("subscribers") || lower.contains("subs") -> {
                    trimmed.replace("subscribers", "Subscribers", ignoreCase = true)
                        .replace("subscriber", "Subscriber", ignoreCase = true)
                        .replace("subs", "Subscribers", ignoreCase = true)
                }

                lower.contains("monthly listener") || lower.contains("monthly listeners") || lower.contains(
                    "listeners"
                ) -> {
                    trimmed.replace("monthly listeners", "Monthly Listeners", ignoreCase = true)
                        .replace("monthly listener", "Monthly Listener", ignoreCase = true)
                        .replace("listeners", "Monthly Listeners", ignoreCase = true)
                }

                else -> "$trimmed Subscribers"
            }
        }
    }

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = artistPage.artist.thumbnailUrl,
        title = artistPage.artist.title,
        subtitle = formattedSubtitle,
        isCircular = true,
        sharedElementKey = artistPage.artist.browseId,
        onBack = onBack,
        headerActions = {
            // Follow/Subscribe Button
            if (onSubscribeClick != null) {
                val label = if (isSubscribed) "Subscribed" else "Subscribe"
                FilledTonalButton(
                    onClick = {
                        onSubscribeClick(artistPage.artist.browseId)
                    },
                    shape = CircleShape,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSubscribed) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (isSubscribed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                ) {
                    Text(
                        label,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start Radio Button
            if (onStartRadioClick != null) {
                IconButton(
                    onClick = {
                        val allSongs = artistPage.sections.flatMap { it.items }
                            .filterIsInstance<SearchItem.Song>().map { it.song }
                        if (allSongs.isNotEmpty()) onStartRadioClick(allSongs.random().videoId)
                    },
                    modifier = Modifier.size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_radio),
                        contentDescription = "Start Radio",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            }

            // Play All Button
            IconButton(
                onClick = {
                    val allSongs = artistPage.sections.flatMap { it.items }
                        .filterIsInstance<SearchItem.Song>().map { it.song }
                    if (allSongs.isNotEmpty()) onSongClick(allSongs.first(), allSongs, 0)
                },
                modifier = Modifier.size(56.dp)
                    .background(MaterialTheme.colorScheme.onBackground, CircleShape)
            ) {
                Icon(painter = painterResource(id = com.codetrio.spatialflow.R.drawable.ic_play)
                    , "Play All",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(28.dp))
            }
        }
    ) {
        artistPage.sections.forEach { section ->
            val isTopSongs = section.title.contains("song", ignoreCase = true)
            if (isTopSongs) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Top songs", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (section.browseEndpoint != null) {
                                    onSectionClick?.invoke(section.browseEndpoint, section.params, "Top songs")
                                } else {
                                    val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                                    if (songs.isNotEmpty()) onSongClick(songs.first(), songs, 0)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (section.browseEndpoint != null) "See all" else "Play all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, "See all",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                val songs = section.items.filterIsInstance<SearchItem.Song>()
                val topSongsToTake = songs.take(5)
                val columns = if (isLandscape) 2 else 1
                val songChunks = topSongsToTake.chunked(columns)

                songChunks.forEachIndexed { chunkIdx, chunk ->
                    item(key = "top-songs-row-$chunkIdx") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            chunk.forEachIndexed { colIdx, item ->
                                val song = item.song
                                val isPlaying = song.videoId == currentOnlineSong?.videoId
                                val index = chunkIdx * columns + colIdx
                                Box(modifier = Modifier.weight(1f)) {
                                    ArtistTopSongItem(
                                        song = song,
                                        isPlaying = isPlaying,
                                        onClick = { onSongClick(song, songs.map { it.song }, index) },
                                        onMenuClick = { onSongMenuClick(song) }
                                    )
                                }
                            }
                            if (chunk.size < columns) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                item {
                    HomeSectionRow(
                        section = section,
                        currentOnlineSong = currentOnlineSong,
                        onSongClick = { song ->
                            val sectionSongs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
                            val idx = sectionSongs.indexOfFirst { it.videoId == song.videoId }
                            onSongClick(song, sectionSongs, idx)
                        },
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onPlaylistClick = onPlaylistClick,
                        onSongMenuClick = onSongMenuClick,
                        onSectionClick = { browseId, params, title ->
                            onSectionClick?.invoke(browseId, params, title)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Adaptive container that hosts both optimized 2-pane landscape layout and hero parallax portrait layout.
 */
@Composable
fun AdaptiveDetailContainer(
    isLandscape: Boolean,
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    isCircular: Boolean = false,
    sharedElementKey: String? = null,
    headerActions: @Composable (RowScope.() -> Unit)?,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val lazyListState = rememberLazyListState()

    val context = LocalContext.current
    val activity = remember(context) { context as? androidx.fragment.app.FragmentActivity }
    val playerSharedViewModel = remember(activity) {
        activity?.let { androidx.lifecycle.ViewModelProvider(it)[PlayerSharedViewModel::class.java] }
    }
    val isPlayerExpanded = playerSharedViewModel?.isPlayerExpanded?.collectAsStateWithLifecycle()?.value ?: false

    androidx.activity.compose.BackHandler(enabled = !isPlayerExpanded) {
        onBack()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLandscape) {
        // --- LANDSCAPE TWO-PANE ---
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Ambient glass backdrop in landscape mode
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.22f }
                        .blur(56.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                )
            }
            
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Sticky Hero (Fixed premium width to eliminate stretching)
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .padding(start = 16.dp, top = 24.dp, bottom = 24.dp, end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val baseModifier = Modifier
                        .fillMaxHeight(0.85f)
                        .aspectRatio(1f)
                        .clip(if (isCircular) CircleShape else RoundedCornerShape(24.dp))

                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = if (sharedElementKey != null) {
                            baseModifier.sharedElementIfAvailable("image-$sharedElementKey")
                        } else baseModifier,
                        contentScale = ContentScale.Crop
                    )
                }

                // RIGHT: Scrollable Info & Content (Consumes remaining width tightly)
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp, start = 8.dp, end = 24.dp)
                ) {
                    item {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = if (sharedElementKey != null) {
                                Modifier.sharedElementIfAvailable("title-$sharedElementKey")
                            } else Modifier
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (headerActions != null) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                headerActions()
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    content()
                }
            }

            // Floating Back Button (High-contrast visible)
            Box(modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 16.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    shape = IconButtonDefaults.filledShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back", 
                        tint = MaterialTheme.colorScheme.onSurface
                    )


                }
            }
        }
    } else {
        // --- PORTRAIT: PARALLAX ---
        val density = LocalDensity.current
        val fadeDistancePx = with(density) { 240.dp.toPx() }
        val imageAlpha by remember {
            derivedStateOf {
                val offset = if (lazyListState.firstVisibleItemIndex == 0) lazyListState.firstVisibleItemScrollOffset.toFloat() else fadeDistancePx
                1f - (offset / fadeDistancePx).coerceIn(0f, 1f)
            }
        }
        
        // 1:1 scroll tracking to move elements up in perfect sync with the list (no parallax)
        val scrollOffset by remember {
            derivedStateOf {
                if (lazyListState.firstVisibleItemIndex == 0) {
                    -lazyListState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    -fadeDistancePx
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Ambient dynamic blurred background art
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .graphicsLayer { 
                            alpha = imageAlpha * 0.5f 
                            translationY = scrollOffset
                        }
                        .blur(56.dp),
                    contentScale = ContentScale.Crop
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .graphicsLayer { 
                            translationY = scrollOffset
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            // Foreground image translating 1:1 on scroll
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                val baseModifier = Modifier.fillMaxSize().graphicsLayer { 
                    alpha = imageAlpha 
                    translationY = scrollOffset
                }
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = if (sharedElementKey != null) {
                        baseModifier.sharedElementIfAvailable("image-$sharedElementKey")
                    } else baseModifier,
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { 
                            alpha = imageAlpha 
                            translationY = scrollOffset
                        }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item { Spacer(modifier = Modifier.height(240.dp)) }

                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = if (sharedElementKey != null) {
                                Modifier.sharedElementIfAvailable("title-$sharedElementKey")
                            } else Modifier
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (headerActions != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            headerActions()
                        }
                    }
                }

                content()
            }

            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back", 
                        tint = Color.White
                    )
                }
            }
        }
    }
}
}

@Composable
fun ArtistTopSongItem(
    song: OnlineSong,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenuClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(song.thumbnailUrl).crossfade(false).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
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
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${song.artist}${song.duration?.let { " • $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Animated skeleton shown while waiting for Detail content to load.
 */
@Composable
fun DetailScreenSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShimmerModifier(width = 140.dp, height = 140.dp, shape = RoundedCornerShape(16.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                ShimmerModifier(width = 160.dp, height = 24.dp, shape = RoundedCornerShape(8.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerModifier(width = 100.dp, height = 16.dp, shape = RoundedCornerShape(6.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerModifier(width = 90.dp, height = 36.dp, shape = RoundedCornerShape(18.dp))
                    ShimmerModifier(width = 90.dp, height = 36.dp, shape = RoundedCornerShape(18.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ShimmerModifier(width = 48.dp, height = 48.dp, shape = RoundedCornerShape(6.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerModifier(width = 160.dp, height = 16.dp, shape = RoundedCornerShape(8.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        ShimmerModifier(width = 100.dp, height = 12.dp, shape = RoundedCornerShape(6.dp))
                    }
                }
            }
        }
    }
}

/**
 * Reusable Material 3 Expressive Button Group (Shuffle / Radio).
 * Renders standard M3 Expressive buttons utilizing native shape morphing
 * and native width animation on click/interaction.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveConnectedButtonGroup(
    onShuffleClick: () -> Unit,
    onRadioClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    androidx.compose.material3.ButtonGroup(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        expandedRatio = 0.3f,
        overflowIndicator = {}
    ) {
        val scope = this
        
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 12.dp else 28.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "ShuffleCorner"
                )
                androidx.compose.material3.Button(
                    onClick = onShuffleClick,
                    modifier = with(scope) {
                        Modifier
                            .animateWidth(interactionSource)
                            .weight(1f)
                            .height(52.dp)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            menuContent = {}
        )
        
        customItem(
            buttonGroupContent = {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 12.dp else 28.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "RadioCorner"
                )
                androidx.compose.material3.Button(
                    onClick = onRadioClick,
                    modifier = with(scope) {
                        Modifier
                            .animateWidth(interactionSource)
                            .weight(1f)
                            .height(52.dp)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Radio, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Radio", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            menuContent = {}
        )
    }
}

/**
 * Generic detail view for any section type (Albums, Artists, Playlists, Songs)
 */
@Composable
fun SectionDetailView(
    section: HomeSection,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onStartRadioClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val hasSongsOnly = section.items.isNotEmpty() && section.items.all { it is SearchItem.Song }

    if (hasSongsOnly) {
        val songs = section.items.filterIsInstance<SearchItem.Song>().map { it.song }
        AdaptiveDetailContainer(
            isLandscape = isLandscape,
            thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
            title = section.title,
            subtitle = "Songs • ${songs.size} items",
            sharedElementKey = section.title,
            onBack = onBack,
            headerActions = {
                ExpressiveConnectedButtonGroup(
                    onShuffleClick = { if (songs.isNotEmpty()) onSongClick(songs.first(), songs.shuffled(), 0) },
                    onRadioClick = { if (songs.isNotEmpty()) onStartRadioClick(songs.random().videoId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
            itemsIndexed(
                items = songs,
                key = { _, song -> "section-song-${song.videoId}" }
            ) { index, song ->
                SearchResultItem(
                    item = SearchItem.Song(song),
                    isCurrentlyPlaying = song.videoId == currentOnlineSong?.videoId,
                    isLoading = isLoadingStream && song.videoId == currentOnlineSong?.videoId,
                    onSongMenuClick = onSongMenuClick,
                    onClick = { onSongClick(song, songs, index) }
                )
            }
        }
    } else {
        val firstItemThumb = when (val item = section.items.firstOrNull()) {
            is SearchItem.Song -> item.song.thumbnailUrl
            is SearchItem.Album -> item.album.thumbnailUrl
            is SearchItem.Artist -> item.artist.thumbnailUrl
            is SearchItem.Playlist -> item.playlist.thumbnailUrl
            null -> null
        }

        AdaptiveDetailContainer(
            isLandscape = isLandscape,
            thumbnailUrl = firstItemThumb,
            title = section.title,
            subtitle = "Collection • ${section.items.size} items",
            sharedElementKey = section.title,
            onBack = onBack,
            headerActions = null
        ) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }

            val columns = if (isLandscape) 4 else 2
            val chunked = section.items.chunked(columns)

            itemsIndexed(chunked) { _, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            HomeCarouselItem(
                                item = item,
                                isPlaying = when (item) {
                                    is SearchItem.Song -> item.song.videoId == currentOnlineSong?.videoId
                                    else -> false
                                },
                                onClick = {
                                    when (item) {
                                        is SearchItem.Song -> onSongClick(item.song, listOf(item.song), 0)
                                        is SearchItem.Album -> onAlbumClick(item.album)
                                        is SearchItem.Artist -> onArtistClick(item.artist)
                                        is SearchItem.Playlist -> onPlaylistClick(item.playlist)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (rowItems.size < columns) {
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

