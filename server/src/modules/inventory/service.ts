/**
 * Inventory service — ledger, projections, low-stock §6.6, §8.11, §16.1
 */
import { eq, and, desc } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";

function now() { return new Date(); }

async function audit(db: MedacDb, params: { actorUserId?: string | null; actorSessionId?: string | null; patientId?: string | null; action: string; entityType: string; entityId?: string | null; requestId?: string | null; ipPrefix?: string | null; metadataJson?: Record<string, unknown> }): Promise<void> {
  await db.insert(schema.auditEvents).values({
    id: uuidv4(),
    occurredAt: now(),
    actorUserId: params.actorUserId ?? null,
    actorSessionId: params.actorSessionId ?? null,
    patientId: params.patientId ?? null,
    action: params.action,
    entityType: params.entityType,
    entityId: params.entityId ?? null,
    requestId: params.requestId ?? null,
    ipPrefix: params.ipPrefix ?? null,
    metadataJson: params.metadataJson ?? {},
  });
}

async function assertMedicationBelongs(db: MedacDb, patientId: string, medicationId: string): Promise<typeof schema.patientMedications.$inferSelect> {
  const med = await db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, medicationId), eq(schema.patientMedications.patientId, patientId)) });
  if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
  return med;
}

async function getOrCreateAccount(db: MedacDb, patientId: string, medicationId: string, unit: string): Promise<typeof schema.inventoryAccounts.$inferSelect> {
  let account = await db.query.inventoryAccounts.findFirst({ where: and(eq(schema.inventoryAccounts.patientId, patientId), eq(schema.inventoryAccounts.medicationId, medicationId), eq(schema.inventoryAccounts.unit, unit)) });
  if (!account) {
    const id = uuidv4();
    await db.insert(schema.inventoryAccounts).values({ id, patientId, medicationId, unit });
    account = await db.query.inventoryAccounts.findFirst({ where: eq(schema.inventoryAccounts.id, id) });
    if (!account) throw new Error("Failed to create inventory account");
  }
  return account;
}

export async function getInventory(db: MedacDb, userId: string, patientId: string, medicationId: string): Promise<{ account: typeof schema.inventoryAccounts.$inferSelect | null; balance: string; transactions: Array<typeof schema.inventoryTransactions.$inferSelect>; lowStock: boolean; forecastDays?: number | null }> {
  await requirePermissionOnPatient(db, userId, patientId, "inventory:read");
  await assertMedicationBelongs(db, patientId, medicationId);
  const accounts = await db.query.inventoryAccounts.findMany({ where: and(eq(schema.inventoryAccounts.patientId, patientId), eq(schema.inventoryAccounts.medicationId, medicationId)) });
  if (accounts.length === 0) return { account: null, balance: "0", transactions: [], lowStock: false, forecastDays: null };
  // For MVP, use first account (single unit per medication) — ledger sum
  const account = accounts[0];
  const txs = await db.query.inventoryTransactions.findMany({ where: eq(schema.inventoryTransactions.inventoryAccountId, account.id), orderBy: [desc(schema.inventoryTransactions.effectiveAt)] });
  const balance = txs.reduce((sum, tx) => sum + Number(tx.quantityDelta), 0);
  const lowStock = account.lowStockThresholdValue !== null && account.lowStockThresholdValue !== undefined ? balance <= Number(account.lowStockThresholdValue) : false;
  // Forecast: if we have a fixed_times schedule, estimate daily consumption
  let forecastDays: number | null = null;
  if (balance > 0) {
    const schedules = await db.query.medicationScheduleVersions.findMany({ where: eq(schema.medicationScheduleVersions.medicationId, medicationId) });
    const active = schedules.find(s => !s.effectiveUntil);
    if (active && active.scheduleType === "fixed_times") {
      const fts = await db.query.scheduleFixedTimes.findMany({ where: eq(schema.scheduleFixedTimes.scheduleVersionId, active.id) });
      const daily = fts.length || 1;
      const perDay = daily * 1; // assume 1 unit per occurrence
      forecastDays = perDay > 0 ? Math.floor(balance / perDay) : null;
    }
  }
  return { account, balance: String(balance), transactions: txs.slice(0, 20), lowStock, forecastDays };
}

export async function createTransaction(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; data: { transaction_type: string; quantity_delta: string; unit: string; reason_text?: string; effective_at?: string }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.inventoryTransactions.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "inventory:write");
  await assertMedicationBelongs(db, input.patientId, input.medicationId);
  const account = await getOrCreateAccount(db, input.patientId, input.medicationId, input.data.unit);
  // Ensure unit consistency: cannot mix units in one account (already per unit), but check that dose units match
  const id = uuidv4();
  const effectiveAt = input.data.effective_at ? new Date(input.data.effective_at) : now();
  await db.insert(schema.inventoryTransactions).values({
    id,
    inventoryAccountId: account.id,
    transactionType: input.data.transaction_type as never,
    quantityDelta: input.data.quantity_delta as never,
    reasonText: input.data.reason_text ?? null,
    actorUserId: input.userId,
    effectiveAt,
  });
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "inventory.transaction_create", entityType: "inventory_transaction", entityId: id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { transaction_type: input.data.transaction_type } });
  const tx = await db.query.inventoryTransactions.findFirst({ where: eq(schema.inventoryTransactions.id, id) });
  return tx!;
}

export async function listTransactions(db: MedacDb, userId: string, patientId: string, medicationId: string, query: { limit?: number; cursor?: string }): Promise<{ transactions: Array<typeof schema.inventoryTransactions.$inferSelect>; nextCursor?: string }> {
  await requirePermissionOnPatient(db, userId, patientId, "inventory:read");
  await assertMedicationBelongs(db, patientId, medicationId);
  const account = await db.query.inventoryAccounts.findFirst({ where: and(eq(schema.inventoryAccounts.patientId, patientId), eq(schema.inventoryAccounts.medicationId, medicationId)) });
  if (!account) return { transactions: [] };
  const limit = Math.min(query.limit ?? 50, 100);
  let cursorId: string | undefined;
  if (query.cursor) try { cursorId = Buffer.from(query.cursor, "base64url").toString("utf8"); } catch {}
  let txs = await db.query.inventoryTransactions.findMany({ where: eq(schema.inventoryTransactions.inventoryAccountId, account.id), orderBy: [desc(schema.inventoryTransactions.effectiveAt)], limit: limit + 1 });
  if (cursorId) {
    const idx = txs.findIndex(t => t.id === cursorId);
    if (idx >= 0) txs = txs.slice(idx + 1);
  }
  const hasMore = txs.length > limit;
  const page = hasMore ? txs.slice(0, limit) : txs;
  const nextCursor = hasMore ? Buffer.from(page[page.length - 1].id).toString("base64url") : undefined;
  return { transactions: page, nextCursor };
}

export async function updateSettings(db: MedacDb, input: { userId: string; patientId: string; medicationId: string; data: { low_stock_threshold_value?: string | null; unit?: string }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.inventoryAccounts.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "inventory:write");
  await assertMedicationBelongs(db, input.patientId, input.medicationId);
  const unit = input.data.unit ?? "tablet";
  const account = await getOrCreateAccount(db, input.patientId, input.medicationId, unit);
  const updates: Record<string, unknown> = { updatedAt: now() };
  if (input.data.low_stock_threshold_value !== undefined) updates.lowStockThresholdValue = input.data.low_stock_threshold_value;
  await db.update(schema.inventoryAccounts).set(updates as never).where(eq(schema.inventoryAccounts.id, account.id));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "inventory.settings_update", entityType: "inventory_account", entityId: account.id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const updated = await db.query.inventoryAccounts.findFirst({ where: eq(schema.inventoryAccounts.id, account.id) });
  return updated!;
}
