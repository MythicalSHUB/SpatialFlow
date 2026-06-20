@file:Suppress("DEPRECATION")
@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)

package com.codetrio.spatialflow.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codetrio.spatialflow.BuildConfig
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.player.SleepTimerBottomSheet
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

private val SmoothSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    private val context: Context = application.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Dark Mode ────────────────────────────────────────────────────────────
    private val _darkMode = MutableStateFlow(
        prefs.getString("theme_mode", "system").let {
            if (it == "system") prefs.getBoolean(KEY_DARK_MODE, true) else it == "dark"
        }
    )
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        prefs.edit {putBoolean(KEY_DARK_MODE, enabled)}
        prefs.edit {putString("theme_mode", if (enabled) "dark" else "light")}
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (enabled) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ── YouTube Music Cookies ──────────────────────────────────────────────────
    private val _ytCookies = MutableStateFlow(prefs.getString("yt_cookies", null))
    val ytCookies: StateFlow<String?> = _ytCookies.asStateFlow()

    fun setYtCookies(cookies: String?) {
        _ytCookies.value = cookies
        if (cookies != null) {
            prefs.edit {putString("yt_cookies", cookies)}
            com.codetrio.spatialflow.data.innertube.InnerTubeClient.cookie = cookies
        } else {
            prefs.edit {remove("yt_cookies")}
            com.codetrio.spatialflow.data.innertube.InnerTubeClient.cookie = null
        }
    }

    // ── Haptics ──────────────────────────────────────────────────────────────
    private val _vibrationStrength =
        MutableStateFlow(prefs.getFloat(KEY_VIBRATION_STRENGTH, 80f))
    val vibrationStrength: StateFlow<Float> = _vibrationStrength.asStateFlow()

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    val hasHaptics: Boolean = vibrator?.hasVibrator() == true

    fun setVibrationStrength(strength: Float) {
        _vibrationStrength.value = strength
        prefs.edit {putFloat(KEY_VIBRATION_STRENGTH, strength)}
        if (strength > 0 && hasHaptics && vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (strength / 100f * 255).toInt().coerceIn(1, 255)
                vibrator.vibrate(VibrationEffect.createOneShot(50, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    // ── Crossfade ────────────────────────────────────────────────────────────
    private val _crossfadeEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_ENABLED, false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDuration =
        MutableStateFlow(prefs.getFloat(KEY_CROSSFADE_DURATION, 3f))
    val crossfadeDuration: StateFlow<Float> = _crossfadeDuration.asStateFlow()

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        prefs.edit {putBoolean(KEY_CROSSFADE_ENABLED, enabled)}
    }

    fun setCrossfadeDuration(duration: Float) {
        _crossfadeDuration.value = duration
        prefs.edit {putFloat(KEY_CROSSFADE_DURATION, duration)}
    }

    // ── Volume Normalization ─────────────────────────────────────────────────
    private val _volumeNormalizationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_NORMALIZATION_ENABLED, false))
    val volumeNormalizationEnabled: StateFlow<Boolean> = _volumeNormalizationEnabled.asStateFlow()

    private val _targetLufs = MutableStateFlow(prefs.getFloat(KEY_TARGET_LUFS, -14f))
    val targetLufs: StateFlow<Float> = _targetLufs.asStateFlow()

    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        _volumeNormalizationEnabled.value = enabled
        prefs.edit {putBoolean(KEY_VOLUME_NORMALIZATION_ENABLED, enabled)}
    }

    fun setTargetLufs(lufs: Float) {
        _targetLufs.value = lufs
        prefs.edit {putFloat(KEY_TARGET_LUFS, lufs)}
    }

    // ── Audio Focus ──────────────────────────────────────────────────────────
    private val _audioFocus = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_FOCUS, true))
    val audioFocus: StateFlow<Boolean> = _audioFocus.asStateFlow()

    fun setAudioFocus(enabled: Boolean) {
        _audioFocus.value = enabled
        prefs.edit {putBoolean(KEY_AUDIO_FOCUS, enabled)}
    }

    // ── Pure AMOLED Black ──────────────────────────────────────────────────
    private val _amoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_BLACK, false))
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    fun setAmoledBlack(enabled: Boolean) {
        _amoledBlack.value = enabled
        prefs.edit {putBoolean(KEY_AMOLED_BLACK, enabled)}
    }

    // ── Hide Nav Labels ──────────────────────────────────────────────────
    private val _hideNavLabels = MutableStateFlow(prefs.getBoolean("hide_nav_labels", false))
    val hideNavLabels: StateFlow<Boolean> = _hideNavLabels.asStateFlow()

    fun setHideNavLabels(hide: Boolean) {
        _hideNavLabels.value = hide
        prefs.edit {putBoolean("hide_nav_labels", hide)}
    }

    // ── Dynamic Nav Style ──────────────────────────────────────────────────
    private val _dynamicNavStyle = MutableStateFlow(prefs.getBoolean("dynamic_nav_style", false))
    val dynamicNavStyle: StateFlow<Boolean> = _dynamicNavStyle.asStateFlow()

    fun setDynamicNavStyle(enabled: Boolean) {
        _dynamicNavStyle.value = enabled
        prefs.edit {putBoolean("dynamic_nav_style", enabled)}
    }

    // ── Dynamic Album Theme ────────────────────────────────────────────────
    private val _dynamicAlbumTheme = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_ALBUM_THEME, false))
    val dynamicAlbumTheme: StateFlow<Boolean> = _dynamicAlbumTheme.asStateFlow()

    fun setDynamicAlbumTheme(enabled: Boolean) {
        _dynamicAlbumTheme.value = enabled
        prefs.edit {putBoolean(KEY_DYNAMIC_ALBUM_THEME, enabled)}
    }

    // ── Ignore Short Audio ──────────────────────────────────────────────────
    private val _ignoreShortAudio = MutableStateFlow(prefs.getBoolean(KEY_IGNORE_SHORT_AUDIO, false))
    val ignoreShortAudio: StateFlow<Boolean> = _ignoreShortAudio.asStateFlow()

    private val _ignoreShortAudioDuration = MutableStateFlow(prefs.getFloat(KEY_IGNORE_SHORT_AUDIO_DURATION, 30f))
    val ignoreShortAudioDuration: StateFlow<Float> = _ignoreShortAudioDuration.asStateFlow()

    fun setIgnoreShortAudio(enabled: Boolean) {
        _ignoreShortAudio.value = enabled
        prefs.edit {putBoolean(KEY_IGNORE_SHORT_AUDIO, enabled)}
    }

    fun setIgnoreShortAudioDuration(duration: Float) {
        _ignoreShortAudioDuration.value = duration
        prefs.edit {putFloat(KEY_IGNORE_SHORT_AUDIO_DURATION, duration)}
    }

    // ── Data Saver ──────────────────────────────────────────────────────────
    private val _dataSaver = MutableStateFlow(prefs.getBoolean(KEY_DATA_SAVER, false))
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        prefs.edit {putBoolean(KEY_DATA_SAVER, enabled)}
        com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.clearCache()
    }

    // ── Audio Quality ────────────────────────────────────────────────────────
    private val _audioQuality = MutableStateFlow(prefs.getString(KEY_AUDIO_QUALITY, "High") ?: "High")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        prefs.edit {putString(KEY_AUDIO_QUALITY, quality)}
        com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.clearCache()
    }

    // ── Pause Listening History ────────────────────────────────────────────────
    private val _pauseHistory = MutableStateFlow(prefs.getBoolean("pause_history", false))
    val pauseHistory: StateFlow<Boolean> = _pauseHistory.asStateFlow()

    fun setPauseHistory(pause: Boolean) {
        _pauseHistory.value = pause
        prefs.edit {putBoolean("pause_history", pause)}
    }

    // ── Haptics Granular ─────────────────────────────────────────────────────
    private val _hapticPlayPause = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_PLAY_PAUSE, true))
    val hapticPlayPause: StateFlow<Boolean> = _hapticPlayPause.asStateFlow()

    private val _hapticQueue = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_QUEUE, true))
    val hapticQueue: StateFlow<Boolean> = _hapticQueue.asStateFlow()

    private val _hapticFavorite = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FAVORITE, true))
    val hapticFavorite: StateFlow<Boolean> = _hapticFavorite.asStateFlow()

    fun setHapticPlayPause(enabled: Boolean) {
        _hapticPlayPause.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_PLAY_PAUSE, enabled)}
    }

    fun setHapticQueue(enabled: Boolean) {
        _hapticQueue.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_QUEUE, enabled)}
    }

    fun setHapticFavorite(enabled: Boolean) {
        _hapticFavorite.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_FAVORITE, enabled)}
    }



    // ── Library Paths ────────────────────────────────────────────────────────
    private val _libraryPaths = MutableStateFlow(
        (prefs.getString(KEY_LIBRARY_PATHS, "") ?: "").split("||").filter { it.isNotEmpty() }
    )
    val libraryPaths: StateFlow<List<String>> = _libraryPaths.asStateFlow()

    fun addLibraryPath(path: String) {
        val current = _libraryPaths.value
        if (current.contains(path)) return
        val newList = current + path
        _libraryPaths.value = newList
        prefs.edit {putString(KEY_LIBRARY_PATHS, newList.joinToString("||"))}
    }

    fun removeLibraryPath(path: String) {
        val current = _libraryPaths.value
        val newList = current.filter { it != path }
        _libraryPaths.value = newList
        prefs.edit {putString(KEY_LIBRARY_PATHS, newList.joinToString("||"))}
    }

    // ── Hidden Folders ────────────────────────────────────────────────────────
    private val _hiddenFolders = MutableStateFlow(
        (prefs.getString(KEY_HIDDEN_FOLDERS, "") ?: "").split("||").filter { it.isNotEmpty() }
    )
    val hiddenFolders: StateFlow<List<String>> = _hiddenFolders.asStateFlow()

    fun addHiddenFolder(path: String) {
        val current = _hiddenFolders.value
        if (current.contains(path)) return
        val newList = current + path
        _hiddenFolders.value = newList
        prefs.edit {putString(KEY_HIDDEN_FOLDERS, newList.joinToString("||"))}
    }

    fun removeHiddenFolder(path: String) {
        val current = _hiddenFolders.value
        val newList = current.filter { it != path }
        _hiddenFolders.value = newList
        prefs.edit {putString(KEY_HIDDEN_FOLDERS, newList.joinToString("||"))}
    }

    // ── Cache & Downloads ───────────────────────────────────────────────────
    private val _downloadFolder = MutableStateFlow(prefs.getString("download_folder", null))
    val downloadFolder: StateFlow<String?> = _downloadFolder.asStateFlow()

    fun setDownloadFolder(uri: String?) {
        _downloadFolder.value = uri
        if (uri != null) {
            prefs.edit {putString("download_folder", uri)}
        } else {
            prefs.edit {remove("download_folder")}
        }
    }

    // Sizes in MB. -1 or 0 could mean unlimited, or we can use large numbers. We'll use Int (MB). 0 = unlimited.
    private val _songCacheMaxSize = MutableStateFlow(prefs.getInt("song_cache_max_size", 1024)) // Default 1GB
    val songCacheMaxSize: StateFlow<Int> = _songCacheMaxSize.asStateFlow()

    fun setSongCacheMaxSize(sizeMb: Int) {
        _songCacheMaxSize.value = sizeMb
        prefs.edit {putInt("song_cache_max_size", sizeMb)}
    }

    private val _imageCacheMaxSize = MutableStateFlow(prefs.getInt("image_cache_max_size", 512)) // Default 500MB
    val imageCacheMaxSize: StateFlow<Int> = _imageCacheMaxSize.asStateFlow()

    fun setImageCacheMaxSize(sizeMb: Int) {
        _imageCacheMaxSize.value = sizeMb
        prefs.edit {putInt("image_cache_max_size", sizeMb)}
    }

    // ── Backup & Restore ──────────────────────────────────────────────────────
    fun exportBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settingsMap = prefs.all
                val favManager = com.codetrio.spatialflow.util.FavoritesManager(context)
                val favoritesList = favManager.favoriteIds.toList()

                val db = com.codetrio.spatialflow.data.db.MusicDatabase.getDatabase(context)
                val playlistDao = db.playlistDao()

                // Read playlists and songs using Flow.first() extension
                val playlists = playlistDao.getAllPlaylists().first()
                val playlistSongsList = mutableListOf<com.codetrio.spatialflow.data.db.PlaylistSongEntity>()
                playlists.forEach { playlist: com.codetrio.spatialflow.data.db.PlaylistEntity ->
                    val songs = playlistDao.getSongsForPlaylist(playlist.id).first()
                    playlistSongsList.addAll(songs)
                }

                val backupObject = com.google.gson.JsonObject().apply {
                    addProperty("version", 1)

                    val settingsObj = com.google.gson.JsonObject()
                    settingsMap.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> settingsObj.addProperty(key, value)
                            is Float -> settingsObj.addProperty(key, value)
                            is Int -> settingsObj.addProperty(key, value)
                            is Long -> settingsObj.addProperty(key, value)
                            is String -> settingsObj.addProperty(key, value)
                        }
                    }
                    add("settings", settingsObj)

                    val favArray = com.google.gson.JsonArray()
                    favoritesList.forEach { favArray.add(it) }
                    add("favorites", favArray)

                    val gson = com.google.gson.Gson()
                    add("playlists", gson.toJsonTree(playlists))
                    add("playlist_songs", gson.toJsonTree(playlistSongsList))
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(backupObject.toString().toByteArray())
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Backup exported successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to export backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to export: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Could not read backup file")

                val gson = com.google.gson.Gson()
                val backupObject = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)

                if (backupObject.has("settings")) {
                    val settingsObj = backupObject.getAsJsonObject("settings")
                    prefs.edit {
                        settingsObj.entrySet().forEach { (key, jsonElement) ->
                            if (jsonElement.isJsonPrimitive) {
                                val prim = jsonElement.asJsonPrimitive
                                if (prim.isBoolean) putBoolean(key, prim.asBoolean)
                                else if (prim.isNumber) {
                                    val num = prim.asNumber
                                    when (key) {
                                        KEY_VIBRATION_STRENGTH, KEY_CROSSFADE_DURATION, KEY_TARGET_LUFS -> putFloat(key, num.toFloat())
                                        else -> {
                                            try {
                                                putInt(key, num.toInt())
                                            } catch (_: Exception) {
                                                putFloat(key, num.toFloat())
                                            }
                                        }
                                    }
                                }
                                else if (prim.isString) putString(key, prim.asString)
                            }
                        }
                    }

                    _darkMode.value = prefs.getBoolean(KEY_DARK_MODE, true)
                    _amoledBlack.value = prefs.getBoolean(KEY_AMOLED_BLACK, false)
                    _dynamicAlbumTheme.value = prefs.getBoolean(KEY_DYNAMIC_ALBUM_THEME, false)
                    _vibrationStrength.value = prefs.getFloat(KEY_VIBRATION_STRENGTH, 80f)
                    _crossfadeEnabled.value = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
                    _crossfadeDuration.value = prefs.getFloat(KEY_CROSSFADE_DURATION, 3f)
                    _audioFocus.value = prefs.getBoolean(KEY_AUDIO_FOCUS, true)
                    _dataSaver.value = prefs.getBoolean(KEY_DATA_SAVER, false)
                    _audioQuality.value = prefs.getString(KEY_AUDIO_QUALITY, "High") ?: "High"
                    _pauseHistory.value = prefs.getBoolean("pause_history", false)
                    _downloadFolder.value = prefs.getString("download_folder", null)
                    _songCacheMaxSize.value = prefs.getInt("song_cache_max_size", 1024)
                    _imageCacheMaxSize.value = prefs.getInt("image_cache_max_size", 512)
                    _hapticPlayPause.value = prefs.getBoolean(KEY_HAPTIC_PLAY_PAUSE, true)
                    _hapticQueue.value = prefs.getBoolean(KEY_HAPTIC_QUEUE, true)
                    _hapticFavorite.value = prefs.getBoolean(KEY_HAPTIC_FAVORITE, true)
                    _volumeNormalizationEnabled.value = prefs.getBoolean(KEY_VOLUME_NORMALIZATION_ENABLED, false)
                    _targetLufs.value = prefs.getFloat(KEY_TARGET_LUFS, -14f)
                }

                if (backupObject.has("favorites")) {
                    val favArray = backupObject.getAsJsonArray("favorites")
                    val favManager = com.codetrio.spatialflow.util.FavoritesManager(context)
                    favArray.forEach { element ->
                        val idStr = element.asString
                        idStr.toLongOrNull()?.let { idLong ->
                            favManager.setFavorite(idLong, true)
                        }
                    }
                }

                val db = com.codetrio.spatialflow.data.db.MusicDatabase.getDatabase(context)
                val playlistDao = db.playlistDao()

                if (backupObject.has("playlists")) {
                    val typeTokenPlaylists = object :
                        com.google.gson.reflect.TypeToken<List<com.codetrio.spatialflow.data.db.PlaylistEntity>>() {}.type
                    val playlistsList: List<com.codetrio.spatialflow.data.db.PlaylistEntity> =
                        gson.fromJson(backupObject.get("playlists"), typeTokenPlaylists)

                    playlistsList.forEach { playlist ->
                        playlistDao.insertPlaylist(playlist)
                    }
                }

                if (backupObject.has("playlist_songs")) {
                    val typeTokenSongs = object : com.google.gson.reflect.TypeToken<List<com.codetrio.spatialflow.data.db.PlaylistSongEntity>>() {}.type
                    val songsList: List<com.codetrio.spatialflow.data.db.PlaylistSongEntity> = gson.fromJson(backupObject.get("playlist_songs"), typeTokenSongs)

                    songsList.forEach { song ->
                        playlistDao.insertPlaylistSong(song)
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Backup imported successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to import backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Import failed: ${e.localizedMessage}")
                }
            }
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────────
    private val _songCacheSize = MutableStateFlow("Calculating...")
    val songCacheSize: StateFlow<String> = _songCacheSize.asStateFlow()

    private val _imageCacheSize = MutableStateFlow("Calculating...")
    val imageCacheSize: StateFlow<String> = _imageCacheSize.asStateFlow()

    fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val imageCacheDir = File(context.cacheDir, "image_cache")
            val imageSize = getDirSize(imageCacheDir)
            
            var totalCacheSize = getDirSize(context.cacheDir)
            totalCacheSize += getDirSize(context.codeCacheDir)
            val songSize = (totalCacheSize - imageSize).coerceAtLeast(0L)

            _imageCacheSize.value = formatFileSize(imageSize)
            _songCacheSize.value = formatFileSize(songSize)
        }
    }

    fun clearSongCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name != "image_cache") {
                    deleteDir(file)
                }
            }
            deleteDir(context.codeCacheDir)
            calculateCacheSize()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun clearImageCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val imageCacheDir = File(context.cacheDir, "image_cache")
            deleteDir(imageCacheDir)
            calculateCacheSize()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isFile) file.length() else getDirSize(file)
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups =
            (log10(bytes.toDouble()) / log10(1024.0)).toInt()
                .coerceAtMost(units.size - 1)
        return DecimalFormat("#.##")
            .format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun deleteDir(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) deleteDir(file)
            file.delete()
        }
    }

    companion object {
        const val PREFS_NAME = "AppSettings"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        const val KEY_AUDIO_FOCUS = "audio_focus"
        const val KEY_LIBRARY_PATHS = "library_paths"
        const val KEY_HIDDEN_FOLDERS = "hidden_folders"
        const val KEY_AMOLED_BLACK = "amoled_black"
        const val KEY_IGNORE_SHORT_AUDIO = "ignore_short_audio"
        const val KEY_IGNORE_SHORT_AUDIO_DURATION = "ignore_short_audio_duration"
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_AUDIO_QUALITY = "audio_quality"
        const val KEY_SYNC_FREQUENCY = "sync_frequency"
        const val KEY_HAPTIC_PLAY_PAUSE = "haptic_play_pause"
        const val KEY_HAPTIC_QUEUE = "haptic_queue"
        const val KEY_HAPTIC_FAVORITE = "haptic_favorite"
        const val KEY_DYNAMIC_ALBUM_THEME = "dynamic_album_theme"
        const val KEY_VOLUME_NORMALIZATION_ENABLED = "volume_normalization_enabled"
        const val KEY_TARGET_LUFS = "target_lufs"
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS FRAGMENT
// ═══════════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — ENTRY POINT
// ═══════════════════════════════════════════════════════════════════════════════

private fun getPathFromUri(uri: Uri): String {
    if (android.provider.DocumentsContract.isTreeUri(uri)) {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            val relativePath = split[1]
            return if ("primary" == type.lowercase()) {
                android.os.Environment.getExternalStorageDirectory().toString() + "/" + relativePath
            } else {
                "/storage/$type/$relativePath"
            }
        }
    }
    return uri.path ?: uri.toString()
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun SettingsScreenContent(
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(LocalContext.current.findActivity() as androidx.activity.ComponentActivity),
    playerSharedViewModel: PlayerSharedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(LocalContext.current.findActivity() as androidx.activity.ComponentActivity)
) {
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val isPlayerExpanded by playerSharedViewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val amoledBlack by viewModel.amoledBlack.collectAsStateWithLifecycle()
    val dynamicAlbumTheme by viewModel.dynamicAlbumTheme.collectAsStateWithLifecycle()
    val hideNavLabels by viewModel.hideNavLabels.collectAsStateWithLifecycle()
    val dynamicNavStyle by viewModel.dynamicNavStyle.collectAsStateWithLifecycle()
    val vibrationStrength by viewModel.vibrationStrength.collectAsStateWithLifecycle()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
    val audioFocus by viewModel.audioFocus.collectAsStateWithLifecycle()
    val sleepTimerEndTime by playerSharedViewModel.sleepTimerEndTime.collectAsStateWithLifecycle()
    val sleepTimerMode by playerSharedViewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val volumeNormalizationEnabled by viewModel.volumeNormalizationEnabled.collectAsStateWithLifecycle()
    val targetLufs by viewModel.targetLufs.collectAsStateWithLifecycle()
    val libraryPaths by viewModel.libraryPaths.collectAsStateWithLifecycle()
    val songCacheSize by viewModel.songCacheSize.collectAsStateWithLifecycle()
    val imageCacheSize by viewModel.imageCacheSize.collectAsStateWithLifecycle()

    val ignoreShortAudio by viewModel.ignoreShortAudio.collectAsStateWithLifecycle()
    val ignoreShortAudioDuration by viewModel.ignoreShortAudioDuration.collectAsStateWithLifecycle()
    val hiddenFolders by viewModel.hiddenFolders.collectAsStateWithLifecycle()

    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val pauseHistory by viewModel.pauseHistory.collectAsStateWithLifecycle()
    val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()
    val songCacheMaxSize by viewModel.songCacheMaxSize.collectAsStateWithLifecycle()
    val imageCacheMaxSize by viewModel.imageCacheMaxSize.collectAsStateWithLifecycle()

    val hapticPlayPause by viewModel.hapticPlayPause.collectAsStateWithLifecycle()
    val hapticQueue by viewModel.hapticQueue.collectAsStateWithLifecycle()
    val hapticFavorite by viewModel.hapticFavorite.collectAsStateWithLifecycle()

    val ytCookies by viewModel.ytCookies.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val directoryPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.addLibraryPath(it.toString())
        }
    }

    val downloadFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val path = getPathFromUri(it)
            viewModel.setDownloadFolder(path)
        }
    }

    val exportBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(context, it) { _, msg ->
                (context as? MainActivity)?.showSnackbar(msg, 0)
            }
        }
    }

    val importBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBackup(context, it) { _, msg ->
                (context as? MainActivity)?.showSnackbar(msg, 0)
            }
        }
    }

    val hiddenFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val path = getPathFromUri(it)
                viewModel.addHiddenFolder(path)
            } catch (e: Exception) {
                android.util.Log.e("SettingsScreenContent", "Failed to resolve hidden folder path", e)
            }
        }
    }



    LaunchedEffect(Unit) { viewModel.calculateCacheSize() }

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val canPop = currentBackStackEntry?.destination?.route != SettingsRoute.Main.route

    NavHost(
        navController = navController, 
        startDestination = SettingsRoute.Main.route,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
        composable(SettingsRoute.Main.route) {
            SettingsMainScreen(navController = navController)
        }
        composable(SettingsRoute.MusicManagement.route) {
            MusicManagementScreen(
                navController = navController,
                libraryPaths = libraryPaths,
                onRemovePath = { viewModel.removeLibraryPath(it) },
                onAddPathClick = { directoryPickerLauncher.launch(null) },
                songCacheSize = songCacheSize,
                imageCacheSize = imageCacheSize,
                onClearSongCache = {
                    viewModel.clearSongCache {
                        (context as? MainActivity)?.showSnackbar("Song cache cleared", 0)
                    }
                },
                onClearImageCache = {
                    viewModel.clearImageCache {
                        (context as? MainActivity)?.showSnackbar("Image cache cleared", 0)
                    }
                },
                songCacheMaxSize = songCacheMaxSize,
                onSongCacheMaxSizeChange = { viewModel.setSongCacheMaxSize(it) },
                imageCacheMaxSize = imageCacheMaxSize,
                onImageCacheMaxSizeChange = { viewModel.setImageCacheMaxSize(it) },
                downloadFolder = downloadFolder,
                onDownloadFolderClick = { downloadFolderPickerLauncher.launch(null) },
                ignoreShortAudio = ignoreShortAudio,
                onIgnoreShortAudioChange = { viewModel.setIgnoreShortAudio(it) },
                ignoreShortAudioDuration = ignoreShortAudioDuration,
                onIgnoreShortAudioDurationChange = { viewModel.setIgnoreShortAudioDuration(it) },
                hiddenFolders = hiddenFolders,
                onAddHiddenFolderClick = { hiddenFolderPickerLauncher.launch(null) },
                onRemoveHiddenFolder = { viewModel.removeHiddenFolder(it) }
            )
        }
        composable(SettingsRoute.BackupRestore.route) {
            BackupRestoreScreen(
                navController = navController,
                onBackupClick = { exportBackupLauncher.launch("spatialflow_backup.json") },
                onRestoreClick = { importBackupLauncher.launch(arrayOf("application/json")) }
            )
        }
        composable(SettingsRoute.Account.route) {
            AccountScreen(
                navController = navController,
                ytCookies = ytCookies,
                onYtCookiesChange = { viewModel.setYtCookies(it) },
                dataSaver = dataSaver,
                onDataSaverChange = { viewModel.setDataSaver(it) },
                pauseHistory = pauseHistory,
                onPauseHistoryChange = { viewModel.setPauseHistory(it) }
            )
        }
        composable(SettingsRoute.Appearance.route) {
            AppearanceScreen(
                navController = navController,
                darkMode = darkMode,
                onDarkModeChange = { viewModel.setDarkMode(it) },
                amoledBlack = amoledBlack,
                onAmoledBlackChange = { viewModel.setAmoledBlack(it) },
                dynamicAlbumTheme = dynamicAlbumTheme,
                onDynamicAlbumThemeChange = { viewModel.setDynamicAlbumTheme(it) },
                hideNavLabels = hideNavLabels,
                onHideNavLabelsChange = { viewModel.setHideNavLabels(it) },
                dynamicNavStyle = dynamicNavStyle,
                onDynamicNavStyleChange = { viewModel.setDynamicNavStyle(it) }
            )
        }
        composable(SettingsRoute.Playback.route) {
            PlaybackScreen(
                navController = navController,
                crossfadeEnabled = crossfadeEnabled,
                onCrossfadeToggle = { viewModel.setCrossfadeEnabled(it) },
                crossfadeDuration = crossfadeDuration,
                onCrossfadeDurationChange = { viewModel.setCrossfadeDuration(it) },
                audioFocus = audioFocus,
                onAudioFocusToggle = { viewModel.setAudioFocus(it) },
                sleepTimerEndTime = sleepTimerEndTime,
                sleepTimerMode = sleepTimerMode,
                onStartSleepTimer = { playerSharedViewModel.startCustomSleepTimer(it) },
                onCancelSleepTimer = { playerSharedViewModel.cancelSleepTimer() },
                onSetEndOfSong = { enable ->
                    if (enable) playerSharedViewModel.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.END_OF_SONG)
                    else playerSharedViewModel.cancelSleepTimer()
                },
                audioQuality = audioQuality,
                onAudioQualityChange = { viewModel.setAudioQuality(it) },
                volumeNormalizationEnabled = volumeNormalizationEnabled,
                onVolumeNormalizationChange = { viewModel.setVolumeNormalizationEnabled(it) },
                targetLufs = targetLufs,
                onTargetLufsChange = { viewModel.setTargetLufs(it) }
            )
        }
        composable(SettingsRoute.Haptics.route) {
            HapticsScreen(
                navController = navController,
                hasHaptics = viewModel.hasHaptics,
                vibrationStrength = vibrationStrength,
                onVibrationStrengthChange = { viewModel.setVibrationStrength(it) },
                hapticPlayPause = hapticPlayPause,
                onHapticPlayPauseChange = { viewModel.setHapticPlayPause(it) },
                hapticQueue = hapticQueue,
                onHapticQueueChange = { viewModel.setHapticQueue(it) },
                hapticFavorite = hapticFavorite,
                onHapticFavoriteChange = { viewModel.setHapticFavorite(it) }
            )
        }
        composable(SettingsRoute.About.route) {
            AboutScreen(
                navController = navController,
                onCheckUpdate = {
                    (context as? Activity)?.let { activity ->
                        (context as? MainActivity)?.updateManager?.checkForUpdate(
                            activity.findViewById(android.R.id.content),
                            BuildConfig.VERSION_NAME
                        )
                    }
                },
                onWhatsNew = { navController.navigate(SettingsRoute.WhatsNew.route) },
                onOpenUrl = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            )
        }
        composable(SettingsRoute.Feedback.route) {
            FeedbackScreen(navController = navController)
        }
        composable(SettingsRoute.WhatsNew.route) {
            WhatsNewScreen(navController = navController)
        }
    }

    androidx.activity.compose.BackHandler(enabled = canPop && !isPlayerExpanded) {
        navController.popBackStack()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — MAIN SETTINGS BODY
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

sealed class SettingsRoute(val route: String) {
    object Main : SettingsRoute("main")
    object MusicManagement : SettingsRoute("music_management")
    object Account : SettingsRoute("account")
    object Appearance : SettingsRoute("appearance")
    object Playback : SettingsRoute("playback")
    object Haptics : SettingsRoute("haptics")
    object About : SettingsRoute("about")
    object Feedback : SettingsRoute("feedback")
    object WhatsNew : SettingsRoute("whats_new")
    object BackupRestore : SettingsRoute("backup_restore")
}

@Composable
private fun SettingsMainScreen(navController: androidx.navigation.NavController) {
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset {
                if (!isLandscape && mainActivity != null) {
                    val delta = consumed.y
                    if (delta < -30f) mainActivity.hideBottomNavWithAnimation()
                    else if (delta > 30f) mainActivity.showBottomNavWithAnimation()
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .then(if (isLandscape) Modifier.padding(start = 88.dp) else Modifier)
            .nestedScroll(nestedScrollConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(bottom = 120.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp)
        )

        SettingsGroupCard(
            items = listOf(
                {
                    SettingsCategoryItem(
                        title = "Account & Sync",
                        subtitle = "YouTube Music login, history sync",
                        icon = Icons.Rounded.AccountCircle,
                        onClick = { navController.navigate(SettingsRoute.Account.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Playback",
                        subtitle = "Audio behavior, crossfade, audio focus",
                        icon = Icons.Rounded.PlayCircle,
                        onClick = { navController.navigate(SettingsRoute.Playback.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Music Management",
                        subtitle = "Manage folders, refresh library, storage",
                        icon = Icons.Rounded.LibraryMusic,
                        onClick = { navController.navigate(SettingsRoute.MusicManagement.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Appearance",
                        subtitle = "Themes, layout, visual styles",
                        icon = Icons.Rounded.Palette,
                        onClick = { navController.navigate(SettingsRoute.Appearance.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Haptics",
                        subtitle = "Vibration strength, haptic feedback",
                        icon = Icons.Rounded.Vibration,
                        onClick = { navController.navigate(SettingsRoute.Haptics.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Backup & Restore",
                        subtitle = "Export and import your library and settings",
                        icon = Icons.Rounded.SettingsBackupRestore,
                        onClick = { navController.navigate(SettingsRoute.BackupRestore.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Feedback & Bug Reports",
                        subtitle = "Report issues, request features, export logs",
                        icon = Icons.Rounded.BugReport,
                        onClick = { navController.navigate(SettingsRoute.Feedback.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "About",
                        subtitle = "App version, credits, updates",
                        icon = Icons.Rounded.Info,
                        onClick = { navController.navigate(SettingsRoute.About.route) }
                    )
                }
            )
        )
        
        AppLogoSection(isLandscape = isLandscape)
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun MusicManagementScreen(
    navController: androidx.navigation.NavController,
    libraryPaths: List<String>,
    onRemovePath: (String) -> Unit,
    onAddPathClick: () -> Unit,
    songCacheSize: String,
    imageCacheSize: String,
    onClearSongCache: () -> Unit,
    onClearImageCache: () -> Unit,
    ignoreShortAudio: Boolean,
    onIgnoreShortAudioChange: (Boolean) -> Unit,
    ignoreShortAudioDuration: Float,
    onIgnoreShortAudioDurationChange: (Float) -> Unit,
    songCacheMaxSize: Int,
    onSongCacheMaxSizeChange: (Int) -> Unit,
    imageCacheMaxSize: Int,
    onImageCacheMaxSizeChange: (Int) -> Unit,
    downloadFolder: String?,
    onDownloadFolderClick: () -> Unit,
    hiddenFolders: List<String>,
    onAddHiddenFolderClick: () -> Unit,
    onRemoveHiddenFolder: (String) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Music Management") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader(stringResource(R.string.settings_header_library))
            SettingsGroupCard(buildList {
                add { LibrarySourceHeader() }
                libraryPaths.forEach { path ->
                    add { LibraryPathRow(path, onRemovePath) }
                }
                if (libraryPaths.isEmpty()) {
                    add { LibraryPathRow(stringResource(R.string.setting_music_source_default), null) }
                }
                add { AddMorePathRow(onAddPathClick) }
            })

            SettingsHeader("Audio Filtering")
            SettingsGroupCard(buildList {
                add {
                    IgnoreShortAudioRow(ignoreShortAudio, onIgnoreShortAudioChange)
                }
                if (ignoreShortAudio) {
                    add {
                        IgnoreShortAudioDurationRow(ignoreShortAudioDuration, onIgnoreShortAudioDurationChange)
                    }
                }
            })

            SettingsHeader("Hidden Folders")
            SettingsGroupCard(buildList {
                hiddenFolders.forEach { folder ->
                    add { HiddenFolderRow(folder, onRemoveHiddenFolder) }
                }
                if (hiddenFolders.isEmpty()) {
                    add {
                        ListItem(
                            headlineContent = { Text("No folders blacklisted", style = MaterialTheme.typography.bodyLarge) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                add {
                    AddHiddenFolderRow(onAddHiddenFolderClick)
                }
            })

            SettingsHeader("Downloads")
            SettingsGroupCard(listOf(
                { DownloadFolderRow(downloadFolder, onDownloadFolderClick) }
            ))

            SettingsHeader(stringResource(R.string.settings_header_storage))
            SettingsGroupCard(buildList {
                add { SongCacheSizeRow(songCacheSize, songCacheMaxSize, onSongCacheMaxSizeChange, onClearSongCache) }
                add { ImageCacheSizeRow(imageCacheSize, imageCacheMaxSize, onImageCacheMaxSizeChange, onClearImageCache) }
            })
        }
    }
}

@Composable
private fun BackupRestoreScreen(
    navController: androidx.navigation.NavController,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Backup & Restore") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader("Backup & Restore")
            SettingsGroupCard(buildList {
                add {
                    ListItem(
                        onClick = onBackupClick,
                        content = {
                            Column {
                                Text("Export Settings & Library", style = MaterialTheme.typography.bodyLarge)
                                Text("Export playlists, favorites, and preferences to JSON", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                add {
                    ListItem(
                        onClick = onRestoreClick,
                        content = {
                            Column {
                                Text("Import Settings & Library", style = MaterialTheme.typography.bodyLarge)
                                Text("Restore playlists, favorites, and preferences from JSON", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = 180f }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            })
        }
    }
}

@Composable
private fun IgnoreShortAudioRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Ignore Short Audio (Voice Notes)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Hide voice notes and short recordings from library",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun IgnoreShortAudioDurationRow(
    value: Float, onValueChange: (Float) -> Unit
) {
    ListItem(
        onClick = { },
        content = {
            Column {
                Text(
                    text = "Minimum Duration Threshold",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Ignore tracks under ${value.toInt()} seconds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Slider(
                value = value.coerceIn(10f, 120f),
                onValueChange = onValueChange,
                valueRange = 10f..120f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun HiddenFolderRow(path: String, onRemove: (String) -> Unit) {
    ListItem(
        onClick = { },
        content = {
            Text(
                text = path.substringAfterLast("/"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Text(
                text = "Remove",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRemove(path) }
                    .padding(8.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AddHiddenFolderRow(onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        content = {
            Text(
                text = "Add Hidden Folder",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AccountScreen(
    navController: androidx.navigation.NavController,
    ytCookies: String?,
    onYtCookiesChange: (String?) -> Unit,
    dataSaver: Boolean,
    onDataSaverChange: (Boolean) -> Unit,
    pauseHistory: Boolean,
    onPauseHistoryChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Account & Sync") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader("YouTube Music Account")
            SettingsGroupCard(listOf(
                {
                    var showLoginDialog by remember { mutableStateOf(false) }

                    if (showLoginDialog) {
                        YouTubeMusicLoginDialog(
                            onDismissRequest = { showLoginDialog = false },
                            onLoginSuccess = { cookies ->
                                onYtCookiesChange(cookies)
                                showLoginDialog = false
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (ytCookies != null) "Logged in to YouTube Music" else "Guest Mode (Anonymous)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (ytCookies != null) {
                                Text(
                                    text = "Profile: SpatialFlow User",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (ytCookies != null) "Accessing personalized playlists, library, and recommended interests." else "No account linked. Tap to sign in securely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                if (ytCookies != null) {
                                    onYtCookiesChange(null)
                                } else {
                                    showLoginDialog = true
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ytCookies != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (ytCookies != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(if (ytCookies != null) "Log Out" else "Log In")
                        }
                    }
                }
            ))

            SettingsHeader("Data Saving")
            SettingsGroupCard(listOf(
                { DataSaverRow(dataSaver, onDataSaverChange) }
            ))

            SettingsHeader("Sync & Privacy")
            SettingsGroupCard(listOf(
                { PauseHistoryRow(pauseHistory, onPauseHistoryChange) },
                { ManualSyncRow() }
            ))
        }
    }
}

@Composable
private fun DataSaverRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Data Saver (Wi-Fi Only)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Restrict streaming and high-res cover art downloads to Wi-Fi only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun PauseHistoryRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Pause Listening History",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Stop tracking played songs for recommendations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ManualSyncRow() {
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ListItem(
        onClick = {
            if (!isSyncing) {
                isSyncing = true
                scope.launch {
                    delay(2000) // Simulate sync
                    isSyncing = false
                }
            }
        },
        content = {
            Column {
                Text(text = "Manual Sync", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Sync playlists and favorites with server", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Now",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AppearanceScreen(
    navController: androidx.navigation.NavController,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    amoledBlack: Boolean,
    onAmoledBlackChange: (Boolean) -> Unit,
    dynamicAlbumTheme: Boolean,
    onDynamicAlbumThemeChange: (Boolean) -> Unit,
    hideNavLabels: Boolean,
    onHideNavLabelsChange: (Boolean) -> Unit,
    dynamicNavStyle: Boolean,
    onDynamicNavStyleChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Appearance") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader(stringResource(R.string.settings_header_general))
            SettingsGroupCard(buildList {
                add { ThemeRow(darkMode, onDarkModeChange) }
                if (darkMode) {
                    add { AmoledBlackRow(amoledBlack, onAmoledBlackChange) }
                }
                add { DynamicAlbumThemeRow(dynamicAlbumTheme, onDynamicAlbumThemeChange) }
            })

            SettingsHeader("Navigation Bar")
            SettingsGroupCard(buildList {
                add { HideNavLabelsRow(hideNavLabels, onHideNavLabelsChange) }
                add { DynamicNavStyleRow(dynamicNavStyle, onDynamicNavStyleChange) }
            })
        }
    }
}

@Composable
private fun DynamicNavStyleRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Dynamic Navbar Style",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Compact height with bold, elevated icons",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun HideNavLabelsRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Hide Navigation Labels",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Remove text labels from the bottom navigation bar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun DynamicAlbumThemeRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Dynamic colors",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Extract theme colors from the currently playing album art",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AmoledBlackRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Pure AMOLED Black",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Pitch black background in dark mode to save battery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun PlaybackScreen(
    navController: androidx.navigation.NavController,
    crossfadeEnabled: Boolean,
    onCrossfadeToggle: (Boolean) -> Unit,
    crossfadeDuration: Float,
    onCrossfadeDurationChange: (Float) -> Unit,
    audioFocus: Boolean,
    onAudioFocusToggle: (Boolean) -> Unit,
    sleepTimerEndTime: Long,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit,
    audioQuality: String,
    onAudioQualityChange: (String) -> Unit,
    volumeNormalizationEnabled: Boolean,
    onVolumeNormalizationChange: (Boolean) -> Unit,
    targetLufs: Float,
    onTargetLufsChange: (Float) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Playback") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader("Crossfade")
            SettingsGroupCard(listOf(
                { CrossfadeRow(crossfadeEnabled, onCrossfadeToggle, crossfadeDuration, onCrossfadeDurationChange) }
            ))

            SettingsHeader("Streaming Quality")
            SettingsGroupCard(listOf(
                { AudioQualityRow(audioQuality, onAudioQualityChange) }
            ))
            
            SettingsHeader("Audio Focus")
            SettingsGroupCard(listOf(
                { AudioFocusRow(audioFocus, onAudioFocusToggle) }
            ))

            SettingsHeader("Volume Controls")
            SettingsGroupCard(listOf(
                { VolumeNormalizationRow(volumeNormalizationEnabled, onVolumeNormalizationChange, targetLufs, onTargetLufsChange) }
            ))

            SettingsHeader("Sleep Timer")
            SettingsGroupCard(listOf(
                { SleepTimerSection(sleepTimerEndTime, sleepTimerMode, onStartSleepTimer, onCancelSleepTimer, onSetEndOfSong) }
            ))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AudioQualityRow(
    currentQuality: String,
    onQualityChange: (String) -> Unit
) {
    val options = listOf("High", "Normal", "Data Saver")
    
    ListItem(
        headlineContent = {
            Column {
                Text(
                    text = "Streaming Quality",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "High uses more data, Data Saver uses lowest bitrate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option == currentQuality
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onQualityChange(option) }
                        ) {
                            Text(
                                text = option,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeNormalizationRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    targetLufs: Float,
    onTargetLufsChange: (Float) -> Unit
) {
    val options = listOf(
        Pair("Quiet", -19f),
        Pair("Normal", -14f),
        Pair("Loud", -11f)
    )

    Column {
        ListItem(
            headlineContent = {
                Text("Volume Normalization", style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
                Text(
                    "Automatically adjust playback volume so all songs sound equally loud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        AnimatedVisibility(visible = enabled) {
            ListItem(
                headlineContent = {
                    Column {
                        Text(
                            text = "Target Loudness",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.forEach { option ->
                                val isSelected = option.second == targetLufs
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { onTargetLufsChange(option.second) }
                                ) {
                                    Text(
                                        text = option.first,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun HapticsScreen(
    navController: androidx.navigation.NavController,
    hasHaptics: Boolean,
    vibrationStrength: Float,
    onVibrationStrengthChange: (Float) -> Unit,
    hapticPlayPause: Boolean,
    onHapticPlayPauseChange: (Boolean) -> Unit,
    hapticQueue: Boolean,
    onHapticQueueChange: (Boolean) -> Unit,
    hapticFavorite: Boolean,
    onHapticFavoriteChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Haptics") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader(stringResource(R.string.settings_haptics_title))
            SettingsGroupCard(buildList {
                if (!hasHaptics) {
                    add { HapticsNotSupported() }
                }
                add { VibrationStrengthRow(vibrationStrength, onVibrationStrengthChange, hasHaptics) }
            })

            SettingsHeader("Granular Interactions")
            SettingsGroupCard(buildList {
                add { HapticToggleRow("Haptics on Play/Pause", "Vibrate when playing or pausing audio", hapticPlayPause, onHapticPlayPauseChange, hasHaptics) }
                add { HapticToggleRow("Haptics on Queue Reordering", "Vibrate when dragging items in the queue", hapticQueue, onHapticQueueChange, hasHaptics) }
                add { HapticToggleRow("Haptics on Heart/Favorite", "Vibrate when liking a song", hapticFavorite, onHapticFavoriteChange, hasHaptics) }
            })
        }
    }
}

@Composable
private fun HapticToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = SmoothSpring,
        label = "haptic_row_alpha"
    )

    ListItem(
        onClick = { if (enabled) onToggle(!checked) },
        content = {
            Column(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
// ── Whats New ───────────────────────────────────────────────────────────────

@Composable
private fun WhatsNewScreen(navController: androidx.navigation.NavController) {
    val releases by androidx.compose.runtime.produceState<List<com.codetrio.spatialflow.update.GitHubReleaseClient.ReleaseInfo>?>(initialValue = null) {
        withContext(Dispatchers.IO) {
            val client = com.codetrio.spatialflow.update.GitHubReleaseClient("MythicalSHUB", "SpatialFlow")
            value = client.getAllReleases()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("What's New") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        if (releases == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (releases!!.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No releases found.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)
            ) {
                val groupedReleases = releases!!.groupBy { release ->
                    release.tagName.removePrefix("v").removePrefix("V").substringBefore('.')
                }

                groupedReleases.forEach { (majorVersion, majorReleases) ->
                    item(key = "header_$majorVersion") {
                        Text(
                            text = "Version $majorVersion Series",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    itemsIndexed(majorReleases, key = { _, rel -> rel.tagName }) { _, release ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = getSettingsSegmentedShape(index = 0, count = 2),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = release.tagName,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = getSettingsSegmentedShape(index = 1, count = 2),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                val cleanedBody = release.changelog
                                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // bold
                                    .replace(Regex("__(.*?)__"), "$1") // bold
                                    .replace(Regex("### (.*)"), "$1") // headers
                                    .replace(Regex("## (.*)"), "$1")
                                    .replace(Regex("# (.*)"), "$1")
                                    .trim()

                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = cleanedBody,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(
    navController: androidx.navigation.NavController,
    onCheckUpdate: () -> Unit,
    onWhatsNew: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("About") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Hero App Logo (Elegant Box)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_applogo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(72.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            
            // Version Pills
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            
            // Action Buttons
            androidx.compose.material3.ButtonGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            ) {
                Button(
                    onClick = onCheckUpdate,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Update, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Updates", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onWhatsNew,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("What's New", style = MaterialTheme.typography.labelLarge)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Credits Card
            CreditsCard(onOpenUrl)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsGroupCard(items: List<@Composable () -> Unit>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        items.forEachIndexed { index, item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = getSettingsSegmentedShape(index = index, count = items.size),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                item()
            }
        }
    }
}

private fun getSettingsSegmentedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 32.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

// ── Theme ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeRow(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!isDark) },
        content = {
            Text(
                text = stringResource(R.string.setting_dark_mode),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = { ThemeSwitch(isDark, onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ThemeSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                },
                label = "theme_icon"
            ) { dark ->
                Icon(
                    imageVector = if (!dark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        }
    )
}



// ── Haptics ─────────────────────────────────────────────────────────────────

@Composable
private fun HapticsNotSupported() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_haptics_not_supported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VibrationStrengthRow(
    value: Float, onValueChange: (Float) -> Unit, enabled: Boolean
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = SmoothSpring,
        label = "haptic_alpha"
    )

    ListItem(
        onClick = { /* Slider handles interaction */ },
        content = {
            Text(
                text = stringResource(R.string.setting_vibration_strength),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        },
        supportingContent = {
            Slider(
                value = value.coerceIn(0f, 100f),
                onValueChange = onValueChange,
                enabled = enabled,
                valueRange = 0f..100f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Wavy Divider ────────────────────────────────────────────────────────────

// ── Crossfade ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CrossfadeRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    duration: Float,
    onDurationChange: (Float) -> Unit
) {
    ListItem(
        onClick = { onToggle(!enabled) },
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Text(
                text = stringResource(R.string.setting_crossfade),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Column {
                AnimatedContent(
                    targetState = if (enabled) "${duration.toInt()}s" else "Off",
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                            fadeOut(spring(stiffness = Spring.StiffnessMedium))
                    },
                    label = "crossfade_label"
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                AnimatedVisibility(visible = enabled) {
                    Slider(
                        value = duration,
                        onValueChange = onDurationChange,
                        enabled = enabled,
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Audio Focus ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AudioFocusRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!enabled) },
        content = {
            Text(
                text = stringResource(R.string.setting_audio_focus),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_audio_focus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Sleep Timer ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SleepTimerSection(
    endTime: Long, 
    mode: PlayerSharedViewModel.SleepTimerMode,
    onStart: (Int) -> Unit, 
    onCancel: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    val remaining by remember(endTime) {
        derivedStateOf { endTime - System.currentTimeMillis() }
    }

    Column {
        ListItem(
            onClick = { showBottomSheet = true },
            content = {
                Text(
                    text = stringResource(R.string.setting_sleep_timer),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            supportingContent = {
                val supportingText = when (mode) {
                    PlayerSharedViewModel.SleepTimerMode.OFF -> stringResource(R.string.setting_sleep_timer_off)
                    PlayerSharedViewModel.SleepTimerMode.END_OF_SONG -> "Stop at end of current song"
                    PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE -> "Stop at end of queue"
                    PlayerSharedViewModel.SleepTimerMode.CUSTOM -> {
                        if (remaining > 0) "${remaining / 60000} min remaining"
                        else stringResource(R.string.setting_sleep_timer_off)
                    }
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_timer),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if (showBottomSheet) {
            SleepTimerBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sleepTimerEndTime = endTime,
                sleepTimerMode = mode,
                onStartTimer = { mins ->
                    onStart(mins)
                },
                onCancelTimer = {
                    onCancel()
                },
                onSetEndOfSong = { enable ->
                    onSetEndOfSong(enable)
                }
            )
        }
    }
}

// ── Library ─────────────────────────────────────────────────────────────────

@Composable
private fun LibrarySourceHeader() {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.setting_music_source),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryPathRow(path: String, onRemove: ((String) -> Unit)?) {
    ListItem(
        onClick = { },
        content = {
            Text(
                text = path.substringAfterLast(":"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(
                    if (onRemove != null) R.drawable.ic_folder_music else R.drawable.ic_folder_open
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = if (onRemove != null) {
            {
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onRemove(path) }
                        .padding(8.dp)
                )
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddMorePathRow(onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        content = {
            Text(
                text = stringResource(R.string.setting_add_more_path),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Storage ─────────────────────────────────────────────────────────────────

@Composable
private fun DownloadFolderRow(path: String?, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        content = {
            Column {
                Text(
                    text = "Download Location",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = path ?: "Not Set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun CacheSizeRow(
    title: String,
    currentSize: String, // from calculation, e.g. "120 MB"
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Max $title Size") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(100, 500, 1024, 2048, 0).forEach { sizeOption ->
                        val text = if (sizeOption == 0) "Unlimited" else if (sizeOption >= 1024) "${sizeOption / 1024} GB" else "$sizeOption MB"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (maxSize == sizeOption),
                                onClick = {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Extract numerical value roughly to show progress
    val currentBytes = parseCacheSize(currentSize)
    val maxBytes = if (maxSize == 0) 0L else maxSize * 1024L * 1024L
    val progress = if (maxBytes > 0) (currentBytes.toFloat() / maxBytes).coerceIn(0f, 1f) else 0f

    ListItem(
        onClick = { showDialog = true },
        content = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = currentSize,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        label = "cache_size"
                    ) { size ->
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val maxText = if (maxSize == 0) "Unlimited" else if (maxSize >= 1024) "${maxSize / 1024} GB" else "$maxSize MB"
                    Text(
                        text = "Max: $maxText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (maxBytes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Cache",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// Very basic string parsing for cache size
private fun parseCacheSize(sizeStr: String): Long {
    try {
        val split = sizeStr.trim().split(" ")
        if (split.size != 2) return 0L
        val value = split[0].toFloatOrNull() ?: return 0L
        val unit = split[1].uppercase()
        return when (unit) {
            "KB" -> (value * 1024).toLong()
            "MB" -> (value * 1024 * 1024).toLong()
            "GB" -> (value * 1024 * 1024 * 1024).toLong()
            "B" -> value.toLong()
            else -> 0L
        }
    } catch (e: Exception) {
        return 0L
    }
}

@Composable
private fun SongCacheSizeRow(
    cacheSize: String,
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    CacheSizeRow(
        title = "Song Cache",
        currentSize = cacheSize,
        maxSize = maxSize,
        onMaxSizeChange = onMaxSizeChange,
        onClear = onClear
    )
}

@Composable
private fun ImageCacheSizeRow(
    cacheSize: String,
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    CacheSizeRow(
        title = "Image Cache",
        currentSize = cacheSize,
        maxSize = maxSize,
        onMaxSizeChange = onMaxSizeChange,
        onClear = onClear
    )
}

// ── Credits ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CreditsCard(onOpenUrl: (String) -> Unit) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                coil.compose.AsyncImage(
                    model = "https://github.com/MythicalSHUB.png",
                    contentDescription = "Shubham Karande",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                
                Text(
                    text = "Shubham Karande",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Android Developer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Row(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SocialIconButton(icon = R.drawable.ic_github, onClick = { onOpenUrl("https://github.com/MythicalSHUB") })
                    SocialIconButton(icon = R.drawable.ic_instagram, onClick = { onOpenUrl("https://instagram.com/mythicalshub") })
                    SocialIconButton(icon = R.drawable.ic_youtube, onClick = { onOpenUrl("https://youtube.com/@8dmusic_s") })
                }
            }
        }
    }
}

@Composable
private fun SocialIconButton(icon: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// SocialButton is replaced by ButtonGroup clickableItem

// ── App Logo ────────────────────────────────────────────────────────────────

@Composable
private fun AppLogoSection(isLandscape: Boolean) {
    val iconSize by animateDpAsState(
        targetValue = if (isLandscape) 160.dp else 250.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "logo_size"
    )
    val fontSize by animateFloatAsState(
        targetValue = if (isLandscape) 32f else 44f,
        animationSpec = SmoothSpring,
        label = "logo_text_size"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isLandscape) 8.dp else 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_applogo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontSize = fontSize.sp,
            letterSpacing = 0.sp
        )
        Text(
            text = "© 2025 Shubham Karande",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeMusicLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        },
        title = {
            Text(
                text = "Log In to YouTube Music",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                Text(
                    text = "Sign in to access your real playlists, library, and personalized interests.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val cookieManager = CookieManager.getInstance()
                                        val cookies = cookieManager.getCookie("https://music.youtube.com")
                                        if (cookies != null && cookies.contains("SAPISID") && cookies.contains("HSID")) {
                                            onLoginSuccess(cookies)
                                        }
                                    }
                                }
                                
                                // Load Google login continuing to YouTube Music
                                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://music.youtube.com/")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

// ── Feedback & Bug Reports ──────────────────────────────────────────────────

@Composable
private fun FeedbackScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val debugInfo = remember {
        """
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE}
        SDK: ${Build.VERSION.SDK_INT}
        App Version: ${BuildConfig.VERSION_NAME}
        """.trimIndent()
    }

    Scaffold(
        topBar = { SettingsDetailTopBar("Feedback & Bug Reports") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader("Report & Request")
            SettingsGroupCard(
                items = listOf(
                    {
                        ListItem(
                            headlineContent = { Text("Report a Bug", style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = { Text("Open GitHub with pre-filled device information", style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.BugReport, null, tint = MaterialTheme.colorScheme.error) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val title = URLEncoder.encode("[Bug] ", "UTF-8")
                                val body = URLEncoder.encode("## Description\n\n\n## Device Information\n$debugInfo\n\n## Steps to Reproduce\n1.\n2.\n3.\n", "UTF-8")
                                val url = "https://github.com/MythicalSHUB/SpatialFlow/issues/new?title=$title&body=$body"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = { Text("Request Feature", style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = { Text("Suggest a new feature for SpatialFlow", style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val title = URLEncoder.encode("[Feature] ", "UTF-8")
                                val body = URLEncoder.encode("## Feature Description\n\n\n## Why is this needed?\n\n", "UTF-8")
                                val url = "https://github.com/MythicalSHUB/SpatialFlow/issues/new?title=$title&body=$body"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    }
                )
            )

            SettingsHeader("Debug Information")
            SettingsGroupCard(
                items = listOf(
                    {
                        ListItem(
                            headlineContent = { Text("Copy Debug Information", style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = { Text("Copy device & app version to clipboard", style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Debug Info", debugInfo)
                                clipboardManager.setPrimaryClip(clip)
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = { Text("Export Logs", style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = { Text("Share raw debug logs to attach to an issue", style = MaterialTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, debugInfo)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Logs"))
                            }
                        )
                    }
                )
            )
        }
    }
}
