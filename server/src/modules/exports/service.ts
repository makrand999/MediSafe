/**
 * Exports service — Phase 10 §8.13, §17.1 async CSV
 * Generates separate CSVs for patient, meds, schedules, dose events, inventory, symptoms (if selected)
 * Protects against CSV formula injection by prefixing dangerous cells.
 */
import { eq, and, inArray } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import { mkdir, writeFile, readFile, rm } from "node:fs/promises";
import { join } from "node:path";
import { createHmac, randomBytes } from "node:crypto";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { coarsenIp } from "../auth/tokens.js";
import { getConfig } from "../../config/index.js";

function now() { return new Date(); }
async function audit(db: MedacDb, params: { actorUserId?: string | null; actorSessionId?: string | null; patientId?: string | null; action: string; entityType: string; entityId?: string | null; requestId?: string | null; ipPrefix?: string | null; metadataJson?: Record<string, unknown> }): Promise<void> {
  await db.insert(schema.auditEvents).values({ id: uuidv4(), occurredAt: now(), actorUserId: params.actorUserId ?? null, actorSessionId: params.actorSessionId ?? null, patientId: params.patientId ?? null, action: params.action, entityType: params.entityType, entityId: params.entityId ?? null, requestId: params.requestId ?? null, ipPrefix: params.ipPrefix ?? null, metadataJson: params.metadataJson ?? {} });
}

// In-memory store for export metadata (would be table in production, but use job_runs for now)
interface ExportRecord {
  id: string;
  patientId: string;
  userId: string;
  status: "pending" | "processing" | "completed" | "failed" | "expired";
  createdAt: Date;
  expiresAt: Date;
  filePath?: string;
  downloadToken?: string;
  downloadTokenExpiresAt?: Date;
}

const exportsStore = new Map<string, ExportRecord>();

function escapeCsvValue(value: string | null | undefined): string {
  if (value === null || value === undefined) return "";
  let s = String(value);
  // Protect against CSV formula injection per §17.1: prefix dangerous cells beginning with =,+,-,@ with '
  if (/^[=+\-@]/.test(s)) s = `'${s}`;
  // Escape quotes and wrap if needed
  if (s.includes(",") || s.includes('"') || s.includes("\n")) {
    s = `"${s.replace(/"/g, '""')}"`;
  }
  return s;
}

function toCsv(rows: Array<Record<string, unknown>>, headers: string[]): string {
  const headerLine = headers.map(h => escapeCsvValue(h)).join(",");
  const lines = rows.map(row => headers.map(h => escapeCsvValue(row[h] as string)).join(","));
  return [headerLine, ...lines].join("\n");
}

export async function createExport(db: MedacDb, input: { userId: string; patientId: string; from?: string; to?: string; includeSymptoms?: boolean; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<ExportRecord> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "export:create");
  const cfg = getConfig();
  const id = uuidv4();
  const createdAt = now();
  const expiresAt = new Date(createdAt.getTime() + cfg.exportTtlHours * 60 * 60 * 1000);
  const record: ExportRecord = { id, patientId: input.patientId, userId: input.userId, status: "pending", createdAt, expiresAt };
  exportsStore.set(id, record);
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "export.create", entityType: "export", entityId: id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });

  // Generate asynchronously (for MVP, do inline but mark as processing then completed)
  record.status = "processing";
  try {
    const dir = cfg.exportDirectory;
    await mkdir(dir, { recursive: true });
    const exportDir = join(dir, id);
    await mkdir(exportDir, { recursive: true });

    // Fetch data
    const patient = await db.query.patients.findFirst({ where: eq(schema.patients.id, input.patientId) });
    const meds = await db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, input.patientId) });
    const medIds = meds.map(m => m.id);
    const schedules = medIds.length > 0 ? await db.query.medicationScheduleVersions.findMany({ where: inArray(schema.medicationScheduleVersions.medicationId, medIds) }) : [];
    const doseEvents = await db.query.doseEventLogs.findMany({ where: eq(schema.doseEventLogs.patientId, input.patientId) });
    const inventory = await db.query.inventoryAccounts.findMany({ where: eq(schema.inventoryAccounts.patientId, input.patientId) });
    const symptoms = input.includeSymptoms ? await db.query.symptomLogs.findMany({ where: eq(schema.symptomLogs.patientId, input.patientId) }) : [];

    // Filter by date range if provided
    let filteredDoseEvents = doseEvents;
    if (input.from) {
      const from = new Date(input.from);
      filteredDoseEvents = filteredDoseEvents.filter(e => new Date(e.actualAt) >= from);
    }
    if (input.to) {
      const to = new Date(input.to);
      filteredDoseEvents = filteredDoseEvents.filter(e => new Date(e.actualAt) < to);
    }

    // Generate CSVs
    const patientCsv = toCsv(patient ? [{ id: patient.id, display_name: patient.displayName, preferred_timezone: patient.preferredTimezone }] : [], ["id", "display_name", "preferred_timezone"]);
    await writeFile(join(exportDir, "patient.csv"), patientCsv, "utf8");

    const medsCsv = toCsv(meds.map(m => ({ id: m.id, entered_name: m.enteredName, status: m.status, dose: `${m.doseQuantityValue} ${m.doseQuantityUnit}` })), ["id", "entered_name", "status", "dose"]);
    await writeFile(join(exportDir, "medications.csv"), medsCsv, "utf8");

    const schedulesCsv = toCsv(schedules.map(s => ({ id: s.id, medication_id: s.medicationId, schedule_type: s.scheduleType, timezone: s.timezone })), ["id", "medication_id", "schedule_type", "timezone"]);
    await writeFile(join(exportDir, "schedules.csv"), schedulesCsv, "utf8");

    const doseCsv = toCsv(filteredDoseEvents.map(e => ({ id: e.id, medication_id: e.medicationId, event_type: e.eventType, actual_at: new Date(e.actualAt).toISOString() })), ["id", "medication_id", "event_type", "actual_at"]);
    await writeFile(join(exportDir, "dose_events.csv"), doseCsv, "utf8");

    const invCsv = toCsv(inventory.map(a => ({ id: a.id, medication_id: a.medicationId, unit: a.unit })), ["id", "medication_id", "unit"]);
    await writeFile(join(exportDir, "inventory.csv"), invCsv, "utf8");

    if (symptoms.length > 0) {
      const symCsv = toCsv(symptoms.map(s => ({ id: s.id, occurred_at: new Date(s.occurredAt).toISOString(), note: s.noteCiphertext ? Buffer.from(s.noteCiphertext, "base64").toString("utf8") : "" })), ["id", "occurred_at", "note"]);
      await writeFile(join(exportDir, "symptoms.csv"), symCsv, "utf8");
    }

    // Create a simple zip by concatenating? For MVP, just keep dir and create a token
    const token = randomBytes(32).toString("hex");
    record.filePath = exportDir;
    record.downloadToken = createHmac("sha256", cfg.tokenHashSecret).update(token).digest("hex");
    // Store raw token hash for verification, but also need to store raw for download? For MVP, store raw in record (not in DB)
    (record as unknown as { rawToken: string }).rawToken = token;
    record.downloadTokenExpiresAt = new Date(Date.now() + 60 * 60 * 1000); // 1h
    record.status = "completed";
    exportsStore.set(id, record);
  } catch (e) {
    record.status = "failed";
    exportsStore.set(id, record);
    throw e;
  }
  return record;
}

export async function getExport(db: MedacDb, userId: string, patientId: string, exportId: string): Promise<ExportRecord> {
  await requirePermissionOnPatient(db, userId, patientId, "export:read");
  const rec = exportsStore.get(exportId);
  if (!rec || rec.patientId !== patientId) throw Object.assign(new Error("Export not found"), { statusCode: 404 });
  // Check expiry
  if (rec.expiresAt.getTime() <= Date.now()) {
    rec.status = "expired";
    exportsStore.set(exportId, rec);
  }
  return rec;
}

export async function downloadExport(db: MedacDb, userId: string, patientId: string, exportId: string, token?: string): Promise<{ filePath: string; contentType: string }> {
  const rec = await getExport(db, userId, patientId, exportId);
  if (rec.status !== "completed" || !rec.filePath) throw Object.assign(new Error("Export not ready"), { statusCode: 404 });
  if (rec.expiresAt.getTime() <= Date.now()) throw Object.assign(new Error("Export expired"), { statusCode: 410 });
  // Verify token if provided (for direct download link)
  if (token) {
    const cfg = getConfig();
    const expected = createHmac("sha256", cfg.tokenHashSecret).update(token).digest("hex");
    if (expected !== rec.downloadToken) throw Object.assign(new Error("Invalid download token"), { statusCode: 403 });
    if (rec.downloadTokenExpiresAt && rec.downloadTokenExpiresAt.getTime() <= Date.now()) throw Object.assign(new Error("Download token expired"), { statusCode: 410 });
  }
  // For MVP, return dir path; caller will read files
  return { filePath: rec.filePath!, contentType: "text/csv" };
}

export async function deleteExport(db: MedacDb, userId: string, patientId: string, exportId: string): Promise<void> {
  await requirePermissionOnPatient(db, userId, patientId, "export:read");
  const rec = exportsStore.get(exportId);
  if (!rec || rec.patientId !== patientId) throw Object.assign(new Error("Export not found"), { statusCode: 404 });
  if (rec.filePath) {
    try { await rm(rec.filePath, { recursive: true, force: true }); } catch {}
  }
  exportsStore.delete(exportId);
}

export function __clearExports(): void { exportsStore.clear(); }
