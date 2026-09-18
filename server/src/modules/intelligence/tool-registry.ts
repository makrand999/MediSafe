/**
 * tool-registry.ts
 * Allowlisted tools: 12 read + 12 proposal (spec lists 12; task says 10 proposal — we implement 12 with exact names per §8.7)
 * Zod schemas, read/proposal classification, reject unknown tools/extra args.
 */

import { z } from "zod";

export type ToolMode = "read" | "proposal";

export interface ToolDefinition {
  name: string;
  version: string;
  mode: ToolMode;
  description: string;
  requiredPermission: string; // role/action gate
  schema: z.ZodTypeAny; // strict arg schema
}

// ---------------------------------------------------------------------------
// Common arg primitives
// ---------------------------------------------------------------------------

const PatientIdSchema = z.string().uuid();
const MedicationIdSchema = z.string().uuid();

// ---------------------------------------------------------------------------
// Read tools (12)
// ---------------------------------------------------------------------------

const ListMedicationsArgs = z.object({ patientId: PatientIdSchema.optional(), status: z.enum(["active", "paused", "discontinued", "archived", "draft", "all"]).optional() }).strict();
const GetMedicationArgs = z.object({ patientId: PatientIdSchema.optional(), medicationId: MedicationIdSchema }).strict();
const GetTodayOccurrencesArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(), // local date
    timezone: z.string().min(1).optional(),
  })
  .strict();
const GetScheduleArgs = z.object({ patientId: PatientIdSchema.optional(), medicationId: MedicationIdSchema }).strict();
const PreviewScheduleArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    scheduleDraft: z.record(z.unknown()).optional(), // validated via output contract elsewhere
    from: z.string().datetime({ offset: true }).optional(),
    to: z.string().datetime({ offset: true }).optional(),
    timezone: z.string().min(1).optional(),
  })
  .strict();
const GetAdherenceReportArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    from: z.string().datetime({ offset: true }),
    to: z.string().datetime({ offset: true }),
    timezone: z.string().min(1).optional(),
  })
  .strict();
const GetInventorySummaryArgs = z.object({ patientId: PatientIdSchema.optional(), medicationId: MedicationIdSchema.optional() }).strict();
const GetExpirationSummaryArgs = z.object({ patientId: PatientIdSchema.optional(), withinDays: z.number().int().min(1).max(365).optional() }).strict();
const GetRecentDoseEventsArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema.optional(),
    limit: z.number().int().min(1).max(100).optional(),
  })
  .strict();
const GetInjectionSiteHistoryArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema.optional(),
    limit: z.number().int().min(1).max(100).optional(),
  })
  .strict();
const SearchRxnormArgs = z.object({ query: z.string().min(2).max(200), limit: z.number().int().min(1).max(20).optional() }).strict();
const ResolveNdcArgs = z.object({ ndc: z.string().min(8).max(20) }).strict();

// ---------------------------------------------------------------------------
// Proposal tools (12)
// ---------------------------------------------------------------------------

const ProposeCreateMedicationArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    enteredName: z.string().min(1).max(200),
    doseValue: z.string().regex(/^\d+(\.\d+)?$/).nullable().optional(),
    doseUnit: z.string().min(1).max(20).nullable().optional(),
    form: z.string().min(1).max(50).nullable().optional(),
    route: z.string().min(1).max(50).nullable().optional(),
    labelDirectionsText: z.string().max(2000).nullable().optional(),
    resourceVersion: z.number().int().optional(), // for idempotency
  })
  .strict();

const ProposeUpdateMedicationArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    patch: z.record(z.string(), z.unknown()),
    expectedVersion: z.number().int().nonnegative(),
  })
  .strict();

const ProposeActivateMedicationArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    expectedVersion: z.number().int().nonnegative(),
  })
  .strict();

const ProposePauseOrResumeMedicationArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    action: z.enum(["pause", "resume"]),
    reason: z.enum(["hospital", "temporary", "other"]).optional(),
    expectedVersion: z.number().int().nonnegative(),
  })
  .strict();

const ProposeDiscontinueMedicationArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    reason: z.string().max(500).optional(),
    expectedVersion: z.number().int().nonnegative(),
  })
  .strict();

const ProposeCreateOrSupersedeScheduleArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    scheduleType: z.enum(["fixed_times", "elapsed_interval", "prn", "cyclic", "taper"]),
    timingMode: z.enum(["local_clock", "elapsed_interval"]),
    timezone: z.string().min(1).max(100),
    payload: z.record(z.unknown()),
    expectedMedicationVersion: z.number().int().nonnegative().optional(),
    expectedScheduleVersion: z.number().int().nonnegative().optional(),
  })
  .strict();

const ProposeLogDoseEventArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    occurrenceId: z.string().uuid().nullable().optional(),
    eventType: z.enum(["taken", "skipped", "snoozed"]),
    actualAt: z.string().datetime({ offset: true }),
    doseValue: z.string().regex(/^\d+(\.\d+)?$/).nullable().optional(),
    doseUnit: z.string().min(1).max(20).nullable().optional(),
    clientEventId: z.string().min(1).max(100),
    deviceId: z.string().uuid(),
  })
  .strict();

const ProposeCorrectDoseEventArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    eventId: z.string().uuid(),
    correctionType: z.enum(["taken", "skipped", "cancelled", "corrected"]),
    reason: z.string().min(1).max(500),
    expectedEventVersion: z.number().int().nonnegative().optional(),
  })
  .strict();

const ProposeInventoryAdjustmentArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    medicationId: MedicationIdSchema,
    transactionType: z.enum(["fill", "manual_adjustment", "lost_or_damaged", "disposed", "transferred"]),
    quantityDelta: z.string().regex(/^-?\d+(\.\d+)?$/),
    unit: z.string().min(1).max(20),
    reasonText: z.string().max(500).optional(),
  })
  .strict();

const ProposeAlertPreferencesArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    preferences: z.array(
      z
        .object({
          alertType: z.string().min(1).max(50),
          enabled: z.boolean(),
          delayMinutes: z.number().int().min(0).max(1440).optional(),
        })
        .strict(),
    ),
  })
  .strict();

const ProposeCaregiverInviteArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    invitedEmail: z.string().email().max(200),
    role: z.enum(["manager", "contributor", "viewer"]),
  })
  .strict();

const ProposeExportArgs = z
  .object({
    patientId: PatientIdSchema.optional(),
    exportType: z.enum(["csv", "pdf"]),
    from: z.string().datetime({ offset: true }).optional(),
    to: z.string().datetime({ offset: true }).optional(),
    includeSymptoms: z.boolean().optional(),
  })
  .strict();

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

export const TOOL_REGISTRY: Record<string, ToolDefinition> = {
  // read
  list_medications: { name: "list_medications", version: "v1", mode: "read", description: "List medications for patient", requiredPermission: "medication:read", schema: ListMedicationsArgs },
  get_medication: { name: "get_medication", version: "v1", mode: "read", description: "Get single medication", requiredPermission: "medication:read", schema: GetMedicationArgs },
  get_today_occurrences: { name: "get_today_occurrences", version: "v1", mode: "read", description: "Get today occurrences", requiredPermission: "schedule:read", schema: GetTodayOccurrencesArgs },
  get_schedule: { name: "get_schedule", version: "v1", mode: "read", description: "Get schedule versions", requiredPermission: "schedule:read", schema: GetScheduleArgs },
  preview_schedule: { name: "preview_schedule", version: "v1", mode: "read", description: "Preview schedule occurrences deterministically", requiredPermission: "schedule:read", schema: PreviewScheduleArgs },
  get_adherence_report: { name: "get_adherence_report", version: "v1", mode: "read", description: "Get adherence report", requiredPermission: "adherence:read", schema: GetAdherenceReportArgs },
  get_inventory_summary: { name: "get_inventory_summary", version: "v1", mode: "read", description: "Get inventory summary", requiredPermission: "inventory:read", schema: GetInventorySummaryArgs },
  get_expiration_summary: { name: "get_expiration_summary", version: "v1", mode: "read", description: "Get expiration summary", requiredPermission: "medication:read", schema: GetExpirationSummaryArgs },
  get_recent_dose_events: { name: "get_recent_dose_events", version: "v1", mode: "read", description: "Get recent dose events", requiredPermission: "doseEvent:read", schema: GetRecentDoseEventsArgs },
  get_injection_site_history: { name: "get_injection_site_history", version: "v1", mode: "read", description: "Get injection site history", requiredPermission: "doseEvent:read", schema: GetInjectionSiteHistoryArgs },
  search_rxnorm: { name: "search_rxnorm", version: "v1", mode: "read", description: "Search RxNorm concepts", requiredPermission: "medication:read", schema: SearchRxnormArgs },
  resolve_ndc: { name: "resolve_ndc", version: "v1", mode: "read", description: "Resolve NDC", requiredPermission: "medication:read", schema: ResolveNdcArgs },

  // proposal
  propose_create_medication: { name: "propose_create_medication", version: "v1", mode: "proposal", description: "Propose creating medication", requiredPermission: "medication:create", schema: ProposeCreateMedicationArgs },
  propose_update_medication: { name: "propose_update_medication", version: "v1", mode: "proposal", description: "Propose updating medication", requiredPermission: "medication:update", schema: ProposeUpdateMedicationArgs },
  propose_activate_medication: { name: "propose_activate_medication", version: "v1", mode: "proposal", description: "Propose activating medication", requiredPermission: "medication:activate", schema: ProposeActivateMedicationArgs },
  propose_pause_or_resume_medication: { name: "propose_pause_or_resume_medication", version: "v1", mode: "proposal", description: "Propose pause/resume", requiredPermission: "medication:pause", schema: ProposePauseOrResumeMedicationArgs },
  propose_discontinue_medication: { name: "propose_discontinue_medication", version: "v1", mode: "proposal", description: "Propose discontinuing", requiredPermission: "medication:discontinue", schema: ProposeDiscontinueMedicationArgs },
  propose_create_or_supersede_schedule: { name: "propose_create_or_supersede_schedule", version: "v1", mode: "proposal", description: "Propose schedule create/supersede", requiredPermission: "schedule:create", schema: ProposeCreateOrSupersedeScheduleArgs },
  propose_log_dose_event: { name: "propose_log_dose_event", version: "v1", mode: "proposal", description: "Propose logging dose event", requiredPermission: "doseEvent:create", schema: ProposeLogDoseEventArgs },
  propose_correct_dose_event: { name: "propose_correct_dose_event", version: "v1", mode: "proposal", description: "Propose correcting dose event", requiredPermission: "doseEvent:correct", schema: ProposeCorrectDoseEventArgs },
  propose_inventory_adjustment: { name: "propose_inventory_adjustment", version: "v1", mode: "proposal", description: "Propose inventory adjustment", requiredPermission: "inventory:write", schema: ProposeInventoryAdjustmentArgs },
  propose_alert_preferences: { name: "propose_alert_preferences", version: "v1", mode: "proposal", description: "Propose alert preferences change", requiredPermission: "alertPreference:write", schema: ProposeAlertPreferencesArgs },
  propose_caregiver_invite: { name: "propose_caregiver_invite", version: "v1", mode: "proposal", description: "Propose caregiver invite", requiredPermission: "invite:create", schema: ProposeCaregiverInviteArgs },
  propose_export: { name: "propose_export", version: "v1", mode: "proposal", description: "Propose export", requiredPermission: "export:create", schema: ProposeExportArgs },
};

export function getToolDefinition(name: string): ToolDefinition | undefined {
  return TOOL_REGISTRY[name];
}

export function isReadTool(name: string): boolean {
  return TOOL_REGISTRY[name]?.mode === "read";
}

export function isProposalTool(name: string): boolean {
  return TOOL_REGISTRY[name]?.mode === "proposal";
}

export function listTools(): ToolDefinition[] {
  return Object.values(TOOL_REGISTRY);
}

export function validateToolCall(name: string, args: unknown): { valid: boolean; error?: string; issues?: z.ZodIssue[] } {
  const def = TOOL_REGISTRY[name];
  if (!def) return { valid: false, error: `Unknown tool: ${name}` };
  const res = def.schema.safeParse(args);
  if (!res.success) {
    return { valid: false, error: "Tool argument validation failed", issues: res.error.issues };
  }
  return { valid: true };
}

export function assertToolCall(name: string, args: unknown): void {
  const res = validateToolCall(name, args);
  if (!res.valid) {
    const detail = res.issues ? JSON.stringify(res.issues) : res.error;
    throw new Error(`Tool validation failed for ${name}: ${detail}`);
  }
}
