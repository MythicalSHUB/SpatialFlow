package com.codetrio.spatialflow.update

import android.util.Log
import kotlin.math.max

object VersionUtils {
    private const val TAG = "VersionUtils"

    @JvmStatic
    fun isNewer(remoteVersion: String, localVersion: String): Boolean {
        try {
            // Remove 'v' prefix if present
            val remote = if (remoteVersion.startsWith("v")) remoteVersion.substring(1) else remoteVersion
            val local = if (localVersion.startsWith("v")) localVersion.substring(1) else localVersion

            val remoteParts = remote.split(".")
            val localParts = local.split(".")

            val maxLength = max(remoteParts.size, localParts.size)

            for (i in 0 until maxLength) {
                val remotePart = if (i < remoteParts.size) remoteParts[i].toIntOrNull() ?: 0 else 0
                val localPart = if (i < localParts.size) localParts[i].toIntOrNull() ?: 0 else 0

                if (remotePart > localPart) {
                    return true
                } else if (remotePart < localPart) {
                    return false
                }
            }
            return false // Versions are equal
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
            return false
        }
    }
}
