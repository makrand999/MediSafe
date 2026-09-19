package com.example.medac.data

import com.google.gson.annotations.SerializedName

// ── Auth ──
data class RegisterRequest(val email: String, val password: String, val preferred_locale: String? = null)
data class RegisterResponse(@SerializedName("user_id") val userId: String)
data class LoginRequest(
    val email: String,
    val password: String,
    val device: DeviceInfo? = null
)
data class DeviceInfo(
    val platform: String? = "android",
    @SerializedName("display_name") val displayName: String? = null,
    val timezone: String? = null
)
data class LoginResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("mfa_required") val mfaRequired: Boolean? = null,
    @SerializedName("mfa_session_token") val mfaSessionToken: String? = null
)
data class MfaVerifyRequest(
    @SerializedName("mfa_session_token") val mfaSessionToken: String,
    val code: String,
    @SerializedName("use_recovery_code") val useRecoveryCode: Boolean = false
)
data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)
data class ForgotRequest(val email: String)
data class ApiErrorEnvelope(val error: ApiErrorDetail)
data class ApiErrorDetail(val code: String, val message: String, @SerializedName("request_id") val requestId: String?, val details: Any? = null)

// ── Patient ──
data class PatientDto(
    val id: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("preferred_timezone") val preferredTimezone: String?,
    @SerializedName("notification_privacy_mode") val notificationPrivacyMode: String?
)
data class PatientsResponse(val patients: List<PatientDto>)
data class CreatePatientRequest(
    @SerializedName("display_name") val displayName: String,
    @SerializedName("preferred_timezone") val preferredTimezone: String? = null,
    @SerializedName("notification_privacy_mode") val notificationPrivacyMode: String? = null
)

// ── Medication ──
data class MedicationDto(
    val id: String,
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("entered_name") val enteredName: String,
    @SerializedName("entered_strength_value") val enteredStrengthValue: String?,
    @SerializedName("entered_strength_unit") val enteredStrengthUnit: String?,
    val form: String?,
    val route: String?,
    @SerializedName("dose_quantity_value") val doseQuantityValue: String,
    @SerializedName("dose_quantity_unit") val doseQuantityUnit: String,
    @SerializedName("indication_text") val indicationText: String? = null,
    @SerializedName("label_instructions_text") val labelInstructionsText: String?,
    @SerializedName("normalization_status") val normalizationStatus: String?,
    val status: String,
    @SerializedName("high_attention_user_flag") val highAttention: Boolean?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)
data class MedicationsResponse(val medications: List<MedicationDto>)
data class CreateMedicationRequest(
    @SerializedName("entered_name") val enteredName: String,
    @SerializedName("entered_strength_value") val enteredStrengthValue: String? = null,
    @SerializedName("entered_strength_unit") val enteredStrengthUnit: String? = null,
    val form: String? = null,
    val route: String? = null,
    @SerializedName("dose_quantity_value") val doseQuantityValue: String,
    @SerializedName("dose_quantity_unit") val doseQuantityUnit: String,
    @SerializedName("indication_text") val indicationText: String? = null,
    @SerializedName("label_instructions_text") val labelInstructionsText: String? = null,
    @SerializedName("normalization_status") val normalizationStatus: String = "unresolved",
    val status: String = "active",
    @SerializedName("high_attention_user_flag") val highAttention: Boolean = false,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null
)
data class UpdateMedicationRequest(
    @SerializedName("entered_name") val enteredName: String? = null,
    @SerializedName("entered_strength_value") val enteredStrengthValue: String? = null,
    @SerializedName("entered_strength_unit") val enteredStrengthUnit: String? = null,
    val form: String? = null,
    val route: String? = null,
    @SerializedName("dose_quantity_value") val doseQuantityValue: String? = null,
    @SerializedName("dose_quantity_unit") val doseQuantityUnit: String? = null,
    @SerializedName("indication_text") val indicationText: String? = null,
    @SerializedName("label_instructions_text") val labelInstructionsText: String? = null,
    val status: String? = null,
    @SerializedName("high_attention_user_flag") val highAttention: Boolean? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null
)

// ── Schedule ──
data class CreateScheduleRequest(
    @SerializedName("schedule_type") val scheduleType: String,
    @SerializedName("timing_mode") val timingMode: String,
    val timezone: String,
    @SerializedName("miss_window_minutes") val missWindowMinutes: Int = 60,
    @SerializedName("effective_from") val effectiveFrom: String,
    @SerializedName("fixed_times") val fixedTimes: List<FixedTime>? = null,
    val interval: IntervalPayload? = null
)
data class ScheduleVersionDto(
    val id: String,
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("version_number") val versionNumber: Int? = 1,
    @SerializedName("schedule_type") val scheduleType: String,
    @SerializedName("timing_mode") val timingMode: String? = null,
    val timezone: String? = null,
    @SerializedName("effective_from") val effectiveFrom: String? = null,
    @SerializedName("effective_until") val effectiveUntil: String? = null,
    @SerializedName("miss_window_minutes") val missWindowMinutes: Int? = null,
    @SerializedName("fixed_times") val fixedTimes: List<FixedTime>? = null,
    val interval: IntervalPayload? = null
)
data class SchedulesResponse(val schedules: List<ScheduleVersionDto>)
data class FixedTime(
    @SerializedName("local_time") val localTime: String,
    @SerializedName("dose_quantity_value") val doseValue: String = "1",
    @SerializedName("dose_quantity_unit") val doseUnit: String = "tablet"
)
data class IntervalPayload(
    @SerializedName("interval_minutes") val intervalMinutes: Int,
    @SerializedName("anchor_at") val anchorAt: String
)
data class ScheduleResponse(val schedule: Any, val preview: List<OccurrenceDto>?)

object MedacDateUtils {
    private val DD_MM_YYYY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ISO_DATE = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

    fun normalizeToIsoDate(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        return try {
            java.time.LocalDate.parse(trimmed, ISO_DATE).format(ISO_DATE)
        } catch (_: Exception) {
            try {
                java.time.LocalDate.parse(trimmed, DD_MM_YYYY).format(ISO_DATE)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun computeEndDate(isoStartDate: String?, duration: String?): String? {
        if (isoStartDate.isNullOrBlank() || duration.isNullOrBlank()) return null
        val start = try { java.time.LocalDate.parse(isoStartDate, ISO_DATE) } catch (_: Exception) { return null }
        val cleanDuration = duration.lowercase().replace("days", "").replace("day", "").replace("∞", "").replace("ongoing", "").trim()
        val days = cleanDuration.toIntOrNull() ?: return null
        return start.plusDays(days.toLong()).format(ISO_DATE)
    }
}
data class OccurrenceDto(
    val id: String,
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("scheduled_at_utc") val scheduledAtUtc: String,
    @SerializedName("nominal_dose_value") val nominalDoseValue: String?,
    @SerializedName("nominal_dose_unit") val nominalDoseUnit: String?,
    val state: String,
    val timezone: String?
)
data class OccurrencesResponse(val occurrences: List<OccurrenceDto>, @SerializedName("next_cursor") val nextCursor: String?)

// ── Dose events ──
data class CreateDoseEventRequest(
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("occurrence_id") val occurrenceId: String?,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("actual_at") val actualAt: String,
    val timezone: String?,
    @SerializedName("dose_value") val doseValue: String? = null,
    @SerializedName("dose_unit") val doseUnit: String? = null,
    val note: String? = null,
    @SerializedName("client_event_id") val clientEventId: String
)
data class DoseEventDto(
    val id: String,
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("occurrence_id") val occurrenceId: String?,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("actual_at") val actualAt: String,
    @SerializedName("client_event_id") val clientEventId: String?
)
data class DoseEventResponse(val event: DoseEventDto, val outcome: String?)
data class DoseEventsResponse(val events: List<DoseEventDto>, @SerializedName("next_cursor") val nextCursor: String?)
data class BatchDoseRequest(val events: List<CreateDoseEventRequest>)
data class CorrectionRequest(
    @SerializedName("correction_type") val correctionType: String,
    val reason: String,
    @SerializedName("client_event_id") val clientEventId: String
)

// ── Alerts / Inventory / Reports ──
data class AlertDto(
    val id: String,
    @SerializedName("alert_type") val alertType: String,
    val severity: String,
    val status: String,
    @SerializedName("message_key") val messageKey: String?
)
data class AlertsResponse(val alerts: List<AlertDto>)

// Alert preferences §13
data class AlertPreferenceDto(
    val id: String,
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("alert_type") val alertType: String,
    val enabled: Boolean,
    @SerializedName("delay_minutes") val delayMinutes: Int?,
    @SerializedName("quiet_hours_start") val quietHoursStart: String?,
    @SerializedName("quiet_hours_end") val quietHoursEnd: String?,
    val timezone: String?
)
data class AlertPreferencesResponse(val preferences: List<AlertPreferenceDto>)
data class PutAlertPreferencesRequest(val preferences: List<PutAlertPreferenceItem>)
data class PutAlertPreferenceItem(
    @SerializedName("alert_type") val alertType: String,
    val enabled: Boolean,
    @SerializedName("delay_minutes") val delayMinutes: Int? = null,
    @SerializedName("quiet_hours_start") val quietHoursStart: String? = null,
    @SerializedName("quiet_hours_end") val quietHoursEnd: String? = null,
    val timezone: String? = null
)
data class GenerateAlertsResponse(val generated: GeneratedCounts)
data class GeneratedCounts(
    @SerializedName("low_stock") val lowStock: Int = 0,
    @SerializedName("expiration") val expiration: Int = 0,
    @SerializedName("stale_device") val staleDevice: Int = 0,
    val missed: Int = 0
)

data class InventoryResponse(
    val account: InventoryAccount?,
    val balance: Double?,
    @SerializedName("low_stock") val lowStock: Boolean?,
    @SerializedName("forecast_days") val forecastDays: Double?,
    @SerializedName("recent_transactions") val recentTransactions: List<InventoryTransactionDto>?
)
data class InventoryAccount(
    val id: String,
    val unit: String?,
    @SerializedName("low_stock_threshold_value") val lowStockThresholdValue: String?
)
data class InventoryTransactionDto(
    val id: String,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("quantity_delta") val quantityDelta: String,
    @SerializedName("effective_at") val effectiveAt: String?,
    val unit: String? = null,
    @SerializedName("reason_text") val reasonText: String? = null
)
data class InventoryTransactionsResponse(
    val transactions: List<InventoryTransactionDto>,
    @SerializedName("next_cursor") val nextCursor: String?
)
data class InventoryTxRequest(
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("quantity_delta") val quantityDelta: String,
    val unit: String,
    @SerializedName("reason_text") val reasonText: String? = null,
    @SerializedName("effective_at") val effectiveAt: String? = null
)
data class InventorySettingsRequest(
    @SerializedName("low_stock_threshold_value") val lowStockThresholdValue: String?,
    val unit: String? = null
)
data class InventorySettingsResponse(val account: InventoryAccount)
// Adherence report §14 (full)
data class AdherenceReportDto(
    @SerializedName("patient_id") val patientId: String,
    val from: String,
    val to: String,
    val timezone: String?,
    @SerializedName("eligible_scheduled_count") val eligibleScheduledCount: Int,
    @SerializedName("taken_count") val takenCount: Int,
    @SerializedName("skipped_count") val skippedCount: Int,
    @SerializedName("missed_count") val missedCount: Int,
    @SerializedName("pending_count") val pendingCount: Int,
    @SerializedName("excluded_count") val excludedCount: Int,
    @SerializedName("excluded_reasons") val excludedReasons: ExcludedReasons?,
    @SerializedName("adherence_percentage") val adherencePercentage: Double?,
    @SerializedName("denominator_definition") val denominatorDefinition: String?
)
data class ExcludedReasons(
    @SerializedName("prn_excluded") val prnExcluded: Int? = null,
    val cancelled: Int? = null,
    val paused: Int? = null,
    @SerializedName("medication_not_found") val medicationNotFound: Int? = null
)
// Keep old simple type for compat (unused after)
data class AdherenceResponse(
    @SerializedName("eligible_scheduled_count") val eligible: Int?,
    @SerializedName("taken_count") val taken: Int?,
    @SerializedName("adherence_percentage") val adherence: Double?
)

// ── Exports §15 ──
data class ExportDto(
    val id: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("download_token_expires_at") val downloadTokenExpiresAt: String?
)
data class CreateExportRequest(
    val from: String? = null,
    val to: String? = null,
    @SerializedName("include_symptoms") val includeSymptoms: Boolean? = null
)
data class CreateExportResponse(val export: ExportDto)
data class GetExportResponse(val export: ExportDto)

// ── Expirations ──
data class ExpirationDto(
    val id: String,
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("expiration_date") val expirationDate: String,
    @SerializedName("lot_number") val lotNumber: String?,
    @SerializedName("quantity_value") val quantityValue: String?,
    @SerializedName("quantity_unit") val quantityUnit: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)
data class ExpirationsResponse(val expirations: List<ExpirationDto>)
data class CreateExpirationRequest(
    @SerializedName("expiration_date") val expirationDate: String,
    @SerializedName("lot_number") val lotNumber: String? = null,
    @SerializedName("quantity_value") val quantityValue: String? = null,
    @SerializedName("quantity_unit") val quantityUnit: String? = null
)
data class UpdateExpirationRequest(
    @SerializedName("expiration_date") val expirationDate: String? = null,
    @SerializedName("lot_number") val lotNumber: String? = null,
    @SerializedName("quantity_value") val quantityValue: String? = null,
    @SerializedName("quantity_unit") val quantityUnit: String? = null
)

// ── Intelligence ──
data class LabelInterpretationRequest(
    @SerializedName("imageBase64") val imageBase64: String? = null,
    @SerializedName("image_base64") val imageBase64Alt: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("mime_type") val mimeTypeAlt: String? = null,
    @SerializedName("image") val imageAlt: String? = null,
    @SerializedName("ocrText") val ocrText: String? = null,
    @SerializedName("ocr_text") val ocrTextAlt: String? = null,
    val locale: String? = null
)
data class LabelInterpretationResponse(
    val interpretation: InterpretationDto? = null,
    @SerializedName("ai_run_id") val aiRunId: String?,
    val disclosure: String? = null,
    @SerializedName("requires_user_confirmation") val requiresUserConfirmation: Boolean? = null,
    val provider: String? = null,
    val model: String? = null,
    // Legacy alias now returned via intelligence route routes.ts:116 — server returns {success, medicine, interpretation, ai_run_id}
    val success: Boolean? = null,
    val medicine: AiMedicineDto? = null
)
data class AiMedicineDto(
    val name: String? = null,
    @SerializedName("genericName") val genericName: String? = null,
    @SerializedName("generic_name") val genericNameAlt: String? = null,
    val purpose: String? = null,
    val instructions: String? = null,
    @SerializedName("suggestedTimes") val suggestedTimes: List<String>? = null,
    @SerializedName("suggested_times") val suggestedTimesAlt: List<String>? = null,
    val form: String? = null,
    @SerializedName("imageConfidence") val imageConfidence: Double? = null,
    @SerializedName("image_confidence") val imageConfidenceAlt: Double? = null,
    @SerializedName("ocrConfidence") val ocrConfidence: Double? = null,
    @SerializedName("ocr_confidence") val ocrConfidenceAlt: Double? = null
)
data class InterpretationDto(
    @SerializedName("observed_text") val observedText: String? = null,
    @SerializedName("candidate_name") val candidateName: String?,
    @SerializedName("candidate_generic_name") val candidateGenericName: String? = null,
    @SerializedName("strength_value") val strengthValue: String?,
    @SerializedName("strength_unit") val strengthUnit: String?,
    val form: String?,
    val route: String?,
    @SerializedName("label_directions_text") val labelDirectionsText: String?,
    @SerializedName("prescriber_fields") val prescriberFields: Map<String,String>? = null,
    @SerializedName("uncertain_fields") val uncertainFields: List<String>?,
    @SerializedName("clarifying_questions") val clarifyingQuestions: List<String>?,
    val evidence: List<EvidenceDto>? = null,
    val confidence: Double?
)
data class EvidenceDto(
    val field: String?,
    val span: String?,
    val confidence: Double?
)

// ── Intelligence: Instruction parse §16.2 ──
data class InstructionParseRequest(
    val text: String,
    val locale: String? = null
)
data class InstructionParseResponse(
    val parse: InstructionParseDto,
    @SerializedName("ai_run_id") val aiRunId: String?,
    @SerializedName("requires_user_confirmation") val requiresUserConfirmation: Boolean? = null,
    val disclosure: String? = null
)
data class InstructionParseDto(
    @SerializedName("entered_text") val enteredText: String? = null,
    @SerializedName("dose_value") val doseValue: String? = null,
    @SerializedName("dose_unit") val doseUnit: String? = null,
    @SerializedName("frequency_text") val frequencyText: String? = null,
    val route: String? = null,
    @SerializedName("uncertain_fields") val uncertainFields: List<String>? = null,
    @SerializedName("clarifying_questions") val clarifyingQuestions: List<String>? = null,
    val evidence: List<EvidenceDto>? = null,
    val confidence: Double? = null
)

// ── Intelligence: Conversations & Messages §16.3 ──
data class CreateConversationRequest(
    val title: String? = null
)
data class CreateConversationResponse(
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("patient_id") val patientId: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null
)
data class CreateMessageRequest(
    val content: String,
    val role: String? = "user"
)
data class CreateMessageResponse(
    val message: String,
    @SerializedName("facts_used") val factsUsed: List<FactUsedDto>? = null,
    val proposals: List<ProposalRefDto>? = null,
    val limitations: List<String>? = null,
    @SerializedName("ai_run_id") val aiRunId: String? = null,
    @SerializedName("turn_count") val turnCount: Int? = null
)
data class FactUsedDto(
    val type: String,
    val id: String,
    val version: Int? = null
)
data class ProposalRefDto(
    @SerializedName("proposal_id") val proposalId: String,
    @SerializedName("action_type") val actionType: String? = null,
    @SerializedName("requires_confirmation") val requiresConfirmation: Boolean? = null,
    @SerializedName("expires_at") val expiresAt: String? = null
)

// ── Intelligence: Schedule drafts §16.4 ──
data class ScheduleDraftRequest(
    @SerializedName("medicationId") val medicationId: String? = null,
    val constraints: String? = null,
    val timezone: String? = null,
    @SerializedName("wakeTime") val wakeTime: String? = null,
    @SerializedName("sleepTime") val sleepTime: String? = null
)
data class ScheduleDraftResponse(
    val draft: ScheduleDraftDto,
    @SerializedName("ai_run_id") val aiRunId: String?,
    @SerializedName("requires_user_confirmation") val requiresUserConfirmation: Boolean? = null,
    val limitations: List<String>? = null
)
data class ScheduleDraftDto(
    val times: List<String>? = null,
    @SerializedName("preview") val preview: List<OccurrenceDto>? = null,
    @SerializedName("human_summary") val humanSummary: String? = null,
    val constraints: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
    // flexible: server may return map; capture raw via extra
    @SerializedName("draft_times") val draftTimes: List<String>? = null
)

// ── Intelligence: Summaries §16.5 ──
data class SummaryRequest(
    val type: String,
    val from: String? = null,
    val to: String? = null
)
data class SummaryResponse(
    val summary: Any?,
    @SerializedName("ai_run_id") val aiRunId: String? = null
) {
    val summaryText: String get() = when (summary) {
        is String -> summary
        else -> summary?.toString() ?: ""
    }
}

// ── Intelligence: Proposals §16.6 ──
data class ProposalDto(
    val id: String,
    @SerializedName("action_type") val actionType: String?,
    val status: String?,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("required_permission") val requiredPermission: String? = null,
    @SerializedName("resource_versions") val resourceVersions: Map<String, Int>? = null,
    @SerializedName("human_summary") val humanSummary: String? = null,
    @SerializedName("payload_hash") val payloadHash: String? = null,
    @SerializedName("executed_at") val executedAt: String? = null
)
data class ProposalEnvelope(
    val proposal: ProposalDto
)
data class ConfirmProposalRequest(
    @SerializedName("payload_hash") val payloadHash: String,
    @SerializedName("expected_resource_versions") val expectedResourceVersions: Map<String, Int>? = null
)
data class SyncResponse(val changes: List<SyncChange>, @SerializedName("next_cursor") val nextCursor: String?)
data class SyncChange(val type: String, val id: String, @SerializedName("updated_at") val updatedAt: String)

// ── Symptoms (§11) ──
data class SymptomLogDto(
    val id: String,
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("occurred_at") val occurredAt: String,
    val note: String?,
    @SerializedName("linked_dose_event_id") val linkedDoseEventId: String?,
    @SerializedName("actor_user_id") val actorUserId: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("corrected_by_log_id") val correctedByLogId: String?
)
data class SymptomLogsResponse(
    val logs: List<SymptomLogDto>,
    @SerializedName("next_cursor") val nextCursor: String?
)
data class CreateSymptomRequest(
    @SerializedName("occurred_at") val occurredAt: String,
    val note: String? = null,
    @SerializedName("linked_dose_event_id") val linkedDoseEventId: String? = null
)
data class CorrectionSymptomRequest(
    val note: String? = null,
    val reason: String? = null
)

// ── Injection sites (§12) ──
data class InjectionLogDto(
    val id: String,
    @SerializedName("patient_id") val patientId: String,
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("dose_event_id") val doseEventId: String?,
    @SerializedName("site_code") val siteCode: String,
    @SerializedName("occurred_at") val occurredAt: String,
    @SerializedName("actor_user_id") val actorUserId: String?,
    @SerializedName("created_at") val createdAt: String?
)
data class InjectionLogsResponse(val logs: List<InjectionLogDto>)
data class CreateInjectionRequest(
    @SerializedName("medication_id") val medicationId: String,
    @SerializedName("dose_event_id") val doseEventId: String? = null,
    @SerializedName("site_code") val siteCode: String,
    @SerializedName("occurred_at") val occurredAt: String
)

// ── Patients extended (§4.2-4.4) ──
data class PatientPatchRequest(
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("preferred_timezone") val preferredTimezone: String? = null,
    @SerializedName("notification_privacy_mode") val notificationPrivacyMode: String? = null
)
data class MembershipDto(
    val id: String,
    @SerializedName("patient_id") val patientId: String?,
    @SerializedName("patientId") val patientIdAlt: String? = null,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("userId") val userIdAlt: String? = null,
    val role: String,
    val status: String?,
    @SerializedName("granted_by_user_id") val grantedByUserId: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("revoked_at") val revokedAt: String?
) { val effectivePatientId: String get() = patientId ?: patientIdAlt ?: ""; val effectiveUserId: String get() = userId ?: userIdAlt ?: "" }
data class MembershipsResponse(val memberships: List<MembershipDto>)
data class UpdateMembershipRequest(val role: String)
data class InviteDto(
    val id: String,
    @SerializedName("invited_email_normalized") val invitedEmailNormalized: String?,
    val role: String?,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("accepted_at") val acceptedAt: String?,
    @SerializedName("revoked_at") val revokedAt: String?,
    @SerializedName("created_at") val createdAt: String?
)
data class InvitesResponse(val invites: List<InviteDto>)
data class CreateInviteRequest(val email: String, val role: String)
data class CreateInviteResponse(@SerializedName("invite_id") val inviteId: String)
data class AcceptInviteResponse(@SerializedName("patient_id") val patientId: String)

// ── Auth extended (§3.7-3.10) ──
data class LogoutAllResponse(val ok: Boolean? = null)
data class PasswordResetRequest(val token: String, @SerializedName("new_password") val newPassword: String)
data class PasswordChangeRequest(@SerializedName("current_password") val currentPassword: String, @SerializedName("new_password") val newPassword: String)
data class SessionDto(
    val id: String,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("last_used_at") val lastUsedAt: String?,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("revoked_at") val revokedAt: String?,
    @SerializedName("user_agent_summary") val userAgentSummary: String?
)
data class SessionsResponse(val sessions: List<SessionDto>)
data class TotpSetupResponse(@SerializedName("secret_base32") val secretBase32: String, @SerializedName("otpauth_url") val otpauthUrl: String)
data class ConfirmTotpRequest(val code: String)
data class ConfirmTotpResponse(@SerializedName("recovery_codes") val recoveryCodes: List<String>)

// ── Drug normalization §6 ──
data class DrugSearchResponse(
    @SerializedName("concepts") val concepts: List<DrugConceptDto>? = null,
    @SerializedName("results") val results: List<DrugConceptDto>? = null,
    @SerializedName("items") val items: List<DrugConceptDto>? = null,
    @SerializedName("drugGroup") val drugGroup: Any? = null,
    @SerializedName("conceptGroup") val conceptGroup: Any? = null
) {
    fun allConcepts(): List<DrugConceptDto> = concepts ?: results ?: items ?: emptyList()
}
data class DrugConceptDto(
    @SerializedName("rxcui") val rxcui: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("synonym") val synonym: String? = null,
    @SerializedName("tty") val tty: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("suppress") val suppress: String? = null,
    @SerializedName("umlscui") val umlscui: String? = null,
    val strength: String? = null
) {
    val effectiveName: String get() = name ?: displayName ?: synonym ?: rxcui ?: ""
}
data class ResolveNdcRequest(@SerializedName("ndc") val ndc: String)
data class ResolveNdcResponse(
    val concept: ConceptDto? = null,
    @SerializedName("rxcui") val rxcui: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("drug_concept_id") val drugConceptId: String? = null,
    val status: String? = null
) {
    fun resolved(): ConceptDto? = concept ?: if (rxcui != null) ConceptDto(rxcui, name) else null
}
data class ConceptDto(
    @SerializedName("rxcui") val rxcui: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("synonym") val synonym: String? = null,
    @SerializedName("tty") val tty: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("strength") val strength: String? = null,
    @SerializedName("form") val form: String? = null,
    @SerializedName("route") val route: String? = null,
    @SerializedName("display_name") val displayName: String? = null
) {
    val effectiveName: String get() = name ?: displayName ?: synonym ?: rxcui
}

// ── Health & meta §2 ──
data class HealthResponse(
    val status: String,
    val service: String? = null
)
data class VersionResponse(
    val version: String? = null,
    @SerializedName("build_id") val buildId: String? = null,
    @SerializedName("buildId") val buildIdAlt: String? = null,
    val env: String? = null
) {
    val effectiveBuildId: String? get() = buildId ?: buildIdAlt
}
data class HealthStatusCombined(
    val live: HealthResponse? = null,
    val ready: HealthResponse? = null,
    val lastCheckedAt: String? = null
) {
    val liveOk: Boolean get() = live?.status == "ok"
    val readyOk: Boolean get() = ready?.status == "ready"
}
