package com.codetrio.spatialflow.data.lyrics.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.ConfidenceScorer
import com.codetrio.spatialflow.data.lyrics.LrcLibApi
import com.codetrio.spatialflow.data.lyrics.KugouApi
import com.codetrio.spatialflow.data.lyrics.BetterLyricsApi
import com.codetrio.spatialflow.data.lyrics.SimpMusicApi
import com.codetrio.spatialflow.data.lyrics.LyricsNormalizer
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.MetadataRepair
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.EmbeddedLyricsProvider
import com.codetrio.spatialflow.data.lyrics.providers.LrcLibProvider
import com.codetrio.spatialflow.data.lyrics.providers.KugouProvider
import com.codetrio.spatialflow.data.lyrics.providers.BetterLyricsProvider
import com.codetrio.spatialflow.data.lyrics.providers.SimpMusicProvider
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * Top-level orchestrator for the lyrics fetch pipeline.
 * Coordinates: MetadataRepair → Cache → ProviderRouter → ConfidenceScorer →
 * DecisionEngine → Cache.
 * Thread-safe. All callbacks delivered on main thread.
 */
class LyricsFetchManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val metadataRepair: MetadataRepair = MetadataRepair()
    private val normalizer: LyricsNormalizer = LyricsNormalizer()
    private val decisionEngine: LyricsDecisionEngine
    private val cacheManager: LyricsCacheManager = LyricsCacheManager(context)
    private val providerStats: ProviderStats = ProviderStats(context)
    private val telemetry: LyricsTelemetry = LyricsTelemetry()
    private val router: ProviderRouter
    private val bgScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    @Volatile
    private var betterLyricsApi: BetterLyricsApi? = null

    // Track current search to allow cancellation
    @Volatile
    private var currentFetch: Job? = null

    @Volatile
    private var currentTrackKey: String? = null
    private val cancelled = AtomicBoolean(false)

    /**
     * Callback interface for lyrics results.
     * All methods called on main thread.
     */
    interface LyricsCallback {
        /** Called when lyrics are ready to display */
        fun onLyricsFound(result: LyricsResult)

        /** Called when a better version is found during background search */
        fun onLyricsUpgraded(betterResult: LyricsResult)

        /** Called when all providers exhausted with no result */
        fun onLyricsNotFound(reason: String)

        /** Called when track is detected as instrumental */
        fun onInstrumental()

        /** Called when a provider completes execution */
        fun onProviderResult(providerName: String, result: LyricsResult?) {}

        /** Optional status update during search */
        fun onSearchStatus(message: String) {}
    }

    init {
        val scorer = ConfidenceScorer()
        this.decisionEngine = LyricsDecisionEngine(scorer)

        // Build providers
        val client = buildHttpClient()
        val providers = createProviders(client)
        this.router = ProviderRouter(providers, scorer, providerStats)
    }

    companion object {
        private const val TAG = "LyricsFetchManager"

        @Volatile
        private var instance: LyricsFetchManager? = null

        @JvmStatic
        fun getInstance(context: Context): LyricsFetchManager {
            return instance ?: synchronized(this) {
                instance ?: LyricsFetchManager(context.applicationContext).also { instance = it }
            }
        }

        private fun buildHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    /**
     * Fetch lyrics for a song. This is the main entry point.
     * Automatically checks cache, repairs metadata, searches providers, scores
     * results.
     */
    fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        filePath: String?,
        callback: LyricsCallback,
        videoId: String? = null
    ) {
        // Cancel any ongoing fetch
        cancelCurrent()
        cancelled.set(false)

        currentFetch = bgScope.launch {
            if (cancelled.get()) return@launch

            // Repair metadata
            val track = metadataRepair.repair(title, artist, album, durationMs, filePath, videoId)
            currentTrackKey = track.getCacheKey()

            telemetry.logSearchStart(track)

            // 1. Check LyricsHelper.singleLyricsCache first (finalized text cache)
            val cachedSingleText = LyricsHelper.getSingle(track.cleanedTitle, track.cleanedArtist)
            if (cachedSingleText != null) {
                val isSynced = cachedSingleText.contains("[") || cachedSingleText.contains("<tt") || cachedSingleText.contains("<p begin=") || cachedSingleText.contains("ttm:begin")
                val isWordByWord = (cachedSingleText.contains("<tt") || cachedSingleText.contains("<p begin=") || cachedSingleText.contains("ttm:begin")) ||
                        (cachedSingleText.contains("<") && cachedSingleText.contains(">") && cachedSingleText.contains("["))
                val cachedResult = LyricsResult(
                    providerName = "Local Cache",
                    plainLyrics = if (!isSynced) cachedSingleText else null,
                    syncedLyrics = if (isSynced) cachedSingleText else null,
                    confidence = 1.0f,
                    isSynced = isSynced,
                    isWordByWord = isWordByWord
                )
                telemetry.logCacheStatus("SINGLE_TEXT_HIT", "length=${cachedSingleText.length}")
                telemetry.logResult(cachedResult, "CACHE_HIT")
                
                withContext(Dispatchers.Main) {
                    callback.onProviderResult("Local Cache", cachedResult)
                    callback.onLyricsFound(cachedResult)
                }
                
                // If it's not the best possible (word-by-word), we should background search and upgrade!
                if (!isSynced || !isWordByWord) {
                    launchBackgroundUpgrade(track, cachedResult, callback)
                } else {
                    // Search all providers in background to populate selector list
                    val resultsList = mutableListOf<LyricsResult>()
                    router.searchAll(track, track.detectedLanguage, cancelOnEarlyWin = false) { provider, res ->
                        if (res != null) {
                            synchronized(resultsList) {
                                resultsList.add(res)
                            }
                        }
                        bgScope.launch(Dispatchers.Main) { callback.onProviderResult(provider, res) }
                    }
                    if (resultsList.isNotEmpty()) {
                        LyricsHelper.put(track.getCacheKey(), resultsList)
                    }
                }
                
                return@launch
            }

            // 2. Check LyricsHelper.cache (List<LyricsResult>) second
            val cachedList = LyricsHelper.get(track.getCacheKey())
            if (cachedList != null && cachedList.isNotEmpty()) {
                telemetry.logCacheStatus("HELPER_LIST_HIT", "size=${cachedList.size}")
                withContext(Dispatchers.Main) {
                    cachedList.forEach { res ->
                        callback.onProviderResult(res.providerName.orEmpty(), res)
                    }
                }
                val best = cachedList.maxByOrNull { it.confidence }
                if (best != null && best.hasLyrics()) {
                    withContext(Dispatchers.Main) {
                        callback.onLyricsFound(best)
                    }
                    return@launch
                }
            }

            // Check standard file/database cache
            val cached = cacheManager.get(track)
            if (cached != null) {
                val decision = decisionEngine.decideFetch(cached, false)

                when (decision) {
                    LyricsDecisionEngine.FetchDecision.USE_CACHE -> {
                        telemetry.logCacheStatus("HIT", "confidence=${cached.confidence}")
                        telemetry.logResult(cached, "CACHE_HIT")
                        
                        val text = cached.syncedLyrics ?: cached.plainLyrics
                        if (!text.isNullOrBlank()) {
                            LyricsHelper.putSingle(track.cleanedTitle, track.cleanedArtist, text)
                        }

                        withContext(Dispatchers.Main) {
                            callback.onProviderResult(cached.providerName.orEmpty(), cached)
                            callback.onLyricsFound(cached)
                        }
                        
                        // Search all providers in background to populate the selector list in the UI
                        val resultsList = mutableListOf<LyricsResult>()
                        router.searchAll(track, track.detectedLanguage, cancelOnEarlyWin = false) { provider, res ->
                            if (res != null) {
                                synchronized(resultsList) {
                                    resultsList.add(res)
                                }
                            }
                            bgScope.launch(Dispatchers.Main) { callback.onProviderResult(provider, res) }
                        }
                        if (resultsList.isNotEmpty()) {
                            LyricsHelper.put(track.getCacheKey(), resultsList)
                        }
                        return@launch
                    }

                    LyricsDecisionEngine.FetchDecision.USE_CACHE_AND_SEARCH_BACKGROUND -> {
                        telemetry.logCacheStatus("HIT_UNSYNCED", "showing cached, searching for synced")
                        
                        val text = cached.syncedLyrics ?: cached.plainLyrics
                        if (!text.isNullOrBlank()) {
                            LyricsHelper.putSingle(track.cleanedTitle, track.cleanedArtist, text)
                        }

                        withContext(Dispatchers.Main) {
                            callback.onProviderResult(cached.providerName.orEmpty(), cached)
                            callback.onLyricsFound(cached)
                        }
                        // Continue to background search for synced upgrade
                        launchBackgroundUpgrade(track, cached, callback)
                        return@launch
                    }

                    else -> {
                        // For FETCH or SKIP_NEGATIVE_CACHE, continue pipeline execution
                    }
                }
            }

            // If negative cache was set previously, clear it on fetch to allow auto-search across all providers
            if (cacheManager.isNegativeCacheActive(track)) {
                telemetry.logCacheStatus("NEGATIVE_BYPASS", "Bypassing negative cache to search all providers")
            }

            telemetry.logCacheStatus("MISS", null)

            if (cancelled.get()) return@launch

            withContext(Dispatchers.Main) {
                callback.onSearchStatus("Searching multiple sources…")
            }

            val resultsList = mutableListOf<LyricsResult>()
            // Search all providers using the concurrent select engine
            val result = router.searchAll(track, track.detectedLanguage, cancelOnEarlyWin = false) { provider, res ->
                if (res != null) {
                    synchronized(resultsList) {
                        resultsList.add(res)
                    }
                }
                bgScope.launch(Dispatchers.Main) { callback.onProviderResult(provider, res) }
            }

            if (resultsList.isNotEmpty()) {
                LyricsHelper.put(track.getCacheKey(), resultsList)
            }

            if (cancelled.get()) return@launch

            if (result != null && result.hasLyrics()) {
                // Decision engine evaluates the result
                when (val decision = decisionEngine.decide(result, null)) {
                    LyricsDecisionEngine.Decision.ACCEPT -> {
                        cacheManager.put(track, result)
                        
                        val text = result.syncedLyrics ?: result.plainLyrics
                        if (!text.isNullOrBlank()) {
                            LyricsHelper.putSingle(track.cleanedTitle, track.cleanedArtist, text)
                        }

                        telemetry.logResult(result, "ACCEPTED")
                        withContext(Dispatchers.Main) {
                            callback.onLyricsFound(result)
                        }
                    }

                    LyricsDecisionEngine.Decision.SHOW_AND_CONTINUE -> {
                        cacheManager.put(track, result)
                        
                        val text = result.syncedLyrics ?: result.plainLyrics
                        if (!text.isNullOrBlank()) {
                            LyricsHelper.putSingle(track.cleanedTitle, track.cleanedArtist, text)
                        }

                        telemetry.logResult(result, "SHOW_AND_CONTINUE")
                        withContext(Dispatchers.Main) {
                            callback.onLyricsFound(result)
                        }

                        // Continue searching for better result in background
                        if (!result.isSynced) {
                            launchBackgroundUpgrade(track, result, callback)
                        }
                    }

                    LyricsDecisionEngine.Decision.MARK_INSTRUMENTAL -> {
                        cacheManager.putNegative(track)
                        telemetry.logResult(result, "INSTRUMENTAL")
                        withContext(Dispatchers.Main) {
                            callback.onInstrumental()
                        }
                    }

                    LyricsDecisionEngine.Decision.REJECT -> {
                        // Try deep search with alternate queries
                        val deepResult = deepSearch(track)
                        if (deepResult != null && !cancelled.get()) {
                            cacheManager.put(track, deepResult)
                            
                            val text = deepResult.syncedLyrics ?: deepResult.plainLyrics
                            if (!text.isNullOrBlank()) {
                                LyricsHelper.putSingle(track.cleanedTitle, track.cleanedArtist, text)
                            }

                            telemetry.logResult(deepResult, "DEEP_SEARCH_ACCEPT")
                            withContext(Dispatchers.Main) {
                                callback.onLyricsFound(deepResult)
                            }
                        } else if (!cancelled.get()) {
                            cacheManager.putNegative(track)
                            telemetry.logFailure("All providers returned low confidence results")
                            withContext(Dispatchers.Main) {
                                callback.onLyricsNotFound("No matching lyrics found")
                            }
                        }
                    }

                    else -> {
                        cacheManager.putNegative(track)
                        telemetry.logFailure("Decision: $decision")
                        withContext(Dispatchers.Main) {
                            callback.onLyricsNotFound("No lyrics available")
                        }
                    }
                }
            } else if (!cancelled.get()) {
                // Deep search as last resort
                val deepResult = deepSearch(track)
                if (deepResult != null && !cancelled.get()) {
                    cacheManager.put(track, deepResult)
                    
                    val text = deepResult.syncedLyrics ?: deepResult.plainLyrics
                    if (!text.isNullOrBlank()) {
                        LyricsHelper.putSingle(track.cleanedTitle, track.cleanedArtist, text)
                    }

                    telemetry.logResult(deepResult, "DEEP_SEARCH_ACCEPT")
                    withContext(Dispatchers.Main) {
                        callback.onLyricsFound(deepResult)
                    }
                } else if (!cancelled.get()) {
                    cacheManager.putNegative(track)
                    telemetry.logFailure("No results from any provider")
                    withContext(Dispatchers.Main) {
                        callback.onLyricsNotFound("No lyrics available")
                    }
                }
            }
        }
    }

    /**
     * Retry lyrics search — clears negative cache and forces full refetch.
     */
    fun retryLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        filePath: String?,
        callback: LyricsCallback,
        videoId: String? = null
    ) {
        val track = metadataRepair.repair(title, artist, album, durationMs, filePath, videoId)
        cacheManager.evict(track) // Clear ALL cache for this track
        providerStats.resetAll() // Reset provider failure state
        Log.d(TAG, "Retry: evicted cache and reset stats for ${track.getCacheKey()}")

        // Now do a full fetch
        fetchLyrics(title, artist, album, durationMs, filePath, callback, videoId)
    }

    /**
     * Cancel the current fetch operation (e.g., when song changes).
     */
    fun cancelCurrent() {
        cancelled.set(true)
        currentFetch?.let {
            if (it.isActive) {
                it.cancel()
                Log.d(TAG, "Cancelled current fetch")
            }
        }
    }

    /**
     * Deep search: retry with alternate query formulations.
     */
    private suspend fun deepSearch(track: TrackMetadata): LyricsResult? {
        if (cancelled.get()) return null

        val queries = normalizer.generateQueries(track)
        telemetry.logQueries(queries)

        // Skip first query (already tried) and try remaining
        for (i in 1 until queries.size) {
            if (cancelled.get()) break
            val q = queries[i]
            val altTrack = TrackMetadata(
                rawTitle = q[1],
                rawArtist = q[0],
                cleanedTitle = q[1],
                cleanedArtist = q[0],
                durationMs = track.durationMs,
                filePath = track.filePath,
                version = track.version,
                detectedLanguage = track.detectedLanguage
            )

            val result = router.searchAll(altTrack, track.detectedLanguage)
            if (result != null && result.hasLyrics()) {
                val confidence = result.confidence
                if (confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                    return result
                }
            }
        }

        return null
    }

    /**
     * Background search for better lyrics while currently showing lower-quality.
     * Handles: unsynced → synced, synced → word-by-word.
     */
    private fun launchBackgroundUpgrade(
        track: TrackMetadata,
        current: LyricsResult,
        callback: LyricsCallback
    ) {
        bgScope.launch {
            if (cancelled.get()) return@launch

            Log.d(TAG, "Background upgrade search (current: synced=${current.isSynced}, wordByWord=${current.isWordByWord})...")

            val resultsList = mutableListOf<LyricsResult>()
            // Search all providers for a better result
            val result = router.searchAll(track, track.detectedLanguage, cancelOnEarlyWin = false) { provider, res ->
                if (res != null) {
                    synchronized(resultsList) {
                        resultsList.add(res)
                    }
                }
                bgScope.launch(Dispatchers.Main) { callback.onProviderResult(provider, res) }
            }

            if (resultsList.isNotEmpty()) {
                LyricsHelper.put(track.getCacheKey(), resultsList)
            }

            if (cancelled.get()) return@launch

            if (result != null && result.hasLyrics() && result.confidence >= ConfidenceScorer.THRESHOLD_SHOW) {
                val decision = decisionEngine.decide(result, current)
                if (decision == LyricsDecisionEngine.Decision.REPLACE_UNSYNCED ||
                    decision == LyricsDecisionEngine.Decision.REPLACE_WITH_WORD_BY_WORD ||
                    decision == LyricsDecisionEngine.Decision.ACCEPT
                ) {
                    cacheManager.put(track, result)
                    
                    val text = result.syncedLyrics ?: result.plainLyrics
                    if (!text.isNullOrBlank()) {
                        LyricsHelper.putSingle(track.cleanedTitle, track.cleanedArtist, text)
                    }

                    telemetry.logResult(result, "BACKGROUND_UPGRADE")
                    withContext(Dispatchers.Main) {
                        callback.onLyricsUpgraded(result)
                    }
                }
            }
        }
    }

    private fun createProviders(client: OkHttpClient): List<LyricsProvider> {
        val providers = ArrayList<LyricsProvider>()

        // ═══════════════════════════════════════════
        // LOCAL PROVIDERS (no network, highest priority)
        // ═══════════════════════════════════════════
        providers.add(EmbeddedLyricsProvider())

        // ═══════════════════════════════════════════
        // YOUTUBE MUSIC PROVIDER (official source, highest network priority)
        // ═══════════════════════════════════════════
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.YouTubeMusicLyricsProvider())

        // ═══════════════════════════════════════════
        // NETWORKING APIS
        // ═══════════════════════════════════════════

        val gsonConverter = GsonConverterFactory.create()

        // SyncLRC
        val syncLrcApi = Retrofit.Builder()
            .baseUrl("https://api.synclrc.dev/")
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(com.codetrio.spatialflow.data.lyrics.SyncLrcApi::class.java)

        // LRCLIB
        val lrcLibApi = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(LrcLibApi::class.java)

        // KuGou
        val kugouApi = Retrofit.Builder()
            .baseUrl("https://wwwapi.kugou.com/")
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(KugouApi::class.java)

        val kugouLegacyApi = Retrofit.Builder()
            .baseUrl("http://lyrics.kugou.com/")
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(com.codetrio.spatialflow.data.lyrics.KugouLegacyApi::class.java)

        // BetterLyrics
        val betterLyricsApiInstance = Retrofit.Builder()
            .baseUrl("https://lyrics-api.boidu.dev/")
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(BetterLyricsApi::class.java)
        this.betterLyricsApi = betterLyricsApiInstance

        // SimpMusic
        val simpMusicApi = Retrofit.Builder()
            .baseUrl("https://api-lyrics.simpmusic.org/")
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(SimpMusicApi::class.java)

        // YouLyPlus (multi-mirror TTML / LRC Karaoke)
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.YouLyPlusProvider())

        // YouTube Subtitle (timed transcript captions)
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.YouTubeSubtitleProvider())

        // Paxsenix providers — no Retrofit needed; PaxsenixLyrics singleton handles HTTP with OkHttp
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.paxsenix.PaxsenixAppleMusicProvider(appContext))
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.paxsenix.PaxsenixSpotifyProvider(appContext))
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.paxsenix.PaxsenixMusixmatchProvider(appContext))
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.paxsenix.PaxsenixNeteaseProvider(appContext))
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.paxsenix.PaxsenixYouTubeProvider(appContext))
        providers.add(com.codetrio.spatialflow.data.lyrics.providers.SyncLrcProvider(syncLrcApi))
        providers.add(LrcLibProvider(lrcLibApi))
        providers.add(KugouProvider(kugouApi, kugouLegacyApi))
        providers.add(BetterLyricsProvider(betterLyricsApiInstance))
        providers.add(SimpMusicProvider(simpMusicApi))

        Log.d(TAG, "Initialized ${providers.size} lyrics providers")
        return providers
    }


}
