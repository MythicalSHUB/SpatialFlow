package com.codetrio.spatialflow.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AudioFileManager {

    private static final String TAG = "AudioFileManager";

    public static String getRealPathFromURI(Context context, Uri uri) {
        if (uri == null) return null;

        try {
            String fileName = getFileName(context, uri);
            File tempFile = new File(context.getCacheDir(),
                    "temp_" + System.currentTimeMillis() + "_" + fileName);

            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {

                if (inputStream == null) {
                    Log.e(TAG, "Cannot open input stream from URI");
                    return null;
                }

                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }

            return tempFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error extracting file from URI: " + e.getMessage(), e);
            return null;
        }
    }

    public static File createOutputFile(Context context, String fileName) {
        if (!fileName.toLowerCase().endsWith(".m4a") && !fileName.toLowerCase().endsWith(".mp3")) {
            fileName = fileName + ".m4a";
        }

        String cleanName = fileName.replaceAll("[^a-zA-Z0-9._\\s()\\[\\]-]", "_");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Create temp file in cache for FFmpeg processing
            File tempDir = new File(context.getCacheDir(), "SpatialFlow_output");
            if (!tempDir.exists()) tempDir.mkdirs();

            File tempFile = new File(tempDir, cleanName);
            Log.d(TAG, "Temp output file for processing: " + tempFile.getAbsolutePath());
            return tempFile;
        } else {
            // Android 9 and below - Direct file access to Downloads/SpatialFlow
            File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "SpatialFlow");
            if (!downloadsDir.exists()) downloadsDir.mkdirs();

            File outputFile = new File(downloadsDir, cleanName);
            Log.d(TAG, "Output file (legacy): " + outputFile.getAbsolutePath());
            return outputFile;
        }
    }

    public static void scanFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Copy to MediaStore Audio for Android 10+
            copyToMediaStore(context, file);
        } else {
            // Trigger media scanner for older versions
            try {
                context.sendBroadcast(
                        new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.fromFile(file)));
                Log.d(TAG, "Media scan requested: " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "scanFile failed: " + e.getMessage());
            }
        }
    }

    private static void copyToMediaStore(Context context, File sourceFile) {
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file doesn't exist: " + sourceFile.getAbsolutePath());
            return;
        }

        // Get clean metadata from filename
        String nameWithoutExtension = sourceFile.getName();
        if (nameWithoutExtension.toLowerCase().endsWith(".mp3")) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length() - 4);
        } else if (nameWithoutExtension.toLowerCase().endsWith(".m4a")) {
            nameWithoutExtension = nameWithoutExtension.substring(0, nameWithoutExtension.length() - 4);
        }

        // Split by " - " to get Title and Artist
        String title = nameWithoutExtension;
        String artist = "Unknown Artist";
        if (nameWithoutExtension.contains(" - ")) {
            String[] parts = nameWithoutExtension.split(" - ", 2);
            title = parts[0].trim();
            artist = parts[1].trim();
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, sourceFile.getName());
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        
        String mimeType = "audio/mp4";
        if (sourceFile.getName().toLowerCase().endsWith(".mp3")) {
            mimeType = "audio/mpeg";
        }
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);

        // Save under Music/SpatialFlow folder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SpatialFlow");
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri itemUri = resolver.insert(collection, values);

        if (itemUri == null) {
            Log.e(TAG, "Failed to create MediaStore entry");
            return;
        }

        try (InputStream in = new java.io.FileInputStream(sourceFile);
             OutputStream out = resolver.openOutputStream(itemUri)) {

            if (out == null) {
                Log.e(TAG, "Failed to open output stream");
                return;
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                resolver.update(itemUri, values, null, null);
            }

            Log.d(TAG, "File copied to MediaStore successfully: Music/SpatialFlow/" + sourceFile.getName());

        } catch (IOException e) {
            Log.e(TAG, "Error copying to MediaStore: " + e.getMessage(), e);
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String name = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                    name = cursor.getString(nameIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename: " + e.getMessage());
            }
        }

        if (name == null) {
            name = uri.getLastPathSegment();
        }

        return name != null ? name : "audio.m4a";
    }
}
