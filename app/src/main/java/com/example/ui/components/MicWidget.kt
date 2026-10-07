package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.VoiceState
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.SurfaceElevated

/**
 * Aesthetic glowing Cyber-Pill Mic Widget.
 * Shows real-time soundwave pulsation, glowing radar aura, and status indicators.
 */
@Composable
fun MicWidget(
    voiceState: VoiceState,
    soundLevel: Float,
    isAvailable: Boolean,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceState.Listening || voiceState is VoiceState.Processing

    // Infinite breathing ring animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(110.dp)
        ) {
            // Ambient Aura when active
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale + (soundLevel * 0.4f))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = auraAlpha),
                                    NeonIndigo.copy(alpha = auraAlpha * 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Dynamic Audio Reactive Waveform Ring
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val baseRadius = size.minDimension / 2.3f
                    val reactiveRadius = baseRadius + (soundLevel * 12.dp.toPx())
                    drawCircle(
                        color = ElectricCyan.copy(alpha = 0.5f),
                        radius = reactiveRadius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                    )
                }
            }

            // Core Interactive Mic Button
            val buttonColor = when {
                !isAvailable -> SurfaceElevated
                isListening -> ElectricCyan
                else -> NeonIndigo
            }

            val iconColor = when {
                !isAvailable -> Color.Gray
                isListening -> DeepVoid
                else -> Color.White
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .shadow(
                        elevation = if (isListening) 16.dp else 6.dp,
                        shape = CircleShape,
                        spotColor = if (isListening) ElectricCyan else NeonIndigo
                    )
                    .background(
                        Brush.linearGradient(
                            colors = if (isListening) {
                                listOf(ElectricCyan, GlowGreen)
                            } else {
                                listOf(NeonIndigo, Color(0xFF4F46E5))
                            }
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = if (isListening) Color.White.copy(alpha = 0.8f) else ElectricCyan.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onMicClick)
                    .testTag("mic_widget_button")
            ) {
                Icon(
                    imageVector = when {
                        !isAvailable -> Icons.Default.MicOff
                        isListening -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = if (isListening) "Stop voice capture" else "Start voice processing",
                    tint = iconColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // State caption
        val statusText = when (voiceState) {
            is VoiceState.Idle -> "Tap to Speak Context"
            is VoiceState.Listening -> "Listening... (Local Vector Engine)"
            is VoiceState.Processing -> "Vectorizing context..."
            is VoiceState.Success -> "Context processed!"
            is VoiceState.Error -> voiceState.message
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            ),
            color = if (isListening) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
