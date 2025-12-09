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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.Locale;

public class EffectsFragment extends Fragment {

    private static final String TAG = "EffectsFragment";

    private FragmentEffectsBinding binding;
    private PlayerSharedViewModel viewModel;
    private AudioPlaybackService service;

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

        viewModel = new ViewModelProvider(requireActivity()).get(PlayerSharedViewModel.class);

        setupObservers();
        setupListeners();

        Log.d(TAG, "EffectsFragment initialized with ViewBinding");
    }

    private void setupObservers() {
        // Service connection
        viewModel.getAudioService().observe(getViewLifecycleOwner(), audioService -> {
            this.service = audioService;
            Log.d(TAG, "Service connected: " + (audioService != null));

            // Apply current settings to service when connected
            if (service != null) {
                applyAllEffectsToService();
            }
        });

        // 8D Audio
        viewModel.getIs8DEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null) {
                binding.switch8D.setChecked(enabled);
                if (service != null) {
                    service.set8DEnabled(enabled);
                }
                Log.d(TAG, "8D state: " + enabled);
            }
        });

        // Bass Boost
        viewModel.getIsBassEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null) {
                binding.switchBass.setChecked(enabled);
                binding.sliderBassBoost.setEnabled(enabled);
                if (service != null) {
                    service.setBassEnabled(enabled);
                }
            }
        });

        viewModel.getBassBoost().observe(getViewLifecycleOwner(), boost -> {
            if (boost != null) {
                binding.sliderBassBoost.setValue(boost);
                binding.tvBassBoostValue.setText(String.format(Locale.getDefault(), "%+d dB", boost));
                if (service != null && Boolean.TRUE.equals(viewModel.getIsBassEnabled().getValue())) {
                    service.setBassBoost(boost);
                }
            }
        });

        // Equalizer
        viewModel.getIsEqualizerEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null) {
                binding.switchEqualizer.setChecked(enabled);
                enableEqualizerSliders(enabled);
                if (service != null) {
                    service.setEqualizerEnabled(enabled);
                }
            }
        });

        // EQ Bands
        viewModel.getEqBand1().observe(getViewLifecycleOwner(), gain -> updateEqBand(0, gain, binding.sliderBand1, binding.tvBand1Value));
        viewModel.getEqBand2().observe(getViewLifecycleOwner(), gain -> updateEqBand(1, gain, binding.sliderBand2, binding.tvBand2Value));
        viewModel.getEqBand3().observe(getViewLifecycleOwner(), gain -> updateEqBand(2, gain, binding.sliderBand3, binding.tvBand3Value));
        viewModel.getEqBand4().observe(getViewLifecycleOwner(), gain -> updateEqBand(3, gain, binding.sliderBand4, binding.tvBand4Value));
        viewModel.getEqBand5().observe(getViewLifecycleOwner(), gain -> updateEqBand(4, gain, binding.sliderBand5, binding.tvBand5Value));

        // Loudness
        viewModel.getIsLoudnessEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null) {
                binding.switchLoudness.setChecked(enabled);
                binding.sliderLoudness.setEnabled(enabled);
                if (service != null) {
                    service.setLoudnessEnabled(enabled);
                }
            }
        });

        viewModel.getLoudnessGain().observe(getViewLifecycleOwner(), gain -> {
            if (gain != null) {
                binding.sliderLoudness.setValue(gain);
                binding.tvLoudnessValue.setText(String.format(Locale.getDefault(), "+%d dB", gain));
                if (service != null && Boolean.TRUE.equals(viewModel.getIsLoudnessEnabled().getValue())) {
                    service.setLoudnessGain(gain);
                }
            }
        });

        // Balance
        viewModel.getBalance().observe(getViewLifecycleOwner(), balance -> {
            if (balance != null) {
                binding.sliderBalance.setValue(balance);
                updateBalanceLabel(balance);
                if (service != null && binding.switchBalance.isChecked()) {
                    service.setBalance(balance);
                }
            }
        });

        // Speed
        viewModel.getPlaybackSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed != null) {
                binding.sliderSpeed.setValue(speed);
                binding.tvSpeedValue.setText(String.format(Locale.getDefault(), "%.2fx", speed));
                if (service != null) {
                    service.setPlaybackSpeed(speed);
                }
            }
        });

        // Processing Status
        viewModel.getIsProcessing().observe(getViewLifecycleOwner(), isProcessing -> {
            if (isProcessing != null) {
                binding.cardProcessing.setVisibility(isProcessing ? View.VISIBLE : View.GONE);

                if (isProcessing) {
                    disableControls();
                } else {
                    enableControls();
                }

                Log.d(TAG, "Processing: " + isProcessing);
            }
        });

        viewModel.getProcessingProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null && progress > 0) {
                binding.progressBar.setIndeterminate(false);
                binding.progressBar.setProgress(progress);
                binding.tvProcessingStatus.setText(String.format(Locale.getDefault(),
                        "Processing 8D Audio: %d%%", progress));

                if (progress >= 100) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        binding.cardProcessing.setVisibility(View.GONE);
                    }, 1500);
                }
            }
        });
    }

    private void setupListeners() {
        // ===== 8D AUDIO (FFMPEG PROCESSING) =====
        binding.switch8D.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "8D switch toggled: " + isChecked);
            viewModel.set8DEnabled(isChecked);
            if (service != null) {
                service.set8DEnabled(isChecked);
            }
            // Trigger FFmpeg reprocessing for 8D
            viewModel.triggerReprocessing();
        });

        // ===== BASS BOOST (REAL-TIME ANDROID AUDIOEFFECT) =====
        binding.switchBass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setBassEnabled(isChecked);
            binding.sliderBassBoost.setEnabled(isChecked);
            if (service != null) {
                service.setBassEnabled(isChecked); // Real-time via BassBoost API
            }
        });

        binding.sliderBassBoost.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int dbValue = (int) value;
                binding.tvBassBoostValue.setText(String.format(Locale.getDefault(), "%+d dB", dbValue));
                viewModel.setBassBoost(dbValue);
                if (service != null && binding.switchBass.isChecked()) {
                    service.setBassBoost(dbValue); // Real-time via BassBoost API
                }
            }
        });

        // ===== EQUALIZER (REAL-TIME ANDROID AUDIOEFFECT) =====
        binding.switchEqualizer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setEqualizerEnabled(isChecked);
            enableEqualizerSliders(isChecked);
            if (service != null) {
                service.setEqualizerEnabled(isChecked); // Real-time via Equalizer API
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
                service.setLoudnessEnabled(isChecked); // Real-time via LoudnessEnhancer API
            }
        });

        binding.sliderLoudness.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int gainValue = (int) value;
                binding.tvLoudnessValue.setText(String.format(Locale.getDefault(), "+%d dB", gainValue));
                viewModel.setLoudnessGain(gainValue);
                if (service != null && binding.switchLoudness.isChecked()) {
                    service.setLoudnessGain(gainValue); // Real-time via LoudnessEnhancer API
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
                    service.setBalance(0); // Real-time via MediaPlayer.setVolume()
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
                    service.setBalance(balanceValue); // Real-time via MediaPlayer.setVolume()
                }
            }
        });

        // ===== PLAYBACK SPEED (REAL-TIME VIA PLAYBACKPARAMS) =====
        binding.sliderSpeed.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                binding.tvSpeedValue.setText(String.format(Locale.getDefault(), "%.2fx", value));
                viewModel.setPlaybackSpeed(value);
                if (service != null) {
                    service.setPlaybackSpeed(value); // Real-time via PlaybackParams API
                }
            }
        });
    }

    private void setupBandSlider(Slider slider, TextView valueView, int bandIndex) {
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                int dbValue = (int) value;
                valueView.setText(String.format(Locale.getDefault(), "%+d dB", dbValue));

                switch (bandIndex) {
                    case 0: viewModel.setEqBand1(dbValue); break;
                    case 1: viewModel.setEqBand2(dbValue); break;
                    case 2: viewModel.setEqBand3(dbValue); break;
                    case 3: viewModel.setEqBand4(dbValue); break;
                    case 4: viewModel.setEqBand5(dbValue); break;
                }

                // Real-time via Equalizer API
                if (service != null && binding.switchEqualizer.isChecked()) {
                    service.setEqBandGain(bandIndex, dbValue);
                }
            }
        });
    }


    private void updateEqBand(int bandIndex, Integer gain, Slider slider, TextView valueText) {
        if (gain != null) {
            slider.setValue(gain);
            valueText.setText(String.format(Locale.getDefault(), "%+d dB", gain));
            if (service != null && Boolean.TRUE.equals(viewModel.getIsEqualizerEnabled().getValue())) {
                service.setEqBandGain(bandIndex, gain);
            }
        }
    }

    private void updateBalanceLabel(int balance) {
        if (balance == 0) {
            binding.tvBalanceValue.setText("Center");
        } else if (balance < 0) {
            binding.tvBalanceValue.setText("L" + Math.abs(balance));
        } else {
            binding.tvBalanceValue.setText("R" + balance);
        }
    }

    private void enableEqualizerSliders(boolean enabled) {
        binding.sliderBand1.setEnabled(enabled);
        binding.sliderBand2.setEnabled(enabled);
        binding.sliderBand3.setEnabled(enabled);
        binding.sliderBand4.setEnabled(enabled);
        binding.sliderBand5.setEnabled(enabled);
    }

    private void disableControls() {
        binding.switch8D.setEnabled(false);
        binding.switchBass.setEnabled(false);
        binding.sliderBassBoost.setEnabled(false);
        binding.switchEqualizer.setEnabled(false);
        enableEqualizerSliders(false);
        binding.switchLoudness.setEnabled(false);
        binding.sliderLoudness.setEnabled(false);
        binding.switchBalance.setEnabled(false);
        binding.sliderBalance.setEnabled(false);
        binding.sliderSpeed.setEnabled(false);
    }

    private void enableControls() {
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

        binding.sliderSpeed.setEnabled(true);
    }

    private void applyAllEffectsToService() {
        if (service == null) return;

        // Apply 8D
        Boolean is8D = viewModel.getIs8DEnabled().getValue();
        if (is8D != null) {
            service.set8DEnabled(is8D);
        }

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

        // Apply Equalizer
        Boolean eqEnabled = viewModel.getIsEqualizerEnabled().getValue();
        if (eqEnabled != null) {
            service.setEqualizerEnabled(eqEnabled);
            if (eqEnabled) {
                Integer band1 = viewModel.getEqBand1().getValue();
                if (band1 != null) service.setEqBandGain(0, band1);

                Integer band2 = viewModel.getEqBand2().getValue();
                if (band2 != null) service.setEqBandGain(1, band2);

                Integer band3 = viewModel.getEqBand3().getValue();
                if (band3 != null) service.setEqBandGain(2, band3);

                Integer band4 = viewModel.getEqBand4().getValue();
                if (band4 != null) service.setEqBandGain(3, band4);

                Integer band5 = viewModel.getEqBand5().getValue();
                if (band5 != null) service.setEqBandGain(4, band5);
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
        if (binding.switchBalance.isChecked()) {
            Integer balance = viewModel.getBalance().getValue();
            if (balance != null) {
                service.setBalance(balance);
            }
        }

        // Apply Speed
        Float speed = viewModel.getPlaybackSpeed().getValue();
        if (speed != null) {
            service.setPlaybackSpeed(speed);
        }

        Log.d(TAG, "All effects applied to service");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
