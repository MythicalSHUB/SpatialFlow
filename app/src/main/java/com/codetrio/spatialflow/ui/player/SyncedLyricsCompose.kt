package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricWord
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexNonRounded

// ════════════════════════════════════════════════════════════════════════════════
// ─ Data Classes & Constants
// ════════════════════════════════════════════════════════════════════════════════

private data class LyricDisplayItem(
    val line: LyricLine,
    val originalIndex: Int,
    val isActive: Boolean = false,
    val isPast: Boolean = false
)

// Apple Music-inspired easing curves
private object AppleMusicEasing {
    val smoothEnter = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val smoothExit = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val wordSweep = CubicBezierEasing(0.33f, 0.66f, 0.66f, 1.00f)
    val scaleDown = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Main Synced Lyrics Composable
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SyncedLyricsCompose(
    onSeekTo: (Int) -> Unit,
    lyrics: List<LyricLine>,
    currentPositionProvider: () -> Int,
    contentColor: Color,
    dynamicAccentColor: Color,
    currentSong: SongItem? = null,
    selectedProvider: String? = null,
    providerResults: Map<String, LyricsResult> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val currentView = LocalView.current
    DisposableEffect(currentView) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    // ── Detect karaoke mode ──
    val isKaraokeMode = remember(lyrics) {
        lyrics.any { !it.isInterlude && it.isWordByWord && it.words.isNotEmpty() }
    }

    // ── Filter out interludes when in karaoke mode ──
    val displayItems = remember(lyrics, isKaraokeMode) {
        lyrics.mapIndexedNotNull { index, line ->
            if (isKaraokeMode && line.isInterlude) null
            else LyricDisplayItem(
                line = line,
                originalIndex = index
            )
        }
    }

    // ── Optimized binary search for active line index using derivedStateOf ──
    val activeIndex by remember(displayItems) {
        derivedStateOf {
            val currentPos = currentPositionProvider()
            if (displayItems.isEmpty()) -1
            else binarySearchActiveIndex(displayItems, currentPos)
        }
    }

    // ── Improved Apple Music-style springy auto-scroll ──
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            performSmoothedScroll(listState, activeIndex, displayItems)
        }
    }

    // ── Edge-faded container ──
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val edgePx = size.height * 0.20f

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = edgePx
                    ),
                    blendMode = BlendMode.DstIn
                )

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - edgePx,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 40.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(
                items = displayItems,
                key = { _, item -> "${item.line.startTimeMs}_${item.originalIndex}" }
            ) { displayIndex, item ->
                val line = item.line
                val isActive = displayIndex == activeIndex
                val isPast = displayIndex < activeIndex

                Box(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                ) {
                    if (line.isInterlude) {
                        AnimatedVisibility(
                            visible = isActive,
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = tween(300)),
                            exit = shrinkVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + slideOutVertically(
                                targetOffsetY = { -it / 2 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut(animationSpec = tween(250))
                        ) {
                            InterludeItem(
                                isActive = true,
                                currentPositionProvider = currentPositionProvider,
                                line = line,
                                nextLineStartMs = if (item.originalIndex + 1 < lyrics.size) {
                                    lyrics[item.originalIndex + 1].startTimeMs
                                } else {
                                    line.startTimeMs + 5000
                                },
                                accentColor = dynamicAccentColor,
                                contentColor = contentColor
                            )
                        }
                    } else {
                        LyricLineItem(
                            line = line,
                            isActive = isActive,
                            isPast = isPast,
                            currentPositionProvider = currentPositionProvider,
                            lineStartMs = line.startTimeMs,
                            lineEndMs = if (item.originalIndex + 1 < lyrics.size) {
                                lyrics[item.originalIndex + 1].startTimeMs
                            } else {
                                line.startTimeMs + 5000
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            onClick = {
                                onSeekTo(
                                    line.startTimeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                                )
                            }
                        )
                    }
                }
            }

            // ── Metadata footer at the end of lyrics ──
            item(key = "lyrics_metadata_footer") {
                LyricsMetadataFooter(
                    currentSong = currentSong,
                    selectedProvider = selectedProvider,
                    providerResults = providerResults,
                    contentColor = contentColor
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Lyric Line Item Composable - APPLE MUSIC STYLE HIGHLIGHTING
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionProvider: () -> Int,
    lineStartMs: Long,
    lineEndMs: Long,
    contentColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val isKaraoke = line.isWordByWord && line.words.isNotEmpty()
    val scale = 1.0f

    val alpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1.0f
            isPast -> 0.60f
            else -> 0.38f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "LyricAlpha"
    )

    val rawPos = if (isKaraoke && isActive) currentPositionProvider() else lineStartMs.toInt()
    val smoothedPos by animateFloatAsState(
        targetValue = rawPos.toFloat(),
        animationSpec = tween(
            durationMillis = 200,
            easing = LinearEasing
        ),
        label = "SmoothKaraokePos"
    )

    val bracketMatch = remember(line.content) {
        Regex("""^(.*?)\s*([({\[].+?[)}\]])\s*$""").find(line.content)
    }
    
    val hasValidMainContent = remember(bracketMatch) {
        bracketMatch != null && bracketMatch.groupValues[1].isNotBlank()
    }

    val mainContent = remember(line.content, bracketMatch, hasValidMainContent) {
        if (hasValidMainContent) {
            bracketMatch!!.groupValues[1].trimEnd() + " "
        } else {
            line.content + " "
        }
    }
    
    val bracketContent = remember(line.content, bracketMatch, hasValidMainContent) {
        if (hasValidMainContent) {
            bracketMatch!!.groupValues[2] + " "
        } else {
            null
        }
    }
    
    val bracketStartIndex = remember(line.content, bracketMatch, hasValidMainContent) {
        if (hasValidMainContent) bracketMatch!!.groups[2]?.range?.first ?: -1 else -1
    }

    val dimColor = contentColor.copy(alpha = 0.35f)
    val litColor = contentColor

    val commonModifier = Modifier
        .fillMaxWidth()
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
        .padding(start = 28.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)
        .graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)

            val driftProgress = if (isActive) {
                val pos = currentPositionProvider()
                val duration = (lineEndMs - lineStartMs).coerceAtLeast(1)
                ((pos - lineStartMs).toFloat() / duration).coerceIn(0f, 1f)
            } else 0f

            val driftDp = if (isActive) {
                val eased = easeOutCubic(driftProgress)
                (-2f * eased)
            } else 0f

            translationY = driftDp * density
        }

    val mainTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = GoogleSansFlexNonRounded,
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 50.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )

    val bracketTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = GoogleSansFlexNonRounded,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )

    val mainTextLayoutResult = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val bracketTextLayoutResult = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    Column(modifier = commonModifier) {
        // ── Main Text Box ──
        Box {
            // Base Dim Text
            Text(
                text = mainContent,
                style = mainTextStyle,
                color = if (isKaraoke) dimColor else (if (isActive) Color.White else dimColor),
                onTextLayout = { mainTextLayoutResult.value = it },
                maxLines = Int.MAX_VALUE
            )

            // Overlay Lit Text
            if (isKaraoke && isActive) {
                Text(
                    text = mainContent,
                    style = mainTextStyle.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = accentColor.copy(alpha = 0.5f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = 18f
                        )
                    ),
                    color = litColor,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithCache {
                            onDrawWithContent {
                                val layout = mainTextLayoutResult.value
                                if (layout != null) {
                                    drawContent()
                                    eraseFutureText(layout, line.words, smoothedPos.toLong(), 0, this)
                                }
                            }
                        }
                )
            }
        }

        // ── Bracket Text Box ──
        if (bracketContent != null) {
            Box(modifier = Modifier.padding(top = 4.dp)) {
                // Base Dim Text
                Text(
                    text = bracketContent,
                    style = bracketTextStyle,
                    color = if (isKaraoke) dimColor else (if (isActive) Color.White else dimColor),
                    onTextLayout = { bracketTextLayoutResult.value = it },
                    maxLines = 1
                )

                // Overlay Lit Text
                if (isKaraoke && isActive) {
                    Text(
                        text = bracketContent,
                        style = bracketTextStyle.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = accentColor.copy(alpha = 0.4f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                blurRadius = 18f
                            )
                        ),
                        color = litColor,
                        maxLines = 1,
                        modifier = Modifier
                            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                            .drawWithCache {
                                onDrawWithContent {
                                    val layout = bracketTextLayoutResult.value
                                    if (layout != null) {
                                        drawContent()
                                        eraseFutureText(layout, line.words, smoothedPos.toLong(), bracketStartIndex, this)
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}

private fun eraseFutureText(
    layout: androidx.compose.ui.text.TextLayoutResult,
    words: List<LyricWord>,
    pos: Long,
    wordOffset: Int,
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope
) {
    val textLength = layout.layoutInput.text.length
    for (charIndex in 0 until textLength) {
        val absoluteCharIndex = charIndex + wordOffset
        val controllingWord = findControllingWord(absoluteCharIndex, words, wordOffset)
        
        val charProgress = if (controllingWord != null) {
            calculateCharProgress(
                charIndex = charIndex,
                word = controllingWord,
                wordOffset = wordOffset,
                pos = pos
            )
        } else 0f
        
        if (charProgress >= 0.99f) {
            // Fully swept character: leave it fully lit (do not erase)
        } else if (charProgress < 0.01f) {
            // Fully future character: erase it completely!
            val path = layout.getPathForRange(charIndex, charIndex + 1)
            drawScope.drawPath(path, color = Color.Black, blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
        } else {
            // Partially sweeping character: soft gradient erase
            val path = layout.getPathForRange(charIndex, charIndex + 1)
            val box = layout.getBoundingBox(charIndex)
            
            val gradientWidth = box.width * 1.5f
            val sweepCenter = box.left + (box.width * charProgress)
            
            // We want to erase the right side. So the brush goes from Transparent (left) to Black (right)
            val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                0.0f to Color.Transparent, 
                1.0f to Color.Black,       
                startX = sweepCenter - (gradientWidth / 2f),
                endX = sweepCenter + (gradientWidth / 2f)
            )
            
            drawScope.drawPath(path, brush = brush, blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
        }
    }
}

private fun findControllingWord(absoluteCharIndex: Int, words: List<LyricWord>, offset: Int = 0): LyricWord? {
    if (words.isEmpty()) return null
    
    val exactWord = words.find { absoluteCharIndex in it.charRange }
    if (exactWord != null) return exactWord
    
    if (absoluteCharIndex < words.first().charRange.first) return words.first()
    if (absoluteCharIndex > words.last().charRange.last) return words.last()
    
    return words.lastOrNull { it.charRange.last < absoluteCharIndex } ?: words.first()
}

private fun calculateCharProgress(
    charIndex: Int,
    word: LyricWord,
    wordOffset: Int,
    pos: Long
): Float {
    val wordStartMs = word.absoluteStartTimeMs
    val wordEndMs = wordStartMs + word.durationMs.coerceAtLeast(120L)
    
    val wordProgress = when {
        pos < wordStartMs -> 0f
        pos >= wordEndMs -> 1f
        else -> {
            val duration = (wordEndMs - wordStartMs).toFloat().coerceAtLeast(1f)
            ((pos - wordStartMs).toFloat() / duration).coerceIn(0f, 1f)
        }
    }

    val easedWordProgress = easeOutCubic(wordProgress)
    
    val wStart = word.charRange.first - wordOffset
    val wEnd = (word.charRange.last + 1) - wordOffset
    val wordLength = (wEnd - wStart).toFloat().coerceAtLeast(1f)
    
    val sweepWidth = 0.35f
    val sweepPosition = easedWordProgress * (1f + sweepWidth)
    
    val charOffsetInWord = (charIndex - wStart).toFloat()
    val charRelativePosition = charOffsetInWord / wordLength
    
    return when {
        sweepPosition < charRelativePosition -> 0f
        sweepPosition >= (charRelativePosition + sweepWidth) -> 1f
        else -> (sweepPosition - charRelativePosition) / sweepWidth
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Interlude Item Composable
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun InterludeItem(
    isActive: Boolean,
    currentPositionProvider: () -> Int,
    line: LyricLine,
    nextLineStartMs: Long,
    accentColor: Color,
    contentColor: Color
) {
    val duration = (nextLineStartMs - line.startTimeMs).coerceAtLeast(1)
    val rawProgressState = remember(isActive, line.startTimeMs, nextLineStartMs) {
        derivedStateOf {
            if (isActive) {
                ((currentPositionProvider() - line.startTimeMs).toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
        }
    }

    val animatedProgressState = animateFloatAsState(
        targetValue = rawProgressState.value,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "InterludeProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "InterludeBreathing")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathScale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.85f else 0.25f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "InterludeAlpha"
    )

    val density = LocalDensity.current
    val thickStroke = remember(density) {
        androidx.compose.ui.graphics.drawscope.Stroke(
            width = with(density) { 4.dp.toPx() },
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = "Interlude",
            tint = accentColor.copy(alpha = iconAlpha),
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = if (isActive) breatheScale else 1f
                    scaleY = if (isActive) breatheScale else 1f
                }
        )

        LinearWavyProgressIndicator(
            progress = {
                animatedProgressState.value
            },
            modifier = Modifier
                .weight(1f)
                .height(11.dp),
            color = accentColor.copy(alpha = if (isActive) 0.75f else 0.18f),
            trackColor = contentColor.copy(alpha = 0.06f),
            stroke = thickStroke,
            trackStroke = thickStroke,
            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            amplitude = { p -> WavyProgressIndicatorDefaults.indicatorAmplitude(p) * 2.5f }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Error State Composable
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun LyricsErrorState(
    message: String,
    onRetry: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) {
        Color.White.copy(alpha = 0.85f)
    } else {
        Color(0xFF1C1B1F).copy(alpha = 0.85f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Helper Functions
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Interpolate between two colors smoothly
 * Used for Apple Music-style word highlighting
 */
private fun interpolateColor(
    startColor: Color,
    endColor: Color,
    progress: Float
): Color {
    val clampedProgress = progress.coerceIn(0f, 1f)

    return Color(
        red = startColor.red + (endColor.red - startColor.red) * clampedProgress,
        green = startColor.green + (endColor.green - startColor.green) * clampedProgress,
        blue = startColor.blue + (endColor.blue - startColor.blue) * clampedProgress,
        alpha = startColor.alpha + (endColor.alpha - startColor.alpha) * clampedProgress
    )
}

/**
 * Binary search to find the index of the active lyric line
 */
private fun binarySearchActiveIndex(
    displayItems: List<LyricDisplayItem>,
    currentPosition: Int
): Int {
    if (displayItems.isEmpty()) return -1

    var lo = 0
    var hi = displayItems.size - 1
    var result = -1

    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val itemStartTime = displayItems[mid].line.startTimeMs

        when {
            itemStartTime <= currentPosition -> {
                result = mid
                lo = mid + 1
            }
            else -> hi = mid - 1
        }
    }

    return result
}

/**
 * Performs smoothed scroll animation with spring physics
 */
private suspend fun performSmoothedScroll(
    listState: androidx.compose.foundation.lazy.LazyListState,
    activeIndex: Int,
    displayItems: List<LyricDisplayItem>
) {
    val visibleItem = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == activeIndex }

    val viewportHeight = listState.layoutInfo.viewportEndOffset -
            listState.layoutInfo.viewportStartOffset

    if (visibleItem != null && viewportHeight > 0) {
        val targetCenterOffset = visibleItem.offset -
                (viewportHeight / 2 - visibleItem.size / 2)

        if (targetCenterOffset != 0) {
            var previousValue = 0f
            val animState = AnimationState(initialValue = 0f)

            listState.scroll {
                animState.animateTo(
                    targetValue = targetCenterOffset.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 85f
                    )
                ) {
                    val delta = this.value - previousValue
                    scrollBy(delta)
                    previousValue = this.value
                }
            }
        }
    } else {
        listState.animateScrollToItem(
            index = activeIndex,
            scrollOffset = -350
        )
    }
}

/**
 * Cubic easing out curve for smooth animations
 */
private fun easeOutCubic(t: Float): Float {
    val clampedT = t.coerceIn(0f, 1f)
    val f = clampedT - 1f
    return f * f * f + 1f
}