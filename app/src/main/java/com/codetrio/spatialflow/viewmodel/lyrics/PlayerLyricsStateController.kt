package com.codetrio.spatialflow.viewmodel.lyrics

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LrcParser
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsRepository
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.LyricsState
import com.codetrio.spatialflow.model.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles lyrics state, fetch, and retry flows for player view models.
 * Kotlin + StateFlow implementation for modern reactive architecture.
 */
class PlayerLyricsStateController(private val logTag: String) {
    private var activeLyricsSongId: Long? = null

    private val _syncedLyrics = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyrics: StateFlow<List<LyricLine>?> = _syncedLyrics.asStateFlow()

    private val _plainLyrics = MutableStateFlow<String?>(null)
    val plainLyrics: StateFlow<String?> = _plainLyrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    private val _isLyricsModeEnabled = MutableStateFlow(false)
    val isLyricsModeEnabled: StateFlow<Boolean> = _isLyricsModeEnabled.asStateFlow()

    private val _lyricsState = MutableStateFlow(LyricsState.IDLE)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)

    private val _providerResults = MutableStateFlow<Map<String, LyricsResult>>(emptyMap())
    val providerResults: StateFlow<Map<String, LyricsResult>> = _providerResults.asStateFlow()

    private val _selectedProvider = MutableStateFlow<String?>(null)
    val selectedProvider: StateFlow<String?> = _selectedProvider.asStateFlow()

    val shouldShowLyrics: Boolean get() = _isLyricsModeEnabled.value

    fun setLyricsModeEnabled(enabled: Boolean) {
        _isLyricsModeEnabled.value = enabled
    }

    fun clearForSongChange(song: SongItem? = null) {
        activeLyricsSongId = song?.id
        _syncedLyrics.value = null
        _plainLyrics.value = null
        _error.value = null
        _isLoading.value = false
        _lyricsState.value = LyricsState.IDLE
        _statusMessage.value = null
        _providerResults.value = emptyMap()
        _selectedProvider.value = null
    }

    fun fetchLyrics(context: Context, song: SongItem?) {
        if (song == null) return

        val repository = LyricsRepository.getInstance(context)
        activeLyricsSongId = song.id
        repository.cancelCurrent()

        // Reset state
        _lyricsState.value = LyricsState.IDLE
        _syncedLyrics.value = null
        _plainLyrics.value = null
        _error.value = null
        _providerResults.value = emptyMap()
        _selectedProvider.value = null

        setLyricsState(LyricsState.FETCHING)
        _statusMessage.value = "Searching for lyrics..."

        repository.fetchLyrics(
            song.title,
            song.artist,
            null,
            song.duration,
            song.path,
            createCallback(
                requestSongId = song.id,
                keepExistingLyricsOnNotFound = true,
                instrumentalMessage = "Instrumental track - no vocals",
                logUpgrades = true
            ),
            song.videoId
        )
    }

    fun retryLyrics(context: Context, song: SongItem?) {
        if (song == null) return

        activeLyricsSongId = song.id

        // Reset state
        _syncedLyrics.value = null
        _plainLyrics.value = null
        _error.value = null
        _lyricsState.value = LyricsState.IDLE
        _providerResults.value = emptyMap()
        _selectedProvider.value = null

        setLyricsState(LyricsState.FETCHING)
        _statusMessage.value = "Retrying all sources..."

        LyricsRepository.getInstance(context).retryLyrics(
            song.title,
            song.artist,
            null,
            song.duration,
            song.path,
            createCallback(
                requestSongId = song.id,
                keepExistingLyricsOnNotFound = false,
                instrumentalMessage = "Instrumental track",
                logUpgrades = false
            ),
            song.videoId
        )
    }

    fun selectProvider(providerName: String) {
        val result = _providerResults.value[providerName]
        if (result != null && result.hasLyrics()) {
            _selectedProvider.value = providerName
            applyLyricsResult(result)
            _lyricsState.value = LyricsState.SUCCESS
            _error.value = null
        }
    }

    fun determineBestResult(results: Map<String, LyricsResult>): LyricsResult? {
        val candidates = results.values

        // First priority: Karaoke (word-by-word)
        val karaokeCandidates = candidates.filter { it.isWordByWord }
        if (karaokeCandidates.isNotEmpty()) {
            return karaokeCandidates.maxWithOrNull(
                compareBy<LyricsResult> { it.providerName == "SyncLRC" }
                    .thenBy { it.confidence }
            )
        }

        // Second priority: Synced
        val syncedCandidates = candidates.filter { it.isSynced }
        if (syncedCandidates.isNotEmpty()) {
            return syncedCandidates.maxByOrNull { it.confidence }
        }

        // Third priority: Plain lyrics
        val plainCandidates = candidates.filter { !it.isSynced && !it.isWordByWord && !it.isInstrumental }
        if (plainCandidates.isNotEmpty()) {
            return plainCandidates.maxByOrNull { it.confidence }
        }

        // Fourth priority: Instrumental
        return candidates.firstOrNull { it.isInstrumental }
    }

    private fun createCallback(
        requestSongId: Long,
        keepExistingLyricsOnNotFound: Boolean,
        instrumentalMessage: String,
        logUpgrades: Boolean
    ): LyricsRepository.ExtendedLyricsCallback {
        return object : LyricsRepository.ExtendedLyricsCallback {
            override fun onLyricsFound(result: LyricsResult) {
                if (!isActiveLyricsRequest(requestSongId)) return

                if (_selectedProvider.value == null) {
                    applyLyricsResult(result)
                    _lyricsState.value = LyricsState.SUCCESS
                    _isLoading.value = false
                    _error.value = null
                    _statusMessage.value = "Lyrics from ${result.providerName}"
                }
            }

            override fun onLyricsUpgraded(betterResult: LyricsResult) {
                if (!isActiveLyricsRequest(requestSongId)) return

                if (_selectedProvider.value == null && !betterResult.syncedLyrics.isNullOrEmpty()) {
                    val lines = LrcParser.parse(betterResult.syncedLyrics)
                    _syncedLyrics.value = lines
                    _plainLyrics.value = null
                    _lyricsState.value = LyricsState.SUCCESS
                    if (logUpgrades) {
                        _statusMessage.value = "Upgraded to synced lyrics from ${betterResult.providerName}"
                        Log.d(logTag, "Lyrics upgraded to synced from ${betterResult.providerName}")
                    }
                }
            }

            override fun onLyricsNotFound(reason: String) {
                if (!isActiveLyricsRequest(requestSongId)) return

                if (keepExistingLyricsOnNotFound && hasLyricsData()) return

                if (_syncedLyrics.value.isNullOrEmpty() && _plainLyrics.value.isNullOrEmpty()) {
                    _lyricsState.value = LyricsState.FAILED
                    _isLoading.value = false
                    _error.value = Exception(reason)
                    _statusMessage.value = null
                }
            }

            override fun onInstrumental() {
                if (!isActiveLyricsRequest(requestSongId)) return

                if (_syncedLyrics.value.isNullOrEmpty() && _plainLyrics.value.isNullOrEmpty()) {
                    _lyricsState.value = LyricsState.FAILED
                    _isLoading.value = false
                    _error.value = Exception(instrumentalMessage)
                    _statusMessage.value = null
                }
            }

            override fun onSearchStatus(message: String) {
                if (!isActiveLyricsRequest(requestSongId)) return

                _statusMessage.value = message
            }

            override fun onProviderResult(providerName: String, result: LyricsResult?) {
                if (!isActiveLyricsRequest(requestSongId)) return

                val currentMap = _providerResults.value.toMutableMap()
                if (result != null) {
                    currentMap[providerName] = result
                } else {
                    currentMap[providerName] = LyricsResult(providerName = providerName, confidence = -1f)
                }
                _providerResults.value = currentMap

                if (_selectedProvider.value == null) {
                    val best = determineBestResult(currentMap)
                    if (best != null && best.hasLyrics()) {
                        applyLyricsResult(best)
                        _lyricsState.value = LyricsState.SUCCESS
                        _error.value = null
                    } else if (best != null && best.isInstrumental) {
                        _syncedLyrics.value = null
                        _plainLyrics.value = null
                        _lyricsState.value = LyricsState.FAILED
                        _error.value = Exception(instrumentalMessage)
                    }
                }
            }
        }
    }

    private fun isActiveLyricsRequest(requestSongId: Long): Boolean {
        return activeLyricsSongId == requestSongId
    }

    private fun applyLyricsResult(result: LyricsResult) {
        if (!result.syncedLyrics.isNullOrEmpty()) {
            val lines = LrcParser.parse(result.syncedLyrics)
            _syncedLyrics.value = lines
            _plainLyrics.value = null
        } else if (!result.plainLyrics.isNullOrEmpty()) {
            _syncedLyrics.value = null
            _plainLyrics.value = result.plainLyrics
        }
    }

    private fun hasLyricsData(): Boolean {
        val synced = _syncedLyrics.value
        val plain = _plainLyrics.value
        return !synced.isNullOrEmpty() || !plain.isNullOrEmpty()
    }

    private fun setLyricsState(newState: LyricsState) {
        val current = _lyricsState.value
        if (!current.canTransitionTo(newState)) {
            Log.w(logTag, "Invalid lyrics state transition: $current -> $newState. Blocked.")
            return
        }
        _lyricsState.value = newState
        _isLoading.value = newState.isLoading
    }
}
