package com.codetrio.spatialflow.ui.player

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

data class ExtractedPaletteColors(
    val dominant: Color,
    val vibrant: Color,
    val darkMuted: Color,
    val lightVibrant: Color,
    val accent: Color
) {
    companion object {
        val Default = ExtractedPaletteColors(
            dominant = Color(0xFF121212),
            vibrant = Color(0xFF8338EC),
            darkMuted = Color(0xFF0F0F1A),
            lightVibrant = Color(0xFFD8B4FE),
            accent = Color(0xFF3A86FF)
        )
    }
}

/**
 * Extracts and caches rich ambient palette colors from album art bitmaps using AndroidX Palette.
 */
object PlayerColorExtractor {

    private val cache = LruCache<String, ExtractedPaletteColors>(30)

    fun extractFromBitmap(key: String?, bitmap: Bitmap?): ExtractedPaletteColors {
        if (key != null) {
            cache.get(key)?.let { return it }
        }
        if (bitmap == null) return ExtractedPaletteColors.Default

        val palette = Palette.from(bitmap).generate()

        val dominantInt = palette.getDominantColor(0xFF121212.toInt())
        val vibrantInt = palette.getVibrantColor(palette.getLightVibrantColor(0xFF8338EC.toInt()))
        val darkMutedInt = palette.getDarkMutedColor(palette.getDarkVibrantColor(0xFF0F0F1A.toInt()))
        val lightVibrantInt = palette.getLightVibrantColor(palette.getVibrantColor(0xFFD8B4FE.toInt()))
        val accentInt = palette.getVibrantColor(palette.getDominantColor(0xFF3A86FF.toInt()))

        val colors = ExtractedPaletteColors(
            dominant = Color(dominantInt),
            vibrant = Color(vibrantInt),
            darkMuted = Color(darkMutedInt),
            lightVibrant = Color(lightVibrantInt),
            accent = Color(accentInt)
        )

        if (key != null) {
            cache.put(key, colors)
        }

        return colors
    }
}
