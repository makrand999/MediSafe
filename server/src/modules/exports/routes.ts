/**
 * Exports routes — Phase 10 §8.13, §17.1
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function exportRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.post("/patients/:patientId/exports", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = z.object({ from: z.string().datetime({ offset: true }).optional(), to: z.string().datetime({ offset: true }).optional(), include_symptoms: z.boolean().optional() }).safeParse(req.body ?? {});
    if (!b.success) return sendValidationError(req, reply, b.error.issues);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const exp = await service.createExport(db, { userId: user.id, patientId: p.data.patientId, from: b.data.from, to: b.data.to, includeSymptoms: b.data.include_symptoms, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(202).send({ export: { id: exp.id, status: exp.status, created_at: exp.createdAt, expires_at: exp.expiresAt } });
  });

  fastify.get("/patients/:patientId/exports/:exportId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), exportId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const exp = await service.getExport(db, user.id, p.data.patientId, p.data.exportId);
    return reply.send({ export: { id: exp.id, status: exp.status, created_at: exp.createdAt, expires_at: exp.expiresAt, download_token_expires_at: exp.downloadTokenExpiresAt } });
  });

  fastify.get("/patients/:patientId/exports/:exportId/download", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), exportId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const q = z.object({ token: z.string().optional() }).safeParse((req as { query: unknown }).query);
    if (!q.success) return sendValidationError(req, reply, q.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    const result = await service.downloadExport(db, user.id, p.data.patientId, p.data.exportId, q.data.token);
    // For MVP, return the patient.csv as download (single file)
    try {
      const content = await readFile(join(result.filePath, "patient.csv"), "utf8");
      reply.header("Content-Type", "text/csv");
      reply.header("Content-Disposition", `attachment; filename="export-${p.data.exportId}.csv"`);
      return reply.send(content);
    } catch {
      return reply.code(404).send({ error: { code: "NOT_FOUND", message: "Export file not found", request_id: (req as unknown as { id: string }).id } });
    }
  });

  fastify.delete("/patients/:patientId/exports/:exportId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ patientId: z.string().uuid(), exportId: z.string().uuid() }).safeParse((req as { params: unknown }).params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const { user } = ctx(req);
    await service.deleteExport(db, user.id, p.data.patientId, p.data.exportId);
    return reply.code(204).send();
  });
}
