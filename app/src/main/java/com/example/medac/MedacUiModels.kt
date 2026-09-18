package com.example.medac

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MedacTab(
    val label: String,
    val icon: @Composable () -> Unit
) {
    TODAY("Today", { Icon(Icons.Outlined.Home, contentDescription = null) }),
    MEDICINES("Medicines", { Icon(Icons.Outlined.Medication, contentDescription = null) }),
    REMINDERS("Reminders", { Icon(Icons.Outlined.Notifications, contentDescription = null) }),
    PROFILE("Profile", { Icon(Icons.Outlined.AccountCircle, contentDescription = null) })
}

enum class DoseStatus {
    TAKEN,
    NOW,
    UPCOMING,
    SKIPPED,
    MISSED
}

enum class ScanMode {
    OCR,
    PRESCRIPTION
}

data class DoseScheduleItem(
    val medicineId: Long = 0L,
    val medicineName: String,
    val genericNameAndDose: String,
    val use: String,
    val time: String,
    val active: Boolean,
    val cardImageUri: String? = null
)

data class ReminderEntry(
    val medicineName: String,
    val time: String,
    val genericNameAndDose: String,
    val active: Boolean,
    val cardImageUri: String? = null
)

data class MedicineDraft(
    val photoUri: Uri? = null,
    val cardImageUri: String? = null,
    val detectedSummary: String = "",
    val detectedText: String = "",
    val name: String = "",
    val genericNameAndDose: String = "",
    val purpose: String = "",
    val times: List<String> = emptyList(),
    val isAiEnhanced: Boolean = false,
    val form: String = "",
    val foodTiming: String = "",
    val instruction: String = "",
    // "high" | "medium" | "low" from the server interpretation — never guessed locally
    val aiConfidence: String? = null,
    val frequency: String = "Once daily",
    val duration: String = "Ongoing",
    val startDate: String = "",
    val remindersEnabled: Boolean = true,
    val refillTrackingEnabled: Boolean = false,
    val notes: String = "",
    val currentSupply: Int = 0,
    val refillThresholdPercent: Int = 0
)

data class OcrDraftSuggestion(
    val summary: String,
    val name: String,
    val genericNameAndDose: String,
    val purpose: String,
    val times: List<String>,
    val fullText: String
)

data class PrescriptionImportState(
    val medicines: List<ManagedMedicine> = emptyList(),
    val status: String = "",
    val sourceUri: Uri? = null
)

fun reminderKey(name: String, time: String): String = "$name|$time"

fun parseTime(value: String): LocalTime? {
    return try {
        LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        null
    }
}

// No log yet → time decides. Due window is 30min before → 60min after; after that an
// unlogged dose is honestly Missed (still tappable for a late Taken, per §4.2).
fun doseStatusFor(time: String): DoseStatus {
    val target = parseTime(time) ?: return DoseStatus.UPCOMING
    val now = LocalTime.now()
    return when {
        now.isBefore(target) && java.time.Duration.between(now, target).toMinutes() > 30 -> DoseStatus.UPCOMING
        now.isBefore(target) -> DoseStatus.NOW
        java.time.Duration.between(target, now).toMinutes() <= 60 -> DoseStatus.NOW
        else -> DoseStatus.MISSED
    }
}

fun showTimePicker(context: Context, initial: String?, onSelected: (String) -> Unit) {
    val initialTime = parseTime(initial ?: "08:00") ?: LocalTime.of(8, 0)
    android.app.TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(String.format(Locale.getDefault(), "%02d:%02d", hour, minute)) },
        initialTime.hour, initialTime.minute, true
    ).show()
}

fun showDatePicker(context: Context, initial: String?, onSelected: (String) -> Unit) {
    val init = try { java.time.LocalDate.parse(initial) } catch (_: Exception) { java.time.LocalDate.now() }
    android.app.DatePickerDialog(context, { _, y, m, d -> onSelected(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)) }, init.year, init.monthValue - 1, init.dayOfMonth).show()
}

fun currentPeriod(now: LocalTime = LocalTime.now()): String {
    val h = now.hour
    return when (h) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        else -> "Night"
    }
}

fun periodForTime(time: String): String {
    val h = parseTime(time)?.hour ?: return "Night"
    return when (h) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        else -> "Night"
    }
}

fun doseLogKey(medicineId: Long, date: String, time: String): String = "${medicineId}_${date}_${time}"

fun todayDateString(): String = java.time.LocalDate.now().toString()

/**
 * Tolerant taken-check: matches by stable id OR by medicine name
 * (case-insensitive) so logs written from the alarm receiver / notification
 * before a cloud-sync id change still count. Date is enforced when supplied;
 * Today screens pass today's date, calendar passes the selected date.
 */
fun isDoseTaken(
    item: DoseScheduleItem,
    logs: List<DoseLogEntry>,
    date: String? = null
): Boolean {
    return logs.any { log ->
        val sameMed = log.medicineId == item.medicineId ||
            log.medicineName.equals(item.medicineName, ignoreCase = true)
        val sameTime = log.time == item.time
        val sameDate = date == null || log.date.isBlank() || log.date == date
        sameMed && sameTime && log.status == "TAKEN" && sameDate
    }
}

fun doseStatusForLog(time: String, log: DoseLogEntry?): DoseStatus {
    if (log != null) {
        return when (log.status) {
            "TAKEN" -> DoseStatus.TAKEN
            "SKIPPED" -> DoseStatus.SKIPPED
            "MISSED" -> DoseStatus.MISSED
            else -> doseStatusFor(time)
        }
    }
    return doseStatusFor(time)
}

data class PatientChip(val id: String, val name: String, val role: String)

data class AlertItem(
    val id: String,
    val severity: String, // info | attention | urgent_review | potential_emergency
    val message: String
)

data class InventoryUi(
    val remaining: Int,
    val threshold: Int,
    val forecastDays: Int?
)

enum class AddMedicineStep { Capture, Review, Schedule }

data class ScheduleDraftUi(
    val type: String = "fixed_times",
    val times: List<String> = emptyList(),
    val timingMode: String = "local_clock" // local_clock | elapsed_interval
)

fun isValidReminderTime(value: String): Boolean {
    return Regex("""^([01]\d|2[0-3]):[0-5]\d$""").matches(value)
}

fun suggestedTimesForPrescriptionLine(line: String): List<String> {
    val normalized = line.lowercase(Locale.getDefault())
    return when {
        normalized.contains("1-1-1") ||
            normalized.contains("thrice") ||
            normalized.contains("tds") ||
            normalized.contains("tid") -> listOf("08:00", "14:00", "20:00")
        normalized.contains("1-0-1") ||
            normalized.contains("twice") ||
            normalized.contains("bd") ||
            normalized.contains("bid") -> listOf("08:00", "20:00")
        normalized.contains("0-0-1") ||
            normalized.contains("night") ||
            normalized.contains("hs") -> listOf("20:00")
        normalized.contains("0-1-0") ||
            normalized.contains("afternoon") -> listOf("14:00")
        normalized.contains("evening") -> listOf("18:00")
        normalized.contains("morning") ||
            normalized.contains("1-0-0") ||
            normalized.contains("once") ||
            normalized.contains("daily") ||
            normalized.contains("od") -> listOf("08:00")
        normalized.contains("qid") -> listOf("08:00", "12:00", "16:00", "20:00")
        else -> listOf("08:00")
    }
}
