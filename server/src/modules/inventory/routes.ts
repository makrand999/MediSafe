/**
 * Inventory routes — Phase 8 §8.11
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { CreateTransactionSchema, UpdateSettingsSchema } from "./validation.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function inventoryRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.get("/patients/:patientId/medications/:medicationId/inventory", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.getInventory(db, user.id, p.data.patientId, p.data.medicationId);
    return reply.send({
      account: result.account ? { id: result.account.id, unit: result.account.unit, low_stock_threshold_value: result.account.lowStockThresholdValue ? String(result.account.lowStockThresholdValue) : null } : null,
      balance: result.balance,
      low_stock: result.lowStock,
      forecast_days: result.forecastDays,
      recent_transactions: result.transactions.map(t => ({ id: t.id, transaction_type: t.transactionType, quantity_delta: String(t.quantityDelta), effective_at: t.effectiveAt })),
    });
  });

  fastify.post("/patients/:patientId/medications/:medicationId/inventory/transactions", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = CreateTransactionSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const tx = await service.createTransaction(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send({ transaction: { id: tx.id, transaction_type: tx.transactionType, quantity_delta: String(tx.quantityDelta), effective_at: tx.effectiveAt } });
  });

  fastify.get("/patients/:patientId/medications/:medicationId/inventory/transactions", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ cursor: z.string().optional(), limit: z.coerce.number().int().min(1).max(100).optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.listTransactions(db, user.id, p.data.patientId, p.data.medicationId, { cursor: q.data.cursor, limit: q.data.limit });
    return reply.send({ transactions: result.transactions.map(t => ({ id: t.id, transaction_type: t.transactionType, quantity_delta: String(t.quantityDelta), effective_at: t.effectiveAt })), next_cursor: result.nextCursor });
  });

  fastify.patch("/patients/:patientId/medications/:medicationId/inventory/settings", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = UpdateSettingsSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const account = await service.updateSettings(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send({ account: { id: account.id, unit: account.unit, low_stock_threshold_value: account.lowStockThresholdValue ? String(account.lowStockThresholdValue) : null } });
  });
}
