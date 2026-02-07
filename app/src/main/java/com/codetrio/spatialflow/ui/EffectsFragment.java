package com.codetrio.spatialflow.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.codetrio.spatialflow.databinding.FragmentEffectsBinding;
import com.codetrio.spatialflow.service.AudioPlaybackService;
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel;

import com.codetrio.spatialflow.R;
import com.google.android.material.slider.Slider;

import java.util.Locale;

public class EffectsFragment extends Fragment {

    private static final String TAG = "EffectsFragment";

    private FragmentEffectsBinding binding;
    private PlayerSharedViewModel viewModel;
    private AudioPlaybackService service;

    // Guard to ignore programmatic switch updates
    private boolean ignoreSwitchEvents = false;

    // Flag to prevent redundant service calls during initialization
    private boolean isInitializing = true;

    // Track last song ID for proper song change detection
    private long lastSongId = -1;

    // ============================================================================================
    // LIFECYCLE METHODS
    // ============================================================================================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentEffectsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Start initialization - observers will only update UI, not service
        isInitializing = true;

        viewModel = new ViewModelProvider(requireActivity()).get(PlayerSharedViewModel.class);

        setupObservers();
        setupListeners();

        // End initialization after a short delay to ensure all observers have fired
        view.post(() -> {
            isInitializing = false;
            Log.d(TAG, "EffectsFragment initialization complete");
        });

        // ---------------------------
        // SCROLL-AWARE BOTTOM NAV
        // Works with both ScrollView (portrait) and NestedScrollView (landscape)
        // ---------------------------
        View scrollView = view.findViewById(R.id.effectsScroll);
        if (scrollView != null) {
            scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (getActivity() instanceof com.codetrio.spatialflow.MainActivity) {
                    com.codetrio.spatialflow.MainActivity activity = (com.codetrio.spatialflow.MainActivity) getActivity();
                    int dy = scrollY - oldScrollY;
                    if (dy > 10) {
                        activity.hideBottomNavWithAnimation();
                    } else if (dy < -10) {
                        activity.showBottomNavWithAnimation();
                    }
                }
            });
        }

        Log.d(TAG, "EffectsFragment initialized with ViewBinding");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ============================================================================================
    // SETUP METHODS
    // ============================================================================================

    private void setupObservers() {
        // Service connection
        viewModel.getAudioService().observe(getViewLifecycleOwner(), audioService -> {
            this.service = audioService;
            Log.d(TAG, "Service connected: " + (audioService != null));

            if (service != null) {
                applyAllEffectsToService();
            }
        });

        // Effects refresh trigger - called when song changes to re-apply effects
        viewModel.getEffectsRefreshTrigger().observe(getViewLifecycleOwner(), trigger -> {
            if (trigger != null && service != null && !isInitializing) {
                Log.d(TAG, "Effects refresh triggered by song change");
                applyAllEffectsToService();
            }
        });

        // Song change - Reset 8D toggle when a DIFFERENT song is selected
        viewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                long currentSongId = song.id;

                // Only reset if this is actually a different song (not first load or same song)
                if (lastSongId != -1 && currentSongId != lastSongId) {
                    // Turn off 8D when song changes
                    if (Boolean.TRUE.equals(viewModel.getIs8DEnabled().getValue())) {
                        viewModel.set8DEnabled(false);
                        if (service != null) {
                            service.set8DEnabled(false);
                        }
                        Log.d(TAG, "8D auto-disabled on song change (was: " + lastSongId + ", now: " + currentSongId
                                + ")");
                    }

                    // Refresh all effects EXCEPT 8D when song changes
                    refreshEffectsExcluding8D();
                    Log.d(TAG, "Effects refreshed on song change");
                }

                // Update last song ID
                lastSongId = currentSongId;
            }
        });

        // 8D Audio
        viewModel.getIs8DEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null) {
                ignoreSwitchEvents = true;
                if (binding != null)
                    binding.switch8D.setChecked(enabled);
                ignoreSwitchEvents = false;

                // Skip service call during initialization - applyAllEffectsToService handles it
                if (!isInitializing && service != null) {
                    service.set8DEnabled(enabled);
                    Log.d(TAG, "8D state: " + enabled);
                }
            }
        });

        // Bass Boost
        viewModel.getIsBassEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null && binding != null) {
                binding.switchBass.setChecked(enabled);
                binding.sliderBassBoost.setEnabled(enabled);
                // Skip service call during initialization
                if (!isInitializing && service != null) {
                    service.setBassEnabled(enabled);
                }
            }
        });

        viewModel.getBassBoost().observe(getViewLifecycleOwner(), boost -> {
            if (boost != null && binding != null) {
                binding.sliderBassBoost.setValue(boost);
                binding.tvBassBoostValue.setText(String.format(Locale.getDefault(), "%+d dB", boost));
                // Skip service call during initialization
                if (!isInitializing && service != null
                        && Boolean.TRUE.equals(viewModel.getIsBassEnabled().getValue())) {
                    service.setBassBoost(boost);
                }
            }
        });

        // Equalizer
        viewModel.getIsEqualizerEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null && binding != null) {
                binding.switchEqualizer.setChecked(enabled);
                enableEqualizerSliders(enabled);
                // Skip service call during initialization
                if (!isInitializing && service != null) {
                    service.setEqualizerEnabled(enabled);
                }
            }
        });

        // EQ Bands
        viewModel.getEqBand1().observe(getViewLifecycleOwner(), gain -> updateEqBand(0, gain,
                binding != null ? binding.sliderBand1 : null, binding != null ? binding.tvBand1Value : null));
        viewModel.getEqBand2().observe(getViewLifecycleOwner(), gain -> updateEqBand(1, gain,
                binding != null ? binding.sliderBand2 : null, binding != null ? binding.tvBand2Value : null));
        viewModel.getEqBand3().observe(getViewLifecycleOwner(), gain -> updateEqBand(2, gain,
                binding != null ? binding.sliderBand3 : null, binding != null ? binding.tvBand3Value : null));
        viewModel.getEqBand4().observe(getViewLifecycleOwner(), gain -> updateEqBand(3, gain,
                binding != null ? binding.sliderBand4 : null, binding != null ? binding.tvBand4Value : null));
        viewModel.getEqBand5().observe(getViewLifecycleOwner(), gain -> updateEqBand(4, gain,
                binding != null ? binding.sliderBand5 : null, binding != null ? binding.tvBand5Value : null));

        // Loudness
        viewModel.getIsLoudnessEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null && binding != null) {
                binding.switchLoudness.setChecked(enabled);
                binding.sliderLoudness.setEnabled(enabled);
                // Skip service call during initialization
                if (!isInitializing && service != null) {
                    service.setLoudnessEnabled(enabled);
                }
            }
        });

        viewModel.getLoudnessGain().observe(getViewLifecycleOwner(), gain -> {
            if (gain != null && binding != null) {
                binding.sliderLoudness.setValue(gain);
                binding.tvLoudnessValue.setText(String.format(Locale.getDefault(), "+%d dB", gain));
                // Skip service call during initialization
                if (!isInitializing && service != null
                        && Boolean.TRUE.equals(viewModel.getIsLoudnessEnabled().getValue())) {
                    service.setLoudnessGain(gain);
                }
            }
        });

        // Balance
        viewModel.getBalance().observe(getViewLifecycleOwner(), balance -> {
            if (balance != null && binding != null) {
                binding.sliderBalance.setValue(balance);
                updateBalanceLabel(balance);
                // Skip service call during initialization
                if (!isInitializing && service != null && binding.switchBalance.isChecked()) {
                    service.setBalance(balance);
                }
            }
        });

        // Speed
        viewModel.getPlaybackSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed != null && binding != null) {
                binding.sliderSpeed.setValue(speed);
                binding.tvSpeedValue.setText(String.format(Locale.getDefault(), "%.2fx", speed));
                // Skip service call during initialization and only if speed toggle is enabled
                if (!isInitializing && service != null && binding.switchSpeed.isChecked()) {
                    service.setPlaybackSpeed(speed);
                }
            }
        });

        // Processing Status
        viewModel.getIsProcessing().observe(getViewLifecycleOwner(), isProcessing -> {
            if (binding == null)
                return;
            if (isProcessing != null) {
                binding.cardProcessing.setVisibility(isProcessing ? View.VISIBLE : View.GONE);

                if (isProcessing) {
                    disableControls();
                    // Start wavy animation for processing
                    binding.progressBar.setWaveSpeed(30);
                    binding.progressBar.setWaveAmplitude(4);
                } else {
                    enableControls();
                    // Stop wavy animation
                    binding.progressBar.setWaveSpeed(0);
                    binding.progressBar.setWaveAmplitude(0);
                    // Only refresh effects if not initializing (actual processing just finished)
                    if (!isInitializing) {
                        refreshAllEffects();
                    }
                }

                Log.d(TAG, "Processing: " + isProcessing);
            }
        });

        viewModel.getProcessingProgress().observe(getViewLifecycleOwner(), progress -> {
            if (binding == null)
                return;
            if (progress != null && progress > 0) {
                binding.progressBar.setIndeterminate(false);
                binding.progressBar.setProgress(progress);
                binding.tvProcessingStatus.setText(String.format(Locale.getDefault(),
                        "Processing 8D Audio: %d%%", progress));

                // Increase wave speed as progress increases
                if (progress < 100) {
                    int waveSpeed = 20 + (progress / 5); // 20-40dp speed
                    binding.progressBar.setWaveSpeed(waveSpeed);
                }

                if (progress >= 100) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (binding != null) {
                            binding.cardProcessing.setVisibility(View.GONE);
                            binding.progressBar.setWaveSpeed(0);
                            binding.progressBar.setWaveAmplitude(0);
                            refreshAllEffects();
                        }
                    }, 1500);
                }
            }
        });

        // Effects refresh trigger (when audio effects are re-initialized)
        final boolean[] isFirstTrigger = { true };
        viewModel.getEffectsRefreshTrigger().observe(getViewLifecycleOwner(), triggered -> {
            // Skip the initial observer registration call
            if (isFirstTrigger[0]) {
                isFirstTrigger[0] = false;
                return;
            }

            if (binding != null && service != null) {
                Log.d(TAG, "Effects refresh triggered - re-applying all effects");
                // Don't reset 8D here - that's handled separately in song change observer
                refreshAllEffects();
            }
        });
    }

    private void setupListeners() {
        if (binding == null)
            return;

        // ===== 8D AUDIO (FFMPEG PROCESSING) =====
        binding.switch8D.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (ignoreSwitchEvents)
                return;

            Log.d(TAG, "8D switch toggled (user): " + isChecked);

            viewModel.set8DEnabled(isChecked);
            if (service != null) {
                service.set8DEnabled(isChecked);
            }

            viewModel.triggerReprocessing();

            // Refresh all effects when 8D is turned ON
            if (isChecked) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    refreshAllEffects();
                    Log.d(TAG, "All effects refreshed after 8D enabled");
                }, 300);
            }
        });

        // ===== BASS BOOST (REAL-TIME ANDROID AUDIOEFFECT) =====
        binding.switchBass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setBassEnabled(isChecked);
            binding.sliderBassBoost.setEnabled(isChecked);
            if (service != null) {
                service.setBassEnabled(isChecked);
            }
        });

        binding.sliderBassBoost.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int dbValue = (int) value;
                binding.tvBassBoostValue.setText(String.format(Locale.getDefault(), "%+d dB", dbValue));
                viewModel.setBassBoost(dbValue);
                if (service != null && binding.switchBass.isChecked()) {
                    service.setBassBoost(dbValue);
                }
            }
        });

        // ===== EQUALIZER (REAL-TIME ANDROID AUDIOEFFECT) =====
        binding.switchEqualizer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setEqualizerEnabled(isChecked);
            enableEqualizerSliders(isChecked);
            if (service != null) {
                service.setEqualizerEnabled(isChecked);
            }
        });
        setupBandSlider(binding.sliderBand1, binding.tvBand1Value, 0);
        setupBandSlider(binding.sliderBand2, binding.tvBand2Value, 1);
        setupBandSlider(binding.sliderBand3, binding.tvBand3Value, 2);
        setupBandSlider(binding.sliderBand4, binding.tvBand4Value, 3);
        setupBandSlider(binding.sliderBand5, binding.tvBand5Value, 4);

        // ===== LOUDNESS (REAL-TIME ANDROID AUDIOEFFECT) =====
        binding.switchLoudness.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setLoudnessEnabled(isChecked);
            binding.sliderLoudness.setEnabled(isChecked);
            if (service != null) {
                service.setLoudnessEnabled(isChecked);
            }
        });

        binding.sliderLoudness.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int gainValue = (int) value;
                binding.tvLoudnessValue.setText(String.format(Locale.getDefault(), "+%d dB", gainValue));
                viewModel.setLoudnessGain(gainValue);
                if (service != null && binding.switchLoudness.isChecked()) {
                    service.setLoudnessGain(gainValue);
                }
            }
        });

        // ===== BALANCE (REAL-TIME VIA MEDIAPLAYER) =====
        binding.switchBalance.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.sliderBalance.setEnabled(isChecked);
            if (!isChecked) {
                binding.sliderBalance.setValue(0);
                binding.tvBalanceValue.setText("Center");
                viewModel.setBalance(0);
                if (service != null) {
                    service.setBalance(0);
                }
            }
            Log.d(TAG, "Balance toggle: " + (isChecked ? "ON" : "OFF"));
        });

        binding.sliderBalance.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int balanceValue = (int) value;
                updateBalanceLabel(balanceValue);
                viewModel.setBalance(balanceValue);
                if (service != null && binding.switchBalance.isChecked()) {
                    service.setBalance(balanceValue);
                }
            }
        });

        // ===== PLAYBACK SPEED (REAL-TIME VIA PLAYBACKPARAMS) =====
        binding.switchSpeed.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.sliderSpeed.setEnabled(isChecked);

            if (!isChecked) {
                // Reset to 1.0x when disabled
                binding.sliderSpeed.setValue(1.0f);
                binding.tvSpeedValue.setText("1.00x");
                viewModel.setPlaybackSpeed(1.0f);
                if (service != null) {
                    service.setPlaybackSpeed(1.0f);
                }
            }
            Log.d(TAG, "Speed toggle: " + (isChecked ? "ON" : "OFF"));
        });

        binding.sliderSpeed.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                binding.tvSpeedValue.setText(String.format(Locale.getDefault(), "%.2fx", value));
                viewModel.setPlaybackSpeed(value);
                if (service != null && binding.switchSpeed.isChecked()) {
                    service.setPlaybackSpeed(value);
                }
            }
        });
    }

    private void setupBandSlider(Slider slider, TextView valueView, int bandIndex) {
        if (slider == null)
            return;

        // Prevent parent ScrollView from intercepting touch events when dragging
        // vertical sliders
        slider.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                case android.view.MotionEvent.ACTION_MOVE:
                    // Disable parent scroll interception
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // Re-enable parent scroll interception
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false; // Let slider handle the touch event
        });

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                int dbValue = (int) value;
                if (valueView != null)
                    valueView.setText(String.format(Locale.getDefault(), "%+d dB", dbValue));

                switch (bandIndex) {
                    case 0:
                        viewModel.setEqBand1(dbValue);
                        break;
                    case 1:
                        viewModel.setEqBand2(dbValue);
                        break;
                    case 2:
                        viewModel.setEqBand3(dbValue);
                        break;
                    case 3:
                        viewModel.setEqBand4(dbValue);
                        break;
                    case 4:
                        viewModel.setEqBand5(dbValue);
                        break;
                }

                if (service != null && binding != null && binding.switchEqualizer.isChecked()) {
                    service.setEqBandGain(bandIndex, dbValue);
                }
            }
        });
    }

    // ============================================================================================
    // UI HELPER METHODS
    // ============================================================================================

    private void updateEqBand(int bandIndex, Integer gain, Slider slider, TextView valueText) {
        if (slider == null || valueText == null)
            return;
        if (gain != null) {
            slider.setValue(gain);
            valueText.setText(String.format(Locale.getDefault(), "%+d dB", gain));
            // Skip service call during initialization
            if (!isInitializing && service != null
                    && Boolean.TRUE.equals(viewModel.getIsEqualizerEnabled().getValue())) {
                service.setEqBandGain(bandIndex, gain);
            }
        }
    }

    private void updateBalanceLabel(int balance) {
        if (binding == null)
            return;
        if (balance == 0) {
            binding.tvBalanceValue.setText("Center");
        } else if (balance < 0) {
            binding.tvBalanceValue.setText("L" + Math.abs(balance));
        } else {
            binding.tvBalanceValue.setText("R" + balance);
        }
    }

    private void enableEqualizerSliders(boolean enabled) {
        if (binding == null)
            return;
        binding.sliderBand1.setEnabled(enabled);
        binding.sliderBand2.setEnabled(enabled);
        binding.sliderBand3.setEnabled(enabled);
        binding.sliderBand4.setEnabled(enabled);
        binding.sliderBand5.setEnabled(enabled);
    }

    private void disableControls() {
        if (binding == null)
            return;
        binding.switch8D.setEnabled(false);
        binding.switchBass.setEnabled(false);
        binding.sliderBassBoost.setEnabled(false);
        binding.switchEqualizer.setEnabled(false);
        enableEqualizerSliders(false);
        binding.switchLoudness.setEnabled(false);
        binding.sliderLoudness.setEnabled(false);
        binding.switchBalance.setEnabled(false);
        binding.sliderBalance.setEnabled(false);
        binding.switchSpeed.setEnabled(false);
        binding.sliderSpeed.setEnabled(false);

    }

    private void enableControls() {
        if (binding == null)
            return;
        binding.switch8D.setEnabled(true);
        binding.switchBass.setEnabled(true);

        Boolean bassEnabled = viewModel.getIsBassEnabled().getValue();
        binding.sliderBassBoost.setEnabled(bassEnabled != null && bassEnabled);

        binding.switchEqualizer.setEnabled(true);
        Boolean eqEnabled = viewModel.getIsEqualizerEnabled().getValue();
        enableEqualizerSliders(eqEnabled != null && eqEnabled);

        binding.switchLoudness.setEnabled(true);
        Boolean loudnessEnabled = viewModel.getIsLoudnessEnabled().getValue();
        binding.sliderLoudness.setEnabled(loudnessEnabled != null && loudnessEnabled);

        binding.switchBalance.setEnabled(true);
        binding.sliderBalance.setEnabled(binding.switchBalance.isChecked());

        binding.switchSpeed.setEnabled(true);
        binding.sliderSpeed.setEnabled(binding.switchSpeed.isChecked());

    }

    // ============================================================================================
    // EFFECT LOGIC METHODS
    // ============================================================================================

    private void refreshAllEffects() {
        if (service == null || binding == null)
            return;

        Log.d(TAG, "Refreshing all effects after 8D processing");

        applyAllEffectsToService();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (binding == null)
                return;

            // Refresh Bass
            Boolean bassEnabled = viewModel.getIsBassEnabled().getValue();
            if (bassEnabled != null && bassEnabled) {
                Integer bassBoost = viewModel.getBassBoost().getValue();
                if (bassBoost != null) {
                    binding.sliderBassBoost.setValue(bassBoost);
                    binding.tvBassBoostValue.setText(String.format(Locale.getDefault(), "%+d dB", bassBoost));
                }
            }

            // Refresh EQ
            Boolean eqEnabled = viewModel.getIsEqualizerEnabled().getValue();
            if (eqEnabled != null && eqEnabled) {
                Integer band1 = viewModel.getEqBand1().getValue();
                if (band1 != null)
                    binding.sliderBand1.setValue(band1);

                Integer band2 = viewModel.getEqBand2().getValue();
                if (band2 != null)
                    binding.sliderBand2.setValue(band2);

                Integer band3 = viewModel.getEqBand3().getValue();
                if (band3 != null)
                    binding.sliderBand3.setValue(band3);

                Integer band4 = viewModel.getEqBand4().getValue();
                if (band4 != null)
                    binding.sliderBand4.setValue(band4);

                Integer band5 = viewModel.getEqBand5().getValue();
                if (band5 != null)
                    binding.sliderBand5.setValue(band5);
            }

            // Refresh Loudness
            Boolean loudnessEnabled = viewModel.getIsLoudnessEnabled().getValue();
            if (loudnessEnabled != null && loudnessEnabled) {
                Integer loudnessGain = viewModel.getLoudnessGain().getValue();
                if (loudnessGain != null) {
                    binding.sliderLoudness.setValue(loudnessGain);
                    binding.tvLoudnessValue.setText(String.format(Locale.getDefault(), "+%d dB", loudnessGain));
                }
            }

            // Refresh Balance
            if (binding.switchBalance.isChecked()) {
                Integer balance = viewModel.getBalance().getValue();
                if (balance != null) {
                    binding.sliderBalance.setValue(balance);
                    updateBalanceLabel(balance);
                }
            }

            // Refresh Speed
            if (binding.switchSpeed.isChecked()) {
                Float speed = viewModel.getPlaybackSpeed().getValue();
                if (speed != null) {
                    binding.sliderSpeed.setValue(speed);
                    binding.tvSpeedValue.setText(String.format(Locale.getDefault(), "%.2fx", speed));
                }
            }

            Log.d(TAG, "All UI controls refreshed");
        }, 200);
    }

    /**
     * Refresh all effects EXCEPT 8D. Called on song change to re-apply
     * Bass, EQ, Loudness, Balance, Speed without modifying 8D state.
     * 
     * IMPORTANT: Equalizer is applied FIRST and given time to initialize
     * before other effects to prevent audio dropout.
     */
    private void refreshEffectsExcluding8D() {
        if (service == null)
            return;

        Log.d(TAG, "Refreshing effects (excluding 8D) on song change - EQ FIRST");

        // ===== STEP 1: Apply Equalizer FIRST (prevents audio dropout) =====
        Boolean eqEnabled = viewModel.getIsEqualizerEnabled().getValue();
        if (eqEnabled != null) {
            service.setEqualizerEnabled(eqEnabled);
            if (eqEnabled) {
                Integer band1 = viewModel.getEqBand1().getValue();
                if (band1 != null)
                    service.setEqBandGain(0, band1);
                Integer band2 = viewModel.getEqBand2().getValue();
                if (band2 != null)
                    service.setEqBandGain(1, band2);
                Integer band3 = viewModel.getEqBand3().getValue();
                if (band3 != null)
                    service.setEqBandGain(2, band3);
                Integer band4 = viewModel.getEqBand4().getValue();
                if (band4 != null)
                    service.setEqBandGain(3, band4);
                Integer band5 = viewModel.getEqBand5().getValue();
                if (band5 != null)
                    service.setEqBandGain(4, band5);
            }
            Log.d(TAG, "EQ applied first, waiting before other effects...");
        }

        // ===== STEP 2: Apply other effects AFTER EQ with small delay =====
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (service == null)
                return;

            // Apply Bass
            Boolean bassEnabled = viewModel.getIsBassEnabled().getValue();
            if (bassEnabled != null) {
                service.setBassEnabled(bassEnabled);
                if (bassEnabled) {
                    Integer bassBoost = viewModel.getBassBoost().getValue();
                    if (bassBoost != null) {
                        service.setBassBoost(bassBoost);
                    }
                }
            }

            // Apply Loudness
            Boolean loudnessEnabled = viewModel.getIsLoudnessEnabled().getValue();
            if (loudnessEnabled != null) {
                service.setLoudnessEnabled(loudnessEnabled);
                if (loudnessEnabled) {
                    Integer loudnessGain = viewModel.getLoudnessGain().getValue();
                    if (loudnessGain != null) {
                        service.setLoudnessGain(loudnessGain);
                    }
                }
            }

            // Apply Balance
            if (binding != null && binding.switchBalance.isChecked()) {
                Integer balance = viewModel.getBalance().getValue();
                if (balance != null) {
                    service.setBalance(balance);
                }
            }

            // Apply Speed
            if (binding != null && binding.switchSpeed.isChecked()) {
                Float speed = viewModel.getPlaybackSpeed().getValue();
                if (speed != null) {
                    service.setPlaybackSpeed(speed);
                }
            }

            Log.d(TAG, "All effects (excluding 8D) applied after EQ");
        }, 25); // 25ms delay - optimized for faster response
    }

    /**
     * Apply all effects to service. Equalizer is applied FIRST to prevent
     * audio dropout, then other effects follow after a short delay.
     */
    private void applyAllEffectsToService() {
        if (service == null)
            return;

        // 8D flag (doesn't affect audio processing directly)
        Boolean is8D = viewModel.getIs8DEnabled().getValue();
        if (is8D != null) {
            service.set8DEnabled(is8D);
        }

        // ===== STEP 1: Apply Equalizer FIRST (prevents audio dropout) =====
        Boolean eqEnabled = viewModel.getIsEqualizerEnabled().getValue();
        if (eqEnabled != null) {
            service.setEqualizerEnabled(eqEnabled);
            if (eqEnabled) {
                Integer band1 = viewModel.getEqBand1().getValue();
                if (band1 != null)
                    service.setEqBandGain(0, band1);

                Integer band2 = viewModel.getEqBand2().getValue();
                if (band2 != null)
                    service.setEqBandGain(1, band2);

                Integer band3 = viewModel.getEqBand3().getValue();
                if (band3 != null)
                    service.setEqBandGain(2, band3);

                Integer band4 = viewModel.getEqBand4().getValue();
                if (band4 != null)
                    service.setEqBandGain(3, band4);

                Integer band5 = viewModel.getEqBand5().getValue();
                if (band5 != null)
                    service.setEqBandGain(4, band5);
            }
            Log.d(TAG, "EQ applied first in applyAllEffects");
        }

        // ===== STEP 2: Apply other effects AFTER EQ with small delay =====
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (service == null)
                return;

            // Apply Bass
            Boolean bassEnabled = viewModel.getIsBassEnabled().getValue();
            if (bassEnabled != null) {
                service.setBassEnabled(bassEnabled);
                if (bassEnabled) {
                    Integer bassBoost = viewModel.getBassBoost().getValue();
                    if (bassBoost != null) {
                        service.setBassBoost(bassBoost);
                    }
                }
            }

            // Apply Loudness
            Boolean loudnessEnabled = viewModel.getIsLoudnessEnabled().getValue();
            if (loudnessEnabled != null) {
                service.setLoudnessEnabled(loudnessEnabled);
                if (loudnessEnabled) {
                    Integer loudnessGain = viewModel.getLoudnessGain().getValue();
                    if (loudnessGain != null) {
                        service.setLoudnessGain(loudnessGain);
                    }
                }
            }

            // Apply Balance
            if (binding != null && binding.switchBalance.isChecked()) {
                Integer balance = viewModel.getBalance().getValue();
                if (balance != null) {
                    service.setBalance(balance);
                }
            }

            // Apply Speed
            if (binding != null && binding.switchSpeed.isChecked()) {
                Float speed = viewModel.getPlaybackSpeed().getValue();
                if (speed != null) {
                    service.setPlaybackSpeed(speed);
                }
            }

            Log.d(TAG, "All effects applied to service (EQ-first order)");
        }, 25); // 25ms delay - optimized for faster response
    }
}
