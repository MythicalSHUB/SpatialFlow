package com.codetrio.spatialflow.update;

import android.util.Log;

public class VersionUtils {

    private static final String TAG = "VersionUtils";

    public static boolean isNewer(String remoteVersion, String localVersion) {
        try {
            // Remove 'v' prefix if present
            String remote = remoteVersion.startsWith("v") ? remoteVersion.substring(1) : remoteVersion;
            String local = localVersion.startsWith("v") ? localVersion.substring(1) : localVersion;

            String[] remoteParts = remote.split("\\.");
            String[] localParts = local.split("\\.");

            int maxLength = Math.max(remoteParts.length, localParts.length);

            for (int i = 0; i < maxLength; i++) {
                int remotePart = i < remoteParts.length ? Integer.parseInt(remoteParts[i]) : 0;
                int localPart = i < localParts.length ? Integer.parseInt(localParts[i]) : 0;

                if (remotePart > localPart) {
                    return true;
                } else if (remotePart < localPart) {
                    return false;
                }
            }
            return false; // Versions are equal

        } catch (Exception e) {
            Log.e(TAG, "Error comparing versions", e);
            return false;
        }
    }
}
