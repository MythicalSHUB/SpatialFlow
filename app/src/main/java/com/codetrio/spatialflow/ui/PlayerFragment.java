package com.codetrio.spatialflow.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
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
import android.view.GestureDetector;
import android.view.MotionEvent;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.TransitionManager;
import android.view.animation.PathInterpolator;

import android.graphics.drawable.GradientDrawable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.shape.AbsoluteCornerSize;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.transition.MaterialContainerTransform;
import com.google.android.material.color.MaterialColors;

import com.codetrio.spatialflow.MainActivity;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.model.SongItem;
import com.codetrio.spatialflow.service.AudioPlaybackService;
import com.codetrio.spatialflow.ui.adapter.ExpressiveSongAdapter;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.codetrio.spatialflow.ui.custom.AnimatedMeshGradientView;
import com.codetrio.spatialflow.util.AudioFileManager;
import com.codetrio.spatialflow.util.FFmpegCommandBuilder;
import com.codetrio.spatialflow.util.PlayerHapticManager;
import com.codetrio.spatialflow.util.ColorUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.card.MaterialCardView;
import com.codetrio.spatialflow.ui.SongActionsBottomSheet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlayerFragment extends Fragment {

    private static final String TAG = "PlayerFragment";

    private PlayerSharedViewModel viewModel;
    private AudioPlaybackService audioService;
    private boolean serviceBound = false;

    private List<SongItem> allSongsBackup = new ArrayList<>(); // Backup of full library

    // =========================
    // EFFECTS-AWARE HAPTICS
    // =========================
    private boolean isSongHapticsEnabled = false;
    private boolean isActuallyPlaying = false;
    // Haptics logic moved to PlayerHapticManager
    private static final int TAG_ORIGINAL_SHAPE = R.id.sortButtonGroup;
    private static final int TAG_RUNNING_ANIMATOR = R.id.gradientBackground;

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
    // Search components
    private com.google.android.material.search.SearchBar searchBar;
    private com.google.android.material.search.SearchView searchView;
    private RecyclerView rvSearchResults;
    private ExpressiveSongAdapter searchAdapter;
    private Slider seekBar;
    private MaterialButton btnPlayPauseToggle;
    private MaterialButton btnPrevious, btnNext;
    private MaterialButton btnShuffle, btnRepeat, btnFavorite;
    private MaterialCardView secondaryControlsCard;
    private Chip chipSongHaptics;
    private LinearProgressIndicator waveProgress;
    private View progressHeadIndicator;

    // Mini Player components (inside sheet)
    private ImageView ivMiniAlbumArt;
    private MaterialTextView tvMiniSongName;
    private MaterialTextView tvMiniArtistName;
    private LinearProgressIndicator miniProgress;
    private MaterialButton btnMiniPlayPause, btnMiniPrevious, btnMiniNext;

    // Song Library
    private RecyclerView rvSongLibrary;
    private ExpressiveSongAdapter songAdapter;
    private MaterialCardView cardRefreshing;
    private SwipeRefreshLayout swipeRefreshLayout;
    private com.google.android.material.button.MaterialButtonToggleGroup sortButtonGroup;
    private ConstraintLayout libraryContent;
    private com.codetrio.spatialflow.ui.custom.VerticalLetterBar letterBar;

    // Player sheet state
    private boolean isPlayerExpanded = false;
    private int[] currentGradientColors = null;
    private boolean isDarkMode; // Detected dynamically

    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isUserSeeking = false;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private OnBackPressedCallback onBackPressedCallback;

    private int[] previousGradientColors;
    private long currentSongIdInView = -1;
    private ValueAnimator waveAnimator; // Smooth wave amplitude animator
    private int currentWaveAmplitude = 0;
    private boolean isAnimating = false; // Flag to prevent double-swipes

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
            viewModel.initFavorites(requireContext());
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

        // Defer heavy initialization to after first frame for faster fragment display
        rootView.post(() -> {
            initHapticsSystem();
            setupObservers();
            setupListeners();
            startProgressLoop();
        });

        Intent intent = new Intent(getContext(), AudioPlaybackService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        tvSongName.setSelected(true);

        // Apply initial dynamic calibration (handles "No Song Selected" state)
        // Calling unconditionally to ensure proper Light/Dark mode start
        // Observe Favorite State
        if (viewModel != null) {
            viewModel.getIsCurrentSongFavorite().observe(getViewLifecycleOwner(), isFav -> {
                if (btnFavorite != null) {
                    if (Boolean.TRUE.equals(isFav)) {
                        btnFavorite.setIconResource(R.drawable.ic_favorite);
                        btnFavorite.setIconTint(android.content.res.ColorStateList.valueOf(
                                isDarkMode ? android.graphics.Color.WHITE : 0xFFFFFFFF)); // Force white always if
                                                                                          // needed? Or adapt.
                    } else {
                        btnFavorite.setIconResource(R.drawable.ic_favorite_border);
                        btnFavorite.setIconTint(android.content.res.ColorStateList.valueOf(
                                isDarkMode ? android.graphics.Color.WHITE : 0xCC000000));
                    }
                }
            });
        }

        applyMaterialDynamicCalibration();

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        updateSystemBars();

        // Restore mini player visibility if a song is playing or was played
        if (viewModel != null && viewModel.getCurrentSong().getValue() != null) {
            if (bottomSheetBehavior != null && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        }

        // Reload haptic settings from SharedPreferences (in case changed in Settings)
        if (hapticManager != null) {
            hapticManager.loadSettingsFromPrefs();
        }

        // Re-enable haptics if they were enabled before fragment switch
        Boolean hapticsEnabled = viewModel != null ? viewModel.getIsHapticsEnabled().getValue() : null;
        if (hapticsEnabled != null && hapticsEnabled && audioService != null && hapticManager != null) {
            // Release old visualizer first to avoid state errors
            hapticManager.release();

            // Small delay to ensure clean state before re-init
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (getContext() == null)
                    return;
                enableAdvancedHaptics();
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
                        // Enable haptics and update chip
                        viewModel.setHapticsEnabled(true);
                        if (chipSongHaptics != null) {
                            chipSongHaptics.setChecked(true);
                        }
                        initAdvancedHaptics();
                        enableAdvancedHaptics();
                    } else {
                        Log.w(TAG, "RECORD_AUDIO denied");
                        viewModel.setHapticsEnabled(false);
                        showSnackbar("Microphone permission required for haptics", Snackbar.LENGTH_LONG);
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
        seekBar = view.findViewById(R.id.seekBar);

        // Initialize Search components
        searchBar = view.findViewById(R.id.searchBar);
        searchView = view.findViewById(R.id.searchView);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        setupSearch();

        btnPlayPauseToggle = view.findViewById(R.id.btnPlayPauseToggle);
        btnPrevious = view.findViewById(R.id.btnPrevious);
        btnNext = view.findViewById(R.id.btnNext);

        // Secondary controls (shuffle, repeat, favorite)
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnRepeat = view.findViewById(R.id.btnRepeat);
        btnFavorite = view.findViewById(R.id.btnFavorite);
        secondaryControlsCard = view.findViewById(R.id.secondaryControls);

        chipSongHaptics = view.findViewById(R.id.chipSongHaptics);
        chipSongHaptics.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        waveProgress = view.findViewById(R.id.waveProgress);
        progressHeadIndicator = view.findViewById(R.id.progressHeadIndicator);

        // Mini Player components (re-mapped to new IDs inside sheet)
        ivMiniAlbumArt = view.findViewById(R.id.ivMiniAlbumArt);
        tvMiniSongName = view.findViewById(R.id.tvMiniSongName);
        tvMiniArtistName = view.findViewById(R.id.tvMiniArtistName);
        miniProgress = view.findViewById(R.id.miniProgress);
        btnMiniPlayPause = view.findViewById(R.id.btnMiniPlayPause);
        btnMiniPrevious = view.findViewById(R.id.btnMiniPrevious);
        btnMiniNext = view.findViewById(R.id.btnMiniNext);

        // Setup Favorite Button Listener
        if (btnFavorite != null) {
            btnFavorite.setOnClickListener(v -> viewModel.toggleFavorite());
        }
        // Observe Favorite State
        if (viewModel != null) { // Might be null here in initViews, so check or move to onServiceConnected?
            // ViewModel is usually strictly available?
            // Better to do this in onViewCreated or observe in onActivityCreated.
            // But let's assume viewModel is init by now or add observation logic later.
        }

        rvSongLibrary = view.findViewById(R.id.rvSongLibrary);
        sortButtonGroup = view.findViewById(R.id.sortButtonGroup);
        letterBar = view.findViewById(R.id.letterBar);
        setupSongLibrary();

        // ---------------------------
        // SCROLL-AWARE BOTTOM NAV + DETACHABLE MINI PLAYER
        // ---------------------------
        if (rvSongLibrary != null) {
            rvSongLibrary.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx,
                        int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (getActivity() instanceof MainActivity) {
                        MainActivity activity = (MainActivity) getActivity();
                        if (dy > 10) {
                            // Scrolling down - hide nav, detach mini player
                            activity.hideBottomNavWithAnimation();
                            detachMiniPlayer();
                        } else if (dy < -10) {
                            // Scrolling up - show nav, reconnect mini player
                            activity.showBottomNavWithAnimation();
                            attachMiniPlayer();
                        }
                    }
                }
            });
        }

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
        // Initialize ExpressiveSongAdapter with click listener
        songAdapter = new ExpressiveSongAdapter((song, position) -> {
            viewModel.playSongAtIndex(position);
            expandPlayer();
        });

        // Set long-press listener to show action bottom sheet
        songAdapter.setLongClickListener((song, position) -> {
            SongActionsBottomSheet bottomSheet = SongActionsBottomSheet.newInstance(song);
            bottomSheet.setActionListener(new SongActionsBottomSheet.ActionListener() {
                @Override
                public void onPlayNext(SongItem song) {
                    viewModel.addToQueueNext(song);
                    showSnackbar("Playing next: " + song.title, Snackbar.LENGTH_SHORT);
                }

                @Override
                public void onAddToQueue(SongItem song) {
                    viewModel.addToQueue(song);
                    showSnackbar("Added to queue: " + song.title, Snackbar.LENGTH_SHORT);
                }

                @Override
                public void onDelete(SongItem song) {
                    showDeleteConfirmationDialog(song);
                }
            });
            bottomSheet.show(getChildFragmentManager(), "SongActionsBottomSheet");
        });

        rvSongLibrary.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSongLibrary.setAdapter(songAdapter);

        // Performance optimizations
        rvSongLibrary.setHasFixedSize(true);
        rvSongLibrary.setItemViewCacheSize(20);
        // Use RecycledViewPool for better memory reuse
        androidx.recyclerview.widget.RecyclerView.RecycledViewPool pool = new androidx.recyclerview.widget.RecyclerView.RecycledViewPool();
        pool.setMaxRecycledViews(0, 25);
        rvSongLibrary.setRecycledViewPool(pool);

        // Setup refresh card
        setupRefreshCard();

        // Setup sort button group
        setupSortButtons();

        // Setup A-Z letter bar
        setupLetterBar();

        loadSongLibrary();
    }

    /**
     * Setup A-Z letter bar for fast scrolling.
     */
    private void setupLetterBar() {
        if (letterBar == null || rvSongLibrary == null || songAdapter == null)
            return;

        // Apply theme-aware color
        letterBar.setDarkMode(isDarkMode);

        letterBar.setOnLetterSelectListener(letter -> {
            List<SongItem> songs = songAdapter.getAllSongs();
            if (songs == null || songs.isEmpty())
                return;

            int targetIndex = -1;
            String letterStr = String.valueOf(letter).toUpperCase();

            // Handle # for numbers/symbols
            if (letter == '#') {
                for (int i = 0; i < songs.size(); i++) {
                    String title = songs.get(i).title;
                    if (title != null && !title.isEmpty()) {
                        char firstChar = Character.toUpperCase(title.charAt(0));
                        if (!Character.isLetter(firstChar)) {
                            targetIndex = i;
                            break;
                        }
                    }
                }
            } else {
                // Find first song starting with this letter
                for (int i = 0; i < songs.size(); i++) {
                    String title = songs.get(i).title;
                    if (title != null && !title.isEmpty()) {
                        if (title.toUpperCase().startsWith(letterStr)) {
                            targetIndex = i;
                            break;
                        }
                    }
                }
            }

            if (targetIndex >= 0) {
                // Smooth scroll with offset to show context
                LinearLayoutManager layoutManager = (LinearLayoutManager) rvSongLibrary.getLayoutManager();
                if (layoutManager != null) {
                    layoutManager.scrollToPositionWithOffset(targetIndex, 0);
                }
            }
        });
    }

    /**
     * Setup Material 3 Search Bar and SearchView for filtering songs.
     */
    private void setupSearch() {
        if (searchBar == null || searchView == null)
            return;

        // Initialize search results adapter
        searchAdapter = new ExpressiveSongAdapter((song, position) -> {
            // Hide search view and play the selected song
            searchView.hide();
            // Find position in main list
            List<SongItem> mainList = songAdapter.getAllSongs();
            int mainPosition = -1;
            for (int i = 0; i < mainList.size(); i++) {
                if (mainList.get(i).id == song.id) {
                    mainPosition = i;
                    break;
                }
            }
            if (mainPosition >= 0) {
                viewModel.playSongAtIndex(mainPosition);
            }
            // If song not found in main list, it still gets selected via adapter click
            expandPlayer();
        });

        if (rvSearchResults != null) {
            rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
            rvSearchResults.setAdapter(searchAdapter);
        }

        // Connect SearchBar with SearchView
        searchView.setupWithSearchBar(searchBar);

        // Listen for text changes in SearchView
        searchView.getEditText().addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        // Handle search submit
        searchView.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            String query = searchView.getText().toString();
            filterSongs(query);
            return false;
        });
    }

    /**
     * Filter songs based on search query.
     */
    private void filterSongs(String query) {
        if (allSongsBackup == null || allSongsBackup.isEmpty())
            return;

        if (query == null || query.trim().isEmpty()) {
            searchAdapter.submitList(new ArrayList<>(allSongsBackup));
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        List<SongItem> filtered = new ArrayList<>();

        for (SongItem song : allSongsBackup) {
            boolean matches = false;
            if (song.title != null && song.title.toLowerCase().contains(lowerQuery)) {
                matches = true;
            } else if (song.artist != null && song.artist.toLowerCase().contains(lowerQuery)) {
                matches = true;
            }
            if (matches) {
                filtered.add(song);
            }
        }

        searchAdapter.submitList(filtered);
    }

    /**
     * Setup refresh gesture.
     * Uses SwipeRefreshLayout as a gesture detector to trigger the custom card
     * refresh.
     */
    private void setupRefreshCard() {
        cardRefreshing = rootView.findViewById(R.id.cardRefreshing);
        swipeRefreshLayout = rootView.findViewById(R.id.swipeRefreshLayout);
        libraryContent = rootView.findViewById(R.id.libraryContent);

        if (swipeRefreshLayout != null) {
            // Hide the standard SwipeRefreshLayout indicator by setting transparent colors
            swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.TRANSPARENT);
            // Alternatively, move offset off-screen if reachable, but transparent is safer

            swipeRefreshLayout.setOnRefreshListener(() -> {
                // When swiped, trigger our custom refresh card
                swipeRefreshLayout.setRefreshing(false); // Stop standard indicator immediately
                refreshSongLibrary();
            });
        }

        // Keep the header click as a backup/shortcut
        View libraryHeader = rootView.findViewById(R.id.libraryHeader);
        if (libraryHeader != null) {
            libraryHeader.setOnClickListener(v -> refreshSongLibrary());
        }
    }

    /**
     * Refresh the song library from storage with smooth animation (2-2.5 sec).
     */
    private void refreshSongLibrary() {
        // Show the refresh card with animation
        if (cardRefreshing != null && libraryContent != null) {
            // Animate layout changes (smooth push down)
            androidx.transition.Transition transition = new androidx.transition.TransitionSet()
                    .addTransition(new androidx.transition.ChangeBounds())
                    .setDuration(500)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator());

            androidx.transition.TransitionManager.beginDelayedTransition(libraryContent, transition);
            cardRefreshing.setVisibility(View.VISIBLE);
        }

        // Run on background thread then update UI
        new Thread(() -> {
            // Refresh animation for 2-2.5 seconds
            try {
                Thread.sleep(2000 + (int) (Math.random() * 500));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Load songs on main thread
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // Hide the refresh card BEFORE reloading
                    if (cardRefreshing != null && libraryContent != null) {
                        // Animate layout changes (smooth slide up) - Same interpolator for exact
                        // reverse
                        androidx.transition.Transition transition = new androidx.transition.TransitionSet()
                                .addTransition(new androidx.transition.ChangeBounds())
                                .setDuration(500)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator());

                        androidx.transition.TransitionManager.beginDelayedTransition(libraryContent, transition);
                        cardRefreshing.setVisibility(View.GONE);
                    }

                    // Delay the reload slightly to let the animation complete
                    new android.os.Handler().postDelayed(() -> {
                        loadSongLibrary();
                    }, 500); // Match the animation duration
                });
            }
        }).start();
    }

    /**
     * Setup sort button group listeners.
     */
    private void setupSortButtons() {
        if (sortButtonGroup == null || rootView == null)
            return;

        MaterialButton btnAZ = rootView.findViewById(R.id.btnSortAZ);
        MaterialButton btnRecent = rootView.findViewById(R.id.btnSortRecent);
        MaterialButton btnArtist = rootView.findViewById(R.id.btnSortArtist);
        MaterialButton btnFavorites = rootView.findViewById(R.id.btnSortFavorites);

        MaterialButton[] buttons = { btnAZ, btnRecent, btnArtist, btnFavorites };

        // 1. Capture original 'Connected' shapes once layout is ready
        sortButtonGroup.post(() -> {
            for (MaterialButton btn : buttons) {
                if (btn != null) {
                    // Save the original shape (which has the correct connected corners)
                    btn.setTag(TAG_ORIGINAL_SHAPE, btn.getShapeAppearanceModel());

                    // If initially checked, make it round immediately
                    if (btn.isChecked()) {
                        btn.setShapeAppearanceModel(
                                btn.getShapeAppearanceModel().toBuilder()
                                        .setAllCornerSizes(new RelativeCornerSize(0.5f))
                                        .build());
                    }
                }
            }
        });

        // 2. Handle Selection Changes with Animation
        sortButtonGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            MaterialButton button = rootView.findViewById(checkedId);
            if (button == null)
                return;

            // Retrieve original shape
            ShapeAppearanceModel originalShape = (ShapeAppearanceModel) button.getTag(TAG_ORIGINAL_SHAPE);
            if (originalShape == null)
                return; // Should not happen if post() ran

            // Cancel any running animator on this specific button
            ValueAnimator runningAnimator = (ValueAnimator) button.getTag(TAG_RUNNING_ANIMATOR);
            if (runningAnimator != null) {
                runningAnimator.cancel();
            }

            if (isChecked) {
                // Animate to ROUND (Pill)
                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(250);
                animator.setInterpolator(new FastOutSlowInInterpolator());

                final float pillRadius = button.getHeight() / 2f;
                final RectF bounds = new RectF(0, 0, button.getWidth(), button.getHeight());

                animator.addUpdateListener(a -> {
                    float f = (float) a.getAnimatedValue();

                    // Interpolate specific corners - pinned logic
                    ShapeAppearanceModel.Builder builder = originalShape.toBuilder();

                    float startTL = originalShape.getTopLeftCornerSize().getCornerSize(bounds);
                    if (Math.abs(startTL - pillRadius) > 1f) {
                        builder.setTopLeftCornerSize(new AbsoluteCornerSize(startTL + (pillRadius - startTL) * f));
                    }

                    float startTR = originalShape.getTopRightCornerSize().getCornerSize(bounds);
                    if (Math.abs(startTR - pillRadius) > 1f) {
                        builder.setTopRightCornerSize(new AbsoluteCornerSize(startTR + (pillRadius - startTR) * f));
                    }

                    float startBR = originalShape.getBottomRightCornerSize().getCornerSize(bounds);
                    if (Math.abs(startBR - pillRadius) > 1f) {
                        builder.setBottomRightCornerSize(new AbsoluteCornerSize(startBR + (pillRadius - startBR) * f));
                    }

                    float startBL = originalShape.getBottomLeftCornerSize().getCornerSize(bounds);
                    if (Math.abs(startBL - pillRadius) > 1f) {
                        builder.setBottomLeftCornerSize(new AbsoluteCornerSize(startBL + (pillRadius - startBL) * f));
                    }

                    button.setShapeAppearanceModel(builder.build());
                });

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        button.setShapeAppearanceModel(
                                originalShape.toBuilder()
                                        .setAllCornerSizes(new RelativeCornerSize(0.5f))
                                        .build());
                        button.setTag(TAG_RUNNING_ANIMATOR, null);
                    }
                });

                button.setTag(TAG_RUNNING_ANIMATOR, animator);
                animator.start();

                // Reset to full list first (in case we were filtered)
                // Use backup to ensure we have the full list even if ViewModel has filtered
                // list
                List<SongItem> fullList = new ArrayList<>(allSongsBackup);
                if (fullList.isEmpty() && viewModel.getSongList().getValue() != null) {
                    fullList.addAll(viewModel.getSongList().getValue());
                }

                if (!fullList.isEmpty()) {
                    // Check if we need to filter for favorites
                    if (checkedId == R.id.btnSortFavorites) {
                        List<SongItem> favs = new ArrayList<>();
                        com.codetrio.spatialflow.util.FavoritesManager fm = new com.codetrio.spatialflow.util.FavoritesManager(
                                requireContext());
                        for (SongItem s : fullList) {
                            if (fm.isFavorite(s.id)) {
                                favs.add(s);
                            }
                        }

                        // 1. Set Sort Order FIRST to avoid visual jump
                        songAdapter.setSortOrder(ExpressiveSongAdapter.SortOrder.A_Z);
                        // 2. Submit List (will sort automatically)
                        songAdapter.submitList(favs);
                        // 3. Sync ViewModel with the ACTUALLY displayed list
                        viewModel.setSongList(songAdapter.getAllSongs());

                    } else {
                        // Determine sort order
                        ExpressiveSongAdapter.SortOrder newOrder = ExpressiveSongAdapter.SortOrder.A_Z;
                        if (checkedId == R.id.btnSortAZ)
                            newOrder = ExpressiveSongAdapter.SortOrder.A_Z;
                        else if (checkedId == R.id.btnSortRecent)
                            newOrder = ExpressiveSongAdapter.SortOrder.DATE_ADDED;
                        else if (checkedId == R.id.btnSortArtist)
                            newOrder = ExpressiveSongAdapter.SortOrder.ARTIST;

                        // 1. Set Sort Order
                        songAdapter.setSortOrder(newOrder);
                        // 2. Submit List
                        songAdapter.submitList(fullList);
                        // 3. Sync ViewModel
                        viewModel.setSongList(songAdapter.getAllSongs());
                    }
                }

                // Restore highlighting
                SongItem current = viewModel.getCurrentSong().getValue();
                if (current != null) {
                    songAdapter.setCurrentlyPlaying(songAdapter.getPositionById(current.id));
                }

                button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

            } else

            {
                // Animate back to ORIGINAL (Connected) shape
                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(250);
                animator.setInterpolator(new FastOutSlowInInterpolator());

                final float pillRadius = button.getHeight() / 2f;
                final RectF bounds = new RectF(0, 0, button.getWidth(), button.getHeight());

                animator.addUpdateListener(a -> {
                    float f = (float) a.getAnimatedValue(); // 0 -> 1

                    // Interpolate each corner: PILL -> ORIGINAL - pinned logic
                    ShapeAppearanceModel.Builder builder = originalShape.toBuilder();

                    float targetTL = originalShape.getTopLeftCornerSize().getCornerSize(bounds);
                    if (Math.abs(targetTL - pillRadius) > 1f) {
                        builder.setTopLeftCornerSize(new AbsoluteCornerSize(pillRadius + (targetTL - pillRadius) * f));
                    }

                    float targetTR = originalShape.getTopRightCornerSize().getCornerSize(bounds);
                    if (Math.abs(targetTR - pillRadius) > 1f) {
                        builder.setTopRightCornerSize(new AbsoluteCornerSize(pillRadius + (targetTR - pillRadius) * f));
                    }

                    float targetBR = originalShape.getBottomRightCornerSize().getCornerSize(bounds);
                    if (Math.abs(targetBR - pillRadius) > 1f) {
                        builder.setBottomRightCornerSize(
                                new AbsoluteCornerSize(pillRadius + (targetBR - pillRadius) * f));
                    }

                    float targetBL = originalShape.getBottomLeftCornerSize().getCornerSize(bounds);
                    if (Math.abs(targetBL - pillRadius) > 1f) {
                        builder.setBottomLeftCornerSize(
                                new AbsoluteCornerSize(pillRadius + (targetBL - pillRadius) * f));
                    }

                    button.setShapeAppearanceModel(builder.build());
                });

                animator.addListener(new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Restore exact original shape object to ensure perfect connections
                        button.setShapeAppearanceModel(originalShape);
                        button.setTag(TAG_RUNNING_ANIMATOR, null);
                    }

                });

                button.setTag(TAG_RUNNING_ANIMATOR, animator);
                animator.start();
            }
        });
    }

    /**
     * Show Material 3 confirmation dialog for deleting a song.
     */
    private void showDeleteConfirmationDialog(SongItem song) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Song")
                .setMessage("Are you sure you want to permanently delete \"" + song.title
                        + "\"?\n\nThis action cannot be undone.")
                .setIcon(R.drawable.ic_delete)
                .setPositiveButton("Delete", (dialog, which) -> deleteSongFromDevice(song))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Delete song from device storage using proper APIs for all Android versions.
     */
    private void deleteSongFromDevice(SongItem song) {
        try {
            boolean deleted = false;

            // For Android 11+ (API 30+), use MediaStore to delete
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Try to delete via MediaStore - this will show system confirmation
                android.content.ContentResolver resolver = requireContext().getContentResolver();
                try {
                    int rowsDeleted = resolver.delete(song.contentUri, null, null);
                    deleted = rowsDeleted > 0;
                } catch (SecurityException e) {
                    // Need to request permission from the user
                    android.app.PendingIntent pendingIntent = android.provider.MediaStore.createDeleteRequest(
                            resolver, java.util.Collections.singletonList(song.contentUri));
                    try {
                        startIntentSenderForResult(
                                pendingIntent.getIntentSender(),
                                REQUEST_DELETE_PERMISSION,
                                null, 0, 0, 0, null);
                        return; // Will handle result in onActivityResult
                    } catch (android.content.IntentSender.SendIntentException ex) {
                        Log.e(TAG, "Error requesting delete permission", ex);
                    }
                }
            } else {
                // For older Android versions, delete file directly and update MediaStore
                java.io.File file = new java.io.File(song.path);
                if (file.exists()) {
                    deleted = file.delete();
                    if (deleted) {
                        // Also remove from MediaStore
                        requireContext().getContentResolver().delete(song.contentUri, null, null);
                    }
                }
            }

            if (deleted) {
                loadSongLibrary(); // Refresh list
                showSnackbar("Deleted: " + song.title, Snackbar.LENGTH_SHORT);
            } else {
                showSnackbar("Failed to delete. Check permissions.", Snackbar.LENGTH_LONG);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting song: " + e.getMessage(), e);
            showSnackbar("Error deleting song: " + e.getMessage(), Snackbar.LENGTH_LONG);
        }
    }

    private static final int REQUEST_DELETE_PERMISSION = 1001;

    private void setupBottomSheet() {
        if (playerBottomSheet == null)
            return;

        bottomSheetBehavior = BottomSheetBehavior.from(playerBottomSheet);
        bottomSheetBehavior.setHideable(false); // Restrict swipe down from mini
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Create a base peek height (Mini Player + BottomNav + Margin)
        int navHeight = (int) (80 * getResources().getDisplayMetrics().density);
        int miniHeight = (int) (80 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        int basePeekHeight = navHeight + miniHeight + margin;

        // Apply Window Insets to adjust Peek Height dynamically (e.g., for 3-button
        // nav)
        ViewCompat.setOnApplyWindowInsetsListener(playerBottomSheet, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Add system bottom inset (navigation bar height) to the peek height
            // This ensures the mini player sits ABOVE the nav bar
            bottomSheetBehavior.setPeekHeight(basePeekHeight + insets.bottom);

            return windowInsets;
        });

        // Request insets to ensure the listener runs immediately
        ViewCompat.requestApplyInsets(playerBottomSheet);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                isPlayerExpanded = (newState == BottomSheetBehavior.STATE_EXPANDED);
                if (onBackPressedCallback != null) {
                    onBackPressedCallback.setEnabled(isPlayerExpanded);
                }

                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    // Smoothly reset mini player translation when expanding
                    if (playerBottomSheet != null && isMiniPlayerDetached) {
                        playerBottomSheet.animate()
                                .translationY(0f)
                                .setDuration(280)
                                .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                                .start();
                        isMiniPlayerDetached = false;
                    }
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).hideBottomNavItems(); // Hide items first
                        ((MainActivity) getActivity()).setBottomNavVisibility(false);
                    }
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (getActivity() instanceof MainActivity) {
                        MainActivity activity = (MainActivity) getActivity();
                        // Show nav and animate items
                        activity.animateBottomNavEntrance();
                        activity.setBottomNavVisibility(true);
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
                        // Reset translation to normal (nav is now visible)
                        playerBottomSheet.setTranslationY(0f);
                        isMiniPlayerDetached = false;
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
            animateControlsEntrance(); // Trigger staggered animation
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
                        float diffX = e2.getX() - e1.getX();

                        // Prioritize Horizontal Swipe (Song Change)
                        if (Math.abs(diffX) > Math.abs(diffY)) {
                            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                if (diffX > 0) {
                                    // Right Swipe -> Previous
                                    performLateralTransition(-1);
                                } else {
                                    // Left Swipe -> Next
                                    performLateralTransition(1);
                                }
                                return true;
                            }
                        } else {
                            // Vertical Swipe (Collapse)
                            if (diffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                                if (isPlayerExpanded) {
                                    collapsePlayer();
                                    return true;
                                }
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

    private void performLateralTransition(int direction) {
        if (isAnimating)
            return;
        isAnimating = true;

        // Direction: 1 = Next (Swipe Left), -1 = Prev (Swipe Right)

        // Material 3 Emphasized Decelerate Interpolator
        // standard: 0.2, 0.0, 0.0, 1.0
        android.view.animation.Interpolator emphasizedDecelerate = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);

        // Animate Out props
        float targetTranslationX = (direction == 1) ? -200f : 200f; // Move LEFT for Next, RIGHT for Prev
        float targetAlpha = 0f;
        float targetScale = 0.9f; // Subtle scale down (M3 spec: 0.9 -> 1.0)

        // Animate Album Art & Text OUT
        if (cardAlbumArt != null) {
            cardAlbumArt.animate()
                    .translationX(targetTranslationX)
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setDuration(300) // Slightly longer for emphasized feel
                    .setInterpolator(emphasizedDecelerate)
                    .withEndAction(() -> {
                        // Action after animation slides out
                        if (direction == 1) {
                            viewModel.playNextSong();
                        } else {
                            viewModel.playPreviousSong();
                        }
                    })
                    .start();
        }

        // Animate Text OUT slightly faster
        if (tvSongName != null) {
            tvSongName.animate()
                    .translationX(targetTranslationX * 0.5f)
                    .alpha(0f)
                    .setDuration(200)
                    .setInterpolator(emphasizedDecelerate)
                    .start();
        }
        if (tvArtistName != null) {
            tvArtistName.animate()
                    .translationX(targetTranslationX * 0.5f)
                    .alpha(0f)
                    .setDuration(200)
                    .setInterpolator(emphasizedDecelerate)
                    .start();
        }
    }

    // NEW: Staggered Entrance Animation for Controls
    private void animateControlsEntrance() {
        // Collect views to animate in order
        View[] viewsToAnimate = new View[] {
                tvSongName,
                tvArtistName,
                chipSongHaptics,
                // seekBar, // Excluded by user request
                // tvCurrentTime, // Excluded
                // tvTotalTime, // Excluded
                btnShuffle,
                btnPrevious,
                btnPlayPauseToggle,
                btnNext,
                btnRepeat,
                secondaryControlsCard
        };

        android.view.animation.Interpolator emphasizedInterpolator = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);

        long startDelay = 50;
        long delayStep = 30; // 30ms stagger between items

        for (View view : viewsToAnimate) {
            if (view == null)
                continue;

            // Reset to initial state (slightly down and invisible)
            view.setTranslationY(50f);
            view.setAlpha(0f);

            view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay(startDelay)
                    .setInterpolator(emphasizedInterpolator)
                    .start();

            startDelay += delayStep;
        }
    }

    private void loadSongLibrary() {
        // Run MediaStore query on background thread to prevent UI lag
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<SongItem> songs = new ArrayList<>();

            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED
            };

            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

            try {
                Context context = getContext();
                if (context == null)
                    return;

                try (Cursor cursor = context.getContentResolver().query(
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
                        int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                        int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            String title = cursor.getString(titleColumn);
                            String artist = cursor.getString(artistColumn);
                            long albumId = cursor.getLong(albumIdColumn);
                            String path = cursor.getString(dataColumn);
                            long duration = cursor.getLong(durationColumn);
                            long dateAdded = cursor.getLong(dateColumn);

                            songs.add(new SongItem(id, title, artist, albumId, path, duration, dateAdded));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading songs: " + e.getMessage(), e);
            }

            // Post results to main thread
            final List<SongItem> finalSongs = songs;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (getActivity() == null || !isAdded())
                    return;

                allSongsBackup.clear();
                allSongsBackup.addAll(finalSongs);

                // Apply current filter/sort state to the new list
                int checkedId = (sortButtonGroup != null) ? sortButtonGroup.getCheckedButtonId() : View.NO_ID;

                if (checkedId == R.id.btnSortFavorites) {
                    List<SongItem> favs = new ArrayList<>();
                    // Use new FavoritesManager instance to be safe
                    com.codetrio.spatialflow.util.FavoritesManager fm = new com.codetrio.spatialflow.util.FavoritesManager(
                            requireContext());
                    for (SongItem s : finalSongs) {
                        if (fm.isFavorite(s.id))
                            favs.add(s);
                    }
                    songAdapter.setSortOrder(ExpressiveSongAdapter.SortOrder.A_Z);
                    songAdapter.submitList(favs);
                    viewModel.setSongList(songAdapter.getAllSongs());
                } else {
                    ExpressiveSongAdapter.SortOrder order = ExpressiveSongAdapter.SortOrder.A_Z;
                    if (checkedId == R.id.btnSortRecent)
                        order = ExpressiveSongAdapter.SortOrder.DATE_ADDED;
                    else if (checkedId == R.id.btnSortArtist)
                        order = ExpressiveSongAdapter.SortOrder.ARTIST;

                    songAdapter.setSortOrder(order);
                    songAdapter.submitList(finalSongs);
                    viewModel.setSongList(songAdapter.getAllSongs());
                }

                // Search results also need to be updated
                if (searchAdapter != null) {
                    searchAdapter.submitList(new ArrayList<>(finalSongs));
                }

                Log.d(TAG, "Loaded " + finalSongs.size() + " songs from library");
            });
        });
        executor.shutdown();
    }

    /**
     * Check if device has any vibration capability (not just amplitude control).
     */

    private PlayerHapticManager hapticManager;

    private void initHapticsSystem() {
        if (getContext() != null) {
            hapticManager = new PlayerHapticManager(getContext());

            // CRITICAL: Attach AFTER layout is complete, use a view that's always attached
            if (playerBottomSheet != null) {
                playerBottomSheet.post(() -> {
                    if (hapticManager != null) {
                        hapticManager.attachView(playerBottomSheet);
                        Log.d(TAG, "Haptic view attached to playerBottomSheet");
                    }
                });
            }

            // Sync initial state
            Boolean enabled = viewModel.getIsHapticsEnabled().getValue();
            boolean isEnabled = Boolean.TRUE.equals(enabled);

            if (chipSongHaptics != null) {
                chipSongHaptics.setChecked(isEnabled);
                updateChipIcon();

                chipSongHaptics.setOnCheckedChangeListener((chip, checked) -> {
                    viewModel.setHapticsEnabled(checked);
                    if (hapticManager != null) {
                        hapticManager.setHapticsEnabled(checked);
                    }
                    updateChipIcon();
                });
            }

            if (hapticManager != null) {
                hapticManager.setHapticsEnabled(isEnabled);
            }
        }
    }

    private void updateChipIcon() {
        if (chipSongHaptics == null)
            return;
        boolean isChecked = chipSongHaptics.isChecked();
        // Show icon only when active/checked
        chipSongHaptics.setChipIconVisible(isChecked);
        if (isChecked) {
            chipSongHaptics.setChipIconResource(R.drawable.ic_haptic);
        }
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
                        float progressFraction = (float) current / total;

                        if (miniProgress != null) {
                            miniProgress.setProgressCompat(waveValue, true);
                        }
                        if (waveProgress != null) {
                            waveProgress.setProgressCompat(waveValue, true);

                            // Move the progress head indicator along with progress
                            if (progressHeadIndicator != null) {
                                int progressWidth = waveProgress.getWidth();
                                int indicatorWidth = progressHeadIndicator.getWidth();
                                // Center the indicator on the exact progress point
                                float indicatorX = (progressFraction * progressWidth) - (indicatorWidth / 2f);
                                progressHeadIndicator.setTranslationX(indicatorX);
                            }
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

                // 33ms = ~30fps (sufficient for smooth progress updates, saves CPU)
                progressHandler.postDelayed(this, 33);
            }
        };

        progressHandler.post(progressRunnable);
    }

    private void initAdvancedHaptics() {
        if (!hasRecordPermission() || audioService == null || hapticManager == null) {
            Log.w(TAG, "Cannot init - no permission, service or haptic manager");
            return;
        }

        try {
            int audioSessionId = audioService.getAudioSessionId();
            if (audioSessionId == 0) {
                Log.w(TAG, "Invalid audio session");
                return;
            }

            hapticManager.initVisualizer(audioSessionId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to init haptics: " + e.getMessage(), e);
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
            if (hapticsEnabled != null && hapticsEnabled && hapticManager != null) {
                if (isActuallyPlaying) {
                    // Playback resumed - ensure haptics are active
                    if (audioService != null) {
                        initAdvancedHaptics();
                    }
                    try {
                        // Re-enable if initialized
                        if (hapticManager.hasHapticsCapability()) {
                            hapticManager.setHapticsEnabled(true);
                            Log.d(TAG, "Haptics enabled on playback resume");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error enabling haptics: " + e.getMessage());
                    }
                } else {
                    // Playback paused - disable temporary
                    try {
                        hapticManager.setHapticsEnabled(false);
                        Log.d(TAG, "Haptics disabled on playback pause");
                    } catch (Exception e) {
                        Log.e(TAG, "Error disabling haptics: " + e.getMessage());
                    }
                }
            }
        });

        // FIXED: Add direct observer for Haptics Enabled state
        viewModel.getIsHapticsEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null && hapticManager != null) {
                hapticManager.setHapticsEnabled(enabled);

                if (enabled) {
                    // Try to init visualizer if service is ready
                    if (audioService != null) {
                        initAdvancedHaptics();
                    }
                } else {
                    disableAdvancedHaptics();
                }

                // Sync Chip UI state safely
                if (chipSongHaptics != null) {
                    if (chipSongHaptics.isChecked() != enabled) {
                        // This triggers the listener which handles the animation update
                        chipSongHaptics.setChecked(enabled);
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
                loadSongMetadata(uri); // REMOVED: Redundant, handled by Glide in
                // getCurrentSong

                // Method removed

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
            if (bassBoost != null && hapticManager != null) {
                hapticManager.bassBoostMultiplier = 1.0f + (bassBoost / 12.0f) * 0.8f;
                hapticManager.bassBoostMultiplier = Math.max(0.7f, Math.min(1.8f, hapticManager.bassBoostMultiplier));
            }
        });

        viewModel.getLoudnessGain().observe(getViewLifecycleOwner(), loudness -> {
            if (loudness != null && hapticManager != null) {
                hapticManager.loudnessMultiplier = 1.0f + (loudness / 10.0f) * 0.5f;
            }
        });

        viewModel.getEqBand1().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand2().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand3().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand4().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());
        viewModel.getEqBand5().observe(getViewLifecycleOwner(), gain -> updateEqMultipliers());

        viewModel.getPlaybackSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed != null && hapticManager != null) {
                hapticManager.playbackSpeedFactor = speed;
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

                // Handle "Slide In" Animation if coming from a transition
                // We define the logic here but execute it AFTER image load to prevent
                // flickering
                Runnable animateInCallback = () -> {
                    if (isAnimating) {
                        float entryX = 200f;
                        if (cardAlbumArt != null) {
                            if (cardAlbumArt.getTranslationX() > 0)
                                entryX = -200f; // If exited Right, enter Left
                            else if (cardAlbumArt.getTranslationX() < 0)
                                entryX = 200f;

                            cardAlbumArt.setTranslationX(entryX);
                            cardAlbumArt.setScaleX(0.9f); // Start slightly scaled down
                            cardAlbumArt.setScaleY(0.9f);
                            cardAlbumArt.setAlpha(0f);

                            android.view.animation.Interpolator emphasizedDecelerate = new PathInterpolator(0.2f, 0.0f,
                                    0.0f, 1.0f);

                            cardAlbumArt.animate()
                                    .translationX(0f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .alpha(1f)
                                    .setDuration(400) // Longer entry
                                    .setInterpolator(emphasizedDecelerate)
                                    .withEndAction(() -> isAnimating = false)
                                    .start();
                        }

                        if (tvSongName != null) {
                            tvSongName.setTranslationX(entryX * 0.5f);
                            tvSongName.setAlpha(0f);
                            tvSongName.animate()
                                    .translationX(0f)
                                    .alpha(1f)
                                    .setDuration(400)
                                    .setStartDelay(50)
                                    .setInterpolator(new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f))
                                    .start();
                        }
                        if (tvArtistName != null) {
                            tvArtistName.setTranslationX(entryX * 0.5f);
                            tvArtistName.setAlpha(0f);
                            tvArtistName.animate()
                                    .translationX(0f)
                                    .alpha(1f)
                                    .setDuration(400)
                                    .setStartDelay(50)
                                    .setInterpolator(new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f))
                                    .start();
                        }
                    } else {
                        // Ensure state is clean if not animating
                        if (cardAlbumArt != null) {
                            cardAlbumArt.setTranslationX(0f);
                            cardAlbumArt.setScaleX(1f);
                            cardAlbumArt.setScaleY(1f);
                            cardAlbumArt.setAlpha(1f);
                        }
                        if (tvSongName != null) {
                            tvSongName.setTranslationX(0f);
                            tvSongName.setAlpha(1f);
                        }
                        if (tvArtistName != null) {
                            tvArtistName.setTranslationX(0f);
                            tvArtistName.setAlpha(1f);
                        }
                    }
                };

                Uri artUri = song.getAlbumArtUri();
                Glide.with(this)
                        .asBitmap()
                        .load(artUri)
                        .placeholder(R.drawable.default_album_art)
                        .error(R.drawable.default_album_art)
                        .centerCrop()
                        .thumbnail(0.25f) // Load low-res first for faster display
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE)
                        .addListener(new RequestListener<Bitmap>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target,
                                    boolean isFirstResource) {
                                // Animate anyway with placeholder/error
                                if (getActivity() != null)
                                    getActivity().runOnUiThread(animateInCallback);
                                applyMaterialDynamicCalibration();
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target,
                                    DataSource dataSource, boolean isFirstResource) {
                                // Animate In with valid art
                                if (getActivity() != null)
                                    getActivity().runOnUiThread(animateInCallback);

                                // Extract colors from the ACTUAL bitmap we just loaded
                                extractGradientFromBitmap(resource);
                                return false;
                            }
                        })
                        .into(ivAlbumArt);

                if (ivMiniAlbumArt != null) {
                    Glide.with(this)
                            .load(artUri)
                            .placeholder(R.drawable.default_album_art)
                            .error(R.drawable.default_album_art)
                            .centerCrop()
                            .into(ivMiniAlbumArt);
                }

                // extractGradientFromAlbumArt(artUri); // REMOVED - handled in Glide listener
                // above

                // Update Adapter selection when song changes (via next/prev or click)
                if (songAdapter != null) {
                    int pos = songAdapter.getPositionById(song.id);
                    songAdapter.setCurrentlyPlaying(pos);
                }

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
                // Actually apply the effects (except 8D which resets)
                viewModel.applyAllEffects();
                // Show brief tooltip confirming effects are applied
                Snackbar.make(coordinatorLayout, R.string.effects_applied_tooltip, Snackbar.LENGTH_SHORT)
                        .setAnchorView(playerBottomSheet)
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
        if (hapticManager == null)
            return;

        Integer band1 = viewModel.getEqBand1().getValue();
        Integer band2 = viewModel.getEqBand2().getValue();
        Integer band3 = viewModel.getEqBand3().getValue();
        Integer band4 = viewModel.getEqBand4().getValue();
        Integer band5 = viewModel.getEqBand5().getValue();

        if (band1 != null && band2 != null) {
            float avgBassGain = (band1 + band2) / 2.0f;
            hapticManager.eqBassMultiplier = 1.0f + (avgBassGain / 15.0f) * 0.6f;
            hapticManager.eqBassMultiplier = Math.max(0.4f, Math.min(1.6f, hapticManager.eqBassMultiplier));
        }

        if (band3 != null) {
            hapticManager.eqMidMultiplier = 1.0f + (band3 / 15.0f) * 0.5f;
            hapticManager.eqMidMultiplier = Math.max(0.5f, Math.min(1.5f, hapticManager.eqMidMultiplier));
        }

        if (band4 != null && band5 != null) {
            float avgHighGain = (band4 + band5) / 2.0f;
            hapticManager.eqHighMultiplier = 1.0f + (avgHighGain / 15.0f) * 0.4f;
            hapticManager.eqHighMultiplier = Math.max(0.6f, Math.min(1.4f, hapticManager.eqHighMultiplier));
        }
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

    private void setupListeners() {
        btnPlayPauseToggle.setOnClickListener(v -> {
            Boolean playing = viewModel.getIsPlaying().getValue();
            if (playing != null && playing)
                viewModel.pauseAudio();
            else
                viewModel.playAudio();
        });

        btnPrevious.setOnClickListener(v -> performLateralTransition(-1));
        btnNext.setOnClickListener(v -> performLateralTransition(1));

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
        if (chipSongHaptics != null && hapticManager != null) {
            boolean hasHaptics = hapticManager.hasHapticsCapability();
            chipSongHaptics.setEnabled(hasHaptics);
            chipSongHaptics.setAlpha(hasHaptics ? 1.0f : 0.5f);

            Boolean savedState = viewModel.getIsHapticsEnabled().getValue();
            chipSongHaptics.setChecked(savedState != null && savedState && hasHaptics);

            chipSongHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Check permission FIRST when enabling
                if (isChecked && !hasRecordPermission()) {
                    // Revert toggle and request permission
                    chipSongHaptics.setChecked(false);
                    requestRecordAudioPermission();
                    return;
                }
                viewModel.setHapticsEnabled(isChecked);
                updateHapticsChipVisuals(isChecked, true);
                updateChipIcon();
            });
            // Init visuals without animation
            updateHapticsChipVisuals(chipSongHaptics.isChecked(), false);
            updateChipIcon();
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
            return;
        }

        if (hapticManager != null && audioService != null) {
            // Only init if not already initialized
            hapticManager.initVisualizer(audioService.getAudioSessionId());
        }
    }

    private void disableAdvancedHaptics() {
        if (hapticManager != null) {
            hapticManager.release();
            Log.d(TAG, "Haptics DISABLED");
        }
    }

    private void releaseVisualizer() {
        if (hapticManager != null) {
            hapticManager.release();
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
        if (hapticManager != null) {
            hapticManager.detachView();
        }
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
                & Configuration.UI_MODE_NIGHT_MASK;
        this.isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        // Material 3 Expressive Dynamic Colors
        int surfaceHigh = MaterialColors.getColor(requireContext(),
                R.attr.colorSurfaceContainerHigh, Color.GRAY);
        int secContainer = MaterialColors.getColor(requireContext(),
                R.attr.colorSecondaryContainer, Color.GRAY);
        int tertContainer = MaterialColors.getColor(requireContext(),
                R.attr.colorTertiaryContainer, Color.GRAY);
        int primContainer = MaterialColors.getColor(requireContext(),
                R.attr.colorPrimaryContainer, Color.GRAY);

        int[] defaultColors = new int[] { primContainer, tertContainer, secContainer, surfaceHigh };
        gradientBackground.setColors(defaultColors);
        gradientBackground.setIsDarkMode(isDarkMode);

        int primaryColor = MaterialColors.getColor(requireContext(), R.attr.colorOnSurface,
                Color.BLACK);
        int secondaryColor = MaterialColors.getColor(requireContext(),
                R.attr.colorOnSurfaceVariant, Color.DKGRAY);

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
            btnPlayPauseToggle.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            btnPlayPauseToggle.setIconTint(ColorStateList
                    .valueOf(isDarkMode ? Color.BLACK : Color.WHITE));
        }

        if (btnPrevious != null)
            btnPrevious.setIconTint(ColorStateList.valueOf(primaryColor));
        if (btnNext != null)
            btnNext.setIconTint(ColorStateList.valueOf(primaryColor));

        ColorStateList secTint = ColorStateList.valueOf(secondaryColor);
        if (btnShuffle != null)
            btnShuffle.setIconTint(secTint);
        if (btnRepeat != null)
            btnRepeat.setIconTint(secTint);
        if (btnFavorite != null)
            btnFavorite.setIconTint(secTint);

        if (btnMiniPlayPause != null) {
            btnMiniPlayPause.setBackgroundTintList(
                    ColorStateList.valueOf(Color.TRANSPARENT));
            btnMiniPlayPause.setIconTint(ColorStateList.valueOf(primaryColor));
        }
        if (btnMiniPrevious != null)
            btnMiniPrevious.setIconTint(ColorStateList.valueOf(primaryColor));
        if (btnMiniNext != null)
            btnMiniNext.setIconTint(ColorStateList.valueOf(primaryColor));

        if (miniProgress != null) {
            miniProgress.setIndicatorColor(primaryColor);
            miniProgress.setTrackColor((primaryColor & 0x00FFFFFF) | 0x33000000);
        }
    }

    // State flag for mini player detachment
    private boolean isMiniPlayerDetached = false;

    /**
     * Detach mini player - slides down to bottom when nav hides.
     */
    private void detachMiniPlayer() {
        if (isMiniPlayerDetached || playerBottomSheet == null)
            return;
        isMiniPlayerDetached = true;

        // Get nav bar height to slide down by that amount
        float navHeight = 80 * getResources().getDisplayMetrics().density;

        // Slide mini player down to fill the nav's space
        // 100ms delay to sync with nav bar slide (which also has 100ms delay after menu
        // items hide)
        playerBottomSheet.animate()
                .translationY(navHeight)
                .setStartDelay(100) // Sync with nav bar slide
                .setDuration(280) // Match nav duration
                .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                .start();
    }

    /**
     * Attach mini player - slides back up when nav shows.
     */
    private void attachMiniPlayer() {
        if (!isMiniPlayerDetached || playerBottomSheet == null)
            return;
        isMiniPlayerDetached = false;

        // Slide mini player back up to original position (sync with nav show - 280ms)
        // Matches MainActivity.showBottomNavWithAnimation()
        playerBottomSheet.animate()
                .translationY(0f)
                .setStartDelay(100) // Match detach delay for symmetry
                .setDuration(280) // Strictly match nav duration
                .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                .start();
    }

    /**
     * Reset mini player translation based on current nav visibility.
     * Call this when player collapses from full to mini.
     */
    public void resetMiniPlayerTranslation() {
        if (playerBottomSheet == null)
            return;

        // Check if nav is currently hidden
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            if (activity.isBottomNavHidden()) {
                // Nav is hidden, mini player should be down
                float navHeight = 80 * getResources().getDisplayMetrics().density;
                playerBottomSheet.setTranslationY(navHeight);
                isMiniPlayerDetached = true;
            } else {
                // Nav is visible, mini player should be up
                playerBottomSheet.setTranslationY(0f);
                isMiniPlayerDetached = false;
            }
        }
    }

    // Dynamic colors for Haptic Chip Animation
    private int hapticChipBaseColor = 0x33FFFFFF; // Default
    private int hapticChipFillColor = 0xFF6200EE; // Default Purple

    private void updateHapticsChipVisuals(boolean isChecked, boolean animate) {
        // Effects removed per user request.
        // Standard Material Chip behavior handles visuals now.
    }

    private void extractGradientFromBitmap(Bitmap bitmap) {
        if (bitmap == null || gradientBackground == null)
            return;

        Palette.from(bitmap).generate(palette -> {
            if (palette == null)
                return;

            // Extract vibrant and muted colors
            int vibrant = palette.getVibrantColor(0xFF000000);
            int vibrantDark = palette.getDarkVibrantColor(0xFF000000);
            int muted = palette.getMutedColor(0xFF000000);
            int mutedDark = palette.getDarkMutedColor(0xFF000000);
            int dominant = palette.getDominantColor(0xFF000000);

            // Construct palette for the gradient view
            int[] colors = new int[] { vibrant, vibrantDark, muted, mutedDark, dominant };

            // Filter out pure blacks if possible (unless all black)
            List<Integer> validColors = new ArrayList<>();
            for (int c : colors) {
                if (c != 0xFF000000 && c != 0)
                    validColors.add(c);
            }
            if (validColors.isEmpty())
                validColors.add(0xFF1a1a2e); // Default dark

            int[] finalColors = new int[validColors.size()];
            for (int i = 0; i < validColors.size(); i++)
                finalColors[i] = validColors.get(i);

            gradientBackground.setColors(finalColors);
        });
    }
}
