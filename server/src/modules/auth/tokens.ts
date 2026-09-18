/**
 * modules/auth/tokens.ts — JWT access tokens (JOSE) + opaque refresh tokens (plan §10.2)
 * Access token 10min, minimal claims sub/sid/iat/exp/iss/aud/token_version, kid rotation.
 * Refresh tokens: 256-bit random, stored only as HMAC(hash) using TOKEN_HASH_SECRET.
 */
import { createHmac, randomBytes } from "node:crypto";
import { readFileSync, existsSync } from "node:fs";
import { SignJWT, jwtVerify, importPKCS8, importSPKI, type JWTPayload } from "jose";
import { getConfig } from "../../config/index.js";

export interface AccessTokenClaims {
  sub: string; // user id
  sid: string; // session id
  iat: number;
  exp: number;
  iss: string;
  aud: string;
  token_version: number;
}

let privateKeyCache: { key: unknown; kid: string } | null = null;
let publicKeyCache: { key: unknown; kid: string } | null = null;

function _loadPrivateKey(): { key: unknown; kid: string } {
  if (privateKeyCache) return privateKeyCache;
  const cfg = getConfig();
  const path = cfg.accessTokenPrivateKeyFile;
  let pem: string;
  if (existsSync(path)) {
    pem = readFileSync(path, "utf8");
  } else {
    // Dev fallback: generate ephemeral? For tests we generate keypair if missing.
    throw new Error(`ACCESS_TOKEN_PRIVATE_KEY_FILE not found: ${path}. Provide PEM or set env for tests.`);
  }
  // kid derived from file path or header; for rotation, file may contain kid in name or we use hash
  const kid = deriveKid(pem);
  privateKeyCache = { key: null as unknown, kid };
  // lazy import async can't be sync; we import on demand via jose importPKCS8
  return privateKeyCache;
}

function _loadPublicKey(): { key: unknown; kid: string } {
  if (publicKeyCache) return publicKeyCache;
  const cfg = getConfig();
  const path = cfg.accessTokenPublicKeyFile;
  if (existsSync(path)) {
    const pem = readFileSync(path, "utf8");
    const kid = deriveKid(pem);
    publicKeyCache = { key: null as unknown, kid };
    return publicKeyCache;
  }
  throw new Error(`ACCESS_TOKEN_PUBLIC_KEY_FILE not found: ${path}`);
}

function deriveKid(pem: string): string {
  // stable short hash of PEM for kid rotation per §10.2
  const h = createHmac("sha256", "kid-derive").update(pem).digest("hex").slice(0, 8);
  return h;
}

// For testing without files, allow injecting keys via env vars or generated pairs
let testPrivateKey: string | null = null;
let testPublicKey: string | null = null;
export function __setTestKeys(privPem: string, pubPem: string): void {
  testPrivateKey = privPem;
  testPublicKey = pubPem;
  privateKeyCache = null;
  publicKeyCache = null;
}
export function __clearTestKeys(): void {
  testPrivateKey = null;
  testPublicKey = null;
  privateKeyCache = null;
  publicKeyCache = null;
}

async function getPrivateKey() {
  if (testPrivateKey) {
    return importPKCS8(testPrivateKey, "RS256");
  }
  const cfg = getConfig();
  const pem = readFileSync(cfg.accessTokenPrivateKeyFile, "utf8");
  return importPKCS8(pem, "RS256");
}

async function getPublicKey() {
  if (testPublicKey) {
    return importSPKI(testPublicKey, "RS256");
  }
  const cfg = getConfig();
  const pem = readFileSync(cfg.accessTokenPublicKeyFile, "utf8");
  return importSPKI(pem, "RS256");
}

export function hashToken(raw: string): string {
  const cfg = getConfig();
  return createHmac("sha256", cfg.tokenHashSecret).update(raw).digest("hex");
}

export function generateOpaqueToken(bytes = 32): string {
  // 256-bit = 32 bytes -> base64url no padding
  return randomBytes(bytes).toString("base64url");
}

export function generateEmailToken(): string {
  return generateOpaqueToken(32);
}
export function generateRefreshToken(): string {
  return generateOpaqueToken(32);
}
export function generateInviteToken(): string {
  return generateOpaqueToken(32);
}
export function generateRecoveryCode(): string {
  // 9 random bytes -> 18 hex chars; take 12 hex chars (A-F 0-9) -> passes /^[A-Z0-9]{4}-...$/ while remaining cryptographically random
  // Use hex uppercase (hex subset of A-Z0-9) to guarantee alphanumeric without '_' which base64url can produce
  const raw = randomBytes(9).toString("hex").toUpperCase().slice(0, 12);
  return `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}`;
}

export async function signAccessToken(params: { userId: string; sessionId: string; tokenVersion: number }): Promise<string> {
  const cfg = getConfig();
  const now = Math.floor(Date.now() / 1000);
  const exp = now + cfg.accessTokenTtlSeconds;
  const pk = await getPrivateKey();
  const kid = testPrivateKey ? deriveKid(testPrivateKey) : deriveKid(readFileSync(cfg.accessTokenPrivateKeyFile, "utf8"));
  const jwt = await new SignJWT({ token_version: params.tokenVersion } as JWTPayload)
    .setProtectedHeader({ alg: "RS256", kid })
    .setSubject(params.userId)
    .setIssuer(cfg.accessTokenIssuer)
    .setAudience(cfg.accessTokenAudience)
    .setIssuedAt(now)
    .setExpirationTime(exp)
    .setJti(params.sessionId) // use jti for sid mapping; also include sid claim for compatibility
    .sign(pk);
  // jose doesn't allow custom sid separate from jti easily; we encode sid as extra claim via payload merge
  // Workaround: we already put token_version; add sid by re-signing with sid? Simpler: include sid in payload
  // Re-sign with sid claim if not already present
  // Actually SignJWT(payload) payload includes sid if we passed it
  // Let's ensure sid is present: re-create if needed
  if (!jwt.includes("sid")) {
    const jwt2 = await new SignJWT({ sid: params.sessionId, token_version: params.tokenVersion } as JWTPayload)
      .setProtectedHeader({ alg: "RS256", kid })
      .setSubject(params.userId)
      .setIssuer(cfg.accessTokenIssuer)
      .setAudience(cfg.accessTokenAudience)
      .setIssuedAt(now)
      .setExpirationTime(exp)
      .setJti(params.sessionId)
      .sign(pk);
    return jwt2;
  }
  return jwt;
}

export async function verifyAccessToken(token: string): Promise<AccessTokenClaims & { sid: string; jti?: string }> {
  const cfg = getConfig();
  const pub = await getPublicKey();
  const { payload } = await jwtVerify(token, pub, {
    issuer: cfg.accessTokenIssuer,
    audience: cfg.accessTokenAudience,
  });
  // expect sub, sid, token_version
  if (!payload.sub || typeof payload.sub !== "string") throw new Error("Missing sub");
  const sid = (payload as Record<string, unknown>).sid as string | undefined ?? (payload.jti as string | undefined);
  if (!sid) throw new Error("Missing sid/jti");
  const token_version = (payload as Record<string, unknown>).token_version as number | undefined;
  if (token_version === undefined) throw new Error("Missing token_version");
  return {
    sub: payload.sub as string,
    sid,
    jti: payload.jti as string | undefined,
    iat: payload.iat as number,
    exp: payload.exp as number,
    iss: payload.iss as string,
    aud: (payload.aud as string) ?? cfg.accessTokenAudience,
    token_version: token_version as number,
  };
}

export function coarsenIp(ip: string | undefined): string | undefined {
  if (!ip) return undefined;
  // Store only prefix: /24 for v4, /64 for v6 per §6.1 minimized
  if (ip.includes(":")) {
    // ipv6: keep first 4 groups
    const parts = ip.split(":");
    return parts.slice(0, 4).join(":") + "::/64";
  }
  const parts = ip.split(".");
  if (parts.length === 4) return parts.slice(0, 3).join(".") + ".0/24";
  return ip;
}

export function summarizeUserAgent(ua: string | undefined): string | undefined {
  if (!ua) return undefined;
  // length-limited, sanitized
  return ua.slice(0, 200).replace(/[^ -~]/g, "");
}
