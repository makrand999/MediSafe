/**
 * Expiration routes — Phase 8 §8.12
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function expirationRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.get("/patients/:patientId/medications/:medicationId/expirations", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const exps = await service.listExpirations(db, user.id, p.data.patientId, p.data.medicationId);
    return reply.send({ expirations: exps.map(toApi) });
  });

  fastify.post("/patients/:patientId/medications/:medicationId/expirations", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = z.object({ expiration_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), lot_number: z.string().max(100).nullable().optional(), quantity_value: z.string().regex(/^\d+(\.\d+)?$/).nullable().optional(), quantity_unit: z.string().max(32).nullable().optional() }).safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const exp = await service.createExpiration(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send(toApi(exp));
  });

  fastify.patch("/patients/:patientId/medications/:medicationId/expirations/:expirationId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid(), expirationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = z.object({ expiration_date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(), lot_number: z.string().max(100).nullable().optional(), quantity_value: z.string().regex(/^\d+(\.\d+)?$/).nullable().optional(), quantity_unit: z.string().max(32).nullable().optional() }).safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const exp = await service.updateExpiration(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, expirationId: p.data.expirationId, patch: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(exp));
  });

  fastify.delete("/patients/:patientId/medications/:medicationId/expirations/:expirationId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid(), expirationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    await service.deleteExpiration(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, expirationId: p.data.expirationId, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(204).send();
  });
}

function toApi(e: Record<string, unknown>): Record<string, unknown> {
  return {
    id: e.id,
    medication_id: e.medicationId,
    expiration_date: e.expirationDate,
    lot_number: e.lotNumber,
    quantity_value: e.quantityValue ? String(e.quantityValue) : null,
    quantity_unit: e.quantityUnit,
    created_at: e.createdAt,
    updated_at: e.updatedAt,
  };
}
