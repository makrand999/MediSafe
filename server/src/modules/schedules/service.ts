/**
 * Schedule service — versioning, preview, occurrence persistence §5.5, §6.4, §6.5, §14
 */
import { eq, and, asc, desc, gte, inArray, lte, sql } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";
import { CreateScheduleSchema, type CreateScheduleInput } from "./validation.js";
import { generateOccurrences, type ScheduleVersionInput, type MedicationPausePeriod } from "./engine.js";

function now() { return new Date(); }

async function audit(db: MedacDb, params: { actorUserId?: string | null; actorSessionId?: string | null; patientId?: string | null; action: string; entityType: string; entityId?: string | null; requestId?: string | null; ipPrefix?: string | null; metadataJson?: Record<string, unknown> }): Promise<void> {
  await db.insert(schema.auditEvents).values({
    id: uuidv4(),
    occurredAt: now(),
    actorUserId: params.actorUserId ?? null,
    actorSessionId: params.actorSessionId ?? null,
    patientId: params.patientId ?? null,
    action: params.action,
    entityType: params.entityType,
    entityId: params.entityId ?? null,
    requestId: params.requestId ?? null,
    ipPrefix: params.ipPrefix ?? null,
    metadataJson: params.metadataJson ?? {},
  });
}
async function outbox(db: MedacDb, eventType: string, aggregateType: string, aggregateId: string, payload: Record<string, unknown>): Promise<void> {
  await db.insert(schema.outboxEvents).values({ id: uuidv4(), eventType, aggregateType, aggregateId, payloadJson: payload, availableAt: now() });
}

async function assertMedicationBelongs(db: MedacDb, patientId: string, medicationId: string): Promise<typeof schema.patientMedications.$inferSelect> {
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, medicationId), eq(schema.patientMedications.patientId, patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found or not in patient"), { statusCode: 404 });
  return med;
}

function toEngineInput(version: typeof schema.medicationScheduleVersions.$inferSelect, fixedTimes: Array<typeof schema.scheduleFixedTimes.$inferSelect>, interval: typeof schema.scheduleIntervals.$inferSelect | undefined, cycle: typeof schema.scheduleCycles.$inferSelect | undefined, taperSteps: Array<typeof schema.taperSteps.$inferSelect>, taperFixedByStep: Map<string, Array<typeof schema.taperStepFixedTimes.$inferSelect>>): ScheduleVersionInput {
  const base: ScheduleVersionInput = {
    id: version.id,
    medication_id: version.medicationId,
    version_number: version.versionNumber,
    schedule_type: version.scheduleType as never,
    timing_mode: version.timingMode as never,
    timezone: version.timezone,
    effective_from: version.effectiveFrom.toISOString(),
    effective_until: version.effectiveUntil ? version.effectiveUntil.toISOString() : null,
    miss_window_minutes: version.missWindowMinutes ?? null,
    fixed_times: fixedTimes.map(ft => ({
      schedule_version_id: ft.scheduleVersionId,
      local_time: ft.localTime,
      days_of_week: ft.daysOfWeek ?? null,
      dose_quantity_value: ft.doseQuantityValue ? String(ft.doseQuantityValue) : "0",
      dose_quantity_unit: ft.doseQuantityUnit ?? "tablet",
      ambiguous_time_policy: (ft.ambiguousTimePolicy as never) ?? "earlier",
    })),
    interval: interval ? {
      schedule_version_id: interval.scheduleVersionId,
      interval_minutes: interval.intervalMinutes,
      anchor_at: interval.anchorAt.toISOString(),
      dose_quantity_value: interval.doseQuantityValue ? String(interval.doseQuantityValue) : "0",
      dose_quantity_unit: interval.doseQuantityUnit ?? "tablet",
      anchor_policy: interval.anchorPolicy as never,
    } : undefined,
    cycle: cycle ? {
      schedule_version_id: cycle.scheduleVersionId,
      cycle_anchor_date: cycle.cycleAnchorDate,
      on_days: cycle.onDays,
      off_days: cycle.offDays,
      local_time: "08:00",
      dose_quantity_value: "0",
      dose_quantity_unit: "tablet",
    } : undefined,
    taper_steps: taperSteps.map(ts => {
      const fts = taperFixedByStep.get(ts.id) ?? [];
      return {
        id: ts.id,
        schedule_version_id: ts.scheduleVersionId,
        step_order: ts.stepOrder,
        starts_on: ts.startsOn,
        ends_on: ts.endsOn ?? ts.startsOn,
        dose_quantity_value: String(ts.doseQuantityValue),
        dose_quantity_unit: ts.doseQuantityUnit,
        fixed_times: fts.map(ft => ({
          schedule_version_id: ts.scheduleVersionId,
          local_time: ft.localTime,
          days_of_week: ft.daysOfWeek ?? null,
          dose_quantity_value: String(ts.doseQuantityValue),
          dose_quantity_unit: ts.doseQuantityUnit,
          ambiguous_time_policy: (ft.ambiguousTimePolicy as never) ?? "earlier",
        })),
        local_time: fts[0]?.localTime ?? null,
        ambiguous_time_policy: (fts[0]?.ambiguousTimePolicy as never) ?? "earlier",
        days_of_week: fts[0]?.daysOfWeek ?? null,
      };
    }),
  };
  // For cyclic, need to fill local_time/dose from first fixed_times if exists or from taper logic
  // For cyclic we stored cycle without dose; if fixed_times provided for cyclic, use it
  if (base.schedule_type === "cyclic" && fixedTimes.length > 0) {
    const ft = fixedTimes[0];
    base.cycle = {
      schedule_version_id: version.id,
      cycle_anchor_date: cycle?.cycleAnchorDate ?? new Date().toISOString().slice(0, 10),
      on_days: cycle?.onDays ?? 1,
      off_days: cycle?.offDays ?? 0,
      local_time: ft.localTime,
      dose_quantity_value: String(ft.doseQuantityValue ?? "0"),
      dose_quantity_unit: ft.doseQuantityUnit ?? "tablet",
      ambiguous_time_policy: (ft.ambiguousTimePolicy as never) ?? "earlier",
    };
  }
  return base;
}

export async function createSchedule(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; data: CreateScheduleInput; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<{ version: typeof schema.medicationScheduleVersions.$inferSelect; preview: ReturnType<typeof generateOccurrences> }> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "schedule:create");
  const med = await assertMedicationBelongs(db, input.patientId, input.medicationId);
  if (med.status === "archived" || med.status === "discontinued") throw Object.assign(new Error("Cannot create schedule for archived/discontinued medication"), { statusCode: 400 });
  // Validate input via Zod already, but double-check
  const data = CreateScheduleSchema.parse(input.data);
  // Determine next version number
  const existing = await db.query.medicationScheduleVersions.findMany({ where: eq(schema.medicationScheduleVersions.medicationId, input.medicationId), orderBy: [desc(schema.medicationScheduleVersions.versionNumber)] });
  const nextVersion = existing.length > 0 ? existing[0].versionNumber + 1 : 1;
  // If there's an active version without effective_until, close it if this is new active
  const nowDate = now();
  const effectiveFrom = data.effective_from ? new Date(data.effective_from) : nowDate;
  // Close previous active version if it has no effective_until and effective_from < new effective_from
  // For first version, no close needed
  // For supersede via create, we treat as new version and close previous
  if (existing.length > 0 && !existing[0].effectiveUntil) {
    await db.update(schema.medicationScheduleVersions).set({ effectiveUntil: effectiveFrom, updatedAt: nowDate }).where(eq(schema.medicationScheduleVersions.id, existing[0].id));
  }
  const versionId = uuidv4();
  await db.insert(schema.medicationScheduleVersions).values({
    id: versionId,
    medicationId: input.medicationId,
    versionNumber: nextVersion,
    scheduleType: data.schedule_type as never,
    timingMode: data.timing_mode as never,
    timezone: data.timezone,
    effectiveFrom,
    effectiveUntil: null,
    missWindowMinutes: data.miss_window_minutes ?? null,
    createdByUserId: input.userId,
  });
  // Insert child rows
  if (data.schedule_type === "fixed_times" && data.fixed_times) {
    for (const ft of data.fixed_times) {
      await db.insert(schema.scheduleFixedTimes).values({
        id: uuidv4(),
        scheduleVersionId: versionId,
        localTime: ft.local_time,
        daysOfWeek: ft.days_of_week ?? null,
        doseQuantityValue: ft.dose_quantity_value ?? data.dose_quantity_value ?? "1",
        doseQuantityUnit: ft.dose_quantity_unit ?? data.dose_quantity_unit ?? med.doseQuantityUnit,
        ambiguousTimePolicy: (ft.ambiguous_time_policy as never) ?? "earlier",
      });
    }
  }
  if (data.schedule_type === "elapsed_interval" && data.interval) {
    await db.insert(schema.scheduleIntervals).values({
      scheduleVersionId: versionId,
      intervalMinutes: data.interval.interval_minutes,
      anchorAt: new Date(data.interval.anchor_at),
      doseQuantityValue: data.interval.dose_quantity_value ?? data.dose_quantity_value ?? "1",
      doseQuantityUnit: data.interval.dose_quantity_unit ?? data.dose_quantity_unit ?? med.doseQuantityUnit,
      anchorPolicy: (data.interval.anchor_policy as never) ?? "fixed_anchor",
    });
  }
  if (data.schedule_type === "cyclic" && data.cycle) {
    await db.insert(schema.scheduleCycles).values({
      scheduleVersionId: versionId,
      cycleAnchorDate: data.cycle.cycle_anchor_date,
      onDays: data.cycle.on_days,
      offDays: data.cycle.off_days,
    });
    // Also store fixed times for cyclic's dose time if provided via fixed_times or cycle local_time
    const localTime = data.cycle.local_time ?? data.fixed_times?.[0]?.local_time ?? "08:00";
    const doseVal = data.cycle.dose_quantity_value ?? data.dose_quantity_value ?? "1";
    const doseUnit = data.cycle.dose_quantity_unit ?? data.dose_quantity_unit ?? med.doseQuantityUnit;
    await db.insert(schema.scheduleFixedTimes).values({
      id: uuidv4(),
      scheduleVersionId: versionId,
      localTime,
      daysOfWeek: null,
      doseQuantityValue: doseVal,
      doseQuantityUnit: doseUnit,
      ambiguousTimePolicy: (data.cycle.ambiguous_time_policy as never) ?? "earlier",
    });
  }
  if (data.schedule_type === "taper" && data.taper_steps) {
    for (const step of data.taper_steps) {
      const stepId = uuidv4();
      await db.insert(schema.taperSteps).values({
        id: stepId,
        scheduleVersionId: versionId,
        stepOrder: step.step_order,
        startsOn: step.starts_on,
        endsOn: step.ends_on ?? step.starts_on,
        doseQuantityValue: step.dose_quantity_value,
        doseQuantityUnit: step.dose_quantity_unit,
      });
      if (step.fixed_times && step.fixed_times.length > 0) {
        for (const ft of step.fixed_times) {
          await db.insert(schema.taperStepFixedTimes).values({
            id: uuidv4(),
            taperStepId: stepId,
            localTime: ft.local_time,
            daysOfWeek: ft.days_of_week ?? null,
            ambiguousTimePolicy: (ft.ambiguous_time_policy as never) ?? "earlier",
          });
        }
      } else if (step.local_time) {
        await db.insert(schema.taperStepFixedTimes).values({
          id: uuidv4(),
          taperStepId: stepId,
          localTime: step.local_time,
          daysOfWeek: step.days_of_week ?? null,
          ambiguousTimePolicy: (step.ambiguous_time_policy as never) ?? "earlier",
        });
      }
    }
  }
  // For prn, no child rows needed

  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "schedule.create", entityType: "medication_schedule_version", entityId: versionId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { schedule_type: data.schedule_type, version: nextVersion } });
  await outbox(db, "schedule_version_created", "medication_schedule_version", versionId, { patientId: input.patientId, medicationId: input.medicationId, schedule_type: data.schedule_type });

  const version = await db.query.medicationScheduleVersions.findFirst({ where: eq(schema.medicationScheduleVersions.id, versionId) });
  // Preview next 60 days (or 30 past 60 future for generation, but for API preview limit to 30 occurrences)
  const preview = await previewForVersion(db, version!, input.patientId);
  // Persist rolling window: 30 days past through 60 days future for idempotent generation (plan §6.5)
  const persistFrom = new Date(effectiveFrom.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString();
  const persistTo = new Date(nowDate.getTime() + 60 * 24 * 60 * 60 * 1000).toISOString();
  // Fire-and-forget but await for MVP to ensure preview and persistence same calculation
  try {
    await generateAndPersistOccurrences(db, versionId, persistFrom, persistTo);
  } catch (e) {
    // Log but don't fail creation — occurrence generation is background job retriable
    console.warn("Occurrence persistence failed", (e as Error).message);
  }
  return { version: version!, preview };
}

async function previewForVersion(db: MedacDb, version: typeof schema.medicationScheduleVersions.$inferSelect, patientId: string): Promise<ReturnType<typeof generateOccurrences>> {
  const fixedTimes = await db.query.scheduleFixedTimes.findMany({ where: eq(schema.scheduleFixedTimes.scheduleVersionId, version.id) });
  const interval = await db.query.scheduleIntervals.findFirst({ where: eq(schema.scheduleIntervals.scheduleVersionId, version.id) });
  const cycle = await db.query.scheduleCycles.findFirst({ where: eq(schema.scheduleCycles.scheduleVersionId, version.id) });
  const taperSteps = await db.query.taperSteps.findMany({ where: eq(schema.taperSteps.scheduleVersionId, version.id), orderBy: [desc(schema.taperSteps.stepOrder)] });
  // Reverse order to ascending for engine
  taperSteps.sort((a, b) => a.stepOrder - b.stepOrder);
  const taperFixedMap = new Map<string, Array<typeof schema.taperStepFixedTimes.$inferSelect>>();
  for (const ts of taperSteps) {
    const fts = await db.query.taperStepFixedTimes.findMany({ where: eq(schema.taperStepFixedTimes.taperStepId, ts.id) });
    taperFixedMap.set(ts.id, fts);
  }
  const medication = await db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, version.medicationId) });
  const pauses = await db.query.medicationPausePeriods.findMany({ where: eq(schema.medicationPausePeriods.medicationId, version.medicationId) });
  const pausePeriods: MedicationPausePeriod[] = pauses.map(p => ({ id: p.id, medication_id: p.medicationId, starts_at: p.startsAt.toISOString(), ends_at: p.endsAt ? p.endsAt.toISOString() : new Date(8640000000000000).toISOString(), reason: p.reason }));
  const engineInput = toEngineInput(version, fixedTimes, interval ?? undefined, cycle ?? undefined, taperSteps, taperFixedMap);
  const nowDate = new Date();
  const from = nowDate.toISOString();
  const to = new Date(nowDate.getTime() + 60 * 24 * 60 * 60 * 1000).toISOString(); // 60 days future
  const medDates = medication ? { start_date: medication.startDate, end_date: medication.endDate } : undefined;
  const occurrences = generateOccurrences({ scheduleVersion: engineInput, rangeFrom: from, rangeTo: to, pausePeriods, medicationStartEnd: medDates });
  // Limit preview to 30
  return occurrences.slice(0, 30);
}

export type ScheduleVersionWithFixedTimes = typeof schema.medicationScheduleVersions.$inferSelect & { fixedTimes: Array<typeof schema.scheduleFixedTimes.$inferSelect> };

export async function listSchedules(db: MedacDb, userId: string, patientId: string, medicationId: string): Promise<Array<ScheduleVersionWithFixedTimes>> {
  await requirePermissionOnPatient(db, userId, patientId, "schedule:read");
  await assertMedicationBelongs(db, patientId, medicationId);
  const versions = await db.query.medicationScheduleVersions.findMany({ where: eq(schema.medicationScheduleVersions.medicationId, medicationId), orderBy: [desc(schema.medicationScheduleVersions.versionNumber)] });
  if (versions.length === 0) return [];
  const allFixed = await db.query.scheduleFixedTimes.findMany({ where: inArray(schema.scheduleFixedTimes.scheduleVersionId, versions.map(v => v.id)) });
  const byVersion = new Map<string, typeof allFixed>();
  for (const ft of allFixed) {
    const list = byVersion.get(ft.scheduleVersionId) ?? [];
    list.push(ft);
    byVersion.set(ft.scheduleVersionId, list);
  }
  return versions.map(v => ({ ...v, fixedTimes: byVersion.get(v.id) ?? [] }));
}

export async function getSchedule(db: MedacDb, userId: string, patientId: string, medicationId: string, versionId: string): Promise<{ version: typeof schema.medicationScheduleVersions.$inferSelect; fixedTimes: Array<typeof schema.scheduleFixedTimes.$inferSelect>; interval?: typeof schema.scheduleIntervals.$inferSelect; cycle?: typeof schema.scheduleCycles.$inferSelect; taperSteps: Array<typeof schema.taperSteps.$inferSelect>; preview: ReturnType<typeof generateOccurrences> }> {
  await requirePermissionOnPatient(db, userId, patientId, "schedule:read");
  await assertMedicationBelongs(db, patientId, medicationId);
  const version = await db.query.medicationScheduleVersions.findFirst({ where: and(eq(schema.medicationScheduleVersions.id, versionId), eq(schema.medicationScheduleVersions.medicationId, medicationId)) });
  if (!version) throw Object.assign(new Error("Schedule version not found"), { statusCode: 404 });
  const fixedTimes = await db.query.scheduleFixedTimes.findMany({ where: eq(schema.scheduleFixedTimes.scheduleVersionId, versionId) });
  const interval = await db.query.scheduleIntervals.findFirst({ where: eq(schema.scheduleIntervals.scheduleVersionId, versionId) });
  const cycle = await db.query.scheduleCycles.findFirst({ where: eq(schema.scheduleCycles.scheduleVersionId, versionId) });
  const taperSteps = await db.query.taperSteps.findMany({ where: eq(schema.taperSteps.scheduleVersionId, versionId) });
  const preview = await previewForVersion(db, version, patientId);
  return { version, fixedTimes, interval: interval ?? undefined, cycle: cycle ?? undefined, taperSteps, preview };
}

export async function supersedeSchedule(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; versionId: string; data: CreateScheduleInput; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<{ version: typeof schema.medicationScheduleVersions.$inferSelect; preview: ReturnType<typeof generateOccurrences> }> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "schedule:supersede");
  const existing = await db.query.medicationScheduleVersions.findFirst({ where: and(eq(schema.medicationScheduleVersions.id, input.versionId), eq(schema.medicationScheduleVersions.medicationId, input.medicationId)) });
  if (!existing) throw Object.assign(new Error("Schedule version not found"), { statusCode: 404 });
  // Ensure it's the latest active version (no effective_until)
  const latest = await db.query.medicationScheduleVersions.findMany({ where: eq(schema.medicationScheduleVersions.medicationId, input.medicationId), orderBy: [desc(schema.medicationScheduleVersions.versionNumber)] });
  const latestActive = latest.find(v => !v.effectiveUntil);
  if (!latestActive || latestActive.id !== input.versionId) throw Object.assign(new Error("Only the active schedule version can be superseded"), { statusCode: 400 });
  // Create new version superseding
  return createSchedule(db, { userId: input.userId, patientId: input.patientId, medicationId: input.medicationId, data: input.data, actorSessionId: input.actorSessionId, requestId: input.requestId, ip: input.ip });
}

export async function generateAndPersistOccurrences(db: MedacDb, versionId: string, rangeFrom: string, rangeTo: string): Promise<number> {
  const version = await db.query.medicationScheduleVersions.findFirst({ where: eq(schema.medicationScheduleVersions.id, versionId) });
  if (!version) throw new Error("Version not found");
  const fixedTimes = await db.query.scheduleFixedTimes.findMany({ where: eq(schema.scheduleFixedTimes.scheduleVersionId, versionId) });
  const interval = await db.query.scheduleIntervals.findFirst({ where: eq(schema.scheduleIntervals.scheduleVersionId, versionId) });
  const cycle = await db.query.scheduleCycles.findFirst({ where: eq(schema.scheduleCycles.scheduleVersionId, versionId) });
  const taperSteps = await db.query.taperSteps.findMany({ where: eq(schema.taperSteps.scheduleVersionId, versionId) });
  taperSteps.sort((a, b) => a.stepOrder - b.stepOrder);
  const taperFixedMap = new Map<string, Array<typeof schema.taperStepFixedTimes.$inferSelect>>();
  for (const ts of taperSteps) {
    const fts = await db.query.taperStepFixedTimes.findMany({ where: eq(schema.taperStepFixedTimes.taperStepId, ts.id) });
    taperFixedMap.set(ts.id, fts);
  }
  const medication = await db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, version.medicationId) });
  if (!medication) throw new Error("Medication not found");
  const pauses = await db.query.medicationPausePeriods.findMany({ where: eq(schema.medicationPausePeriods.medicationId, version.medicationId) });
  const pausePeriods: MedicationPausePeriod[] = pauses.map(p => ({ id: p.id, medication_id: p.medicationId, starts_at: p.startsAt.toISOString(), ends_at: p.endsAt ? p.endsAt.toISOString() : new Date(8640000000000000).toISOString(), reason: p.reason }));
  const engineInput = toEngineInput(version, fixedTimes, interval ?? undefined, cycle ?? undefined, taperSteps, taperFixedMap);
  const medDates = { start_date: medication.startDate, end_date: medication.endDate };
  const candidates = generateOccurrences({ scheduleVersion: engineInput, rangeFrom, rangeTo, pausePeriods, medicationStartEnd: medDates });
  let inserted = 0;
  for (const c of candidates) {
    // Idempotent insert: on conflict (schedule_version_id, scheduled_at_utc) do nothing
    try {
      await db.insert(schema.doseOccurrences).values({
        id: uuidv4(),
        patientId: medication.patientId,
        medicationId: medication.id,
        scheduleVersionId: versionId,
        scheduledAtUtc: new Date(c.scheduled_at_utc),
        scheduledLocalDatetime: new Date(c.scheduled_local_datetime),
        timezone: c.timezone,
        utcOffsetMinutes: c.utc_offset_minutes,
        dstAdjusted: c.dst_adjusted,
        nominalDoseValue: c.dose_value,
        nominalDoseUnit: c.dose_unit,
        state: "scheduled",
      }).onConflictDoNothing({ target: [schema.doseOccurrences.scheduleVersionId, schema.doseOccurrences.scheduledAtUtc] } as never);
      inserted++;
    } catch {
      // ignore duplicate
    }
  }
  return inserted;
}

export async function listOccurrences(db: MedacDb, userId: string, patientId: string, query: { from?: string; to?: string; cursor?: string; limit?: number }): Promise<{ occurrences: Array<typeof schema.doseOccurrences.$inferSelect>; nextCursor?: string }> {
  await requirePermissionOnPatient(db, userId, patientId, "occurrence:read");
  const limit = Math.min(query.limit ?? 50, 100);
  let fromDate: Date | undefined;
  let toDate: Date | undefined;
  if (query.from) fromDate = new Date(query.from);
  if (query.to) toDate = new Date(query.to);
  // Simple cursor: base64 of last id
  let cursorId: string | undefined;
  if (query.cursor) {
    try { cursorId = Buffer.from(query.cursor, "base64url").toString("utf8"); } catch {}
  }
  // Push the date window into SQL: occurrences are pre-generated ~60 days
  // into the future, so fetching "latest N" then filtering in memory returns
  // only far-future rows and an empty page for today.
  const conditions = [eq(schema.doseOccurrences.patientId, patientId)];
  if (fromDate) conditions.push(gte(schema.doseOccurrences.scheduledAtUtc, fromDate));
  if (toDate) conditions.push(lte(schema.doseOccurrences.scheduledAtUtc, toDate));
  const orderBy = fromDate ? [asc(schema.doseOccurrences.scheduledAtUtc)] : [desc(schema.doseOccurrences.scheduledAtUtc)];
  let occs = await db.query.doseOccurrences.findMany({ where: and(...conditions), orderBy, limit: limit + 1 });
  // Defensive in-memory filter for the same window.
  if (fromDate) occs = occs.filter(o => o.scheduledAtUtc >= fromDate!);
  if (toDate) occs = occs.filter(o => o.scheduledAtUtc <= toDate!);
  // Cursor filter
  if (cursorId) {
    const idx = occs.findIndex(o => o.id === cursorId);
    if (idx >= 0) occs = occs.slice(idx + 1);
  }
  // Sort ascending for response
  occs.sort((a, b) => a.scheduledAtUtc.getTime() - b.scheduledAtUtc.getTime());
  const hasMore = occs.length > limit;
  const page = hasMore ? occs.slice(0, limit) : occs;
  const nextCursor = hasMore ? Buffer.from(page[page.length - 1].id).toString("base64url") : undefined;
  return { occurrences: page, nextCursor };
}
