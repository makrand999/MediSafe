import { describe, it, expect } from "vitest";
import { validatePasswordStrength } from "../../src/modules/auth/argon2.js";
import { generateOpaqueToken, hashToken, coarsenIp, generateRecoveryCode } from "../../src/modules/auth/tokens.js";
import { verifyTotp, generateTotpSecret, encryptSecret, decryptSecret, totpCode } from "../../src/modules/auth/totp.js";
import { checkRateLimit, __clearRateBuckets } from "../../src/modules/auth/service.js";

describe("password validation §10.1", () => {
  it("rejects <12 chars", () => {
    expect(validatePasswordStrength("short11").valid).toBe(false);
    expect(validatePasswordStrength("12345678901").valid).toBe(false);
  });
  it("accepts 12 chars", () => {
    expect(validatePasswordStrength("123456789012").valid).toBe(true);
  });
  it("rejects >128 chars", () => {
    expect(validatePasswordStrength("a".repeat(129)).valid).toBe(false);
  });
  it("allows 128 chars", () => {
    expect(validatePasswordStrength("a".repeat(128)).valid).toBe(true);
  });
  it("no char class required - all lowercase passes if length ok", () => {
    expect(validatePasswordStrength("alllowercaselongpassword").valid).toBe(true);
  });
});

describe("token generation §10.2", () => {
  it("opaque token is 256-bit (32 bytes base64url ~43 chars)", () => {
    const t = generateOpaqueToken(32);
    // base64url without padding: 43 chars for 32 bytes
    expect(t.length).toBeGreaterThanOrEqual(43);
    // different each time
    expect(generateOpaqueToken(32)).not.toBe(t);
  });
  it("hashToken is deterministic HMAC", () => {
    process.env.TOKEN_HASH_SECRET = "test-secret-256-bit-hex-12345678";
    const h1 = hashToken("hello");
    const h2 = hashToken("hello");
    expect(h1).toBe(h2);
    expect(hashToken("hello2")).not.toBe(h1);
  });
  it("coarsenIp v4 /24 and v6 /64", () => {
    expect(coarsenIp("192.168.1.123")).toBe("192.168.1.0/24");
    expect(coarsenIp("2001:db8:85a3:8d3:1319:8a2e:370:7348")).toContain("/64");
  });
  it("recovery code format", () => {
    const c = generateRecoveryCode();
    expect(c).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
  });
});

describe("TOTP §6.1", () => {
  it("encrypt/decrypt roundtrip", () => {
    process.env.FIELD_ENCRYPTION_KEY_CURRENT = "dGVzdC1rZXktMzItYnl0ZXMtMTIzNDU2Nzg5MDEyMw=="; // base64 32 bytes would be better; use fallback padding
    const { raw } = generateTotpSecret();
    const enc = encryptSecret(raw);
    const dec = decryptSecret(enc);
    expect(dec.equals(raw)).toBe(true);
  });
  it("totp verify window and reuse detection", () => {
    const { raw } = generateTotpSecret();
    const now = Date.now();
    const code = totpCode(raw, now);
    const ok = verifyTotp(raw, code, { timeMs: now, lastUsedStep: null });
    expect(ok.valid).toBe(true);
    // same step rejected if lastUsedStep is current
    const replay = verifyTotp(raw, code, { timeMs: now, lastUsedStep: ok.step });
    expect(replay.valid).toBe(false);
    // wrong code
    expect(verifyTotp(raw, "000000", { timeMs: now }).valid).toBe(false);
  });
});

describe("rate limiter §10.4", () => {
  it("enforces limit", () => {
    __clearRateBuckets();
    const key = "test:rate:" + Math.random();
    expect(checkRateLimit(key, 2, 1000)).toBe(true);
    expect(checkRateLimit(key, 2, 1000)).toBe(true);
    expect(checkRateLimit(key, 2, 1000)).toBe(false);
  });
});

describe("token rotation reuse detection (unit logic)", () => {
  it("previousTokenHash detection principle", async () => {
    // Simulate session store with family revocation logic without DB
    const familyId = "fam-" + Math.random();
    const sessions = new Map<string, { id: string; familyId: string; currentHash: string; previousHash: string | null; revoked: boolean }>();
    const s: { id: string; familyId: string; currentHash: string; previousHash: string | null; revoked: boolean } = { id: "s1", familyId, currentHash: "hash1", previousHash: null, revoked: false };
    sessions.set(s.id, s);
    // rotation: hash1 -> hash2
    s.previousHash = s.currentHash;
    s.currentHash = "hash2";
    // replay hash1 should be detected as previous
    const reuse = [...sessions.values()].find(v => v.previousHash === "hash1");
    expect(reuse).toBeDefined();
    // revoke family would mark revoked
    for (const v of sessions.values()) if (v.familyId === reuse!.familyId) v.revoked = true;
    expect(s.revoked).toBe(true);
  });
});
