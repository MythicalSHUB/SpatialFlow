package com.codetrio.spatialflow.ui.player

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Global singleton that holds the three reactive palette color slots extracted
 * from the current album artwork. Any composable in the app can observe these
 * without re-extracting the palette.
 *
 * Mirrors MediaViewModelObject.palette* from the design spec.
 */
object PlayerPaletteState {
    // Spec §2.1: initialized to Color.Black; updated by LaunchedEffect in FullPlayer
    val vibrantColor: MutableState<Color>      = mutableStateOf(Color.Black)
    val darkVibrantColor: MutableState<Color>  = mutableStateOf(Color.Black)
    val darkMutedColor: MutableState<Color>    = mutableStateOf(Color.Black)
}
