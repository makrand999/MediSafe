/**
 * modules/patients/routes.ts — Patient & membership endpoints §8.6, §7.1
 */
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { assertValidRole } from "../../lib/permissions.js";

export async function patientRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: import("fastify").FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    user: (req as unknown as { user: { id: string; sessionId: string } }).user,
  });
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  fastify.get("/patients", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const db = getDb();
    const { user } = ctx(req);
    const patients = await service.listPatients(db, user.id);
    return reply.send({ patients });
  });

  fastify.post("/patients", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const body = z.object({
      display_name: z.string().min(1).max(200),
      preferred_timezone: z.string().min(1).max(100).optional(),
      date_of_birth: z.string().date().nullable().optional(),
      notification_privacy_mode: z.enum(["private", "generic", "detailed"]).optional(),
    }).strict().parse(req.body);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const pat = await service.createPatient(db, {
      userId: user.id,
      displayName: body.display_name,
      preferredTimezone: body.preferred_timezone,
      dateOfBirth: body.date_of_birth ?? null,
      notificationPrivacyMode: body.notification_privacy_mode,
      actorSessionId: user.sessionId,
      requestId,
      ip: ip ?? null,
    });
    return reply.code(201).send(pat);
  });

  fastify.get("/patients/:patientId", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { user } = ctx(req);
    const pat = await service.getPatient(db, user.id, params.patientId);
    return reply.send(pat);
  });

  fastify.patch("/patients/:patientId", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid() }).parse(req.params);
    const body = z.object({ display_name: z.string().min(1).max(200).optional(), preferred_timezone: z.string().min(1).max(100).optional(), notification_privacy_mode: z.enum(["private", "generic", "detailed"]).optional() }).strict().parse(req.body);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const pat = await service.updatePatient(db, {
      userId: user.id,
      patientId: params.patientId,
      patch: { displayName: body.display_name, preferredTimezone: body.preferred_timezone, notificationPrivacyMode: body.notification_privacy_mode },
      actorSessionId: user.sessionId,
      requestId,
      ip: ip ?? null,
    });
    return reply.send(pat);
  });

  fastify.post("/patients/:patientId/archive", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    await service.archivePatient(db, { userId: user.id, patientId: params.patientId, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send({ ok: true });
  });

  fastify.post("/patients/:patientId/deletion-request", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { user } = ctx(req);
    // Only owners can request deletion — check via permission
    await service.requirePermissionOnPatient(db, user.id, params.patientId, "patient:delete");
    const { eq } = await import("drizzle-orm");
    const { patients } = await import("../../db/schema.js");
    await db.update(patients).set({ deletionRequestedAt: new Date(), updatedAt: new Date() }).where(eq(patients.id, params.patientId));
    return reply.send({ ok: true });
  });

  fastify.get("/patients/:patientId/memberships", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { user } = ctx(req);
    const members = await service.listMemberships(db, user.id, params.patientId);
    return reply.send({ memberships: members });
  });

  fastify.patch("/patients/:patientId/memberships/:membershipId", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid(), membershipId: z.string().uuid() }).parse(req.params);
    const body = z.object({ role: z.string() }).parse(req.body);
    assertValidRole(body.role);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    await service.updateMembership(db, { actorUserId: user.id, patientId: params.patientId, membershipId: params.membershipId, newRole: body.role as never, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.send({ ok: true });
  });

  fastify.delete("/patients/:patientId/memberships/:membershipId", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid(), membershipId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    await service.revokeMembership(db, { actorUserId: user.id, patientId: params.patientId, membershipId: params.membershipId, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(204).send();
  });

  fastify.post("/patients/:patientId/invites", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid() }).parse(req.params);
    const body = z.object({ email: z.string().email(), role: z.string() }).parse(req.body);
    assertValidRole(body.role);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const { inviteId } = await service.createInvite(db, { actorUserId: user.id, patientId: params.patientId, invitedEmail: body.email, role: body.role as never, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    // Never reveal whether email has account; return only invite id
    return reply.code(201).send({ invite_id: inviteId });
  });

  fastify.get("/patients/:patientId/invites", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { user } = ctx(req);
    const invites = await service.listInvites(db, user.id, params.patientId);
    // Sanitize: don't expose token_hash; return limited fields
    return reply.send({ invites: invites.map(i => ({ id: i.id, invited_email_normalized: i.invitedEmailNormalized, role: i.role, expires_at: i.expiresAt, accepted_at: i.acceptedAt, revoked_at: i.revokedAt, created_at: i.createdAt })) });
  });

  fastify.delete("/patients/:patientId/invites/:inviteId", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ patientId: z.string().uuid(), inviteId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    await service.revokeInvite(db, { actorUserId: user.id, patientId: params.patientId, inviteId: params.inviteId, actorSessionId: user.sessionId, requestId, ip: ip ?? null });
    return reply.code(204).send();
  });

  // Public (authenticated) invite accept by token
  fastify.post("/invites/:token/accept", async (req, reply) => {
    await auth(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ token: z.string().min(10) }).parse(req.params);
    const db = getDb();
    const { user, requestId, ip } = ctx(req);
    const res = await service.acceptInvite(db, { token: params.token, userId: user.id, requestId, ip: ip ?? null, actorSessionId: user.sessionId });
    return reply.send({ patient_id: res.patientId });
  });
}
