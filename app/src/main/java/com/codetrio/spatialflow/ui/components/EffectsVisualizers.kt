package com.codetrio.spatialflow.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


// ─── Reverb: Dampened Waveform with Ambient Glow & Early Reflections ────────

@Composable
fun ReverbRoomVisualizer(
    presetIndex: Int, // 0 = None, 1–6 = rooms
    enabled: Boolean,
    speed: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Continuous rotation to make the cylinder spin smoothly like FL Studio
    val infiniteTransition = rememberInfiniteTransition(label = "reverb_rotation")
    val rotationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (6000f / speed).toInt().coerceIn(1500, 15000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "cylinder_spin"
    )

    // Animate glow alpha with a delay to let the AnimatedVisibility slide-down finish first, preventing clipping
    val glowAlpha by animateFloatAsState(
        targetValue = if (enabled && presetIndex > 0) 1f else 0f,
        animationSpec = if (enabled && presetIndex > 0) {
            tween(durationMillis = 400, delayMillis = 350)
        } else {
            tween(durationMillis = 150)
        },
        label = "reverb_glow_alpha"
    )

    // Layout scaling based on preset index (Higher preset = physically larger cylinder)
    val targetScaleFraction = if (presetIndex <= 0 || !enabled) 0.4f else (0.5f + presetIndex * 0.08f).coerceAtMost(1f)
    val roomScaleFraction by animateFloatAsState(
        targetValue = targetScaleFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reverb_room_scale"
    )

    // Pre-cache dp conversions outside the Canvas DrawScope
    val density = LocalDensity.current
    val strokeWidth = with(density) { 1.5.dp.toPx() }
    val ryBase = with(density) { 16.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp) // Height slightly increased to properly frame the 3D projection
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val wireColor = if (enabled) primaryColor else onSurfaceVariant.copy(alpha = 0.38f)
        val currentRotation = if (enabled) rotationPhase else 0f

        // 1. Calculate dynamic boundaries based on the preset scale
        val cylinderWidth = w * 0.5f * roomScaleFraction
        val cylinderHeight = h * 0.65f * roomScaleFraction
        
        // Horizontal and vertical radii for the top/bottom ellipse perspective flatlines
        val rx = cylinderWidth / 2f
        val ry = ryBase * roomScaleFraction // Ellipse flatness/tilt

        val topCenterY = cy - (cylinderHeight / 2f)
        val bottomCenterY = cy + (cylinderHeight / 2f)

        // 2. Draw ambient background glow inside the cylinder zone
        if (glowAlpha > 0f) {
            val glowRadius = (h * 0.48f * roomScaleFraction).coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.12f * roomScaleFraction * glowAlpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(cx, cy)
            )
        }

        // 3. Render the vertical structural lines (Pillars) with depth mapping
        // More presets = more geometric line segments for structural complexity
        val verticalPillarsCount = 12 + (presetIndex * 2) 
        
        for (i in 0 until verticalPillarsCount) {
            // Distribute angles evenly around the 360-degree loop + add rotation animation
            val angle = (i.toFloat() / verticalPillarsCount) * (2 * PI).toFloat() + currentRotation
            
            // Map 3D coordinates onto a 2D isometric viewport plane
            val xOffset = rx * cos(angle)
            val yOffset = ry * sin(angle)

            val px = cx + xOffset
            val pTopY = topCenterY + yOffset
            val pBottomY = bottomCenterY + yOffset

            // Depth calculation: fade elements resting on the back wall to increase 3D realism
            val isFacingFront = sin(angle) > 0
            val lineAlpha = if (isFacingFront) 0.65f else 0.2f

            drawLine(
                color = wireColor.copy(alpha = lineAlpha),
                start = Offset(px, pTopY),
                end = Offset(px, pBottomY),
                strokeWidth = strokeWidth
            )
        }

        // 4. Draw Top Ring Oval Rim
        val topPath = Path().apply {
            for (step in 0..100) {
                val angle = (step.toFloat() / 100f) * (2 * PI).toFloat()
                val x = cx + rx * cos(angle)
                val y = topCenterY + ry * sin(angle)
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = topPath,
            color = wireColor.copy(alpha = 0.8f),
            style = Stroke(width = strokeWidth)
        )

        // 5. Draw Bottom Ring Oval Rim
        val bottomPath = Path().apply {
            for (step in 0..100) {
                val angle = (step.toFloat() / 100f) * (2 * PI).toFloat()
                val x = cx + rx * cos(angle)
                val y = bottomCenterY + ry * sin(angle)
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = bottomPath,
            color = wireColor.copy(alpha = 0.8f),
            style = Stroke(width = strokeWidth)
        )
    }
}

// ─── Loudness Enhancer: Arc Gauge with Pulse + dB Readout ───────────────────

@Composable
fun LoudnessRingIndicator(
    gain: Float, // 0f..12f
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val coolTeal = Color(0xFF4DD0E1)
    val warmAmber = Color(0xFFFFB74D)
    val dangerRed = Color(0xFFFF5252)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    val animatedGain by animateFloatAsState(
        targetValue = gain,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "loudness_gain"
    )

    val fraction = (animatedGain / 12f).coerceIn(0f, 1f)

    // Jitter phase for needle vibration at high levels
    val infiniteTransition = rememberInfiniteTransition(label = "loudness_jitter")
    val jitterPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "jitter"
    )

    // Needle jitter scales up past 8 dB
    val jitter = if (enabled && gain > 8f) {
        val intensity = ((gain - 8f) / 4f).coerceIn(0f, 1f)
        sin(jitterPhase * 6f) * 2.2f * intensity
    } else 0f

    // dB text readout
    val textMeasurer = rememberTextMeasurer()
    val dbText = if (gain > 0f) "+${gain.toInt()} dB" else "0 dB"
    val textStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    val textColor = if (enabled) lerp(coolTeal, dangerRed, fraction) else onSurfaceVariant.copy(alpha = 0.4f)

    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 6.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height * 0.65f
        val radius = size.height * 0.56f
        val arcRect = Size(radius * 2, radius * 2)
        val topLeft = Offset(cx - radius, cy - radius)

        // Pivot center for needle (matching arc center)
        val pivotX = cx
        val pivotY = cy

        // 1. Draw Backplate Glow (Warm vacuum tube ambiance)
        if (enabled) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lerp(coolTeal, dangerRed, fraction).copy(alpha = 0.08f + fraction * 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius * 1.2f
                ),
                radius = radius * 1.2f,
                center = Offset(cx, cy)
            )
        }

        // 2. Draw Backplate Arc Track (200° sweep)
        drawArc(
            color = onSurfaceVariant.copy(alpha = 0.1f),
            startAngle = 170f,
            sweepAngle = 200f,
            useCenter = false,
            topLeft = topLeft,
            size = arcRect,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )

        // 3. Draw Radial Scale Ticks
        val tickCount = 13
        for (i in 0 until tickCount) {
            val tickAngle = 170f + i * (200f / (tickCount - 1))
            val angleRad = Math.toRadians(tickAngle.toDouble())
            
            val isWarningTick = i >= 10
            val tickColor = when {
                !enabled -> onSurfaceVariant.copy(alpha = 0.2f)
                isWarningTick -> dangerRed.copy(alpha = 0.8f)
                else -> coolTeal.copy(alpha = 0.7f)
            }

            // Radial line coordinates
            val innerR = radius - 4.dp.toPx()
            val outerR = radius + (if (i % 3 == 0) 4.dp.toPx() else 1.5.dp.toPx())
            
            val tickStartX = cx + innerR * cos(angleRad).toFloat()
            val tickStartY = cy + innerR * sin(angleRad).toFloat()
            val tickEndX = cx + outerR * cos(angleRad).toFloat()
            val tickEndY = cy + outerR * sin(angleRad).toFloat()

            drawLine(
                color = tickColor,
                start = Offset(tickStartX, tickStartY),
                end = Offset(tickEndX, tickEndY),
                strokeWidth = (if (i % 3 == 0) 2.5.dp.toPx() else 1.2.dp.toPx())
            )
        }

        // 4. Draw Active Arc Fill
        if (enabled && fraction > 0.01f) {
            val sweepAngle = 200f * fraction
            drawArc(
                color = lerp(coolTeal, dangerRed, fraction),
                startAngle = 170f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcRect,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }

        // 5. Draw Mechanical Needle Pointer
        val needleAngle = 170f + 200f * fraction + jitter
        val needleAngleRad = Math.toRadians(needleAngle.toDouble())
        val needleLength = radius * 0.95f
        
        val needleEndX = pivotX + needleLength * cos(needleAngleRad).toFloat()
        val needleEndY = pivotY + needleLength * sin(needleAngleRad).toFloat()

        // Shadow for the needle to give depth
        if (enabled) {
            drawLine(
                color = Color.Black.copy(alpha = 0.15f),
                start = Offset(pivotX + 2.5.dp.toPx(), pivotY + 2.5.dp.toPx()),
                end = Offset(needleEndX + 2.5.dp.toPx(), needleEndY + 2.5.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Main needle pointer line
        drawLine(
            color = if (enabled) lerp(coolTeal, dangerRed, fraction) else onSurfaceVariant.copy(alpha = 0.3f),
            start = Offset(pivotX, pivotY),
            end = Offset(needleEndX, needleEndY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Pivot brass cap
        drawCircle(
            color = if (enabled) lerp(coolTeal, dangerRed, fraction).copy(alpha = 0.2f) else Color.Transparent,
            radius = 10.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )
        drawCircle(
            color = if (enabled) primaryColor else onSurfaceVariant.copy(alpha = 0.5f),
            radius = 6.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )

        // 6. Center LED Peak Bar segments (Bottom horizontal indicator)
        val segWidth = 8.dp.toPx()
        val segHeight = 4.dp.toPx()
        val gap = 4.dp.toPx()
        val segCount = 7
        val totalWidth = segCount * segWidth + (segCount - 1) * gap
        val startX = cx - totalWidth / 2f
        val segY = size.height - 8.dp.toPx()

        for (j in 0 until segCount) {
            val isActive = enabled && (fraction >= (j.toFloat() / (segCount - 1)))
            val segColor = when {
                !isActive -> onSurfaceVariant.copy(alpha = 0.08f)
                j >= 5 -> dangerRed // Peak Red
                j >= 3 -> warmAmber // Mid Orange
                else -> coolTeal     // Normal Teal
            }
            drawRoundRect(
                color = segColor,
                topLeft = Offset(startX + j * (segWidth + gap), segY),
                size = Size(segWidth, segHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }

        // 7. Center dB Readout
        val measuredText = textMeasurer.measure(dbText, textStyle)
        drawText(
            textLayoutResult = measuredText,
            color = textColor,
            topLeft = Offset(
                cx - measuredText.size.width / 2f,
                cy + radius * 0.28f - measuredText.size.height / 2f
            )
        )
    }
}

// ─── Stereo Balance: Speaker Icons with Constant-Power Panning ──────────────

@Composable
fun BalanceChannelMeter(
    balancePosition: Float, // -50..50
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val animatedBalance by animateFloatAsState(
        targetValue = balancePosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "balance_pos"
    )

    val normalizedBalance = (animatedBalance / 50f).coerceIn(-1f, 1f)

    // Constant-power panning law
    val panAngle = (PI.toFloat() / 4f) * (normalizedBalance + 1f)
    val leftIntensity = if (enabled) cos(panAngle) else 0.3f
    val rightIntensity = if (enabled) sin(panAngle) else 0.3f

    // Infinite transition for wave propagation animation
    val infiniteTransition = rememberInfiniteTransition(label = "balance_ripple")
    val waveOffsetFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_offset"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 8.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // 1. Draw Soundstage grid background (subtle crosshairs and ticks)
        val gridColor = onSurfaceVariant.copy(alpha = 0.05f)
        // Horizontal baseline
        drawLine(
            color = gridColor,
            start = Offset(24.dp.toPx(), cy),
            end = Offset(w - 24.dp.toPx(), cy),
            strokeWidth = 1.dp.toPx()
        )
        // Vertical midline
        drawLine(
            color = gridColor,
            start = Offset(cx, 8.dp.toPx()),
            end = Offset(cx, h - 8.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Draw Listener sweet spot (head/ears representation in the center)
        val listenerColor = if (enabled) primaryColor.copy(alpha = 0.15f) else onSurfaceVariant.copy(alpha = 0.1f)
        // Sweet spot zone ring
        drawCircle(
            color = listenerColor,
            radius = 16.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
        )
        // Inner core
        drawCircle(
            color = if (enabled) primaryColor.copy(alpha = 0.7f) else onSurfaceVariant.copy(alpha = 0.4f),
            radius = 5.dp.toPx(),
            center = Offset(cx, cy)
        )

        // 3. Draw Left & Right Speaker Sources (at the far edges)
        val leftSpeakerX = 16.dp.toPx()
        val rightSpeakerX = w - 16.dp.toPx()

        // Left Speaker Source Indicator
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = 0.1f + leftIntensity * 0.2f) else onSurfaceVariant.copy(alpha = 0.08f),
            topLeft = Offset(leftSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = leftIntensity) else onSurfaceVariant.copy(alpha = 0.3f),
            topLeft = Offset(leftSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Right Speaker Source Indicator
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = 0.1f + rightIntensity * 0.2f) else onSurfaceVariant.copy(alpha = 0.08f),
            topLeft = Offset(rightSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = if (enabled) primaryColor.copy(alpha = rightIntensity) else onSurfaceVariant.copy(alpha = 0.3f),
            topLeft = Offset(rightSpeakerX - 6.dp.toPx(), cy - 12.dp.toPx()),
            size = Size(12.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 4. Draw Acoustic Soundwave Propagation
        if (enabled) {
            val maxWaveRadius = cx - leftSpeakerX - 8.dp.toPx()
            val waveCount = 3
            for (i in 0 until waveCount) {
                // Staggered propagation progress
                val progress = (waveOffsetFraction + i.toFloat() / waveCount) % 1f
                val r = progress * maxWaveRadius
                val waveAlpha = (1f - progress) * 0.45f

                // Left speaker soundwave arc (propagating to the right)
                if (leftIntensity > 0.05f) {
                    drawArc(
                        color = primaryColor.copy(alpha = waveAlpha * leftIntensity),
                        startAngle = -45f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(leftSpeakerX - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Right speaker soundwave arc (propagating to the left)
                if (rightIntensity > 0.05f) {
                    drawArc(
                        color = primaryColor.copy(alpha = waveAlpha * rightIntensity),
                        startAngle = 135f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(rightSpeakerX - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        // 5. Draw Dynamic Glowing Sound Source Node
        val nodeX = cx + (cx - leftSpeakerX - 24.dp.toPx()) * normalizedBalance
        val nodeY = cy

        if (enabled) {
            // Radial outer glow centered on the source node
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(nodeX, nodeY),
                    radius = 24.dp.toPx()
                ),
                radius = 24.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )

            // Inner core orb
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
            // Accent dot inside orb
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        } else {
            // Disabled state center indicator
            drawCircle(
                color = onSurfaceVariant.copy(alpha = 0.3f),
                radius = 5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        }
    }
}



// ─── Playback Speed: Vinyl Turntable & Scrolling Time-Stretch Waveform ──────

@Composable
fun SpeedDialVisualizer(
    speed: Float, // 0.5..2.0
    enabled: Boolean,
    isPitchMatched: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Continuous, frame-aligned delta accumulation loop to handle dynamic speed changes smoothly without jumping
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(enabled, speed) {
        if (enabled) {
            var lastTime = withFrameNanos { it }
            while (true) {
                withFrameNanos { time ->
                    val deltaSeconds = (time - lastTime) / 1_000_000_000f
                    lastTime = time
                    // 1.0x speed = 2 * PI radians in 1.8 seconds -> speed coefficient scales this rate
                    val rate = (2f * PI.toFloat() / 1.8f) * speed
                    phase = (phase + rate * deltaSeconds) % (2f * PI.toFloat())
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "speed_wave")
    // Secondary phase for vinyl wobble noise
    val wobblePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wobble"
    )

    val waveColor = when {
        !enabled -> onSurfaceVariant.copy(alpha = 0.2f)
        !isPitchMatched -> tertiaryColor // Vinyl mode: warm tint
        else -> primaryColor
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // 1. Vinyl Turntable centered parameters
        val recordRadius = h * 0.46f
        val recordCenterX = w / 2f
        val recordCenterY = midY

        val platterColor = onSurfaceVariant.copy(alpha = 0.15f)
        val vinylColor = Color(0xFF141414)

        // Turntable Platter Ambient Glow
        if (enabled) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(waveColor.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(recordCenterX, recordCenterY),
                    radius = recordRadius * 1.3f
                ),
                radius = recordRadius * 1.3f,
                center = Offset(recordCenterX, recordCenterY)
            )
        }

        // Platter outer rim
        drawCircle(
            color = platterColor,
            radius = recordRadius,
            center = Offset(recordCenterX, recordCenterY),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Vinyl Disc Body
        drawCircle(
            color = vinylColor,
            radius = recordRadius - 2.dp.toPx(),
            center = Offset(recordCenterX, recordCenterY)
        )

        // Concentric Vinyl Grooves
        if (enabled) {
            val grooveColor = Color.White.copy(alpha = 0.05f)
            drawCircle(color = grooveColor, radius = recordRadius * 0.8f, center = Offset(recordCenterX, recordCenterY), style = Stroke(width = 1f))
            drawCircle(color = grooveColor, radius = recordRadius * 0.65f, center = Offset(recordCenterX, recordCenterY), style = Stroke(width = 1f))
            drawCircle(color = grooveColor, radius = recordRadius * 0.5f, center = Offset(recordCenterX, recordCenterY), style = Stroke(width = 1f))
        }

        // Center label (Dynamic color scheme matching pitch mode)
        drawCircle(
            color = waveColor.copy(alpha = if (enabled) 0.85f else 0.35f),
            radius = recordRadius * 0.32f,
            center = Offset(recordCenterX, recordCenterY)
        )

        // Center spindle hole
        drawCircle(
            color = Color(0xFF222222),
            radius = recordRadius * 0.08f,
            center = Offset(recordCenterX, recordCenterY)
        )

        // Spinning indicator mark
        if (enabled) {
            val indicatorRadius = recordRadius * 0.62f
            val markerPhase = phase // Spun by wave scroll phase
            val indX = recordCenterX + indicatorRadius * cos(markerPhase)
            val indY = recordCenterY + indicatorRadius * sin(markerPhase)
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 2.dp.toPx(),
                center = Offset(indX, indY)
            )
        }

        // 2. Tonearm / Needle (Pivots on the right side of the centered disc)
        val pivotX = recordCenterX + recordRadius + 8.dp.toPx()
        val pivotY = recordCenterY - recordRadius + 4.dp.toPx()

        // Rest or park position
        val targetX = if (enabled) recordCenterX + recordRadius * 0.6f * cos(0.2f) else pivotX - 3.dp.toPx()
        val targetY = if (enabled) recordCenterY + recordRadius * 0.6f * sin(0.2f) else pivotY + 22.dp.toPx()

        // Micro wobble jitter feedback on the needle
        val jitter = if (enabled && !isPitchMatched) {
            sin(wobblePhase * 4f) * 0.6.dp.toPx()
        } else 0f

        // Draw metal arm
        drawLine(
            color = if (enabled) onSurfaceVariant else onSurfaceVariant.copy(alpha = 0.4f),
            start = Offset(pivotX, pivotY),
            end = Offset(targetX + jitter, targetY + jitter),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Stylus / cartridge head
        drawCircle(
            color = waveColor,
            radius = 2.5.dp.toPx(),
            center = Offset(targetX + jitter, targetY + jitter)
        )

        // Pivot base circle
        drawCircle(
            color = onSurfaceVariant.copy(alpha = 0.8f),
            radius = 3.5.dp.toPx(),
            center = Offset(pivotX, pivotY)
        )
    }
}

// ─── 8D Audio: Headphone Listener with Orbiting Glowing Sound Node ──────────

@Composable
fun Spatial8DVisualizer(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val infiniteTransition = rememberInfiniteTransition(label = "8d_visualizer")
    
    // Orbital rotation angle in degrees
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Waves propagation offset
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    // Breathing pulse for headphones
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val density = LocalDensity.current
    val headRadius = with(density) { 14.dp.toPx() }
    val orbitRadius = with(density) { 38.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(116.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        val activeColor = primaryColor
        val inactiveColor = onSurfaceVariant.copy(alpha = 0.3f)
        val currentColor = if (enabled) activeColor else inactiveColor

        // 1. Draw Radar/Compass concentric rings (Grid)
        val radarColor = onSurfaceVariant.copy(alpha = 0.05f)
        drawCircle(
            color = radarColor,
            radius = orbitRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
        )
        drawCircle(
            color = radarColor,
            radius = orbitRadius * 0.6f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
        )
        // Subtle crosshair lines
        drawLine(
            color = radarColor,
            start = Offset(cx - orbitRadius - 10.dp.toPx(), cy),
            end = Offset(cx + orbitRadius + 10.dp.toPx(), cy),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = radarColor,
            start = Offset(cx, cy - orbitRadius - 10.dp.toPx()),
            end = Offset(cx, cy + orbitRadius + 10.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Draw Listener Head and Headphones (Center)
        val currentPulse = if (enabled) pulseScale else 1f
        val currentHeadRadius = headRadius * currentPulse

        // Listener Head
        drawCircle(
            color = currentColor.copy(alpha = if (enabled) 0.1f else 0.05f),
            radius = currentHeadRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = currentColor.copy(alpha = if (enabled) 0.6f else 0.3f),
            radius = currentHeadRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Headphones earcups (Left & Right of Head)
        val earcupW = 4.dp.toPx() * currentPulse
        val earcupH = 12.dp.toPx() * currentPulse
        val earcupRadius = 1.5.dp.toPx()

        // Left earcup
        drawRoundRect(
            color = currentColor,
            topLeft = Offset(cx - currentHeadRadius - earcupW, cy - earcupH / 2f),
            size = Size(earcupW, earcupH),
            cornerRadius = CornerRadius(earcupRadius)
        )
        // Right earcup
        drawRoundRect(
            color = currentColor,
            topLeft = Offset(cx + currentHeadRadius, cy - earcupH / 2f),
            size = Size(earcupW, earcupH),
            cornerRadius = CornerRadius(earcupRadius)
        )

        // Headphone headband (arc connecting earcups over the top of the head)
        val bandRect = Size((currentHeadRadius + earcupW / 2f) * 2f, (currentHeadRadius + earcupW / 2f) * 2f)
        drawArc(
            color = currentColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - currentHeadRadius - earcupW / 2f, cy - currentHeadRadius - earcupW / 2f),
            size = bandRect,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 3. Draw Orbiting Sound Source & Particle Trail
        val angleRad = if (enabled) Math.toRadians(rotationAngle.toDouble()) else Math.toRadians(-90.0)
        val nodeX = cx + orbitRadius * cos(angleRad).toFloat()
        val nodeY = cy + orbitRadius * sin(angleRad).toFloat()

        if (enabled) {
            // Draw particle trails behind the orbiting node
            val trailCount = 5
            for (i in 1..trailCount) {
                val trailAngleRad = Math.toRadians((rotationAngle - i * 8f).toDouble())
                val tx = cx + orbitRadius * cos(trailAngleRad).toFloat()
                val ty = cy + orbitRadius * sin(trailAngleRad).toFloat()
                val trailAlpha = 0.5f * (1f - i.toFloat() / trailCount)
                val trailSize = 4.dp.toPx() * (1f - 0.15f * i)

                drawCircle(
                    color = secondaryColor.copy(alpha = trailAlpha),
                    radius = trailSize,
                    center = Offset(tx, ty)
                )
            }

            // Draw soundwave rings expanding from the active sound source
            val waveCount = 2
            for (wIndex in 0 until waveCount) {
                val wProgress = (waveOffset + wIndex.toFloat() / waveCount) % 1f
                val maxWaveRadius = 24.dp.toPx()
                val wr = wProgress * maxWaveRadius
                val wAlpha = (1f - wProgress) * 0.4f

                drawCircle(
                    color = primaryColor.copy(alpha = wAlpha),
                    radius = wr,
                    center = Offset(nodeX, nodeY),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Radial Glow centered on the main sound node
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(nodeX, nodeY),
                    radius = 16.dp.toPx()
                ),
                radius = 16.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )

            // Inner solid core node
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        } else {
            // Static node in disabled state
            drawCircle(
                color = inactiveColor,
                radius = 4.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        }
    }
}

