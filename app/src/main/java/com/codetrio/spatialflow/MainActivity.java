package com.codetrio.spatialflow;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int AUDIO_PERMISSION_REQUEST = 100;

    private BottomNavigationView navView;
    private NavController navController;
    private int previousDestination = R.id.navigation_player; // default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enable dynamic colors
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        setupSystemBars();
        setContentView(R.layout.activity_main);

        // Check audio/media permissions for MediaStore song list
        checkAudioPermission();

        navView = findViewById(R.id.nav_view);
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        applyWindowInsetsBehavior();
        setupBottomNavColors(navView);

        NavigationUI.setupWithNavController(navView, navController);

        navView.setOnItemSelectedListener(item -> {
            int destId = item.getItemId();
            NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == destId) return true;

            NavOptions navOptions = getNavOptions(previousDestination, destId);

            navController.popBackStack(navController.getGraph().getStartDestinationId(), false);
            navController.navigate(destId, null, navOptions);

            previousDestination = destId;
            return true;
        });

        navView.setOnItemReselectedListener(item -> {
            // optional: scroll to top or refresh
        });

        if (savedInstanceState == null) {
            navController.navigate(R.id.navigation_player);
            navView.setSelectedItemId(R.id.navigation_player);
        }
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
            v.setPadding(v.getPaddingLeft(), sys.top, v.getPaddingRight(), v.getPaddingBottom());
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

    private NavOptions getNavOptions(int fromId, int toId) {
        int fromIndex = getDestinationIndex(fromId);
        int toIndex = getDestinationIndex(toId);

        if (toIndex > fromIndex) {
            return new NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_right)
                    .setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left)
                    .setPopExitAnim(R.anim.slide_out_right)
                    .build();
        } else {
            return new NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_left)
                    .setExitAnim(R.anim.slide_out_right)
                    .setPopEnterAnim(R.anim.slide_in_right)
                    .setPopExitAnim(R.anim.slide_out_left)
                    .build();
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
}
