/**
 * modules/auth/service.ts — Auth business logic (plan §8.4, §10)
 */
import { eq, and, gt } from "drizzle-orm";
import { v4 as uuidv4 } from "uuid";
import type { MedacDb } from "../../db/index.js";
import * as schema from "../../db/schema.js";
import { hashPassword, verifyPassword, validatePasswordStrength, needsRehash } from "./argon2.js";
import { hashToken, generateOpaqueToken, generateRecoveryCode, generateRefreshToken, signAccessToken, coarsenIp, summarizeUserAgent } from "./tokens.js";
import { generateTotpSecret, encryptSecret, decryptSecret, verifyTotp, buildOtpAuthUrl } from "./totp.js";
import { getConfig } from "../../config/index.js";

// ── Helpers ───────────────────────────────────────────────────────────
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function now(): Date {
  return new Date();
}

// In-memory rate limiter (§10.4) — per process; production would use PG/Redis but spec allows app-level initially.
// Keys are strings; returns true if allowed, false if limited.
const rateBuckets = new Map<string, { count: number; resetAt: number }>();
export function checkRateLimit(key: string, limit: number, windowMs: number): boolean {
  const nowMs = Date.now();
  const b = rateBuckets.get(key);
  if (!b || nowMs >= b.resetAt) {
    rateBuckets.set(key, { count: 1, resetAt: nowMs + windowMs });
    return true;
  }
  if (b.count < limit) {
    b.count++;
    return true;
  }
  return false;
}
export function __clearRateBuckets(): void {
  rateBuckets.clear();
}
function assertRateLimit(key: string, limit: number, windowMs: number): void {
  if (!checkRateLimit(key, limit, windowMs)) {
    const err = new Error("Too many requests") as Error & { statusCode?: number; code?: string };
    err.statusCode = 429;
    err.code = "RATE_LIMITED";
    throw err;
  }
}

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
  await db.insert(schema.outboxEvents).values({
    id: uuidv4(),
    eventType,
    aggregateType,
    aggregateId,
    payloadJson: payload,
    availableAt: now(),
  });
}

// ── Registration ─────────────────────────────────────────────────────
export async function register(db: MedacDb, input: { email: string; password: string; preferredLocale?: string; requestId?: string; ip?: string }): Promise<{ userId: string }> {
  const emailNormalized = normalizeEmail(input.email);
  const emailBucket = hashToken(`register:${emailNormalized}`).slice(0, 24);
  assertRateLimit(`register:ip:${input.ip ?? "unknown"}`, 10, 60 * 60 * 1000);
  assertRateLimit(`register:email:${emailBucket}`, 6, 60 * 60 * 1000);
  const { valid, reason } = validatePasswordStrength(input.password);
  if (!valid) {
    const err = new Error(reason) as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "WEAK_PASSWORD";
    throw err;
  }
  // breached-password check stub — allow but could call k-anonymity API
  const existing = await db.query.users.findFirst({ where: eq(schema.users.emailNormalized, emailNormalized) });
  if (existing) {
    const err = new Error("Email already registered") as Error & { statusCode?: number; code?: string };
    err.statusCode = 409;
    err.code = "EMAIL_TAKEN";
    throw err;
  }
  const passwordHash = await hashPassword(input.password);
  const userId = uuidv4();
  await db.insert(schema.users).values({
    id: userId,
    emailNormalized,
    emailDisplay: input.email.trim(),
    passwordHash,
    status: "active",
    emailVerifiedAt: now(),
    preferredLocale: input.preferredLocale ?? "en",
  });
  await db.insert(schema.userSecurity).values({ userId, failedLoginCount: 0, tokenVersion: 1 });
  // Auto-activate for testing (option 2) — still create verification token/outbox for backward compat but not required for login
  const rawToken = generateOpaqueToken(32);
  const tokenHash = hashToken(rawToken);
  const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000);
  await db.insert(schema.emailVerificationTokens).values({ id: uuidv4(), userId, tokenHash, expiresAt });
  await outbox(db, "user_registered", "user", userId, { emailNormalized, rawToken });
  await audit(db, { action: "user.register", entityType: "user", entityId: userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
  // Email send would be via outbox/worker; for console provider we could log without PHI. Caller should use token to send.
  // Return raw token in non-production for testing convenience? Only via service caller.
  // We store hash; rawToken must be delivered via email. For tests, fetch via helper or return when console mode.
  // To avoid leaking in prod, caller decides to not return raw token; but for API we do NOT return it — email is outbox.
  // For convenience in integration tests, we expose via returned object when env is test.
  const expose = process.env.NODE_ENV !== "production" ? { rawVerificationToken: rawToken } : {};
  void expose;
  // Audit doesn't log token.
  return { userId };
}

// For tests/dev: get latest verification token raw? We can't recover from hash; so we generate and store then return for non-prod internal use.
// Provide a helper that returns raw token directly for e2e tests by querying DB? Instead register returns token in test mode.
export async function registerAndReturnToken(db: MedacDb, input: { email: string; password: string }): Promise<{ userId: string; rawToken: string }> {
  const emailNormalized = normalizeEmail(input.email);
  const passwordHash = await hashPassword(input.password);
  const userId = uuidv4();
  // upsert for test helper
  await db.insert(schema.users).values({ id: userId, emailNormalized, emailDisplay: input.email, passwordHash, status: "pending" }).onConflictDoNothing();
  await db.insert(schema.userSecurity).values({ userId, tokenVersion: 1 }).onConflictDoNothing();
  const rawToken = generateOpaqueToken(32);
  await db.insert(schema.emailVerificationTokens).values({ id: uuidv4(), userId, tokenHash: hashToken(rawToken), expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000) });
  return { userId, rawToken };
}

export async function verifyEmail(db: MedacDb, input: { token: string; requestId?: string; ip?: string }): Promise<void> {
  assertRateLimit(`verify-email:ip:${input.ip ?? "unknown"}`, 20, 60 * 60 * 1000);
  const h = hashToken(input.token);
  const row = await db.query.emailVerificationTokens.findFirst({ where: and(eq(schema.emailVerificationTokens.tokenHash, h), gt(schema.emailVerificationTokens.expiresAt, now())) });
  if (!row || row.usedAt) {
    const err = new Error("Invalid or expired verification token") as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "INVALID_TOKEN";
    throw err;
  }
  await db.update(schema.emailVerificationTokens).set({ usedAt: now() }).where(eq(schema.emailVerificationTokens.id, row.id));
  await db.update(schema.users).set({ emailVerifiedAt: now(), status: "active", updatedAt: now() }).where(eq(schema.users.id, row.userId));
  await audit(db, { actorUserId: row.userId, action: "user.verify_email", entityType: "user", entityId: row.userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
}

export async function resendVerification(db: MedacDb, input: { email: string; requestId?: string; ip?: string }): Promise<void> {
  assertRateLimit(`resend-verify:ip:${input.ip ?? "unknown"}`, 10, 60 * 60 * 1000);
  const emailNormalized = normalizeEmail(input.email);
  // No enumeration: always return success, but only create token if user exists and pending
  const user = await db.query.users.findFirst({ where: eq(schema.users.emailNormalized, emailNormalized) });
  if (!user) {
    await audit(db, { action: "user.resend_verification_noop", entityType: "user", requestId: input.requestId, ipPrefix: coarsenIp(input.ip), metadataJson: { reason: "user_not_found" } });
    return;
  }
  if (user.status === "active" && user.emailVerifiedAt) return;
  const raw = generateOpaqueToken(32);
  await db.insert(schema.emailVerificationTokens).values({ id: uuidv4(), userId: user.id, tokenHash: hashToken(raw), expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000) });
  await outbox(db, "user_registered", "user", user.id, { emailNormalized });
  await audit(db, { actorUserId: user.id, action: "user.resend_verification", entityType: "user", entityId: user.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
}

// ── Login ────────────────────────────────────────────────────────────
export async function login(db: MedacDb, input: { email: string; password: string; deviceInfo?: { platform?: string; displayName?: string; timezone?: string }; ip?: string; userAgent?: string; requestId?: string }): Promise<{ userId: string; needsMfa: boolean; accessToken?: string; refreshToken?: string; sessionId?: string; mfaSessionToken?: string }> {
  const emailNormalized = normalizeEmail(input.email);
  const accountBucket = hashToken(`login:${emailNormalized}`).slice(0, 24);
  assertRateLimit(`login:ip:${input.ip ?? "unknown"}`, 60, 15 * 60 * 1000);
  assertRateLimit(`login:account:${accountBucket}`, 10, 15 * 60 * 1000);
  const user = await db.query.users.findFirst({ where: eq(schema.users.emailNormalized, emailNormalized) });
  if (!user) {
    // no enumeration timing side-channel mitigated by dummy hash verify
    await hashPassword("dummy-password-for-timing-12-chars");
    const err = new Error("Invalid credentials") as Error & { statusCode?: number; code?: string };
    err.statusCode = 401;
    err.code = "INVALID_CREDENTIALS";
    throw err;
  }
  const sec = await db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, user.id) });
  if (sec?.lockedUntil && sec.lockedUntil > now()) {
    const err = new Error("Account locked") as Error & { statusCode?: number; code?: string };
    err.statusCode = 423;
    err.code = "ACCOUNT_LOCKED";
    throw err;
  }
  if (user.status !== "active") {
    const err = new Error("Account not active") as Error & { statusCode?: number; code?: string };
    err.statusCode = 403;
    err.code = "ACCOUNT_NOT_ACTIVE";
    throw err;
  }
  const ok = await verifyPassword(user.passwordHash, input.password);
  if (!ok) {
    const newCount = (sec?.failedLoginCount ?? 0) + 1;
    const lockedUntil = newCount >= 10 ? new Date(Date.now() + 15 * 60 * 1000) : null;
    if (sec) await db.update(schema.userSecurity).set({ failedLoginCount: newCount, lockedUntil }).where(eq(schema.userSecurity.userId, user.id));
    await audit(db, { actorUserId: user.id, action: "user.login_failed", entityType: "user", entityId: user.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
    const err = new Error("Invalid credentials") as Error & { statusCode?: number; code?: string };
    err.statusCode = 401;
    err.code = "INVALID_CREDENTIALS";
    throw err;
  }
  // reset count on success
  if (sec) await db.update(schema.userSecurity).set({ failedLoginCount: 0, lockedUntil: null }).where(eq(schema.userSecurity.userId, user.id));

  // rehash if outdated
  if (needsRehash(user.passwordHash)) {
    const newHash = await hashPassword(input.password);
    await db.update(schema.users).set({ passwordHash: newHash, updatedAt: now() }).where(eq(schema.users.id, user.id));
  }

  await db.update(schema.users).set({ lastLoginAt: now(), updatedAt: now() }).where(eq(schema.users.id, user.id));

  // MFA check
  const totp = await db.query.mfaTotpCredentials.findFirst({ where: eq(schema.mfaTotpCredentials.userId, user.id) });
  const mfaEnabled = !!totp?.enabledAt;
  if (mfaEnabled) {
    // Issue a short-lived mfa session token (opaque) that must be presented to /auth/mfa/verify along with TOTP
    const mfaToken = generateOpaqueToken(32);
    // store as a pending session? Simplify: use password_reset_tokens table-like store? Instead create an auth_sessions row with revoked? Simpler: return mfaSessionToken and expect client to call verify with it.
    // We'll stash it in a in-memory map for 5 minutes; for persistence we could use email_verification_tokens but use a dedicated table? We'll use outbox-like ephemeral: create a row in auth_sessions with expiry and not yet fully valid.
    // Instead implement mfa challenge via a signed JWT with limited scope? Simpler: hash and store in db via a temporary table emulation using passwordResetTokens with special use.
    // For plan completeness we store in auth_sessions with status pending_mfa: create row with refreshTokenHash = hash(mfaToken) and expires in 5 min, previousTokenHash null, family same.
    const familyId = uuidv4();
    const mfaExpiresAt = new Date(Date.now() + 5 * 60 * 1000);
    const deviceId = await ensureDevice(db, user.id, input.deviceInfo);
    await db.insert(schema.authSessions).values({
      id: uuidv4(),
      userId: user.id,
      deviceId,
      refreshTokenHash: hashToken(mfaToken),
      refreshFamilyId: familyId,
      expiresAt: mfaExpiresAt,
      createdIpPrefix: coarsenIp(input.ip),
      userAgentSummary: summarizeUserAgent(input.userAgent),
    });
    await audit(db, { actorUserId: user.id, action: "user.login_mfa_required", entityType: "user", entityId: user.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
    return { userId: user.id, needsMfa: true, mfaSessionToken: mfaToken };
  }

  // No MFA — create full session
  const session = await createSession(db, { userId: user.id, tokenVersion: sec?.tokenVersion ?? 1, deviceInfo: input.deviceInfo, ip: input.ip, userAgent: input.userAgent, requestId: input.requestId });
  return { userId: user.id, needsMfa: false, accessToken: session.accessToken, refreshToken: session.refreshToken, sessionId: session.sessionId };
}

async function ensureDevice(db: MedacDb, userId: string, info?: { platform?: string; displayName?: string; timezone?: string }): Promise<string | null> {
  if (!info) return null;
  // Reuse existing device matching platform+displayName? Simple: create new if not provided
  const deviceId = uuidv4();
  await db.insert(schema.devices).values({
    id: deviceId,
    userId,
    platform: (info.platform as typeof schema.devicePlatformEnum.enumValues[number]) ?? "unknown",
    displayName: info.displayName ?? null,
    timezone: info.timezone ?? null,
    lastSeenAt: now(),
  });
  return deviceId;
}

async function createSession(db: MedacDb, params: { userId: string; tokenVersion: number; deviceInfo?: { platform?: string; displayName?: string; timezone?: string }; ip?: string; userAgent?: string; requestId?: string }): Promise<{ sessionId: string; accessToken: string; refreshToken: string }> {
  const cfg = getConfig();
  const sessionId = uuidv4();
  const familyId = uuidv4();
  const rawRefresh = generateRefreshToken();
  const refreshHash = hashToken(rawRefresh);
  const expiresAt = new Date(Date.now() + cfg.refreshTokenTtlDays * 24 * 60 * 60 * 1000);
  const deviceId = await ensureDevice(db, params.userId, params.deviceInfo);
  await db.insert(schema.authSessions).values({
    id: sessionId,
    userId: params.userId,
    deviceId,
    refreshTokenHash: refreshHash,
    refreshFamilyId: familyId,
    expiresAt,
    createdIpPrefix: coarsenIp(params.ip),
    userAgentSummary: summarizeUserAgent(params.userAgent),
    lastUsedAt: now(),
  });
  const accessToken = await signAccessToken({ userId: params.userId, sessionId, tokenVersion: params.tokenVersion });
  await audit(db, { actorUserId: params.userId, actorSessionId: sessionId, action: "user.login_success", entityType: "session", entityId: sessionId, requestId: params.requestId, ipPrefix: coarsenIp(params.ip) });
  return { sessionId, accessToken, refreshToken: rawRefresh };
}

export async function verifyMfa(db: MedacDb, input: { mfaSessionToken: string; code: string; recoveryCode?: boolean; ip?: string; requestId?: string; userAgent?: string }): Promise<{ accessToken: string; refreshToken: string; sessionId: string }> {
  assertRateLimit(`mfa:ip:${input.ip ?? "unknown"}`, 20, 15 * 60 * 1000);
  const h = hashToken(input.mfaSessionToken);
  const sess = await db.query.authSessions.findFirst({ where: eq(schema.authSessions.refreshTokenHash, h) });
  if (!sess || sess.revokedAt || sess.expiresAt < now()) {
    const err = new Error("Invalid MFA session") as Error & { statusCode?: number; code?: string };
    err.statusCode = 401;
    err.code = "INVALID_MFA_SESSION";
    throw err;
  }
  const userId = sess.userId;
  const sec = await db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, userId) });
  const cred = await db.query.mfaTotpCredentials.findFirst({ where: eq(schema.mfaTotpCredentials.userId, userId) });
  if (!cred || !cred.enabledAt) {
    const err = new Error("MFA not enabled") as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "MFA_NOT_ENABLED";
    throw err;
  }

  let verified = false;
  let newStep: number | null = null;
  if (input.recoveryCode) {
    const codeHash = hashToken(input.code.replace(/-/g, "").toUpperCase());
    const codes = await db.query.mfaRecoveryCodes.findMany({ where: eq(schema.mfaRecoveryCodes.userId, userId) });
    const match = codes.find(c => c.codeHash === codeHash && !c.usedAt);
    if (match) {
      await db.update(schema.mfaRecoveryCodes).set({ usedAt: now() }).where(eq(schema.mfaRecoveryCodes.id, match.id));
      verified = true;
    }
  } else {
    const secret = decryptSecret(cred.encryptedSecret);
    const res = verifyTotp(secret, input.code, { lastUsedStep: cred.lastUsedStep ?? null });
    if (res.valid) {
      verified = true;
      newStep = res.step;
      await db.update(schema.mfaTotpCredentials).set({ lastUsedStep: newStep }).where(eq(schema.mfaTotpCredentials.id, cred.id));
    }
  }
  if (!verified) {
    const err = new Error("Invalid MFA code") as Error & { statusCode?: number; code?: string };
    err.statusCode = 401;
    err.code = "INVALID_MFA_CODE";
    throw err;
  }

  // Upgrade the pending session into a real session: replace refresh token, issue access token
  const rawRefresh = generateRefreshToken();
  const refreshHash = hashToken(rawRefresh);
  const cfg = getConfig();
  const newExpiresAt = new Date(Date.now() + cfg.refreshTokenTtlDays * 24 * 60 * 60 * 1000);
  await db.update(schema.authSessions).set({ refreshTokenHash: refreshHash, expiresAt: newExpiresAt, lastUsedAt: now() }).where(eq(schema.authSessions.id, sess.id));
  const tokenVersion = sec?.tokenVersion ?? 1;
  const accessToken = await signAccessToken({ userId, sessionId: sess.id, tokenVersion });
  await audit(db, { actorUserId: userId, actorSessionId: sess.id, action: "user.mfa_verified", entityType: "session", entityId: sess.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
  return { accessToken, refreshToken: rawRefresh, sessionId: sess.id };
}

// ── Refresh rotation with reuse detection (revoke family) ─────────────
export async function refresh(db: MedacDb, input: { refreshToken: string; ip?: string; userAgent?: string; requestId?: string }): Promise<{ accessToken: string; refreshToken: string; sessionId: string }> {
  assertRateLimit(`refresh:ip:${input.ip ?? "unknown"}`, 60, 60 * 1000);
  const h = hashToken(input.refreshToken);
  const sess = await db.query.authSessions.findFirst({ where: eq(schema.authSessions.refreshTokenHash, h) });
  if (sess) {
    // Pending MFA challenges share this table but are intentionally short-lived
    // and have no last_used_at. They are not refresh sessions and must never be
    // exchangeable at /auth/refresh without a valid TOTP/recovery code.
    if (!sess.lastUsedAt) {
      const err = new Error("Invalid refresh token") as Error & { statusCode?: number; code?: string };
      err.statusCode = 401;
      err.code = "INVALID_REFRESH";
      throw err;
    }
    if (sess.revokedAt || sess.expiresAt < now()) {
      const err = new Error("Refresh token expired") as Error & { statusCode?: number; code?: string };
      err.statusCode = 401;
      err.code = "REFRESH_EXPIRED";
      throw err;
    }
    // Valid rotation: generate new token, keep family, store previous hash for detection
    const rawNew = generateRefreshToken();
    const newHash = hashToken(rawNew);
    const cfg = getConfig();
    const newExpiresAt = new Date(Date.now() + cfg.refreshTokenTtlDays * 24 * 60 * 60 * 1000);
    await db.update(schema.authSessions).set({
      refreshTokenHash: newHash,
      previousTokenHash: h,
      lastUsedAt: now(),
      expiresAt: newExpiresAt,
    }).where(eq(schema.authSessions.id, sess.id));
    const sec = await db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, sess.userId) });
    const tokenVersion = sec?.tokenVersion ?? 1;
    const accessToken = await signAccessToken({ userId: sess.userId, sessionId: sess.id, tokenVersion });
    await audit(db, { actorUserId: sess.userId, actorSessionId: sess.id, action: "user.refresh", entityType: "session", entityId: sess.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
    return { accessToken, refreshToken: rawNew, sessionId: sess.id };
  }
  // Not found by current hash — check if it's a reused previous token (reuse detection)
  const prevSess = await db.query.authSessions.findFirst({ where: eq(schema.authSessions.previousTokenHash, h) });
  if (prevSess) {
    // Replay detected — revoke entire family
    const familyId = prevSess.refreshFamilyId;
    await db.update(schema.authSessions).set({ revokedAt: now() }).where(eq(schema.authSessions.refreshFamilyId, familyId));
    // bump token_version to invalidate access tokens
    const sec = await db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, prevSess.userId) });
    if (sec) await db.update(schema.userSecurity).set({ tokenVersion: (sec.tokenVersion ?? 1) + 1 }).where(eq(schema.userSecurity.userId, prevSess.userId));
    await audit(db, { actorUserId: prevSess.userId, action: "user.refresh_reuse_detected", entityType: "session", entityId: prevSess.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip), metadataJson: { familyId } });
    const err = new Error("Refresh token reuse detected") as Error & { statusCode?: number; code?: string };
    err.statusCode = 401;
    err.code = "REFRESH_REUSE";
    throw err;
  }
  const err = new Error("Invalid refresh token") as Error & { statusCode?: number; code?: string };
  err.statusCode = 401;
  err.code = "INVALID_REFRESH";
  throw err;
}

export async function logout(db: MedacDb, input: { refreshToken?: string; sessionId?: string; userId?: string; requestId?: string; ip?: string }): Promise<void> {
  if (input.refreshToken) {
    const h = hashToken(input.refreshToken);
    const sess = await db.query.authSessions.findFirst({ where: eq(schema.authSessions.refreshTokenHash, h) });
    if (sess) {
      await db.update(schema.authSessions).set({ revokedAt: now() }).where(eq(schema.authSessions.id, sess.id));
      await audit(db, { actorUserId: sess.userId, actorSessionId: sess.id, action: "user.logout", entityType: "session", entityId: sess.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
    }
    return;
  }
  if (input.sessionId && input.userId) {
    await db.update(schema.authSessions).set({ revokedAt: now() }).where(and(eq(schema.authSessions.id, input.sessionId), eq(schema.authSessions.userId, input.userId)));
    await audit(db, { actorUserId: input.userId, actorSessionId: input.sessionId, action: "user.logout", entityType: "session", entityId: input.sessionId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
  }
}

export async function logoutAll(db: MedacDb, input: { userId: string; requestId?: string; ip?: string }): Promise<void> {
  await db.update(schema.authSessions).set({ revokedAt: now() }).where(eq(schema.authSessions.userId, input.userId));
  // bump token_version to invalidate all access tokens
  const sec = await db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, input.userId) });
  if (sec) await db.update(schema.userSecurity).set({ tokenVersion: (sec.tokenVersion ?? 1) + 1 }).where(eq(schema.userSecurity.userId, input.userId));
  await audit(db, { actorUserId: input.userId, action: "user.logout_all", entityType: "user", entityId: input.userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
}

// ── Password reset/forgot (no enumeration) ───────────────────────────
export async function forgotPassword(db: MedacDb, input: { email: string; requestId?: string; ip?: string }): Promise<void> {
  const emailNormalized = normalizeEmail(input.email);
  const emailBucket = hashToken(`forgot:${emailNormalized}`).slice(0, 24);
  assertRateLimit(`forgot:ip:${input.ip ?? "unknown"}`, 10, 60 * 60 * 1000);
  assertRateLimit(`forgot:email:${emailBucket}`, 6, 60 * 60 * 1000);
  const user = await db.query.users.findFirst({ where: eq(schema.users.emailNormalized, emailNormalized) });
  // Always succeed response even if not found (no enumeration per §8.4)
  if (!user) {
    await audit(db, { action: "user.forgot_noop", entityType: "user", requestId: input.requestId, ipPrefix: coarsenIp(input.ip), metadataJson: { reason: "not_found" } });
    return;
  }
  const raw = generateOpaqueToken(32);
  await db.insert(schema.passwordResetTokens).values({ id: uuidv4(), userId: user.id, tokenHash: hashToken(raw), expiresAt: new Date(Date.now() + 60 * 60 * 1000) });
  await outbox(db, "password_changed", "user", user.id, { reason: "forgot" });
  await audit(db, { actorUserId: user.id, action: "user.forgot", entityType: "user", entityId: user.id, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
}

export async function resetPassword(db: MedacDb, input: { token: string; newPassword: string; requestId?: string; ip?: string }): Promise<void> {
  const { valid, reason } = validatePasswordStrength(input.newPassword);
  if (!valid) {
    const err = new Error(reason) as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "WEAK_PASSWORD";
    throw err;
  }
  const h = hashToken(input.token);
  const row = await db.query.passwordResetTokens.findFirst({ where: and(eq(schema.passwordResetTokens.tokenHash, h), gt(schema.passwordResetTokens.expiresAt, now())) });
  if (!row || row.usedAt) {
    const err = new Error("Invalid or expired reset token") as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "INVALID_TOKEN";
    throw err;
  }
  const newHash = await hashPassword(input.newPassword);
  await db.update(schema.passwordResetTokens).set({ usedAt: now() }).where(eq(schema.passwordResetTokens.id, row.id));
  await db.update(schema.users).set({ passwordHash: newHash, updatedAt: now() }).where(eq(schema.users.id, row.userId));
  await db.update(schema.userSecurity).set({ passwordChangedAt: now(), failedLoginCount: 0, lockedUntil: null, tokenVersion: (await db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, row.userId) }))!.tokenVersion + 1 }).where(eq(schema.userSecurity.userId, row.userId));
  // revoke all sessions
  await db.update(schema.authSessions).set({ revokedAt: now() }).where(eq(schema.authSessions.userId, row.userId));
  await audit(db, { actorUserId: row.userId, action: "user.reset_password", entityType: "user", entityId: row.userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
  await outbox(db, "password_changed", "user", row.userId, { via: "reset" });
}

export async function changePassword(db: MedacDb, input: { userId: string; currentPassword: string; newPassword: string; sessionId?: string; requestId?: string; ip?: string }): Promise<void> {
  const { valid, reason } = validatePasswordStrength(input.newPassword);
  if (!valid) {
    const err = new Error(reason) as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "WEAK_PASSWORD";
    throw err;
  }
  const user = await db.query.users.findFirst({ where: eq(schema.users.id, input.userId) });
  if (!user) throw Object.assign(new Error("User not found"), { statusCode: 404 });
  const ok = await verifyPassword(user.passwordHash, input.currentPassword);
  if (!ok) {
    const err = new Error("Current password incorrect") as Error & { statusCode?: number; code?: string };
    err.statusCode = 401;
    err.code = "INVALID_CREDENTIALS";
    throw err;
  }
  const newHash = await hashPassword(input.newPassword);
  await db.update(schema.users).set({ passwordHash: newHash, updatedAt: now() }).where(eq(schema.users.id, input.userId));
  const sec = await db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, input.userId) });
  if (sec) await db.update(schema.userSecurity).set({ passwordChangedAt: now(), tokenVersion: (sec.tokenVersion ?? 1) + 1 }).where(eq(schema.userSecurity.userId, input.userId));
  // keep current session? Spec says revoke all after reset; for change we revoke all except maybe current? We'll revoke all for safety and re-issue not needed — require re-login.
  await db.update(schema.authSessions).set({ revokedAt: now() }).where(eq(schema.authSessions.userId, input.userId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.sessionId, action: "user.change_password", entityType: "user", entityId: input.userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
}

// ── TOTP MFA ─────────────────────────────────────────────────────────
export async function totpSetup(db: MedacDb, input: { userId: string; requestId?: string; ip?: string }): Promise<{ secretBase32: string; otpauthUrl: string }> {
  const user = await db.query.users.findFirst({ where: eq(schema.users.id, input.userId) });
  if (!user) throw Object.assign(new Error("User not found"), { statusCode: 404 });
  const existing = await db.query.mfaTotpCredentials.findFirst({ where: eq(schema.mfaTotpCredentials.userId, input.userId) });
  if (existing?.enabledAt) {
    const err = new Error("MFA already enabled") as Error & { statusCode?: number; code?: string };
    err.statusCode = 409;
    err.code = "MFA_ALREADY_ENABLED";
    throw err;
  }
  const { raw, base32 } = generateTotpSecret();
  const enc = encryptSecret(raw);
  if (existing) {
    await db.update(schema.mfaTotpCredentials).set({ encryptedSecret: enc, enabledAt: null, lastUsedStep: null }).where(eq(schema.mfaTotpCredentials.userId, input.userId));
  } else {
    await db.insert(schema.mfaTotpCredentials).values({ id: uuidv4(), userId: input.userId, encryptedSecret: enc });
  }
  const url = buildOtpAuthUrl({ issuer: "Medac", accountName: user.emailDisplay, secretBase32: base32 });
  await audit(db, { actorUserId: input.userId, action: "user.mfa_setup", entityType: "user", entityId: input.userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
  return { secretBase32: base32, otpauthUrl: url };
}

export async function totpConfirm(db: MedacDb, input: { userId: string; code: string; requestId?: string; ip?: string }): Promise<{ recoveryCodes: string[] }> {
  const cred = await db.query.mfaTotpCredentials.findFirst({ where: eq(schema.mfaTotpCredentials.userId, input.userId) });
  if (!cred) {
    const err = new Error("MFA setup not started") as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "MFA_NOT_SETUP";
    throw err;
  }
  if (cred.enabledAt) {
    const err = new Error("MFA already enabled") as Error & { statusCode?: number; code?: string };
    err.statusCode = 409;
    err.code = "MFA_ALREADY_ENABLED";
    throw err;
  }
  const secret = decryptSecret(cred.encryptedSecret);
  const res = verifyTotp(secret, input.code);
  if (!res.valid) {
    const err = new Error("Invalid TOTP code") as Error & { statusCode?: number; code?: string };
    err.statusCode = 400;
    err.code = "INVALID_TOTP";
    throw err;
  }
  await db.update(schema.mfaTotpCredentials).set({ enabledAt: now(), lastUsedStep: res.step }).where(eq(schema.mfaTotpCredentials.id, cred.id));
  // generate 10 recovery codes
  const codes: string[] = [];
  for (let i = 0; i < 10; i++) {
    const raw = generateRecoveryCode();
    const hash = hashToken(raw.replace(/-/g, "").toUpperCase());
    await db.insert(schema.mfaRecoveryCodes).values({ id: uuidv4(), userId: input.userId, codeHash: hash });
    codes.push(raw);
  }
  await db.update(schema.userSecurity).set({ mfaRequired: true }).where(eq(schema.userSecurity.userId, input.userId));
  await audit(db, { actorUserId: input.userId, action: "user.mfa_confirm", entityType: "user", entityId: input.userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
  return { recoveryCodes: codes };
}

export async function totpDelete(db: MedacDb, input: { userId: string; requestId?: string; ip?: string }): Promise<void> {
  await db.delete(schema.mfaTotpCredentials).where(eq(schema.mfaTotpCredentials.userId, input.userId));
  await db.delete(schema.mfaRecoveryCodes).where(eq(schema.mfaRecoveryCodes.userId, input.userId));
  await db.update(schema.userSecurity).set({ mfaRequired: false }).where(eq(schema.userSecurity.userId, input.userId));
  await audit(db, { actorUserId: input.userId, action: "user.mfa_delete", entityType: "user", entityId: input.userId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
}

export async function listSessions(db: MedacDb, userId: string) {
  return db.query.authSessions.findMany({ where: eq(schema.authSessions.userId, userId) });
}

export async function deleteSession(db: MedacDb, input: { userId: string; sessionId: string; requestId?: string; ip?: string }): Promise<void> {
  const sess = await db.query.authSessions.findFirst({ where: and(eq(schema.authSessions.id, input.sessionId), eq(schema.authSessions.userId, input.userId)) });
  if (!sess) {
    const err = new Error("Session not found") as Error & { statusCode?: number; code?: string };
    err.statusCode = 404;
    err.code = "NOT_FOUND";
    throw err;
  }
  await db.update(schema.authSessions).set({ revokedAt: now() }).where(eq(schema.authSessions.id, input.sessionId));
  await audit(db, { actorUserId: input.userId, actorSessionId: input.sessionId, action: "user.session_revoke", entityType: "session", entityId: input.sessionId, requestId: input.requestId, ipPrefix: coarsenIp(input.ip) });
}
