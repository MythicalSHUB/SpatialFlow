package com.codetrio.spatialflow.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricWord
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexNonRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val LYRIC_FOCUS_MIN_SCROLL_PX = 1
private const val LYRIC_FOCUS_ANIMATED_DISTANCE = 15
private const val LYRIC_FOCUS_SCROLL_DURATION_MS = 360
private const val MANUAL_SCROLL_RESUME_DELAY_MS = 1200L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val LYRIC_FOCUS_ANCHOR_RATIO = 0.38f
private const val LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO = 0.22f
private const val LYRIC_FOCUS_TOP_GUARD_RATIO = 0.18f
private const val LYRIC_FOCUS_BOTTOM_GUARD_RATIO = 0.24f
private const val ACTIVE_WORD_LIFT_DP = 1.2f

/**
 * Apple Music-styled High-precision Karaoke Canvas View.
 * Features 42% focus anchoring, spring scaling from left origin, depth-of-field blur,
 * sub-frame playback clock with drift correction, and character-level DstOut sweep masks.
 */
@Composable
fun KaraokeLyricsView(
    lyrics: List<LyricLine>,
    currentPositionProvider: () -> Int,
    isPlayingProvider: () -> Boolean,
    playbackSpeedProvider: () -> Float,
    onSeekTo: (Int) -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    footerContent: @Composable () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val view = LocalView.current

    // ── Sub-frame clock state ──
    var interpolatedPositionMs by remember { mutableFloatStateOf(currentPositionProvider().toFloat()) }
    val currentPositionMsProvider = remember { { interpolatedPositionMs.toLong() } }

    // ── User scroll interaction state ──
    var isUserInteracting by remember { mutableStateOf(false) }
    var userInteractionToken by remember { mutableIntStateOf(0) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(userInteractionToken) {
        if (userInteractionToken > 0) {
            delay(MANUAL_SCROLL_RESUME_DELAY_MS.milliseconds)
            isUserInteracting = false
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 1f) {
                    val now = System.currentTimeMillis()
                    if (now - lastManualScrollTime > MANUAL_SCROLL_DEBOUNCE_MS) {
                        isUserInteracting = true
                        userInteractionToken += 1
                        lastManualScrollTime = now
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isUserInteracting = true
                userInteractionToken += 1
                lastManualScrollTime = System.currentTimeMillis()
                return super.onPostFling(consumed, available)
            }
        }
    }

    // ── High-precision sub-frame clock tick with frame deltas ──
    LaunchedEffect(Unit) {
        var lastSystemNanos = System.nanoTime()

        while (isActive) {
            withFrameNanos { frameNanos ->
                val deltaNanos = frameNanos - lastSystemNanos
                lastSystemNanos = frameNanos

                val isPlaying = isPlayingProvider()
                val speed = playbackSpeedProvider()
                val actualMs = currentPositionProvider().toFloat()

                if (isPlaying) {
                    val deltaMs = (deltaNanos / 1_000_000f) * speed
                    val projected = interpolatedPositionMs + deltaMs
                    val drift = actualMs - projected

                    if (abs(drift) > 1000f) {
                        // User seeked or track changed — hard snap to actual position
                        interpolatedPositionMs = actualMs
                    } else {
                        // Smooth monotonic forward step with subtle 5% drift correction
                        interpolatedPositionMs = (projected + drift * 0.05f).coerceAtLeast(interpolatedPositionMs)
                    }
                } else {
                    // Paused: Lock clock immediately to actual media player position
                    interpolatedPositionMs = actualMs
                }
            }
        }
    }

    // ── Inject instrumental break interludes ──
    val displayLyrics = lyrics

    // ── Active line index computation ──
    val activeIndex by remember(displayLyrics) {
        derivedStateOf {
            val currentPos = interpolatedPositionMs.toInt()
            if (displayLyrics.isEmpty()) -1
            else {
                var index = -1
                for (i in displayLyrics.indices) {
                    val line = displayLyrics[i]
                    if (line.isInterlude) continue
                    if (line.startTimeMs <= currentPos) {
                        index = i
                    } else break
                }
                index
            }
        }
    }

    // ── Edge-faded container ──
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
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
        // ── Native Spring Focal Scroll Engine ──
        LaunchedEffect(activeIndex, isUserInteracting, displayLyrics) {
            if (isUserInteracting || displayLyrics.isEmpty() || activeIndex !in displayLyrics.indices) return@LaunchedEffect

            try {
                val targetIndexInList = activeIndex + 1 // +1 for head_spacer
                val visibleInfo = listState.layoutInfo
                val viewportHeight = visibleInfo.viewportSize.height
                val targetOffset = (viewportHeight * 0.28f).toInt()

                val itemInfo = visibleInfo.visibleItemsInfo.firstOrNull { it.index == targetIndexInList }
                if (itemInfo != null) {
                    val scrollDistance = (itemInfo.offset - targetOffset).toFloat()
                    if (abs(scrollDistance) > 5f) {
                        listState.animateScrollBy(
                            value = scrollDistance,
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = 150f,
                                visibilityThreshold = 0.01f
                            )
                        )
                    }
                } else {
                    // Off-screen active line or initial sheet open -> Autofocus jump to target line
                    listState.scrollToItem(
                        index = targetIndexInList,
                        scrollOffset = -targetOffset
                    )
                }
            } catch (_: Exception) {
                // Ignore scroll interruptions during fast seeks
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "head_spacer") {
                Spacer(modifier = Modifier.height(180.dp))
            }

            itemsIndexed(
                items = displayLyrics,
                key = { index, item -> "${index}_${item.startTimeMs}_${item.content.hashCode()}" }
            ) { index, line ->
                val isActive = index == activeIndex
                val distanceFromActive = if (activeIndex >= 0) {
                    abs(index - activeIndex)
                } else {
                    Int.MAX_VALUE
                }
                val lineEndMs = if (index + 1 < displayLyrics.size) {
                    displayLyrics[index + 1].startTimeMs
                } else {
                    line.startTimeMs + 5000
                }
                val isInterludeVisibleState = remember(line, lineEndMs) {
                    derivedStateOf {
                        line.isInterlude && currentPositionMsProvider() >= line.startTimeMs && currentPositionMsProvider() < lineEndMs
                    }
                }
                val isInterludeVisible = isInterludeVisibleState.value

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false }
                ) {
                    if (line.isInterlude) {
                        AnimatedVisibility(
                            visible = isInterludeVisible,
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(
                                animationSpec = tween(280)
                            ) + expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut(
                                animationSpec = tween(200)
                            ) + shrinkVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        ) {
                            InterludeItem(
                                isActive = isInterludeVisible,
                                currentPositionMsProvider = currentPositionMsProvider,
                                line = line,
                                nextLineStartMs = lineEndMs,
                                accentColor = accentColor,
                                contentColor = contentColor
                            )
                        }
                    } else {
                        KaraokeLineItem(
                            line = line,
                            isActive = isActive,
                            distanceFromActive = distanceFromActive,
                            currentPositionMsProvider = currentPositionMsProvider,
                            lineStartMs = line.startTimeMs,
                            lineEndMs = lineEndMs,
                            accentColor = accentColor,
                            contentColor = contentColor,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSeekTo(line.startTimeMs.toInt())
                            }
                        )
                    }
                }

                // ── Native Bounce Accordion Spacer ──
                BounceSpacer(index = index, activeIndex = activeIndex, listState = listState)
            }

            item(key = "lyrics_footer") {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    footerContent()
                }
            }

            item(key = "footer_spacer") {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }
    }
}


/**
 * Apple Music Focal Scroll Engine
 */
private suspend fun LazyListState.scrollLyricIntoFocus(
    index: Int,
    animateToNearbyItem: Boolean,
    force: Boolean,
    alignByItemCenter: Boolean,
) {
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount == 0) return

    val targetIndex = index.coerceIn(0, itemCount - 1)
    try {
        var itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
        if (itemInfo == null) {
            val distance = abs(targetIndex - firstVisibleItemIndex)
            if (animateToNearbyItem && distance <= LYRIC_FOCUS_ANIMATED_DISTANCE) {
                animateScrollToItem(targetIndex)
            } else {
                scrollToItem(targetIndex)
            }
            withFrameNanos { }
            itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
        }

        itemInfo ?: return

        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val viewportHeight = viewportEnd - viewportStart
        if (viewportHeight <= 0) return

        val itemFocusPoint = if (alignByItemCenter) {
            itemInfo.offset + itemInfo.size / 2
        } else {
            itemInfo.offset
        }

        val anchorRatio = if (alignByItemCenter) {
            LYRIC_FOCUS_ANCHOR_RATIO
        } else {
            LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO
        }
        val targetFocusPoint = viewportStart + (viewportHeight * anchorRatio).roundToInt()
        val scrollDelta = itemFocusPoint - targetFocusPoint
        if (abs(scrollDelta) > LYRIC_FOCUS_MIN_SCROLL_PX) {
            animateScrollBy(
                value = scrollDelta.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    } catch (_: Exception) {
        // Ignore interrupted lyric scrolls. Playback updates will request a fresh alignment.
    }
}

private data class VisualWord(
    val text: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val isTimed: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimatedWord(
    word: VisualWord,
    wordIndex: Int = 0,
    isActive: Boolean,
    currentPositionMsProvider: () -> Long,
    textColor: Color,
    dimColor: Color,
    textStyle: TextStyle,
) {
    if (!word.isTimed) {
        Text(
            text = word.text,
            style = textStyle,
            color = if (isActive) dimColor else dimColor.copy(alpha = 0.5f)
        )
        return
    }

    val wordStartMs = word.startTimeMs
    val wordEndMs = wordStartMs + word.durationMs
    val wordDuration = word.durationMs.coerceAtLeast(1L)
    val chainOffsetMs = (wordIndex * 40L).coerceAtMost(280L)
    val effectiveLiftStartMs = wordStartMs + chainOffsetMs

    val isWordActive by remember(wordStartMs, wordEndMs) {
        derivedStateOf {
            val pos = currentPositionMsProvider()
            pos in wordStartMs until wordEndMs
        }
    }
    val isWordComplete by remember(wordEndMs) {
        derivedStateOf {
            currentPositionMsProvider() >= wordEndMs
        }
    }

    val targetScale = if (isWordActive) 1.02f else 1.0f
    val wordScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 120f,
            visibilityThreshold = 0.001f
        ),
        label = "wordScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isWordActive) 0.35f else 0.0f,
        animationSpec = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        ),
        label = "glowAlpha"
    )

    val glowBlurRadius by animateFloatAsState(
        targetValue = if (isWordActive) 18f else 0.0f,
        animationSpec = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        ),
        label = "glowBlur"
    )

    val wordTextStyle = remember(textStyle, glowAlpha, glowBlurRadius) {
        if (glowAlpha > 0.01f) {
            textStyle.copy(
                shadow = Shadow(
                    color = textColor.copy(alpha = glowAlpha),
                    offset = Offset.Zero,
                    blurRadius = glowBlurRadius.coerceAtLeast(0.1f)
                )
            )
        } else {
            textStyle
        }
    }

    val glowPadding = 10.dp
    val density = LocalDensity.current
    val softEdgePx = with(density) { 24.dp.toPx() }
    val maxLiftPx = with(density) { ACTIVE_WORD_LIFT_DP.dp.toPx() }

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val glowPaddingPx = glowPadding.roundToPx()
                val looseConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity
                )
                val placeable = measurable.measure(looseConstraints)
                val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)
                layout(coreWidth, coreHeight) {
                    placeable.place(-glowPaddingPx, -glowPaddingPx)
                }
            }
            .graphicsLayer {
                clip = false
                scaleX = wordScale
                scaleY = wordScale

                val posMs = currentPositionMsProvider()
                val liftProgress = when {
                    !isActive -> 0f
                    posMs >= wordEndMs -> 1f
                    posMs <= effectiveLiftStartMs -> 0f
                    else -> ((posMs - effectiveLiftStartMs).toFloat() / (wordEndMs - effectiveLiftStartMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                }
                val easedLift = liftProgress * liftProgress * (3f - 2f * liftProgress)
                translationY = if (isActive) -maxLiftPx * easedLift else 0f
            }
    ) {
        // Layer 1: Dim base text
        Text(
            text = word.text,
            style = textStyle,
            color = dimColor,
            modifier = Modifier.padding(glowPadding)
        )

        // Layer 2: Lit/Glowing active text overlay with soft gradient sweep mask
        if (isWordComplete || isWordActive) {
            Text(
                text = word.text,
                style = wordTextStyle,
                color = textColor,
                modifier = Modifier
                    .padding(glowPadding)
                    .then(
                        if (isWordActive && !isWordComplete) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    if (size.width > 0f) {
                                        val posMs = currentPositionMsProvider()
                                        val rawProgress = when {
                                            posMs >= wordEndMs -> 1f
                                            posMs <= wordStartMs -> 0f
                                            else -> ((posMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
                                        }
                                        val sweepProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)
                                        val headX = (size.width + softEdgePx * 2f) * sweepProgress - softEdgePx

                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to Color.Black,
                                                    ((headX - softEdgePx).coerceAtLeast(0f) / size.width).coerceIn(0f, 1f) to Color.Black,
                                                    (headX.coerceAtLeast(0f) / size.width).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.5f),
                                                    ((headX + softEdgePx).coerceAtLeast(0f) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                                                    1.0f to Color.Transparent
                                                )
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun KaraokeLineItem(
    line: LyricLine,
    isActive: Boolean,
    distanceFromActive: Int,
    currentPositionMsProvider: () -> Long,
    lineStartMs: Long,
    lineEndMs: Long,
    accentColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val isKaraoke = line.isWordByWord && line.words.isNotEmpty()

    val alpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1.0f
            distanceFromActive == 1 -> 0.52f
            distanceFromActive == 2 -> 0.30f
            distanceFromActive == 3 -> 0.18f
            else -> 0.10f
        },
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessLow
        ),
        label = "KaraokeAlpha"
    )

    val blurRadius by animateDpAsState(
        targetValue = when {
            isActive -> 0.dp
            distanceFromActive == 1 -> 1.5.dp
            distanceFromActive == 2 -> 3.dp
            else -> 6.dp
        },
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessLow
        ),
        label = "KaraokeBlur"
    )

    // Use TTML-sourced background vocals if available, fall back to bracket regex for plain lyrics
    val bracketMatch = remember(line.content, line.backgroundContent, line.isBackground) {
        if (line.backgroundContent != null || line.isBackground) null
        else Regex("""^(.+?)\s*((\([^\)]+\)\s*|\[[^\]]+\]\s*|\{[^\}]+\}\s*)+)$""").find(line.content.trim())
    }

    val hasValidMainContent = remember(bracketMatch, line.isBackground) {
        if (line.isBackground) false
        else if (bracketMatch != null) {
            val mainText = bracketMatch.groupValues[1].trim()
            val bracketText = bracketMatch.groupValues[2].trim()
            mainText.isNotBlank() && bracketText.isNotBlank() && !mainText.startsWith("(") && !mainText.startsWith("[") && !mainText.startsWith("{")
        } else false
    }

    val mainContent = remember(line.content, line.backgroundContent, bracketMatch, hasValidMainContent, line.isBackground) {
        if (line.isBackground) {
            ""
        } else if (hasValidMainContent) {
            bracketMatch!!.groupValues[1].trimEnd() + " "
        } else {
            line.content + " "
        }
    }

    val bracketContent = remember(line.content, line.backgroundContent, bracketMatch, hasValidMainContent, line.isBackground) {
        if (line.isBackground) {
            line.content + " "
        } else if (line.backgroundContent != null) {
            line.backgroundContent + " "
        } else if (hasValidMainContent) {
            bracketMatch!!.groupValues[2].trim() + " "
        } else {
            null
        }
    }

    val bgWords = remember(line.words, line.backgroundWords, line.backgroundContent, line.isBackground) {
        if (line.backgroundWords.isNotEmpty()) {
            line.backgroundWords.map { it.copy(isBackground = true) }
        } else if (line.isBackground) {
            line.words.map { it.copy(isBackground = true) }
        } else {
            line.words.filter { it.isBackground }
        }
    }
    val hasBgKaraoke = bgWords.isNotEmpty()
    val hasBracketLayer = !bracketContent.isNullOrBlank()
    val bracketTimingWords = if (hasBgKaraoke) {
        bgWords
    } else if (hasBracketLayer && isKaraoke) {
        line.words.filter { it.charRange.first >= mainContent.length || it.isBackground }
    } else {
        emptyList()
    }
    val hasBracketSweep = hasBracketLayer && isActive && bracketTimingWords.isNotEmpty()

    val mainTimingWords = remember(line.words, mainContent, hasValidMainContent) {
        if (line.words.any { it.isBackground }) {
            line.words.filter { !it.isBackground }
        } else if (hasValidMainContent) {
            line.words.filter { it.charRange.last <= mainContent.length && !it.isBackground }
        } else {
            line.words
        }
    }

    val bracketWordOffset = remember(mainContent, line.backgroundContent, line.isBackground, hasValidMainContent) {
        if (line.isBackground || line.backgroundContent != null) {
            0
        } else if (hasValidMainContent) {
            mainContent.length
        } else {
            0
        }
    }

    val density = LocalDensity.current
    val maxWordLiftPx = with(density) { ACTIVE_WORD_LIFT_DP.dp.toPx() }

    val dimColor = contentColor.copy(alpha = 0.35f)
    val litColor = contentColor
    val bgLitColor = contentColor.copy(alpha = 0.75f)
    val bgDimColor = contentColor.copy(alpha = 0.245f)

    val mainFontWeight = when {
        isActive -> FontWeight.ExtraBold
        distanceFromActive == 1 -> FontWeight.Bold
        else -> FontWeight.Medium
    }

    val mainTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = GoogleSansFlexNonRounded,
        fontSize = 34.sp,
        fontWeight = mainFontWeight,
        lineHeight = 44.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )

    val visualWords = remember(mainTimingWords, mainContent, lineStartMs, lineEndMs) {
        val validTimingWords = mainTimingWords.filter { it.absoluteStartTimeMs >= 0L && it.durationMs > 0L }
        if (validTimingWords.isNotEmpty()) {
            val list = mutableListOf<VisualWord>()
            var lastIdx = 0
            validTimingWords.forEach { word ->
                val wStart = word.charRange.first.coerceIn(0, mainContent.length)
                val wEnd = (word.charRange.last + 1).coerceIn(0, mainContent.length)
                if (wStart > lastIdx) {
                    val gapText = mainContent.substring(lastIdx, wStart)
                    if (gapText.isNotEmpty()) {
                        list.add(VisualWord(text = gapText, startTimeMs = 0L, durationMs = 0L, isTimed = false))
                    }
                }
                val wordText = if (wEnd > wStart) mainContent.substring(wStart, wEnd) else word.text
                list.add(VisualWord(text = wordText, startTimeMs = word.absoluteStartTimeMs, durationMs = word.durationMs, isTimed = true))
                lastIdx = wEnd
            }
            if (lastIdx < mainContent.length) {
                val trailingText = mainContent.substring(lastIdx)
                if (trailingText.isNotEmpty()) {
                    list.add(VisualWord(text = trailingText, startTimeMs = 0L, durationMs = 0L, isTimed = false))
                }
            }
            list
        } else {
            val rawTokens = mainContent.split(" ")
            val nonSpaceTokens = rawTokens.filter { it.isNotEmpty() }
            if (nonSpaceTokens.isEmpty()) {
                listOf(VisualWord(text = mainContent, startTimeMs = lineStartMs, durationMs = (lineEndMs - lineStartMs).coerceAtLeast(500L), isTimed = true))
            } else {
                val lineDuration = (lineEndMs - lineStartMs).coerceAtLeast(600L)
                val wordDur = (lineDuration / nonSpaceTokens.size).coerceAtLeast(150L)
                val list = mutableListOf<VisualWord>()
                var currentStart = lineStartMs
                rawTokens.forEachIndexed { idx, token ->
                    if (token.isNotEmpty()) {
                        list.add(VisualWord(text = token, startTimeMs = currentStart, durationMs = wordDur, isTimed = true))
                        currentStart += wordDur
                        if (idx < rawTokens.lastIndex) {
                            list.add(VisualWord(text = " ", startTimeMs = 0L, durationMs = 0L, isTimed = false))
                        }
                    }
                }
                list
            }
        }
    }

    val bracketVisualWords = remember(bracketTimingWords, bracketContent, bracketWordOffset, lineStartMs, lineEndMs) {
        val content = bracketContent ?: return@remember emptyList<VisualWord>()
        val validTimingWords = bracketTimingWords.filter { it.absoluteStartTimeMs >= 0L && it.durationMs > 0L }

        if (validTimingWords.isNotEmpty()) {
            val list = mutableListOf<VisualWord>()
            var lastIdx = 0
            validTimingWords.forEach { word ->
                val wStart = (word.charRange.first - bracketWordOffset).coerceIn(0, content.length)
                val wEnd = (word.charRange.last - bracketWordOffset + 1).coerceIn(0, content.length)
                if (wStart > lastIdx) {
                    val gapText = content.substring(lastIdx, wStart)
                    if (gapText.isNotEmpty()) {
                        list.add(VisualWord(text = gapText, startTimeMs = 0L, durationMs = 0L, isTimed = false))
                    }
                }
                val wordText = if (wEnd > wStart) content.substring(wStart, wEnd) else word.text
                list.add(VisualWord(text = wordText, startTimeMs = word.absoluteStartTimeMs, durationMs = word.durationMs, isTimed = true))
                lastIdx = wEnd
            }
            if (lastIdx < content.length) {
                val trailingText = content.substring(lastIdx)
                if (trailingText.isNotEmpty()) {
                    list.add(VisualWord(text = trailingText, startTimeMs = 0L, durationMs = 0L, isTimed = false))
                }
            }
            list
        } else {
            val rawTokens = content.split(" ")
            val nonSpaceTokens = rawTokens.filter { it.isNotEmpty() }
            val bgLeadInDelayMs = 350L
            if (nonSpaceTokens.isEmpty()) {
                listOf(VisualWord(text = content, startTimeMs = lineStartMs + bgLeadInDelayMs, durationMs = (lineEndMs - lineStartMs - bgLeadInDelayMs).coerceAtLeast(400L), isTimed = true))
            } else {
                val lineDuration = (lineEndMs - lineStartMs).coerceAtLeast(600L)
                val bgActiveDuration = (lineDuration - bgLeadInDelayMs).coerceAtLeast(300L)
                val wordDur = (bgActiveDuration / nonSpaceTokens.size).coerceAtLeast(150L)
                val list = mutableListOf<VisualWord>()
                var currentStart = lineStartMs + bgLeadInDelayMs
                rawTokens.forEachIndexed { idx, token ->
                    if (token.isNotEmpty()) {
                        list.add(VisualWord(text = token, startTimeMs = currentStart, durationMs = wordDur, isTimed = true))
                        currentStart += wordDur
                        if (idx < rawTokens.lastIndex) {
                            list.add(VisualWord(text = " ", startTimeMs = 0L, durationMs = 0L, isTimed = false))
                        }
                    }
                }
                list
            }
        }
    }

    val bracketTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = GoogleSansFlexNonRounded,
        fontSize = 22.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        lineHeight = 30.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )

    val isRtl = remember(line.content) { isRtlText(line.content) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
            .graphicsLayer {
                this.alpha = alpha
                clip = false
            }
            .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    ) {
        // ── Main Text Box ──
        if (isKaraoke) {
            FlowRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                visualWords.forEachIndexed { wordIndex, word ->
                    AnimatedWord(
                        word = word,
                        wordIndex = wordIndex,
                        isActive = isActive,
                        currentPositionMsProvider = currentPositionMsProvider,
                        textColor = litColor,
                        dimColor = dimColor,
                        textStyle = mainTextStyle,
                    )
                }
            }
        } else {
            Text(
                text = mainContent,
                style = mainTextStyle,
                color = if (isActive) litColor else dimColor,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Bracket Text Box (Background Vocals - Entry & Exit Animation) ──
        if (bracketContent != null) {
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                        expandVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                        slideInVertically(initialOffsetY = { -it / 3 }, animationSpec = tween(320, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                       shrinkVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                       slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = tween(240, easing = FastOutSlowInEasing))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (hasBgKaraoke || (hasBracketLayer && isKaraoke)) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.85f)
                        ) {
                            bracketVisualWords.forEachIndexed { wordIndex, word ->
                                AnimatedWord(
                                    word = word,
                                    wordIndex = wordIndex + visualWords.size,
                                    isActive = isActive,
                                    currentPositionMsProvider = currentPositionMsProvider,
                                    textColor = bgLitColor,
                                    dimColor = bgDimColor,
                                    textStyle = bracketTextStyle,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = bracketContent,
                            style = bracketTextStyle,
                            color = bgLitColor,
                            softWrap = true,
                            maxLines = Int.MAX_VALUE,
                            modifier = Modifier.alpha(0.85f)
                        )
                    }
                }
            }
        }
    }
}

private fun calculateLineFillProgress(
    words: List<LyricWord>,
    posMs: Long
): Float {
    if (words.isEmpty()) return 0f

    val firstWordStartMs = words.first().absoluteStartTimeMs
    val lastWordEndMs = words.last().absoluteStartTimeMs + words.last().durationMs
    val lineDuration = (lastWordEndMs - firstWordStartMs).coerceAtLeast(1L)
    val timeElapsed = (posMs - firstWordStartMs).coerceIn(0L, lineDuration)
    return (timeElapsed.toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
}

private fun spatialFlowSweepBrush(
    text: String,
    fillProgress: Float,
    contentColor: Color,
    tailAlpha: Float
): Brush {
    val clampedProgress = fillProgress.coerceIn(0f, 1f)
    val isRtl = isRtlText(text)
    val glowColor = Color.White

    return if (isRtl) {
        val activePos = 1f - clampedProgress
        Brush.horizontalGradient(
            0.0f to contentColor.copy(alpha = tailAlpha),
            (activePos - 0.015f).coerceIn(0f, 1f) to contentColor.copy(alpha = tailAlpha),
            activePos.coerceIn(0f, 1f) to glowColor,
            (activePos + 0.15f).coerceIn(0f, 1f) to contentColor,
            1.0f to contentColor
        )
    } else {
        Brush.horizontalGradient(
            0.0f to contentColor,
            (clampedProgress - 0.15f).coerceIn(0f, 1f) to contentColor,
            clampedProgress.coerceIn(0f, 1f) to glowColor,
            (clampedProgress + 0.015f).coerceIn(0f, 1f) to contentColor.copy(alpha = tailAlpha),
            1.0f to contentColor.copy(alpha = tailAlpha)
        )
    }
}

private fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                -> return false
        }
    }
    return false
}

private fun rtlAwareHorizontalGradient(
    isRtl: Boolean,
    vararg colorStops: Pair<Float, Color>
): Brush {
    val stops = if (isRtl) {
        colorStops
            .map { (fraction, color) -> (1f - fraction).coerceIn(0f, 1f) to color }
            .sortedBy { it.first }
    } else {
        colorStops.toList()
    }
    return Brush.horizontalGradient(*stops.toTypedArray())
}

@Composable
private fun InterludeItem(
    isActive: Boolean,
    currentPositionMsProvider: () -> Long,
    line: LyricLine,
    nextLineStartMs: Long,
    accentColor: Color,
    contentColor: Color
) {
    val duration = (nextLineStartMs - line.startTimeMs).coerceAtLeast(1)
    val rawProgress = if (isActive) {
        ((currentPositionMsProvider() - line.startTimeMs).toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "InterludeProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "InterludeBreathing")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathScale"
    )

    val density = LocalDensity.current
    val interludeStroke = remember(density) {
        Stroke(
            width = with(density) { 3.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = "Interlude",
            tint = accentColor.copy(alpha = 0.95f),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                }
        )

        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = accentColor.copy(alpha = 0.9f),
            trackColor = contentColor.copy(alpha = 0.12f),
            stroke = interludeStroke,
            trackStroke = interludeStroke,
            wavelength = 22.dp,
            amplitude = { 0.8f }
        )
    }
}

@Composable
private fun BounceSpacer(
    index: Int,
    activeIndex: Int,
    listState: LazyListState
) {
    val targetHeightState = remember(index, activeIndex, listState) {
        derivedStateOf {
            val targetIndexInList = activeIndex + 1
            val visibleInfo = listState.layoutInfo
            val viewportHeight = visibleInfo.viewportSize.height
            val anchor = viewportHeight * 0.28f
            val activeItem = visibleInfo.visibleItemsInfo.firstOrNull { it.index == targetIndexInList }
            val scrollDistance = (activeItem?.offset?.toFloat() ?: anchor) - anchor
            val visibleCount = visibleInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val firstVisible = listState.firstVisibleItemIndex
            val itemIndexInList = index + 1
            val inWindow = itemIndexInList >= targetIndexInList - 1
            val weight = if (inWindow) {
                (1f - ((itemIndexInList - firstVisible).toFloat() / visibleCount.toFloat())).coerceIn(0f, 1f)
            } else {
                0f
            }
            val thisScroll = (scrollDistance / visibleCount).coerceIn(-400f, 400f)
            if (inWindow) (thisScroll * weight).coerceAtLeast(0f) else 0f
        }
    }

    val spacerHeight by animateDpAsState(
        targetValue = targetHeightState.value.dp,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 180f,
            visibilityThreshold = 0.01.dp
        ),
        label = "v2BounceSpacer"
    )
    Spacer(modifier = Modifier.height(spacerHeight))
}

private fun calculateWordGlowAlpha(
    words: List<LyricWord>,
    posMs: Long
): Float {
    if (words.isEmpty()) return 0f
    val activeWord = words.firstOrNull { word ->
        posMs in word.absoluteStartTimeMs until (word.absoluteStartTimeMs + word.durationMs)
    } ?: return 0f
    val wordDuration = activeWord.durationMs.coerceAtLeast(80L).toFloat()
    val rawProgress = ((posMs - activeWord.absoluteStartTimeMs).toFloat() / wordDuration).coerceIn(0f, 1f)
    return (rawProgress * 2f).coerceAtMost(1f) * 0.45f
}

private fun drawBackgroundDimEffects(
    layout: TextLayoutResult,
    words: List<LyricWord>,
    wordBounds: List<Pair<Float, Float>>,
    posMs: Long,
    wordOffset: Int,
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    drawContent: () -> Unit
) {
    if (words.isEmpty() || layout.layoutInput.text.isEmpty()) {
        drawContent()
        return
    }

    val textLength = layout.layoutInput.text.length
    val width = drawScope.size.width
    val height = drawScope.size.height
    if (width <= 0f || height <= 0f) {
        drawContent()
        return
    }

    val lineWords = if (wordOffset > 0) {
        words.filter { it.charRange.first >= wordOffset }
    } else {
        words
    }

    if (lineWords.isEmpty()) {
        drawContent()
        return
    }

    val firstWord = lineWords.first()
    val lastWord = lineWords.last()

    val lineStartMs = firstWord.absoluteStartTimeMs
    val lineEndMs = lastWord.absoluteStartTimeMs + lastWord.durationMs

    if (posMs < lineStartMs || posMs >= lineEndMs) {
        drawContent()
        return
    }

    val activeWord = lineWords.find { posMs >= it.absoluteStartTimeMs && posMs < (it.absoluteStartTimeMs + it.durationMs) }
    if (activeWord == null) {
        drawContent()
        return
    }

    val activeWordStartChar = (activeWord.charRange.first - wordOffset).coerceIn(0, textLength - 1)
    val activeLineIndex = layout.getLineForOffset(activeWordStartChar)

    for (lineIdx in 0 until layout.lineCount) {
        val lineTop = layout.getLineTop(lineIdx)
        val lineBottom = layout.getLineBottom(lineIdx)

        if (lineIdx == activeLineIndex) {
            val activeWordIdx = lineWords.indexOf(activeWord)
            val bounds = wordBounds.getOrNull(activeWordIdx)
            val wLeft = bounds?.first ?: layout.getLineLeft(lineIdx)
            val wRight = bounds?.second ?: layout.getLineLeft(lineIdx)

            // Clip out the active word bounds using ClipOp.Difference
            drawScope.clipRect(
                left = wLeft,
                top = lineTop - 10f,
                right = wRight,
                bottom = lineBottom + 10f,
                clipOp = androidx.compose.ui.graphics.ClipOp.Difference
            ) {
                drawContent()
            }
        } else {
            drawScope.clipRect(
                left = 0f,
                top = lineTop - 10f,
                right = width,
                bottom = lineBottom + 10f
            ) {
                drawContent()
            }
        }
    }
}


