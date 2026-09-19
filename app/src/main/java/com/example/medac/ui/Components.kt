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
    percent: Int?,
    modifier: Modifier = Modifier
) {
    val color = when {
        percent == null -> TextSecondary
        percent >= 80 -> StatusGreen
        percent >= 50 -> StatusAmber
        else -> StatusRed
    }
    val container = when {
        percent == null -> BorderSubtle
        percent >= 80 -> StatusGreenContainer
        percent >= 50 -> StatusAmberContainer
        else -> StatusRedContainer
    }
    val animated by animateFloatAsState(targetValue = (percent ?: 0) / 100f, label = "ring")
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
        Text(if (percent == null) "– –" else "$percent%", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

