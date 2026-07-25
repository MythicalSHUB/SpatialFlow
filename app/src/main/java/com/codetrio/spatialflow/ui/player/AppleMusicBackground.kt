package com.codetrio.spatialflow.ui.player

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.AspectRatioFrameLayout
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlayer

/**
 * Full-screen Now Playing background engine.
 * Supports:
 * 1. "Blurred" Mode: RenderScript hardware blurred cover artwork + KenBurns 12s pan/zoom + low-opacity palette gradient.
 * 2. "Solid" Mode: Clean high-contrast spring-animated palette gradient.
 * 3. Canvas Artwork Video layer integration.
 */
@Composable
fun AppleMusicBackground(
    song: SongItem?,
    modifier: Modifier = Modifier,
    canvasArtwork: CanvasArtwork? = null,
    isPlaying: Boolean = true,
    isLyricsModeEnabled: Boolean = false,
    accentColor: Color = Color(0xFF6200EE),
    backgroundColor: Color = Color.Black,
    content: @Composable () -> Unit = {}
) {
    val context = LocalContext.current

    // ── SharedPreferences Settings Observers ───────────────────────────────────
    val bgMode = remember {
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getString("now_playing_background_v2", "Blurred") ?: "Blurred")
    }
    val isBlurred = remember(bgMode.value) { bgMode.value == "Blurred" }

    val kenBurnsEnabled = remember {
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("now_playing_background_effect", false))
    }

    val showAnimatedArt = remember {
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("show_animated_art", true))
    }

    val playerTheme = remember {
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getString("player_theme", "fluid") ?: "fluid")
    }
    val isStatic = remember(playerTheme.value) { playerTheme.value == "static" }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "now_playing_background_v2" -> bgMode.value = p.getString(key, "Blurred") ?: "Blurred"
                "now_playing_background_effect" -> kenBurnsEnabled.value = p.getBoolean(key, false)
                "show_animated_art" -> showAnimatedArt.value = p.getBoolean(key, true)
                "player_theme" -> playerTheme.value = p.getString(key, "fluid") ?: "fluid"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // ── Artwork URI ───────────────────────────────────────────────────────────────
    val rawUri     = song?.getAlbumArtUri()
    val videoId    = song?.videoId
    val artworkUrl = remember(song) {
        when {
            rawUri != null && rawUri.toString().isNotEmpty() ->
                SongItem.enhanceThumbnailUrl(rawUri.toString())
            !videoId.isNullOrEmpty() ->
                "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
            else -> null
        }
    }

    // ── Palette colors from global singleton ───────────────────────────────────────
    val vibrant     = PlayerPaletteState.vibrantColor.value
    val darkVibrant = PlayerPaletteState.darkVibrantColor.value
    val darkMuted   = PlayerPaletteState.darkMutedColor.value

    // ── Spring-animated palette color morphing ─────────────────────────────────────
    val animatedVibrant: Color = animateColorAsState(
        targetValue   = vibrant,
        animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f),
        label         = "vibrant_spring"
    ).value
    val animatedDarkVibrant: Color = animateColorAsState(
        targetValue   = darkVibrant,
        animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f),
        label         = "dark_vibrant_spring"
    ).value
    val animatedDarkMuted: Color = animateColorAsState(
        targetValue   = darkMuted,
        animationSpec = spring(stiffness = 100f, dampingRatio = 0.8f),
        label         = "dark_muted_spring"
    ).value

    // ── Canvas motion artwork ──────────────────────────────────────────────────────
    val hasCanvas = !isLyricsModeEnabled && showAnimatedArt.value && canvasArtwork != null
    val canvasUrl = remember(canvasArtwork) {
        canvasArtwork?.preferredVerticalAnimationUrl
            ?: canvasArtwork?.preferredAnimationUrl
            ?: canvasArtwork?.videoUrlVertical
            ?: canvasArtwork?.videoUrl
    }

    // ═════════════════════════════════════════════════════════════════════════════
    //  BACKGROUND COMPOSITION
    // ═════════════════════════════════════════════════════════════════════════════
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        if (isStatic) {
            // STATIC THEME — SINGLE SOLID COLOR BACKGROUND
            SpatialWrapper {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animatedDarkMuted.copy(alpha = 1f))
                )
            }
        } else {
            // FLUID THEME — BLURRED ARTWORK CANVAS + Dynamic KenBurns & Mesh Saturation
            SpatialWrapper {
                SpatialFloatingLight(
                    modifier = Modifier.fillMaxSize(),
                    album = { artworkUrl },
                    isPlaying = { isPlaying },
                    isLyricsPage = { isLyricsModeEnabled },
                    backgroundEffectEnabled = kenBurnsEnabled.value
                )
            }

            // Dynamic Palette Gradient Overlay
            SpatialWrapper {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    animatedVibrant.copy(alpha = 0.30f),
                                    animatedDarkVibrant.copy(alpha = 0.20f),
                                    animatedDarkMuted.copy(alpha = 0.15f)
                                )
                            )
                        )
                )
            }
        }

        // ── Canvas Video Overlay (if available) ──────────────────────────────────
        if (hasCanvas && !canvasUrl.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f  to Color.White,
                                    0.50f to Color.White,
                                    1.0f  to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl  = canvasUrl,
                        fallbackUrl = null,
                        isPlaying   = isPlaying,
                        resizeMode  = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
                        modifier    = Modifier.fillMaxSize()
                    )
                }
            }
        }

        content()
    }
}
