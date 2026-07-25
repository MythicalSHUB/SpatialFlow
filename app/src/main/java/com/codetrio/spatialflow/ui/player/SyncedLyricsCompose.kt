package com.codetrio.spatialflow.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem

/**
 * Unified Synced Lyrics Composable.
 * Uses KaraokeLyricsView for both Karaoke (word-by-word) and standard Line-by-Line LRC lyrics,
 * ensuring 100% visual and animation parity across all modes.
 */
@Composable
internal fun SyncedLyricsCompose(
    onSeekTo: (Int) -> Unit,
    lyrics: List<LyricLine>,
    currentPositionProvider: () -> Int,
    contentColor: Color,
    dynamicAccentColor: Color,
    currentSong: SongItem? = null,
    selectedProvider: String? = null,
    providerResults: Map<String, LyricsResult> = emptyMap(),
    isPlayingProvider: () -> Boolean = { true },
    playbackSpeedProvider: () -> Float = { 1.0f },
    modifier: Modifier = Modifier
) {
    val currentView = LocalView.current
    DisposableEffect(currentView) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    KaraokeLyricsView(
        lyrics = lyrics,
        currentPositionProvider = currentPositionProvider,
        isPlayingProvider = isPlayingProvider,
        playbackSpeedProvider = playbackSpeedProvider,
        onSeekTo = onSeekTo,
        accentColor = dynamicAccentColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        LyricsMetadataFooter(
            currentSong = currentSong,
            selectedProvider = selectedProvider,
            providerResults = providerResults,
            contentColor = contentColor
        )
    }
}

/**
 * Reusable error and retry state composable for lyrics display.
 */
@Composable
internal fun LyricsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = "Retry")
        }
    }
}
