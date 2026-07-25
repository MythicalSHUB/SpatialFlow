package com.codetrio.spatialflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.codetrio.spatialflow.R

@Composable
fun TelegramJoinDialog(
    onDismiss: () -> Unit,
    onJoin: () -> Unit
) {
    // Beautiful dark slate-blue gradient matching Telegram's dark theme palette
    val dialogGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF172A3A), // Dark slate-blue
            Color(0xFF0E1621)  // Classic Telegram dark mode background
        )
    )

    // Border gradient for a premium glassmorphic blue highlight
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF50B5FF).copy(alpha = 0.4f),
            Color(0xFF0088CC).copy(alpha = 0.1f)
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    brush = borderGradient,
                    shape = RoundedCornerShape(24.dp)
                )
                .background(
                    brush = dialogGradient,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            // Close Button (X) at Top Right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // "Ideas" style Tag at Top Left themed with Telegram Cyan
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF153347),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "COMMUNITY",
                        color = Color(0xFF50B5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Column (Text contents)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Join our Telegram Group",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Get the latest updates, request features, report bugs, and chat with our active developer community!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFBBE1FA), // Soft blue-grey text color for better contrast
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Side: Neon Glowing Telegram Icon
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Blurred glowing backdrop circle (electric blue)
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .blur(16.dp)
                                .background(
                                    color = Color(0xFF229ED9).copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )

                        // Outer Telegram Blue circle with white plane inside
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .background(
                                    color = Color(0xFF229ED9),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_telegram_plane),
                                contentDescription = "Telegram Logo",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(36.dp)
                                    .offset(x = 1.5.dp, y = (-1).dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Later",
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onJoin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp), // Let the outer modifier handle padding for gradient clipping
                        modifier = Modifier
                            .height(40.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF2AABEE), // Telegram Cyan
                                        Color(0xFF229ED9)  // Telegram Blue
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Join Group",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
