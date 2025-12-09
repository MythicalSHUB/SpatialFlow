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
        if (!fileName.toLowerCase().endsWith(".m4a")) {
            fileName = fileName + ".m4a";
        }

        String cleanName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

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
            // Copy to MediaStore Downloads for Android 10+
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

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, "audio/mp4");

        // FIXED: Use Downloads collection with SpatialFlow subfolder
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SpatialFlow");

        values.put(MediaStore.Downloads.IS_PENDING, 1);

        ContentResolver resolver = context.getContentResolver();

        // FIXED: Use Downloads collection instead of Audio
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }

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

            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(itemUri, values, null, null);

            Log.d(TAG, "File copied to MediaStore successfully: Downloads/SpatialFlow/" + sourceFile.getName());

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
