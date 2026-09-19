package com.example.medac.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object MedacRepository {
    private val gson = Gson()

    private fun api(ctx: Context) = NetworkModule.provideApi(ctx)
    private fun db(ctx: Context) = MedacDatabase.get(ctx)

    // ── Patients ──
    suspend fun ensurePatient(ctx: Context, displayName: String = "My plan"): String = withContext(Dispatchers.IO) {
        val r = api(ctx).listPatients()
        if (r.isSuccessful) {
            val list = r.body()?.patients.orEmpty()
            if (list.isNotEmpty()) return@withContext list.first().id
        }
        // create
        val tz = ZoneId.systemDefault().id
        val cr = api(ctx).createPatient(CreatePatientRequest(displayName, tz))
        if (cr.isSuccessful) return@withContext cr.body()!!.id
        throw Exception(cr.errorBody()?.string() ?: "patient create failed ${cr.code()}")
    }

    suspend fun listPatients(ctx: Context): List<PatientDto> = withContext(Dispatchers.IO) {
        val r = api(ctx).listPatients()
        if (r.isSuccessful) r.body()!!.patients else emptyList()
    }
    suspend fun getPatient(ctx: Context, pid: String): PatientDto? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).getPatient(pid); if (r.isSuccessful) r.body() else null } catch (_: Exception) { null }
    }
    suspend fun updatePatient(ctx: Context, pid: String, req: PatientPatchRequest): PatientDto = withContext(Dispatchers.IO) {
        val r = api(ctx).patchPatient(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "patch patient ${r.code()}")
        r.body()!!
    }
    suspend fun archivePatient(ctx: Context, pid: String) = withContext(Dispatchers.IO) {
        val r = api(ctx).archivePatient(pid)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "archive ${r.code()}")
    }
    suspend fun requestPatientDeletion(ctx: Context, pid: String) = withContext(Dispatchers.IO) {
        val r = api(ctx).deletionRequest(pid)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "deletion-request ${r.code()}")
    }
    suspend fun listMemberships(ctx: Context, pid: String): List<MembershipDto> = withContext(Dispatchers.IO) {
        try { val r = api(ctx).listMemberships(pid); if (r.isSuccessful) r.body()?.memberships.orEmpty() else emptyList() } catch (_: Exception) { emptyList() }
    }
    suspend fun updateMembership(ctx: Context, pid: String, mid: String, role: String) = withContext(Dispatchers.IO) {
        val r = api(ctx).updateMembership(pid, mid, UpdateMembershipRequest(role))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "update membership ${r.code()}")
    }
    suspend fun revokeMembership(ctx: Context, pid: String, mid: String) = withContext(Dispatchers.IO) {
        val r = api(ctx).deleteMembership(pid, mid)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "revoke membership ${r.code()}")
    }
    suspend fun listInvites(ctx: Context, pid: String): List<InviteDto> = withContext(Dispatchers.IO) {
        try { val r = api(ctx).listInvites(pid); if (r.isSuccessful) r.body()?.invites.orEmpty() else emptyList() } catch (_: Exception) { emptyList() }
    }
    suspend fun createInvite(ctx: Context, pid: String, email: String, role: String): String = withContext(Dispatchers.IO) {
        val r = api(ctx).createInvite(pid, CreateInviteRequest(email, role))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "create invite ${r.code()}")
        r.body()!!.inviteId
    }
    suspend fun revokeInvite(ctx: Context, pid: String, inviteId: String) = withContext(Dispatchers.IO) {
        val r = api(ctx).deleteInvite(pid, inviteId)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "revoke invite ${r.code()}")
    }
    suspend fun acceptInvite(ctx: Context, token: String): String = withContext(Dispatchers.IO) {
        val r = api(ctx).acceptInvite(token.trim())
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "accept invite ${r.code()}")
        r.body()!!.patientId
    }

    // ── Medications (Room cache keyed by patient + sync cursor) ──
    suspend fun refreshMedications(ctx: Context, pid: String): List<MedicationDto> = withContext(Dispatchers.IO) {
        try {
            val r = api(ctx).listMedications(pid, true)
            if (r.isSuccessful) {
                val meds = r.body()!!.medications
                val entities = meds.map {
                    MedicationEntity(it.id, it.patientId, it.enteredName, it.doseQuantityValue, it.doseQuantityUnit, it.status, it.updatedAt, gson.toJson(it))
                }
                db(ctx).medicationDao().clear(pid)
                if (entities.isNotEmpty()) db(ctx).medicationDao().upsertAll(entities)
                meds
            } else cachedMeds(ctx, pid)
        } catch (_: Exception) { cachedMeds(ctx, pid) }
    }

    private suspend fun cachedMeds(ctx: Context, pid: String): List<MedicationDto> {
        return db(ctx).medicationDao().byPatient(pid).mapNotNull { try { gson.fromJson(it.rawJson, MedicationDto::class.java) } catch (_: Exception) { null } }
    }

    suspend fun createMedication(ctx: Context, pid: String, req: CreateMedicationRequest): MedicationDto = withContext(Dispatchers.IO) {
        val r = api(ctx).createMedication(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "create medication ${r.code()}")
        val dto = r.body()!!
        db(ctx).medicationDao().upsertAll(listOf(MedicationEntity(dto.id, dto.patientId, dto.enteredName, dto.doseQuantityValue, dto.doseQuantityUnit, dto.status, dto.updatedAt, gson.toJson(dto))))
        dto
    }

    suspend fun updateMedication(ctx: Context, pid: String, mid: String, req: UpdateMedicationRequest): MedicationDto = withContext(Dispatchers.IO) {
        val r = api(ctx).updateMedication(pid, mid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "update medication ${r.code()}")
        val dto = r.body()!!
        db(ctx).medicationDao().upsertAll(listOf(MedicationEntity(dto.id, dto.patientId, dto.enteredName, dto.doseQuantityValue, dto.doseQuantityUnit, dto.status, dto.updatedAt, gson.toJson(dto))))
        dto
    }

    // ── Schedules ──
    suspend fun listSchedules(ctx: Context, pid: String, mid: String): List<ScheduleVersionDto> = withContext(Dispatchers.IO) {
        try {
            val r = api(ctx).listSchedules(pid, mid)
            if (r.isSuccessful) r.body()?.schedules.orEmpty() else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getScheduleVersion(ctx: Context, pid: String, mid: String, versionId: String): ScheduleVersionDto? = withContext(Dispatchers.IO) {
        try {
            val r = api(ctx).getScheduleVersion(pid, mid, versionId)
            if (r.isSuccessful) r.body() else null
        } catch (_: Exception) { null }
    }

    suspend fun createSchedule(ctx: Context, pid: String, mid: String, scheduleType: String, timingMode: String, times: List<String>, effectiveFrom: String? = null): List<OccurrenceDto> = withContext(Dispatchers.IO) {
        val tz = ZoneId.systemDefault().id
        val effFrom = effectiveFrom ?: Instant.now().toString()
        val req = when (scheduleType) {
            "prn" -> CreateScheduleRequest("prn", timingMode, tz, 60, effFrom, null, null)
            "elapsed_interval" -> {
                val anchor = Instant.now().toString()
                CreateScheduleRequest("elapsed_interval", timingMode, tz, 60, effFrom, null, IntervalPayload(480, anchor))
            }
            else -> CreateScheduleRequest("fixed_times", "local_clock", tz, 60, effFrom, times.map { FixedTime(it) }, null)
        }
        val r = api(ctx).createSchedule(pid, mid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "schedule ${r.code()}")
        r.body()?.preview.orEmpty()
    }

    suspend fun supersedeSchedule(ctx: Context, pid: String, mid: String, versionId: String, scheduleType: String, timingMode: String, times: List<String>, effectiveFrom: String? = null): List<OccurrenceDto> = withContext(Dispatchers.IO) {
        val tz = ZoneId.systemDefault().id
        val effFrom = effectiveFrom ?: Instant.now().toString()
        val req = when (scheduleType) {
            "prn" -> CreateScheduleRequest("prn", timingMode, tz, 60, effFrom, null, null)
            "elapsed_interval" -> {
                val anchor = Instant.now().toString()
                CreateScheduleRequest("elapsed_interval", timingMode, tz, 60, effFrom, null, IntervalPayload(480, anchor))
            }
            else -> CreateScheduleRequest("fixed_times", "local_clock", tz, 60, effFrom, times.map { FixedTime(it) }, null)
        }
        val r = api(ctx).supersedeSchedule(pid, mid, versionId, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "supersede schedule ${r.code()}")
        r.body()?.preview.orEmpty()
    }

    suspend fun createOrSupersedeSchedule(ctx: Context, pid: String, mid: String, scheduleType: String, timingMode: String, times: List<String>, effectiveFrom: String? = null): List<OccurrenceDto> = withContext(Dispatchers.IO) {
        val schedules = listSchedules(ctx, pid, mid)
        val activeVersion = schedules.firstOrNull { it.effectiveUntil == null }
        if (activeVersion != null) {
            supersedeSchedule(ctx, pid, mid, activeVersion.id, scheduleType, timingMode, times, effectiveFrom)
        } else {
            createSchedule(ctx, pid, mid, scheduleType, timingMode, times, effectiveFrom)
        }
    }

    suspend fun occurrences(ctx: Context, pid: String, fromUtc: String, toUtc: String): List<OccurrenceDto> = withContext(Dispatchers.IO) {
        try {
            val r = api(ctx).occurrences(pid, fromUtc, toUtc, 100)
            if (r.isSuccessful) {
                val list = r.body()!!.occurrences
                // cache occurrences for offline Today
                val entities = list.map { OccurrenceEntity(it.id, it.patientId, it.medicationId, it.scheduledAtUtc, it.state, gson.toJson(it)) }
                if (entities.isNotEmpty()) db(ctx).occurrenceDao().upsertAll(entities)
                list
            } else emptyList()
        } catch (_: Exception) {
            // fallback to cached
            db(ctx).occurrenceDao().range(pid, fromUtc, toUtc).mapNotNull { try { gson.fromJson(it.rawJson, OccurrenceDto::class.java) } catch (_: Exception) { null } }
        }
    }

    // ── Dose events — idempotent + offline queue + batch ──
    suspend fun logDose(ctx: Context, pid: String, medicationId: String, occurrenceId: String?, eventType: String, doseValue: String?, doseUnit: String?, note: String?): DoseEventDto = withContext(Dispatchers.IO) {
        val tz = ZoneId.systemDefault().id
        val actualAt = Instant.now().toString()
        val cid = UUID.randomUUID().toString()
        val req = CreateDoseEventRequest(medicationId, occurrenceId, eventType, actualAt, tz, doseValue, doseUnit, note, cid)
        // optimistic local write
        val local = DoseEventEntity(cid, pid, medicationId, occurrenceId, cid, eventType, actualAt, true, gson.toJson(req))
        db(ctx).doseEventDao().upsert(local)
        try {
            val r = api(ctx).createDoseEvent(pid, req)
            if (r.isSuccessful) {
                db(ctx).doseEventDao().markSynced(cid)
                return@withContext r.body()!!.event
            }
            // keep pending
            local
            throw Exception(r.errorBody()?.string() ?: "dose ${r.code()}")
        } catch (e: Exception) {
            // offline — queued, return local as success per §5.3
            if (e.message?.contains("Unable to resolve") == true || e.message?.contains("Failed to connect") == true) {
                return@withContext DoseEventDto(cid, pid, medicationId, occurrenceId, eventType, actualAt, cid)
            }
            throw e
        }
    }

    suspend fun syncPendingDoses(ctx: Context, pid: String) = withContext(Dispatchers.IO) {
        val pending = db(ctx).doseEventDao().pending()
        if (pending.isEmpty()) return@withContext
        val batch = pending.mapNotNull { try { gson.fromJson(it.rawJson, CreateDoseEventRequest::class.java) } catch (_: Exception) { null } }
        if (batch.isEmpty()) return@withContext
        try {
            val r = api(ctx).batchDoseEvents(pid, BatchDoseRequest(batch))
            if (r.isSuccessful) pending.forEach { db(ctx).doseEventDao().markSynced(it.clientEventId) }
        } catch (_: Exception) { /* keep queued */ }
    }

    suspend fun correctDose(ctx: Context, pid: String, eventId: String, correctionType: String, reason: String) = withContext(Dispatchers.IO) {
        val req = CorrectionRequest(correctionType, reason, UUID.randomUUID().toString())
        val r = api(ctx).correctDose(pid, eventId, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "correction ${r.code()}")
    }

    // ── Alerts ──
    suspend fun openAlerts(ctx: Context, pid: String): List<AlertDto> = withContext(Dispatchers.IO) {
        try {
            val r = api(ctx).alerts(pid, "open")
            if (r.isSuccessful) {
                val list = r.body()!!.alerts
                val ents = list.map { AlertEntity(it.id, pid, it.severity, it.status, it.messageKey, gson.toJson(it)) }
                db(ctx).alertDao().clear(pid)
                if (ents.isNotEmpty()) db(ctx).alertDao().upsertAll(ents)
                list
            } else db(ctx).alertDao().open(pid).mapNotNull { try { gson.fromJson(it.rawJson, AlertDto::class.java) } catch (_: Exception) { null } }
        } catch (_: Exception) {
            db(ctx).alertDao().open(pid).mapNotNull { try { gson.fromJson(it.rawJson, AlertDto::class.java) } catch (_: Exception) { null } }
        }
    }

    suspend fun ackAlert(ctx: Context, pid: String, aid: String) = withContext(Dispatchers.IO) {
        api(ctx).ackAlert(pid, aid)
        // optimistic local
    }

    // ── Alert preferences §13 ──
    suspend fun listAlertPreferences(ctx: Context, pid: String): List<AlertPreferenceDto> = withContext(Dispatchers.IO) {
        try { val r = api(ctx).alertPreferences(pid); if (r.isSuccessful) r.body()?.preferences.orEmpty() else emptyList() } catch (_: Exception) { emptyList() }
    }
    suspend fun updateAlertPreferences(ctx: Context, pid: String, prefs: List<PutAlertPreferenceItem>): List<AlertPreferenceDto> = withContext(Dispatchers.IO) {
        val r = api(ctx).putAlertPreferences(pid, PutAlertPreferencesRequest(prefs))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "preferences ${r.code()}")
        r.body()!!.preferences
    }
    suspend fun generateAlerts(ctx: Context, pid: String): GeneratedCounts = withContext(Dispatchers.IO) {
        val r = api(ctx).generateAlerts(pid)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "generate ${r.code()}")
        r.body()!!.generated
    }

    // ── Adherence report §14 ──
    suspend fun getAdherenceReport(ctx: Context, pid: String, from: String, to: String, timezone: String? = null): AdherenceReportDto? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).adherence(pid, from, to, timezone); if (r.isSuccessful) r.body() else null } catch (_: Exception) { null }
    }

    // ── Exports §15 ──
    suspend fun createExport(ctx: Context, pid: String, from: String?, to: String?, includeSymptoms: Boolean?): ExportDto = withContext(Dispatchers.IO) {
        val r = api(ctx).createExport(pid, CreateExportRequest(from, to, includeSymptoms))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "export create ${r.code()}")
        r.body()!!.export
    }
    suspend fun getExport(ctx: Context, pid: String, exportId: String): ExportDto? = withContext(Dispatchers.IO) {
        try {
            val r = api(ctx).getExport(pid, exportId)
            if (r.isSuccessful) r.body()?.export
            else if (r.code() == 410) throw Exception("410:expired")
            else null
        } catch (e: Exception) { if (e.message?.contains("410") == true) throw e else null }
    }
    suspend fun downloadExport(ctx: Context, pid: String, exportId: String, token: String?): ByteArray? = withContext(Dispatchers.IO) {
        val r = api(ctx).downloadExport(pid, exportId, token)
        if (!r.isSuccessful) {
            if (r.code() == 410) throw Exception("410:expired")
            if (r.code() == 403) throw Exception("403:invalid token")
            if (r.code() == 404) throw Exception("404:not ready")
            throw Exception(r.errorBody()?.string() ?: "download ${r.code()}")
        }
        r.body()?.bytes()
    }
    suspend fun deleteExport(ctx: Context, pid: String, exportId: String): Boolean = withContext(Dispatchers.IO) {
        try { val r = api(ctx).deleteExport(pid, exportId); r.isSuccessful || r.code() == 204 } catch (_: Exception) { false }
    }

    // ── Inventory / adherence ──
    suspend fun getInventory(ctx: Context, pid: String, mid: String): InventoryResponse? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).inventory(pid, mid); if (r.isSuccessful) r.body() else null } catch (_: Exception) { null }
    }
    suspend fun createInventoryTransaction(ctx: Context, pid: String, mid: String, req: InventoryTxRequest): InventoryTransactionDto? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).inventoryTx(pid, mid, req); if (r.isSuccessful) null else null } catch (_: Exception) { null }
    }
    suspend fun listInventoryTransactions(ctx: Context, pid: String, mid: String, cursor: String? = null, limit: Int? = null): InventoryTransactionsResponse? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).inventoryTransactions(pid, mid, cursor, limit); if (r.isSuccessful) r.body() else null } catch (_: Exception) { null }
    }
    suspend fun updateInventorySettings(ctx: Context, pid: String, mid: String, req: InventorySettingsRequest): InventoryAccount? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).updateInventorySettings(pid, mid, req); if (r.isSuccessful) r.body()?.account else null } catch (_: Exception) { null }
    }

    // ── Expirations ──
    suspend fun listExpirations(ctx: Context, pid: String, mid: String): List<ExpirationDto> = withContext(Dispatchers.IO) {
        try { val r = api(ctx).listExpirations(pid, mid); if (r.isSuccessful) r.body()?.expirations.orEmpty() else emptyList() } catch (_: Exception) { emptyList() }
    }
    suspend fun createExpiration(ctx: Context, pid: String, mid: String, req: CreateExpirationRequest): ExpirationDto? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).createExpiration(pid, mid, req); if (r.isSuccessful) r.body() else null } catch (_: Exception) { null }
    }
    suspend fun updateExpiration(ctx: Context, pid: String, mid: String, eid: String, req: UpdateExpirationRequest): ExpirationDto? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).updateExpiration(pid, mid, eid, req); if (r.isSuccessful) r.body() else null } catch (_: Exception) { null }
    }
    suspend fun deleteExpiration(ctx: Context, pid: String, mid: String, eid: String): Boolean = withContext(Dispatchers.IO) {
        try { val r = api(ctx).deleteExpiration(pid, mid, eid); r.isSuccessful } catch (_: Exception) { false }
    }

    // ── Sync cursor ──
    suspend fun incrementalSync(ctx: Context, pid: String) = withContext(Dispatchers.IO) {
        val cursor = TokenStore.getSyncCursor(ctx, pid)
        val r = try { api(ctx).sync(pid, cursor) } catch (_: Exception) { null }
        if (r != null && r.isSuccessful) {
            val body = r.body()!!
            body.nextCursor?.let { TokenStore.setSyncCursor(ctx, pid, it) }
            // invalidate caches if changes include medication/occurrence
            if (body.changes.any { it.type in listOf("medication", "schedule_version", "occurrence") }) {
                // trigger refresh on next load
            }
        }
    }

    // ── Symptoms (§11) ──
    suspend fun listSymptomLogs(ctx: Context, pid: String, cursor: String? = null, limit: Int? = null): SymptomLogsResponse? = withContext(Dispatchers.IO) {
        try { val r = api(ctx).listSymptomLogs(pid, cursor, limit); if (r.isSuccessful) r.body() else null } catch (_: Exception) { null }
    }
    suspend fun createSymptomLog(ctx: Context, pid: String, req: CreateSymptomRequest): SymptomLogDto = withContext(Dispatchers.IO) {
        val r = api(ctx).createSymptomLog(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "create symptom ${r.code()}")
        r.body()!!
    }
    suspend fun correctSymptomLog(ctx: Context, pid: String, logId: String, req: CorrectionSymptomRequest): SymptomLogDto = withContext(Dispatchers.IO) {
        val r = api(ctx).correctSymptomLog(pid, logId, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "correct symptom ${r.code()}")
        r.body()!!
    }

    // ── Injection sites (§12) ──
    suspend fun listInjectionLogs(ctx: Context, pid: String, medicationId: String? = null, limit: Int? = null): List<InjectionLogDto> = withContext(Dispatchers.IO) {
        try { val r = api(ctx).listInjectionLogs(pid, medicationId, limit); if (r.isSuccessful) r.body()?.logs.orEmpty() else emptyList() } catch (_: Exception) { emptyList() }
    }
    suspend fun createInjectionLog(ctx: Context, pid: String, req: CreateInjectionRequest): InjectionLogDto = withContext(Dispatchers.IO) {
        val r = api(ctx).createInjectionLog(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "create injection ${r.code()}")
        r.body()!!
    }

    // ── Intelligence ──
    suspend fun labelInterpretation(ctx: Context, pid: String, base64: String?, ocrText: String?): LabelInterpretationResponse = withContext(Dispatchers.IO) {
        val req = LabelInterpretationRequest(imageBase64 = base64, mimeType = if (base64 != null) "image/jpeg" else null, ocrText = ocrText)
        val r = api(ctx).labelInterpretation(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "intelligence ${r.code()}")
        r.body()!!
    }
    suspend fun instructionParse(ctx: Context, pid: String, text: String, locale: String? = null): InstructionParseResponse = withContext(Dispatchers.IO) {
        val req = InstructionParseRequest(text, locale)
        val r = api(ctx).instructionParse(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "instruction_parse ${r.code()}")
        r.body()!!
    }
    suspend fun createConversation(ctx: Context, pid: String, title: String? = null): CreateConversationResponse = withContext(Dispatchers.IO) {
        val r = api(ctx).createConversation(pid, CreateConversationRequest(title))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "create_conversation ${r.code()}")
        r.body()!!
    }
    suspend fun deleteConversation(ctx: Context, pid: String, cid: String): Boolean = withContext(Dispatchers.IO) {
        val r = api(ctx).deleteConversation(pid, cid)
        r.isSuccessful || r.code() == 204
    }
    suspend fun sendMessage(ctx: Context, pid: String, cid: String, content: String, role: String = "user"): CreateMessageResponse = withContext(Dispatchers.IO) {
        val r = api(ctx).sendMessage(pid, cid, CreateMessageRequest(content, role))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "send_message ${r.code()}")
        r.body()!!
    }
    suspend fun createScheduleDraft(ctx: Context, pid: String, medicationId: String? = null, constraints: String? = null, timezone: String? = null, wakeTime: String? = null, sleepTime: String? = null): ScheduleDraftResponse = withContext(Dispatchers.IO) {
        val req = ScheduleDraftRequest(medicationId, constraints, timezone, wakeTime, sleepTime)
        val r = api(ctx).createScheduleDraft(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "schedule_draft ${r.code()}")
        r.body()!!
    }
    suspend fun createSummary(ctx: Context, pid: String, type: String, from: String? = null, to: String? = null): SummaryResponse = withContext(Dispatchers.IO) {
        val req = SummaryRequest(type, from, to)
        val r = api(ctx).createSummary(pid, req)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "summary ${r.code()}")
        r.body()!!
    }
    suspend fun getProposal(ctx: Context, pid: String, proposalId: String): ProposalDto = withContext(Dispatchers.IO) {
        val r = api(ctx).getProposal(pid, proposalId)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "get_proposal ${r.code()}")
        r.body()!!.proposal
    }
    suspend fun confirmProposal(ctx: Context, pid: String, proposalId: String, payloadHash: String, expectedVersions: Map<String,Int>? = null): ProposalDto = withContext(Dispatchers.IO) {
        val r = api(ctx).confirmProposal(pid, proposalId, ConfirmProposalRequest(payloadHash, expectedVersions))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "confirm_proposal ${r.code()}")
        r.body()!!.proposal
    }
    suspend fun rejectProposal(ctx: Context, pid: String, proposalId: String): ProposalDto = withContext(Dispatchers.IO) {
        val r = api(ctx).rejectProposal(pid, proposalId)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "reject_proposal ${r.code()}")
        r.body()!!.proposal
    }

    // ── Drug normalization §6 (public, no auth) ──
    suspend fun searchDrugs(ctx: Context, q: String): List<DrugConceptDto> = withContext(Dispatchers.IO) {
        val trimmed = q.trim()
        if (trimmed.length < 2 || trimmed.length > 100) return@withContext emptyList()
        val r = api(ctx).drugSearch(trimmed)
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "drugSearch ${r.code()}")
        // primary structured list or fallback to empty; server may return RxNorm nested drugGroup — handled leniently as empty
        val body = r.body()
        body?.allConcepts().orEmpty().ifEmpty {
            // attempt to extract from raw nested shape if Gson didn't map: try via generic map fallback by reading error body? For now return empty
            emptyList()
        }
    }
    suspend fun resolveNdc(ctx: Context, ndc: String): ConceptDto = withContext(Dispatchers.IO) {
        val n = ndc.trim()
        if (n.length < 8 || n.length > 20) throw Exception("NDC must be 8..20 chars")
        val r = api(ctx).resolveNdc(ResolveNdcRequest(n))
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "resolveNdc ${r.code()}")
        val resp = r.body() ?: throw Exception("empty resolveNdc response")
        resp.resolved() ?: resp.concept ?: ConceptDto(resp.rxcui ?: "", resp.name)
    }
    suspend fun getConcept(ctx: Context, rxcui: String): ConceptDto = withContext(Dispatchers.IO) {
        if (!Regex("^\\d+$").matches(rxcui.trim())) throw Exception("rxcui must be ^\\d+$")
        val r = api(ctx).getConcept(rxcui.trim())
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "getConcept ${r.code()}")
        r.body() ?: throw Exception("empty concept")
    }

    // ── Health & meta §2 (public) ──
    suspend fun getHealthLive(ctx: Context): HealthResponse = withContext(Dispatchers.IO) {
        val r = api(ctx).healthLive()
        if (r.isSuccessful) r.body() ?: HealthResponse("ok")
        else {
            val err = r.errorBody()?.string().orEmpty()
            // try parse status from error json
            try { gson.fromJson(err, HealthResponse::class.java) } catch (_: Exception) { throw Exception(err.ifBlank { "health/live ${r.code()}" }) }
        }
    }
    suspend fun getHealthReady(ctx: Context): HealthResponse = withContext(Dispatchers.IO) {
        val r = api(ctx).healthReady()
        if (r.isSuccessful) r.body() ?: HealthResponse("ready")
        else if (r.code() == 503) {
            val err = r.errorBody()?.string().orEmpty()
            try { gson.fromJson(err, HealthResponse::class.java).takeIf { it.status.isNotBlank() } ?: HealthResponse("not_ready") } catch (_: Exception) { HealthResponse("not_ready") }
        } else throw Exception(r.errorBody()?.string() ?: "health/ready ${r.code()}")
    }
    suspend fun getVersion(ctx: Context): VersionResponse = withContext(Dispatchers.IO) {
        val r = api(ctx).version()
        if (!r.isSuccessful) throw Exception(r.errorBody()?.string() ?: "version ${r.code()}")
        r.body() ?: throw Exception("empty version")
    }
}
