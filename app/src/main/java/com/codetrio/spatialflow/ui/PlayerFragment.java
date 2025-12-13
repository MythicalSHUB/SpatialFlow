package com.codetrio.spatialflow.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.service.AudioPlaybackService;
import com.codetrio.spatialflow.util.AudioFileManager;
import com.codetrio.spatialflow.util.FFmpegCommandBuilder;
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.arthenica.ffmpegkit.FFmpegKit;

import java.io.File;

public class PlayerFragment extends Fragment {

    private static final String TAG = "PlayerFragment";
    private static final int PICK_AUDIO_REQUEST = 1;

    private PlayerSharedViewModel viewModel;
    private AudioPlaybackService audioService;
    private boolean serviceBound = false;

    private View rootView;
    private ImageView ivAlbumArt;
    private MaterialTextView tvSongName;
    private MaterialTextView tvCurrentTime;
    private MaterialTextView tvTotalTime;
    private Slider seekBar;

    private FloatingActionButton btnPlayPauseToggle;
    private FloatingActionButton btnStop;
    private MaterialButton btnChangeSong;
    private MaterialButton btnSavePreset;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlaybackService.LocalBinder binder = (AudioPlaybackService.LocalBinder) service;
            audioService = binder.getService();
            serviceBound = true;
            viewModel.setAudioService(audioService);
            Log.d(TAG, "Service connected and bound to ViewModel");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            Log.d(TAG, "Service disconnected");
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_player, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(PlayerSharedViewModel.class);

        initViews(rootView);
        setupObservers();
        setupListeners();

        // Bind to service
        Intent intent = new Intent(getContext(), AudioPlaybackService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Enable marquee scrolling for long titles
        tvSongName.setSelected(true);

        return rootView;
    }

    private void initViews(View view) {
        ivAlbumArt = view.findViewById(R.id.ivAlbumArt);
        tvSongName = view.findViewById(R.id.tvSongName);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        seekBar = view.findViewById(R.id.seekBar);

        btnPlayPauseToggle = view.findViewById(R.id.btnPlayPauseToggle);
        btnStop = view.findViewById(R.id.btnStop);
        btnChangeSong = view.findViewById(R.id.btnChangeSong);
        btnSavePreset = view.findViewById(R.id.btnSavePreset);
    }

    private void setupObservers() {
        viewModel.getIsPlaying().observe(getViewLifecycleOwner(), isPlaying -> {
            if (isPlaying) {
                btnPlayPauseToggle.setImageResource(R.drawable.ic_pause);
                btnPlayPauseToggle.setContentDescription("Pause");
            } else {
                btnPlayPauseToggle.setImageResource(R.drawable.ic_play);
                btnPlayPauseToggle.setContentDescription("Play");
            }
        });

        viewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> {
            seekBar.setValue(position);
            tvCurrentTime.setText(formatTime(position));
        });

        viewModel.getDuration().observe(getViewLifecycleOwner(), duration -> {
            seekBar.setValueTo(duration > 0 ? duration : 100);
            tvTotalTime.setText(formatTime(duration));
        });

        viewModel.getSongUri().observe(getViewLifecycleOwner(), uri -> {
            if (uri != null) {
                loadSongMetadata(uri);
            }
        });
    }

    private void setupListeners() {
        btnPlayPauseToggle.setOnClickListener(v -> {
            Boolean isPlaying = viewModel.getIsPlaying().getValue();
            if (isPlaying != null && isPlaying) {
                viewModel.pauseAudio();
            } else {
                viewModel.playAudio();
            }
        });

        btnStop.setOnClickListener(v -> viewModel.stopAudio());
        btnChangeSong.setOnClickListener(v -> openFilePicker());
        btnSavePreset.setOnClickListener(v -> saveAudioWithEffects());

        seekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {}

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                viewModel.seekTo((int) slider.getValue());
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri audioUri = data.getData();
                viewModel.setSongUri(audioUri);
            }
        }
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
                    if (artist != null && !artist.isEmpty()) {
                        displayName = artist + " - " + title;
                    } else {
                        displayName = title;
                    }
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
                Log.e(TAG, "Error loading metadata: " + e.getMessage(), e);
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
                    uri,
                    new String[]{MediaStore.Audio.Media.DISPLAY_NAME},
                    null,
                    null,
                    null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name: " + e.getMessage());
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
            showSnackbar("No song selected to save", Snackbar.LENGTH_SHORT);
            return;
        }

        Boolean is8D = viewModel.getIs8DEnabled().getValue();
        Boolean isBass = viewModel.getIsBassEnabled().getValue();

        if ((is8D == null || !is8D) && (isBass == null || !isBass)) {
            showSnackbar("Enable at least one effect before saving", Snackbar.LENGTH_LONG);
            return;
        }

        // Show processing snackbar anchored above nav
        Snackbar processingSnackbar = Snackbar.make(rootView, "Processing audio...", Snackbar.LENGTH_INDEFINITE);
        View bottomNav = getActivity().findViewById(R.id.nav_view);
        if (bottomNav != null) {
            processingSnackbar.setAnchorView(bottomNav);
        }
        processingSnackbar.show();

        // Run FFmpeg processing in background
        new Thread(() -> {
            try {
                String inputPath = AudioFileManager.getRealPathFromURI(getContext(), currentUri);
                if (inputPath == null) {
                    dismissSnackbarAndShow(processingSnackbar, "Could not access audio file", Snackbar.LENGTH_SHORT);
                    return;
                }

                String fileName = "Spatial_" + getFileNameFromUri(currentUri);
                File outputFile = AudioFileManager.createOutputFile(getContext(), fileName);
                String outputPath = outputFile.getAbsolutePath();

                boolean enable8D = is8D != null && is8D;
                boolean enableBass = isBass != null && isBass;
                float rotationSpeed = viewModel.get8DSpeed().getValue() != null ? viewModel.get8DSpeed().getValue() : 0.2f;
                int bassGain = viewModel.getBassBoost().getValue() != null ? viewModel.getBassBoost().getValue() : 5;

                // Only 8D is processed via FFmpeg now
                String command = FFmpegCommandBuilder.build8D(
                        inputPath,
                        outputPath,
                        0.2f
                );

                Log.d(TAG, "Executing save command: " + command);

                // Execute FFmpeg synchronously for saving
                FFmpegKit.execute(command);

                // Check if output file was created successfully
                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.d(TAG, "File saved successfully: " + outputPath);

                    // Copy to MediaStore for Android 10+
                    AudioFileManager.scanFile(getContext(), outputFile);

                    // Show success snackbar with action
                    dismissSnackbarAndShowWithAction(processingSnackbar,
                            "✓ Saved to Downloads/SpatialFlow",
                            Snackbar.LENGTH_LONG,
                            outputFile);
                } else {
                    Log.e(TAG, "Output file was not created or is empty");
                    dismissSnackbarAndShow(processingSnackbar, "Failed to save audio", Snackbar.LENGTH_SHORT);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error saving audio: " + e.getMessage(), e);
                dismissSnackbarAndShow(processingSnackbar, "Error: " + e.getMessage(), Snackbar.LENGTH_LONG);
            }
        }).start();
    }

    private void showSnackbar(String message, int duration) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                Snackbar snackbar = Snackbar.make(rootView, message, duration);

                // Anchor above bottom navigation
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null) {
                    snackbar.setAnchorView(bottomNav);
                }

                snackbar.show();
            });
        }
    }

    private void dismissSnackbarAndShow(Snackbar oldSnackbar, String message, int duration) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                if (oldSnackbar != null) {
                    oldSnackbar.dismiss();
                }

                Snackbar snackbar = Snackbar.make(rootView, message, duration);

                // Anchor above bottom navigation
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null) {
                    snackbar.setAnchorView(bottomNav);
                }

                snackbar.show();
            });
        }
    }

    private void dismissSnackbarAndShowWithAction(Snackbar oldSnackbar, String message, int duration, File outputFile) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                if (oldSnackbar != null) {
                    oldSnackbar.dismiss();
                }

                Snackbar snackbar = Snackbar.make(rootView, message, duration);

                // Anchor above bottom navigation
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null) {
                    snackbar.setAnchorView(bottomNav);
                }

                snackbar.setAction("SHOW", v -> {
                    openSpatialFlowFolder(outputFile);
                });

                snackbar.show();
            });
        }
    }

    /**
     * Opens Downloads/SpatialFlow folder in file manager
     * Uses multiple fallback methods for compatibility
     */
    private void openSpatialFlowFolder(File outputFile) {
        try {
            // Method 1: Try to open with file manager using direct path
            if (openFolderWithFileManager(outputFile.getParentFile())) {
                return;
            }

            // Method 2: Try to open Downloads and show navigation hint
            if (openDownloadsFolderWithHint()) {
                return;
            }

            // Method 3: Open any file manager app
            openAnyFileManager();

        } catch (Exception e) {
            Log.e(TAG, "All methods failed: " + e.getMessage());
            showFolderLocationToast(outputFile);
        }
    }

    /**
     * Method 1: Open folder directly with file manager
     */
    private boolean openFolderWithFileManager(File folder) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(folder), "resource/folder");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "Method 1 failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Method 2: Open Downloads folder with navigation hint
     */
    private boolean openDownloadsFolderWithHint() {
        try {
            // Get Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(downloadsDir), "*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(getContext(), "Navigate to 'SpatialFlow' folder", Toast.LENGTH_LONG).show();
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "Method 2 failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Method 3: Open any available file manager
     */
    private void openAnyFileManager() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(Intent.createChooser(intent, "Open File Manager"));
                Toast.makeText(getContext(), "Go to Downloads/SpatialFlow", Toast.LENGTH_LONG).show();
            } else {
                throw new Exception("No file manager found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Method 3 failed: " + e.getMessage());
            throw new RuntimeException("No file manager available");
        }
    }

    /**
     * Shows toast with exact file location
     */
    private void showFolderLocationToast(File outputFile) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(),
                            "File saved to:\nDownloads/SpatialFlow/\n" + outputFile.getName(),
                            Toast.LENGTH_LONG).show()
            );
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (serviceBound) {
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}