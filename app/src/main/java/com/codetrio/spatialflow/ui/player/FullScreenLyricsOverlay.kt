package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import kotlin.math.hypot
import kotlin.math.max

/**
 * Optimized circular reveal modifier utilizing a remembered path and in-place reset/rebuild.
 * Ensures zero Path or Rect allocations on every single animation drawing frame!
 */
private fun Modifier.circularRevealFrom(
    progressProvider: () -> Float,
    centerProvider: () -> Offset?
): Modifier = this.drawWithCache {
    val path = Path()
    onDrawWithContent {
        val progress = progressProvider()
        val normalizedProgress = progress.coerceIn(0f, 1f)
        if (normalizedProgress <= 0f) return@onDrawWithContent
        if (normalizedProgress >= 0.999f) {
            drawContent()
            return@onDrawWithContent
        }

        val fallbackCenter = Offset(size.width * 0.9f, size.height * 0.72f)
        val revealCenter = centerProvider() ?: fallbackCenter

        val maxRadius = max(
            max(
                hypot(revealCenter.x.toDouble(), revealCenter.y.toDouble()),
                hypot((size.width - revealCenter.x).toDouble(), revealCenter.y.toDouble())
            ),
            max(
                hypot(revealCenter.x.toDouble(), (size.height - revealCenter.y).toDouble()),
                hypot((size.width - revealCenter.x).toDouble(), (size.height - revealCenter.y).toDouble())
            )
        ).toFloat()

        val radius = maxRadius * normalizedProgress
        
        path.reset() // Reuse existing Path object
        path.addOval(
            Rect(
                left = revealCenter.x - radius,
                top = revealCenter.y - radius,
                right = revealCenter.x + radius,
                bottom = revealCenter.y + radius
            )
        )

        clipPath(path) {
            this@onDrawWithContent.drawContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullScreenLyricsOverlay(
    currentSong: SongItem?,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLoading: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    contentReady: Boolean,
    backgroundBrush: Brush,
    revealProgressProvider: () -> Float,
    revealCenterProvider: () -> Offset?,
    contentColor: Color,
    contentSecondary: Color,
    dynamicAccentColor: Color,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val consumeClicks = remember { MutableInteractionSource() }
    var showProvidersSheet by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Box(
        modifier = modifier
            .circularRevealFrom(
                progressProvider = revealProgressProvider,
                centerProvider = revealCenterProvider
            )
            .background(backgroundBrush)
            .clickable(
                interactionSource = consumeClicks,
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Centered Title Header Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(48.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Text(
                            text = "LYRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )

                        val activeResult = providerResults[selectedProvider] ?: providerResults.values
                            .filter { it.confidence >= 0f }
                            .maxWithOrNull(
                                compareBy<LyricsResult> { it.providerName == "SyncLRC" }
                                    .thenBy { it.confidence }
                            )

                        val lyricTypeBadge = when {
                            activeResult == null -> null
                            !activeResult.hasLyrics() -> null
                            activeResult.isWordByWord -> "Karaoke Lyrics"
                            activeResult.isSynced -> "Synced Lyrics"
                            else -> "Plain Lyrics"
                        }

                        AnimatedVisibility(
                            visible = lyricTypeBadge != null,
                            enter = fadeIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + slideInHorizontally(
                                initialOffsetX = { it / 2 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ),
                            exit = fadeOut() + slideOutHorizontally()
                        ) {
                            if (lyricTypeBadge != null) {
                                Text(
                                    text = " • $lyricTypeBadge",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = currentSong?.title ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp)
                    )
                }

                IconButton(onClick = { showProvidersSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Change Lyrics Provider",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !contentReady -> Unit

                    !syncedLyrics.isNullOrEmpty() -> {
                        SyncedLyricsCompose(
                            onSeekTo = onSeekTo,
                            lyrics = syncedLyrics,
                            currentPositionProvider = currentPositionProvider,
                            contentColor = contentColor,
                            dynamicAccentColor = dynamicAccentColor,
                            currentSong = currentSong,
                            selectedProvider = selectedProvider,
                            providerResults = providerResults,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    !plainLyrics.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 28.dp)
                        ) {
                            Text(
                                text = plainLyrics,
                                style = MaterialTheme.typography.titleLarge,
                                color = contentColor.copy(alpha = 0.9f)
                            )
                            LyricsMetadataFooter(
                                currentSong = currentSong,
                                selectedProvider = selectedProvider,
                                providerResults = providerResults,
                                contentColor = contentColor
                            )
                        }
                    }

                    isLoading -> {
                        CircularProgressIndicator(color = dynamicAccentColor)
                    }

                    lyricsError != null -> {
                        LyricsErrorState(
                            message = lyricsError.message ?: "Lyrics not found",
                            onRetry = onRetryLyrics
                        )
                    }

                    else -> {
                        LyricsErrorState(
                            message = "Lyrics are not loaded yet",
                            onRetry = onFetchLyrics
                        )
                    }
                }
            }

            if (showProvidersSheet && contentReady) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showProvidersSheet = false },
                    sheetState = sheetState,
                    containerColor = Color.Transparent,
                    dragHandle = null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(backgroundBrush)
                            .navigationBarsPadding()
                            .padding(top = 16.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.4f))
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            ProvidersSelector(
                                providerResults = providerResults,
                                selectedProvider = selectedProvider,
                                onProviderSelected = onProviderSelected,
                                onRefindClick = onRetryLyrics,
                                accentColor = dynamicAccentColor,
                                contentColor = contentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProvidersSelector(
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRefindClick: () -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val knownProviders = listOf(
        "SyncLRC",
        "Apple Music",
        "Spotify",
        "Musixmatch",
        "LRCLIB",
        "YouTube Music",
        "YouTube (Paxsenix)"
    )

    val visibleProviders = knownProviders.filter { provider ->
        val result = providerResults[provider]
        val hasFinished = providerResults.containsKey(provider)
        val hasData = result != null && result.hasLyrics() && result.confidence >= 0f
        
        // Show if it hasn't finished searching, has lyrics, or is the currently selected provider
        !hasFinished || hasData || selectedProvider == provider
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "LYRICS PROVIDERS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            visibleProviders.forEachIndexed { index, provider ->
                val result = providerResults[provider]

                val isSelected = selectedProvider == provider || (selectedProvider == null && result != null && result.confidence >= 0f && result == providerResults.values.filter { it.confidence >= 0f }.maxWithOrNull(
                    compareBy<LyricsResult> { it.providerName == "SyncLRC" }
                        .thenBy { it.confidence }
                ))

                val hasData = result != null && result.hasLyrics() && result.confidence >= 0f

                val displayName = when (provider) {
                    "EmbeddedID3" -> "Embedded ID3"
                    else -> provider
                }

                val supportingText = when {
                    result == null -> "Searching..."
                    !result.hasLyrics() -> "No lyrics found"
                    result.isWordByWord -> "Karaoke (Word-by-word)"
                    result.isSynced -> "Synced (LRC)"
                    else -> "Plain Text"
                }

                val shape = getSegmentedShape(index = index, count = visibleProviders.size)

                val itemBgColor = when {
                    isSelected -> accentColor.copy(alpha = 0.15f)
                    hasData || result == null -> contentColor.copy(alpha = 0.06f)
                    else -> contentColor.copy(alpha = 0.02f)
                }

                ListItem(
                    selected = isSelected,
                    onClick = {
                        if (hasData) {
                            onProviderSelected(provider)
                        }
                    },
                    supportingContent = {
                        Text(text = supportingText)
                    },
                    leadingContent = {
                        val iconRes = when (provider) {
                            "SyncLRC" -> R.drawable.ic_lyrics
                            "LRCLIB" -> R.drawable.ic_lyrics
                            "YouTube Music" -> R.drawable.ic_youtube_music
                            else -> R.drawable.ic_lyrics
                        }
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = provider,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Selected",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = itemBgColor,
                        selectedContainerColor = itemBgColor,
                        contentColor = contentColor.copy(alpha = if (hasData || result == null) 0.9f else 0.4f),
                        selectedContentColor = accentColor,
                        leadingContentColor = contentColor.copy(alpha = if (hasData || result == null) 0.8f else 0.4f),
                        selectedLeadingContentColor = accentColor,
                        trailingContentColor = contentColor.copy(alpha = 0.8f),
                        selectedTrailingContentColor = accentColor,
                        supportingContentColor = contentColor.copy(alpha = 0.6f),
                        selectedSupportingContentColor = accentColor.copy(alpha = 0.7f),
                        disabledContainerColor = itemBgColor,
                        disabledContentColor = contentColor.copy(alpha = 0.3f),
                        disabledLeadingContentColor = contentColor.copy(alpha = 0.3f),
                        disabledTrailingContentColor = contentColor.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                ) {
                    Text(
                        text = displayName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val buttonInteractionSource = remember { MutableInteractionSource() }
        val buttonIsPressed by buttonInteractionSource.collectIsPressedAsState()

        val buttonCornerRadius by animateDpAsState(
            targetValue = if (buttonIsPressed) 28.dp else 16.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "refind_button_corners"
        )

        androidx.compose.material3.Button(
            onClick = onRefindClick,
            interactionSource = buttonInteractionSource,
            shape = RoundedCornerShape(buttonCornerRadius),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refind Lyrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

private fun getSegmentedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 24.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}
