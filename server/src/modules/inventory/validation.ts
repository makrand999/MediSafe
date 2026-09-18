/**
 * Inventory validation — Phase 8 §6.6, §8.11
 */
import { z } from "zod";

export const CreateTransactionSchema = z
  .object({
    transaction_type: z.enum(["fill", "manual_adjustment", "lost_or_damaged", "disposed", "transferred"]),
    quantity_delta: z.string().regex(/^-?\d+(\.\d+)?$/, "Must be decimal string"),
    unit: z.string().min(1).max(32),
    reason_text: z.string().max(500).optional(),
    effective_at: z.string().datetime({ offset: true }).optional(),
  })
  .strict();

export const UpdateSettingsSchema = z
  .object({
    low_stock_threshold_value: z.string().regex(/^\d+(\.\d+)?$/).nullable().optional(),
    unit: z.string().min(1).max(32).optional(),
  })
  .strict();

export type CreateTransactionInput = z.infer<typeof CreateTransactionSchema>;
export type UpdateSettingsInput = z.infer<typeof UpdateSettingsSchema>;
