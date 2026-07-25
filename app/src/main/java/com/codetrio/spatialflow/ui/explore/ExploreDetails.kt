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
import com.codetrio.spatialflow.viewmodel.MoodDetail


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
        parts.mapNotNull { part ->
            var trimmed = part.trim()
            trimmed = trimmed.replace("Spotify", "", ignoreCase = true).trim()
            if (trimmed.isBlank()) return@mapNotNull null

            val lower = trimmed.lowercase()
            when {
                lower.contains("subscriber") || lower.contains("subscribers") || lower.contains("subs") -> {
                    trimmed.replace("subscribers", "Subscribers", ignoreCase = true)
                        .replace("subscriber", "Subscriber", ignoreCase = true)
                        .replace("subs", "Subscribers", ignoreCase = true)
                }

                lower.contains("listener") || lower.contains("listeners") || lower.contains("audience") -> {
                    var clean = trimmed.replace(Regex("(?i)\\b(spotify monthly listeners|monthly listeners|monthly listener|listeners|listener)\\b"), "Monthly Listeners")
                    clean = clean.replace(Regex("(?i)\\bmonthly monthly listeners\\b"), "Monthly Listeners")
                    if (!clean.endsWith("Monthly Listeners", ignoreCase = true) && !clean.endsWith("Listeners", ignoreCase = true)) {
                        clean = "$clean Monthly Listeners"
                    }
                    clean
                }

                else -> {
                    if (trimmed.endsWith("Subscribers", ignoreCase = true) || trimmed.endsWith("Listeners", ignoreCase = true)) {
                        trimmed
                    } else {
                        "$trimmed Subscribers"
                    }
                }
            }.replace("Monthly Monthly Listeners", "Monthly Listeners", ignoreCase = true)
             .replace("Spotify", "", ignoreCase = true)
             .trim()
        }.filter { it.isNotBlank() }.distinct().joinToString(" • ")
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
    UnifiedShimmerProvider {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShimmerModifier(width = 140.dp, height = 140.dp, useMorphingLoadingShape = true)
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
            is SearchItem.TopResult -> item.thumbnailUrl
            is SearchItem.Header -> null
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
                                        is SearchItem.TopResult, is SearchItem.Header -> {}
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

@Composable
fun MoodDetailView(
    moodDetail: MoodDetail,
    currentOnlineSong: OnlineSong?,
    isLoadingStream: Boolean,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    onPlaylistClick: (OnlinePlaylist) -> Unit,
    onArtistClick: (OnlineArtist) -> Unit,
    onSongMenuClick: (OnlineSong) -> Unit,
    onSectionClick: (String, String?, String) -> Unit,
    onStartRadioClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val (_, fallbackImageUrl) = getMoodVisuals(moodDetail.moodName)

    val firstItemThumb = moodDetail.sections.firstOrNull()?.items?.firstOrNull()?.let {
        when (it) {
            is SearchItem.Song -> it.song.thumbnailUrl
            is SearchItem.Album -> it.album.thumbnailUrl
            is SearchItem.Artist -> it.artist.thumbnailUrl
            is SearchItem.Playlist -> it.playlist.thumbnailUrl
            is SearchItem.TopResult -> it.thumbnailUrl
            is SearchItem.Header -> null
        }
    } ?: fallbackImageUrl

    AdaptiveDetailContainer(
        isLandscape = isLandscape,
        thumbnailUrl = firstItemThumb,
        title = moodDetail.moodName,
        subtitle = "Mood Hub • ${moodDetail.sections.size} categories",
        sharedElementKey = "mood-${moodDetail.moodName}",
        onBack = onBack,
        headerActions = {
            val allSongs = moodDetail.sections.flatMap { it.items }
                .filterIsInstance<SearchItem.Song>().map { it.song }
            if (allSongs.isNotEmpty()) {
                ExpressiveConnectedButtonGroup(
                    onShuffleClick = { onSongClick(allSongs.first(), allSongs.shuffled(), 0) },
                    onRadioClick = { onStartRadioClick(allSongs.random().videoId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }

        moodDetail.sections.forEach { section ->
            item(key = "mood-section-${section.title}") {
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
                        onSectionClick(browseId, params, title)
                    }
                )
            }
        }
    }
}

fun getMoodVisuals(name: String): Pair<List<Color>, String> {
    val lower = name.lowercase()
    return when {
        // ── Moods ──
        lower.contains("chill") -> Pair(listOf(Color(0xFF0B1E36), Color(0xFF1E3C72)), "https://yt3.googleusercontent.com/5Wbm5UXl8JWjFxHXdnhhbFPT86rn-7KP0QEprpDUR2orURbk0VXsXWnEJ8drqQKzSdUd-LIJgScxHxF7uw=w120-h120-l90-rj")
        lower.contains("commute") || lower.contains("travel") -> Pair(listOf(Color(0xFF2C0F2F), Color(0xFF4C1E4F)), "https://yt3.googleusercontent.com/lEJ45zST5h0zYX0ZzFrIPf0OUunLaY7Gz83K93DQMTKCILUkA3Y6kLrrKuaREKZkmwpMlEVwf1eMD2QYKg=w544-h544-l90-rj")
        lower.contains("energize") || lower.contains("energy") -> Pair(listOf(Color(0xFF4A0E17), Color(0xFF2B0208)), "https://i.ytimg.com/vi/iGvcyYwNV7E/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3lKGLBfK6iCjZPvJVqAl4BvHZpOrg")
        lower.contains("feel good") || lower.contains("happy") || lower.contains("feelgood") -> Pair(listOf(Color(0xFF4A340E), Color(0xFF2D1E04)), "https://yt3.googleusercontent.com/AT2wfk1PvKjnHO7BzH2i2NkAvqRy_oZNEq48QGBlLJb51AuguCiR3mzp99X1uFdZezAkoSJ2WGjUU-HXZg=w120-h120-l90-rj")
        lower.contains("focus") || lower.contains("study") -> Pair(listOf(Color(0xFF0B2536), Color(0xFF173D54)), "https://yt3.googleusercontent.com/Aen64Gcp92yGdBM6LLWFk8vXDF3l6fzWHnyRJJFmjOcqZovwI9ddtd0ONGGaLUYRiRCswTDhcY8DFM1h=w544-h544-l90-rj")
        lower.contains("gaming") -> Pair(listOf(Color(0xFF1C0E36), Color(0xFF381D66)), "https://yt3.googleusercontent.com/oV0yx_UYlchhRaC5R4sKpDQGbEw1ZzxvvDYESz3shjne70pJwFotUmQc7kGayhY7EXIoTrZxsMsUBeE=w120-h120-l90-rj")
        lower.contains("party") -> Pair(listOf(Color(0xFF2D0F38), Color(0xFF561D6B)), "https://yt3.googleusercontent.com/voQriSjwTDvhYfOsDX3TqTfNOVtVoZyY3TIlDjem4209T6nU839S6NoMERGoN7UisKAFSsViy4Qv6gRI=w544-h544-l90-rj")
        lower.contains("romance") || lower.contains("love") -> Pair(listOf(Color(0xFF3D0911), Color(0xFF1A0205)), "https://yt3.googleusercontent.com/kIN6MHu8YyLF7_FOxwoC0LSbZM4zMMiDLvR5dlnjR8d17Bnt_eLsSvCddxrFm47b9gLNpbd0v6tLvHc=w120-h120-l90-rj")
        lower.contains("sad") || lower.contains("cry") -> Pair(listOf(Color(0xFF0D1E36), Color(0xFF162536)), "https://i.ytimg.com/vi/q48Y5Hf83Bk/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3kotH-DjBWGPNSCY-2wJM_ZN3ASdw")
        lower.contains("sleep") || lower.contains("relax") -> Pair(listOf(Color(0xFF0E0B25), Color(0xFF1C1740)), "https://yt3.googleusercontent.com/Rh31BmBmpm5nP3lM-XO-q-546S1cUAoCSFmqWSSiS01jekVhS7fQqmeFuiK-gYKPDrY9QOHfa9G_2iI-=w120-h120-l90-rj")
        lower.contains("workout") || lower.contains("gym") || lower.contains("fitness") -> Pair(listOf(Color(0xFF0E3823), Color(0xFF1E5C40)), "https://yt3.googleusercontent.com/6gEZkZYsLbktcQLbZhPzVj4KGD-WI69Y_2TA3D1XglIkivE0VXiN1byza_D8LFhIACBVTf839kflhzGo=w120-h120-l90-rj")

        // ── Genres: Regional / Language ──
        lower.contains("african") -> Pair(listOf(Color(0xFF47210E), Color(0xFF240E04)), "https://lh3.googleusercontent.com/_QE_u2AbzzmENeNq9ixrSpB_dZHCgX1sX9_4NAzGIWTR8Gd6jHpGcUZlmG4ZjA97F7pHrErkFOdNfw=w120-h120-p-l90-rj")
        lower.contains("arabic") -> Pair(listOf(Color(0xFF3E1E4F), Color(0xFF240E30)), "https://i.ytimg.com/vi/y0ua77o3rJI/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3l2sZyORL_jC9GB3-KnnTVOlbAI_w")
        lower.contains("bengali") -> Pair(listOf(Color(0xFF0E4F32), Color(0xFF052B1A)), "https://yt3.googleusercontent.com/PM75ZJhqaLfpFMqx_rJdOnshUdE5IPfko7RYkIXe__rRZlwxKXfAA5P_Zifqx_uu8V-WBSJSCpInAFpa=w544-h544-l90-rj")
        lower.contains("bhojpuri") -> Pair(listOf(Color(0xFF4F3B0E), Color(0xFF2B1F04)), "https://yt3.googleusercontent.com/DL2RONGBWdojmekrXzdYa6o0_uAcJ2F_rJbqBsfgJ2_tyxJDl3z9ItBF5t13PuZc6uIVXcUU-G1hyySY=w120-h120-l90-rj")
        lower.contains("carnatic") -> Pair(listOf(Color(0xFF4F0E13), Color(0xFF2B0407)), "https://i.ytimg.com/vi/Go-mAJpH6_w/sddefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCghqEJQEGHgg6AJIWg&rs=AMzJL3l9U87DpC3SxCi9IUtuP6XTT0BvZg")
        lower.contains("gujarati") -> Pair(listOf(Color(0xFF4F2B0E), Color(0xFF2B1404)), "https://i.ytimg.com/vi/MRWHOvPSIi4/sddefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCghqEJQEGHgg6AJIWg&rs=AMzJL3kWFdDEPqjyHY4zyTDmn8I-vI4OZA")
        lower.contains("haryanvi") -> Pair(listOf(Color(0xFF0E434F), Color(0xFF06232B)), "https://yt3.googleusercontent.com/9b6smz_a_wDIELlfuNtf8Py4dxQvkYhrY0JTpbiJ1nEBXXigtYMHokGkUySMJfw2We7fJhjz-x_u2MrT=w120-h120-l90-rj")
        lower.contains("hindi") && !lower.contains("hindustani") -> Pair(listOf(Color(0xFF4F1A0E), Color(0xFF2B0B04)), "https://lh3.googleusercontent.com/bbR8znm7CX07mCGQH-M484ckFRaKkSmTjwrwuFZxQUBy7Uc5gQcintkpqDXCuSX0DdLLg2aPskZhC2s=w120-h120-p-l90-rj")
        lower.contains("hindustani") -> Pair(listOf(Color(0xFF380E4F), Color(0xFF1C042B)), "https://yt3.googleusercontent.com/5tDBQm5emPRocnpNzBKKra2IVSEFAVodBuq6X9tbMc0PVNR9J7YHwKNgvVe1V_mDAsUPRl7F_Az-=s1200")
        lower.contains("kannada") -> Pair(listOf(Color(0xFF4F0E34), Color(0xFF2B041B)), "https://yt3.googleusercontent.com/zSzuaIP-OMj_kns5Q4erv8rW0-oMS5fIbANYcUt_2cnyKQ_3ThEFP4AvjH_D7DeWlCtRQ60ULA=w120-h120-l90-rj-dcpUWACyIJ")
        lower.contains("malayalam") -> Pair(listOf(Color(0xFF0E384F), Color(0xFF041F2B)), "https://yt3.googleusercontent.com/Q3SvVNKE33VIDryHPfRmw8TFI5S7N_cf37OIXSoALVSHCpswOLO8rWFIihJA-Z43d_q59PoTNlvnrUVF=w120-h120-l90-rj")
        lower.contains("marathi") -> Pair(listOf(Color(0xFF4F1D0E), Color(0xFF2B0D04)), "https://i.ytimg.com/vi/SRKqCi9-aeg/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3lg_pIgHLVg514QuZpqDWFs03DHXw")
        lower.contains("punjabi") -> Pair(listOf(Color(0xFF4F0F27), Color(0xFF2B0412)), "https://i.ytimg.com/vi/JQ922u24pH8/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3k55rXpOx8UtU7xpry42BhYfR2EZw")
        lower.contains("tamil") -> Pair(listOf(Color(0xFF0E4F28), Color(0xFF042B14)), "https://lh3.googleusercontent.com/wBG4jypwBcEGHd-qSbM2_4B46WPEhlOCjusCOEkxdnsoIC4WLS9LmFARZsE854pB-vAEYlsp4x2yiHE=w120-h120-p-l90-rj")
        lower.contains("telugu") -> Pair(listOf(Color(0xFF0F264F), Color(0xFF06132B)), "https://yt3.googleusercontent.com/CdnTrpYIv-ZumLEqKXb502AOOd5ec3kp91VgMpfl5lnOCWrQg0twoJDiiQctBQ6KpUpTd0eM7bdBnds=w120-h120-l90-rj")

        // ── Genres: Indian Indie / Desi ──
        lower.contains("desi hip-hop") || lower.contains("desi hip hop") -> Pair(listOf(Color(0xFF43320B), Color(0xFF241A04)), "https://yt3.googleusercontent.com/cD8eFPDqoVhg5AXB7GN8fONZVV6LIxqCLww1V754H2djjQdLLmhth1ucWwHuLMhsUH4kJ_2YivwTrAk=w544-h544-l90-rj")
        lower.contains("devotional") -> Pair(listOf(Color(0xFF4E2C0F), Color(0xFF291404)), "https://yt3.googleusercontent.com/CXTdaEBZHvJREVPpI7epDp4Sqv8oy2eHguv1-rH2Pjy6JAHZNr60BEr4Z5actaBr7UhkhlVamPrFOnjZ=w120-h120-l90-rj")
        lower.contains("family") -> Pair(listOf(Color(0xFF0E4F38), Color(0xFF042B1E)), "https://yt3.googleusercontent.com/dDwLaiGIjQwZTku4-Zj0moP-LnYXoAlDvbrNKVCVs2kpV2SzdcCySUAwlfOrjMLMEq6ubyYHR7QnH3I=w120-h120-l90-rj")
        lower.contains("ghazal") || lower.contains("sufi") -> Pair(listOf(Color(0xFF2F0E4F), Color(0xFF1A042E)), "https://yt3.googleusercontent.com/ovcYuUOnxjVXIApysRqshE_laShvabvSbNa4eXu_QBFjP7j3_SGhp0L1rRvAiol1MyIYOid0rLpXrXXW=w544-h544-l90-rj")
        lower.contains("indian indie") -> Pair(listOf(Color(0xFF4F1B0E), Color(0xFF2B0C04)), "https://yt3.googleusercontent.com/-O9wZ1lc0XI9mEzJaNrQbxf8gaapV0nz-iisPVgXUl0u59vGuGgmlb8hfXgnyhK451TR6X6Lbg=s1200")
        lower.contains("indian pop") -> Pair(listOf(Color(0xFF4F0E2F), Color(0xFF2B0419)), "https://yt3.googleusercontent.com/k8T1iodf5nMMksDlIjS7z0T4uUGi0IWvfzz6H2QgFHs1lv9kjgd5gXRs05lC7TBP_Csv4dsu0K8nr3yqRA=w544-h544-l90-rj")
        lower.contains("monsoon") -> Pair(listOf(Color(0xFF0E2538), Color(0xFF06131F)), "https://yt3.googleusercontent.com/vrtDD6cpMCYeP7f7SImfbNrfiSjGPT6hRH8cEB2ayKGeUwvOpckqM3Z6CT6V8if79vPxt3p2tnw3zfti=w544-h544-l90-rj")

        // ── Genres: Western / International ──
        lower.contains("classical") -> Pair(listOf(Color(0xFF0E2538), Color(0xFF071521)), "https://lh3.googleusercontent.com/Z_MIQT_NQBhEP5PsULMz829VblbsqWgibZXRSgwWhYImkYJYq6DUwUvSkg4-0RJImuZFbrQqvZXetg=w120-h120-p-l90-rj")
        lower.contains("country") || lower.contains("americana") -> Pair(listOf(Color(0xFF38290E), Color(0xFF1F1505)), "https://yt3.googleusercontent.com/JHAV9fESXYj_ewEC9HYdceX_9E0LQIgjixhvxxRFDO4S_aAqq1ySb8pCxthYSHy2-11EKJLqipLEVfk=w120-h120-l90-rj")
        lower.contains("dance") || lower.contains("electronic") || lower.contains("edm") -> Pair(listOf(Color(0xFF0B333B), Color(0xFF04181F)), "https://yt3.googleusercontent.com/VmcOQDfSnyBT2ObpVAQv7A2IZI0U41HnHIrplWW3hRbyLAfCxo5Nzu0uy2U2x5pOMCo7P_vaA8qzDXji=w544-h544-l90-rj")
        lower.contains("decades") -> Pair(listOf(Color(0xFF0E264F), Color(0xFF04132B)), "https://yt3.googleusercontent.com/D8B3OUAetDwJxEsbPPo1n-uipZZeYjnhRdr0tV_ldP-T5Mujn7enMg_swnuvCRozQsPYzO2PzIPeU884=w544-h544-l90-rj")
        lower.contains("folk") || lower.contains("acoustic") -> Pair(listOf(Color(0xFF2C2F36), Color(0xFF14161B)), "https://i.ytimg.com/vi/8UjJBh_GO2k/sddefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCghqEJQEGHgg6AJIWg&rs=AMzJL3mqEOs6DltpzTfq2yZX69QsJ2pUbA")
        lower.contains("hip-hop") || lower.contains("hip hop") || lower.contains("rap") -> Pair(listOf(Color(0xFF4F2B0E), Color(0xFF2B1404)), "https://yt3.googleusercontent.com/zOaDpfj2eyVVe1WX6_hRirEI0DZxghCjpg-wVOebZK3FS_Q7MJKddQ2z5MGLqcUlcWN9RlJME34xpRuA=w120-h120-l90-rj")
        lower.contains("indie") || lower.contains("alternative") -> Pair(listOf(Color(0xFF0B2D3B), Color(0xFF041924)), "https://i.ytimg.com/vi/ocJWc7DgGp8/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3nrVm8fTxDaqW7cqO-pN9l156g9TQ")
        lower.contains("j-pop") || lower.contains("jpop") -> Pair(listOf(Color(0xFF4F1E4A), Color(0xFF2B0E27)), "https://yt3.googleusercontent.com/TdUFj3N68xAQWNhlHyX-7Z9roXUQ0ouJlv3_dHJfKbkzLmmRNIfxzqlUMeND2Z_wLeg333yvsg=w120-h120-l90-rj")
        lower.contains("jazz") || lower.contains("blues") -> Pair(listOf(Color(0xFF4F2C32), Color(0xFF2B141B)), "https://yt3.googleusercontent.com/ZKgKcRJr0BOAJAiKt3t2-a1f63LoI7FpKt1lqKoFH9uCR3Nl4mmZRIxRxqK7EzOJ5dK_uPaCMQgQCRCS=w120-h120-l90-rj")
        lower.contains("k-pop") || lower.contains("kpop") -> Pair(listOf(Color(0xFF381F4F), Color(0xFF1E0E2E)), "https://yt3.googleusercontent.com/c7rpiHR2uLqq12hGkd3O_lnUaJav1pEw3_gLxLRsaii7rvD_N_JY5yiSw7Dxalxbe6ZIiwcE5k0=s1200")
        lower.contains("latin") -> Pair(listOf(Color(0xFF4F101A), Color(0xFF2B040B)), "https://yt3.googleusercontent.com/ploU_4iWpDoJlX3FOlwwd_yQcex0I8A0_665lePXAEBbNp1zn5g42eNwg5Q7lvYc2mG2--UNIYcIhww=w120-h120-p-l90-rj")
        lower.contains("metal") -> Pair(listOf(Color(0xFF1E1E1E), Color(0xFF0F0F0F)), "https://i.ytimg.com/pl_c/PLmXxqSJJq-yUwqtbp8MHBoTDoDULMoViq/studio_square_thumbnail.jpg?sqp=CJ3rw9IG-oaymwEKCMAWENQMIABIWqLzl_8DBgiOva_ABg&rs=AMzJL3n7cg8KNd-aBmA4zWF5X6PjiJPHIA")
        lower.contains("pop") -> Pair(listOf(Color(0xFF4F0E2A), Color(0xFF2B0414)), "https://yt3.googleusercontent.com/wxE4sWHvCcQiK92dPmiqZKLtp1yFwCm3dJb0Fv1kPNSAceFYfZxkF0au_UFSQLznSlsDIYvE=w120-h120-l90-rj")
        lower.contains("r&b") || lower.contains("soul") || lower.contains("rnb") -> Pair(listOf(Color(0xFF2F173A), Color(0xFF160A1C)), "https://i.ytimg.com/vi/nftGJDj8ho0/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3nGsZ1n-T0KIqDZeM_65QkVX6532Q")
        lower.contains("reggae") || lower.contains("caribbean") -> Pair(listOf(Color(0xFF0E3A33), Color(0xFF041F1A)), "https://i.ytimg.com/vi/oFWwJJhUAxM/hqdefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCggAEOADGC0guwJIWg&rs=AMzJL3nbZKER-Hy4_po0ON-pwiZDW4yW6w")
        lower.contains("rock") -> Pair(listOf(Color(0xFF26104F), Color(0xFF11042B)), "https://yt3.googleusercontent.com/QUGEA4FQc0BoAlHXCe71o85DIpcjDplPP_imjTkxsxZef4eAoyo4QIKTWHoU6jM0pCB5H1Fe3Uc=s1200")

        // ── Hash-based fallback for unknown categories ──
        else -> {
            val hash = name.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
            val colorsList = listOf(
                listOf(Color(0xFF3A1E17), Color(0xFF24100C)),
                listOf(Color(0xFF17243A), Color(0xFF0C1424)),
                listOf(Color(0xFF173A2A), Color(0xFF0C2419)),
                listOf(Color(0xFF3A3117), Color(0xFF241E0C)),
                listOf(Color(0xFF2C173A), Color(0xFF1B0C24)),
                listOf(Color(0xFF17383A), Color(0xFF0C2224)),
                listOf(Color(0xFF3A172B), Color(0xFF240C1A)),
                listOf(Color(0xFF1F1F1F), Color(0xFF121212))
            )
            val gradientColors = colorsList[hash % colorsList.size]
            val fallbackImage = "https://picsum.photos/seed/$hash/300/300"
            Pair(gradientColors, fallbackImage)
        }
    }
}

fun createBrightGradientFromBase(baseColor: Color): List<Color> {
    if (baseColor == Color.Black || baseColor == Color.Transparent || (baseColor.red < 0.1f && baseColor.green < 0.1f && baseColor.blue < 0.1f)) {
        return listOf(Color(0xFF0B1E36), Color(0xFF1E3C72))
    }

    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (baseColor.alpha * 255).toInt(),
            (baseColor.red * 255).toInt(),
            (baseColor.green * 255).toInt(),
            (baseColor.blue * 255).toInt()
        ),
        hsl
    )
    
    // Coerce HSL for a very sleek, rich, dark-mode gradient (instead of harsh neon colors)
    val brightHsl1 = floatArrayOf(
        hsl[0],
        hsl[1].coerceIn(0.35f, 0.60f), // pleasant saturation, not neon
        hsl[2].coerceIn(0.15f, 0.32f)  // deep/dark base
    )
    
    val brightHsl2 = floatArrayOf(
        (hsl[0] + 25f) % 360f,          // subtle shifting tone
        hsl[1].coerceIn(0.30f, 0.55f),
        hsl[2].coerceIn(0.10f, 0.24f)  // even darker accent
    )
    
    val color1 = Color(androidx.core.graphics.ColorUtils.HSLToColor(brightHsl1))
    val color2 = Color(androidx.core.graphics.ColorUtils.HSLToColor(brightHsl2))
    
    return listOf(color1, color2)
}


