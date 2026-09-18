/**
 * Medications routes — Phase 4 §8.8
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { CreateMedicationSchema, UpdateMedicationSchema } from "./validation.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function medicationRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.get("/patients/:patientId/medications", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ status: z.string().optional(), include_archived: z.coerce.boolean().optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const meds = await service.listMedications(db, user.id, p.data.patientId, { status: q.data.status, includeArchived: q.data.include_archived });
    return reply.send({ medications: meds.map(toApi) });
  });

  fastify.post("/patients/:patientId/medications", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = CreateMedicationSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.createMedication(db, { userId: user.id, patientId: p.data.patientId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send(toApi(med));
  });

  fastify.get("/patients/:patientId/medications/:medicationId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const med = await service.getMedication(db, user.id, p.data.patientId, p.data.medicationId);
    return reply.send(toApi(med));
  });

  fastify.patch("/patients/:patientId/medications/:medicationId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = UpdateMedicationSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.updateMedication(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, patch: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(med));
  });

  fastify.post("/patients/:patientId/medications/:medicationId/activate", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.changeMedicationStatus(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, newStatus: "active", actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(med));
  });

  fastify.post("/patients/:patientId/medications/:medicationId/pause", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.changeMedicationStatus(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, newStatus: "paused", actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(med));
  });

  fastify.post("/patients/:patientId/medications/:medicationId/resume", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.changeMedicationStatus(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, newStatus: "active", actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(med));
  });

  fastify.post("/patients/:patientId/medications/:medicationId/discontinue", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.changeMedicationStatus(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, newStatus: "discontinued", actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(med));
  });

  fastify.post("/patients/:patientId/medications/:medicationId/archive", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.changeMedicationStatus(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, newStatus: "archived", actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(med));
  });

  fastify.post("/patients/:patientId/medications/:medicationId/normalization-selection", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = z.object({ drug_concept_id: z.string().uuid().nullable(), normalization_status: z.enum(["unresolved", "pending", "resolved", "ambiguous", "failed", "manually_confirmed"]) }).safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const med = await service.setNormalizationSelection(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, drugConceptId: b.data.drug_concept_id, normalizationStatus: b.data.normalization_status, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(med));
  });
}

function toApi(m: Record<string, unknown>): Record<string, unknown> {
  return {
    id: m.id,
    patient_id: m.patientId,
    drug_concept_id: m.drugConceptId,
    entered_name: m.enteredName,
    entered_strength_value: m.enteredStrengthValue !== null && m.enteredStrengthValue !== undefined ? String(m.enteredStrengthValue) : null,
    entered_strength_unit: m.enteredStrengthUnit,
    form: m.form,
    route: m.route,
    dose_quantity_value: String(m.doseQuantityValue),
    dose_quantity_unit: m.doseQuantityUnit,
    indication_text: m.indicationText,
    prescriber_text: m.prescriberText,
    pharmacy_text: m.pharmacyText,
    label_instructions_text: m.labelInstructionsText,
    normalization_status: m.normalizationStatus,
    status: m.status,
    high_attention_user_flag: m.highAttentionUserFlag,
    start_date: m.startDate,
    end_date: m.endDate,
    created_by_user_id: m.createdByUserId,
    updated_by_user_id: m.updatedByUserId,
    created_at: m.createdAt,
    updated_at: m.updatedAt,
    paused_at: m.pausedAt,
    discontinued_at: m.discontinuedAt,
    archived_at: m.archivedAt,
  };
}
