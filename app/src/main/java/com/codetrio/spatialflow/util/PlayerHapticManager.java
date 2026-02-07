package com.codetrio.spatialflow.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.Arrays;

/**
 * PlayerHapticManager: Premium music haptics engine
 * Uses Android's native haptic constants for Apple Music-style feedback
 */
public class PlayerHapticManager {
    private static final String TAG = "PlayerHapticManager";
    private final Context context;
    private final Handler hapticHandler;

    // Settings SharedPreferences
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_HAPTICS_ENABLED = "haptics_enabled";
    private static final String KEY_VIBRATION_STRENGTH = "vibration_strength";
    private SharedPreferences prefs;

    private Vibrator cachedVibrator;
    private Visualizer visualizer;
    private boolean hasAmplitudeControl = false;
    private boolean hasViewHaptics = false;
    private boolean isHapticsEnabled = false;
    private View attachedView = null;

    // User-controlled intensity multiplier (0.0 - 1.0)
    private float vibrationStrengthMultiplier = 0.8f; // Boosted default from 0.5f

    // Device capability modes
    private HapticMode deviceMode;

    private enum HapticMode {
        VIBRATION_ONLY,
        HAPTIC_ONLY,
        DUAL_MODE
    }

    // Required external multipliers
    public float bassBoostMultiplier = 1.0f;
    public float loudnessMultiplier = 1.0f;
    public float eqBassMultiplier = 1.0f;
    public float eqMidMultiplier = 1.0f;
    public float eqHighMultiplier = 1.0f;
    public float playbackSpeedFactor = 1.0f;

    // Signal processing
    private static final int FFT_SIZE = 1024;
    private float[] hammingWindow;

    // Frequency band separation
    private final float[] subBassHistory = new float[4];
    private final float[] bassHistory = new float[5];
    private final float[] midHistory = new float[6];
    private final float[] highHistory = new float[5];
    private int historyIndex = 0;

    // Energy tracking
    private float lastBassEnergy = 0f;
    private float lastMidEnergy = 0f;
    private float lastHighEnergy = 0f;

    private float avgBassLevel = 0f;
    private float avgMidLevel = 0f;
    private float avgHighLevel = 0f;

    private float peakBassLevel = 0.5f;
    private float peakMidLevel = 0.3f;

    private static final float EMA_ALPHA = 0.12f;
    private static final float PEAK_DECAY = 0.998f;

    // Timing & beat detection
    private long lastKickTime = 0;
    private long lastSnareTime = 0;
    private static final long MIN_KICK_INTERVAL = 200;
    private static final long MIN_SNARE_INTERVAL = 150;

    // BPM tracking
    private final float[] beatIntervals = new float[6];
    private int beatIntervalIndex = 0;
    private float estimatedBPM = 120f;

    // Continuous haptic state
    private boolean isPlayingContinuous = false;
    private float currentBassIntensity = 0f;
    private float targetBassIntensity = 0f;

    // Auto-Intensity (Dynamic Gain)
    private float globalEnergyAvg = 0.15f; // Moving average of total energy
    private static final float GAIN_ADJUST_SPEED = 0.005f; // Slow adaptation

    // Asymmetric envelope for "rubbery" feel
    private static final float ENVELOPE_ATTACK = 0.72f;
    private static final float ENVELOPE_DECAY = 0.09f;
    private static final long CONTINUOUS_UPDATE_MS = 50;
    private long lastContinuousUpdate = 0;

    public enum HapticType {
        KICK, SNARE, HIHAT
    }

    private long lastTriggeredKickTime = 0;

    // Listener for settings changes
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (sharedPreferences, key) -> {
        if (KEY_HAPTICS_ENABLED.equals(key)) {
            boolean enabled = sharedPreferences.getBoolean(KEY_HAPTICS_ENABLED, true);
            setHapticsEnabled(enabled);
        } else if (KEY_VIBRATION_STRENGTH.equals(key)) {
            float strength = sharedPreferences.getFloat(KEY_VIBRATION_STRENGTH, 80f); // Default 80%
            vibrationStrengthMultiplier = strength / 100f;
            Log.d(TAG, "Strength updated: " + vibrationStrengthMultiplier);
        }
    };

    public PlayerHapticManager(Context context) {
        this.context = context;

        android.os.HandlerThread hapticThread = new android.os.HandlerThread(
                "HapticThread",
                android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        hapticThread.start();
        this.hapticHandler = new Handler(hapticThread.getLooper());

        init();
        loadSettingsFromPrefs();
        registerPrefsListener();
    }

    private void registerPrefsListener() {
        if (context != null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private void unregisterPrefsListener() {
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private void init() {
        hasAmplitudeControl = detectAmplitudeControl();
        detectDeviceCapabilities();

        Arrays.fill(subBassHistory, 0f);
        Arrays.fill(bassHistory, 0f);
        Arrays.fill(midHistory, 0f);
        Arrays.fill(highHistory, 0f);
        Arrays.fill(beatIntervals, 500f);

        // Pre-calculate Hamming window for FFT
        hammingWindow = new float[FFT_SIZE / 2];
        for (int i = 0; i < hammingWindow.length; i++) {
            hammingWindow[i] = (float) (0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (hammingWindow.length - 1)));
        }

        Log.d(TAG, "Haptic Mode: " + deviceMode + ", Amplitude Control: " + hasAmplitudeControl);
    }

    /**
     * Load haptic settings from SharedPreferences.
     * Call this when resuming playback or when settings change.
     */
    public void loadSettingsFromPrefs() {
        if (context == null)
            return;
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        isHapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true);
        vibrationStrengthMultiplier = prefs.getFloat(KEY_VIBRATION_STRENGTH, 80f) / 100f; // Default 80%
        Log.d(TAG, "Settings loaded - Enabled: " + isHapticsEnabled +
                ", Strength: " + vibrationStrengthMultiplier);
    }

    private void detectDeviceCapabilities() {
        Vibrator vib = getVibrator();
        boolean hasVibrator = vib != null && vib.hasVibrator();
        hasViewHaptics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

        if (hasVibrator && hasViewHaptics) {
            deviceMode = HapticMode.DUAL_MODE;
        } else if (hasVibrator) {
            deviceMode = HapticMode.VIBRATION_ONLY;
        } else if (hasViewHaptics) {
            deviceMode = HapticMode.HAPTIC_ONLY;
        } else {
            deviceMode = HapticMode.VIBRATION_ONLY;
        }
    }

    public void attachView(View view) {
        this.attachedView = view;
        if (view != null && deviceMode != HapticMode.VIBRATION_ONLY) {
            view.setHapticFeedbackEnabled(true);
            view.setClickable(true);
        }
        Log.d(TAG, "View attached for haptics");
    }

    public void setHapticsEnabled(boolean enabled) {
        boolean wasEnabled = this.isHapticsEnabled;
        this.isHapticsEnabled = enabled;

        if (!enabled && wasEnabled) {
            // Clean stop when toggling off
            stopAllHaptics();
        }

        Log.d(TAG, "Haptics " + (enabled ? "ENABLED" : "DISABLED"));
    }

    public boolean isHapticsEnabled() {
        return isHapticsEnabled;
    }

    public boolean hasHapticsCapability() {
        return deviceMode != null;
    }

    private Vibrator getVibrator() {
        if (cachedVibrator != null)
            return cachedVibrator;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            cachedVibrator = manager != null ? manager.getDefaultVibrator() : null;
        } else {
            cachedVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        return cachedVibrator;
    }

    private boolean detectAmplitudeControl() {
        Vibrator vibrator = getVibrator();
        if (vibrator == null)
            return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return vibrator.hasAmplitudeControl();
        }
        return false;
    }

    public void initVisualizer(int audioSessionId) {
        try {
            if (visualizer != null) {
                try {
                    visualizer.setEnabled(false);
                    visualizer.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing visualizer");
                }
                visualizer = null;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }

            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(FFT_SIZE);
            visualizer.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int rate) {
                        }

                        @Override
                        public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                            if (isHapticsEnabled) {
                                processMusicHaptics(fft);
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate(), false, true);

            visualizer.setEnabled(true);
            Log.d(TAG, "Visualizer initialized [session:" + audioSessionId + "]");
        } catch (Exception e) {
            Log.e(TAG, "Visualizer init failed: " + e.getMessage());
            visualizer = null;
        }
    }

    public void release() {
        stopAllHaptics();
        unregisterPrefsListener();
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception e) {
                Log.w(TAG, "Release error");
            }
            visualizer = null;
        }
    }

    // ============================================================
    // MUSIC ANALYSIS & HAPTIC PROCESSING
    // ============================================================

    private void processMusicHaptics(byte[] fft) {
        // Extract frequency bands with Hamming window (Stricter bins for vocal
        // isolation)
        // Bin 2-5: ~86-215Hz (Deep Sub/Kick)
        float subBassEnergy = computeBandEnergy(fft, 2, 5);
        // Bin 7-12: ~300-516Hz (Mud/Warmth/Low Vocals)
        float bassEnergy = computeBandEnergy(fft, 7, 12);
        // Bin 30-100: ~1.2k-4.3k (Snare Crack/Presence/Vocals)
        float midEnergy = computeBandEnergy(fft, 30, 100);
        // Bin 101-200: ~4.3k-8.6k (Highs/Air)
        float highEnergy = computeBandEnergy(fft, 101, 200);

        // --- 1. Auto-Intensity & Global Gain Calculation ---
        float frameEnergy = (subBassEnergy + bassEnergy + midEnergy + highEnergy) / 4f;
        // Update slowly moving average used for normalization (Auto-gain)
        if (frameEnergy > 0.01f) {
            globalEnergyAvg = globalEnergyAvg * (1 - GAIN_ADJUST_SPEED) + frameEnergy * GAIN_ADJUST_SPEED;
        }
        // Protect against divide by zero or extreme quiet
        float safeAvg = Math.max(0.08f, globalEnergyAvg); // Increase noise floor for auto-gain
        // Calculate dynamic multiplier: Boost quiet songs, attenuate loud ones slightly
        // If song is quiet (safeAvg 0.08), boost = 0.16/0.08 = 2.0x
        // If song is loud (safeAvg 0.4), boost = 0.16/0.4 = 0.4x
        float dynamicGain = 0.16f / safeAvg;
        // Clamp gain to reasonable limits (0.5x to 3.0x) - Restored higher boost
        dynamicGain = Math.max(0.6f, Math.min(3.0f, dynamicGain));

        // Apply Multipliers
        // NOTE: reduced bassBoost influence on energy calculation to prevent clipping
        subBassEnergy *= bassBoostMultiplier * eqBassMultiplier * loudnessMultiplier * dynamicGain;
        bassEnergy *= (bassBoostMultiplier * 0.8f) * dynamicGain; // Increased bass presence
        midEnergy *= eqMidMultiplier * loudnessMultiplier * dynamicGain;
        float vocalEnergy = (bassEnergy * 0.4f + midEnergy * 0.6f); // Approximation of vocal band

        // Update history
        updateHistory(subBassHistory, subBassEnergy);
        updateHistory(bassHistory, bassEnergy);
        updateHistory(midHistory, midEnergy);
        historyIndex++;

        // Calculate averages
        float avgSubBass = fastAverage(subBassHistory);
        float avgMid = fastAverage(midHistory);

        // Update peaks
        peakBassLevel = Math.max(subBassEnergy, peakBassLevel * 0.985f);
        peakMidLevel = Math.max(midEnergy, peakMidLevel * 0.985f);

        long now = System.currentTimeMillis();
        long adjustedKickInterval = (long) (MIN_KICK_INTERVAL / playbackSpeedFactor);
        long adjustedSnareInterval = (long) (MIN_SNARE_INTERVAL / playbackSpeedFactor);

        // ============================================================
        // CONTINUOUS HAPTIC (Merged Sub-Bass + Vocal Texture)
        // ============================================================

        float combinedHapticSignal;

        // Peak tracking for normalization
        float dynamicPeak = Math.max(peakBassLevel, 0.1f);

        // Logic: specific mix based on content
        if (subBassEnergy > dynamicPeak * 0.4f) {
            // Bass Heavy Moment: Bass dominates
            combinedHapticSignal = subBassEnergy;
        } else {
            // Quiet/Vocal Moment: Blend Vocal/Mid energy
            // Restored sensitivity but kept gate high
            combinedHapticSignal = subBassEnergy + (vocalEnergy * 0.45f);
        }

        // Normalize
        float normalizedSignal = Math.min(1f, combinedHapticSignal / dynamicPeak);

        // [AGGRESSIVE STRICT GATE] Increased to 0.55f
        // Only trigger continuous layer if there is VERY SIGNIFICANT bass content.
        // This effectively kills "continuous" buzzing for everything except heavy
        // drops.
        if (normalizedSignal < 0.55f) {
            normalizedSignal = 0f;
            // Immediate stop if continuous was playing but now signal is weak
            if (isPlayingContinuous) {
                stopContinuousHaptic();
            }
        }

        // Target Calculation
        // Steep power curve (3.0) ensures only high-energy signals produce strong
        // vibration
        targetBassIntensity = (float) Math.pow(normalizedSignal, 3.0f);
        targetBassIntensity = Math.min(1f, Math.max(0f, targetBassIntensity));

        // Smooth output
        if (targetBassIntensity > currentBassIntensity) {
            currentBassIntensity = currentBassIntensity * (1 - ENVELOPE_ATTACK) + targetBassIntensity * ENVELOPE_ATTACK;
        } else {
            // Faster decay to stop vibrations quickly
            currentBassIntensity = currentBassIntensity * (1 - 0.2f) + targetBassIntensity * 0.2f;
        }

        // Update continuous haptic
        // Only update if intensity is > 0 OR we need to send a stop command (taken care
        // of above)
        if ((now - lastContinuousUpdate) >= CONTINUOUS_UPDATE_MS) {
            lastContinuousUpdate = now;
            if (currentBassIntensity > 0.05f) {
                updateContinuousBassHaptic(currentBassIntensity);
            }
        }

        // ============================================================
        // TRANSIENT DETECTION (Sharp Peaks)
        // ============================================================

        // Kick detection
        float subBassRise = subBassEnergy - avgSubBass;
        boolean isKick = subBassEnergy > avgSubBass * 1.5f &&
                subBassRise > 0.05f &&
                (now - lastKickTime) > adjustedKickInterval;

        if (isKick) {
            updateBPM(now);
            lastKickTime = now;
            float kickIntensity = computeIntensity(subBassEnergy, avgSubBass, peakBassLevel);
            // Dynamic gain ensures we don't need to hardcode specific thresholds as much
            triggerTransientHaptic(Math.max(0.65f, kickIntensity), HapticType.KICK);
        }

        // Snare detection
        float midRise = midEnergy - lastMidEnergy;
        boolean isSnare = midEnergy > avgMid * 1.8f &&
                midRise > (avgMid * 0.4f) &&
                midRise > 0.08f &&
                subBassEnergy < avgSubBass * 1.2f &&
                (now - lastSnareTime) > adjustedSnareInterval;

        if (isSnare) {
            lastSnareTime = now;
            float snareIntensity = computeIntensity(midEnergy, avgMid, peakMidLevel);
            triggerTransientHaptic(Math.min(0.8f, snareIntensity), HapticType.SNARE);
        }

        // Store for next frame
        lastBassEnergy = bassEnergy;
        lastMidEnergy = midEnergy;
    }

    private float computeBandEnergy(byte[] fft, int startBin, int endBin) {
        float sum = 0f;
        int count = 0;
        int maxBin = Math.min(endBin, (fft.length - 2) / 2);

        for (int i = startBin; i < maxBin; i++) {
            int idx = i * 2;
            if (idx + 1 >= fft.length)
                break;

            float real = (float) fft[idx];
            float imag = (float) fft[idx + 1];

            // Apply Hamming window
            int windowIdx = Math.min(i, hammingWindow.length - 1);
            float window = hammingWindow[windowIdx];

            float magnitude = (float) Math.sqrt(real * real + imag * imag) * window;
            sum += magnitude;
            count++;
        }

        return count > 0 ? sum / count : 0f;
    }

    private void updateHistory(float[] history, float value) {
        history[historyIndex % history.length] = value;
    }

    private float fastAverage(float[] array) {
        float sum = 0f;
        for (float value : array)
            sum += value;
        return sum / array.length;
    }

    private float computeIntensity(float energy, float avg, float peak) {
        if (peak <= avg)
            return 0f;
        float rawIntensity = (energy - avg) / (peak - avg + 0.01f);
        return Math.min(1f, (float) Math.pow(rawIntensity, 0.75) * 1.15f);
    }

    private void updateBPM(long now) {
        if (lastKickTime > 0) {
            long interval = now - lastKickTime;
            if (interval > 250 && interval < 1500) {
                beatIntervals[beatIntervalIndex] = interval;
                beatIntervalIndex = (beatIntervalIndex + 1) % beatIntervals.length;
                estimatedBPM = 60000f / fastAverage(beatIntervals);
            }
        }
    }

    // ============================================================
    // HAPTIC TRIGGERING SYSTEM
    // ============================================================

    // Priority System: Prevent continuous haptics from cutting off kicks
    private long lastTriggeredTransientTime = 0;
    // [AGGRESSIVE SYNC] 200ms Side-chain window
    private static final long TRANSIENT_PROTECTION_WINDOW_MS = 200;

    /**
     * Continuous bass-following haptic using repeating pattern
     */
    private void updateContinuousBassHaptic(float intensity) {
        if (!isHapticsEnabled)
            return;

        long now = System.currentTimeMillis();
        // [SYNC FIX] Absolute silence during Kick window
        if (now - lastTriggeredTransientTime < TRANSIENT_PROTECTION_WINDOW_MS) {
            return;
        }

        Vibrator vibrator = getVibrator();
        if (vibrator == null || !vibrator.hasVibrator())
            return;

        // [OPTIMIZATION] Skip tiny updates to reduce IPC calls
        if (Math.abs(intensity - currentBassIntensity) < 0.05f && isPlayingContinuous) {
            return;
        }

        intensity = Math.min(1f, Math.max(0f, intensity));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAmplitudeControl) {
            // [POWER FIX] LINEAR curve for raw power
            float curvedIntensity = intensity;

            float userMultiplier = vibrationStrengthMultiplier;

            // [STRENGTH FIX] High floor for Pixel
            int baseAmplitude = 90;
            int maxRange = (int) (165 * userMultiplier);
            int amplitude = baseAmplitude + (int) (curvedIntensity * maxRange);
            amplitude = Math.max(90, Math.min(255, amplitude));

            try {
                // Short 45ms burst creates a granular texture rather than a smooth hum
                VibrationEffect effect = VibrationEffect.createOneShot(45, amplitude);
                vibrator.vibrate(effect);
                isPlayingContinuous = true;
            } catch (Exception e) {
                // Ignore
            }
        } else {
            // Legacy
            long duration = (long) (30 + vibrationStrengthMultiplier * 30);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
                isPlayingContinuous = true;
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void stopContinuousHaptic() {
        if (!isPlayingContinuous)
            return;

        Vibrator vibrator = getVibrator();
        if (vibrator != null) {
            try {
                vibrator.cancel();
                isPlayingContinuous = false;
            } catch (Exception e) {
                Log.w(TAG, "Stop continuous error: " + e.getMessage());
            }
        }
    }

    /**
     * Transient haptic for strong beats
     */
    private void triggerTransientHaptic(float intensity, HapticType type) {
        long now = System.currentTimeMillis();
        // Prevent machine-gunning
        if (type == HapticType.KICK && now - lastTriggeredKickTime < 110) {
            return;
        }
        if (type == HapticType.KICK)
            lastTriggeredKickTime = now;

        // [SYNC FIX] Mark time
        lastTriggeredTransientTime = now;

        if (!isHapticsEnabled)
            return;

        // [CRITICAL SYNC] CANCEL ANY EXISTING VIBRATION BEFORE KICK
        // This stops the motor spin-down/blur from previous continuous haptics
        // ensuring the kick hits from absolute zero (Punchy).
        stopContinuousHaptic();

        switch (deviceMode) {
            case DUAL_MODE:
                // Prioritize Vibration Service for raw power
                performVibrationTransient(type, intensity);
                // View haptic as secondary texture (on UI thread, might be skipped if janking)
                if (attachedView != null && attachedView.isAttachedToWindow()) {
                    // Run on UI thread
                    attachedView.post(() -> performAndroidHaptic(attachedView, type, intensity));
                }
                break;

            case HAPTIC_ONLY:
                if (attachedView != null && attachedView.isAttachedToWindow()) {
                    attachedView.post(() -> performAndroidHaptic(attachedView, type, intensity * 1.5f));
                }
                break;

            case VIBRATION_ONLY:
            default:
                performVibrationTransient(type, intensity);
                break;
        }
    }

    private void performAndroidHaptic(View view, HapticType type, float intensity) {
        if (!view.isHapticFeedbackEnabled())
            view.setHapticFeedbackEnabled(true);
        try {
            int constant = HapticFeedbackConstants.KEYBOARD_TAP;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (type == HapticType.KICK)
                    constant = HapticFeedbackConstants.LONG_PRESS; // Heavy
                else if (type == HapticType.SNARE)
                    constant = HapticFeedbackConstants.CONFIRM; // Distinct
            }
            view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception e) {
        }
    }

    /**
     * Vibration for transient beats
     */
    private void performVibrationTransient(HapticType type, float intensity) {
        Vibrator vibrator = getVibrator();
        if (vibrator == null || !vibrator.hasVibrator())
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAmplitudeControl) {
            int amplitude, duration;

            switch (type) {
                case KICK:
                    // [MAX PUNCH]
                    // 100ms is a solid "Thump"
                    duration = 100;
                    amplitude = 255;
                    break;

                case SNARE:
                    duration = 60;
                    amplitude = 255;
                    break;

                case HIHAT:
                    duration = 25;
                    amplitude = 180;
                    break;

                default:
                    duration = 50;
                    amplitude = 220;
            }

            try {
                // OneShot
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
                // We do NOT set isPlayingContinuous = true here, transients are separate
            } catch (Exception e) {
                Log.w(TAG, "Vibration transient error: " + e.getMessage());
            }
        } else {
            // Legacy
            int duration = type == HapticType.KICK ? 60 : 30;
            vibrator.vibrate(duration);
        }
    }

    private void stopAllHaptics() {
        stopContinuousHaptic();
        currentBassIntensity = 0f;
        targetBassIntensity = 0f;
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    public float getEstimatedBPM() {
        return estimatedBPM;
    }

    public String getDeviceMode() {
        return deviceMode != null ? deviceMode.toString() : "UNKNOWN";
    }

    public float getCurrentIntensity() {
        return currentBassIntensity;
    }

    public void detachView() {
        attachedView = null;
    }
}