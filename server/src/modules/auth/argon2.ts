/**
 * modules/auth/argon2.ts — Argon2id hashing with tunable params via env (plan §10.1)
 * Rehash on login if outdated.
 */
import * as argon2 from "argon2";
import { getConfig } from "../../config/index.js";

export interface Argon2Params {
  memoryCost: number; // KiB
  timeCost: number;
  parallelism: number;
}

export function currentArgon2Params(): Argon2Params {
  const cfg = getConfig();
  return {
    memoryCost: cfg.argon2MemoryKib,
    timeCost: cfg.argon2TimeCost,
    parallelism: cfg.argon2Parallelism,
  };
}

export async function hashPassword(plain: string): Promise<string> {
  const p = currentArgon2Params();
  return argon2.hash(plain, {
    type: argon2.argon2id,
    memoryCost: p.memoryCost,
    timeCost: p.timeCost,
    parallelism: p.parallelism,
  });
}

export async function verifyPassword(hash: string, plain: string): Promise<boolean> {
  try {
    return await argon2.verify(hash, plain);
  } catch {
    return false;
  }
}

/**
 * Detect if stored hash uses outdated params and should be rehashed.
 * Uses argon2.needsRehash when available, else parses PHC string.
 */
export function needsRehash(hash: string): boolean {
  const p = currentArgon2Params();
  try {
    // @ts-expect-error needsRehash exists in recent argon2
    if (typeof argon2.needsRehash === "function") return argon2.needsRehash(hash, { type: argon2.argon2id, memoryCost: p.memoryCost, timeCost: p.timeCost, parallelism: p.parallelism });
  } catch {
    // Fall through to PHC parsing for older argon2 library versions.
  }
  // Fallback: parse $argon2id$v=19$m=...,t=...,p=...$
  const m = hash.match(/\$m=(\d+),t=(\d+),p=(\d+)/);
  if (!m) return true; // unknown format -> rehash
  const mem = Number(m[1]);
  const t = Number(m[2]);
  const par = Number(m[3]);
  return mem !== p.memoryCost || t !== p.timeCost || par !== p.parallelism;
}

export function validatePasswordStrength(password: string): { valid: boolean; reason?: string } {
  if (password.length < 12) return { valid: false, reason: "Password must be at least 12 characters" };
  if (password.length > 128) return { valid: false, reason: "Password must be at most 128 characters" };
  // No arbitrary char class rules per §10.1; only length.
  // Breached-password check would be k-anonymity here; stubbed in service.
  return { valid: true };
}
