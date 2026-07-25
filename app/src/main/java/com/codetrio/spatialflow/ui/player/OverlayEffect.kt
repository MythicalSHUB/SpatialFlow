package com.codetrio.spatialflow.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint

/**
 * Applies a BlendMode.Plus (additive light) offscreen layer to its content.
 *
 * Any white pixels in the child composable literally add luminance to whatever
 * is rendered behind them, producing a glowing / frosted-glass quality.
 *
 * ⚠️ ORDER MATTERS:  if you apply `.alpha()` on the same composable, it MUST come
 *    AFTER `.overlayEffect()` in the modifier chain — or use
 *    `.graphicsLayer { this.alpha = … }` instead — otherwise `alpha` creates its
 *    own offscreen layer that breaks the blend mode.
 *
 * Mirrors OverlayEffect.kt from the design spec.
 */
@Composable
fun Modifier.overlayEffect(): Modifier = this.drawWithCache {
    val overlayPaint = Paint().apply {
        blendMode = BlendMode.Plus
    }
    val rect = Rect(0f, 0f, size.width, size.height)

    onDrawWithContent {
        val canvas = this.drawContext.canvas
        // Push an offscreen layer with Plus blend mode
        canvas.saveLayer(rect, overlayPaint)
        drawContent()
        canvas.restore()
    }
}
