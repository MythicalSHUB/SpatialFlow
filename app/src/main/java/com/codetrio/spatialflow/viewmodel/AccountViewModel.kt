package com.codetrio.spatialflow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.UserProfile
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates standard account retrieval loops sourcing aggregated library contents,
 * preference values, and basic user lifecycle bindings.
 */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _playlists = MutableStateFlow<List<OnlinePlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _artists = MutableStateFlow<List<OnlineArtist>>(emptyList())
    val artists = _artists.asStateFlow()

    private val _albums = MutableStateFlow<List<OnlineAlbum>>(emptyList())
    val albums = _albums.asStateFlow()

    private val _songs = MutableStateFlow<List<OnlineSong>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _podcasts = MutableStateFlow<List<OnlinePlaylist>>(emptyList())
    val podcasts = _podcasts.asStateFlow()

    private val _history = MutableStateFlow<List<OnlineSong>>(emptyList())
    val history = _history.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        loadProfile()
        loadLibrary()
    }

    fun loadProfile() {
        val context = getApplication<Application>()
        if (!AccountManager.isLoggedIn(context)) {
            _userProfile.value = null
            return
        }

        viewModelScope.launch {
            try {
                YouTubeMusic.accountProfile().onSuccess { profile ->
                    _userProfile.value = profile
                }.onFailure {
                    android.util.Log.w("AccountViewModel", "Failed to load profile", it)
                }
            } catch (_: Exception) {
                // Silent fail
            }
        }
    }

    fun loadLibrary() {
        val context = getApplication<Application>()
        if (!AccountManager.isLoggedIn(context)) {
            _playlists.value = emptyList()
            _artists.value = emptyList()
            _albums.value = emptyList()
            _songs.value = emptyList()
            _podcasts.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load Playlists
                YouTubeMusic.libraryPlaylists().onSuccess { items ->
                    val filtered = items.filter { it.title.isNotEmpty() && !it.title.equals("New playlist", ignoreCase = true) }
                    _playlists.value = filtered
                }.onFailure {
                    android.util.Log.w("AccountViewModel", "Failed to load playlists", it)
                }

                // Load Podcasts
                YouTubeMusic.libraryPodcasts().onSuccess { items ->
                    _podcasts.value = items
                }.onFailure { throwable ->
                    val filteredPlaylists = _playlists.value
                    _podcasts.value = filteredPlaylists.filter { it.title.lowercase().contains("podcast") || it.title.lowercase().contains("show") }
                    android.util.Log.w("AccountViewModel", "Failed to load podcasts, using playlist fallback", throwable)
                }

                // Load Albums
                YouTubeMusic.libraryAlbums().onSuccess { items ->
                    _albums.value = items
                }.onFailure {
                    android.util.Log.w("AccountViewModel", "Failed to load library albums", it)
                }

                // Load Liked Songs (from playlist "LM")
                YouTubeMusic.playlist("LM").onSuccess { page ->
                    _songs.value = page.songs
                }.onFailure {
                    android.util.Log.w("AccountViewModel", "Failed to load liked songs from playlist LM", it)
                }

                // Load History
                YouTubeMusic.history().onSuccess { items ->
                    _history.value = items
                }.onFailure {
                    android.util.Log.w("AccountViewModel", "Failed to load listening history", it)
                }

                // Load Artists and merge with artists from saved albums and songs
                var fetchedArtists = emptyList<OnlineArtist>()
                YouTubeMusic.libraryArtists().onSuccess { items ->
                    fetchedArtists = items
                }.onFailure {
                    android.util.Log.w("AccountViewModel", "Failed to load library artists", it)
                }

                val compiledMap = mutableMapOf<String, OnlineArtist>()
                
                // 1. Add explicitly followed artists
                fetchedArtists.forEach { artist ->
                    compiledMap[artist.browseId] = artist
                }

                // 2. Add artists from saved albums
                _albums.value.forEach { album ->
                    album.artists.forEach { ref ->
                        val artistId = ref.id
                        if (artistId != null && !compiledMap.containsKey(artistId)) {
                            compiledMap[artistId] = OnlineArtist(
                                browseId = artistId,
                                title = ref.name,
                                thumbnailUrl = album.thumbnailUrl
                            )
                        }
                    }
                }

                // 3. Add artists from liked songs
                _songs.value.forEach { song ->
                    val artistId = song.artistId
                    if (artistId != null && !compiledMap.containsKey(artistId)) {
                        compiledMap[artistId] = OnlineArtist(
                            browseId = artistId,
                            title = song.artist,
                            thumbnailUrl = song.thumbnailUrl
                        )
                    }
                }

                _artists.value = compiledMap.values.toList().sortedBy { it.title.lowercase() }
            } catch (e: Exception) {
                android.util.Log.e("AccountViewModel", "Error fetching library", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun onLoggedIn(cookies: String) {
        val context = getApplication<Application>()
        AccountManager.saveLoginState(context, cookies)
        YouTubeMusic.setCookie(cookies)
        refreshAll()
    }

    fun toggleSync(enabled: Boolean) {
        AccountManager.setSyncEnabled(getApplication(), enabled)
    }
    
    fun logout() {
        AccountManager.logout(getApplication())
        _userProfile.value = null
        _playlists.value = emptyList()
        _artists.value = emptyList()
        _albums.value = emptyList()
        _songs.value = emptyList()
        _podcasts.value = emptyList()
        _history.value = emptyList()
    }
}
