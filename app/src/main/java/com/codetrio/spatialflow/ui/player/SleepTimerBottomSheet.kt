package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SleepTimerBottomSheet(
    onDismissRequest: () -> Unit,
    sleepTimerEndTime: Long,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onStartTimer: (minutes: Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(1f) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Live countdown calculations
    var remainingMinutes by remember { mutableLongStateOf(0L) }
    var remainingSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sleepTimerEndTime, sleepTimerMode) {
        if (sleepTimerMode == PlayerSharedViewModel.SleepTimerMode.CUSTOM && sleepTimerEndTime > 0) {
            while (true) {
                val now = System.currentTimeMillis()
                val diffMs = sleepTimerEndTime - now
                if (diffMs <= 0) {
                    remainingMinutes = 0
                    remainingSeconds = 0
                    break
                } else {
                    val totalSecs = diffMs / 1000
                    remainingMinutes = totalSecs / 60
                    remainingSeconds = totalSecs % 60
                }
                kotlinx.coroutines.delay(1000.milliseconds)
            }
        } else {
            remainingMinutes = 0
            remainingSeconds = 0
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            AnimatedVisibility(
                visible = sleepTimerMode != PlayerSharedViewModel.SleepTimerMode.OFF,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val activeText = when (sleepTimerMode) {
                                PlayerSharedViewModel.SleepTimerMode.END_OF_SONG -> "Active: Stop at end of current song"
                                PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE -> "Active: Stop at end of queue"
                                PlayerSharedViewModel.SleepTimerMode.CUSTOM -> {
                                    val timeStr = String.format(LocalLocale.current.platformLocale, "%02d:%02d", remainingMinutes, remainingSeconds)
                                    "Countdown: $timeStr remaining"
                                }
                                else -> ""
                            }
                            Text(
                                text = activeText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Grouped Options using ListItems
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Hours Slider Section (Hides when END_OF_SONG is toggled)
                    AnimatedVisibility(
                        visible = sleepTimerMode != PlayerSharedViewModel.SleepTimerMode.END_OF_SONG,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = "Set Hours (${sliderValue.roundToInt()}h)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                supportingContent = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        Slider(
                                            value = sliderValue,
                                            onValueChange = { sliderValue = it },
                                            valueRange = 1f..5f,
                                            steps = 3,
                                            onValueChangeFinished = {
                                                onStartTimer(sliderValue.roundToInt() * 60)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            listOf(1, 2, 3, 4, 5).forEach { hour ->
                                                Text(
                                                    text = "${hour}h",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (sliderValue.roundToInt() == hour) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sliderValue.roundToInt() == hour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.width(28.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    headlineColor = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp)
                            )
                        }
                    }

                    // 2. End of Track Toggle Section
                    ListItem(
                        checked = sleepTimerMode == PlayerSharedViewModel.SleepTimerMode.END_OF_SONG,
                        onCheckedChange = { isChecked ->
                            onSetEndOfSong(isChecked)
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        },
                        content = {
                            Text(
                                text = "Stop at end of track",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = sleepTimerMode == PlayerSharedViewModel.SleepTimerMode.END_OF_SONG,
                                onCheckedChange = null
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            headlineColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Connected buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel button on left
                Button(
                    onClick = {
                        onCancelTimer()
                        sliderValue = 1f
                    },
                    shape = RoundedCornerShape(
                        topStart = 24.dp,
                        bottomStart = 24.dp,
                        topEnd = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Timer", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Custom button on right
                Button(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        bottomStart = 4.dp,
                        topEnd = 24.dp,
                        bottomEnd = 24.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Custom Timer", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = 0,
            initialMinute = 30,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val totalMinutes = state.hour * 60 + state.minute
                    if (totalMinutes > 0) {
                        onStartTimer(totalMinutes)
                    }
                    showTimePicker = false
                }) {
                    Text("Set", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Select Duration", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = state)
                }
            }
        )
    }
}
