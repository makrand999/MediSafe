/**
 * Schedule routes — Phase 5 §8.9, §14
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { CreateScheduleSchema } from "./validation.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function scheduleRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.get("/patients/:patientId/medications/:medicationId/schedules", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const versions = await service.listSchedules(db, user.id, p.data.patientId, p.data.medicationId);
    return reply.send({ schedules: versions.map(toApi) });
  });

  fastify.post("/patients/:patientId/medications/:medicationId/schedules", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = CreateScheduleSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const { version, preview } = await service.createSchedule(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send({ schedule: toApi(version), preview });
  });

  fastify.get("/patients/:patientId/medications/:medicationId/schedules/:versionId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid(), versionId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.getSchedule(db, user.id, p.data.patientId, p.data.medicationId, p.data.versionId);
    return reply.send({ schedule: toApi(result.version), fixed_times: result.fixedTimes, interval: result.interval, cycle: result.cycle, taper_steps: result.taperSteps, preview: result.preview });
  });

  fastify.post("/patients/:patientId/medications/:medicationId/schedules/:versionId/supersede", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), medicationId: z.string().uuid(), versionId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = CreateScheduleSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const { version, preview } = await service.supersedeSchedule(db, { userId: user.id, patientId: p.data.patientId, medicationId: p.data.medicationId, versionId: p.data.versionId, data: b.data, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(201).send({ schedule: toApi(version), preview });
  });

  fastify.get("/patients/:patientId/occurrences", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ from: z.string().datetime().optional(), to: z.string().datetime().optional(), cursor: z.string().optional(), limit: z.coerce.number().int().min(1).max(100).optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.listOccurrences(db, user.id, p.data.patientId, { from: q.data.from, to: q.data.to, cursor: q.data.cursor, limit: q.data.limit });
    return reply.send({ occurrences: result.occurrences.map(toOccurrenceApi), next_cursor: result.nextCursor });
  });
}

function toApi(v: Record<string, unknown>): Record<string, unknown> {
  return {
    id: v.id,
    medication_id: v.medicationId,
    version_number: v.versionNumber,
    schedule_type: v.scheduleType,
    timing_mode: v.timingMode,
    timezone: v.timezone,
    effective_from: v.effectiveFrom,
    effective_until: v.effectiveUntil,
    miss_window_minutes: v.missWindowMinutes,
    created_by_user_id: v.createdByUserId,
    created_at: v.createdAt,
    updated_at: v.updatedAt,
  };
}

function toOccurrenceApi(o: Record<string, unknown>): Record<string, unknown> {
  return {
    id: o.id,
    patient_id: o.patientId,
    medication_id: o.medicationId,
    schedule_version_id: o.scheduleVersionId,
    scheduled_at_utc: o.scheduledAtUtc,
    scheduled_local_datetime: o.scheduledLocalDatetime,
    timezone: o.timezone,
    utc_offset_minutes: o.utcOffsetMinutes,
    dst_adjusted: o.dstAdjusted,
    nominal_dose_value: o.nominalDoseValue !== null && o.nominalDoseValue !== undefined ? String(o.nominalDoseValue) : null,
    nominal_dose_unit: o.nominalDoseUnit,
    state: o.state,
    generated_at: o.generatedAt,
  };
}
