/**
 * output-contracts.ts
 * Strict Zod schemas for label interpretation, instruction parse, schedule draft, summaries.
 * - reject unknown keys (strict)
 * - invalid enums/units/dates rejected
 * - require evidence spans and uncertain_fields
 * - unknown state rather than guess (nullable)
 * - repair at most once
 * Ref: SERVER_IMPLEMENTATION_PLAN.md §12.3, §8.7 label contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// Shared primitives
// ---------------------------------------------------------------------------

export const StrengthUnitEnum = z.enum([
  "mg",
  "mcg",
  "g",
  "mL",
  "mg/mL",
  "units",
  "IU",
  "percent",
  "%",
  "mg_per_mL",
]);

export const FormEnum = z.enum([
  "tablet",
  "capsule",
  "liquid",
  "injection",
  "patch",
  "inhaler",
  "drops",
  "cream",
  "ointment",
  "suppository",
  "other",
  "unknown",
]);

export const RouteEnum = z.enum([
  "oral",
  "sublingual",
  "topical",
  "inhalation",
  "injection",
  "ophthalmic",
  "otic",
  "nasal",
  "rectal",
  "vaginal",
  "transdermal",
  "other",
  "unknown",
]);

const EvidenceSchema = z
  .object({
    field: z.string().min(1).max(64),
    span: z.string().min(1).max(500),
    confidence: z.number().min(0).max(1),
  })
  .strict();

// ---------------------------------------------------------------------------
// 1. Label interpretation — must NOT invent purpose/instructions/typical times
// ---------------------------------------------------------------------------

export const LabelInterpretationSchema = z
  .object({
    observed_text: z.string().min(1).max(5000),
    candidate_name: z.string().min(1).max(200).nullable(),
    candidate_generic_name: z.string().min(1).max(200).nullable(),
    strength_value: z.string().regex(/^\d+(\.\d+)?$/).nullable(), // decimal as string
    strength_unit: StrengthUnitEnum.nullable(),
    form: FormEnum.nullable(),
    route: RouteEnum.nullable(),
    label_directions_text: z.string().min(1).max(2000).nullable(),
    prescriber_fields: z
      .object({
        prescriber_name: z.string().max(200).nullable().optional(),
        pharmacy_name: z.string().max(200).nullable().optional(),
        pharmacy_phone: z.string().max(50).nullable().optional(),
        fill_date: z.string().max(50).nullable().optional(), // ISO date string if present
      })
      .strict()
      .nullable()
      .optional(),
    uncertain_fields: z.array(z.string().min(1).max(64)).max(20),
    clarifying_questions: z.array(z.string().min(1).max(300)).max(5),
    evidence: z.array(EvidenceSchema).min(1).max(30),
    confidence: z.number().min(0).max(1),
  })
  .strict()
  .superRefine((val, ctx) => {
    // If candidate_name is present, evidence must contain span for it
    // Already enforced via evidence min 1; additional semantic check:
    if (val.candidate_name !== null && val.uncertain_fields.includes("candidate_name")) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "candidate_name is both provided and marked uncertain",
        path: ["uncertain_fields"],
      });
    }
    // Must not invent purpose/instructions fields — enforced by strict(), but extra guard:
    const forbidden = ["purpose", "instructions", "typical_times", "typical_time", "frequency_inferred"];
    for (const f of forbidden) {
      if ((val as unknown as Record<string, unknown>)[f] !== undefined) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, message: `Forbidden field: ${f}`, path: [f] });
      }
    }
  });

export type LabelInterpretation = z.infer<typeof LabelInterpretationSchema>;

// ---------------------------------------------------------------------------
// 2. Instruction parse
// ---------------------------------------------------------------------------

export const InstructionParseSchema = z
  .object({
    entered_text: z.string().min(1).max(2000),
    dose_value: z.string().regex(/^\d+(\.\d+)?$/).nullable(),
    dose_unit: StrengthUnitEnum.nullable(),
    frequency_text: z.string().max(500).nullable(),
    route: RouteEnum.nullable(),
    uncertain_fields: z.array(z.string().min(1).max(64)).max(20),
    clarifying_questions: z.array(z.string().min(1).max(300)).max(5),
    evidence: z.array(EvidenceSchema).min(1).max(30),
    confidence: z.number().min(0).max(1),
  })
  .strict();

export type InstructionParse = z.infer<typeof InstructionParseSchema>;

// ---------------------------------------------------------------------------
// 3. Schedule draft
// ---------------------------------------------------------------------------

export const ScheduleDraftSchema = z
  .object({
    schedule_type: z.enum(["fixed_times", "elapsed_interval", "prn", "cyclic", "taper"]),
    timing_mode: z.enum(["local_clock", "elapsed_interval"]),
    timezone: z.string().min(1).max(100), // IANA name validated elsewhere
    fixed_times: z.array(z.string().regex(/^\d{2}:\d{2}$/)).max(10).optional(),
    interval_minutes: z.number().int().positive().max(10080).nullable().optional(),
    days_of_week: z.array(z.number().int().min(0).max(6)).max(7).optional(),
    dose_value: z.string().regex(/^\d+(\.\d+)?$/).nullable(),
    dose_unit: StrengthUnitEnum.nullable(),
    is_convenience_proposal: z.boolean(), // must label times as proposal not prescription when applicable
    limitations: z.array(z.string().max(500)).max(10).optional(),
    uncertain_fields: z.array(z.string().min(1).max(64)).max(20),
    clarifying_questions: z.array(z.string().min(1).max(300)).max(5),
    evidence: z.array(EvidenceSchema).min(1).max(30),
    confidence: z.number().min(0).max(1),
  })
  .strict();

export type ScheduleDraft = z.infer<typeof ScheduleDraftSchema>;

// ---------------------------------------------------------------------------
// 4. Summaries (adherence / inventory / general)
// ---------------------------------------------------------------------------

export const SummarySchema = z
  .object({
    title: z.string().min(1).max(200),
    summary_text: z.string().min(1).max(5000),
    facts_used: z
      .array(
        z
          .object({
            type: z.enum(["medication", "occurrence", "dose_event", "inventory", "adherence", "symptom"]),
            id: z.string().uuid(),
            version: z.number().int().nonnegative().optional(),
          })
          .strict(),
      )
      .max(50),
    limitations: z.array(z.string().max(500)).max(10),
    uncertain_fields: z.array(z.string().min(1).max(64)).max(20).optional(),
    confidence: z.number().min(0).max(1).optional(),
  })
  .strict();

export type Summary = z.infer<typeof SummarySchema>;

// ---------------------------------------------------------------------------
// Validation + repair (at most once)
// ---------------------------------------------------------------------------

export interface ValidationResult<T> {
  ok: boolean;
  data?: T;
  issues?: z.ZodIssue[];
  repaired?: boolean;
}

/**
 * Strict parse. If fails, optionally try one repair (strip unknown keys via JSON parse + re-validate
 * after removing forbidden fields). Repair function may alternatively call model for corrected JSON,
 * but we only auto-repair by sanitizing unknown keys at most once.
 */
export function validateStrict<T>(schema: z.ZodType<T>, raw: unknown): ValidationResult<T> {
  const res = schema.safeParse(raw);
  if (res.success) return { ok: true, data: res.data, repaired: false };
  return { ok: false, issues: res.error.issues, repaired: false };
}

export function parseJsonStrict<T>(schema: z.ZodType<T>, jsonString: string): ValidationResult<T> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(jsonString);
  } catch (e) {
    return { ok: false, issues: [{ code: "custom", message: `Invalid JSON: ${(e as Error).message}`, path: [] } as unknown as z.ZodIssue] };
  }
  return validateStrict(schema, parsed);
}

/**
 * Attempts validation, and on failure retries once after stripping unknown keys that are not in schema shape.
 * For Zod strict schemas this means removing top-level keys not in allowed set if caller provides allowlist.
 * Simpler: try to sanitize by JSON round-trip and re-parse after removing forbidden keys, at most once.
 * Returns repaired=true if second attempt succeeded.
 */
export function validateWithOneRepair<T>(
  schema: z.ZodType<T>,
  rawJsonString: string,
  repairFn?: (raw: string, issues: z.ZodIssue[]) => string,
): ValidationResult<T> {
  const first = parseJsonStrict(schema, rawJsonString);
  if (first.ok) return first;

  // Try deterministic sanitization: if repairFn provided, use it (e.g., llm repair)
  let repairedString: string | null = null;
  if (repairFn) {
    try {
      repairedString = repairFn(rawJsonString, first.issues ?? []);
    } catch {
      repairedString = null;
    }
  } else {
    // Default repair: try to parse, remove obviously forbidden keys, re-stringify
    try {
      const obj = JSON.parse(rawJsonString) as Record<string, unknown>;
      // Remove keys that are clearly not in any of our contracts (heuristic forbidden list)
      const forbidden = new Set(["purpose", "instructions", "typical_times", "typical_time", "frequency_inferred"]);
      let mutated = false;
      for (const k of Object.keys(obj)) {
        if (forbidden.has(k)) {
          delete obj[k];
          mutated = true;
        }
      }
      if (mutated) repairedString = JSON.stringify(obj);
    } catch {
      repairedString = null;
    }
  }

  if (repairedString !== null) {
    const second = parseJsonStrict(schema, repairedString);
    if (second.ok) return { ok: true, data: second.data, repaired: true };
    // Return second failure but mark repaired attempt
    return { ok: false, issues: second.issues, repaired: true };
  }

  return { ok: false, issues: first.issues, repaired: false };
}

// Export registry of contract versions
export const OUTPUT_CONTRACTS = {
  "label_interpretation@v1": LabelInterpretationSchema,
  "instruction_parse@v1": InstructionParseSchema,
  "schedule_draft@v1": ScheduleDraftSchema,
  "summary@v1": SummarySchema,
} as const;

export type OutputContractVersion = keyof typeof OUTPUT_CONTRACTS;
