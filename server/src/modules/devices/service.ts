/**
 * modules/devices/service.ts — Device and push token management
 * Implements encrypted push token storage (AES-256-GCM) with SHA-256 fingerprinting.
 */
import { createHash } from "node:crypto";
import { eq, and, isNull, desc } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { encryptSecret, decryptSecret } from "../auth/totp.js";

export function computeTokenFingerprint(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

export function encryptPushToken(token: string): string {
  return encryptSecret(Buffer.from(token, "utf-8"));
}

export function decryptPushToken(ciphertext: string): string {
  return decryptSecret(ciphertext).toString("utf-8");
}

export async function listUserDevices(db: MedacDb, userId: string): Promise<Array<typeof schema.devices.$inferSelect>> {
  return db.query.devices.findMany({
    where: and(eq(schema.devices.userId, userId), isNull(schema.devices.revokedAt)),
    orderBy: [desc(schema.devices.createdAt)],
  });
}

export async function revokeDevice(db: MedacDb, userId: string, deviceId: string): Promise<void> {
  const device = await db.query.devices.findFirst({
    where: and(eq(schema.devices.id, deviceId), eq(schema.devices.userId, userId)),
  });
  if (!device) {
    throw Object.assign(new Error("Device not found"), { statusCode: 404 });
  }

  const now = new Date();
  await db.update(schema.devices).set({ revokedAt: now, updatedAt: now }).where(eq(schema.devices.id, deviceId));

  // Invalidate push tokens for revoked device
  await db.update(schema.pushTokens).set({ invalidatedAt: now, updatedAt: now }).where(eq(schema.pushTokens.deviceId, deviceId));
}

export async function upsertPushToken(
  db: MedacDb,
  input: {
    userId: string;
    deviceId: string;
    token: string;
    provider?: string;
  },
): Promise<typeof schema.pushTokens.$inferSelect> {
  const device = await db.query.devices.findFirst({
    where: and(eq(schema.devices.id, input.deviceId), eq(schema.devices.userId, input.userId)),
  });
  if (!device) {
    throw Object.assign(new Error("Device not found"), { statusCode: 404 });
  }
  if (device.revokedAt) {
    throw Object.assign(new Error("Device is revoked"), { statusCode: 400 });
  }

  const tokenCiphertext = encryptPushToken(input.token);
  const tokenFingerprint = computeTokenFingerprint(input.token);
  const provider = input.provider ?? "fcm";
  const now = new Date();

  const existing = await db.query.pushTokens.findFirst({
    where: eq(schema.pushTokens.deviceId, input.deviceId),
  });

  if (existing) {
    await db
      .update(schema.pushTokens)
      .set({
        provider,
        tokenCiphertext,
        tokenFingerprint,
        invalidatedAt: null,
        updatedAt: now,
      })
      .where(eq(schema.pushTokens.id, existing.id));

    const updated = await db.query.pushTokens.findFirst({ where: eq(schema.pushTokens.id, existing.id) });
    return updated!;
  }

  const newId = uuidv4();
  await db.insert(schema.pushTokens).values({
    id: newId,
    deviceId: input.deviceId,
    provider,
    tokenCiphertext,
    tokenFingerprint,
    createdAt: now,
  });

  const created = await db.query.pushTokens.findFirst({ where: eq(schema.pushTokens.id, newId) });
  return created!;
}

export async function deletePushToken(db: MedacDb, userId: string, deviceId: string): Promise<void> {
  const device = await db.query.devices.findFirst({
    where: and(eq(schema.devices.id, deviceId), eq(schema.devices.userId, userId)),
  });
  if (!device) {
    throw Object.assign(new Error("Device not found"), { statusCode: 404 });
  }

  const now = new Date();
  await db.update(schema.pushTokens).set({ invalidatedAt: now, updatedAt: now }).where(eq(schema.pushTokens.deviceId, deviceId));
}

export interface DecryptedPushToken {
  deviceId: string;
  token: string;
  provider: string;
}

export async function getValidPushTokensForUser(db: MedacDb, userId: string): Promise<DecryptedPushToken[]> {
  const userDevices = await db.query.devices.findMany({
    where: and(eq(schema.devices.userId, userId), isNull(schema.devices.revokedAt)),
  });

  const results: DecryptedPushToken[] = [];
  for (const dev of userDevices) {
    const pt = await db.query.pushTokens.findFirst({
      where: and(eq(schema.pushTokens.deviceId, dev.id), isNull(schema.pushTokens.invalidatedAt)),
    });
    if (pt) {
      try {
        const decrypted = decryptPushToken(pt.tokenCiphertext);
        results.push({
          deviceId: dev.id,
          token: decrypted,
          provider: pt.provider,
        });
      } catch {
        // Corrupted or unable to decrypt
      }
    }
  }
  return results;
}
