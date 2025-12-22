package com.codetrio.spatialflow;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.Rational;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity implements DefaultLifecycleObserver {

    private static final String TAG = "MainActivity";
    private static final int AUDIO_PERMISSION_REQUEST = 100;

    public static final String EXTRA_OPEN_PLAYER = "open_player";

    private BottomNavigationView navView;
    private NavController navController;
    private int previousDestination = R.id.navigation_player;
    private boolean isNavigating = false; // Prevent rapid navigation

    private AudioPlaybackService audioService;
    private boolean isServiceBound = false;
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
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        getLifecycle().addObserver(this);

        setupSystemBars();
        setContentView(R.layout.activity_main);

        startAudioService();
        checkAudioPermission();

        navView = findViewById(R.id.nav_view);
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        // Enable hardware acceleration for smooth animations
        navView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        applyWindowInsetsBehavior();
        setupBottomNavColors(navView);

        NavigationUI.setupWithNavController(navView, navController);

        // Open PlayerFragment when tapped from PiP
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            navController.navigate(R.id.navigation_player);
            navView.setSelectedItemId(R.id.navigation_player);
        }

        // Smooth bottom nav with debounced navigation
        navView.setOnItemSelectedListener(item -> {
            int destId = item.getItemId();
            NavDestination current = navController.getCurrentDestination();

            // Don't navigate if already on this destination or currently navigating
            if (current != null && current.getId() == destId) return true;
            if (isNavigating) return false;

            // Bounce icon animation
            bounceBottomNavIcon(item);

            // Get slide animation based on direction
            NavOptions navOptions = getNavOptions(previousDestination, destId);

            // Navigate with debounce protection
            isNavigating = true;
            navController.navigate(destId, null, navOptions);

            // Reset navigation lock after animation completes
            navView.postDelayed(() -> isNavigating = false, 300);

            previousDestination = destId;
            return true;
        });

        navView.setOnItemReselectedListener(item -> {
            bounceBottomNavIcon(item);
        });

        // Add destination change listener to optimize fragment transitions
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            // Force layout to prevent stuttering
            navView.requestLayout();
        });
    }

    private void bounceBottomNavIcon(MenuItem item) {
        View iconView = navView.findViewById(item.getItemId());
        if (iconView != null) {
            // Use hardware layer during animation for smoothness
            iconView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.4f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.4f, 1f);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(scaleX, scaleY);
            animatorSet.setDuration(300);
            animatorSet.setInterpolator(new OvershootInterpolator(1.5f));

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Reset to default layer type after animation
                    iconView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            });

            animatorSet.start();
        }
    }

    private NavOptions getNavOptions(int fromId, int toId) {
        int fromIndex = getDestinationIndex(fromId);
        int toIndex = getDestinationIndex(toId);

        if (toIndex > fromIndex) {
            // Forward navigation (Player → Effects → Settings)
            return new NavOptions.Builder()
                    .setEnterAnim(R.anim.fragment_cursel_in)
                    .setExitAnim(R.anim.fragment_cursel_out)
                    .setPopEnterAnim(R.anim.fragment_cursel_in_pop)
                    .setPopExitAnim(R.anim.fragment_cursel_out_pop)
                    .setLaunchSingleTop(true)
                    .build();
        } else {
            // Backward navigation (Settings → Effects → Player)
            return new NavOptions.Builder()
                    .setEnterAnim(R.anim.fragment_cursel_in_pop)
                    .setExitAnim(R.anim.fragment_cursel_out_pop)
                    .setPopEnterAnim(R.anim.fragment_cursel_in)
                    .setPopExitAnim(R.anim.fragment_cursel_out)
                    .setLaunchSingleTop(true)
                    .build();
        }
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
        if (manager == null) return;

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AudioPlaybackService.class.getName().equals(service.service.getClassName())) {
                return;
            }
        }
        startAudioService();
    }

    private void checkAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                        AUDIO_PERMISSION_REQUEST
                );
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        AUDIO_PERMISSION_REQUEST
                );
            }
        }
    }

    private void applyWindowInsetsBehavior() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), sys.top, v.getPaddingRight(), sys.bottom);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = sys.bottom;
            v.setLayoutParams(params);
            v.bringToFront();
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.nav_host_fragment_activity_main),
                (v, insets) -> insets
        );

        findViewById(R.id.container).requestApplyInsets();
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
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                        activeColor,
                        inactiveColor
                }
        );

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int activeTextColor = typedValue.data;

        ColorStateList textColorStateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                        activeTextColor,
                        inactiveColor
                }
        );

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

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());

        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isDarkMode);
            insetsController.setAppearanceLightNavigationBars(!isDarkMode);
        }
    }

    private int getDestinationIndex(int id) {
        if (id == R.id.navigation_player) return 0;
        if (id == R.id.navigation_effects) return 1;
        if (id == R.id.navigation_settings) return 2;
        return 0;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    // ===== PiP support =====

    public void enterPipModeIfPossible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(1, 1);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build();
            enterPictureInPictureMode(params);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        if (navView != null) {
            navView.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // PiP ONLY when PlayerFragment is visible and audio is playing
        if (isPlayerFragmentVisible() && audioService != null && audioService.isPlaying()) {
            enterPipModeIfPossible();
        }
    }

    private boolean isPlayerFragmentVisible() {
        NavDestination currentDestination = navController.getCurrentDestination();
        return currentDestination != null && currentDestination.getId() == R.id.navigation_player;
    }

    // ===== LIFECYCLE =====
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
}