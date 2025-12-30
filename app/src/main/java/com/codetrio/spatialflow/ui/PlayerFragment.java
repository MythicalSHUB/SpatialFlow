package com.codetrio.spatialflow.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.media.AudioManager;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.service.AudioPlaybackService;
import com.codetrio.spatialflow.util.AudioFileManager;
import com.codetrio.spatialflow.util.FFmpegCommandBuilder;
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.util.Arrays;

public class PlayerFragment extends Fragment {

    private static final String TAG = "PlayerFragment";

    private PlayerSharedViewModel viewModel;
    private AudioPlaybackService audioService;
    private boolean serviceBound = false;

    // =========================
    // EFFECTS-AWARE HAPTICS
    // =========================
    private boolean isSongHapticsEnabled = false;
    private boolean isActuallyPlaying = false;

    // FFT Configuration
    private static final int FFT_SIZE = 1024;

    // Multi-band history buffers
    private float[] subBassHistory = new float[4];
    private float[] bassHistory = new float[6];
    private float[] lowMidHistory = new float[6];
    private float[] midHistory = new float[6];
    private float[] highMidHistory = new float[4];
    private float[] highHistory = new float[4];
    private int historyIndex = 0;

    // Current energy levels
    private float subBassEnergy = 0f;
    private float bassEnergy = 0f;
    private float lowMidEnergy = 0f;
    private float midEnergy = 0f;
    private float highMidEnergy = 0f;
    private float highEnergy = 0f;

    // Previous frame energies
    private float lastSubBassEnergy = 0f;
    private float lastBassEnergy = 0f;
    private float lastLowMidEnergy = 0f;
    private float lastMidEnergy = 0f;
    private float lastHighMidEnergy = 0f;

    // Beat timing
    private long lastBassTime = 0;
    private long lastSnareTime = 0;
    private long lastHiHatTime = 0;
    private static final long MIN_BASS_INTERVAL = 200;
    private static final long MIN_SNARE_INTERVAL = 100;
    private static final long MIN_HIHAT_INTERVAL = 50;

    // Tempo tracking
    private float[] beatIntervals = new float[8];
    private int beatIntervalIndex = 0;
    private float estimatedBPM = 120f;

    // HAPTIC CONSTANTS
    private static final int HAPTIC_LIGHT_TICK = HapticFeedbackConstants.CLOCK_TICK;
    private static final int HAPTIC_SEGMENT_TICK = HapticFeedbackConstants.SEGMENT_TICK;
    private static final int HAPTIC_SEGMENT_FREQUENT = HapticFeedbackConstants.SEGMENT_FREQUENT_TICK;
    private static final int HAPTIC_TEXT_HANDLE = HapticFeedbackConstants.TEXT_HANDLE_MOVE;
    private static final int HAPTIC_CONTEXT_CLICK = HapticFeedbackConstants.CONTEXT_CLICK;
    private static final int HAPTIC_KEYBOARD_PRESS = HapticFeedbackConstants.KEYBOARD_PRESS;
    private static final int HAPTIC_LONG_PRESS = HapticFeedbackConstants.LONG_PRESS;

    // ===== EFFECTS MODULATION =====
    private float bassBoostMultiplier = 1.0f;
    private float loudnessMultiplier = 1.0f;
    private float eqBassMultiplier = 1.0f;
    private float eqMidMultiplier = 1.0f;
    private float eqHighMultiplier = 1.0f;
    private float playbackSpeedFactor = 1.0f;

    // Adaptive levels
    private float avgBassLevel = 0f;
    private float avgMidLevel = 0f;
    private float avgHighLevel = 0f;
    private float peakBassLevel = 0f;
    private float peakMidLevel = 0f;
    private float peakHighLevel = 0f;
    private int sampleCount = 0;

    // UI Components
    private View rootView;
    private ImageView ivAlbumArt;
    private MaterialTextView tvSongName;
    private MaterialTextView tvCurrentTime;
    private MaterialTextView tvTotalTime;
    private Slider seekBar;
    private LinearProgressIndicator waveProgress;
    private MaterialButton btnPlayPauseToggle;
    private MaterialButton btnChangeSong;
    private MaterialButton btnSavePreset;
    private MaterialButton btnRewind30, btnForward30;
    private ImageView ivVolumeIcon;
    private Slider volumeSlider;
    private MaterialTextView tvVolumePercent;
    private Chip chipSongHaptics;

    private Visualizer visualizer;
    private Handler hapticHandler;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isUserSeeking = false;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // =========================
    // SERVICE CONNECTION
    // =========================
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlaybackService.LocalBinder binder = (AudioPlaybackService.LocalBinder) service;
            audioService = binder.getService();
            serviceBound = true;
            viewModel.setAudioService(audioService);

            // Restore haptics state from ViewModel
            Boolean savedHapticState = viewModel.getIsHapticsEnabled().getValue();
            if (savedHapticState != null && savedHapticState) {
                isSongHapticsEnabled = true;
                initAdvancedHaptics();
                enableAdvancedHaptics();
            }

            Log.d(TAG, "Service connected, haptics: " + isSongHapticsEnabled);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            Log.d(TAG, "Service disconnected");
        }
    };

    // =========================
    // LIFECYCLE
    // =========================
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_player, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(PlayerSharedViewModel.class);

        setupPermissionLauncher();
        initViews(rootView);
        initHapticsSystem();
        setupObservers();
        setupListeners();
        startProgressLoop();

        Intent intent = new Intent(getContext(), AudioPlaybackService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        tvSongName.setSelected(true);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Restore haptics state
        Boolean savedHapticState = viewModel.getIsHapticsEnabled().getValue();
        if (savedHapticState != null) {
            isSongHapticsEnabled = savedHapticState;
            if (chipSongHaptics != null) {
                chipSongHaptics.setChecked(isSongHapticsEnabled);
            }
        }

        // Re-enable visualizer if haptics were on
        if (isSongHapticsEnabled && visualizer != null && isActuallyPlaying) {
            try {
                visualizer.setEnabled(true);
                Log.d(TAG, "Visualizer re-enabled on resume");
            } catch (Exception e) {
                Log.e(TAG, "Error re-enabling visualizer: " + e.getMessage());
            }
        }

        // ===== FORCE REFRESH: Always check and restore waves =====
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (waveProgress != null && viewModel != null) {
                Boolean isPlaying = viewModel.getIsPlaying().getValue();
                if (isPlaying != null && isPlaying) {
                    waveProgress.setWaveAmplitude(20);
                    waveProgress.setWaveSpeed(100);
                    Log.d(TAG, "Waves force-refreshed on resume");
                }
            }
        }, 100); // Small delay ensures view is attached
    }


    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "Fragment paused, haptics remain: " + isSongHapticsEnabled);
    }

    // =========================
    // PERMISSIONS
    // =========================
    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        Log.d(TAG, "RECORD_AUDIO granted");
                        initAdvancedHaptics();
                        if (chipSongHaptics.isChecked()) {
                            enableAdvancedHaptics();
                        }
                    } else {
                        Log.w(TAG, "RECORD_AUDIO denied");
                        chipSongHaptics.setChecked(false);
                        viewModel.setHapticsEnabled(false);
                        showSnackbar("Microphone permission required", Snackbar.LENGTH_LONG);
                    }
                });
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordAudioPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Microphone Permission")
                    .setMessage("Music Haptics analyzes frequencies for beat-synced vibrations. Audio is processed locally, never recorded.")
                    .setPositiveButton("Grant", (d, w) -> requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO))
                    .setNegativeButton("Cancel", (d, w) -> {
                        chipSongHaptics.setChecked(false);
                        viewModel.setHapticsEnabled(false);
                    })
                    .show();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    // =========================
    // INIT
    // =========================
    private void initViews(View view) {
        ivAlbumArt = view.findViewById(R.id.ivAlbumArt);
        tvSongName = view.findViewById(R.id.tvSongName);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        seekBar = view.findViewById(R.id.seekBar);
        waveProgress = view.findViewById(R.id.waveProgress);

        btnPlayPauseToggle = view.findViewById(R.id.btnPlayPauseToggle);
        btnChangeSong = view.findViewById(R.id.btnChangeSong);
        btnSavePreset = view.findViewById(R.id.btnSavePreset);
        btnRewind30 = view.findViewById(R.id.btnRewind30);
        btnForward30 = view.findViewById(R.id.btnForward30);

        ivVolumeIcon = view.findViewById(R.id.ivVolumeIcon);
        volumeSlider = view.findViewById(R.id.volumeSlider);
        tvVolumePercent = view.findViewById(R.id.tvVolumePercent);

        chipSongHaptics = view.findViewById(R.id.chipSongHaptics);

        progressHandler = new Handler(Looper.getMainLooper());
    }

    private void initHapticsSystem() {
        hapticHandler = new Handler();

        // Restore saved state from ViewModel
        Boolean savedHapticState = viewModel.getIsHapticsEnabled().getValue();
        if (savedHapticState != null) {
            isSongHapticsEnabled = savedHapticState;
            chipSongHaptics.setChecked(isSongHapticsEnabled);
        } else {
            chipSongHaptics.setChecked(false);
            isSongHapticsEnabled = false;
        }

        updateChipIcon();

        chipSongHaptics.setOnCheckedChangeListener((chip, checked) -> {
            isSongHapticsEnabled = checked;
            viewModel.setHapticsEnabled(checked);
            updateChipIcon();

            if (checked) {
                enableAdvancedHaptics();
            } else {
                disableAdvancedHaptics();
            }
        });

        // Initialize history buffers
        Arrays.fill(subBassHistory, 0f);
        Arrays.fill(bassHistory, 0f);
        Arrays.fill(lowMidHistory, 0f);
        Arrays.fill(midHistory, 0f);
        Arrays.fill(highMidHistory, 0f);
        Arrays.fill(highHistory, 0f);
        Arrays.fill(beatIntervals, 500f);

        Log.d(TAG, "Effects-aware haptics initialized, state: " + isSongHapticsEnabled);
    }

    private void updateChipIcon() {
        if (chipSongHaptics == null) return;
        chipSongHaptics.setChipIcon(getResources().getDrawable(
                isSongHapticsEnabled ? R.drawable.ic_vibration : R.drawable.ic_vibration_off, null));
    }

    // =========================
    // PROGRESS ANIMATION
    // =========================
    private void startProgressLoop() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (audioService != null && isActuallyPlaying && !isUserSeeking) {
                    int current = audioService.getCurrentPosition();
                    int total = audioService.getDuration();

                    if (total > 0) {
                        // Update wave progress (0-1000 scale)
                        int waveValue = (int) ((current * 1000L) / total);
                        if (waveProgress != null) {
                            waveProgress.setProgressCompat(waveValue, true);
                        }

                        // Sync seekbar
                        if (seekBar != null) {
                            seekBar.setValue(current);
                        }

                        // Update time
                        if (tvCurrentTime != null) {
                            tvCurrentTime.setText(formatTime(current));
                        }
                    }
                }

                // ~60 FPS
                progressHandler.postDelayed(this, 16);
            }
        };

        progressHandler.post(progressRunnable);
    }

    // =========================
    // VISUALIZER INIT
    // =========================
    private void initAdvancedHaptics() {
        if (!hasRecordPermission() || audioService == null) {
            Log.w(TAG, "Cannot init - no permission or service");
            return;
        }

        try {
            int audioSessionId = audioService.getAudioSessionId();
            if (audioSessionId == 0) {
                Log.w(TAG, "Invalid audio session");
                return;
            }

            if (visualizer != null) {
                releaseVisualizer();
            }

            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(FFT_SIZE);

            visualizer.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int rate) {}

                        @Override
                        public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                            Boolean hapticsEnabled = viewModel.getIsHapticsEnabled().getValue();
                            if (hapticsEnabled != null && hapticsEnabled && isActuallyPlaying) {
                                processEffectsAwareBeatDetection(fft);
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true
            );

            Log.d(TAG, "Visualizer created with effects-aware processing");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Visualizer: " + e.getMessage(), e);
        }
    }

    // =========================
    // EFFECTS-AWARE BEAT DETECTION
    // =========================
    private void processEffectsAwareBeatDetection(byte[] fft) {
        if (!isActuallyPlaying) return;

        float subBassSum = 0f, bassSum = 0f, lowMidSum = 0f;
        float midSum = 0f, highMidSum = 0f, highSum = 0f;

        int subBassEnd = 4;
        int bassEnd = 16;
        int lowMidEnd = 32;
        int midEnd = 128;
        int highMidEnd = 256;
        int highEnd = 400;

        for (int i = 2; i < subBassEnd; i += 2) {
            float real = (float) fft[i];
            float imag = (float) fft[i + 1];
            subBassSum += (float) Math.sqrt(real * real + imag * imag);
        }

        for (int i = subBassEnd; i < bassEnd; i += 2) {
            float real = (float) fft[i];
            float imag = (float) fft[i + 1];
            bassSum += (float) Math.sqrt(real * real + imag * imag);
        }

        for (int i = bassEnd; i < lowMidEnd; i += 2) {
            float real = (float) fft[i];
            float imag = (float) fft[i + 1];
            lowMidSum += (float) Math.sqrt(real * real + imag * imag);
        }

        for (int i = lowMidEnd; i < midEnd; i += 2) {
            float real = (float) fft[i];
            float imag = (float) fft[i + 1];
            midSum += (float) Math.sqrt(real * real + imag * imag);
        }

        for (int i = midEnd; i < Math.min(highMidEnd, fft.length - 1); i += 2) {
            float real = (float) fft[i];
            float imag = (float) fft[i + 1];
            highMidSum += (float) Math.sqrt(real * real + imag * imag);
        }

        for (int i = highMidEnd; i < Math.min(highEnd, fft.length - 1); i += 2) {
            float real = (float) fft[i];
            float imag = (float) fft[i + 1];
            highSum += (float) Math.sqrt(real * real + imag * imag);
        }

        subBassEnergy = subBassSum / ((subBassEnd - 2) / 2 + 1);
        bassEnergy = bassSum / ((bassEnd - subBassEnd) / 2 + 1);
        lowMidEnergy = lowMidSum / ((lowMidEnd - bassEnd) / 2 + 1);
        midEnergy = midSum / ((midEnd - lowMidEnd) / 2 + 1);
        highMidEnergy = highMidSum / ((highMidEnd - midEnd) / 2 + 1);
        highEnergy = highSum / ((Math.min(highEnd, fft.length - 1) - highMidEnd) / 2 + 1);

        bassEnergy *= bassBoostMultiplier * eqBassMultiplier * loudnessMultiplier;
        lowMidEnergy *= eqBassMultiplier * loudnessMultiplier;
        midEnergy *= eqMidMultiplier * loudnessMultiplier;
        highMidEnergy *= eqHighMultiplier * loudnessMultiplier;
        highEnergy *= eqHighMultiplier * loudnessMultiplier;

        subBassHistory[historyIndex % subBassHistory.length] = subBassEnergy;
        bassHistory[historyIndex % bassHistory.length] = bassEnergy;
        lowMidHistory[historyIndex % lowMidHistory.length] = lowMidEnergy;
        midHistory[historyIndex % midHistory.length] = midEnergy;
        highMidHistory[historyIndex % highMidHistory.length] = highMidEnergy;
        highHistory[historyIndex % highHistory.length] = highEnergy;
        historyIndex++;

        float avgBass = calculateAverage(bassHistory);
        float avgMid = calculateAverage(midHistory);
        float avgHigh = calculateAverage(highHistory);

        sampleCount++;
        avgBassLevel = (avgBassLevel * Math.min(sampleCount - 1, 100) + bassEnergy) / Math.min(sampleCount, 101);
        avgMidLevel = (avgMidLevel * Math.min(sampleCount - 1, 100) + midEnergy) / Math.min(sampleCount, 101);
        avgHighLevel = (avgHighLevel * Math.min(sampleCount - 1, 100) + highEnergy) / Math.min(sampleCount, 101);

        if (bassEnergy > peakBassLevel) peakBassLevel = bassEnergy;
        else peakBassLevel *= 0.999f;

        if (midEnergy > peakMidLevel) peakMidLevel = midEnergy;
        else peakMidLevel *= 0.999f;

        if (highEnergy > peakHighLevel) peakHighLevel = highEnergy;
        else peakHighLevel *= 0.999f;

        long now = System.currentTimeMillis();

        long adjustedBassInterval = (long) (MIN_BASS_INTERVAL / playbackSpeedFactor);
        long adjustedSnareInterval = (long) (MIN_SNARE_INTERVAL / playbackSpeedFactor);
        long adjustedHiHatInterval = (long) (MIN_HIHAT_INTERVAL / playbackSpeedFactor);

        boolean isKickTransient = bassEnergy > avgBass * 1.18f &&
                bassEnergy > lastBassEnergy * 1.15f &&
                (now - lastBassTime) > adjustedBassInterval;

        boolean isSnareTransient = (midEnergy > avgMid * 1.4f &&
                midEnergy > lastMidEnergy * 1.25f &&
                (now - lastSnareTime) > adjustedSnareInterval) ||
                (midEnergy > avgMid * 1.8f &&
                        (now - lastSnareTime) > adjustedSnareInterval);

        boolean isHiHatTransient = (highEnergy > avgHigh * 1.6f &&
                highEnergy > lastHighMidEnergy * 1.3f &&
                (now - lastHiHatTime) > adjustedHiHatInterval) ||
                (highMidEnergy > calculateAverage(highMidHistory) * 1.7f &&
                        (now - lastHiHatTime) > adjustedHiHatInterval);

        if (isKickTransient) {
            if (lastBassTime > 0) {
                long interval = now - lastBassTime;
                if (interval > 300 && interval < 1200) {
                    beatIntervals[beatIntervalIndex] = interval;
                    beatIntervalIndex = (beatIntervalIndex + 1) % beatIntervals.length;
                    estimatedBPM = 60000f / calculateAverage(beatIntervals);
                }
            }

            lastBassTime = now;
            float intensity = Math.min(1f, (bassEnergy - avgBass) / (peakBassLevel - avgBass + 0.1f));
            triggerHaptic(intensity, HapticType.KICK);

            Log.v(TAG, String.format("🥁 KICK: %.2f (Bass×%.2f Loud×%.2f EQ×%.2f)",
                    intensity, bassBoostMultiplier, loudnessMultiplier, eqBassMultiplier));
        }

        if (isSnareTransient) {
            lastSnareTime = now;
            float intensity = Math.min(0.85f, (midEnergy - avgMid) / (peakMidLevel - avgMid + 0.1f));
            triggerHaptic(intensity, HapticType.SNARE);

            Log.v(TAG, String.format("🥁 SNARE: %.2f (EQ×%.2f)", intensity, eqMidMultiplier));
        }

        if (isHiHatTransient) {
            lastHiHatTime = now;
            float intensity = Math.min(0.6f, (highEnergy - avgHigh) / (peakHighLevel - avgHigh + 0.1f));
            triggerHaptic(intensity, HapticType.HIHAT);

            Log.v(TAG, String.format("🔔 HI-HAT: %.2f (EQ×%.2f)", intensity, eqHighMultiplier));
        }

        lastSubBassEnergy = subBassEnergy;
        lastBassEnergy = bassEnergy;
        lastLowMidEnergy = lowMidEnergy;
        lastMidEnergy = midEnergy;
        lastHighMidEnergy = highMidEnergy;
    }

    private float calculateAverage(float[] array) {
        float sum = 0f;
        for (float value : array) sum += value;
        return sum / array.length;
    }

    private enum HapticType {
        KICK, SNARE, HIHAT
    }

    // =========================
    // HAPTIC TRIGGERING
    // =========================
    private void triggerHaptic(float intensity, HapticType type) {
        if (!isActuallyPlaying || rootView == null || !rootView.isAttachedToWindow()) return;

        int hapticConstant;

        switch (type) {
            case KICK:
                if (intensity > 0.9f) {
                    hapticConstant = HAPTIC_LONG_PRESS;
                } else if (intensity > 0.75f) {
                    hapticConstant = HAPTIC_KEYBOARD_PRESS;
                } else if (intensity > 0.6f) {
                    hapticConstant = HAPTIC_CONTEXT_CLICK;
                } else if (intensity > 0.4f) {
                    hapticConstant = HAPTIC_TEXT_HANDLE;
                } else if (intensity > 0.25f) {
                    hapticConstant = HAPTIC_SEGMENT_TICK;
                } else {
                    hapticConstant = HAPTIC_LIGHT_TICK;
                }
                break;

            case SNARE:
                hapticConstant = intensity > 0.6f ? HAPTIC_CONTEXT_CLICK :
                        intensity > 0.4f ? HAPTIC_TEXT_HANDLE :
                                intensity > 0.25f ? HAPTIC_SEGMENT_TICK : HAPTIC_SEGMENT_FREQUENT;
                break;

            case HIHAT:
                hapticConstant = intensity > 0.5f ? HAPTIC_SEGMENT_TICK :
                        intensity > 0.3f ? HAPTIC_SEGMENT_FREQUENT : HAPTIC_LIGHT_TICK;
                break;

            default:
                hapticConstant = HAPTIC_LIGHT_TICK;
                break;
        }

        try {
            rootView.performHapticFeedback(hapticConstant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception e) {
            Log.e(TAG, "Haptic error: " + e.getMessage());
        }
    }

    // =========================
// OBSERVERS WITH EFFECTS SYNC
// =========================
    private void setupObservers() {
        // Playing state observer - controls play/pause button and wave animation
        // Playing state observer
        // Playing state observer
        viewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            isActuallyPlaying = playing != null && playing;

            btnPlayPauseToggle.setIcon(getResources().getDrawable(
                    isActuallyPlaying ? R.drawable.ic_pause : R.drawable.ic_play, null));

            // Control wave animation - MUST call after view is attached
            // In setupObservers()
            if (waveProgress != null) {
                if (isActuallyPlaying) {
                    waveProgress.setWaveAmplitude(20);   // THICC waves
                    waveProgress.setWaveSpeed(100);       // FAST speed
                } else {
                    waveProgress.setWaveSpeed(0);
                    waveProgress.setWaveAmplitude(0);
                    waveProgress.setProgressCompat(0, false);
                }
            }


            if (visualizer != null) {
                Boolean hapticsEnabled = viewModel.getIsHapticsEnabled().getValue();
                if (hapticsEnabled != null && hapticsEnabled) {
                    try {
                        visualizer.setEnabled(isActuallyPlaying);
                        Log.d(TAG, "Visualizer toggled: " + isActuallyPlaying);
                    } catch (Exception e) {
                        Log.e(TAG, "Error toggling visualizer: " + e.getMessage());
                    }
                }
            }
        });



        // Current position observer - updates seekbar and time display
        viewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> {
            if (position != null && !isUserSeeking) {
                seekBar.setValue(position);
                tvCurrentTime.setText(formatTime(position));
            }
        });

        // Duration observer - sets max values and total time
        viewModel.getDuration().observe(getViewLifecycleOwner(), duration -> {
            if (duration != null) {
                seekBar.setValueTo(duration > 0 ? duration : 100);
                tvTotalTime.setText(formatTime(duration));

                // Set wave progress max (0-1000 scale for smooth animation)
                if (waveProgress != null) {
                    waveProgress.setMax(1000);
                }
            }
        });

        // Song URI observer - loads metadata and resets states
        viewModel.getSongUri().observe(getViewLifecycleOwner(), uri -> {
            if (uri != null) {
                loadSongMetadata(uri);
                resetHapticState();

                // Reset progress indicator to flat
                if (waveProgress != null) {
                    waveProgress.setProgressCompat(0, false);
                    waveProgress.setWaveSpeed((int) 0f);
                    waveProgress.setWaveAmplitude((int) 0f);
                }

                // Reinitialize haptics if enabled
                Boolean hapticsEnabled = viewModel.getIsHapticsEnabled().getValue();
                if (hapticsEnabled != null && hapticsEnabled) {
                    disableAdvancedHaptics();
                    releaseVisualizer();
                    new Handler().postDelayed(() -> {
                        initAdvancedHaptics();
                        enableAdvancedHaptics();
                    }, 500);
                }
            }
        });

        // ===== EFFECTS OBSERVERS - Modulate haptic intensity =====

        // Bass Boost observer - increases kick drum haptic intensity
        viewModel.getBassBoost().observe(getViewLifecycleOwner(), bassBoost -> {
            if (bassBoost != null) {
                // Map 0-12dB to 0.7x-1.8x multiplier
                bassBoostMultiplier = 1.0f + (bassBoost / 12.0f) * 0.8f;
                bassBoostMultiplier = Math.max(0.7f, Math.min(1.8f, bassBoostMultiplier));
                Log.d(TAG, String.format("Bass Boost: %d dB → Haptic ×%.2f", bassBoost, bassBoostMultiplier));
            }
        });

        // Loudness Gain observer - amplifies all haptics
        viewModel.getLoudnessGain().observe(getViewLifecycleOwner(), loudness -> {
            if (loudness != null) {
                // Map 0-10dB to 1.0x-1.5x multiplier
                loudnessMultiplier = 1.0f + (loudness / 10.0f) * 0.5f;
                Log.d(TAG, String.format("Loudness: +%d dB → Haptic ×%.2f", loudness, loudnessMultiplier));
            }
        });

        // EQ Band observers - frequency-specific haptic adjustments
        viewModel.getEqBand1().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand2().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand3().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand4().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand5().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());

        // Playback Speed observer - adjusts beat timing thresholds
        viewModel.getPlaybackSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed != null) {
                playbackSpeedFactor = speed;
                Log.d(TAG, String.format("Playback Speed: %.2fx → Timing adjusted", speed));
            }
        });
    }



    private void updateEqMultipliers() {
        Integer band1 = viewModel.getEqBand1().getValue();
        Integer band2 = viewModel.getEqBand2().getValue();
        Integer band3 = viewModel.getEqBand3().getValue();
        Integer band4 = viewModel.getEqBand4().getValue();
        Integer band5 = viewModel.getEqBand5().getValue();

        if (band1 != null && band2 != null) {
            float avgBassGain = (band1 + band2) / 2.0f;
            eqBassMultiplier = 1.0f + (avgBassGain / 15.0f) * 0.6f;
            eqBassMultiplier = Math.max(0.4f, Math.min(1.6f, eqBassMultiplier));
        }

        if (band3 != null) {
            eqMidMultiplier = 1.0f + (band3 / 15.0f) * 0.5f;
            eqMidMultiplier = Math.max(0.5f, Math.min(1.5f, eqMidMultiplier));
        }

        if (band4 != null && band5 != null) {
            float avgHighGain = (band4 + band5) / 2.0f;
            eqHighMultiplier = 1.0f + (avgHighGain / 15.0f) * 0.4f;
            eqHighMultiplier = Math.max(0.6f, Math.min(1.4f, eqHighMultiplier));
        }

        Log.d(TAG, String.format("EQ Multipliers → Bass: ×%.2f Mid: ×%.2f High: ×%.2f",
                eqBassMultiplier, eqMidMultiplier, eqHighMultiplier));
    }

    private void resetHapticState() {
        Arrays.fill(subBassHistory, 0f);
        Arrays.fill(bassHistory, 0f);
        Arrays.fill(lowMidHistory, 0f);
        Arrays.fill(midHistory, 0f);
        Arrays.fill(highMidHistory, 0f);
        Arrays.fill(highHistory, 0f);
        Arrays.fill(beatIntervals, 500f);
        historyIndex = 0;
        beatIntervalIndex = 0;
        sampleCount = 0;
        avgBassLevel = 0f;
        avgMidLevel = 0f;
        avgHighLevel = 0f;
        peakBassLevel = 0f;
        peakMidLevel = 0f;
        peakHighLevel = 0f;
        lastBassTime = 0;
        lastSnareTime = 0;
        lastHiHatTime = 0;
        estimatedBPM = 120f;
    }

    // =========================
    // LISTENERS
    // =========================
    private void setupListeners() {
        btnPlayPauseToggle.setOnClickListener(v -> {
            Boolean playing = viewModel.getIsPlaying().getValue();
            if (playing != null && playing) viewModel.pauseAudio();
            else viewModel.playAudio();
        });

        btnRewind30.setOnClickListener(v -> {
            if (audioService != null) {
                int currentPos = audioService.getCurrentPosition();
                int newPos = Math.max(0, currentPos - 30000);
                viewModel.seekTo(newPos);
                showSnackbar("Rewound 30 seconds", Snackbar.LENGTH_SHORT);
            }
        });

        btnForward30.setOnClickListener(v -> {
            if (audioService != null) {
                int currentPos = audioService.getCurrentPosition();
                int duration = audioService.getDuration();
                int newPos = Math.min(duration, currentPos + 30000);
                viewModel.seekTo(newPos);
                showSnackbar("Skipped 30 seconds", Snackbar.LENGTH_SHORT);
            }
        });

        AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);

        if (audioManager != null) {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            float volumePercent = (currentVolume * 100f) / maxVolume;

            int roundedPercent = Math.round(volumePercent);
            volumeSlider.setValue(roundedPercent);
            tvVolumePercent.setText(String.format("%d%%", roundedPercent));
            updateVolumeIcon(roundedPercent);

            volumeSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int newVolume = Math.round((value / 100f) * maxVol);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);

                    tvVolumePercent.setText(String.format("%d%%", (int) value));
                    updateVolumeIcon((int) value);
                }
            });
        }

        btnChangeSong.setOnClickListener(v -> openSongPicker());
        btnSavePreset.setOnClickListener(v -> saveAudioWithEffects());

        seekBar.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && audioService != null) {
                isUserSeeking = true;
                audioService.seekTo((int) value);
                tvCurrentTime.setText(formatTime((int) value));
            }
        });

        seekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                viewModel.seekTo((int) slider.getValue());
                isUserSeeking = false;
            }
        });
    }

    private void updateVolumeIcon(int volumePercent) {
        if (ivVolumeIcon == null) return;

        int iconRes;
        if (volumePercent == 0) {
            iconRes = R.drawable.ic_volume_off;
        } else if (volumePercent < 50) {
            iconRes = R.drawable.ic_volume_down;
        } else {
            iconRes = R.drawable.ic_volume_up;
        }

        ivVolumeIcon.setImageDrawable(getResources().getDrawable(iconRes, null));
    }

    private void enableAdvancedHaptics() {
        if (!hasRecordPermission()) {
            requestRecordAudioPermission();
            chipSongHaptics.setChecked(false);
            viewModel.setHapticsEnabled(false);
            return;
        }

        if (visualizer != null) {
            try {
                visualizer.setEnabled(true);
                resetHapticState();

                Log.d(TAG, "Effects-aware haptics ENABLED");
                showSnackbar("Music Haptics enabled - Feel the effects!", Snackbar.LENGTH_SHORT);
            } catch (Exception e) {
                Log.e(TAG, "Failed: " + e.getMessage());
                chipSongHaptics.setChecked(false);
                viewModel.setHapticsEnabled(false);
            }
        } else {
            initAdvancedHaptics();
            if (visualizer != null) {
                enableAdvancedHaptics();
            } else {
                showSnackbar("Play a song first", Snackbar.LENGTH_SHORT);
                chipSongHaptics.setChecked(false);
                viewModel.setHapticsEnabled(false);
            }
        }
    }

    private void disableAdvancedHaptics() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                Log.d(TAG, "Haptics DISABLED");
            } catch (Exception e) {
                Log.e(TAG, "Failed to disable: " + e.getMessage());
            }
        }
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
                visualizer = null;
                Log.d(TAG, "Visualizer released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing: " + e.getMessage());
            }
        }
    }

    // =========================
    // UTILITY METHODS
    // =========================
    private void openSongPicker() {
        SongPickerBottomSheet sheet = new SongPickerBottomSheet();
        sheet.setOnSongSelectedListener((title, artist, path) -> {
            Uri uri = Uri.fromFile(new File(path));
            viewModel.setSongUri(uri);
        });
        sheet.show(getParentFragmentManager(), "song_picker");
    }

    private void loadSongMetadata(Uri uri) {
        new Thread(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                if (getContext() == null) return;

                retriever.setDataSource(getContext(), uri);

                String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

                byte[] art = retriever.getEmbeddedPicture();
                Bitmap albumArt = null;
                if (art != null) {
                    albumArt = BitmapFactory.decodeByteArray(art, 0, art.length);
                }

                retriever.release();

                String displayName;
                if (title != null && !title.isEmpty()) {
                    displayName = (artist != null && !artist.isEmpty()) ? artist + " - " + title : title;
                } else {
                    displayName = getFileNameFromUri(uri);
                }

                final String finalDisplayName = displayName;
                final Bitmap finalAlbumArt = albumArt;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvSongName.setText(finalDisplayName);
                        if (finalAlbumArt != null) {
                            ivAlbumArt.setImageBitmap(finalAlbumArt);
                        } else {
                            ivAlbumArt.setImageResource(R.drawable.default_album_art);
                        }
                        viewModel.updateSongMetadata(finalDisplayName, finalAlbumArt);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Metadata error: " + e.getMessage(), e);
            }
        }).start();
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String getFileNameFromUri(Uri uri) {
        String displayName = null;

        if (getContext() != null) {
            try (Cursor cursor = getContext().getContentResolver().query(
                    uri, new String[]{MediaStore.Audio.Media.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }

        if (displayName == null) {
            String path = uri.getPath();
            if (path != null) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash != -1) {
                    displayName = path.substring(lastSlash + 1);
                }
            }
        }

        return displayName != null ? displayName : "Unknown Song";
    }

    private void saveAudioWithEffects() {
        Uri currentUri = viewModel.getSongUri().getValue();
        if (currentUri == null) {
            showSnackbar("No song selected", Snackbar.LENGTH_SHORT);
            return;
        }

        Boolean is8D = viewModel.getIs8DEnabled().getValue();
        Boolean isBass = viewModel.getIsBassEnabled().getValue();

        if ((is8D == null || !is8D) && (isBass == null || !isBass)) {
            showSnackbar("Enable 8D effect", Snackbar.LENGTH_LONG);
            return;
        }

        Snackbar processingSnackbar = Snackbar.make(rootView, "Processing...", Snackbar.LENGTH_INDEFINITE);
        View bottomNav = getActivity().findViewById(R.id.nav_view);
        if (bottomNav != null) processingSnackbar.setAnchorView(bottomNav);
        processingSnackbar.show();

        new Thread(() -> {
            try {
                String inputPath = AudioFileManager.getRealPathFromURI(getContext(), currentUri);
                if (inputPath == null) {
                    dismissSnackbarAndShow(processingSnackbar, "Could not access file", Snackbar.LENGTH_SHORT);
                    return;
                }

                String fileName = "Spatial_" + getFileNameFromUri(currentUri);
                File outputFile = AudioFileManager.createOutputFile(getContext(), fileName);
                String outputPath = outputFile.getAbsolutePath();

                String command = FFmpegCommandBuilder.build8D(inputPath, outputPath, 0.2f);
                FFmpegKit.execute(command);

                if (outputFile.exists() && outputFile.length() > 0) {
                    AudioFileManager.scanFile(getContext(), outputFile);
                    dismissSnackbarAndShowWithAction(processingSnackbar, "✓ Saved to Downloads/SpatialFlow", Snackbar.LENGTH_LONG, outputFile);
                } else {
                    dismissSnackbarAndShow(processingSnackbar, "Failed to save", Snackbar.LENGTH_SHORT);
                }

            } catch (Exception e) {
                dismissSnackbarAndShow(processingSnackbar, "Error: " + e.getMessage(), Snackbar.LENGTH_LONG);
            }
        }).start();
    }

    private void showSnackbar(String message, int duration) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                Snackbar snackbar = Snackbar.make(rootView, message, duration);
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null) snackbar.setAnchorView(bottomNav);
                snackbar.show();
            });
        }
    }

    private void dismissSnackbarAndShow(Snackbar oldSnackbar, String message, int duration) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                if (oldSnackbar != null) oldSnackbar.dismiss();
                Snackbar snackbar = Snackbar.make(rootView, message, duration);
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null) snackbar.setAnchorView(bottomNav);
                snackbar.show();
            });
        }
    }

    private void dismissSnackbarAndShowWithAction(Snackbar oldSnackbar, String message, int duration, File outputFile) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                if (oldSnackbar != null) oldSnackbar.dismiss();
                Snackbar snackbar = Snackbar.make(rootView, message, duration);
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null) snackbar.setAnchorView(bottomNav);
                snackbar.setAction("SHOW", v -> openSpatialFlowFolder(outputFile));
                snackbar.show();
            });
        }
    }

    private void openSpatialFlowFolder(File outputFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(outputFile.getParent());
        intent.setDataAndType(uri, "resource/folder");
        if (intent.resolveActivityInfo(requireContext().getPackageManager(), 0) != null) {
            startActivity(intent);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Stop progress loop
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }

        // Only release visualizer if haptics disabled or app closing
        Boolean hapticsEnabled = viewModel.getIsHapticsEnabled().getValue();
        boolean shouldRelease = (hapticsEnabled == null || !hapticsEnabled) ||
                (getActivity() != null && getActivity().isFinishing());

        if (shouldRelease) {
            releaseVisualizer();
            Log.d(TAG, "Visualizer released on destroy");
        } else {
            Log.d(TAG, "Visualizer kept alive (haptics ON, fragment switch)");
        }

        if (serviceBound) {
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}
