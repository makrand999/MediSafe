/**
 * Symptoms routes — Phase 8 §8.12
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function symptomRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.get("/patients/:patientId/symptom-logs", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ cursor: z.string().optional(), limit: z.coerce.number().int().min(1).max(100).optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.listSymptoms(db, user.id, p.data.patientId, { cursor: q.data.cursor, limit: q.data.limit });
    return reply.send({ logs: result.logs.map(toApi), next_cursor: result.nextCursor });
  });

  fastify.post("/patients/:patientId/symptom-logs", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = z.object({ occurred_at: z.string().datetime({ offset: true }), note: z.string().max(2000).nullable().optional(), linked_dose_event_id: z.string().uuid().nullable().optional() }).safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const log = await service.createSymptom(db, { userId: user.id, patientId: p.data.patientId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send(toApi(log));
  });

  fastify.post("/patients/:patientId/symptom-logs/:logId/corrections", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), logId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = z.object({ note: z.string().max(2000).nullable().optional(), reason: z.string().max(500).optional() }).safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const log = await service.correctSymptom(db, { userId: user.id, patientId: p.data.patientId, logId: p.data.logId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send(toApi(log));
  });
}

function toApi(l: Record<string, unknown>): Record<string, unknown> {
  return {
    id: l.id,
    patient_id: l.patientId,
    occurred_at: l.occurredAt,
    note: l.noteCiphertext,
    linked_dose_event_id: l.linkedDoseEventId,
    actor_user_id: l.actorUserId,
    created_at: l.createdAt,
    corrected_by_log_id: l.correctedByLogId,
  };
}
