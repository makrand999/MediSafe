/**
 * Alerts routes — Phase 9 §8.14, §16
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { PutPreferencesSchema } from "./validation.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function alertRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.get("/patients/:patientId/alerts", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ status: z.string().optional(), limit: z.coerce.number().int().min(1).max(100).optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const alerts = await service.listAlerts(db, user.id, p.data.patientId, { status: q.data.status, limit: q.data.limit });
    return reply.send({ alerts: alerts.map(toApi) });
  });

  fastify.post("/patients/:patientId/alerts/:alertId/acknowledge", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), alertId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const alert = await service.acknowledgeAlert(db, { userId: user.id, patientId: p.data.patientId, alertId: p.data.alertId, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send(toApi(alert));
  });

  fastify.get("/patients/:patientId/alert-preferences", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const prefs = await service.getPreferences(db, user.id, p.data.patientId);
    return reply.send({ preferences: prefs.map(toPrefApi) });
  });

  fastify.put("/patients/:patientId/alert-preferences", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = PutPreferencesSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const prefs = await service.putPreferences(db, { userId: user.id, patientId: p.data.patientId, preferences: b.data.preferences as never, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send({ preferences: prefs.map(toPrefApi) });
  });

  // Manual trigger for deterministic alerts (for testing, would be job in production)
  fastify.post("/patients/:patientId/alerts/generate", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    // Require manager or owner to trigger
    const { requirePermissionOnPatient } = await import("../patients/service.js");
    await requirePermissionOnPatient(db, user.id, p.data.patientId, "alert:read");
    const low = await service.generateLowStockAlerts(db, p.data.patientId);
    const exp = await service.generateExpirationAlerts(db, p.data.patientId);
    const stale = await service.generateStaleDeviceAlerts(db, p.data.patientId);
    const missed = await service.generateMissedHighAttentionAlerts(db, p.data.patientId);
    return reply.send({ generated: { low_stock: low, expiration: exp, stale_device: stale, missed: missed } });
  });
}

function toApi(a: Record<string, unknown>): Record<string, unknown> {
  return {
    id: a.id,
    patient_id: a.patientId,
    alert_type: a.alertType,
    severity: a.severity,
    status: a.status,
    source_entity_type: a.sourceEntityType,
    source_entity_id: a.sourceEntityId,
    message_key: a.messageKey,
    created_at: a.createdAt,
    acknowledged_at: a.acknowledgedAt,
    resolved_at: a.resolvedAt,
  };
}

function toPrefApi(p: Record<string, unknown>): Record<string, unknown> {
  return {
    id: p.id,
    patient_id: p.patientId,
    user_id: p.userId,
    alert_type: p.alertType,
    enabled: p.enabled,
    delay_minutes: p.delayMinutes,
    quiet_hours_start: p.quietHoursStart,
    quiet_hours_end: p.quietHoursEnd,
    timezone: p.timezone,
  };
}
