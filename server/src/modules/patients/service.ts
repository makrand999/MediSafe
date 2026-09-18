/**
 * modules/patients/service.ts — Patients, memberships, caregiver_invites, patient_consents
 * Enforces every patient-scoped query includes membership authorization §7.1
 */
import { eq, and } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { requirePermission, type PatientRole, type Permission } from "../../lib/permissions.js";
import { hashToken, generateOpaqueToken, coarsenIp } from "../auth/tokens.js";

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
async function outbox(db: MedacDb, eventType: string, aggregateType: string, aggregateId: string, payload: Record<string, unknown>): Promise<void> {
  await db.insert(schema.outboxEvents).values({ id: uuidv4(), eventType, aggregateType, aggregateId, payloadJson: payload, availableAt: now() });
}

export function normalizeEmail(email: string): string { return email.trim().toLowerCase(); }

// Central membership loader — §7.1 step 4
export async function requireMembership(db: MedacDb, userId: string, patientId: string): Promise<{ role: PatientRole; membershipId: string }> {
  const m = await db.query.patientMemberships.findFirst({ where: and(eq(schema.patientMemberships.patientId, patientId), eq(schema.patientMemberships.userId, userId), eq(schema.patientMemberships.status, "active")) });
  if (!m) {
    const err = new Error("Not a member of patient") as Error & { statusCode?: number; code?: string };
    err.statusCode = 403;
    err.code = "FORBIDDEN";
    throw err;
  }
  return { role: m.role as PatientRole, membershipId: m.id };
}

export async function requirePermissionOnPatient(db: MedacDb, userId: string, patientId: string, permission: Permission): Promise<PatientRole> {
  const { role } = await requireMembership(db, userId, patientId);
  requirePermission(role, permission);
  return role;
}

// ── Patients ─────────────────────────────────────────────────────────
export async function listPatients(db: MedacDb, userId: string): Promise<Array<typeof schema.patients.$inferSelect>> {
  // Query via memberships join — never expose patients without membership
  const memberships = await db.query.patientMemberships.findMany({ where: and(eq(schema.patientMemberships.userId, userId), eq(schema.patientMemberships.status, "active")) });
  const ids = memberships.map(m => m.patientId);
  if (ids.length === 0) return [];
  // Drizzle doesn't have IN helper easily without sql; fetch individually is fine for small N, but use sql
  const { inArray } = await import("drizzle-orm");
  return db.query.patients.findMany({ where: inArray(schema.patients.id, ids) });
}

export async function createPatient(db: MedacDb, input: { userId: string; displayName: string; preferredTimezone?: string; dateOfBirth?: string | null; notificationPrivacyMode?: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.patients.$inferSelect> {
  const patientId = uuidv4();
  const tz = input.preferredTimezone ?? "UTC";
  // Validate IANA timezone — reject bare offsets per §5.6
  if (/^[+-]\d{2}:\d{2}$/.test(tz) || tz === "Z") {
    const err = new Error("Timezone must be IANA name, not offset") as Error & { statusCode?: number };
    err.statusCode = 400;
    throw err;
  }
  try { Intl.DateTimeFormat(undefined, { timeZone: tz }); } catch {
    const err = new Error(`Invalid timezone ${tz}`) as Error & { statusCode?: number };
    err.statusCode = 400;
    throw err;
  }
  await db.insert(schema.patients).values({
    id: patientId,
    displayName: input.displayName,
    preferredTimezone: tz,
    dateOfBirth: input.dateOfBirth ?? null,
    notificationPrivacyMode: (input.notificationPrivacyMode as typeof schema.notificationPrivacyModeEnum.enumValues[number]) ?? "generic",
    createdByUserId: input.userId,
  });
  await db.insert(schema.patientMemberships).values({
    id: uuidv4(),
    patientId,
    userId: input.userId,
    role: "owner",
    status: "active",
    grantedByUserId: input.userId,
  });
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId, action: "patient.create", entityType: "patient", entityId: patientId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  const pat = await db.query.patients.findFirst({ where: eq(schema.patients.id, patientId) });
  return pat!;
}

export async function getPatient(db: MedacDb, userId: string, patientId: string): Promise<typeof schema.patients.$inferSelect> {
  await requirePermissionOnPatient(db, userId, patientId, "patient:read");
  const pat = await db.query.patients.findFirst({ where: eq(schema.patients.id, patientId) });
  if (!pat) {
    const err = new Error("Patient not found") as Error & { statusCode?: number };
    err.statusCode = 404;
    throw err;
  }
  return pat;
}

export async function updatePatient(db: MedacDb, input: { userId: string; patientId: string; patch: { displayName?: string; preferredTimezone?: string; notificationPrivacyMode?: string }; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.patients.$inferSelect> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "patient:update");
  const updates: Record<string, unknown> = { updatedAt: now() };
  if (input.patch.displayName !== undefined) updates.displayName = input.patch.displayName;
  if (input.patch.preferredTimezone !== undefined) {
    const tz = input.patch.preferredTimezone;
    if (/^[+-]\d{2}:\d{2}$/.test(tz)) throw Object.assign(new Error("Timezone must be IANA"), { statusCode: 400 });
    try { Intl.DateTimeFormat(undefined, { timeZone: tz }); } catch { throw Object.assign(new Error(`Invalid timezone ${tz}`), { statusCode: 400 }); }
    updates.preferredTimezone = tz;
  }
  if (input.patch.notificationPrivacyMode !== undefined) updates.notificationPrivacyMode = input.patch.notificationPrivacyMode;
  await db.update(schema.patients).set(updates as never).where(eq(schema.patients.id, input.patientId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "patient.update", entityType: "patient", entityId: input.patientId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  return (await db.query.patients.findFirst({ where: eq(schema.patients.id, input.patientId) }))!;
}

export async function archivePatient(db: MedacDb, input: { userId: string; patientId: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<void> {
  await requirePermissionOnPatient(db, input.userId, input.patientId, "patient:archive");
  await db.update(schema.patients).set({ archivedAt: now(), updatedAt: now() }).where(eq(schema.patients.id, input.patientId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "patient.archive", entityType: "patient", entityId: input.patientId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
}

// ── Memberships ──────────────────────────────────────────────────────
export async function listMemberships(db: MedacDb, userId: string, patientId: string) {
  await requirePermissionOnPatient(db, userId, patientId, "membership:read");
  return db.query.patientMemberships.findMany({ where: eq(schema.patientMemberships.patientId, patientId) });
}

export async function updateMembership(db: MedacDb, input: { actorUserId: string; patientId: string; membershipId: string; newRole: PatientRole; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<void> {
  await requirePermissionOnPatient(db, input.actorUserId, input.patientId, "membership:update");
  const mem = await db.query.patientMemberships.findFirst({ where: eq(schema.patientMemberships.id, input.membershipId) });
  if (!mem || mem.patientId !== input.patientId) throw Object.assign(new Error("Membership not found"), { statusCode: 404 });
  // Prevent demoting last owner
  if (mem.role === "owner" && input.newRole !== "owner") {
    const owners = await db.query.patientMemberships.findMany({ where: and(eq(schema.patientMemberships.patientId, input.patientId), eq(schema.patientMemberships.role, "owner" as never), eq(schema.patientMemberships.status, "active")) });
    if (owners.length <= 1) {
      const err = new Error("Cannot remove last owner") as Error & { statusCode?: number; code?: string };
      err.statusCode = 400;
      err.code = "LAST_OWNER";
      throw err;
    }
  }
  await db.update(schema.patientMemberships).set({ role: input.newRole as never, updatedAt: now() }).where(eq(schema.patientMemberships.id, input.membershipId));
  await audit(db, { actorUserId: input.actorUserId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "membership.update", entityType: "patient_membership", entityId: input.membershipId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { newRole: input.newRole } });
}

export async function revokeMembership(db: MedacDb, input: { actorUserId: string; patientId: string; membershipId: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<void> {
  await requirePermissionOnPatient(db, input.actorUserId, input.patientId, "membership:revoke");
  const mem = await db.query.patientMemberships.findFirst({ where: eq(schema.patientMemberships.id, input.membershipId) });
  if (!mem || mem.patientId !== input.patientId) throw Object.assign(new Error("Membership not found"), { statusCode: 404 });
  if (mem.role === "owner") {
    const owners = await db.query.patientMemberships.findMany({ where: and(eq(schema.patientMemberships.patientId, input.patientId), eq(schema.patientMemberships.role, "owner" as never), eq(schema.patientMemberships.status, "active")) });
    if (owners.length <= 1) {
      const err = new Error("Cannot remove last owner") as Error & { statusCode?: number; code?: string };
      err.statusCode = 400;
      err.code = "LAST_OWNER";
      throw err;
    }
  }
  await db.update(schema.patientMemberships).set({ status: "revoked", revokedAt: now(), updatedAt: now() }).where(eq(schema.patientMemberships.id, input.membershipId));
  await audit(db, { actorUserId: input.actorUserId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "membership.revoke", entityType: "patient_membership", entityId: input.membershipId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  await outbox(db, "membership_revoked", "patient_membership", input.membershipId, { patientId: input.patientId, userId: mem.userId });
}

// ── Caregiver invites (single-use, expiry, no enumeration §6.2, §8.6) ─
export async function createInvite(db: MedacDb, input: { actorUserId: string; patientId: string; invitedEmail: string; role: PatientRole; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<{ inviteId: string }> {
  await requirePermissionOnPatient(db, input.actorUserId, input.patientId, "invite:create");
  // role validation: cannot invite as owner unless actor is owner? permission matrix already enforces, but double-check
  if (input.role === "owner") {
    const { role: actorRole } = await requireMembership(db, input.actorUserId, input.patientId);
    if (actorRole !== "owner") {
      const err = new Error("Only owners can invite owners") as Error & { statusCode?: number };
      err.statusCode = 403;
      throw err;
    }
  }
  const emailNormalized = normalizeEmail(input.invitedEmail);
  const rawToken = generateOpaqueToken(32);
  const inviteId = uuidv4();
  const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  await db.insert(schema.caregiverInvites).values({
    id: inviteId,
    patientId: input.patientId,
    invitedEmailNormalized: emailNormalized,
    role: input.role as never,
    tokenHash: hashToken(rawToken),
    invitedByUserId: input.actorUserId,
    expiresAt,
  });
  // Do not reveal in response whether email has account; always return generic id. Raw token sent via email outbox (not returned except in test).
  await outbox(db, "caregiver_invited", "caregiver_invite", inviteId, { patientId: input.patientId, invitedEmailNormalized: emailNormalized, role: input.role, rawToken });
  await audit(db, { actorUserId: input.actorUserId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "invite.create", entityType: "caregiver_invite", entityId: inviteId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null, metadataJson: { role: input.role } });
  return { inviteId };
}

// Test helper: create invite and return raw token for acceptance in same process
export async function createInviteAndReturnToken(db: MedacDb, input: { actorUserId: string; patientId: string; invitedEmail: string; role: PatientRole }): Promise<{ inviteId: string; rawToken: string }> {
  await requirePermissionOnPatient(db, input.actorUserId, input.patientId, "invite:create");
  const emailNormalized = normalizeEmail(input.invitedEmail);
  const rawToken = generateOpaqueToken(32);
  const inviteId = uuidv4();
  await db.insert(schema.caregiverInvites).values({
    id: inviteId,
    patientId: input.patientId,
    invitedEmailNormalized: emailNormalized,
    role: input.role as never,
    tokenHash: hashToken(rawToken),
    invitedByUserId: input.actorUserId,
    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
  });
  return { inviteId, rawToken };
}

export async function listInvites(db: MedacDb, userId: string, patientId: string) {
  await requirePermissionOnPatient(db, userId, patientId, "invite:read");
  return db.query.caregiverInvites.findMany({ where: eq(schema.caregiverInvites.patientId, patientId) });
}

export async function revokeInvite(db: MedacDb, input: { actorUserId: string; patientId: string; inviteId: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<void> {
  await requirePermissionOnPatient(db, input.actorUserId, input.patientId, "invite:revoke");
  const inv = await db.query.caregiverInvites.findFirst({ where: eq(schema.caregiverInvites.id, input.inviteId) });
  if (!inv || inv.patientId !== input.patientId) throw Object.assign(new Error("Invite not found"), { statusCode: 404 });
  if (inv.acceptedAt || inv.revokedAt) throw Object.assign(new Error("Invite already used or revoked"), { statusCode: 400 });
  await db.update(schema.caregiverInvites).set({ revokedAt: now() }).where(eq(schema.caregiverInvites.id, input.inviteId));
  await audit(db, { actorUserId: input.actorUserId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "invite.revoke", entityType: "caregiver_invite", entityId: input.inviteId, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
}

export async function acceptInvite(db: MedacDb, input: { token: string; userId: string; requestId?: string | null; ip?: string | null; actorSessionId?: string | null }): Promise<{ patientId: string }> {
  const h = hashToken(input.token);
  const inv = await db.query.caregiverInvites.findFirst({ where: eq(schema.caregiverInvites.tokenHash, h) });
  if (!inv || inv.revokedAt || inv.acceptedAt || inv.expiresAt < now()) {
    const err = new Error("Invalid or expired invite") as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "INVALID_INVITE";
    throw err;
  }
  // Enforce single-use
  await db.update(schema.caregiverInvites).set({ acceptedAt: now() }).where(eq(schema.caregiverInvites.id, inv.id));
  // Create membership if not already active
  const existing = await db.query.patientMemberships.findFirst({ where: and(eq(schema.patientMemberships.patientId, inv.patientId), eq(schema.patientMemberships.userId, input.userId)) });
  if (existing) {
    if (existing.status === "active") {
      // already member — idempotent? Return patientId but mark invite used.
      await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: inv.patientId, action: "invite.accept_already_member", entityType: "caregiver_invite", entityId: inv.id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
      return { patientId: inv.patientId };
    }
    await db.update(schema.patientMemberships).set({ status: "active", role: inv.role as never, revokedAt: null, updatedAt: now() }).where(eq(schema.patientMemberships.id, existing.id));
  } else {
    await db.insert(schema.patientMemberships).values({
      id: uuidv4(),
      patientId: inv.patientId,
      userId: input.userId,
      role: inv.role as never,
      status: "active",
      grantedByUserId: inv.invitedByUserId,
    });
  }
  await audit(db, { actorUserId: input.userId, actorSessionId: input.actorSessionId, patientId: inv.patientId, action: "invite.accept", entityType: "caregiver_invite", entityId: inv.id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  return { patientId: inv.patientId };
}

// ── Patient consents ─────────────────────────────────────────────────
export async function createConsent(db: MedacDb, input: { actorUserId: string; patientId: string; consentType: string; scopeJson?: Record<string, unknown>; policyVersion: string; actorSessionId?: string | null; requestId?: string | null; ip?: string | null }): Promise<typeof schema.patientConsents.$inferSelect> {
  await requirePermissionOnPatient(db, input.actorUserId, input.patientId, "consent:write");
  const id = uuidv4();
  await db.insert(schema.patientConsents).values({
    id,
    patientId: input.patientId,
    consentType: input.consentType,
    grantedByUserId: input.actorUserId,
    scopeJson: (input.scopeJson ?? {}) as never,
    policyVersion: input.policyVersion,
  });
  await audit(db, { actorUserId: input.actorUserId, actorSessionId: input.actorSessionId, patientId: input.patientId, action: "consent.create", entityType: "patient_consent", entityId: id, requestId: input.requestId, ipPrefix: input.ip ? coarsenIp(input.ip) : null });
  return (await db.query.patientConsents.findFirst({ where: eq(schema.patientConsents.id, id) }))!;
}
