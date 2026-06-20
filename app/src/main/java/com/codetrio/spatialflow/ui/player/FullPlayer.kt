package com.codetrio.spatialflow.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel

/**
 * Stateful wrapper for the FullPlayer UI component.
 * Decouples the UI component from ViewModel, Activity, and direct permission handling.
 */
@RequiresApi(Build.VERSION_CODES.Q)
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
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onArtistClick: (String?, String) -> Unit = { _, _ -> },
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val contentSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF1C1B1F).copy(alpha = 0.6f)

    val dynamicAccentColor = remember(accentColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(accentColor.toArgb(), hsl)
        if (hsl[1] < 0.08f) {
            // Monochromatic / Grayscale
            if (isDark) Color.White else Color(0xFF1C1B1F)
        } else {
            if (isDark) {
                accentColor
            } else {
                hsl[2] = hsl[2].coerceAtMost(0.45f)
                hsl[1] = hsl[1].coerceAtLeast(0.6f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
    }

    val backgroundBrush = remember(uiState.playerBackgroundColor, isDark) {
        val playerColor = Color(uiState.playerBackgroundColor)
        val finalColor = deriveArtworkSurfaceColor(
            sourceColor = playerColor,
            isDark = isDark,
            darkLightness = 0.155f,
            lightLightness = 0.835f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f
        )
        androidx.compose.ui.graphics.SolidColor(finalColor)
    }

    val haptic = LocalHapticFeedback.current
    val hasLyrics = !syncedLyrics.isNullOrEmpty() || !plainLyrics.isNullOrBlank()
    val currentSongId = uiState.currentSong?.id
    var lastLyricsFetchSongId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    
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
    
    val lyricsBackgroundBrush = remember(uiState.playerBackgroundColor, isDark) {
        val playerColor = Color(uiState.playerBackgroundColor)
        val finalColor = deriveArtworkSurfaceColor(
            sourceColor = playerColor,
            isDark = isDark,
            darkLightness = 0.145f,
            lightLightness = 0.825f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f
        )
        androidx.compose.ui.graphics.SolidColor(finalColor)
    }

    // Trigger lyrics load when enabled
    LaunchedEffect(isLyricsModeEnabled, currentSongId) {
        if (!isLyricsModeEnabled) return@LaunchedEffect
        if (currentSongId == null) {
            lastLyricsFetchSongId = null
            return@LaunchedEffect
        }
        if (lastLyricsFetchSongId != currentSongId) {
            lastLyricsFetchSongId = currentSongId
            onFetchLyrics()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .then(if (isQueueExpanded || isLyricsModeEnabled) Modifier else dragModifier) // Drag down anywhere to collapse
    ) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val albumArtSize = screenWidth * 0.9f

        val density = androidx.compose.ui.platform.LocalDensity.current
        val statusBarTopDp = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
        val minTopOffset = statusBarTopDp + 68.dp // Removed 16.dp extra gap

        // Calculate top offset to perfectly match yEndPx in PlayerBottomSheetCompose
        val topOffset = ((screenHeight - albumArtSize) / 2f - 220.dp).coerceAtLeast(minTopOffset)

        var lyricsButtonCenterInRoot by remember { mutableStateOf<Offset?>(null) }
        val lyricsRevealProgress by animateFloatAsState(
            targetValue = if (isLyricsModeEnabled) 1f else 0f,
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            label = "LyricsCircularReveal"
        )

        // Tie the visibility/readiness directly to the lyricsRevealProgress animation state
        val lyricsContentReady = lyricsRevealProgress > 0.8f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
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

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentSecondary
                )

                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(topOffset - (statusBarTopDp + 68.dp)))

            // Album Art Container Placeholder (ArtworkPager is rendered at this absolute position)
            Box(
                modifier = Modifier
                    .size(albumArtSize)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata row: title/artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
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
                        if (currentSongId != null && lastLyricsFetchSongId != currentSongId && !hasLyrics && !isLyricsLoading) {
                            lastLyricsFetchSongId = currentSongId
                            onFetchLyrics()
                        }
                    },
                    contentColor = contentColor,
                    accentColor = dynamicAccentColor,
                    isDark = isDark,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        lyricsButtonCenterInRoot = Offset(
                            x = position.x + coordinates.size.width / 2f,
                            y = position.y + coordinates.size.height / 2f
                        )
                    }
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
                isDark = isDark
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

        if (lyricsRevealProgress > 0f) {
            FullScreenLyricsOverlay(
                currentSong = uiState.currentSong,
                syncedLyrics = syncedLyrics,
                plainLyrics = plainLyrics,
                isLoading = isLyricsLoading,
                lyricsError = lyricsError,
                currentPositionProvider = currentPositionProvider,
                contentReady = lyricsContentReady,
                backgroundBrush = lyricsBackgroundBrush,
                revealProgressProvider = { lyricsRevealProgress },
                revealCenterProvider = { lyricsButtonCenterInRoot },
                contentColor = contentColor,
                contentSecondary = contentSecondary,
                dynamicAccentColor = dynamicAccentColor,
                onRetryLyrics = onRetryLyrics,
                onFetchLyrics = onFetchLyrics,
                onSeekTo = onSeekTo,
                providerResults = providerResults,
                selectedProvider = selectedProvider,
                onProviderSelected = onProviderSelected,
                modifier = Modifier.fillMaxSize()
            )
        }

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
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleLoopMode = { viewModel.toggleLoopMode() },
            onShowSleepTimerDialog = { showSleepTimerDialog = true },
            playerBackgroundColor = uiState.playerBackgroundColor,
            dynamicAccentColor = dynamicAccentColor,
            isDark = isDark
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
