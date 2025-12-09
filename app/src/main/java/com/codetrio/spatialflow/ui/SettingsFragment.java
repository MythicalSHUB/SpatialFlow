package com.codetrio.spatialflow.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.update.UpdateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

public class SettingsFragment extends Fragment {

    private UpdateManager updateManager;
    private static final String VERSION_NAME = "1.0";
    private static final String PREFS_NAME = "AppSettings"; // ✅ Match MainActivity
    private static final String KEY_DARK_MODE = "dark_mode";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        updateManager = new UpdateManager(requireContext());

        // Dark mode switch
        MaterialSwitch switchTheme = view.findViewById(R.id.switchTheme);
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        switchTheme.setChecked(isDarkMode);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save preference
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();

            // Apply theme immediately
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // Version display
        MaterialTextView tvVersion = view.findViewById(R.id.tvVersion);
        tvVersion.setText("Version " + VERSION_NAME);

        // Update check button
        MaterialButton btnCheckUpdate = view.findViewById(R.id.btnCheckUpdate);
        btnCheckUpdate.setOnClickListener(v -> {
            View rootView = view.findViewById(R.id.settingsRoot);
            updateManager.checkForUpdate(rootView, VERSION_NAME);
        });

        // Social buttons
        MaterialButton btnGitHub = view.findViewById(R.id.btnGitHub);
        btnGitHub.setOnClickListener(v -> openUrl("https://github.com/MythicalSHUB"));

        MaterialButton btnInstagram = view.findViewById(R.id.btnInstagram);
        btnInstagram.setOnClickListener(v -> openUrl("https://instagram.com/mythicalshub"));

        MaterialButton btnYoutube = view.findViewById(R.id.btnYoutube);
        btnYoutube.setOnClickListener(v -> openUrl("https://youtube.com/@8dmusic_s"));

        return view;
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}
