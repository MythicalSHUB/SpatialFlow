@file:OptIn(androidx.media3.common.util.UnstableApi::class)
@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.codetrio.spatialflow.ui.player.canvas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import java.util.Locale

private const val STALL_CHECK_INTERVAL_MS = 1_000L
private const val STALL_TIMEOUT_MS = 5_000L
private const val CANVAS_MAX_VIDEO_WIDTH = 1_920
private const val CANVAS_MAX_VIDEO_HEIGHT = 1_920

/**
 * Renders a looped, muted motion artwork video using ExoPlayer on a TextureView surface.
 *
 * - [primaryUrl]: Preferred URL (typically HLS m3u8 or direct MP4).
 * - [fallbackUrl]: Fallback URL (typically a cached local MP4 file URI).
 * - Implements a 5-second stall detector: if the primary HLS stream stalls, automatically
 *   switches to [fallbackUrl].
 * - Crossfades from transparent to fully opaque over 300ms after the first frame renders,
 *   hiding the initial black frame.
 */
@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback ?: return

    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    val shouldPlay by rememberUpdatedState(isPlaying)

    val cacheDataSourceFactory = remember(context) {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context,
            com.codetrio.spatialflow.di.MediaEntryPoint::class.java
        )
        entryPoint.cacheDataSourceFactory()
    }

    val mediaSourceFactory = remember(cacheDataSourceFactory) {
        DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory)
    }

    val exoPlayer = remember(initial, mediaSourceFactory) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setForceHighestSupportedBitrate(true)
                    .build()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = isPlaying
            }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    // ── Stall monitor: switch from primary HLS to fallback MP4 after 5 s of no position advance ──
    LaunchedEffect(currentUrl, isPlaying, primary, fallback, exoPlayer) {
        if (!isPlaying || fallback.isNullOrBlank() || currentUrl != primary) return@LaunchedEffect
        var lastPosition = exoPlayer.currentPosition
        var stalledForMs = 0L

        while (isActive && isPlaying && currentUrl == primary) {
            delay(STALL_CHECK_INTERVAL_MS)
            val currentPosition = exoPlayer.currentPosition
            val isActivelyRendering = exoPlayer.playbackState == Player.STATE_READY &&
                exoPlayer.isPlaying && currentPosition != lastPosition
            stalledForMs = if (isActivelyRendering) 0L else stalledForMs + STALL_CHECK_INTERVAL_MS
            if (stalledForMs >= STALL_TIMEOUT_MS) {
                currentUrl = fallback
                isVideoReady = false
                return@LaunchedEffect
            }
            lastPosition = currentPosition
        }
    }

    // ── Lifecycle: pause/resume with app lifecycle ──
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                exoPlayer.setCanvasPlayback(shouldPlay)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Error / state listeners ──
    DisposableEffect(exoPlayer, primary, fallback) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val next = when (currentUrl) {
                    primary -> fallback?.takeIf { it != currentUrl }
                    else -> null
                }
                if (!next.isNullOrBlank()) {
                    currentUrl = next
                    isVideoReady = false
                }
            }

            override fun onRenderedFirstFrame() {
                isVideoReady = true
                if (shouldPlay) exoPlayer.setCanvasPlayback(isPlaying = true)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ── Load new URL into player ──
    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        isVideoReady = false
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType = when {
            lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
            lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
            primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
            fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
            else -> MimeTypes.APPLICATION_M3U8
        }

        val mediaItem = MediaItem.Builder()
            .setUri(normalized)
            .setMimeType(mimeType)
            .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // ── 300 ms crossfade on first frame ──
    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    ContentFrame(
        player = exoPlayer,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = resizeMode.toContentScale(),
        keepContentOnReset = false,
        shutter = {},
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}

private fun Int.toContentScale(): ContentScale = when (this) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> ContentScale.FillWidth
    AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> ContentScale.FillHeight
    else -> ContentScale.Fit
}

private fun ExoPlayer.setCanvasPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    } else {
        pause()
    }
}

private const val CANVAS_PLAYBACK_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
