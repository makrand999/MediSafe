/**
 * modules/devices/routes.ts — Device and push token management endpoints
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import * as service from "./service.js";
import { PutPushTokenSchema } from "./validation.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({
    error: {
      code: "VALIDATION_ERROR",
      message: "Request validation failed.",
      request_id: (req as unknown as { id: string }).id,
      details: { validation: issues },
    },
  });
}

export async function deviceRoutes(fastify: FastifyInstance): Promise<void> {
  const getDb = () => (fastify as unknown as { db: import("../../db/index.js").MedacDb }).db;
  const auth = (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate.bind(fastify);

  // List all registered devices for the current user
  fastify.get("/me/devices", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const db = getDb();
    const userId = (req as unknown as { user: { id: string } }).user.id;
    const devices = await service.listUserDevices(db, userId);
    return reply.send({
      devices: devices.map((d) => ({
        id: d.id,
        platform: d.platform,
        display_name: d.displayName,
        app_version: d.appVersion,
        timezone: d.timezone,
        notification_permission: d.notificationPermission,
        last_seen_at: d.lastSeenAt,
        created_at: d.createdAt,
      })),
    });
  });

  // Revoke a device
  fastify.delete("/me/devices/:deviceId", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ deviceId: z.string().uuid() }).safeParse(req.params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const db = getDb();
    const userId = (req as unknown as { user: { id: string } }).user.id;
    await service.revokeDevice(db, userId, p.data.deviceId);
    return reply.code(204).send();
  });

  // Register / update FCM push token for a device
  fastify.put("/me/devices/:deviceId/push-token", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ deviceId: z.string().uuid() }).safeParse(req.params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);
    const b = PutPushTokenSchema.safeParse(req.body);
    if (!b.success) return sendValidationError(req, reply, b.error.issues);

    const db = getDb();
    const userId = (req as unknown as { user: { id: string } }).user.id;
    const token = await service.upsertPushToken(db, {
      userId,
      deviceId: p.data.deviceId,
      token: b.data.token,
      provider: b.data.provider,
    });

    return reply.send({
      id: token.id,
      device_id: token.deviceId,
      provider: token.provider,
      token_fingerprint: token.tokenFingerprint,
      created_at: token.createdAt,
      updated_at: token.updatedAt,
    });
  });

  // Delete / invalidate push token for a device
  fastify.delete("/me/devices/:deviceId/push-token", async (req, reply) => {
    await auth(req as unknown as never, reply as unknown as never);
    if (reply.sent) return;
    const p = z.object({ deviceId: z.string().uuid() }).safeParse(req.params);
    if (!p.success) return sendValidationError(req, reply, p.error.issues);

    const db = getDb();
    const userId = (req as unknown as { user: { id: string } }).user.id;
    await service.deletePushToken(db, userId, p.data.deviceId);
    return reply.code(204).send();
  });
}
