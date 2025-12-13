package com.codetrio.spatialflow;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private BottomNavigationView navView;
    private NavController navController;
    private int previousDestination = R.id.navigation_player; // default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ⭐ ENABLE DYNAMIC COLORS FIRST - This is the key!
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        setupSystemBars();
        setContentView(R.layout.activity_main);

        navView = findViewById(R.id.nav_view);
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        // apply insets so navView doesn't overlap app content and top area is safe
        applyWindowInsetsBehavior();

        // color the bottom nav icons/text according to theme - NOW USING DYNAMIC COLORS
        setupBottomNavColors(navView);

        // wire up Navigation component
        NavigationUI.setupWithNavController(navView, navController);

        // override selection to provide custom backstack behavior + animated transitions
        navView.setOnItemSelectedListener(item -> {
            int destId = item.getItemId();
            NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == destId) return true;

            NavOptions navOptions = getNavOptions(previousDestination, destId);

            // Pop to start destination, then navigate so each tab behaves like top-level
            navController.popBackStack(navController.getGraph().getStartDestinationId(), false);
            navController.navigate(destId, null, navOptions);

            previousDestination = destId;
            return true;
        });

        navView.setOnItemReselectedListener(item -> {
            // optional: scroll to top or refresh
        });

        // ensure initial selected item
        if (savedInstanceState == null) {
            navController.navigate(R.id.navigation_player);
            navView.setSelectedItemId(R.id.navigation_player);
        }

        // (Optional) start background service if your app requires it (uncomment if needed)
        // startYourService();
    }

    private void applyWindowInsetsBehavior() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // add top padding if you want content below status bar (optional)
            v.setPadding(v.getPaddingLeft(), sys.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        // apply bottom navigation margin equal to nav bar inset so it sits above system nav
        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = sys.bottom;
            v.setLayoutParams(params);

            // ensure bottom nav is above content visually
            v.bringToFront();
            return insets;
        });

        // ALSO pad inner NavHostFragment's RecyclerView if it exists (not always necessary, but safe)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment_activity_main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // find inner recycler view if needed later (fragments may host it)
            return insets;
        });

        // trigger insets dispatch
        findViewById(R.id.container).requestApplyInsets();
    }

    private void setupBottomNavColors(BottomNavigationView navView) {
        // ⭐ USE THEME ATTRIBUTES INSTEAD OF HARDCODED COLORS
        // This allows dynamic colors to work properly

        // Get colors from the current theme (which has dynamic colors applied)
        android.util.TypedValue typedValue = new android.util.TypedValue();
        android.content.res.Resources.Theme theme = getTheme();

        // Get active color (selected state) - use onSecondaryContainer for better contrast
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, typedValue, true);
        int activeColor = typedValue.data;

        // Get inactive color (unselected state)
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
        int inactiveColor = typedValue.data;

        // Get background color
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true);
        int backgroundColor = typedValue.data;

        // ---- Create ColorStateList for icons ----
        ColorStateList iconColorStateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},     // selected
                        new int[]{-android.R.attr.state_checked}      // unselected
                },
                new int[]{
                        activeColor,
                        inactiveColor
                }
        );

        // ---- Create ColorStateList for text (uses onSurface for better visibility) ----
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int activeTextColor = typedValue.data;

        ColorStateList textColorStateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},     // selected
                        new int[]{-android.R.attr.state_checked}      // unselected
                },
                new int[]{
                        activeTextColor,
                        inactiveColor
                }
        );

        navView.setItemIconTintList(iconColorStateList);
        navView.setItemTextColor(textColorStateList);

        // ---- BACKGROUND COLOR (uses Material3 dynamic color) ----
        navView.setBackgroundColor(backgroundColor);

        // ---- Material3 icon size ----
        navView.setItemIconSize((int) (28 * getResources().getDisplayMetrics().density));
    }

    private void setupSystemBars() {
        Window window = getWindow();

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());

        // make status/nav bar transparent so app draws behind them (we handle padding)
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