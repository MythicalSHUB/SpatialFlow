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

import com.codetrio.spatialflow.R;
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
        this.context = context.getApplicationContext();
        this.client = new GitHubReleaseClient(GITHUB_OWNER, GITHUB_REPO);
    }

    // -----------------------------------------------------
    // CHECK FOR UPDATE
    // -----------------------------------------------------
    public void checkForUpdate(View rootView, String currentVersion) {
        showSnackbarAnchored(rootView, "Checking for updates...", Snackbar.LENGTH_SHORT);

        new Thread(() -> {
            GitHubReleaseClient.ReleaseInfo temp = client.getLatestRelease();
            final GitHubReleaseClient.ReleaseInfo release = temp; // ✅ FIXED (final variable)

            if (release == null) {
                runOnUi(() ->
                        showSnackbarAnchored(rootView, "Failed to check for updates", Snackbar.LENGTH_LONG)
                );
                return;
            }

            boolean isNewer = VersionUtils.isNewer(release.tagName, currentVersion);

            runOnUi(() -> {
                if (isNewer) {
                    promptUpdate(rootView, release);
                } else {
                    showSnackbarAnchored(rootView, "You're on the latest version! 🎉", Snackbar.LENGTH_LONG);
                }
            });
        }).start();
    }

    // -----------------------------------------------------
    // UPDATE PROMPT
    // -----------------------------------------------------
    private void promptUpdate(View rootView, GitHubReleaseClient.ReleaseInfo release) {
        String message = "New version " + release.tagName + " is available!\n\n";

        if (release.changelog != null && !release.changelog.isEmpty()) {
            String changelog = release.changelog.length() > 400
                    ? release.changelog.substring(0, 400) + "..."
                    : release.changelog;
            message += changelog;
        }

        Activity host = getActivityIfPossible();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
                host != null ? host : rootView.getContext()
        )
                .setTitle("Update Available")
                .setMessage(message)
                .setPositiveButton("Update", (dialog, which) -> {
                    startDownload(rootView, release.apkUrl);
                })
                .setNegativeButton("Later", null);

        runOnUi(builder::show);
    }

    // -----------------------------------------------------
    // START APK DOWNLOAD
    // -----------------------------------------------------
    private void startDownload(View rootView, String apkUrl) {
        try {

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                runOnUi(() -> showSnackbarAnchored(rootView, "Download Manager not available", Snackbar.LENGTH_LONG));
                return;
            }

            Uri uri = Uri.parse(apkUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            request.setTitle("SpatialFlow Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            String filename = "SpatialFlow-update.apk";
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename);

            long downloadId = dm.enqueue(request);

            SharedPreferences prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong("download_id", downloadId)
                    .putString("download_filename", filename)
                    .apply();

            runOnUi(() -> {
                Snackbar sb = makeAnchoredSnackbar(rootView, "Downloading update...", Snackbar.LENGTH_LONG);
                sb.setAction("View", v -> {
                    Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                });
                sb.show();
            });

        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            runOnUi(() ->
                    showSnackbarAnchored(rootView, "Download failed. Try again.", Snackbar.LENGTH_LONG)
            );
        }
    }

    // -----------------------------------------------------
    // INSTALL APK
    // -----------------------------------------------------
    public static void installApk(Context ctx, File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(ctx, "APK not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Context context = ctx.getApplicationContext();

        // Android O+ unknown sources permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:" + context.getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Toast.makeText(context, "Enable permission and retry.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        try {
            Uri apkUri;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile
                );
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Failed to install APK", e);
            Toast.makeText(context, "Failed to open installer", Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------------
    // UI HELPERS
    // -----------------------------------------------------
    private void runOnUi(Runnable r) {
        Activity a = getActivityIfPossible();
        if (a != null) {
            a.runOnUiThread(r);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
        }
    }

    private Snackbar makeAnchoredSnackbar(View rootView, String msg, int duration) {
        Snackbar sb = Snackbar.make(rootView, msg, duration);

        Activity a = getActivityIfPossible();
        if (a != null) {
            View nav = a.findViewById(R.id.nav_view);
            if (nav != null) sb.setAnchorView(nav);
        }

        return sb;
    }

    private void showSnackbarAnchored(View rootView, String msg, int duration) {
        runOnUi(() -> makeAnchoredSnackbar(rootView, msg, duration).show());
    }

    private Activity getActivityIfPossible() {
        if (context instanceof Activity) return (Activity) context;
        return null;
    }
}
