package com.codetrio.spatialflow.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.codetrio.spatialflow.model.SongItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persistence utility responsible for saving and restoring application playback state 
 * (current song, full queue history, and exact playback head position).
 */
object PlaybackStateManager {
    
    private const val TAG = "PlaybackStateManager"
    private const val PREF_NAME = "playback_state"
    
    private const val KEY_CURRENT_SONG = "current_song_json"
    private const val KEY_CURRENT_INDEX = "current_index"
    private const val KEY_PLAYBACK_POS = "playback_position_ms"
    private const val KEY_QUEUE = "playback_queue_json"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save complete playback context snapshots to disk.
     */
    @JvmStatic
    fun saveState(
        context: Context, 
        currentSong: SongItem?, 
        positionMs: Long,
        queue: List<SongItem>?,
        currentIndex: Int
    ) {
        try {
            getPrefs(context).edit {

                if (currentSong != null) {
                    putString(KEY_CURRENT_SONG, gson.toJson(currentSong))
                } else {
                    remove(KEY_CURRENT_SONG)
                }

                putLong(KEY_PLAYBACK_POS, positionMs)
                putInt(KEY_CURRENT_INDEX, currentIndex)

                if (!queue.isNullOrEmpty()) {
                    // Serialize queue to disk safely
                    putString(KEY_QUEUE, gson.toJson(queue))
                } else {
                    remove(KEY_QUEUE)
                }

            }
            Log.d(TAG, "Saved state: pos=$positionMs ms, queueSize=${queue?.size ?: 0}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playback state: ${e.message}")
        }
    }
    
    /**
     * Quick-save ONLY the head position to prevent heavy serialize passes on fast head-updates.
     */
    @JvmStatic
    fun savePosition(context: Context, positionMs: Long) {
        getPrefs(context).edit { putLong(KEY_PLAYBACK_POS, positionMs) }
    }

    @JvmStatic
    fun getLastSong(context: Context): SongItem? {
        val json = getPrefs(context).getString(KEY_CURRENT_SONG, null) ?: return null
        return try {
            gson.fromJson(json, SongItem::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding song: ${e.message}")
            null
        }
    }

    @JvmStatic
    fun getLastPosition(context: Context): Long {
        return getPrefs(context).getLong(KEY_PLAYBACK_POS, 0L)
    }

    @JvmStatic
    fun getLastQueue(context: Context): List<SongItem> {
        val json = getPrefs(context).getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SongItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding queue: ${e.message}")
            emptyList()
        }
    }

    @JvmStatic
    fun getLastIndex(context: Context): Int {
        return getPrefs(context).getInt(KEY_CURRENT_INDEX, 0)
    }
    
    @JvmStatic
    fun clear(context: Context) {
        getPrefs(context).edit { clear() }
    }
}
