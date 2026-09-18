/**
 * Alerts service — Phase 9 §6.8, §16 deterministic alerts
 * MVP: low_stock, expiration, stale_device, missed_user_attention_med (user-flagged)
 * Delivery is generic, no PHI in payload.
 */
import { eq, and, desc, sql } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";

function now() { return new Date(); }
async function audit(db: MedacDb, params: { actorUserId?: string | null; actorSessionId?: string | null; patientId?: string | null; action: string; entityType: string; entityId?: string | null; requestId?: string | null; ipPrefix?: string | null; metadataJson?: Record<string, unknown> }): Promise<void> {
  await db.insert(schema.auditEvents).values({ id: uuidv4(), occurredAt: now(), actorUserId: params.actorUserId ?? null, actorSessionId: params.actorSessionId ?? null, patientId: params.patientId ?? null, action: params.action, entityType: params.entityType, entityId: params.entityId ?? null, requestId: params.requestId ?? null, ipPrefix: params.ipPrefix ?? null, metadataJson: params.metadataJson ?? {} });
}

// ── Preferences ───────────────────────────────────────────────────────────
export async function getPreferences(db: MedacDb, userId: string, patientId: string): Promise<Array<typeof schema.alertPreferences.$inferSelect>> {
  await requirePermissionOnPatient(db, userId, patientId, "alertPreference:read");
  return db.query.alertPreferences.findMany({ where: and(eq(schema.alertPreferences.patientId, patientId), eq(schema.alertPreferences.userId, userId)) });
}

export async function putPreferences(db: MedacDb, input: { userId: string; patientId: string; preferences: Array<{ alert_type: string; enabled: boolean; delay_minutes?: number; quiet_hours_start?: string | null; quiet_hours_end?: string | null; timezone?: string | null }>; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<Array<typeof schema.alertPreferences.$inferSelect>> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "alertPreference:write");
  for (const pref of input.preferences) {
    const existing = await db.query.alertPreferences.findFirst({ where: and(eq(schema.alertPreferences.patientId, input.patientId), eq(schema.alertPreferences.userId, input.userId), eq(schema.alertPreferences.alertType, pref.alert_type)) });
    if (existing) {
      await db.update(schema.alertPreferences).set({
        enabled: pref.enabled,
        delayMinutes: pref.delay_minutes ?? null,
        quietHoursStart: pref.quiet_hours_start ?? null,
        quietHoursEnd: pref.quiet_hours_end ?? null,
        timezone: pref.timezone ?? null,
        updatedAt: now(),
      }).where(eq(schema.alertPreferences.id, existing.id));
    } else {
      await db.insert(schema.alertPreferences).values({
        id: uuidv4(),
        patientId: input.patientId,
        userId: input.userId,
        alertType: pref.alert_type,
        enabled: pref.enabled,
        delayMinutes: pref.delay_minutes ?? null,
        quietHoursStart: pref.quiet_hours_start ?? null,
        quietHoursEnd: pref.quiet_hours_end ?? null,
        timezone: pref.timezone ?? null,
      });
    }
  }
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "alert_preferences.update", entityType: "alert_preferences", requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  return getPreferences(db, input.userId, input.patientId);
}

// ── Alerts ─────────────────────────────────────────────────────────────────
export async function listAlerts(db: MedacDb, userId: string, patientId: string, query: { status?: string; limit?: number }): Promise<Array<typeof schema.alerts.$inferSelect>> {
  await requirePermissionOnPatient(db, userId, patientId, "alert:read");
  const limit = Math.min(query.limit ?? 50, 100);
  let where = eq(schema.alerts.patientId, patientId);
  if (query.status) where = and(where, eq(schema.alerts.status, query.status as never)) as never;
  return db.query.alerts.findMany({ where, orderBy: [desc(schema.alerts.createdAt)], limit });
}

export async function acknowledgeAlert(db: MedacDb, input: { userId: string; patientId: string; alertId: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.alerts.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "alert:acknowledge");
  const alert = await db.query.alerts.findFirst({ where: and(eq(schema.alerts.id, input.alertId), eq(schema.alerts.patientId, input.patientId)) });
  if (!alert) throw Object.assign(new Error("Alert not found"), { statusCode: 404 });
  if (alert.status !== "open") throw Object.assign(new Error("Alert not open"), { statusCode: 400 });
  await db.update(schema.alerts).set({ status: "acknowledged" as never, acknowledgedAt: now(), updatedAt: now() }).where(eq(schema.alerts.id, input.alertId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "alert.acknowledge", entityType: "alert", entityId: input.alertId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const updated = await db.query.alerts.findFirst({ where: eq(schema.alerts.id, input.alertId) });
  return updated!;
}

// ── Deterministic alert generation (called by job or manual trigger) ─────────
export async function generateLowStockAlerts(db: MedacDb, patientId: string): Promise<number> {
  const meds = await db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, patientId) });
  let created = 0;
  for (const med of meds) {
    const accounts = await db.query.inventoryAccounts.findMany({ where: and(eq(schema.inventoryAccounts.patientId, patientId), eq(schema.inventoryAccounts.medicationId, med.id)) });
    for (const acc of accounts) {
      if (acc.lowStockThresholdValue === null) continue;
      const txs = await db.query.inventoryTransactions.findMany({ where: eq(schema.inventoryTransactions.inventoryAccountId, acc.id) });
      const balance = txs.reduce((sum, tx) => sum + Number(tx.quantityDelta), 0);
      if (balance <= Number(acc.lowStockThresholdValue)) {
        const existing = await db.query.alerts.findFirst({ where: and(eq(schema.alerts.patientId, patientId), eq(schema.alerts.alertType, "low_stock"), eq(schema.alerts.sourceEntityId, acc.id), eq(schema.alerts.status, "open" as never)) });
        if (existing) continue;
        const alertId = uuidv4();
        await db.insert(schema.alerts).values({
          id: alertId,
          patientId,
          alertType: "low_stock",
          severity: "attention" as never,
          status: "open" as never,
          sourceEntityType: "inventory_account",
          sourceEntityId: acc.id,
          messageKey: "alert.low_stock",
        });
        // Generic delivery (no PHI)
        const members = await db.query.patientMemberships.findMany({ where: and(eq(schema.patientMemberships.patientId, patientId), eq(schema.patientMemberships.status, "active" as never)) });
        for (const m of members) {
          const pref = await db.query.alertPreferences.findFirst({ where: and(eq(schema.alertPreferences.patientId, patientId), eq(schema.alertPreferences.userId, m.userId), eq(schema.alertPreferences.alertType, "low_stock")) });
          if (pref && !pref.enabled) continue;
          await db.insert(schema.notificationDeliveries).values({
            id: uuidv4(),
            alertId,
            userId: m.userId,
            channel: "push",
            status: "queued" as never,
            attemptCount: 0,
          });
        }
        created++;
      }
    }
  }
  return created;
}

export async function generateExpirationAlerts(db: MedacDb, patientId: string): Promise<number> {
  const meds = await db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, patientId) });
  let created = 0;
  const nowDate = new Date();
  const threshold = new Date(nowDate.getTime() + 30 * 24 * 60 * 60 * 1000);
  for (const med of meds) {
    const exps = await db.query.medicationExpirations.findMany({ where: eq(schema.medicationExpirations.medicationId, med.id) });
    for (const exp of exps) {
      const expDate = new Date(exp.expirationDate);
      if (expDate <= threshold && expDate >= nowDate) {
        const existing = await db.query.alerts.findFirst({ where: and(eq(schema.alerts.patientId, patientId), eq(schema.alerts.alertType, "expiration"), eq(schema.alerts.sourceEntityId, exp.id), eq(schema.alerts.status, "open" as never)) });
        if (existing) continue;
        const alertId = uuidv4();
        await db.insert(schema.alerts).values({
          id: alertId,
          patientId,
          alertType: "expiration",
          severity: expDate <= new Date(nowDate.getTime() + 7 * 24 * 60 * 60 * 1000) ? "attention" as never : "info" as never,
          status: "open" as never,
          sourceEntityType: "medication_expiration",
          sourceEntityId: exp.id,
          messageKey: "alert.expiration_approaching",
        });
        created++;
      }
    }
  }
  return created;
}

export async function generateStaleDeviceAlerts(db: MedacDb, patientId: string): Promise<number> {
  // Find devices that haven't synced in 7 days or report notifications disabled
  const members = await db.query.patientMemberships.findMany({ where: eq(schema.patientMemberships.patientId, patientId) });
  let created = 0;
  for (const m of members) {
    const devices = await db.query.devices.findMany({ where: eq(schema.devices.userId, m.userId) });
    for (const dev of devices) {
      const stale = !dev.lastSeenAt || (Date.now() - new Date(dev.lastSeenAt).getTime() > 7 * 24 * 60 * 60 * 1000);
      const noPerm = dev.notificationPermission === "denied";
      if (stale || noPerm) {
        const existing = await db.query.alerts.findFirst({ where: and(eq(schema.alerts.patientId, patientId), eq(schema.alerts.alertType, "stale_device"), eq(schema.alerts.sourceEntityId, dev.id as never), eq(schema.alerts.status, "open" as never)) });
        if (existing) continue;
        const alertId = uuidv4();
        await db.insert(schema.alerts).values({
          id: alertId,
          patientId,
          alertType: "stale_device",
          severity: "info" as never,
          status: "open" as never,
          sourceEntityType: "device",
          sourceEntityId: dev.id,
          messageKey: "alert.stale_device",
        });
        created++;
      }
    }
  }
  return created;
}

export async function generateMissedHighAttentionAlerts(db: MedacDb, patientId: string): Promise<number> {
  // For meds with high_attention_user_flag true, if occurrence missed (past miss_window) and no taken event
  const meds = await db.query.patientMedications.findMany({ where: and(eq(schema.patientMedications.patientId, patientId), eq(schema.patientMedications.highAttentionUserFlag, true)) });
  let created = 0;
  for (const med of meds) {
    const schedules = await db.query.medicationScheduleVersions.findMany({ where: eq(schema.medicationScheduleVersions.medicationId, med.id) });
    for (const sched of schedules) {
      if (sched.scheduleType === "prn") continue;
      const occs = await db.query.doseOccurrences.findMany({ where: and(eq(schema.doseOccurrences.scheduleVersionId, sched.id), eq(schema.doseOccurrences.state, "missed" as never)) });
      for (const occ of occs) {
        const existing = await db.query.alerts.findFirst({ where: and(eq(schema.alerts.patientId, patientId), eq(schema.alerts.alertType, "missed_user_attention_med"), eq(schema.alerts.sourceEntityId, occ.id), eq(schema.alerts.status, "open" as never)) });
        if (existing) continue;
        const alertId = uuidv4();
        await db.insert(schema.alerts).values({
          id: alertId,
          patientId,
          alertType: "missed_user_attention_med",
          severity: "attention" as never,
          status: "open" as never,
          sourceEntityType: "dose_occurrence",
          sourceEntityId: occ.id,
          messageKey: "alert.missed_high_attention",
        });
        created++;
      }
    }
  }
  return created;
}
