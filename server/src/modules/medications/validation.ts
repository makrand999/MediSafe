/**
 * Medications validation — controlled units/forms/routes §5.3, §6.3
 * Decimal quantities as strings to avoid floating loss.
 */
import { z } from "zod";

// Reuse enums from intelligence but extend dose units to include count units
export const MedicationFormEnum = z.enum([
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

export const MedicationRouteEnum = z.enum([
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

export const DoseUnitEnum = z.enum([
  "mg",
  "mcg",
  "g",
  "mL",
  "mg/mL",
  "units",
  "IU",
  "percent",
  "%",
  "tablet",
  "capsule",
  "patch",
  "puff",
  "drop",
  "suppository",
  "application",
  "actuation",
  "other",
]);

export const StrengthUnitEnum = DoseUnitEnum;

export const decimalString = z
  .string()
  .regex(/^\d+(\.\d+)?$/, "Must be decimal string")
  .max(12);

export const MedicationStatusEnum = z.enum(["draft", "active", "paused", "discontinued", "archived"]);
export const NormalizationStatusEnum = z.enum(["unresolved", "pending", "resolved", "ambiguous", "failed", "manually_confirmed"]);

export const CreateMedicationSchema = z
  .object({
    entered_name: z.string().min(1).max(200),
    entered_strength_value: decimalString.nullish(),
    entered_strength_unit: StrengthUnitEnum.nullish(),
    form: MedicationFormEnum.nullish(),
    route: MedicationRouteEnum.nullish(),
    dose_quantity_value: decimalString,
    dose_quantity_unit: DoseUnitEnum,
    indication_text: z.string().max(1000).nullish(),
    prescriber_text: z.string().max(500).nullish(),
    pharmacy_text: z.string().max(500).nullish(),
    label_instructions_text: z.string().max(2000).nullish(),
    ndc: z.string().max(20).nullish(),
    normalization_status: NormalizationStatusEnum.optional(),
    status: MedicationStatusEnum.optional(),
    high_attention_user_flag: z.boolean().optional(),
    start_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullish(),
    end_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullish(),
    drug_concept_id: z.string().uuid().nullish(),
  })
  .strict()
  .superRefine((data, ctx) => {
    if (data.entered_strength_value !== null && data.entered_strength_value !== undefined && !data.entered_strength_unit) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "entered_strength_unit required when entered_strength_value provided", path: ["entered_strength_unit"] });
    }
    if (data.start_date && data.end_date && data.end_date < data.start_date) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "end_date must be >= start_date", path: ["end_date"] });
    }
  });

export const UpdateMedicationSchema = z
  .object({
    entered_name: z.string().min(1).max(200).optional(),
    entered_strength_value: decimalString.nullish(),
    entered_strength_unit: StrengthUnitEnum.nullish(),
    form: MedicationFormEnum.nullish(),
    route: MedicationRouteEnum.nullish(),
    dose_quantity_value: decimalString.optional(),
    dose_quantity_unit: DoseUnitEnum.optional(),
    indication_text: z.string().max(1000).nullable().optional(),
    prescriber_text: z.string().max(500).nullable().optional(),
    pharmacy_text: z.string().max(500).nullable().optional(),
    label_instructions_text: z.string().max(2000).nullable().optional(),
    normalization_status: NormalizationStatusEnum.optional(),
    status: MedicationStatusEnum.optional(),
    high_attention_user_flag: z.boolean().optional(),
    start_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().optional(),
    end_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().optional(),
    drug_concept_id: z.string().uuid().nullable().optional(),
    // Optimistic concurrency
    expected_updated_at: z.string().datetime().optional(),
  })
  .strict();

export type CreateMedicationInput = z.infer<typeof CreateMedicationSchema>;
export type UpdateMedicationInput = z.infer<typeof UpdateMedicationSchema>;
