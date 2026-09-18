package com.example.medac

import com.google.gson.annotations.SerializedName

// API Request/Response Models
data class SendRequest(val message: String)
data class SendResponse(
    val message: String,
    val reply: String,
    @SerializedName("duration_ms") val durationMs: Int
)

data class UploadResponse(
    val status: String,
    val filename: String,
    @SerializedName("upload_status") val uploadStatus: String,
    val detail: String
)

// Local Reminder Model
data class MedicineReminder(
    val id: Long = System.currentTimeMillis(),
    val medicineName: String,
    val times: List<String> // HH:MM
)

data class ManagedMedicine(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val genericNameAndDose: String,
    val purpose: String = "",
    val times: List<String> = emptyList(),
    val photoUris: List<String> = emptyList(),
    val ocrText: String = "",
    val cardImageUri: String? = null,
    val form: String = "",
    val foodTiming: String = "",
    val instruction: String = "",
    // active | paused | discontinued | archived — nullable because Gson skips Kotlin
    // defaults when reading older persisted JSON that predates the field
    val status: String? = "active",
    val highAttention: Boolean = false,
    val frequency: String = "Once daily",
    val duration: String = "Ongoing",
    val startDate: String = "",
    val refillTrackingEnabled: Boolean = false,
    val currentSupply: Int = 0,
    val refillThresholdPercent: Int = 0,
    val notes: String = ""
)

// Null (legacy record) counts as active
val ManagedMedicine.statusOrActive: String get() = status ?: "active"

data class DoseLogEntry(
    val id: String = "",
    val medicineId: Long = 0L,
    val medicineName: String = "",
    val time: String = "",
    val date: String = "",
    val status: String = "PENDING",
    val reason: String = "",
    val doseAmount: String = "",
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatMessage(val text: String, val isUser: Boolean)
