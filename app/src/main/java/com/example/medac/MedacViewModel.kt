package com.example.medac

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medac.data.MedacRepository
import com.example.medac.data.ScanSettings
import com.example.medac.data.ScanStrategy
import com.example.medac.data.TokenStore
import com.google.gson.Gson
import com.example.medac.ocr.GoogleLensOcrClient
import com.example.medac.ocr.LensOcrResult
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class IntelligenceChatItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    /** True while tokens are still arriving for this bubble. */
    val isStreaming: Boolean = false,
    val factsUsed: List<com.example.medac.data.FactUsedDto> = emptyList(),
    val proposals: List<com.example.medac.data.ProposalRefDto> = emptyList(),
    val limitations: List<String> = emptyList(),
    val aiRunId: String? = null,
    val turnCount: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class MedacViewModel : ViewModel() {
    private val gson = Gson()

    private val doseLogsPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "entries") {
            loadDoseLogs()
        }
    }

    fun refreshLocalDoseLogs() {
        loadDoseLogs()
    }

    // ── Local cache (Room is source of truth via MedacRepository, but keep in-memory for Compose) ──
    private val _medicines = MutableStateFlow<List<ManagedMedicine>>(emptyList())
    val medicines: StateFlow<List<ManagedMedicine>> = _medicines
    private val _serverMeds = MutableStateFlow<List<com.example.medac.data.MedicationDto>>(emptyList())
    val serverMeds: StateFlow<List<com.example.medac.data.MedicationDto>> = _serverMeds

    private val _pausedReminderKeys = MutableStateFlow<Set<String>>(emptySet())
    val pausedReminderKeys: StateFlow<Set<String>> = _pausedReminderKeys

    private val _draft = MutableStateFlow(MedicineDraft())
    val draft: StateFlow<MedicineDraft> = _draft

    private val _isAnalyzingPhoto = MutableStateFlow(false)
    val isAnalyzingPhoto: StateFlow<Boolean> = _isAnalyzingPhoto

    private val _prescriptionImport = MutableStateFlow(PrescriptionImportState())
    val prescriptionImport: StateFlow<PrescriptionImportState> = _prescriptionImport

    private val _doseLogs = MutableStateFlow<List<DoseLogEntry>>(emptyList())
    val doseLogs: StateFlow<List<DoseLogEntry>> = _doseLogs

    private val _aiIdentifying = MutableStateFlow(false)
    val aiIdentifying: StateFlow<Boolean> = _aiIdentifying
    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError

    /** Default label-scan path (vision vs Google Lens OCR); persisted in ScanSettings. */
    private val _scanVisionDefault = MutableStateFlow(ScanSettings.DEFAULT_VISION)
    val scanVisionDefault: StateFlow<Boolean> = _scanVisionDefault

    // ── Patient-scoped state per §2.2 ──
    private val _patients = MutableStateFlow<List<PatientChip>>(emptyList())
    val patients: StateFlow<List<PatientChip>> = _patients
    private val _activePatientId = MutableStateFlow<String?>(null)
    val activePatientId: StateFlow<String?> = _activePatientId
    private val _alerts = MutableStateFlow<List<AlertItem>>(emptyList())
    val alerts: StateFlow<List<AlertItem>> = _alerts
    private val _occurrences = MutableStateFlow<List<com.example.medac.data.OccurrenceDto>>(emptyList())
    val occurrences: StateFlow<List<com.example.medac.data.OccurrenceDto>> = _occurrences
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _quietHours = MutableStateFlow("22:00" to "07:00")
    val quietHours: StateFlow<Pair<String, String>> = _quietHours

    private val _inventories = MutableStateFlow<Map<String, com.example.medac.data.InventoryResponse?>>(emptyMap())
    val inventories: StateFlow<Map<String, com.example.medac.data.InventoryResponse?>> = _inventories
    private val _inventoryTransactions = MutableStateFlow<Map<String, List<com.example.medac.data.InventoryTransactionDto>>>(emptyMap())
    val inventoryTransactions: StateFlow<Map<String, List<com.example.medac.data.InventoryTransactionDto>>> = _inventoryTransactions
    private val _expirations = MutableStateFlow<Map<String, List<com.example.medac.data.ExpirationDto>>>(emptyMap())
    val expirations: StateFlow<Map<String, List<com.example.medac.data.ExpirationDto>>> = _expirations

    private val _symptomLogs = MutableStateFlow<List<com.example.medac.data.SymptomLogDto>>(emptyList())
    val symptomLogs: StateFlow<List<com.example.medac.data.SymptomLogDto>> = _symptomLogs
    private val _injectionLogs = MutableStateFlow<Map<String, List<com.example.medac.data.InjectionLogDto>>>(emptyMap())
    val injectionLogs: StateFlow<Map<String, List<com.example.medac.data.InjectionLogDto>>> = _injectionLogs
    private var _symptomCursor: String? = null

    // ── Alerts preferences / generate / adherence / exports §13-15 ──
    private val _alertPreferences = MutableStateFlow<List<com.example.medac.data.AlertPreferenceDto>>(emptyList())
    val alertPreferences: StateFlow<List<com.example.medac.data.AlertPreferenceDto>> = _alertPreferences
    private val _isLoadingPrefs = MutableStateFlow(false)
    val isLoadingPrefs: StateFlow<Boolean> = _isLoadingPrefs
    private val _adherenceReport = MutableStateFlow<com.example.medac.data.AdherenceReportDto?>(null)
    val adherenceReport: StateFlow<com.example.medac.data.AdherenceReportDto?> = _adherenceReport
    private val _isLoadingAdherence = MutableStateFlow(false)
    val isLoadingAdherence: StateFlow<Boolean> = _isLoadingAdherence
    private val _exports = MutableStateFlow<List<com.example.medac.data.ExportDto>>(emptyList())
    val exports: StateFlow<List<com.example.medac.data.ExportDto>> = _exports
    private val _generateResult = MutableStateFlow<com.example.medac.data.GeneratedCounts?>(null)
    val generateResult: StateFlow<com.example.medac.data.GeneratedCounts?> = _generateResult
    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError

    // ── Patients extended §4.2-4.4 ──
    private val _currentPatient = MutableStateFlow<com.example.medac.data.PatientDto?>(null)
    val currentPatient: StateFlow<com.example.medac.data.PatientDto?> = _currentPatient
    private val _memberships = MutableStateFlow<List<com.example.medac.data.MembershipDto>>(emptyList())
    val memberships: StateFlow<List<com.example.medac.data.MembershipDto>> = _memberships
    private val _invites = MutableStateFlow<List<com.example.medac.data.InviteDto>>(emptyList())
    val invites: StateFlow<List<com.example.medac.data.InviteDto>> = _invites
    private val _patientActionMsg = MutableStateFlow<String?>(null)
    val patientActionMsg: StateFlow<String?> = _patientActionMsg
    private val _patientError = MutableStateFlow<String?>(null)
    val patientError: StateFlow<String?> = _patientError

    // ── Intelligence §16.2-16.6 ──
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId
    private val _intelligenceMessages = MutableStateFlow<List<IntelligenceChatItem>>(emptyList())
    val intelligenceMessages: StateFlow<List<IntelligenceChatItem>> = _intelligenceMessages
    private val _intelligenceProposals = MutableStateFlow<Map<String, com.example.medac.data.ProposalDto>>(emptyMap())
    val intelligenceProposals: StateFlow<Map<String, com.example.medac.data.ProposalDto>> = _intelligenceProposals
    private val _scheduleDraft = MutableStateFlow<com.example.medac.data.ScheduleDraftResponse?>(null)
    val scheduleDraft: StateFlow<com.example.medac.data.ScheduleDraftResponse?> = _scheduleDraft
    private val _summary = MutableStateFlow<com.example.medac.data.SummaryResponse?>(null)
    val summary: StateFlow<com.example.medac.data.SummaryResponse?> = _summary
    private val _instructionParse = MutableStateFlow<com.example.medac.data.InstructionParseResponse?>(null)
    val instructionParse: StateFlow<com.example.medac.data.InstructionParseResponse?> = _instructionParse
    private val _isIntelligenceLoading = MutableStateFlow(false)
    val isIntelligenceLoading: StateFlow<Boolean> = _isIntelligenceLoading
    private val _intelligenceError = MutableStateFlow<String?>(null)
    val intelligenceError: StateFlow<String?> = _intelligenceError
    private val _proposalActionMsg = MutableStateFlow<String?>(null)
    val proposalActionMsg: StateFlow<String?> = _proposalActionMsg

    // ── Drug normalization §6 + Health §2 ──
    private val _drugSearchResults = MutableStateFlow<List<com.example.medac.data.DrugConceptDto>>(emptyList())
    val drugSearchResults: StateFlow<List<com.example.medac.data.DrugConceptDto>> = _drugSearchResults
    private val _selectedConcept = MutableStateFlow<com.example.medac.data.ConceptDto?>(null)
    val selectedConcept: StateFlow<com.example.medac.data.ConceptDto?> = _selectedConcept
    private val _healthStatus = MutableStateFlow<com.example.medac.data.HealthStatusCombined?>(null)
    val healthStatus: StateFlow<com.example.medac.data.HealthStatusCombined?> = _healthStatus
    private val _versionInfo = MutableStateFlow<com.example.medac.data.VersionResponse?>(null)
    val versionInfo: StateFlow<com.example.medac.data.VersionResponse?> = _versionInfo
    private val _isDrugSearching = MutableStateFlow(false)
    val isDrugSearching: StateFlow<Boolean> = _isDrugSearching
    private val _isHealthLoading = MutableStateFlow(false)
    val isHealthLoading: StateFlow<Boolean> = _isHealthLoading
    private val _drugSearchError = MutableStateFlow<String?>(null)
    val drugSearchError: StateFlow<String?> = _drugSearchError
    private val _conceptLoading = MutableStateFlow(false)
    val conceptLoading: StateFlow<Boolean> = _conceptLoading
    private var drugSearchJob: kotlinx.coroutines.Job? = null

    private var appContext: Context? = null

    fun load(context: Context) {
        val appCtx = context.applicationContext
        if (appContext == null) {
            appContext = appCtx
            loadMedicines()
            loadPausedReminders()
            loadDoseLogs()
            loadQuietHours()
            _scanVisionDefault.value = ScanSettings.isVisionDefault(appCtx)
            rescheduleAllActiveReminders()
            try {
                appCtx.getSharedPreferences("dose_logs", Context.MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(doseLogsPrefsListener)
            } catch (_: Exception) {}
        } else {
            loadDoseLogs()
        }
        // try server refresh best-effort (offline-first)
        viewModelScope.launch(Dispatchers.IO) {
            try { refreshServer(context) } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) { /* data error, not offline */ }
        }
    }

    suspend fun refreshServer(context: Context) {
        if (!TokenStore.isLoggedIn(context)) {
            android.util.Log.w("MedacSync", "refreshServer: not logged in")
            return
        }
        _isSyncing.value = true
        try {
            android.util.Log.d("MedacSync", "refreshServer: ensurePatient start")
            // ensure patient exists per §5.1 (first run)
            val pid = MedacRepository.ensurePatient(context)
            android.util.Log.d("MedacSync", "refreshServer: pid=$pid")
            _activePatientId.value = pid
            // patients for switcher
            android.util.Log.d("MedacSync", "refreshServer: listPatients")
            val plist = MedacRepository.listPatients(context)
            _patients.value = plist.map { PatientChip(it.id, it.displayName?.ifBlank { null } ?: "My plan", "owner") }
            android.util.Log.d("MedacSync", "refreshServer: patients=${plist.size}")
            // incremental sync
            android.util.Log.d("MedacSync", "refreshServer: incrementalSync")
            MedacRepository.incrementalSync(context, pid)
            android.util.Log.d("MedacSync", "refreshServer: syncPendingDoses")
            MedacRepository.syncPendingDoses(context, pid)
            // refresh domain
            android.util.Log.d("MedacSync", "refreshServer: refreshMedications")
            val medDtos = MedacRepository.refreshMedications(context, pid)
            _serverMeds.value = medDtos
            android.util.Log.d("MedacSync", "refreshServer: meds=${medDtos.size}")
            if (medDtos.isNotEmpty()) {
                // Merge (never wipe): preserve local schedule times, photos and
                // stable ids so alarms + dose logs survive cloud sync.
                val existingByName = _medicines.value.associateBy { it.name.lowercase(Locale.getDefault()) }
                _medicines.value = medDtos.map { dto ->
                    val existing = existingByName[dto.enteredName.lowercase(Locale.getDefault())]
                    val fallbackTimes = if (existing == null || existing.times.isEmpty()) {
                        try {
                            val schedules = MedacRepository.listSchedules(context, pid, dto.id)
                            val activeVersion = schedules.firstOrNull { it.effectiveUntil == null } ?: schedules.lastOrNull()
                            val timesFromActive = activeVersion?.fixedTimes?.map { it.localTime }.orEmpty()
                            if (timesFromActive.isNotEmpty()) {
                                timesFromActive
                            } else {
                                schedules.flatMap { it.fixedTimes.orEmpty().map { ft -> ft.localTime } }
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    medicationDtoToManaged(dto, existing, fallbackTimes)
                }
                persistMedicines()
            }
            // occurrences for Today
            val from = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
            val to = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
            android.util.Log.d("MedacSync", "refreshServer: occurrences from=$from to=$to")
            _occurrences.value = MedacRepository.occurrences(context, pid, from, to)
            android.util.Log.d("MedacSync", "refreshServer: occurrences=${_occurrences.value.size}")
            // Backfill any still-empty schedules from today's occurrences, then
            // (re)schedule alarms so reboot/reschedule sees real times.
            if (backfillTimesFromOccurrences()) {
                persistMedicines()
            }
            try { rescheduleAllActiveReminders() } catch (_: Exception) {}
            // alerts
            android.util.Log.d("MedacSync", "refreshServer: openAlerts")
            val alertsDto = MedacRepository.openAlerts(context, pid)
            _alerts.value = alertsDto.map {
                val msg = when (it.severity) {
                    "attention" -> it.messageKey ?: "Attention: ${it.alertType}"
                    "urgent_review" -> it.messageKey ?: "Urgent: ${it.alertType}"
                    "potential_emergency" -> it.messageKey ?: "Emergency: ${it.alertType}"
                    else -> it.messageKey ?: it.alertType
                }
                AlertItem(it.id, it.severity, msg)
            }
            android.util.Log.d("MedacSync", "refreshServer: alerts=${_alerts.value.size} -> offline=false")
            // dose logs from server
            _isOffline.value = false
        } catch (e: java.io.IOException) {
            android.util.Log.e("MedacSync", "refreshServer network failed: ${e.message}", e)
            _isOffline.value = true
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val isNetwork = msg.contains("Unable to resolve", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("Network", ignoreCase = true) ||
                    e is java.net.UnknownHostException ||
                    e is java.net.ConnectException ||
                    e is java.net.SocketTimeoutException
            if (isNetwork) {
                android.util.Log.e("MedacSync", "refreshServer network failed: ${e.message}", e)
                _isOffline.value = true
            } else {
                // Programming / data error — don't show offline banner, keep previous offline state or false
                android.util.Log.e("MedacSync", "refreshServer data error (not offline): ${e.message}", e)
                // keep _isOffline as false if we had at least patients success, else leave as is
                if (_patients.value.isEmpty()) {
                    // don't mask as offline; just log
                }
            }
        } finally { _isSyncing.value = false }
    }

    fun switchPatient(pid: String) {
        _activePatientId.value = pid
        appContext?.let { ctx ->
            viewModelScope.launch { try { refreshServer(ctx) } catch (_: Exception) {} }
        }
    }

    // ── Medicine status machine (§4.3) — optimistic local, best-effort server ──
    fun setMedicineStatus(medicine: ManagedMedicine, newStatus: String) {
        val ctx = appContext ?: return
        if (newStatus == "paused" || newStatus == "discontinued") {
            medicine.times.forEach { cancelMedicineReminder(ctx, medicine.name, it) }
        }
        _medicines.value = _medicines.value.map { if (it.id == medicine.id) it.copy(status = newStatus) else it }
        persistMedicines()
        if (newStatus == "active") {
            medicine.times.forEach { time -> if (!isReminderPaused(medicine.name, time)) scheduleMedicineReminder(ctx, medicine.name, time) }
        }
        val pid = _activePatientId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val meds = MedacRepository.refreshMedications(ctx, pid)
                val target = meds.firstOrNull { it.enteredName.equals(medicine.name, true) } ?: return@launch
                val api = com.example.medac.data.NetworkModule.provideApi(ctx)
                when (newStatus) {
                    "paused", "discontinued" -> api.pauseMedication(pid, target.id)
                    "active" -> api.resumeMedication(pid, target.id)
                }
            } catch (_: Exception) {}
        }
    }

    fun setHighAttention(medicine: ManagedMedicine, value: Boolean) {
        _medicines.value = _medicines.value.map { if (it.id == medicine.id) it.copy(highAttention = value) else it }
        persistMedicines()
        val pid = _activePatientId.value; val ctx = appContext
        if (pid == null || ctx == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val meds = MedacRepository.refreshMedications(ctx, pid)
                val target = meds.firstOrNull { it.enteredName.equals(medicine.name, true) } ?: return@launch
                MedacRepository.updateMedication(ctx, pid, target.id, com.example.medac.data.UpdateMedicationRequest(highAttention = value))
            } catch (_: Exception) {}
        }
    }

    fun setQuietHours(start: String, end: String) {
        if (!isValidReminderTime(start) || !isValidReminderTime(end)) return
        _quietHours.value = start to end
        val ctx = appContext ?: return
        ctx.getSharedPreferences("reminder_state", Context.MODE_PRIVATE).edit().putString("quiet_hours", "$start,$end").apply()
    }

    // ── Dose note (§4.2 "Add note" — linked to the dose event) ──
    fun addDoseNote(medicineId: Long, medicineName: String, time: String, note: String) {
        val date = todayStr(); val key = doseLogKey(medicineId, date, time)
        val existing = _doseLogs.value.firstOrNull { it.id == key }
        val entry = (existing ?: DoseLogEntry(id = key, medicineId = medicineId, medicineName = medicineName, time = time, date = date, status = "PENDING"))
            .copy(note = note.trim(), updatedAt = System.currentTimeMillis())
        _doseLogs.value = listOf(entry) + _doseLogs.value.filterNot { it.id == key }
        persistDoseLogs()
    }

    // ── Draft edits ──
    fun updateDraft(
        name: String? = null,
        genericNameAndDose: String? = null,
        purpose: String? = null,
        form: String? = null,
        foodTiming: String? = null,
        instruction: String? = null,
        frequency: String? = null,
        duration: String? = null,
        startDate: String? = null,
        remindersEnabled: Boolean? = null,
        refillTrackingEnabled: Boolean? = null,
        notes: String? = null,
        currentSupply: Int? = null,
        refillThresholdPercent: Int? = null
    ) {
        _draft.value = _draft.value.copy(
            name = name ?: _draft.value.name,
            genericNameAndDose = genericNameAndDose ?: _draft.value.genericNameAndDose,
            purpose = purpose ?: _draft.value.purpose,
            form = form ?: _draft.value.form,
            foodTiming = foodTiming ?: _draft.value.foodTiming,
            instruction = instruction ?: _draft.value.instruction,
            frequency = frequency ?: _draft.value.frequency,
            duration = duration ?: _draft.value.duration,
            startDate = startDate ?: _draft.value.startDate,
            remindersEnabled = remindersEnabled ?: _draft.value.remindersEnabled,
            refillTrackingEnabled = refillTrackingEnabled ?: _draft.value.refillTrackingEnabled,
            notes = notes ?: _draft.value.notes,
            currentSupply = currentSupply ?: _draft.value.currentSupply,
            refillThresholdPercent = refillThresholdPercent ?: _draft.value.refillThresholdPercent
        )
    }
    fun updateDraftTimes(times: List<String>) { _draft.value = _draft.value.copy(times = times.sorted()) }
    fun useScanPhotoAsCardImage() {
        val photoUri = _draft.value.photoUri ?: return
        _draft.value = _draft.value.copy(cardImageUri = photoUri.toString(), detectedSummary = "Using the scanned photo as the reminder card image.")
    }
    fun clearDraft() { _draft.value = MedicineDraft() }
    fun hasUnsavedDraft(): Boolean {
        val d = _draft.value
        return d.photoUri != null || d.detectedSummary.isNotBlank() || d.detectedText.isNotBlank() || d.name.isNotBlank() || d.genericNameAndDose.isNotBlank() || d.purpose.isNotBlank() || d.times.isNotEmpty() || d.form.isNotBlank() || d.foodTiming.isNotBlank() || d.instruction.isNotBlank()
    }
    fun addReminderTime(time: String) { if (!isValidReminderTime(time)) return; updateDraftTimes((_draft.value.times + time).distinct()) }
    fun replaceReminderTime(index: Int, time: String) { if (!isValidReminderTime(time)) return; val m = _draft.value.times.toMutableList(); if (index !in m.indices) return; m[index]=time; updateDraftTimes(m.distinct()) }
    fun removeReminderTime(index: Int) { val m=_draft.value.times.toMutableList(); if(index !in m.indices) return; m.removeAt(index); updateDraftTimes(m) }
    fun updateReminderTime(entry: ReminderEntry, newTime: String) {
        if (!isValidReminderTime(newTime) || newTime == entry.time) return
        val med = _medicines.value.firstOrNull { it.name.equals(entry.medicineName, ignoreCase = true) && it.times.contains(entry.time) } ?: return
        val updated = med.copy(times = (med.times - entry.time + newTime).distinct().sorted())
        updateMedicine(updated)
    }

    fun ensureLocalPhotoUri(context: Context, uri: Uri): Uri {
        return try {
            val dir = File(context.filesDir, "medicine_photos").apply { mkdirs() }
            val path = uri.path.orEmpty()
            if (path.contains("medicine_photos") && dir.listFiles()?.any { it.name == uri.lastPathSegment } == true) {
                return uri
            }
            val file = File(dir, "medicine_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            android.util.Log.w("MedacPhoto", "Failed to copy photo locally: ${e.message}")
            uri
        }
    }

    // ── Google Lens OCR ──
    fun analyzeMedicinePhoto(uri: Uri, context: Context) {
        val appCtx = context.applicationContext
        val localPhotoUri = ensureLocalPhotoUri(appCtx, uri)
        val photoUriStr = localPhotoUri.toString()
        _draft.value = _draft.value.copy(
            photoUri = localPhotoUri,
            cardImageUri = photoUriStr,
            detectedSummary = "Reading the label with Google Lens...",
            detectedText = "",
            name = "",
            genericNameAndDose = "",
            purpose = "",
            times = emptyList(),
            isAiEnhanced = false
        )
        _isAnalyzingPhoto.value = true
        viewModelScope.launch {
            try {
                val ocrResult = GoogleLensOcrClient.recognizeText(appCtx, localPhotoUri)
                val ocrText = ocrResult.fullText.trim()
                if (ocrText.isBlank()) {
                    _isAnalyzingPhoto.value = false
                    _draft.value = _draft.value.copy(
                        photoUri = localPhotoUri,
                        cardImageUri = photoUriStr,
                        detectedSummary = "The label photo was captured, but the text could not be read clearly.",
                        detectedText = "",
                        name = "",
                        genericNameAndDose = "",
                        purpose = "",
                        times = emptyList(),
                        isAiEnhanced = false
                    )
                } else {
                    _draft.value = _draft.value.copy(
                        photoUri = localPhotoUri,
                        cardImageUri = photoUriStr,
                        detectedSummary = "OCR complete. Review the detected details below.",
                        detectedText = ocrText,
                        isAiEnhanced = false
                    )
                    _isAnalyzingPhoto.value = false
                    applyFallbackDraft(localPhotoUri, ocrText)
                }
            } catch (e: Exception) {
                android.util.Log.e("MedacOCR", "Google Lens OCR failed: ${e.message}", e)
                _isAnalyzingPhoto.value = false
                _draft.value = _draft.value.copy(
                    photoUri = localPhotoUri,
                    cardImageUri = photoUriStr,
                    detectedSummary = "The label photo was captured, but the text could not be read clearly.",
                    detectedText = "",
                    name = "",
                    genericNameAndDose = "",
                    purpose = "",
                    times = emptyList(),
                    isAiEnhanced = false
                )
            }
        }
    }

    fun analyzePrescriptionPhoto(uri: Uri, context: Context) {
        _isAnalyzingPhoto.value = true
        _prescriptionImport.value = PrescriptionImportState(
            status = "Reading the prescription with Google Lens...",
            sourceUri = uri
        )
        viewModelScope.launch {
            try {
                val ocrResult = GoogleLensOcrClient.recognizeText(context.applicationContext, uri)
                _isAnalyzingPhoto.value = false
                val ocrText = ocrResult.fullText.trim()
                if (ocrText.isBlank()) {
                    _prescriptionImport.value = PrescriptionImportState(
                        status = "No readable medicine text was found in the prescription photo.",
                        sourceUri = uri
                    )
                } else {
                    val meds = buildPrescriptionMedicines(ocrText, uri)
                    _prescriptionImport.value = PrescriptionImportState(
                        medicines = meds,
                        status = if (meds.isEmpty()) "Text was detected, but no medicine list could be created. Try a clearer, flatter photo." else "Review the medicines detected from the prescription.",
                        sourceUri = uri
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MedacOCR", "Google Lens prescription OCR failed: ${e.message}", e)
                _isAnalyzingPhoto.value = false
                _prescriptionImport.value = PrescriptionImportState(
                    status = "The prescription photo could not be read clearly.",
                    sourceUri = uri
                )
            }
        }
    }
    fun clearPrescriptionImport(){ _prescriptionImport.value=PrescriptionImportState()}
    fun savePrescriptionImport(){
        val ctx=appContext?:return; val imports=_prescriptionImport.value.medicines; if(imports.isEmpty()) return
        imports.forEach{ imported -> val prev=_medicines.value.firstOrNull{ it.name.equals(imported.name, ignoreCase=true)}; prev?.times?.forEach{ cancelMedicineReminder(ctx, prev.name, it)}; imported.times.forEach{ scheduleMedicineReminder(ctx, imported.name, it)}}
        val names=imports.map{ it.name.lowercase(Locale.getDefault())}.toSet()
        _pausedReminderKeys.value=_pausedReminderKeys.value.filterNot{ k -> names.any{ n-> k.lowercase(Locale.getDefault()).startsWith("$n|")}}.toSet()
        _medicines.value=imports + _medicines.value.filterNot{ names.contains(it.name.lowercase(Locale.getDefault()))}
        persistMedicines(); persistPausedReminders(); clearPrescriptionImport()
    }

    // ── Save draft — server-backed per §4.4/§5/§7, offline queue fallback ──
    fun saveDraftMedicine(scheduleType: String = "fixed_times", timingMode: String = "local_clock") {
        val ctx = appContext ?: return
        val d = _draft.value
        if (d.name.isBlank()) return
        // optimistic local first (offline-first)
        val imageUri = d.cardImageUri ?: d.photoUri?.toString()
        val localMed = ManagedMedicine(
            name = d.name.trim(),
            purpose = d.purpose.trim(),
            genericNameAndDose = d.genericNameAndDose.trim(),
            times = d.times.distinct().sorted(),
            photoUris = listOfNotNull(imageUri),
            ocrText = d.detectedText,
            cardImageUri = imageUri,
            form = d.form.trim(),
            foodTiming = d.foodTiming.trim(),
            instruction = d.instruction.trim(),
            frequency = d.frequency.ifBlank { "Once daily" },
            duration = d.duration.ifBlank { "Ongoing" },
            startDate = d.startDate,
            refillTrackingEnabled = d.refillTrackingEnabled,
            currentSupply = d.currentSupply,
            refillThresholdPercent = d.refillThresholdPercent,
            notes = d.notes.trim()
        )
        // persist locally immediately
        val prev = _medicines.value.firstOrNull{ it.name.equals(localMed.name, ignoreCase=true)}
        prev?.times?.forEach{ cancelMedicineReminder(ctx, prev.name, it)}
        _pausedReminderKeys.value = _pausedReminderKeys.value.filterNot{ it.startsWith("${localMed.name}|")}.toSet()
        persistPausedReminders()
        if (d.remindersEnabled) {
            localMed.times.forEach{ scheduleMedicineReminder(ctx, localMed.name, it)}
        }
        _medicines.value = listOf(localMed) + _medicines.value.filterNot{ it.name.equals(localMed.name, ignoreCase=true)}
        persistMedicines()
        clearDraft()
        // then try server (fire-and-forget, queuing on failure)
        val pid = _activePatientId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // map to server CreateMedicationRequest — decimals as strings (§1.4)
                val (rawStrengthVal, rawStrengthUnit) = parseStrengthDose(d.genericNameAndDose)
                val strengthUnit = serverStrengthUnit(rawStrengthUnit)
                val strengthVal = if (strengthUnit != null) rawStrengthVal else null
                val doseVal = strengthVal ?: "1"
                val doseUnit = normalizeDoseUnitForForm(d.form)
                val serverForm = normalizeFormForServer(d.form)
                val serverRoute = normalizeRouteForForm(d.form)
                val isoStartDate = com.example.medac.data.MedacDateUtils.normalizeToIsoDate(d.startDate)
                val isoEndDate = com.example.medac.data.MedacDateUtils.computeEndDate(isoStartDate, d.duration)
                val effectiveFrom = if (isoStartDate != null) {
                    try {
                        java.time.LocalDate.parse(isoStartDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString()
                    } catch (_: Exception) { java.time.Instant.now().toString() }
                } else java.time.Instant.now().toString()

                val req = com.example.medac.data.CreateMedicationRequest(
                    enteredName = d.name.trim(),
                    enteredStrengthValue = strengthVal,
                    enteredStrengthUnit = strengthUnit,
                    form = serverForm,
                    route = serverRoute,
                    doseQuantityValue = doseVal,
                    doseQuantityUnit = doseUnit,
                    indicationText = d.purpose.trim().ifBlank { null },
                    labelInstructionsText = d.instruction.trim().ifBlank { null },
                    status = "active",
                    highAttention = false,
                    startDate = isoStartDate,
                    endDate = isoEndDate
                )
                val dto = MedacRepository.createMedication(ctx, pid, req)
                // schedule
                MedacRepository.createSchedule(ctx, pid, dto.id, scheduleType, timingMode, d.times, effectiveFrom)
                // refresh cache
                refreshServer(ctx)
            } catch (e: Exception) {
                android.util.Log.w("MedacSync", "saveDraft server failed (queued locally): ${e.message}")
                _isOffline.value = true
            }
        }
    }

    fun removeMedicine(medicine: ManagedMedicine){
        val ctx=appContext?:return
        medicine.times.forEach{ cancelMedicineReminder(ctx, medicine.name, it)}
        _pausedReminderKeys.value=_pausedReminderKeys.value.filterNot{ it.startsWith("${medicine.name}|")}.toSet()
        persistPausedReminders()
        _medicines.value=_medicines.value.filterNot{ it.id==medicine.id}
        persistMedicines()
        // server archive
        val pid=_activePatientId.value ?: return
        viewModelScope.launch(Dispatchers.IO){
            try{
                // find server id by name
                val meds = MedacRepository.refreshMedications(ctx, pid)
                val target = meds.firstOrNull{ it.enteredName.equals(medicine.name, true)} ?: return@launch
                com.example.medac.data.NetworkModule.provideApi(ctx).archiveMedication(pid, target.id)
            } catch(_:Exception){}
        }
    }
    fun updateMedicine(updated: ManagedMedicine) {
        val ctx = appContext ?: return
        val prev = findMedicine(_medicines.value, updated.id, updated.name)
        prev?.times?.forEach { cancelMedicineReminder(ctx, prev.name, it) }
        _pausedReminderKeys.value = _pausedReminderKeys.value.filterNot { it.startsWith("${prev?.name ?: updated.name}|") }.toSet()
        persistPausedReminders()

        if (updated.statusOrActive == "active") {
            updated.times.forEach { scheduleMedicineReminder(ctx, updated.name, it) }
        }

        _medicines.value = _medicines.value.map {
            if (it.id == updated.id || it.name.equals(prev?.name ?: updated.name, ignoreCase = true)) updated else it
        }
        persistMedicines()

        val pid = _activePatientId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var serverId = serverIdFor(updated)
                if (serverId == null && prev != null) {
                    serverId = serverIdFor(prev)
                }
                if (serverId == null) {
                    val meds = MedacRepository.refreshMedications(ctx, pid)
                    _serverMeds.value = meds
                    serverId = meds.firstOrNull { it.enteredName.equals(updated.name, true) }?.id
                        ?: prev?.let { p -> meds.firstOrNull { it.enteredName.equals(p.name, true) }?.id }
                        ?: meds.firstOrNull { it.id.hashCode().toLong() == updated.id }?.id
                }
                if (serverId != null) {
                    val (rawStrengthVal, rawStrengthUnit) = parseStrengthDose(updated.genericNameAndDose)
                    val strengthUnit = serverStrengthUnit(rawStrengthUnit)
                    val strengthVal = if (strengthUnit != null) rawStrengthVal else null
                    val doseVal = strengthVal ?: "1"
                    val doseUnit = normalizeDoseUnitForForm(updated.form)
                    val serverForm = normalizeFormForServer(updated.form)
                    val serverRoute = normalizeRouteForForm(updated.form)
                    val isoStartDate = com.example.medac.data.MedacDateUtils.normalizeToIsoDate(updated.startDate)
                    val isoEndDate = com.example.medac.data.MedacDateUtils.computeEndDate(isoStartDate, updated.duration)

                    val updateReq = com.example.medac.data.UpdateMedicationRequest(
                        enteredName = updated.name.trim(),
                        enteredStrengthValue = strengthVal,
                        enteredStrengthUnit = strengthUnit,
                        form = serverForm,
                        route = serverRoute,
                        doseQuantityValue = doseVal,
                        doseQuantityUnit = doseUnit,
                        indicationText = updated.purpose.trim().ifBlank { null },
                        labelInstructionsText = updated.instruction.trim().ifBlank { null },
                        status = updated.statusOrActive,
                        startDate = isoStartDate,
                        endDate = isoEndDate
                    )

                    MedacRepository.updateMedication(ctx, pid, serverId, updateReq)

                    val effectiveFrom = if (isoStartDate != null) {
                        try {
                            java.time.LocalDate.parse(isoStartDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString()
                        } catch (_: Exception) { java.time.Instant.now().toString() }
                    } else java.time.Instant.now().toString()

                    MedacRepository.createOrSupersedeSchedule(ctx, pid, serverId, "fixed_times", "local_clock", updated.times, effectiveFrom)
                    refreshServer(ctx)
                    android.util.Log.d("MedacSync", "updateMedicine: successfully updated server med $serverId with times ${updated.times}")
                }
            } catch (e: Exception) {
                android.util.Log.w("MedacSync", "updateMedicine server update failed: ${e.message}")
            }
        }
    }

    fun updateMedicinePhoto(medicineId: Long, uri: Uri, context: Context): ManagedMedicine? {
        val appCtx = context.applicationContext
        val localUri = ensureLocalPhotoUri(appCtx, uri)
        val uriStr = localUri.toString()
        val med = _medicines.value.firstOrNull { it.id == medicineId } ?: return null
        val updated = med.copy(
            cardImageUri = uriStr,
            photoUris = (listOf(uriStr) + med.photoUris).distinct()
        )
        updateMedicine(updated)
        return updated
    }

    fun removeMedicinePhoto(medicineId: Long): ManagedMedicine? {
        val med = _medicines.value.firstOrNull { it.id == medicineId } ?: return null
        val updated = med.copy(
            cardImageUri = null,
            photoUris = emptyList()
        )
        updateMedicine(updated)
        return updated
    }

    fun restoreMedicine(medicine: ManagedMedicine){
        val ctx=appContext?:return
        _medicines.value=listOf(medicine)+_medicines.value.filterNot{ it.id==medicine.id}
        persistMedicines()
        medicine.times.forEach{ time -> if(!isReminderPaused(medicine.name, time)) scheduleMedicineReminder(ctx, medicine.name, time)}
    }
    fun setReminderEnabled(entry: ReminderEntry, enabled: Boolean){
        val ctx=appContext?:return
        val key=reminderKey(entry.medicineName, entry.time)
        _pausedReminderKeys.value = if(enabled) (_pausedReminderKeys.value - key) else (_pausedReminderKeys.value + key)
        persistPausedReminders()
        if(enabled) scheduleMedicineReminder(ctx, entry.medicineName, entry.time) else cancelMedicineReminder(ctx, entry.medicineName, entry.time)
        // server pause/resume if mapped
        val pid=_activePatientId.value ?: return
        viewModelScope.launch(Dispatchers.IO){
            try{
                val meds=MedacRepository.refreshMedications(ctx, pid)
                val target=meds.firstOrNull{ it.enteredName==entry.medicineName} ?: return@launch
                val api = com.example.medac.data.NetworkModule.provideApi(ctx)
                if(enabled) api.resumeMedication(pid, target.id) else api.pauseMedication(pid, target.id)
            }catch(_:Exception){}
        }
    }
    fun todaySchedule(): List<DoseScheduleItem>{
        val localItems = _medicines.value.filter { it.statusOrActive == "active" }.flatMap{ med ->
            // Show every dose scheduled for today regardless of the time the
            // medicine was added — past doses stay visible for late logging.
            med.times.mapNotNull{ time ->
                if (parseTime(time) == null) return@mapNotNull null
                DoseScheduleItem(
                    medicineId = med.id,
                    medicineName = med.name,
                    genericNameAndDose = med.genericNameAndDose,
                    use = med.purpose,
                    time = time,
                    active = !isReminderPaused(med.name, time),
                    cardImageUri = med.cardImageUri ?: med.photoUris.firstOrNull()
                )
            }
        }
        val occ = _occurrences.value
        if(occ.isEmpty()) return localItems.sortedBy{ it.time}
        // Server occurrences preferred, but resolved through _serverMeds so the
        // card shows the real medicine name/dose/image and reuses the local
        // ManagedMedicine.id (keeps dose-log keys stable).
        val serverItems = occ.mapNotNull{ o ->
            val serverDto = _serverMeds.value.firstOrNull{ it.id == o.medicationId }
            val local = _medicines.value.firstOrNull { med ->
                serverDto?.enteredName?.equals(med.name, ignoreCase = true) == true ||
                    med.name.equals(serverDto?.enteredName ?: o.medicationId, ignoreCase = true) ||
                    med.id.toString() == o.medicationId
            }
            val name = local?.name ?: serverDto?.enteredName ?: o.medicationId
            // Skip raw-UUID rows we cannot resolve to any known medicine.
            if (local == null && serverDto == null) return@mapNotNull null
            val time = try{ Instant.parse(o.scheduledAtUtc).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))} catch(_:Exception){ return@mapNotNull null}
            val cardImg = local?.cardImageUri ?: local?.photoUris?.firstOrNull()
            val dose = "${o.nominalDoseValue ?: ""} ${o.nominalDoseUnit ?: ""}".trim()
                .ifBlank { local?.genericNameAndDose.orEmpty() }
            DoseScheduleItem(
                medicineId = local?.id ?: o.medicationId.hashCode().toLong(),
                medicineName = name,
                use = local?.purpose.orEmpty(),
                genericNameAndDose = dose,
                time = time,
                active = o.state !in listOf("cancelled","taken","skipped") &&
                    !isReminderPaused(name, time),
                cardImageUri = cardImg
            )
        }
        // Union: keep local-only medicines that have no server occurrence yet
        // instead of hiding them when occurrences exist.
        val serverKeys = serverItems.map { "${it.medicineName.lowercase(Locale.getDefault())}|${it.time}" }.toSet()
        val missingLocal = localItems.filterNot {
            "${it.medicineName.lowercase(Locale.getDefault())}|${it.time}" in serverKeys
        }
        return (serverItems + missingLocal).sortedBy{ it.time}
    }
    fun reminderEntries(active:Boolean): List<ReminderEntry>{
        return _medicines.value.flatMap{ med ->
            val medActive = med.statusOrActive == "active"
            med.times.map{ time -> ReminderEntry(med.name, time, med.genericNameAndDose.ifBlank{ med.purpose.ifBlank{ "Follow label instructions"}}, medActive && !isReminderPaused(med.name, time), med.cardImageUri)}
        }.filter{ it.active==active}.sortedBy{ it.time}
    }
    fun nextDoseMinutes(): Int?{
        val now= LocalTime.now(); return todaySchedule().filter{ it.active}.mapNotNull{ item -> val t=parseTime(item.time) ?: return@mapNotNull null; val m=java.time.Duration.between(now,t).toMinutes(); when{ m>=0->m.toInt(); else-> (m+24*60).toInt()}}.minOrNull()
    }
    fun todayStr(): String = LocalDate.now().toString()
    /**
     * Tolerant lookup: matches by stable id first, then by medicine name
     * (case-insensitive) so logs written before a cloud-sync id change or
     * from the alarm receiver still count.
     */
    fun getDoseLog(medicineId:Long, time:String, date:String=todayStr(), medicineName: String? = null): DoseLogEntry?{
        val k=doseLogKey(medicineId,date,time)
        _doseLogs.value.firstOrNull{ it.id==k }?.let { return it }
        if (medicineName != null) {
            _doseLogs.value.firstOrNull {
                it.medicineName.equals(medicineName, ignoreCase = true) && it.time == time && it.date == date
            }?.let { return it }
        }
        return null
    }
    fun markDoseTaken(medicineId:Long, medicineName:String, time:String, doseAmount:String="", date:String=todayStr()){
        val key=doseLogKey(medicineId,date,time)
        val entry=DoseLogEntry(id=key, medicineId=medicineId, medicineName=medicineName, time=time, date=date, status="TAKEN", doseAmount=doseAmount, updatedAt=System.currentTimeMillis())
        _doseLogs.value=listOf(entry)+_doseLogs.value.filterNot{ it.id==key}; persistDoseLogs()
        // server
        val pid=_activePatientId.value; val ctx=appContext
        if(pid!=null && ctx!=null){
            viewModelScope.launch(Dispatchers.IO){
                try{
                    val med = findMedicine(_medicines.value, medicineId, medicineName)
                    var serverMedId = med?.let { serverIdFor(it) }
                    if (serverMedId == null) {
                        val meds = MedacRepository.refreshMedications(ctx, pid)
                        _serverMeds.value = meds
                        serverMedId = meds.firstOrNull { it.enteredName.equals(medicineName, true) }?.id
                    }
                    if (serverMedId != null) {
                        val occId = _occurrences.value.firstOrNull { it.medicationId == serverMedId }?.id
                        MedacRepository.logDose(ctx, pid, serverMedId, occId, "taken", doseAmount.ifBlank { null }, null, null)
                    }
                }catch(_:Exception){ _isOffline.value=true }
            }
        }
    }
    fun markDoseSkipped(medicineId:Long, medicineName:String, time:String, reason:String, date:String=todayStr()){
        val key=doseLogKey(medicineId,date,time)
        val entry=DoseLogEntry(id=key, medicineId=medicineId, medicineName=medicineName, time=time, date=date, status="SKIPPED", reason=reason.trim(), updatedAt=System.currentTimeMillis())
        _doseLogs.value=listOf(entry)+_doseLogs.value.filterNot{ it.id==key}; persistDoseLogs()
        val pid=_activePatientId.value; val ctx=appContext
        if(pid!=null && ctx!=null){
            viewModelScope.launch(Dispatchers.IO){
                try{
                    val med = findMedicine(_medicines.value, medicineId, medicineName)
                    var serverMedId = med?.let { serverIdFor(it) }
                    if (serverMedId == null) {
                        val meds = MedacRepository.refreshMedications(ctx, pid)
                        _serverMeds.value = meds
                        serverMedId = meds.firstOrNull { it.enteredName.equals(medicineName, true) }?.id
                    }
                    if (serverMedId != null) {
                        val occId = _occurrences.value.firstOrNull { it.medicationId == serverMedId }?.id
                        MedacRepository.logDose(ctx, pid, serverMedId, occId, "skipped", null, null, reason)
                    }
                }catch(_:Exception){ _isOffline.value=true }
            }
        }
    }
    fun clearDoseLog(medicineId:Long, time:String, date:String=todayStr()){ val k=doseLogKey(medicineId,date,time); _doseLogs.value=_doseLogs.value.filterNot{ it.id==k}; persistDoseLogs()}
    fun logsForMedicine(medicineId:Long): List<DoseLogEntry> = _doseLogs.value.filter{ it.medicineId==medicineId}.sortedByDescending{ it.date+it.time}
    fun clearAiError(){ _aiError.value=null}

    fun scheduleForDate(date: LocalDate): List<DoseScheduleItem> {
        val dateStr = date.toString()
        val zone = ZoneId.systemDefault()
        return _medicines.value.filter { it.statusOrActive == "active" }.flatMap { med ->
            val isoStart = com.example.medac.data.MedacDateUtils.normalizeToIsoDate(med.startDate)
            val medStart = try {
                if (isoStart != null) LocalDate.parse(isoStart) else null
            } catch (_: Exception) { null }
            if (medStart != null && date.isBefore(medStart)) {
                return@flatMap emptyList<DoseScheduleItem>()
            }
            val durationDays = when (med.duration.lowercase().replace(" days", "").replace(" ", "").trim()) {
                "7" -> 7
                "14" -> 14
                "30" -> 30
                "90" -> 90
                else -> null
            }
            if (medStart != null && durationDays != null) {
                val medEnd = medStart.plusDays(durationDays.toLong())
                if (date.isAfter(medEnd)) {
                    return@flatMap emptyList<DoseScheduleItem>()
                }
            }
            med.times.map { time ->
                DoseScheduleItem(
                    medicineId = med.id,
                    medicineName = med.name,
                    genericNameAndDose = med.genericNameAndDose,
                    use = med.purpose,
                    time = time,
                    active = !isReminderPaused(med.name, time),
                    cardImageUri = med.cardImageUri ?: med.photoUris.firstOrNull()
                )
            }
        }.sortedBy { it.time }
    }

    fun recordRefill(medicineId: Long, additionalUnits: Int = 30) {
        _medicines.value = _medicines.value.map { med ->
            if (med.id == medicineId) {
                val newSupply = (med.currentSupply + additionalUnits).coerceAtLeast(0)
                med.copy(currentSupply = newSupply)
            } else med
        }
        persistMedicines()
        val med = _medicines.value.firstOrNull { it.id == medicineId }
        val serverId = med?.let { serverIdFor(it) }
        if (serverId != null) {
            addInventoryTransaction(serverId, "fill", additionalUnits.toString(), "units", "Refill recorded")
        }
    }

    fun clearAllHistory() {
        _doseLogs.value = emptyList()
        persistDoseLogs()
    }

    fun transactionsFor(medicineId: String): List<com.example.medac.data.InventoryTransactionDto> = _inventoryTransactions.value[medicineId].orEmpty()
    fun expirationsFor(medicineId: String): List<com.example.medac.data.ExpirationDto> = _expirations.value[medicineId].orEmpty()

    fun loadInventory(medicineId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inv = MedacRepository.getInventory(ctx, pid, medicineId)
                if (inv != null) _inventories.value = _inventories.value + (medicineId to inv)
                val tx = MedacRepository.listInventoryTransactions(ctx, pid, medicineId, null, 20)
                if (tx != null) _inventoryTransactions.value = _inventoryTransactions.value + (medicineId to tx.transactions)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {}
        }
    }
    fun loadExpirations(medicineId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = MedacRepository.listExpirations(ctx, pid, medicineId)
                _expirations.value = _expirations.value + (medicineId to list)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {}
        }
    }
    fun addInventoryTransaction(medicineId: String, type: String, delta: String, unit: String, reason: String? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prev = _inventories.value[medicineId]
        val deltaNum = delta.toDoubleOrNull() ?: 0.0
        if (prev != null) {
            val newBal = (prev.balance ?: 0.0) + deltaNum
            _inventories.value = _inventories.value + (medicineId to prev.copy(balance = newBal))
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.InventoryTxRequest(type, delta, unit, reason?.ifBlank { null }, java.time.Instant.now().toString())
                val api = com.example.medac.data.NetworkModule.provideApi(ctx)
                val r = api.inventoryTx(pid, medicineId, req)
                if (r.isSuccessful) {
                    val inv = MedacRepository.getInventory(ctx, pid, medicineId)
                    if (inv != null) _inventories.value = _inventories.value + (medicineId to inv)
                    val tx = MedacRepository.listInventoryTransactions(ctx, pid, medicineId, null, 20)
                    if (tx != null) _inventoryTransactions.value = _inventoryTransactions.value + (medicineId to tx.transactions)
                } else if (prev != null) {
                    _inventories.value = _inventories.value + (medicineId to prev)
                }
                _isOffline.value = false
            } catch (e: java.io.IOException) {
                _isOffline.value = true
            } catch (_: Exception) {
                if (prev != null) _inventories.value = _inventories.value + (medicineId to prev)
            }
        }
    }
    fun updateInventorySettings(medicineId: String, threshold: String?, unit: String?) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prev = _inventories.value[medicineId]
        if (prev != null && threshold != null) {
            val newAcc = prev.account?.copy(lowStockThresholdValue = threshold) ?: com.example.medac.data.InventoryAccount("local", unit ?: prev.account?.unit, threshold)
            _inventories.value = _inventories.value + (medicineId to prev.copy(account = newAcc))
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.InventorySettingsRequest(threshold, unit)
                val api = com.example.medac.data.NetworkModule.provideApi(ctx)
                val r = api.updateInventorySettings(pid, medicineId, req)
                if (r.isSuccessful) {
                    val inv = MedacRepository.getInventory(ctx, pid, medicineId)
                    if (inv != null) _inventories.value = _inventories.value + (medicineId to inv)
                } else if (prev != null) _inventories.value = _inventories.value + (medicineId to prev)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {
                if (prev != null) _inventories.value = _inventories.value + (medicineId to prev)
            }
        }
    }
    fun createExpiration(medicineId: String, date: String, lot: String?, qty: String?, unit: String?) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val tmp = com.example.medac.data.ExpirationDto(java.util.UUID.randomUUID().toString(), medicineId, date, lot?.ifBlank { null }, qty?.ifBlank { null }, unit?.ifBlank { null }, null, null)
        _expirations.value = _expirations.value + (medicineId to (_expirations.value[medicineId].orEmpty() + tmp))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.CreateExpirationRequest(date, lot?.ifBlank { null }, qty?.ifBlank { null }, unit?.ifBlank { null })
                val api = com.example.medac.data.NetworkModule.provideApi(ctx)
                val r = api.createExpiration(pid, medicineId, req)
                val list = MedacRepository.listExpirations(ctx, pid, medicineId)
                _expirations.value = _expirations.value + (medicineId to list)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {
                _expirations.value = _expirations.value + (medicineId to _expirations.value[medicineId].orEmpty().filterNot { it.id == tmp.id })
            }
        }
    }
    fun updateExpiration(medicineId: String, eid: String, date: String?, lot: String?, qty: String?, unit: String?) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prev = _expirations.value[medicineId].orEmpty()
        val updated = prev.map { if (it.id == eid) it.copy(expirationDate = date ?: it.expirationDate, lotNumber = lot ?: it.lotNumber, quantityValue = qty ?: it.quantityValue, quantityUnit = unit ?: it.quantityUnit) else it }
        _expirations.value = _expirations.value + (medicineId to updated)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.UpdateExpirationRequest(date, lot, qty, unit)
                val api = com.example.medac.data.NetworkModule.provideApi(ctx)
                val r = api.updateExpiration(pid, medicineId, eid, req)
                val list = MedacRepository.listExpirations(ctx, pid, medicineId)
                _expirations.value = _expirations.value + (medicineId to list)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true; _expirations.value = _expirations.value + (medicineId to prev) } catch (_: Exception) { _expirations.value = _expirations.value + (medicineId to prev) }
        }
    }
    fun deleteExpiration(medicineId: String, eid: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prev = _expirations.value[medicineId].orEmpty()
        _expirations.value = _expirations.value + (medicineId to prev.filterNot { it.id == eid })
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = com.example.medac.data.NetworkModule.provideApi(ctx)
                val r = api.deleteExpiration(pid, medicineId, eid)
                if (!r.isSuccessful) _expirations.value = _expirations.value + (medicineId to prev)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true; _expirations.value = _expirations.value + (medicineId to prev) } catch (_: Exception) { _expirations.value = _expirations.value + (medicineId to prev) }
        }
    }
    fun loadSymptomLogs(cursor: String? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = MedacRepository.listSymptomLogs(ctx, pid, cursor ?: _symptomCursor, 50)
                if (resp != null) {
                    _symptomLogs.value = if (cursor == null && _symptomCursor == null) resp.logs else _symptomLogs.value + resp.logs
                    _symptomCursor = resp.nextCursor
                }
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {}
        }
    }
    fun refreshSymptomLogs() { _symptomCursor = null; _symptomLogs.value = emptyList(); loadSymptomLogs() }
    fun createSymptomLog(occurredAt: String, note: String?, linkedDoseEventId: String?) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val tmp = com.example.medac.data.SymptomLogDto(UUID.randomUUID().toString(), pid, occurredAt, note?.ifBlank { null }, linkedDoseEventId?.ifBlank { null }, null, Instant.now().toString(), null)
        _symptomLogs.value = listOf(tmp) + _symptomLogs.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.CreateSymptomRequest(occurredAt, note?.ifBlank { null }, linkedDoseEventId?.ifBlank { null })
                val dto = MedacRepository.createSymptomLog(ctx, pid, req)
                _symptomLogs.value = _symptomLogs.value.map { if (it.id == tmp.id) dto else it }
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {
                _symptomLogs.value = _symptomLogs.value.filterNot { it.id == tmp.id }
            }
        }
    }
    fun correctSymptomLog(logId: String, note: String?, reason: String?) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prev = _symptomLogs.value
        val target = prev.firstOrNull { it.id == logId } ?: return
        val corrected = target.copy(note = note ?: target.note)
        _symptomLogs.value = prev.map { if (it.id == logId) corrected else it }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.CorrectionSymptomRequest(note?.ifBlank { null }, reason?.ifBlank { null })
                val dto = MedacRepository.correctSymptomLog(ctx, pid, logId, req)
                _symptomLogs.value = _symptomLogs.value.filterNot { it.id == logId } + dto
                _symptomCursor = null
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true; _symptomLogs.value = prev } catch (_: Exception) { _symptomLogs.value = prev }
        }
    }
    fun loadInjectionLogs(medicationId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = MedacRepository.listInjectionLogs(ctx, pid, medicationId, 50)
                _injectionLogs.value = _injectionLogs.value + (medicationId to list)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {}
        }
    }
    fun createInjectionLog(medicationId: String, siteCode: String, occurredAt: String? = null, doseEventId: String? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val at = occurredAt ?: Instant.now().toString()
        val tmp = com.example.medac.data.InjectionLogDto(UUID.randomUUID().toString(), pid, medicationId, doseEventId?.ifBlank { null }, siteCode, at, null, at)
        _injectionLogs.value = _injectionLogs.value + (medicationId to (listOf(tmp) + (_injectionLogs.value[medicationId].orEmpty())))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.CreateInjectionRequest(medicationId, doseEventId?.ifBlank { null }, siteCode, at)
                val dto = MedacRepository.createInjectionLog(ctx, pid, req)
                val cur = _injectionLogs.value[medicationId].orEmpty().map { if (it.id == tmp.id) dto else it }
                _injectionLogs.value = _injectionLogs.value + (medicationId to cur)
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {
                _injectionLogs.value = _injectionLogs.value + (medicationId to _injectionLogs.value[medicationId].orEmpty().filterNot { it.id == tmp.id })
            }
        }
    }

    fun serverIdFor(medicine: ManagedMedicine): String? {
        return _serverMeds.value.firstOrNull { it.enteredName.equals(medicine.name, true) }?.id
    }
    // ── Alert preferences / generate §13 ──
    fun loadAlertPreferences() {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        _isLoadingPrefs.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = MedacRepository.listAlertPreferences(ctx, pid)
                _alertPreferences.value = list
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {}
            _isLoadingPrefs.value = false
        }
    }
    fun updateAlertPreferences(prefs: List<com.example.medac.data.PutAlertPreferenceItem>) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prev = _alertPreferences.value
        // optimistic: map prefs to dto-like
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = MedacRepository.updateAlertPreferences(ctx, pid, prefs)
                _alertPreferences.value = updated
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true; _alertPreferences.value = prev }
            catch (_: Exception) { _alertPreferences.value = prev }
        }
    }
    fun generateAlerts(onDone: ((com.example.medac.data.GeneratedCounts) -> Unit)? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = MedacRepository.generateAlerts(ctx, pid)
                _generateResult.value = res
                onDone?.invoke(res)
                // refresh alerts
                try {
                    val alertsDto = MedacRepository.openAlerts(ctx, pid)
                    _alerts.value = alertsDto.map {
                        val msg = when (it.severity) {
                            "attention" -> it.messageKey ?: "Attention: ${it.alertType}"
                            "urgent_review" -> it.messageKey ?: "Urgent: ${it.alertType}"
                            "potential_emergency" -> it.messageKey ?: "Emergency: ${it.alertType}"
                            else -> it.messageKey ?: it.alertType
                        }
                        AlertItem(it.id, it.severity, msg)
                    }
                } catch (_: Exception) {}
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {}
        }
    }
    fun clearGenerateResult() { _generateResult.value = null }

    // ── Adherence report §14 ──
    fun loadAdherenceReport(from: String, to: String, timezone: String? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        _isLoadingAdherence.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = MedacRepository.getAdherenceReport(ctx, pid, from, to, timezone ?: ZoneId.systemDefault().id)
                if (report != null) _adherenceReport.value = report
                _isOffline.value = false
            } catch (e: java.io.IOException) { _isOffline.value = true } catch (_: Exception) {}
            _isLoadingAdherence.value = false
        }
    }

    // ── Exports §15 ──
    fun createExport(from: String?, to: String?, includeSymptoms: Boolean?) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exp = MedacRepository.createExport(ctx, pid, from, to, includeSymptoms)
                _exports.value = listOf(exp) + _exports.value
                _exportError.value = null
                _isOffline.value = false
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg.contains("Unable to resolve") || msg.contains("Failed to connect")) _isOffline.value = true
                else _exportError.value = msg.take(200)
            }
        }
    }
    fun refreshExport(exportId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exp = MedacRepository.getExport(ctx, pid, exportId)
                if (exp != null) _exports.value = _exports.value.map { if (it.id == exportId) exp else it }
                _exportError.value = null
            } catch (e: Exception) {
                if (e.message?.contains("410") == true) {
                    _exportError.value = "Expired (410) — export no longer available"
                    _exports.value = _exports.value.map { if (it.id == exportId) it.copy(status = "expired") else it }
                } else _exportError.value = e.message?.take(200)
            }
        }
    }
    suspend fun downloadExportBytes(exportId: String, token: String?): ByteArray? {
        val pid = _activePatientId.value ?: return null
        val ctx = appContext ?: return null
        return try { MedacRepository.downloadExport(ctx, pid, exportId, token) } catch (e: Exception) {
            _exportError.value = when {
                e.message?.contains("410") == true -> "Expired (410)"
                e.message?.contains("403") == true -> "Invalid token (403)"
                e.message?.contains("404") == true -> "Not ready (404)"
                else -> e.message?.take(200)
            }
            null
        }
    }
    fun deleteExport(exportId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prev = _exports.value
        _exports.value = _exports.value.filterNot { it.id == exportId }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = MedacRepository.deleteExport(ctx, pid, exportId)
                if (!ok) _exports.value = prev
            } catch (e: java.io.IOException) { _isOffline.value = true; _exports.value = prev } catch (_: Exception) { _exports.value = prev }
        }
    }
    fun clearExportError() { _exportError.value = null }
    fun clearPatientMsg() { _patientActionMsg.value = null; _patientError.value = null }

    // ── Patients extended ──
    fun loadCurrentPatient() {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dto = MedacRepository.getPatient(ctx, pid)
            if (dto != null) _currentPatient.value = dto
        }
    }
    fun updateCurrentPatient(displayName: String?, timezone: String?, privacy: String?) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        _patientError.value = null; _patientActionMsg.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = com.example.medac.data.PatientPatchRequest(displayName?.ifBlank{null}, timezone?.ifBlank{null}, privacy?.ifBlank{null})
                val dto = MedacRepository.updatePatient(ctx, pid, req)
                _currentPatient.value = dto
                _patientActionMsg.value = "Saved"
                // refresh patients list
                val list = MedacRepository.listPatients(ctx)
                _patients.value = list.map { PatientChip(it.id, it.displayName?.ifBlank{null} ?: "My plan", "owner") }
            } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }
    fun archiveCurrentPatient() {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        _patientError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try { MedacRepository.archivePatient(ctx, pid); _patientActionMsg.value = "Archived" } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }
    fun requestDeletionCurrentPatient() {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        _patientError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try { MedacRepository.requestPatientDeletion(ctx, pid); _patientActionMsg.value = "Deletion requested" } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }
    fun loadMemberships() {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) { _memberships.value = MedacRepository.listMemberships(ctx, pid) }
    }
    fun updateMembership(mid: String, role: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { MedacRepository.updateMembership(ctx, pid, mid, role); _memberships.value = MedacRepository.listMemberships(ctx, pid); _patientActionMsg.value = "Role updated" } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }
    fun revokeMembership(mid: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { MedacRepository.revokeMembership(ctx, pid, mid); _memberships.value = MedacRepository.listMemberships(ctx, pid); _patientActionMsg.value = "Revoked" } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }
    fun loadInvites() {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) { _invites.value = MedacRepository.listInvites(ctx, pid) }
    }
    fun createInvite(email: String, role: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        _patientError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try { MedacRepository.createInvite(ctx, pid, email.trim(), role); _invites.value = MedacRepository.listInvites(ctx, pid); _patientActionMsg.value = "Invite sent" } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }
    fun revokeInvite(inviteId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { MedacRepository.revokeInvite(ctx, pid, inviteId); _invites.value = MedacRepository.listInvites(ctx, pid) } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }
    fun acceptInvite(token: String) {
        val ctx = appContext ?: return
        _patientError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newPid = MedacRepository.acceptInvite(ctx, token.trim())
                _patientActionMsg.value = "Joined patient $newPid"
                refreshServer(ctx)
            } catch (e: Exception) { _patientError.value = e.message?.take(200) }
        }
    }

    // ── AI identify — patient-scoped per §16.1, requires_user_confirmation ──
    fun setScanVisionDefault(enabled: Boolean) {
        val ctx = appContext ?: return
        ScanSettings.setVisionDefault(ctx, enabled)
        _scanVisionDefault.value = enabled
    }

    fun identifyMedicinePhoto(uri: Uri, context: Context){
        val t0=System.currentTimeMillis()
        val appCtx=context.applicationContext
        val localPhotoUri = ensureLocalPhotoUri(appCtx, uri)
        val photoUriStr = localPhotoUri.toString()
        _draft.value=_draft.value.copy(
            photoUri=localPhotoUri,
            cardImageUri=photoUriStr,
            detectedSummary="Scanning label...",
            detectedText="",
            isAiEnhanced=false,
            form="",
            foodTiming="",
            instruction=""
        )
        _aiIdentifying.value=true; _aiError.value=null; _isAnalyzingPhoto.value=true
        viewModelScope.launch{
            // OCR always runs in the background: it feeds detectedText and stays the
            // fallback whenever vision is disabled, fails, times out or returns no name.
            val ocrDeferred = async(Dispatchers.IO) {
                try {
                    GoogleLensOcrClient.recognizeText(appCtx, localPhotoUri).fullText.trim()
                } catch(e: Exception) {
                    android.util.Log.e("MedacOCR", "Google Lens OCR failed: ${e.message}", e)
                    ""
                }
            }

            val pid = _activePatientId.value ?: try{ MedacRepository.ensurePatient(appCtx)} catch(_:Exception){ null}
            if(pid!=null) _activePatientId.value=pid

            // ── Vision-first (default): the image goes to the server vision model ──
            var serverDone=false
            if(ScanSettings.strategyFor(_scanVisionDefault.value) == ScanStrategy.VISION){
                val visionRes = try{
                    withTimeout(35000){ MedicineAiRepository.identify(appCtx, localPhotoUri) }
                }catch(e:Exception){
                    android.util.Log.w("MedacAI","vision identify failed: ${e.message}")
                    AuthApiResult.Error(e.message ?: "vision identify failed")
                }
                val visionMed = (visionRes as? AuthApiResult.Success<AiMedicine>)?.data
                if(visionMed!=null && ScanSettings.visionResultUsable(visionMed.name)){
                    val ocrText = ocrDeferred.await()
                    _draft.value=_draft.value.copy(
                        photoUri=localPhotoUri,
                        cardImageUri=photoUriStr,
                        detectedSummary="AI identified from the image: ${visionMed.name} — please confirm",
                        detectedText=ocrText,
                        name=visionMed.name,
                        genericNameAndDose=visionMed.genericName.ifBlank{ _draft.value.genericNameAndDose },
                        purpose=visionMed.purpose.ifBlank{ _draft.value.purpose },
                        instruction=visionMed.instructions.ifBlank{ _draft.value.instruction },
                        times=visionMed.suggestedTimes.distinct().sorted().ifEmpty{ _draft.value.times },
                        form=visionMed.form.ifBlank{ _draft.value.form },
                        isAiEnhanced=true,
                        aiConfidence="high"
                    )
                    _aiError.value=null
                    serverDone=true
                    android.util.Log.d("MedacAI","vision identify success: name=${visionMed.name}")
                }else{
                    android.util.Log.d("MedacAI","vision unusable — falling back to OCR text")
                }
            }

            val ocrText = ocrDeferred.await()
            val ocrMs=System.currentTimeMillis()-t0
            if(!serverDone && ocrText.isNotBlank()){
                val s=buildDraftSuggestion(ocrText)
                _draft.value=_draft.value.copy(
                    photoUri=localPhotoUri,
                    cardImageUri=photoUriStr,
                    detectedSummary="OCR: ${s.name.ifBlank{ "text captured"}} — refining with AI...",
                    detectedText=ocrText,
                    name=s.name,
                    genericNameAndDose=s.genericNameAndDose,
                    purpose=s.purpose,
                    instruction=s.instruction,
                    times=s.times,
                    isAiEnhanced=false
                )
                _isAnalyzingPhoto.value=false
            }
            // Try server intelligence if patient available (skipped when vision already answered)
            if(!serverDone && pid!=null){
                try{
                    // Image upload code is preserved in codebase, but OCR text is always used for server identification
                    // val base64 = compressToBase64(appCtx, uri)
                    val resp = MedacRepository.labelInterpretation(appCtx, pid, null, ocrText.ifBlank{null})
                    // New server 07:12:29 returns legacy shape via intelligence route routes.ts:116: {success, medicine, interpretation, ai_run_id}
                    // Prefer medicine (full legacy with purpose/instructions/suggestedTimes) if present, fallback to interpretation
                    val med = resp.medicine
                    val interp = resp.interpretation
                    val name = med?.name?.takeIf{it.isNotBlank()} ?: interp?.candidateName ?: _draft.value.name
                    val generic = med?.genericName ?: med?.genericNameAlt ?: interp?.candidateGenericName
                    val purpose = med?.purpose?.takeIf{it.isNotBlank()} ?: _draft.value.purpose
                    val instr = med?.instructions?.takeIf{it.isNotBlank()} ?: interp?.labelDirectionsText ?: _draft.value.instruction
                    val form = med?.form ?: interp?.form ?: _draft.value.form
                    val times = med?.suggestedTimes ?: med?.suggestedTimesAlt
                    android.util.Log.d("MedacAI","label-interpretation success: medName=${med?.name} interpName=${interp?.candidateName}")
                    _draft.value=_draft.value.copy(
                        photoUri=localPhotoUri,
                        cardImageUri=photoUriStr,
                        detectedSummary= if(name.isNotBlank()) "AI identified: $name" else _draft.value.detectedSummary,
                        detectedText=ocrText,
                        name=name,
                        genericNameAndDose=if (!generic.isNullOrBlank()) generic else _draft.value.genericNameAndDose,
                        purpose=purpose,
                        instruction=instr,
                        form=form ?: _draft.value.form,
                        times=times?.distinct()?.sorted()?.takeIf{it.isNotEmpty()} ?: _draft.value.times,
                        isAiEnhanced=name.isNotBlank(),
                        aiConfidence="high"
                    )
                    if(name.isNotBlank()) _aiError.value=null
                    serverDone=true
                }catch(e:Exception){
                    val msg=e.message.orEmpty()
                    android.util.Log.w("MedacAI","label-interpretation failed: $msg")
                    val isTempUnavailable = msg.contains("INTELLIGENCE_TEMPORARILY_UNAVAILABLE", true) || msg.contains("temporarily unavailable", true)
                    val isRateLimited = msg.contains("429") || msg.contains("RATE_LIMITED", true)
                    _aiError.value = when{
                        isTempUnavailable -> "Intelligence temporarily unavailable — on-device scan kept. You can continue with manual entry and try again shortly."
                        isRateLimited -> "Too many requests — please wait a moment and tap Retry."
                        msg.contains("DECODED_TOO_LARGE", true) || msg.contains("413", true) -> "Image too large — try cropping closer to the label."
                        msg.contains("INVALID_IMAGE", true) -> "Image invalid or unsupported — try a clearer, straight-on photo."
                        msg.isNotBlank() && msg.length < 200 -> msg
                        else -> null
                    }
                    if(ocrText.isNotBlank() && _draft.value.name.isBlank()){
                        val s=buildDraftSuggestion(ocrText)
                        _draft.value=_draft.value.copy(
                            photoUri=localPhotoUri,
                            cardImageUri=photoUriStr,
                            detectedSummary=s.summary,
                            detectedText=ocrText,
                            name=s.name,
                            genericNameAndDose=s.genericNameAndDose,
                            purpose=s.purpose,
                            instruction=s.instruction,
                            times=s.times
                        )
                    }
                }
            }
            if(!serverDone){
                if(ocrText.isNotBlank()){
                    val s=buildDraftSuggestion(ocrText)
                    _draft.value=_draft.value.copy(
                        photoUri=localPhotoUri,
                        cardImageUri=photoUriStr,
                        detectedSummary=s.summary,
                        detectedText=ocrText,
                        name=s.name,
                        genericNameAndDose=s.genericNameAndDose,
                        purpose=s.purpose,
                        instruction=s.instruction,
                        times=s.times
                    )
                    try{
                        // Always use OCR text for identification without uploading image (image upload code preserved in codebase)
                        // val imageDeferred = if(runImageInParallel) async(Dispatchers.IO) { MedicineAiRepository.identify(appCtx, uri) } else null
                        val textDeferred = async(Dispatchers.IO) {
                            if(ocrText.trim().length >= 3) MedicineAiRepository.identifyFromText(appCtx, ocrText) else AuthApiResult.Error("ocrText too short")
                        }
                        var winner: AiMedicine? = null
                        var winnerSource = ""
                        try{
                            val textRes = withTimeout(15000) { textDeferred.await() }
                            if(textRes is AuthApiResult.Success<*>){
                                @Suppress("UNCHECKED_CAST")
                                val m=(textRes as AuthApiResult.Success<AiMedicine>).data
                                val hasName = m.name.isNotBlank()
                                android.util.Log.d("MedacAI","text identify result: name=${m.name} ocrConf=${m.ocrConfidence} hasName=$hasName")
                                if(hasName){
                                    winner=m; winnerSource="ocrText"
                                }
                            } else {
                                val err=(textRes as? AuthApiResult.Error)?.message
                                android.util.Log.d("MedacAI","text identify error: $err")
                            }
                        }catch(e:Exception){
                            android.util.Log.d("MedacAI","text identify timeout/error: ${e.message}")
                        }
                        if(winner!=null){
                            _draft.value=_draft.value.copy(
                                photoUri=localPhotoUri,
                                cardImageUri=photoUriStr,
                                name=winner.name,
                                purpose=winner.purpose.ifBlank { _draft.value.purpose },
                                instruction=winner.instructions.ifBlank { _draft.value.instruction },
                                genericNameAndDose=winner.genericName.ifBlank { _draft.value.genericNameAndDose },
                                times=winner.suggestedTimes.distinct().sorted().ifEmpty { _draft.value.times },
                                isAiEnhanced=true, form=winner.form.ifBlank { _draft.value.form },
                                aiConfidence="high",
                                detectedSummary="AI identified ($winnerSource): ${winner.name} — please confirm",
                                detectedText=ocrText
                            )
                            serverDone=true
                        } else {
                            if(_draft.value.name.isBlank()) {
                                val fallback = buildDraftSuggestion(ocrText)
                                _draft.value = _draft.value.copy(
                                    photoUri=localPhotoUri,
                                    cardImageUri=photoUriStr,
                                    name = fallback.name,
                                    detectedSummary = fallback.summary,
                                    detectedText = ocrText
                                )
                            }
                        }
                    }catch(_:Exception){
                    }
                } else {
                    _aiError.value="Could not identify medicine from this image. Try a clearer, straight-on photo."
                }
            }
            _aiIdentifying.value=false; _isAnalyzingPhoto.value=false
        }
    }

    // ── Intelligence: instruction parse, conversations/messages, drafts, summaries, proposals §16.2-16.6 ──
    fun parseInstruction(text: String, locale: String? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        if (text.isBlank() || text.length > 2000) { _intelligenceError.value = "Text must be 1..2000 chars"; return }
        _isIntelligenceLoading.value = true; _intelligenceError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = MedacRepository.instructionParse(ctx, pid, text.trim(), locale)
                _instructionParse.value = resp
                _intelligenceError.value = null
                _isOffline.value = false
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("429") || msg.contains("RATE_LIMITED", true) -> _intelligenceError.value = "Rate limited (429) — please wait a moment"
                    msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException -> { _isOffline.value = true; _intelligenceError.value = "Offline — will retry when online" }
                    else -> _intelligenceError.value = msg.take(300)
                }
            } finally { _isIntelligenceLoading.value = false }
        }
    }
    fun clearInstructionParse() { _instructionParse.value = null }

    fun ensureConversation(title: String? = null, onReady: ((String)->Unit)? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        if (_currentConversationId.value != null) { onReady?.invoke(_currentConversationId.value!!); return }
        _isIntelligenceLoading.value = true; _intelligenceError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = MedacRepository.createConversation(ctx, pid, title)
                _currentConversationId.value = resp.conversationId
                _isOffline.value = false
                onReady?.invoke(resp.conversationId)
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("429") || msg.contains("RATE_LIMITED", true) -> _intelligenceError.value = "Rate limited (429) — please wait"
                    msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException -> { _isOffline.value = true; _intelligenceError.value = "Offline — conversation not created" }
                    else -> _intelligenceError.value = msg.take(300)
                }
            } finally { _isIntelligenceLoading.value = false }
        }
    }
    fun deleteCurrentConversation() {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val cid = _currentConversationId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { MedacRepository.deleteConversation(ctx, pid, cid) } catch (_: Exception) {}
            _currentConversationId.value = null
            _intelligenceMessages.value = emptyList()
            _intelligenceProposals.value = emptyMap()
        }
    }
    fun sendIntelligenceMessage(content: String) {
        if (content.isBlank() || content.length > 5000) { _intelligenceError.value = "Message must be 1..5000 chars"; return }
        val pid = _activePatientId.value ?: run { _intelligenceError.value = "No patient — sign in first"; return }
        val ctx = appContext ?: return
        // append user bubble immediately
        _intelligenceMessages.value = _intelligenceMessages.value + IntelligenceChatItem(text = content, isUser = true)
        _isIntelligenceLoading.value = true; _intelligenceError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ensure conversation exists
                var cid = _currentConversationId.value
                if (cid == null) {
                    val conv = MedacRepository.createConversation(ctx, pid, null)
                    cid = conv.conversationId
                    _currentConversationId.value = cid
                }
                // Stream the reply into a placeholder bubble; if streaming is
                // unavailable (legacy harness, proxy, old server) fall back to
                // the JSON endpoint without losing the user's message.
                val placeholderId = UUID.randomUUID().toString()
                val streamed = StringBuilder()
                _intelligenceMessages.value = _intelligenceMessages.value +
                    IntelligenceChatItem(id = placeholderId, text = "", isUser = false, isStreaming = true)

                fun updatePlaceholder(text: String, streaming: Boolean) {
                    _intelligenceMessages.value = _intelligenceMessages.value.map {
                        if (it.id == placeholderId) it.copy(text = text, isStreaming = streaming) else it
                    }
                }

                val resp = try {
                    MedacRepository.sendMessageStream(
                        ctx, pid, cid!!, content,
                        onDelta = { delta ->
                            streamed.append(delta)
                            updatePlaceholder(streamed.toString(), true)
                        },
                        onTool = { /* tool progress: bubble keeps the text so far */ }
                    )
                } catch (streamError: Exception) {
                    android.util.Log.w("MedacAI", "stream failed, falling back: ${streamError.message}")
                    updatePlaceholder("", true) // clear partial text before the retry
                    MedacRepository.sendMessage(ctx, pid, cid, content, "user")
                }
                // finalise the bubble (streamed text wins if the server echoed nothing)
                val finalText = resp.message.ifBlank { streamed.toString() }
                _intelligenceMessages.value = _intelligenceMessages.value.map {
                    if (it.id == placeholderId) it.copy(
                        text = finalText,
                        isStreaming = false,
                        factsUsed = resp.factsUsed.orEmpty(),
                        proposals = resp.proposals.orEmpty(),
                        limitations = resp.limitations.orEmpty(),
                        aiRunId = resp.aiRunId,
                        turnCount = resp.turnCount
                    ) else it
                }
                // fetch proposals details for confirm/reject (payload_hash + human_summary)
                resp.proposals?.forEach { ref ->
                    try {
                        val p = MedacRepository.getProposal(ctx, pid, ref.proposalId)
                        _intelligenceProposals.value = _intelligenceProposals.value + (p.id to p)
                    } catch (_: Exception) {}
                }
                _isOffline.value = false
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("429") || msg.contains("RATE_LIMITED", true) -> _intelligenceError.value = "Rate limited (429) — please wait before sending again"
                    msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException -> { _isOffline.value = true; _intelligenceError.value = "Offline — message queued locally"; }
                    msg.contains("403") || msg.contains("PATIENT_MISMATCH") || msg.contains("FORBIDDEN") -> _intelligenceError.value = "No access (403) — check patient membership"
                    else -> _intelligenceError.value = msg.take(400)
                }
                // keep user message, assistant will show error instead of reply
            } finally { _isIntelligenceLoading.value = false }
        }
    }
    fun clearIntelligenceMessages() { _intelligenceMessages.value = emptyList() }
    fun clearIntelligenceError() { _intelligenceError.value = null }
    fun clearProposalMsg() { _proposalActionMsg.value = null }

    fun createScheduleDraft(constraints: String? = null, medicationId: String? = null, wakeTime: String? = null, sleepTime: String? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        if (constraints != null && constraints.length > 2000) { _intelligenceError.value = "Constraints must be ≤2000 chars"; return }
        if (wakeTime != null && !Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(wakeTime)) { _intelligenceError.value = "wakeTime must be HH:mm"; return }
        if (sleepTime != null && !Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(sleepTime)) { _intelligenceError.value = "sleepTime must be HH:mm"; return }
        _isIntelligenceLoading.value = true; _intelligenceError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tz = ZoneId.systemDefault().id
                val resp = MedacRepository.createScheduleDraft(ctx, pid, medicationId?.ifBlank{null}, constraints?.ifBlank{null}, tz, wakeTime?.ifBlank{null}, sleepTime?.ifBlank{null})
                _scheduleDraft.value = resp
                _isOffline.value = false
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("429") || msg.contains("RATE_LIMITED", true) -> _intelligenceError.value = "Rate limited (429) — try again shortly"
                    msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException -> { _isOffline.value = true; _intelligenceError.value = "Offline — draft not available" }
                    else -> _intelligenceError.value = msg.take(400)
                }
            } finally { _isIntelligenceLoading.value = false }
        }
    }
    fun clearScheduleDraft() { _scheduleDraft.value = null }

    fun fetchSummary(type: String, from: String? = null, to: String? = null) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        if (type !in listOf("adherence","inventory","timeline")) { _intelligenceError.value = "Summary type must be adherence|inventory|timeline"; return }
        _isIntelligenceLoading.value = true; _intelligenceError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = MedacRepository.createSummary(ctx, pid, type, from, to)
                _summary.value = resp
                _isOffline.value = false
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("429") || msg.contains("RATE_LIMITED", true) -> _intelligenceError.value = "Rate limited (429)"
                    msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException -> { _isOffline.value = true; _intelligenceError.value = "Offline — summary not available" }
                    else -> _intelligenceError.value = msg.take(400)
                }
            } finally { _isIntelligenceLoading.value = false }
        }
    }
    fun clearSummary() { _summary.value = null }

    fun confirmProposal(proposalId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        val prop = _intelligenceProposals.value[proposalId] ?: run { _proposalActionMsg.value = "Proposal not loaded — retry"; return }
        val hash = prop.payloadHash ?: run { _proposalActionMsg.value = "Missing payload_hash — cannot confirm"; return }
        _isIntelligenceLoading.value = true; _proposalActionMsg.value = null; _intelligenceError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = MedacRepository.confirmProposal(ctx, pid, proposalId, hash, prop.resourceVersions)
                _intelligenceProposals.value = _intelligenceProposals.value + (updated.id to updated)
                _proposalActionMsg.value = "Confirmed — ${updated.status}"
                // refresh medicines/occurrences if schedule/medication proposal
                if (updated.actionType?.contains("schedule", true) == true || updated.actionType?.contains("medication", true) == true) {
                    try { refreshServer(ctx) } catch (_: Exception) {}
                }
                _isOffline.value = false
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("429") -> _intelligenceError.value = "Rate limited (429)"
                    msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException -> { _isOffline.value = true; _intelligenceError.value = "Offline — cannot confirm" }
                    msg.contains("403") -> _intelligenceError.value = "Forbidden (403) — requires ${prop.requiredPermission ?: "permission"}"
                    msg.contains("409") || msg.contains("payload_hash") || msg.contains("version") -> _intelligenceError.value = "Confirm failed — payload or version mismatch (refresh proposal)"
                    else -> _intelligenceError.value = msg.take(400)
                }
            } finally { _isIntelligenceLoading.value = false }
        }
    }
    fun rejectProposal(proposalId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        _isIntelligenceLoading.value = true; _proposalActionMsg.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = MedacRepository.rejectProposal(ctx, pid, proposalId)
                _intelligenceProposals.value = _intelligenceProposals.value + (updated.id to updated)
                _proposalActionMsg.value = "Rejected"
                _isOffline.value = false
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException) { _isOffline.value = true; _intelligenceError.value = "Offline — cannot reject" }
                else _intelligenceError.value = msg.take(400)
            } finally { _isIntelligenceLoading.value = false }
        }
    }
    fun loadProposal(proposalId: String) {
        val pid = _activePatientId.value ?: return
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val p = MedacRepository.getProposal(ctx, pid, proposalId)
                _intelligenceProposals.value = _intelligenceProposals.value + (p.id to p)
            } catch (e: Exception) { _intelligenceError.value = e.message?.take(300) }
        }
    }

    // ── Drug normalization §6 + Health §2 flows ──
    fun searchDrugs(query: String) {
        val ctx = appContext ?: return
        val q = query.trim()
        drugSearchJob?.cancel()
        if (q.length < 2) { _drugSearchResults.value = emptyList(); _drugSearchError.value = null; return }
        _isDrugSearching.value = true
        drugSearchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            try {
                val list = MedacRepository.searchDrugs(ctx, q)
                _drugSearchResults.value = list
                _drugSearchError.value = null
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg.contains("Unable to resolve") || msg.contains("Failed to connect") || e is java.io.IOException) _isOffline.value = true
                _drugSearchError.value = msg.take(200)
                _drugSearchResults.value = emptyList()
            } finally { _isDrugSearching.value = false }
        }
    }
    fun clearDrugSearch() { drugSearchJob?.cancel(); _drugSearchResults.value = emptyList(); _drugSearchError.value = null }

    fun selectDrugConcept(item: com.example.medac.data.DrugConceptDto) {
        // fill draft name + attempt to parse strength/form heuristics
        val name = item.effectiveName.trim()
        if (name.isBlank()) return
        val cur = _draft.value
        // naive strength parse from name e.g. "Lisinopril 10 MG"
        val strength = Regex("""\d+(\.\d+)?\s*(mg|mcg|g|mg/mL|mL)""", RegexOption.IGNORE_CASE).find(name)?.value
        _draft.value = cur.copy(
            name = name.substringBefore(" ").ifBlank { name }.let { // keep first token as medicine name, dose separately
                // if name contains strength, split
                if (strength != null) name.substringBefore(strength).trim().ifBlank { name } else name
            },
            genericNameAndDose = strength ?: cur.genericNameAndDose,
            form = item.tty?.let { when(it){ "SBD","SCD"->"tablet"; "SBDG","SCDG"->"tablet"; else->cur.form } } ?: cur.form
        )
        // also fetch concept detail to enrich form/route if available
        item.rxcui?.let { rxcui -> fetchConcept(rxcui) }
        clearDrugSearch()
    }
    fun fetchConcept(rxcui: String) {
        val ctx = appContext ?: return
        if (!Regex("^\\d+$").matches(rxcui)) { _drugSearchError.value = "rxcui must be digits"; return }
        _conceptLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val c = MedacRepository.getConcept(ctx, rxcui)
                _selectedConcept.value = c
                // enrich draft if matched?
                if (_draft.value.name.isBlank()) _draft.value = _draft.value.copy(name = c.effectiveName)
                _drugSearchError.value = null
            } catch (e: Exception) { _drugSearchError.value = e.message?.take(200) }
            finally { _conceptLoading.value = false }
        }
    }
    fun resolveNdc(ndc: String) {
        val ctx = appContext ?: return
        val n = ndc.trim()
        if (n.length < 8 || n.length > 20) { _drugSearchError.value = "NDC 8..20 chars"; return }
        _conceptLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val c = MedacRepository.resolveNdc(ctx, n)
                _selectedConcept.value = c
                // fill draft with resolved concept
                val name = c.effectiveName
                if (name.isNotBlank()) {
                    _draft.value = _draft.value.copy(name = name, form = c.form ?: _draft.value.form, genericNameAndDose = c.strength ?: _draft.value.genericNameAndDose)
                }
                _drugSearchError.value = null
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg.contains("Unable to resolve") || msg.contains("Failed to connect")) _isOffline.value = true
                _drugSearchError.value = msg.take(200)
            } finally { _conceptLoading.value = false }
        }
    }
    fun clearSelectedConcept() { _selectedConcept.value = null }
    fun clearDrugSearchError() { _drugSearchError.value = null }

    fun refreshHealth() {
        val ctx = appContext ?: return
        _isHealthLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val liveDeferred = async { try { MedacRepository.getHealthLive(ctx) } catch (_: Exception) { null } }
                val readyDeferred = async { try { MedacRepository.getHealthReady(ctx) } catch (_: Exception) { com.example.medac.data.HealthResponse("not_ready") } }
                val versionDeferred = async { try { MedacRepository.getVersion(ctx) } catch (_: Exception) { null } }
                val live = liveDeferred.await()
                val ready = readyDeferred.await()
                val ver = versionDeferred.await()
                val now = java.time.Instant.now().toString()
                _healthStatus.value = com.example.medac.data.HealthStatusCombined(live, ready, now)
                if (ver != null) _versionInfo.value = ver
            } finally { _isHealthLoading.value = false }
        }
    }
    fun getHealthLive() { refreshHealth() }
    fun getHealthReady() { refreshHealth() }
    fun getVersion() {
        val ctx = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { _versionInfo.value = MedacRepository.getVersion(ctx) } catch (_: Exception) {}
        }
    }

    // ── Persistence (local cache fallback) ──
    private fun loadDoseLogs(){
        val ctx=appContext?:return; val json=ctx.getSharedPreferences("dose_logs", Context.MODE_PRIVATE).getString("entries","[]").orEmpty()
        _doseLogs.value=try{ val arr=JSONArray(json); List(arr.length()){i-> gson.fromJson(arr.getJSONObject(i).toString(), DoseLogEntry::class.java)}}catch(_:Exception){ emptyList()}
    }
    private fun persistDoseLogs(){ val ctx=appContext?:return; val arr=JSONArray().apply{ _doseLogs.value.forEach{ put(JSONObject(gson.toJson(it)))}}; ctx.getSharedPreferences("dose_logs", Context.MODE_PRIVATE).edit().putString("entries", arr.toString()).apply()}
    private fun isReminderPaused(name:String, time:String): Boolean = _pausedReminderKeys.value.contains(reminderKey(name,time))
    private fun loadMedicines(){
        val ctx=appContext?:return; val json=ctx.getSharedPreferences("medicines", Context.MODE_PRIVATE).getString("items","[]").orEmpty()
        _medicines.value=try{ val arr=JSONArray(json); List(arr.length()){ i-> gson.fromJson(arr.getJSONObject(i).toString(), ManagedMedicine::class.java)}}catch(_:Exception){ emptyList()}
    }
    private fun persistMedicines(){ val ctx=appContext?:return; val arr=JSONArray().apply{ _medicines.value.forEach{ put(JSONObject(gson.toJson(it)))}}; ctx.getSharedPreferences("medicines", Context.MODE_PRIVATE).edit().putString("items", arr.toString()).apply()}
    private fun loadPausedReminders(){ val ctx=appContext?:return; _pausedReminderKeys.value=ctx.getSharedPreferences("reminder_state", Context.MODE_PRIVATE).getStringSet("paused_keys", emptySet()).orEmpty()}
    private fun persistPausedReminders(){ val ctx=appContext?:return; ctx.getSharedPreferences("reminder_state", Context.MODE_PRIVATE).edit().putStringSet("paused_keys", _pausedReminderKeys.value).apply()}
    private fun loadQuietHours(){ val ctx=appContext?:return; val stored=ctx.getSharedPreferences("reminder_state", Context.MODE_PRIVATE).getString("quiet_hours", null); if(stored!=null){ val parts=stored.split(","); if(parts.size==2 && isValidReminderTime(parts[0]) && isValidReminderTime(parts[1])) _quietHours.value=parts[0] to parts[1]}}
    private fun rescheduleAllActiveReminders() {
        val ctx = appContext ?: return
        _medicines.value.filter { it.statusOrActive == "active" }.forEach { med ->
            med.times.forEach { time ->
                if (!isReminderPaused(med.name, time)) {
                    scheduleMedicineReminder(ctx, med.name, time)
                }
            }
        }
    }

    private fun medicationDtoToManaged(
        dto: com.example.medac.data.MedicationDto,
        existing: ManagedMedicine? = null,
        fallbackTimes: List<String> = emptyList()
    ): ManagedMedicine{
        // Keep the existing local id when the same medicine (by name) already
        // exists so dose-log keys and alarm bookkeeping stay stable across
        // syncs. Only new server medicines get a hash-derived id.
        val id = existing?.id ?: dto.id.hashCode().toLong()
        val times = when {
            !existing?.times.isNullOrEmpty() -> existing?.times.orEmpty()
            fallbackTimes.isNotEmpty() -> fallbackTimes.distinct().sorted()
            else -> emptyList()
        }
        val strength = if (!dto.enteredStrengthValue.isNullOrBlank() && !dto.enteredStrengthUnit.isNullOrBlank()) {
            "${dto.enteredStrengthValue} ${dto.enteredStrengthUnit}"
        } else null
        val dose = strength ?: existing?.genericNameAndDose?.takeIf { it.isNotBlank() } ?: "${dto.doseQuantityValue} ${dto.doseQuantityUnit}".trim()
        return ManagedMedicine(
            id = id,
            name = dto.enteredName,
            genericNameAndDose = dose,
            purpose = dto.indicationText ?: existing?.purpose.orEmpty(),
            times = times,
            photoUris = existing?.photoUris ?: emptyList(),
            ocrText = existing?.ocrText.orEmpty(),
            cardImageUri = existing?.cardImageUri,
            form = dto.form ?: existing?.form.orEmpty(),
            foodTiming = existing?.foodTiming.orEmpty(),
            instruction = dto.labelInstructionsText ?: existing?.instruction.orEmpty(),
            status = dto.status,
            highAttention = dto.highAttention ?: existing?.highAttention ?: false,
            frequency = existing?.frequency ?: "Once daily",
            duration = existing?.duration ?: "Ongoing",
            startDate = existing?.startDate.orEmpty(),
            refillTrackingEnabled = existing?.refillTrackingEnabled ?: false,
            currentSupply = existing?.currentSupply ?: 0,
            refillThresholdPercent = existing?.refillThresholdPercent ?: 0,
            notes = existing?.notes.orEmpty()
        )
    }

    /**
     * Derives HH:mm times from today's server occurrences for medicines whose
     * local [ManagedMedicine.times] is still empty. Returns true if anything changed.
     */
    private fun backfillTimesFromOccurrences(): Boolean {
        val occ = _occurrences.value
        if (occ.isEmpty()) return false
        val zone = ZoneId.systemDefault()
        val timesByMedId = occ.groupBy { it.medicationId }.mapValues { (_, list) ->
            list.mapNotNull {
                try {
                    Instant.parse(it.scheduledAtUtc).atZone(zone).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                } catch (_: Exception) { null }
            }.distinct().sorted()
        }
        // Map server medication id -> local medicine name via _serverMeds.
        val nameByServerId = _serverMeds.value.associate { it.id to it.enteredName }
        var changed = false
        _medicines.value = _medicines.value.map { med ->
            if (med.times.isNotEmpty()) return@map med
            // Find server id(s) matching this local medicine by name.
            val serverIds = _serverMeds.value
                .filter { it.enteredName.equals(med.name, ignoreCase = true) }
                .map { it.id }
                .ifEmpty {
                    // Fallback: occurrence already keyed by local hash (offline cache).
                    nameByServerId.entries
                        .filter { it.value.equals(med.name, ignoreCase = true) }
                        .map { it.key }
                }
            val derived = serverIds.flatMap { timesByMedId[it].orEmpty() }.distinct().sorted()
            if (derived.isNotEmpty()) {
                changed = true
                med.copy(times = derived)
            } else med
        }
        return changed
    }

    private fun applyFallbackDraft(photoUri: Uri?, ocrText: String){
        _isAnalyzingPhoto.value=false; val s=buildDraftSuggestion(ocrText)
        _draft.value=_draft.value.copy(
            photoUri=photoUri,
            cardImageUri=photoUri?.toString(),
            detectedSummary=s.summary,
            detectedText=s.fullText,
            name=s.name,
            genericNameAndDose=s.genericNameAndDose,
            purpose=s.purpose,
            instruction=s.instruction,
            times=s.times,
            isAiEnhanced=false
        )
    }
    private fun buildDraftSuggestion(rawText:String): OcrDraftSuggestion{
        val lines=rawText.lines().map{ it.trim()}.filter{ it.isNotBlank()}
        val summary=if(lines.isEmpty()) "Photo captured. No readable label text was detected yet." else "Review the detected medicine name and details."
        val name=lines.firstOrNull().orEmpty()
        val dose=Regex("""\b\d+(\.\d+)?\s?(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE).find(rawText)?.value.orEmpty()
        val instr=lines.firstOrNull{ it.contains("take",true)||it.contains("after",true)||it.contains("before",true)||it.contains("daily",true)}.orEmpty()
        val purpose=lines.firstOrNull{ it.contains("for ",true)||it.contains("relie",true)||it.contains("pain",true)||it.contains("fever",true)||it.contains("cough",true)||it.contains("cold",true)}.orEmpty()
        val times=when{ rawText.contains("three times",true)->listOf("08:00","14:00","20:00"); rawText.contains("twice",true)||rawText.contains("2 times",true)->listOf("08:00","20:00"); rawText.contains("once",true)||rawText.contains("daily",true)->listOf("08:00"); else->listOf("08:00")}
        return OcrDraftSuggestion(
            summary = summary,
            name = name,
            genericNameAndDose = dose,
            purpose = purpose,
            instruction = instr,
            times = times,
            fullText = rawText.trim()
        )
    }
    private fun buildPrescriptionMedicines(rawText:String, uri:Uri): List<ManagedMedicine>{
        val lines=rawText.lines().map{ it.trim().trim('-','*','•')}.filter{ l-> l.length>=3 && !l.contains("doctor",true)&&!l.contains("hospital",true)&&!l.contains("clinic",true)&&!l.contains("patient",true)&&!l.contains("date",true)}
        val doseRegex=Regex("""\b\d+(\.\d+)?\s?(mg|mcg|g|gm|ml|iu|units?)\b""", RegexOption.IGNORE_CASE)
        val freqRegex=Regex("""\b(once|twice|thrice|daily|morning|night|evening|afternoon|before|after|od|bd|bid|tds|tid|qid|hs|sos|1-0-0|0-1-0|0-0-1|1-0-1|1-1-1)\b""", RegexOption.IGNORE_CASE)
        return lines.mapIndexedNotNull{ idx, line ->
            if(!doseRegex.containsMatchIn(line) && !freqRegex.containsMatchIn(line)) return@mapIndexedNotNull null
            val dose=doseRegex.find(line)?.value.orEmpty()
            val name=line.substringBefore(dose.ifBlank{"  "}).replace(Regex("""^\d+[\).]\s*"""),"").replace(Regex("""\b(tab|tablet|cap|capsule|syp|syrup|inj|injection)\b\.?""", RegexOption.IGNORE_CASE),"").trim(' ','-',':').ifBlank{ line.substringBeforeLast(" ").trim()}
            if(name.length<2) return@mapIndexedNotNull null
            ManagedMedicine(id=System.currentTimeMillis()+idx, name=name, purpose=line, genericNameAndDose=dose, times=suggestedTimesForPrescriptionLine(line), photoUris=listOf(uri.toString()), cardImageUri=uri.toString(), ocrText=rawText)
        }.distinctBy{ it.name.lowercase(Locale.getDefault())}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            appContext?.getSharedPreferences("dose_logs", Context.MODE_PRIVATE)
                ?.unregisterOnSharedPreferenceChangeListener(doseLogsPrefsListener)
        } catch (_: Exception) {}
    }
}
