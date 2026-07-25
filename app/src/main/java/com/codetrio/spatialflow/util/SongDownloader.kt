package com.codetrio.spatialflow.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

import androidx.core.app.NotificationCompat
import android.net.Uri
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor
import com.codetrio.spatialflow.model.SongItem
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SongDownloader {
    private const val TAG = "SongDownloader"
    private const val CHANNEL_ID = "spatialflow_downloads"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    fun cleanTitle(title: String): String {
        var clean = title
        val regexes = listOf(
            "(?i)\\[official music video\\]",
            "(?i)\\(official music video\\)",
            "(?i)\\[official video\\]",
            "(?i)\\(official video\\)",
            "(?i)\\[lyrics\\]",
            "(?i)\\(lyrics\\)",
            "(?i)\\[lyric video\\]",
            "(?i)\\(lyric video\\)",
            "(?i)\\[official audio\\]",
            "(?i)\\(official audio\\)",
            "(?i)official music video",
            "(?i)official video",
            "(?i)lyric video",
            "(?i)\\[video\\]",
            "(?i)\\(video\\)",
            "(?i)\\[official release\\]",
            "(?i)\\(official release\\)"
        )
        for (regex in regexes) {
            clean = clean.replace(Regex(regex), "")
        }
        // Remove trailing dashes, slashes, and spaces
        return clean.trim().replace(Regex("\\s+"), " ").removeSuffix("-").removeSuffix("/").trim()
    }

    fun cleanArtist(artist: String): String {
        return artist.replace(Regex("(?i)\\s*-\\s*Topic"), "").trim().replace(Regex("\\s+"), " ")
    }

    fun cleanAlbum(title: String): String {
        var clean = cleanTitle(title)
        val albumRegexes = listOf(
            "(?i)\\s*\\((slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\)",
            "(?i)\\s*\\[(slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\]"
        )
        for (regex in albumRegexes) {
            clean = clean.replace(Regex(regex), "")
        }
        return clean.trim().replace(Regex("\\s+"), " ")
    }

    fun downloadSong(context: Context, song: SongItem) {
        val videoId = song.videoId
        if (videoId.isNullOrEmpty()) {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Local songs are already offline")
            return
        }

        val notificationId = song.id.toInt().coerceAtLeast(1)
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)

        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Extracting and starting download: $cleanTitleStr")

        scope.launch {
            _downloadProgress.value += (videoId to 0)
            try {
                // Initialize/Update download notification
                updateNotification(context, cleanTitleStr, 0, notificationId)

                val playerResult = NewPipeStreamExtractor.getPlayerResult(videoId)
                val bestStream = playerResult?.streams?.firstOrNull()
                val streamUrl = bestStream?.url ?: NewPipeStreamExtractor.getStreamUrl(videoId)
                if (streamUrl == null) {
                    cancelNotification(context, notificationId)
                    withContext(Dispatchers.Main) {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to extract download URL for $cleanTitleStr")
                    }
                    return@launch
                }

                val mimeType = bestStream?.mimeType ?: ""
                val isOpusSource = mimeType.contains("webm", ignoreCase = true) || 
                                   mimeType.contains("opus", ignoreCase = true) ||
                                   streamUrl.contains("mime=audio/webm") ||
                                   streamUrl.contains("codecs=opus")

                // Create temp download file
                val tempDownloadFile = File(context.cacheDir, "temp_download_${videoId}.tmp")
                val url = URL(streamUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    cancelNotification(context, notificationId)
                    withContext(Dispatchers.Main) {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Download server error: HTTP ${connection.responseCode}")
                    }
                    return@launch
                }

                val totalBytes = connection.contentLength
                connection.inputStream.use { input ->
                    FileOutputStream(tempDownloadFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes.toFloat() / totalBytes) * 100).toInt()
                                if (_downloadProgress.value[videoId] != progress) {
                                    _downloadProgress.value += (videoId to progress)
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 400) { // Throttle updates to every 400ms for fluid CPU performance
                                    lastProgressUpdate = now
                                    updateNotification(context, cleanTitleStr, progress, notificationId)
                                }
                            }
                        }
                    }
                }

                // Load, auto-crop 1:1, and compress thumbnail to a temp file
                var artworkFile: File? = null
                val thumbnailUrl = song.thumbnailUrl
                if (!thumbnailUrl.isNullOrEmpty()) {
                    try {
                        val future = Glide.with(context)
                            .asBitmap()
                            .load(thumbnailUrl)
                            .submit()
                        val originalBitmap = future.get()
                        if (originalBitmap != null) {
                            val size = originalBitmap.width.coerceAtMost(originalBitmap.height)
                            val x = (originalBitmap.width - size) / 2
                            val y = (originalBitmap.height - size) / 2
                            val croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, size, size)
                            
                            val artFile = File(context.cacheDir, "temp_art_${videoId}.jpg")
                            FileOutputStream(artFile).use { fos ->
                                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                            }
                            artworkFile = artFile
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch and crop thumbnail", e)
                    }
                }

                // Prepare output file (Ogg Opus format)
                val fileName = "$cleanTitleStr - $cleanArtistStr.opus"
                val outputFile = AudioFileManager.createOutputFile(context, fileName)
                val cleanAlbumStr = cleanAlbum(song.title)

                // Encode cover art as METADATA_BLOCK_PICTURE (Opus/FLAC Vorbis Comment standard)
                // Must be done BEFORE artworkFile is deleted.
                val pictureBase64: String = artworkFile?.let { art ->
                    try {
                        val jpegBytes = art.readBytes()
                        val mime = "image/jpeg"
                        val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
                        val buf = java.nio.ByteBuffer.allocate(
                            4 +                // picture type
                            4 + mimeBytes.size + // MIME type length + MIME type string
                            4 +                // description length (empty, 0 bytes follow)
                            4 +                // width
                            4 +                // height
                            4 +                // color depth
                            4 +                // indexed-color count
                            4 + jpegBytes.size // data length + raw JPEG bytes
                        ).order(java.nio.ByteOrder.BIG_ENDIAN)
                        buf.putInt(3)             // picture type: front cover
                        buf.putInt(mimeBytes.size)
                        buf.put(mimeBytes)
                        buf.putInt(0)             // description length (empty)
                        buf.putInt(0)             // width
                        buf.putInt(0)             // height
                        buf.putInt(0)             // color depth
                        buf.putInt(0)             // indexed-color count
                        buf.putInt(jpegBytes.size)
                        buf.put(jpegBytes)
                        android.util.Base64.encodeToString(buf.array(), android.util.Base64.NO_WRAP)
                    } catch (e: Exception) {
                        Log.e(TAG, "METADATA_BLOCK_PICTURE encoding failed", e)
                        ""
                    }
                } ?: ""

                // Package using FFmpeg — metadata + cover art embedded as Vorbis Comments natively
                val ffmpegArgs = FFmpegCommandBuilder.buildOpusPackageArgs(
                    inputPath = tempDownloadFile.absolutePath,
                    outputPath = outputFile.absolutePath,
                    isOpusSource = isOpusSource,
                    title = cleanTitleStr,
                    artist = cleanArtistStr,
                    album = cleanAlbumStr,
                    pictureBase64 = pictureBase64
                ).toTypedArray()

                Log.d(TAG, "Executing FFmpeg packaging (${ffmpegArgs.size} args)")
                val session = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(ffmpegArgs)
                val success = ReturnCode.isSuccess(session.returnCode)

                // Clean up temp files
                try { tempDownloadFile.delete() } catch (_: Exception) {}
                try { artworkFile?.delete() } catch (_: Exception) {}

                if (!success) {
                    Log.e(TAG, "FFmpeg packaging failed with code ${session.returnCode}\n${session.allLogsAsString}")
                    throw Exception("FFmpeg failed to remux audio stream to Opus container")
                }

                // Copy file to MediaStore and request a media scan
                AudioFileManager.scanFile(context, outputFile)

                // Show completion status
                showFinishedNotification(context, cleanTitleStr, cleanArtistStr, notificationId)

                _downloadProgress.value -= videoId
                withContext(Dispatchers.Main) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                        "Downloaded: $cleanTitleStr",
                        iconResId = R.drawable.ic_downloaded
                    )
                }

            } catch (e: Exception) {
                _downloadProgress.value -= videoId
                Log.e(TAG, "Download failed for ${song.title}", e)
                cancelNotification(context, notificationId)
                withContext(Dispatchers.Main) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Download failed: ${e.message}")
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active song downloads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(context: Context, title: String, progress: Int, notificationId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.codetrio.spatialflow.R.drawable.ic_music_note)
            .setContentTitle("Downloading Track")
            .setContentText("$title ($progress%)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun showFinishedNotification(context: Context, title: String, artist: String, notificationId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.codetrio.spatialflow.R.drawable.ic_music_note)
            .setContentTitle("Download Complete")
            .setContentText("$title — $artist")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    fun isSongDownloaded(context: Context, song: SongItem): Boolean {
        if (song.videoId.isNullOrEmpty()) return true // Local songs are always offline
        
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        
        val fileNames = listOf(
            "$cleanTitleStr - $cleanArtistStr.opus",
            "$cleanTitleStr - $cleanArtistStr.mp3"
        )

        for (fileName in fileNames) {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")

            // 1. Check legacy direct file path if pre-Q
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow")
                val file = File(downloadsDir, cleanName)
                if (file.exists()) return true
            }

            // 2. Query MediaStore
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(cleanName)
            
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.count > 0) return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if song is downloaded", e)
            }
        }

        return false
    }

    fun getDownloadedSongUri(context: Context, song: SongItem): Uri? {
        if (song.videoId.isNullOrEmpty()) return null
        
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        
        val fileNames = listOf(
            "$cleanTitleStr - $cleanArtistStr.opus",
            "$cleanTitleStr - $cleanArtistStr.mp3"
        )

        for (fileName in fileNames) {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")

            // 1. Check legacy direct file path if pre-Q
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow")
                val file = File(downloadsDir, cleanName)
                if (file.exists()) return Uri.fromFile(file)
            }

            // 2. Query MediaStore
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(cleanName)
            
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val id = cursor.getLong(idIndex)
                        return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving downloaded song URI", e)
            }
        }
        return null
    }

}
