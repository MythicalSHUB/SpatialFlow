package com.codetrio.spatialflow.data.cache

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.EmptyResult
import com.codetrio.spatialflow.domain.error.Result
import com.codetrio.spatialflow.domain.repository.LocalCacheDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileCacheDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalCacheDataSource {

    companion object {
        private const val TAG = "FileCacheDataSource"
        private val TEMP_FILE_PREFIXES = arrayOf("temp_", "8d_audio_")
        private val CACHE_SUBDIRS = arrayOf("SpatialFlow_output")
    }

    override suspend fun clearOldCache(): EmptyResult<DataError.Local> = withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0
            var freedBytes = 0L

            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                // Clean temp files with known prefixes
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (shouldDeleteFile(file)) {
                            val size = file.length()
                            if (file.delete()) {
                                deletedCount++
                                freedBytes += size
                            }
                        }
                    }
                }

                // Clean subdirectories
                for (subdir in CACHE_SUBDIRS) {
                    val dir = File(cacheDir, subdir)
                    if (dir.exists() && dir.isDirectory) {
                        val dirSize = deleteDirectory(dir)
                        if (dirSize > 0) {
                            deletedCount++
                            freedBytes += dirSize
                        }
                    }
                }
            }

            // Clear Glide disk cache
            try {
                Glide.get(context).clearDiskCache()
                Log.d(TAG, "Glide disk cache cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear Glide cache: ${e.message}")
            }

            if (deletedCount > 0) {
                Log.d(TAG, "Cache cleanup: deleted $deletedCount files, freed ${formatSize(freedBytes)}")
            } else {
                Log.d(TAG, "Cache cleanup: no old files to delete")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Cache cleanup failed: ${e.message}", e)
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    private fun shouldDeleteFile(file: File?): Boolean {
        if (file == null || !file.isFile) return false
        val name = file.name
        return TEMP_FILE_PREFIXES.any { name.startsWith(it) }
    }

    private fun deleteDirectory(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var totalSize = 0L
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    totalSize += deleteDirectory(file)
                } else {
                    totalSize += file.length()
                    file.delete()
                }
            }
        }
        dir.delete()
        return totalSize
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
