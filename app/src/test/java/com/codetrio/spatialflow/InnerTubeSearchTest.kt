package com.codetrio.spatialflow

import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeSearchTest {

    @Test
    fun testYouTubeMusicSearchFacade() {
        runBlocking {
            val query = "Taylor Swift"
            val parsedResult = YouTubeMusic.search(query)
            
            assertTrue("YouTube Music Search request should be successful", parsedResult.isSuccess)
            
            parsedResult.onSuccess { searchResult ->
                assertTrue("Should parse at least one search result item", searchResult.items.isNotEmpty())
                
                // Validate that we have multiple item types parsed (songs, albums, artists, etc.)
                val songCount = searchResult.items.count { it is com.codetrio.spatialflow.data.innertube.SearchItem.Song }
                val artistCount = searchResult.items.count { it is com.codetrio.spatialflow.data.innertube.SearchItem.Artist }
                val playlistCount = searchResult.items.count { it is com.codetrio.spatialflow.data.innertube.SearchItem.Playlist }
                
                println("Test search found: $songCount songs, $artistCount artists, $playlistCount playlists.")
                assertTrue("Should successfully parse songs in results", songCount > 0)
            }
        }
    }
}
