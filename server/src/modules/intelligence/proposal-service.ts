/**
 * proposal-service.ts
 * Encrypted payloadCiphertext, payloadHash, expiry AI_PROPOSAL_TTL_MINUTES,
 * resourceVersionsJson optimistic concurrency, status lifecycle, exactly-once,
 * confirmation requires auth+hash+versions+unexpired, transactional.
 * Ref: SERVER_IMPLEMENTATION_PLAN.md §6.10 ai_action_proposals, §8.7 confirmation
 */

import { createHash, createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ProposalStatus = "pending" | "confirmed" | "executed" | "rejected" | "expired" | "superseded" | "failed";

export interface Proposal {
  id: string;
  aiRunId: string;
  patientId: string;
  proposedByUserId: string;
  actionType: string;
  payloadCiphertext: string; // base64(iv + ciphertext)
  payloadHash: string; // hex sha256 of canonical JSON
  humanSummaryCiphertext: string;
  requiredPermission: string;
  resourceVersionsJson: Record<string, number>; // e.g. { medication:3, schedule:2 }
  status: ProposalStatus;
  expiresAt: string; // ISO
  confirmedByUserId?: string;
  confirmedAt?: string;
  executedAt?: string;
  createdAt: string;
}

export interface CreateProposalInput {
  id?: string;
  aiRunId: string;
  patientId: string;
  proposedByUserId: string;
  actionType: string;
  payload: Record<string, unknown>;
  humanSummary: string;
  requiredPermission: string;
  resourceVersionsJson: Record<string, number>;
  ttlMinutes: number;
}

export interface ConfirmProposalInput {
  proposalId: string;
  patientId: string;
  confirmingUserId: string;
  payloadHash: string;
  expectedResourceVersions: Record<string, number>;
  hasRecentAuth: boolean;
  isAuthorized: boolean; // caller must have checked membership/permission
}

// ---------------------------------------------------------------------------
// Crypto helpers (app-level field encryption)
// ---------------------------------------------------------------------------

function getEncryptionKey(): Buffer {
  // In production this comes from FIELD_ENCRYPTION_KEY_CURRENT (32 bytes hex/base64)
  // For scaffolding, derive a deterministic test key if not set, but never log it.
  const raw = process.env.FIELD_ENCRYPTION_KEY_CURRENT;
  if (raw) {
    // try hex 64 chars = 32 bytes, or base64
    if (/^[0-9a-fA-F]{64}$/.test(raw)) return Buffer.from(raw, "hex");
    const b = Buffer.from(raw, "base64");
    if (b.length === 32) return b;
    // fallback: hash to 32 bytes (not production)
    return createHash("sha256").update(raw).digest();
  }
  // Test fallback — random per process but stable for tests that use injected key
  return createHash("sha256").update("test-encryption-key-do-not-use-in-prod").digest();
}

export function encryptPayload(plaintextJson: string, key?: Buffer): string {
  const k = key ?? getEncryptionKey();
  const iv = randomBytes(12); // GCM 12-byte IV
  const cipher = createCipheriv("aes-256-gcm", k, iv);
  const enc = Buffer.concat([cipher.update(plaintextJson, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  // store iv + tag + ciphertext as base64
  return Buffer.concat([iv, tag, enc]).toString("base64");
}

export function decryptPayload(ciphertextB64: string, key?: Buffer): string {
  const k = key ?? getEncryptionKey();
  const buf = Buffer.from(ciphertextB64, "base64");
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const enc = buf.subarray(28);
  const decipher = createDecipheriv("aes-256-gcm", k, iv);
  decipher.setAuthTag(tag);
  return decipher.update(enc, undefined, "utf8") + decipher.final("utf8");
}

export function hashPayloadCanonical(payload: Record<string, unknown>): string {
  const canonical = JSON.stringify(payload, Object.keys(payload).sort());
  return createHash("sha256").update(canonical).digest("hex");
}

// ---------------------------------------------------------------------------
// In-memory store for scaffolding / tests (replace with Postgres in production)
// ---------------------------------------------------------------------------

export class InMemoryProposalStore {
  private map = new Map<string, Proposal>();

  get(id: string): Proposal | undefined {
    return this.map.get(id);
  }

  set(p: Proposal): void {
    this.map.set(p.id, p);
  }

  list(): Proposal[] {
    return [...this.map.values()];
  }

  clear(): void {
    this.map.clear();
  }
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class ProposalService {
  constructor(
    private readonly store: InMemoryProposalStore,
    private readonly defaultTtlMinutes: number = Number(process.env.AI_PROPOSAL_TTL_MINUTES ?? 30),
  ) {}

  create(input: CreateProposalInput): Proposal {
    const now = new Date();
    const expiresAt = new Date(now.getTime() + input.ttlMinutes * 60_000);
    const payloadJson = JSON.stringify(input.payload, Object.keys(input.payload).sort());
    const payloadHash = hashPayloadCanonical(input.payload);
    const payloadCiphertext = encryptPayload(payloadJson);
    const humanSummaryCiphertext = encryptPayload(input.humanSummary);

    const proposal: Proposal = {
      id: input.id ?? randomId(),
      aiRunId: input.aiRunId,
      patientId: input.patientId,
      proposedByUserId: input.proposedByUserId,
      actionType: input.actionType,
      payloadCiphertext,
      payloadHash,
      humanSummaryCiphertext,
      requiredPermission: input.requiredPermission,
      resourceVersionsJson: { ...input.resourceVersionsJson },
      status: "pending",
      expiresAt: expiresAt.toISOString(),
      createdAt: now.toISOString(),
    };
    this.store.set(proposal);
    return proposal;
  }

  isExpired(p: Proposal, now = new Date()): boolean {
    return new Date(p.expiresAt).getTime() <= now.getTime();
  }

  /**
   * Confirmation requires:
   * - status pending
   * - not expired
   * - patient matches
   * - hash matches (confirmation binding)
   * - resource versions match (optimistic concurrency)
   * - isAuthorized + hasRecentAuth for high-impact actions
   * Transactional: only one caller can move pending -> confirmed.
   */
  confirm(input: ConfirmProposalInput, now = new Date()): Proposal {
    const p = this.store.get(input.proposalId);
    if (!p) throw Object.assign(new Error("Proposal not found"), { code: "PROPOSAL_NOT_FOUND" });
    if (p.patientId !== input.patientId) throw Object.assign(new Error("Patient mismatch"), { code: "PATIENT_MISMATCH" });
    if (p.status !== "pending") throw Object.assign(new Error(`Proposal not pending: ${p.status}`), { code: "PROPOSAL_NOT_PENDING" });
    if (this.isExpired(p, now)) {
      p.status = "expired";
      this.store.set(p);
      throw Object.assign(new Error("Proposal expired"), { code: "PROPOSAL_EXPIRED" });
    }
    if (!input.isAuthorized) throw Object.assign(new Error("Not authorized"), { code: "NOT_AUTHORIZED" });
    if (p.payloadHash !== input.payloadHash) throw Object.assign(new Error("Payload hash mismatch"), { code: "HASH_MISMATCH" });
    if (!versionsEqual(p.resourceVersionsJson, input.expectedResourceVersions)) {
      throw Object.assign(new Error("Resource version conflict"), { code: "VERSION_CONFLICT" });
    }
    // High-impact actions require recent auth — caller provides hasRecentAuth boolean
    if (isHighImpact(p.actionType) && !input.hasRecentAuth) {
      throw Object.assign(new Error("Recent authentication required"), { code: "RECENT_AUTH_REQUIRED" });
    }
    // Exactly-once guard: CAS pending -> confirmed
    p.status = "confirmed";
    p.confirmedByUserId = input.confirmingUserId;
    p.confirmedAt = now.toISOString();
    this.store.set(p);
    return p;
  }

  /**
   * Execute confirmed proposal exactly once. Returns executed proposal.
   */
  execute(proposalId: string, executor: (payload: Record<string, unknown>) => Promise<void> | void): Proposal {
    const p = this.store.get(proposalId);
    if (!p) throw Object.assign(new Error("Proposal not found"), { code: "PROPOSAL_NOT_FOUND" });
    if (p.status !== "confirmed") throw Object.assign(new Error(`Proposal not confirmed: ${p.status}`), { code: "NOT_CONFIRMED" });
    // exactly-once: if already executed, reject replay
    // We move to executed only once; subsequent calls will see status !== confirmed
    const payloadJson = decryptPayload(p.payloadCiphertext);
    const payload = JSON.parse(payloadJson) as Record<string, unknown>;
    // In production this would be transactional with DB mutation
    // Here we call executor then mark executed
    // If executor throws, mark failed
    try {
      const res = executor(payload);
      if (res instanceof Promise) {
        // caller should await; for sync wrapper we treat as succeeded if no throw
      }
    } catch (e) {
      p.status = "failed";
      this.store.set(p);
      throw e;
    }
    p.status = "executed";
    p.executedAt = new Date().toISOString();
    this.store.set(p);
    return p;
  }

  reject(proposalId: string, patientId: string): Proposal {
    const p = this.store.get(proposalId);
    if (!p) throw Object.assign(new Error("Not found"), { code: "PROPOSAL_NOT_FOUND" });
    if (p.patientId !== patientId) throw Object.assign(new Error("Patient mismatch"), { code: "PATIENT_MISMATCH" });
    if (p.status !== "pending") throw Object.assign(new Error("Not pending"), { code: "PROPOSAL_NOT_PENDING" });
    p.status = "rejected";
    this.store.set(p);
    return p;
  }

  expireSweep(now = new Date()): number {
    let count = 0;
    for (const p of this.store.list()) {
      if (p.status === "pending" && this.isExpired(p, now)) {
        p.status = "expired";
        this.store.set(p);
        count++;
      }
    }
    return count;
  }

  decryptHumanSummary(p: Proposal): string {
    return decryptPayload(p.humanSummaryCiphertext);
  }

  decryptPayload(p: Proposal): Record<string, unknown> {
    const json = decryptPayload(p.payloadCiphertext);
    return JSON.parse(json) as Record<string, unknown>;
  }
}

function randomId(): string {
  return randomBytes(16).toString("hex");
}

function versionsEqual(a: Record<string, number>, b: Record<string, number>): boolean {
  const ka = Object.keys(a).sort();
  const kb = Object.keys(b).sort();
  if (ka.length !== kb.length) return false;
  for (let i = 0; i < ka.length; i++) {
    if (ka[i] !== kb[i]) return false;
    if (a[ka[i]] !== b[ka[i]]) return false;
  }
  return true;
}

function isHighImpact(actionType: string): boolean {
  return new Set([
    "propose_discontinue_medication",
    "propose_caregiver_invite",
    "propose_create_or_supersede_schedule",
  ]).has(actionType);
}
