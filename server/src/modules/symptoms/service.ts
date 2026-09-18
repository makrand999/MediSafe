/**
 * Symptoms service — Phase 8 §6.7, §8.12
 * No causal claims, just timeline
 */
import { eq, and, desc } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";

function now() { return new Date(); }
function encryptNote(note: string | null | undefined): string | null {
  if (!note) return null;
  return Buffer.from(note, "utf8").toString("base64");
}
function decryptNote(cipher: string | null | undefined): string | null {
  if (!cipher) return null;
  try { return Buffer.from(cipher, "base64").toString("utf8"); } catch { return null; }
}
async function audit(db: MedacDb, params: { actorUserId?: string | null; actorSessionId?: string | null; patientId?: string | null; action: string; entityType: string; entityId?: string | null; requestId?: string | null; ipPrefix?: string | null; metadataJson?: Record<string, unknown> }): Promise<void> {
  await db.insert(schema.auditEvents).values({ id: uuidv4(), occurredAt: now(), actorUserId: params.actorUserId ?? null, actorSessionId: params.actorSessionId ?? null, patientId: params.patientId ?? null, action: params.action, entityType: params.entityType, entityId: params.entityId ?? null, requestId: params.requestId ?? null, ipPrefix: params.ipPrefix ?? null, metadataJson: params.metadataJson ?? {} });
}

export async function listSymptoms(db: MedacDb, userId: string, patientId: string, query: { limit?: number; cursor?: string }): Promise<{ logs: Array<typeof schema.symptomLogs.$inferSelect>; nextCursor?: string }> {
  await requirePermissionOnPatient(db, userId, patientId, "symptom:read");
  const limit = Math.min(query.limit ?? 50, 100);
  let cursorId: string | undefined;
  if (query.cursor) try { cursorId = Buffer.from(query.cursor, "base64url").toString("utf8"); } catch {}
  let logs = await db.query.symptomLogs.findMany({ where: eq(schema.symptomLogs.patientId, patientId), orderBy: [desc(schema.symptomLogs.occurredAt)], limit: limit + 1 });
  if (cursorId) {
    const idx = logs.findIndex(l => l.id === cursorId);
    if (idx >= 0) logs = logs.slice(idx + 1);
  }
  const hasMore = logs.length > limit;
  const page = hasMore ? logs.slice(0, limit) : logs;
  const nextCursor = hasMore ? Buffer.from(page[page.length - 1].id).toString("base64url") : undefined;
  return { logs: page.map(l => ({ ...l, noteCiphertext: decryptNote(l.noteCiphertext) as never })), nextCursor };
}

export async function createSymptom(db: MedacDb, input: { userId: string; patientId: string; data: { occurred_at: string; note?: string | null; linked_dose_event_id?: string | null }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.symptomLogs.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "symptom:write");
  if (input.data.linked_dose_event_id) {
    const ev = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.id, input.data.linked_dose_event_id), eq(schema.doseEventLogs.patientId, input.patientId)) });
    if (!ev) throw Object.assign(new Error("Linked dose event not found"), { statusCode: 404 });
  }
  const id = uuidv4();
  await db.insert(schema.symptomLogs).values({
    id,
    patientId: input.patientId,
    occurredAt: new Date(input.data.occurred_at),
    noteCiphertext: encryptNote(input.data.note ?? null),
    linkedDoseEventId: input.data.linked_dose_event_id ?? null,
    actorUserId: input.userId,
  });
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "symptom.create", entityType: "symptom_log", entityId: id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const log = await db.query.symptomLogs.findFirst({ where: eq(schema.symptomLogs.id, id) });
  return { ...log!, noteCiphertext: decryptNote(log!.noteCiphertext) as never };
}

export async function correctSymptom(db: MedacDb, input: { userId: string; patientId: string; logId: string; data: { note?: string | null; reason?: string }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.symptomLogs.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "symptom:write");
  const original = await db.query.symptomLogs.findFirst({ where: and(eq(schema.symptomLogs.id, input.logId), eq(schema.symptomLogs.patientId, input.patientId)) });
  if (!original) throw Object.assign(new Error("Symptom log not found"), { statusCode: 404 });
  if (original.correctedByLogId) throw Object.assign(new Error("Already corrected"), { statusCode: 400 });
  const newId = uuidv4();
  await db.insert(schema.symptomLogs).values({
    id: newId,
    patientId: input.patientId,
    occurredAt: original.occurredAt,
    noteCiphertext: encryptNote(input.data.note ?? null),
    linkedDoseEventId: original.linkedDoseEventId,
    actorUserId: input.userId,
  });
  await db.update(schema.symptomLogs).set({ correctedByLogId: newId }).where(eq(schema.symptomLogs.id, original.id));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "symptom.correct", entityType: "symptom_log", entityId: newId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { corrects: original.id } });
  const created = await db.query.symptomLogs.findFirst({ where: eq(schema.symptomLogs.id, newId) });
  return { ...created!, noteCiphertext: decryptNote(created!.noteCiphertext) as never };
}
