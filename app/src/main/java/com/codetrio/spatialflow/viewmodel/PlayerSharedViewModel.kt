package com.codetrio.spatialflow.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.db.HistoryEventEntity
import com.codetrio.spatialflow.data.db.PlaylistDao
import com.codetrio.spatialflow.data.db.PlaylistEntity
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.data.innertube.path
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.model.toPlaylistSongEntity
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.util.FavoritesManager
import com.codetrio.spatialflow.util.PlaybackStateManager
import com.codetrio.spatialflow.util.PlayerHapticManager
import com.codetrio.spatialflow.util.SongDownloader
import com.codetrio.spatialflow.viewmodel.lyrics.PlayerLyricsStateController
import com.google.gson.JsonElement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.Random
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Premium Kotlin-first ViewModel for SpatialFlow.
 * Migrated from legacy Java/LiveData architecture to StateFlow + Coroutines.
 * Optimized for Material 3 Expressive and Media3 integration.
 */
@HiltViewModel
class PlayerSharedViewModel @Inject constructor(
    private val application: Application,
    private val playlistDao: PlaylistDao
) : AndroidViewModel(application) {
    
    private val appContext: Context get() = application.applicationContext

    private val _streamingQuality = MutableStateFlow(
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .getString("audio_quality", "High") ?: "High"
    )
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _playbackFormat = MutableStateFlow("OPUS")
    val playbackFormat: StateFlow<String> = _playbackFormat.asStateFlow()

    fun updatePlaybackFormat(format: String) {
        if (_playbackFormat.value != format) {
            _playbackFormat.value = format
        }
    }



    val bgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    fun cleanupBgScope() {
        bgScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // Local custom playlists backing flow (OuterTune style)
    val localPlaylistsFlow = playlistDao.getAllPlaylists().stateIn(
        bgScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // ========== PERSONAL LISTENING ANALYTICS & RECAP MODELS ==========

    data class TopSongStat(
        val songId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String?,
        val count: Int
    )

    data class TopArtistStat(
        val artist: String,
        val count: Int,
        val thumbnailUrl: String?
    )

    data class ListeningRecap(
        val totalPlayCount: Int,
        val totalMinutes: Long,
        val topSongs: List<TopSongStat>,
        val topArtists: List<TopArtistStat>,
        val peakMood: String,
        val peakMoodDescription: String
    )

    // Reactive flow that live-aggregates history records
    val listeningRecapFlow = playlistDao.getAllHistoryEventsFlow().map { events ->
        if (events.isEmpty()) return@map null
        
        val totalPlayCount = events.size
        val totalMs = events.sumOf { it.duration }
        val totalMinutes = totalMs / 1000 / 60
        
        val topSongs = events.groupBy { it.songId }
            .map { (songId, list) ->
                val first = list.first()
                TopSongStat(
                    songId = songId,
                    title = first.title,
                    artist = first.artist,
                    thumbnailUrl = first.thumbnailUrl,
                    count = list.size
                )
            }
            .sortedByDescending { it.count }
            .take(10)
            
        val topArtists = events.groupBy { it.artist }
            .map { (artist, list) ->
                TopArtistStat(
                    artist = artist,
                    count = list.size,
                    thumbnailUrl = list.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.thumbnailUrl
                )
            }
            .sortedByDescending { it.count }
            .take(5)
            
        val hourCounts = events.groupBy { it.hourOfDay }.mapValues { it.value.size }
        val peakHour = hourCounts.maxByOrNull { it.value }?.key ?: 12
        
        val (peakMood, peakMoodDescription) = when (peakHour) {
            in 5..11 -> "Morning Spark" to "You find your energy in morning melodies."
            in 12..16 -> "Afternoon Groove" to "Midday rhythms keep you focused and moving."
            in 17..21 -> "Evening Harmony" to "Winding down with perfect sunset soundtracks."
            else -> "Midnight Mystique" to "Late night is when your true music flow awakens."
        }
        
        ListeningRecap(
            totalPlayCount = totalPlayCount,
            totalMinutes = totalMinutes,
            topSongs = topSongs,
            topArtists = topArtists,
            peakMood = peakMood,
            peakMoodDescription = peakMoodDescription
        )
    }.stateIn(
        bgScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val artistProfileMap = mutableStateMapOf<String, String>()

    fun resolveArtistProfileImage(artistName: String) {
        if (artistProfileMap.containsKey(artistName)) return
        bgScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.search(artistName, SearchFilter.ARTISTS)
                result.onSuccess { searchResult ->
                    val artistItem = searchResult.items.firstOrNull { it is SearchItem.Artist } as? SearchItem.Artist
                    val url = artistItem?.artist?.thumbnailUrl
                    if (!url.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            artistProfileMap[artistName] = url
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerSharedViewModel", "Failed to fetch artist profile image for $artistName: ${e.message}")
            }
        }
    }

    private var trackHistoryJob: Job? = null

    private fun trackHistoryForSong(song: SongItem) {
        trackHistoryJob?.cancel()
        trackHistoryJob = bgScope.launch(Dispatchers.IO) {
            try {
                val prefs = appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                if (prefs.getBoolean("pause_history", false)) {
                    Log.d("PlayerSharedViewModel", "History tracking is paused, skipping local history logging for: ${song.title}")
                    return@launch
                }

                var accumulatedMs = 0L
                var insertedId: Long? = null
                
                while (true) {
                    delay(1000.milliseconds)
                    if (_isPlaying.value) {
                        accumulatedMs += 1000L
                        
                        if (accumulatedMs >= 2000L) {
                            val calendar = Calendar.getInstance()
                            val hour = calendar.get(Calendar.HOUR_OF_DAY)
                            
                            val historyEvent = HistoryEventEntity(
                                eventId = insertedId ?: 0L,
                                songId = song.videoId ?: song.id.toString(),
                                title = song.title,
                                artist = song.artist,
                                duration = accumulatedMs,
                                thumbnailUrl = song.thumbnailUrl ?: song.path,
                                timestamp = System.currentTimeMillis(),
                                hourOfDay = hour
                            )
                            val rowId = playlistDao.insertHistoryEvent(historyEvent)
                            if (insertedId == null) {
                                insertedId = rowId
                                Log.d("PlayerSharedViewModel", "Successfully logged initial play event for: ${song.title}")
                            } else {
                                Log.d("PlayerSharedViewModel", "Updated real-time listening duration for ${song.title} to: ${accumulatedMs / 1000}s")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerSharedViewModel", "Error logging history: ${e.message}")
            }
        }
    }

    fun createLocalPlaylist(title: String) {
        bgScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(PlaylistEntity(title = title))
        }
    }

    fun deleteLocalPlaylist(playlistId: Long) {
        bgScope.launch(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist != null) {
                playlistDao.deletePlaylist(playlist)
                playlistDao.deleteAllSongsInPlaylist(playlistId)
            }
        }
    }

    fun addSongToLocalPlaylist(playlistId: Long, song: SongItem) {
        bgScope.launch(Dispatchers.IO) {
            val count = playlistDao.getSongCountForPlaylistSync(playlistId)
            val entity = song.toPlaylistSongEntity(playlistId, count)
            playlistDao.insertPlaylistSong(entity)
        }
    }

    fun removeSongFromLocalPlaylist(playlistId: Long, songId: String) {
        bgScope.launch(Dispatchers.IO) {
            playlistDao.deletePlaylistSong(playlistId, songId)
        }
    }

    fun getSongsForLocalPlaylist(playlistId: Long) = playlistDao.getSongsForPlaylist(playlistId)



    companion object {
        private const val TAG = "PlayerSharedViewModel"
        
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }

    private val shuffleRandom = Random()
    private var favoritesManager: FavoritesManager? = null
    @SuppressLint("StaticFieldLeak")
    var audioService: AudioPlaybackService? = null
        set(value) {
            field = value
            _audioServiceState.value = value
            value?.setViewModel(this)
            
            if (value != null) {
                val activeMediaItem = value.player.currentMediaItem
                if (activeMediaItem != null) {
                    val activeSongId = activeMediaItem.mediaId.toLongOrNull()
                    if (activeSongId != null) {
                        val matchedSong = findSongById(activeSongId)
                        if (matchedSong != null && _currentSong.value != matchedSong) {
                            Log.d(TAG, "Service is already playing song: ${matchedSong.title}. Restoring view model state.")
                            _currentSongIndex.value = findSongIndexById(activeSongId)
                            _currentSong.value = matchedSong
                            _songUri.value = matchedSong.contentUri
                            _isPlaybackReady.value = true
                        }
                    }
                } else {
                    // Cold-start warm preloading: when the service connects, immediately
                    // load the restored playback song so pressing Play works instantly
                    val restoredSong = _currentSong.value
                    val restoredUri = _songUri.value
                    val restoredPos = _currentPosition.value.toLong()
                    if (restoredSong != null && restoredUri != null) {
                        if (value.getCurrentSourceUri() != restoredUri) {
                            Log.d(TAG, "Cold-start pre-loading restored song: ${restoredSong.title} at pos $restoredPos")
                            value.loadAudio(restoredUri, restoredPos)
                            if (restoredSong.thumbnailUrl != null) {
                                value.setSongMetadataByUrl(restoredSong.title, restoredSong.thumbnailUrl)
                            } else {
                                value.setSongMetadataById(restoredSong.title, restoredSong.albumId)
                            }
                        } else {
                            Log.d(TAG, "Skipping cold-start pre-loading: song is already active in service")
                        }
                    }
                }
            }
        }

    fun restoreStateFrom(oldVm: PlayerSharedViewModel) {
        Log.d(TAG, "Restoring UI state from old ViewModel instance to prevent memory leak and state wipe.")
        _songList.value = oldVm._songList.value
        _localSongs.value = oldVm._localSongs.value
        _currentSongIndex.value = oldVm._currentSongIndex.value
        _currentSong.value = oldVm._currentSong.value
        _songUri.value = oldVm._songUri.value
        _isPlaying.value = oldVm._isPlaying.value
        _currentPosition.value = oldVm._currentPosition.value
        _duration.value = oldVm._duration.value
        _isShuffleEnabled.value = oldVm._isShuffleEnabled.value
        _repeatMode.value = oldVm._repeatMode.value
        _isPlayerExpanded.value = oldVm._isPlayerExpanded.value
        _sleepTimerEndTime.value = oldVm._sleepTimerEndTime.value
        _sleepTimerMode.value = oldVm._sleepTimerMode.value
        lyricsController.setLyricsModeEnabled(oldVm.lyricsController.isLyricsModeEnabled.value)
        
        // Preserve any active sleep timer jobs
        this.sleepTimerJob = oldVm.sleepTimerJob
        
        // Ensure UI stays in sync with ExoPlayer if active
        _isPlaybackReady.value = oldVm._isPlaybackReady.value
    }
    var hapticManager: PlayerHapticManager? = null

    // ========== STATEFLOW DEFINITIONS ==========

    private val _songUri = MutableStateFlow<Uri?>(null)
    val songUri: StateFlow<Uri?> = _songUri.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0)
    val processingProgress: StateFlow<Int> = _processingProgress.asStateFlow()

    // Song Library Management
    private val _songList = MutableStateFlow<List<SongItem>>(emptyList())
    val songList: StateFlow<List<SongItem>> = _songList.asStateFlow()

    private val _isQueueExpanded = MutableStateFlow(false)
    val isQueueExpanded: StateFlow<Boolean> = _isQueueExpanded.asStateFlow()
    fun setQueueExpanded(expanded: Boolean) {
        _isQueueExpanded.value = expanded
    }

    private val _localSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val localSongs: StateFlow<List<SongItem>> = _localSongs.asStateFlow()

    // Dynamic blend color extracted from album art — drives the activity-level mini player background
    private val _miniPlayerBlendColor = MutableStateFlow(0)
    val miniPlayerBlendColor: StateFlow<Int> = _miniPlayerBlendColor.asStateFlow()

    private val _bottomNavTranslationY = MutableStateFlow(0f)
    val bottomNavTranslationY: StateFlow<Float> = _bottomNavTranslationY.asStateFlow()
    fun setBottomNavTranslationY(translation: Float) {
        _bottomNavTranslationY.value = translation
    }

    private val _bottomNavHeight = MutableStateFlow(0f)
    val bottomNavHeight: StateFlow<Float> = _bottomNavHeight.asStateFlow()
    fun setBottomNavHeight(height: Float) {
        _bottomNavHeight.value = height
    }

    private val _playerBackgroundColor = MutableStateFlow(0xFF0F0F0F.toInt())
    val playerBackgroundColor: StateFlow<Int> = _playerBackgroundColor.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _currentSongArtwork = MutableStateFlow<ByteArray?>(null)

    private val _canvasArtwork = MutableStateFlow<com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork?>(null)
    val canvasArtwork: StateFlow<com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork?> = _canvasArtwork.asStateFlow()

    private val _shouldPromptEffects = MutableStateFlow(false)

    private val _effectsRefreshTrigger = MutableStateFlow(false)


    // Lyrics State (Delegated to Controller)
    val lyricsController = PlayerLyricsStateController(TAG)

    // Effects settings
    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    private val _isReverbEnabled = MutableStateFlow(false)
    val isReverbEnabled: StateFlow<Boolean> = _isReverbEnabled.asStateFlow()

    private val _reverbPreset = MutableStateFlow<Short>(0)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()

    private val _reverbIntensity = MutableStateFlow(1.0f)
    val reverbIntensity: StateFlow<Float> = _reverbIntensity.asStateFlow()
    val reverbLevel: StateFlow<Float> = _reverbIntensity.asStateFlow()

    private val _speed8D = MutableStateFlow(0.2f)
    val speed8D: StateFlow<Float> = _speed8D.asStateFlow()

    // 5-Band Equalizer
    private val _isEqualizerEnabled = MutableStateFlow(false)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _eqBand1 = MutableStateFlow(0) // 60Hz
    private val _eqBand2 = MutableStateFlow(0) // 230Hz
    private val _eqBand3 = MutableStateFlow(0) // 910Hz
    private val _eqBand4 = MutableStateFlow(0) // 3600Hz
    private val _eqBand5 = MutableStateFlow(0) // 14000Hz

    val eqBand1: StateFlow<Int> = _eqBand1.asStateFlow()
    val eqBand2: StateFlow<Int> = _eqBand2.asStateFlow()
    val eqBand3: StateFlow<Int> = _eqBand3.asStateFlow()
    val eqBand4: StateFlow<Int> = _eqBand4.asStateFlow()
    val eqBand5: StateFlow<Int> = _eqBand5.asStateFlow()

    // Loudness Enhancer
    private val _isLoudnessEnabled = MutableStateFlow(false)
    val isLoudnessEnabled: StateFlow<Boolean> = _isLoudnessEnabled.asStateFlow()

    private val _loudnessGain = MutableStateFlow(0) // 0-12 dB
    val loudnessGain: StateFlow<Int> = _loudnessGain.asStateFlow()

    // Balance (L/R)
    private val _balance = MutableStateFlow(0f) // -50.0 to +50.0
    val balance: StateFlow<Float> = _balance.asStateFlow()

    // Playback Speed
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isPitchMatched = MutableStateFlow(true)
    val isPitchMatched: StateFlow<Boolean> = _isPitchMatched.asStateFlow()

    // Shuffle & Repeat
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(REPEAT_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _favoriteSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteSongIds: StateFlow<Set<Long>> = _favoriteSongIds.asStateFlow()

    // Favorites
    private val _isCurrentSongFavorite = MutableStateFlow(false)
    val isCurrentSongFavorite: StateFlow<Boolean> = _isCurrentSongFavorite.asStateFlow()

    private val _isCurrentSongDisliked = MutableStateFlow(false)
    val isCurrentSongDisliked: StateFlow<Boolean> = _isCurrentSongDisliked.asStateFlow()

    // Haptics
    private val _isHapticsEnabled = MutableStateFlow(appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).getBoolean("haptics_enabled", false))
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    // Service Reference
    private val _audioServiceState = MutableStateFlow<AudioPlaybackService?>(null)
    val audioServiceState: StateFlow<AudioPlaybackService?> = _audioServiceState.asStateFlow()

    private val _likesCount = MutableStateFlow("Like")
    val likesCount: StateFlow<String> = _likesCount.asStateFlow()
    val likesCountFlow get() = likesCount

    private val _showSignInDialog = MutableStateFlow(false)
    val showSignInDialog: StateFlow<Boolean> = _showSignInDialog.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    private val _playerExpansionFraction = MutableStateFlow(0f)
    val playerExpansionFraction: StateFlow<Float> = _playerExpansionFraction.asStateFlow()

    fun setPlayerExpansionFraction(fraction: Float) {
        _playerExpansionFraction.value = fraction
    }

    private val _isPlaybackReady = MutableStateFlow(false)
    val isPlaybackReady: StateFlow<Boolean> = _isPlaybackReady.asStateFlow()

    fun setPlaybackReady(ready: Boolean) {
        _isPlaybackReady.value = ready
    }

    fun dismissSignInDialog() {
        _showSignInDialog.value = false
    }

    // ========== SLEEP TIMER SYSTEM ==========
    enum class SleepTimerMode {
        OFF,
        END_OF_SONG,
        END_OF_QUEUE,
        CUSTOM
    }

    private val _sleepTimerMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepTimerMode: StateFlow<SleepTimerMode> = _sleepTimerMode.asStateFlow()

    private val _sleepTimerEndTime = MutableStateFlow(0L)
    val sleepTimerEndTime: StateFlow<Long> = _sleepTimerEndTime.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setSleepTimerMode(mode: SleepTimerMode) {
        _sleepTimerMode.value = mode
        if (mode != SleepTimerMode.CUSTOM) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            _sleepTimerEndTime.value = 0L
        }
    }

    fun startCustomSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMode.value = SleepTimerMode.CUSTOM
        val endTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        _sleepTimerEndTime.value = endTime

        sleepTimerJob = bgScope.launch(Dispatchers.Main) {
            while (System.currentTimeMillis() < endTime) {
                delay(1000.milliseconds)
            }
            if (_sleepTimerMode.value == SleepTimerMode.CUSTOM) {
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimerMode(SleepTimerMode.OFF)
    }

    // Dynamic likes count tracking
    private var baseLikesCountInt = 0

    private fun parseLikesCount(likesStr: String): Int {
        var str = likesStr.uppercase().trim().replace(",", "").replace(".", "")
        // Bail early on known non-numeric YouTube strings
        if (str.isBlank() || str == "LIKE" || str == "LIKED" ||
            str.contains("NO LIKE") || str.contains("HIDDEN") ||
            !str.any(Char::isDigit)) return 0

        var multiplier = 1
        when {
            str.endsWith("B") -> { multiplier = 1_000_000_000; str = str.removeSuffix("B") }
            str.endsWith("M") -> { multiplier = 1_000_000;     str = str.removeSuffix("M") }
            str.endsWith("K") -> { multiplier = 1_000;         str = str.removeSuffix("K") }
        }
        return ((str.toDoubleOrNull() ?: return 0) * multiplier).toInt()
    }

    private fun formatLikesCount(count: Int): String {
        if (count <= 0) return "Like"
        if (count < 10_000) { // Under 10,000 likes, show the exact count with commas for interactive increment visibility (e.g. 1,234 -> 1,235)
            return String.format(Locale.US, "%,d", count)
        }
        if (count < 1_000_000) {
            val k = count / 1_000.0
            return String.format(Locale.US, "%.1fK", k).replace(".0K", "K")
        }
        val m = count / 1_000_000.0
        return String.format(Locale.US, "%.1fM", m).replace(".0M", "M")
    }

    private fun updateLikesCountDisplay(userLiked: Boolean) {
        if (baseLikesCountInt <= 0) {
            _likesCount.value = if (userLiked) "1" else "Like"
            return
        }
        val targetCount = if (userLiked) baseLikesCountInt + 1 else baseLikesCountInt
        _likesCount.value = formatLikesCount(targetCount)
    }

    private var engagementJob: Job? = null

    private fun fetchEngagementAndHistoryForSong(song: SongItem) {
        val videoId = song.videoId
        if (videoId.isNullOrEmpty()) {
            baseLikesCountInt = 0
            _likesCount.value = "Like"
            return
        }

        engagementJob?.cancel()
        engagementJob = bgScope.launch(Dispatchers.IO) {
            try {
                // Initialize default state
                withContext(Dispatchers.Main) {
                    _likesCount.value = "Like"
                }
                baseLikesCountInt = 0

                // Fetch player info for likes count and animated thumbnail directly from WebRemix
                val playerJson = com.codetrio.spatialflow.data.innertube.InnerTubeClient.playerWebRemix(videoId)
                val playerResult = com.codetrio.spatialflow.data.innertube.InnerTubeParser.parsePlayerResponse(playerJson, parseStreams = false)

                // ── RESOLVE CANVAS ARTWORK (MOTION ARTWORK) ──
                launch {
                    try {
                        val resolved = com.codetrio.spatialflow.ui.player.canvas.resolveCanvasArtworkForPlayback(
                            mediaId = videoId,
                            songTitleRaw = song.title,
                            artistNameRaw = song.artist,
                            albumTitleRaw = null, // Can be improved if album name is available in SongItem
                            requireVertical = true
                        )
                        if (resolved != null) {
                            Log.d("CanvasArtwork", "Resolved canvas for ${song.title}: ${resolved.preferredVerticalAnimationUrl}")
                            _canvasArtwork.value = resolved
                        }
                    } catch (e: Exception) {
                        Log.e("CanvasArtwork", "Error resolving canvas for ${song.title}: ${e.message}")
                    }
                }

                // Fallback Chain for Real Likes Count
                var likesCountStr = playerResult?.likesCount
                var remoteLikeStatus: Boolean?

                // Always try nextYoutubeWeb endpoint (highly reliable for authenticated like status and guest likes)
                try {
                    val nextYoutubeWebJson = InnerTubeClient.nextYoutubeWeb(videoId)
                    remoteLikeStatus = parseLikeStatusFromNextResponse(nextYoutubeWebJson)
                    if (likesCountStr.isNullOrBlank()) {
                        likesCountStr = parseLikesFromNextResponse(nextYoutubeWebJson)
                        if (!likesCountStr.isNullOrBlank()) {
                            Log.d("LikesFix", "Count resolved from nextYoutubeWeb: $likesCountStr")
                        }
                    }
                    if (remoteLikeStatus != null) {
                        Log.d("LikesFix", "Remote like status resolved: $remoteLikeStatus")
                        withContext(Dispatchers.Main) {
                            favoritesManager?.setFavorite(song.id, remoteLikeStatus)
                            _isCurrentSongFavorite.value = remoteLikeStatus
                        }
                    }
                } catch (e: Exception) {
                    Log.w("LikesFix", "nextYoutubeWeb failed: ${e.message}")
                }

                // Fallback 2: Try nextAndroid endpoint (extremely reliable for mobile engagement/exact likes)
                if (likesCountStr.isNullOrBlank()) {
                    try {
                        val nextAndroidJson = InnerTubeClient.nextAndroid(song.videoId)
                        likesCountStr = parseLikesFromNextResponse(nextAndroidJson)
                        if (!likesCountStr.isNullOrBlank()) {
                            Log.d("LikesFix", "Count resolved from nextAndroid: $likesCountStr")
                        }
                    } catch (e: Exception) {
                        Log.w("LikesFix", "nextAndroid failed: ${e.message}")
                    }
                }

                // Fallback 3: Try next web remix endpoint
                if (likesCountStr.isNullOrBlank()) {
                    try {
                        val nextJson = InnerTubeClient.next(song.videoId)
                        likesCountStr = parseLikesFromNextResponse(nextJson)
                        if (!likesCountStr.isNullOrBlank()) {
                            Log.d("LikesFix", "Count resolved from next(WEB_REMIX): $likesCountStr")
                        }
                    } catch (e: Exception) {
                        Log.w("LikesFix", "next(WEB_REMIX) failed: ${e.message}")
                    }
                }

                if (likesCountStr.isNullOrBlank()) {
                    Log.e("LikesFix", "All tiers failed — no likes count found for videoId=${song.videoId}")
                }

                likesCountStr?.let { count ->
                    baseLikesCountInt = parseLikesCount(count)
                    // Post to Main thread safely to update display Flow
                    withContext(Dispatchers.Main) {
                        updateLikesCountDisplay(_isCurrentSongFavorite.value)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to report playback start or fetch engagement: ${e.message}")
            }
        }
    }

    private fun parseLikeStatusFromNextResponse(json: JsonElement): Boolean? {
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            
            // ── CHECK 0b (NEW): segmentedLikeDislikeButtonViewModel ─────────────
            // Newer ViewComponent-based layout (YouTube 2025+)
            if (obj.has("segmentedLikeDislikeButtonViewModel")) {
                val vm = obj.getAsJsonObject("segmentedLikeDislikeButtonViewModel")
                val lbvm = vm.get("likeButtonViewModel")
                if (lbvm != null && lbvm.isJsonObject) {
                    val lbvmObj = lbvm.asJsonObject
                    // Check for nested toggleButtonViewModel configurations
                    lbvmObj.path("toggleButtonViewModel.toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                    lbvmObj.path("toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                }
            }

            if (obj.has("likeButtonViewModel")) {
                val lbvmObj = obj.getAsJsonObject("likeButtonViewModel")
                lbvmObj.path("toggleButtonViewModel.toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                lbvmObj.path("toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
            }

            // Check for segmentedLikeDislikeButtonRenderer
            if (obj.has("segmentedLikeDislikeButtonRenderer")) {
                val seg = obj.getAsJsonObject("segmentedLikeDislikeButtonRenderer")
                val likeButtonEl = seg.get("likeButton")
                if (likeButtonEl != null && likeButtonEl.isJsonObject) {
                    val inner = likeButtonEl.asJsonObject
                    if (inner.has("toggleButtonRenderer")) {
                        val tbr = inner.getAsJsonObject("toggleButtonRenderer")
                        return tbr.get("isToggled")?.asBoolean ?: false
                    }
                    if (inner.has("likeButtonRenderer")) {
                        val lbr = inner.getAsJsonObject("likeButtonRenderer")
                        val status = lbr.get("likeStatus")?.asString
                        if (status != null) {
                            return status == "LIKE"
                        }
                    }
                }
            }
            
            // Check for toggleButtonRenderer representing LIKE icon
            if (obj.has("toggleButtonRenderer")) {
                val toggleRenderer = obj.getAsJsonObject("toggleButtonRenderer")
                val iconType = toggleRenderer.path("defaultIcon.iconType")?.asString 
                    ?: toggleRenderer.path("defaultIcon.icon.iconType")?.asString
                val isLikeButton = iconType == "LIKE" || iconType?.contains("THUMB", ignoreCase = true) == true || iconType?.contains("FAVORITE", ignoreCase = true) == true
                
                if (isLikeButton) {
                    return toggleRenderer.get("isToggled")?.asBoolean ?: false
                }
            }

            if (obj.has("likeButtonRenderer")) {
                val lbr = obj.getAsJsonObject("likeButtonRenderer")
                val status = lbr.get("likeStatus")?.asString
                if (status != null) {
                    return status == "LIKE"
                }
            }
            
            for (entry in obj.entrySet()) {
                val result = parseLikeStatusFromNextResponse(entry.value)
                if (result != null) return result
            }
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            for (element in arr) {
                val result = parseLikeStatusFromNextResponse(element)
                if (result != null) return result
            }
        }
        return null
    }

    private fun parseLikesFromNextResponse(json: JsonElement): String? {
        if (json.isJsonObject) {
            val obj = json.asJsonObject

            // ── CHECK 0 (NEW): segmentedLikeDislikeButtonRenderer ──────────────
            // Current YouTube /next response format (2024-2025)
            if (obj.has("segmentedLikeDislikeButtonRenderer")) {
                val seg = obj.getAsJsonObject("segmentedLikeDislikeButtonRenderer")
                // Path: likeButton -> likeButtonRenderer -> likeCount / likesCount
                val likeButtonEl = seg.get("likeButton")
                if (likeButtonEl != null && likeButtonEl.isJsonObject) {
                    val inner = likeButtonEl.asJsonObject
                    if (inner.has("likeButtonRenderer")) {
                        val lbr = inner.getAsJsonObject("likeButtonRenderer")
                        val el = lbr.get("likeCount") ?: lbr.get("likesCount")
                        if (el != null && el.isJsonPrimitive) {
                            val s = el.asString
                            if (s.isNotBlank()) return s
                        }
                    }
                }
            }

            // ── CHECK 0b (NEW): segmentedLikeDislikeButtonViewModel ─────────────
            // Newer ViewComponent-based layout (YouTube 2025+)
            if (obj.has("segmentedLikeDislikeButtonViewModel")) {
                val vm = obj.getAsJsonObject("segmentedLikeDislikeButtonViewModel")
                // likeButtonViewModel -> likeCountEntity -> likeCountIfLiked / likeCountIfIndifferent
                val lbvm = vm.get("likeButtonViewModel")
                if (lbvm != null && lbvm.isJsonObject) {
                    val lbvmObj = lbvm.asJsonObject
                    // toggledText holds the "liked" count string
                    lbvmObj.path("toggleButtonViewModel.toggledText.styleText.style1.text.content")
                        ?.asString?.let { if (it.isNotBlank() && it.any(Char::isDigit)) return it }
                    // defaultText holds the unliked count string
                    lbvmObj.path("toggleButtonViewModel.defaultText.styleText.style1.text.content")
                        ?.asString?.let { if (it.isNotBlank() && it.any(Char::isDigit)) return it }
                }
            }
            
            // Check for factoidRenderer representing likes (Highly reliable standard YouTube engagement panel)
            if (obj.has("factoidRenderer")) {
                val factoid = obj.getAsJsonObject("factoidRenderer")
                val label = factoid.path("label.runs.0.text")?.asString 
                    ?: factoid.path("label.simpleText")?.asString
                val isLikesFactoid = label?.contains("like", ignoreCase = true) == true
                if (isLikesFactoid) {
                    factoid.path("value.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    factoid.path("value.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                }
            }

            // Check for likeButtonRenderer
            if (obj.has("likeButtonRenderer")) {
                val likeRenderer = obj.getAsJsonObject("likeButtonRenderer")
                
                val likesCountEl = likeRenderer.get("likesCount") ?: likeRenderer.get("likeCount")
                if (likesCountEl != null) {
                    if (likesCountEl.isJsonPrimitive) {
                        val str = likesCountEl.asString
                        if (str.isNotBlank()) return str
                    } else if (likesCountEl.isJsonObject) {
                        val countObj = likesCountEl.asJsonObject
                        countObj.get("simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                        countObj.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString?.let {
                            if (it.isNotBlank()) return it
                        }
                    }
                }
                
                val likesText = likeRenderer.getAsJsonObject("likesCountText") ?: likeRenderer.getAsJsonObject("likeCountText")
                if (likesText != null) {
                    likesText.get("simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    likesText.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString?.let {
                        if (it.isNotBlank()) return it
                    }
                }
            }
            
            // Check for toggleButtonRenderer representing LIKE icon
            if (obj.has("toggleButtonRenderer")) {
                val toggleRenderer = obj.getAsJsonObject("toggleButtonRenderer")
                val iconType = toggleRenderer.path("defaultIcon.iconType")?.asString 
                    ?: toggleRenderer.path("defaultIcon.icon.iconType")?.asString
                val isLikeButton = iconType == "LIKE" || iconType?.contains("THUMB", ignoreCase = true) == true || iconType?.contains("FAVORITE", ignoreCase = true) == true
                
                if (isLikeButton) {
                    toggleRenderer.path("defaultText.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("defaultText.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("toggledText.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("toggledText.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                }
            }
            
            // Recurse down object entries
            for (entry in obj.entrySet()) {
                val result = parseLikesFromNextResponse(entry.value)
                if (result != null) return result
            }
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            for (element in arr) {
                val result = parseLikesFromNextResponse(element)
                if (result != null) return result
            }
        }
        return null
    }

    private val _isCurrentSongDownloaded = MutableStateFlow(false)
    val isCurrentSongDownloaded: StateFlow<Boolean> = _isCurrentSongDownloaded.asStateFlow()

    private val _currentSongDownloadProgress = MutableStateFlow<Int?>(null)
    val currentSongDownloadProgress: StateFlow<Int?> = _currentSongDownloadProgress.asStateFlow()

    // ========== INITIALIZATION ==========

    private val appSettingsPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "autoplay_enabled") {
            val enabled = sharedPreferences.getBoolean("autoplay_enabled", true)
            if (_isAutoplayEnabled.value != enabled) {
                bgScope.launch(Dispatchers.Main) {
                    setAutoplayEnabled(enabled)
                }
            }
        } else if (key == "audio_quality") {
            _streamingQuality.value = sharedPreferences.getString("audio_quality", "High") ?: "High"
        }
    }

    init {
        com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlaybackCache.init(appContext)
        initFavorites(appContext)
        loadLastPlaybackState()
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(appSettingsPrefListener)
        bgScope.launch(Dispatchers.Main) {
            currentSong.collect { song ->
                _likesCount.value = "Like"
                _isCurrentSongDisliked.value = false
                _canvasArtwork.value = null

                // Reset lyrics state & provider locks on song change
                lyricsController.clearForSongChange(song)

                if (song != null) {
                    playedShuffledIds.add(song.id)
                    checkQueueAndFetchMore()

                    // Automatically fetch lyrics for the active song across all song change scenarios
                    fetchLyricsForCurrentSong()

                    // Load embedded artwork bytes for legacy notification/artwork references
                    val embedded = withContext(Dispatchers.IO) { song.getEmbeddedPicture(appContext) }
                    _currentSongArtwork.value = embedded
                    
                    val colors = extractColorsFromArtwork(song, embedded)
                    _miniPlayerBlendColor.value = colors.first
                    _playerBackgroundColor.value = colors.second

                    // Trigger exact real engagement likes count and watch history fetching
                    fetchEngagementAndHistoryForSong(song)
                    trackHistoryForSong(song)
                } else {
                    _currentSongArtwork.value = null
                    _miniPlayerBlendColor.value = 0
                    _playerBackgroundColor.value = 0xFF0F0F0F.toInt()
                }
            }
        }

        bgScope.launch(Dispatchers.Default) {
            combine(
                currentSong,
                SongDownloader.downloadProgress
            ) { song, progressMap ->
                if (song == null) {
                    false to null
                } else {
                    val videoId = song.videoId
                    if (videoId.isNullOrEmpty()) {
                        true to null
                    } else {
                        val progress = progressMap[videoId]
                        if (progress != null) {
                            false to progress
                        } else {
                            val downloaded = SongDownloader.isSongDownloaded(appContext, song)
                            downloaded to null
                        }
                    }
                }
            }.collect { (downloaded, progress) ->
                _isCurrentSongDownloaded.value = downloaded
                _currentSongDownloadProgress.value = progress
            }
        }
    }

    private suspend fun extractColorsFromArtwork(song: SongItem, embeddedArtwork: ByteArray?): Triple<Int, Int, Int> {
        val artworkSource = embeddedArtwork ?: song.getAlbumArtUri() ?: R.drawable.ic_music_note
        
        val request = ImageRequest.Builder(appContext)
            .data(artworkSource)
            .allowHardware(false) // Hardware Bitmaps are not allowed for Palette extraction!
            .build()
            
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                val result = appContext.imageLoader.execute(request)
                if (result is SuccessResult) {
                    bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Coil failed to load album art for palette: ${e.message}")
            }

            if (bitmap != null) {
                try {
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(24) // More swatches for richer seed selection
                        .generate()
                    
                    // 1. Extract palette swatches and pick the most chromatic + relevant accent
                    val allSwatches = palette.swatches
                    val totalPopulation = allSwatches.sumOf { it.population }.coerceAtLeast(1)
                    val candidateSwatches = listOfNotNull(
                        palette.vibrantSwatch,
                        palette.darkVibrantSwatch,
                        palette.lightVibrantSwatch,
                        palette.mutedSwatch,
                        palette.darkMutedSwatch,
                        palette.lightMutedSwatch,
                        palette.dominantSwatch
                    )
                    val bestAccentSwatch = candidateSwatches.maxByOrNull { swatch ->
                        val sat = swatch.hsl[1]
                        val pop = swatch.population.toFloat() / totalPopulation.toFloat()
                        sat * (0.25f + pop)
                    }
                    val accentColor = bestAccentSwatch?.rgb
                        ?: palette.getVibrantColor(palette.getDominantColor(0xFF8338EC.toInt()))

                    // 2. Extract the dominant color as background seed
                    val baseBgColor = palette.getDominantColor(0xFF0F0F0F.toInt())
                    
                    // 3. Pick the best seed for theme generation (prefer vibrant, fall back to accent)
                    val themeSeed = (palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: bestAccentSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: accentColor)
                    
                    // Convert both to HSL for precise visual enhancements
                    val accentHsl = FloatArray(3)
                    val bgHsl = FloatArray(3)
                    ColorUtils.colorToHSL(accentColor, accentHsl)
                    ColorUtils.colorToHSL(baseBgColor, bgHsl)
                    
                    // Detect monochrome only when palette is truly near-grayscale.
                    val maxChannelDelta = maxOf(
                        abs(Color.red(baseBgColor) - Color.green(baseBgColor)),
                        abs(Color.green(baseBgColor) - Color.blue(baseBgColor)),
                        abs(Color.blue(baseBgColor) - Color.red(baseBgColor))
                    ) / 255f
                    val colorfulPopulation = allSwatches
                        .filter { it.hsl[1] >= 0.16f }
                        .sumOf { it.population }
                    val colorfulRatio = colorfulPopulation.toFloat() / totalPopulation.toFloat()
                    val isMonochromatic = (
                        colorfulRatio < 0.08f &&
                            bgHsl[1] < 0.14f &&
                            accentHsl[1] < 0.18f &&
                            maxChannelDelta < 0.09f
                        ) || (bgHsl[1] < 0.06f && accentHsl[1] < 0.08f)
                    
                    val finalAccent = if (isMonochromatic) {
                        // For grayscale/monochromatic art, apply a matching pure white accent color (saturation = 0)
                        accentHsl[0] = 0f
                        accentHsl[1] = 0f
                        accentHsl[2] = 1f
                        ColorUtils.HSLToColor(accentHsl)
                    } else {
                        // For colorful art, boost the saturation and lightness of the extracted accent color so it pops
                        if (accentHsl[2] < 0.60f) {
                            accentHsl[2] = 0.65f // Elevate brightness for crisp contrast
                        }
                        if (accentHsl[1] < 0.30f) {
                            accentHsl[1] = 0.60f // Inject beautiful saturation to keep it vibrant
                        }
                        ColorUtils.HSLToColor(accentHsl)
                    }
                    
                    val finalBg = if (isMonochromatic) {
                        // For grayscale/monochromatic art, apply a pure neutral gray/charcoal background seed (saturation = 0)
                        bgHsl[0] = 0f
                        bgHsl[1] = 0f
                        bgHsl[2] = 0.30f
                        ColorUtils.HSLToColor(bgHsl)
                    } else {
                        // For colorful art, avoid muddy hue from near-gray dominant colors:
                        // if dominant saturation is low, borrow hue from accent and keep saturation tasteful.
                        if (bgHsl[1] < 0.16f) {
                            bgHsl[0] = accentHsl[0]
                            bgHsl[1] = (accentHsl[1] * 0.36f).coerceIn(0.18f, 0.40f)
                        } else {
                            bgHsl[1] = bgHsl[1].coerceIn(0.18f, 0.60f)
                        }
                        bgHsl[2] = bgHsl[2].coerceIn(0.22f, 0.48f)
                        ColorUtils.HSLToColor(bgHsl)
                    }

                    // Boost theme seed saturation for richer M3 palette generation
                    val seedHsl = FloatArray(3)
                    ColorUtils.colorToHSL(themeSeed, seedHsl)
                    if (!isMonochromatic && seedHsl[1] < 0.35f) {
                        seedHsl[1] = 0.50f
                    }
                    if (!isMonochromatic && seedHsl[2] < 0.30f) {
                        seedHsl[2] = 0.45f
                    }
                    val finalSeed = if (isMonochromatic) {
                        0xFF6750A4.toInt() // Fallback to M3 purple for grayscale art
                    } else {
                        ColorUtils.HSLToColor(seedHsl)
                    }
                    
                    return@withContext Triple(finalAccent, finalBg, finalSeed)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate Palette from bitmap: ${e.message}")
                }
            }

            // Sleek fallback colors (vibrant neon violet and elegant deep charcoal)
            Triple(0xFF8338EC.toInt(), 0xFF0A0B0E.toInt(), 0xFF6750A4.toInt())
        }
    }

    fun initFavorites(context: Context) {
        if (favoritesManager == null) {
            favoritesManager = FavoritesManager(context)
            refreshFavorites()
        }
    }

    fun refreshFavorites() {
        val ids = favoritesManager?.favoriteIds?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        _favoriteSongIds.value = ids
        _currentSong.value?.let { song ->
            val isFav = ids.contains(song.id)
            _isCurrentSongFavorite.value = isFav
            updateLikesCountDisplay(isFav)
        }
    }

    /**
     * Prime the player with its last persisted memory.
     */
    fun loadLastPlaybackState() {
        bgScope.launch(Dispatchers.IO) {
            val lastSong = PlaybackStateManager.getLastSong(appContext) ?: return@launch
            val lastQueue = PlaybackStateManager.getLastQueue(appContext)
            val lastIndex = PlaybackStateManager.getLastIndex(appContext)
            val lastPos = PlaybackStateManager.getLastPosition(appContext)

            Log.d(TAG, "Loading persisted playback state: ${lastSong.title} at pos $lastPos")

            withContext(Dispatchers.Main) {
                if (lastQueue.isNotEmpty()) {
                    val fullIndex = lastIndex.coerceIn(0, lastQueue.lastIndex)
                    val windowStart = (fullIndex - 20).coerceAtLeast(0)
                    val windowEnd = (fullIndex + 50).coerceAtMost(lastQueue.size)
                    val initialChunk = lastQueue.subList(windowStart, windowEnd)

                    _songList.value = ArrayList(initialChunk)
                    _currentSongIndex.value = fullIndex - windowStart

                    bgScope.launch {
                        delay(2000)
                        val currentList = _songList.value.toMutableList()
                        if (currentList.size == initialChunk.size && currentList.zip(initialChunk).all { it.first.id == it.second.id }) {
                            val beforeList = lastQueue.subList(0, windowStart)
                            val afterList = lastQueue.subList(windowEnd, lastQueue.size)

                            val mergedList = mutableListOf<SongItem>()
                            mergedList.addAll(beforeList)
                            mergedList.addAll(currentList)
                            mergedList.addAll(afterList)

                            _songList.value = mergedList
                            _currentSongIndex.value = fullIndex
                            audioService?.refreshNativeQueue()
                            Log.d(TAG, "⚡ Chunky Hydration: Backfilled remaining queue. New size: ${mergedList.size}")
                        }
                    }
                } else {
                    _songList.value = listOf(lastSong)
                    _currentSongIndex.value = 0
                }

                _currentSong.value = lastSong
                _duration.value = lastSong.duration.toInt()
                _currentPosition.value = lastPos.toInt()

                // Prepare lazy resolver pointer
                if (!lastSong.videoId.isNullOrEmpty()) {
                    lastSong.contentUri = "innertube://${lastSong.videoId}".toUri()
                }
                _songUri.value = lastSong.contentUri
            }
        }
    }

    fun savePlaybackState() {
        val song = _currentSong.value ?: return
        val queue = _songList.value
        val idx = _currentSongIndex.value
        val pos = _currentPosition.value

        PlaybackStateManager.saveState(
            appContext,
            song,
            pos.toLong(),
            queue,
            idx
        )
    }

    // ========== SERVICE & MANAGERS ==========


    val isProcessingFlow get() = isProcessing
    val processingProgressFlow get() = processingProgress
    val isEqualizerEnabledFlow get() = isEqualizerEnabled
    val isReverbEnabledFlow get() = isReverbEnabled
    val isLoudnessEnabledFlow get() = isLoudnessEnabled
    val is8DEnabledFlow get() = is8DEnabled
    val reverbPresetFlow get() = reverbPreset
    val reverbIntensityFlow get() = reverbIntensity
    val reverbLevelFlow get() = reverbLevel
    val loudnessGainFlow get() = loudnessGain
    val balanceFlow get() = balance
    val playbackSpeedFlow get() = playbackSpeed
    val isPitchMatchedFlow get() = isPitchMatched
    val eqBand1Flow get() = eqBand1
    val eqBand2Flow get() = eqBand2
    val eqBand3Flow get() = eqBand3
    val eqBand4Flow get() = eqBand4
    val eqBand5Flow get() = eqBand5
    val songListFlow get() = songList
    val currentSongIndexFlow get() = currentSongIndex
    val isCurrentSongFavoriteFlow get() = isCurrentSongFavorite
    
    // Lyrics delegated flows
    val syncedLyrics get() = lyricsController.syncedLyrics
    val plainLyrics get() = lyricsController.plainLyrics
    val isLyricsLoading get() = lyricsController.isLoading
    val lyricsError get() = lyricsController.error
    val isLyricsModeEnabled get() = lyricsController.isLyricsModeEnabled
    val providerResults get() = lyricsController.providerResults
    val selectedProvider get() = lyricsController.selectedProvider

    // ========== PLAYBACK CONTROLS ==========

    fun playAudio() {
        audioService?.play()
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_play_pause")
    }
    fun pauseAudio() {
        audioService?.pause()
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_play_pause")
    }
    fun seekTo(position: Int) = audioService?.seekTo(position)
    fun toggleFavorite(songId: Long) = toggleFavorite()

    fun toggleFavorite() {
        val song = _currentSong.value ?: return
        val manager = favoritesManager ?: return

        // Guard guest likes: Prompts premium Sign In Dialog
        if (!AccountManager.isLoggedIn(appContext)) {
            _showSignInDialog.value = true
            return
        }

        val newState = !manager.isFavorite(song.id)
        manager.setFavorite(song.id, newState)
        _isCurrentSongFavorite.value = newState
        updateLikesCountDisplay(newState)
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_favorite")

        // If we liked it, we cannot have it disliked
        if (newState) {
            _isCurrentSongDisliked.value = false
        }

        // Sync like status to YouTube Music in the background
        if (!song.videoId.isNullOrEmpty()) {
            bgScope.launch(Dispatchers.IO) {
                try {
                    val status = if (newState) {
                        YouTubeMusic.LikeStatus.LIKE
                    } else {
                        YouTubeMusic.LikeStatus.INDIFFERENT
                    }
                    val result = YouTubeMusic.updateLikeStatus(song.videoId!!, status)
                    Log.d(TAG, "Synced like status to YouTube Music for ${song.title}: success=${result.getOrDefault(false)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync like to YouTube Music: ${e.message}")
                }
            }
        }
    }

    fun toggleDislike() {
        val song = _currentSong.value ?: return
        
        // Guard guest dislikes: Prompts premium Sign In Dialog
        if (!AccountManager.isLoggedIn(appContext)) {
            _showSignInDialog.value = true
            return
        }

        val newState = !_isCurrentSongDisliked.value
        _isCurrentSongDisliked.value = newState

        // If we are now disliking, we cannot have it liked
        if (newState && _isCurrentSongFavorite.value) {
            val manager = favoritesManager
            if (manager != null) {
                manager.setFavorite(song.id, false)
                _isCurrentSongFavorite.value = false
                updateLikesCountDisplay(false)
            }
        }

        // Sync dislike status to YouTube Music in the background
        if (!song.videoId.isNullOrEmpty()) {
            bgScope.launch(Dispatchers.IO) {
                try {
                    val status = if (newState) {
                        YouTubeMusic.LikeStatus.DISLIKE
                    } else {
                        YouTubeMusic.LikeStatus.INDIFFERENT
                    }
                    val result = YouTubeMusic.updateLikeStatus(song.videoId!!, status)
                    Log.d(TAG, "Synced dislike status to YouTube Music for ${song.title}: success=${result.getOrDefault(false)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync dislike to YouTube Music: ${e.message}")
                }
            }
        }
    }

    fun toggleLoopMode() {
        val nextMode = when (_repeatMode.value) {
            REPEAT_OFF -> REPEAT_ALL
            REPEAT_ALL -> REPEAT_ONE
            else -> REPEAT_OFF
        }
        _repeatMode.value = nextMode
    }

    fun setRepeatMode(mode: Int) {
        val normalizedMode = when (mode) {
            REPEAT_OFF, REPEAT_ALL, REPEAT_ONE -> mode
            else -> REPEAT_OFF
        }
        if (_repeatMode.value != normalizedMode) {
            _repeatMode.value = normalizedMode
        }
    }

    fun updateSongIndexOnly(index: Int) {
        _currentSongIndex.value = index
        if (index in _songList.value.indices) {
            val song = _songList.value[index]
            _currentSong.value = song
        }
    }
    fun postIsProcessing(processing: Boolean) {
        _isProcessing.value = processing
    }

    fun triggerEffectsRefresh() {
        _effectsRefreshTrigger.value = !_effectsRefreshTrigger.value
    }
    
    fun retryLyrics() = lyricsController.retryLyrics(appContext, _currentSong.value)

    fun fetchLyrics() = lyricsController.fetchLyrics(appContext, _currentSong.value)

    fun fetchLyricsForCurrentSong() = fetchLyrics()



    fun setLyricsModeEnabled(enabled: Boolean) {
        lyricsController.setLyricsModeEnabled(enabled, appContext, _currentSong.value)
    }

    fun selectLyricsProvider(providerName: String) {
        lyricsController.selectProvider(providerName, appContext, _currentSong.value)
    }


    fun setIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun postIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setCurrentPosition(position: Int) {
        _currentPosition.value = position
    }

    fun setDuration(dur: Int) {
        _duration.value = dur
    }

    // ========== AUDIO EFFECTS ==========

    fun set8DEnabled(enabled: Boolean) {
        if (enabled && _isReverbEnabled.value) {
            setReverbEnabled(false)
        }
        _is8DEnabled.value = enabled
        audioService?.set8DEnabled(enabled)
    }

    fun setReverbEnabled(enabled: Boolean) {
        if (enabled && _is8DEnabled.value) {
            set8DEnabled(false)
        }
        _isReverbEnabled.value = enabled
        audioService?.setReverbEnabled(enabled)
        
        if (enabled && _reverbPreset.value.toInt() == 0) {
            setReverbPreset(1)
        }
    }

    fun setReverbPreset(preset: Int) {
        val shortPreset = preset.toShort()
        _reverbPreset.value = shortPreset
        audioService?.setReverbPreset(shortPreset)
    }

    fun setReverbIntensity(intensity: Float) {
        _reverbIntensity.value = intensity.coerceIn(0f, 1f)
        audioService?.setReverbIntensity(intensity)
    }

    fun setReverbLevel(level: Float) {
        setReverbIntensity(level)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        if (audioService != null) {
            audioService?.setEqualizerEnabled(enabled)
            if (enabled && _isLoudnessEnabled.value) {
                if (_loudnessGain.value > 800) setLoudnessGain(600)
            }
        }
    }

    fun setEqBand1(gainDb: Int) { _eqBand1.value = gainDb; audioService?.setEqBandGain(0, gainDb) }
    fun setEqBand2(gainDb: Int) { _eqBand2.value = gainDb; audioService?.setEqBandGain(1, gainDb) }
    fun setEqBand3(gainDb: Int) { _eqBand3.value = gainDb; audioService?.setEqBandGain(2, gainDb) }
    fun setEqBand4(gainDb: Int) { _eqBand4.value = gainDb; audioService?.setEqBandGain(3, gainDb) }
    fun setEqBand5(gainDb: Int) { _eqBand5.value = gainDb; audioService?.setEqBandGain(4, gainDb) }

    fun setLoudnessEnabled(enabled: Boolean) {
        _isLoudnessEnabled.value = enabled
        audioService?.setLoudnessEnabled(enabled)
    }

    fun setLoudnessGain(gain: Int) {
        _loudnessGain.value = gain
        audioService?.setLoudnessGain(gain)
    }

    fun setBalance(balanceValue: Float) {
        _balance.value = balanceValue
        audioService?.setBalance(balanceValue)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        audioService?.setPlaybackSpeed(speed, _isPitchMatched.value)
    }

    fun setPitchMatched(enabled: Boolean) {
        _isPitchMatched.value = enabled
        audioService?.setPlaybackSpeed(_playbackSpeed.value, enabled)
    }

    // ========== LIBRARY MANAGEMENT ==========

    fun setSongList(songs: List<SongItem>) {
        _songList.value = songs
        Log.d(TAG, "Song list updated: ${songs.size} songs")
        
        // Proactive queue warmer
        if (songs.isNotEmpty()) {
            bgScope.launch(Dispatchers.IO) {
                songs.take(3).forEach { song ->
                    song.videoId?.let { YouTubeMusic.prefetchStream(it) }
                }
            }
        }
    }

    fun setLocalSongs(songs: List<SongItem>) {
        _localSongs.value = songs
    }

    fun rescanLocalFiles() {
        bgScope.launch(Dispatchers.IO) {
            val songs = mutableListOf<SongItem>()
            try {
                val proj = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                    android.provider.MediaStore.Audio.Media.ALBUM_ID,
                    android.provider.MediaStore.Audio.Media.DATA,
                    android.provider.MediaStore.Audio.Media.DURATION,
                    android.provider.MediaStore.Audio.Media.DATE_ADDED
                )
                val prefs = appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                val hiddenFolders = (prefs.getString("hidden_folders", "") ?: "").split("||").filter { it.isNotEmpty() }
                val ignoreShort = prefs.getBoolean("ignore_short_audio", false)
                val minDurationMs = if (ignoreShort) prefs.getFloat("ignore_short_audio_duration", 30f).toLong() * 1000L else 0L

                appContext.contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                    val albumCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                    val durCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                    val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol)
                        if (path != null && hiddenFolders.any { path.startsWith(it) }) {
                            continue
                        }
                        val duration = c.getLong(durCol)
                        if (ignoreShort && duration < minDurationMs) {
                            continue
                        }

                        songs.add(
                            SongItem(
                                c.getLong(idCol),
                                c.getString(titleCol),
                                c.getString(artistCol),
                                c.getLong(albumCol),
                                c.getString(dataCol),
                                c.getLong(durCol),
                                c.getLong(dateCol)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerSharedViewModel", "Error scanning local files", e)
            }

            withContext(Dispatchers.Main) {
                setLocalSongs(songs)
            }
        }
    }

    fun setCurrentSong(song: SongItem?) {
        _currentSong.value = song
    }

    fun playSongAtIndex(index: Int) {
        val songs = _songList.value
        if (index !in songs.indices) {
            Log.w(TAG, "Invalid song index: $index")
            return
        }

        val song = songs[index]

        // Force innertube:// scheme for ALL YouTube Music songs to guarantee fresh stream resolution
        if (!song.videoId.isNullOrEmpty()) {
            song.contentUri = "innertube://${song.videoId}".toUri()
            Log.d(TAG, "Injected lazy resolver URI for: ${song.title}")
        }

        // Auto-reset 8D
        if (_currentSong.value?.id != song.id) {
            if (_is8DEnabled.value) {
                _is8DEnabled.value = false
                audioService?.set8DEnabled(false)
            }
        }

        _currentSongIndex.value = index
        _currentSong.value = song
        _songUri.value = song.contentUri
        
        _isPlaybackReady.value = false

        if (hasActiveEffects()) {
            _shouldPromptEffects.value = true
        }

        _isCurrentSongFavorite.value = favoritesManager?.isFavorite(song.id) ?: false
        baseLikesCountInt = 0
        _likesCount.value = "Like"



        audioService?.let {
            // 1. Start playback immediately
            it.loadAndPlay(song.contentUri)
            
            // 2. Set metadata asynchronously
            if (song.thumbnailUrl != null) {
                it.setSongMetadataByUrl(song.title, song.thumbnailUrl)
            } else {
                it.setSongMetadataById(song.title, song.albumId)
            }
            
            // Personalization history
            updateRecentArtists(song.artist)
        } ?: run {
            // Service not yet bound — queue for when it connects
            Log.w(TAG, "AudioService not bound yet, song queued for cold-start loading")
        }
        
        savePlaybackState()
        prefetchNextPredictedSongs()
    }

    private fun updateRecentArtists(artist: String?) {
        if (artist.isNullOrBlank() || artist.equals("<unknown>", ignoreCase = true)) return
        
        bgScope.launch(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences("explore_prefs", Context.MODE_PRIVATE)
            val history = prefs.getString("recent_artists_list", "") ?: ""
            val currentList = history.split("|").filter { it.isNotBlank() }.toMutableList()
            
            currentList.remove(artist.trim())
            currentList.add(0, artist.trim())
            val trimmed = currentList.take(3)
            
            prefs.edit {
                putString("recent_artists_list", trimmed.joinToString("|"))
                    .putString("last_played_artist", artist)
            }
        }
    }

    fun findSongById(songId: Long): SongItem? {
        val song = _songList.value.firstOrNull { it.id == songId }
            ?: _localSongs.value.firstOrNull { it.id == songId }
        if (song != null) return song

        val service = audioService ?: return null
        val item = service.player.currentMediaItem ?: return null
        val currentId = item.mediaId.toLongOrNull()
        if (currentId == songId) {
            val title = item.mediaMetadata.title?.toString() ?: "Unknown"
            val artist = item.mediaMetadata.artist?.toString() ?: "Unknown"
            val videoId = item.mediaId
            val artworkUri = item.mediaMetadata.artworkUri?.toString()
            return SongItem.createOnlineSong(
                videoId = videoId,
                title = title,
                artist = artist,
                streamUrl = item.localConfiguration?.uri?.toString(),
                durationMs = service.player.duration,
                thumbnailUrl = artworkUri
            )
        }
        return null
    }

    fun dismissPlayer() {
        _currentSong.value = null
        _songList.value = emptyList()
        PlaybackStateManager.clear(appContext)
        audioService?.dismissService()
    }

    fun playSong(song: SongItem?) {
        if (song == null) return
        val index = findSongIndexById(song.id)
        if (index != -1) {
            playSongAtIndex(index)
        } else {
            addToQueue(song)
            val newIndex = findSongIndexById(song.id)
            if (newIndex != -1) playSongAtIndex(newIndex)
        }
    }

    fun findSongIndexById(songId: Long): Int = _songList.value.indexOfFirst { it.id == songId }

    fun playNextSong(force: Boolean = false) {
        if (!force) {
            if (_sleepTimerMode.value == SleepTimerMode.END_OF_SONG) {
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
                return
            }
            if (_sleepTimerMode.value == SleepTimerMode.END_OF_QUEUE && _currentSongIndex.value == _songList.value.size - 1) {
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
                return
            }
        }
        val nextIndex = getNextSongIndex()
        if (nextIndex >= 0) {
            playSongAtIndex(nextIndex)
        } else {
            if (_isAutoplayEnabled.value) {
                _currentSong.value?.videoId?.let {
                    checkQueueAndFetchMore()
                } ?: audioService?.pause()
            } else {
                audioService?.pause()
            }
        }
    }

    fun playPreviousSong() {
        val songs = _songList.value
        if (songs.isEmpty()) return

        // If current position is > 3 seconds, just restart the song
        val pos = _currentPosition.value
        if (pos > 3000) {
            audioService?.seekTo(0)
            return
        }

        val currentIdx = _currentSongIndex.value
        val mode = _repeatMode.value
        val shuffle = _isShuffleEnabled.value
        
        if (shuffle && songs.size > 1) {
            var prev = currentIdx
            while (prev == currentIdx) prev = shuffleRandom.nextInt(songs.size)
            playSongAtIndex(prev)
            return
        }
        
        val prevIndex = currentIdx - 1
        if (prevIndex < 0) {
            if (mode == REPEAT_ALL) {
                playSongAtIndex(songs.size - 1)
            } else {
                audioService?.seekTo(0)
            }
        } else {
            playSongAtIndex(prevIndex)
        }
    }

    fun getNextSongIndex(): Int {
        val songs = _songList.value
        if (songs.isEmpty()) return -1
        
        val currentIdx = _currentSongIndex.value
        val mode = _repeatMode.value
        val shuffle = _isShuffleEnabled.value

        if (mode == REPEAT_ONE) return if (currentIdx >= 0) currentIdx else 0
        if (shuffle && songs.size > 1) {
            val unplayed = songs.filter { it.id !in playedShuffledIds }
            if (unplayed.isNotEmpty()) {
                val nextSong = unplayed[shuffleRandom.nextInt(unplayed.size)]
                return songs.indexOfFirst { it.id == nextSong.id }
            } else {
                // All songs played!
                playedShuffledIds.clear()
                _currentSong.value?.id?.let { playedShuffledIds.add(it) }
                return -1
            }
        }

        val next = currentIdx + 1
        return if (next >= songs.size) {
            if (mode == REPEAT_ALL) 0 else -1
        } else next
    }

    private fun prefetchNextPredictedSongs() {
        val songs = _songList.value
        val currentIdx = _currentSongIndex.value
        // Collect the next 2 online songs (skip local files which don't need URL resolution)
        val idsToWarm = (1..2)
            .map { offset -> currentIdx + offset }
            .filter { idx -> idx in songs.indices }
            .mapNotNull { idx -> songs[idx].videoId }
            .filter { it.isNotEmpty() }
        if (idsToWarm.isNotEmpty()) {
            NewPipeStreamExtractor.warmCache(idsToWarm)
        }
    }

    private var specJob: kotlinx.coroutines.Job? = null
    
    /**
     * Touch prediction prefetching. Call this on ACTION_DOWN.
     */
    fun specPrefetch(song: SongItem) {
        specJob?.cancel()
        specJob = bgScope.launch(Dispatchers.IO) {
            // Warm full PlayerResult cache (URL + metadata) not just the URL
            song.videoId?.let { NewPipeStreamExtractor.warmCache(listOf(it)) }
        }
    }

    // ========== QUEUE MANAGEMENT ==========

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val songs = _songList.value
        if (fromIndex !in songs.indices || toIndex !in songs.indices) return
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_queue")

        val newList = ArrayList(songs)
        val moved = newList.removeAt(fromIndex)
        newList.add(toIndex, moved)

        when (val currentIdx = _currentSongIndex.value) {
            fromIndex -> {
                _currentSongIndex.value = toIndex
            }
            in (fromIndex + 1)..toIndex -> {
                _currentSongIndex.value = currentIdx - 1
            }
            in toIndex..<fromIndex -> {
                _currentSongIndex.value = currentIdx + 1
            }
        }
        _songList.value = newList
        audioService?.refreshNativeQueue()
    }

    fun removeSongAtIndex(index: Int) {
        val songs = _songList.value
        if (index !in songs.indices) return
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_queue")

        val newList = ArrayList(songs)
        newList.removeAt(index)

        val currentIdx = _currentSongIndex.value
        when {
            songs.size <= 1 -> {
                _songList.value = emptyList()
                _currentSongIndex.value = -1
                _currentSong.value = null
                audioService?.stop()
                return
            }
            index == currentIdx -> {
                val nextIndex = if (index >= newList.size) newList.size - 1 else index
                _songList.value = newList
                playSongAtIndex(nextIndex)
                return
            }
            index < currentIdx -> {
                _currentSongIndex.value = currentIdx - 1
            }
        }
        _songList.value = newList
        audioService?.refreshNativeQueue()
    }

    fun addToQueue(song: SongItem) {
        val newList = ArrayList(_songList.value)
        if (newList.none { it.id == song.id }) {
            newList.add(song)
            _songList.value = newList
            audioService?.refreshNativeQueue()
        }
    }

    fun addToQueueNext(song: SongItem) {
        val newList = ArrayList(_songList.value)
        newList.removeAll { it.id == song.id }
        
        val currentIdx = _currentSongIndex.value
        val insertIndex = (if (currentIdx < 0) -1 else currentIdx) + 1
        newList.add(insertIndex.coerceAtMost(newList.size), song)
        
        _songList.value = newList
        _currentSongIndex.value = findSongIndexById(_currentSong.value?.id ?: -1)
        audioService?.refreshNativeQueue()
    }

    // ========== UTILS ==========

    fun hasActiveEffects(): Boolean {
        return _is8DEnabled.value || _isEqualizerEnabled.value || 
               _isLoudnessEnabled.value || _isReverbEnabled.value
    }

    fun setIsProcessing(processing: Boolean) { _isProcessing.value = processing }
    fun setProcessingProgress(progress: Int) { _processingProgress.value = progress }
    fun setHapticsEnabled(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).edit {
            putBoolean("haptics_enabled", enabled)
        }
        hapticManager?.setHapticsEnabled(enabled)
    }
    fun setShuffleEnabled(enabled: Boolean) {
        if (_isShuffleEnabled.value != enabled) {
            _isShuffleEnabled.value = enabled
            audioService?.setShuffleModeEnabled(enabled)
            if (enabled) {
                playedShuffledIds.clear()
                _currentSong.value?.let { playedShuffledIds.add(it.id) }
            }
        }
    }
    fun toggleShuffle() {
        setShuffleEnabled(!_isShuffleEnabled.value)
    }

    // ── Autoplay (Infinite Queue) ──
    private val playedShuffledIds = mutableSetOf<Long>()
    private val autoAddedMediaIds = mutableSetOf<String>()
    
    // Endless Queue
    var currentQueue: com.codetrio.spatialflow.player.queue.Queue? = null

    fun setQueue(queue: com.codetrio.spatialflow.player.queue.Queue, startIndex: Int = 0) {
        currentQueue = queue
        synchronized(autoAddedMediaIds) { autoAddedMediaIds.clear() }
        
        val preload = queue.preloadItem
        if (preload != null) {
            setSongList(listOf(preload))
            playSongAtIndex(0)
        }
        
        bgScope.launch(Dispatchers.IO) {
            try {
                val status = queue.getInitialStatus()
                withContext(Dispatchers.Main) {
                    if (!_isAutoplayEnabled.value && queue is com.codetrio.spatialflow.player.queue.YouTubeQueue) {
                        val singleSong = preload ?: status.items.getOrNull(status.mediaItemIndex)
                        if (singleSong != null) {
                            setSongList(listOf(singleSong))
                            playSongAtIndex(0)
                        } else {
                            setSongList(status.items)
                            val matchedIndex = if (preload != null) {
                                val idx = status.items.indexOfFirst { it.id == preload.id }
                                if (idx >= 0) idx else status.mediaItemIndex
                            } else {
                                status.mediaItemIndex
                            }
                            if (matchedIndex in status.items.indices) {
                                playSongAtIndex(matchedIndex)
                            }
                        }
                    } else {
                        setSongList(status.items)
                        val matchedIndex = if (preload != null) {
                            val idx = status.items.indexOfFirst { it.id == preload.id }
                            if (idx >= 0) idx else status.mediaItemIndex
                        } else {
                            status.mediaItemIndex
                        }
                        
                        if (queue is com.codetrio.spatialflow.player.queue.YouTubeQueue) {
                            synchronized(autoAddedMediaIds) {
                                status.items.forEach { item ->
                                    if (item.id != preload?.id) {
                                        item.videoId?.let { autoAddedMediaIds.add(it) }
                                    }
                                }
                            }
                        }
                        
                        if (matchedIndex in status.items.indices) {
                            if (preload != null && status.items[matchedIndex].id == preload.id) {
                                updateSongIndexOnly(matchedIndex)
                                if (status.position > 0) {
                                    audioService?.seekTo(status.position.toInt())
                                }
                            } else {
                                playSongAtIndex(matchedIndex)
                                if (status.position > 0) {
                                    audioService?.seekTo(status.position.toInt())
                                }
                            }
                        } else if (status.items.isNotEmpty()) {
                            playSongAtIndex(0)
                            if (status.position > 0) {
                                audioService?.seekTo(status.position.toInt())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load initial queue status", e)
            }
        }
    }

    fun checkQueueAndFetchMore() {
        if (!_isAutoplayEnabled.value) return
        val queue = currentQueue ?: return
        val currentIdx = _currentSongIndex.value
        val listSize = _songList.value.size
        
        // Auto-Load continuation Pages (Auto-Load More)
        if (listSize - currentIdx <= 5 && queue.hasNextPage()) {
            bgScope.launch(Dispatchers.IO) {
                try {
                    val nextItems = queue.nextPage()
                    if (nextItems.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (_isAutoplayEnabled.value) {
                                synchronized(autoAddedMediaIds) {
                                    nextItems.forEach { it.videoId?.let { id -> autoAddedMediaIds.add(id) } }
                                }
                                addSongsToQueue(nextItems)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch next queue page", e)
                }
            }
        } else if (listSize - currentIdx <= 3 && !queue.hasNextPage() && _isAutoplayEnabled.value) {
            // Bootstrapping Infinite Autoplay (YouTube Radio)
            val currentSong = _currentSong.value
            if (currentSong != null && !currentSong.videoId.isNullOrEmpty()) {
                val radioQueue = com.codetrio.spatialflow.player.queue.YouTubeQueue.radio(currentSong)
                currentQueue = radioQueue
                bgScope.launch(Dispatchers.IO) {
                    try {
                        val status = radioQueue.getInitialStatus()
                        val existingIds = _songList.value.mapNotNull { it.videoId }.toSet()
                        val newItems = status.items.filter { it.videoId != null && it.videoId !in existingIds }
                        if (newItems.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (_isAutoplayEnabled.value) {
                                    synchronized(autoAddedMediaIds) {
                                        newItems.forEach { it.videoId?.let { id -> autoAddedMediaIds.add(id) } }
                                    }
                                    addSongsToQueue(newItems)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to handoff to radio", e)
                    }
                }
            }
        }
    }

    private val _isAutoplayEnabled = MutableStateFlow(
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .getBoolean("autoplay_enabled", true)
    )
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    fun setAutoplayEnabled(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).edit {
            putBoolean("autoplay_enabled", enabled)
        }
        if (enabled) {
            if (currentQueue == null) {
                val currentSong = _currentSong.value
                if (currentSong != null && !currentSong.videoId.isNullOrEmpty()) {
                    currentQueue = com.codetrio.spatialflow.player.queue.YouTubeQueue.radio(currentSong)
                }
            }
            checkQueueAndFetchMore()
        } else {
            onInfiniteQueueDisabled()
        }
    }

    fun onInfiniteQueueDisabled() {
        val currentIdx = _currentSongIndex.value
        val songs = _songList.value
        if (currentIdx in songs.indices) {
            val currentSong = songs[currentIdx]
            if (currentQueue is com.codetrio.spatialflow.player.queue.YouTubeQueue) {
                _songList.value = listOf(currentSong)
                _currentSongIndex.value = 0
            } else {
                val idsToRemove = synchronized(autoAddedMediaIds) { autoAddedMediaIds.toSet() }
                val newList = songs.toMutableList()
                // Purges future automatically injected recommendations
                for (i in newList.size - 1 downTo 0) {
                    if (i == currentIdx) continue
                    val song = newList[i]
                    if (!song.videoId.isNullOrEmpty() && song.videoId in idsToRemove) {
                        newList.removeAt(i)
                    }
                }
                _songList.value = newList
                _currentSongIndex.value = newList.indexOfFirst { it.id == currentSong.id }
            }
            audioService?.refreshNativeQueue()
        }
        synchronized(autoAddedMediaIds) { autoAddedMediaIds.clear() }
    }

    fun addSongsToQueue(songs: List<SongItem>) {
        val newList = ArrayList(_songList.value)
        val existingIds = newList.map { it.id }.toSet()
        val uniqueNewSongs = songs.filter { it.id !in existingIds }
        if (uniqueNewSongs.isEmpty()) return
        newList.addAll(uniqueNewSongs)
        _songList.value = newList
        _currentSongIndex.value = findSongIndexById(_currentSong.value?.id ?: -1)
        audioService?.refreshNativeQueue()
    }

    override fun onCleared() {
        super.onCleared()
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(appSettingsPrefListener)
        audioService = null
        _audioServiceState.value = null
    }
}
