package com.codetrio.spatialflow.viewmodel

import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.ui.UiText

/**
 * User actions (intents) supported by ExploreViewModel under MVI pattern.
 */
sealed interface ExploreAction {
    data class OnSearchQueryChange(val query: String) : ExploreAction
    data class OnSearchFilterSelect(val filter: SearchFilter?) : ExploreAction
    data class OnSearchSubmit(val query: String) : ExploreAction
    data object OnClearSearch : ExploreAction
    data class OnSongClick(val song: OnlineSong) : ExploreAction
    data class OnSongWithQueueClick(val song: OnlineSong, val queue: List<OnlineSong>, val index: Int) : ExploreAction
    data class OnAlbumClick(val album: OnlineAlbum) : ExploreAction
    data class OnAlbumBrowseIdClick(val browseId: String) : ExploreAction
    data class OnArtistClick(val artist: OnlineArtist) : ExploreAction
    data class OnArtistBrowseIdClick(val browseId: String) : ExploreAction
    data class OnPlaylistClick(val playlist: OnlinePlaylist) : ExploreAction
    data class OnPlaylistIdClick(val playlistId: String) : ExploreAction
    data class OnSectionClick(val browseId: String, val params: String?, val title: String) : ExploreAction
    data class OnMoodClick(val title: String, val browseId: String, val params: String?) : ExploreAction
    data class OnSetMood(val mood: String?) : ExploreAction
    data object OnRefreshHomeFeed : ExploreAction
    data object OnLoadMoreHomeSections : ExploreAction
    data object OnLoadMoreSearchResults : ExploreAction
    data object OnPopDetail : ExploreAction
    data object OnResetToHome : ExploreAction
    data class OnHandleDeepLink(val url: String) : ExploreAction
}

/**
 * One-time side-effect events emitted by ExploreViewModel.
 */
sealed interface ExploreEvent {
    data class ShowSnackbar(val message: UiText) : ExploreEvent
    data object TriggerInstantPlayback : ExploreEvent
}
