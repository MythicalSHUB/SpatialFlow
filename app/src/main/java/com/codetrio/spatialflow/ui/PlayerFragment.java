package com.codetrio.spatialflow.ui;

import android.annotation.SuppressLint;
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
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Vibrator;
import android.os.Build;
import android.os.VibrationEffect;
import android.animation.ValueAnimator;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.transition.MaterialContainerTransform;
import com.google.android.material.color.MaterialColors;

import com.codetrio.spatialflow.MainActivity;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.model.SongItem;
import com.codetrio.spatialflow.service.AudioPlaybackService;
import com.codetrio.spatialflow.ui.adapter.SongLibraryAdapter;
import com.codetrio.spatialflow.ui.custom.AnimatedMeshGradientView;
import com.codetrio.spatialflow.util.AudioFileManager;
import com.codetrio.spatialflow.util.FFmpegCommandBuilder;
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    // UI Components - consolidated player sheet
    private View rootView;
    private CoordinatorLayout coordinatorLayout;
    private MaterialCardView playerBottomSheet;
    private BottomSheetBehavior<MaterialCardView> bottomSheetBehavior;
    private AnimatedMeshGradientView gradientBackground;
    private ConstraintLayout miniPlayerContent;
    private ConstraintLayout fullPlayerContent;
    private MaterialCardView cardAlbumArt;
    private ImageView ivAlbumArt;
    private MaterialTextView tvSongName;
    private MaterialTextView tvArtistName;
    private MaterialTextView tvCurrentTime;
    private MaterialTextView tvTotalTime;
    private MaterialTextView tvNowPlaying;
    private MaterialTextView tvLibrarySongCount;
    private Slider seekBar;
    private MaterialButton btnPlayPauseToggle;
    private MaterialButton btnPrevious, btnNext;
    private MaterialButton btnShuffle, btnRepeat, btnFavorite;
    private MaterialCardView secondaryControlsCard;
    private Chip chipSongHaptics;
    private LinearProgressIndicator waveProgress;

    // Mini Player components (inside sheet)
    private ImageView ivMiniAlbumArt;
    private MaterialTextView tvMiniSongName;
    private MaterialTextView tvMiniArtistName;
    private LinearProgressIndicator miniProgress;
    private MaterialButton btnMiniPlayPause, btnMiniPrevious, btnMiniNext;

    // Song Library
    private RecyclerView rvSongLibrary;
    private SongLibraryAdapter songAdapter;

    // Player sheet state
    private boolean isPlayerExpanded = false;
    private int[] currentGradientColors = null;
    private boolean isDarkMode; // Detected dynamically

    private Visualizer visualizer;
    private Handler hapticHandler;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isUserSeeking = false;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private OnBackPressedCallback onBackPressedCallback;

    private int[] previousGradientColors;
    private long currentSongIdInView = -1;
    private ValueAnimator waveAnimator; // Smooth wave amplitude animator
    private int currentWaveAmplitude = 0;

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
            Log.d(TAG, "Service connected");
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_player, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(PlayerSharedViewModel.class);

        // Detect Theme Immediately
        int nightModeFlags = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // Intercept back-press to collapse player
        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (bottomSheetBehavior != null
                        && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    collapsePlayer();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);

        setupPermissionLauncher();
        initViews(rootView);
        setupSwipeGesture();
        initHapticsSystem();
        setupObservers();
        setupListeners();
        startProgressLoop();

        Intent intent = new Intent(getContext(), AudioPlaybackService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        tvSongName.setSelected(true);

        // Apply initial dynamic calibration (handles "No Song Selected" state)
        // Calling unconditionally to ensure proper Light/Dark mode start
        applyMaterialDynamicCalibration();

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (visualizer != null) {
            visualizer.setEnabled(true);
        }
        updateSystemBars();

        // Restore mini player visibility if a song is playing or was played
        if (viewModel != null && viewModel.getCurrentSong().getValue() != null) {
            if (bottomSheetBehavior != null && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        }

        // Re-enable haptics if they were enabled before fragment switch
        Boolean hapticsEnabled = viewModel != null ? viewModel.getIsHapticsEnabled().getValue() : null;
        if (hapticsEnabled != null && hapticsEnabled && audioService != null) {
            // Release old visualizer first to avoid state errors
            releaseVisualizer();

            // Small delay to ensure clean state before re-init
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                initAdvancedHaptics();
                if (visualizer != null) {
                    try {
                        visualizer.setEnabled(true);
                        Log.d(TAG, "Haptics re-enabled on resume");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to re-enable haptics: " + e.getMessage());
                    }
                }
            }, 100);
        }

        Log.d(TAG, "Fragment resumed");
    }

    private void updateSystemBars() {
        if (getActivity() == null || getContext() == null)
            return;
        android.view.Window window = getActivity().getWindow();

        // Detect System Theme using context
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        this.isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        androidx.core.view.WindowInsetsControllerCompat insetsController = androidx.core.view.WindowCompat
                .getInsetsController(window, window.getDecorView());

        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isDarkMode);
            insetsController.setAppearanceLightNavigationBars(!isDarkMode);
        }
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
                        enableAdvancedHaptics();
                    } else {
                        Log.w(TAG, "RECORD_AUDIO denied");
                        viewModel.setHapticsEnabled(false);
                        showSnackbar("Microphone permission required", Snackbar.LENGTH_LONG);
                    }
                });
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordAudioPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Microphone Permission")
                    .setMessage(
                            "Music Haptics analyzes frequencies for beat-synced vibrations. Audio is processed locally, never recorded.")
                    .setPositiveButton("Grant",
                            (d, w) -> requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO))
                    .setNegativeButton("Cancel", (d, w) -> {
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
        // Main containers
        coordinatorLayout = view.findViewById(R.id.coordinatorLayout);
        playerBottomSheet = view.findViewById(R.id.playerBottomSheet);
        fullPlayerContent = view.findViewById(R.id.fullPlayerContent);
        miniPlayerContent = view.findViewById(R.id.miniPlayerContent);

        setupBottomSheet();

        gradientBackground = view.findViewById(R.id.gradientBackground);

        // Full Player components
        cardAlbumArt = view.findViewById(R.id.cardAlbumArt);
        ivAlbumArt = view.findViewById(R.id.ivAlbumArt);
        tvSongName = view.findViewById(R.id.tvSongName);
        tvArtistName = view.findViewById(R.id.tvArtistName);
        tvNowPlaying = view.findViewById(R.id.tvNowPlaying);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        tvLibrarySongCount = view.findViewById(R.id.tvLibrarySongCount);
        seekBar = view.findViewById(R.id.seekBar);

        btnPlayPauseToggle = view.findViewById(R.id.btnPlayPauseToggle);
        btnPrevious = view.findViewById(R.id.btnPrevious);
        btnNext = view.findViewById(R.id.btnNext);

        // Secondary controls (shuffle, repeat, favorite)
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnRepeat = view.findViewById(R.id.btnRepeat);
        btnFavorite = view.findViewById(R.id.btnFavorite);
        secondaryControlsCard = view.findViewById(R.id.secondaryControls);

        chipSongHaptics = view.findViewById(R.id.chipSongHaptics);
        waveProgress = view.findViewById(R.id.waveProgress);

        // Mini Player components (re-mapped to new IDs inside sheet)
        ivMiniAlbumArt = view.findViewById(R.id.ivMiniAlbumArt);
        tvMiniSongName = view.findViewById(R.id.tvMiniSongName);
        tvMiniArtistName = view.findViewById(R.id.tvMiniArtistName);
        miniProgress = view.findViewById(R.id.miniProgress);
        btnMiniPlayPause = view.findViewById(R.id.btnMiniPlayPause);
        btnMiniPrevious = view.findViewById(R.id.btnMiniPrevious);
        btnMiniNext = view.findViewById(R.id.btnMiniNext);

        // Song Library
        rvSongLibrary = view.findViewById(R.id.rvSongLibrary);
        setupSongLibrary();

        // Handle Window Insets for Edge-to-Edge
        View libraryContent = view.findViewById(R.id.libraryContent);
        if (libraryContent != null) {
            ViewCompat.setOnApplyWindowInsetsListener(libraryContent, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
                return windowInsets;
            });
        }

        if (fullPlayerContent != null) {
            ViewCompat.setOnApplyWindowInsetsListener(fullPlayerContent, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
                return windowInsets;
            });
        }

        progressHandler = new Handler(Looper.getMainLooper());
    }

    private void setupSongLibrary() {
        songAdapter = new SongLibraryAdapter((song, position) -> {
            viewModel.playSongAtIndex(position);
            expandPlayer();
        });

        rvSongLibrary.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSongLibrary.setAdapter(songAdapter);

        loadSongLibrary();
    }

    private void setupBottomSheet() {
        if (playerBottomSheet == null)
            return;

        bottomSheetBehavior = BottomSheetBehavior.from(playerBottomSheet);
        bottomSheetBehavior.setHideable(false); // Restrict swipe down from mini
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Peek height = Mini Player (80dp) + BottomNav (80dp) + bottom margin (8dp)
        int navHeight = (int) (80 * getResources().getDisplayMetrics().density);
        int miniHeight = (int) (80 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        bottomSheetBehavior.setPeekHeight(navHeight + miniHeight + margin);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                isPlayerExpanded = (newState == BottomSheetBehavior.STATE_EXPANDED);
                if (onBackPressedCallback != null) {
                    onBackPressedCallback.setEnabled(isPlayerExpanded);
                }

                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setBottomNavVisibility(false);
                    }
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setBottomNavVisibility(true);
                    }
                    // Reset transforms when collapsed
                    if (cardAlbumArt != null) {
                        cardAlbumArt.setScaleX(1.0f);
                        cardAlbumArt.setScaleY(1.0f);
                        cardAlbumArt.setTranslationX(0f);
                        cardAlbumArt.setTranslationY(0f);
                    }
                    if (miniPlayerContent != null) {
                        miniPlayerContent.setAlpha(1.0f);
                        miniPlayerContent.setVisibility(View.VISIBLE);
                    }
                    if (playerBottomSheet != null) {
                        // Reset to capsule look
                        playerBottomSheet.setRadius(12 * getResources().getDisplayMetrics().density);
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) playerBottomSheet
                                .getLayoutParams();
                        int margin = (int) (8 * getResources().getDisplayMetrics().density);
                        lp.setMargins(0, 0, 0, margin);
                        playerBottomSheet.setLayoutParams(lp);
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // 1. Capsule to Full-Screen Morph (Margins and Corners)
                float density = getResources().getDisplayMetrics().density;
                float currentBottomMargin = 8 * (1.0f - slideOffset) * density;
                float currentRadius = (12 - (12 * slideOffset)) * density;

                if (playerBottomSheet.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) playerBottomSheet
                            .getLayoutParams();
                    lp.setMargins(0, 0, 0, (int) currentBottomMargin);
                    playerBottomSheet.setLayoutParams(lp);
                }
                playerBottomSheet.setRadius(currentRadius);

                // 2. Alpha Cross-Fade for main containers
                // Mini content fades out almost immediately to make room for growing arc
                float miniAlpha = 1.0f - (slideOffset * 5.0f); // VERY fast fade
                miniPlayerContent.setAlpha(Math.max(0f, miniAlpha));
                miniPlayerContent.setVisibility(miniAlpha > 0.01f ? View.VISIBLE : View.GONE);

                // Full content fades in softly
                float fullAlpha = (slideOffset - 0.15f) * 1.5f;
                fullPlayerContent.setAlpha(Math.max(0f, fullAlpha));
                fullPlayerContent.setVisibility(fullAlpha > 0.05f ? View.VISIBLE : View.GONE);

                // 3. "Premium Growth" Transform for Album Art
                if (ivMiniAlbumArt != null && cardAlbumArt != null) {
                    // Full Art target is its center layout position.
                    // We calculate where ivMiniAlbumArt is relative to the sheet.

                    float miniSize = ivMiniAlbumArt.getWidth();
                    float fullSize = cardAlbumArt.getWidth();
                    if (fullSize <= 0)
                        fullSize = (int) (320 * getResources().getDisplayMetrics().density); // Est.

                    float startScale = miniSize / fullSize;

                    // Center-based position mapping relative to their parent
                    float miniCenterX = ivMiniAlbumArt.getLeft() + (miniSize / 2f);
                    float miniCenterY = ivMiniAlbumArt.getTop() + (miniSize / 2f);
                    float fullCenterX = cardAlbumArt.getLeft() + (fullSize / 2f);
                    float fullCenterY = cardAlbumArt.getTop() + (fullSize / 2f);

                    float startX = miniCenterX - fullCenterX;
                    float startY = miniCenterY - fullCenterY;

                    float currentScale = startScale + (slideOffset * (1.0f - startScale));
                    float currentX = startX * (1.0f - slideOffset);
                    float currentY = startY * (1.0f - slideOffset);

                    cardAlbumArt.setScaleX(currentScale);
                    cardAlbumArt.setScaleY(currentScale);
                    cardAlbumArt.setTranslationX(currentX);
                    cardAlbumArt.setTranslationY(currentY);

                    // Also animate the corner radius of the album art card
                    float artCorner = (12 + (20 * slideOffset)) * density;
                    cardAlbumArt.setRadius(artCorner);
                    cardAlbumArt.setAlpha(1.0f);
                }

                // 4. Mesh Background Parallax/Scale
                if (gradientBackground != null) {
                    float bgScale = 1.0f + (slideOffset * 0.15f);
                    gradientBackground.setScaleX(bgScale);
                    gradientBackground.setScaleY(bgScale);
                    gradientBackground.setAlpha(0.7f + (slideOffset * 0.3f));
                }

                // 4. Smooth BottomNav slide
                if (getActivity() instanceof MainActivity) {
                    MainActivity main = (MainActivity) getActivity();
                    View navView = main.findViewById(R.id.nav_view);
                    if (navView != null) {
                        float translationY = slideOffset * (navView.getHeight() + 100);
                        main.setBottomNavTranslation(translationY);
                    }
                }
            }
        });

        // Tap mini player to expand
        miniPlayerContent.setOnClickListener(v -> expandPlayer());
    }

    private void expandPlayer() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void collapsePlayer() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSwipeGesture() {
        if (fullPlayerContent == null)
            return;

        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(
                getContext(),
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_THRESHOLD = 100;
                    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

                    @Override
                    public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2,
                            float velocityX, float velocityY) {
                        if (e1 == null || e2 == null)
                            return false;

                        float diffY = e2.getY() - e1.getY();
                        if (diffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            if (isPlayerExpanded) {
                                collapsePlayer();
                                return true;
                            }
                        }
                        return false;
                    }
                });

        fullPlayerContent.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    // =========================
    // ANIMATED GRADIENT METHODS (UPDATED!)
    // =========================

    /**
     * Extract dominant colors from album art and create Apple Music-style gradient.
     * Now applies ANIMATED gradients to all three components.
     */
    private void extractGradientFromAlbumArt(Uri artUri) {
        if (artUri == null || gradientBackground == null || getContext() == null)
            return;

        Glide.with(this)
                .asBitmap()
                .load(artUri)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap,
                            @Nullable Transition<? super Bitmap> transition) {
                        Palette.from(bitmap).generate(palette -> {
                            if (palette == null || getContext() == null)
                                return;

                            // System Theme Check
                            int nightModeFlags = getContext().getResources().getConfiguration().uiMode
                                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                            PlayerFragment.this.isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

                            // Comprehensive Palette Extraction
                            int vibrant = palette.getVibrantColor(0xFF1a1a2e);
                            int darkVibrant = palette.getDarkVibrantColor(0xFF16213e);
                            int lightVibrant = palette.getLightVibrantColor(vibrant);
                            int muted = palette.getMutedColor(0xFF0f0f23);
                            int darkMuted = palette.getDarkMutedColor(0xFF0f0f23);
                            int lightMuted = palette.getLightMutedColor(muted);
                            int dominant = palette.getDominantColor(0xFF000000);

                            // 1. Mesh Gradient Background
                            int[] colors;
                            if (isDarkMode) {
                                colors = new int[] {
                                        darkenColor(vibrant, 0.85f),
                                        darkenColor(darkVibrant, 0.8f),
                                        darkenColor(muted, 0.7f),
                                };
                            } else {
                                // Light Mode: Use "Bright Colors" as requested
                                // We use 30% lightening to keep them vibrant but airy
                                colors = new int[] {
                                        lightenColor(vibrant, 0.3f),
                                        lightenColor(lightVibrant, 0.2f),
                                        lightenColor(vibrant, 0.4f), // Mix in another vibrant shade
                                };
                            }
                            gradientBackground.setColors(colors);
                            gradientBackground.setIsDarkMode(isDarkMode);

                            // 2. Text and Icon Color Logic
                            int primaryTextColor = isDarkMode ? android.graphics.Color.WHITE
                                    : 0xE6000000; // 90% black for premium look
                            int secondaryTextColor = isDarkMode ? 0xB3FFFFFF : 0x99000000; // 60% black

                            if (tvSongName != null)
                                tvSongName.setTextColor(primaryTextColor);
                            if (tvArtistName != null)
                                tvArtistName.setTextColor(secondaryTextColor);
                            if (tvNowPlaying != null)
                                tvNowPlaying.setTextColor(primaryTextColor);
                            if (tvCurrentTime != null)
                                tvCurrentTime.setTextColor(secondaryTextColor);
                            if (tvTotalTime != null)
                                tvTotalTime.setTextColor(secondaryTextColor);

                            // 3. Main Controls (High Contrast Vibrant)
                            if (btnPlayPauseToggle != null) {
                                btnPlayPauseToggle
                                        .setBackgroundTintList(android.content.res.ColorStateList.valueOf(vibrant));
                                double luminance = getLuminance(vibrant);
                                int iconTint = luminance > 0.5 ? android.graphics.Color.BLACK
                                        : android.graphics.Color.WHITE;
                                btnPlayPauseToggle.setIconTint(android.content.res.ColorStateList.valueOf(iconTint));
                            }

                            // Adjust skip buttons for readability - punchier in light mode
                            int skipBgColor = isDarkMode ? (lightVibrant & 0x00FFFFFF) | 0x4D000000
                                    : (darkVibrant & 0x00FFFFFF) | 0x26000000; // 15% opacity for better contrast
                            int skipIconTint = isDarkMode ? android.graphics.Color.WHITE : 0xCC000000; // 80% black

                            if (btnPrevious != null) {
                                btnPrevious
                                        .setBackgroundTintList(android.content.res.ColorStateList.valueOf(skipBgColor));
                                btnPrevious.setIconTint(android.content.res.ColorStateList.valueOf(skipIconTint));
                            }
                            if (btnNext != null) {
                                btnNext.setBackgroundTintList(android.content.res.ColorStateList.valueOf(skipBgColor));
                                btnNext.setIconTint(android.content.res.ColorStateList.valueOf(skipIconTint));
                            }

                            // 4. Secondary Controls Card (Glass Effect)
                            if (secondaryControlsCard != null) {
                                int cardColor = isDarkMode ? (darkMuted & 0x00FFFFFF) | 0x80000000
                                        : (lightMuted & 0x00FFFFFF) | 0x26000000; // Softer frosted glass
                                secondaryControlsCard.setCardBackgroundColor(cardColor);
                            }

                            int secondaryIconColor = isDarkMode ? android.graphics.Color.WHITE
                                    : 0xCC000000;
                            android.content.res.ColorStateList secondaryIconTint = android.content.res.ColorStateList
                                    .valueOf(secondaryIconColor);
                            if (btnShuffle != null)
                                btnShuffle.setIconTint(secondaryIconTint);
                            if (btnRepeat != null)
                                btnRepeat.setIconTint(secondaryIconTint);
                            if (btnFavorite != null)
                                btnFavorite.setIconTint(secondaryIconTint);

                            // Music Haptics Chip (Glass Style like Secondary Controls)
                            if (chipSongHaptics != null) {
                                int chipBgColor = isDarkMode ? (darkMuted & 0x00FFFFFF) | 0x80000000
                                        : (lightMuted & 0x00FFFFFF) | 0x26000000;

                                chipSongHaptics.setChipBackgroundColor(
                                        android.content.res.ColorStateList.valueOf(chipBgColor));
                                chipSongHaptics.setTextColor(secondaryIconTint);
                                chipSongHaptics.setChipStrokeWidth(0);

                                // Keep Dynamic Icon as requested before
                                chipSongHaptics.setChipIconTint(android.content.res.ColorStateList.valueOf(vibrant));
                                chipSongHaptics.setCheckedIconTint(android.content.res.ColorStateList.valueOf(vibrant));
                                chipSongHaptics.setAlpha(1.0f); // Reset transparency to handle background properly
                            }

                            // 5. Mini Player (Sync with Theme)
                            if (playerBottomSheet != null) {
                                // For light mode, we mix with a bit more darkness to ensure WHITE controls Pop
                                int miniBg = isDarkMode ? (darkVibrant & 0x00FFFFFF) | 0xFF000000
                                        : (lightVibrant & 0x00FFFFFF) | 0xFFE0E0E0; // Off-white for contrast
                                playerBottomSheet.setCardBackgroundColor(miniBg);
                            }

                            if (tvMiniSongName != null)
                                tvMiniSongName.setTextColor(
                                        isDarkMode ? android.graphics.Color.WHITE : android.graphics.Color.BLACK);
                            if (tvMiniArtistName != null)
                                tvMiniArtistName.setTextColor(isDarkMode ? 0xB3FFFFFF : 0x99000000);

                            if (btnMiniPlayPause != null) {
                                btnMiniPlayPause.setBackgroundTintList(
                                        android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                                btnMiniPlayPause.setIconTint(
                                        android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                            }

                            if (btnMiniPrevious != null)
                                btnMiniPrevious.setIconTint(
                                        android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                            if (btnMiniNext != null)
                                btnMiniNext.setIconTint(
                                        android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));

                            if (miniProgress != null) {
                                miniProgress.setIndicatorColor(android.graphics.Color.WHITE);
                                miniProgress.setTrackColor(0x33FFFFFF); // 20% White
                            }

                            // 6. Progress Indicators
                            if (waveProgress != null) {
                                int waveIndicator = isDarkMode ? (vibrant & 0x00FFFFFF) | 0xB3000000
                                        : (vibrant & 0x00FFFFFF) | 0xCC000000;
                                waveProgress.setIndicatorColor(waveIndicator);
                                waveProgress.setTrackColor(isDarkMode ? 0x33FFFFFF : 0x1A000000);
                            }

                            if (seekBar != null) {
                                seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(vibrant));
                                seekBar.setTrackActiveTintList(android.content.res.ColorStateList.valueOf(vibrant));
                                seekBar.setTrackInactiveTintList(android.content.res.ColorStateList.valueOf(
                                        isDarkMode ? 0x33FFFFFF : 0x26000000));
                            }
                        });
                    }

                    private double getLuminance(int color) {
                        return (0.299 * android.graphics.Color.red(color)
                                + 0.587 * android.graphics.Color.green(color)
                                + 0.114 * android.graphics.Color.blue(color)) / 255;
                    }

                    private int lightenColor(int color, float factor) {
                        int a = android.graphics.Color.alpha(color);
                        int r = android.graphics.Color.red(color);
                        int g = android.graphics.Color.green(color);
                        int b = android.graphics.Color.blue(color);

                        r = (int) (r + (255 - r) * factor);
                        g = (int) (g + (255 - g) * factor);
                        b = (int) (b + (255 - b) * factor);

                        return android.graphics.Color.argb(a, r, g, b);
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                    }

                    @Override
                    public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                        applyMaterialDynamicCalibration();
                    }
                });
    }

    private int darkenColor(int color, float factor) {
        int a = android.graphics.Color.alpha(color);
        int r = (int) (android.graphics.Color.red(color) * factor);
        int g = (int) (android.graphics.Color.green(color) * factor);
        int b = (int) (android.graphics.Color.blue(color) * factor);
        return android.graphics.Color.argb(a, r, g, b);
    }

    private void loadSongLibrary() {
        List<SongItem> songs = new ArrayList<>();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder)) {

            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    long albumId = cursor.getLong(albumIdColumn);
                    String path = cursor.getString(dataColumn);
                    long dateAdded = cursor.getLong(dateColumn);

                    songs.add(new SongItem(id, title, artist, albumId, path, dateAdded));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading songs: " + e.getMessage(), e);
        }

        viewModel.setSongList(songs);
        songAdapter.submitList(songs);

        if (tvLibrarySongCount != null) {
            tvLibrarySongCount.setText(songs.size() + " songs");
        }

        Log.d(TAG, "Loaded " + songs.size() + " songs from library");
    }

    /**
     * Check if device has haptic feedback capability.
     */
    private boolean hasHapticsCapability() {
        if (getContext() == null)
            return false;
        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null)
            return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return vibrator.hasVibrator() && vibrator.hasAmplitudeControl();
        }
        return vibrator.hasVibrator();
    }

    private void initHapticsSystem() {
        hapticHandler = new Handler();

        // Check device haptics capability first
        boolean hasHaptics = hasHapticsCapability();
        if (!hasHaptics) {
            // Device doesn't support haptics
            isSongHapticsEnabled = false;
            viewModel.setHapticsEnabled(false);
            Log.d(TAG, "Device doesn't support haptic feedback");
            return;
        }

        isSongHapticsEnabled = viewModel.getIsHapticsEnabled().getValue() != null
                && viewModel.getIsHapticsEnabled().getValue();

        Arrays.fill(subBassHistory, 0f);
        Arrays.fill(bassHistory, 0f);
        Arrays.fill(lowMidHistory, 0f);
        Arrays.fill(midHistory, 0f);
        Arrays.fill(highMidHistory, 0f);
        Arrays.fill(highHistory, 0f);
        Arrays.fill(beatIntervals, 500f);

        Log.d(TAG, "Effects-aware haptics initialized, state: " + isSongHapticsEnabled + ", device capable: "
                + hasHaptics);
    }

    private void startProgressLoop() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (audioService != null && isActuallyPlaying && !isUserSeeking) {
                    int current = audioService.getCurrentPosition();
                    int total = audioService.getDuration();

                    if (total > 0) {
                        int waveValue = (int) ((current * 1000L) / total);
                        if (miniProgress != null) {
                            miniProgress.setProgressCompat(waveValue, true);
                        }
                        if (waveProgress != null) {
                            waveProgress.setProgressCompat(waveValue, true);
                        }

                        if (seekBar != null && total > 0) {
                            // Clamp value to prevent crash when position exceeds duration slightly
                            int clampedValue = Math.min(current, total);
                            seekBar.setValue(clampedValue);
                        }

                        if (tvCurrentTime != null) {
                            tvCurrentTime.setText(formatTime(current));
                        }
                    }
                }

                progressHandler.postDelayed(this, 16);
            }
        };

        progressHandler.post(progressRunnable);
    }

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
                        public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int rate) {
                        }

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
                    true);

            Log.d(TAG, "Visualizer created with effects-aware processing");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Visualizer: " + e.getMessage(), e);
        }
    }

    private void processEffectsAwareBeatDetection(byte[] fft) {
        if (!isActuallyPlaying)
            return;

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

        if (bassEnergy > peakBassLevel)
            peakBassLevel = bassEnergy;
        else
            peakBassLevel *= 0.999f;

        if (midEnergy > peakMidLevel)
            peakMidLevel = midEnergy;
        else
            peakMidLevel *= 0.999f;

        if (highEnergy > peakHighLevel)
            peakHighLevel = highEnergy;
        else
            peakHighLevel *= 0.999f;

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
        for (float value : array)
            sum += value;
        return sum / array.length;
    }

    private enum HapticType {
        KICK, SNARE, HIHAT
    }

    private void triggerHaptic(float intensity, HapticType type) {
        if (!isActuallyPlaying || rootView == null || !rootView.isAttachedToWindow())
            return;

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
                hapticConstant = intensity > 0.6f ? HAPTIC_CONTEXT_CLICK
                        : intensity > 0.4f ? HAPTIC_TEXT_HANDLE
                                : intensity > 0.25f ? HAPTIC_SEGMENT_TICK : HAPTIC_SEGMENT_FREQUENT;
                break;

            case HIHAT:
                hapticConstant = intensity > 0.5f ? HAPTIC_SEGMENT_TICK
                        : intensity > 0.3f ? HAPTIC_SEGMENT_FREQUENT : HAPTIC_LIGHT_TICK;
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

    private void setupObservers() {
        viewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            isActuallyPlaying = playing != null && playing;

            btnPlayPauseToggle.setIcon(getResources().getDrawable(
                    isActuallyPlaying ? R.drawable.ic_pause : R.drawable.ic_play, null));

            if (btnMiniPlayPause != null) {
                btnMiniPlayPause.setIcon(getResources().getDrawable(
                        isActuallyPlaying ? R.drawable.ic_pause : R.drawable.ic_play, null));
            }

            // Toggle wave animation based on playback state (smooth transition)
            if (waveProgress != null) {
                int targetAmplitude = isActuallyPlaying ? 10 : 0;
                animateWaveAmplitude(targetAmplitude);
                if (isActuallyPlaying) {
                    waveProgress.setWaveSpeed(200);
                }
            }

            // Handle haptics with playback state
            Boolean hapticsEnabled = viewModel.getIsHapticsEnabled().getValue();
            if (hapticsEnabled != null && hapticsEnabled) {
                if (isActuallyPlaying) {
                    // Playback resumed - ensure visualizer is ready
                    if (visualizer == null && audioService != null) {
                        initAdvancedHaptics();
                    }
                    if (visualizer != null) {
                        try {
                            visualizer.setEnabled(true);
                            Log.d(TAG, "Visualizer enabled on playback resume");
                        } catch (Exception e) {
                            Log.e(TAG, "Error enabling visualizer: " + e.getMessage());
                            // Visualizer in bad state - release and retry
                            releaseVisualizer();
                            initAdvancedHaptics();
                            if (visualizer != null) {
                                try {
                                    visualizer.setEnabled(true);
                                } catch (Exception ex) {
                                    Log.e(TAG, "Failed retry: " + ex.getMessage());
                                }
                            }
                        }
                    }
                } else {
                    // Playback paused - disable but don't release
                    if (visualizer != null) {
                        try {
                            visualizer.setEnabled(false);
                            Log.d(TAG, "Visualizer disabled on playback pause");
                        } catch (Exception e) {
                            Log.e(TAG, "Error disabling visualizer: " + e.getMessage());
                        }
                    }
                }
            }
        });

        viewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> {
            if (position != null && !isUserSeeking && seekBar != null) {
                // Clamp position to valueTo to prevent IllegalStateException
                float maxValue = seekBar.getValueTo();
                int clampedPosition = (int) Math.min(position, maxValue);
                seekBar.setValue(clampedPosition);
                tvCurrentTime.setText(formatTime(position));

                // Sync wave progress with playback position
                if (waveProgress != null && maxValue > 0) {
                    int waveValue = (int) ((clampedPosition / maxValue) * 1000);
                    waveProgress.setProgressCompat(waveValue, true);
                }

                // Sync mini player progress
                if (miniProgress != null && maxValue > 0) {
                    int miniValue = (int) ((clampedPosition / maxValue) * 1000);
                    miniProgress.setProgressCompat(miniValue, true);
                }
            }
        });

        viewModel.getDuration().observe(getViewLifecycleOwner(), duration -> {
            if (duration != null) {
                seekBar.setValueTo(duration > 0 ? duration : 100);
                tvTotalTime.setText(formatTime(duration));
            }
        });

        viewModel.getSongUri().observe(getViewLifecycleOwner(), uri -> {
            if (uri != null) {
                loadSongMetadata(uri);
                resetHapticState();

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

        viewModel.getBassBoost().observe(getViewLifecycleOwner(), bassBoost -> {
            if (bassBoost != null) {
                bassBoostMultiplier = 1.0f + (bassBoost / 12.0f) * 0.8f;
                bassBoostMultiplier = Math.max(0.7f, Math.min(1.8f, bassBoostMultiplier));
                Log.d(TAG, String.format("Bass Boost: %d dB → Haptic ×%.2f", bassBoost, bassBoostMultiplier));
            }
        });

        viewModel.getLoudnessGain().observe(getViewLifecycleOwner(), loudness -> {
            if (loudness != null) {
                loudnessMultiplier = 1.0f + (loudness / 10.0f) * 0.5f;
                Log.d(TAG, String.format("Loudness: +%d dB → Haptic ×%.2f", loudness, loudnessMultiplier));
            }
        });

        viewModel.getEqBand1().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand2().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand3().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand4().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand5().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());

        viewModel.getPlaybackSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed != null) {
                playbackSpeedFactor = speed;
                Log.d(TAG, String.format("Playback Speed: %.2fx → Timing adjusted", speed));
            }
        });

        viewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                tvSongName.setText(song.title);
                if (tvArtistName != null) {
                    tvArtistName.setText(song.artist);
                }

                if (tvMiniSongName != null) {
                    tvMiniSongName.setText(song.title);
                }
                if (tvMiniArtistName != null) {
                    tvMiniArtistName.setText(song.artist);
                }

                Uri artUri = song.getAlbumArtUri();
                Glide.with(this)
                        .load(artUri)
                        .placeholder(R.drawable.default_album_art)
                        .error(R.drawable.default_album_art)
                        .centerCrop()
                        .into(ivAlbumArt);

                if (ivMiniAlbumArt != null) {
                    Glide.with(this)
                            .load(artUri)
                            .placeholder(R.drawable.default_album_art)
                            .error(R.drawable.default_album_art)
                            .centerCrop()
                            .into(ivMiniAlbumArt);
                }

                extractGradientFromAlbumArt(artUri);

                // Re-apply 8D effect to the new song if it was enabled
                // (Don't reset 8D state, let it persist across songs)
                viewModel.triggerEffectsRefresh();

                // Update favorite button for new song
                updateFavoriteButtonState();

                Log.d(TAG, "Now playing: " + song.title + " by " + song.artist);
            }
        });

        viewModel.getShouldPromptEffects().observe(getViewLifecycleOwner(), shouldPrompt -> {
            if (shouldPrompt != null && shouldPrompt && coordinatorLayout != null) {
                Snackbar.make(coordinatorLayout, "Apply effects to this song?", Snackbar.LENGTH_LONG)
                        .setAction("Apply", v -> {
                            viewModel.applyAllEffects();
                            showSnackbar("Effects applied!", Snackbar.LENGTH_SHORT);
                        })
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                viewModel.clearEffectsPrompt();
                            }
                        })
                        .show();
            }
        });

        // Shuffle state observer
        viewModel.getIsShuffleEnabled().observe(getViewLifecycleOwner(), shuffleEnabled -> {
            if (btnShuffle != null) {
                int tint = (shuffleEnabled != null && shuffleEnabled)
                        ? ContextCompat.getColor(requireContext(), R.color.purple_200)
                        : (isDarkMode ? android.graphics.Color.WHITE : 0xCC000000);
                btnShuffle.setIconTint(android.content.res.ColorStateList.valueOf(tint));
            }
        });

        // Repeat mode observer
        viewModel.getRepeatMode().observe(getViewLifecycleOwner(), mode -> {
            if (btnRepeat != null && mode != null) {
                int icon;
                int tint;
                switch (mode) {
                    case PlayerSharedViewModel.REPEAT_ALL:
                        icon = R.drawable.ic_repeat;
                        tint = ContextCompat.getColor(requireContext(), R.color.purple_200);
                        break;
                    case PlayerSharedViewModel.REPEAT_ONE:
                        icon = R.drawable.ic_repeat_one;
                        tint = ContextCompat.getColor(requireContext(), R.color.purple_200);
                        break;
                    default: // REPEAT_OFF
                        icon = R.drawable.ic_repeat;
                        tint = isDarkMode ? android.graphics.Color.WHITE : 0xCC000000;
                        break;
                }
                btnRepeat.setIcon(ContextCompat.getDrawable(requireContext(), icon));
                btnRepeat.setIconTint(android.content.res.ColorStateList.valueOf(tint));
            }
        });

        // Favorites observer (update icon when favorites change)
        viewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), favorites -> {
            updateFavoriteButtonState();
            // Update library to show favorite indicators
            if (songAdapter != null) {
                songAdapter.setFavorites(favorites);
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

    /**
     * Updates the favorite button icon based on current song's favorite status.
     */
    private void updateFavoriteButtonState() {
        if (btnFavorite == null)
            return;

        SongItem current = viewModel.getCurrentSong().getValue();
        if (current == null)
            return;

        boolean isFav = viewModel.isFavorite(current.id);
        int icon = isFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border;
        int tint = isFav
                ? ContextCompat.getColor(requireContext(), R.color.purple_200)
                : (isDarkMode ? android.graphics.Color.WHITE : 0xCC000000);

        btnFavorite.setIcon(ContextCompat.getDrawable(requireContext(), icon));
        btnFavorite.setIconTint(android.content.res.ColorStateList.valueOf(tint));
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
        lastHiHatTime = 0;
        estimatedBPM = 120f;
    }

    private void setupListeners() {
        btnPlayPauseToggle.setOnClickListener(v -> {
            Boolean playing = viewModel.getIsPlaying().getValue();
            if (playing != null && playing)
                viewModel.pauseAudio();
            else
                viewModel.playAudio();
        });

        btnPrevious.setOnClickListener(v -> viewModel.playPreviousSong());
        btnNext.setOnClickListener(v -> viewModel.playNextSong());

        // Shuffle button
        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(v -> {
                viewModel.toggleShuffle();
            });
        }

        // Repeat button
        if (btnRepeat != null) {
            btnRepeat.setOnClickListener(v -> {
                viewModel.cycleRepeatMode();
            });
        }

        // Favorite button
        if (btnFavorite != null) {
            btnFavorite.setOnClickListener(v -> {
                SongItem current = viewModel.getCurrentSong().getValue();
                if (current != null) {
                    viewModel.toggleFavorite(current.id);
                }
            });
        }

        // Haptics Chip listener
        if (chipSongHaptics != null) {
            boolean hasHaptics = hasHapticsCapability();
            chipSongHaptics.setEnabled(hasHaptics);
            chipSongHaptics.setAlpha(hasHaptics ? 1.0f : 0.5f);

            Boolean savedState = viewModel.getIsHapticsEnabled().getValue();
            chipSongHaptics.setChecked(savedState != null && savedState && hasHaptics);

            chipSongHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
                viewModel.setHapticsEnabled(isChecked);
                if (isChecked) {
                    enableAdvancedHaptics();
                } else {
                    disableAdvancedHaptics();
                }
            });
        }

        if (btnMiniPlayPause != null) {
            btnMiniPlayPause.setOnClickListener(v -> {
                Boolean playing = viewModel.getIsPlaying().getValue();
                if (playing != null && playing)
                    viewModel.pauseAudio();
                else
                    viewModel.playAudio();
            });
        }

        if (btnMiniPrevious != null) {
            btnMiniPrevious.setOnClickListener(v -> viewModel.playPreviousSong());
        }

        if (btnMiniNext != null) {
            btnMiniNext.setOnClickListener(v -> viewModel.playNextSong());
        }

        if (playerBottomSheet != null) {
            playerBottomSheet.setOnClickListener(v -> expandPlayer());
        }

        setupSwipeGesture();

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

    /**
     * Smoothly animate the wave amplitude transition for a polished effect.
     */
    private void animateWaveAmplitude(int targetAmplitude) {
        if (waveAnimator != null && waveAnimator.isRunning()) {
            waveAnimator.cancel();
        }

        waveAnimator = ValueAnimator.ofInt(currentWaveAmplitude, targetAmplitude);
        waveAnimator.setDuration(400); // 400ms smooth transition
        waveAnimator.setInterpolator(new FastOutSlowInInterpolator());
        waveAnimator.addUpdateListener(animation -> {
            if (waveProgress != null) {
                currentWaveAmplitude = (int) animation.getAnimatedValue();
                waveProgress.setWaveAmplitude(currentWaveAmplitude);
            }
        });
        waveAnimator.start();
    }

    private void enableAdvancedHaptics() {
        if (!hasRecordPermission()) {
            requestRecordAudioPermission();
            viewModel.setHapticsEnabled(false);
            return;
        }

        if (visualizer != null) {
            try {
                visualizer.setEnabled(true);
                resetHapticState();
                Log.d(TAG, "Effects-aware haptics ENABLED");
            } catch (Exception e) {
                Log.e(TAG, "Failed: " + e.getMessage());
                viewModel.setHapticsEnabled(false);
            }
        } else {
            initAdvancedHaptics();
            if (visualizer != null) {
                enableAdvancedHaptics();
            } else {
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

    private void loadSongMetadata(Uri uri) {
        new Thread(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                if (getContext() == null)
                    return;

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
                    uri, new String[] { MediaStore.Audio.Media.DISPLAY_NAME }, null, null, null)) {
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
        if (bottomNav != null)
            processingSnackbar.setAnchorView(bottomNav);
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
                    dismissSnackbarAndShowWithAction(processingSnackbar, "✓ Saved to Downloads/SpatialFlow",
                            Snackbar.LENGTH_LONG, outputFile);
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
                if (bottomNav != null)
                    snackbar.setAnchorView(bottomNav);
                snackbar.show();
            });
        }
    }

    private void dismissSnackbarAndShow(Snackbar oldSnackbar, String message, int duration) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                if (oldSnackbar != null)
                    oldSnackbar.dismiss();
                Snackbar snackbar = Snackbar.make(rootView, message, duration);
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null)
                    snackbar.setAnchorView(bottomNav);
                snackbar.show();
            });
        }
    }

    private void dismissSnackbarAndShowWithAction(Snackbar oldSnackbar, String message, int duration, File outputFile) {
        if (getActivity() != null && rootView != null) {
            getActivity().runOnUiThread(() -> {
                if (oldSnackbar != null)
                    oldSnackbar.dismiss();
                Snackbar snackbar = Snackbar.make(rootView, message, duration);
                View bottomNav = getActivity().findViewById(R.id.nav_view);
                if (bottomNav != null)
                    snackbar.setAnchorView(bottomNav);
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

        // Always release visualizer on fragment destroy for clean re-init
        // (Keeping it alive causes corrupted state on return)
        releaseVisualizer();
        Log.d(TAG, "Visualizer released on destroy");

        if (serviceBound && getContext() != null) {
            getContext().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void applyMaterialDynamicCalibration() {
        if (getContext() == null || gradientBackground == null)
            return;

        // Ensure current isDarkMode is synced using context resources
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        this.isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // Material 3 Expressive Dynamic Colors
        int surfaceHigh = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorSurfaceContainerHigh, android.graphics.Color.GRAY);
        int secContainer = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorSecondaryContainer, android.graphics.Color.GRAY);
        int tertContainer = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorTertiaryContainer, android.graphics.Color.GRAY);
        int primContainer = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorPrimaryContainer, android.graphics.Color.GRAY);

        int[] defaultColors = new int[] { primContainer, tertContainer, secContainer, surfaceHigh };
        gradientBackground.setColors(defaultColors);
        gradientBackground.setIsDarkMode(isDarkMode);

        int primaryColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface,
                android.graphics.Color.BLACK);
        int secondaryColor = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.DKGRAY);

        if (playerBottomSheet != null)
            playerBottomSheet.setCardBackgroundColor(surfaceHigh);

        if (tvSongName != null)
            tvSongName.setTextColor(primaryColor);
        if (tvArtistName != null)
            tvArtistName.setTextColor(secondaryColor);
        if (tvNowPlaying != null)
            tvNowPlaying.setTextColor(primaryColor);
        if (tvCurrentTime != null)
            tvCurrentTime.setTextColor(secondaryColor);
        if (tvTotalTime != null)
            tvTotalTime.setTextColor(secondaryColor);

        if (secondaryControlsCard != null)
            secondaryControlsCard.setCardBackgroundColor(surfaceHigh);

        if (btnPlayPauseToggle != null) {
            btnPlayPauseToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
            btnPlayPauseToggle.setIconTint(android.content.res.ColorStateList
                    .valueOf(isDarkMode ? android.graphics.Color.BLACK : android.graphics.Color.WHITE));
        }

        if (btnPrevious != null)
            btnPrevious.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        if (btnNext != null)
            btnNext.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));

        android.content.res.ColorStateList secTint = android.content.res.ColorStateList.valueOf(secondaryColor);
        if (btnShuffle != null)
            btnShuffle.setIconTint(secTint);
        if (btnRepeat != null)
            btnRepeat.setIconTint(secTint);
        if (btnFavorite != null)
            btnFavorite.setIconTint(secTint);

        if (chipSongHaptics != null) {
            chipSongHaptics.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(surfaceHigh));
            chipSongHaptics.setTextColor(secTint);
            chipSongHaptics.setChipStrokeWidth(0);
            chipSongHaptics.setChipIconTint(secTint);
            chipSongHaptics.setAlpha(1.0f);
        }

        if (btnMiniPlayPause != null) {
            btnMiniPlayPause.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnMiniPlayPause.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        }
        if (btnMiniPrevious != null)
            btnMiniPrevious.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));
        if (btnMiniNext != null)
            btnMiniNext.setIconTint(android.content.res.ColorStateList.valueOf(primaryColor));

        if (miniProgress != null) {
            miniProgress.setIndicatorColor(primaryColor);
            miniProgress.setTrackColor((primaryColor & 0x00FFFFFF) | 0x33000000);
        }
    }
}
