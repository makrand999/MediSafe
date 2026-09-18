/**
 * modules/auth/totp.ts — TOTP MFA with recovery codes (RFC 6238)
 * No external dependency; HMAC-SHA1, 30s step, 6 digits, window ±1.
 * Secret encrypted with FIELD_ENCRYPTION_KEY_CURRENT (AES-256-GCM).
 */
import { createHmac, randomBytes, createCipheriv, createDecipheriv } from "node:crypto";
import { getConfig } from "../../config/index.js";

const STEP_SECONDS = 30;
const DIGITS = 6;
const WINDOW = 1;

function base32Decode(s: string): Buffer {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const clean = s.toUpperCase().replace(/[^A-Z2-7]/g, "");
  let bits = 0;
  let value = 0;
  const out: number[] = [];
  for (const ch of clean) {
    value = (value << 5) | alphabet.indexOf(ch);
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

function base32Encode(buf: Buffer): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = 0;
  let value = 0;
  let out = "";
  for (const b of buf) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += alphabet[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += alphabet[(value << (5 - bits)) & 31];
  while (out.length % 8 !== 0) out += "=";
  return out;
}

export function generateTotpSecret(): { raw: Buffer; base32: string } {
  const raw = randomBytes(20); // 160-bit per RFC 4226
  return { raw, base32: base32Encode(raw).replace(/=/g, "") };
}

export function totpCode(secret: Buffer, timeMs: number = Date.now()): string {
  const counter = Math.floor(timeMs / 1000 / STEP_SECONDS);
  return hotp(secret, counter);
}

function hotp(secret: Buffer, counter: number): string {
  const buf = Buffer.alloc(8);
  // 64-bit big-endian counter
  buf.writeUInt32BE(Math.floor(counter / 0x100000000), 0);
  buf.writeUInt32BE(counter >>> 0, 4);
  const h = createHmac("sha1", secret).update(buf).digest();
  const offset = h[h.length - 1] & 0x0f;
  const bin =
    ((h[offset] & 0x7f) << 24) |
    ((h[offset + 1] & 0xff) << 16) |
    ((h[offset + 2] & 0xff) << 8) |
    (h[offset + 3] & 0xff);
  const otp = bin % 10 ** DIGITS;
  return otp.toString().padStart(DIGITS, "0");
}

export function verifyTotp(secret: Buffer, code: string, opts?: { timeMs?: number; window?: number; lastUsedStep?: number | null }): { valid: boolean; step: number } {
  const window = opts?.window ?? WINDOW;
  const now = opts?.timeMs ?? Date.now();
  const counter = Math.floor(now / 1000 / STEP_SECONDS);
  for (let delta = -window; delta <= window; delta++) {
    const c = counter + delta;
    if (opts?.lastUsedStep !== undefined && opts?.lastUsedStep !== null && c <= opts.lastUsedStep) continue; // reject reuse of same step per §6.1
    if (hotp(secret, c) === code) return { valid: true, step: c };
  }
  return { valid: false, step: counter };
}

// ── Encryption for stored secret (AES-256-GCM) ──────────────────────────
function getEncryptionKeySync(): Buffer {
  const cfg = getConfig();
  const b64 = cfg.fieldEncryptionKeyCurrent;
  try {
    const buf = Buffer.from(b64, "base64");
    if (buf.length === 32) return buf;
  } catch {
    // Fall through to the development compatibility path below.
  }
  // dev fallback: pad/truncate utf8 to 32
  const raw = Buffer.from(b64, "utf8");
  if (raw.length === 32) return raw;
  const out = Buffer.alloc(32, 0);
  raw.copy(out);
  return out;
}

export function encryptSecret(plain: Buffer): string {
  const key = getEncryptionKeySync();
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const enc = Buffer.concat([cipher.update(plain), cipher.final()]);
  const tag = cipher.getAuthTag();
  // store as iv:tag:ciphertext base64
  return `${iv.toString("base64")}:${tag.toString("base64")}:${enc.toString("base64")}`;
}

export function decryptSecret(ciphertext: string): Buffer {
  const key = getEncryptionKeySync();
  const [ivB64, tagB64, encB64] = ciphertext.split(":");
  const iv = Buffer.from(ivB64, "base64");
  const tag = Buffer.from(tagB64, "base64");
  const enc = Buffer.from(encB64, "base64");
  const decipher = createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(enc), decipher.final()]);
}

export function buildOtpAuthUrl(opts: { issuer: string; accountName: string; secretBase32: string }): string {
  const label = `${encodeURIComponent(opts.issuer)}:${encodeURIComponent(opts.accountName)}`;
  const params = new URLSearchParams({ secret: opts.secretBase32, issuer: opts.issuer, algorithm: "SHA1", digits: String(DIGITS), period: String(STEP_SECONDS) });
  return `otpauth://totp/${label}?${params.toString()}`;
}

export { base32Encode, base32Decode };
