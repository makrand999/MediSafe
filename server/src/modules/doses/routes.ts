/**
 * Doses routes — Phase 7 §8.10, §15
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { DoseEventSchema, BatchDoseEventSchema, CorrectionSchema } from "./validation.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function doseRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.post("/patients/:patientId/dose-events", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = DoseEventSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const { event, outcome } = await service.createDoseEvent(db, { userId: user.id, patientId: p.data.patientId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(outcome === "duplicate" ? 200 : 201).send({ event: toApi(event), outcome });
  });

  fastify.post("/patients/:patientId/dose-events/batch", { bodyLimit: 2_000_000 }, async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = BatchDoseEventSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const results = await service.createBatch(db, { userId: user.id, patientId: p.data.patientId, events: b.data.events, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send({ results });
  });

  fastify.get("/patients/:patientId/dose-events", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ from: z.string().datetime({ offset: true }).optional(), to: z.string().datetime({ offset: true }).optional(), cursor: z.string().optional(), limit: z.coerce.number().int().min(1).max(100).optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.listDoseEvents(db, user.id, p.data.patientId, { from: q.data.from, to: q.data.to, cursor: q.data.cursor, limit: q.data.limit });
    return reply.send({ events: result.events.map(toApi), next_cursor: result.nextCursor });
  });

  fastify.post("/patients/:patientId/dose-events/:eventId/corrections", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), eventId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = CorrectionSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const event = await service.correctDoseEvent(db, { userId: user.id, patientId: p.data.patientId, eventId: p.data.eventId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send({ event: toApi(event) });
  });

  fastify.get("/patients/:patientId/sync", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ cursor: z.string().optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.sync(db, user.id, p.data.patientId, q.data.cursor);
    return reply.send(result);
  });
}

function toApi(e: Record<string, unknown>): Record<string, unknown> {
  return {
    id: e.id,
    patient_id: e.patientId,
    medication_id: e.medicationId,
    occurrence_id: e.occurrenceId,
    event_type: e.eventType,
    actual_at: e.actualAt,
    recorded_at: e.recordedAt,
    timezone: e.timezone,
    dose_value: e.doseValue !== null && e.doseValue !== undefined ? String(e.doseValue) : null,
    dose_unit: e.doseUnit,
    note: e.noteCiphertext, // already decrypted in service for list, but for single we decrypt
    client_event_id: e.clientEventId,
    device_id: e.deviceId,
    actor_user_id: e.actorUserId,
    corrects_event_id: e.correctsEventId,
    created_at: e.createdAt,
  };
}
