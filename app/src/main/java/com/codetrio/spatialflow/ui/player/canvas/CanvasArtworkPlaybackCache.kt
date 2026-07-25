package com.codetrio.spatialflow.ui.player.canvas

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "CanvasArtworkCache"
private const val CANVAS_MAX_DIMENSION_PX = 4_096 // Increased for high quality support (4K)

object CanvasArtworkPlaybackCache {

    private const val DEFAULT_MAX_SIZE_MB = 128 // Reduced from 512MB for high-res videos
    private const val PERSIST_FILE = "canvas_artwork_cache.json"
    private const val PERSIST_DEBOUNCE_MS = 2_000L
    private const val DOWNLOAD_BUFFER = 64 * 1024
    private const val DOWNLOAD_MAX_ATTEMPTS = 4
    private const val DOWNLOAD_RETRY_DELAY_MS = 750L
    private const val BYTES_PER_MB = 1024L * 1024L

    private val map = LinkedHashMap<String, CanvasCacheEntry>(DEFAULT_MAX_SIZE_MB, 0.75f, true)
    private val cacheJobs = LinkedHashMap<String, Job>()

    @Volatile private var maxSizeBytes = DEFAULT_MAX_SIZE_MB.toLong() * BYTES_PER_MB
    @Volatile private var cacheDirectory: File? = null
    @Volatile private var cacheFile: File? = null

    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", CANVAS_DOWNLOAD_UA)
                        .header("Connection", "keep-alive")
                        .build()
                )
            }
            .build()
    }

    fun init(context: Context) {
        val directory = File(context.cacheDir, "canvas_cache")
        cacheDirectory = directory
        cacheFile = directory.resolve(PERSIST_FILE)
        loadFromDisk()
    }

    @Synchronized
    fun get(mediaId: String, preferCachedOnly: Boolean = false): CanvasArtwork? {
        if (maxSizeBytes == 0L || mediaId.isBlank()) return null
        val entry = map[mediaId] ?: return null
        val playable = entry.toPlayableArtwork(
            directory = cacheDirectory ?: return null,
            preferCachedOnly = preferCachedOnly,
        )
        if (playable == null) {
            map.remove(mediaId)
            schedulePersist()
            return null
        }
        map[mediaId] = entry.copy(lastAccessedAtMs = System.currentTimeMillis())
        schedulePersist()
        return playable
    }

    suspend fun put(mediaId: String, artwork: CanvasArtwork): CanvasArtwork =
        withContext(Dispatchers.IO) {
            if (maxSizeBytes == 0L || mediaId.isBlank()) return@withContext artwork
            val directory = cacheDirectory ?: return@withContext artwork
            directory.mkdirs()
            val current = synchronized(this@CanvasArtworkPlaybackCache) {
                map[mediaId]?.toPlayableArtwork(directory = directory, preferCachedOnly = false)
            }
            cacheArtworkInBackground(directory = directory, mediaId = mediaId, artwork = artwork)
            current ?: artwork
        }

    private fun cacheArtworkInBackground(directory: File, mediaId: String, artwork: CanvasArtwork) {
        synchronized(this@CanvasArtworkPlaybackCache) {
            cacheJobs[mediaId]?.takeIf { it.isActive }?.let { return }
            cacheJobs[mediaId] = persistScope.launch {
                try {
                    cacheArtworkVideos(directory = directory, mediaId = mediaId, artwork = artwork)
                } finally {
                    synchronized(this@CanvasArtworkPlaybackCache) { cacheJobs.remove(mediaId) }
                }
            }
        }
    }

    private suspend fun cacheArtworkVideos(directory: File, mediaId: String, artwork: CanvasArtwork) {
        val current = synchronized(this@CanvasArtworkPlaybackCache) { map[mediaId] }

        // Fetch both variants concurrently for efficiency
        withContext(Dispatchers.IO) {
            val regularJob = launch {
                val regularFileName = cacheCanvasVideo(
                    directory = directory,
                    mediaId = mediaId,
                    variant = CanvasVideoVariant.Regular,
                    url = artwork.downloadableRegularUrl(),
                    currentFileName = current?.regularFileName,
                )
                synchronized(this@CanvasArtworkPlaybackCache) {
                    val updated = map[mediaId] ?: CanvasCacheEntry(
                        mediaId = mediaId,
                        artwork = artwork,
                        createdAtMs = System.currentTimeMillis(),
                        lastAccessedAtMs = System.currentTimeMillis(),
                    )
                    persistEntry(
                        directory = directory,
                        entry = updated.copy(
                            regularFileName = regularFileName,
                            lastAccessedAtMs = System.currentTimeMillis()
                        ),
                    )
                }
            }

            val verticalJob = launch {
                val verticalFileName = cacheCanvasVideo(
                    directory = directory,
                    mediaId = mediaId,
                    variant = CanvasVideoVariant.Vertical,
                    url = artwork.downloadableVerticalUrl(),
                    currentFileName = current?.verticalFileName,
                )
                synchronized(this@CanvasArtworkPlaybackCache) {
                    val updated = map[mediaId] ?: CanvasCacheEntry(
                        mediaId = mediaId,
                        artwork = artwork,
                        createdAtMs = System.currentTimeMillis(),
                        lastAccessedAtMs = System.currentTimeMillis(),
                    )
                    persistEntry(
                        directory = directory,
                        entry = updated.copy(
                            verticalFileName = verticalFileName,
                            lastAccessedAtMs = System.currentTimeMillis()
                        ),
                    )
                }
            }

            regularJob.join()
            verticalJob.join()
        }
    }

    @Synchronized
    fun clear() {
        cancelCacheJobsLocked()
        clearFilesLocked()
        map.clear()
        schedulePersist()
    }

    fun clearAndPersist(): Boolean {
        synchronized(this) {
            cancelCacheJobsLocked()
            clearFilesLocked()
            map.clear()
            persistJob?.cancel()
        }
        return writeToDisk()
    }

    @Synchronized
    fun setMaxSize(valueMb: Int) {
        maxSizeBytes = valueMb.toCanvasCacheLimitBytes()
        val directory = cacheDirectory
        if (maxSizeBytes == 0L) {
            cancelCacheJobsLocked()
            clearFilesLocked()
            map.clear()
            schedulePersist()
            return
        }
        if (directory != null) trimLocked(directory)
        schedulePersist()
    }

    @Synchronized
    private fun loadFromDisk() {
        val file = cacheFile ?: return
        if (!file.exists()) return
        try {
            val raw = file.readText()
            if (raw.isBlank()) return
            val restored = decodeEntries(raw)
            map.clear()
            restored.filter { it.mediaId.isNotBlank() }.forEach { map[it.mediaId] = it }
            cacheDirectory?.let(::trimLocked)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to restore canvas cache: ${error.message}")
            runCatching { file.delete() }
        }
    }

    private fun decodeEntries(raw: String): List<CanvasCacheEntry> =
        runCatching {
            json.decodeFromString(ListSerializer(CanvasCacheEntry.serializer()), raw)
        }.getOrElse {
            Log.w(TAG, "Falling back to legacy cache format")
            emptyList()
        }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = persistScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            writeToDisk()
        }
    }

    private fun persistEntry(directory: File, entry: CanvasCacheEntry) {
        synchronized(this@CanvasArtworkPlaybackCache) {
            map[entry.mediaId] = entry
            trimLocked(directory)
            schedulePersist()
        }
    }

    private fun writeToDisk(): Boolean {
        val file = cacheFile ?: return true
        return try {
            val snapshot = synchronized(this@CanvasArtworkPlaybackCache) { map.values.toList() }
            val raw = json.encodeToString(ListSerializer(CanvasCacheEntry.serializer()), snapshot)
            file.parentFile?.mkdirs()
            file.writeText(raw)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Failed to write canvas cache: ${error.message}")
            false
        }
    }

    private suspend fun cacheCanvasVideo(
        directory: File,
        mediaId: String,
        variant: CanvasVideoVariant,
        url: String?,
        currentFileName: String?,
    ): String? {
        currentFileName?.takeIf {
            directory.resolve(it).let { f -> f.isUsableFile() && f.isPlayableCanvasVideo() }
        }?.let { return it }

        if (url.isNullOrBlank()) return null
        val fileName = canvasFileName(mediaId, variant, url)
        val target = directory.resolve(fileName)
        if (target.isUsableFile()) {
            if (target.isPlayableCanvasVideo()) return fileName
            runCatching { target.delete() }
        }

        val partial = directory.resolve("$fileName.part")
        return try {
            downloadToFile(url = url, target = partial)
            if (partial.length() <= 0L) throw IOException("Downloaded empty video")
            if (!partial.isPlayableCanvasVideo()) throw IOException("Codec unsupported on device")
            if (target.exists() && !target.delete()) throw IOException("Replace failed")
            if (!partial.renameTo(target)) throw IOException("Commit rename failed")
            fileName
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to cache $variant canvas for $mediaId: ${error.message}")
            runCatching { partial.delete() }
            currentFileName?.takeIf { directory.resolve(it).let { f -> f.isUsableFile() && f.isPlayableCanvasVideo() } }
        }
    }

    private suspend fun downloadToFile(url: String, target: File) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        target.parentFile?.mkdirs()
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < DOWNLOAD_MAX_ATTEMPTS) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                downloadPartial(
                    url = url,
                    target = target,
                    existingBytes = target.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L,
                )
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                attempt++
                if (attempt >= DOWNLOAD_MAX_ATTEMPTS) break
                delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
            }
        }
        throw IOException("Download failed after $DOWNLOAD_MAX_ATTEMPTS attempts", lastError)
    }

    private suspend fun downloadPartial(url: String, target: File, existingBytes: Long) {
        val requestBuilder = Request.Builder().url(url)
            .header("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
        if (existingBytes > 0L) requestBuilder.header("Range", "bytes=$existingBytes-")
        val request = requestBuilder.build()

        httpClient.newCall(request).execute().use { response ->
            if (existingBytes > 0L && response.code == 416) return
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val append = existingBytes > 0L && response.code == 206
            if (existingBytes > 0L && !append) {
                if (target.exists() && !target.delete()) throw IOException("Failed to restart download")
            }
            val body = response.body
            val contentType = body.contentType()?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (contentType.contains("mpegurl") || contentType.contains("m3u8") || contentType.startsWith("text/")) {
                throw IOException("Not direct video: $contentType")
            }
            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun trimLocked(directory: File) {
        val activeFiles = map.values.flatMap { listOfNotNull(it.regularFileName, it.verticalFileName) }.toSet()
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mp4") && it.name !in activeFiles }
            ?.forEach { runCatching { it.delete() } }
        trimToByteLimitLocked(directory)
    }

    private fun trimToByteLimitLocked(directory: File) {
        val limitBytes = maxSizeBytes
        if (limitBytes == Long.MAX_VALUE) return
        var totalBytes = map.values.sumOf { it.byteSize(directory) }
        val iterator = map.entries.iterator()
        while (totalBytes > limitBytes && iterator.hasNext()) {
            val entry = iterator.next().value
            val entryBytes = entry.byteSize(directory)
            iterator.remove()
            runCatching { entry.regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { entry.verticalFileName?.let { directory.resolve(it).delete() } }
            totalBytes -= entryBytes
        }
    }

    private fun clearFilesLocked() {
        val directory = cacheDirectory ?: return
        map.values.forEach { entry ->
            runCatching { entry.regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { entry.verticalFileName?.let { directory.resolve(it).delete() } }
        }
        directory.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".mp4") || it.name.endsWith(".part")) }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun cancelCacheJobsLocked() {
        cacheJobs.values.forEach { it.cancel() }
        cacheJobs.clear()
    }

    private fun canvasFileName(mediaId: String, variant: CanvasVideoVariant, url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$mediaId|${variant.cacheKey}|$url".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "${variant.cacheKey}-$digest.mp4"
    }

    private fun CanvasArtwork.downloadableRegularUrl(): String? =
        videoUrl.takeIfDownloadableVideo() ?: animated.takeIfDownloadableVideo()

    private fun CanvasArtwork.downloadableVerticalUrl(): String? =
        videoUrlVertical.takeIfDownloadableVideo() ?: animatedVertical.takeIfDownloadableVideo()

    private fun String?.takeIfDownloadableVideo(): String? =
        this?.trim()?.takeIf { value ->
            val normalized = value.lowercase(Locale.ROOT)
            value.isNotBlank() &&
                !normalized.contains(".m3u8") &&
                (normalized.startsWith("http://") || normalized.startsWith("https://"))
        }

    @Serializable
    internal data class CanvasCacheEntry(
        val mediaId: String,
        val artwork: CanvasArtwork,
        val regularFileName: String? = null,
        val verticalFileName: String? = null,
        val createdAtMs: Long,
        val lastAccessedAtMs: Long,
    ) {
        fun byteSize(directory: File): Long =
            listOfNotNull(regularFileName, verticalFileName).sumOf {
                directory.resolve(it).takeIf { f -> f.isUsableFile() }?.length() ?: 0L
            }

        fun toPlayableArtwork(directory: File, preferCachedOnly: Boolean): CanvasArtwork? {
            val regularUri = regularFileName?.let(directory::resolve)
                ?.takeIf { it.isUsableFile() && it.isPlayableCanvasVideo() }
                ?.let { Uri.fromFile(it).toString() }
            val verticalUri = verticalFileName?.let(directory::resolve)
                ?.takeIf { it.isUsableFile() && it.isPlayableCanvasVideo() }
                ?.let { Uri.fromFile(it).toString() }

            if (regularUri == null && verticalUri == null) return null
            return artwork.copy(
                animated = if (preferCachedOnly) regularUri else artwork.animated.takeIfNotBlank() ?: regularUri,
                videoUrl = regularUri,
                animatedVertical = if (preferCachedOnly) verticalUri else artwork.animatedVertical.takeIfNotBlank() ?: verticalUri,
                videoUrlVertical = verticalUri,
            )
        }
    }

    private enum class CanvasVideoVariant(val cacheKey: String) {
        Regular("regular"), Vertical("vertical")
    }
}

// ───────────── Extension helpers ─────────────

private fun File.isUsableFile(): Boolean = isFile && length() > 0L

private fun File.isPlayableCanvasVideo(): Boolean {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(absolutePath)
        (0 until extractor.trackCount).any { index ->
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            mime.startsWith("video/") && format.isSupportedCanvasVideoFormat()
        }
    } catch (e: Throwable) {
        false
    } finally {
        extractor.release()
    }
}

private fun MediaFormat.isSupportedCanvasVideoFormat(): Boolean {
    val mime = getString(MediaFormat.KEY_MIME)?.takeIf { it.startsWith("video/") } ?: return false
    val width = optionalInteger(MediaFormat.KEY_WIDTH) ?: return false
    val height = optionalInteger(MediaFormat.KEY_HEIGHT) ?: return false
    if (width > CANVAS_MAX_DIMENSION_PX || height > CANVAS_MAX_DIMENSION_PX) return false
    
    val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
    val codecInfos = codecList.codecInfos

    return runCatching {
        codecInfos.any { codecInfo ->
            if (codecInfo.isEncoder) return@any false
            val supportedType = codecInfo.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) } ?: return@any false
            val capabilities = codecInfo.getCapabilitiesForType(supportedType)
            val videoCaps = capabilities.videoCapabilities ?: return@any true
            
            // More lenient size check: if the codec claims to support the size, trust it.
            videoCaps.isSizeSupported(width, height) || videoCaps.isSizeSupported(height, width)
        }
    }.getOrDefault(false)
}

// Removed unused supportsCanvasSize and related variables to clean up warnings


private fun android.media.MediaCodecInfo.VideoCapabilities.supportsCanvasSize(
    width: Int,
    height: Int,
    frameRate: Double?,
): Boolean = runCatching {
    if (frameRate != null && frameRate > 0.0) areSizeAndRateSupported(width, height, frameRate)
    else isSizeSupported(width, height)
}.getOrDefault(false)

private fun MediaFormat.optionalInteger(key: String): Int? =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

private fun String?.takeIfNotBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun Int.toCanvasCacheLimitBytes(): Long =
    if (this < 0) Long.MAX_VALUE else if (this == 0) 0L else toLong() * 1024L * 1024L

private const val CANVAS_DOWNLOAD_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile"
