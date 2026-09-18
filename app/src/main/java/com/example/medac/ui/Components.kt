package com.example.medac.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.medac.ui.theme.BlueInfo
import com.example.medac.ui.theme.BlueInfoText
import com.example.medac.ui.theme.BorderSubtle
import com.example.medac.ui.theme.CardSurface
import com.example.medac.ui.theme.DividerMuted
import com.example.medac.ui.theme.NavyPrimary
import com.example.medac.ui.theme.PausedMuted
import com.example.medac.ui.theme.StatusAmber
import com.example.medac.ui.theme.StatusAmberContainer
import com.example.medac.ui.theme.StatusBlue
import com.example.medac.ui.theme.StatusBlueContainer
import com.example.medac.ui.theme.StatusGray
import com.example.medac.ui.theme.StatusGrayContainer
import com.example.medac.ui.theme.StatusGreen
import com.example.medac.ui.theme.StatusGreenContainer
import com.example.medac.ui.theme.StatusRed
import com.example.medac.ui.theme.StatusRedContainer
import com.example.medac.ui.theme.TextPrimary
import com.example.medac.ui.theme.TextSecondary
import com.example.medac.ui.theme.WarningRed

enum class AlertSeverity { Info, Attention, UrgentReview, PotentialEmergency }

@Composable
fun SeverityBanner(
    severity: AlertSeverity,
    message: String,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "Done",
    onDismiss: (() -> Unit)? = null
) {
    val icon = when (severity) {
        AlertSeverity.Info -> Icons.Outlined.Info
        else -> Icons.Outlined.WarningAmber
    }
    // Deconstruct correctly
    val bg = when (severity) {
        AlertSeverity.Info -> StatusGrayContainer
        AlertSeverity.Attention -> StatusAmberContainer
        AlertSeverity.UrgentReview -> StatusRedContainer
        AlertSeverity.PotentialEmergency -> StatusRedContainer
    }
    val tint = when (severity) {
        AlertSeverity.Info -> StatusGray
        AlertSeverity.Attention -> StatusAmber
        AlertSeverity.UrgentReview -> StatusRed
        AlertSeverity.PotentialEmergency -> StatusRed
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(0.5.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 32.dp)) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("×", color = TextSecondary) }
            }
        }
    }
}

@Composable
fun UncertainFieldBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit = {},
    editable: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = StatusAmberContainer,
            border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                if (editable) {
                    androidx.compose.material3.OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusAmber,
                            unfocusedBorderColor = StatusAmber.copy(alpha = 0.5f)
                        )
                    )
                } else {
                    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚠", style = MaterialTheme.typography.labelMedium, color = StatusAmber)
            Text("Please verify", style = MaterialTheme.typography.labelMedium, color = StatusAmber)
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int, steps: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        steps.forEachIndexed { index, label ->
            val active = index == currentStep
            val done = index < currentStep
            Surface(
                shape = RoundedCornerShape(50),
                color = when {
                    active -> NavyPrimary
                    done -> StatusGreenContainer
                    else -> StatusGrayContainer
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (done) "✓ $label" else "${index + 1} · $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        active -> CardSurface
                        done -> StatusGreen
                        else -> StatusGray
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PatientSwitcher(
    patients: List<com.example.medac.PatientChip>,
    activePatientId: String?,
    onSwitch: (String) -> Unit
) {
    if (patients.size < 2) return
    val active = patients.firstOrNull { it.id == activePatientId } ?: patients.first()
    var open by remember { mutableStateOf(false) }
    Box {
        PatientSwitcherChip(name = active.name, role = active.role, onClick = { open = true })
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            patients.forEach { p ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text(p.role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    },
                    onClick = { open = false; if (p.id != active.id) onSwitch(p.id) },
                    trailingIcon = if (p.id == active.id) {
                        { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun ClarifyingQuestionRow(question: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(question, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
fun ProposalCard(
    summary: String,
    detail: String,
    factsUsed: Int? = null,
    isExpired: Boolean = false,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = BlueInfo
            ) {
                Text(
                    "Suggested by assistant",
                    style = MaterialTheme.typography.labelMedium,
                    color = BlueInfoText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (factsUsed != null) Text("Based on: $factsUsed facts", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                if (isExpired) {
                    Text("Expired — ask again", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Confirm") }
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Reject") }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineBanner(queuedCount: Int = 0) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = StatusAmberContainer,
        border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(18.dp))
            Text(
                if (queuedCount > 0) "Offline · $queuedCount updates waiting" else "Offline · updates will sync when back online",
                style = MaterialTheme.typography.labelMedium,
                color = StatusAmber
            )
        }
    }
}

@Composable
fun SyncChip(isSyncing: Boolean, queuedCount: Int, lastSync: String? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = StatusGrayContainer,
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = NavyPrimary)
                Text("Syncing…", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            } else if (queuedCount > 0) {
                Text("$queuedCount updates waiting", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            } else if (lastSync != null) {
                Text("Synced $lastSync", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            } else {
                Text("Up to date", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
fun PatientSwitcherChip(
    name: String,
    role: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = CardSurface,
        border = BorderStroke(0.5.dp, BorderSubtle),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = CircleShape, color = BlueInfo, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, color = BlueInfoText)
                }
            }
            Text(name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
            Text("▾", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

@Composable
fun StockBar(
    remaining: Int,
    threshold: Int,
    forecastDays: Int?
) {
    val progress = if (threshold <= 0) 1f else (remaining.toFloat() / (threshold * 2).coerceAtLeast(1)).coerceIn(0f, 1f)
    val barColor = when {
        remaining <= 3 || (forecastDays != null && forecastDays <= 3) -> StatusRed
        remaining <= threshold -> StatusAmber
        else -> StatusGreen
    }
    val containerColor = when (barColor) {
        StatusRed -> StatusRedContainer
        StatusAmber -> StatusAmberContainer
        else -> StatusGreenContainer
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("$remaining left", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (forecastDays != null) Text("· ~$forecastDays days", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(containerColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun SchedulePreviewRow(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BlueInfo,
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = BlueInfoText, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = BlueInfoText)
        }
    }
}

@Composable
fun AdherenceRing(
    percent: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        percent >= 80 -> StatusGreen
        percent >= 50 -> StatusAmber
        else -> StatusRed
    }
    val container = when {
        percent >= 80 -> StatusGreenContainer
        percent >= 50 -> StatusAmberContainer
        else -> StatusRedContainer
    }
    val animated by animateFloatAsState(targetValue = percent / 100f, label = "ring")
    Box(
        modifier = modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(72.dp)) {
            drawArc(
                color = container,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
            )
        }
        Text("$percent%", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TypeaheadField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<Pair<String, String>>,
    onSelect: (Pair<String, String>) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        if (suggestions.isNotEmpty() && value.isNotBlank()) {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column {
                    suggestions.take(5).forEach { (brand, generic) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(brand to generic) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(brand, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                if (generic.isNotBlank()) Text(generic, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        }
                        if (brand != suggestions.take(5).last().first) {
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(DividerMuted))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonCard() {
    Surface(shape = RoundedCornerShape(22.dp), color = PausedMuted, modifier = Modifier.fillMaxWidth().height(88.dp)) {}
}
