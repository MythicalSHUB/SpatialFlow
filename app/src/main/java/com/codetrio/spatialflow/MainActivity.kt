@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.ui.EffectsScreenEntryPoint
import com.codetrio.spatialflow.ui.PlayerBottomSheetCompose
import com.codetrio.spatialflow.ui.SettingsScreenContent
import com.codetrio.spatialflow.ui.TagEditorScreenEntryPoint
import com.codetrio.spatialflow.ui.explore.ExploreScreen
import com.codetrio.spatialflow.ui.explore.GoogleSignInScreen
import com.codetrio.spatialflow.ui.library.LibraryScreen
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import com.codetrio.spatialflow.ui.theme.observeKey
import com.codetrio.spatialflow.ui.onboarding.OnboardingScreen
import android.content.Context
import com.codetrio.spatialflow.update.GitHubReleaseClient
import com.codetrio.spatialflow.update.UpdateManager
import com.codetrio.spatialflow.update.VersionUtils
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val playerViewModel: PlayerSharedViewModel by viewModels()
    private val exploreViewModel: ExploreViewModel by viewModels()

    private var navController: NavController? = null
    private var previousDestination = "explore"
    private var isNavigating = false

    var audioService: AudioPlaybackService? = null
    var isServiceBound = false
    lateinit var updateManager: UpdateManager

    var isBottomNavVisible by mutableStateOf(true)
        private set

    fun hideBottomNavWithAnimation() {
        isBottomNavVisible = false
    }

    fun showBottomNavWithAnimation() {
        isBottomNavVisible = true
    }

    private val serviceConnection = object : ServiceConnection {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as? AudioPlaybackService.LocalBinder
            audioService = binder?.getService()
            isServiceBound = true
            audioService?.let {
                playerViewModel.audioService = it
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            isServiceBound = false
            audioService = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        val splashStartTime = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - splashStartTime < SPLASH_DURATION
        }

        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        setupSystemBars()

        playerViewModel.loadLastPlaybackState()

        startAudioService()
        // Permissions are now handled in Onboarding Screen

        setContent {
            val prefs = remember { getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
            val themeMode by prefs.observeKey("theme_mode", "system")
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
            val playerBackgroundColor by playerViewModel.playerBackgroundColor.collectAsStateWithLifecycle()
            val dynamicAlbumColor = if (currentSong != null) playerBackgroundColor else null

            SpatialFlowTheme(darkTheme = isDarkTheme, dynamicAlbumColor = dynamicAlbumColor) {
                val hideNavLabels by prefs.observeKey("hide_nav_labels", false)
                val dynamicNavStyle by prefs.observeKey("dynamic_nav_style", false)
                val hasSeenOnboarding by prefs.observeKey("has_seen_onboarding_1_8", false)

                val currentNavController = rememberNavController()
                LaunchedEffect(currentNavController) {
                    navController = currentNavController
                }

                val isPlayerExpanded by playerViewModel.isPlayerExpanded.collectAsStateWithLifecycle()

                var editingSong by remember { mutableStateOf<SongItem?>(null) }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        val navBackStackEntry by currentNavController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        val showBottomBar = currentDestination?.route in listOf("explore", "library", "effects", "settings")

                        val density = LocalDensity.current
                        val navigationBarsHeightPx = WindowInsets.navigationBars.getBottom(density).toFloat()
                        val bottomNavHeight = 80.dp
                        val bottomNavHeightPx = with(density) { bottomNavHeight.toPx() }
                        val totalSlideDistPx = bottomNavHeightPx + navigationBarsHeightPx

                        val playerExpansionFractionState = playerViewModel.playerExpansionFraction.collectAsState()

                        // This is the target for general bottom nav visibility (like scrolling or settings screen)
                        val baseTargetTranslation = if (showBottomBar && isBottomNavVisible) 0f else totalSlideDistPx

                        val animatedBaseTranslationState = animateFloatAsState(
                            targetValue = baseTargetTranslation,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "BottomNavTranslationY"
                        )

                        // Monitor translations without triggering recomposition every frame
                        LaunchedEffect(totalSlideDistPx) {
                            snapshotFlow {
                                val currentBase = animatedBaseTranslationState.value
                                val currentFraction = playerExpansionFractionState.value
                                (currentBase + (totalSlideDistPx * currentFraction)).coerceAtMost(totalSlideDistPx)
                            }.collect { translationY ->
                                playerViewModel.setBottomNavTranslationY(translationY)
                            }
                        }

                        val navBarHeight by animateDpAsState(if (dynamicNavStyle) 64.dp else 80.dp, label = "navBarHeight")
                        val navIconSize by animateDpAsState(if (dynamicNavStyle) 34.dp else 26.dp, label = "navIconSize")
                        val navElevation by animateDpAsState(if (dynamicNavStyle) 8.dp else 3.dp, label = "navElevation")
                        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                        NavigationBar(
                            modifier = Modifier
                                .graphicsLayer {
                                    val currentBase = animatedBaseTranslationState.value
                                    val currentFraction = playerExpansionFractionState.value
                                    val translationY = (currentBase + (totalSlideDistPx * currentFraction)).coerceAtMost(totalSlideDistPx)
                                    this.translationY = translationY
                                    this.alpha = if (translationY >= totalSlideDistPx - 1f) 0f else 1f
                                }
                                .height(navBarHeight + bottomPadding)
                                .onGloballyPositioned { coordinates ->
                                    val height = coordinates.size.height.toFloat()
                                    playerViewModel.setBottomNavHeight(height)
                                },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = navElevation
                        ) {
                            val items = listOf(
                                Triple("explore", "Explore", R.drawable.ic_explore),
                                Triple("library", "Library", R.drawable.ic_library_music),
                                Triple("effects", "Effects", R.drawable.ic_equalizer),
                                Triple("settings", "Settings", R.drawable.ic_settings)
                            )
                             items.forEach { (route, label, iconResId) ->
                                val selected = currentDestination?.route == route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (route == "explore") {
                                            exploreViewModel.resetToHome()
                                        }
                                        if (!selected) {
                                            currentNavController.navigate(route) {
                                                popUpTo(currentNavController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(painter = painterResource(id = iconResId), contentDescription = label, modifier = Modifier.size(navIconSize)) },
                                    label = if (hideNavLabels) null else { { Text(label) } }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    val navBackStackEntry by currentNavController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    val showBottomBar = currentDestination?.route in listOf("explore", "library", "effects", "settings")

                    val playerExpansionFractionState = playerViewModel.playerExpansionFraction.collectAsState()

                    // Base padding target not considering expansion
                    val baseTargetPadding = if (showBottomBar && isBottomNavVisible) {
                        paddingValues.calculateBottomPadding()
                    } else {
                        0.dp
                    }

                    val animatedBasePaddingState = animateDpAsState(
                        targetValue = baseTargetPadding,
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "BottomNavPadding"
                    )

                    // Final padding does not scale down during expansion to prevent massive Layout invalidation
                    // The player sheet covers the NavHost anyway, so keeping padding static is optimal
                    val finalPadding = animatedBasePaddingState.value

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = finalPadding.coerceAtLeast(0.dp))
                        ) {
                            val routeIndices = remember {
                                mapOf(
                                    "explore" to 0,
                                    "library" to 1,
                                    "effects" to 2,
                                    "settings" to 3
                                )
                            }

                            NavHost(
                                navController = currentNavController,
                                startDestination = if (hasSeenOnboarding) "explore" else "onboarding",
                                modifier = Modifier.fillMaxSize(),
                                enterTransition = {
                                    val initialRoute = initialState.destination.route ?: ""
                                    val targetRoute = targetState.destination.route ?: ""
                                    val initialIndex = routeIndices[initialRoute] ?: 0
                                    val targetIndex = routeIndices[targetRoute] ?: 0
                                    
                                    if (targetIndex > initialIndex) {
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeIn(animationSpec = tween(220))
                                    } else {
                                        slideInHorizontally(
                                            initialOffsetX = { -it },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeIn(animationSpec = tween(220))
                                    }
                                },
                                exitTransition = {
                                    val initialRoute = initialState.destination.route ?: ""
                                    val targetRoute = targetState.destination.route ?: ""
                                    val initialIndex = routeIndices[initialRoute] ?: 0
                                    val targetIndex = routeIndices[targetRoute] ?: 0
                                    
                                    if (targetIndex > initialIndex) {
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 3 },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeOut(animationSpec = tween(220))
                                    } else {
                                        slideOutHorizontally(
                                            targetOffsetX = { it / 3 },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeOut(animationSpec = tween(220))
                                    }
                                },
                                popEnterTransition = {
                                    val initialRoute = initialState.destination.route ?: ""
                                    val targetRoute = targetState.destination.route ?: ""
                                    val initialIndex = routeIndices[initialRoute] ?: 0
                                    val targetIndex = routeIndices[targetRoute] ?: 0
                                    
                                    if (targetIndex > initialIndex) {
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeIn(animationSpec = tween(220))
                                    } else {
                                        slideInHorizontally(
                                            initialOffsetX = { -it },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeIn(animationSpec = tween(220))
                                    }
                                },
                                popExitTransition = {
                                    val initialRoute = initialState.destination.route ?: ""
                                    val targetRoute = targetState.destination.route ?: ""
                                    val initialIndex = routeIndices[initialRoute] ?: 0
                                    val targetIndex = routeIndices[targetRoute] ?: 0
                                    
                                    if (targetIndex > initialIndex) {
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 3 },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeOut(animationSpec = tween(220))
                                    } else {
                                        slideOutHorizontally(
                                            targetOffsetX = { it / 3 },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeOut(animationSpec = tween(220))
                                    }
                                }
                            ) {
                                composable("onboarding") {
                                    OnboardingScreen(
                                        onComplete = {
                                            currentNavController.navigate("explore") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("explore") {
                                    ExploreScreen(
                                        viewModel = exploreViewModel,
                                        playerSharedViewModel = playerViewModel,
                                        onNavigateToLibrary = {
                                            currentNavController.navigate("library") {
                                                popUpTo(currentNavController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                                composable("library") {
                                    LibraryScreen(
                                        viewModel = playerViewModel,
                                        onEditSong = { song ->
                                            editingSong = song
                                            currentNavController.navigate("tag_editor")
                                        },
                                        onNavigateToExplore = {
                                            currentNavController.navigate("explore") {
                                                popUpTo(currentNavController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                                composable("effects") {
                                    EffectsScreenEntryPoint(viewmodel = playerViewModel)
                                }
                                composable("settings") {
                                    SettingsScreenContent(
                                    )
                                }
                                composable("tag_editor") {
                                    editingSong?.let { song ->
                                        TagEditorScreenEntryPoint(
                                            song = song,
                                            onNavigateUp = {
                                                currentNavController.navigateUp()
                                            }
                                        )
                                    }
                                }
                                composable("google_signin") {
                                    val accountViewModel: com.codetrio.spatialflow.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                    GoogleSignInScreen(
                                        accountViewModel = accountViewModel,
                                        onSignInSuccess = {
                                            currentNavController.navigate("explore") {
                                                popUpTo("explore") { inclusive = true }
                                            }
                                        },
                                        onNavigateUp = {
                                            currentNavController.navigateUp()
                                        }
                                    )
                                }
                            }
                        }

                        PlayerBottomSheetCompose(
                            activity = this@MainActivity,
                            viewModel = playerViewModel
                        )
                    }
                }
            }
        }

        handleIntent(intent)

        updateManager = UpdateManager(this)
        checkForUpdateOnLaunch()
    }

    fun navigateToSettings() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "settings") {
            controller.navigate("settings") {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            previousDestination = "settings"
        }
    }

    fun navigateToGoogleSignIn() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "google_signin") {
            controller.navigate("google_signin")
        }
    }

    fun showArtistPage(artistId: String?, artistName: String?) {
        val controller = navController ?: return
        val cameFromOutside = controller.currentDestination?.route != "explore"
        
        if (cameFromOutside) {
            isNavigating = true
            controller.navigate("explore") {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            previousDestination = "explore"
            isNavigating = false
        }

        if (!artistId.isNullOrBlank()) {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.loadArtist(artistId)
        } else if (!artistName.isNullOrBlank() && artistName != "Unknown Artist") {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.setSearchFilter(com.codetrio.spatialflow.data.innertube.SearchFilter.ARTISTS)
            exploreViewModel.search(artistName)
        }
    }

    private fun checkForUpdateOnLaunch() {
        if (!shouldCheckForUpdate()) {
            Log.d(TAG, "Update check skipped (checked recently)")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = GitHubReleaseClient("MythicalSHUB", "SpatialFlow")
                val release = client.latestRelease ?: return@launch

                val currentVersion = BuildConfig.VERSION_NAME
                if (VersionUtils.isNewer(release.tagName, currentVersion)) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(release)
                    }
                } else {
                    Log.d(TAG, "App is up to date (current: $currentVersion, latest: ${release.tagName})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }
    }

    private fun showUpdateDialog(release: GitHubReleaseClient.ReleaseInfo) {
        val message = StringBuilder("Version ${release.tagName} is now available!\n\n")
        release.changelog?.takeIf { it.isNotEmpty() }?.let { changelog ->
            val displayLog = if (changelog.length > 350) "${changelog.substring(0, 350)}..." else changelog
            message.append("What's New:\n").append(displayLog)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Update Available")
            .setMessage(message.toString())
            .setCancelable(true)
            .setPositiveButton("Update Now") { _, _ ->
                val rootView = findViewById<View>(android.R.id.content)
                updateManager.checkForUpdate(rootView, BuildConfig.VERSION_NAME)
            }
            .setNegativeButton("Later") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Don't Show Again") { dialog, _ ->
                disableUpdateCheck()
                dialog.dismiss()
            }
            .show()
    }

    private fun shouldCheckForUpdate(): Boolean {
        val prefs = getSharedPreferences("update_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("update_check_disabled", false)) return false

        val lastCheck = prefs.getLong("last_update_check", 0)
        val currentTime = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        return if (currentTime - lastCheck > oneDayMillis) {
            prefs.edit { putLong("last_update_check", currentTime) }
            true
        } else false
    }

    private fun disableUpdateCheck() {
        getSharedPreferences("update_prefs", MODE_PRIVATE).edit {
            putBoolean("update_check_disabled", true)
        }
        Log.d(TAG, "Auto update check disabled by user")
    }

    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioPlaybackService::class.java)
        try {
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "startService failed, relying on bind: ${e.message}")
        }
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        Log.d(TAG, "Audio service started and bound")
    }

    private fun ensureServiceRunning() {
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return
        val isRunning = manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == AudioPlaybackService::class.java.name
        }
        if (!isRunning) startAudioService()
    }

    private fun checkAudioPermission() {
        val requiredPermissions = getRequiredRuntimePermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), AUDIO_PERMISSION_REQUEST)
        }
    }

    private fun getRequiredRuntimePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECORD_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    override fun onResume() {
        super.onResume()
        setupSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupSystemBars()
    }

    fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PREVIOUS_DESTINATION, previousDestination)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController?.navigateUp() == true || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        ensureServiceRunning()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
                Log.d(TAG, "Service not bound, skipping unbind")
            }
            isServiceBound = false
        }
    }
    fun showSnackbar(message: String, duration: Int) {
        com.codetrio.spatialflow.ui.SnackbarController.showMessage(message)
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (Intent.ACTION_VIEW == intent.action || "android.intent.action.MUSIC_PLAYER" == intent.action) {
            intent.data?.let {
                playExternalUri(it)
                return
            }
        }

        if (navController != null && intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            try {
                navController?.navigate("library")
            } catch (e: Exception) {
                Log.e(TAG, "Navigation request failed in handleIntent", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun playExternalUri(uri: Uri) {
        var displayName = "External Track"
        if ("content" == uri.scheme) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.takeIf { it.isNotEmpty() }?.let {
                                displayName = it
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query metadata for external uri", e)
            }
        } else if ("file" == uri.scheme) {
            displayName = uri.lastPathSegment ?: displayName
        }

        if (displayName.contains(".")) {
            val dotIdx = displayName.lastIndexOf('.')
            if (dotIdx > 0) displayName = displayName.substring(0, dotIdx)
        }

        val externalId = -System.currentTimeMillis()
        val externalSong = SongItem(
            externalId,
            displayName,
            "External Source",
            -1L,
            uri.toString(),
            0L,
            System.currentTimeMillis() / 1000
        ).apply { contentUri = uri }

        val triggerPlayTask = Runnable {
            playerViewModel.playSong(externalSong)
            navController?.let { controller ->
                try {
                    controller.navigate("library")
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation route to PlayerFragment failed in playExternalUri", e)
                }
            }
        }

        if (!isServiceBound) {
            lifecycleScope.launch {
                playerViewModel.audioServiceState.collect { service ->
                    if (service != null) {
                        triggerPlayTask.run()
                        this@launch.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                    }
                }
            }
        } else {
            triggerPlayTask.run()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val AUDIO_PERMISSION_REQUEST = 100
        private const val SPLASH_DURATION = 800L
        private const val KEY_PREVIOUS_DESTINATION = "key_previous_destination"
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
