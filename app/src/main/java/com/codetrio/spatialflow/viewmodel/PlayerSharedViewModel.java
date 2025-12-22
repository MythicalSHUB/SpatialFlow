package com.codetrio.spatialflow.viewmodel;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.codetrio.spatialflow.service.AudioPlaybackService;

public class PlayerSharedViewModel extends ViewModel {

    private MutableLiveData<Uri> songUri = new MutableLiveData<>();
    private MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private MutableLiveData<Integer> currentPosition = new MutableLiveData<>(0);
    private MutableLiveData<Integer> duration = new MutableLiveData<>(0);
    private MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);
    private MutableLiveData<Integer> processingProgress = new MutableLiveData<>(0);

    // Effects settings
    private MutableLiveData<Boolean> is8DEnabled = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> isBassEnabled = new MutableLiveData<>(false);

    // 8D speed locked at 0.2 Hz
    private MutableLiveData<Float> speed8D = new MutableLiveData<>(0.2f);

    // Bass boost (-15 to +15 dB)
    private MutableLiveData<Integer> bassBoost = new MutableLiveData<>(0);

    // 5-Band Equalizer
    private MutableLiveData<Boolean> isEqualizerEnabled = new MutableLiveData<>(false);
    private MutableLiveData<Integer> eqBand1 = new MutableLiveData<>(0); // 60Hz
    private MutableLiveData<Integer> eqBand2 = new MutableLiveData<>(0); // 230Hz
    private MutableLiveData<Integer> eqBand3 = new MutableLiveData<>(0); // 910Hz
    private MutableLiveData<Integer> eqBand4 = new MutableLiveData<>(0); // 3600Hz
    private MutableLiveData<Integer> eqBand5 = new MutableLiveData<>(0); // 14000Hz

    // Loudness Enhancer
    private MutableLiveData<Boolean> isLoudnessEnabled = new MutableLiveData<>(false);
    private MutableLiveData<Integer> loudnessGain = new MutableLiveData<>(0); // 0-12 dB

    // Balance (L/R)
    private MutableLiveData<Integer> balance = new MutableLiveData<>(0); // -50 to +50, 0 = center

    // Playback Speed
    private MutableLiveData<Float> playbackSpeed = new MutableLiveData<>(1.0f); // 0.5x to 2.0x

    // Service reference
    private MutableLiveData<AudioPlaybackService> audioServiceLiveData = new MutableLiveData<>();
    private AudioPlaybackService audioService;

    // ===== SERVICE BINDING =====

    public LiveData<AudioPlaybackService> getAudioService() {
        return audioServiceLiveData;
    }

    public void setAudioService(AudioPlaybackService service) {
        this.audioService = service;
        this.audioServiceLiveData.setValue(service);
        service.setViewModel(this);
    }

    // ===== SONG URI =====

    public LiveData<Uri> getSongUri() {
        return songUri;
    }

    public void setSongUri(Uri uri) {
        songUri.setValue(uri);
        if (audioService != null) {
            audioService.loadAudio(uri);
        }
    }

    // ===== PLAYBACK CONTROLS =====

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public void setIsPlaying(boolean playing) {
        isPlaying.setValue(playing);
    }

    // 🔥 NEW: Thread-safe setter
    public void postIsPlaying(boolean playing) {
        isPlaying.postValue(playing);
    }

    public void playAudio() {
        if (audioService != null) audioService.play();
    }

    public void pauseAudio() {
        if (audioService != null) audioService.pause();
    }

    public void stopAudio() {
        if (audioService != null) audioService.stop();
    }

    public void seekTo(int position) {
        if (audioService != null) audioService.seekTo(position);
    }

    // ===== POSITION & DURATION =====

    public LiveData<Integer> getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int position) {
        currentPosition.postValue(position);
    }

    public LiveData<Integer> getDuration() {
        return duration;
    }

    public void setDuration(int dur) {
        duration.postValue(dur);
    }

    // ===== PROCESSING STATE =====

    public LiveData<Boolean> getIsProcessing() {
        return isProcessing;
    }

    public void setIsProcessing(boolean processing) {
        isProcessing.setValue(processing);
    }

    public void postIsProcessing(boolean processing) {
        isProcessing.postValue(processing);
    }

    public LiveData<Integer> getProcessingProgress() {
        return processingProgress;
    }

    public void setProcessingProgress(int progress) {
        processingProgress.setValue(progress);
    }

    // ===== 8D AUDIO =====

    public LiveData<Boolean> getIs8DEnabled() {
        return is8DEnabled;
    }

    public void set8DEnabled(boolean enabled) {
        is8DEnabled.setValue(enabled);
        if (audioService != null) {
            audioService.set8DEnabled(enabled);
        }
    }

    public LiveData<Float> get8DSpeed() {
        return speed8D;
    }

    public void set8DSpeed(float speed) {
        speed8D.setValue(speed);
    }

    // ===== BASS BOOST =====

    public LiveData<Boolean> getIsBassEnabled() {
        return isBassEnabled;
    }

    public void setBassEnabled(boolean enabled) {
        isBassEnabled.setValue(enabled);
        if (audioService != null) {
            audioService.setBassEnabled(enabled);
        }
    }

    public LiveData<Integer> getBassBoost() {
        return bassBoost;
    }

    public void setBassBoost(int boost) {
        bassBoost.setValue(boost);
        if (audioService != null) {
            audioService.setBassBoost(boost);
        }
    }

    // ===== 5-BAND EQUALIZER =====

    public LiveData<Boolean> getIsEqualizerEnabled() {
        return isEqualizerEnabled;
    }

    public void setEqualizerEnabled(boolean enabled) {
        isEqualizerEnabled.setValue(enabled);
        if (audioService != null) {
            audioService.setEqualizerEnabled(enabled);
        }
    }

    public LiveData<Integer> getEqBand1() {
        return eqBand1;
    }

    public void setEqBand1(int gainDb) {
        eqBand1.setValue(gainDb);
        if (audioService != null) {
            audioService.setEqBandGain(0, gainDb);
        }
    }

    public LiveData<Integer> getEqBand2() {
        return eqBand2;
    }

    public void setEqBand2(int gainDb) {
        eqBand2.setValue(gainDb);
        if (audioService != null) {
            audioService.setEqBandGain(1, gainDb);
        }
    }

    public LiveData<Integer> getEqBand3() {
        return eqBand3;
    }

    public void setEqBand3(int gainDb) {
        eqBand3.setValue(gainDb);
        if (audioService != null) {
            audioService.setEqBandGain(2, gainDb);
        }
    }

    public LiveData<Integer> getEqBand4() {
        return eqBand4;
    }

    public void setEqBand4(int gainDb) {
        eqBand4.setValue(gainDb);
        if (audioService != null) {
            audioService.setEqBandGain(3, gainDb);
        }
    }

    public LiveData<Integer> getEqBand5() {
        return eqBand5;
    }

    public void setEqBand5(int gainDb) {
        eqBand5.setValue(gainDb);
        if (audioService != null) {
            audioService.setEqBandGain(4, gainDb);
        }
    }

    // Generic setter for any band
    public void setEqBandGain(int bandIndex, int gainDb) {
        switch (bandIndex) {
            case 0:
                setEqBand1(gainDb);
                break;
            case 1:
                setEqBand2(gainDb);
                break;
            case 2:
                setEqBand3(gainDb);
                break;
            case 3:
                setEqBand4(gainDb);
                break;
            case 4:
                setEqBand5(gainDb);
                break;
        }
    }

    // ===== LOUDNESS ENHANCER =====

    public LiveData<Boolean> getIsLoudnessEnabled() {
        return isLoudnessEnabled;
    }

    public void setLoudnessEnabled(boolean enabled) {
        isLoudnessEnabled.setValue(enabled);
        if (audioService != null) {
            audioService.setLoudnessEnabled(enabled);
        }
    }

    public LiveData<Integer> getLoudnessGain() {
        return loudnessGain;
    }

    public void setLoudnessGain(int gain) {
        loudnessGain.setValue(gain);
        if (audioService != null) {
            audioService.setLoudnessGain(gain);
        }
    }

    // ===== BALANCE (L/R) =====

    public LiveData<Integer> getBalance() {
        return balance;
    }

    public void setBalance(int balanceValue) {
        balance.setValue(balanceValue);
        if (audioService != null) {
            audioService.setBalance(balanceValue);
        }
    }

    // ===== PLAYBACK SPEED =====

    public LiveData<Float> getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(float speed) {
        playbackSpeed.setValue(speed);
        if (audioService != null) {
            audioService.setPlaybackSpeed(speed);
        }
    }

    // ===== REPROCESSING TRIGGER =====

    public void triggerReprocessing() {
        if (audioService != null && songUri.getValue() != null) {
            boolean enable8D = is8DEnabled.getValue() != null && is8DEnabled.getValue();
            boolean enableBass = isBassEnabled.getValue() != null && isBassEnabled.getValue();

            // Use current 8D speed and bass boost values
            float speed = speed8D.getValue() != null ? speed8D.getValue() : 0.2f;
            int boost = bassBoost.getValue() != null ? bassBoost.getValue() : 0;

            audioService.applyEffects(enable8D, enableBass, speed, boost);
        }
    }

    // ===== METADATA =====

    public void updateSongMetadata(String songName, Bitmap albumArt) {
        if (audioService != null) {
            audioService.setSongMetadata(songName, albumArt);
        }
    }

    // ===== UTILITY METHODS =====

    // 🔥 NEW: Check if service is bound
    public boolean isServiceBound() {
        return audioService != null;
    }

    // 🔥 NEW: Get current playback state from service
    public boolean isCurrentlyPlaying() {
        return audioService != null && audioService.isPlaying();
    }

    // 🔥 NEW: Check if 8D processing is active
    public boolean isCurrentlyProcessing() {
        return audioService != null && audioService.isProcessing();
    }

    // 🔥 NEW: Reset all effects to default
    public void resetAllEffects() {
        set8DEnabled(false);
        setBassEnabled(false);
        setBassBoost(0);
        setEqualizerEnabled(false);
        setEqBand1(0);
        setEqBand2(0);
        setEqBand3(0);
        setEqBand4(0);
        setEqBand5(0);
        setLoudnessEnabled(false);
        setLoudnessGain(0);
        setBalance(0);
        setPlaybackSpeed(1.0f);
    }

    // 🔥 NEW: Apply all current effects to service (for reconnection)
    public void applyAllEffects() {
        if (audioService == null) return;

        // Apply all current state values to service
        audioService.set8DEnabled(is8DEnabled.getValue() != null && is8DEnabled.getValue());
        audioService.setBassEnabled(isBassEnabled.getValue() != null && isBassEnabled.getValue());

        if (isBassEnabled.getValue() != null && isBassEnabled.getValue()) {
            audioService.setBassBoost(bassBoost.getValue() != null ? bassBoost.getValue() : 0);
        }

        audioService.setEqualizerEnabled(isEqualizerEnabled.getValue() != null && isEqualizerEnabled.getValue());

        if (isEqualizerEnabled.getValue() != null && isEqualizerEnabled.getValue()) {
            audioService.setEqBandGain(0, eqBand1.getValue() != null ? eqBand1.getValue() : 0);
            audioService.setEqBandGain(1, eqBand2.getValue() != null ? eqBand2.getValue() : 0);
            audioService.setEqBandGain(2, eqBand3.getValue() != null ? eqBand3.getValue() : 0);
            audioService.setEqBandGain(3, eqBand4.getValue() != null ? eqBand4.getValue() : 0);
            audioService.setEqBandGain(4, eqBand5.getValue() != null ? eqBand5.getValue() : 0);
        }

        audioService.setLoudnessEnabled(isLoudnessEnabled.getValue() != null && isLoudnessEnabled.getValue());

        if (isLoudnessEnabled.getValue() != null && isLoudnessEnabled.getValue()) {
            audioService.setLoudnessGain(loudnessGain.getValue() != null ? loudnessGain.getValue() : 0);
        }

        audioService.setBalance(balance.getValue() != null ? balance.getValue() : 0);
        audioService.setPlaybackSpeed(playbackSpeed.getValue() != null ? playbackSpeed.getValue() : 1.0f);
    }

    // ===== LIFECYCLE =====

    @Override
    protected void onCleared() {
        super.onCleared();
        audioService = null;
    }
}
