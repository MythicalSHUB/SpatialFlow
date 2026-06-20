@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.codetrio.spatialflow.ui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.ui.player.WavyMusicSlider
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class ExternalPlayerActivity : ComponentActivity() {

    private val TAG = "ExternalPlayerActivity"

    // Converted to MutableState to trigger Compose recomposition upon service binding!
    private var audioService = mutableStateOf<AudioPlaybackService?>(null)
    private var isBound = false
    private var currentUri: Uri? = null

    // Safety flag to prevent shutting down playback when routing to the real app
    private var isTransitioningToFullPlayer = false

    // Metadata States
    private var songTitle = mutableStateOf("External Track")
    private var songArtist = mutableStateOf("Loading...")
    private var songAlbum = mutableStateOf("External Source")
    private var thumbnailBitmap = mutableStateOf<Bitmap?>(null)

    // Service Connection
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioPlaybackService.LocalBinder
            audioService.value = binder?.getService()
            isBound = true
            Log.d(TAG, "Connected to AudioPlaybackService")

            // Start loading the song immediately upon connect
            currentUri?.let { uri ->
                audioService.value?.loadAndPlay(uri)
                updateServiceMetadata()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService.value = null
            isBound = false
        }
    }

    private fun updateServiceMetadata() {
        val title = songTitle.value
        // Broadcast update so player engine caches visual cues
        audioService.value?.setSongMetadataById(title, -1L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Resolve incoming Intent data Uri
        val intentData = intent.data
        if (intentData != null) {
            currentUri = intentData
            loadAllMetadata(intentData) // Combined immediate & background ID3 extraction

            // Start and bind the playback engine service
            val serviceIntent = Intent(this, AudioPlaybackService::class.java)
            try {
                startService(serviceIntent)
                bindService(serviceIntent, connection, BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting or binding AudioPlaybackService", e)
            }
        } else {
            Log.w(TAG, "Activity launched without external intent data. Finishing.")
            finish()
            return
        }

        setContent {
            SpatialFlowTheme(darkTheme = true) { // Sleek immersive popup palette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)) // Standard M3 Scrim token
                        .clickable {
                            // Tapping backdrop exits popup (which calls onDestroy automatically)
                            finish()
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    PopupPlayerContent(
                        uri = currentUri,
                        title = songTitle.value,
                        artist = songArtist.value,
                        album = songAlbum.value,
                        artBitmap = thumbnailBitmap.value,
                        service = audioService.value, // Observed state updates layout when bound!
                        onOpenFullPlayer = {
                            openFullApp(currentUri)
                        },
                        modifier = Modifier.clickable(enabled = false) {} // Absorb internal taps
                    )
                }
            }
        }
    }

    private fun loadAllMetadata(uri: Uri) {
        // 1. Immediate fallback: Resolve display filename so the UI displays text instantly
        var initialTitle = "External Track"
        if ("content" == uri.scheme) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            val resolved = cursor.getString(index)
                            if (!resolved.isNullOrEmpty()) initialTitle = resolved
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Immediate metadata filename query failed", e)
            }
        } else if ("file" == uri.scheme) {
            initialTitle = uri.lastPathSegment ?: "File Audio"
        }

        // Clean track label file extension if existing
        if (initialTitle.contains(".")) {
            val dotIdx = initialTitle.lastIndexOf('.')
            if (dotIdx > 0) initialTitle = initialTitle.substring(0, dotIdx)
        }
        songTitle.value = initialTitle

        // 2. Background Deep Metadata parsing (Artist, Album, real Title overriding filename, Art)
        lifecycleScope.launch(Dispatchers.IO) {
            var mmr: MediaMetadataRetriever? = null
            try {
                mmr = MediaMetadataRetriever()
                mmr.setDataSource(this@ExternalPlayerActivity, uri)

                val realTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val realArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val realAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val artBytes = mmr.embeddedPicture

                var bitmap: Bitmap? = null
                if (artBytes != null) {
                    bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }

                withContext(Dispatchers.Main) {
                    // Apply ID3 tag title if embedded, else retain filename
                    if (!realTitle.isNullOrBlank()) {
                        songTitle.value = realTitle
                    }
                    songArtist.value = if (!realArtist.isNullOrBlank()) realArtist else "Unknown Artist"
                    songAlbum.value = if (!realAlbum.isNullOrBlank()) realAlbum else "Unknown Album"

                    if (bitmap != null) {
                        thumbnailBitmap.value = bitmap
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed running deep ID3 metadata background extractor", e)
                withContext(Dispatchers.Main) {
                    songArtist.value = "External Source"
                    songAlbum.value = "Unknown Album"
                }
            } finally {
                try {
                    mmr?.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun openFullApp(uri: Uri?) {
        isTransitioningToFullPlayer = true // Safe exit condition: skip background kill routine
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // CRITICAL FIX: Grant explicit temporary read permissions on forward
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            action = Intent.ACTION_VIEW
            data = uri
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!isTransitioningToFullPlayer) {
            // Scenario: User closed the bottom sheet (tap backdrop/back button)
            Log.d(TAG, "Terminating all app contexts following direct popup dismissal")

            // 1. Stop service, abandon audio focus, kill playback engine
            try {
                audioService.value?.stop()
                val serviceIntent = Intent(this, AudioPlaybackService::class.java)
                stopService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed tearing down AudioPlaybackService during shutdown", e)
            }

            // 2. Release standard binding
            if (isBound) {
                try {
                    unbindService(connection)
                } catch (_: Exception) {}
                isBound = false
            }

            // 3. Evict activity from recents history completely
            finishAndRemoveTask()

            // 4. Force absolute process termination to satisfy complete containment
            android.os.Process.killProcess(android.os.Process.myPid())
        } else {
            // Scenario: Tapping "Open full player" - keep service alive for main shell!
            if (isBound) {
                try {
                    unbindService(connection)
                } catch (_: Exception) {}
                isBound = false
            }
        }
    }
}

@Composable
fun PopupPlayerContent(
    uri: Uri?,
    title: String,
    artist: String,
    album: String,
    artBitmap: Bitmap?,
    service: AudioPlaybackService?,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(service) { service?.getPlayerInstance() }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderScrubPos by remember { mutableFloatStateOf(0f) }

    // Attach core listeners to ExoPlayer to sync UI triggers
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}

        isPlaying = player.isPlaying
        currentPos = player.currentPosition
        duration = if (player.duration > 0) player.duration else 0L

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = if (player.duration > 0) player.duration else 0L
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Background tracking loop for live timeline scrubbing updates
    LaunchedEffect(player, isPlaying, isScrubbing) {
        if (player != null && isPlaying && !isScrubbing) {
            while (true) {
                currentPos = player.currentPosition
                duration = if (player.duration > 0) player.duration else 0L
                delay(250.milliseconds)
            }
        }
    }

    // Raised Material Container matching visual specs
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Seamless top pill handler
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info Segment (Scraped Bitmap Art and Dynamic Meta Labels)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Highly polished Rounded Thumbnail Art
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (artBitmap != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(artBitmap)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback visual context if ID3 is completely missing
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri) // Backstop fallback to raw parser
                                .crossfade(true)
                                .build(),
                            contentDescription = "Album Art Fallback",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Dynamic Metadata Titles (Pulled directly from embedded audio ID3 tags)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2, // Spans up to 2 lines for complete visibility
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scrub timeline: Layered animated WavyProgress overlay with an invisible tactile Slider
            val safeDur = if (duration > 0) duration else 1L
            val displayPos = if (isScrubbing) (sliderScrubPos * safeDur).toLong() else currentPos
            val progressRatio = (currentPos.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)

            WavyMusicSlider(
                value = if (isScrubbing) sliderScrubPos else progressRatio,
                onValueChange = {
                    isScrubbing = true
                    sliderScrubPos = it
                },
                onValueChangeFinished = {
                    isScrubbing = false
                    player?.seekTo((sliderScrubPos * safeDur).toLong())
                },
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                thumbColor = MaterialTheme.colorScheme.onSurface,
                isPlaying = isPlaying,
                trackHeight = 4.dp,
                thumbRadius = 6.dp,
                waveAmplitudeWhenPlaying = 6.dp,
                waveLength = 48.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // Timestamps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(displayPos),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Prominent Full-Width Play/Pause Action utilizing Native M3 visual squish
            ExpressivePlayPauseButton(
                isPlaying = isPlaying,
                onClick = {
                    if (player != null) {
                        if (isPlaying) service?.pause() else service?.play()
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Full-Player Gateway
            ExpressiveActionButton(
                text = "Open full player",
                onClick = onOpenFullPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }
    }
}

// Native Material 3 Expressive component adapters

@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Native Material 3 Button handles all expressive container-squish and press 
    // physics automatically. Removing manual graphicsLayer prevents interference!
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth() // Upgraded to full width to cleanly align with popup content
            .height(72.dp), // Curated premium height
        shape = MaterialTheme.shapes.extraLarge, // Official curated Squircle Token
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "Play/Pause",
            modifier = Modifier.size(36.dp) // Prominent icon size
        )
    }
}

@Composable
fun ExpressiveActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ExtendedFloatingActionButton offers state-layer and hover transitions natively
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}