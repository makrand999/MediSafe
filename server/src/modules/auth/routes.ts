/**
 * modules/auth/routes.ts — Auth endpoints §8.4
 */
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import * as service from "./service.js";

export async function authRoutes(fastify: FastifyInstance): Promise<void> {
  // helper to get db and requestId/ip/ua
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const ctx = (req: import("fastify").FastifyRequest) => ({
    requestId: (req as unknown as { id: string }).id,
    ip: (req as unknown as { ip: string }).ip,
    userAgent: req.headers["user-agent"] as string | undefined,
  });

  fastify.post("/auth/register", async (req, reply) => {
    const body = z.object({ email: z.string().email().max(320), password: z.string().min(12).max(128), preferred_locale: z.string().min(2).max(20).optional() }).strict().parse(req.body);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    const res = await service.register(db, { email: body.email, password: body.password, preferredLocale: body.preferred_locale, requestId, ip });
    return reply.code(201).send({ user_id: res.userId });
  });

  fastify.post("/auth/verify-email", async (req, reply) => {
    const body = z.object({ token: z.string().min(10) }).parse(req.body);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    await service.verifyEmail(db, { token: body.token, requestId, ip });
    return reply.send({ ok: true });
  });

  fastify.post("/auth/resend-verification", async (req, reply) => {
    const body = z.object({ email: z.string().email() }).parse(req.body);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    await service.resendVerification(db, { email: body.email, requestId, ip });
    // never reveal existence
    return reply.send({ ok: true });
  });

  fastify.post("/auth/login", async (req, reply) => {
    const body = z.object({
      email: z.string().email().max(320),
      password: z.string().min(1).max(128),
      device: z.object({
        platform: z.enum(["ios", "android", "web", "unknown"]).optional(),
        display_name: z.string().max(100).optional(),
        timezone: z.string().max(100).optional(),
      }).strict().optional(),
    }).strict().parse(req.body);
    const db = getDb();
    const { requestId, ip, userAgent } = ctx(req);
    const res = await service.login(db, {
      email: body.email,
      password: body.password,
      deviceInfo: body.device ? { platform: body.device.platform, displayName: body.device.display_name, timezone: body.device.timezone } : undefined,
      ip, userAgent, requestId,
    });
    if (res.needsMfa) return reply.send({ mfa_required: true, mfa_session_token: res.mfaSessionToken });
    return reply.send({ access_token: res.accessToken, refresh_token: res.refreshToken, session_id: res.sessionId });
  });

  fastify.post("/auth/mfa/verify", async (req, reply) => {
    const body = z.object({ mfa_session_token: z.string(), code: z.string().min(3), use_recovery_code: z.boolean().optional() }).parse(req.body);
    const db = getDb();
    const { requestId, ip, userAgent } = ctx(req);
    const res = await service.verifyMfa(db, { mfaSessionToken: body.mfa_session_token, code: body.code, recoveryCode: body.use_recovery_code, ip, requestId, userAgent });
    return reply.send({ access_token: res.accessToken, refresh_token: res.refreshToken, session_id: res.sessionId });
  });

  fastify.post("/auth/refresh", async (req, reply) => {
    const body = z.object({ refresh_token: z.string().min(10) }).parse(req.body);
    const db = getDb();
    const { requestId, ip, userAgent } = ctx(req);
    const res = await service.refresh(db, { refreshToken: body.refresh_token, ip, userAgent, requestId });
    return reply.send({ access_token: res.accessToken, refresh_token: res.refreshToken, session_id: res.sessionId });
  });

  fastify.post("/auth/logout", async (req, reply) => {
    const body = z.object({ refresh_token: z.string().optional() }).parse(req.body ?? {});
    const header = req.headers.authorization;
    let sessionId: string | undefined;
    let userId: string | undefined;
    if (header?.startsWith("Bearer ")) {
      try {
        const { verifyAccessToken } = await import("./tokens.js");
        const claims = await verifyAccessToken(header.slice(7));
        sessionId = claims.sid;
        userId = claims.sub;
      } catch {
        // Logout is intentionally idempotent; an invalid access token is treated
        // like an already logged-out session.
      }
    }
    const db = getDb();
    const { requestId, ip } = ctx(req);
    await service.logout(db, { refreshToken: body.refresh_token, sessionId, userId, requestId, ip });
    return reply.send({ ok: true });
  });

  fastify.post("/auth/logout-all", async (req, reply) => {
    await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const db = getDb();
    const { requestId, ip } = ctx(req);
    const userId = (req as unknown as { user: { id: string } }).user.id;
    await service.logoutAll(db, { userId, requestId, ip });
    return reply.send({ ok: true });
  });

  fastify.post("/auth/password/forgot", async (req, reply) => {
    const body = z.object({ email: z.string().email() }).parse(req.body);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    await service.forgotPassword(db, { email: body.email, requestId, ip });
    return reply.send({ ok: true });
  });

  fastify.post("/auth/password/reset", async (req, reply) => {
    const body = z.object({ token: z.string().min(10), new_password: z.string().min(12).max(128) }).parse(req.body);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    await service.resetPassword(db, { token: body.token, newPassword: body.new_password, requestId, ip });
    return reply.send({ ok: true });
  });

  fastify.post("/auth/password/change", async (req, reply) => {
    await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const body = z.object({ current_password: z.string(), new_password: z.string().min(12).max(128) }).parse(req.body);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    const user = (req as unknown as { user: { id: string; sessionId: string } }).user;
    await service.changePassword(db, { userId: user.id, currentPassword: body.current_password, newPassword: body.new_password, sessionId: user.sessionId, requestId, ip });
    return reply.send({ ok: true });
  });

  fastify.get("/auth/sessions", async (req, reply) => {
    await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const db = getDb();
    const userId = (req as unknown as { user: { id: string } }).user.id;
    const sessions = await service.listSessions(db, userId);
    return reply.send({ sessions: sessions.map(s => ({ id: s.id, device_id: s.deviceId, created_at: s.createdAt, last_used_at: s.lastUsedAt, expires_at: s.expiresAt, revoked_at: s.revokedAt, user_agent_summary: s.userAgentSummary })) });
  });

  fastify.delete("/auth/sessions/:sessionId", async (req, reply) => {
    await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const params = z.object({ sessionId: z.string().uuid() }).parse(req.params);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    const userId = (req as unknown as { user: { id: string } }).user.id;
    await service.deleteSession(db, { userId, sessionId: params.sessionId, requestId, ip });
    return reply.code(204).send();
  });

  fastify.post("/auth/mfa/totp/setup", async (req, reply) => {
    await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const db = getDb();
    const { requestId, ip } = ctx(req);
    const userId = (req as unknown as { user: { id: string } }).user.id;
    const res = await service.totpSetup(db, { userId, requestId, ip });
    return reply.send({ secret_base32: res.secretBase32, otpauth_url: res.otpauthUrl });
  });

  fastify.post("/auth/mfa/totp/confirm", async (req, reply) => {
    await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const body = z.object({ code: z.string().length(6) }).parse(req.body);
    const db = getDb();
    const { requestId, ip } = ctx(req);
    const userId = (req as unknown as { user: { id: string } }).user.id;
    const res = await service.totpConfirm(db, { userId, code: body.code, requestId, ip });
    return reply.send({ recovery_codes: res.recoveryCodes });
  });

  fastify.delete("/auth/mfa/totp", async (req, reply) => {
    await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as import("fastify").FastifyRequest, reply as unknown as import("fastify").FastifyReply);
    if (reply.sent) return;
    const db = getDb();
    const { requestId, ip } = ctx(req);
    const userId = (req as unknown as { user: { id: string } }).user.id;
    await service.totpDelete(db, { userId, requestId, ip });
    return reply.code(204).send();
  });
}
