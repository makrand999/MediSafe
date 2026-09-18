/**
 * context-builders.ts
 * Purpose-specific minimal data builders: label, schedule draft, today assistant,
 * adherence, inventory, symptom timeline — enforce byte/token limit, track categories sent,
 * patient-scoped.
 * Ref: SERVER_IMPLEMENTATION_PLAN.md §12.2
 */

export const MAX_CONTEXT_BYTES = 16_000;
export const MAX_CONTEXT_TOKENS_ESTIMATE = 4_000; // ~4 chars per token heuristic

export type ContextPurpose = "label" | "schedule_draft" | "today_assistant" | "adherence" | "inventory" | "symptom_timeline";

export interface BuildOptions {
  patientId: string;
  // caller provides minimal domain objects; builders will pick only needed fields
}

export interface BuiltContext {
  purpose: ContextPurpose;
  patientId: string;
  categories: string[]; // metadata about what categories were sent
  byteLength: number;
  tokenEstimate: number;
  data: Record<string, unknown>;
}

function assertPatientScoped(patientId: string): void {
  if (!patientId || typeof patientId !== "string") throw new Error("patientId is required (patient-scoped context)");
}

function enforceLimit(ctx: BuiltContext): BuiltContext {
  if (ctx.byteLength > MAX_CONTEXT_BYTES) {
    throw Object.assign(new Error(`Context exceeds byte limit: ${ctx.byteLength} > ${MAX_CONTEXT_BYTES}`), { code: "CONTEXT_TOO_LARGE" });
  }
  if (ctx.tokenEstimate > MAX_CONTEXT_TOKENS_ESTIMATE) {
    throw Object.assign(new Error(`Context exceeds token estimate: ${ctx.tokenEstimate} > ${MAX_CONTEXT_TOKENS_ESTIMATE}`), {
      code: "CONTEXT_TOO_LARGE",
    });
  }
  return ctx;
}

function byteLen(obj: unknown): number {
  return Buffer.byteLength(JSON.stringify(obj), "utf8");
}

function tokenEst(obj: unknown): number {
  return Math.ceil(JSON.stringify(obj).length / 4);
}

// ---------------------------------------------------------------------------
// 1. Label interpretation: image/OCR only, locale, output schema
// ---------------------------------------------------------------------------

export function buildLabelContext(input: {
  patientId: string;
  locale?: string;
  ocrText?: string;
  // image not included as data — image is sent via vision channel separately
  imageMeta?: { mime: string; width?: number; height?: number };
}): BuiltContext {
  assertPatientScoped(input.patientId);
  const data: Record<string, unknown> = {
    locale: input.locale ?? "en",
    ocr_text_present: Boolean(input.ocrText),
  };
  if (input.ocrText) data.ocr_text = input.ocrText.slice(0, 5000);
  if (input.imageMeta) data.image_meta = { mime: input.imageMeta.mime };
  const categories = ["label_ocr", ...(input.imageMeta ? ["label_image_meta"] : [])];
  const ctx: BuiltContext = {
    purpose: "label",
    patientId: input.patientId,
    categories,
    byteLength: byteLen(data),
    tokenEstimate: tokenEst(data),
    data,
  };
  return enforceLimit(ctx);
}

// ---------------------------------------------------------------------------
// 2. Schedule draft: selected medication + constraints + timezone + current schedule
// ---------------------------------------------------------------------------

export function buildScheduleDraftContext(input: {
  patientId: string;
  medication: { id: string; enteredName: string; doseValue?: string; doseUnit?: string; form?: string };
  currentSchedule?: unknown | null;
  userConstraints: { frequencyText?: string; preferredTimes?: string[]; timezone: string; wakeTime?: string; sleepTime?: string };
}): BuiltContext {
  assertPatientScoped(input.patientId);
  const data: Record<string, unknown> = {
    medication: {
      id: input.medication.id,
      entered_name: input.medication.enteredName,
      dose_value: input.medication.doseValue ?? null,
      dose_unit: input.medication.doseUnit ?? null,
      form: input.medication.form ?? null,
    },
    constraints: {
      frequency_text: input.userConstraints.frequencyText ?? null,
      preferred_times: input.userConstraints.preferredTimes ?? null,
      timezone: input.userConstraints.timezone,
      wake_time: input.userConstraints.wakeTime ?? null,
      sleep_time: input.userConstraints.sleepTime ?? null,
    },
    current_schedule_present: Boolean(input.currentSchedule),
  };
  if (input.currentSchedule) data.current_schedule = input.currentSchedule;
  const categories = ["medication", "schedule_constraints", ...(input.currentSchedule ? ["current_schedule"] : [])];
  const ctx: BuiltContext = {
    purpose: "schedule_draft",
    patientId: input.patientId,
    categories,
    byteLength: byteLen(data),
    tokenEstimate: tokenEst(data),
    data,
  };
  return enforceLimit(ctx);
}

// ---------------------------------------------------------------------------
// 3. Today assistant: today occurrences and current event states only
// ---------------------------------------------------------------------------

export function buildTodayAssistantContext(input: {
  patientId: string;
  occurrences: Array<{ id: string; scheduledAtUtc: string; state: string; doseValue?: string }>;
  timezone: string;
}): BuiltContext {
  assertPatientScoped(input.patientId);
  // Limit occurrences to today only (caller should have filtered), enforce max 50
  const limited = input.occurrences.slice(0, 50).map((o) => ({
    id: o.id,
    scheduled_at_utc: o.scheduledAtUtc,
    state: o.state,
    dose_value: o.doseValue ?? null,
  }));
  const data: Record<string, unknown> = {
    timezone: input.timezone,
    occurrences: limited,
    occurrence_count: limited.length,
  };
  const categories = ["today_occurrences"];
  const ctx: BuiltContext = {
    purpose: "today_assistant",
    patientId: input.patientId,
    categories,
    byteLength: byteLen(data),
    tokenEstimate: tokenEst(data),
    data,
  };
  return enforceLimit(ctx);
}

// ---------------------------------------------------------------------------
// 4. Adherence summary: deterministic report + medication display names
// ---------------------------------------------------------------------------

export function buildAdherenceContext(input: {
  patientId: string;
  report: { from: string; to: string; eligible: number; taken: number; missed: number; adherencePct: number | null; denominatorDefinition: string };
  medicationNames: string[]; // display names only, not full records
}): BuiltContext {
  assertPatientScoped(input.patientId);
  const data: Record<string, unknown> = {
    report: input.report,
    medication_display_names: input.medicationNames.slice(0, 20),
  };
  const categories = ["adherence_report", "medication_names"];
  const ctx: BuiltContext = {
    purpose: "adherence",
    patientId: input.patientId,
    categories,
    byteLength: byteLen(data),
    tokenEstimate: tokenEst(data),
    data,
  };
  return enforceLimit(ctx);
}

// ---------------------------------------------------------------------------
// 5. Inventory summary: deterministic balances/forecast
// ---------------------------------------------------------------------------

export function buildInventoryContext(input: {
  patientId: string;
  summary: { medicationId: string; unit: string; currentBalance: string; lowStockThreshold?: string; forecastDaysRemaining?: number | null };
}): BuiltContext {
  assertPatientScoped(input.patientId);
  const data: Record<string, unknown> = { inventory_summary: input.summary };
  const categories = ["inventory_summary"];
  const ctx: BuiltContext = {
    purpose: "inventory",
    patientId: input.patientId,
    categories,
    byteLength: byteLen(data),
    tokenEstimate: tokenEst(data),
    data,
  };
  return enforceLimit(ctx);
}

// ---------------------------------------------------------------------------
// 6. Symptom timeline: selected date range timestamps + notes, non-causal instruction
// ---------------------------------------------------------------------------

export function buildSymptomTimelineContext(input: {
  patientId: string;
  from: string;
  to: string;
  entries: Array<{ occurredAt: string; note: string; linkedDoseEventId?: string | null }>;
}): BuiltContext {
  assertPatientScoped(input.patientId);
  const limited = input.entries.slice(0, 50).map((e) => ({
    occurred_at: e.occurredAt,
    note: e.note.slice(0, 500),
    linked_dose_event_id: e.linkedDoseEventId ?? null,
  }));
  const data: Record<string, unknown> = {
    range: { from: input.from, to: input.to },
    entries: limited,
    instruction: "Summarize patterns as non-causal observations only. Do not diagnose.",
  };
  const categories = ["symptom_timeline"];
  const ctx: BuiltContext = {
    purpose: "symptom_timeline",
    patientId: input.patientId,
    categories,
    byteLength: byteLen(data),
    tokenEstimate: tokenEst(data),
    data,
  };
  return enforceLimit(ctx);
}
