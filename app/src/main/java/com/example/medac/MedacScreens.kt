package com.example.medac

import com.example.medac.ui.MarkdownText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.medac.ui.AdherenceRing
import com.example.medac.ui.ClarifyingQuestionRow
import com.example.medac.ui.SchedulePreviewRow
import com.example.medac.ui.StepIndicator
import com.example.medac.ui.UncertainFieldBox
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ──────────────────────────────────────────────────────────────
// AI ADD MEDICINE — 3 steps: Capture → Review → Schedule (§4.4)
// ──────────────────────────────────────────────────────────────

@Composable
fun AiAddMedicineScreen(
    draft: MedicineDraft,
    isIdentifying: Boolean,
    aiError: String?,
    onCapturePhoto: () -> Unit,
    onNameChange: (String) -> Unit = {},
    onDosageChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit = {},
    onFoodTimingChange: (String) -> Unit = {},
    onTimeChange: (List<String>) -> Unit = {},
    onInstructionChange: (String) -> Unit = {},
    onFormChange: (String) -> Unit = {},
    onPurposeChange: (String) -> Unit = {},
    // spec additions
    step: AddMedicineStep = AddMedicineStep.Capture,
    onStepChange: (AddMedicineStep) -> Unit = {},
    uncertainFields: List<String> = emptyList(),
    clarifyingQuestions: List<String> = emptyList(),
    confidence: String? = null,
    previewText: String? = null,
    onManualAdd: () -> Unit = {},
    scheduleType: String = "fixed_times",
    onScheduleTypeChange: (String) -> Unit = {},
    timingMode: String = "local_clock",
    onTimingModeChange: (String) -> Unit = {},
    viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val stepTitles = listOf("Capture", "Review", "Schedule")
    when (step) {
        AddMedicineStep.Capture -> LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { StepIndicator(currentStep = 0, steps = stepTitles) }
            item { CameraCaptureCard(photoCaptured = draft.photoUri != null, isAnalyzing = isIdentifying, onCapturePhoto = onCapturePhoto, photoUri = draft.photoUri?.toString()) }
            item { TextButton(onClick = onManualAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add manually instead") } }
            if (isIdentifying) {
                item { Surface(shape = RoundedCornerShape(18.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) { Text("Identifying medicine...", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = BlueInfoText) } }
            }
            if (!aiError.isNullOrBlank()) {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = StatusAmberContainer, border = BorderStroke(0.5.dp, StatusAmber.copy(alpha=0.25f)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(aiError, style = MaterialTheme.typography.bodyMedium, color = StatusAmber)
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }
                }
            }
            if (draft.purpose.isNotBlank() || draft.instruction.isNotBlank()) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (draft.purpose.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("What it's used for", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = BlueInfoText)
                                    Text(draft.purpose, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                }
                            }
                            if (draft.instruction.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Instructions", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = BlueInfoText)
                                    Text(draft.instruction, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
            if (draft.name.isNotBlank() || draft.photoUri != null) {
                item {
                    Button(onClick = { onStepChange(AddMedicineStep.Review) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Text("Continue → Review") }
                }
            }
        }
        AddMedicineStep.Review -> LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { StepIndicator(currentStep = 1, steps = stepTitles) }
            if (!aiError.isNullOrBlank()) {
                item { Surface(shape = RoundedCornerShape(18.dp), color = StatusAmberContainer, border = BorderStroke(0.5.dp, StatusAmber.copy(alpha=0.25f)), modifier = Modifier.fillMaxWidth()) { Text(aiError, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = TextSecondary) } }
            }
            if (confidence != null) {
                item { Text("AI confidence: $confidence", style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
            }
            // Name field — typeahead from drug-normalization/search (debounced 300ms)
            item {
                DrugTypeaheadField(
                    draft = draft,
                    onNameChange = onNameChange,
                    onDosageChange = onDosageChange,
                    uncertainFields = uncertainFields,
                    viewModel = viewModel
                )
            }
            if ("entered_name" in uncertainFields) item { ClarifyingQuestionRow(clarifyingQuestions.firstOrNull() ?: "Is this the correct name?") }
            // Form selector chips
            item {
                val forms = listOf("Tablet", "Capsule", "Liquid", "Injection", "Inhaler", "Drops", "Other")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Form", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        forms.forEach { f ->
                            val sel = draft.form.equals(f, ignoreCase = true) || (draft.form.isBlank() && f == "Tablet")
                            Surface(
                                modifier = Modifier.clickable { onFormChange(f) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) NavyPrimary else CardSurface,
                                border = BorderStroke(0.5.dp, if (sel) NavyPrimary else BorderSubtle)
                            ) {
                                Text(
                                    text = f,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal),
                                    color = if (sel) CardSurface else TextPrimary
                                )
                            }
                        }
                    }
                }
            }
            item {
                var dosageOpen by rememberSaveable { mutableStateOf(false) }
                val dosageLabel = draft.genericNameAndDose.ifBlank { "Select dosage" }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth().clickable { dosageOpen = !dosageOpen }) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Dosage", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                                Text(dosageLabel, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(if (dosageOpen) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        }
                    }
                    if (dosageOpen) {
                        if ("strength" in uncertainFields) {
                            UncertainFieldBox(label = "Strength / dose", value = draft.genericNameAndDose, onValueChange = onDosageChange)
                        } else {
                            val isTablet = draft.form.equals("tablet", ignoreCase = true) || draft.form.isBlank()
                            val isLiquid = draft.form.equals("liquid", ignoreCase = true) || draft.form.equals("syrup", ignoreCase = true) || draft.form.equals("drops", ignoreCase = true)
                            if (isTablet) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf("Half", "Full").forEach { opt ->
                                        val sel = draft.genericNameAndDose == opt
                                        val bg = if (sel) NavyPrimary else CardSurface
                                        val fg = if (sel) CardSurface else TextPrimary
                                        Surface(modifier = Modifier.weight(1f).clickable { onDosageChange(opt) }, shape = RoundedCornerShape(14.dp), color = bg, border = BorderStroke(0.5.dp, if (sel) NavyPrimary else BorderSubtle)) { Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { Text(opt, style = MaterialTheme.typography.titleMedium, color = fg) } }
                                    }
                                }
                            } else {
                                OutlinedTextField(value = draft.genericNameAndDose, onValueChange = onDosageChange, modifier = Modifier.fillMaxWidth(), label = { Text("Amount (${if (isLiquid) "ml" else "g"})") }, placeholder = { Text(if(isLiquid) "e.g. 5" else "e.g. 1") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                            }
                        }
                    }
                }
            }
            clarifyingQuestions.drop(1).forEach { q -> item { ClarifyingQuestionRow(q) } }
            // What it's used for (Purpose) & Instructions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What it's used for", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                        OutlinedTextField(
                            value = draft.purpose,
                            onValueChange = onPurposeChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Relieves pain and fever") },
                            minLines = 2,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Instructions", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                        OutlinedTextField(
                            value = draft.instruction,
                            onValueChange = onInstructionChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Take with water after meals") },
                            minLines = 2,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onStepChange(AddMedicineStep.Capture) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Text("Back") }
                    Button(onClick = { onStepChange(AddMedicineStep.Schedule) }, enabled = draft.name.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Text("Next → Schedule") }
                }
            }
        }
        AddMedicineStep.Schedule -> LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { StepIndicator(currentStep = 2, steps = stepTitles) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("When to take")
                    // Quick period chips + exact-time chips — every time is editable
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        listOf("Morning", "Afternoon", "Night").forEach { period ->
                            val sel = draft.times.any { t -> periodForTime(t) == period }
                            val bg = if (sel) NavyPrimary else CardSurface
                            val fg = if (sel) CardSurface else TextPrimary
                            Surface(modifier = Modifier.weight(1f).clickable {
                                val current = draft.times.toMutableList()
                                val periodTimes = mapOf("Morning" to "08:00", "Afternoon" to "14:00", "Night" to "20:00")
                                val time = periodTimes[period]!!
                                if (sel) current.removeAll { periodForTime(it) == period } else if (time !in current) current.add(time)
                                onTimeChange(current.distinct().sorted())
                            }, shape = RoundedCornerShape(14.dp), color = bg, border = BorderStroke(0.5.dp, if (sel) NavyPrimary else BorderSubtle)) { Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(period, style = MaterialTheme.typography.labelLarge, color = fg) } }
                        }
                    }
                    // Exact times: tap to change, × to remove, + to add
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.times.sorted().forEach { tm ->
                            Surface(shape = RoundedCornerShape(50), color = BlueInfo, border = BorderStroke(0.5.dp, BorderSubtle)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                                    Text(tm, modifier = Modifier.clickable { showTimePicker(context, tm) { new -> onTimeChange((draft.times - tm + new).distinct()) } }, style = MaterialTheme.typography.labelLarge, color = BlueInfoText)
                                    Text("×", modifier = Modifier
                                        .clickable { onTimeChange(draft.times - tm) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = BlueInfoText)
                                }
                            }
                        }
                        Surface(shape = RoundedCornerShape(50), color = CardSurface, border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.4f)), modifier = Modifier.clickable { showTimePicker(context, draft.times.lastOrNull()) { onTimeChange(draft.times + it) } }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                Text("Add exact time", style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                            }
                        }
                    }
                }
            }
            item {
                var moreOpen by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { moreOpen = !moreOpen }) { Text(if (moreOpen) "Hide options" else "More options") }
                    if (moreOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScheduleTypeRow("Fixed times", "Same times every day", scheduleType == "fixed_times", onClick = { onScheduleTypeChange("fixed_times") })
                            ScheduleTypeRow("As needed (PRN)", "No schedule — take when needed", scheduleType == "prn", onClick = { onScheduleTypeChange("prn") })
                            ScheduleTypeRow("Every 8 hours", "Elapsed interval — keeps rhythm across time zones", selected = false, enabled = false, tag = "Soon", onClick = {})
                            ScheduleTypeRow("On/off cycle", "e.g. 21 days on, 7 days off", selected = false, enabled = false, tag = "Soon", onClick = {})
                            ScheduleTypeRow("Taper", "Dose steps down over time", selected = false, enabled = false, tag = "Soon", onClick = {})
                        }
                    }
                }
            }
            if (scheduleType == "prn") {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                        Text("As-needed medicine — no daily times. Log each dose from Today when you take it.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = BlueInfoText)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Before eating", "After eating").forEach { opt ->
                        val sel = draft.foodTiming == opt
                        val bg = if (sel) NavyPrimary else CardSurface
                        val fg = if (sel) CardSurface else TextPrimary
                        Surface(modifier = Modifier.weight(1f).clickable { onFoodTimingChange(if (sel) "" else opt) }, shape = RoundedCornerShape(14.dp), color = bg, border = BorderStroke(0.5.dp, if (sel) NavyPrimary else BorderSubtle)) { Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(opt, style = MaterialTheme.typography.labelMedium, color = fg) } }
                    }
                }
            }
            if (previewText != null) {
                item { SchedulePreviewRow(previewText) }
            } else if (draft.times.isNotEmpty() && scheduleType == "fixed_times") {
                item { SchedulePreviewRow("You'll take this at ${draft.times.joinToString(" and ")} daily · next dose tomorrow ${draft.times.first()}") }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onStepChange(AddMedicineStep.Review) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Text("Back") }
                    Button(onClick = onSave, enabled = draft.name.isNotBlank() && (draft.times.isNotEmpty() || scheduleType == "prn") && !isIdentifying, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) { Text("Save medicine") }
                }
            }
        }
    }
}

@Composable
private fun ScheduleTypeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, tag: String? = null) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) BlueInfo else CardSurface,
        border = BorderStroke(0.5.dp, if (selected) NavyPrimary else BorderSubtle),
        modifier = Modifier.fillMaxWidth().let { if (enabled) it.clickable(onClick = onClick) else it }
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) TextPrimary else TextSecondary)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            if (tag != null) {
                Surface(shape = RoundedCornerShape(50), color = StatusGrayContainer) {
                    Text(tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = StatusGray)
                }
            }
        }
    }
}

private fun formatShortDateTime(iso: String): String = try {
    val inst = Instant.parse(iso)
    val z = ZoneId.systemDefault()
    val ld = inst.atZone(z).toLocalDate().toString()
    val lt = inst.atZone(z).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    "$ld $lt"
} catch (_: Exception) { iso.take(16) }

@Composable
fun SymptomTimelineScreen(
    logs: List<com.example.medac.data.SymptomLogDto>,
    onCreateSymptom: (String, String?, String?) -> Unit = { _, _, _ -> },
    onCorrectSymptom: (String, String?, String?) -> Unit = { _, _, _ -> },
    onLoadMore: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showCorrect by remember { mutableStateOf<com.example.medac.data.SymptomLogDto?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Symptoms", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        item { Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("+ Add symptom", style = MaterialTheme.typography.labelMedium) } }
        if (logs.isEmpty()) {
            item { EmptySectionCard("No symptoms yet. Tap + Add to log how you feel.") }
        } else {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        logs.forEachIndexed { idx, log ->
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(log.note?.ifBlank { "Symptom" } ?: "Symptom", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary, maxLines = 2)
                                    Text(formatShortDateTime(log.occurredAt), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                                    if (log.linkedDoseEventId != null) Text("Linked dose: ${log.linkedDoseEventId.take(8)}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                    if (log.correctedByLogId != null) Text("Corrected", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = StatusAmber)
                                }
                                TextButton(onClick = { showCorrect = log }, modifier = Modifier.heightIn(min = 32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Correct", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) }
                            }
                            if (idx < logs.lastIndex) androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                        }
                    }
                }
            }
            if (logs.size >= 50) item { TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Load more") } }
        }
    }
    if (showAdd) {
        var datePart by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
        var timePart by remember { mutableStateOf(java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
        var note by remember { mutableStateOf("") }
        var linked by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Add symptom") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showDatePicker(context, datePart) { datePart = it } }) { Text(datePart, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showTimePicker(context, timePart) { timePart = it } }) { Text(timePart, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, minLines = 2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = linked, onValueChange = { linked = it }, label = { Text("Linked dose event id (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = { Button(onClick = {
            val at = try { java.time.LocalDate.parse(datePart).atTime(java.time.LocalTime.parse(timePart)).atZone(ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { java.time.Instant.now().toString() }
            onCreateSymptom(at, note.ifBlank { null }, linked.ifBlank { null }); showAdd = false
        }, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Text("Save") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }
    showCorrect?.let { log ->
        var note by remember(log.id) { mutableStateOf(log.note ?: "") }
        var reason by remember(log.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showCorrect = null }, title = { Text("Correct symptom") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatShortDateTime(log.occurredAt), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, minLines = 2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = { Button(onClick = { onCorrectSymptom(log.id, note.ifBlank { null }, reason.ifBlank { null }); showCorrect = null }, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Text("Save") } }, dismissButton = { TextButton(onClick = { showCorrect = null }) { Text("Cancel") } })
    }
}

// ──────────────────────────────────────────────────────────────
// REMINDERS — §4.5 with Quiet hours & Discreet mode
// ──────────────────────────────────────────────────────────────

@Composable
fun RemindersScreen(
    activeReminders: List<ReminderEntry>,
    pausedReminders: List<ReminderEntry>,
    onToggleReminder: (ReminderEntry, Boolean) -> Unit,
    onEditReminder: (ReminderEntry) -> Unit,
    quietStart: String = "22:00",
    quietEnd: String = "07:00",
    onQuietChange: (String, String) -> Unit = { _, _ -> },
    discreet: Boolean = false,
    onDiscreetChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quiet hours - compact calm card, not dominant
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quiet hours", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                    Text("No notifications between these times — tap to change", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50), color = BlueInfo,
                            modifier = Modifier.clickable { showTimePicker(context, quietStart) { onQuietChange(it, quietEnd) } }
                        ) { Text(quietStart, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = BlueInfoText) }
                        Text("—", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Surface(
                            shape = RoundedCornerShape(50), color = BlueInfo,
                            modifier = Modifier.clickable { showTimePicker(context, quietEnd) { onQuietChange(quietStart, it) } }
                        ) { Text(quietEnd, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = BlueInfoText) }
                    }
                    androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Discreet", style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TextPrimary)
                            Text("Lock screen shows only “Reminder”", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                        }
                        Switch(checked = discreet, onCheckedChange = onDiscreetChange)
                    }
                }
            }
        }
        item {
            Text("Active", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.8.sp), color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
        if (activeReminders.isEmpty()) {
            item { EmptySectionCard("No active reminders — add a medicine to schedule them.") }
        } else {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        activeReminders.forEachIndexed { idx, reminder ->
                            CompactReminderRow(reminder = reminder, onEdit = { onEditReminder(reminder) }, onToggle = { onToggleReminder(reminder, false) })
                            if (idx < activeReminders.lastIndex) androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
        item {
            Text("Paused", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.8.sp), color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
        if (pausedReminders.isEmpty()) {
            item { EmptySectionCard("No paused reminders.") }
        } else {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = PausedMuted, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        pausedReminders.forEachIndexed { idx, reminder ->
                            CompactReminderRow(reminder = reminder, muted = true, onEdit = { onEditReminder(reminder) }, onToggle = { onToggleReminder(reminder, true) })
                            if (idx < pausedReminders.lastIndex) androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

// ──────────────────────────────────────────────────────────────
// PROFILE — §4.6 grouped cards
// ──────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    medicineCount: Int,
    activeReminderCount: Int,
    username: String? = null,
    onLogout: (() -> Unit)? = null,
    onNavigate: (String) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Profile", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        // Compact user header - distinct from menu rows
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(0.5.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NavyPrimary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                (username?.take(1)?.uppercase() ?: "M"),
                                style = MaterialTheme.typography.titleMedium,
                                color = CardSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            username?.takeIf { it.isNotBlank() } ?: "MedAc user",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (!username.isNullOrBlank()) username else "Not signed in",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!username.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(50), color = StatusGreenContainer) {
                            Text(
                                "Active",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                color = StatusGreen
                            )
                        }
                    }
                }
            }
        }
        // Compact stats - not same card style as menus
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactStatTile(
                    icon = Icons.Outlined.Medication,
                    value = medicineCount.toString(),
                    label = "Medicines",
                    modifier = Modifier.weight(1f)
                )
                CompactStatTile(
                    icon = Icons.Outlined.AccessTime,
                    value = activeReminderCount.toString(),
                    label = "Active reminders",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section: Care
        item {
            Text(
                "Care",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        item {
            GroupedMenuCard {
                ProfileMenuRow(
                    icon = Icons.Outlined.Group,
                    iconBg = StatusBlueContainer,
                    iconTint = StatusBlue,
                    title = "People & sharing",
                    subtitle = "Patients, members, invites",
                    onClick = { onNavigate("sharing") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.Outlined.Notifications,
                    iconBg = StatusAmberContainer,
                    iconTint = StatusAmber,
                    title = "Alert preferences",
                    subtitle = "Delays, quiet hours",
                    onClick = { onNavigate("alertPrefs") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.Outlined.CameraAlt,
                    iconBg = StatusBlueContainer,
                    iconTint = StatusBlue,
                    title = "Scan settings",
                    subtitle = "Vision or text scan default",
                    onClick = { onNavigate("scanSettings") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    iconBg = BlueInfo,
                    iconTint = NavyPrimary,
                    title = "Doctor report",
                    subtitle = "Adherence & CSV export",
                    onClick = { onNavigate("report") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    iconBg = StatusGreenContainer,
                    iconTint = StatusGreen,
                    title = "Assistant",
                    subtitle = "AI conversations",
                    onClick = { onNavigate("assistant") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.Outlined.Info,
                    iconBg = BlueInfo,
                    iconTint = NavyPrimary,
                    title = "Symptoms",
                    subtitle = "Timeline & corrections",
                    onClick = { onNavigate("symptoms") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    iconBg = StatusGrayContainer,
                    iconTint = TextSecondary,
                    title = "Exports",
                    subtitle = "CSV · download · 1h link",
                    onClick = { onNavigate("exports") }
                )
            }
        }

        // Section: Account
        item {
            Text(
                "Account",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
        item {
            GroupedMenuCard {
                ProfileMenuRow(
                    icon = Icons.Outlined.Person,
                    iconBg = BlueInfo,
                    iconTint = NavyPrimary,
                    title = "Account & security",
                    subtitle = "Password, sessions",
                    onClick = { onNavigate("account") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.Outlined.Lock,
                    iconBg = StatusGrayContainer,
                    iconTint = TextSecondary,
                    title = "Security",
                    subtitle = "MFA, active sessions",
                    onClick = { onNavigate("security") }
                )
            }
        }

        // Section: Support
        item {
            Text(
                "Support",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
        item {
            GroupedMenuCard {
                ProfileMenuRow(
                    icon = Icons.Outlined.Info,
                    iconBg = StatusGrayContainer,
                    iconTint = TextSecondary,
                    title = "About / privacy",
                    subtitle = "What's stored, request ID",
                    onClick = { onNavigate("about") }
                )
                androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                ProfileMenuRow(
                    icon = Icons.Outlined.Info,
                    iconBg = BlueInfo,
                    iconTint = NavyPrimary,
                    title = "Diagnostics",
                    subtitle = "Health live/ready · version",
                    onClick = { onNavigate("health") }
                )
            }
        }

        // Footer captions - small, not card
        item {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Accessibility · large text & high contrast are active; minimum 48dp touch targets.",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = TextSecondary
                )
                Text(
                    "Privacy · photos stay on device & OCR runs locally; AI receives image only when you tap Identify.",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = TextSecondary
                )
            }
        }
        if (onLogout != null) {
            item {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) { Text("Log out", style = MaterialTheme.typography.labelMedium) }
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun CompactStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = BlueInfo, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary)
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GroupedMenuCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = iconBg, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp), color = TextPrimary, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 13.sp), color = TextSecondary, maxLines = 1)
        }
        Text("›", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = TextSecondary)
    }
}

// ──────────────────────────────────────────────────────────────
// ASSISTANT — §16 Intelligence (real server)
// ──────────────────────────────────────────────────────────────

// legacy types kept for compat
data class AssistantMessage(val text: String, val isUser: Boolean, val factsUsed: Int? = null, val proposal: ProposalUi? = null)
data class ProposalUi(val id: String, val summary: String, val detail: String, val expired: Boolean = false)

@Composable
fun AssistantScreen(
    messages: List<IntelligenceChatItem> = emptyList(),
    proposals: Map<String, com.example.medac.data.ProposalDto> = emptyMap(),
    isLoading: Boolean = false,
    error: String? = null,
    onClearError: () -> Unit = {},
    input: String = "",
    onInputChange: (String) -> Unit = {},
    onSend: (String) -> Unit = {},
    onConfirmProposal: (String) -> Unit = {},
    onRejectProposal: (String) -> Unit = {},
    // schedule draft
    draft: com.example.medac.data.ScheduleDraftResponse? = null,
    constraintsInput: String = "",
    onConstraintsChange: (String) -> Unit = {},
    wakeTime: String = "07:00",
    sleepTime: String = "22:00",
    onWakeTimeChange: (String) -> Unit = {},
    onSleepTimeChange: (String) -> Unit = {},
    medicationId: String = "",
    onMedicationIdChange: (String) -> Unit = {},
    onCreateDraft: () -> Unit = {},
    // summary
    summary: com.example.medac.data.SummaryResponse? = null,
    summaryType: String = "adherence",
    onSummaryTypeChange: (String) -> Unit = {},
    summaryFrom: String = "",
    summaryTo: String = "",
    onSummaryFromChange: (String) -> Unit = {},
    onSummaryToChange: (String) -> Unit = {},
    onFetchSummary: () -> Unit = {},
    // instruction parse
    instructionText: String = "",
    onInstructionTextChange: (String) -> Unit = {},
    instructionParse: com.example.medac.data.InstructionParseResponse? = null,
    onParseInstruction: () -> Unit = {},
    onDeleteConversation: () -> Unit = {},
    proposalMsg: String? = null,
    onClearProposalMsg: () -> Unit = {},
    // compat overload for MainActivity mock call site
    legacyMessages: List<AssistantMessage>? = null
) {
    var tab by rememberSaveable { mutableStateOf("Chat") }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            try { listState.animateScrollToItem(messages.size + 2) } catch (_: Exception) {}
        }
    }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // Tab row 14dp compact
        Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Chat","Draft","Summary").forEach { t ->
                    val sel = tab == t
                    Surface(shape = RoundedCornerShape(10.dp), color = if (sel) NavyPrimary else CardSurface, modifier = Modifier.weight(1f).clickable { tab = t }) {
                        Text(t, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = if (sel) CardSurface else TextPrimary)
                    }
                }
            }
        }
        if (error != null) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (error.contains("429")) StatusAmberContainer else StatusRedContainer, border = BorderStroke(0.5.dp, if (error.contains("429")) StatusAmber.copy(0.3f) else StatusRed.copy(0.3f)), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = if (error.contains("429")) StatusAmber else StatusRed)
                    TextButton(onClick = onClearError, modifier = Modifier.heightIn(min = 32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("×", color = TextSecondary) }
                }
            }
        }
        if (proposalMsg != null) {
            Surface(shape = RoundedCornerShape(14.dp), color = StatusGreenContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(proposalMsg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = StatusGreen)
                    TextButton(onClick = onClearProposalMsg) { Text("×", color = TextSecondary) }
                }
            }
        }
        when (tab) {
            "Chat" -> {
                LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                            Text("Not medical advice · AI limited · conversations expire 72h · 6 turns / 12 tool calls", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = BlueInfoText)
                        }
                    }
                    if (messages.isEmpty()) {
                        item { EmptySectionCard("Ask about your medicines. Assistant cites facts_used and offers confirmable proposals.") }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            if (msg.isUser) {
                                Surface(shape = RoundedCornerShape(14.dp), color = NavyPrimary, modifier = Modifier.fillMaxWidth()) {
                                    Text(msg.text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = CardSurface)
                                }
                            } else {
                                Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MarkdownText(markdown = msg.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                        if (msg.limitations.isNotEmpty()) {
                                            Surface(shape = RoundedCornerShape(10.dp), color = StatusAmberContainer, modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text("Limitations", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 0.6.sp), color = StatusAmber)
                                                    msg.limitations.forEach { Text("• $it", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary) }
                                                }
                                            }
                                        }
                                        // meta row
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (msg.turnCount != null) Surface(shape = RoundedCornerShape(50), color = BlueInfo) { Text("turn ${msg.turnCount}/6", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = BlueInfoText) }
                                            if (msg.aiRunId != null) Text(msg.aiRunId.take(8), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                        }
                                        if (msg.factsUsed.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("Facts used (${msg.factsUsed.size})", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                                                msg.factsUsed.forEach { f ->
                                                    Surface(shape = RoundedCornerShape(10.dp), color = StatusGrayContainer, modifier = Modifier.fillMaxWidth()) {
                                                        Text("${f.type} ${f.id.take(8)}${f.version?.let { " v$it" } ?: ""}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, maxLines = 1)
                                                    }
                                                }
                                            }
                                        }
                                        // proposals with 14dp compact card, requires_user_confirmation flag via proposals map
                                        msg.proposals.forEach { ref ->
                                            val p = proposals[ref.proposalId]
                                            ProposalCompactCard(
                                                proposal = p,
                                                ref = ref,
                                                onConfirm = { onConfirmProposal(ref.proposalId) },
                                                onReject = { onRejectProposal(ref.proposalId) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        // instruction parse quick tool (16.2) — grouped card 14dp
                        Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Parse instruction", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                                OutlinedTextField(value = instructionText, onValueChange = onInstructionTextChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text("e.g. Take 1 tablet twice daily") }, shape = RoundedCornerShape(14.dp), singleLine = false, minLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = onParseInstruction, enabled = instructionText.isNotBlank() && !isLoading, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Text("Parse", style = MaterialTheme.typography.labelMedium) }
                                }
                                instructionParse?.let { ip ->
                                    val parse = ip.parse
                                    Surface(shape = RoundedCornerShape(10.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("${parse.doseValue ?: "—"} ${parse.doseUnit ?: ""} · ${parse.frequencyText ?: "—"} · route ${parse.route ?: "—"}", style = MaterialTheme.typography.labelMedium, color = BlueInfoText)
                                            if (!parse.uncertainFields.isNullOrEmpty()) Text("Uncertain: ${parse.uncertainFields.joinToString(", ")}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusAmber)
                                            parse.clarifyingQuestions?.forEach { q -> ClarifyingQuestionRow(q) }
                                            Text("confidence ${((parse.confidence ?: 0.0)*100).toInt()}%${if (ip.requiresUserConfirmation == true) " · requires confirmation" else ""}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                            parse.evidence?.take(3)?.forEach { e -> Text("${e.field}: ${e.span} (${((e.confidence ?: 0.0)*100).toInt()}%)", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, maxLines = 1) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDeleteConversation, modifier = Modifier.heightIn(min = 32.dp)) { Text("Delete conversation", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = WarningRed) }
                            if (isLoading) { Text("· loading…", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp)) }
                        }
                    }
                }
                // input pinned
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Adherence last 7d?", "What times should I take?").forEach { s ->
                            Surface(shape = RoundedCornerShape(50), color = CardSurface, border = BorderStroke(0.5.dp, NavyPrimary.copy(alpha = 0.4f)), modifier = Modifier.clickable { onInputChange(s) }) {
                                Text(s, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = NavyPrimary)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f), placeholder = { Text("Ask assistant…") }, shape = RoundedCornerShape(14.dp), singleLine = true)
                        Button(onClick = { if (input.isNotBlank()) onSend(input) }, enabled = input.isNotBlank() && !isLoading, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("Send") }
                    }
                }
            }
            "Draft" -> {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                            Text("Draft times are convenience proposals, not prescription content · requires confirmation", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = BlueInfoText)
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Schedule draft", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                                OutlinedTextField(value = constraintsInput, onValueChange = { if (it.length <= 2000) onConstraintsChange(it) }, modifier = Modifier.fillMaxWidth(), label = { Text("Constraints (1..2000)") }, placeholder = { Text("e.g. prefer mornings, avoid late night") }, shape = RoundedCornerShape(14.dp), minLines = 2)
                                Text("${constraintsInput.length}/2000", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = medicationId, onValueChange = onMedicationIdChange, modifier = Modifier.fillMaxWidth(), label = { Text("Medication ID (optional, uuid)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showTimePicker(context, wakeTime) { onWakeTimeChange(it) } }) { Text("Wake $wakeTime", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showTimePicker(context, sleepTime) { onSleepTimeChange(it) } }) { Text("Sleep $sleepTime", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                                }
                                Button(onClick = onCreateDraft, enabled = !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Text("Create draft", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    draft?.let { d ->
                        item {
                            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Draft result", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                                    val times = d.draft.times ?: d.draft.draftTimes ?: d.draft.preview?.mapNotNull { try { java.time.Instant.parse(it.scheduledAtUtc).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) } catch (_: Exception) { null } }
                                    if (!times.isNullOrEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { times.forEach { tm -> Surface(shape = RoundedCornerShape(50), color = BlueInfo) { Text(tm, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = BlueInfoText) } } }
                                        SchedulePreviewRow("Take at ${times.joinToString(" and ")} · ${d.draft.humanSummary ?: "preview"}")
                                    } else {
                                        Text(d.draft.humanSummary ?: "Draft created", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    }
                                    if (d.limitations?.isNotEmpty() == true) { d.limitations.forEach { Text("• $it", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusAmber) } }
                                    if (d.requiresUserConfirmation == true) Surface(shape = RoundedCornerShape(10.dp), color = StatusAmberContainer, modifier = Modifier.fillMaxWidth()) { Text("Requires confirmation before applying", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusAmber) }
                                    if (d.aiRunId != null) Text("ai_run ${d.aiRunId.take(8)}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                    // if draft produced proposals, they are in messages; but also show hint to check Chat
                                    Text("Confirm via proposal in Chat if suggested.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            "Summary" -> {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Summary", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("adherence","inventory","timeline").forEach { t ->
                                        val sel = summaryType == t
                                        Surface(shape = RoundedCornerShape(50), color = if (sel) NavyPrimary else CardSurface, border = BorderStroke(0.5.dp, if (sel) NavyPrimary else BorderSubtle), modifier = Modifier.clickable { onSummaryTypeChange(t) }) {
                                            Text(t, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = if (sel) CardSurface else TextPrimary)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showDatePicker(context, summaryFrom.ifBlank { java.time.LocalDate.now().toString() }) { onSummaryFromChange(it) } }) { Text(if (summaryFrom.isBlank()) "From date" else summaryFrom, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                                    Text("—", color = TextSecondary)
                                    Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showDatePicker(context, summaryTo.ifBlank { java.time.LocalDate.now().toString() }) { onSummaryToChange(it) } }) { Text(if (summaryTo.isBlank()) "To date" else summaryTo, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                                }
                                Button(onClick = onFetchSummary, enabled = !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Text("Fetch summary", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    summary?.let { s ->
                        item {
                            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Result", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                                    Text(s.summaryText.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    if (s.aiRunId != null) Text("ai_run ${s.aiRunId.take(8)}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalCompactCard(
    proposal: com.example.medac.data.ProposalDto?,
    ref: com.example.medac.data.ProposalRefDto,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val isExpired = try { proposal?.expiresAt?.let { java.time.Instant.parse(it).isBefore(java.time.Instant.now()) } ?: false } catch (_: Exception) { false }
    Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
        Column {
            Surface(shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Proposal · ${ref.actionType ?: proposal?.actionType ?: "—"}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = BlueInfoText, modifier = Modifier.weight(1f))
                    if (ref.requiresConfirmation == true || proposal?.status == "pending") Surface(shape = RoundedCornerShape(50), color = StatusAmberContainer) { Text("confirm required", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp), color = StatusAmber) }
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(proposal?.humanSummary ?: ref.proposalId.take(8), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TextPrimary, maxLines = 3)
                if (proposal?.payloadHash != null) Text("hash ${proposal.payloadHash.take(8)}…", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    proposal?.expiresAt?.let { Text("expires ${it.take(16)}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary) }
                    proposal?.requiredPermission?.let { Surface(shape = RoundedCornerShape(50), color = StatusGrayContainer) { Text(it, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp), color = TextSecondary) } }
                    proposal?.status?.let { Surface(shape = RoundedCornerShape(50), color = when(it){"executed"->StatusGreenContainer;"rejected"->StatusRedContainer; else->StatusGrayContainer}) { Text(it, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = when(it){"executed"->StatusGreen;"rejected"->StatusRed; else->TextSecondary}) } }
                }
                if (proposal?.resourceVersions?.isNotEmpty() == true) {
                    Text(proposal.resourceVersions.entries.joinToString(", ") { "${it.key.take(8)} v${it.value}" }, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, maxLines = 2)
                }
                if (isExpired) {
                    Text("Expired — ask again", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusRed)
                } else if (proposal?.status in listOf("executed","rejected","expired")) {
                    Text("Status: ${proposal?.status}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onConfirm, modifier = Modifier.weight(1f).heightIn(min = 40.dp), shape = RoundedCornerShape(14.dp)) { Text("Confirm", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)) }
                        OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f).heightIn(min = 40.dp), shape = RoundedCornerShape(14.dp)) { Text("Reject", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)) }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Other shared components (kept for compatibility)
// ──────────────────────────────────────────────────────────────

@Composable
private fun CompactReminderRow(
    reminder: ReminderEntry,
    muted: Boolean = false,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    standalone: Boolean = false
) {
    val hasGeneratedImage = !reminder.cardImageUri.isNullOrBlank()
    val primaryTextColor = when { hasGeneratedImage -> Color.White; muted -> TextSecondary; else -> TextPrimary }
    val secondaryTextColor = if (hasGeneratedImage) Color.White.copy(alpha = 0.9f) else TextSecondary
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = if (muted) StatusGrayContainer else BlueInfo, modifier = Modifier.width(58.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp).fillMaxWidth()) {
                    Text(reminder.time, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold, color = if (muted) TextSecondary else NavyPrimary, maxLines = 1)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(reminder.medicineName, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = primaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(reminder.genericNameAndDose.ifBlank { "—" }, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = secondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Edit", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = if (hasGeneratedImage) Color.White else NavyPrimary)
            }
            Switch(checked = reminder.active, onCheckedChange = { onToggle() })
        }
    }
    if (standalone) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = if (hasGeneratedImage) Color.Transparent else if (muted) PausedMuted else CardSurface, border = BorderStroke(0.5.dp, BorderSubtle)) {
            Box {
                if (hasGeneratedImage) {
                    AsyncImage(model = reminder.cardImageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(64.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.Black.copy(alpha = if (muted) 0.7f else 0.58f)))
                }
                content()
            }
        }
    } else {
        Box {
            if (hasGeneratedImage) {
                AsyncImage(model = reminder.cardImageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(52.dp))
                Box(modifier = Modifier.fillMaxWidth().height(52.dp).background(Color.Black.copy(alpha = if (muted) 0.7f else 0.58f)))
            }
            content()
        }
    }
}

@Composable
private fun CameraCaptureCard(
    photoCaptured: Boolean,
    isAnalyzing: Boolean,
    onCapturePhoto: () -> Unit,
    photoUri: String? = null
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(16.dp)).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (photoUri != null) {
                    AsyncImage(model = photoUri, contentDescription = "Captured medicine label", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Outlined.Medication, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(40.dp))
                        Text(text = "Photograph the medicine label — the text will be read automatically.", style = MaterialTheme.typography.bodyLarge, color = TextPrimary, textAlign = TextAlign.Center)
                    }
                }
            }
            Button(onClick = onCapturePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) {
                Text(when { isAnalyzing -> "Scanning..."; photoCaptured -> "Retake photo"; else -> "Scan label" })
            }
        }
    }
}

@Composable
private fun TimeChipRow(times: List<String>, onImage: Boolean = false) {
    if (times.isEmpty()) {
        Text("No reminder times", style = MaterialTheme.typography.bodyMedium, color = if (onImage) Color.White.copy(alpha = 0.85f) else TextSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        times.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { time ->
                    Surface(shape = RoundedCornerShape(14.dp), color = if (onImage) Color.White.copy(alpha = 0.18f) else BlueInfo, border = BorderStroke(0.5.dp, if (onImage) Color.White.copy(alpha = 0.32f) else BorderSubtle)) {
                        Text(text = time, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = if (onImage) Color.White else BlueInfoText)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.8.sp), color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
fun EmptySectionCard(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle)) {
        Text(text = text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp), color = TextSecondary)
    }
}

@Composable
fun InfoCard(title: String, body: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
            Text(body.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = TextPrimary)
        }
    }
}

@Composable
fun PrescriptionImportDialog(state: PrescriptionImportState, isAnalyzing: Boolean, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Prescription medicines") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.status, style = MaterialTheme.typography.bodyLarge)
            if (state.medicines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.medicines.forEach { medicine ->
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BlueInfo, border = BorderStroke(0.5.dp, BorderSubtle)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(medicine.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(medicine.genericNameAndDose, medicine.purpose).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                TimeChipRow(medicine.times)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = onSave, enabled = state.medicines.isNotEmpty() && !isAnalyzing, modifier = Modifier.heightIn(min = 56.dp)) { Text("Save all") } }, dismissButton = { OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancel") } }, properties = DialogProperties(usePlatformDefaultWidth = true))
}

@Composable
fun ConfirmActionDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { Button(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) { Text(confirmLabel) } }, dismissButton = { OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancel") } }, properties = DialogProperties(usePlatformDefaultWidth = true))
}

// ──────────────────────────────────────────────────────────────
// Profile sub-screens — minimal per §4.6 to satisfy navigation
// ──────────────────────────────────────────────────────────────

@Composable
fun DoctorReportScreen(
    onBack: () -> Unit,
    medicines: List<ManagedMedicine> = emptyList(),
    logs: List<DoseLogEntry> = emptyList(),
    viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val report by viewModel.adherenceReport.collectAsState()
    val isLoading by viewModel.isLoadingAdherence.collectAsState()
    val genResult by viewModel.generateResult.collectAsState()
    var fromDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().minusDays(30).toString()) }
    var toDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().plusDays(1).toString()) }
    // initial load
    LaunchedEffect(Unit) {
        val tz = java.time.ZoneId.systemDefault().id
        val fromIso = try { java.time.LocalDate.parse(fromDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { java.time.Instant.now().minusSeconds(30*24*3600).toString() }
        val toIso = try { java.time.LocalDate.parse(toDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { java.time.Instant.now().toString() }
        viewModel.loadAdherenceReport(fromIso, toIso, tz)
    }
    fun reload() {
        val tz = java.time.ZoneId.systemDefault().id
        val fromIso = try { java.time.LocalDate.parse(fromDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { java.time.Instant.now().minusSeconds(30*24*3600).toString() }
        val toIso = try { java.time.LocalDate.parse(toDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { java.time.Instant.now().toString() }
        viewModel.loadAdherenceReport(fromIso, toIso, tz)
    }
    val reportSnapshot = report
    val pct = reportSnapshot?.adherencePercentage?.toInt() ?: reportSnapshot?.let { adherencePercentOrNull(it.takenCount, it.eligibleScheduledCount) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Doctor report", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Range", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showDatePicker(context, fromDate) { fromDate = it } }) { Text(fromDate, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = BlueInfoText) }
                        Text("—", color = TextSecondary)
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showDatePicker(context, toDate) { toDate = it } }) { Text(toDate, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = BlueInfoText) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { reload() }, modifier = Modifier.heightIn(min = 40.dp), shape = RoundedCornerShape(12.dp)) { Text("Load", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) }
                    }
                    Text("Timezone: ${reportSnapshot?.timezone ?: java.time.ZoneId.systemDefault().id}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AdherenceRing(percent = pct, modifier = Modifier.size(56.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        if (isLoading) Text("Loading…", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextSecondary)
                        else {
                            Text(if (pct == null) "No data for range" else "$pct% adherence", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                            val r = reportSnapshot
                            if (r != null) {
                                Text("${r.takenCount} / ${r.eligibleScheduledCount} taken · ${r.skippedCount} skipped · ${r.missedCount} missed", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                                Text("pending ${r.pendingCount} · excluded ${r.excludedCount} (${listOfNotNull(r.excludedReasons?.prnExcluded?.let{"prn ${it}"}, r.excludedReasons?.cancelled?.let{"cancel ${it}"}, r.excludedReasons?.paused?.let{"paused ${it}"}).joinToString(", ")})", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                Text(r.denominatorDefinition ?: "eligible = scheduled excluding PRN/cancelled/paused", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, lineHeight = 12.sp)
                            } else {
                                val takenLocal = logs.count { it.status == "TAKEN" }; val totalLocal = logs.size; val pctLocal = adherencePercentOrNull(takenLocal, totalLocal)
                                Text(if (pctLocal == null) "No local doses logged · as-needed not counted" else "$pctLocal% (local) · $takenLocal / $totalLocal taken · as-needed not counted", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Generate alerts", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                    Text("Manual trigger — manager+ required", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    Button(onClick = { viewModel.generateAlerts() }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Generate alerts", style = MaterialTheme.typography.labelMedium) }
                    genResult?.let { g -> Text("Generated: low_stock ${g.lowStock} · expiration ${g.expiration} · stale_device ${g.staleDevice} · missed ${g.missed}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusGreen) }
                }
            }
        }
        item { Text("CSV export moved to Profile → Exports", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
fun ExportsScreen(
    viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val exports by viewModel.exports.collectAsState()
    val exportError by viewModel.exportError.collectAsState()
    var fromDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().minusDays(30).toString()) }
    var toDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    var includeSymptoms by rememberSaveable { mutableStateOf(false) }
    var downloading by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Exports", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Create export", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showDatePicker(context, fromDate) { fromDate = it } }) { Text(fromDate, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = BlueInfoText) }
                        Text("—", color = TextSecondary)
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showDatePicker(context, toDate) { toDate = it } }) { Text(toDate, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = BlueInfoText) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Include symptoms", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(checked = includeSymptoms, onCheckedChange = { includeSymptoms = it })
                    }
                    Button(onClick = {
                        val fromIso = try { java.time.LocalDate.parse(fromDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { null }
                        val toIso = try { java.time.LocalDate.parse(toDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { null }
                        viewModel.createExport(fromIso, toIso, if (includeSymptoms) true else null)
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Create export", style = MaterialTheme.typography.labelMedium) }
                    Text("Server generates CSVs inline — status becomes completed when ready. Expires in ~24h.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                }
            }
        }
        if (exportError != null) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = StatusAmberContainer, border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(exportError ?: "", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusAmber, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearExportError() }) { Text("Dismiss", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) }
                    }
                }
            }
        }
        if (exports.isEmpty()) {
            item { EmptySectionCard("No exports yet — create one above.") }
        } else {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        exports.forEachIndexed { idx, exp ->
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(exp.id.take(8), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TextPrimary)
                                    Text(exp.status, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = when(exp.status){ "completed"->StatusGreen; "expired"->StatusRed; "failed"->StatusRed; else->TextSecondary })
                                    Text("created ${exp.createdAt?.substringBefore("T") ?: "—"} · expires ${exp.expiresAt?.substringBefore("T") ?: "—"}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                    if (exp.downloadTokenExpiresAt != null) Text("link valid until ${exp.downloadTokenExpiresAt.substringBefore("T")}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { viewModel.refreshExport(exp.id) }, modifier = Modifier.heightIn(min = 32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Refresh", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) }
                                        TextButton(onClick = { viewModel.deleteExport(exp.id) }, modifier = Modifier.heightIn(min = 32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Delete", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = WarningRed) }
                                    }
                                    Button(
                                        onClick = {
                                            downloading = exp.id
                                            scope.launch {
                                                val bytes = viewModel.downloadExportBytes(exp.id, null)
                                                downloading = null
                                                if (bytes != null) {
                                                    try {
                                                        val file = java.io.File(context.cacheDir, "export-${exp.id}.csv")
                                                        file.writeBytes(bytes)
                                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                            setDataAndType(uri, "text/csv")
                                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(android.content.Intent.createChooser(intent, "Open CSV"))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        },
                                        enabled = exp.status == "completed" && downloading != exp.id,
                                        modifier = Modifier.heightIn(min = 36.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) { Text(if (downloading==exp.id) "…" else "Download", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) }
                                }
                            }
                            if (idx < exports.lastIndex) androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
        item { Text("410 Expired — file removed after TTL. Download link valid 1h.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
fun AlertPreferencesScreen(
    onBack: () -> Unit,
    viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val prefs by viewModel.alertPreferences.collectAsState()
    val isLoading by viewModel.isLoadingPrefs.collectAsState()
    var editing by remember { mutableStateOf<com.example.medac.data.AlertPreferenceDto?>(null) }
    // local mutable copy for editing
    var draftPrefs by remember { mutableStateOf<List<com.example.medac.data.AlertPreferenceDto>>(emptyList()) }
    LaunchedEffect(Unit) { viewModel.loadAlertPreferences() }
    LaunchedEffect(prefs) { if (draftPrefs.isEmpty() || prefs.isNotEmpty()) draftPrefs = prefs }
    val display = if (draftPrefs.isNotEmpty()) draftPrefs else prefs
    // ensure defaults if empty: show skeletons for known types
    val knownTypes = listOf("low_stock","expiration","missed_user_attention_med","stale_device","generic")
    val effective = if (display.isEmpty() && !isLoading) knownTypes.map { t ->
        com.example.medac.data.AlertPreferenceDto(id = t, patientId = "", userId = null, alertType = t, enabled = true, delayMinutes = 0, quietHoursStart = "22:00", quietHoursEnd = "07:00", timezone = java.time.ZoneId.systemDefault().id)
    } else display
    // save helper
    fun saveAll() {
        val items = effective.map {
            com.example.medac.data.PutAlertPreferenceItem(
                alertType = it.alertType, enabled = it.enabled, delayMinutes = it.delayMinutes,
                quietHoursStart = it.quietHoursStart, quietHoursEnd = it.quietHoursEnd, timezone = it.timezone
            )
        }
        viewModel.updateAlertPreferences(items)
    }
    editing?.let { pref ->
        var enabled by remember(pref.id) { mutableStateOf(pref.enabled) }
        var delay by remember(pref.id) { mutableStateOf(pref.delayMinutes?.toString() ?: "0") }
        var quietStart by remember(pref.id) { mutableStateOf(pref.quietHoursStart ?: "22:00") }
        var quietEnd by remember(pref.id) { mutableStateOf(pref.quietHoursEnd ?: "07:00") }
        var tz by remember(pref.id) { mutableStateOf(pref.timezone ?: java.time.ZoneId.systemDefault().id) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(pref.alertType.replace("_"," ")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Enabled", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    OutlinedTextField(value = delay, onValueChange = { delay = it.filter { c-> c.isDigit() }.take(4) }, label = { Text("Delay minutes (0..1440)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showTimePicker(context, quietStart) { quietStart = it } }) { Text(quietStart, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                        Text("—", color = TextSecondary)
                        Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.clickable { showTimePicker(context, quietEnd) { quietEnd = it } }) { Text(quietEnd, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = BlueInfoText) }
                    }
                    OutlinedTextField(value = tz, onValueChange = { tz = it }, label = { Text("Timezone (IANA)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = pref.copy(enabled = enabled, delayMinutes = delay.toIntOrNull()?.coerceIn(0,1440), quietHoursStart = quietStart.ifBlank{null}, quietHoursEnd = quietEnd.ifBlank{null}, timezone = tz.ifBlank{null})
                    draftPrefs = effective.map { if (it.id == pref.id || it.alertType == pref.alertType) updated else it }
                    // also update viewmodel list optimistically
                    editing = null
                }, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(14.dp)) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Alert preferences", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        if (isLoading && effective.isEmpty()) {
            item { Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) { Text("Loading…", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = TextSecondary) } }
        } else {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        effective.forEachIndexed { idx, pref ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { editing = pref }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(pref.alertType.replace("_"," "), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TextPrimary)
                                    val sub = buildString {
                                        append(if (pref.enabled) "Enabled" else "Disabled")
                                        pref.delayMinutes?.let { append(" · ${it}min delay") }
                                        if (pref.quietHoursStart != null && pref.quietHoursEnd != null) append(" · quiet ${pref.quietHoursStart}–${pref.quietHoursEnd}")
                                        if (pref.timezone != null) append(" · ${pref.timezone}")
                                    }
                                    Text(sub, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                                }
                                Switch(checked = pref.enabled, onCheckedChange = { checked ->
                                    val upd = pref.copy(enabled = checked)
                                    draftPrefs = effective.map { if (it.id == pref.id || it.alertType == pref.alertType) upd else it }
                                })
                            }
                            if (idx < effective.lastIndex) androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { viewModel.loadAlertPreferences() }, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Refresh", style = MaterialTheme.typography.labelMedium) }
                    Button(onClick = { saveAll() }, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Save", style = MaterialTheme.typography.labelMedium) }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Generate alerts", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                        val gen by viewModel.generateResult.collectAsState()
                        Button(onClick = { viewModel.generateAlerts() }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Generate now", style = MaterialTheme.typography.labelMedium) }
                        gen?.let { g -> Text("Last: low_stock ${g.lowStock} · expiration ${g.expiration} · stale_device ${g.staleDevice} · missed ${g.missed}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusGreen) }
                    }
                }
            }
        }
        item { Text("Quiet hours per preference — Tap row to edit delay & quiet hours via time pickers. PUT 1..20 prefs.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

/**
 * Label-scan preference: vision (photo sent to the AI model) vs text scan
 * (on-device Google Lens, only the text leaves the phone). The non-selected
 * path stays available as the automatic fallback.
 */
@Composable
fun ScanSettingsScreen(
    onBack: () -> Unit,
    viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val visionDefault by viewModel.scanVisionDefault.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Scan settings", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Read the photo with AI (vision)",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                color = TextPrimary
                            )
                            Text(
                                if (visionDefault) "Default: the label photo is read by the AI model."
                                else "Default: text is read on-device with Google Lens.",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                color = TextSecondary
                            )
                        }
                        Switch(checked = visionDefault, onCheckedChange = { viewModel.setScanVisionDefault(it) })
                    }
                    androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                    Text(
                        "Vision sends the label photo to your Medac server, where the AI model reads it. " +
                            "Text scan keeps the photo on the device and sends only the recognised text (Google Lens). " +
                            "Whichever you choose, the other path is kept as a fallback: if vision cannot name the medicine, " +
                            "Medac falls back to the text scan automatically.",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = TextSecondary
                    )
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Photos are only sent when you scan a label. This applies to the next scan.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = BlueInfoText
                )
            }
        }
    }
}

@Composable
fun PeopleSharingScreen(onBack: () -> Unit, viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val memberships by viewModel.memberships.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val msg by viewModel.patientActionMsg.collectAsState()
    val err by viewModel.patientError.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("viewer") }
    var token by rememberSaveable { mutableStateOf("") }
    var editMember by remember { mutableStateOf<com.example.medac.data.MembershipDto?>(null) }
    LaunchedEffect(Unit) { viewModel.loadMemberships(); viewModel.loadInvites() }
    editMember?.let { m ->
        var sel by remember(m.id) { mutableStateOf(m.role) }
        AlertDialog(onDismissRequest = { editMember = null }, title = { Text("Update role") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(m.effectiveUserId.take(8), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("viewer","contributor","manager","owner").forEach { r ->
                        Surface(shape = RoundedCornerShape(50), color = if (sel==r) NavyPrimary else CardSurface, border = BorderStroke(0.5.dp, if(sel==r) NavyPrimary else BorderSubtle), modifier = Modifier.clickable{ sel=r }) {
                            Text(r, modifier = Modifier.padding(horizontal=8.dp, vertical=6.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color = if(sel==r) CardSurface else TextPrimary)
                        }
                    }
                }
            }
        }, confirmButton = { Button(onClick={ viewModel.updateMembership(m.id, sel); editMember=null }, modifier=Modifier.heightIn(min=44.dp), shape=RoundedCornerShape(14.dp)) { Text("Save") } }, dismissButton = { TextButton(onClick={ editMember=null }) { Text("Cancel") } })
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("People & sharing", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        if (msg != null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusGreenContainer, modifier=Modifier.fillMaxWidth()) { Row(modifier=Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween){ Text(msg!!, style=MaterialTheme.typography.labelMedium, color=StatusGreen, modifier=Modifier.weight(1f)); TextButton(onClick={ viewModel.clearPatientMsg() }) { Text("×") } } } }
        if (err != null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusRedContainer, modifier=Modifier.fillMaxWidth()) { Row(modifier=Modifier.padding(12.dp)){ Text(err!!, style=MaterialTheme.typography.labelMedium, color=StatusRed, modifier=Modifier.weight(1f)); TextButton(onClick={ viewModel.clearPatientMsg() }){ Text("×") } } } }
        item {
            Surface(shape=RoundedCornerShape(14.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth()){
                Column(modifier=Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(modifier=Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween){
                        Text("Members (${memberships.size})", style=MaterialTheme.typography.titleMedium.copy(fontSize=14.sp), color=TextPrimary)
                        TextButton(onClick={ viewModel.loadMemberships() }, modifier=Modifier.heightIn(min=32.dp), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)) { Text("Refresh", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp)) }
                    }
                    if(memberships.isEmpty()) Text("No members — pull to refresh", style=MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp), color=TextSecondary)
                }
            }
        }
        if(memberships.isNotEmpty()){
            item {
                Surface(shape=RoundedCornerShape(14.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth()){
                    Column{
                        memberships.forEachIndexed{ idx, m ->
                            Row(modifier=Modifier.padding(horizontal=14.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                Column(modifier=Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)){
                                    Text(m.effectiveUserId.take(8) + " · " + m.role, style=MaterialTheme.typography.titleMedium.copy(fontSize=13.sp), color=TextPrimary)
                                    Text((m.status ?: "active") + (m.revokedAt?.let{ " · revoked" } ?: ""), style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=TextSecondary)
                                    Text("created ${m.createdAt?.take(10) ?: "—"}", style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=TextSecondary)
                                }
                                Surface(shape=RoundedCornerShape(50), color=when(m.role){"owner"->NavyPrimary; "manager"->StatusBlueContainer; "contributor"->StatusGreenContainer; else->StatusGrayContainer}){
                                    Text(m.role, modifier=Modifier.padding(horizontal=6.dp, vertical=3.dp), style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=when(m.role){"owner"->CardSurface; "manager"->StatusBlue; "contributor"->StatusGreen; else->TextSecondary})
                                }
                            }
                            Row(modifier=Modifier.padding(start=14.dp, end=14.dp, bottom=8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                TextButton(onClick={ editMember=m }, modifier=Modifier.heightIn(min=32.dp), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)){ Text("Change role", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp)) }
                                TextButton(onClick={ viewModel.revokeMembership(m.id) }, modifier=Modifier.heightIn(min=32.dp), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)){ Text("Revoke", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=WarningRed) }
                            }
                            if(idx < memberships.lastIndex) androidx.compose.material3.HorizontalDivider(color=DividerMuted, thickness=0.5.dp)
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Invite by email", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                    OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("viewer", "contributor", "manager","owner").forEach { r ->
                            Surface(shape = RoundedCornerShape(50), color = if (role == r) NavyPrimary else CardSurface, border = BorderStroke(0.5.dp, if (role == r) NavyPrimary else BorderSubtle), modifier = Modifier.clickable { role = r }) {
                                Text(r, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = if (role == r) CardSurface else TextPrimary, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp))
                            }
                        }
                    }
                    Button(onClick = { viewModel.createInvite(email, role); email="" }, enabled = email.contains("@"), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Send invite", style = MaterialTheme.typography.labelMedium) }
                    Text("Only owners can invite owners", style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=TextSecondary)
                }
            }
        }
        item {
            Surface(shape=RoundedCornerShape(14.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth()){
                Column(modifier=Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(modifier=Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween){
                        Text("Pending invites (${invites.size})", style=MaterialTheme.typography.titleMedium.copy(fontSize=14.sp), color=TextPrimary)
                        TextButton(onClick={ viewModel.loadInvites() }, modifier=Modifier.heightIn(min=32.dp), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)){ Text("Refresh", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp)) }
                    }
                    if(invites.isEmpty()) Text("No pending invites", style=MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp), color=TextSecondary)
                }
            }
        }
        if(invites.isNotEmpty()){
            item {
                Surface(shape=RoundedCornerShape(14.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth()){
                    Column{
                        invites.forEachIndexed{ idx, inv ->
                            Row(modifier=Modifier.padding(horizontal=14.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                Column(modifier=Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)){
                                    Text(inv.invitedEmailNormalized ?: inv.id.take(8), style=MaterialTheme.typography.titleMedium.copy(fontSize=13.sp), color=TextPrimary, maxLines=1)
                                    Text("${inv.role ?: "—"} · expires ${inv.expiresAt?.take(10) ?: "—"}", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=TextSecondary)
                                    if(inv.acceptedAt!=null) Text("accepted ${inv.acceptedAt.take(10)}", style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=StatusGreen)
                                    if(inv.revokedAt!=null) Text("revoked", style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=StatusRed)
                                }
                                TextButton(onClick={ viewModel.revokeInvite(inv.id) }, modifier=Modifier.heightIn(min=32.dp), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)){ Text("Revoke", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=WarningRed) }
                            }
                            if(idx < invites.lastIndex) androidx.compose.material3.HorizontalDivider(color=DividerMuted, thickness=0.5.dp)
                        }
                    }
                }
            }
        }
        item {
            Surface(shape=RoundedCornerShape(14.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth()){
                Column(modifier=Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Accept invite by token", style=MaterialTheme.typography.titleMedium.copy(fontSize=14.sp), color=TextPrimary)
                    Text("Paste token from email/outbox (opaque, ≥10 chars)", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=TextSecondary)
                    OutlinedTextField(value=token, onValueChange={ token=it }, modifier=Modifier.fillMaxWidth(), label={ Text("Invite token") }, singleLine=true, shape=RoundedCornerShape(12.dp))
                    Button(onClick={ viewModel.acceptInvite(token); token="" }, enabled=token.trim().length>=10, modifier=Modifier.fillMaxWidth().heightIn(min=44.dp), shape=RoundedCornerShape(12.dp)){ Text("Accept invite", style=MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
fun SecurityScreen(onBack: () -> Unit, authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val sessions by authViewModel.sessions.collectAsState()
    val totpSetup by authViewModel.totpSetup.collectAsState()
    val recovery by authViewModel.recoveryCodes.collectAsState()
    val busy by authViewModel.isBusy.collectAsState()
    val err by authViewModel.error.collectAsState()
    val msg by authViewModel.authActionMsg.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit){ authViewModel.listSessions(context) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Security", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        if(msg!=null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusGreenContainer, modifier=Modifier.fillMaxWidth()){ Row(modifier=Modifier.padding(12.dp)){ Text(msg!!, style=MaterialTheme.typography.labelMedium, color=StatusGreen, modifier=Modifier.weight(1f)); TextButton(onClick={ authViewModel.clearAuthMsg() }){ Text("×") } } } }
        if(err!=null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusRedContainer, modifier=Modifier.fillMaxWidth()){ Row(modifier=Modifier.padding(12.dp)){ Text(err!!, style=MaterialTheme.typography.labelMedium, color=StatusRed, modifier=Modifier.weight(1f)); TextButton(onClick={ authViewModel.clearError() }){ Text("×") } } } }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MFA — Time-based OTP", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                    Text("Scan QR placeholder, confirm 6-digit code. Recovery codes shown once (XXXX-XXXX-XXXX).", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp), color = TextSecondary)
                    if(totpSetup==null){
                        Button(onClick = { authViewModel.totpSetup(context) }, enabled=!busy, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Set up MFA", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        Surface(shape=RoundedCornerShape(12.dp), color=BlueInfo, modifier=Modifier.fillMaxWidth()){
                            Column(modifier=Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Text("Secret: ${totpSetup!!.secretBase32}", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=BlueInfoText)
                                Text(totpSetup!!.otpauthUrl, style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=TextSecondary)
                                Surface(shape=RoundedCornerShape(8.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth().height(96.dp)){
                                    Box(contentAlignment=Alignment.Center, modifier=Modifier.fillMaxSize()){ Text("QR placeholder", style=MaterialTheme.typography.labelMedium, color=TextSecondary) }
                                }
                                Text("Scan otpauth_url in authenticator app", style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=TextSecondary)
                            }
                        }
                        OutlinedTextField(value=code, onValueChange={ code=it.filter{ c-> c.isDigit() }.take(6) }, label={ Text("6-digit code") }, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth())
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                            Button(onClick={ authViewModel.totpConfirm(context, code) }, enabled=code.length==6 && !busy, modifier=Modifier.weight(1f).heightIn(min=44.dp), shape=RoundedCornerShape(12.dp)){ Text("Confirm", style=MaterialTheme.typography.labelMedium) }
                            OutlinedButton(onClick={ authViewModel.clearTotpSetup(); code="" }, modifier=Modifier.weight(1f).heightIn(min=44.dp), shape=RoundedCornerShape(12.dp)){ Text("Cancel") }
                        }
                    }
                    if(!recovery.isNullOrEmpty()){
                        Surface(shape=RoundedCornerShape(12.dp), color=StatusAmberContainer, modifier=Modifier.fillMaxWidth()){
                            Column(modifier=Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)){
                                Text("Recovery codes — save them!", style=MaterialTheme.typography.titleMedium.copy(fontSize=13.sp), color=StatusAmber)
                                recovery!!.forEach{ c-> Text(c, style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=TextPrimary) }
                            }
                        }
                    }
                    OutlinedButton(onClick={ authViewModel.totpDelete(context) }, modifier=Modifier.fillMaxWidth().heightIn(min=44.dp), shape=RoundedCornerShape(12.dp), border=BorderStroke(0.5.dp, BorderSubtle)){ Text("Disable MFA", style=MaterialTheme.typography.labelMedium, color=WarningRed) }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier=Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(modifier=Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween){
                        Text("Active sessions (${sessions.size})", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                        TextButton(onClick={ authViewModel.listSessions(context) }, modifier=Modifier.heightIn(min=32.dp), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)){ Text("Refresh", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp)) }
                    }
                    if(sessions.isEmpty()) Text("No sessions — pull to refresh", style=MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp), color=TextSecondary)
                }
            }
        }
        if(sessions.isNotEmpty()){
            item {
                Surface(shape=RoundedCornerShape(14.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth()){
                    Column{
                        sessions.forEachIndexed{ idx, s ->
                            Row(modifier=Modifier.padding(horizontal=14.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                Column(modifier=Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)){
                                    Text(s.id.take(8) + (s.deviceId?.let{ " · $it" } ?: ""), style=MaterialTheme.typography.titleMedium.copy(fontSize=13.sp), color=TextPrimary, maxLines=1)
                                    Text("created ${s.createdAt?.take(16) ?: "—"} · last ${s.lastUsedAt?.take(16) ?: "—"}", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=TextSecondary)
                                    if(s.userAgentSummary!=null) Text(s.userAgentSummary, style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=TextSecondary, maxLines=1)
                                    Text("expires ${s.expiresAt?.take(10) ?: "—"}${if(s.revokedAt!=null) " · revoked" else ""}", style=MaterialTheme.typography.labelMedium.copy(fontSize=10.sp), color=if(s.revokedAt!=null) StatusRed else TextSecondary)
                                }
                                TextButton(onClick={ authViewModel.revokeSession(context, s.id) }, modifier=Modifier.heightIn(min=32.dp), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)){ Text("Revoke", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=WarningRed) }
                            }
                            if(idx < sessions.lastIndex) androidx.compose.material3.HorizontalDivider(color=DividerMuted, thickness=0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountScreen(username: String?, onBack: () -> Unit, onLogoutAll: () -> Unit = {}, viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel(), authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val patient by viewModel.currentPatient.collectAsState()
    val pErr by viewModel.patientError.collectAsState()
    val pMsg by viewModel.patientActionMsg.collectAsState()
    val authErr by authViewModel.error.collectAsState()
    val authMsg by authViewModel.authActionMsg.collectAsState()
    val busy by authViewModel.isBusy.collectAsState()
    var displayName by rememberSaveable { mutableStateOf("") }
    var timezone by rememberSaveable { mutableStateOf("") }
    var privacy by rememberSaveable { mutableStateOf("private") }
    var curPass by rememberSaveable { mutableStateOf("") }
    var newPass by rememberSaveable { mutableStateOf("") }
    var resetToken by rememberSaveable { mutableStateOf("") }
    var resetNewPass by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit){ viewModel.loadCurrentPatient() }
    LaunchedEffect(patient){
        patient?.let { displayName = it.displayName ?: ""; timezone = it.preferredTimezone ?: ""; privacy = it.notificationPrivacyMode ?: "private" }
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Account", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        if(pMsg!=null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusGreenContainer, modifier=Modifier.fillMaxWidth()){ Row(modifier=Modifier.padding(12.dp)){ Text(pMsg!!, style=MaterialTheme.typography.labelMedium, color=StatusGreen, modifier=Modifier.weight(1f)); TextButton(onClick={ viewModel.clearPatientMsg() }){ Text("×") } } } }
        if(pErr!=null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusRedContainer, modifier=Modifier.fillMaxWidth()){ Row(modifier=Modifier.padding(12.dp)){ Text(pErr!!, style=MaterialTheme.typography.labelMedium, color=StatusRed, modifier=Modifier.weight(1f)); TextButton(onClick={ viewModel.clearPatientMsg() }){ Text("×") } } } }
        if(authMsg!=null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusGreenContainer, modifier=Modifier.fillMaxWidth()){ Row(modifier=Modifier.padding(12.dp)){ Text(authMsg!!, style=MaterialTheme.typography.labelMedium, color=StatusGreen, modifier=Modifier.weight(1f)); TextButton(onClick={ authViewModel.clearAuthMsg() }){ Text("×") } } } }
        if(authErr!=null) item { Surface(shape=RoundedCornerShape(14.dp), color=StatusRedContainer, modifier=Modifier.fillMaxWidth()){ Row(modifier=Modifier.padding(12.dp)){ Text(authErr!!, style=MaterialTheme.typography.labelMedium, color=StatusRed, modifier=Modifier.weight(1f)); TextButton(onClick={ authViewModel.clearError() }){ Text("×") } } } }
        item { InfoCard(title = "Email", body = username ?: "—") }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Patient details", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                    OutlinedTextField(value=displayName, onValueChange={ displayName=it }, label={ Text("Display name") }, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=timezone, onValueChange={ timezone=it }, label={ Text("Timezone (IANA e.g. America/New_York)") }, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth())
                    Text("Privacy mode", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=TextSecondary)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        listOf("private","generic","detailed").forEach{ m ->
                            Surface(shape=RoundedCornerShape(50), color=if(privacy==m) NavyPrimary else CardSurface, border=BorderStroke(0.5.dp, if(privacy==m) NavyPrimary else BorderSubtle), modifier=Modifier.clickable{ privacy=m }){
                                Text(m, modifier=Modifier.padding(horizontal=8.dp, vertical=6.dp), style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=if(privacy==m) CardSurface else TextPrimary)
                            }
                        }
                    }
                    Button(onClick={ viewModel.updateCurrentPatient(displayName, timezone, privacy) }, modifier=Modifier.fillMaxWidth().heightIn(min=44.dp), shape=RoundedCornerShape(12.dp)){ Text("Save patient", style=MaterialTheme.typography.labelMedium) }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                        OutlinedButton(onClick={ viewModel.archiveCurrentPatient() }, modifier=Modifier.weight(1f).heightIn(min=44.dp), shape=RoundedCornerShape(12.dp), border=BorderStroke(0.5.dp, BorderSubtle)){ Text("Archive", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp)) }
                        OutlinedButton(onClick={ viewModel.requestDeletionCurrentPatient() }, modifier=Modifier.weight(1f).heightIn(min=44.dp), shape=RoundedCornerShape(12.dp), border=BorderStroke(0.5.dp, WarningRed.copy(alpha=0.5f))){ Text("Request deletion", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=WarningRed) }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Change password", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                    Text("Logs out other devices (bumps token_version)", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    OutlinedTextField(value=curPass, onValueChange={ curPass=it }, label={ Text("Current password") }, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=newPass, onValueChange={ newPass=it }, label={ Text("New password (≥12 chars)") }, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth())
                    Button(onClick = { authViewModel.passwordChange(context, curPass, newPass) }, enabled=newPass.length>=12 && curPass.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Change password", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        item {
            Surface(shape=RoundedCornerShape(14.dp), color=CardSurface, border=BorderStroke(0.5.dp, BorderSubtle), modifier=Modifier.fillMaxWidth()){
                Column(modifier=Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Reset password (with token)", style=MaterialTheme.typography.titleMedium.copy(fontSize=14.sp), color=TextPrimary)
                    Text("Token from /auth/password/forgot email (outbox rawToken in dev)", style=MaterialTheme.typography.labelMedium.copy(fontSize=11.sp), color=TextSecondary)
                    OutlinedTextField(value=resetToken, onValueChange={ resetToken=it }, label={ Text("Reset token") }, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=resetNewPass, onValueChange={ resetNewPass=it }, label={ Text("New password (≥12)") }, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth())
                    Button(onClick={ authViewModel.passwordResetConfirm(context, resetToken, resetNewPass) }, enabled=resetNewPass.length>=12 && resetToken.isNotBlank() && !busy, modifier=Modifier.fillMaxWidth().heightIn(min=44.dp), shape=RoundedCornerShape(12.dp)){ Text("Reset password", style=MaterialTheme.typography.labelMedium) }
                }
            }
        }
        item { OutlinedButton(onClick = onLogoutAll, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, BorderSubtle)) { Text("Log out everywhere", style = MaterialTheme.typography.labelMedium) } }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("About / privacy", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        item { InfoCard(title = "Data", body = "Medicines, doses, reminders stored locally + synced via /sync. Photos in app-private storage. OCR on device first.") }
        item { InfoCard(title = "Support", body = "Include request ID from errors. x-request-id is logged locally.") }
    }
}

@Composable
private fun DrugTypeaheadField(
    draft: MedicineDraft,
    onNameChange: (String) -> Unit,
    onDosageChange: (String) -> Unit,
    uncertainFields: List<String>,
    viewModel: MedacViewModel
) {
    val results by viewModel.drugSearchResults.collectAsState()
    val searching by viewModel.isDrugSearching.collectAsState()
    val error by viewModel.drugSearchError.collectAsState()
    val selected by viewModel.selectedConcept.collectAsState()
    // debounced search on name change
    LaunchedEffect(draft.name) {
        if (draft.name.trim().length >= 2) viewModel.searchDrugs(draft.name) else viewModel.clearDrugSearch()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if ("entered_name" in uncertainFields || "candidate_name" in uncertainFields) {
            UncertainFieldBox(label = "Medicine name", value = draft.name, onValueChange = onNameChange)
        } else {
            OutlinedTextField(value = draft.name, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth(), label = { Text("Medicine name") }, placeholder = { Text("e.g. Lisinopril") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        }
        if (searching) {
            Text("Searching...", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
        }
        if (error != null && results.isEmpty()) {
            Text(error ?: "", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusRed, maxLines = 2)
        }
        if (results.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column {
                    results.take(6).forEachIndexed { idx, item ->
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.selectDrugConcept(item)
                            onNameChange(item.effectiveName)
                            // apply dose if item name contains strength
                            Regex("""\d+(\.\d+)?\s*(mg|mcg|g|mg/mL)""", RegexOption.IGNORE_CASE).find(item.effectiveName)?.value?.let { onDosageChange(it) }
                        }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.effectiveName.ifBlank { item.rxcui ?: "—" }, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("rxcui ${item.rxcui ?: "—"}${item.tty?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, maxLines = 1)
                            }
                            Text("›", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        }
                        if (idx < results.take(6).lastIndex) androidx.compose.material3.HorizontalDivider(color = DividerMuted, thickness = 0.5.dp)
                    }
                }
            }
        }
        if (selected != null) {
            Surface(shape = RoundedCornerShape(14.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Selected concept", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = BlueInfoText)
                    Text("${selected!!.effectiveName} · rxcui ${selected!!.rxcui}${selected!!.tty?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = TextPrimary)
                    if (selected!!.strength != null) Text("Strength: ${selected!!.strength}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    TextButton(onClick = { viewModel.clearSelectedConcept() }, modifier = Modifier.heightIn(min = 32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Clear", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) }
                }
            }
        }
    }
}

@Composable
private fun NdcResolverSection(viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var ndc by rememberSaveable { mutableStateOf("") }
    val selected by viewModel.selectedConcept.collectAsState()
    val loading by viewModel.conceptLoading.collectAsState()
    val error by viewModel.drugSearchError.collectAsState()
    val valid = ndc.trim().length in 8..20
    Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NDC lookup", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
            Text("Resolve an 8..20 char NDC to an RxCUI concept.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = ndc, onValueChange = { ndc = it.filter { c -> c.isDigit() || c == '-' }.take(20) }, modifier = Modifier.weight(1f), label = { Text("NDC") }, placeholder = { Text("e.g. 12345-678-90") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Button(onClick = { viewModel.resolveNdc(ndc) }, enabled = valid && !loading, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text(if (loading) "…" else "Resolve", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)) }
            }
            if (error != null) Text(error ?: "", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = StatusRed)
            if (selected != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = BlueInfo, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${selected!!.effectiveName} · rxcui ${selected!!.rxcui}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TextPrimary)
                        Text("tty ${selected!!.tty ?: "—"} · form ${selected!!.form ?: "—"} · route ${selected!!.route ?: "—"}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                        TextButton(onClick = { viewModel.clearSelectedConcept() }, modifier = Modifier.heightIn(min = 32.dp)) { Text("Clear", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthDiagnosticsScreen(viewModel: MedacViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val health by viewModel.healthStatus.collectAsState()
    val version by viewModel.versionInfo.collectAsState()
    val loading by viewModel.isHealthLoading.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshHealth() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Diagnostics", style = MaterialTheme.typography.titleLarge, color = TextPrimary) }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Health", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        StatusPill(label = "live", ok = health?.liveOk == true, value = health?.live?.status ?: "—")
                        StatusPill(label = "ready", ok = health?.readyOk == true, value = health?.ready?.status ?: "—")
                    }
                    if (health?.live?.service != null) Text("service ${health?.live?.service}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    Text("last checked ${health?.lastCheckedAt?.take(19) ?: "—"}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    if (loading) Text("Refreshing...", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    Button(onClick = { viewModel.refreshHealth() }, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("Refresh", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(0.5.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Version", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.6.sp), color = TextSecondary)
                    Text("version ${version?.version ?: "—"}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary)
                    Text("build ${version?.effectiveBuildId ?: "—"} · env ${version?.env ?: "—"}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    Text("GET /health/live, /health/ready, /version — public, no auth. 503 ready = not_ready.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                }
            }
        }
        item { Text("If live ≠ ok or ready ≠ ready, check server. Version build_id verifies deploy.", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun StatusPill(label: String, ok: Boolean, value: String) {
    Surface(shape = RoundedCornerShape(50), color = if (ok) StatusGreenContainer else StatusRedContainer) {
        Text("$label: $value", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = if (ok) StatusGreen else StatusRed)
    }
}

@Composable
private fun DashedBorderBox() {
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        drawRoundRect(brush = SolidColor(BorderSubtle), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()))
    }
}
