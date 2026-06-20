@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.ui.onboarding
    
    import android.Manifest
    import android.annotation.SuppressLint
    import android.content.Context
    import android.content.pm.PackageManager
    import android.os.Build
    import android.os.VibrationEffect
    import android.os.Vibrator
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.animation.AnimatedContent
    import androidx.compose.animation.SizeTransform
    import androidx.compose.animation.core.FastOutSlowInEasing
    import androidx.compose.animation.core.Spring
    import androidx.compose.animation.core.animateFloat
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.spring
    import androidx.compose.animation.core.tween
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
    import androidx.compose.animation.slideInVertically
    import androidx.compose.animation.slideOutVertically
    import androidx.compose.animation.togetherWith
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.Image
    import androidx.compose.foundation.background
    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.layout.Row
    import androidx.compose.foundation.layout.Spacer
    import androidx.compose.foundation.layout.aspectRatio
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.fillMaxWidth
    import androidx.compose.foundation.layout.height
    import androidx.compose.foundation.layout.heightIn
    import androidx.compose.foundation.layout.navigationBarsPadding
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.layout.size
    import androidx.compose.foundation.layout.width
    import androidx.compose.foundation.pager.HorizontalPager
    import androidx.compose.foundation.pager.PagerState
    import androidx.compose.foundation.pager.rememberPagerState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.ArrowBack
    import androidx.compose.material.icons.filled.ArrowForward
    import androidx.compose.material.icons.filled.Check
    import androidx.compose.material.icons.filled.CheckCircle
    import androidx.compose.material.icons.filled.DarkMode
    import androidx.compose.material.icons.filled.Home
    import androidx.compose.material.icons.filled.LibraryMusic
    import androidx.compose.material.icons.filled.LightMode
    import androidx.compose.material.icons.filled.Mic
    import androidx.compose.material.icons.filled.Notifications
    import androidx.compose.material.icons.filled.Search
    import androidx.compose.material.icons.filled.Settings
    import androidx.compose.material3.Card
    import androidx.compose.material3.CardDefaults
    import androidx.compose.material3.ExperimentalMaterial3Api
    import androidx.compose.material3.FloatingActionButton
    import androidx.compose.material3.FloatingActionButtonDefaults
    import androidx.compose.material3.Icon
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.NavigationBar
    import androidx.compose.material3.NavigationBarItem
    import androidx.compose.material3.Scaffold
    import androidx.compose.material3.Surface
    import androidx.compose.material3.Switch
    import androidx.compose.material3.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.LaunchedEffect
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableFloatStateOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.remember
    import androidx.compose.runtime.rememberCoroutineScope
    import androidx.compose.runtime.saveable.rememberSaveable
    import androidx.compose.runtime.setValue
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.draw.rotate
    import androidx.compose.ui.draw.shadow
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.painterResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.core.content.ContextCompat
    import com.codetrio.spatialflow.R
    import kotlinx.coroutines.launch
    import androidx.core.content.edit

@OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun OnboardingScreen(
        onComplete: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { 9 })
        val prefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
        val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
        val hasHaptics = remember { vibrator?.hasVibrator() == true }
        
        var isLoggedIn by remember { mutableStateOf(com.codetrio.spatialflow.data.innertube.AccountManager.isLoggedIn(context)) }
        var userName by remember { mutableStateOf("Connected User") }
        var userProfileUrl by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn) {
                val result = com.codetrio.spatialflow.data.innertube.YouTubeMusic.accountProfile()
                userName = result.getOrNull()?.name ?: "Connected User"
                userProfileUrl = result.getOrNull()?.avatarUrl
            }
        }
        
        var hapticsLevel by rememberSaveable { mutableFloatStateOf(prefs.getFloat("vibration_strength", 80f)) }
        var themeMode by rememberSaveable { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
        var hideNavLabels by rememberSaveable { mutableStateOf(prefs.getBoolean("hide_nav_labels", false)) }
        var dynamicNavStyle by rememberSaveable { mutableStateOf(prefs.getBoolean("dynamic_nav_style", false)) }
    
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        val micPermission = Manifest.permission.RECORD_AUDIO
    
        var audioGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) }
        var notifGranted by remember { mutableStateOf(notifPermission?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true) }
        var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, micPermission) == PackageManager.PERMISSION_GRANTED) }
        
        val allPermissionsGranted = audioGranted && notifGranted && micGranted
    
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            audioGranted = permissions[audioPermission] ?: audioGranted
            if (notifPermission != null) {
                notifGranted = permissions[notifPermission] ?: notifGranted
            }
            micGranted = permissions[micPermission] ?: micGranted
        }
    
        Scaffold(
            bottomBar = {
                val isNextEnabled = if (pagerState.currentPage == 4) allPermissionsGranted else true
                SetupBottomBar(
                    pagerState = pagerState,
                    isNextEnabled = isNextEnabled,
                    onNextClicked = {
                        if (isNextEnabled && pagerState.currentPage < pagerState.pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    },
                    onBackClicked = {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage - 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    },
                    onFinishClicked = {
                        prefs.edit {
                            putBoolean("has_seen_onboarding_1_8", true)
                                .putFloat("vibration_strength", hapticsLevel)
                                .putString("theme_mode", themeMode)
                                .putBoolean("hide_nav_labels", hideNavLabels)
                                .putBoolean("dynamic_nav_style", dynamicNavStyle)
                        }
                        onComplete()
                    }
                )
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { page ->
                val pageOffsetProvider = {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                }
    
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> WelcomePage(pageOffsetProvider)
                        1 -> EcosystemPage(pageOffsetProvider)
                        2 -> FeatureListPage(pageOffsetProvider)
                        3 -> SignInPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn,
                            userName = userName,
                            userProfileUrl = userProfileUrl,
                            onLoginSuccess = { isLoggedIn = true },
                            onGuestClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(4) 
                                }
                            }
                        )
                        4 -> PermissionsPage(
                            pageOffsetProvider = pageOffsetProvider,
                            audioGranted = audioGranted,
                            notifGranted = notifGranted,
                            micGranted = micGranted,
                            onRequestAudio = { permissionLauncher.launch(arrayOf(audioPermission)) },
                            onRequestNotif = { notifPermission?.let { permissionLauncher.launch(arrayOf(it)) } },
                            onRequestMic = { permissionLauncher.launch(arrayOf(micPermission)) }
                        )
                        5 -> ThemeSelectionPage(
                            pageOffsetProvider = pageOffsetProvider,
                            themeMode = themeMode,
                            onThemeChanged = { 
                                themeMode = it
                                prefs.edit { putString("theme_mode", it) }
                            }
                        )
                        6 -> NavigationStylePage(
                            pageOffsetProvider = pageOffsetProvider,
                            hideNavLabels = hideNavLabels,
                            dynamicNavStyle = dynamicNavStyle,
                            onHideNavLabelsChanged = {
                                hideNavLabels = it
                                prefs.edit { putBoolean("hide_nav_labels", it) }
                            },
                            onDynamicNavStyleChanged = {
                                dynamicNavStyle = it
                                prefs.edit { putBoolean("dynamic_nav_style", it) }
                            }
                        )
                        7 -> PreferencesPage(
                            pageOffsetProvider = pageOffsetProvider,
                            hapticsLevel = hapticsLevel,
                            onHapticsLevelChanged = {
                                hapticsLevel = it
                                prefs.edit {putFloat("vibration_strength", it) }
                                if (it > 0 && hasHaptics && vibrator != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val amplitude = (it / 100f * 255).toInt().coerceIn(1, 255)
                                        vibrator.vibrate(VibrationEffect.createOneShot(50, amplitude))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(50)
                                    }
                                }
                            }
                        )
                        8 -> FinishPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn,
                            userProfileUrl = userProfileUrl
                        )
                    }
                }
            }
        }
    }
    
    @SuppressLint("ConfigurationScreenWidthHeight")
    @Composable
    fun ImmersivePageLayout(
        pageOffsetProvider: () -> Float,
        drawableRes: Int?,
        imageUrl: String? = null,
        imageScale: Float = 1f,
        iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
        content: @Composable () -> Unit
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val horizontalPadding = if (screenWidth < 360.dp) 20.dp else if (screenWidth > 480.dp) 32.dp else 28.dp
        val verticalPadding = if (screenWidth < 360.dp) 16.dp else if (screenWidth > 480.dp) 24.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Image Canvas (takes all remaining space above text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationX = offset * 15f
                        rotationZ = offset * 2f
                        scaleX = imageScale + kotlin.math.abs(offset * 0.08f)
                        scaleY = imageScale + kotlin.math.abs(offset * 0.08f)
                    }
            ) {
                if (imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = "Background decoration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(CircleShape)
                    )
                } else if (drawableRes != null) {
                    Image(
                        painter = painterResource(id = drawableRes),
                        contentDescription = "Background decoration",
                        contentScale = ContentScale.Fit,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconTint),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    // Fallback / Placeholder surface if drawable is missing
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
                }
            }
    
            // Content Area at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                content()
            }
        }
    }
    
    @Composable
    fun WelcomePage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_applogo,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Welcome\nto SpatialFlow.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Lets setup the everything for you",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 50f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun EcosystemPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_youtube_music,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Your\nComplete\nEcosystem.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stream millions of tracks or play your local library, perfectly synced.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -60f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun FeatureListPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_lyrics,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Stream",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Discover",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 40f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Sing Along.",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 60f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Karaoke lyrics, powerful search, and offline downloads all in one place.",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 80f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ThemeSelectionPage(
        pageOffsetProvider: () -> Float,
        themeMode: String,
        onThemeChanged: (String) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_appearance,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Style it\nyour way.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Triple("system", "System Default", Icons.Default.Settings),
                    Triple("dark", "Dark Mode", Icons.Default.DarkMode),
                    Triple("light", "Light Mode", Icons.Default.LightMode)
                ).forEachIndexed { index, (mode, label, _) ->
                    Card(
                        onClick = { onThemeChanged(mode) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (themeMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * (50f + (index * 40f))
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (themeMode == mode) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (themeMode == mode) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (themeMode == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun NavigationStylePage(
        pageOffsetProvider: () -> Float,
        hideNavLabels: Boolean,
        dynamicNavStyle: Boolean,
        onHideNavLabelsChanged: (Boolean) -> Unit,
        onDynamicNavStyleChanged: (Boolean) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_settings,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Navigate\nSeamlessly.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(48.dp))
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 25f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth().height(if (dynamicNavStyle) 64.dp else 80.dp),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp)
                ) {
                    listOf("Home" to Icons.Default.Home, "Search" to Icons.Default.Search, "Library" to Icons.Default.LibraryMusic).forEachIndexed { index, item ->
                        val labelComposable: (@Composable () -> Unit)? = if (hideNavLabels) null else {
                            @Composable { Text(item.first) }
                        }
                        NavigationBarItem(
                            selected = index == 0,
                            onClick = {},
                            icon = { Icon(item.second, contentDescription = null) },
                            label = labelComposable,
                            alwaysShowLabel = !hideNavLabels
                        )
                    }
                }
            }
    
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 50f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Nav Labels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remove text labels from the bottom navigation bar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = hideNavLabels, onCheckedChange = onHideNavLabelsChanged)
                }
            }
    
            Spacer(modifier = Modifier.height(16.dp))
    
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 100f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic Navbar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compact height with bold, elevated icons.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = dynamicNavStyle, onCheckedChange = onDynamicNavStyleChanged)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PreferencesPage(
        pageOffsetProvider: () -> Float,
        hapticsLevel: Float,
        onHapticsLevelChanged: (Float) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_haptic,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Sensory\nExperience.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(48.dp))
    
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 50f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Music Haptics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust the intensity of beat-synced vibrations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Slider(
                        value = hapticsLevel,
                        onValueChange = onHapticsLevelChanged,
                        valueRange = 0f..100f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionsPage(
        pageOffsetProvider: () -> Float,
        audioGranted: Boolean,
        notifGranted: Boolean,
        micGranted: Boolean,
        onRequestAudio: () -> Unit,
        onRequestNotif: () -> Unit,
        onRequestMic: () -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_folder_open,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Enable\nPermissions.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            // Audio Permission Card
            PermissionCard(
                title = "Music Library",
                description = "Access local audio files for offline playback.",
                icon = Icons.Default.LibraryMusic,
                isGranted = audioGranted,
                pageOffsetProvider = pageOffsetProvider,
                offsetMultiplier = 200f,
                onRequest = onRequestAudio
            )
    
            Spacer(modifier = Modifier.height(12.dp))
    
            // Notification Permission Card
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Playback Notifications",
                    description = "Control music from your lock screen.",
                    icon = Icons.Default.Notifications,
                    isGranted = notifGranted,
                    pageOffsetProvider = pageOffsetProvider,
                    offsetMultiplier = 300f,
                    onRequest = onRequestNotif
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
    
            // Microphone Permission Card
            PermissionCard(
                title = "Audio Engine",
                description = "Required for immersive effects and synced lyrics.",
                icon = Icons.Default.Mic,
                isGranted = micGranted,
                pageOffsetProvider = pageOffsetProvider,
                offsetMultiplier = 400f,
                onRequest = onRequestMic
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionCard(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isGranted: Boolean,
        pageOffsetProvider: () -> Float,
        offsetMultiplier: Float,
        onRequest: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 0.dp, vertical = 6.dp)
                .graphicsLayer {
                    val offset = pageOffsetProvider()
                    translationY = offset * offsetMultiplier * 0.2f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = { if (!isGranted) onRequest() }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else icon,
                        contentDescription = "Permission icon",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = if (isGranted) 1.1f else 1f
                                scaleY = if (isGranted) 1.1f else 1f
                                alpha = if (isGranted) 1f else 0.8f
                            },
                        tint = if (isGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (!isGranted) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Grant $title permission",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    @Composable
    fun SignInPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        userName: String,
        userProfileUrl: String?,
        onLoginSuccess: () -> Unit,
        onGuestClick: () -> Unit
    ) {
        var showWebView by remember { mutableStateOf(false) }

        if (showWebView) {
            val accountViewModel: com.codetrio.spatialflow.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showWebView = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.codetrio.spatialflow.ui.explore.GoogleSignInScreen(
                    accountViewModel = accountViewModel,
                    onSignInSuccess = { 
                        showWebView = false 
                        onLoginSuccess()
                    },
                    onNavigateUp = { showWebView = false },
                    isOnboarding = true
                )
            }
        }

        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = if (isLoggedIn && userProfileUrl != null) null else R.drawable.ic_youtube_music,
            imageUrl = if (isLoggedIn) userProfileUrl else null,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Welcome,\n$userName",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -20f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Login Successful", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(120.dp))
                } else {
                    Text(
                        text = "Sign In &\nSync.",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
        
                    Spacer(modifier = Modifier.height(20.dp))
        
                    Text(
                        text = "Connect your account to sync playlists, liked songs, and preferences across all your devices.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp,
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -20f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    androidx.compose.material3.Button(
                        onClick = { showWebView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -10f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_youtube_music),
                            contentDescription = "YouTube Music",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with YouTube Music",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    androidx.compose.material3.OutlinedButton(
                        onClick = onGuestClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -5f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Continue as Guest",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
    
    @Composable
    fun FinishPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        userProfileUrl: String?
    ) {
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
    
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = if (isLoggedIn && userProfileUrl != null) null else R.drawable.ic_applogo,
            imageUrl = if (isLoggedIn) userProfileUrl else null,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * -30f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Setup Complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
    
                Text(
                    text = "Ready to\nFlow.",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * -20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
    
                Spacer(modifier = Modifier.height(20.dp))
    
                Text(
                    text = "Your library is fully initialized and the engine is primed. It's time to immerse yourself in the ultimate auditory experience.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                    modifier = Modifier.graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * -10f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SetupBottomBar(
        modifier: Modifier = Modifier,
        pagerState: PagerState,
        isNextEnabled: Boolean = true,
        onNextClicked: () -> Unit,
        onBackClicked: () -> Unit,
        onFinishClicked: () -> Unit
    ) {
        val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
        val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)
    
        val targetShapeValues = when (pagerState.currentPage % 3) {
            0 -> listOf(50f, 50f, 50f, 50f)
            1 -> listOf(26f, 26f, 26f, 26f)
            else -> listOf(18f, 50f, 18f, 50f)
        }
    
        val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
        val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
        val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
        val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")
    
        val animatedRotation by animateFloatAsState(
            targetValue = pagerState.currentPage * 360f,
            animationSpec = rotationAnimationSpec,
            label = "Rotation"
        )
    
        val shape = RoundedCornerShape(
            topEnd = 38.dp,
            topStart = 38.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        )
    
        Surface(
            modifier = modifier.shadow(elevation = 8.dp, shape = shape, clip = true),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = shape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = (pagerState.currentPage + 1f) / pagerState.pageCount,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    label = "progress"
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = pagerState.currentPage,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "StepTextAnimation"
                    ) { targetPage ->
                        if (targetPage == 0) {
                            Text(
                                text = "Let's Go!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.IconButton(
                                    onClick = onBackClicked,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Step $targetPage of ${pagerState.pageCount - 1}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
    
                    val isLastPage = pagerState.currentPage == pagerState.pageCount - 1
                    val fabAlpha by animateFloatAsState(if (isNextEnabled || isLastPage) 1f else 0.4f, label = "fab_alpha")
    
                    AnimatedContent(
                        targetState = isLastPage,
                        label = "FabOrExtended",
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
                        }
                    ) { isFinish ->
                        if (isFinish) {
                            androidx.compose.material3.ExtendedFloatingActionButton(
                                onClick = onFinishClicked,
                                text = { Text("Start Listening") },
                                icon = { Icon(Icons.Default.Check, contentDescription = "Finish") },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            )
                        } else {
                            FloatingActionButton(
                                onClick = { if (isNextEnabled) onNextClicked() },
                                shape = RoundedCornerShape(
                                    topStartPercent = animatedTopStart.toInt(),
                                    topEndPercent = animatedTopEnd.toInt(),
                                    bottomStartPercent = animatedBottomStart.toInt(),
                                    bottomEndPercent = animatedBottomEnd.toInt()
                                ),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .rotate(animatedRotation)
                                    .graphicsLayer { alpha = fabAlpha }
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.rotate(-animatedRotation)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
