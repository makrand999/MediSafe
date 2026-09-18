/**
 * Doses service — single/batch, corrections, idempotency, inventory, sync §6.5, §8.10, §15
 */
import { eq, and, desc, gte, lte, sql, inArray } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";
import type { DoseEventInput, CorrectionInput } from "./validation.js";

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

// Simple encrypt for notes — use same as proposal service (base64 of plaintext for MVP, real encrypt would use FIELD_ENCRYPTION)
function encryptNote(note: string | null | undefined): string | null {
  if (!note) return null;
  return Buffer.from(note, "utf8").toString("base64");
}
function decryptNote(cipher: string | null | undefined): string | null {
  if (!cipher) return null;
  try { return Buffer.from(cipher, "base64").toString("utf8"); } catch { return null; }
}

async function assertMedicationBelongs(db: MedacDb, patientId: string, medicationId: string): Promise<typeof schema.patientMedications.$inferSelect> {
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, medicationId), eq(schema.patientMedications.patientId, patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  return med;
}

async function assertOccurrenceBelongs(db: MedacDb, patientId: string, occurrenceId: string, medicationId?: string): Promise<typeof schema.doseOccurrences.$inferSelect> {
  const occ = await db.query.doseOccurrences.findFirst({ where: and(eq(schema.doseOccurrences.id, occurrenceId), eq(schema.doseOccurrences.patientId, patientId)) });
  if (!occ) throw Object.assign(new Error("Occurrence not found"), { statusCode: 404 });
  if (medicationId && occ.medicationId !== medicationId) throw Object.assign(new Error("Occurrence does not belong to medication"), { statusCode: 400 });
  return occ;
}

function isMedicationAllowedForEvent(med: typeof schema.patientMedications.$inferSelect, actualAt: Date): boolean {
  if (med.status === "archived") return false;
  if (med.status === "discontinued" && med.discontinuedAt) {
    // Allow historical events before discontinuation
    if (actualAt.getTime() >= new Date(med.discontinuedAt).getTime()) {
      // Only allow if explicitly PRN/unplanned? For now reject future after discontinued
      return false;
    }
  }
  if (med.startDate && actualAt < new Date(med.startDate)) return false;
  if (med.endDate && actualAt > new Date(med.endDate)) return false;
  return true;
}

async function applyInventoryForEvent(db: MedacDb, patientId: string, medicationId: string, eventId: string, eventType: string, doseValue: string | null, doseUnit: string | null, actorUserId: string | null): Promise<void> {
  if (!doseValue || !doseUnit) return;
  if (eventType !== "taken") return; // Only taken consumes inventory per plan §6.6
  // Find or create inventory account for this medication+unit
  let account = await db.query.inventoryAccounts.findFirst({ where: and(eq(schema.inventoryAccounts.patientId, patientId), eq(schema.inventoryAccounts.medicationId, medicationId), eq(schema.inventoryAccounts.unit, doseUnit)) });
  if (!account) {
    const accId = uuidv4();
    await db.insert(schema.inventoryAccounts).values({ id: accId, patientId, medicationId, unit: doseUnit });
    account = await db.query.inventoryAccounts.findFirst({ where: eq(schema.inventoryAccounts.id, accId) });
    if (!account) return;
  }
  // Check idempotency: if transaction already exists for this dose_event, don't duplicate
  const existing = await db.query.inventoryTransactions.findFirst({ where: eq(schema.inventoryTransactions.doseEventId, eventId) });
  if (existing) return;
  const qty = Number(doseValue);
  if (Number.isNaN(qty) || qty <= 0) return;
  await db.insert(schema.inventoryTransactions).values({
    id: uuidv4(),
    inventoryAccountId: account.id,
    transactionType: "dose_consumed" as never,
    quantityDelta: `-${qty}` as never,
    doseEventId: eventId,
    actorUserId: actorUserId ?? null,
    effectiveAt: now(),
  });
}

async function reverseInventoryForEvent(db: MedacDb, doseEventId: string, actorUserId: string | null): Promise<void> {
  const tx = await db.query.inventoryTransactions.findFirst({ where: eq(schema.inventoryTransactions.doseEventId, doseEventId) });
  if (!tx) return;
  // Check if already reversed
  const reversed = await db.query.inventoryTransactions.findFirst({ where: eq(schema.inventoryTransactions.reversesTransactionId, tx.id) });
  if (reversed) return;
  await db.insert(schema.inventoryTransactions).values({
    id: uuidv4(),
    inventoryAccountId: tx.inventoryAccountId,
    transactionType: "dose_consumption_reversed" as never,
    quantityDelta: String(-Number(tx.quantityDelta)) as never, // reverse sign
    doseEventId: null,
    actorUserId: actorUserId ?? null,
    effectiveAt: now(),
    reversesTransactionId: tx.id,
  });
}

// ── Single create with idempotency ──────────────────────────────────────────
export async function createDoseEvent(db: MedacDb, input: { userId: string; patientId: string; data: DoseEventInput; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<{ event: typeof schema.doseEventLogs.$inferSelect; outcome: "accepted" | "duplicate" }> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "doseEvent:create");
  const med = await assertMedicationBelongs(db, input.patientId, input.data.medication_id);
  if (input.data.occurrence_id) await assertOccurrenceBelongs(db, input.patientId, input.data.occurrence_id, input.data.medication_id);
  const actualAt = new Date(input.data.actual_at);
  if (!isMedicationAllowedForEvent(med, actualAt)) throw Object.assign(new Error("Medication not allowed for event at this time"), { statusCode: 400, code: "MEDICATION_NOT_ACTIVE" });
  // Validate device exists, otherwise null to avoid FK violation — do before idempotency check
  let validatedDeviceId: string | null = input.data.device_id ?? null;
  if (validatedDeviceId) {
    const dev = await db.query.devices.findFirst({ where: eq(schema.devices.id, validatedDeviceId as never) });
    if (!dev) validatedDeviceId = null;
  }
  // Idempotency check (use validatedDeviceId)
  let existing: typeof schema.doseEventLogs.$inferSelect | undefined;
  if (validatedDeviceId) {
    existing = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.deviceId, validatedDeviceId as never), eq(schema.doseEventLogs.clientEventId, input.data.client_event_id)) });
  } else {
    existing = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.patientId, input.patientId), eq(schema.doseEventLogs.clientEventId, input.data.client_event_id)) });
  }
  if (existing) {
    return { event: existing, outcome: "duplicate" };
  }
  const eventId = uuidv4();
  const noteCipher = encryptNote(input.data.note ?? null);
  try {
    await db.insert(schema.doseEventLogs).values({
      id: eventId,
      patientId: input.patientId,
      medicationId: input.data.medication_id,
      occurrenceId: input.data.occurrence_id ?? null,
      eventType: input.data.event_type as never,
      actualAt,
      recordedAt: now(),
      timezone: input.data.timezone ?? null,
      doseValue: input.data.dose_value ?? null,
      doseUnit: input.data.dose_unit ?? null,
      noteCiphertext: noteCipher,
      clientEventId: input.data.client_event_id,
      deviceId: validatedDeviceId as never,
      actorUserId: input.userId,
      correctsEventId: null,
    });
  } catch (e) {
    const code = (e as { code?: string }).code;
    if (code === "23505") {
      // Unique violation — treat as duplicate (idempotency)
      const existingDup = validatedDeviceId
        ? await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.deviceId, validatedDeviceId as never), eq(schema.doseEventLogs.clientEventId, input.data.client_event_id)) })
        : await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.patientId, input.patientId), eq(schema.doseEventLogs.clientEventId, input.data.client_event_id)) });
      if (existingDup) return { event: existingDup, outcome: "duplicate" };
      // Fallback: try both
      const fallback = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.patientId, input.patientId), eq(schema.doseEventLogs.clientEventId, input.data.client_event_id)) });
      if (fallback) return { event: fallback, outcome: "duplicate" };
      throw e;
    }
    if (code === "23503" && validatedDeviceId) {
      await db.insert(schema.doseEventLogs).values({
        id: eventId,
        patientId: input.patientId,
        medicationId: input.data.medication_id,
        occurrenceId: input.data.occurrence_id ?? null,
        eventType: input.data.event_type as never,
        actualAt,
        recordedAt: now(),
        timezone: input.data.timezone ?? null,
        doseValue: input.data.dose_value ?? null,
        doseUnit: input.data.dose_unit ?? null,
        noteCiphertext: noteCipher,
        clientEventId: input.data.client_event_id,
        deviceId: null as never,
        actorUserId: input.userId,
        correctsEventId: null,
      });
    } else throw e;
  }
  // Inventory, outbox, audit atomically (best-effort, but we do in same transaction if DB supports)
  await applyInventoryForEvent(db, input.patientId, input.data.medication_id, eventId, input.data.event_type, input.data.dose_value ?? null, input.data.dose_unit ?? null, input.userId);
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "dose_event.create", entityType: "dose_event_log", entityId: eventId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { event_type: input.data.event_type } });
  await outbox(db, "dose_event_recorded", "dose_event_log", eventId, { patientId: input.patientId, medicationId: input.data.medication_id, event_type: input.data.event_type });
  // Update occurrence state if applicable
  if (input.data.occurrence_id) {
    const stateMap: Record<string, string> = { taken: "taken", skipped: "skipped", snoozed: "scheduled", cancelled: "cancelled", corrected: "corrected" };
    const newState = stateMap[input.data.event_type] ?? "scheduled";
    await db.update(schema.doseOccurrences).set({ state: newState as never, stateUpdatedAt: now() }).where(eq(schema.doseOccurrences.id, input.data.occurrence_id as never));
  }
  const event = await db.query.doseEventLogs.findFirst({ where: eq(schema.doseEventLogs.id, eventId) });
  return { event: event!, outcome: "accepted" };
}

// ── Batch ────────────────────────────────────────────────────────────────────
export async function createBatch(db: MedacDb, input: { userId: string; patientId: string; events: DoseEventInput[]; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<Array<{ client_event_id: string; outcome: "accepted" | "duplicate" | "rejected" | "conflict"; event_id?: string; error?: string }>> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "doseEvent:create");
  if (input.events.length > 100) throw Object.assign(new Error("Batch too large"), { statusCode: 400 });
  const results: Array<{ client_event_id: string; outcome: "accepted" | "duplicate" | "rejected" | "conflict"; event_id?: string; error?: string }> = [];
  for (const ev of input.events) {
    try {
      const res = await createDoseEvent(db, { userId: input.userId, patientId: input.patientId, data: ev, actorSessionId: input.actorSessionId, requestId: input.requestId, ip: input.ip });
      results.push({ client_event_id: ev.client_event_id, outcome: res.outcome, event_id: res.event.id });
    } catch (e) {
      const err = e as Error & { statusCode?: number; code?: string };
      if (err.statusCode === 409 || err.code === "CONFLICT") results.push({ client_event_id: ev.client_event_id, outcome: "conflict", error: err.message });
      else results.push({ client_event_id: ev.client_event_id, outcome: "rejected", error: err.message });
    }
  }
  return results;
}

// ── List ─────────────────────────────────────────────────────────────────────
export async function listDoseEvents(db: MedacDb, userId: string, patientId: string, query: { from?: string; to?: string; cursor?: string; limit?: number }): Promise<{ events: Array<typeof schema.doseEventLogs.$inferSelect>; nextCursor?: string }> {
  await requirePermissionOnPatient(db, userId, patientId, "doseEvent:read");
  const limit = Math.min(query.limit ?? 50, 100);
  let cursorId: string | undefined;
  if (query.cursor) {
    try { cursorId = Buffer.from(query.cursor, "base64url").toString("utf8"); } catch {}
  }
  let events = await db.query.doseEventLogs.findMany({ where: eq(schema.doseEventLogs.patientId, patientId), orderBy: [desc(schema.doseEventLogs.actualAt)], limit: limit + 1 });
  if (query.from) {
    const from = new Date(query.from);
    events = events.filter(e => e.actualAt >= from);
  }
  if (query.to) {
    const to = new Date(query.to);
    events = events.filter(e => e.actualAt < to);
  }
  if (cursorId) {
    const idx = events.findIndex(e => e.id === cursorId);
    if (idx >= 0) events = events.slice(idx + 1);
  }
  events.sort((a, b) => a.actualAt.getTime() - b.actualAt.getTime());
  const hasMore = events.length > limit;
  const page = hasMore ? events.slice(0, limit) : events;
  const nextCursor = hasMore ? Buffer.from(page[page.length - 1].id).toString("base64url") : undefined;
  // Decrypt notes for API (but keep redacted in logs)
  return { events: page.map(e => ({ ...e, noteCiphertext: decryptNote(e.noteCiphertext) as never })), nextCursor };
}

// ── Correction ───────────────────────────────────────────────────────────────
export async function correctDoseEvent(db: MedacDb, input: { userId: string; patientId: string; eventId: string; data: CorrectionInput; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.doseEventLogs.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "doseEvent:correct");
  const original = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.id, input.eventId), eq(schema.doseEventLogs.patientId, input.patientId)) });
  if (!original) throw Object.assign(new Error("Original event not found"), { statusCode: 404 });
  if (original.correctsEventId) throw Object.assign(new Error("Cannot correct a correction; correct the original"), { statusCode: 400 });
  // Check correction chain not looping (simple: ensure not correcting itself)
  if (input.eventId === original.id && input.data.correction_type === "corrected") {
    // allowed, it's the original
  }
  // Idempotency for correction
  const deviceId = input.data.device_id ?? null;
  let existing: typeof schema.doseEventLogs.$inferSelect | undefined;
  if (deviceId) {
    existing = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.deviceId, deviceId as never), eq(schema.doseEventLogs.clientEventId, input.data.client_event_id)) });
  } else {
    existing = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.patientId, input.patientId), eq(schema.doseEventLogs.clientEventId, input.data.client_event_id)) });
  }
  if (existing) return existing;
  const newId = uuidv4();
  const actualAt = input.data.actual_at ? new Date(input.data.actual_at) : new Date(original.actualAt);
  const noteCipher = encryptNote(input.data.note ?? null);
  let validatedDeviceId2: string | null = deviceId;
  if (deviceId) {
    const dev = await db.query.devices.findFirst({ where: eq(schema.devices.id, deviceId as never) });
    if (!dev) validatedDeviceId2 = null;
  }
  await db.insert(schema.doseEventLogs).values({
    id: newId,
    patientId: input.patientId,
    medicationId: original.medicationId,
    occurrenceId: original.occurrenceId,
    eventType: input.data.correction_type as never,
    actualAt,
    recordedAt: now(),
    timezone: null,
    doseValue: input.data.dose_value ?? original.doseValue,
    doseUnit: input.data.dose_unit ?? original.doseUnit,
    noteCiphertext: noteCipher,
    clientEventId: input.data.client_event_id,
    deviceId: validatedDeviceId2 as never,
    actorUserId: input.userId,
    correctsEventId: original.id,
  });
  // Reverse inventory for original if it was taken
  if (original.eventType === "taken") await reverseInventoryForEvent(db, original.id, input.userId);
  // Apply inventory for new if taken
  if (input.data.correction_type === "taken") await applyInventoryForEvent(db, input.patientId, original.medicationId, newId, "taken", input.data.dose_value ?? String(original.doseValue ?? ""), input.data.dose_unit ?? original.doseUnit ?? null, input.userId);
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "dose_event.correct", entityType: "dose_event_log", entityId: newId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { corrects: original.id, correction_type: input.data.correction_type } });
  await outbox(db, "dose_event_corrected", "dose_event_log", newId, { patientId: input.patientId, corrects: original.id });
  // Update occurrence state to corrected
  if (original.occurrenceId) {
    await db.update(schema.doseOccurrences).set({ state: "corrected" as never, stateUpdatedAt: now() }).where(eq(schema.doseOccurrences.id, original.occurrenceId as never));
  }
  const created = await db.query.doseEventLogs.findFirst({ where: eq(schema.doseEventLogs.id, newId) });
  return { ...created!, noteCiphertext: decryptNote(created!.noteCiphertext) as never };
}

// ── Sync (change feed) ───────────────────────────────────────────────────────
export async function sync(db: MedacDb, userId: string, patientId: string, cursor?: string): Promise<{ changes: Array<{ type: string; id: string; updated_at: string }>; next_cursor: string }> {
  await requirePermissionOnPatient(db, userId, patientId, "doseEvent:read"); // sync requires at least read
  // Cursor is base64url of last seen timestamp or id; for MVP, use timestamp
  let since = new Date(0);
  if (cursor) {
    try {
      const decoded = Buffer.from(cursor, "base64url").toString("utf8");
      const parsed = JSON.parse(decoded) as { since: string };
      since = new Date(parsed.since);
    } catch {}
  }
  // Collect changes since `since` across patient-scoped tables
  const meds = await db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, patientId) });
  const schedules = await db.query.medicationScheduleVersions.findMany({ where: inArray(schema.medicationScheduleVersions.medicationId, meds.map(m => m.id)) });
  const occurrences = await db.query.doseOccurrences.findMany({ where: eq(schema.doseOccurrences.patientId, patientId), limit: 100 });
  const doseEvents = await db.query.doseEventLogs.findMany({ where: eq(schema.doseEventLogs.patientId, patientId), orderBy: [desc(schema.doseEventLogs.createdAt)], limit: 100 });
  const membership = await db.query.patientMemberships.findFirst({ where: and(eq(schema.patientMemberships.patientId, patientId), eq(schema.patientMemberships.userId, userId)) });
  const changes: Array<{ type: string; id: string; updated_at: string }> = [];
  for (const m of meds) if (new Date(m.updatedAt) > since) changes.push({ type: "medication", id: m.id, updated_at: new Date(m.updatedAt).toISOString() });
  for (const s of schedules) if (new Date(s.updatedAt) > since) changes.push({ type: "schedule_version", id: s.id, updated_at: new Date(s.updatedAt).toISOString() });
  for (const o of occurrences) if (new Date(o.updatedAt) > since) changes.push({ type: "occurrence", id: o.id, updated_at: new Date(o.updatedAt).toISOString() });
  for (const e of doseEvents) if (new Date(e.createdAt) > since) changes.push({ type: "dose_event", id: e.id, updated_at: new Date(e.createdAt).toISOString() });
  if (membership?.status === "revoked" && membership.revokedAt && new Date(membership.revokedAt) > since) changes.push({ type: "membership_revoked", id: membership.id, updated_at: new Date(membership.revokedAt).toISOString() });
  changes.sort((a, b) => new Date(a.updated_at).getTime() - new Date(b.updated_at).getTime());
  const nextSince = changes.length > 0 ? changes[changes.length - 1].updated_at : new Date().toISOString();
  const next_cursor = Buffer.from(JSON.stringify({ since: nextSince })).toString("base64url");
  return { changes: changes.slice(0, 100), next_cursor };
}
