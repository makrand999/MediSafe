package com.example.medac.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medac.DEFAULT_SNOOZE_DELAY_MINUTES
import com.example.medac.ui.theme.AsteriskGold
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Singleton player for in-app alarm ringtone audio and repeating vibration.
 */
object InAppAlarmPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

    fun start(context: Context) {
        if (isPlaying) return
        isPlaying = true

        try {
            var alertUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context.applicationContext, alertUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(0.85f, 0.85f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("InAppAlarmPlayer", "Failed to play alarm audio: ${e.message}")
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            android.util.Log.w("InAppAlarmPlayer", "Failed to start vibration: ${e.message}")
        }
    }

    fun stop() {
        isPlaying = false
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}
    }
}

/**
 * Modern interactive swipe slider to confirm medicine intake.
 */
@Composable
fun SwipeToConfirmSlider(
    modifier: Modifier = Modifier,
    onConfirmed: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 56.dp
    val density = LocalDensity.current
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF1E293B))
            .border(BorderStroke(1.5.dp, Color(0xFF334155)), RoundedCornerShape(32.dp))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
    ) {
        // Fill progress behind the thumb as it is dragged
        if (trackWidthPx > 0f && dragOffset.value > 0f) {
            val progressFraction = (dragOffset.value / maxDragPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF15803D), Color(0xFF22C55E))
                        )
                    )
            )
        }

        // Center track guidance text
        Text(
            text = "Swipe to mark as taken  >>>",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 15.sp
            ),
            modifier = Modifier.align(Alignment.Center)
        )

        // Draggable Thumb Button
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF22C55E), Color(0xFF16A34A))
                    )
                )
                .border(BorderStroke(2.dp, Color(0xFF86EFAC)), CircleShape)
                .pointerInput(trackWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = maxDragPx * 0.72f
                            if (dragOffset.value >= threshold && maxDragPx > 0f) {
                                coroutineScope.launch {
                                    dragOffset.animateTo(maxDragPx, tween(150))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirmed()
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val targetVal = (dragOffset.value + dragAmount).coerceIn(0f, maxDragPx)
                                dragOffset.snapTo(targetVal)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val isNearEnd = maxDragPx > 0f && dragOffset.value >= maxDragPx * 0.72f
            Icon(
                imageVector = if (isNearEnd) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Swipe handle",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Full-screen simple medicine alarm UI shown when it's time for medicine.
 * Features:
 * - Clear medicine details and scheduled time
 * - Pulsing visual alarm icon
 * - A swipe to set the medicine taken
 * - A snooze button to re-fire the alarm after the default delay
 * - A button to go to the home page (skip registering the medicine taken or not)
 *
 * The Taken / Snooze / Skip triple matches the notification actions so every
 * alarm surface offers the same choices.
 */
@Composable
fun DueMedicineAlarmFullScreen(
    medicineName: String,
    time: String,
    instructions: String = "",
    onMarkTaken: () -> Unit,
    onSnooze: () -> Unit,
    onGoToHome: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF020617)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 28.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF334155).copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = AsteriskGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TIME FOR MEDICINE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AsteriskGold
                    )
                }

                // Center Medicine Information & Pulsing Icon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pulsing Alarm Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .border(
                                    BorderStroke(2.dp, Color(0xFF38BDF8).copy(alpha = pulseAlpha)),
                                    CircleShape
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF0EA5E9), Color(0xFF0284C7))
                                    )
                                )
                                .border(2.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Alarm Ringing",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = medicineName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (time.isNotBlank()) "Scheduled dose at $time" else "Time to take your medication",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )

                    if (instructions.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = instructions,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                // Bottom Action Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. A swipe to set the medicine taken
                    SwipeToConfirmSlider(
                        onConfirmed = onMarkTaken
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Snooze — same default delay as the notification action
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                            contentColor = Color(0xFFE2E8F0)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Snooze,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Snooze ${DEFAULT_SNOOZE_DELAY_MINUTES} min",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. A button to go to home page (skip registering the medicine taken or not)
                    OutlinedButton(
                        onClick = onGoToHome,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                            contentColor = Color(0xFFE2E8F0)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Go to Home (Skip)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Swipe to log dose, snooze, or tap Home to skip registering",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
