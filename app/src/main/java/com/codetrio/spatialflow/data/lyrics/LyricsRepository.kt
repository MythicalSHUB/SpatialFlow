package com.codetrio.spatialflow.data.lyrics

import android.content.Context
import com.codetrio.spatialflow.data.lyrics.engine.LyricsFetchManager

/**
 * Facade for the lyrics fetch system.
 * Delegates to LyricsFetchManager internally while maintaining
 * backward-compatible interface.
 *
 * This replaces the old implementation that used CountDownLatch +
 * SharedPreferences.
 */
class LyricsRepository private constructor(context: Context) {

    private val fetchManager: LyricsFetchManager = LyricsFetchManager.getInstance(context)

    companion object {
        private const val TAG = "LyricsRepository"

        @Volatile
        private var instance: LyricsRepository? = null

        @JvmStatic
        fun getInstance(context: Context): LyricsRepository {
            return instance ?: synchronized(this) {
                instance ?: LyricsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Legacy callback interface for backward compatibility.
     */
    interface LyricsCallback {
        fun onSuccess(response: LrcLibResponse)
        fun onError(t: Throwable)
    }

    /**
     * New extended callback with full lifecycle support.
     */
    interface ExtendedLyricsCallback {
        fun onLyricsFound(result: LyricsResult)
        fun onLyricsUpgraded(betterResult: LyricsResult)
        fun onLyricsNotFound(reason: String)
        fun onInstrumental()
        fun onSearchStatus(message: String)
        fun onProviderResult(providerName: String, result: LyricsResult?) {}
    }

    /**
     * Fetch lyrics using the legacy callback interface.
     * Maintained for backward compatibility.
     */
    fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSeconds: Float,
        callback: LyricsCallback
    ) {
        val durationMs = (durationSeconds * 1000).toLong()

        fetchManager.fetchLyrics(trackName, artistName, albumName, durationMs, null,
            object : LyricsFetchManager.LyricsCallback {
                override fun onLyricsFound(result: LyricsResult) {
                    result.toLrcLibResponse().let { callback.onSuccess(it) }
                }

                override fun onLyricsUpgraded(betterResult: LyricsResult) {
                    betterResult.toLrcLibResponse().let { callback.onSuccess(it) }
                }

                override fun onLyricsNotFound(reason: String) {
                    callback.onError(Exception(reason))
                }

                override fun onInstrumental() {
                    val response = LrcLibResponse(
                        id = 0,
                        name = null,
                        artistName = null,
                        albumName = null,
                        duration = 0f,
                        instrumental = true,
                        plainLyrics = null,
                        syncedLyrics = null
                    )
                    callback.onSuccess(response)
                }

                override fun onSearchStatus(message: String) {
                    // Legacy callback doesn't support status updates
                }
            })
    }

    /**
     * Fetch lyrics with the new extended callback — full lifecycle support.
     */
    fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        filePath: String?,
        callback: ExtendedLyricsCallback,
        videoId: String? = null
    ) {
        fetchManager.fetchLyrics(title, artist, album, durationMs, filePath,
            object : LyricsFetchManager.LyricsCallback {
                override fun onLyricsFound(result: LyricsResult) {
                    callback.onLyricsFound(result)
                }

                override fun onLyricsUpgraded(betterResult: LyricsResult) {
                    callback.onLyricsUpgraded(betterResult)
                }

                override fun onLyricsNotFound(reason: String) {
                    callback.onLyricsNotFound(reason)
                }

                override fun onInstrumental() {
                    callback.onInstrumental()
                }

                override fun onSearchStatus(message: String) {
                    callback.onSearchStatus(message)
                }

                override fun onProviderResult(providerName: String, result: LyricsResult?) {
                    callback.onProviderResult(providerName, result)
                }
            },
            videoId
        )
    }

    /**
     * Retry lyrics — clears negative cache and forces full refetch.
     */
    fun retryLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        filePath: String?,
        callback: ExtendedLyricsCallback,
        videoId: String? = null
    ) {
        fetchManager.retryLyrics(title, artist, album, durationMs, filePath,
            object : LyricsFetchManager.LyricsCallback {
                override fun onLyricsFound(result: LyricsResult) {
                    callback.onLyricsFound(result)
                }

                override fun onLyricsUpgraded(betterResult: LyricsResult) {
                    callback.onLyricsUpgraded(betterResult)
                }

                override fun onLyricsNotFound(reason: String) {
                    callback.onLyricsNotFound(reason)
                }

                override fun onInstrumental() {
                    callback.onInstrumental()
                }

                override fun onSearchStatus(message: String) {
                    callback.onSearchStatus(message)
                }

                override fun onProviderResult(providerName: String, result: LyricsResult?) {
                    callback.onProviderResult(providerName, result)
                }
            },
            videoId
        )
    }

    /**
     * Cancel the current fetch (e.g., when song changes).
     */
    fun cancelCurrent() {
        fetchManager.cancelCurrent()
    }
}
