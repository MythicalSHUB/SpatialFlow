package com.codetrio.spatialflow;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import com.codetrio.spatialflow.util.CacheManager;

public class SpatialFlowApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply dynamic colors to all activities
        DynamicColors.applyToActivitiesIfAvailable(this);

        // Clean up old temp files on app start
        CacheManager.clearOldCache(this);
    }
}