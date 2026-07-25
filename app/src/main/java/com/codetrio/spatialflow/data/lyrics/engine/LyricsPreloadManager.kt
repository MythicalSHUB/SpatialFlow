package com.codetrio.spatialflow.data.lyrics.engine

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsRepository
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Preloads lyrics for upcoming tracks in the playback queue
 * to eliminate loading spinners during track transitions.
 */
class LyricsPreloadManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val repository: LyricsRepository = LyricsRepository.getInstance(appContext)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "LyricsPreloadManager"

        @Volatile
        private var instance: LyricsPreloadManager? = null

        fun getInstance(context: Context): LyricsPreloadManager {
            return instance ?: synchronized(this) {
                instance ?: LyricsPreloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Preloads upcoming items in queue (up to maxCount).
     */
    fun preloadUpcoming(queue: List<SongItem>, currentIndex: Int, maxCount: Int = 3) {
        if (queue.isEmpty() || currentIndex < 0) return

        val upcoming = queue.drop(currentIndex + 1).take(maxCount)
        scope.launch {
            for (song in upcoming) {
                try {
                    val title = song.title
                    val artist = song.artist
                    val durationMs = song.duration
                    
                    Log.d(TAG, "Preloading lyrics for: $title - $artist")
                    repository.fetchLyrics(
                        title = title,
                        artist = artist,
                        album = null,
                        durationMs = durationMs,
                        filePath = null,
                        callback = object : LyricsRepository.ExtendedLyricsCallback {
                            override fun onLyricsFound(result: LyricsResult) {
                                Log.d(TAG, "Preloaded lyrics hit for: $title")
                            }

                            override fun onLyricsUpgraded(betterResult: LyricsResult) {}
                            override fun onLyricsNotFound(reason: String) {}
                            override fun onInstrumental() {}
                            override fun onSearchStatus(message: String) {}
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed preloading item: ${e.message}")
                }
            }
        }
    }
}
