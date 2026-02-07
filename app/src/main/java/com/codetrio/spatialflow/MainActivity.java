package com.codetrio.spatialflow;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.codetrio.spatialflow.service.AudioPlaybackService;
import com.codetrio.spatialflow.update.GitHubReleaseClient;
import com.codetrio.spatialflow.update.UpdateManager;
import com.codetrio.spatialflow.update.VersionUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity implements DefaultLifecycleObserver {

    private static final String TAG = "MainActivity";
    private static final int AUDIO_PERMISSION_REQUEST = 100;

    public static final String EXTRA_OPEN_PLAYER = "open_player";

    private BottomNavigationView navView;
    private NavController navController;
    private int previousDestination = R.id.navigation_player;
    private boolean isNavigating = false;

    private AudioPlaybackService audioService;
    private boolean isServiceBound = false;
    private UpdateManager updateManager;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            audioService = ((AudioPlaybackService.LocalBinder) service).getService();
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            isServiceBound = false;
            audioService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install Splash Screen
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        // Keep splash visible for 2 seconds
        final long splashStartTime = System.currentTimeMillis();
        final long SPLASH_DURATION = 2000;
        splashScreen.setKeepOnScreenCondition(() -> {
            long elapsed = System.currentTimeMillis() - splashStartTime;
            return elapsed < SPLASH_DURATION;
        });

        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        getLifecycle().addObserver(this);

        setupSystemBars();
        setContentView(R.layout.activity_main);

        // Play theme transition animation (if pending from theme switch)
        com.codetrio.spatialflow.util.ThemeAnimationHelper.playPendingAnimation(this);

        startAudioService();
        checkAudioPermission();

        navView = findViewById(R.id.nav_view);
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        navView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        applyWindowInsetsBehavior();
        setupBottomNavColors(navView);

        NavigationUI.setupWithNavController(navView, navController);

        // Open PlayerFragment when tapped from notification
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            NavOptions navOptions = new NavOptions.Builder()
                    .setEnterAnim(R.anim.fragment_zoom_in)
                    .setExitAnim(R.anim.fragment_zoom_out)
                    .setPopEnterAnim(R.anim.fragment_zoom_in)
                    .setPopExitAnim(R.anim.fragment_zoom_out)
                    .setLaunchSingleTop(true)
                    .build();
            navController.navigate(R.id.navigation_player, null, navOptions);
            navView.setSelectedItemId(R.id.navigation_player);
        }

        navView.setOnItemSelectedListener(item -> {
            int destId = item.getItemId();
            NavDestination current = navController.getCurrentDestination();

            if (current != null && current.getId() == destId)
                return true;
            if (isNavigating)
                return false;

            NavOptions navOptions = getNavOptions(previousDestination, destId);

            isNavigating = true;
            navController.navigate(destId, null, navOptions);

            navView.postDelayed(() -> isNavigating = false, 50);

            previousDestination = destId;
            return true;
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            navView.requestLayout();
        });

        // ===== 🆕 CHECK FOR UPDATE ON LAUNCH =====
        updateManager = new UpdateManager(this);
        checkForUpdateOnLaunch();

        // Trigger staggered entrance animation for bottom nav after splash
        navView.postDelayed(this::animateBottomNavEntrance, 200);
    }

    // ===== 🆕 UPDATE CHECK LOGIC =====
    private void checkForUpdateOnLaunch() {
        if (!shouldCheckForUpdate()) {
            Log.d(TAG, "Update check skipped (checked recently)");
            return;
        }

        new Thread(() -> {
            try {
                GitHubReleaseClient client = new GitHubReleaseClient("MythicalSHUB", "SpatialFlow");
                GitHubReleaseClient.ReleaseInfo release = client.getLatestRelease();

                if (release == null) {
                    Log.d(TAG, "No release info available");
                    return;
                }

                String currentVersion = BuildConfig.VERSION_NAME;
                boolean isNewer = VersionUtils.isNewer(release.tagName, currentVersion);

                if (isNewer) {
                    runOnUiThread(() -> showUpdateDialog(release));
                } else {
                    Log.d(TAG, "App is up to date (current: " + currentVersion + ", latest: " + release.tagName + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
            }
        }).start();
    }

    private void showUpdateDialog(GitHubReleaseClient.ReleaseInfo release) {
        String message = "Version " + release.tagName + " is now available!\n\n";

        if (release.changelog != null && !release.changelog.isEmpty()) {
            String changelog = release.changelog.length() > 350
                    ? release.changelog.substring(0, 350) + "..."
                    : release.changelog;
            message += "📋 What's New:\n" + changelog;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("🎉 Update Available")
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("Update Now", (dialog, which) -> {
                    View rootView = findViewById(android.R.id.content);
                    updateManager.checkForUpdate(rootView, BuildConfig.VERSION_NAME);
                })
                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                .setNeutralButton("Don't Show Again", (dialog, which) -> {
                    disableUpdateCheck();
                    dialog.dismiss();
                })
                .show();
    }

    // Check once per day (24 hours)
    private boolean shouldCheckForUpdate() {
        SharedPreferences prefs = getSharedPreferences("update_prefs", Context.MODE_PRIVATE);

        boolean updateCheckDisabled = prefs.getBoolean("update_check_disabled", false);
        if (updateCheckDisabled) {
            return false;
        }

        long lastCheck = prefs.getLong("last_update_check", 0);
        long currentTime = System.currentTimeMillis();
        long oneDayMillis = 24 * 60 * 60 * 1000;

        if (currentTime - lastCheck > oneDayMillis) {
            prefs.edit().putLong("last_update_check", currentTime).apply();
            return true;
        }
        return false;
    }

    private void disableUpdateCheck() {
        SharedPreferences prefs = getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("update_check_disabled", true).apply();
        Log.d(TAG, "Auto update check disabled by user");
    }

    // ===== REST OF YOUR EXISTING CODE =====

    private NavOptions getNavOptions(int fromId, int toId) {
        // Google-style simple zoom animation - same for all directions
        return new NavOptions.Builder()
                .setEnterAnim(R.anim.fragment_zoom_in)
                .setExitAnim(R.anim.fragment_zoom_out)
                .setPopEnterAnim(R.anim.fragment_zoom_in)
                .setPopExitAnim(R.anim.fragment_zoom_out)
                .setLaunchSingleTop(true)
                .build();
    }

    private void startAudioService() {
        Intent serviceIntent = new Intent(this, AudioPlaybackService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Audio service started and bound");
    }

    private void ensureServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null)
            return;

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AudioPlaybackService.class.getName().equals(service.service.getClassName())) {
                return;
            }
        }
        startAudioService();
    }

    private void checkAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[] { Manifest.permission.READ_MEDIA_AUDIO },
                        AUDIO_PERMISSION_REQUEST);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                        AUDIO_PERMISSION_REQUEST);
            }
        }
    }

    private void applyWindowInsetsBehavior() {
        View container = findViewById(R.id.container);
        View navHostFragment = findViewById(R.id.nav_host_fragment_activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(container, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, 0); // Remove top padding
            return windowInsets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.setOnApplyWindowInsetsListener(navHostFragment, (v, windowInsets) -> {
            return windowInsets;
        });

        container.requestApplyInsets();
    }

    private void setupBottomNavColors(BottomNavigationView navView) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        android.content.res.Resources.Theme theme = getTheme();

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, typedValue, true);
        int activeColor = typedValue.data;

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
        int inactiveColor = typedValue.data;

        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true);
        int backgroundColor = typedValue.data;

        ColorStateList iconColorStateList = new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] { -android.R.attr.state_checked }
                },
                new int[] {
                        activeColor,
                        inactiveColor
                });

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int activeTextColor = typedValue.data;

        ColorStateList textColorStateList = new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] { -android.R.attr.state_checked }
                },
                new int[] {
                        activeTextColor,
                        inactiveColor
                });

        navView.setItemIconTintList(iconColorStateList);
        navView.setItemTextColor(textColorStateList);
        navView.setBackgroundColor(backgroundColor);
        navView.setItemIconSize((int) (28 * getResources().getDisplayMetrics().density));
    }

    private void setupSystemBars() {
        Window window = getWindow();

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        WindowCompat.setDecorFitsSystemWindows(window, false);

        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isDarkMode);
            insetsController.setAppearanceLightNavigationBars(!isDarkMode);
        }
    }

    private int getDestinationIndex(int id) {
        if (id == R.id.navigation_player)
            return 0;
        if (id == R.id.navigation_effects)
            return 1;
        if (id == R.id.navigation_settings)
            return 2;
        return 0;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    // PIP mode methods removed as per user request

    // isPlayerFragmentVisible kept for potential future use
    private boolean isPlayerFragmentVisible() {
        NavDestination currentDestination = navController.getCurrentDestination();
        return currentDestination != null && currentDestination.getId() == R.id.navigation_player;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // PIP mode removed
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStart(owner);
        ensureServiceRunning();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (isServiceBound) {
                unbindService(serviceConnection);
                isServiceBound = false;
            }
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Service not bound, skipping unbind");
        }
    }

    public AudioPlaybackService getAudioService() {
        return audioService;
    }

    public boolean isAudioServiceBound() {
        return isServiceBound;
    }

    public void setBottomNavVisibility(boolean visible) {
        if (navView == null)
            return;
        navView.clearAnimation();
        if (visible) {
            navView.setVisibility(View.VISIBLE);
            isNavHidden = false; // Reset scroll state when explicitly shown
        }
        float targetY = visible ? 0 : navView.getHeight() + 100;
        navView.animate()
                .translationY(targetY)
                .setDuration(300)
                .setInterpolator(visible ? new android.view.animation.DecelerateInterpolator()
                        : new android.view.animation.AccelerateInterpolator())
                .start();
    }

    public void setBottomNavTranslation(float translationY) {
        if (navView == null)
            return;
        navView.setTranslationY(translationY);
        if (translationY >= navView.getHeight()) {
            navView.setVisibility(View.GONE);
        } else {
            navView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide all bottom nav items immediately (call when player expands).
     */
    public void hideBottomNavItems() {
        if (navView == null)
            return;
        for (int i = 0; i < navView.getMenu().size(); i++) {
            View itemView = navView.findViewById(navView.getMenu().getItem(i).getItemId());
            if (itemView != null) {
                itemView.setTranslationY(100f); // Push down below nav bar
                itemView.setAlpha(0f);
            }
        }
    }

    /**
     * Staggered slide-up entrance animation for bottom navigation menu items.
     * Each item slides up one by one with a delay.
     */
    public void animateBottomNavEntrance() {
        if (navView == null)
            return;

        // Animate each item sliding up with stagger - faster animations
        for (int i = 0; i < navView.getMenu().size(); i++) {
            View itemView = navView.findViewById(navView.getMenu().getItem(i).getItemId());
            if (itemView == null)
                continue;

            long delay = 180 + (i * 50L); // 180ms base delay + 50ms stagger (faster)

            itemView.animate()
                    .translationY(0f) // Slide up to normal position
                    .alpha(1f)
                    .setDuration(200) // Faster item animation
                    .setStartDelay(delay)
                    .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    .start();
        }
    }

    // State flag to prevent duplicate animations
    private boolean isNavHidden = false;

    /**
     * Hide bottom nav with staggered animation (for scroll-aware behavior).
     * Items animate out first, then nav slides down.
     */
    public void hideBottomNavWithAnimation() {
        if (navView == null || isNavHidden)
            return;
        isNavHidden = true;

        // First hide items with staggered animation (faster)
        for (int i = 0; i < navView.getMenu().size(); i++) {
            View itemView = navView.findViewById(navView.getMenu().getItem(i).getItemId());
            if (itemView == null)
                continue;

            long delay = i * 30L; // 30ms stagger (faster)

            itemView.animate()
                    .translationY(50f)
                    .alpha(0f)
                    .setDuration(120) // Faster hide
                    .setStartDelay(delay)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .start();
        }

        // After items hide, slide nav down (sync with mini player - 280ms)
        navView.postDelayed(() -> {
            navView.animate()
                    .translationY(navView.getHeight() + 50)
                    .setDuration(280) // Match mini player duration
                    .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    .start();
        }, 100); // Faster delay since item animations are faster
    }

    /**
     * Show bottom nav with staggered animation (for scroll-aware behavior).
     * Nav slides up first (empty), then items animate in one by one.
     */
    public void showBottomNavWithAnimation() {
        if (navView == null || !isNavHidden)
            return;
        isNavHidden = false;

        // First ensure items are hidden
        hideBottomNavItems();

        // Slide nav up (sync with mini player - 280ms)
        navView.setVisibility(View.VISIBLE);
        navView.animate()
                .translationY(0f)
                .setDuration(280) // Match mini player duration
                .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                .start();

        // After nav slides up, animate items
        animateBottomNavEntrance();
    }

    /**
     * Check if bottom nav is currently hidden (for external queries).
     */
    public boolean isBottomNavHidden() {
        return isNavHidden;
    }

    /**
     * Show a Snackbar anchored above the Bottom Navigation View.
     * This ensures it doesn't get covered by the nav bar or floating UI.
     *
     * @param message  The message to display
     * @param duration Snackbar duration (LENGTH_SHORT, LENGTH_LONG)
     */
    public void showSnackbar(String message, int duration) {
        if (navView == null)
            return;
        com.google.android.material.snackbar.Snackbar.make(navView, message, duration)
                .setAnchorView(navView)
                .show();
    }
}
