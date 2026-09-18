/**
 * Medications service — lifecycle, normalization, audit §5.3, §6.3, §8.8
 */
import { eq, and, desc } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";
import type { CreateMedicationInput, UpdateMedicationInput } from "./validation.js";
import { getDrugNormalizationProvider } from "../drug-normalization/provider.js";

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

function assertValidStatusTransition(current: string, next: string): void {
  const allowed: Record<string, string[]> = {
    draft: ["active", "archived"],
    active: ["paused", "discontinued", "archived"],
    paused: ["active", "discontinued", "archived"],
    discontinued: ["archived"],
    archived: [],
  };
  if (!allowed[current]?.includes(next)) {
    const err = new Error(`Invalid status transition ${current} -> ${next}`) as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "INVALID_STATUS_TRANSITION";
    throw err;
  }
}

export async function createMedication(db: MedacDb, input: { userId: string; patientId: string; data: CreateMedicationInput; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.patientMedications.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "medication:create");
  const id = uuidv4();
  const status = input.data.status ?? "draft";
  // Validate status is draft or active initially (not discontinued/archived directly)
  if (!["draft", "active"].includes(status)) {
    const err = new Error("Initial status must be draft or active") as Error & { statusCode?: number };
    err.statusCode = 400;
    throw err;
  }
  const normalizationStatus = input.data.normalization_status ?? "unresolved";
  // If ndc provided, try resolve but never block creation
  let drugConceptId = input.data.drug_concept_id ?? null;
  let finalNormalizationStatus = normalizationStatus;
  let ndcToStore: string | null = input.data.ndc ?? null;
  if (ndcToStore && !drugConceptId) {
    try {
      const provider = getDrugNormalizationProvider();
      const res = await provider.resolveNdc(ndcToStore);
      if (res.status === "resolved" && res.rxcui) {
        // Ensure drug_concept exists or create placeholder
        const existing = await db.query.drugConcepts.findFirst({ where: eq(schema.drugConcepts.rxnormRxcui, res.rxcui) });
        if (existing) drugConceptId = existing.id;
        else {
          const conceptId = uuidv4();
          await db.insert(schema.drugConcepts).values({ id: conceptId, rxnormRxcui: res.rxcui, conceptName: res.name ?? ndcToStore, source: "rxnorm", ndc: ndcToStore });
          drugConceptId = conceptId;
        }
        finalNormalizationStatus = "resolved";
      } else if (res.status === "ambiguous") finalNormalizationStatus = "ambiguous";
      else if (res.status === "temporarily_unavailable") finalNormalizationStatus = "pending";
      else if (res.status === "unresolved") finalNormalizationStatus = "unresolved";
    } catch {
      finalNormalizationStatus = "failed";
    }
  }
  // If drug_concept_id provided but not found, validate exists
  if (drugConceptId) {
    const dc = await db.query.drugConcepts.findFirst({ where: eq(schema.drugConcepts.id, drugConceptId) });
    if (!dc) throw Object.assign(new Error("drug_concept not found"), { statusCode: 404 });
  }
  await db.insert(schema.patientMedications).values({
    id,
    patientId: input.patientId,
    drugConceptId: drugConceptId ?? null,
    enteredName: input.data.entered_name,
    enteredStrengthValue: input.data.entered_strength_value ?? null,
    enteredStrengthUnit: input.data.entered_strength_unit ?? null,
    form: input.data.form ?? null,
    route: input.data.route ?? null,
    doseQuantityValue: input.data.dose_quantity_value,
    doseQuantityUnit: input.data.dose_quantity_unit,
    indicationText: input.data.indication_text ?? null,
    prescriberText: input.data.prescriber_text ?? null,
    pharmacyText: input.data.pharmacy_text ?? null,
    labelInstructionsText: input.data.label_instructions_text ?? null,
    normalizationStatus: finalNormalizationStatus as never,
    status: status as never,
    highAttentionUserFlag: input.data.high_attention_user_flag ?? false,
    startDate: input.data.start_date ?? null,
    endDate: input.data.end_date ?? null,
    createdByUserId: input.userId,
    updatedByUserId: input.userId,
  });
  // If NDC was stored, also ensure drug_concepts ndc field for lookup (optional)
  if (ndcToStore && drugConceptId) {
    // update ndc on concept if not set
    const dc = await db.query.drugConcepts.findFirst({ where: eq(schema.drugConcepts.id, drugConceptId) });
    if (dc && !dc.ndc) await db.update(schema.drugConcepts).set({ ndc: ndcToStore }).where(eq(schema.drugConcepts.id, drugConceptId));
  }
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "medication.create", entityType: "patient_medication", entityId: id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { status, normalizationStatus: finalNormalizationStatus } });
  await outbox(db, "medication_created", "patient_medication", id, { patientId: input.patientId });
  if (status === "active") await outbox(db, "medication_activated", "patient_medication", id, { patientId: input.patientId });
  const med = await db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, id) });
  return med!;
}

export async function listMedications(db: MedacDb, userId: string, patientId: string, query: { status?: string; includeArchived?: boolean }): Promise<Array<typeof schema.patientMedications.$inferSelect>> {
  await requirePermissionOnPatient(db, userId, patientId, "medication:read");
  let meds = await db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, patientId), orderBy: [desc(schema.patientMedications.createdAt)] });
  if (!query.includeArchived) meds = meds.filter(m => m.status !== "archived");
  if (query.status) meds = meds.filter(m => m.status === query.status);
  return meds;
}

export async function getMedication(db: MedacDb, userId: string, patientId: string, medicationId: string): Promise<typeof schema.patientMedications.$inferSelect> {
  await requirePermissionOnPatient(db, userId, patientId, "medication:read");
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, medicationId), eq(schema.patientMedications.patientId, patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  return med;
}

export async function updateMedication(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; patch: UpdateMedicationInput; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.patientMedications.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "medication:update");
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, input.medicationId), eq(schema.patientMedications.patientId, input.patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  if (med.status === "discontinued" || med.status === "archived") {
    // Allow limited updates but not status change via PATCH (use dedicated endpoints)
    if (input.patch.status && input.patch.status !== med.status) throw Object.assign(new Error("Use dedicated status endpoint"), { statusCode: 400 });
  }
  if (input.patch.expected_updated_at) {
    const expected = new Date(input.patch.expected_updated_at);
    if (med.updatedAt && Math.abs(med.updatedAt.getTime() - expected.getTime()) > 1000) {
      const err = new Error("Medication version conflict") as Error & { statusCode?: number; code?: string };
      err.statusCode = 409;
      err.code = "MEDICATION_VERSION_CONFLICT";
      throw err;
    }
  }
  const updates: Record<string, unknown> = { updatedAt: now(), updatedByUserId: input.userId };
  if (input.patch.entered_name !== undefined) updates.enteredName = input.patch.entered_name;
  if (input.patch.entered_strength_value !== undefined) updates.enteredStrengthValue = input.patch.entered_strength_value ?? null;
  if (input.patch.entered_strength_unit !== undefined) updates.enteredStrengthUnit = input.patch.entered_strength_unit ?? null;
  if (input.patch.form !== undefined) updates.form = input.patch.form ?? null;
  if (input.patch.route !== undefined) updates.route = input.patch.route ?? null;
  if (input.patch.dose_quantity_value !== undefined) updates.doseQuantityValue = input.patch.dose_quantity_value;
  if (input.patch.dose_quantity_unit !== undefined) updates.doseQuantityUnit = input.patch.dose_quantity_unit;
  if (input.patch.indication_text !== undefined) updates.indicationText = input.patch.indication_text ?? null;
  if (input.patch.prescriber_text !== undefined) updates.prescriberText = input.patch.prescriber_text ?? null;
  if (input.patch.pharmacy_text !== undefined) updates.pharmacyText = input.patch.pharmacy_text ?? null;
  if (input.patch.label_instructions_text !== undefined) updates.labelInstructionsText = input.patch.label_instructions_text ?? null;
  if (input.patch.high_attention_user_flag !== undefined) updates.highAttentionUserFlag = input.patch.high_attention_user_flag;
  if (input.patch.start_date !== undefined) updates.startDate = input.patch.start_date ?? null;
  if (input.patch.end_date !== undefined) updates.endDate = input.patch.end_date ?? null;
  if (input.patch.drug_concept_id !== undefined) {
    if (input.patch.drug_concept_id) {
      const dc = await db.query.drugConcepts.findFirst({ where: eq(schema.drugConcepts.id, input.patch.drug_concept_id) });
      if (!dc) throw Object.assign(new Error("drug_concept not found"), { statusCode: 404 });
      updates.drugConceptId = input.patch.drug_concept_id;
    } else updates.drugConceptId = null;
  }
  if (input.patch.normalization_status !== undefined) updates.normalizationStatus = input.patch.normalization_status;
  // status via patch not allowed to jump discontinuation without endpoint
  if (input.patch.status !== undefined && input.patch.status !== med.status) {
    assertValidStatusTransition(med.status, input.patch.status);
    updates.status = input.patch.status;
    if (input.patch.status === "discontinued") updates.discontinuedAt = now();
    if (input.patch.status === "archived") updates.archivedAt = now();
  }
  await db.update(schema.patientMedications).set(updates as never).where(eq(schema.patientMedications.id, input.medicationId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "medication.update", entityType: "patient_medication", entityId: input.medicationId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const updated = await db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, input.medicationId) });
  return updated!;
}

export async function changeMedicationStatus(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; newStatus: "active" | "paused" | "discontinued" | "archived"; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.patientMedications.$inferSelect> {
  const permissionMap: Record<string, string> = { active: "medication:activate", paused: "medication:pause", discontinued: "medication:discontinue", archived: "medication:archive" };
  await requirePermissionOnPatient(db, input.userId, input.patientId, permissionMap[input.newStatus] as never);
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, input.medicationId), eq(schema.patientMedications.patientId, input.patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  // Resolve intended transition: paused -> active is resume
  let targetStatus = input.newStatus;
  if (med.status === "paused" && input.newStatus === "active") targetStatus = "active";
  if (med.status === "active" && input.newStatus === "paused") targetStatus = "paused";
  assertValidStatusTransition(med.status, targetStatus);
  const updates: Record<string, unknown> = { status: targetStatus, updatedAt: now(), updatedByUserId: input.userId };
  if (targetStatus === "paused") updates.pausedAt = now();
  if (targetStatus === "active" && med.status === "paused") updates.pausedAt = null; // clear
  if (targetStatus === "discontinued") updates.discontinuedAt = now();
  if (targetStatus === "archived") {
    // If dose history exists, archive (keep history). Otherwise archive anyway per plan.
    updates.archivedAt = now();
  }
  await db.update(schema.patientMedications).set(updates as never).where(eq(schema.patientMedications.id, input.medicationId));
  const actionMap: Record<string, string> = { active: "medication.activate", paused: "medication.pause", discontinued: "medication.discontinue", archived: "medication.archive" };
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: actionMap[targetStatus] ?? "medication.status_change", entityType: "patient_medication", entityId: input.medicationId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { from: med.status, to: targetStatus } });
  const eventMap: Record<string, string> = { active: "medication_activated", paused: "medication_status_changed", discontinued: "medication_status_changed", archived: "medication_status_changed" };
  await outbox(db, eventMap[targetStatus] ?? "medication_status_changed", "patient_medication", input.medicationId, { patientId: input.patientId, from: med.status, to: targetStatus });
  const updated = await db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, input.medicationId) });
  return updated!;
}

export async function setNormalizationSelection(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; drugConceptId: string | null; normalizationStatus: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.patientMedications.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "medication:update");
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, input.medicationId), eq(schema.patientMedications.patientId, input.patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  if (input.drugConceptId) {
    const dc = await db.query.drugConcepts.findFirst({ where: eq(schema.drugConcepts.id, input.drugConceptId) });
    if (!dc) throw Object.assign(new Error("drug_concept not found"), { statusCode: 404 });
  }
  await db.update(schema.patientMedications).set({ drugConceptId: input.drugConceptId as never, normalizationStatus: input.normalizationStatus as never, updatedAt: now(), updatedByUserId: input.userId }).where(eq(schema.patientMedications.id, input.medicationId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "medication.normalization_selection", entityType: "patient_medication", entityId: input.medicationId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { drugConceptId: input.drugConceptId, normalizationStatus: input.normalizationStatus } });
  const updated = await db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, input.medicationId) });
  return updated!;
}
