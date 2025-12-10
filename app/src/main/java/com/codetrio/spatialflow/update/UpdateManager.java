package com.codetrio.spatialflow.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_OWNER = "MythicalSHUB";
    private static final String GITHUB_REPO = "SpatialFlow";

    private final Context context;
    private final GitHubReleaseClient client;

    public UpdateManager(Context context) {
        this.context = context;
        this.client = new GitHubReleaseClient(GITHUB_OWNER, GITHUB_REPO);
    }

    public void checkForUpdate(View rootView, String currentVersion) {
        Snackbar.make(rootView, "Checking for updates...", Snackbar.LENGTH_SHORT).show();

        new Thread(() -> {
            GitHubReleaseClient.ReleaseInfo release = client.getLatestRelease();

            if (release == null) {
                ((Activity) context).runOnUiThread(() ->
                        Snackbar.make(rootView, "Failed to check for updates", Snackbar.LENGTH_LONG).show()
                );
                return;
            }

            boolean isNewer = VersionUtils.isNewer(release.tagName, currentVersion);

            ((Activity) context).runOnUiThread(() -> {
                if (isNewer) {
                    promptUpdate(rootView, release);
                } else {
                    Snackbar.make(rootView, "You're on the latest version! 🎉", Snackbar.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void promptUpdate(View rootView, GitHubReleaseClient.ReleaseInfo release) {
        String message = "New version " + release.tagName + " is available!\n\n";
        if (!release.changelog.isEmpty()) {
            String changelog = release.changelog.length() > 200
                    ? release.changelog.substring(0, 200) + "..."
                    : release.changelog;
            message += changelog;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle("Update Available")
                .setMessage(message)
                .setPositiveButton("Update", (dialog, which) -> {
                    startDownload(rootView, release.apkUrl);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void startDownload(View rootView, String apkUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("SpatialFlow");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "SpatialFlow-update.apk");

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = downloadManager.enqueue(request);

            // Save download ID
            SharedPreferences prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
            prefs.edit().putLong("download_id", downloadId).apply();

            Snackbar.make(rootView, "Downloading update...", Snackbar.LENGTH_LONG)
                    .setAction("View", v -> {
                        Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                        context.startActivity(intent);
                    })
                    .show();

        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            Snackbar.make(rootView, "Download failed. Please try again.", Snackbar.LENGTH_LONG).show();
        }
    }

    public static void installApk(Context context, File apkFile) {
        // Check install permission for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
                return;
            }
        }

        try {
            Uri apkUri;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ requires FileProvider
                apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile
                );
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(installIntent);

        } catch (Exception e) {
            Log.e(TAG, "Failed to install APK", e);
            Toast.makeText(context, "Failed to open installer", Toast.LENGTH_SHORT).show();
        }
    }
}
