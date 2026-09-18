/**
 * Alerts validation — Phase 9 §6.8, §8.14, §16
 */
import { z } from "zod";

export const AlertTypeEnum = z.enum(["low_stock", "expiration", "missed_user_attention_med", "stale_device", "generic"]);
export const AlertSeverityEnum = z.enum(["info", "attention", "urgent_review"]);
export const AlertStatusEnum = z.enum(["open", "acknowledged", "resolved", "cancelled"]);

export const AlertPreferenceSchema = z
  .object({
    alert_type: AlertTypeEnum,
    enabled: z.boolean(),
    delay_minutes: z.number().int().min(0).max(1440).optional(),
    quiet_hours_start: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/).nullable().optional(),
    quiet_hours_end: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/).nullable().optional(),
    timezone: z.string().min(1).max(64).nullable().optional(),
  })
  .strict();

export const PutPreferencesSchema = z
  .object({
    preferences: z.array(AlertPreferenceSchema).min(1).max(20),
  })
  .strict();

export type PutPreferencesInput = z.infer<typeof PutPreferencesSchema>;
