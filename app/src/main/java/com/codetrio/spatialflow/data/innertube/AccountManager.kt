package com.codetrio.spatialflow.data.innertube

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Facilitates application authentication state holding user-facing identity tokens 
 * and enabling account sync state tracking.
 */
object AccountManager {

    private const val PREFS_NAME = "AppSettings"
    private const val KEY_COOKIE_DATA = "yt_cookies"
    private const val KEY_SYNC_ENABLED = "sync_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getAuthCookie(context).isNullOrEmpty()
    }

    fun getAccountName(context: Context): String {
        return if (isLoggedIn(context)) "Connected Account" else "Guest User"
    }

    fun getAuthCookie(context: Context): String? {
        return getPrefs(context).getString(KEY_COOKIE_DATA, null)
    }

    fun isSyncEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SYNC_ENABLED, true)
    }

    fun saveLoginState(context: Context, cookie: String) {
        // Update persistent store
        getPrefs(context).edit {
            putString(KEY_COOKIE_DATA, cookie)
        }
        // Update immediate memory runtime in InnerTube facade
        InnerTubeClient.cookie = cookie
        InnerTubeClient.visitorData = null
    }

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_SYNC_ENABLED, enabled) }
    }

    fun logout(context: Context) {
        // Remove cookie from persistent storage
        getPrefs(context).edit { remove(KEY_COOKIE_DATA) }
        // Wipe InnerTube runtime
        InnerTubeClient.cookie = null
        InnerTubeClient.visitorData = null
    }
}
