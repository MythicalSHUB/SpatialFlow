package com.codetrio.spatialflow.ui.player

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flaviofaria.kenburnsview.KenBurnsView
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SpatialWrapper(content: @Composable () -> Unit) = key(content.hashCode()) {
    content()
}

@Stable
private enum class Option {
    Set,
    Pause,
    Resume,
    Init
}

/**
 * SpatialFlow full-screen player blurred artwork canvas with KenBurns slow pan/zoom motion engine,
 * 2.0x saturation boost, RenderScript hardware blur, and page transition dimming overlay.
 */
@Composable
fun SpatialFloatingLight(
    modifier: Modifier = Modifier,
    album: () -> Any?,
    isPlaying: () -> Boolean,
    isLyricsPage: () -> Boolean = { false },
    showMiniPlayer: () -> Boolean = { false },
    backgroundEffectEnabled: Boolean = true
) {
    val drawable = remember(album()) {
        mutableStateOf<Drawable?>(null)
    }

    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }

    SpatialWrapper {
        LaunchedEffect(album()) {
            val albumData = album() ?: return@LaunchedEffect
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(albumData)
                        .allowHardware(false)
                        .build()

                    val rawBitmap = imageLoader.execute(request).drawable?.toBitmap()
                    if (rawBitmap != null) {
                        val compressed = BitmapResolver.bitmapCompress(
                            bitmap = rawBitmap,
                            kenBurnsMode = backgroundEffectEnabled
                        )
                        rawBitmap.recycle()

                        val resolved = imageResolve(compressed)
                        compressed.recycle()

                        drawable.value = resolved.toDrawable(context.resources)
                    }
                } catch (_: Exception) {
                    drawable.value = null
                }
            }
        }
    }

    SpatialWrapper {
        val lossEffect = remember("SpatialFloatingLight_lossEffect") {
            derivedStateOf {
                !isLyricsPage()
            }
        }

        val useBackground = remember(album()) {
            derivedStateOf {
                album() == null
            }
        }

        if (backgroundEffectEnabled) {
            val lastOption = remember("SpatialFloatingLight_lastOption") {
                mutableStateOf(Option.Init.name)
            }
            SpatialWrapper {
                val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
                val active = lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED) && !showMiniPlayer()

                AndroidView(
                    factory = { ctx ->
                        KenBurnsView(ctx).apply {
                            setTransitionGenerator(
                                RandomTransitionGenerator(
                                    12000,
                                    AccelerateDecelerateInterpolator()
                                )
                            )
                        }
                    },
                    modifier = modifier.drawWithCache {
                        onDrawBehind {
                            if (useBackground.value) {
                                drawRect(Color.Black)
                            }
                        }
                    }
                ) { view ->
                    if (drawable.value != null) {
                        if (view.drawable != drawable.value) {
                            val thisOptionType = Option.Set.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            view.setImageDrawable(drawable.value!!)
                            lastOption.value = thisOptionType
                        } else if (!isPlaying() || !active) {
                            val thisOptionType = Option.Pause.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            view.pause()
                            lastOption.value = thisOptionType
                        } else {
                            val thisOptionType = Option.Resume.name
                            if (lastOption.value == thisOptionType) return@AndroidView
                            view.resume()
                            lastOption.value = thisOptionType
                        }
                    }
                }
            }
        } else {
            SpatialWrapper {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(data = drawable.value)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithCache {
                            onDrawBehind {
                                if (useBackground.value) {
                                    drawRect(Color.Black)
                                }
                            }
                        }
                )
            }
        }

        // Animated Darkness Overlay for non-lyrics page states
        SpatialWrapper {
            val alpha = animateFloatAsState(
                targetValue = if (lossEffect.value) 0.618f else 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                ),
                label = "lossEffectAlpha"
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(data = drawable.value)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        this.alpha = alpha.value
                    },
                colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.Overlay)
            )
        }
    }
}

/**
 * RenderScript Hardware Blur & Saturation Processing Pipeline.
 * 1. Saturation boosted by 2.0x via ColorMatrix.
 * 2. Overlay tinting applied for text contrast.
 * 3. RenderScript Toolkit.blur (radius 12) executed on downscaled bitmap.
 */
fun imageResolve(image: Bitmap, moreLight: Boolean = false): Bitmap {
    var resizedBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
    resizedBitmap.applyCanvas {
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(2.0f)

        paint.colorFilter = ColorMatrixColorFilter(saturationMatrix)
        drawBitmap(resizedBitmap, 0f, 0f, paint)

        if (moreLight) {
            drawColor((0x1AFFFFFF).toInt())
            drawColor((0xFFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x52FFFFFF).toInt())
            drawColor((0xBFFFFFFF).toInt(), PorterDuff.Mode.OVERLAY)
        } else {
            drawColor((0x33000000).toInt(), PorterDuff.Mode.OVERLAY)
            drawColor((0x40000000).toInt())
        }
    }

    try {
        resizedBitmap = Toolkit.blur(resizedBitmap, 12)
    } catch (_: Exception) {
        // Fallback if NDK binding encounters unexpected hardware constraint
    }
    return resizedBitmap
}
