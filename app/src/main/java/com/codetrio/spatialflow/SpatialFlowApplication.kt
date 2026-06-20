package com.codetrio.spatialflow

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor
import com.codetrio.spatialflow.util.CacheManager
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class SpatialFlowApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: SpatialFlowApplication
            private set
    }


    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { InnerTubeClient.httpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Dedicated high-speed memory cache (25% of available application heap)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250 * 1024 * 1024) // 250MB size quota for offline artwork persistent caching
                    .build()
            }
            .crossfade(true)
            .crossfade(300) // Expressive 300ms transition fade-in
            .allowHardware(true) // Offload rendering directly to GPU hardware buffers for zero UI thread lag
            .allowRgb565(false) // Disable 16-bit compression to ensure pure ARGB_8888 color resolution on premium displays
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    @SuppressLint("VisibleForTests")
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Proactive Initialization: Connect localized persistence cache to high-speed networking engine
        InnerTubeClient.initialize(this)

        // Warm up native FFmpeg binaries in background thread so playback can bind instantly later
        Thread {
            try {
                com.arthenica.ffmpegkit.FFmpegKitConfig.ignoreSignal(com.arthenica.ffmpegkit.Signal.SIGXCPU)
                com.arthenica.ffmpegkit.FFmpegKit.executeAsync("-version") { }
            } catch (_: Exception) {
            }
        }.start()
        
        // Initialize global theme mode
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Apply dynamic colors to all activities
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Load saved YouTube Music cookies for logged-in features
        val savedCookie = prefs.getString("yt_cookies", null)
        if (savedCookie != null) {
            InnerTubeClient.cookie = savedCookie
        }

        // Background Warm-up: Initialize NewPipe, OkHttp, and TLS to YouTube CDN
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // 1. Warm up OkHttp TCP+TLS connection to YouTube CDN
                InnerTubeClient.httpClient.newCall(
                    okhttp3.Request.Builder()
                        .url("https://www.youtube.com/generate_204")
                        .head()
                        .build()
                ).execute().close()
                Log.d("SpatialFlowApp", "Successfully warmed OkHttp connection to YouTube CDN")
            } catch (e: Exception) {
                Log.w("SpatialFlowApp", "Failed to warm up OkHttp connection: ${e.message}")
            }

            try {
                // 2. Initialize NewPipe Extractor and perform a dummy extraction to JIT-warm the JavaScript engine
                NewPipeStreamExtractor.init(this@SpatialFlowApplication)
                // A lightweight dummy request to trigger JS evaluation
                // This is a known lightweight video ID just for warming the engine
                NewPipeStreamExtractor.getStreamUrl("dQw4w9WgXcQ")
                Log.d("SpatialFlowApp", "Successfully JIT-warmed NewPipe JS Extractor engine")
            } catch (e: Exception) {
                Log.w("SpatialFlowApp", "Failed to warm up NewPipe Extractor: ${e.message}")
            }
        }

        // Suppress expected Glide warnings for missing album arts
        Glide.init(this, GlideBuilder().setLogLevel(Log.ERROR))

        // Clean up old temp files on app start
        CacheManager.clearOldCache(this)
    }
}
