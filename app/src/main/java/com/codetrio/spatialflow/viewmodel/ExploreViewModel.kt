package com.codetrio.spatialflow.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codetrio.spatialflow.data.innertube.AlbumPage
import com.codetrio.spatialflow.data.innertube.ArtistPage
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.PlaylistPage
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class DetailType {
    ALBUM, PLAYLIST, ARTIST, SECTION, GENRES, MOOD
}

@Stable
data class MoodDetail(
    val moodName: String,
    val sections: List<HomeSection>
)

@Stable
data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val searchFilter: SearchFilter? = null
)

@Stable
data class HomeUiState(
    val homeSections: List<HomeSection> = emptyList(),
    val isLoadingHome: Boolean = false,
    val currentMood: String? = null,
    val homeMoods: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMoreHome: Boolean = false,
    val pinnedSpeedDialIds: Set<String> = emptySet(),
    val notInterestedIds: Set<String> = emptySet(),
    val error: String? = null
)

@Stable
data class DetailUiState(
    val albumDetail: AlbumPage? = null,
    val artistDetail: ArtistPage? = null,
    val playlistDetail: PlaylistPage? = null,
    val sectionDetail: HomeSection? = null,
    val moodDetail: MoodDetail? = null,
    val genresSections: List<HomeSection> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailStack: List<DetailType> = emptyList()
)

@Stable
data class PlaybackUiState(
    val isLoadingStream: Boolean = false,
    val currentOnlineSong: OnlineSong? = null,
    val streamUrl: String? = null,
    val streamError: String? = null,
    val onlineQueue: List<OnlineSong> = emptyList(),
    val currentOnlineIndex: Int = -1
)

/**
 * ViewModel for the Explore (Online Music) tab.
 * Manages search, home feed, and online playback state.
 */
class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    var cameFromLibrary: Boolean = false

    private val TAG = "ExploreViewModel"

    // ========== Unified MVI State ==========

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Granular 4-way UI state flows for UI performance optimization
    val searchState: StateFlow<SearchUiState> = _uiState
        .map { SearchUiState(it.searchQuery, it.searchResults, it.suggestions, it.isSearching, it.isLoadingSuggestions, it.searchHistory, it.searchFilter) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())

    val homeState: StateFlow<HomeUiState> = _uiState
        .map { HomeUiState(it.homeSections, it.isLoadingHome, it.currentMood, it.homeMoods, it.isRefreshing, it.isLoadingMoreHome, it.pinnedSpeedDialIds, it.notInterestedIds, it.error) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val detailState: StateFlow<DetailUiState> = _uiState
        .map { DetailUiState(it.albumDetail, it.artistDetail, it.playlistDetail, it.sectionDetail, it.moodDetail, it.genresSections, it.isLoadingDetail, it.detailStack) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DetailUiState())

    val playbackState: StateFlow<PlaybackUiState> = _uiState
        .map { PlaybackUiState(it.isLoadingStream, it.currentOnlineSong, it.streamUrl, it.streamError, it.onlineQueue, it.currentOnlineIndex) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackUiState())

    private val _subscriptionChanged = MutableStateFlow(false)
    val subscriptionChanged = _subscriptionChanged.asStateFlow()

    fun consumeSubscriptionChanged() {
        _subscriptionChanged.value = false
    }

    // Public delegated StateFlows for external backward compatibility
    val currentOnlineSong: StateFlow<OnlineSong?> = uiState
        .map { it.currentOnlineSong }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val onlineQueue: StateFlow<List<OnlineSong>> = uiState
        .map { it.onlineQueue }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentOnlineIndex: StateFlow<Int> = uiState
        .map { it.currentOnlineIndex }
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    val searchFilter: StateFlow<SearchFilter?> = uiState
        .map { it.searchFilter }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoadingMoreHome: StateFlow<Boolean> = uiState
        .map { it.isLoadingMoreHome }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val error: StateFlow<String?> = uiState
        .map { it.error }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streamUrl: StateFlow<String?> = uiState
        .map { it.streamUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoadingStream: StateFlow<Boolean> = uiState
        .map { it.isLoadingStream }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)



    private var searchContinuation: String? = null
    private var homeContinuationToken: String? = null
    private var homeLoaded = false

    fun setMood(mood: String?) {
        if (_uiState.value.currentMood == mood) return
        _uiState.update { it.copy(currentMood = mood) }
        homeLoaded = false // Force reload
        loadHomeFeed()
    }

    // ========== Search History ==========

    private val prefs = application.getSharedPreferences("explore_prefs", 0)
    private val SEARCH_HISTORY_KEY = "search_history"
    private val MAX_HISTORY = 20

    init {
        loadSearchHistory()
        val speedDialSongs = getPinnedSpeedDialSongs()
        val notInterested = prefs.getStringSet("not_interested_ids", null)?.toSet() ?: emptySet()
        _uiState.update { 
            it.copy(
                pinnedSpeedDialIds = speedDialSongs.map { s -> s.videoId }.toSet(),
                notInterestedIds = notInterested
            )
        }
    }

    private fun loadSearchHistory() {
        val historyString = prefs.getString("search_history_list", null)
        if (historyString != null) {
            val list = historyString.split("\n").filter { it.isNotBlank() }
            _uiState.update { it.copy(searchHistory = list) }
        } else {
            // Fallback & migration from old set-based format
            val oldSet = prefs.getStringSet(SEARCH_HISTORY_KEY, null)
            if (oldSet != null) {
                val list = oldSet.toList()
                _uiState.update { it.copy(searchHistory = list) }
                prefs.edit {
                        putString("search_history_list", list.joinToString("\n"))
                        .remove(SEARCH_HISTORY_KEY)
                }
            } else {
                _uiState.update { it.copy(searchHistory = emptyList()) }
            }
        }
    }

    private fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        val appSettings = getApplication<android.app.Application>().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        if (appSettings.getBoolean("pause_history", false)) {
            return
        }
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(query) // Remove duplicate
        current.add(0, query) // Add to top
        val trimmed = current.take(MAX_HISTORY)
        _uiState.update { it.copy(searchHistory = trimmed) }
        prefs.edit {
                putString("search_history_list", trimmed.joinToString("\n"))
            }
    }


    fun removeFromSearchHistory(query: String) {
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(query)
        _uiState.update { it.copy(searchHistory = current) }
        prefs.edit {
            putString("search_history_list", current.joinToString("\n"))
        }
    }

    fun clearSearchHistory() {
        _uiState.update { it.copy(searchHistory = emptyList()) }
        prefs.edit {
            remove("search_history_list")
                .remove(SEARCH_HISTORY_KEY)
        }
    }

    // Shared event to signal that the host fragment should propagate current state to Player service INSTANTLY!
    private val _playbackTriggerEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val playbackTriggerEvent = _playbackTriggerEvent.asSharedFlow()

    private val _events = Channel<ExploreEvent>()
    val events: Flow<ExploreEvent> = _events.receiveAsFlow()

    fun sendEvent(event: ExploreEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    fun onAction(action: ExploreAction) {
        when (action) {
            is ExploreAction.OnSearchQueryChange -> setSearchQuery(action.query)
            is ExploreAction.OnSearchFilterSelect -> setSearchFilter(action.filter)
            is ExploreAction.OnSearchSubmit -> search(action.query)
            is ExploreAction.OnClearSearch -> clearSearch()
            is ExploreAction.OnSongClick -> playOnlineSong(action.song)
            is ExploreAction.OnSongWithQueueClick -> playOnlineSongWithQueue(action.song, action.queue, action.index)
            is ExploreAction.OnAlbumClick -> loadAlbum(action.album.browseId)
            is ExploreAction.OnAlbumBrowseIdClick -> loadAlbum(action.browseId)
            is ExploreAction.OnArtistClick -> loadArtist(action.artist.browseId)
            is ExploreAction.OnArtistBrowseIdClick -> loadArtist(action.browseId)
            is ExploreAction.OnPlaylistClick -> loadPlaylist(action.playlist.playlistId)
            is ExploreAction.OnPlaylistIdClick -> loadPlaylist(action.playlistId)
            is ExploreAction.OnSectionClick -> loadSectionDetails(action.browseId, action.params, action.title)
            is ExploreAction.OnMoodClick -> loadMood(action.title, action.browseId, action.params)
            is ExploreAction.OnSetMood -> setMood(action.mood)
            is ExploreAction.OnRefreshHomeFeed -> refreshHomeFeed()
            is ExploreAction.OnLoadMoreHomeSections -> loadMoreHomeSections()
            is ExploreAction.OnLoadMoreSearchResults -> loadMoreResults()
            is ExploreAction.OnPopDetail -> popDetailStack()
            is ExploreAction.OnResetToHome -> resetToHome()
            is ExploreAction.OnHandleDeepLink -> handleDeepLink(action.url)
        }
    }

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    private fun handleDeepLink(urlString: String) {
        try {
            val uri = android.net.Uri.parse(urlString)
            val host = uri.host ?: return
            
            var videoId: String? = null
            if (host.contains("youtu.be")) {
                videoId = uri.lastPathSegment
            } else if (host.contains("youtube.com")) {
                videoId = uri.getQueryParameter("v")
            }
            
            val playlistId = uri.getQueryParameter("list")

            if (videoId != null) {
                startRadio(videoId)
            } else if (playlistId != null) {
                loadPlaylist(playlistId)
            } else {
                sendEvent(ExploreEvent.ShowSnackbar(com.codetrio.spatialflow.ui.UiText.DynamicString("Invalid YouTube link")))
            }
        } catch (e: Exception) {
            sendEvent(ExploreEvent.ShowSnackbar(com.codetrio.spatialflow.ui.UiText.DynamicString("Failed to parse link")))
        }
    }

    // ========== Search Methods ==========

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            fetchSuggestions(query)
        } else {
            _uiState.update { it.copy(suggestions = emptyList(), isLoadingSuggestions = false) }
        }
    }

    fun setSearchFilter(filter: SearchFilter?) {
        _uiState.update { it.copy(searchFilter = filter) }
        // Re-search with filter if we have a query
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            search(query)
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        _uiState.update { 
            it.copy(
                searchQuery = query,
                isSearching = true,
                suggestions = emptyList(),
                searchResults = emptyList()
            )
        }

        searchJob = viewModelScope.launch {
            try {
                if (trimmedQuery.isNotEmpty()) {
                    addToSearchHistory(trimmedQuery)
                }
                val result = YouTubeMusic.search(trimmedQuery, _uiState.value.searchFilter)
                result.onSuccess { searchResult ->
                    // Filter duplicates to prevent duplicate keys in lazy lists
                    val seenIds = mutableSetOf<String>()
                    val uniqueItems = searchResult.items.filter { item ->
                        val id = when (item) {
                            is SearchItem.TopResult -> "topresult-${item.title}"
                            is SearchItem.Header -> "header-${item.title}"
                            is SearchItem.Song -> "song-${item.song.videoId}"
                            is SearchItem.Album -> "album-${item.album.browseId}"
                            is SearchItem.Artist -> "artist-${item.artist.browseId}"
                            is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                        }
                        val isNotInterested = item is SearchItem.Song && _uiState.value.notInterestedIds.contains(item.song.videoId)
                        !isNotInterested && seenIds.add(id)
                    }
                    _uiState.update { it.copy(searchResults = uniqueItems) }
                    searchContinuation = searchResult.continuation
                    prefetchThumbnails(uniqueItems.take(12))
                    
                    // Pre-resolve stream URLs for top 3 song results for instant playback
                    uniqueItems
                        .filterIsInstance<SearchItem.Song>()
                        .take(3)
                        .forEach { songItem ->
                            YouTubeMusic.prefetchStream(songItem.song.videoId)
                        }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Search failed", e)
                    _uiState.update { it.copy(error = "Search failed: ${e.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Search error", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun loadMoreResults() {
        val cont = searchContinuation ?: return
        if (_uiState.value.isSearching) return
        
        // Synchronously set to true to prevent any other triggers in the same frame/recomposition loop
        _uiState.update { it.copy(isSearching = true) }

        viewModelScope.launch {
            try {
                val result = YouTubeMusic.searchContinuation(cont)
                result.onSuccess { searchResult ->
                    var newItemsToPrefetch = emptyList<SearchItem>()
                    _uiState.update { state ->
                        val currentList = state.searchResults
                        val seenIds = currentList.map { item ->
                            when (item) {
                                is SearchItem.TopResult -> "topresult-${item.title}"
                                is SearchItem.Header -> "header-${item.title}"
                                is SearchItem.Song -> "song-${item.song.videoId}"
                                is SearchItem.Album -> "album-${item.album.browseId}"
                                is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                            }
                        }.toMutableSet()

                        val uniqueNewItems = searchResult.items.filter { item ->
                            val id = when (item) {
                                is SearchItem.TopResult -> "topresult-${item.title}"
                                is SearchItem.Header -> "header-${item.title}"
                                is SearchItem.Song -> "song-${item.song.videoId}"
                                is SearchItem.Album -> "album-${item.album.browseId}"
                                is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                            }
                            val isNotInterested = item is SearchItem.Song && _uiState.value.notInterestedIds.contains(item.song.videoId)
                            !isNotInterested && seenIds.add(id)
                        }

                        searchContinuation = searchResult.continuation
                        newItemsToPrefetch = uniqueNewItems
                        state.copy(searchResults = currentList + uniqueNewItems)
                    }
                    prefetchThumbnails(newItemsToPrefetch.take(8))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun fetchSuggestions(query: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(150.milliseconds) // Reduced debounce for instant feel
            _uiState.update { it.copy(isLoadingSuggestions = true) }
            try {
                val result = YouTubeMusic.searchSuggestions(query)
                result.onSuccess { suggestions ->
                    _uiState.update { it.copy(suggestions = suggestions.take(8), isLoadingSuggestions = false) }
                }
                result.onFailure {
                    _uiState.update { it.copy(isLoadingSuggestions = false) }
                }
            } catch (_: Exception) {
                // Silently ignore suggestion errors
                _uiState.update { it.copy(isLoadingSuggestions = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                suggestions = emptyList(),
                searchFilter = null,
                isLoadingSuggestions = false
            )
        }
        searchContinuation = null
    }

    // ========== Home Feed ==========

    // (Removed getPersonalizedSections to rely purely on YouTube Explore)

    private fun syncCookieFromDisk() {
        val ctx = getApplication<Application>()
        val cookie = ctx.getSharedPreferences("AppSettings", 0).getString("yt_cookies", null)
        InnerTubeClient.cookie = cookie
    }

    fun forceReloadHomeFeed() {
        syncCookieFromDisk()
        homeLoaded = false
        loadHomeFeed()
    }

    private suspend fun fetchMergedHomeFeed(): List<HomeSection> = coroutineScope {
        val sections = mutableListOf<HomeSection>()
        val mood = _uiState.value.currentMood

        if (mood != null) {
            // Parallel Synthesis: Mood specific feed creation
            try {
                val playTask = async { YouTubeMusic.search(mood, SearchFilter.PLAYLISTS) }
                val songTask = async { YouTubeMusic.search("$mood music", SearchFilter.SONGS) }
                val albumTask = async { YouTubeMusic.search("$mood releases", SearchFilter.ALBUMS) }

                // Wait for all simultaneously
                val responses = awaitAll(playTask, songTask, albumTask)
                
                responses[0].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("$mood Curations", res.items.shuffled().take(12)))
                }
                responses[1].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("Top $mood Tracks", res.items.take(20)))
                }
                responses[2].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("$mood Spotlight", res.items.shuffled().take(12)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to synthesize mood feed", e)
            }
            return@coroutineScope sections
        }

        // Standard Feed Scenario: Load pure YouTube Home
        val homePage = YouTubeMusic.home().getOrNull()

        homeContinuationToken = homePage?.continuation

        // Extract Moods
        val allMoods = mutableListOf<String>()
        homePage?.moods?.let { allMoods.addAll(it) }
        if (allMoods.isNotEmpty()) {
            _uiState.update { it.copy(homeMoods = allMoods.distinct()) }
        }

        // Add pure Home sections
        homePage?.sections?.let { sections.addAll(it) }

        // Post-processing: Filter out disliked / not interested songs
        val notInterested = _uiState.value.notInterestedIds

        val processedSections = sections.map { sec ->
            sec.copy(items = sec.items.filterNot { item ->
                item is SearchItem.Song && notInterested.contains(item.song.videoId)
            })
        }.filter { it.items.isNotEmpty() }.toMutableList()

        processedSections
    }

    fun loadHomeFeed() {
        if (homeLoaded || _uiState.value.isLoadingHome) return

        syncCookieFromDisk() // Critical defensive hydration step
        _uiState.update { it.copy(isLoadingHome = true) }
        viewModelScope.launch {
            try {
                val mergedSections = fetchMergedHomeFeed()
                _uiState.update { it.copy(homeSections = mergedSections) }
                homeLoaded = true
                prefetchThumbnails(mergedSections.take(4).flatMap { it.items.take(4) })
            } catch (e: Exception) {
                Log.e(TAG, "Home feed error", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoadingHome = false) }
            }
        }
    }

    fun refreshHomeFeed() {
        syncCookieFromDisk()
        homeLoaded = false
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                val mergedSections = fetchMergedHomeFeed()
                _uiState.update { it.copy(homeSections = mergedSections) }
                homeLoaded = true
                prefetchThumbnails(mergedSections.take(4).flatMap { it.items.take(4) })
            } catch (e: Exception) {
                Log.e(TAG, "Refresh error", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun loadMoreHomeSections() {
        val token = homeContinuationToken ?: return
        if (_uiState.value.isLoadingHome || _uiState.value.isLoadingMoreHome) return

        _uiState.update { it.copy(isLoadingMoreHome = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.homeContinuation(token)
                result.onSuccess { page ->
                    val current = _uiState.value.homeSections.toMutableList()
                    page.sections.forEach { newSec ->
                        if (current.none { it.title.equals(newSec.title, ignoreCase = true) }) {
                            current.add(newSec)
                        }
                    }
                    _uiState.update { it.copy(homeSections = current) }
                    homeContinuationToken = page.continuation
                    prefetchThumbnails(page.sections.take(3).flatMap { it.items.take(4) })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load more home sections failed", e)
            } finally {
                _uiState.update { it.copy(isLoadingMoreHome = false) }
            }
        }
    }

    // ========== Online Playback ==========

    fun playOnlineSong(song: OnlineSong) {
        _uiState.update { 
            it.copy(
                currentOnlineSong = song,
                isLoadingStream = false,
                streamError = null
            )
        }

        // Track played artist for home feed personalization
        val appSettings = getApplication<android.app.Application>().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        if (!appSettings.getBoolean("pause_history", false)) {
            if (song.artist.isNotBlank() && !song.artist.equals("<unknown>", ignoreCase = true)) {
                val historyString = prefs.getString("recent_artists_list", "") ?: ""
                val currentList = if (historyString.isBlank()) {
                    mutableListOf()
                } else {
                    historyString.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                }
                currentList.remove(song.artist.trim())
                currentList.add(0, song.artist.trim())
                val trimmedList = currentList.take(3)
                prefs.edit {
                    putString("recent_artists_list", trimmedList.joinToString("|"))
                        .putString("last_played_artist", song.artist) // Backwards compatibility
                }
            }
        }

        // EXTREME LATENCY REMOVAL: Deleted explicit blocking network call! 
        // ExoPlayer uses ResolvingDataSource to resolve URIs asynchronously in the background AFTER opening the player instantly!
    }

    /**
     * Play an online song and set queue context.
     */
    fun playOnlineSongWithQueue(song: OnlineSong, queue: List<OnlineSong>, index: Int) {
        _uiState.update { 
            it.copy(
                onlineQueue = queue,
                currentOnlineIndex = index
            )
        }
        playOnlineSong(song)

        // Fire-and-forget trigger informing ExploreFragment to hand over data to main player IMMEDIATELY without waiting.
        _playbackTriggerEvent.tryEmit(Unit)

        // PREFETCH NEXT SONG TO WARM UP THE CACHE INSTANTLY!
        if (index + 1 < queue.size) {
            val nextSong = queue[index + 1]
            YouTubeMusic.prefetchStream(nextSong.videoId)
        }
    }

    fun addToQueueNext(song: OnlineSong) {
        val currentQueue = _uiState.value.onlineQueue.toMutableList()
        val currentIndex = _uiState.value.currentOnlineIndex
        currentQueue.add(currentIndex + 1, song)
        _uiState.update { it.copy(onlineQueue = currentQueue) }
    }

    fun addToQueueLast(song: OnlineSong) {
        val currentQueue = _uiState.value.onlineQueue.toMutableList()
        currentQueue.add(song)
        _uiState.update { it.copy(onlineQueue = currentQueue) }
    }

    fun updateActiveSongAndIndex(index: Int, song: OnlineSong?) {
        _uiState.update { 
            it.copy(
                currentOnlineIndex = index,
                currentOnlineSong = song
            )
        }
    }

    // ========== Detail Pages ==========

    fun loadAlbum(browseId: String) {
        _uiState.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.album(browseId)
                result.onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            albumDetail = res,
                            detailStack = it.detailStack + DetailType.ALBUM
                        )
                    }
                    prefetchUrls(listOfNotNull(res.album.thumbnailUrl) + res.songs.map { it.thumbnailUrl })
                }
                result.onFailure { _uiState.update { it.copy(error = "Failed to load album") } }
            } finally {
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun loadArtist(browseId: String, fallbackThumbnailUrl: String? = null) {
        _uiState.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.artist(browseId)
                result.onSuccess { res ->
                    _uiState.update { state ->
                        val correctedThumbnailUrl = res.artist.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: fallbackThumbnailUrl
                        val updatedArtist = res.artist.copy(thumbnailUrl = correctedThumbnailUrl)
                        state.copy(
                            artistDetail = res.copy(artist = updatedArtist),
                            detailStack = state.detailStack + DetailType.ARTIST
                        )
                    }
                    prefetchUrls(listOfNotNull(res.artist.thumbnailUrl))
                    prefetchThumbnails(res.sections.flatMap { it.items.take(4) })
                }
                result.onFailure { _uiState.update { it.copy(error = "Failed to load artist") } }
            } finally {
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun loadPlaylist(playlistId: String) {
        _uiState.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.playlist(playlistId)
                result.onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            playlistDetail = res,
                            detailStack = it.detailStack + DetailType.PLAYLIST
                        )
                    }
                    prefetchUrls(listOfNotNull(res.playlist.thumbnailUrl) + res.songs.map { it.thumbnailUrl })
                }
                result.onFailure { _uiState.update { it.copy(error = "Failed to load playlist") } }
            } finally {
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun loadSectionDetails(browseId: String, params: String?, fallbackTitle: String = "Section") {
        _uiState.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.section(browseId, params)
                result.onSuccess { res ->
                    _uiState.update {
                        val correctedTitle = if (res.title == "Section") fallbackTitle else res.title
                        it.copy(
                            sectionDetail = res.copy(title = correctedTitle),
                            detailStack = it.detailStack + DetailType.SECTION
                        )
                    }
                    prefetchThumbnails(res.items.take(12))
                }
                result.onFailure { _uiState.update { it.copy(error = "Failed to load section") } }
            } finally {
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun loadGenres() {
        _uiState.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.moodsAndGenres()
                result.onSuccess { sections ->
                    _uiState.update {
                        it.copy(
                            genresSections = sections,
                            detailStack = it.detailStack + DetailType.GENRES
                        )
                    }
                }
                result.onFailure {
                    _uiState.update { it.copy(error = "Failed to load Moods & Genres") }
                }
            } finally {
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun loadMood(title: String, browseId: String, params: String?) {
        _uiState.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.moodCategory(browseId, params)
                result.onSuccess { homePage ->
                    _uiState.update {
                        it.copy(
                            moodDetail = MoodDetail(title, homePage.sections),
                            detailStack = it.detailStack + DetailType.MOOD
                        )
                    }
                }
                result.onFailure {
                    _uiState.update { it.copy(error = "Failed to load mood category details") }
                }
            } finally {
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }
    fun popDetailStack() {
        _uiState.update { state ->
            if (state.detailStack.isEmpty()) return@update state
            val nextStack = state.detailStack.dropLast(1)
            val popped = state.detailStack.last()
            when (popped) {
                DetailType.ALBUM -> state.copy(albumDetail = null, detailStack = nextStack)
                DetailType.PLAYLIST -> state.copy(playlistDetail = null, detailStack = nextStack)
                DetailType.ARTIST -> state.copy(artistDetail = null, detailStack = nextStack)
                DetailType.SECTION -> state.copy(sectionDetail = null, detailStack = nextStack)
                DetailType.GENRES -> state.copy(genresSections = emptyList(), detailStack = nextStack)
                DetailType.MOOD -> state.copy(moodDetail = null, detailStack = nextStack)
            }
        }
    }

    fun resetToHome() {
        _uiState.update { state ->
            state.copy(
                albumDetail = null,
                artistDetail = null,
                playlistDetail = null,
                sectionDetail = null,
                moodDetail = null,
                genresSections = emptyList(),
                detailStack = emptyList(),
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                isLoadingSuggestions = false
            )
        }
        cameFromLibrary = false
    }
    // ========== Start Radio ==========

    fun startRadio(videoId: String) {
        _uiState.update { it.copy(isLoadingStream = true) }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.startRadio(videoId = videoId)
                result.onSuccess { radioSongs ->
                    if (radioSongs.isNotEmpty()) {
                        playOnlineSongWithQueue(radioSongs.first(), radioSongs, 0)
                    }
                }
                result.onFailure { 
                    _uiState.update { it.copy(error = "Failed to start radio") }
                }
            } finally {
                _uiState.update { it.copy(isLoadingStream = false) }
            }
        }
    }

    fun startRadioForItem(item: SearchItem) {
        _uiState.update { it.copy(isLoadingStream = true) }
        viewModelScope.launch {
            try {
                val (vId, pId) = when (item) {
                    is SearchItem.TopResult -> {
                        when {
                            item.song != null -> item.song.videoId to "RDAMVM${item.song.videoId}"
                            item.playlist != null -> null to "RDAMPL${item.playlist.playlistId}"
                            item.album != null -> null to "RDAMPL${item.album.playlistId?.takeIf { it.isNotEmpty() } ?: item.album.browseId}"
                            item.artist != null -> null to "RDAMPR${item.artist.browseId}"
                            else -> null to null
                        }
                    }
                    is SearchItem.Header -> null to null
                    is SearchItem.Song -> item.song.videoId to "RDAMVM${item.song.videoId}"
                    is SearchItem.Playlist -> null to "RDAMPL${item.playlist.playlistId}"
                    is SearchItem.Album -> null to "RDAMPL${item.album.playlistId?.takeIf { it.isNotEmpty() } ?: item.album.browseId}"
                    is SearchItem.Artist -> null to "RDAMPR${item.artist.browseId}"
                }
                
                if (vId == null && pId == null) return@launch

                val result = YouTubeMusic.startRadio(videoId = vId, playlistId = pId)
                result.onSuccess { radioSongs ->
                    if (radioSongs.isNotEmpty()) {
                        playOnlineSongWithQueue(radioSongs.first(), radioSongs, 0)
                    } else {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("No radio station available")
                    }
                }
                result.onFailure { 
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to start radio")
                }
            } finally {
                _uiState.update { it.copy(isLoadingStream = false) }
            }
        }
    }

    fun saveItemToLibrary(item: SearchItem) {
        viewModelScope.launch {
            try {
                val result = when (item) {
                    is SearchItem.TopResult -> {
                        when {
                            item.song != null -> YouTubeMusic.updateLikeStatus(item.song.videoId, YouTubeMusic.LikeStatus.LIKE)
                            item.playlist != null -> YouTubeMusic.updateLikeStatus(item.playlist.playlistId, YouTubeMusic.LikeStatus.LIKE)
                            item.album != null -> YouTubeMusic.updateLikeStatus(item.album.playlistId ?: item.album.browseId, YouTubeMusic.LikeStatus.LIKE)
                            item.artist != null -> YouTubeMusic.subscribeArtist(item.artist.browseId)
                            else -> return@launch
                        }
                    }
                    is SearchItem.Header -> return@launch
                    is SearchItem.Song -> YouTubeMusic.updateLikeStatus(item.song.videoId, YouTubeMusic.LikeStatus.LIKE)
                    is SearchItem.Playlist -> YouTubeMusic.updateLikeStatus(item.playlist.playlistId, YouTubeMusic.LikeStatus.LIKE)
                    is SearchItem.Album -> YouTubeMusic.updateLikeStatus(item.album.playlistId ?: item.album.browseId, YouTubeMusic.LikeStatus.LIKE)
                    is SearchItem.Artist -> YouTubeMusic.subscribeArtist(item.artist.browseId)
                }
                result.onSuccess {
                    val name = when(item) {
                        is SearchItem.TopResult -> item.title
                        is SearchItem.Header -> ""
                        is SearchItem.Song -> item.song.title
                        is SearchItem.Playlist -> item.playlist.title
                        is SearchItem.Album -> item.album.title
                        is SearchItem.Artist -> item.artist.title
                    }
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("'$name' saved to Library!")
                }.onFailure {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to save to Library")
                }
            } catch (e: Exception) {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Error saving to Library")
            }
        }
    }

    // ========== YouTube Playlist Management ==========

    fun subscribeToArtist(channelId: String) {
        // Optimistically update UI state
        _uiState.update { state ->
            if (state.artistDetail?.artist?.browseId == channelId) {
                state.copy(
                    artistDetail = state.artistDetail.copy(
                        artist = state.artistDetail.artist.copy(isSubscribed = true)
                    )
                )
            } else {
                state
            }
        }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.subscribeArtist(channelId)
                result.onSuccess {
                    Log.d(TAG, "Subscribed to artist $channelId")
                    _subscriptionChanged.value = true
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = true)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Subscribe failed, reverting", e)
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = false)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subscribe error", e)
            }
        }
    }

    fun unsubscribeFromArtist(channelId: String) {
        // Optimistically update UI state
        _uiState.update { state ->
            if (state.artistDetail?.artist?.browseId == channelId) {
                state.copy(
                    artistDetail = state.artistDetail.copy(
                        artist = state.artistDetail.artist.copy(isSubscribed = false)
                    )
                )
            } else {
                state
            }
        }
        viewModelScope.launch {
            try {
                val result = YouTubeMusic.unsubscribeArtist(channelId)
                result.onSuccess {
                    Log.d(TAG, "Unsubscribed from artist $channelId")
                    _subscriptionChanged.value = true
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = false)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Unsubscribe failed, reverting", e)
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = true)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unsubscribe error", e)
            }
        }
    }



    fun clearError() {
        _uiState.update { 
            it.copy(
                error = null,
                streamError = null
            )
        }
    }

    // ========== Speed Dial & Not Interested ==========

    private val gson = com.google.gson.Gson()

    fun getPinnedSpeedDialSongs(): List<OnlineSong> {
        val json = prefs.getString("speed_dial_songs_json", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<OnlineSong>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePinnedSpeedDialSongs(songs: List<OnlineSong>) {
        prefs.edit { putString("speed_dial_songs_json", gson.toJson(songs)) }
        _uiState.update { state -> state.copy(pinnedSpeedDialIds = songs.map { it.videoId }.toSet()) }
    }

    fun pinToSpeedDial(song: OnlineSong) {
        val current = getPinnedSpeedDialSongs().toMutableList()
        val exists = current.any { it.videoId == song.videoId }
        if (exists) {
            current.removeAll { it.videoId == song.videoId }
            viewModelScope.launch {
                try {
                    YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.INDIFFERENT)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send indifferent status to YT", e)
                }
            }
        } else {
            current.add(0, song)
            viewModelScope.launch {
                try {
                    YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.LIKE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send like status to YT", e)
                }
            }
        }
        savePinnedSpeedDialSongs(current)
        
        viewModelScope.launch {
            val mergedSections = fetchMergedHomeFeed()
            _uiState.update { it.copy(homeSections = mergedSections) }
        }
    }

    fun setNotInterested(song: OnlineSong) {
        val current = _uiState.value.notInterestedIds.toMutableSet()
        current.add(song.videoId)
        _uiState.update { it.copy(notInterestedIds = current) }
        
        prefs.edit { putStringSet("not_interested_ids", current) }

        viewModelScope.launch {
            try {
                YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.DISLIKE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send not interested/dislike feedback to YouTube Music", e)
            }
            
            val mergedSections = fetchMergedHomeFeed()
            _uiState.update { it.copy(homeSections = mergedSections) }
        }

        val filteredSearchResults = _uiState.value.searchResults.filterNot { item ->
            item is SearchItem.Song && item.song.videoId == song.videoId
        }
        
        val updatedAlbumDetail = _uiState.value.albumDetail?.let { album ->
            album.copy(songs = album.songs.filterNot { it.videoId == song.videoId })
        }
        val updatedPlaylistDetail = _uiState.value.playlistDetail?.let { playlist ->
            playlist.copy(songs = playlist.songs.filterNot { it.videoId == song.videoId })
        }

        _uiState.update { state ->
            state.copy(
                searchResults = filteredSearchResults,
                albumDetail = updatedAlbumDetail,
                playlistDetail = updatedPlaylistDetail
            )
        }
    }

    private fun prefetchThumbnails(items: List<SearchItem>) {
        val urls = items.mapNotNull { item ->
            when (item) {
                is SearchItem.TopResult -> item.thumbnailUrl
                is SearchItem.Header -> null
                is SearchItem.Song -> item.song.thumbnailUrl
                is SearchItem.Album -> item.album.thumbnailUrl
                is SearchItem.Artist -> item.artist.thumbnailUrl
                is SearchItem.Playlist -> item.playlist.thumbnailUrl
            }
        }
        prefetchUrls(urls)
    }

    private fun prefetchUrls(urls: List<String?>) {
        val applicationContext = getApplication<Application>()
        viewModelScope.launch {
            val loader = applicationContext.imageLoader
            urls.forEach { url ->
                if (!url.isNullOrBlank()) {
                    val req = ImageRequest.Builder(applicationContext)
                        .data(url)
                        .build()
                    loader.enqueue(req)
                }
            }
        }
    }
}

@Stable
data class ExploreUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
    val homeSections: List<HomeSection> = emptyList(),
    val isLoadingHome: Boolean = false,
    val isLoadingStream: Boolean = false,
    val currentMood: String? = null,
    val currentOnlineSong: OnlineSong? = null,
    val error: String? = null,
    val albumDetail: AlbumPage? = null,
    val artistDetail: ArtistPage? = null,
    val playlistDetail: PlaylistPage? = null,
    val sectionDetail: HomeSection? = null,
    val moodDetail: MoodDetail? = null,
    val genresSections: List<HomeSection> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val homeMoods: List<String> = emptyList(),
    val searchFilter: SearchFilter? = null,
    val streamUrl: String? = null,
    val streamError: String? = null,
    val onlineQueue: List<OnlineSong> = emptyList(),
    val currentOnlineIndex: Int = -1,
    val isLoadingMoreHome: Boolean = false,
    val pinnedSpeedDialIds: Set<String> = emptySet(),
    val notInterestedIds: Set<String> = emptySet(),
    val detailStack: List<DetailType> = emptyList()
)
