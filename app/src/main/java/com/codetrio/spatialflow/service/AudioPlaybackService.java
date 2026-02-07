package com.codetrio.spatialflow.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;

import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.codetrio.spatialflow.MainActivity;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.util.AudioFileManager;
import com.codetrio.spatialflow.util.FFmpegCommandBuilder;
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel;

import java.io.File;
import java.io.IOException;

public class AudioPlaybackService extends Service {

    private static final String TAG = "AudioPlaybackService";
    private static final String CHANNEL_ID = "audio_playback_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_PLAY = "com.codetrio.spatialflow.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.codetrio.spatialflow.ACTION_PAUSE";
    private static final String ACTION_PREVIOUS = "com.codetrio.spatialflow.ACTION_PREVIOUS";
    private static final String ACTION_NEXT = "com.codetrio.spatialflow.ACTION_NEXT";
    private static final String ACTION_TOGGLE_LOOP = "com.codetrio.spatialflow.ACTION_TOGGLE_LOOP";
    private static final String ACTION_TOGGLE_FAV = "com.codetrio.spatialflow.ACTION_TOGGLE_FAV";

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private PlayerSharedViewModel viewModel;
    private Handler handler;
    private Runnable progressRunnable;
    private MediaSessionCompat mediaSession;

    private BassBoost bassBoostEffect;
    private Equalizer equalizerEffect;
    private LoudnessEnhancer loudnessEnhancer;

    private Uri currentSourceUri;
    private String currentOriginalFilePath;
    private String currentProcessedFilePath;
    private boolean isProcessing = false;

    private String currentSongName = "SpatialFlow";
    private Bitmap currentAlbumArt = null;
    private boolean is8DEnabled = false;

    // 8D processing state
    private boolean hasProcessed8D = false;
    private float last8DSpeed = -1f;
    private String lastProcessedSourcePath = null;

    // Track which file MediaPlayer currently uses
    private String currentlyLoadedPath = null;

    // Autoplay flag - when true, play() is called automatically after prepare
    private boolean shouldAutoPlay = false;

    // Crossfade support - Real DJ-style crossfade with secondary player
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_CROSSFADE_DURATION = "crossfade_duration";
    private static final String KEY_AUDIO_FOCUS = "audio_focus";
    private int crossfadeDurationMs = 0;
    private boolean isCrossfading = false;
    private boolean crossfadeNextSongStarted = false; // Track if next song was started via crossfade
    private float currentVolume = 1.0f;
    private Runnable crossfadeRunnable;
    private MediaPlayer crossfadePlayer; // Secondary player for incoming song during crossfade
    private float crossfadeInVolume = 0.0f; // Volume of incoming song during crossfade

    // Audio Focus
    private AudioManager audioManager;
    private boolean audioFocusEnabled = true;
    private boolean wasPlayingBeforeFocusLoss = false;
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus permanently - pause
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    pause();
                    wasPlayingBeforeFocusLoss = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus temporarily - pause but remember state
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    pause();
                    wasPlayingBeforeFocusLoss = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Can duck - lower volume
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                // Got focus back - restore volume and resume if we were playing
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(currentVolume, currentVolume);
                    if (wasPlayingBeforeFocusLoss) {
                        play();
                        wasPlayingBeforeFocusLoss = false;
                    }
                }
                break;
        }
    };

    // ============================================================================================
    // LIFECYCLE METHODS
    // ============================================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        handler = new Handler(Looper.getMainLooper());

        // Initialize AudioManager for audio focus handling
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        // Load crossfade and audio focus preferences
        loadAudioPreferences();

        createNotificationChannel();
        setupMediaSession();
        setupMediaPlayerListeners();
        setupProgressTracking();
    }

    private void loadAudioPreferences() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        crossfadeDurationMs = (int) (prefs.getFloat(KEY_CROSSFADE_DURATION, 0f) * 1000);
        audioFocusEnabled = prefs.getBoolean(KEY_AUDIO_FOCUS, true);
        Log.d(TAG, "Loaded prefs: crossfade=" + crossfadeDurationMs + "ms, audioFocus=" + audioFocusEnabled);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = createNotification(
                mediaPlayer != null && mediaPlayer.isPlaying());
        startForeground(NOTIFICATION_ID, notification);

        MediaButtonReceiver.handleIntent(mediaSession, intent);

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PLAY:
                    play();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_PREVIOUS:
                    playPrevious();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
                case ACTION_TOGGLE_LOOP:
                    if (viewModel != null)
                        viewModel.toggleLoopMode();
                    updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
                    break;
                case ACTION_TOGGLE_FAV:
                    if (viewModel != null)
                        viewModel.toggleFavorite();
                    // updateNotification call inside viewmodel.toggleFavorite() handles UI refresh
                    break;
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        releaseAudioEffects();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Clean up crossfade player if active
        if (crossfadePlayer != null) {
            try {
                crossfadePlayer.release();
            } catch (Exception ignored) {
            }
            crossfadePlayer = null;
        }

        if (mediaSession != null) {
            mediaSession.release();
        }
        stopProgressTracking();
    }

    // ============================================================================================
    // PUBLIC CONTROL METHODS
    // ============================================================================================

    public void setViewModel(PlayerSharedViewModel vm) {
        this.viewModel = vm;
        Log.d(TAG, "ViewModel set");
    }

    public void loadAudio(Uri uri) {
        shouldAutoPlay = false; // Disable autoplay for regular load
        loadAudioInternal(uri);
    }

    /**
     * Load audio and automatically start playback when ready.
     */
    public void loadAndPlay(Uri uri) {
        shouldAutoPlay = true; // Enable autoplay
        loadAudioInternal(uri);
    }

    public void setSongMetadata(String songName, Bitmap albumArt) {
        this.currentSongName = songName != null ? songName : "SpatialFlow";

        if (albumArt != null) {
            int size = Math.min(albumArt.getWidth(), albumArt.getHeight());
            Bitmap squared = Bitmap.createBitmap(albumArt,
                    (albumArt.getWidth() - size) / 2,
                    (albumArt.getHeight() - size) / 2,
                    size, size);
            this.currentAlbumArt = Bitmap.createScaledBitmap(squared, 512, 512, true);
        } else {
            this.currentAlbumArt = null;
        }

        updateMediaMetadata();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            updateNotification(true);
        }
    }

    /**
     * Helper to set metadata by loading album art from ID (runs on background
     * thread)
     */
    public void setSongMetadataById(String songName, long albumId) {
        new Thread(() -> {
            Bitmap albumArt = null;
            if (albumId > 0) {
                try {
                    Uri artUri = Uri.parse("content://media/external/audio/albumart/" + albumId);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            albumArt = getContentResolver().loadThumbnail(artUri, new android.util.Size(512, 512),
                                    null);
                        } catch (IOException e) {
                            Log.w(TAG, "loadThumbnail failed, trying MediaStore: " + e.getMessage());
                            // Fallback
                            try {
                                albumArt = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(),
                                        artUri);
                            } catch (Exception ex) {
                                Log.w(TAG, "Fallback bitmap load failed: " + ex.getMessage());
                            }
                        }
                    } else {
                        albumArt = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), artUri);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading album art for metadata: " + e.getMessage());
                }
            }

            final Bitmap finalArt = albumArt;
            handler.post(() -> setSongMetadata(songName, finalArt));
        }).start();
    }

    public void play() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            // Request audio focus if enabled
            if (audioFocusEnabled && audioManager != null) {
                int result = audioManager.requestAudioFocus(
                        audioFocusListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w(TAG, "Audio focus not granted, playing anyway");
                }
            }

            try {
                // Reload preferences for crossfade (user may have changed)
                loadAudioPreferences();

                mediaPlayer.start();
                currentVolume = 1.0f;
                mediaPlayer.setVolume(currentVolume, currentVolume);

                if (viewModel != null) {
                    viewModel.postIsPlaying(true);
                }
                handler.post(progressRunnable);
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                updateNotification(true);
                Log.d(TAG, "Playback started");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot start: " + e.getMessage(), e);
            }
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                if (viewModel != null) {
                    viewModel.postIsPlaying(false);
                }
                stopProgressTracking();
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                updateNotification(false);
                Log.d(TAG, "Playback paused");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot pause: " + e.getMessage(), e);
            }
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }

                if (viewModel != null) {
                    viewModel.postIsPlaying(false);
                    viewModel.setCurrentPosition(0);
                }

                stopProgressTracking();
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
                stopForeground(true);

                // Explicitly cancel notification to ensure it clears
                android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.cancel(NOTIFICATION_ID);
                }

                mediaPlayer.reset();
                if (currentOriginalFilePath != null) {
                    mediaPlayer.setDataSource(currentOriginalFilePath);

                    mediaPlayer.setOnPreparedListener(mp -> {
                        Log.d(TAG, "Media reset and prepared after stop - ready for user action");
                        setupMediaPlayerListeners();
                        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
                    });
                    mediaPlayer.prepareAsync();
                }

                Log.d(TAG, "Playback stopped");
            } catch (IOException | IllegalStateException e) {
                Log.e(TAG, "Error stopping: " + e.getMessage(), e);
            }
        }
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(position);
                if (viewModel != null) {
                    viewModel.setCurrentPosition(position);
                }
                updatePlaybackState(
                        mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                Log.d(TAG, "Seeked to: " + position);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot seek: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Rewind 30 seconds backward
     */
    public void rewind30() {
        if (mediaPlayer != null) {
            try {
                int currentPos = mediaPlayer.getCurrentPosition();
                int newPos = Math.max(0, currentPos - 30000); // 30 seconds = 30000ms
                mediaPlayer.seekTo(newPos);

                if (viewModel != null) {
                    viewModel.setCurrentPosition(newPos);
                }

                updatePlaybackState(
                        mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);

                Log.d(TAG, "Rewound 30 seconds: " + currentPos + " → " + newPos);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot rewind: " + e.getMessage());
            }
        }
    }

    /**
     * Forward 30 seconds ahead
     */
    public void forward30() {
        if (mediaPlayer != null) {
            try {
                int currentPos = mediaPlayer.getCurrentPosition();
                int duration = mediaPlayer.getDuration();
                int newPos = Math.min(duration, currentPos + 30000); // 30 seconds forward
                mediaPlayer.seekTo(newPos);

                if (viewModel != null) {
                    viewModel.setCurrentPosition(newPos);
                }

                updatePlaybackState(
                        mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);

                Log.d(TAG, "Forwarded 30 seconds: " + currentPos + " → " + newPos);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot forward: " + e.getMessage());
            }
        }
    }

    /**
     * Play previous song in the playlist
     */
    public void playPrevious() {
        if (viewModel != null) {
            viewModel.playPreviousSong();
            Log.d(TAG, "Playing previous song via ViewModel");
        } else {
            Log.w(TAG, "Cannot play previous - ViewModel not connected");
        }
    }

    /**
     * Play next song in the playlist
     */
    public void playNext() {
        if (viewModel != null) {
            viewModel.playNextSong();
            Log.d(TAG, "Playing next song via ViewModel");
        } else {
            Log.w(TAG, "Cannot play next - ViewModel not connected");
        }
    }

    // ============================================================================================
    // PUBLIC EFFECT METHODS
    // ============================================================================================

    public void applyEffects(boolean enable8D, boolean enableBass, float speed8D, int bassBoost) {
        Log.d(TAG, "applyEffects called: 8D=" + enable8D + ", speed=" + speed8D);

        if (currentSourceUri == null) {
            Log.e(TAG, "No audio loaded");
            return;
        }

        if (isProcessing) {
            Log.w(TAG, "Already processing, ignoring duplicate request");
            return;
        }

        String currentSourcePath = AudioFileManager.getRealPathFromURI(this, currentSourceUri);

        if (!enable8D) {
            Log.d(TAG, "8D disabled, loading original");
            is8DEnabled = false;
            hasProcessed8D = false;
            lastProcessedSourcePath = null;
            currentProcessedFilePath = null;

            loadOriginalAudio();
            setBassEnabled(enableBass);
            setBassBoost(bassBoost);
            setPlaybackSpeed(1.0f);
            updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
            return;
        }

        is8DEnabled = true;
        setBassEnabled(enableBass);
        setBassBoost(bassBoost);

        boolean sameSource = currentSourcePath != null &&
                currentSourcePath.equals(lastProcessedSourcePath);
        boolean sameSpeed = Math.abs(speed8D - last8DSpeed) < 0.01f;

        if (hasProcessed8D && sameSource && currentProcessedFilePath != null && sameSpeed) {
            Log.d(TAG, "8D already processed with same parameters, skipping reprocessing");
            if (!isCurrentlyPlayingProcessedFile()) {
                loadProcessedAudio(speed8D);
            } else {
                setPlaybackSpeed(speed8D);
            }
            updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
            return;
        }

        Log.d(TAG, "Starting NEW 8D processing with FFmpeg");
        isProcessing = true;

        if (viewModel != null) {
            handler.post(() -> {
                viewModel.setIsProcessing(true);
                viewModel.setProcessingProgress(0);
            });
        }

        final boolean wasPlaying = mediaPlayer.isPlaying();
        final int savedPos = wasPlaying ? mediaPlayer.getCurrentPosition() : 0;

        if (currentSourcePath == null) {
            Log.e(TAG, "Input path is null");
            finishProcessing(false);
            return;
        }

        File outputFile = new File(getCacheDir(),
                "8d_audio_" + System.currentTimeMillis() + ".m4a");
        String outputPath = outputFile.getAbsolutePath();

        String command = FFmpegCommandBuilder.build8D(currentSourcePath, outputPath, 0.08f);
        Log.d(TAG, "FFmpeg command: " + command);

        final int songDuration = mediaPlayer.getDuration();
        final float userSpeed = speed8D;

        FFmpegKit.executeAsync(
                command,
                session -> {
                    ReturnCode returnCode = session.getReturnCode();
                    Log.d(TAG, "FFmpeg completed with code: " + returnCode);

                    if (ReturnCode.isSuccess(returnCode)) {
                        currentProcessedFilePath = outputPath;
                        hasProcessed8D = true;
                        last8DSpeed = userSpeed;
                        lastProcessedSourcePath = currentSourcePath;

                        handler.post(() -> {
                            try {
                                boolean stillPlaying = mediaPlayer.isPlaying();
                                int currentPos = stillPlaying ? mediaPlayer.getCurrentPosition() : savedPos;

                                if (stillPlaying) {
                                    mediaPlayer.pause();
                                }

                                mediaPlayer.reset();
                                mediaPlayer.setDataSource(outputPath);
                                currentlyLoadedPath = outputPath;

                                mediaPlayer.setOnPreparedListener(mp -> {
                                    Log.d(TAG, "8D audio prepared, duration: " + mp.getDuration());

                                    initializeAudioEffects();
                                    setBassEnabled(enableBass);
                                    setBassBoost(bassBoost);
                                    setPlaybackSpeed(userSpeed);

                                    finishProcessing(true);

                                    if (wasPlaying || stillPlaying) {
                                        mp.seekTo(currentPos);
                                        play();
                                    }

                                    setupMediaPlayerListeners();
                                });

                                mediaPlayer.prepareAsync();

                            } catch (IOException e) {
                                Log.e(TAG, "Error loading 8D audio: " + e.getMessage(), e);
                                hasProcessed8D = false;
                                finishProcessing(false);
                            }
                        });
                    } else {
                        Log.e(TAG, "FFmpeg FAILED: " + returnCode);
                        hasProcessed8D = false;
                        handler.post(() -> finishProcessing(false));
                    }
                },
                log -> Log.d(TAG, "FFmpeg: " + log.getMessage()),
                statistics -> {
                    if (statistics != null) {
                        double timeInMillis = statistics.getTime();
                        if (timeInMillis > 0 && songDuration > 0) {
                            double progress = Math.min((timeInMillis * 100) / songDuration, 99);
                            if (viewModel != null) {
                                handler.post(() -> viewModel.setProcessingProgress((int) progress));
                            }
                        }
                    }
                });
    }

    public void set8DEnabled(boolean enabled) {
        this.is8DEnabled = enabled;
        updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
        Log.d(TAG, "8D enabled flag set to: " + enabled);
    }

    public void setBassEnabled(boolean enabled) {
        if (bassBoostEffect != null) {
            bassBoostEffect.setEnabled(enabled);
            Log.d(TAG, "BassBoost enabled: " + enabled);
        }
    }

    /**
     * Sets powerful bass boost with proper gain staging.
     * Simple and effective - no over-boosting that reduces volume.
     *
     * @param boostDb Bass boost level (-15 to +15 dB)
     */
    public void setBassBoost(int boostDb) {
        if (bassBoostEffect == null)
            return;

        final int DB_MIN = -15;
        final int DB_MAX = 15;

        try {
            // Clamp input
            int clampedDb = Math.max(DB_MIN, Math.min(DB_MAX, boostDb));

            if (clampedDb <= 0) {
                // Disable or reduce bass
                bassBoostEffect.setStrength((short) 0);
                Log.d(TAG, "Bass boost disabled");
                return;
            }

            // === SIMPLE POWERFUL FORMULA ===
            // Direct linear mapping with power curve for punch
            // At +15dB: always hit 1000 (maximum)
            float normalized = (float) clampedDb / 15.0f; // 0.0 to 1.0

            // Square curve for more aggressive response at higher levels
            float curved = normalized * normalized; // Exponential punch

            // Convert to Android strength (0-1000)
            int strength = Math.round(curved * 1000);

            // Ensure max strength at high boost
            if (clampedDb >= 12) {
                strength = 1000; // Force maximum at +12dB and above
            }

            bassBoostEffect.setStrength((short) strength);

            Log.d(TAG, "💥 Bass: " + clampedDb + "dB → Strength: " + strength + "/1000");

        } catch (Exception e) {
            Log.e(TAG, "Bass boost failed: " + e.getMessage());
        }
    }

    public void setEqualizerEnabled(boolean enabled) {
        if (equalizerEffect != null) {
            equalizerEffect.setEnabled(enabled);
            Log.d(TAG, "Equalizer enabled: " + enabled);
        }
    }

    public void setEqBandGain(int bandIndex, int gainDb) {
        if (equalizerEffect != null) {
            try {
                short numBands = equalizerEffect.getNumberOfBands();
                if (bandIndex < numBands) {
                    short gainMb = (short) (gainDb * 100);
                    equalizerEffect.setBandLevel((short) bandIndex, gainMb);
                    Log.d(TAG, "EQ band " + bandIndex + ": " + gainDb + " dB");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set EQ: " + e.getMessage());
            }
        }
    }

    public void setLoudnessEnabled(boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && loudnessEnhancer != null) {
            loudnessEnhancer.setEnabled(enabled);
            Log.d(TAG, "Loudness enabled: " + enabled);
        }
    }

    public void setLoudnessGain(int gainDb) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && loudnessEnhancer != null) {
            try {
                int gainMb = gainDb * 1000;
                loudnessEnhancer.setTargetGain(gainMb);
                Log.d(TAG, "Loudness: " + gainDb + " dB");
            } catch (Exception e) {
                Log.e(TAG, "Failed to set loudness: " + e.getMessage());
            }
        }
    }

    public void setBalance(int balanceValue) {
        if (mediaPlayer != null) {
            try {
                float leftVol = 1.0f;
                float rightVol = 1.0f;

                if (balanceValue < 0) {
                    rightVol = 1.0f + (balanceValue / 50.0f);
                } else if (balanceValue > 0) {
                    leftVol = 1.0f - (balanceValue / 50.0f);
                }

                mediaPlayer.setVolume(leftVol, rightVol);
                Log.d(TAG, "Balance: " + balanceValue);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot set balance: " + e.getMessage());
            }
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
            try {
                // BUGFIX: setPlaybackParams() can auto-start playback on some Android versions
                // We need to check the current state and restore it after setting params
                boolean wasPlaying = mediaPlayer.isPlaying();

                android.media.PlaybackParams params = mediaPlayer.getPlaybackParams();
                params.setSpeed(speed);
                params.setPitch(speed);
                mediaPlayer.setPlaybackParams(params);

                // Restore paused state if the player was not playing before
                if (!wasPlaying && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    Log.d(TAG, "Speed: " + speed + "x (pitch matched) - re-paused to prevent auto-play");
                } else {
                    Log.d(TAG, "Speed: " + speed + "x (pitch matched)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set playback speed: " + e.getMessage());
            }
        }
    }

    // ============================================================================================
    // GETTERS
    // ============================================================================================

    public int getAudioSessionId() {
        if (mediaPlayer != null) {
            return mediaPlayer.getAudioSessionId();
        }
        return 0;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot get position: " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot get duration: " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    public boolean is8DEnabled() {
        return is8DEnabled;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    // ============================================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================================

    private void loadAudioInternal(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "URI is null");
            return;
        }

        Log.d(TAG, "Loading audio from URI: " + uri + " (autoplay: " + shouldAutoPlay + ")");

        // Reset crossfade state when loading new audio
        if (crossfadeRunnable != null) {
            handler.removeCallbacks(crossfadeRunnable);
        }
        isCrossfading = false;
        currentVolume = 1.0f;

        loadAudioInternalDirect(uri);
    }

    private void loadAudioInternalDirect(Uri uri) {
        currentSourceUri = uri;

        hasProcessed8D = false;
        last8DSpeed = -1f;
        lastProcessedSourcePath = null;
        currentProcessedFilePath = null;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        stopProgressTracking();

        try {
            currentOriginalFilePath = AudioFileManager.getRealPathFromURI(this, uri);
            if (currentOriginalFilePath == null) {
                Log.e(TAG, "Failed to get file path from URI");
                return;
            }

            Log.d(TAG, "File path: " + currentOriginalFilePath);

            mediaPlayer.reset();
            mediaPlayer.setDataSource(currentOriginalFilePath);
            currentlyLoadedPath = currentOriginalFilePath;

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "Audio loaded and ready, duration: " + mp.getDuration());

                if (viewModel != null) {
                    viewModel.setDuration(mp.getDuration());
                    viewModel.setCurrentPosition(0);
                }

                updateMediaMetadata();
                initializeAudioEffects();
                setupMediaPlayerListeners();

                // Check if we should auto-play
                if (shouldAutoPlay) {
                    Log.d(TAG, "Autoplay enabled - starting playback");
                    play();
                    shouldAutoPlay = false; // Reset flag
                } else {
                    if (viewModel != null) {
                        viewModel.postIsPlaying(false);
                    }
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    updateNotification(false);
                    Log.d(TAG, "Ready to play - awaiting user action");
                }
            });

            mediaPlayer.prepareAsync();

        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "Error loading audio: " + e.getMessage(), e);
            shouldAutoPlay = false; // Reset on error
        }
    }

    private void loadProcessedAudio(float speed) {
        if (currentProcessedFilePath == null || !new File(currentProcessedFilePath).exists()) {
            Log.e(TAG, "Processed file not found");
            return;
        }

        boolean wasPlaying = mediaPlayer.isPlaying();
        int position = mediaPlayer.getCurrentPosition();

        if (wasPlaying) {
            mediaPlayer.pause();
        }

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(currentProcessedFilePath);
            currentlyLoadedPath = currentProcessedFilePath;

            mediaPlayer.setOnPreparedListener(mp -> {
                initializeAudioEffects();
                setPlaybackSpeed(speed);

                if (wasPlaying) {
                    mp.seekTo(position);
                    play();
                }
                setupMediaPlayerListeners();
            });
            mediaPlayer.prepareAsync();

            Log.d(TAG, "Processed audio loading...");
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "Error loading processed audio: " + e.getMessage(), e);
        }
    }

    private void loadOriginalAudio() {
        if (currentSourceUri == null)
            return;

        boolean wasPlaying = mediaPlayer.isPlaying();
        int position = mediaPlayer.getCurrentPosition();

        if (wasPlaying) {
            mediaPlayer.pause();
        }

        try {
            String originalPath = AudioFileManager.getRealPathFromURI(this, currentSourceUri);
            if (originalPath != null) {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(originalPath);
                currentlyLoadedPath = originalPath;

                mediaPlayer.setOnPreparedListener(mp -> {
                    initializeAudioEffects();

                    if (wasPlaying) {
                        mp.seekTo(position);
                        play();
                    }
                    setupMediaPlayerListeners();
                });
                mediaPlayer.prepareAsync();

                Log.d(TAG, "Original audio loading...");
            }
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "Error loading original: " + e.getMessage(), e);
        }
    }

    private void initializeAudioEffects() {
        try {
            int audioSessionId = mediaPlayer.getAudioSessionId();
            releaseAudioEffects();

            // Create Equalizer FIRST (priority effect to prevent dropout)
            equalizerEffect = new Equalizer(0, audioSessionId);
            equalizerEffect.setEnabled(false);

            bassBoostEffect = new BassBoost(0, audioSessionId);
            bassBoostEffect.setEnabled(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                loudnessEnhancer.setEnabled(false);
            }

            Log.d(TAG, "AudioEffects initialized for session: " + audioSessionId);

            // IMPORTANT: Trigger effects refresh so EffectsFragment re-applies user
            // settings
            // This ensures EQ bands, bass boost, etc. are applied to new effect instances
            if (viewModel != null) {
                handler.post(() -> {
                    viewModel.triggerEffectsRefresh();
                    Log.d(TAG, "Effects refresh triggered after initialization");
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AudioEffects: " + e.getMessage(), e);
        }
    }

    private void releaseAudioEffects() {
        if (bassBoostEffect != null) {
            bassBoostEffect.release();
            bassBoostEffect = null;
        }
        if (equalizerEffect != null) {
            equalizerEffect.release();
            equalizerEffect = null;
        }
        if (loudnessEnhancer != null) {
            loudnessEnhancer.release();
            loudnessEnhancer = null;
        }

    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                stop();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onRewind() {
                rewind30();
            }

            @Override
            public void onFastForward() {
                forward30();
            }

            @Override
            public void onSkipToNext() {
                playNext();
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }
        });

        updatePlaybackState(PlaybackStateCompat.STATE_NONE);
        mediaSession.setActive(true);
    }

    private void setupMediaPlayerListeners() {
        mediaPlayer.setOnCompletionListener(mp -> {
            Log.d(TAG, "Playback completion signal received");

            // If crossfade already handled the transition, don't trigger playNextSong
            if (crossfadeNextSongStarted) {
                Log.d(TAG, "Completion ignored - crossfade already transitioned to next song");
                crossfadeNextSongStarted = false;
                isCrossfading = false;
                return;
            }

            try {
                int currentPos = mp.getCurrentPosition();
                int duration = mp.getDuration();

                // Only skip if we are close to the end (within 1 second)
                // This prevents accidental skips triggered by stop/reset/seek during
                // transitions
                if (currentPos >= duration - 1000) {
                    Log.d(TAG, "Natural completion detected, auto-playing next");
                    if (viewModel != null) {
                        viewModel.setCurrentPosition(0);
                        handler.post(() -> {
                            if (viewModel != null) {
                                viewModel.playNextSong();
                            }
                        });
                    }
                } else {
                    Log.d(TAG, "Completion signal ignored: Position " + currentPos + " < Duration " + duration);
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "MediaPlayer state error during completion check");
            }

            updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            Log.d(TAG, "MediaPlayer prepared, duration: " + mp.getDuration());
            if (viewModel != null) {
                viewModel.setDuration(mp.getDuration());
            }
            updateMediaMetadata();
            initializeAudioEffects();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
            if (viewModel != null) {
                viewModel.setIsPlaying(false);
            }
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            return false;
        });
    }

    private void setupProgressTracking() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            int currentPos = mediaPlayer.getCurrentPosition();
                            int duration = mediaPlayer.getDuration();

                            if (viewModel != null) {
                                viewModel.setCurrentPosition(currentPos);
                            }
                            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);

                            // Crossfade at END of song - start fading when approaching end
                            loadAudioPreferences();
                            int remainingMs = duration - currentPos;
                            if (crossfadeDurationMs > 0 && remainingMs <= crossfadeDurationMs && remainingMs > 0
                                    && !isCrossfading) {
                                Log.d(TAG,
                                        "Starting crossfade fadeout at end of song (" + remainingMs + "ms remaining)");
                                startCrossfadeOut(remainingMs);
                            }
                        }
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "MediaPlayer in invalid state for position sync");
                    }
                    handler.postDelayed(this, 100);
                }
            }
        };
        handler.post(progressRunnable);
    }

    /**
     * Real DJ-style crossfade: Start next song early, fade OUT current + fade IN
     * next simultaneously
     */
    private void startCrossfadeOut(int durationMs) {
        isCrossfading = true;
        crossfadeNextSongStarted = false;

        // Cancel any existing crossfade
        if (crossfadeRunnable != null) {
            handler.removeCallbacks(crossfadeRunnable);
        }

        // Get next song info from ViewModel
        if (viewModel == null) {
            Log.w(TAG, "Crossfade: No ViewModel, falling back to simple fade");
            simpleFadeOut(durationMs);
            return;
        }

        int nextIndex = viewModel.getNextSongIndex();
        if (nextIndex < 0) {
            Log.d(TAG, "Crossfade: No next song, simple fade out");
            simpleFadeOut(durationMs);
            return;
        }

        // Get next song URI
        java.util.List<com.codetrio.spatialflow.model.SongItem> songs = viewModel.getSongList().getValue();
        if (songs == null || nextIndex >= songs.size()) {
            simpleFadeOut(durationMs);
            return;
        }

        com.codetrio.spatialflow.model.SongItem nextSong = songs.get(nextIndex);
        Uri nextSongUri = nextSong.contentUri;

        // Prepare secondary player for incoming song
        try {
            if (crossfadePlayer != null) {
                try {
                    crossfadePlayer.release();
                } catch (Exception ignored) {
                }
            }

            crossfadePlayer = new MediaPlayer();
            crossfadePlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            crossfadePlayer.setDataSource(this, nextSongUri);
            crossfadePlayer.setVolume(0f, 0f); // Start silent
            crossfadeInVolume = 0f;

            crossfadePlayer.setOnPreparedListener(mp -> {
                Log.d(TAG, "Crossfade: Next song prepared, starting crossfade mix");
                crossfadeNextSongStarted = true;
                mp.start();
                startCrossfadeMix(durationMs);
            });

            crossfadePlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Crossfade player error: " + what);
                simpleFadeOut(durationMs);
                return true;
            });

            crossfadePlayer.prepareAsync();
            Log.d(TAG, "Crossfade: Preparing next song: " + nextSong.title);

        } catch (Exception e) {
            Log.e(TAG, "Crossfade preparation failed: " + e.getMessage());
            simpleFadeOut(durationMs);
        }
    }

    /**
     * Perform the actual crossfade mixing: fade out current + fade in next
     * simultaneously
     */
    private void startCrossfadeMix(int durationMs) {
        final int fadeSteps = 25;
        final long stepDuration = durationMs / fadeSteps;
        final float volumeStep = 1.0f / fadeSteps;

        crossfadeRunnable = new Runnable() {
            float outVol = currentVolume;
            float inVol = 0f;
            int step = 0;

            @Override
            public void run() {
                if (step < fadeSteps) {
                    // Fade OUT current song
                    outVol -= volumeStep;
                    outVol = Math.max(0f, outVol);

                    // Fade IN next song (with slight boost curve for smoother transition)
                    inVol += volumeStep;
                    inVol = Math.min(1f, inVol);
                    float curvedInVol = (float) Math.pow(inVol, 0.7); // Smooth curve for fade-in

                    try {
                        if (mediaPlayer != null) {
                            mediaPlayer.setVolume(outVol, outVol);
                        }
                        if (crossfadePlayer != null) {
                            crossfadePlayer.setVolume(curvedInVol, curvedInVol);
                            crossfadeInVolume = curvedInVol;
                        }
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "Crossfade volume set error: " + e.getMessage());
                    }

                    step++;
                    handler.postDelayed(this, stepDuration);
                } else {
                    // Crossfade complete - switch players
                    finishCrossfade();
                }
            }
        };
        handler.post(crossfadeRunnable);
    }

    /**
     * Complete the crossfade transition - swap players and update state
     */
    private void finishCrossfade() {
        Log.d(TAG, "Crossfade complete, swapping to next song");

        // Stop and release old player
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
        }

        // Swap players - crossfadePlayer becomes main player
        mediaPlayer = crossfadePlayer;
        crossfadePlayer = null;
        currentVolume = 1.0f;

        if (mediaPlayer != null) {
            mediaPlayer.setVolume(1f, 1f);
            setupMediaPlayerListeners();
        }

        // Update ViewModel to next song (without triggering playback again)
        if (viewModel != null) {
            int nextIndex = viewModel.getNextSongIndex();
            if (nextIndex >= 0) {
                java.util.List<com.codetrio.spatialflow.model.SongItem> songs = viewModel.getSongList().getValue();
                if (songs != null && nextIndex < songs.size()) {
                    com.codetrio.spatialflow.model.SongItem nextSong = songs.get(nextIndex);

                    // Update UI state without reloading audio - audio is already playing
                    final int finalNextIndex = nextIndex;
                    handler.post(() -> {
                        // Use updateSongIndexOnly to sync UI without triggering playback
                        viewModel.updateSongIndexOnly(finalNextIndex);

                        // Update metadata for notification
                        setSongMetadataById(nextSong.title, nextSong.albumId);

                        if (mediaPlayer != null) {
                            viewModel.setDuration(mediaPlayer.getDuration());
                            viewModel.setCurrentPosition(0);
                        }
                    });
                }
            }
        }

        isCrossfading = false;
        crossfadeNextSongStarted = false;

        // Re-initialize effects for the new player
        initializeAudioEffects();

        updateNotification(true);
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    /**
     * Simple fade out (fallback when crossfade to next song isn't possible)
     */
    private void simpleFadeOut(int durationMs) {
        final int fadeSteps = 20;
        final long stepDuration = durationMs / fadeSteps;
        final float volumeStep = currentVolume / fadeSteps;

        crossfadeRunnable = new Runnable() {
            float vol = currentVolume;
            int step = 0;

            @Override
            public void run() {
                if (step < fadeSteps && mediaPlayer != null) {
                    vol -= volumeStep;
                    vol = Math.max(0, vol);
                    try {
                        mediaPlayer.setVolume(vol, vol);
                    } catch (IllegalStateException e) {
                        // Player might have been released
                    }
                    step++;
                    handler.postDelayed(this, stepDuration);
                }
            }
        };
        handler.post(crossfadeRunnable);
    }

    private void stopProgressTracking() {
        if (handler != null && progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audio Playback",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Shows currently playing audio");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(boolean isPlaying) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(MainActivity.EXTRA_OPEN_PLAYER, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Previous track
        Intent prevIntent = new Intent(this, AudioPlaybackService.class)
                .setAction(ACTION_PREVIOUS);
        PendingIntent prevPendingIntent = PendingIntent.getService(
                this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Loop Action
        Intent loopIntent = new Intent(this, AudioPlaybackService.class)
                .setAction(ACTION_TOGGLE_LOOP);
        PendingIntent loopPendingIntent = PendingIntent.getService(
                this, 2, loopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Play/Pause
        Intent playPauseIntent = new Intent(this, AudioPlaybackService.class)
                .setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this, 3, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Favorite Action
        Intent favIntent = new Intent(this, AudioPlaybackService.class)
                .setAction(ACTION_TOGGLE_FAV);
        PendingIntent favPendingIntent = PendingIntent.getService(
                this, 4, favIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Next track
        Intent nextIntent = new Intent(this, AudioPlaybackService.class)
                .setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, 5, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(currentSongName)
                .setContentText(is8DEnabled ? "🎧 8D Audio" : "Normal Playback")
                .setSubText("SpatialFlow")
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(isPlaying)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setAutoCancel(false);

        if (currentAlbumArt != null) {
            builder.setLargeIcon(currentAlbumArt);
        }

        // Add all control actions
        // Order: Loop | Prev | Play | Next | Fav

        // Loop Icon - change based on state if possible, but notification icon update
        // is tricky without custom view.
        // Using static loop icon for now.
        int loopIcon = R.drawable.ic_repeat; // Assuming valid drawable
        if (viewModel != null) {
            Integer mode = viewModel.getRepeatMode().getValue();
            if (mode != null && mode == PlayerSharedViewModel.REPEAT_ONE) {
                loopIcon = R.drawable.ic_repeat_one;
            } else if (mode != null && mode == PlayerSharedViewModel.REPEAT_ALL) {
                loopIcon = R.drawable.ic_repeat; // Active color handled by system usually? No.
            }
        }

        builder.addAction(loopIcon, "Loop", loopPendingIntent);
        builder.addAction(R.drawable.ic_skip_previous, "Previous", prevPendingIntent);
        builder.addAction(
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                isPlaying ? "Pause" : "Play",
                playPausePendingIntent);
        builder.addAction(R.drawable.ic_skip_next, "Next", nextPendingIntent);

        // Fav Icon
        int favIcon = R.drawable.ic_favorite_border;
        if (viewModel != null) {
            Boolean isFav = viewModel.getIsCurrentSongFavorite().getValue();
            if (Boolean.TRUE.equals(isFav)) {
                favIcon = R.drawable.ic_favorite;
            }
        }
        builder.addAction(favIcon, "Favorite", favPendingIntent);

        // MediaStyle with 3 actions in compact view (prev, play/pause, next)
        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(1, 2, 3); // indices: 1=prev, 2=play/pause, 3=next (0 is loop)

        builder.setStyle(mediaStyle);

        return builder.build();
    }

    public void updateNotification(boolean isPlaying) {
        Notification notification = createNotification(isPlaying);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void updatePlaybackState(int state) {
        long position = 0;
        try {
            position = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        } catch (IllegalStateException e) {
            Log.w(TAG, "Cannot get position in current state");
        }

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_STOP |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_REWIND |
                                PlaybackStateCompat.ACTION_FAST_FORWARD |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, position, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateMediaMetadata() {
        long duration = 0;
        try {
            duration = mediaPlayer != null && mediaPlayer.getDuration() > 0 ? mediaPlayer.getDuration() : 0;
        } catch (IllegalStateException e) {
            Log.w(TAG, "Cannot get duration in current state");
        }

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSongName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "SpatialFlow")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        if (currentAlbumArt != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt);
        }

        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void finishProcessing(boolean success) {
        isProcessing = false;
        if (viewModel != null) {
            viewModel.postIsProcessing(false);
            handler.post(() -> viewModel.setProcessingProgress(success ? 100 : 0));
        }
        updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
        Log.d(TAG, "Processing finished: " + (success ? "SUCCESS" : "FAILED"));
    }

    private boolean isCurrentlyPlayingProcessedFile() {
        if (mediaPlayer == null || currentProcessedFilePath == null) {
            return false;
        }
        return currentlyLoadedPath != null &&
                currentlyLoadedPath.equals(currentProcessedFilePath);
    }

    // ============================================================================================
    // INNER CLASSES
    // ============================================================================================

    public class LocalBinder extends Binder {
        public AudioPlaybackService getService() {
            return AudioPlaybackService.this;
        }
    }
}
