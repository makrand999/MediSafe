/**
 * Reports service — deterministic adherence §8.13, §5.11, §17.2
 * Not a judgment, just report.
 */
import { eq, and, gte, lte } from "drizzle-orm";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";

export interface AdherenceReport {
  patient_id: string;
  from: string;
  to: string;
  timezone: string;
  eligible_scheduled_count: number;
  taken_count: number;
  skipped_count: number;
  missed_count: number;
  pending_count: number;
  excluded_count: number;
  excluded_reasons: Record<string, number>;
  adherence_percentage: number | null;
  denominator_definition: string;
}

export async function getAdherenceReport(db: MedacDb, userId: string, patientId: string, query: { from: string; to: string; timezone?: string }): Promise<AdherenceReport> {
  await requirePermissionOnPatient(db, userId, patientId, "adherence:read");
  const from = new Date(query.from);
  const to = new Date(query.to);
  if (from >= to) throw Object.assign(new Error("from must be < to"), { statusCode: 400 });
  const tz = query.timezone ?? "UTC";
  try { new Intl.DateTimeFormat("en-US", { timeZone: tz }); } catch { throw Object.assign(new Error("Invalid timezone"), { statusCode: 400 }); }

  // Fetch occurrences in range
  const occs = await db.query.doseOccurrences.findMany({ where: and(eq(schema.doseOccurrences.patientId, patientId), gte(schema.doseOccurrences.scheduledAtUtc, from), lte(schema.doseOccurrences.scheduledAtUtc, to)) });
  // Fetch dose events for those occurrences
  const events = await db.query.doseEventLogs.findMany({ where: eq(schema.doseEventLogs.patientId, patientId) });
  const eventByOccurrence = new Map<string, typeof events[0]>();
  for (const e of events) {
    if (e.occurrenceId) {
      // Latest event per occurrence wins (or correction chain)
      const existing = eventByOccurrence.get(e.occurrenceId);
      if (!existing || new Date(e.createdAt) > new Date(existing.createdAt)) eventByOccurrence.set(e.occurrenceId, e);
    }
  }
  // Fetch schedules to know PRN vs scheduled, and pauses
  const meds = await db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, patientId) });
  const medMap = new Map(meds.map(m => [m.id, m]));
  const schedules = await db.query.medicationScheduleVersions.findMany({ where: eq(schema.medicationScheduleVersions.timezone, tz) }); // not needed for filtering

  let eligible = 0;
  let taken = 0;
  let skipped = 0;
  let missed = 0;
  let pending = 0;
  let excluded = 0;
  const excludedReasons: Record<string, number> = {};

  for (const occ of occs) {
    const med = medMap.get(occ.medicationId);
    if (!med) { excluded++; excludedReasons["medication_not_found"] = (excludedReasons["medication_not_found"] ?? 0) + 1; continue; }
    // Exclude PRN schedules from ordinary adherence
    const sched = await db.query.medicationScheduleVersions.findFirst({ where: eq(schema.medicationScheduleVersions.id, occ.scheduleVersionId) });
    if (sched?.scheduleType === "prn") { excluded++; excludedReasons["prn_excluded"] = (excludedReasons["prn_excluded"] ?? 0) + 1; continue; }
    // Exclude cancelled occurrences
    if (occ.state === "cancelled") { excluded++; excludedReasons["cancelled"] = (excludedReasons["cancelled"] ?? 0) + 1; continue; }
    // Exclude paused periods (check if occurrence's scheduled time is within a pause)
    const pauses = await db.query.medicationPausePeriods.findMany({ where: eq(schema.medicationPausePeriods.medicationId, occ.medicationId) });
    const isPaused = pauses.some(p => {
      const s = new Date(p.startsAt).getTime();
      const e = p.endsAt ? new Date(p.endsAt).getTime() : Infinity;
      const t = new Date(occ.scheduledAtUtc).getTime();
      return t >= s && t < e;
    });
    if (isPaused) { excluded++; excludedReasons["paused"] = (excludedReasons["paused"] ?? 0) + 1; continue; }

    eligible++;
    const ev = eventByOccurrence.get(occ.id);
    if (!ev) {
      // No event yet
      if (occ.state === "missed") missed++;
      else if (occ.state === "taken") taken++; // should not happen without event, but check state
      else if (occ.state === "skipped") skipped++;
      else pending++;
      continue;
    }
    if (ev.eventType === "taken") taken++;
    else if (ev.eventType === "skipped") skipped++;
    else if (occ.state === "missed") missed++;
    else pending++;
  }

  const adherence = eligible > 0 ? (taken / eligible) * 100 : null;
  return {
    patient_id: patientId,
    from: from.toISOString(),
    to: to.toISOString(),
    timezone: tz,
    eligible_scheduled_count: eligible,
    taken_count: taken,
    skipped_count: skipped,
    missed_count: missed,
    pending_count: pending,
    excluded_count: excluded,
    excluded_reasons: excludedReasons,
    adherence_percentage: adherence !== null ? Math.round(adherence * 100) / 100 : null,
    denominator_definition: "eligible_scheduled_count = total scheduled occurrences in range excluding PRN, cancelled, paused; adherence = taken / eligible",
  };
}
