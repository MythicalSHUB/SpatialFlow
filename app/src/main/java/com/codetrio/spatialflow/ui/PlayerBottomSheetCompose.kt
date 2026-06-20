package com.codetrio.spatialflow.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.ArtworkPager
import com.codetrio.spatialflow.ui.player.FullPlayerScreen
import com.codetrio.spatialflow.ui.player.PlayerUiState
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// ==========================================
// 1. STEP 1: CORE STATE MACHINE & INTERFACES
// ==========================================
enum class PlayerSheetState {
    COLLAPSED,
    EXPANDED
}

private enum class MiniDismissDragPhase {
    IDLE,
    TENSION,
    SNAPPING,
    FREE_DRAG
}

// ==========================================
// 2. STEP 2: DYNAMIC SHAPE & SQUIRCLE MORPHING
// ==========================================
class PlayerSheetDynamicShape(
    private val topRadiusProvider: () -> Dp,
    private val bottomRadiusProvider: () -> Dp,
    private val virtualHeightProvider: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val topRadius = topRadiusProvider().nonNegative()
        val bottomRadius = bottomRadiusProvider().nonNegative()

        // Fall back to standard high-performance rounded corner shape
        val topRadiusPx = with(density) { topRadius.toPx() }
        val bottomRadiusPx = with(density) { bottomRadius.toPx() }
        val virtualHeight = virtualHeightProvider()
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, virtualHeight),
                topLeft = CornerRadius(topRadiusPx, topRadiusPx),
                topRight = CornerRadius(topRadiusPx, topRadiusPx),
                bottomRight = CornerRadius(bottomRadiusPx, bottomRadiusPx),
                bottomLeft = CornerRadius(bottomRadiusPx, bottomRadiusPx)
            )
        )
    }
}

private fun Dp.nonNegative(): Dp = takeIf { it.value.isFinite() && it.value > 0f } ?: 0.dp

// ==========================================
// 3. STEP 3: DRAW-PHASE & LAYOUT-PHASE PROVIDERS
// ==========================================
val MiniPlayerHeight = 80.dp // High-fidelity capsule height matching controls breathing room

data class SheetVisualState(
    val currentBottomPadding: Dp,
    val playerContentAreaHeightPxProvider: () -> Float,
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetYProvider: () -> Float,
    isNavBarHiddenProvider: () -> Boolean,
    navBarCornerRadiusDp: Dp
): SheetVisualState {
    
    val currentBottomPadding by remember(showPlayerContentArea, collapsedStateHorizontalPadding, predictiveBackCollapseProgress) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea) {
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else {
                0.dp
            }
        }
    }

    val density = LocalDensity.current
    val miniHeightPx = remember(density) { with(density) { MiniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    
    // Dynamic height provider (Layout-phase)
    val playerContentAreaHeightPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction) {
        {
            if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, playerContentExpansionFraction.value)
            } else {
                0f
            }
        }
    }

    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    val visualSheetTranslationYProvider: () -> Float = remember(currentSheetTranslationY) {
        {
            val progress = predictiveBackCollapseProgressState.value
            val collapsedY = sheetCollapsedTargetYProvider()
            currentSheetTranslationY.value * (1f - progress) + (collapsedY * progress)
        }
    }

    // Dynamic top corners logic (lerps from pill corner to 0.dp)
    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(showPlayerContentArea, playerContentExpansionFraction, navBarCornerRadiusDp, isNavBarHiddenProvider) {
        {
            if (showPlayerContentArea) {
                val collapsedCornerTarget = 40.dp // Perfect capsule: half of 80.dp height
                val fraction = playerContentExpansionFraction.value
                lerp(collapsedCornerTarget, 0.dp, fraction)
            } else {
                40.dp
            }
        }
    }

    // Dynamic bottom corners logic (morphs down to 0.dp)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(showPlayerContentArea, playerContentExpansionFraction, isNavBarHiddenProvider, navBarCornerRadiusDp) {
        {
            val fraction = playerContentExpansionFraction.value
            val collapsedRadius = 40.dp // Perfect capsule: half of 80.dp height
            
            // Morphs outward to 26.dp in first 20% drag to form curved floating card, then goes flat
            if (fraction < 0.2f) {
                lerp(collapsedRadius, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
            } else {
                lerp(26.dp, 0.dp, ((fraction - 0.2f) / 0.8f).coerceIn(0f, 1f))
            }
        }
    }

    val collapsedStateHorizontalPaddingPx = remember(collapsedStateHorizontalPadding, density) {
        with(density) { collapsedStateHorizontalPadding.toPx() }
    }

    // Draw-phase padding providers
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
        }
    }
    
    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
        }
    }

    return remember(
        currentBottomPadding,
        playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider
    ) {
        SheetVisualState(
            currentBottomPadding = currentBottomPadding,
            playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
            visualSheetTranslationYProvider = visualSheetTranslationYProvider,
            overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
            playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
            currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
            currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
        )
    }
}

// ==========================================
// 4. STEP 4: STACKING & CROSS-FADING
// ==========================================
@Composable
fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    baseAccentColor: Color,
    context: Context,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    dragModifier: Modifier
) {
    val currentSong = uiState.currentSong ?: return
    val isPlaying = uiState.isPlaying

    val miniPlayerZIndex by remember {
        derivedStateOf { if (playerContentExpansionFraction.value < 0.5f) 1f else 0f }
    }
    val fullPlayerZIndex by remember {
        derivedStateOf { if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f }
    }

    // Mini Player Container
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .graphicsLayer {
                // Cross-fades fully to 0.0 alpha at 50% expansion
                alpha = (1f - playerContentExpansionFraction.value * 2f).coerceIn(0f, 1f)
            }
            .zIndex(miniPlayerZIndex)
    ) {
        MiniPlayerContentInternal(
            viewModel = viewModel,
            currentSong = currentSong,
            isPlaying = isPlaying,
            isProcessing = uiState.isProcessing,
            accentColor = baseAccentColor,
            onClick = onExpand,
            modifier = Modifier.fillMaxSize()
        )
    }

    // Full Player Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(containerHeight)
            .graphicsLayer {
                val fraction = playerContentExpansionFraction.value
                // Fades in smoothly past 50% expansion
                alpha = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f)
                val scale = androidx.compose.ui.util.lerp(0.972f, 1f, fraction)
                scaleX = scale
                scaleY = scale
                
                // Shift offscreen when collapsed to protect touch targets without causing layout invalidation
                translationY = if (fraction <= 0.01f) {
                    10000f
                } else {
                    androidx.compose.ui.util.lerp(100f, 0f, fraction)
                }
            }
            .zIndex(fullPlayerZIndex)
    ) {
        FullPlayerScreen(
            activity = activity,
            viewModel = viewModel,
            uiState = uiState,
            songList = songList,
            accentColor = baseAccentColor,
            context = context,
            onCollapse = onCollapse,
            dragModifier = dragModifier,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ==========================================
// 5. STEP 5: VERTICAL GESTURE HANDLER & MATH
// ==========================================
data class SheetVerticalDragFrame(
    val translationY: Float,
    val expansionFraction: Float
)

fun computeSheetVerticalDragFrame(
    accumulatedDragY: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float
): SheetVerticalDragFrame {
    // Clamped strictly to expandedY to prevent vertical rubber-band/upward drift when fully expanded
    val newY = (initialYOnDragStart + accumulatedDragY)
        .coerceIn(
            expandedY,
            collapsedY + miniHeightPx * 0.2f
        )
    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)
    val dragRatio = (initialYOnDragStart - newY) / denominator
    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
    return SheetVerticalDragFrame(translationY = newY, expansionFraction = newFraction)
}

fun resolveVerticalSheetTargetState(
    currentSheetContentState: PlayerSheetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float
): PlayerSheetState {
    return when {
        currentSheetContentState == PlayerSheetState.EXPANDED && accumulatedDragY <= 0f -> 
            PlayerSheetState.EXPANDED

        abs(accumulatedDragY) > minDragThresholdPx ->
            if (accumulatedDragY < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        abs(verticalVelocity) > velocityThreshold ->
            if (verticalVelocity < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        else ->
            if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }
}

fun collapseSpringDampingForFraction(currentFraction: Float): Float {
    return androidx.compose.ui.util.lerp(Spring.DampingRatioNoBouncy, Spring.DampingRatioLowBouncy, currentFraction)
}

fun collapseInitialSquashForFraction(currentFraction: Float): Float {
    return androidx.compose.ui.util.lerp(1.0f, 0.97f, currentFraction)
}

class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    private val onAnimateSheet: suspend (targetExpanded: Boolean, animationSpec: AnimationSpec<Float>?, initialVelocity: Float) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit,
    private val onDragStateChange: (Boolean) -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var dragJob: Job? = null
    private var isActivelyDragging = false

    fun onDragStart() {
        isActivelyDragging = true
        onDragStateChange(true)
        dragJob?.cancel()
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        initialYOnDragStart = currentSheetTranslationY.value
        accumulatedDragYSinceStart = 0f
    }

    fun onVerticalDrag(uptimeMillis: Long, position: Offset, dragAmount: Float) {
        if (currentSheetStateProvider() == PlayerSheetState.EXPANDED && dragAmount < 0) {
            // Dragging UP when fully expanded: Ignore vertical sheet movement completely
            return
        }
        accumulatedDragYSinceStart += dragAmount
        val dragFrame = computeSheetVerticalDragFrame(
            accumulatedDragY = accumulatedDragYSinceStart,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart
        )
        dragJob?.cancel()
        dragJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            currentSheetTranslationY.snapTo(dragFrame.translationY)
            playerContentExpansionFraction.snapTo(dragFrame.expansionFraction)
        }
        velocityTracker.addPosition(uptimeMillis, position)
    }

    fun onDragEnd() {
        if (!isActivelyDragging) return
        isActivelyDragging = false
        onDragStateChange(false)
        dragJob?.cancel()
        val verticalVelocity = velocityTracker.calculateVelocity().y
        val currentFraction = playerContentExpansionFraction.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }
        
        val targetState = resolveVerticalSheetTargetState(
            currentSheetContentState = currentSheetStateProvider(),
            accumulatedDragY = accumulatedDragYSinceStart,
            minDragThresholdPx = minDragThresholdPx,
            verticalVelocity = verticalVelocity,
            velocityThreshold = 150f,
            currentFraction = currentFraction
        )

        scope.launch {
            if (targetState == PlayerSheetState.EXPANDED) {
                launch { onAnimateSheet(true, null, 0f) }
                onExpandSheetState()
            } else {
                val dynamicDamping = collapseSpringDampingForFraction(currentFraction)
                
                // Gelatinous Squash & Stretch Bounce Trigger
                launch {
                    val initialSquash = collapseInitialSquashForFraction(currentFraction)
                    visualOvershootScaleY.snapTo(initialSquash)
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessVeryLow
                        )
                    )
                }
                
                launch {
                    onAnimateSheet(
                        false,
                        spring(dampingRatio = dynamicDamping, stiffness = Spring.StiffnessLow),
                        verticalVelocity
                    )
                }
                onCollapseSheetState()
            }
        }
    }
}

fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(change.uptimeMillis, change.position, dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragEnd() }
        )
    }
}

// ==========================================
// 6. STEP 6: HORIZONTAL SWIPE-TO-DISMISS TENSION
// ==========================================
class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticFeedback: HapticFeedback,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onDismissQueue: () -> Unit
) {
    private var dragPhase = MiniDismissDragPhase.IDLE
    private var accumulatedDragX = 0f
    private var offsetJob: Job? = null

    fun onDragStart() {
        dragPhase = MiniDismissDragPhase.TENSION
        accumulatedDragX = 0f
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.stop()
        }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                
                // Pulls back with structural resistance under the 100dp threshold
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val maxTensionOffsetPx = 30f * density.density
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = androidx.compose.ui.util.lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.SNAPPING
                }
            }

            MiniDismissDragPhase.SNAPPING -> {
                // Triggers tactile haptic pulse upon snapping
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    )
                }
                dragPhase = MiniDismissDragPhase.FREE_DRAG
            }

            MiniDismissDragPhase.FREE_DRAG -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                    )
                }
            }
            else -> Unit
        }
    }

    fun onDragEnd() {
        dragPhase = MiniDismissDragPhase.IDLE
        offsetJob?.cancel()
        val dismissThreshold = screenWidthPx * 0.4f
        
        if (abs(accumulatedDragX) > dismissThreshold) {
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = targetDismissOffset,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
                onDismissQueue()
                offsetAnimatable.snapTo(0f)
            }
        } else {
            // Springs back dynamically to center
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }
}

@Composable
fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticFeedback: HapticFeedback,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissQueue: () -> Unit
): MiniPlayerDismissGestureHandler {
    val onDismissQueueState = rememberUpdatedState(onDismissQueue)
    return remember(scope, density, hapticFeedback, offsetAnimatable, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = { onDismissQueueState.value() }
        )
    }
}

fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() }
        )
    }
}

// ==========================================
// 7. STEP 7: THE MASTER ASSEMBLY CONTAINER
// ==========================================
@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerBottomSheetCompose(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val hapticFeedback = LocalHapticFeedback.current
        
        // Playback state observers combined into stable UI model
        val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
        val duration by viewModel.duration.collectAsStateWithLifecycle()
        val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
        val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
        val isHapticsEnabled by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
        val miniPlayerBlendColor by viewModel.miniPlayerBlendColor.collectAsStateWithLifecycle()
        val isCurrentSongFavorite by viewModel.isCurrentSongFavoriteFlow.collectAsStateWithLifecycle()
        val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
        val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
        val playerBackgroundColor by viewModel.playerBackgroundColor.collectAsStateWithLifecycle()
        val likesCount by viewModel.likesCountFlow.collectAsStateWithLifecycle()
        val isDark = isSystemInDarkTheme()
        val dynamicBgColor = remember(playerBackgroundColor, isDark) {
            val baseColor = Color(playerBackgroundColor)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
            val isMonochrome = hsl[1] < 0.06f
            if (isDark) {
                // Dark Theme: Set a premium, richly-colored dark solid background (Exactly 15.5% lightness for gorgeous color presence)
                hsl[2] = 0.155f
                hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.32f, 0.54f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            } else {
                // Light Theme: Set a premium, elegant soft pastel solid background (Exactly 83.5% lightness for deep pastel flavor)
                hsl[2] = 0.835f
                hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.30f, 0.48f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
        val isCurrentSongDisliked by viewModel.isCurrentSongDisliked.collectAsStateWithLifecycle()
        val isCurrentSongDownloaded by viewModel.isCurrentSongDownloaded.collectAsStateWithLifecycle()
        val currentSongDownloadProgress by viewModel.currentSongDownloadProgress.collectAsStateWithLifecycle()
        val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
        val showSignInDialog by viewModel.showSignInDialog.collectAsStateWithLifecycle()
        if (showSignInDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSignInDialog() },
                title = {
                    Text(
                        text = "Sign in Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "To like or dislike songs on YouTube Music, please connect your account in Settings. This enables full synchronization of liked tracks and personalization algorithms.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissSignInDialog()
                            activity.navigateToSettings()
                        }
                    ) {
                        Text(text = "Go to Settings", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSignInDialog() }) {
                        Text(text = "Cancel")
                    }
                }
            )
        }
        val lyricsArtworkProgress by animateFloatAsState(
            targetValue = if (isLyricsModeEnabled) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            label = "LyricsArtworkSharedElement"
        )
 
        val uiState = remember(
            currentSong, isPlaying, duration, isProcessing, currentSongIndex,
            isHapticsEnabled, miniPlayerBlendColor, isCurrentSongFavorite, isCurrentSongDisliked, repeatMode, isShuffleEnabled,
            playerBackgroundColor, likesCount, isCurrentSongDownloaded, currentSongDownloadProgress
        ) {
            PlayerUiState(
                currentSong = currentSong,
                isPlaying = isPlaying,
                duration = duration,
                isProcessing = isProcessing,
                currentSongIndex = currentSongIndex,
                isHapticsEnabled = isHapticsEnabled,
                miniPlayerBlendColor = miniPlayerBlendColor,
                isCurrentSongFavorite = isCurrentSongFavorite,
                isCurrentSongDisliked = isCurrentSongDisliked,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                playerBackgroundColor = playerBackgroundColor,
                likesCount = likesCount,
                isCurrentSongDownloaded = isCurrentSongDownloaded,
                currentSongDownloadProgress = currentSongDownloadProgress
            )
        }

        // List is separate from quick state changes
        val songList by viewModel.songList.collectAsStateWithLifecycle()
        val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
        val artworkAlpha by animateFloatAsState(
            targetValue = if (isQueueExpanded) 0f else 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
            label = "ArtworkAlpha"
        )

        // Sliding Layout States
        val containerHeight = with(density) { maxHeight.toPx().toDp() }
        val screenHeightPx = with(density) { containerHeight.toPx() }
        val bottomNavTranslationYState = viewModel.bottomNavTranslationY.collectAsStateWithLifecycle()
        val dynamicBottomNavHeightState = viewModel.bottomNavHeight.collectAsStateWithLifecycle()
        
        // Zero-Recomposition logic: calculating layout targets inside derivedStateOf
        // This ensures the composable body doesn't re-run for every pixel of nav bar scroll
        val sheetCollapsedTargetYState = remember(screenHeightPx, density) {
            derivedStateOf {
                val bottomNavHeightPx = if (dynamicBottomNavHeightState.value > 0f) {
                    dynamicBottomNavHeightState.value
                } else {
                    with(density) { 75.dp.toPx() }
                }
                val miniPlayerHeightPx = with(density) { MiniPlayerHeight.toPx() }
                
                // Dynamically adjust gap: 12dp when BottomNav is visible, 8dp when hidden
                val bottomNavVisibilityFraction = (bottomNavTranslationYState.value / (if (bottomNavHeightPx > 0) bottomNavHeightPx else 1f)).coerceIn(0f, 1f)
                val currentBottomGapPx = with(density) { 
                    lerp(12.dp, 8.dp, bottomNavVisibilityFraction).toPx() 
                }
                
                // Ensure the mini player stops at the bottom margin and doesn't follow the nav bar into the abyss
                val effectiveBottomNavHeight = (bottomNavHeightPx - bottomNavTranslationYState.value).coerceAtLeast(0f)
                screenHeightPx - miniPlayerHeightPx - effectiveBottomNavHeight - currentBottomGapPx
            }
        }

        // Animation States
        val playerContentExpansionFraction = remember { Animatable(0f) }
        val isPlaybackReady by viewModel.isPlaybackReady.collectAsStateWithLifecycle()

        var currentSheetContentState by remember { mutableStateOf(PlayerSheetState.COLLAPSED) }
        
        val currentSheetTranslationY = remember { Animatable(screenHeightPx) }
        val visualOvershootScaleY = remember { Animatable(1f) }
        val offsetAnimatable = remember { Animatable(0f) }

        val screenWidthPx = with(density) { maxWidth.toPx() }
        
        var isDragging by remember { mutableStateOf(false) }

        val showPlayerContentArea = currentSong != null

        LaunchedEffect(currentSheetContentState) {
            viewModel.setPlayerExpanded(currentSheetContentState == PlayerSheetState.EXPANDED)
        }

        // Synchronize dynamic scrolling Bottom Nav visibility changes
        LaunchedEffect(Unit) {
            snapshotFlow { sheetCollapsedTargetYState.value }.collect { targetY ->
                if (currentSheetContentState == PlayerSheetState.COLLAPSED && 
                    !isDragging && 
                    !currentSheetTranslationY.isRunning && 
                    currentSheetTranslationY.value != screenHeightPx
                ) {
                    currentSheetTranslationY.snapTo(targetY)
                }
            }
        }

        val sheetBackProgress = 0f

        androidx.activity.compose.BackHandler(enabled = currentSheetContentState == PlayerSheetState.EXPANDED && !isLyricsModeEnabled) {
            currentSheetContentState = PlayerSheetState.COLLAPSED
        }

        // Auto-expand/collapse sheet when currentSong is changed / ready
        LaunchedEffect(currentSong, isPlaybackReady) {
            if (currentSong != null && isPlaybackReady) {
                if (currentSheetTranslationY.value == screenHeightPx) {
                    currentSheetTranslationY.animateTo(
                        targetValue = sheetCollapsedTargetYState.value,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    )
                    currentSheetContentState = PlayerSheetState.COLLAPSED
                }
            }
        }

        // Dynamic Blend Colors from Artwork
        val baseAccentColor = remember(miniPlayerBlendColor, isDark) {
            val rawAccent = if (miniPlayerBlendColor != 0) Color(miniPlayerBlendColor) else Color(0xFF8338EC)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(rawAccent.toArgb(), hsl)
            if (hsl[1] < 0.05f) {
                // Monochromatic / Grayscale
                if (isDark) Color.White else Color(0xFF1C1B1F)
            } else {
                rawAccent
            }
        }

        // Resolves sheet animations
        suspend fun animatePlayerSheet(targetExpanded: Boolean, initialVelocity: Float = 0f) {
            val expectedBottomNavHeight = if (dynamicBottomNavHeightState.value > 0f) {
                dynamicBottomNavHeightState.value
            } else {
                with(density) { 75.dp.toPx() }
            }
            val expectedBottomGapPx = with(density) { 12.dp.toPx() }
            val miniPlayerHeightPx = with(density) { MiniPlayerHeight.toPx() }
            val destY = if (targetExpanded) {
                0f
            } else {
                screenHeightPx - miniPlayerHeightPx - expectedBottomNavHeight - expectedBottomGapPx
            }
            val destFraction = if (targetExpanded) 1f else 0f
            
            val collapsedY = screenHeightPx - miniPlayerHeightPx - expectedBottomNavHeight - expectedBottomGapPx
            val totalDistance = collapsedY
            val initialFractionVelocity = if (totalDistance > 0f) {
                -initialVelocity / totalDistance
            } else 0f
            
            coroutineScope {
                launch {
                    currentSheetTranslationY.animateTo(
                        targetValue = destY,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                        initialVelocity = initialVelocity
                    )
                }
                launch {
                    playerContentExpansionFraction.animateTo(
                        targetValue = destFraction,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                        initialVelocity = initialFractionVelocity
                    )
                }
            }
        }

        LaunchedEffect(currentSheetContentState) {
            animatePlayerSheet(currentSheetContentState == PlayerSheetState.EXPANDED)
        }

        LaunchedEffect(playerContentExpansionFraction) {
            snapshotFlow { playerContentExpansionFraction.value }
                .collect { viewModel.setPlayerExpansionFraction(it) }
        }

        val sheetVisualState = rememberSheetVisualState(
            showPlayerContentArea = showPlayerContentArea,
            collapsedStateHorizontalPadding = 12.dp,
            predictiveBackCollapseProgress = sheetBackProgress,
            playerContentExpansionFraction = playerContentExpansionFraction,
            containerHeight = containerHeight,
            currentSheetTranslationY = currentSheetTranslationY,
            sheetCollapsedTargetYProvider = { sheetCollapsedTargetYState.value },
            isNavBarHiddenProvider = { (bottomNavTranslationYState.value / (if (dynamicBottomNavHeightState.value > 0) dynamicBottomNavHeightState.value else 1f)) > 0.5f },
            navBarCornerRadiusDp = 32.dp
        )

        // Dynamic Shape instantiation
        val playerShadowShape = remember(sheetVisualState.overallSheetTopCornerRadiusProvider, sheetVisualState.playerContentActualBottomRadiusProvider, sheetVisualState.playerContentAreaHeightPxProvider) {
            PlayerSheetDynamicShape(
                topRadiusProvider = sheetVisualState.overallSheetTopCornerRadiusProvider,
                bottomRadiusProvider = sheetVisualState.playerContentActualBottomRadiusProvider,
                virtualHeightProvider = sheetVisualState.playerContentAreaHeightPxProvider
            )
        }

        val velocityTracker = remember { VelocityTracker() }
        
        val sheetVerticalDragGestureHandler = remember(density) {
            val miniHeightPx = with(density) { MiniPlayerHeight.toPx() }
            SheetVerticalDragGestureHandler(
                scope = scope,
                velocityTracker = velocityTracker,
                densityProvider = { density },
                playerContentExpansionFraction = playerContentExpansionFraction,
                currentSheetTranslationY = currentSheetTranslationY,
                expandedYProvider = { 0f },
                collapsedYProvider = { sheetCollapsedTargetYState.value },
                miniHeightPxProvider = { miniHeightPx },
                currentSheetStateProvider = { currentSheetContentState },
                visualOvershootScaleY = visualOvershootScaleY,
                onAnimateSheet = { target, _, velocity -> animatePlayerSheet(target, velocity) },
                onExpandSheetState = { currentSheetContentState = PlayerSheetState.EXPANDED },
                onCollapseSheetState = { currentSheetContentState = PlayerSheetState.COLLAPSED },
                onDragStateChange = { dragging -> isDragging = dragging }
            )
        }

        val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = {
                scope.launch {
                    currentSheetTranslationY.snapTo(screenHeightPx)
                }
                viewModel.dismissPlayer()
            }
        )

        if (showPlayerContentArea) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, sheetVisualState.visualSheetTranslationYProvider().roundToInt()) }
                    .height(containerHeight),
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = sheetVisualState.currentBottomPadding)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isQueueExpanded) 5f else 2f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Phase-Optimized Layout measuring (Zero-Recomposition)
                                .layout { measurable, constraints ->
                                    val targetHeightPx = sheetVisualState.playerContentAreaHeightPxProvider().toInt()
                                    val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider().toInt()
                                    val endPaddingPx = sheetVisualState.currentHorizontalPaddingEndPxProvider().toInt()
                                    val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                    
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = innerWidth,
                                            maxWidth = innerWidth,
                                            minHeight = targetHeightPx,
                                            maxHeight = targetHeightPx
                                        )
                                    )
                                    layout(constraints.maxWidth, targetHeightPx) {
                                        placeable.placeRelative(startPaddingPx, 0)
                                    }
                                }
                                .miniPlayerDismissHorizontalGesture(
                                    enabled = currentSheetContentState == PlayerSheetState.COLLAPSED,
                                    handler = miniDismissGestureHandler
                                )
                                .graphicsLayer {
                                    translationX = offsetAnimatable.value
                                    scaleY = visualOvershootScaleY.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .shadow(
                                    elevation = if (currentSheetContentState == PlayerSheetState.COLLAPSED) 6.dp else 0.dp,
                                    shape = playerShadowShape
                                )
                                .background(
                                    color = dynamicBgColor,
                                    shape = playerShadowShape
                                )
                                .playerSheetVerticalDragGesture(enabled = !isLyricsModeEnabled && !isQueueExpanded, handler = sheetVerticalDragGestureHandler)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    currentSheetContentState = if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                        PlayerSheetState.EXPANDED
                                    } else {
                                        PlayerSheetState.COLLAPSED
                                    }
                                }
                        ) {
                            val verticalDragModifier = Modifier.playerSheetVerticalDragGesture(
                                enabled = currentSheetContentState == PlayerSheetState.EXPANDED && !isLyricsModeEnabled && !isQueueExpanded,
                                handler = sheetVerticalDragGestureHandler
                            )

                            UnifiedPlayerMiniAndFullLayers(
                                activity = activity,
                                viewModel = viewModel,
                                uiState = uiState,
                                songList = songList,
                                baseAccentColor = baseAccentColor,
                                context = context,
                                playerContentExpansionFraction = playerContentExpansionFraction,
                                containerHeight = containerHeight,
                                onCollapse = { currentSheetContentState = PlayerSheetState.COLLAPSED },
                                onExpand = { currentSheetContentState = PlayerSheetState.EXPANDED },
                                dragModifier = verticalDragModifier
                            )
                        }
                    }

                    // Integrated shared artwork layer. In lyrics mode the same ArtworkPager
                    // moves into the app bar as a compact thumbnail instead of being hidden.
                    val screenWidth = LocalConfiguration.current.screenWidthDp
                    val albumArtSizeDp = (screenWidth * 0.9f).dp
                    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()

                    Box(
                        modifier = Modifier
                            .size(albumArtSizeDp)
                            .graphicsLayer {
                                val t = playerContentExpansionFraction.value
                                val lyricsT = lyricsArtworkProgress

                                val miniSizePx = 48.dp.toPx()
                                val fullSizePx = albumArtSizeDp.toPx()
                                val normalScale = androidx.compose.ui.util.lerp(miniSizePx / fullSizePx, 1f, t)

                                // Account for the horizontal padding applied in the parent layout block
                                val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider()

                                // Position relative to the Surface top-left
                                val xStartPx = startPaddingPx + 16.dp.toPx()
                                val xEndPx = (with(density) { screenWidth.dp.toPx() } - fullSizePx) / 2f

                                val yStartPx = 16.dp.toPx()
                                val minTopOffsetDp = with(density) { statusBarTopPx.toDp() } + 68.dp // Removed 16.dp extra gap
                                val topOffsetDp = ((containerHeight - albumArtSizeDp) / 2f - 220.dp).coerceAtLeast(minTopOffsetDp)
                                val yEndPx = with(density) { topOffsetDp.toPx() }

                                val normalX = androidx.compose.ui.util.lerp(xStartPx, xEndPx, t)
                                val normalY = androidx.compose.ui.util.lerp(yStartPx, yEndPx, t)
                                val normalCornerRadius = androidx.compose.ui.util.lerp(fullSizePx / 2f, 16.dp.toPx(), t)
                                val normalShadow = androidx.compose.ui.util.lerp(0f, 16.dp.toPx(), t)

                                val appBarThumbSizePx = 44.dp.toPx()
                                val appBarScale = appBarThumbSizePx / fullSizePx
                                val appBarX = 22.dp.toPx()
                                val appBarY = statusBarTopPx + 18.dp.toPx()

                                val dismissOffsetPx = offsetAnimatable.value * (1f - t)

                                scaleX = androidx.compose.ui.util.lerp(normalScale, appBarScale, lyricsT)
                                scaleY = androidx.compose.ui.util.lerp(normalScale, appBarScale, lyricsT)
                                translationX = androidx.compose.ui.util.lerp(normalX, appBarX, lyricsT) + dismissOffsetPx
                                translationY = androidx.compose.ui.util.lerp(normalY, appBarY, lyricsT)
                                transformOrigin = TransformOrigin(0f, 0f)

                                val cornerRadius = androidx.compose.ui.util.lerp(normalCornerRadius, 10.dp.toPx(), lyricsT)
                                shape = RoundedCornerShape(cornerRadius)
                                clip = true
                                shadowElevation = androidx.compose.ui.util.lerp(normalShadow, 6.dp.toPx(), lyricsT)
                                alpha = artworkAlpha
                            }
                            .zIndex(if (isQueueExpanded) 1f else if (isLyricsModeEnabled || lyricsArtworkProgress > 0f) 6f else 3f)
                    ) {
                        ArtworkPager(
                            viewModel = viewModel,
                            currentSong = currentSong!!,
                            songList = songList,
                            currentSongIndex = uiState.currentSongIndex,
                            context = context,
                            userScrollEnabled = playerContentExpansionFraction.value > 0.95f && !isLyricsModeEnabled && !isQueueExpanded,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Global Dynamic Snackbar
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
        
        LaunchedEffect(Unit) {
            com.codetrio.spatialflow.ui.SnackbarController.events.collect { event ->
                snackbarHostState.showSnackbar(
                    com.codetrio.spatialflow.ui.CustomSnackbarVisuals(
                        message = event.message,
                        duration = event.duration,
                        iconResId = event.iconResId,
                        iconVector = event.iconVector
                    )
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f) // Keep Snackbar always on top
        ) {
            androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset {
                        val bottomNavHeightPx = if (dynamicBottomNavHeightState.value > 0f) {
                            dynamicBottomNavHeightState.value
                        } else {
                            with(density) { 75.dp.toPx() }
                        }
                        val bottomNavVisibilityFraction = (bottomNavTranslationYState.value / (if (bottomNavHeightPx > 0) bottomNavHeightPx else 1f)).coerceIn(0f, 1f)
                        val currentBottomGapPx = with(density) { 
                            androidx.compose.ui.unit.lerp(12.dp, 8.dp, bottomNavVisibilityFraction).toPx() 
                        }
                        
                        val effectiveBottomNavHeight = (bottomNavHeightPx - bottomNavTranslationYState.value).coerceAtLeast(0f)
                        
                        var bottomOffsetPx = effectiveBottomNavHeight + with(density) { 16.dp.toPx() }
                        
                        if (showPlayerContentArea) {
                            val miniPlayerHeightPx = with(density) { MiniPlayerHeight.toPx() }
                            bottomOffsetPx = effectiveBottomNavHeight + currentBottomGapPx + miniPlayerHeightPx + with(density) { 8.dp.toPx() }
                            
                            val topOfPlayerSheetY = sheetVisualState.visualSheetTranslationYProvider()
                            bottomOffsetPx = screenHeightPx - topOfPlayerSheetY + with(density) { 8.dp.toPx() }
                        }
                        
                        IntOffset(0, -bottomOffsetPx.roundToInt())
                    }
            ) { data ->
                androidx.compose.material3.Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val customVisuals = data.visuals as? com.codetrio.spatialflow.ui.CustomSnackbarVisuals
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        if (customVisuals?.iconVector != null) {
                            Icon(
                                imageVector = customVisuals.iconVector,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.inversePrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        } else if (customVisuals?.iconResId != null) {
                            Icon(
                                painter = painterResource(id = customVisuals.iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.inversePrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// 8. MINI PLAYER ROW INTERNAL CONTENT
// ==========================================
@Composable
private fun MiniPlayerContentInternal(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    isPlaying: Boolean,
    isProcessing: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val contentSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF1C1B1F).copy(alpha = 0.6f)
    val playBgColor = if (isDark) Color(0x1AFFFFFF) else Color(0x0D000000)

    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Morphing Art Space
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
            val duration by viewModel.duration.collectAsStateWithLifecycle()

            val progressAnimatable = remember { Animatable(0f) }
            LaunchedEffect(currentPosition, duration) {
                val target = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                progressAnimatable.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 350, easing = LinearEasing)
                )
            }

            val amplitudeAnimatable = remember { Animatable(if (isPlaying) 1f else 0f) }
            LaunchedEffect(isPlaying) {
                amplitudeAnimatable.animateTo(
                    targetValue = if (isPlaying) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }

            if (isProcessing) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(58.dp),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.25f),
                    amplitude = amplitudeAnimatable.value,
                    stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = with(LocalDensity.current) { 6.dp.toPx() },
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    trackStroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = with(LocalDensity.current) { 6.dp.toPx() },
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            } else {
                CircularWavyProgressIndicator(
                    progress = { progressAnimatable.value },
                    modifier = Modifier.size(58.dp),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.25f),
                    waveSpeed = WavyProgressIndicatorDefaults.CircularWavelength * 0.4f,
                    amplitude = { amplitudeAnimatable.value },
                    stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = with(LocalDensity.current) { 6.dp.toPx() },
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    trackStroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = with(LocalDensity.current) { 6.dp.toPx() },
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }

            Box(modifier = Modifier.size(48.dp)) {
                // Placeholder Box for structure: AsyncImage is managed globally via parent's floating shared element!
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentSong.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = contentSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Controls Row using custom icons painterResource
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.playPreviousSong() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = "Previous",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    if (isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(playBgColor, CircleShape)
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = "Play/Pause",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { viewModel.playNextSong() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
