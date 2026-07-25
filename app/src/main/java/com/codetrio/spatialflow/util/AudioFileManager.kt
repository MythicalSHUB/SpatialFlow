@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object AudioFileManager {

    private const val TAG = "AudioFileManager"

    @JvmStatic
    fun getRealPathFromURI(context: Context, uri: Uri?): String? {
        if (uri == null) return null

        try {
            val fileName = getFileName(context, uri)
            val tempFile = File(
                context.cacheDir,
                "temp_${System.currentTimeMillis()}_$fileName"
            )

            context.contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) {
                    Log.e(TAG, "Cannot open input stream from URI")
                    return null
                }
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }

            return tempFile.absolutePath

        } catch (e: IOException) {
            Log.e(TAG, "Error extracting file from URI: ${e.message}", e)
            return null
        }
    }

    @JvmStatic
    fun createOutputFile(context: Context, fileName: String): File {
        var finalFileName = fileName
        if (!finalFileName.endsWith(".m4a", ignoreCase = true) &&
            !finalFileName.endsWith(".mp3", ignoreCase = true) &&
            !finalFileName.endsWith(".opus", ignoreCase = true)
        ) {
            finalFileName += ".m4a"
        }

        val cleanName = finalFileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Create temp file in cache for FFmpeg processing
            val tempDir = File(context.cacheDir, "SpatialFlow_output")
            if (!tempDir.exists()) tempDir.mkdirs()

            val tempFile = File(tempDir, cleanName)
            Log.d(TAG, "Temp output file for processing: ${tempFile.absolutePath}")
            tempFile
        } else {
            // Android 9 and below - Direct file access to Downloads/SpatialFlow
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "SpatialFlow"
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val outputFile = File(downloadsDir, cleanName)
            Log.d(TAG, "Output file (legacy): ${outputFile.absolutePath}")
            outputFile
        }
    }

    @JvmStatic
    fun scanFile(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Copy to MediaStore Audio for Android 10+
            copyToMediaStore(context, file)
        } else {
            // Trigger media scanner for older versions
            try {
                context.sendBroadcast(
                    android.content.Intent(
                        android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(file)
                    )
                )
                Log.d(TAG, "Media scan requested: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "scanFile failed: ${e.message}")
            }
        }
    }

    private fun copyToMediaStore(context: Context, sourceFile: File) {
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file doesn't exist: ${sourceFile.absolutePath}")
            return
        }

        // Get clean metadata from filename
        var nameWithoutExtension = sourceFile.name
        if (nameWithoutExtension.endsWith(".mp3", ignoreCase = true)) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length - 4)
        } else if (nameWithoutExtension.endsWith(".m4a", ignoreCase = true)) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length - 4)
        } else if (nameWithoutExtension.endsWith(".opus", ignoreCase = true)) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length - 5)
        }

        // Split by " - " to get Title and Artist
        var title = nameWithoutExtension
        var artist = "Unknown Artist"
        if (nameWithoutExtension.contains(" - ")) {
            val parts = nameWithoutExtension.split(" - ".toRegex(), 2).toTypedArray()
            title = parts[0].trim { it <= ' ' }
            artist = parts[1].trim { it <= ' ' }
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)

            val mimeType = when {
                sourceFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                sourceFile.name.endsWith(".opus", ignoreCase = true) -> "audio/opus"
                else -> "audio/mp4"
            }
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SpatialFlow")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values)

        if (itemUri == null) {
            Log.e(TAG, "Failed to create MediaStore entry")
            return
        }

        try {
            FileInputStream(sourceFile).use { `in` ->
                resolver.openOutputStream(itemUri).use { out ->
                    if (out == null) {
                        Log.e(TAG, "Failed to open output stream")
                        return
                    }

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (`in`.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }

            Log.d(TAG, "File copied to MediaStore successfully: Music/SpatialFlow/${sourceFile.name}")

        } catch (e: IOException) {
            Log.e(TAG, "Error copying to MediaStore: ${e.message}", e)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null

        if ("content" == uri.scheme) {
            try {
                context.contentResolver.query(uri, null, null, null, null).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting filename: ${e.message}")
            }
        }

        if (name == null) {
            name = uri.lastPathSegment
        }

        return name ?: "audio.m4a"
    }
}
