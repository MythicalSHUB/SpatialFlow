package com.codetrio.spatialflow.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;

public class UpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
            long downloadId = prefs.getLong("download_id", -1);

            if (downloadId == -1) return;

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);

            Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    String localUri = cursor.getString(uriIndex);

                    if (localUri != null) {
                        File apkFile = new File(Uri.parse(localUri).getPath());
                        UpdateManager.installApk(context, apkFile);
                    }
                } else {
                    Log.e(TAG, "Download failed with status: " + status);
                }
            }
            cursor.close();

            // Clear download ID
            prefs.edit().remove("download_id").apply();
        }
    }
}
