/**
 * Tool executor — DB-backed read tools and proposal creation
 * Implements Orchestrator ToolExecutor interface with patient-scoped, permission-checked DB queries
 */
import { eq, and, desc, inArray } from "drizzle-orm";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import type { PatientAuthorization } from "./orchestrator.js";
import type { DbProposalService } from "./db-proposal-service.js";
import { getDrugNormalizationProvider } from "../drug-normalization/provider.js";
import { generateOccurrences, type ScheduleVersionInput } from "../schedules/engine.js";

export class DbToolExecutor {
  constructor(
    private readonly db: MedacDb,
    private readonly proposalService: DbProposalService,
    private readonly aiRunId: string,
    private readonly proposedByUserId: string,
  ) {}

  private enforcePatientScope(args: Record<string, unknown>, auth: PatientAuthorization): string {
    const requested = args.patientId as string | undefined;
    if (requested && requested !== auth.patientId) {
      throw Object.assign(new Error("Patient mismatch: tool patientId does not match authorized patient"), { statusCode: 403, code: "PATIENT_MISMATCH" });
    }
    return auth.patientId;
  }

  async executeRead(name: string, args: unknown, auth: PatientAuthorization): Promise<unknown> {
    const a = args as Record<string, unknown>;
    const patientId = this.enforcePatientScope(a, auth);
    switch (name) {
      case "list_medications": {
        const meds = await this.db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, patientId), orderBy: [desc(schema.patientMedications.createdAt)], limit: 50 });
        return { items: meds.map(m => ({ id: m.id, type: "medication", name: m.enteredName, status: m.status, dose: `${m.doseQuantityValue} ${m.doseQuantityUnit}` })), count: meds.length };
      }
      case "get_medication": {
        const medicationId = a.medicationId as string;
        const med = await this.db.query.patientMedications.findFirst({ where: and(eq(schema.patientMedications.id, medicationId), eq(schema.patientMedications.patientId, patientId)) });
        if (!med) throw Object.assign(new Error("Medication not found"), { statusCode: 404 });
        return { id: med.id, type: "medication", name: med.enteredName, status: med.status, dose: `${med.doseQuantityValue} ${med.doseQuantityUnit}`, enteredName: med.enteredName, labelInstructions: med.labelInstructionsText };
      }
      case "get_today_occurrences": {
        const date = (a.date as string) ?? new Date().toISOString().slice(0, 10);
        const start = new Date(`${date}T00:00:00.000Z`);
        const end = new Date(`${date}T23:59:59.999Z`);
        const occs = await this.db.query.doseOccurrences.findMany({ where: and(eq(schema.doseOccurrences.patientId, patientId), eq(schema.doseOccurrences.state, "scheduled" as never)), limit: 20 });
        // Filter by date
        const filtered = occs.filter(o => o.scheduledAtUtc >= start && o.scheduledAtUtc <= end);
        return { date, occurrences: filtered.map(o => ({ id: o.id, medicationId: o.medicationId, scheduledAtUtc: o.scheduledAtUtc.toISOString(), state: o.state })), count: filtered.length };
      }
      case "get_schedule": {
        const medicationId = a.medicationId as string;
        const versions = await this.db.query.medicationScheduleVersions.findMany({ where: eq(schema.medicationScheduleVersions.medicationId, medicationId), orderBy: [desc(schema.medicationScheduleVersions.versionNumber)], limit: 5 });
        return { medicationId, versions: versions.map(v => ({ id: v.id, version: v.versionNumber, type: v.scheduleType, timezone: v.timezone, effectiveFrom: v.effectiveFrom.toISOString() })) };
      }
      case "preview_schedule": {
        // Use engine to preview next 7 days for a draft payload
        const medicationId = a.medicationId as string;
        const payload = a.scheduleDraft as Record<string, unknown> | undefined;
        // If payload provided, preview it directly via engine without persisting
        if (payload && payload.schedule_type) {
          const tz = (payload.timezone as string) ?? "UTC";
          const versionInput: ScheduleVersionInput = {
            id: "preview",
            medication_id: medicationId,
            version_number: 999,
            schedule_type: payload.schedule_type as never,
            timing_mode: (payload.timing_mode as never) ?? "local_clock",
            timezone: tz,
            effective_from: new Date().toISOString(),
            effective_until: null,
            miss_window_minutes: null,
            fixed_times: (payload.fixed_times as Array<{ local_time: string }>)?.map((ft: { local_time: string }) => ({ schedule_version_id: "preview", local_time: ft.local_time, dose_quantity_value: "1", dose_quantity_unit: "tablet" })) ?? [],
          };
          const from = (a.from as string) ?? new Date().toISOString();
          const to = (a.to as string) ?? new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
          const occs = generateOccurrences({ scheduleVersion: versionInput, rangeFrom: from, rangeTo: to });
          return { preview: occs.slice(0, 10), count: occs.length };
        }
        // Otherwise preview existing latest schedule
        const latest = await this.db.query.medicationScheduleVersions.findFirst({ where: eq(schema.medicationScheduleVersions.medicationId, medicationId), orderBy: [desc(schema.medicationScheduleVersions.versionNumber)] });
        if (!latest) return { preview: [], count: 0 };
        return { preview: [], count: 0, note: "no draft provided" };
      }
      case "get_adherence_report": {
        const from = new Date(a.from as string);
        const to = new Date(a.to as string);
        const occs = await this.db.query.doseOccurrences.findMany({ where: eq(schema.doseOccurrences.patientId, patientId) });
        const inRange = occs.filter(o => o.scheduledAtUtc >= from && o.scheduledAtUtc < to);
        const taken = inRange.filter(o => o.state === "taken").length;
        const missed = inRange.filter(o => o.state === "missed").length;
        const total = inRange.length;
        return { from: from.toISOString(), to: to.toISOString(), total, taken, missed, adherence: total ? (taken / total) * 100 : 0 };
      }
      case "get_inventory_summary": {
        const medicationId = a.medicationId as string | undefined;
        const accounts = await this.db.query.inventoryAccounts.findMany({ where: eq(schema.inventoryAccounts.patientId, patientId) });
        const filtered = medicationId ? accounts.filter(acc => acc.medicationId === medicationId) : accounts;
        const summaries = await Promise.all(filtered.map(async acc => {
          const txs = await this.db.query.inventoryTransactions.findMany({ where: eq(schema.inventoryTransactions.inventoryAccountId, acc.id) });
          const qty = txs.reduce((sum, tx) => sum + Number(tx.quantityDelta), 0);
          return { medicationId: acc.medicationId, unit: acc.unit, quantity: qty, threshold: acc.lowStockThresholdValue };
        }));
        return { accounts: summaries };
      }
      case "get_expiration_summary": {
        const withinDays = (a.withinDays as number) ?? 30;
        const cutoff = new Date(Date.now() + withinDays * 24 * 60 * 60 * 1000);
        const meds = await this.db.query.patientMedications.findMany({ where: eq(schema.patientMedications.patientId, patientId) });
        const medIds = meds.map(m => m.id);
        const exps = medIds.length > 0 ? await this.db.query.medicationExpirations.findMany({ where: inArray(schema.medicationExpirations.medicationId, medIds) }) : [];
        // Simplified: just return count
        return { withinDays, cutoff: cutoff.toISOString(), count: exps.length, expirations: exps.slice(0, 5) };
      }
      case "get_recent_dose_events": {
        const medicationId = a.medicationId as string | undefined;
        const limit = (a.limit as number) ?? 10;
        const where = medicationId ? and(eq(schema.doseEventLogs.patientId, patientId), eq(schema.doseEventLogs.medicationId, medicationId)) : eq(schema.doseEventLogs.patientId, patientId);
        const events = await this.db.query.doseEventLogs.findMany({ where, orderBy: [desc(schema.doseEventLogs.actualAt)], limit });
        return { events: events.map(e => ({ id: e.id, medicationId: e.medicationId, eventType: e.eventType, actualAt: e.actualAt.toISOString() })), count: events.length };
      }
      case "get_injection_site_history": {
        const medicationId = a.medicationId as string | undefined;
        const limit = (a.limit as number) ?? 10;
        const where = medicationId ? and(eq(schema.injectionSiteLogs.patientId, patientId), eq(schema.injectionSiteLogs.medicationId, medicationId)) : eq(schema.injectionSiteLogs.patientId, patientId);
        const logs = await this.db.query.injectionSiteLogs.findMany({ where, orderBy: [desc(schema.injectionSiteLogs.occurredAt)], limit });
        return { logs: logs.map(l => ({ id: l.id, siteCode: l.siteCode, occurredAt: l.occurredAt.toISOString() })), count: logs.length };
      }
      case "search_rxnorm": {
        const query = a.query as string;
        const provider = getDrugNormalizationProvider();
        const res = await provider.searchByName(query);
        return res;
      }
      case "resolve_ndc": {
        const ndc = a.ndc as string;
        const provider = getDrugNormalizationProvider();
        const res = await provider.resolveNdc(ndc);
        return res;
      }
      default:
        throw new Error(`Unknown read tool ${name}`);
    }
  }

  async createProposal(name: string, args: unknown, auth: PatientAuthorization, aiRunId: string): Promise<{ proposalId: string; expiresAt: string }> {
    const a = args as Record<string, unknown>;
    // Enforce patient scope
    const requestedPid = a.patientId as string | undefined;
    if (requestedPid && requestedPid !== auth.patientId) {
      throw Object.assign(new Error("Patient mismatch: proposal patientId does not match authorized patient"), { statusCode: 403, code: "PATIENT_MISMATCH" });
    }
    const patientId = auth.patientId;
    // Map tool name to required permission and action type
    const permissionMap: Record<string, string> = {
      propose_create_medication: "medication:create",
      propose_update_medication: "medication:update",
      propose_activate_medication: "medication:activate",
      propose_pause_or_resume_medication: "medication:pause",
      propose_discontinue_medication: "medication:discontinue",
      propose_create_or_supersede_schedule: "schedule:create",
      propose_log_dose_event: "doseEvent:create",
      propose_correct_dose_event: "doseEvent:correct",
      propose_inventory_adjustment: "inventory:write",
      propose_alert_preferences: "alertPreference:write",
      propose_caregiver_invite: "invite:create",
      propose_export: "export:create",
    };
    const requiredPermission = permissionMap[name] ?? "medication:read";
    // For resource versions, fetch current versions
    const resourceVersions: Record<string, number> = {};
    if (a.medicationId) {
      const med = await this.db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, a.medicationId as string) });
      if (med) resourceVersions.medication = Number(med.updatedAt?.getTime() ?? 0);
    }
    // Human summary
    const humanSummary = `Proposed ${name} for patient ${patientId}: ${JSON.stringify(a).slice(0, 500)}`;
    const proposal = await this.proposalService.create({
      aiRunId,
      patientId,
      proposedByUserId: this.proposedByUserId,
      actionType: name,
      payload: a as Record<string, unknown>,
      humanSummary,
      requiredPermission,
      resourceVersionsJson: resourceVersions,
    });
    return { proposalId: proposal.id, expiresAt: proposal.expiresAt.toISOString() };
  }
}
