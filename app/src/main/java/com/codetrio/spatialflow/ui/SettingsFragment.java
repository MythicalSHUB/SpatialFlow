package com.codetrio.spatialflow.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;

import com.codetrio.spatialflow.BuildConfig;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.update.UpdateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

public class SettingsFragment extends Fragment {

    private UpdateManager updateManager;
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_DARK_MODE = "dark_mode";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        updateManager = new UpdateManager(requireContext());

        // ---------------------------
        // DARK MODE SWITCH
        // ---------------------------
        MaterialSwitch switchTheme = view.findViewById(R.id.switchTheme);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        switchTheme.setChecked(isDarkMode);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();

            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES :
                            AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        // ---------------------------
        // VERSION TEXT
        // ---------------------------
        MaterialTextView tvVersion = view.findViewById(R.id.tvVersion);
        tvVersion.setText("Version " + BuildConfig.VERSION_NAME);

        // ---------------------------
        // CHECK UPDATE BUTTON
        // ---------------------------
        MaterialButton btnCheckUpdate = view.findViewById(R.id.btnCheckUpdate);
        btnCheckUpdate.setOnClickListener(v -> {
            View rootView = view.findViewById(R.id.settingsRoot);
            updateManager.checkForUpdate(rootView, BuildConfig.VERSION_NAME);
        });

        // ---------------------------
        // WHAT'S NEW CLICK HANDLER
        // Supports old id & new grouped id
        // ---------------------------
        View whatsNewView = view.findViewById(R.id.cardWhatsNew);
        if (whatsNewView == null) {
            whatsNewView = view.findViewById(R.id.rowWhatsNew); // new grouped layout id
        }

        if (whatsNewView != null) {
            whatsNewView.setOnClickListener(v -> showWhatsNewDialog());
        }

        // ---------------------------
        // SOCIAL BUTTONS
        MaterialButton btnGitHub = view.findViewById(R.id.btnGitHub);
        btnGitHub.setOnClickListener(v -> openUrl("https://github.com/MythicalSHUB"));

        MaterialButton btnInstagram = view.findViewById(R.id.btnInstagram);
        btnInstagram.setOnClickListener(v -> openUrl("https://instagram.com/mythicalshub"));

        MaterialButton btnYoutube = view.findViewById(R.id.btnYoutube);
        btnYoutube.setOnClickListener(v -> openUrl("https://youtube.com/@8dmusic_s"));

        return view;
    }

    // ---------------------------
    // OPEN URL
    // ---------------------------
    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    // ---------------------------
    // WHAT'S NEW DIALOG
    // FIXED + FULL HTML SUPPORT
    // ---------------------------
    private void showWhatsNewDialog() {
        String rawHtml = getString(R.string.whats_new_content_template, BuildConfig.VERSION_NAME);

        Spanned styled = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_LEGACY);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
                .setMessage(styled)
                .setPositiveButton(R.string.whats_new_positive_button, null)
                .show();
    }
}
