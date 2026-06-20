package com.codetrio.spatialflow.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class to manage app cache and clean up temp files.
 * Clears old temporary files (FFmpeg output, temp audio copies) on startup
 * to prevent app size bloat.
 */
public class CacheManager {

    private static final String TAG = "CacheManager";

    // File patterns to clean
    private static final String[] TEMP_FILE_PREFIXES = {
            "temp_", // Audio temp copies from AudioFileManager
            "8d_audio_", // 8D processed audio from AudioPlaybackService
    };

    // Directories to clean
    private static final String[] CACHE_SUBDIRS = {
            "SpatialFlow_output" // FFmpeg output directory
    };

    /**
     * Clear old cache files on app startup.
     * Runs on background thread to avoid blocking main thread.
     */
    public static void clearOldCache(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                int deletedCount = 0;
                long freedBytes = 0;

                File cacheDir = context.getCacheDir();
                if (cacheDir != null && cacheDir.exists()) {
                    // Clean temp files with known prefixes
                    File[] files = cacheDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (shouldDeleteFile(file)) {
                                long size = file.length();
                                if (file.delete()) {
                                    deletedCount++;
                                    freedBytes += size;
                                }
                            }
                        }
                    }

                    // Clean subdirectories
                    for (String subdir : CACHE_SUBDIRS) {
                        File dir = new File(cacheDir, subdir);
                        if (dir.exists() && dir.isDirectory()) {
                            long dirSize = deleteDirectory(dir);
                            if (dirSize > 0) {
                                deletedCount++;
                                freedBytes += dirSize;
                            }
                        }
                    }
                }

                // Clear Glide disk cache
                try {
                    Glide.get(context).clearDiskCache();
                    Log.d(TAG, "Glide disk cache cleared");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to clear Glide cache: " + e.getMessage());
                }

                if (deletedCount > 0) {
                    Log.d(TAG, "Cache cleanup: deleted " + deletedCount + " files, freed " +
                            formatSize(freedBytes));
                } else {
                    Log.d(TAG, "Cache cleanup: no old files to delete");
                }

            } catch (Exception e) {
                Log.e(TAG, "Cache cleanup failed: " + e.getMessage(), e);
            }
        });
        executor.shutdown();
    }

    /**
     * Check if a file should be deleted based on its name prefix.
     */
    private static boolean shouldDeleteFile(File file) {
        if (file == null || !file.isFile())
            return false;

        String name = file.getName();
        for (String prefix : TEMP_FILE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively delete a directory and all its contents.
     * 
     * @return Total bytes deleted
     */
    private static long deleteDirectory(File dir) {
        long totalSize = 0;

        if (dir == null || !dir.exists())
            return 0;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    totalSize += deleteDirectory(file);
                } else {
                    totalSize += file.length();
                    file.delete();
                }
            }
        }
        dir.delete();
        return totalSize;
    }

    /**
     * Format bytes to human-readable string.
     */
    @SuppressLint("DefaultLocale")
    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
