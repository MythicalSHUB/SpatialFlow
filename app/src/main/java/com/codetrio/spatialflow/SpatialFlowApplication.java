package com.codetrio.spatialflow;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class SpatialFlowApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
