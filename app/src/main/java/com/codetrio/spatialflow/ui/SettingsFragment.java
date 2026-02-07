package com.codetrio.spatialflow.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.text.HtmlCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.codetrio.spatialflow.BuildConfig;
import com.codetrio.spatialflow.MainActivity;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.update.UpdateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.listitem.ListItemLayout;

import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;

import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

public class SettingsFragment extends Fragment {

    private UpdateManager updateManager;
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_VIBRATION_STRENGTH = "vibration_strength";
    private static final String KEY_CROSSFADE_ENABLED = "crossfade_enabled";
    private static final String KEY_CROSSFADE_DURATION = "crossfade_duration";
    private static final String KEY_AUDIO_FOCUS = "audio_focus";
    private static final String KEY_MUSIC_SOURCE_URI = "music_source_uri";

    // UI references for dynamic updates
    private MaterialTextView tvCacheSize;
    private MaterialTextView tvMusicSourcePath;
    private MaterialTextView tvSleepTimerStatus;
    private MaterialTextView tvCrossfadeValue;

    // Sleep timer
    private static CountDownTimer sleepTimer;
    private static long sleepTimerEndTime = 0;

    // Sleep Timer UI
    private LinearLayout sleepTimerExpandableContent;
    private ImageView ivSleepTimerExpand;
    private Slider sliderSleepTimer;
    private MaterialTextView tvSleepTimerDuration;
    private MaterialButton btnStartSleepTimer;
    private MaterialButton btnCustomTime;
    private boolean isSleepTimerExpanded = false;

    // Directory picker launcher
    private ActivityResultLauncher<Uri> directoryPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register directory picker
        directoryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        // Persist permission
                        requireContext().getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        // Add to library paths
                        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME,
                                Context.MODE_PRIVATE);
                        addLibraryPath(uri.toString(), prefs);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        updateManager = new UpdateManager(requireContext());
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // ---------------------------
        // DARK MODE SWITCH
        // ---------------------------
        MaterialSwitch switchTheme = view.findViewById(R.id.switchTheme);

        // Check current actual night mode status from configuration
        int currentNightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isCurrentlyDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // Set switch to reflect current actual state (not saved preference)
        switchTheme.setChecked(isCurrentlyDark);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();

            // Prepare animation (captures screenshot before theme change)
            com.codetrio.spatialflow.util.ThemeAnimationHelper.prepareThemeChange(
                    getActivity(), buttonView, isChecked);

            // Apply theme (will recreate activity)
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        // ---------------------------
        // HAPTICS & VIBRATION SECTION
        // ---------------------------
        setupHapticsControls(view, prefs);

        // ---------------------------
        // PLAYBACK SECTION
        // ---------------------------
        setupPlaybackSettings(view, prefs);

        // ---------------------------
        // LIBRARY SECTION
        // ---------------------------
        setupLibrarySettings(view, prefs);

        // ---------------------------
        // STORAGE SECTION
        // ---------------------------
        setupStorageSettings(view);

        // ---------------------------
        // M3 EXPRESSIVE LIST APPEARANCE
        // ---------------------------
        setupExpressiveListAppearance(view);

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
        // ---------------------------
        View whatsNewView = view.findViewById(R.id.rowWhatsNew);
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

        // ---------------------------
        // SCROLL-AWARE BOTTOM NAV
        // ---------------------------
        NestedScrollView scrollView = view.findViewById(R.id.settingsScroll);
        if (scrollView != null) {
            scrollView.setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        if (getActivity() instanceof MainActivity) {
                            MainActivity activity = (MainActivity) getActivity();
                            int dy = scrollY - oldScrollY;
                            if (dy > 10) {
                                activity.hideBottomNavWithAnimation();
                            } else if (dy < -10) {
                                activity.showBottomNavWithAnimation();
                            }
                        }
                    });
        }

        return view;
    }

    // ---------------------------
    // HAPTICS CONTROLS SETUP
    // ---------------------------
    private void setupHapticsControls(View view, SharedPreferences prefs) {
        MaterialTextView tvNotSupported = view.findViewById(R.id.tvHapticsNotSupported);
        Slider sliderVibrationStrength = view.findViewById(R.id.sliderVibrationStrength);

        // Check device haptics support
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        boolean hasHaptics = vibrator != null && vibrator.hasVibrator();

        // Show "Not Supported" if device lacks vibrator
        if (!hasHaptics && tvNotSupported != null) {
            tvNotSupported.setVisibility(View.VISIBLE);
            if (sliderVibrationStrength != null)
                sliderVibrationStrength.setEnabled(false);
        }

        // Load and save vibration strength
        if (sliderVibrationStrength != null) {
            sliderVibrationStrength.setValue(prefs.getFloat(KEY_VIBRATION_STRENGTH, 50f));
            sliderVibrationStrength.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser)
                    prefs.edit().putFloat(KEY_VIBRATION_STRENGTH, value).apply();
            });
        }
    }

    // ---------------------------
    // PLAYBACK SETTINGS SETUP
    // ---------------------------
    private void setupPlaybackSettings(View view, SharedPreferences prefs) {
        // Crossfade
        MaterialSwitch switchCrossfade = view.findViewById(R.id.switchCrossfade);
        Slider sliderCrossfade = view.findViewById(R.id.sliderCrossfade);
        tvCrossfadeValue = view.findViewById(R.id.tvCrossfadeValue);

        // Setup crossfade toggle
        if (switchCrossfade != null && sliderCrossfade != null) {
            boolean crossfadeEnabled = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false);
            float savedCrossfade = prefs.getFloat(KEY_CROSSFADE_DURATION, 3f);

            switchCrossfade.setChecked(crossfadeEnabled);
            sliderCrossfade.setEnabled(crossfadeEnabled);
            sliderCrossfade.setValue(savedCrossfade);
            updateCrossfadeText(crossfadeEnabled ? savedCrossfade : 0f);

            switchCrossfade.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, isChecked).apply();
                sliderCrossfade.setEnabled(isChecked);
                if (isChecked) {
                    updateCrossfadeText(sliderCrossfade.getValue());
                } else {
                    updateCrossfadeText(0f);
                }
            });

            sliderCrossfade.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    prefs.edit().putFloat(KEY_CROSSFADE_DURATION, value).apply();
                }
                if (switchCrossfade.isChecked()) {
                    updateCrossfadeText(value);
                }
            });
        }

        // Audio Focus
        MaterialSwitch switchAudioFocus = view.findViewById(R.id.switchAudioFocus);
        if (switchAudioFocus != null) {
            switchAudioFocus.setChecked(prefs.getBoolean(KEY_AUDIO_FOCUS, true));
            switchAudioFocus.setOnCheckedChangeListener(
                    (btn, isChecked) -> prefs.edit().putBoolean(KEY_AUDIO_FOCUS, isChecked).apply());
        }

        // Sleep Timer - Expandable Version
        tvSleepTimerStatus = view.findViewById(R.id.tvSleepTimerStatus);
        View rowSleepTimer = view.findViewById(R.id.rowSleepTimer);
        sleepTimerExpandableContent = view.findViewById(R.id.sleepTimerExpandableContent);
        ivSleepTimerExpand = view.findViewById(R.id.ivSleepTimerExpand);
        sliderSleepTimer = view.findViewById(R.id.sliderSleepTimer);
        tvSleepTimerDuration = view.findViewById(R.id.tvSleepTimerDuration);
        btnStartSleepTimer = view.findViewById(R.id.btnStartSleepTimer);
        btnCustomTime = view.findViewById(R.id.btnCustomTime);

        updateSleepTimerStatus();
        updateStartButtonState();

        // Header click to expand/collapse
        if (rowSleepTimer != null) {
            rowSleepTimer.setOnClickListener(v -> toggleSleepTimerExpanded());
        }

        // Slider value change listener
        if (sliderSleepTimer != null) {
            sliderSleepTimer.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    updateSliderDurationText((int) value);
                }
            });
        }

        // Start/Cancel Timer button - toggles based on timer state
        if (btnStartSleepTimer != null) {
            btnStartSleepTimer.setOnClickListener(v -> {
                if (sleepTimerEndTime > 0) {
                    // Timer is active, cancel it
                    cancelSleepTimer();
                } else {
                    // No timer, start one
                    int hours = (int) sliderSleepTimer.getValue();
                    startSleepTimer(hours * 60);
                    toggleSleepTimerExpanded(); // Collapse after starting
                }
            });
        }

        // Custom Time button
        if (btnCustomTime != null) {
            btnCustomTime.setOnClickListener(v -> showCustomTimePicker());
        }
    }

    private void toggleSleepTimerExpanded() {
        if (sleepTimerExpandableContent == null)
            return;

        isSleepTimerExpanded = !isSleepTimerExpanded;

        if (isSleepTimerExpanded) {
            // Expand - instant, no animation
            sleepTimerExpandableContent.setVisibility(View.VISIBLE);

            // Rotate expand icon
            if (ivSleepTimerExpand != null) {
                ivSleepTimerExpand.setRotation(180f);
            }

            // Update UI state
            updateSliderDurationText((int) sliderSleepTimer.getValue());
            updateStartButtonState();
        } else {
            // Collapse - instant, no animation
            sleepTimerExpandableContent.setVisibility(View.GONE);

            // Rotate expand icon back
            if (ivSleepTimerExpand != null) {
                ivSleepTimerExpand.setRotation(0f);
            }
        }
    }

    private void updateSliderDurationText(int hours) {
        if (tvSleepTimerDuration != null) {
            tvSleepTimerDuration.setText(hours == 1 ? "1 hour" : hours + " hours");
        }
    }

    private void updateStartButtonState() {
        if (btnStartSleepTimer == null)
            return;

        if (sleepTimerEndTime > 0) {
            // Timer is active - show Cancel
            btnStartSleepTimer.setText("Cancel Timer");
            btnStartSleepTimer.setIconResource(R.drawable.ic_close);
        } else {
            // No timer - show Start
            btnStartSleepTimer.setText("Start Timer");
            btnStartSleepTimer.setIconResource(R.drawable.ic_play);
        }
    }

    private void cancelSleepTimer() {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        sleepTimerEndTime = 0;
        updateSleepTimerStatus();
        updateStartButtonState();
        showSnackbar("Sleep timer cancelled");
    }

    private void showCustomTimePicker() {
        com.google.android.material.timepicker.MaterialTimePicker picker = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(0)
                .setMinute(30)
                .setTitleText("Set sleep timer duration")
                .setInputMode(com.google.android.material.timepicker.MaterialTimePicker.INPUT_MODE_KEYBOARD)
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            int hours = picker.getHour();
            int mins = picker.getMinute();
            int totalMinutes = hours * 60 + mins;

            if (totalMinutes > 0) {
                // Cancel existing timer
                if (sleepTimer != null) {
                    sleepTimer.cancel();
                    sleepTimer = null;
                }
                startSleepTimer(totalMinutes);
                updateSleepTimerStatus();
            } else {
                showSnackbar("Please set a valid duration");
            }
        });

        picker.show(getParentFragmentManager(), "sleep_timer_picker");
    }

    private void startSleepTimer(int totalMinutes) {
        long millis = totalMinutes * 60 * 1000L;
        sleepTimerEndTime = System.currentTimeMillis() + millis;

        sleepTimer = new CountDownTimer(millis, 60000) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateSleepTimerStatus();
            }

            @Override
            public void onFinish() {
                sleepTimerEndTime = 0;
                updateSleepTimerStatus();
                updateStartButtonState();
                // Stop playback via MainActivity
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    if (activity.getAudioService() != null) {
                        activity.getAudioService().stop();
                    }
                }
                showSnackbar("Sleep timer finished");
            }
        }.start();

        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        String msg = hours > 0
                ? String.format("Sleep timer set for %dh %dm", hours, mins)
                : String.format("Sleep timer set for %d min", mins);
        showSnackbar(msg);
        updateStartButtonState();
    }

    private void updateCrossfadeText(float seconds) {
        if (tvCrossfadeValue != null) {
            if (seconds == 0) {
                tvCrossfadeValue.setText(getString(R.string.setting_sleep_timer_off));
            } else {
                tvCrossfadeValue.setText((int) seconds + "s");
            }
        }
    }

    private void updateSleepTimerStatus() {
        if (tvSleepTimerStatus == null)
            return;

        if (sleepTimerEndTime == -1) {
            tvSleepTimerStatus.setText(R.string.setting_sleep_timer_end_of_song);
        } else if (sleepTimerEndTime > 0) {
            long remaining = sleepTimerEndTime - System.currentTimeMillis();
            if (remaining > 0) {
                int mins = (int) (remaining / 60000);
                tvSleepTimerStatus.setText(mins + " min remaining");
            } else {
                tvSleepTimerStatus.setText(R.string.setting_sleep_timer_off);
            }
        } else {
            tvSleepTimerStatus.setText(R.string.setting_sleep_timer_off);
        }
    }

    // ---------------------------
    // LIBRARY SETTINGS SETUP
    // ---------------------------
    private LinearLayout containerLibraryPaths;
    private static final String KEY_LIBRARY_PATHS = "library_paths"; // Store as comma-separated URIs

    private void setupLibrarySettings(View view, SharedPreferences prefs) {
        containerLibraryPaths = view.findViewById(R.id.containerLibraryPaths);
        View rowAddMorePath = view.findViewById(R.id.rowAddMorePath);

        // Load and display all saved paths
        refreshLibraryPathsUI(prefs);

        // "Add folder" button click
        if (rowAddMorePath != null) {
            rowAddMorePath.setOnClickListener(v -> directoryPickerLauncher.launch(null));
        }
    }

    private void refreshLibraryPathsUI(SharedPreferences prefs) {
        if (containerLibraryPaths == null)
            return;
        containerLibraryPaths.removeAllViews();

        String pathsString = prefs.getString(KEY_LIBRARY_PATHS, "");
        if (pathsString.isEmpty()) {
            // Show default text if no paths
            addPathRowToContainer(getString(R.string.setting_music_source_default), null, prefs);
        } else {
            String[] uris = pathsString.split("\\|\\|");
            for (String uriStr : uris) {
                if (!uriStr.isEmpty()) {
                    addPathRowToContainer(null, uriStr, prefs);
                }
            }
        }
    }

    // @RequiresApi(api = Build.VERSION_CODES.O) - Removed as logic is compatible
    // with minSdk
    private void addPathRowToContainer(String displayText, String uriString, SharedPreferences prefs) {
        if (containerLibraryPaths == null)
            return;

        Context context = getContext();
        if (context == null)
            return;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        // Folder icon
        // Folder icon - use music folder for actual paths, regular folder for default
        android.widget.ImageView folderIcon = new android.widget.ImageView(context);
        folderIcon.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20)));
        folderIcon.setImageResource(uriString != null ? R.drawable.ic_folder_music : R.drawable.ic_folder_open);
        folderIcon.setColorFilter(com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        row.addView(folderIcon);

        // Path text
        MaterialTextView pathText = new MaterialTextView(context);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dpToPx(12));
        pathText.setLayoutParams(textParams);
        pathText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        pathText.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        pathText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        pathText.setSingleLine(true);

        if (displayText != null) {
            pathText.setText(displayText);
        } else if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            String path = uri.getPath();
            if (path != null && path.contains(":")) {
                path = path.substring(path.indexOf(":") + 1);
            }
            pathText.setText(path != null ? path : uriString);
        }
        row.addView(pathText);

        // Remove button (only if it's a real path, not default text)
        if (uriString != null) {
            MaterialTextView removeBtn = new MaterialTextView(context);
            removeBtn.setText("Remove");
            removeBtn.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            removeBtn.setTypeface(removeBtn.getTypeface(), android.graphics.Typeface.BOLD);
            removeBtn.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    context, android.R.attr.colorError, 0xFF888888));
            removeBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            removeBtn.setClickable(true);
            removeBtn.setFocusable(true);

            // Set ripple background
            android.util.TypedValue outValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            removeBtn.setBackgroundResource(outValue.resourceId);

            final String pathToRemove = uriString;
            removeBtn.setOnClickListener(v -> {
                removeLibraryPath(pathToRemove, prefs);
            });
            row.addView(removeBtn);
        }

        containerLibraryPaths.addView(row);
    }

    private void addLibraryPath(String uriString, SharedPreferences prefs) {
        String existing = prefs.getString(KEY_LIBRARY_PATHS, "");
        if (existing.contains(uriString)) {
            showSnackbar("Folder already added");
            return;
        }

        String newPaths = existing.isEmpty() ? uriString : existing + "||" + uriString;
        prefs.edit().putString(KEY_LIBRARY_PATHS, newPaths).apply();
        refreshLibraryPathsUI(prefs);
        showSnackbar("Folder added. Restart app to rescan.");
    }

    private void removeLibraryPath(String uriString, SharedPreferences prefs) {
        String existing = prefs.getString(KEY_LIBRARY_PATHS, "");
        String[] uris = existing.split("\\|\\|");
        StringBuilder newPaths = new StringBuilder();

        for (String uri : uris) {
            if (!uri.equals(uriString) && !uri.isEmpty()) {
                if (newPaths.length() > 0)
                    newPaths.append("||");
                newPaths.append(uri);
            }
        }

        prefs.edit().putString(KEY_LIBRARY_PATHS, newPaths.toString()).apply();
        refreshLibraryPathsUI(prefs);
        showSnackbar("Folder removed. Restart app to rescan.");
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ---------------------------
    // STORAGE SETTINGS SETUP
    // ---------------------------
    private void setupStorageSettings(View view) {
        tvCacheSize = view.findViewById(R.id.tvCacheSize);
        View rowClearCache = view.findViewById(R.id.rowClearCache);

        // Calculate cache size in background
        calculateCacheSize();

        if (rowClearCache != null) {
            rowClearCache.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.setting_clear_cache)
                        .setMessage("This will clear all cached data including album art. Continue?")
                        .setPositiveButton("Clear", (dialog, which) -> clearCache())
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }

    private void calculateCacheSize() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long size = getDirSize(requireContext().getCacheDir());
            size += getDirSize(requireContext().getCodeCacheDir());

            String formattedSize = formatFileSize(size);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (tvCacheSize != null) {
                    tvCacheSize.setText(formattedSize);
                }
            });
        });
    }

    private long getDirSize(File dir) {
        if (dir == null || !dir.exists())
            return 0;

        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += getDirSize(file);
                }
            }
        }
        return size;
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0)
            return "0 B";

        final String[] units = { "B", "KB", "MB", "GB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);

        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private void clearCache() {
        Executors.newSingleThreadExecutor().execute(() -> {
            deleteDir(requireContext().getCacheDir());
            deleteDir(requireContext().getCodeCacheDir());

            new Handler(Looper.getMainLooper()).post(() -> {
                if (tvCacheSize != null) {
                    tvCacheSize.setText("0 B");
                }
                showSnackbar("Cache cleared");
            });
        });
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists())
            return;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                }
                file.delete();
            }
        }
    }

    // ---------------------------
    // M3 EXPRESSIVE LIST APPEARANCE
    // ---------------------------
    private void setupExpressiveListAppearance(View view) {
        // Playback group: 3 items (Crossfade, Audio Focus, Sleep Timer)
        ListItemLayout listItemCrossfade = view.findViewById(R.id.listItemCrossfade);
        ListItemLayout listItemAudioFocus = view.findViewById(R.id.listItemAudioFocus);
        ListItemLayout listItemSleepTimer = view.findViewById(R.id.listItemSleepTimer);

        if (listItemCrossfade != null) {
            listItemCrossfade.updateAppearance(0, 3); // First of 3
        }
        if (listItemAudioFocus != null) {
            listItemAudioFocus.updateAppearance(1, 3); // Middle of 3
        }
        if (listItemSleepTimer != null) {
            listItemSleepTimer.updateAppearance(2, 3); // Last of 3
        }

        // Library group: 1 item (single)
        ListItemLayout listItemLibrary = view.findViewById(R.id.listItemLibrary);
        if (listItemLibrary != null) {
            listItemLibrary.updateAppearance(0, 1); // Single item
        }

        // Storage group: 1 item (single)
        ListItemLayout listItemStorage = view.findViewById(R.id.listItemStorage);
        if (listItemStorage != null) {
            listItemStorage.updateAppearance(0, 1); // Single item
        }

        // About group: 2 items (Version, What's New)
        ListItemLayout listItemVersion = view.findViewById(R.id.listItemVersion);
        ListItemLayout listItemWhatsNew = view.findViewById(R.id.listItemWhatsNew);

        if (listItemVersion != null) {
            listItemVersion.updateAppearance(0, 2); // First of 2
        }
        if (listItemWhatsNew != null) {
            listItemWhatsNew.updateAppearance(1, 2); // Last of 2
        }
    }

    private void showSnackbar(String message) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showSnackbar(message, Snackbar.LENGTH_SHORT);
        }
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
