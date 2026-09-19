package com.example.medac.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.example.medac.statusOrActive
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.medac.DoseLogEntry
import com.example.medac.DoseScheduleItem
import com.example.medac.ManagedMedicine
import com.example.medac.MedicineDraft
import com.example.medac.adherencePercentOrNull
import com.example.medac.isDoseTaken
import com.example.medac.showDatePicker
import com.example.medac.showTimePicker
import com.example.medac.todayDateString
import com.example.medac.ui.theme.AsteriskGold
import com.example.medac.ui.theme.BorderSubtle
import com.example.medac.ui.theme.CardSurface
import com.example.medac.ui.theme.DividerMuted
import com.example.medac.ui.theme.MedRemindDarkGreen
import com.example.medac.ui.theme.MedRemindGreen
import com.example.medac.ui.theme.MedRemindLightGreen
import com.example.medac.ui.theme.MedRemindSplashGreen
import com.example.medac.ui.theme.QuickActionBlue
import com.example.medac.ui.theme.QuickActionGreen
import com.example.medac.ui.theme.QuickActionOrange
import com.example.medac.ui.theme.QuickActionPurple
import com.example.medac.ui.theme.StatusGreen
import com.example.medac.ui.theme.StatusGreenContainer
import com.example.medac.ui.theme.TakeButtonOrange
import com.example.medac.ui.theme.TakenBadgeGreen
import com.example.medac.ui.theme.TextPrimary
import com.example.medac.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// Helper to clean up ugly trailing decimal zeroes like 1.0000 or 1.0
fun formatCleanDose(raw: String): String {
    if (raw.isBlank()) return ""
    return raw.replace(Regex("""(\d+)\.0+(\b|[^0-9])""")) { matchResult ->
        val intPart = matchResult.groupValues[1]
        val trailing = matchResult.groupValues[2]
        intPart + trailing
    }.replace(Regex("""(\d+\.\d*?[1-9])0+(\b|[^0-9])""")) { matchResult ->
        val numPart = matchResult.groupValues[1]
        val trailing = matchResult.groupValues[2]
        numPart + trailing
    }.trim()
}

// Helper to format subtitle without repeating form (e.g. avoiding "1 tablet · tablet")
fun formatMedicineSubtitle(genericNameAndDose: String, form: String): String {
    val cleanDose = formatCleanDose(genericNameAndDose)
    val cleanForm = form.trim()
    return when {
        cleanDose.isBlank() && cleanForm.isBlank() -> ""
        cleanDose.isBlank() -> cleanForm
        cleanForm.isBlank() -> cleanDose
        cleanDose.contains(cleanForm, ignoreCase = true) -> cleanDose
        else -> "$cleanDose · $cleanForm"
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 1. BRAND & CUSTOM COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 8-pointed star / asterisk logo icon matching the MedRemind design in the PDF.
 */
@Composable
fun MedRemindAsteriskIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Float = 6f
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2.2f
        val center = Offset(size.width / 2, size.height / 2)
        val numPoints = 8
        for (i in 0 until numPoints) {
            val angle = Math.toRadians((i * (360.0 / numPoints)))
            val endX = center.x + (radius * cos(angle)).toFloat()
            val endY = center.y + (radius * sin(angle)).toFloat()
            drawLine(
                color = color,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Circular progress ring for Daily Progress.
 */
@Composable
fun MedRemindProgressRing(
    percent: Int?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 76,
    strokeWidthDp: Int = 8,
    ringColor: Color = MedRemindGreen,
    trackColor: Color = Color(0xFFE2E8F0)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = ((percent ?: 0).coerceIn(0, 100)) / 100f,
        label = "progress_ring"
    )

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round)
            )
            // Progress
            if (animatedProgress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = if (percent == null) "– –" else "$percent%",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
    }
}

/**
 * Daily Progress Card with ring and dose count text.
 */
@Composable
fun DailyProgressCard(
    takenDoses: Int,
    totalDoses: Int,
    modifier: Modifier = Modifier
) {
    val percent = adherencePercentOrNull(takenDoses, totalDoses)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Progress",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$takenDoses of $totalDoses doses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            MedRemindProgressRing(
                percent = percent,
                sizeDp = 72,
                strokeWidthDp = 8,
                ringColor = MedRemindGreen
            )
        }
    }
}

/**
 * Quick Action tile (2x2 grid item).
 */
@Composable
fun QuickActionTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Today's schedule item card with Asterisk star icon, details, and Take / Taken button.
 */
@Composable
fun MedRemindScheduleCard(
    item: DoseScheduleItem,
    isTaken: Boolean,
    onTakeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Photo thumbnail or Asterisk icon in tinted box
            val photoUri = item.cardImageUri
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF7ED))
                    .border(0.5.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Medicine photo for ${item.medicineName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MedRemindAsteriskIcon(
                        modifier = Modifier.size(24.dp),
                        color = AsteriskGold,
                        strokeWidth = 4.5f
                    )
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.medicineName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.genericNameAndDose.isNotBlank()) {
                    Text(
                        text = formatCleanDose(item.genericNameAndDose),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary
                )
            }

            // Button
            if (isTaken) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = StatusGreenContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = TakenBadgeGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Taken",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TakenBadgeGreen
                        )
                    }
                }
            } else {
                Button(
                    onClick = onTakeClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TakeButtonOrange,
                        contentColor = Color.White
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(
                        text = "Take",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}


/**
 * Success modal dialog (Screen 6).
 */
@Composable
fun MedRemindSuccessDialog(
    title: String = "Success",
    message: String = "Medication added successfully",
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(StatusGreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedRemindGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "OK",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. SCREEN 1: WELCOME & PIN LOGIN SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun WelcomePinScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF166534), // Rich dark forest green
                        Color(0xFF14532D)
                    )
                )
            )
    ) {
        // Top Branding Section
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Center Logo & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    MedRemindAsteriskIcon(
                        modifier = Modifier.size(54.dp),
                        color = Color.White,
                        strokeWidth = 7f
                    )
                }

                Text(
                    text = "Medisafe",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = Color.White
                )

                Text(
                    text = "Your Personal Medication Assistant",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Action Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                shape = RoundedCornerShape(24.dp),
                color = CardSurface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Welcome Back!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Text(
                        text = "Enter your PIN to access your medications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showPinDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MedRemindGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Enter PIN",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // PIN Dialog
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false; enteredPin = ""; pinError = null },
                title = {
                    Text(
                        text = "Enter Security PIN",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Enter your 4-digit PIN or tap Continue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = { if (it.length <= 6) enteredPin = it },
                            placeholder = { Text("••••") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pinError != null) {
                            Text(
                                text = pinError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPinDialog = false
                            onUnlock()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen)
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false; enteredPin = "" }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. SCREENS 2 & 7: MEDREMIND DASHBOARD (HOME SCREEN)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MedRemindDashboardScreen(
    schedule: List<DoseScheduleItem>,
    doseLogs: List<DoseLogEntry>,
    onTakeDose: (DoseScheduleItem) -> Unit,
    onNavigateToAddMedication: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToRefill: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDoses = schedule.size
    val takenDoses = schedule.count { item ->
        isDoseTaken(item, doseLogs, todayDateString())
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top App Bar / Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Medisafe",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Your medication assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .border(1.dp, BorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .border(1.dp, BorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Daily Progress Ring Card
        item {
            DailyProgressCard(
                takenDoses = takenDoses,
                totalDoses = totalDoses
            )
        }

        // Quick Actions (2x2 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionTile(
                        title = "Add Medication",
                        icon = Icons.Default.Add,
                        iconColor = QuickActionGreen,
                        bgColor = Color(0xFFDCFCE7),
                        onClick = onNavigateToAddMedication,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        title = "Calendar View",
                        icon = Icons.Default.CalendarMonth,
                        iconColor = QuickActionBlue,
                        bgColor = Color(0xFFDBEAFE),
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionTile(
                        title = "History Log",
                        icon = Icons.Default.History,
                        iconColor = QuickActionPurple,
                        bgColor = Color(0xFFF3E8FF),
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        title = "Refill Tracker",
                        icon = Icons.Default.Inventory,
                        iconColor = QuickActionOrange,
                        bgColor = Color(0xFFFFEDD5),
                        onClick = onNavigateToRefill,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Today's Schedule Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today's Schedule",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                TextButton(onClick = onNavigateToNotifications) {
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MedRemindGreen
                    )
                }
            }
        }

        // Medication List Items
        if (schedule.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No medications scheduled for today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateToAddMedication,
                            colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add your first medication")
                        }
                    }
                }
            }
        } else {
            items(schedule) { item ->
                val isTaken = isDoseTaken(item, doseLogs, todayDateString())
                MedRemindScheduleCard(
                    item = item,
                    isTaken = isTaken,
                    onTakeClick = { onTakeDose(item) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 4. SCREENS 3, 4, 5: NEW MEDICATION SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedRemindAddMedicationScreen(
    draft: MedicineDraft,
    onDraftChange: (MedicineDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onScanPhoto: () -> Unit,
    isAnalyzing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val frequencies = listOf("Once daily", "Twice daily", "Three times daily", "Four times daily", "As needed")
    val durations = listOf("7", "14", "30", "90", "∞ Ongoing")

    var name by remember(draft.name) { mutableStateOf(draft.name) }
    var dosage by remember(draft.genericNameAndDose) { mutableStateOf(draft.genericNameAndDose) }
    var selectedFrequency by remember(draft.frequency) { mutableStateOf(draft.frequency.ifBlank { "Once daily" }) }
    var selectedDuration by remember(draft.duration) { mutableStateOf(draft.duration.ifBlank { "Ongoing" }) }
    var startDate by remember(draft.startDate) {
        mutableStateOf(draft.startDate.ifBlank {
            LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        })
    }
    var times by remember(draft.times) {
        mutableStateOf(if (draft.times.isNotEmpty()) draft.times else listOf("09:00"))
    }
    var remindersEnabled by remember(draft.remindersEnabled) { mutableStateOf(draft.remindersEnabled) }
    var refillTrackingEnabled by remember(draft.refillTrackingEnabled) { mutableStateOf(draft.refillTrackingEnabled) }
    var notes by remember(draft.notes) { mutableStateOf(draft.notes) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Bar
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "New Medication",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }

        // Scrollable Form
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Optional AI / Photo Scan Banner or Attached Photo Preview
            val photoAttachedUri = draft.cardImageUri ?: draft.photoUri?.toString()
            if (!photoAttachedUri.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = photoAttachedUri,
                            contentDescription = "Captured medicine label",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Medicine Photo Attached",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MedRemindDarkGreen
                            )
                            Text(
                                text = "Saved as profile photo for visual recognition",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        TextButton(onClick = { if (!isAnalyzing) onScanPhoto() }) {
                            Text("Retake", color = MedRemindGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { if (!isAnalyzing) onScanPhoto() }),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MedRemindGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scan Prescription or Label",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MedRemindDarkGreen
                            )
                            Text(
                                text = "Auto-fill details & capture medicine photo",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // 1. Medication Name Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Medication Name",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onDraftChange(draft.copy(name = it))
                    },
                    placeholder = { Text("e.g., Drug1, Aspirin") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface,
                        focusedBorderColor = MedRemindGreen,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Dosage Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Dosage",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = {
                        dosage = it
                        onDraftChange(draft.copy(genericNameAndDose = it))
                    },
                    placeholder = { Text("e.g., 100mg, 1 tablet") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface,
                        focusedBorderColor = MedRemindGreen,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. How often? Frequency Chips
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "How often?",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    frequencies.forEach { freq ->
                        val selected = selectedFrequency.equals(freq, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedFrequency = freq
                                onDraftChange(draft.copy(frequency = freq))
                            },
                            label = {
                                Text(
                                    text = freq,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MedRemindGreen,
                                selectedLabelColor = Color.White,
                                containerColor = CardSurface,
                                labelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = BorderSubtle,
                                selectedBorderColor = MedRemindGreen
                            )
                        )
                    }
                }
            }

            // 4. For how long? Duration Chips
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "For how long?",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    durations.forEach { dur ->
                        val selected = selectedDuration.equals(dur, ignoreCase = true) ||
                                (dur == "∞ Ongoing" && selectedDuration.contains("Ongoing", ignoreCase = true))
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable {
                                    selectedDuration = dur
                                    onDraftChange(draft.copy(duration = dur))
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MedRemindGreen else CardSurface,
                            border = BorderStroke(1.dp, if (selected) MedRemindGreen else BorderSubtle)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = dur,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) Color.White else TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 5. Start Date Selector
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDatePicker(context, null) { selected ->
                            startDate = selected
                            onDraftChange(draft.copy(startDate = selected))
                        }
                    },
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Starts $startDate",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Start Date",
                        tint = MedRemindGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 6. Medication Times
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Medication Times",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    times.forEachIndexed { idx, timeStr ->
                        Surface(
                            modifier = Modifier.clickable {
                                showTimePicker(context, timeStr) { picked ->
                                    val updated = times.toMutableList()
                                    updated[idx] = picked
                                    times = updated
                                    onDraftChange(draft.copy(times = updated))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                        ) {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MedRemindDarkGreen,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // Add Time Button
                    OutlinedButton(
                        onClick = {
                            showTimePicker(context, "12:00") { picked ->
                                val updated = (times + picked).distinct().sorted()
                                times = updated
                                onDraftChange(draft.copy(times = updated))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedRemindGreen)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Time")
                    }
                }
            }

            // 7. Reminders Toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reminders",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Get notified when it's time to take your medication",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = {
                            remindersEnabled = it
                            onDraftChange(draft.copy(remindersEnabled = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MedRemindGreen
                        )
                    )
                }
            }

            // 8. Refill Tracking Toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Refill Tracking",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Get notified when you need to refill",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = refillTrackingEnabled,
                            onCheckedChange = {
                                refillTrackingEnabled = it
                                onDraftChange(draft.copy(refillTrackingEnabled = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MedRemindGreen
                            )
                        )
                    }
                }
            }

            // 9. Notes & Special Instructions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Notes & Instructions",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        onDraftChange(draft.copy(notes = it))
                    },
                    placeholder = { Text("Add notes or special instructions...") },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface,
                        focusedBorderColor = MedRemindGreen,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Bottom Action Buttons
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                name = name.trim(),
                                genericNameAndDose = dosage.trim(),
                                frequency = selectedFrequency,
                                duration = selectedDuration,
                                startDate = startDate,
                                times = times,
                                remindersEnabled = remindersEnabled,
                                refillTrackingEnabled = refillTrackingEnabled,
                                notes = notes.trim()
                            )
                        )
                        onSave()
                    },
                    enabled = name.isNotBlank() && !isAnalyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MedRemindGreen,
                        contentColor = Color.White
                    )
                ) {
                    if (isAnalyzing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Analyzing photo…",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    } else {
                    Text(
                        text = "Add Medication",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 5. SCREEN 8: NOTIFICATIONS SCREEN / FULL SCHEDULE
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MedRemindNotificationsScreen(
    schedule: List<DoseScheduleItem>,
    doseLogs: List<DoseLogEntry>,
    onBack: () -> Unit,
    onTakeClick: (DoseScheduleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDoses = schedule.size
    val takenDoses = schedule.count { item ->
        isDoseTaken(item, doseLogs, todayDateString())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Bar
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                DailyProgressCard(takenDoses = takenDoses, totalDoses = totalDoses)
            }

            item {
                Text(
                    text = "Scheduled Dose Reminders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (schedule.isEmpty()) {
                item {
                    Text(
                        text = "No active notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                items(schedule) { item ->
                    val isTaken = isDoseTaken(item, doseLogs, todayDateString())
                    MedRemindScheduleCard(
                        item = item,
                        isTaken = isTaken,
                        onTakeClick = { onTakeClick(item) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 6. SCREEN 9: CALENDAR VIEW SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MedRemindCalendarScreen(
    scheduleForDate: (LocalDate) -> List<DoseScheduleItem>,
    doseLogs: List<DoseLogEntry>,
    onTakeDose: (DoseScheduleItem, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7 // 0 = Sunday, 1 = Monday...

    val daySchedule = scheduleForDate(selectedDate)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Bar
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Month Header & Navigation
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                            }
                            Text(
                                text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Day of week headers
                        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            daysOfWeek.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextSecondary,
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar grid cells
                        val totalCells = firstDayOfWeek + daysInMonth
                        val numRows = (totalCells + 6) / 7

                        for (r in 0 until numRows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    val dayNumber = cellIndex - firstDayOfWeek + 1
                                    if (dayNumber in 1..daysInMonth) {
                                        val thisDate = currentMonth.atDay(dayNumber)
                                        val isSelected = thisDate == selectedDate
                                        val isToday = thisDate == LocalDate.now()

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected -> MedRemindGreen
                                                        isToday -> Color(0xFFDCFCE7)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable { selectedDate = thisDate },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$dayNumber",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = when {
                                                    isSelected -> Color.White
                                                    isToday -> MedRemindDarkGreen
                                                    else -> TextPrimary
                                                }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Day Schedule Header
            item {
                val formattedHeader = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                Text(
                    text = formattedHeader,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Doses for selected day
            if (daySchedule.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "No medications scheduled for this date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(daySchedule) { item ->
                    val isTaken = isDoseTaken(item, doseLogs, selectedDate.toString())
                    MedRemindScheduleCard(
                        item = item,
                        isTaken = isTaken,
                        onTakeClick = { onTakeDose(item, selectedDate.toString()) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 7. SCREEN 10: HISTORY LOG SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MedRemindHistoryLogScreen(
    doseLogs: List<DoseLogEntry>,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") } // All | Taken | Skipped | Missed
    var showConfirmClear by remember { mutableStateOf(false) }

    val filterOptions = listOf("All", "Taken", "Skipped", "Missed")

    val filteredLogs = when (selectedFilter) {
        "Taken" -> doseLogs.filter { it.status == "TAKEN" }
        "Skipped" -> doseLogs.filter { it.status == "SKIPPED" }
        "Missed" -> doseLogs.filter { it.status == "MISSED" }
        else -> doseLogs
    }

    // Group logs by date
    val groupedLogs = filteredLogs.groupBy { it.date.ifBlank { "Recent" } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Bar
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "History Log",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }

        // Filter Tabs
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle)
        ) {
            TabRow(
                selectedTabIndex = filterOptions.indexOf(selectedFilter),
                containerColor = CardSurface,
                contentColor = MedRemindGreen,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[filterOptions.indexOf(selectedFilter)]),
                        color = MedRemindGreen
                    )
                }
            ) {
                filterOptions.forEach { filter ->
                    Tab(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        text = {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selectedFilter == filter) MedRemindGreen else TextSecondary
                            )
                        }
                    )
                }
            }
        }

        // Log List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (groupedLogs.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "No history logs recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(28.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                groupedLogs.forEach { (dateStr, logs) ->
                    item {
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(logs) { log ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, BorderSubtle),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = log.medicineName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = log.time,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (log.status == "TAKEN") StatusGreenContainer else Color(0xFFFEE2E2)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (log.status == "TAKEN") {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = TakenBadgeGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = if (log.status == "TAKEN") "Taken" else "Missed",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (log.status == "TAKEN") TakenBadgeGreen else Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Clear All Button
                OutlinedButton(
                    onClick = { showConfirmClear = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All Data", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showConfirmClear) {
            AlertDialog(
                onDismissRequest = { showConfirmClear = false },
                title = { Text("Clear All History?") },
                text = { Text("Are you sure you want to clear all history records? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmClear = false
                            onClearAll()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) { Text("Clear All") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClear = false }) { Text("Cancel") }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 8. SCREEN 11: REFILL TRACKER SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MedRemindRefillTrackerScreen(
    medicines: List<ManagedMedicine>,
    onRecordRefill: (Long, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var refillMedicine by remember { mutableStateOf<ManagedMedicine?>(null) }
    var refillAmount by remember { mutableStateOf("30") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Bar
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Refill Tracker",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Medication Supplies",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            if (medicines.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "No medications currently tracked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(28.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(medicines) { med ->
                    val supply = med.currentSupply.coerceAtLeast(0)
                    val isGood = supply > 10

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = med.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    if (med.genericNameAndDose.isNotBlank()) {
                                        Text(
                                            text = formatCleanDose(med.genericNameAndDose),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isGood) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = if (isGood) "Good" else "Low",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isGood) MedRemindDarkGreen else Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Supply Count & Progress Bar
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Current Supply: $supply units",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Refill at: 20%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                val progressVal = (supply / 60f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progressVal },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (isGood) MedRemindGreen else Color(0xFFF97316),
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }

                            // Record Refill Button
                            Button(
                                onClick = { refillMedicine = med },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MedRemindGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "Record Refill",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Refill Record Dialog
        refillMedicine?.let { med ->
            AlertDialog(
                onDismissRequest = { refillMedicine = null },
                title = { Text("Record Refill for ${med.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enter the number of units/tablets added:")
                        OutlinedTextField(
                            value = refillAmount,
                            onValueChange = { refillAmount = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val units = refillAmount.toIntOrNull() ?: 30
                            onRecordRefill(med.id, units)
                            refillMedicine = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen)
                    ) { Text("Save Refill") }
                },
                dismissButton = {
                    TextButton(onClick = { refillMedicine = null }) { Text("Cancel") }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 9. FULL MEDICINE EDIT DIALOG
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedRemindEditMedicineDialog(
    medicine: ManagedMedicine,
    onSave: (ManagedMedicine) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(medicine.name) }
    var dosage by remember { mutableStateOf(formatCleanDose(medicine.genericNameAndDose)) }
    var form by remember { mutableStateOf(medicine.form.ifBlank { "Tablet" }) }
    var frequency by remember { mutableStateOf(medicine.frequency.ifBlank { "Once daily" }) }
    var duration by remember { mutableStateOf(medicine.duration.ifBlank { "Ongoing" }) }
    var startDate by remember { mutableStateOf(medicine.startDate.ifBlank { LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }) }
    var times by remember { mutableStateOf(if (medicine.times.isNotEmpty()) medicine.times else listOf("09:00")) }
    var foodTiming by remember { mutableStateOf(medicine.foodTiming) }
    var purpose by remember { mutableStateOf(medicine.purpose) }
    var remindersEnabled by remember { mutableStateOf(medicine.statusOrActive == "active") }
    var refillTrackingEnabled by remember { mutableStateOf(medicine.refillTrackingEnabled) }
    var currentSupply by remember { mutableStateOf(medicine.currentSupply.toString()) }
    var refillThreshold by remember { mutableStateOf(medicine.refillThresholdPercent.toString()) }
    var notes by remember { mutableStateOf(medicine.notes) }

    val frequencies = listOf("Once daily", "Twice daily", "Three times daily", "Four times daily", "As needed")
    val durations = listOf("7", "14", "30", "90", "∞ Ongoing")
    val forms = listOf("Tablet", "Capsule", "Liquid", "Injection", "Inhaler", "Drops", "Topical", "Other")
    val foodOptions = listOf("Before food", "With food", "After food", "No restriction")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = CardSurface,
                    border = BorderStroke(0.5.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Edit Medication",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Name
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Medication Name", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("e.g., Aspirin") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Dosage
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Dosage / Strength", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        OutlinedTextField(
                            value = dosage,
                            onValueChange = { dosage = it },
                            placeholder = { Text("e.g., 500mg, 1 tablet") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Form Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Form", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            forms.forEach { f ->
                                val selected = form.equals(f, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { form = f },
                                    label = { Text(f) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MedRemindGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Frequency
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How often?", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            frequencies.forEach { freq ->
                                val selected = frequency.equals(freq, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { frequency = freq },
                                    label = { Text(freq) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MedRemindGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Duration
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("For how long?", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            durations.forEach { dur ->
                                val selected = duration.equals(dur, ignoreCase = true) || (dur == "∞ Ongoing" && duration.contains("Ongoing", ignoreCase = true))
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clickable { duration = dur },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MedRemindGreen else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (selected) MedRemindGreen else BorderSubtle)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(dur, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), color = if (selected) Color.White else TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    // Start Date
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDatePicker(context, null) { picked -> startDate = picked }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Starts $startDate", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = TextPrimary)
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MedRemindGreen, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Medication Times
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Medication Times", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            times.forEachIndexed { idx, timeStr ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFDCFCE7),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MedRemindDarkGreen,
                                            modifier = Modifier.clickable {
                                                showTimePicker(context, timeStr) { picked ->
                                                    val updated = times.toMutableList()
                                                    updated[idx] = picked
                                                    times = updated.distinct().sorted()
                                                }
                                            }
                                        )
                                        if (times.size > 1) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Time",
                                                tint = MedRemindDarkGreen,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        times = times.filterIndexed { i, _ -> i != idx }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            // Add Time button
                            OutlinedButton(
                                onClick = {
                                    showTimePicker(context, "12:00") { picked ->
                                        times = (times + picked).distinct().sorted()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderSubtle),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Time")
                            }
                        }
                    }

                    // Food Timing
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Food Timing", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            foodOptions.forEach { opt ->
                                val selected = foodTiming.equals(opt, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { foodTiming = if (selected) "" else opt },
                                    label = { Text(opt) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MedRemindGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Purpose
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Purpose / Treatment", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        OutlinedTextField(
                            value = purpose,
                            onValueChange = { purpose = it },
                            placeholder = { Text("e.g., Blood pressure, Pain relief") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Reminders Switch (only meaningful for active/paused medicines;
                    // discontinued/archived keep their lifecycle status on save)
                    if (medicine.statusOrActive == "active" || medicine.statusOrActive == "paused") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Enable Reminders", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                            Switch(
                                checked = remindersEnabled,
                                onCheckedChange = { remindersEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MedRemindGreen)
                            )
                        }
                    }
                    }

                    // Refill Tracking Switch
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Refill Tracking", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                                Switch(
                                    checked = refillTrackingEnabled,
                                    onCheckedChange = { refillTrackingEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MedRemindGreen)
                                )
                            }
                            if (refillTrackingEnabled) {
                                OutlinedTextField(
                                    value = currentSupply,
                                    onValueChange = { currentSupply = it },
                                    label = { Text("Current Supply (units)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Notes
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Notes & Special Instructions", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = { Text("e.g., Take with a full glass of water") },
                            minLines = 2,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Bottom buttons
                Surface(
                    color = CardSurface,
                    border = BorderStroke(0.5.dp, BorderSubtle),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                // The reminders toggle maps to active/paused; any other
                                // lifecycle status (discontinued/archived) is preserved.
                                val toggledStatus = when (medicine.statusOrActive) {
                                    "active", "paused" -> if (remindersEnabled) "active" else "paused"
                                    else -> medicine.status
                                }
                                val updated = medicine.copy(
                                    name = name.trim(),
                                    genericNameAndDose = dosage.trim(),
                                    form = form.trim(),
                                    frequency = frequency,
                                    duration = duration,
                                    startDate = startDate,
                                    times = times,
                                    foodTiming = foodTiming.trim(),
                                    purpose = purpose.trim(),
                                    status = toggledStatus,
                                    refillTrackingEnabled = refillTrackingEnabled,
                                    currentSupply = currentSupply.toIntOrNull() ?: medicine.currentSupply,
                                    refillThresholdPercent = refillThreshold.toIntOrNull() ?: medicine.refillThresholdPercent,
                                    notes = notes.trim()
                                )
                                onSave(updated)
                            },
                            enabled = name.isNotBlank(),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen, contentColor = Color.White)
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// UPDATE MEDICINE PHOTO DIALOG
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun UpdateMedicinePhotoDialog(
    medicine: ManagedMedicine,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
    onRemovePhoto: (() -> Unit)? = null
) {
    val photoUri = medicine.cardImageUri ?: medicine.photoUris.firstOrNull()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderSubtle),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!photoUri.isNullOrBlank()) "Medicine Photo" else "Add Medicine Photo",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = medicine.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // If photo is present, show a large preview so user can view the full photo
                if (!photoUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Full photo of ${medicine.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                HorizontalDivider(color = DividerMuted)

                // Option 1: Take Photo with Camera
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onTakePhoto),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MedRemindGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MedRemindGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!photoUri.isNullOrBlank()) "Take New Photo" else "Take Photo with Camera",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MedRemindDarkGreen
                            )
                            Text(
                                text = "Snap a photo of the box or blister pack",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Option 2: Choose from Gallery
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseGallery),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!photoUri.isNullOrBlank()) "Choose from Gallery" else "Upload from Gallery",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Select an existing photo from device",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Option 3: Remove Photo (if present)
                if (onRemovePhoto != null && !photoUri.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onRemovePhoto,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Photo", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 10. IMPROVED MEDICINE PROFILE / DETAIL SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MedRemindMedicineDetailScreen(
    medicine: ManagedMedicine,
    logs: List<DoseLogEntry>,
    onBack: () -> Unit,
    onEditSave: (ManagedMedicine) -> Unit,
    onTakeNow: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onArchive: () -> Unit,
    onRecordRefill: (Int) -> Unit = {},
    onUploadMedicinePhoto: (ManagedMedicine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showRefillDialog by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var refillAmount by remember { mutableStateOf("30") }

    val isActive = medicine.statusOrActive == "active"
    val isPaused = medicine.statusOrActive == "paused"
    val isEnded = medicine.statusOrActive == "discontinued"

    val takenCount = logs.count { it.status == "TAKEN" }
    val totalCount = logs.size
    val adherence = adherencePercentOrNull(takenCount, totalCount)

    val isGoodSupply = medicine.currentSupply > 10

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Surface(
            color = CardSurface,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "Medicine Profile",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Medicine", tint = MedRemindGreen)
                }
            }
        }

        // Scrollable Profile Content
        val medPhotoUri = medicine.cardImageUri ?: medicine.photoUris.firstOrNull()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Card
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFFFF7ED))
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                        .clickable { onUploadMedicinePhoto(medicine) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!medPhotoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = medPhotoUri,
                                            contentDescription = "Profile photo of ${medicine.name} - tap to change",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        MedRemindAsteriskIcon(
                                            modifier = Modifier.size(30.dp),
                                            color = AsteriskGold,
                                            strokeWidth = 5f
                                        )
                                    }
                                    // Camera indicator badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                            .background(MedRemindGreen.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Change photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = medicine.name,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    val subText = formatMedicineSubtitle(medicine.genericNameAndDose, medicine.form)
                                    if (subText.isNotBlank()) {
                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            // Status Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = when {
                                    isActive -> Color(0xFFDCFCE7)
                                    isPaused -> Color(0xFFFEF3C7)
                                    else -> Color(0xFFFEE2E2)
                                }
                            ) {
                                Text(
                                    text = when {
                                        isActive -> "Active"
                                        isPaused -> "Paused"
                                        else -> "Ended"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        isActive -> MedRemindDarkGreen
                                        isPaused -> Color(0xFFD97706)
                                        else -> Color(0xFFDC2626)
                                    },
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (medicine.purpose.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Purpose: ${medicine.purpose}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        // Quick Action Buttons (2x2 layout with spacious buttons)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Primary Row: Take Now & Edit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onTakeNow,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TakeButtonOrange, contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Take Now", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.5.dp, MedRemindGreen),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MedRemindGreen)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                }
                            }

                            // Secondary Row: Pause/Resume & Archive
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isActive) {
                                    OutlinedButton(
                                        onClick = onPause,
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pause", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                    }
                                } else {
                                    Button(
                                        onClick = onResume,
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen, contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showArchiveConfirm = true },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Archive", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Schedule & Times Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Schedule & Dosage Times",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )

                        // Key metrics row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Frequency", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(medicine.frequency.ifBlank { "Daily" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Duration", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(medicine.duration.ifBlank { "Ongoing" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                }
                            }

                            if (medicine.foodTiming.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Timing", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text(medicine.foodTiming, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary, maxLines = 1)
                                    }
                                }
                            }
                        }

                        // Scheduled Times chips
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Scheduled Dose Times", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary)
                            if (medicine.times.isEmpty()) {
                                Text("No fixed times (As needed)", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    medicine.times.forEach { t ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFDCFCE7),
                                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MedRemindDarkGreen, modifier = Modifier.size(16.dp))
                                                Text(t, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MedRemindDarkGreen)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (medicine.startDate.isNotBlank()) {
                            Text("Started on ${medicine.startDate}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // 3. Adherence & Adherence Ring
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Adherence Rate", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$takenCount of $totalCount doses taken", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        MedRemindProgressRing(percent = adherence, sizeDp = 68, strokeWidthDp = 7)
                    }
                }
            }

            // 4. Supply & Refill Tracker Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Supply & Refill", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isGoodSupply) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = if (isGoodSupply) "Good" else "Low Stock",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isGoodSupply) MedRemindDarkGreen else Color(0xFFD97706),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Supply: ${medicine.currentSupply} units", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                            Text("Threshold: ${medicine.refillThresholdPercent}%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }

                        val supplyProgress = (medicine.currentSupply / 60f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { supplyProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isGoodSupply) MedRemindGreen else Color(0xFFF97316),
                            trackColor = Color(0xFFE2E8F0)
                        )

                        Button(
                            onClick = { showRefillDialog = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen, contentColor = Color.White)
                        ) {
                            Text("Record Refill", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // 5. Notes & Instructions Card
            if (medicine.notes.isNotBlank() || medicine.instruction.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Notes & Instructions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            val noteContent = listOf(medicine.instruction, medicine.notes).filter { it.isNotBlank() }.joinToString("\n\n")
                            Text(noteContent, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }

            // 6. Recent History Logs
            if (logs.isNotEmpty()) {
                item {
                    Text("Recent Dose History", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                }

                items(logs.take(5)) { log ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(log.date.ifBlank { "Today" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                                Text(log.time, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (log.status == "TAKEN") StatusGreenContainer else Color(0xFFFEE2E2)
                            ) {
                                Text(
                                    text = if (log.status == "TAKEN") "Taken" else "Missed",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (log.status == "TAKEN") TakenBadgeGreen else Color(0xFFDC2626),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Edit Dialog
        if (showEditDialog) {
            MedRemindEditMedicineDialog(
                medicine = medicine,
                onSave = { updated ->
                    showEditDialog = false
                    onEditSave(updated)
                },
                onDismiss = { showEditDialog = false }
            )
        }

        // Refill Dialog
        if (showRefillDialog) {
            AlertDialog(
                onDismissRequest = { showRefillDialog = false },
                title = { Text("Record Refill for ${medicine.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enter the number of units added:")
                        OutlinedTextField(
                            value = refillAmount,
                            onValueChange = { refillAmount = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val units = refillAmount.toIntOrNull() ?: 30
                            onRecordRefill(units)
                            showRefillDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen)
                    ) { Text("Save Refill") }
                },
                dismissButton = {
                    TextButton(onClick = { showRefillDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Archive Confirmation
        if (showArchiveConfirm) {
            AlertDialog(
                onDismissRequest = { showArchiveConfirm = false },
                title = { Text("Archive ${medicine.name}?") },
                text = { Text("This will archive ${medicine.name} and stop all reminders.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showArchiveConfirm = false
                            onArchive()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) { Text("Archive") }
                },
                dismissButton = {
                    TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 11. IMPROVED MY MEDICINES SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MedRemindMedicinesScreen(
    medicines: List<ManagedMedicine>,
    onAddMedicine: () -> Unit,
    onMedicineClick: (ManagedMedicine) -> Unit,
    onUploadMedicinePhoto: (ManagedMedicine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") } // All | Active | Paused | Ended

    val filterOptions = listOf("All", "Active", "Paused", "Ended")

    val filteredList = medicines.filter { med ->
        val matchesQuery = searchQuery.isBlank() ||
                med.name.contains(searchQuery, ignoreCase = true) ||
                med.genericNameAndDose.contains(searchQuery, ignoreCase = true) ||
                med.purpose.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "Active" -> med.statusOrActive == "active"
            "Paused" -> med.statusOrActive == "paused"
            "Ended" -> med.statusOrActive == "discontinued"
            else -> true
        }

        matchesQuery && matchesFilter
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "My Medicines",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Manage your prescriptions and schedules",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Button(
                onClick = onAddMedicine,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MedRemindGreen,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search medicines, doses...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
            },
            trailingIcon = if (searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedBorderColor = MedRemindGreen,
                unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Filter Dropdown Bar
        var filterMenuExpanded by remember { mutableStateOf(false) }
        val currentFilterCount = when (selectedFilter) {
            "Active" -> medicines.count { it.statusOrActive == "active" }
            "Paused" -> medicines.count { it.statusOrActive == "paused" }
            "Ended" -> medicines.count { it.statusOrActive == "discontinued" }
            else -> medicines.size
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Medications (${filteredList.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Box {
                Surface(
                    modifier = Modifier.clickable { filterMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, if (selectedFilter != "All") MedRemindGreen else BorderSubtle),
                    shadowElevation = 0.5.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedFilter != "All") MedRemindGreen else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$selectedFilter ($currentFilterCount)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selectedFilter != "All") MedRemindGreen else TextPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false }
                ) {
                    filterOptions.forEach { filter ->
                        val count = when (filter) {
                            "Active" -> medicines.count { it.statusOrActive == "active" }
                            "Paused" -> medicines.count { it.statusOrActive == "paused" }
                            "Ended" -> medicines.count { it.statusOrActive == "discontinued" }
                            else -> medicines.size
                        }
                        val isSelected = selectedFilter == filter
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$filter ($count)",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MedRemindGreen else TextPrimary
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MedRemindGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedFilter = filter
                                filterMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Medicines List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredList.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Medication, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No medicines found in this section" else "No results for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            if (searchQuery.isBlank()) {
                                Button(
                                    onClick = onAddMedicine,
                                    colors = ButtonDefaults.buttonColors(containerColor = MedRemindGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Add a medication")
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredList) { med ->
                    val isActive = med.statusOrActive == "active"
                    val isPaused = med.statusOrActive == "paused"

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMedicineClick(med) },
                        shape = RoundedCornerShape(18.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Icon or Medicine Photo Thumbnail (tap to upload / change photo)
                            val photoUri = med.cardImageUri ?: med.photoUris.firstOrNull()
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFFF7ED))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                                    .clickable { onUploadMedicinePhoto(med) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!photoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Photo of ${med.name} - tap to change",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    MedRemindAsteriskIcon(
                                        modifier = Modifier.size(26.dp),
                                        color = AsteriskGold,
                                        strokeWidth = 4.5f
                                    )
                                }
                                // Camera badge to make photo upload discoverable
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(18.dp)
                                        .background(MedRemindGreen.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp))
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Upload photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Info
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = med.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = when {
                                            isActive -> Color(0xFFDCFCE7)
                                            isPaused -> Color(0xFFFEF3C7)
                                            else -> Color(0xFFFEE2E2)
                                        }
                                    ) {
                                        Text(
                                            text = when {
                                                isActive -> "Active"
                                                isPaused -> "Paused"
                                                else -> "Ended"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when {
                                                isActive -> MedRemindDarkGreen
                                                isPaused -> Color(0xFFD97706)
                                                else -> Color(0xFFDC2626)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                val subText = formatMedicineSubtitle(med.genericNameAndDose, med.form)
                                if (subText.isNotBlank()) {
                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }

                                val scheduleSummary = if (med.times.isNotEmpty()) {
                                    "${med.frequency.ifBlank { "Daily" }} · ${med.times.joinToString(", ")}"
                                } else {
                                    med.frequency.ifBlank { "As needed" }
                                }
                                Text(
                                    text = scheduleSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Profile",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

