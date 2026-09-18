/**
 * Dose validation — Phase 7 §6.5, §8.10, §15
 */
import { z } from "zod";

export const DoseEventTypeEnum = z.enum(["taken", "skipped", "snoozed", "corrected", "cancelled"]);

export const DoseEventSchema = z
  .object({
    medication_id: z.string().uuid(),
    occurrence_id: z.string().uuid().nullable().optional(),
    event_type: DoseEventTypeEnum,
    actual_at: z.string().datetime({ offset: true }),
    timezone: z.string().min(1).max(64).optional(),
    dose_value: z.string().regex(/^\d+(\.\d+)?$/).nullable().optional(),
    dose_unit: z.string().min(1).max(32).nullable().optional(),
    note: z.string().max(2000).nullable().optional(),
    client_event_id: z.string().min(1).max(100),
    device_id: z.string().uuid().nullable().optional(),
    // For PRN without occurrence, occurrence_id is null
  })
  .strict()
  .superRefine((data, ctx) => {
    if (data.event_type === "corrected" && !data.occurrence_id) {
      // corrected should have occurrence? Actually correction via separate endpoint
    }
  });

export const BatchDoseEventSchema = z
  .object({
    events: z.array(DoseEventSchema).min(1).max(100),
  })
  .strict();

export const CorrectionSchema = z
  .object({
    correction_type: z.enum(["taken", "skipped", "cancelled", "corrected"]),
    actual_at: z.string().datetime({ offset: true }).optional(),
    dose_value: z.string().regex(/^\d+(\.\d+)?$/).nullable().optional(),
    dose_unit: z.string().min(1).max(32).nullable().optional(),
    note: z.string().max(2000).nullable().optional(),
    reason: z.string().min(1).max(500),
    client_event_id: z.string().min(1).max(100),
    device_id: z.string().uuid().nullable().optional(),
  })
  .strict();

export type DoseEventInput = z.infer<typeof DoseEventSchema>;
export type BatchDoseEventInput = z.infer<typeof BatchDoseEventSchema>;
export type CorrectionInput = z.infer<typeof CorrectionSchema>;
