package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem

/**
 * Custom Compose extension to render a marquee with smooth horizontal alpha-faded edges.
 * Uses drawWithCache to avoid allocating Brush and List objects on every frame of the drawing phase.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.basicMarqueeWithFadedEdges(
    edgeWidth: Dp = 12.dp
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val edgeWidthPx = edgeWidth.toPx()
        // Cache the brushes so they aren't recreated every frame
        val leftBrush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = 0f,
            endX = edgeWidthPx
        )
        val rightBrush = Brush.horizontalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startX = size.width - edgeWidthPx,
            endX = size.width
        )
        
        onDrawWithContent {
            drawContent()
            drawRect(brush = leftBrush, blendMode = BlendMode.DstIn)
            drawRect(brush = rightBrush, blendMode = BlendMode.DstIn)
        }
    }
    .basicMarquee()
    .padding(horizontal = edgeWidth)

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

internal fun deriveArtworkSurfaceColor(
    sourceColor: Color,
    isDark: Boolean,
    darkLightness: Float,
    lightLightness: Float,
    darkSaturationRange: ClosedFloatingPointRange<Float>,
    lightSaturationRange: ClosedFloatingPointRange<Float>,
    monochromeSaturationThreshold: Float = 0.06f
): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(sourceColor.toArgb(), hsl)
    val isMonochrome = hsl[1] < monochromeSaturationThreshold
    hsl[2] = if (isDark) darkLightness else lightLightness
    hsl[1] = if (isMonochrome) {
        0f
    } else if (isDark) {
        hsl[1].coerceIn(darkSaturationRange.start, darkSaturationRange.endInclusive)
    } else {
        hsl[1].coerceIn(lightSaturationRange.start, lightSaturationRange.endInclusive)
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
internal fun SplitLikeDislikeChip(
    isLiked: Boolean,
    isDisliked: Boolean,
    likesCount: String,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean
) {
    val backgroundColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
    val displayLikesText = remember(likesCount) {
        likesCount.ifBlank { "Like" }
    }
    
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like Button
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .clickable(onClick = onLikeClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = if (isLiked) R.drawable.ic_thumbup else R.drawable.ic_outline_thumbup),
                contentDescription = "Like",
                tint = if (isLiked) accentColor else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = displayLikesText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (isLiked) accentColor else contentColor.copy(alpha = 0.8f)
            )
        }
        
        // Vertical Divider
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.5f)
                .background(contentColor.copy(alpha = 0.15f))
        )
        
        // Dislike Button
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                .clickable(onClick = onDislikeClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (isDisliked) R.drawable.ic_thumbdown else R.drawable.ic_outline_thumbdown),
                contentDescription = "Dislike",
                tint = if (isDisliked) accentColor else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun PillChip(
    icon: Any,
    label: String,
    onClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean,
    isSelected: Boolean = false,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        accentColor.copy(alpha = if (isDark) 0.25f else 0.18f)
    } else {
        contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
    }
    
    val tintColor = if (isSelected) accentColor else contentColor.copy(alpha = 0.8f)
    val progressColor = accentColor.copy(alpha = if (isDark) 0.35f else 0.25f)
    
    val animatedFill by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 150f),
        label = "DownloadChipProgress"
    )
    
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .drawBehind {
                if (progress != null && progress > 0f) {
                    drawRect(
                        color = progressColor,
                        size = size.copy(width = size.width * animatedFill)
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (icon) {
                is ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                is Painter -> {
                    Icon(
                        painter = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = tintColor
            )
        }
    }
}

@Composable
internal fun WavySliderWithLabels(
    currentPositionProvider: () -> Int,
    duration: Int,
    isPlaying: Boolean,
    onSeekTo: (Int) -> Unit,
    dynamicAccentColor: Color,
    contentColor: Color,
    contentSecondary: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderScrubPos by remember { mutableFloatStateOf(0f) }

    val currentPosition = currentPositionProvider()
    val safeDur = if (duration > 0) duration.toFloat() else 1f
    val displayPos = if (isScrubbing) (sliderScrubPos * safeDur).toInt() else currentPosition
    val progressRatio = (currentPosition.toFloat() / safeDur).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        WavyMusicSlider(
            value = if (isScrubbing) sliderScrubPos else progressRatio,
            onValueChange = {
                isScrubbing = true
                sliderScrubPos = it
            },
            onValueChangeFinished = {
                isScrubbing = false
                onSeekTo((sliderScrubPos * safeDur).toInt())
            },
            activeTrackColor = dynamicAccentColor,
            inactiveTrackColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
            thumbColor = contentColor,
            isPlaying = isPlaying,
            trackHeight = 4.dp,
            thumbRadius = 6.dp,
            waveAmplitudeWhenPlaying = 6.dp,
            waveLength = 48.dp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayPos.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = contentSecondary
            )
            Text(
                text = formatDuration(duration.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = contentSecondary
            )
        }
    }
}

/**
 * Footer showing song metadata at the bottom of lyrics.
 * Displays song name, artist, album, and lyrics provider — only when values are present.
 * Styled to look "always inactive" with small text and low opacity.
 */
@Composable
internal fun LyricsMetadataFooter(
    currentSong: SongItem?,
    selectedProvider: String?,
    providerResults: Map<String, LyricsResult>,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

    // Resolve album from the active provider's matched result
    val activeResult = selectedProvider?.let { providerResults[it] }
    val albumName = activeResult?.matchedAlbum

    val mutedColor = contentColor.copy(alpha = 0.35f)
    val metaStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp,
        color = mutedColor
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Song title
        if (currentSong.title.isNotBlank()) {
            Text(text = currentSong.title, style = metaStyle, maxLines = 1)
        }
        // Artist
        if (currentSong.artist.isNotBlank() &&
            !currentSong.artist.equals("Unknown Artist", ignoreCase = true)
        ) {
            Text(text = currentSong.artist, style = metaStyle, maxLines = 1)
        }
        // Album (from lyrics provider match)
        if (!albumName.isNullOrBlank()) {
            Text(text = albumName, style = metaStyle, maxLines = 1)
        }
        // Lyrics provider
        if (!selectedProvider.isNullOrBlank()) {
            Text(
                text = "Lyrics by $selectedProvider",
                style = metaStyle,
                maxLines = 1
            )
        }
    }
}
