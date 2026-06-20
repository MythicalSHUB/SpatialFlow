@file:Suppress("UNCHECKED_CAST")

package com.codetrio.spatialflow.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowCompat.getInsetsController

@Composable
fun <T> SharedPreferences.observeKey(key: String, defaultValue: T): State<T> {
    val state = remember { mutableStateOf(defaultValue) }
    DisposableEffect(this, key) {
        state.value = when (defaultValue) {
            is Boolean -> getBoolean(key, defaultValue) as T
            is String -> getString(key, defaultValue) as T
            is Float -> getFloat(key, defaultValue) as T
            is Int -> getInt(key, defaultValue) as T
            is Long -> getLong(key, defaultValue) as T
            else -> defaultValue
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
            if (changedKey == key) {
                state.value = when (defaultValue) {
                    is Boolean -> prefs.getBoolean(key, defaultValue) as T
                    is String -> prefs.getString(key, defaultValue) as T
                    is Float -> prefs.getFloat(key, defaultValue) as T
                    is Int -> prefs.getInt(key, defaultValue) as T
                    is Long -> prefs.getLong(key, defaultValue) as T
                    else -> defaultValue
                }
            }
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

/**
 * SpatialFlow's root composable theme.
 *
 * @param darkTheme       Whether to use dark mode colors.
 * @param content         The composable content.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpatialFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicAlbumColor: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val amoledEnabled by prefs.observeKey("amoled_black", false)
    val albumArtThemeEnabled by prefs.observeKey("dynamic_album_theme", false)

    val colorScheme = remember(darkTheme, amoledEnabled, albumArtThemeEnabled, dynamicAlbumColor) {
        val baseScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) darkColorScheme(
                primary = Primary,
                onPrimary = OnPrimary,
                primaryContainer = PrimaryContainer,
                onPrimaryContainer = OnPrimaryContainer,
                background = Surface,
                onBackground = OnSurface,
                surface = Surface,
                onSurface = OnSurface,
                surfaceVariant = SurfaceVariant,
                onSurfaceVariant = OnSurfaceVariant
            )
            else lightColorScheme()
        }

        val finalScheme = if (darkTheme && amoledEnabled && !(albumArtThemeEnabled && dynamicAlbumColor != null)) {
            baseScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerHigh = Color(0xFF0D0D0D),
                surfaceContainerHighest = Color(0xFF141414),
                surfaceContainerLow = Color.Black,
                surfaceContainerLowest = Color.Black
            )
        } else {
            baseScheme
        }

        if (albumArtThemeEnabled && dynamicAlbumColor != null) {
            val seedColor = Color(dynamicAlbumColor)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(seedColor.toArgb(), hsl)
            
            val isMonochrome = hsl[1] < 0.06f
            val baseHue = hsl[0]
            val baseSat = if (isMonochrome) 0f else hsl[1]
            
            // Saturation calculations that respect monochrome
            val primarySat = if (isMonochrome) 0f else baseSat.coerceAtLeast(0.5f)
            val primaryContainerSat = if (isMonochrome) 0f else baseSat.coerceAtLeast(0.4f)
            val secondarySat = if (isMonochrome) 0f else (baseSat * 0.5f).coerceIn(0.2f, 0.35f)
            val secondaryContainerSat = if (isMonochrome) 0f else (baseSat * 0.5f).coerceIn(0.15f, 0.30f)
            val tertiarySat = if (isMonochrome) 0f else (baseSat * 0.6f).coerceIn(0.3f, 0.5f)
            val tertiaryContainerSat = if (isMonochrome) 0f else (baseSat * 0.6f).coerceIn(0.25f, 0.45f)
            
            fun colorAt(h: Float, s: Float, l: Float): Color {
                return Color(
                    androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, s, l))
                )
            }

            val finalDynamicScheme = if (darkTheme) {
                // Dark Theme Tones
                val bgSat = (baseSat * 0.15f).coerceAtMost(0.1f)
                
                finalScheme.copy(
                    background = colorAt(baseHue, bgSat, 0.04f),
                    onBackground = colorAt(baseHue, bgSat, 0.90f),
                    
                    surface = colorAt(baseHue, bgSat, 0.06f),
                    onSurface = colorAt(baseHue, bgSat, 0.90f),
                    
                    surfaceContainerLowest = colorAt(baseHue, bgSat, 0.02f),
                    surfaceContainerLow = colorAt(baseHue, bgSat, 0.08f),
                    surfaceContainer = colorAt(baseHue, bgSat, 0.12f),
                    surfaceContainerHigh = colorAt(baseHue, bgSat, 0.16f),
                    surfaceContainerHighest = colorAt(baseHue, bgSat, 0.22f),
                    
                    surfaceVariant = colorAt(baseHue, bgSat, 0.28f),
                    onSurfaceVariant = colorAt(baseHue, bgSat, 0.80f),
                    
                    primary = colorAt(baseHue, primarySat, 0.75f),
                    onPrimary = colorAt(baseHue, primarySat, 0.20f),
                    primaryContainer = colorAt(baseHue, primaryContainerSat, 0.25f),
                    onPrimaryContainer = colorAt(baseHue, primaryContainerSat, 0.90f),
                    
                    secondary = colorAt(baseHue, secondarySat, 0.70f),
                    onSecondary = colorAt(baseHue, secondarySat, 0.20f),
                    secondaryContainer = colorAt(baseHue, secondaryContainerSat, 0.20f),
                    onSecondaryContainer = colorAt(baseHue, secondaryContainerSat, 0.85f),
                    
                    tertiary = colorAt((baseHue + 60f) % 360f, tertiarySat, 0.70f),
                    onTertiary = colorAt((baseHue + 60f) % 360f, tertiarySat, 0.20f),
                    tertiaryContainer = colorAt((baseHue + 60f) % 360f, tertiaryContainerSat, 0.20f),
                    onTertiaryContainer = colorAt((baseHue + 60f) % 360f, tertiaryContainerSat, 0.85f),
                    
                    outline = colorAt(baseHue, bgSat, 0.60f),
                    outlineVariant = colorAt(baseHue, bgSat, 0.30f),
                    
                    error = colorAt(0f, 0.60f, 0.65f),
                    onError = colorAt(0f, 0.60f, 0.20f),
                    errorContainer = colorAt(0f, 0.60f, 0.25f),
                    onErrorContainer = colorAt(0f, 0.60f, 0.90f)
                )
            } else {
                // Light Theme Tones
                val bgSat = (baseSat * 0.15f).coerceAtMost(0.15f)
                
                finalScheme.copy(
                    background = colorAt(baseHue, bgSat, 0.98f),
                    onBackground = colorAt(baseHue, bgSat, 0.10f),
                    
                    surface = colorAt(baseHue, bgSat, 0.98f),
                    onSurface = colorAt(baseHue, bgSat, 0.10f),
                    
                    surfaceContainerLowest = colorAt(baseHue, bgSat, 1.0f),
                    surfaceContainerLow = colorAt(baseHue, bgSat, 0.96f),
                    surfaceContainer = colorAt(baseHue, bgSat, 0.94f),
                    surfaceContainerHigh = colorAt(baseHue, bgSat, 0.90f),
                    surfaceContainerHighest = colorAt(baseHue, bgSat, 0.86f),
                    
                    surfaceVariant = colorAt(baseHue, bgSat, 0.80f),
                    onSurfaceVariant = colorAt(baseHue, bgSat, 0.30f),
                    
                    primary = colorAt(baseHue, primarySat, 0.40f),
                    onPrimary = colorAt(baseHue, primarySat, 0.95f),
                    primaryContainer = colorAt(baseHue, primaryContainerSat, 0.85f),
                    onPrimaryContainer = colorAt(baseHue, primaryContainerSat, 0.10f),
                    
                    secondary = colorAt(baseHue, secondarySat, 0.45f),
                    onSecondary = colorAt(baseHue, secondarySat, 0.95f),
                    secondaryContainer = colorAt(baseHue, secondaryContainerSat, 0.85f),
                    onSecondaryContainer = colorAt(baseHue, secondaryContainerSat, 0.10f),
                    
                    tertiary = colorAt((baseHue + 60f) % 360f, tertiarySat, 0.45f),
                    onTertiary = colorAt((baseHue + 60f) % 360f, tertiarySat, 0.95f),
                    tertiaryContainer = colorAt((baseHue + 60f) % 360f, tertiaryContainerSat, 0.85f),
                    onTertiaryContainer = colorAt((baseHue + 60f) % 360f, tertiaryContainerSat, 0.10f),
                    
                    outline = colorAt(baseHue, bgSat, 0.50f),
                    outlineVariant = colorAt(baseHue, bgSat, 0.80f),
                    
                    error = colorAt(0f, 0.60f, 0.45f),
                    onError = colorAt(0f, 0.60f, 0.95f),
                    errorContainer = colorAt(0f, 0.60f, 0.85f),
                    onErrorContainer = colorAt(0f, 0.60f, 0.10f)
                )
            }
            
            // AMOLED black override for dynamic theme
            if (darkTheme && amoledEnabled) {
                finalDynamicScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color.Black,
                    surfaceContainer = Color.Black
                )
            } else {
                finalDynamicScheme
            }
        } else {
            finalScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            activity?.window?.let { window ->
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}



internal tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}