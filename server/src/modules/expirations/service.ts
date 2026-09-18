/**
 * Expiration service — Phase 8 §6.3 medication_expirations
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
async function assertMedicationBelongs(db: MedacDb, patientId: string, medicationId: string): Promise<typeof schema.patientMedications.$inferSelect> {
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, medicationId), eq(schema.patientMedications.patientId, patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  return med;
}

export async function listExpirations(db: MedacDb, userId: string, patientId: string, medicationId: string): Promise<Array<typeof schema.medicationExpirations.$inferSelect>> {
  await requirePermissionOnPatient(db, userId, patientId, "medication:read");
  await assertMedicationBelongs(db, patientId, medicationId);
  return db.query.medicationExpirations.findMany({ where: eq(schema.medicationExpirations.medicationId, medicationId), orderBy: [desc(schema.medicationExpirations.expirationDate)] });
}

export async function createExpiration(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; data: { expiration_date: string; lot_number?: string | null; quantity_value?: string | null; quantity_unit?: string | null }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.medicationExpirations.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "medication:update");
  await assertMedicationBelongs(db, input.patientId, input.medicationId);
  const id = uuidv4();
  await db.insert(schema.medicationExpirations).values({
    id,
    medicationId: input.medicationId,
    expirationDate: input.data.expiration_date,
    lotNumber: input.data.lot_number ?? null,
    quantityValue: input.data.quantity_value ?? null,
    quantityUnit: input.data.quantity_unit ?? null,
  });
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "expiration.create", entityType: "medication_expiration", entityId: id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const exp = await db.query.medicationExpirations.findFirst({ where: eq(schema.medicationExpirations.id, id) });
  return exp!;
}

export async function updateExpiration(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; expirationId: string; patch: { expiration_date?: string; lot_number?: string | null; quantity_value?: string | null; quantity_unit?: string | null }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.medicationExpirations.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "medication:update");
  await assertMedicationBelongs(db, input.patientId, input.medicationId);
  const exp = await db.query.medicationExpirations.findFirst({ where: and(eq(schema.medicationExpirations.id, input.expirationId), eq(schema.medicationExpirations.medicationId, input.medicationId)) });
  if (!exp) throw Object.assign(new Error("Expiration not found"), { statusCode: 404 });
  const updates: Record<string, unknown> = { updatedAt: now() };
  if (input.patch.expiration_date !== undefined) updates.expirationDate = input.patch.expiration_date;
  if (input.patch.lot_number !== undefined) updates.lotNumber = input.patch.lot_number;
  if (input.patch.quantity_value !== undefined) updates.quantityValue = input.patch.quantity_value;
  if (input.patch.quantity_unit !== undefined) updates.quantityUnit = input.patch.quantity_unit;
  await db.update(schema.medicationExpirations).set(updates as never).where(eq(schema.medicationExpirations.id, input.expirationId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "expiration.update", entityType: "medication_expiration", entityId: input.expirationId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const updated = await db.query.medicationExpirations.findFirst({ where: eq(schema.medicationExpirations.id, input.expirationId) });
  return updated!;
}

export async function deleteExpiration(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; expirationId: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<void> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "medication:update");
  await assertMedicationBelongs(db, input.patientId, input.medicationId);
  const exp = await db.query.medicationExpirations.findFirst({ where: and(eq(schema.medicationExpirations.id, input.expirationId), eq(schema.medicationExpirations.medicationId, input.medicationId)) });
  if (!exp) throw Object.assign(new Error("Expiration not found"), { statusCode: 404 });
  await db.delete(schema.medicationExpirations).where(eq(schema.medicationExpirations.id, input.expirationId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "expiration.delete", entityType: "medication_expiration", entityId: input.expirationId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
}
