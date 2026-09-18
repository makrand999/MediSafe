/**
 * Injection site logs — Phase 8 §6.7
 */
import { eq, and, desc } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";

function now() { return new Date(); }
async function audit(db: MedacDb, params: { actorUserId?: string | null; actorSessionId?: string | null; patientId?: string | null; action: string; entityType: string; entityId?: string | null; requestId?: string | null; ipPrefix?: string | null; metadataJson?: Record<string, unknown> }): Promise<void> {
  await db.insert(schema.auditEvents).values({ id: uuidv4(), occurredAt: now(), actorUserId: params.actorUserId ?? null, actorSessionId: params.actorSessionId ?? null, patientId: params.patientId ?? null, action: params.action, entityType: params.entityType, entityId: params.entityId ?? null, requestId: params.requestId ?? null, ipPrefix: params.ipPrefix ?? null, metadataJson: params.metadataJson ?? {} });
}

const ALLOWED_SITES = new Set(["left_arm", "right_arm", "left_thigh", "right_thigh", "abdomen_left", "abdomen_right", "buttock_left", "buttock_right", "other"]);

export async function listInjectionSites(db: MedacDb, userId: string, patientId: string, query: { limit?: number; medicationId?: string }): Promise<Array<typeof schema.injectionSiteLogs.$inferSelect>> {
  await requirePermissionOnPatient(db, userId, patientId, "injection:read");
  const where = query.medicationId ? and(eq(schema.injectionSiteLogs.patientId, patientId), eq(schema.injectionSiteLogs.medicationId, query.medicationId)) : eq(schema.injectionSiteLogs.patientId, patientId);
  return db.query.injectionSiteLogs.findMany({ where, orderBy: [desc(schema.injectionSiteLogs.occurredAt)], limit: Math.min(query.limit ?? 50, 100) });
}

export async function createInjectionSite(db: MedacDb, input: { userId: string; patientId: string; data: { medication_id: string; dose_event_id?: string | null; site_code: string; occurred_at: string }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.injectionSiteLogs.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "injection:write");
  if (!ALLOWED_SITES.has(input.data.site_code)) throw Object.assign(new Error("Invalid site_code"), { statusCode: 400 });
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, input.data.medication_id), eq(schema.patientMedications.patientId, input.patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  if (input.data.dose_event_id) {
    const ev = await db.query.doseEventLogs.findFirst({ where: and(eq(schema.doseEventLogs.id, input.data.dose_event_id), eq(schema.doseEventLogs.patientId, input.patientId)) });
    if (!ev) throw Object.assign(new Error("Dose event not found"), { statusCode: 404 });
  }
  const id = uuidv4();
  await db.insert(schema.injectionSiteLogs).values({
    id,
    patientId: input.patientId,
    medicationId: input.data.medication_id,
    doseEventId: input.data.dose_event_id ?? null,
    siteCode: input.data.site_code,
    occurredAt: new Date(input.data.occurred_at),
    actorUserId: input.userId,
  });
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "injection_site.create", entityType: "injection_site_log", entityId: id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const log = await db.query.injectionSiteLogs.findFirst({ where: eq(schema.injectionSiteLogs.id, id) });
  return log!;
}
