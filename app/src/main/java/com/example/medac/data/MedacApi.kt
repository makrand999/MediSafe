package com.example.medac.data

import retrofit2.Response
import retrofit2.http.*

interface MedacApi {
    // Auth
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>
    @POST("auth/verify-email") suspend fun verifyEmail(@Body body: VerifyEmailRequest): Response<Map<String, Any>>
    @POST("auth/resend-verification") suspend fun resendVerification(@Body body: ResendVerificationRequest): Response<Map<String, Any>>
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): Response<LoginResponse>
    @POST("auth/mfa/verify") suspend fun mfaVerify(@Body body: MfaVerifyRequest): Response<LoginResponse>
    @POST("auth/refresh") suspend fun refresh(@Body body: RefreshRequest): Response<LoginResponse>
    @POST("auth/logout") suspend fun logout(@Body body: Map<String, String>? = null): Response<Map<String, Any>>
    @POST("auth/password/forgot") suspend fun forgot(@Body body: ForgotRequest): Response<Map<String, Any>>

    // Patients
    @GET("patients") suspend fun listPatients(): Response<PatientsResponse>
    @POST("patients") suspend fun createPatient(@Body body: CreatePatientRequest): Response<PatientDto>
    @GET("patients/{id}") suspend fun getPatient(@Path("id") id: String): Response<PatientDto>
    @PATCH("patients/{id}") suspend fun patchPatient(@Path("id") id: String, @Body body: PatientPatchRequest): Response<PatientDto>
    @POST("patients/{id}/archive") suspend fun archivePatient(@Path("id") id: String): Response<Map<String, Any>>
    @POST("patients/{id}/deletion-request") suspend fun deletionRequest(@Path("id") id: String): Response<Map<String, Any>>

    // Memberships §4.3
    @GET("patients/{pid}/memberships") suspend fun listMemberships(@Path("pid") pid: String): Response<MembershipsResponse>
    @PATCH("patients/{pid}/memberships/{mid}") suspend fun updateMembership(@Path("pid") pid: String, @Path("mid") mid: String, @Body body: UpdateMembershipRequest): Response<Map<String, Any>>
    @DELETE("patients/{pid}/memberships/{mid}") suspend fun deleteMembership(@Path("pid") pid: String, @Path("mid") mid: String): Response<Void>

    // Invites §4.4
    @POST("patients/{pid}/invites") suspend fun createInvite(@Path("pid") pid: String, @Body body: CreateInviteRequest): Response<CreateInviteResponse>
    @GET("patients/{pid}/invites") suspend fun listInvites(@Path("pid") pid: String): Response<InvitesResponse>
    @DELETE("patients/{pid}/invites/{inviteId}") suspend fun deleteInvite(@Path("pid") pid: String, @Path("inviteId") inviteId: String): Response<Void>
    @POST("invites/{token}/accept") suspend fun acceptInvite(@Path("token") token: String): Response<AcceptInviteResponse>

    // Auth extended §3.7-3.10
    @POST("auth/logout-all") suspend fun logoutAll(): Response<Map<String, Any>>
    @POST("auth/password/reset") suspend fun passwordReset(@Body body: PasswordResetRequest): Response<Map<String, Any>>
    @POST("auth/password/change") suspend fun passwordChange(@Body body: PasswordChangeRequest): Response<Map<String, Any>>
    @GET("auth/sessions") suspend fun listSessions(): Response<SessionsResponse>
    @DELETE("auth/sessions/{sessionId}") suspend fun deleteSession(@Path("sessionId") sessionId: String): Response<Void>
    @POST("auth/mfa/totp/setup") suspend fun totpSetup(): Response<TotpSetupResponse>
    @POST("auth/mfa/totp/confirm") suspend fun totpConfirm(@Body body: ConfirmTotpRequest): Response<ConfirmTotpResponse>
    @DELETE("auth/mfa/totp") suspend fun totpDelete(): Response<Void>

    // Medications
    @GET("patients/{pid}/medications") suspend fun listMedications(
        @Path("pid") pid: String,
        @Query("include_archived") includeArchived: Boolean? = null
    ): Response<MedicationsResponse>
    @POST("patients/{pid}/medications") suspend fun createMedication(
        @Path("pid") pid: String, @Body body: CreateMedicationRequest
    ): Response<MedicationDto>
    @POST("patients/{pid}/medications/{mid}/pause") suspend fun pauseMedication(@Path("pid") pid: String, @Path("mid") mid: String): Response<Map<String, Any>>
    @POST("patients/{pid}/medications/{mid}/resume") suspend fun resumeMedication(@Path("pid") pid: String, @Path("mid") mid: String): Response<Map<String, Any>>
    @POST("patients/{pid}/medications/{mid}/archive") suspend fun archiveMedication(@Path("pid") pid: String, @Path("mid") mid: String): Response<Map<String, Any>>
    @PATCH("patients/{pid}/medications/{mid}") suspend fun updateMedication(@Path("pid") pid: String, @Path("mid") mid: String, @Body body: UpdateMedicationRequest): Response<MedicationDto>
    @PATCH("patients/{pid}/medications/{mid}") suspend fun updateMedicationMap(@Path("pid") pid: String, @Path("mid") mid: String, @Body body: Map<String, Any>): Response<MedicationDto>

    // Schedules
    @GET("patients/{pid}/medications/{mid}/schedules") suspend fun listSchedules(
        @Path("pid") pid: String, @Path("mid") mid: String
    ): Response<SchedulesResponse>
    @GET("patients/{pid}/medications/{mid}/schedules/{versionId}") suspend fun getScheduleVersion(
        @Path("pid") pid: String, @Path("mid") mid: String, @Path("versionId") versionId: String
    ): Response<ScheduleVersionDto>
    @POST("patients/{pid}/medications/{mid}/schedules") suspend fun createSchedule(
        @Path("pid") pid: String, @Path("mid") mid: String, @Body body: CreateScheduleRequest
    ): Response<ScheduleResponse>
    @POST("patients/{pid}/medications/{mid}/schedules/{versionId}/supersede") suspend fun supersedeSchedule(
        @Path("pid") pid: String, @Path("mid") mid: String, @Path("versionId") versionId: String, @Body body: CreateScheduleRequest
    ): Response<ScheduleResponse>
    @GET("patients/{pid}/occurrences") suspend fun occurrences(
        @Path("pid") pid: String,
        @Query("from") from: String?,
        @Query("to") to: String?,
        @Query("limit") limit: Int? = null
    ): Response<OccurrencesResponse>

    // Doses
    @POST("patients/{pid}/dose-events") suspend fun createDoseEvent(
        @Path("pid") pid: String, @Body body: CreateDoseEventRequest
    ): Response<DoseEventResponse>
    @POST("patients/{pid}/dose-events/batch") suspend fun batchDoseEvents(
        @Path("pid") pid: String, @Body body: BatchDoseRequest
    ): Response<Map<String, Any>>
    @GET("patients/{pid}/dose-events") suspend fun listDoseEvents(
        @Path("pid") pid: String, @Query("from") from: String?, @Query("to") to: String?
    ): Response<DoseEventsResponse>
    @POST("patients/{pid}/dose-events/{eid}/corrections") suspend fun correctDose(
        @Path("pid") pid: String, @Path("eid") eid: String, @Body body: CorrectionRequest
    ): Response<Map<String, Any>>

    // Sync
    @GET("patients/{pid}/sync") suspend fun sync(@Path("pid") pid: String, @Query("cursor") cursor: String?): Response<SyncResponse>

    // Alerts
    @GET("patients/{pid}/alerts") suspend fun alerts(@Path("pid") pid: String, @Query("status") status: String?): Response<AlertsResponse>
    @POST("patients/{pid}/alerts/{aid}/acknowledge") suspend fun ackAlert(@Path("pid") pid: String, @Path("aid") aid: String): Response<Map<String, Any>>
    @GET("patients/{pid}/alert-preferences") suspend fun alertPreferences(@Path("pid") pid: String): Response<AlertPreferencesResponse>
    @PUT("patients/{pid}/alert-preferences") suspend fun putAlertPreferences(@Path("pid") pid: String, @Body body: PutAlertPreferencesRequest): Response<AlertPreferencesResponse>
    @POST("patients/{pid}/alerts/generate") suspend fun generateAlerts(@Path("pid") pid: String): Response<GenerateAlertsResponse>

    // Inventory
    @GET("patients/{pid}/medications/{mid}/inventory") suspend fun inventory(@Path("pid") pid: String, @Path("mid") mid: String): Response<InventoryResponse>
    @POST("patients/{pid}/medications/{mid}/inventory/transactions") suspend fun inventoryTx(
        @Path("pid") pid: String, @Path("mid") mid: String, @Body body: InventoryTxRequest
    ): Response<Map<String, Any>>
    @GET("patients/{pid}/medications/{mid}/inventory/transactions") suspend fun inventoryTransactions(
        @Path("pid") pid: String, @Path("mid") mid: String,
        @Query("cursor") cursor: String? = null, @Query("limit") limit: Int? = null
    ): Response<InventoryTransactionsResponse>
    @PATCH("patients/{pid}/medications/{mid}/inventory/settings") suspend fun updateInventorySettings(
        @Path("pid") pid: String, @Path("mid") mid: String, @Body body: InventorySettingsRequest
    ): Response<InventorySettingsResponse>

    // Expirations
    @GET("patients/{pid}/medications/{mid}/expirations") suspend fun listExpirations(
        @Path("pid") pid: String, @Path("mid") mid: String
    ): Response<ExpirationsResponse>
    @POST("patients/{pid}/medications/{mid}/expirations") suspend fun createExpiration(
        @Path("pid") pid: String, @Path("mid") mid: String, @Body body: CreateExpirationRequest
    ): Response<ExpirationDto>
    @PATCH("patients/{pid}/medications/{mid}/expirations/{eid}") suspend fun updateExpiration(
        @Path("pid") pid: String, @Path("mid") mid: String, @Path("eid") eid: String, @Body body: UpdateExpirationRequest
    ): Response<ExpirationDto>
    @DELETE("patients/{pid}/medications/{mid}/expirations/{eid}") suspend fun deleteExpiration(
        @Path("pid") pid: String, @Path("mid") mid: String, @Path("eid") eid: String
    ): Response<Void>

    // Reports §14
    @GET("patients/{pid}/reports/adherence") suspend fun adherence(
        @Path("pid") pid: String, @Query("from") from: String, @Query("to") to: String,
        @Query("timezone") timezone: String? = null
    ): Response<AdherenceReportDto>

    // Exports §15
    @POST("patients/{pid}/exports") suspend fun createExport(@Path("pid") pid: String, @Body body: CreateExportRequest): Response<CreateExportResponse>
    @GET("patients/{pid}/exports/{exportId}") suspend fun getExport(@Path("pid") pid: String, @Path("exportId") exportId: String): Response<GetExportResponse>
    @GET("patients/{pid}/exports/{exportId}/download") suspend fun downloadExport(@Path("pid") pid: String, @Path("exportId") exportId: String, @Query("token") token: String?): Response<okhttp3.ResponseBody>
    @DELETE("patients/{pid}/exports/{exportId}") suspend fun deleteExport(@Path("pid") pid: String, @Path("exportId") exportId: String): Response<Void>

    // Intelligence
    @POST("patients/{pid}/intelligence/label-interpretations") suspend fun labelInterpretation(
        @Path("pid") pid: String, @Body body: LabelInterpretationRequest
    ): Response<LabelInterpretationResponse>

    @POST("patients/{pid}/intelligence/instruction-parses") suspend fun instructionParse(
        @Path("pid") pid: String, @Body body: InstructionParseRequest
    ): Response<InstructionParseResponse>

    @POST("patients/{pid}/intelligence/conversations") suspend fun createConversation(
        @Path("pid") pid: String, @Body body: CreateConversationRequest
    ): Response<CreateConversationResponse>
    @DELETE("patients/{pid}/intelligence/conversations/{cid}") suspend fun deleteConversation(
        @Path("pid") pid: String, @Path("cid") cid: String
    ): Response<Void>
    @POST("patients/{pid}/intelligence/conversations/{cid}/messages") suspend fun sendMessage(
        @Path("pid") pid: String, @Path("cid") cid: String, @Body body: CreateMessageRequest
    ): Response<CreateMessageResponse>

    @POST("patients/{pid}/intelligence/schedule-drafts") suspend fun createScheduleDraft(
        @Path("pid") pid: String, @Body body: ScheduleDraftRequest
    ): Response<ScheduleDraftResponse>

    @POST("patients/{pid}/intelligence/summaries") suspend fun createSummary(
        @Path("pid") pid: String, @Body body: SummaryRequest
    ): Response<SummaryResponse>

    @GET("patients/{pid}/intelligence/proposals/{proposalId}") suspend fun getProposal(
        @Path("pid") pid: String, @Path("proposalId") proposalId: String
    ): Response<ProposalEnvelope>
    @POST("patients/{pid}/intelligence/proposals/{proposalId}/confirm") suspend fun confirmProposal(
        @Path("pid") pid: String, @Path("proposalId") proposalId: String, @Body body: ConfirmProposalRequest
    ): Response<ProposalEnvelope>
    @POST("patients/{pid}/intelligence/proposals/{proposalId}/reject") suspend fun rejectProposal(
        @Path("pid") pid: String, @Path("proposalId") proposalId: String
    ): Response<ProposalEnvelope>

    // Symptoms (§11)
    @GET("patients/{pid}/symptom-logs") suspend fun listSymptomLogs(
        @Path("pid") pid: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<SymptomLogsResponse>

    @POST("patients/{pid}/symptom-logs") suspend fun createSymptomLog(
        @Path("pid") pid: String,
        @Body body: CreateSymptomRequest
    ): Response<SymptomLogDto>

    @POST("patients/{pid}/symptom-logs/{lid}/corrections") suspend fun correctSymptomLog(
        @Path("pid") pid: String,
        @Path("lid") lid: String,
        @Body body: CorrectionSymptomRequest
    ): Response<SymptomLogDto>

    // Injection sites (§12)
    @GET("patients/{pid}/injection-site-logs") suspend fun listInjectionLogs(
        @Path("pid") pid: String,
        @Query("medication_id") medicationId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<InjectionLogsResponse>

    @POST("patients/{pid}/injection-site-logs") suspend fun createInjectionLog(
        @Path("pid") pid: String,
        @Body body: CreateInjectionRequest
    ): Response<InjectionLogDto>

    // Drug normalization §6
    @GET("drug-normalization/search") suspend fun drugSearch(@Query("q") q: String): Response<DrugSearchResponse>
    @POST("drug-normalization/resolve-ndc") suspend fun resolveNdc(@Body body: ResolveNdcRequest): Response<ResolveNdcResponse>
    @GET("drug-normalization/concepts/{rxcui}") suspend fun getConcept(@Path("rxcui") rxcui: String): Response<ConceptDto>

    // Health & meta §2
    @GET("health/live") suspend fun health(): Response<HealthResponse>
    @GET("health/live") suspend fun healthLive(): Response<HealthResponse>
    @GET("health/ready") suspend fun healthReady(): Response<HealthResponse>
    @GET("version") suspend fun version(): Response<VersionResponse>
}
