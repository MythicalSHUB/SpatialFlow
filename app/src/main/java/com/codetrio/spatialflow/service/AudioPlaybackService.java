// File: AudioPlaybackService.java
package com.codetrio.spatialflow.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.MediaPlayer;
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
import com.arthenica.ffmpegkit.FFmpegSession;
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
    private static final String ACTION_STOP = "com.codetrio.spatialflow.ACTION_STOP";
    private static final String ACTION_TOGGLE_8D = "com.codetrio.spatialflow.ACTION_TOGGLE_8D";

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
    private int savedPosition = 0;

    private String currentSongName = "SpatialFlow";
    private Bitmap currentAlbumArt = null;
    private boolean is8DEnabled = false;

    // ===== NEW: Processing state tracking to prevent duplicate processing =====
    private boolean hasProcessed8D = false;
    private float last8DSpeed = -1f;
    private String lastProcessedSourcePath = null;

    // ===== NEW: Track the path currently loaded into MediaPlayer =====
    private String currentlyLoadedPath = null;

    public class LocalBinder extends Binder {
        public AudioPlaybackService getService() {
            return AudioPlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        setupMediaSession();
        setupMediaPlayerListeners();
        setupProgressTracking();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audio Playback",
                    NotificationManager.IMPORTANCE_HIGH
            );
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

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

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
        });

        updatePlaybackState(PlaybackStateCompat.STATE_NONE);
        mediaSession.setActive(true);
    }

    private void updatePlaybackState(int state) {
        long position = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_STOP |
                                PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, position, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateMediaMetadata() {
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSongName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "SpatialFlow")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                        mediaPlayer != null ? mediaPlayer.getDuration() : 0);

        if (currentAlbumArt != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt);
        }

        mediaSession.setMetadata(metadataBuilder.build());
    }

    private Notification createNotification(boolean isPlaying) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent playPauseIntent = new Intent(this, AudioPlaybackService.class)
                .setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, AudioPlaybackService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggle8DIntent = new Intent(this, AudioPlaybackService.class).setAction(ACTION_TOGGLE_8D);
        PendingIntent toggle8DPendingIntent = PendingIntent.getService(
                this, 3, toggle8DIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(currentSongName)
                .setContentText(is8DEnabled ? "🎧 8D Audio ON" : "Normal Playback")
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

        builder.addAction(R.drawable.ic_stop, "Stop", stopPendingIntent);
        builder.addAction(
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                isPlaying ? "Pause" : "Play",
                playPausePendingIntent
        );
        builder.addAction(
                is8DEnabled ? R.drawable.ic_8d_on : R.drawable.ic_8d_off,
                is8DEnabled ? "8D ON" : "8D OFF",
                toggle8DPendingIntent
        );

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
                new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(1, 2);

        builder.setStyle(mediaStyle);

        return builder.build();
    }

    private void updateNotification(boolean isPlaying) {
        Notification notification = createNotification(isPlaying);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    public void setSongMetadata(String songName, Bitmap albumArt) {
        this.currentSongName = songName != null ? songName : "SpatialFlow";
        this.currentAlbumArt = albumArt;
        updateMediaMetadata();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            updateNotification(true);
        }
    }

    private void setupMediaPlayerListeners() {
        mediaPlayer.setOnCompletionListener(mp -> {
            Log.d(TAG, "Playback completed");
            if (viewModel != null) {
                viewModel.setIsPlaying(false);
                viewModel.setCurrentPosition(0);
            }
            stopProgressTracking();
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
            updateNotification(false);
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
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    if (viewModel != null) {
                        viewModel.setCurrentPosition(mediaPlayer.getCurrentPosition());
                    }
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    handler.postDelayed(this, 500);
                }
            }
        };
    }

    private void initializeAudioEffects() {
        try {
            int audioSessionId = mediaPlayer.getAudioSessionId();
            releaseAudioEffects();

            bassBoostEffect = new BassBoost(0, audioSessionId);
            bassBoostEffect.setEnabled(false);

            equalizerEffect = new Equalizer(0, audioSessionId);
            equalizerEffect.setEnabled(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                loudnessEnhancer.setEnabled(false);
            }

            Log.d(TAG, "AudioEffects initialized for session: " + audioSessionId);

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

    // ===== REAL-TIME EFFECT CONTROLS =====

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

    public void setBassBoost(int boostDb) {
        if (bassBoostEffect != null) {
            try {
                int strength = (int) (((boostDb + 15) / 30.0f) * 1000);
                strength = Math.max(0, Math.min(1000, strength));
                bassBoostEffect.setStrength((short) strength);
                Log.d(TAG, "BassBoost: " + strength);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set bass: " + e.getMessage());
            }
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
            float leftVol = 1.0f;
            float rightVol = 1.0f;

            if (balanceValue < 0) {
                rightVol = 1.0f + (balanceValue / 50.0f);
            } else if (balanceValue > 0) {
                leftVol = 1.0f - (balanceValue / 50.0f);
            }

            mediaPlayer.setVolume(leftVol, rightVol);
            Log.d(TAG, "Balance: " + balanceValue);
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
            try {
                android.media.PlaybackParams params = mediaPlayer.getPlaybackParams();
                params.setSpeed(speed);
                mediaPlayer.setPlaybackParams(params);
                Log.d(TAG, "Speed: " + speed + "x");
            } catch (Exception e) {
                Log.e(TAG, "Failed to set speed: " + e.getMessage());
            }
        }
    }

    // ===== AUDIO LOADING =====

    public void setViewModel(PlayerSharedViewModel vm) {
        this.viewModel = vm;
        Log.d(TAG, "ViewModel set");
    }

    public void loadAudio(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "URI is null");
            return;
        }

        Log.d(TAG, "Loading audio from URI: " + uri);
        currentSourceUri = uri;

        // Reset processing state when loading new audio
        hasProcessed8D = false;
        last8DSpeed = -1f;
        lastProcessedSourcePath = null;
        currentProcessedFilePath = null;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }

        try {
            currentOriginalFilePath = AudioFileManager.getRealPathFromURI(this, uri);
            if (currentOriginalFilePath == null) {
                Log.e(TAG, "Failed to get file path from URI");
                return;
            }

            Log.d(TAG, "File path: " + currentOriginalFilePath);

            mediaPlayer.reset();
            mediaPlayer.setDataSource(currentOriginalFilePath);
            currentlyLoadedPath = currentOriginalFilePath; // track what is loaded
            mediaPlayer.prepare();

            Log.d(TAG, "Audio loaded, duration: " + mediaPlayer.getDuration());

            if (viewModel != null) {
                viewModel.setDuration(mediaPlayer.getDuration());
                viewModel.setCurrentPosition(0);
            }

            updateMediaMetadata();

        } catch (IOException e) {
            Log.e(TAG, "Error loading audio: " + e.getMessage(), e);
        }
    }

    // ===== 8D FFMPEG PROCESSING WITH DUPLICATE PREVENTION =====

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

        // ===== CRITICAL: Check if we need to reprocess =====
        String currentSourcePath = AudioFileManager.getRealPathFromURI(this, currentSourceUri);

        if (!enable8D) {
            Log.d(TAG, "8D disabled, loading original");
            is8DEnabled = false;
            hasProcessed8D = false;
            loadOriginalAudio();
            return;
        }

        // Check if already processed with same parameters
        boolean sameSource = currentSourcePath != null &&
                currentSourcePath.equals(lastProcessedSourcePath);
        boolean sameSpeed = Math.abs(speed8D - last8DSpeed) < 0.01f;

        if (hasProcessed8D && sameSource && sameSpeed && currentProcessedFilePath != null) {
            Log.d(TAG, "8D already processed with same parameters, skipping reprocessing");

            // Just ensure we're playing the processed file if not already
            if (!isCurrentlyPlayingProcessedFile()) {
                loadProcessedAudio();
            }

            is8DEnabled = true;
            updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
            return;
        }

        Log.d(TAG, "Starting NEW 8D processing with FFmpeg");

        isProcessing = true;
        this.is8DEnabled = enable8D;

        if (viewModel != null) {
            handler.post(() -> {
                viewModel.setIsProcessing(true);
                viewModel.setProcessingProgress(0);
            });
        }

        boolean wasPlaying = mediaPlayer.isPlaying();
        if (wasPlaying) {
            savedPosition = mediaPlayer.getCurrentPosition();
            pause();
        }

        String inputPath = currentSourcePath;
        if (inputPath == null) {
            Log.e(TAG, "Input path is null");
            finishProcessing(false);
            return;
        }

        File outputFile = new File(getCacheDir(), "8d_audio_" + System.currentTimeMillis() + ".m4a");
        String outputPath = outputFile.getAbsolutePath();

        String command = FFmpegCommandBuilder.build8D(inputPath, outputPath, speed8D);

        Log.d(TAG, "FFmpeg command: " + command);
        Log.d(TAG, "Input: " + inputPath);
        Log.d(TAG, "Output: " + outputPath);

        final int songDuration = mediaPlayer.getDuration();

        FFmpegKit.executeAsync(command,
                session -> {
                    ReturnCode returnCode = session.getReturnCode();
                    Log.d(TAG, "FFmpeg session completed with code: " + returnCode);

                    if (ReturnCode.isSuccess(returnCode)) {
                        Log.d(TAG, "FFmpeg SUCCESS - 8D processing complete");

                        // Save processing state
                        currentProcessedFilePath = outputPath;
                        hasProcessed8D = true;
                        last8DSpeed = speed8D;
                        lastProcessedSourcePath = inputPath;

                        handler.post(() -> {
                            try {
                                mediaPlayer.reset();
                                mediaPlayer.setDataSource(outputPath);
                                currentlyLoadedPath = outputPath; // track processed file loaded
                                mediaPlayer.prepare();

                                Log.d(TAG, "8D file loaded successfully");

                                finishProcessing(true);

                                if (wasPlaying) {
                                    mediaPlayer.seekTo(savedPosition);
                                    play();
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "Error loading 8D audio: " + e.getMessage(), e);
                                hasProcessed8D = false;
                                finishProcessing(false);
                            }
                        });
                    } else {
                        Log.e(TAG, "FFmpeg FAILED: " + returnCode);
                        String output = session.getOutput();
                        String failLog = session.getFailStackTrace();
                        Log.e(TAG, "Output: " + output);
                        Log.e(TAG, "Error: " + failLog);
                        hasProcessed8D = false;
                        handler.post(() -> finishProcessing(false));
                    }
                },
                log -> Log.d(TAG, "FFmpeg log: " + log.getMessage()),
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
                }
        );
    }

    private boolean isCurrentlyPlayingProcessedFile() {
        if (mediaPlayer == null || currentProcessedFilePath == null) {
            return false;
        }

        try {
            return currentlyLoadedPath != null && currentlyLoadedPath.equals(currentProcessedFilePath);
        } catch (Exception e) {
            Log.e(TAG, "Error checking processed file: " + e.getMessage());
            return false;
        }
    }

    private void loadProcessedAudio() {
        if (currentProcessedFilePath == null || !new File(currentProcessedFilePath).exists()) {
            Log.e(TAG, "Processed file not found");
            return;
        }

        boolean wasPlaying = mediaPlayer.isPlaying();
        int position = mediaPlayer.getCurrentPosition();

        if (wasPlaying) {
            pause();
        }

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(currentProcessedFilePath);
            currentlyLoadedPath = currentProcessedFilePath; // track what is loaded
            mediaPlayer.prepare();

            if (wasPlaying) {
                mediaPlayer.seekTo(position);
                play();
            }

            Log.d(TAG, "Processed audio re-loaded");
        } catch (IOException e) {
            Log.e(TAG, "Error loading processed audio: " + e.getMessage(), e);
        }
    }

    private void loadOriginalAudio() {
        if (currentSourceUri == null) return;

        boolean wasPlaying = mediaPlayer.isPlaying();
        int position = mediaPlayer.getCurrentPosition();

        if (wasPlaying) {
            pause();
        }

        try {
            String originalPath = AudioFileManager.getRealPathFromURI(this, currentSourceUri);
            if (originalPath != null) {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(originalPath);
                currentlyLoadedPath = originalPath; // track what is loaded
                mediaPlayer.prepare();

                if (wasPlaying) {
                    mediaPlayer.seekTo(position);
                    play();
                }

                Log.d(TAG, "Original audio loaded");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading original: " + e.getMessage(), e);
        }
    }

    private void finishProcessing(boolean success) {
        isProcessing = false;
        if (viewModel != null) {
            viewModel.postIsProcessing(false);
            if (success) {
                handler.post(() -> viewModel.setProcessingProgress(100));
            } else {
                handler.post(() -> viewModel.setProcessingProgress(0));
            }
        }
        updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
        Log.d(TAG, "Processing finished: " + (success ? "SUCCESS" : "FAILED"));
    }

    // ===== GETTERS FOR STATE (for fragments to sync UI) =====

    public boolean is8DEnabled() {
        return is8DEnabled;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    // ===== PLAYBACK CONTROLS =====

    public void play() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
                if (viewModel != null) {
                    viewModel.setIsPlaying(true);
                }
                handler.post(progressRunnable);
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
                startForeground(NOTIFICATION_ID, createNotification(true));
                Log.d(TAG, "Playback started");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot start: " + e.getMessage(), e);
            }
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (viewModel != null) {
                viewModel.setIsPlaying(false);
            }
            stopProgressTracking();
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createNotification(false));
            }

            Log.d(TAG, "Playback paused");
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.prepare();
                if (viewModel != null) {
                    viewModel.setIsPlaying(false);
                    viewModel.setCurrentPosition(0);
                }
                stopProgressTracking();
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
                stopForeground(true);
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
                updatePlaybackState(mediaPlayer.isPlaying() ?
                        PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                Log.d(TAG, "Seeked to: " + position);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Cannot seek: " + e.getMessage(), e);
            }
        }
    }

    private void stopProgressTracking() {
        handler.removeCallbacks(progressRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    play();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_STOP:
                    stop();
                    break;
                case ACTION_TOGGLE_8D:
                    Log.d(TAG, "Toggle 8D pressed");
                    if (viewModel != null) {
                        boolean current = viewModel.getIs8DEnabled().getValue() != null &&
                                viewModel.getIs8DEnabled().getValue();
                        Log.d(TAG, "8D state: " + current + " -> " + !current);
                        viewModel.set8DEnabled(!current);
                        viewModel.triggerReprocessing();
                    }
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
        if (mediaSession != null) {
            mediaSession.release();
        }
        stopProgressTracking();
    }
}


