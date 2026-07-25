package com.codetrio.spatialflow.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.material.snackbar.Snackbar

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_OWNER = "MythicalSHUB"
        private const val GITHUB_REPO = "SpatialFlow"
    }

    private val client: GitHubReleaseClient = GitHubReleaseClient(GITHUB_OWNER, GITHUB_REPO)

    // -----------------------------------------------------
    // CHECK FOR UPDATE
    // -----------------------------------------------------
    fun checkForUpdate(rootView: View, currentVersion: String) {
        showSnackbarAnchored(rootView, "Checking for updates...", Snackbar.LENGTH_SHORT)

        Thread {
            val release: GitHubReleaseClient.ReleaseInfo? = client.latestRelease

            if (release == null) {
                runOnUi {
                    showSnackbarAnchored(rootView, "Failed to check for updates", Snackbar.LENGTH_LONG)
                }
                return@Thread
            }

            val isNewer = VersionUtils.isNewer(release.tagName, currentVersion)

            runOnUi {
                if (isNewer) {
                    promptUpdate(rootView, release)
                    showUpdateNotification(release.tagName, release.changelog ?: "", release.apkUrl ?: "")
                } else {
                    showSnackbarAnchored(rootView, "You're on the latest version! 🎉", Snackbar.LENGTH_LONG)
                }
            }
        }.start()
    }

    // -----------------------------------------------------
    // UPDATE PROMPT
    // -----------------------------------------------------
    private fun promptUpdate(rootView: View, release: GitHubReleaseClient.ReleaseInfo) {
        val activity = getFragmentActivity(rootView.context)
        if (activity != null) {
            val sheet = UpdateBottomSheet.newInstance(
                tagName = release.tagName,
                changelog = release.changelog ?: "",
                apkUrl = release.apkUrl ?: ""
            )
            sheet.setOnUpdateClickListener(object : UpdateBottomSheet.OnUpdateClickListener {
                override fun onUpdateClick(apkUrl: String): Long {
                    return startDownload(rootView, apkUrl)
                }

                override fun onLaterClick() {}
            })
            sheet.show(activity.supportFragmentManager, "UpdateBottomSheet")
        } else {
            Log.e(TAG, "Cannot show bottom sheet - context is not a FragmentActivity")
        }
    }

    // -----------------------------------------------------
    // START APK DOWNLOAD
    // -----------------------------------------------------
    fun startDownload(rootView: View, apkUrl: String): Long {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
            if (dm == null) {
                runOnUi {
                    showSnackbarAnchored(rootView, "Download Manager not available", Snackbar.LENGTH_LONG)
                }
                return -1L
            }

            val uri = Uri.parse(apkUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("SpatialFlow Update")
                setDescription("Downloading latest version...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val filename = "SpatialFlow-update.apk"
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
            }

            val downloadId = dm.enqueue(request)

            val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("download_id", downloadId)
                .putString("download_filename", "SpatialFlow-update.apk")
                .apply()

            runOnUi {
                val sb = Snackbar.make(rootView, "Downloading update...", Snackbar.LENGTH_LONG)
                sb.setAction("View") {
                    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                sb.show()
            }
            return downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            runOnUi {
                showSnackbarAnchored(rootView, "Download failed. Try again.", Snackbar.LENGTH_LONG)
            }
            return -1L
        }
    }

    private fun createUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "spatialflow_updates",
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new app update is available"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(tagName: String, changelog: String, apkUrl: String) {
        createUpdateNotificationChannel()
        
        val intent = Intent(context, com.codetrio.spatialflow.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_update_sheet", true)
            putExtra("update_tag_name", tagName)
            putExtra("update_changelog", changelog)
            putExtra("update_apk_url", apkUrl)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val color = androidx.core.content.ContextCompat.getColor(context, com.codetrio.spatialflow.R.color.md_theme_primary)

        val builder = NotificationCompat.Builder(context, "spatialflow_updates")
            .setSmallIcon(com.codetrio.spatialflow.R.drawable.ic_applogo)
            .setContentTitle("Update Available")
            .setContentText("Version $tagName is available to download.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(color)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .addAction(
                com.codetrio.spatialflow.R.drawable.ic_download,
                "Update Now",
                pendingIntent
            )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(999, builder.build())
    }

    // -----------------------------------------------------
    // UI HELPERS
    // -----------------------------------------------------
    private fun runOnUi(r: Runnable) {
        val a = getActivityIfPossible()
        if (a != null) {
            a.runOnUiThread(r)
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(r)
        }
    }

    private fun showSnackbarAnchored(rootView: View, msg: String, duration: Int) {
        runOnUi {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage(msg)
        }
    }

    private fun getActivityIfPossible(): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun getFragmentActivity(context: Context): FragmentActivity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
