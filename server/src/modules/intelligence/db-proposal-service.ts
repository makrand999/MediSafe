/**
 * DB-backed proposal service — PostgreSQL ai_action_proposals
 * Mirrors InMemoryProposalStore logic but persists to DB with encryption, hash, expiry, replay checks
 */
import { eq, and } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { encryptPayload, hashPayloadCanonical, decryptPayload } from "./proposal-service.js";
import { getConfig } from "../../config/index.js";

export interface DbProposal {
  id: string;
  aiRunId: string | null;
  patientId: string;
  proposedByUserId: string | null;
  actionType: string;
  payloadCiphertext: string;
  payloadHash: string;
  humanSummaryCiphertext: string | null;
  requiredPermission: string;
  resourceVersionsJson: Record<string, number>;
  status: string;
  expiresAt: Date;
  confirmedByUserId: string | null;
  confirmedAt: Date | null;
  executedAt: Date | null;
  createdAt: Date;
}

export class DbProposalService {
  constructor(private readonly db: MedacDb) {}

  async create(input: {
    aiRunId: string | null;
    patientId: string;
    proposedByUserId: string;
    actionType: string;
    payload: Record<string, unknown>;
    humanSummary: string;
    requiredPermission: string;
    resourceVersionsJson: Record<string, number>;
    ttlMinutes?: number;
  }): Promise<DbProposal> {
    const cfg = getConfig();
    const ttl = input.ttlMinutes ?? cfg.aiProposalTtlMinutes;
    const now = new Date();
    const expiresAt = new Date(now.getTime() + ttl * 60_000);
    const payloadJson = JSON.stringify(input.payload, Object.keys(input.payload).sort());
    const payloadHash = hashPayloadCanonical(input.payload);
    const payloadCiphertext = encryptPayload(payloadJson);
    const humanSummaryCiphertext = encryptPayload(input.humanSummary);
    const id = uuidv4();
    await this.db.insert(schema.aiActionProposals).values({
      id,
      aiRunId: input.aiRunId,
      patientId: input.patientId,
      proposedByUserId: input.proposedByUserId,
      actionType: input.actionType,
      payloadCiphertext,
      payloadHash,
      humanSummaryCiphertext,
      requiredPermission: input.requiredPermission,
      resourceVersionsJson: input.resourceVersionsJson as never,
      status: "pending" as never,
      expiresAt,
    });
    const row = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, id) });
    if (!row) throw new Error("Failed to create proposal");
    return this.toDbProposal(row);
  }

  async get(proposalId: string): Promise<DbProposal | undefined> {
    const row = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, proposalId) });
    return row ? this.toDbProposal(row) : undefined;
  }

  async confirm(input: {
    proposalId: string;
    patientId: string;
    confirmingUserId: string;
    payloadHash: string;
    expectedResourceVersions: Record<string, number>;
    hasRecentAuth: boolean;
    isAuthorized: boolean;
  }): Promise<DbProposal> {
    const row = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, input.proposalId) });
    if (!row) throw Object.assign(new Error("Proposal not found"), { code: "PROPOSAL_NOT_FOUND", statusCode: 404 });
    if (row.patientId !== input.patientId) throw Object.assign(new Error("Patient mismatch"), { code: "PATIENT_MISMATCH", statusCode: 403 });
    if (row.status !== "pending") throw Object.assign(new Error(`Proposal not pending: ${row.status}`), { code: "PROPOSAL_NOT_PENDING", statusCode: 400 });
    if (row.expiresAt.getTime() <= Date.now()) {
      await this.db.update(schema.aiActionProposals).set({ status: "expired" as never }).where(eq(schema.aiActionProposals.id, input.proposalId));
      throw Object.assign(new Error("Proposal expired"), { code: "PROPOSAL_EXPIRED", statusCode: 400 });
    }
    if (!input.isAuthorized) throw Object.assign(new Error("Not authorized"), { code: "NOT_AUTHORIZED", statusCode: 403 });
    if (row.payloadHash !== input.payloadHash) throw Object.assign(new Error("Payload hash mismatch"), { code: "HASH_MISMATCH", statusCode: 400 });
    const storedVersions = (row.resourceVersionsJson as unknown as Record<string, number>) ?? {};
    if (!this.versionsEqual(storedVersions, input.expectedResourceVersions)) {
      throw Object.assign(new Error("Resource version conflict"), { code: "VERSION_CONFLICT", statusCode: 409 });
    }
    const isHighImpact = new Set(["propose_discontinue_medication", "propose_caregiver_invite", "propose_create_or_supersede_schedule"]).has(row.actionType);
    if (isHighImpact && !input.hasRecentAuth) throw Object.assign(new Error("Recent authentication required"), { code: "RECENT_AUTH_REQUIRED", statusCode: 401 });
    // CAS pending -> confirmed
    const updated = await this.db.update(schema.aiActionProposals).set({
      status: "confirmed" as never,
      confirmedByUserId: input.confirmingUserId,
      confirmedAt: new Date(),
    }).where(and(eq(schema.aiActionProposals.id, input.proposalId), eq(schema.aiActionProposals.status, "pending" as never))).returning();
    if (updated.length === 0) throw Object.assign(new Error("Proposal already confirmed or expired"), { code: "PROPOSAL_NOT_PENDING", statusCode: 409 });
    const fresh = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, input.proposalId) });
    return this.toDbProposal(fresh!);
  }

  async execute(proposalId: string, executor: (payload: Record<string, unknown>) => Promise<void>): Promise<DbProposal> {
    const row = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, proposalId) });
    if (!row) throw Object.assign(new Error("Proposal not found"), { code: "PROPOSAL_NOT_FOUND", statusCode: 404 });
    if (row.status !== "confirmed") throw Object.assign(new Error(`Proposal not confirmed: ${row.status}`), { code: "NOT_CONFIRMED", statusCode: 400 });
    const payloadJson = decryptPayload(row.payloadCiphertext);
    const payload = JSON.parse(payloadJson) as Record<string, unknown>;
    try {
      await executor(payload);
    } catch (e) {
      await this.db.update(schema.aiActionProposals).set({ status: "failed" as never }).where(eq(schema.aiActionProposals.id, proposalId));
      throw e;
    }
    await this.db.update(schema.aiActionProposals).set({ status: "executed" as never, executedAt: new Date() }).where(eq(schema.aiActionProposals.id, proposalId));
    const fresh = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, proposalId) });
    return this.toDbProposal(fresh!);
  }

  async reject(proposalId: string, patientId: string): Promise<DbProposal> {
    const row = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, proposalId) });
    if (!row) throw Object.assign(new Error("Not found"), { code: "PROPOSAL_NOT_FOUND", statusCode: 404 });
    if (row.patientId !== patientId) throw Object.assign(new Error("Patient mismatch"), { code: "PATIENT_MISMATCH", statusCode: 403 });
    if (row.status !== "pending") throw Object.assign(new Error("Not pending"), { code: "PROPOSAL_NOT_PENDING", statusCode: 400 });
    await this.db.update(schema.aiActionProposals).set({ status: "rejected" as never }).where(eq(schema.aiActionProposals.id, proposalId));
    const fresh = await this.db.query.aiActionProposals.findFirst({ where: eq(schema.aiActionProposals.id, proposalId) });
    return this.toDbProposal(fresh!);
  }

  async expireSweep(): Promise<number> {
    const now = new Date();
    const pending = await this.db.query.aiActionProposals.findMany({ where: eq(schema.aiActionProposals.status, "pending" as never) });
    let count = 0;
    for (const p of pending) {
      if (p.expiresAt.getTime() <= now.getTime()) {
        await this.db.update(schema.aiActionProposals).set({ status: "expired" as never }).where(eq(schema.aiActionProposals.id, p.id));
        count++;
      }
    }
    return count;
  }

  decryptHumanSummary(row: DbProposal): string {
    if (!row.humanSummaryCiphertext) return "";
    return decryptPayload(row.humanSummaryCiphertext);
  }

  decryptPayload(row: DbProposal): Record<string, unknown> {
    const json = decryptPayload(row.payloadCiphertext);
    return JSON.parse(json) as Record<string, unknown>;
  }

  private toDbProposal(row: typeof schema.aiActionProposals.$inferSelect): DbProposal {
    return {
      id: row.id,
      aiRunId: row.aiRunId,
      patientId: row.patientId,
      proposedByUserId: row.proposedByUserId,
      actionType: row.actionType,
      payloadCiphertext: row.payloadCiphertext,
      payloadHash: row.payloadHash,
      humanSummaryCiphertext: row.humanSummaryCiphertext,
      requiredPermission: row.requiredPermission,
      resourceVersionsJson: (row.resourceVersionsJson as unknown as Record<string, number>) ?? {},
      status: row.status,
      expiresAt: row.expiresAt,
      confirmedByUserId: row.confirmedByUserId,
      confirmedAt: row.confirmedAt,
      executedAt: row.executedAt,
      createdAt: row.createdAt,
    };
  }

  private versionsEqual(a: Record<string, number>, b: Record<string, number>): boolean {
    const ka = Object.keys(a).sort();
    const kb = Object.keys(b).sort();
    if (ka.length !== kb.length) return false;
    for (let i = 0; i < ka.length; i++) {
      if (ka[i] !== kb[i]) return false;
      if (a[ka[i]] !== b[ka[i]]) return false;
    }
    return true;
  }
}
