package com.codetrio.spatialflow.viewmodel;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.codetrio.spatialflow.model.SongItem;
import com.codetrio.spatialflow.service.AudioPlaybackService;

import com.codetrio.spatialflow.util.FavoritesManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PlayerSharedViewModel extends ViewModel {

    private static final String TAG = "PlayerSharedViewModel";

    private long lastSkipTime = 0;
    private static final long SKIP_DEBOUNCE_DELAY = 500; // ms

    private MutableLiveData<Uri> songUri = new MutableLiveData<>();
    private MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private MutableLiveData<Integer> currentPosition = new MutableLiveData<>(0);
    private MutableLiveData<Integer> duration = new MutableLiveData<>(0);
    private MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);
    private MutableLiveData<Integer> processingProgress = new MutableLiveData<>(0);

    // Song Library Management
    private MutableLiveData<List<SongItem>> songList = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<Integer> currentSongIndex = new MutableLiveData<>(-1);
    private MutableLiveData<SongItem> currentSong = new MutableLiveData<>();
    private MutableLiveData<Boolean> shouldPromptEffects = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> effectsRefreshTrigger = new MutableLiveData<>(false);

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

    // Shuffle & Repeat
    private MutableLiveData<Boolean> isShuffleEnabled = new MutableLiveData<>(false);
    private MutableLiveData<Integer> repeatMode = new MutableLiveData<>(0); // 0=OFF, 1=ALL, 2=ONE
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;
    private Random shuffleRandom = new Random();

    // Favorites
    private FavoritesManager favoritesManager;
    private MutableLiveData<Boolean> isCurrentSongFavorite = new MutableLiveData<>(false);

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

    public void initFavorites(android.content.Context context) {
        if (favoritesManager == null) {
            favoritesManager = new FavoritesManager(context);
        }
    }

    public LiveData<Boolean> getIsCurrentSongFavorite() {
        return isCurrentSongFavorite;
    }

    public void toggleFavorite() {
        SongItem song = currentSong.getValue();
        if (song == null || favoritesManager == null)
            return;

        boolean newState = !favoritesManager.isFavorite(song.id);
        favoritesManager.setFavorite(song.id, newState);
        isCurrentSongFavorite.setValue(newState);

        // Ensure notification updates if playing
        if (audioService != null) {
            // We can trigger a metadata update to refresh notification actions if needed
            // But simpler is to just let the UI update.
            // If notification has a fav button, we need to update notification.
            audioService.updateNotification(Boolean.TRUE.equals(isPlaying.getValue()));
        }
    }

    public void toggleLoopMode() {
        Integer current = repeatMode.getValue();
        if (current == null)
            current = REPEAT_OFF;

        int nextMode;
        if (current == REPEAT_OFF) {
            nextMode = REPEAT_ALL;
        } else if (current == REPEAT_ALL) {
            nextMode = REPEAT_ONE;
        } else {
            nextMode = REPEAT_OFF;
        }
        repeatMode.setValue(nextMode);
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
        if (audioService != null)
            audioService.play();
    }

    public void pauseAudio() {
        if (audioService != null)
            audioService.pause();
    }

    public void stopAudio() {
        if (audioService != null)
            audioService.stop();
    }

    public void seekTo(int position) {
        if (audioService != null)
            audioService.seekTo(position);
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

    // ===== EFFECTS REFRESH TRIGGER =====

    public LiveData<Boolean> getEffectsRefreshTrigger() {
        return effectsRefreshTrigger;
    }

    public void triggerEffectsRefresh() {
        // Toggle the value to trigger observers (for UI updates)
        Boolean current = effectsRefreshTrigger.getValue();
        effectsRefreshTrigger.setValue(current == null || !current);

        // Also apply effects immediately to service (doesn't wait for EffectsFragment)
        applyAllEffects();
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
        if (audioService == null)
            return;

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

    // ===== HAPTICS =====

    private MutableLiveData<Boolean> isHapticsEnabled = new MutableLiveData<>(false);

    public LiveData<Boolean> getIsHapticsEnabled() {
        return isHapticsEnabled;
    }

    public void setHapticsEnabled(boolean enabled) {
        isHapticsEnabled.setValue(enabled);
        Log.d(TAG, "Haptics state saved: " + enabled);
    }

    // ===== SONG LIBRARY MANAGEMENT =====

    public LiveData<List<SongItem>> getSongList() {
        return songList;
    }

    public void setSongList(List<SongItem> songs) {
        songList.setValue(songs);
        Log.d(TAG, "Song list updated: " + songs.size() + " songs");
    }

    public LiveData<Integer> getCurrentSongIndex() {
        return currentSongIndex;
    }

    public LiveData<SongItem> getCurrentSong() {
        return currentSong;
    }

    public LiveData<Boolean> getShouldPromptEffects() {
        return shouldPromptEffects;
    }

    public void clearEffectsPrompt() {
        shouldPromptEffects.setValue(false);
    }

    /**
     * Check if any audio effect is currently enabled.
     */
    public boolean hasActiveEffects() {
        Boolean _8d = is8DEnabled.getValue();
        Boolean bass = isBassEnabled.getValue();
        Boolean eq = isEqualizerEnabled.getValue();
        Boolean loud = isLoudnessEnabled.getValue();
        return (_8d != null && _8d) ||
                (bass != null && bass) ||
                (eq != null && eq) ||
                (loud != null && loud);
    }

    /**
     * Play song at a specific index in the library.
     */
    public void playSongAtIndex(int index) {
        List<SongItem> songs = songList.getValue();
        if (songs == null || index < 0 || index >= songs.size()) {
            Log.w(TAG, "Invalid song index: " + index);
            return;
        }

        SongItem song = songs.get(index);

        // Auto-reset 8D when switching songs
        SongItem previousSong = currentSong.getValue();
        if (previousSong != null && previousSong.id != song.id) {
            if (Boolean.TRUE.equals(is8DEnabled.getValue())) {
                is8DEnabled.setValue(false);
                if (audioService != null) {
                    audioService.set8DEnabled(false);
                }
                Log.d(TAG, "8D auto-disabled on song change");
            }
        }

        currentSongIndex.setValue(index);
        currentSong.setValue(song);
        songUri.setValue(song.contentUri);

        // Check if we should prompt for effects
        if (hasActiveEffects()) {
            shouldPromptEffects.setValue(true);
        }

        // Update favorite state
        if (favoritesManager != null) {
            isCurrentSongFavorite.setValue(favoritesManager.isFavorite(song.id));
        }

        if (audioService != null) {
            audioService.setSongMetadataById(song.title, song.albumId);
            audioService.loadAndPlay(song.contentUri); // Use loadAndPlay for autoplay
        }

        Log.d(TAG, "Playing song at index " + index + ": " + song.title);
    }

    /**
     * Update song index/state without triggering audio reload.
     * Used by crossfade when next song is already playing via secondary player.
     */
    public void updateSongIndexOnly(int index) {
        List<SongItem> songs = songList.getValue();
        if (songs == null || index < 0 || index >= songs.size()) {
            Log.w(TAG, "Invalid song index for crossfade update: " + index);
            return;
        }

        SongItem song = songs.get(index);

        // Auto-reset 8D when switching songs
        SongItem previousSong = currentSong.getValue();
        if (previousSong != null && previousSong.id != song.id) {
            if (Boolean.TRUE.equals(is8DEnabled.getValue())) {
                is8DEnabled.setValue(false);
                if (audioService != null) {
                    audioService.set8DEnabled(false);
                }
                Log.d(TAG, "8D auto-disabled on crossfade song change");
            }
        }

        currentSongIndex.setValue(index);
        currentSong.setValue(song);
        songUri.setValue(song.contentUri);

        // Update favorite state
        if (favoritesManager != null) {
            isCurrentSongFavorite.setValue(favoritesManager.isFavorite(song.id));
        }

        Log.d(TAG, "Crossfade updated to song at index " + index + ": " + song.title);
    }

    /**
     * Play the next song in the library (respects shuffle and repeat modes).
     */
    public void playNextSong() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSkipTime < SKIP_DEBOUNCE_DELAY) {
            Log.d(TAG, "playNextSong ignored (debounced)");
            return;
        }
        lastSkipTime = currentTime;

        int nextIndex = getNextSongIndex();
        if (nextIndex >= 0) {
            playSongAtIndex(nextIndex);
        } else {
            // End of playlist, stop playback
            Log.d(TAG, "End of playlist reached");
            if (audioService != null) {
                audioService.pause();
            }
        }
    }

    /**
     * Play the previous song in the library.
     */
    public void playPreviousSong() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSkipTime < SKIP_DEBOUNCE_DELAY) {
            Log.d(TAG, "playPreviousSong ignored (debounced)");
            return;
        }
        lastSkipTime = currentTime;

        List<SongItem> songs = songList.getValue();
        Integer currentIdx = currentSongIndex.getValue();

        if (songs == null || songs.isEmpty()) {
            Log.w(TAG, "No songs in library");
            return;
        }

        int prevIndex;
        if (currentIdx == null || currentIdx <= 0) {
            prevIndex = songs.size() - 1;
        } else {
            prevIndex = currentIdx - 1;
        }

        playSongAtIndex(prevIndex);
    }

    /**
     * Find index of song by ID.
     */
    public int findSongIndexById(long songId) {
        List<SongItem> songs = songList.getValue();
        if (songs == null)
            return -1;

        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == songId) {
                return i;
            }
        }
        return -1;
    }

    // ===== SHUFFLE & REPEAT =====

    public LiveData<Boolean> getIsShuffleEnabled() {
        return isShuffleEnabled;
    }

    public void toggleShuffle() {
        Boolean current = isShuffleEnabled.getValue();
        isShuffleEnabled.setValue(current == null || !current);
        Log.d(TAG, "Shuffle " + (isShuffleEnabled.getValue() ? "enabled" : "disabled"));
    }

    public LiveData<Integer> getRepeatMode() {
        return repeatMode;
    }

    public void cycleRepeatMode() {
        Integer current = repeatMode.getValue();
        if (current == null)
            current = REPEAT_OFF;
        int next = (current + 1) % 3; // OFF -> ALL -> ONE -> OFF
        repeatMode.setValue(next);
        Log.d(TAG, "Repeat mode: " + (next == REPEAT_OFF ? "OFF" : next == REPEAT_ALL ? "ALL" : "ONE"));
    }

    /**
     * Get next song index respecting shuffle and repeat modes.
     */
    public int getNextSongIndex() {
        List<SongItem> songs = songList.getValue();
        Integer currentIdx = currentSongIndex.getValue();
        Integer mode = repeatMode.getValue();
        Boolean shuffle = isShuffleEnabled.getValue();

        if (songs == null || songs.isEmpty())
            return -1;
        if (currentIdx == null)
            currentIdx = -1;
        if (mode == null)
            mode = REPEAT_OFF;
        if (shuffle == null)
            shuffle = false;

        // Repeat ONE: stay on same song
        if (mode == REPEAT_ONE) {
            return currentIdx >= 0 ? currentIdx : 0;
        }

        // Shuffle: pick random
        if (shuffle && songs.size() > 1) {
            int nextIdx = currentIdx;
            while (nextIdx == currentIdx) {
                nextIdx = shuffleRandom.nextInt(songs.size());
            }
            return nextIdx;
        }

        // Normal: next in sequence
        int nextIdx = currentIdx + 1;
        if (nextIdx >= songs.size()) {
            if (mode == REPEAT_ALL) {
                nextIdx = 0; // Loop back
            } else {
                return -1; // End of list, no repeat
            }
        }
        return nextIdx;
    }

    // ===== QUEUE MANAGEMENT =====

    /**
     * Add a song to the end of the queue (at end of songList).
     */
    public void addToQueue(SongItem song) {
        List<SongItem> songs = songList.getValue();
        if (songs == null)
            songs = new ArrayList<>();

        List<SongItem> newList = new ArrayList<>(songs);
        // Check if song is already in list to avoid duplicates
        boolean exists = false;
        for (SongItem s : newList) {
            if (s.id == song.id) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            newList.add(song);
            songList.setValue(newList);
            Log.d(TAG, "Added to queue: " + song.title);
        }
    }

    /**
     * Add a song to play next (right after current song).
     */
    public void addToQueueNext(SongItem song) {
        List<SongItem> songs = songList.getValue();
        Integer currentIdx = currentSongIndex.getValue();

        if (songs == null)
            songs = new ArrayList<>();
        if (currentIdx == null)
            currentIdx = -1;

        List<SongItem> newList = new ArrayList<>(songs);

        // Remove if already exists
        for (int i = 0; i < newList.size(); i++) {
            if (newList.get(i).id == song.id) {
                newList.remove(i);
                // Adjust currentIdx if removed song was before it
                if (i < currentIdx) {
                    currentIdx--;
                    currentSongIndex.setValue(currentIdx);
                }
                break;
            }
        }

        // Insert after current song
        int insertIndex = currentIdx + 1;
        if (insertIndex > newList.size()) {
            insertIndex = newList.size();
        }
        newList.add(insertIndex, song);
        songList.setValue(newList);
        Log.d(TAG, "Added to play next: " + song.title + " at index " + insertIndex);
    }

    // ===== FAVORITES =====

    public LiveData<Set<Long>> getFavoriteSongIds() {
        if (favoritesManager == null)
            return new MutableLiveData<>(new HashSet<>());
        Set<String> stringIds = favoritesManager.getFavoriteIds();
        Set<Long> longIds = new HashSet<>();
        for (String id : stringIds) {
            try {
                longIds.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return new MutableLiveData<>(longIds);
    }

    public void toggleFavorite(long songId) {
        if (favoritesManager == null)
            return;
        boolean current = favoritesManager.isFavorite(songId);
        favoritesManager.setFavorite(songId, !current);

        // If current song is the one being toggled, update live data
        SongItem activeSong = currentSong.getValue();
        if (activeSong != null && activeSong.id == songId) {
            isCurrentSongFavorite.setValue(!current);
        }

        Log.d(TAG, "Toggled favorite for " + songId + ": " + !current);
    }

    public boolean isFavorite(long songId) {
        if (favoritesManager == null)
            return false;
        return favoritesManager.isFavorite(songId);
    }

    // ===== LIFECYCLE =====

    @Override
    protected void onCleared() {
        super.onCleared();
        audioService = null;
    }
}
