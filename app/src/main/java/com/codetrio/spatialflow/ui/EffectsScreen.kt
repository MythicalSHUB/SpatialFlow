package com.codetrio.spatialflow.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.ui.components.BalanceChannelMeter
import com.codetrio.spatialflow.ui.components.LoudnessRingIndicator
import com.codetrio.spatialflow.ui.components.ReverbRoomVisualizer
import com.codetrio.spatialflow.ui.components.Spatial8DVisualizer
import com.codetrio.spatialflow.ui.components.SpeedDialVisualizer
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EffectsScreenEntryPoint(
    viewmodel: PlayerSharedViewModel = viewModel()
) {
    // Observe processing states
    val isProcessing by viewmodel.isProcessingFlow.collectAsStateWithLifecycle()
    val processingProgress by viewmodel.processingProgressFlow.collectAsStateWithLifecycle()

    // Observe enables
    val is8DEnabled by viewmodel.is8DEnabledFlow.collectAsStateWithLifecycle()
    val isReverbEnabled by viewmodel.isReverbEnabledFlow.collectAsStateWithLifecycle()
    val isEqualizerEnabled by viewmodel.isEqualizerEnabledFlow.collectAsStateWithLifecycle()
    val isLoudnessEnabled by viewmodel.isLoudnessEnabledFlow.collectAsStateWithLifecycle()

    // Values
    val reverbPreset by viewmodel.reverbPresetFlow.collectAsStateWithLifecycle()
    val reverbIntensity by viewmodel.reverbLevelFlow.collectAsStateWithLifecycle()
    val loudnessGain by viewmodel.loudnessGainFlow.collectAsStateWithLifecycle()
    val balancePosition by viewmodel.balanceFlow.collectAsStateWithLifecycle()
    val playbackSpeed by viewmodel.playbackSpeedFlow.collectAsStateWithLifecycle()
    val isPitchMatched by viewmodel.isPitchMatchedFlow.collectAsStateWithLifecycle()

    val eq1 by viewmodel.eqBand1Flow.collectAsStateWithLifecycle()
    val eq2 by viewmodel.eqBand2Flow.collectAsStateWithLifecycle()
    val eq3 by viewmodel.eqBand3Flow.collectAsStateWithLifecycle()
    val eq4 by viewmodel.eqBand4Flow.collectAsStateWithLifecycle()
    val eq5 by viewmodel.eqBand5Flow.collectAsStateWithLifecycle()

    // Independent switch states
    var isBalanceSwitchOn by rememberSaveable { mutableStateOf(false) }
    var isSpeedSwitchOn by rememberSaveable { mutableStateOf(playbackSpeed != 1.0f) }

    EffectsScreen(
        isProcessing = isProcessing,
        processingProgress = processingProgress,
        is8DEnabled = is8DEnabled,
        on8DToggle = { enabled ->
            viewmodel.set8DEnabled(enabled)
            viewmodel.triggerEffectsRefresh()
        },
        isReverbEnabled = isReverbEnabled,
        onReverbToggle = { viewmodel.setReverbEnabled(it) },
        reverbPreset = reverbPreset.toFloat(),
        onReverbPresetChange = { viewmodel.setReverbPreset(it.toInt()) },
        reverbIntensity = reverbIntensity,
        onReverbIntensityChange = { intensity ->
            val normalizedIntensity = intensity.coerceIn(0f, 1f)
            viewmodel.setReverbLevel(normalizedIntensity)
            viewmodel.audioService?.setReverbIntensity(normalizedIntensity)
        },
        isEqualizerEnabled = isEqualizerEnabled,
        onEqualizerToggle = { viewmodel.setEqualizerEnabled(it) },
        eqBands = listOf(eq1.toFloat(), eq2.toFloat(), eq3.toFloat(), eq4.toFloat(), eq5.toFloat()),
        onEqBandChange = { index, gain ->
            val intGain = gain.toInt()
            when (index) {
                0 -> viewmodel.setEqBand1(intGain)
                1 -> viewmodel.setEqBand2(intGain)
                2 -> viewmodel.setEqBand3(intGain)
                3 -> viewmodel.setEqBand4(intGain)
                4 -> viewmodel.setEqBand5(intGain)
            }
            if (isEqualizerEnabled) viewmodel.audioService?.setEqBandGain(index, intGain)
        },
        isLoudnessEnabled = isLoudnessEnabled,
        onLoudnessToggle = { viewmodel.setLoudnessEnabled(it) },
        loudnessGain = loudnessGain.toFloat(),
        onLoudnessGainChange = { gain ->
            val intGain = gain.toInt()
            viewmodel.setLoudnessGain(intGain)
            if (isLoudnessEnabled) viewmodel.audioService?.setLoudnessGain(intGain)
        },
        isBalanceEnabled = isBalanceSwitchOn,
        onBalanceToggle = { 
            isBalanceSwitchOn = it
            if (it) {
                val currentPos: Float = balancePosition
                viewmodel.audioService?.setBalance(currentPos)
            } else {
                viewmodel.setBalance(0F)
                viewmodel.audioService?.setBalance(0F)
            }
        },
        balancePosition = balancePosition,
        onBalancePositionChange = { pos ->
            viewmodel.setBalance(pos)
            if (isBalanceSwitchOn) viewmodel.audioService?.setBalance(pos)
        },
        isSpeedEnabled = isSpeedSwitchOn,
        onSpeedToggle = { 
            isSpeedSwitchOn = it
            if (it) {
                viewmodel.audioService?.setPlaybackSpeed(playbackSpeed, isPitchMatched)
            } else {
                viewmodel.setPlaybackSpeed(1.0f)
                viewmodel.audioService?.setPlaybackSpeed(1.0f, isPitchMatched)
            }
        },
        playbackSpeed = playbackSpeed,
        onPlaybackSpeedChange = { speed ->
            viewmodel.setPlaybackSpeed(speed)
            if (isSpeedSwitchOn) viewmodel.audioService?.setPlaybackSpeed(speed, isPitchMatched)
        },
        isPitchMatched = isPitchMatched,
        onPitchMatchToggle = { 
            val newState = !isPitchMatched
            viewmodel.setPitchMatched(newState)
            if (isSpeedSwitchOn) {
                viewmodel.audioService?.setPlaybackSpeed(playbackSpeed, newState)
            }
        }

    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun EffectsScreen(
    isProcessing: Boolean,
    processingProgress: Int,
    is8DEnabled: Boolean,
    on8DToggle: (Boolean) -> Unit,
    isReverbEnabled: Boolean,
    onReverbToggle: (Boolean) -> Unit,
    reverbPreset: Float,
    onReverbPresetChange: (Float) -> Unit,
    reverbIntensity: Float,
    onReverbIntensityChange: (Float) -> Unit,
    isEqualizerEnabled: Boolean,
    onEqualizerToggle: (Boolean) -> Unit,
    eqBands: List<Float>,
    onEqBandChange: (Int, Float) -> Unit,
    isLoudnessEnabled: Boolean,
    onLoudnessToggle: (Boolean) -> Unit,
    loudnessGain: Float,
    onLoudnessGainChange: (Float) -> Unit,
    isBalanceEnabled: Boolean,
    onBalanceToggle: (Boolean) -> Unit,
    balancePosition: Float,
    onBalancePositionChange: (Float) -> Unit,
    isSpeedEnabled: Boolean,
    onSpeedToggle: (Boolean) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    isPitchMatched: Boolean,
    onPitchMatchToggle: () -> Unit
) {
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    val isInteractionEnabled = !isProcessing

    // Scroll-aware Bottom Nav hiding logic
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Same logic as PlayerFragment: dy > 10 hides, dy < -10 shows (Portrait only)
                if (!isLandscape && mainActivity != null) {
                    val delta = consumed.y
                    if (delta < -10f) {
                        mainActivity.hideBottomNavWithAnimation()
                    } else if (delta > 10f) {
                        mainActivity.showBottomNavWithAnimation()
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            // Offset for Navigation Rail in landscape (80dp + margin)
            .then(if (isLandscape) Modifier.padding(start = 88.dp) else Modifier)
            .nestedScroll(nestedScrollConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 120.dp)
    ) {
        // Header
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            Text(
                text = "Audio Effects",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Configure studio-grade soundstages, equalizers, and performance enhancers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Local visibility state to ensure 1.2s delay at 100% completion
        var showProcessingCard by remember { mutableStateOf(false) }
        LaunchedEffect(isProcessing, processingProgress) {
            if (isProcessing) {
                showProcessingCard = true
            } else if (processingProgress >= 100) {
                delay(1200.milliseconds) // Wait 1.2s before hiding
                showProcessingCard = false
            } else {
                showProcessingCard = false
            }
        }

        // Processing Card
        AnimatedVisibility(visible = showProcessingCard, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            ProcessingCard(processingProgress)
        }

        SegmentedFeatureCard(
            enabled = isInteractionEnabled,
            items = listOf(
                { Audio8DSection(is8DEnabled, on8DToggle, isInteractionEnabled) },
                { ReverbSection(isReverbEnabled, onReverbToggle, reverbPreset, onReverbPresetChange, reverbIntensity, onReverbIntensityChange, isInteractionEnabled, if (isSpeedEnabled) playbackSpeed else 1.0f) },
                { EqualizerSection(isEqualizerEnabled, onEqualizerToggle, eqBands, onEqBandChange, isInteractionEnabled) },
                { LoudnessSection(isLoudnessEnabled, onLoudnessToggle, loudnessGain, onLoudnessGainChange, isInteractionEnabled) },
                { BalanceSection(isBalanceEnabled, onBalanceToggle, balancePosition, onBalancePositionChange, isInteractionEnabled) },
                { SpeedSection(isSpeedEnabled, onSpeedToggle, playbackSpeed, onPlaybackSpeedChange, isInteractionEnabled, isPitchMatched, onPitchMatchToggle) }
            )
        )
    }
}

// --- SUB-COMPOSABLES ---

@Composable
fun ProcessingCard(progress: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val pulseAlpha by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "pulse")

    // Smoothly animate the progress to avoid "jumping"
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "smooth_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Processing 8D Audio: $progress%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(pulseAlpha))
            Spacer(modifier = Modifier.height(16.dp)) // More space for taller wave
            
            // Custom thick stroke for a bolder "Expressive" feel
            val density = LocalDensity.current
            val thickStroke = remember(density) {
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = with(density) { 6.dp.toPx() },
                    cap = StrokeCap.Round
                )
            }
            
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer, // High contrast color
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f), // Visible but subtle track
                stroke = thickStroke,
                trackStroke = thickStroke,
                wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                amplitude = { p -> WavyProgressIndicatorDefaults.indicatorAmplitude(p) * 2.5f }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedFeatureCard(
    enabled: Boolean,
    items: List<@Composable () -> Unit>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .alpha(if (enabled) 1f else 0.6f),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        items.forEachIndexed { index, item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = getEffectsSegmentedShape(index = index, count = items.size),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                item()
            }
        }
    }
}

private fun getEffectsSegmentedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 28.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

/**
 * Custom Switch with "Checked" icon (Checkmark) that is always white.
 */
@Composable
fun ExpressiveSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = Color.White // Always white in both dark/light
                )
            }
        } else null
    )
}

@Composable
fun Audio8DSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    interactionEnabled: Boolean
) {
    var showDialog by remember { mutableStateOf(false) }

    SectionContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = "8D Audio", style = MaterialTheme.typography.titleLarge)
                IconButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.padding(start = 8.dp).size(28.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.codetrio.spatialflow.R.drawable.ic_info),
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            ExpressiveSwitch(checked = enabled, onCheckedChange = onToggle, enabled = interactionEnabled)
        }
        
        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Spatial8DVisualizer(
                    enabled = enabled && interactionEnabled,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Spatial 360° rotating audio effect for an immersive sound stage experience.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Information") },
            text = { Text("8D only works on Local/Downloaded Songs") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got it")
                }
            },
            icon = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.codetrio.spatialflow.R.drawable.ic_info),
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
fun LoudnessSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    value: Float,
    onChange: (Float) -> Unit,
    interactionEnabled: Boolean
) {
    SectionContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Loudness Enhancer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ExpressiveSwitch(checked = enabled, onCheckedChange = onToggle, enabled = interactionEnabled)
        }
        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                
                LoudnessRingIndicator(
                    gain = value,
                    enabled = enabled && interactionEnabled,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Gain", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = String.format(LocalLocale.current.platformLocale, "%s%d dB", if (value > 0) "+" else "", value.toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled && interactionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ResponsiveSlider(value = value, onValueChange = onChange, valueRange = 0f..12f, enabled = enabled && interactionEnabled)
            }
        }
    }
}

data class EqPreset(val name: String, val bands: List<Float>)

val predefinedEqPresets = listOf(
    EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    EqPreset("Bass Boost", listOf(6f, 4f, 0f, -2f, -4f)),
    EqPreset("Treble Boost", listOf(-4f, -2f, 0f, 4f, 6f)),
    EqPreset("Vocal", listOf(-2f, -1f, 4f, 3f, -1f)),
    EqPreset("Acoustic", listOf(3f, 1f, 0f, 2f, 3f)),
    EqPreset("Electronic", listOf(4f, 2f, -1f, 2f, 4f))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EqualizerSection(enabled: Boolean, onToggle: (Boolean) -> Unit, bands: List<Float>, onBandChange: (Int, Float) -> Unit, interactionEnabled: Boolean) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val eqHeight = if (isLandscape) 180.dp else 240.dp
    val sliderWidth = if (isLandscape) 160.dp else 200.dp
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("eq_custom_presets", Context.MODE_PRIVATE) }
    var customPresetsStr by remember { mutableStateOf(prefs.getString("presets", "") ?: "") }
    
    val customPresets = remember(customPresetsStr) {
        if (customPresetsStr.isBlank()) emptyList()
        else {
            customPresetsStr.split(";;").mapNotNull { presetStr ->
                val parts = presetStr.split("|")
                if (parts.size == 2) {
                    val name = parts[0]
                    val bandVals = parts[1].split(",").mapNotNull { it.toFloatOrNull() }
                    if (bandVals.size == 5) EqPreset(name, bandVals) else null
                } else null
            }
        }
    }
    val allPresets = predefinedEqPresets + customPresets

    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            val newPreset = EqPreset(newPresetName.trim(), bands.toList())
                            val updatedPresets = customPresets + newPreset
                            val updatedStr = updatedPresets.joinToString(";;") { p -> 
                                "${p.name}|${p.bands.joinToString(",")}" 
                            }
                            prefs.edit { putString("presets", updatedStr) }
                            customPresetsStr = updatedStr
                        }
                        showSaveDialog = false
                        newPresetName = ""
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    SectionContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Equalizer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ExpressiveSwitch(checked = enabled, onCheckedChange = onToggle, enabled = interactionEnabled)
        }
        
        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                
                val scrollState = rememberScrollState()

                val density = LocalDensity.current
                val fadeWidthPx = with(density) { 24.dp.toPx() }

                // Presets Button Group (Scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (scrollState.maxValue > 0) {
                                val leftColor = if (scrollState.value > 0) Color.Transparent else Color.Black
                                val rightColor = if (scrollState.value < scrollState.maxValue) Color.Transparent else Color.Black
                                drawRect(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        0f to leftColor,
                                        (fadeWidthPx / size.width) to Color.Black,
                                        ((size.width - fadeWidthPx) / size.width) to Color.Black,
                                        1f to rightColor
                                    ),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .padding(bottom = 16.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
            allPresets.forEach { preset ->
                val isSelected = preset.bands == bands
                val isCustom = preset in customPresets
                
                if (isSelected) {
                    androidx.compose.material3.Button(
                        onClick = { 
                            if (enabled && interactionEnabled) {
                                preset.bands.forEachIndexed { idx, v -> onBandChange(idx, v) }
                            }
                        },
                        enabled = enabled && interactionEnabled,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(preset.name, style = MaterialTheme.typography.labelLarge)
                        if (isCustom) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete Preset",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        val updatedPresets = customPresets - preset
                                        val updatedStr = updatedPresets.joinToString(";;") { p -> 
                                            "${p.name}|${p.bands.joinToString(",")}" 
                                        }
                                        prefs.edit { putString("presets", updatedStr) }
                                        customPresetsStr = updatedStr
                                    }
                            )
                        }
                    }
                } else {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { 
                            if (enabled && interactionEnabled) {
                                preset.bands.forEachIndexed { idx, v -> onBandChange(idx, v) }
                            }
                        },
                        enabled = enabled && interactionEnabled,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(preset.name, style = MaterialTheme.typography.labelLarge)
                        if (isCustom) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete Preset",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        val updatedPresets = customPresets - preset
                                        val updatedStr = updatedPresets.joinToString(";;") { p -> 
                                            "${p.name}|${p.bands.joinToString(",")}" 
                                        }
                                        prefs.edit { putString("presets", updatedStr) }
                                        customPresetsStr = updatedStr
                                    }
                            )
                        }
                    }
                }
            }
                    
                    androidx.compose.material3.OutlinedButton(
                        onClick = { if (enabled && interactionEnabled) showSaveDialog = true },
                        enabled = enabled && interactionEnabled,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("+ Save Custom", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz").forEach { Text(text = it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }

        Row(modifier = Modifier.fillMaxWidth().height(eqHeight), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            bands.forEachIndexed { index, value ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    ResponsiveSlider(value = value, onValueChange = { onBandChange(index, it) }, valueRange = -12f..12f, enabled = enabled && interactionEnabled, modifier = Modifier.graphicsLayer { rotationZ = 270f; transformOrigin = TransformOrigin.Center }.requiredWidth(sliderWidth))
                }
            }
        }
        Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            bands.forEach { Text(text = String.format(LocalLocale.current.platformLocale, "%+d dB", it.toInt()), style = MaterialTheme.typography.labelSmall, color = if (enabled && interactionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
            }
        }
    }
}

@Composable
fun BalanceSection(enabled: Boolean, onToggle: (Boolean) -> Unit, value: Float, onChange: (Float) -> Unit, interactionEnabled: Boolean) {
    SectionContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Stereo Balance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ExpressiveSwitch(checked = enabled, onCheckedChange = onToggle, enabled = interactionEnabled)
        }
        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                // Channel Balance Meter
                BalanceChannelMeter(
                    balancePosition = value,
                    enabled = enabled && interactionEnabled
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Position", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(text = when { value.toInt() == 0 -> "Center"; value.toInt() < 0 -> "L${abs(value.toInt())}"; else -> "R${value.toInt()}" }, style = MaterialTheme.typography.labelLarge, color = if (enabled && interactionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.height(16.dp))
                ResponsiveSlider(value = value, onValueChange = onChange, valueRange = -50f..50f, enabled = enabled && interactionEnabled)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Left", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "Center",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled && interactionEnabled && value.toInt() != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .clickable(enabled = enabled && interactionEnabled && value.toInt() != 0) {
                                onChange(0f)
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Text(text = "Right", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedSection(enabled: Boolean, onToggle: (Boolean) -> Unit, value: Float, onChange: (Float) -> Unit, interactionEnabled: Boolean, isPitchMatched: Boolean, onPitchMatchToggle: () -> Unit) {
    SectionContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Playback Speed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ExpressiveSwitch(checked = enabled, onCheckedChange = onToggle, enabled = interactionEnabled)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.animateContentSize()) {
            Text(
                text = if (isPitchMatched) "Speed adjusted while keeping original pitch." else "Pitch changes relative to playback speed\n(Vinyl mode).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Speed Disc Visualizer
        SpeedDialVisualizer(
            speed = value,
            enabled = enabled && interactionEnabled,
            isPitchMatched = isPitchMatched
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Match Pitch Button - Compact and Centered
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = onPitchMatchToggle,
                enabled = enabled && interactionEnabled,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Match Pitch", style = MaterialTheme.typography.labelMedium)
                    if (isPitchMatched) {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Speed", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(text = String.format(LocalLocale.current.platformLocale, "%.2fx", value), style = MaterialTheme.typography.labelLarge, color = if (enabled && interactionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
        Spacer(modifier = Modifier.height(12.dp))
        ResponsiveSlider(value = value, onValueChange = onChange, valueRange = 0.5f..2.0f, enabled = enabled && interactionEnabled)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
             Text(text = "0.5x", style = MaterialTheme.typography.labelSmall); Text(text = "Normal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(text = "2.0x", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Optimized Responsive Slider that eliminates recomposition lag by managing local drag state,
 * while maintaining "Expressive" animations for value jumps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReverbSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    presetValue: Float,
    onPresetChange: (Float) -> Unit,
    intensityValue: Float,
    onIntensityChange: (Float) -> Unit,
    interactionEnabled: Boolean,
    playbackSpeed: Float
) {
    SectionContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Reverb", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ExpressiveSwitch(checked = enabled, onCheckedChange = onToggle, enabled = interactionEnabled)
        }
        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                val presets = listOf(
                    "None",
                    "Small Room",
                    "Concert Hall",
                    "Stadium",
                    "Plate",
                    "Spring"
                )
                val index = presetValue.toInt().coerceIn(0, 5)

                // Reverb Wave Visualization
                ReverbRoomVisualizer(
                    presetIndex = index,
                    enabled = enabled && interactionEnabled,
                    speed = playbackSpeed
                )
                
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (enabled && interactionEnabled) expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = presets[index],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Preset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        enabled = enabled && interactionEnabled
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        presets.forEachIndexed { i, presetName ->
                            DropdownMenuItem(
                                text = { Text(presetName) },
                                onClick = {
                                    onPresetChange(i.toFloat())
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Intensity", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = String.format(LocalLocale.current.platformLocale, "%d%%", (intensityValue * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled && interactionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ResponsiveSlider(
                    value = intensityValue,
                    onValueChange = { newIntensity ->
                        onIntensityChange(newIntensity)
                    },
                    valueRange = 0f..1f,
                    enabled = enabled && interactionEnabled
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Subtle", style = MaterialTheme.typography.labelSmall)
                    Text(text = "Balanced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(text = "Lush", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ResponsiveSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int = 0, enabled: Boolean, modifier: Modifier = Modifier) {
    var isDragging by remember { mutableStateOf(false) }
    var localValue by remember(value) { mutableFloatStateOf(value.coerceIn(valueRange)) }
    
    // Sync local value with external updates when not dragging
    androidx.compose.runtime.LaunchedEffect(value) {
        if (!isDragging) {
            localValue = value.coerceIn(valueRange)
        }
    }

    // Only animate when the value changes externally (not during active dragging)
    val animatedValue by animateFloatAsState(
        targetValue = localValue,
        animationSpec = if (isDragging) {
            androidx.compose.animation.core.snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "expressive_slider"
    )

    Slider(
        value = animatedValue,
        onValueChange = { 
            isDragging = true
            localValue = it
        },
        onValueChangeFinished = {
            isDragging = false
            onValueChange(localValue)
        },
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier
    )
}

@Composable
private fun SectionContainer(content: @Composable ColumnScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 16.dp else 20.dp
    val verticalPadding = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 12.dp else 20.dp
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding), 
        content = content
    )
}


@Preview(showBackground = true)
@Composable
fun EffectsScreenPreview() {
    SpatialFlowTheme(darkTheme = true) {
        EffectsScreen(
            isProcessing = true, processingProgress = 45,
            is8DEnabled = true, on8DToggle = {},
            isReverbEnabled = true, onReverbToggle = {}, reverbPreset = 2f, onReverbPresetChange = {},
            reverbIntensity = 0.8f, onReverbIntensityChange = {},
            isEqualizerEnabled = true, onEqualizerToggle = {},
            eqBands = listOf(0f, 2f, -3f, 5f, 0f), onEqBandChange = { _, _ -> },
            isLoudnessEnabled = true, onLoudnessToggle = {}, loudnessGain = 6f, onLoudnessGainChange = {},
            isBalanceEnabled = true, onBalanceToggle = {}, balancePosition = 0f, onBalancePositionChange = {},
            isSpeedEnabled = true, onSpeedToggle = {}, playbackSpeed = 1.0f, onPlaybackSpeedChange = {},
            isPitchMatched = true, onPitchMatchToggle = {}
        )
    }
}
