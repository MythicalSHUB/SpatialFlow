package com.codetrio.spatialflow.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.player.queue.SlidingQueueDrawer
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stateful wrapper for the FullPlayer UI component.
 * Decouples the UI component from ViewModel, Activity, and direct permission handling.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    context: Context,
    onCollapse: () -> Unit,
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val hapticManager = viewModel.hapticManager
    val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
    val syncedLyrics by viewModel.syncedLyrics.collectAsStateWithLifecycle()
    val plainLyrics by viewModel.plainLyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()
    val providerResults by viewModel.providerResults.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val currentPositionState = viewModel.currentPosition.collectAsStateWithLifecycle()
    val isAutoplayEnabled by viewModel.isAutoplayEnabled.collectAsStateWithLifecycle()

    // Handle back button when lyrics mode is enabled
    BackHandler(enabled = isLyricsModeEnabled) {
        viewModel.setLyricsModeEnabled(false)
    }

    // Dynamically register the active Compose view hosting FullPlayer inside PlayerHapticManager.
    DisposableEffect(view, hapticManager) {
        hapticManager?.attachView(view)
        onDispose {
            hapticManager?.detachView()
        }
    }

    // Modern Compose-way of handling audio recording permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setHapticsEnabled(true)
        } else {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Microphone permission required for haptics")
        }
    }
    
    val localPlaylists by viewModel.localPlaylistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    if (showAddToPlaylistDialog) {
        com.codetrio.spatialflow.ui.LocalPlaylistPickerDialog(
            playlists = localPlaylists,
            onCreateNew = {
                showCreatePlaylistDialog = true
                showAddToPlaylistDialog = false
            },
            onPlaylistSelected = { playlist ->
                val currentSong = uiState.currentSong
                if (currentSong != null) {
                    viewModel.addSongToLocalPlaylist(playlist.id, currentSong)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Added to playlist: ${playlist.title}")
                }
                showAddToPlaylistDialog = false
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    if (showCreatePlaylistDialog) {
        com.codetrio.spatialflow.ui.CreateLocalPlaylistDialog(
            onConfirm = { name ->
                viewModel.createLocalPlaylist(name)
                showCreatePlaylistDialog = false
                showAddToPlaylistDialog = true
            },
            onDismiss = {
                showCreatePlaylistDialog = false
                showAddToPlaylistDialog = true
            }
        )
    }

    FullPlayer(
        viewModel = viewModel,
        uiState = uiState,
        songList = songList,
        accentColor = accentColor,
        isLyricsModeEnabled = isLyricsModeEnabled,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        isLyricsLoading = isLyricsLoading,
        lyricsError = lyricsError,
        providerResults = providerResults,
        selectedProvider = selectedProvider,
        onProviderSelected = { viewModel.selectLyricsProvider(it) },
        currentPositionProvider = { currentPositionState.value },
        isAutoplayEnabled = isAutoplayEnabled,
        onAutoplayToggle = { viewModel.setAutoplayEnabled(!isAutoplayEnabled) },
        onCollapse = onCollapse,
        onPlayPauseClick = {
            if (uiState.isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
        },
        onPreviousClick = {
            viewModel.playPreviousSong()
        },
        onNextClick = {
            viewModel.playNextSong(force = true)
        },
        onHapticChipClick = {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.setHapticsEnabled(!uiState.isHapticsEnabled)
            }
        },
        onLyricsModeChanged = { enabled ->
            viewModel.setLyricsModeEnabled(enabled)
        },
        onFetchLyrics = {
            viewModel.fetchLyricsForCurrentSong()
        },
        onRetryLyrics = {
            viewModel.retryLyrics()
        },
        onSeekTo = { position ->
            viewModel.seekTo(position)
        },
        onFavoriteClick = {
            viewModel.toggleFavorite()
        },
        onDislikeClick = {
            viewModel.toggleDislike()
        },
        onSaveClick = {
            showAddToPlaylistDialog = true
        },
        onArtistClick = { artistId, artistName ->
            onCollapse()
            activity.showArtistPage(artistId, artistName)
        },
        dragModifier = dragModifier,
        modifier = modifier
    )
}

/**
 * Purely stateless UI representation of the Full Player.
 * Does not depend on ViewModels or Activities, ensuring great previewability and testability.
 */
@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun FullPlayer(
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    isLyricsModeEnabled: Boolean,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLyricsLoading: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onHapticChipClick: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onFetchLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onFavoriteClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onSaveClick: () -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    isAutoplayEnabled: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    onArtistClick: (String?, String) -> Unit = { _, _ -> },
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val canvasArtwork by viewModel.canvasArtwork.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
    val showAnimatedArt = prefs.getBoolean("show_animated_art", true)
    val playerTheme = prefs.getString("player_theme", "fluid") ?: "fluid"
    val isStatic = playerTheme == "static"
    val hasCanvas = !isStatic && showAnimatedArt && canvasArtwork != null

    val isTextColorDark = !isDark && isStatic
    val contentColor = if (isTextColorDark) Color(0xFF1C1B1F) else Color.White
    val contentSecondary = if (isTextColorDark) Color(0xFF1C1B1F).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)

    val dynamicAccentColor = remember(accentColor, isTextColorDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(accentColor.toArgb(), hsl)
        if (hsl[1] < 0.08f) {
            // Monochromatic / Grayscale
            if (isTextColorDark) Color(0xFF1C1B1F) else Color.White
        } else {
            if (!isTextColorDark) {
                accentColor
            } else {
                hsl[2] = hsl[2].coerceAtMost(0.45f)
                hsl[1] = hsl[1].coerceAtLeast(0.6f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
    }

    val playerBackgroundColor = remember(uiState.playerBackgroundColor, isTextColorDark) {
        val baseColor = Color(uiState.playerBackgroundColor)
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
        val isMonochrome = hsl[1] < 0.06f
        if (!isTextColorDark) {
            hsl[2] = 0.155f
            hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.32f, 0.54f)
            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        } else {
            hsl[2] = 0.835f
            hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.30f, 0.48f)
            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        }
    }

    val haptic = LocalHapticFeedback.current
    val hasLyrics = !syncedLyrics.isNullOrEmpty() || !plainLyrics.isNullOrBlank()
    val currentSongId = uiState.currentSong?.id
    
    // Sliding Queue Drawer State
    val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()

    // Unify BackHandler to collapse the sliding Queue drawer first
    BackHandler(enabled = isLyricsModeEnabled || isQueueExpanded) {
        if (isLyricsModeEnabled) {
            onLyricsModeChanged(false)
        } else if (isQueueExpanded) {
            viewModel.setQueueExpanded(false)
        }
    }

    // Sleep Timer State & Controller Dialog state
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerEndTime by viewModel.sleepTimerEndTime.collectAsStateWithLifecycle()

    // ── Palette extraction pipeline (spec §2.2) ─────────────────────────────────
    // Triggered whenever the playing track's artwork changes.
    // Runs entirely on Dispatchers.IO — never blocks the main thread.
    // Downloads artwork → BitmapResolver.bitmapCompress (64 px square, RGB_565) →
    // AndroidX Palette.generate() → writes vibrant / darkVibrant / darkMuted into
    // PlayerPaletteState (global singleton). Falls back to Color.Black on missing swatch.
    // ── Palette extraction key ────────────────────────────────────────────────────
    // IMPORTANT: for local MP3 files, thumbnailUrl AND videoId are both null.
    // We MUST include getAlbumArtUri() first, otherwise artworkKey is null and
    // the LaunchedEffect short-circuits → PlayerPaletteState stays Color.Black → pitch black.
    val artworkKey = remember(uiState.currentSong) {
        uiState.currentSong?.let { song ->
            song.getAlbumArtUri()?.toString()       // local MP3 embedded art  (content://)
                ?: song.thumbnailUrl                 // YouTube Music stream
                ?: song.videoId?.let { "yt_$it" }   // YouTube video ID fallback
        }
    }
    val paletteContext = context
    LaunchedEffect(artworkKey) {
        if (artworkKey == null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val song = uiState.currentSong ?: return@withContext
            // artworkData priority: local URI → thumbnail URL → YouTube fallback
            val artworkData: Any = song.getAlbumArtUri()
                ?: song.thumbnailUrl
                ?: song.videoId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                ?: return@withContext

            val loader = ImageLoader(paletteContext)
            try {
                val request = ImageRequest.Builder(paletteContext)
                    .data(artworkData)
                    .allowHardware(false) // hardware bitmaps cannot be read by Palette
                    .build()

                // Step 1 — download/load artwork
                val thisBitmap = loader.execute(request).drawable
                    ?.toBitmap()
                    ?.run { BitmapResolver.bitmapCompress(this) }  // 64 px, RGB_565

                if (thisBitmap != null) {
                    try {
                        // Step 2 — run AndroidX Palette on the compressed bitmap
                        val palette = Palette.from(thisBitmap).generate()

                        // Use dominantSwatch as non-black fallback when a specific
                        // swatch is missing (dark/monochromatic art like "After Dark").
                        val dominant = palette.dominantSwatch?.rgb?.let { Color(it) }

                        PlayerPaletteState.vibrantColor.value =
                            palette.vibrantSwatch?.rgb?.let { Color(it) } ?: Color.Black

                        PlayerPaletteState.darkVibrantColor.value =
                            palette.darkVibrantSwatch?.rgb?.let { Color(it) } ?: Color.Black

                        PlayerPaletteState.darkMutedColor.value =
                            palette.darkMutedSwatch?.rgb?.let { Color(it) } ?: Color.Black

                    } catch (_: Exception) { /* palette failure — keep existing colors */ }

                    thisBitmap.recycle() // ← always recycle to avoid OOM
                }
            } finally {
                loader.shutdown() // ← always shut down the loader
            }
        }
    }
    // ── End palette extraction ──────────────────────────────────────────────────

    AppleMusicBackground(
        song = uiState.currentSong,
        canvasArtwork = canvasArtwork,
        isPlaying = uiState.isPlaying,
        isLyricsModeEnabled = isLyricsModeEnabled,
        modifier = modifier
            .fillMaxSize()
            .then(if (isQueueExpanded || isLyricsModeEnabled) Modifier else dragModifier)
    ) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val albumArtSize = androidx.compose.ui.unit.min(screenWidth * 0.9f, screenHeight * 0.45f)

        val density = androidx.compose.ui.platform.LocalDensity.current
        val statusBarTopDp = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
        val minTopOffset = statusBarTopDp + 68.dp // Removed 16.dp extra gap

        // Calculate top offset to perfectly match yEndPx in PlayerBottomSheetCompose
        val topOffset = ((screenHeight - albumArtSize) / 2f - 220.dp).coerceAtLeast(minTopOffset)

        val dimens = com.codetrio.spatialflow.ui.theme.LocalDimens.current
        val isTablet = configuration.screenWidthDp >= 600

        val rightPaneContent: @Composable () -> Unit = {
            // Metadata row: title/artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = uiState.currentSong?.title ?: "Unknown Title",
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarqueeWithFadedEdges()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.currentSong?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .basicMarqueeWithFadedEdges()
                            .clickable {
                                val song = uiState.currentSong
                                if (song != null) {
                                    onArtistClick(song.artistId, song.artist)
                                }
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Premium YT Music style horizontal control chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val pad = 20.dp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                maxWidth = constraints.maxWidth + 2 * pad
                            )
                        )
                        layout(placeable.width - 2 * pad, placeable.height) {
                            placeable.place(-pad, 0)
                        }
                    }
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp))

                SplitLikeDislikeChip(
                    isLiked = uiState.isCurrentSongFavorite,
                    isDisliked = uiState.isCurrentSongDisliked,
                    likesCount = uiState.likesCount,
                    onLikeClick = onFavoriteClick,
                    onDislikeClick = onDislikeClick,
                    contentColor = contentColor,
                    accentColor = dynamicAccentColor,
                    isDark = isDark
                )

                // Interactive Music Haptics Chip inside the same row
                PillChip(
                    icon = painterResource(id = R.drawable.ic_haptic),
                    label = "Music Haptics",
                    isSelected = uiState.isHapticsEnabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHapticChipClick()
                    },
                    contentColor = contentColor,
                    accentColor = dynamicAccentColor,
                    isDark = isDark
                )

                // Interactive Lyrics Chip inside the same row
                PillChip(
                    icon = painterResource(id = R.drawable.ic_lyrics),
                    label = "Lyrics",
                    isSelected = isLyricsModeEnabled,
                    onClick = {
                        onLyricsModeChanged(true)
                        if (currentSongId != null && !hasLyrics && !isLyricsLoading) {
                            onFetchLyrics()
                        }
                    },
                    contentColor = contentColor,
                    accentColor = dynamicAccentColor,
                    isDark = isDark
                )


                PillChip(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = "Save",
                    onClick = onSaveClick,
                    contentColor = contentColor,
                    accentColor = dynamicAccentColor,
                    isDark = isDark
                )

                PillChip(
                    icon = painterResource(id = R.drawable.ic_share),
                    label = "Share",
                    onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Listening on SpatialFlow Check out : https://music.youtube.com/watch?v=${uiState.currentSong?.videoId}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                    },
                    contentColor = contentColor,
                    accentColor = dynamicAccentColor,
                    isDark = isDark
                )

                val realDownloaded = uiState.isCurrentSongDownloaded
                val realDownloadProgress = uiState.currentSongDownloadProgress
                val isDownloading = realDownloadProgress != null

                val downloadLabel = when {
                    realDownloaded -> "Downloaded"
                    isDownloading -> "Downloading ${realDownloadProgress}%"
                    else -> "Download"
                }
                val downloadIcon: Any = when {
                    realDownloaded -> painterResource(id = R.drawable.ic_downloaded)
                    else -> painterResource(id = R.drawable.ic_download)
                }
                PillChip(
                    icon = downloadIcon,
                    label = downloadLabel,
                    isSelected = realDownloaded || isDownloading,
                    progress = if (isDownloading) realDownloadProgress / 100f else null,
                    onClick = {
                        val currentSong = uiState.currentSong
                        if (currentSong != null && !realDownloaded && !isDownloading) {
                            com.codetrio.spatialflow.util.SongDownloader.downloadSong(context, currentSong)
                        }
                    },
                    contentColor = contentColor,
                    accentColor = dynamicAccentColor,
                    isDark = isDark
                )

                Spacer(modifier = Modifier.width(12.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Premium Wavy Seek Bar (Isolated)
            WavySliderWithLabels(
                currentPositionProvider = currentPositionProvider,
                duration = uiState.duration,
                isPlaying = uiState.isPlaying,
                onSeekTo = onSeekTo,
                dynamicAccentColor = dynamicAccentColor,
                contentColor = contentColor,
                contentSecondary = contentSecondary,
                isDark = isDark,
                playbackFormat = uiState.playbackFormat
            )

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.ButtonGroup(
                modifier = Modifier
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
                            label = "PrevCorner"
                        )
                        androidx.compose.material3.Button(
                            onClick = onPreviousClick,
                            modifier = with(scope) {
                                Modifier
                                    .animateWidth(interactionSource)
                                    .weight(1f)
                                    .height(76.dp)
                            },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(cornerRadius),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
                                contentColor = contentColor
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_skip_previous),
                                    contentDescription = "Previous Song",
                                    modifier = Modifier.size(36.dp)
                                )
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
                            label = "PlayCorner"
                        )
                        androidx.compose.material3.Button(
                            onClick = onPlayPauseClick,
                            modifier = with(scope) {
                                Modifier
                                    .animateWidth(interactionSource)
                                    .weight(1.2f)
                                    .height(76.dp)
                            },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(cornerRadius),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = dynamicAccentColor,
                                contentColor = if (isDark) Color(0xFF1C1B1F) else Color.White
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = if (uiState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(42.dp)
                                )
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
                            label = "NextCorner"
                        )
                        androidx.compose.material3.Button(
                            onClick = onNextClick,
                            modifier = with(scope) {
                                Modifier
                                    .animateWidth(interactionSource)
                                    .weight(1f)
                                    .height(76.dp)
                            },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(cornerRadius),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
                                contentColor = contentColor
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_skip_next),
                                    contentDescription = "Next Song",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    },
                    menuContent = {}
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Swipe Up / Click Chevron Up Indicator to expand Queue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -10f && !isQueueExpanded && !isLyricsModeEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setQueueExpanded(true)
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setQueueExpanded(true)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Open Queue",
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
        }

        AnimatedVisibility(
            visible = !isLyricsModeEnabled,
            enter = fadeIn(animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = dimens.screenMargin, vertical = dimens.smallPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row (Nav controls + collapse) - Symmetric centering
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                            contentDescription = "Collapse Player",
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (!hasCanvas || isLyricsModeEnabled) {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentSecondary
                        )
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Spacer(modifier = Modifier.size(48.dp))
                }

                if (isTablet) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(albumArtSize))
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            rightPaneContent()
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(topOffset - (statusBarTopDp + 68.dp)))

                    // Album Art Container Placeholder (ArtworkPager is rendered at this absolute position)
                    Box(
                        modifier = Modifier.size(albumArtSize)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    rightPaneContent()
                }
            }
        }

        LyricsBottomSheet(
            visible = isLyricsModeEnabled,
            currentSong = uiState.currentSong,
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics,
            isLoading = isLyricsLoading,
            lyricsError = lyricsError,
            currentPositionProvider = currentPositionProvider,
            contentReady = true,
            playerBackgroundColor = playerBackgroundColor,
            canvasArtwork = canvasArtwork,
            contentColor = contentColor,
            contentSecondary = contentSecondary,
            dynamicAccentColor = dynamicAccentColor,
            onRetryLyrics = onRetryLyrics,
            onFetchLyrics = onFetchLyrics,
            onSeekTo = onSeekTo,
            providerResults = providerResults,
            selectedProvider = selectedProvider,
            onProviderSelected = onProviderSelected,
            isPlaying = uiState.isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            duration = uiState.duration.toLong(),
            onCollapse = { viewModel.setLyricsModeEnabled(false) },
            modifier = Modifier.fillMaxSize()
        )

        // --- CUSTOM EMBEDDED SLIDING PLAY QUEUE ---
        SlidingQueueDrawer(
            isQueueExpanded = isQueueExpanded,
            onQueueExpandedChange = { viewModel.setQueueExpanded(it) },
            songList = songList,
            currentSongIndex = currentSongIndex,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            sleepTimerMode = sleepTimerMode,
            onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
            onPlaySongAtIndex = { index -> viewModel.playSongAtIndex(index) },
            onRemoveSongAtIndex = { index -> viewModel.removeSongAtIndex(index) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleLoopMode = { viewModel.toggleLoopMode() },
            onShowSleepTimerDialog = { showSleepTimerDialog = true },
            playerBackgroundColor = playerBackgroundColor.toArgb(),
            dynamicAccentColor = dynamicAccentColor,
            isDark = isDark,
            isAutoplayEnabled = isAutoplayEnabled,
            onAutoplayToggle = onAutoplayToggle
        )

        // --- Standalone Sleep Timer Bottom Sheet ---
        if (showSleepTimerDialog) {
            SleepTimerBottomSheet(
                onDismissRequest = { showSleepTimerDialog = false },
                sleepTimerEndTime = sleepTimerEndTime,
                sleepTimerMode = sleepTimerMode,
                onStartTimer = { mins ->
                    viewModel.startCustomSleepTimer(mins)
                },
                onCancelTimer = {
                    viewModel.cancelSleepTimer()
                },
                onSetEndOfSong = { enable ->
                    if (enable) viewModel.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.END_OF_SONG)
                    else viewModel.cancelSleepTimer()
                }
            )
        }
    }
}
