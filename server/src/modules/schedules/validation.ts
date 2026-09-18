/**
 * Schedule validation — Phase 5 §5.5, §6.4, §14
 */
import { z } from "zod";

export const ScheduleTypeEnum = z.enum(["fixed_times", "elapsed_interval", "prn", "cyclic", "taper"]);
export const TimingModeEnum = z.enum(["local_clock", "elapsed_interval"]);
export const AmbiguousPolicyEnum = z.enum(["earlier", "later"]);

export const DoseValueString = z.string().regex(/^\d+(\.\d+)?$/, "Must be decimal string").max(12);
export const LocalTimeString = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/, "Must be HH:mm");

export const FixedTimeInput = z.object({
  local_time: LocalTimeString,
  days_of_week: z.array(z.number().int().min(0).max(6)).max(7).optional(),
  dose_quantity_value: DoseValueString.optional(),
  dose_quantity_unit: z.string().min(1).max(32).optional(),
  ambiguous_time_policy: AmbiguousPolicyEnum.optional(),
}).strict();

export const IntervalInput = z.object({
  interval_minutes: z.number().int().positive().max(10080),
  anchor_at: z.string().datetime(),
  dose_quantity_value: DoseValueString.optional(),
  dose_quantity_unit: z.string().min(1).max(32).optional(),
  anchor_policy: z.enum(["fixed_anchor", "last_taken"]).optional(),
}).strict();

export const CycleInput = z.object({
  cycle_anchor_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  on_days: z.number().int().positive(),
  off_days: z.number().int().min(0),
  local_time: LocalTimeString.optional(),
  dose_quantity_value: DoseValueString.optional(),
  dose_quantity_unit: z.string().min(1).max(32).optional(),
  ambiguous_time_policy: AmbiguousPolicyEnum.optional(),
}).strict();

export const TaperStepInput = z.object({
  step_order: z.number().int().positive(),
  starts_on: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  ends_on: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().optional(),
  dose_quantity_value: DoseValueString,
  dose_quantity_unit: z.string().min(1).max(32),
  local_time: LocalTimeString.optional(),
  ambiguous_time_policy: AmbiguousPolicyEnum.optional(),
  days_of_week: z.array(z.number().int().min(0).max(6)).max(7).optional(),
  fixed_times: z.array(FixedTimeInput).max(10).optional(),
}).strict();

export const CreateScheduleSchema = z
  .object({
    schedule_type: ScheduleTypeEnum,
    timing_mode: TimingModeEnum,
    timezone: z.string().min(1).max(64),
    miss_window_minutes: z.number().int().min(0).max(1440).optional(),
    effective_from: z.string().datetime().optional(),
    // type-specific
    fixed_times: z.array(FixedTimeInput).max(10).optional(),
    interval: IntervalInput.optional(),
    cycle: CycleInput.optional(),
    taper_steps: z.array(TaperStepInput).max(20).optional(),
    // common dose for fixed_times when not per-time
    dose_quantity_value: DoseValueString.optional(),
    dose_quantity_unit: z.string().min(1).max(32).optional(),
  })
  .strict()
  .superRefine((data, ctx) => {
    // IANA check (reject bare offset)
    if (/^[+-]\d{1,2}(:\d{2})?$/.test(data.timezone) || data.timezone === "Z") {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "timezone must be IANA, not offset", path: ["timezone"] });
    }
    try {
      new Intl.DateTimeFormat("en-US", { timeZone: data.timezone });
    } catch {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: `Invalid IANA timezone ${data.timezone}`, path: ["timezone"] });
    }
    if (data.schedule_type === "fixed_times" && (!data.fixed_times || data.fixed_times.length === 0)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "fixed_times required for fixed_times schedule", path: ["fixed_times"] });
    }
    if (data.schedule_type === "elapsed_interval" && !data.interval) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "interval required for elapsed_interval schedule", path: ["interval"] });
    }
    if (data.schedule_type === "cyclic" && !data.cycle) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "cycle required for cyclic schedule", path: ["cycle"] });
    }
    if (data.schedule_type === "taper" && (!data.taper_steps || data.taper_steps.length === 0)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "taper_steps required for taper schedule", path: ["taper_steps"] });
    }
    if (data.schedule_type === "taper" && data.taper_steps) {
      // Ensure step_order sequential and dates non-overlapping (also checked in engine)
      const sorted = [...data.taper_steps].sort((a, b) => a.step_order - b.step_order);
      for (let i = 1; i < sorted.length; i++) {
        if (sorted[i].step_order !== sorted[i - 1].step_order + 1) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, message: "taper_steps step_order must be sequential", path: ["taper_steps"] });
        }
        if (sorted[i].starts_on <= sorted[i - 1].ends_on!) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, message: "taper_steps dates overlap", path: ["taper_steps"] });
        }
      }
    }
  });

export type CreateScheduleInput = z.infer<typeof CreateScheduleSchema>;
