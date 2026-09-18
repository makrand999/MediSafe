#!/usr/bin/env tsx
/**
 * create-admin.ts — interactive admin creation helper.
 * For development: creates or promotes a user to active status.
 * Usage: DATABASE_URL=... npm run create-admin -- --email admin@example.com --password 'StrongPass1234!'
 */
import pg from "pg";
import { createHash, randomUUID } from "node:crypto";

const connectionString = process.env.DATABASE_URL ?? "postgres://medac:medac@localhost:5432/medac";

function arg(name: string, fallback?: string): string | undefined {
  const idx = process.argv.indexOf(`--${name}`);
  if (idx !== -1 && process.argv[idx + 1]) return process.argv[idx + 1];
  return fallback;
}

function devHash(password: string): string {
  return `argon2:dev:${createHash("sha256").update(password).digest("hex")}`;
}

async function main(): Promise<void> {
  const email = arg("email") ?? "admin@example.com";
  const password = arg("password") ?? "AdminPass1234!";
  const emailNormalized = email.trim().toLowerCase();
  const client = new pg.Client({ connectionString });
  await client.connect();
  try {
    const existing = await client.query("SELECT id FROM users WHERE email_normalized=$1", [emailNormalized]);
    let userId: string;
    if (existing.rows.length > 0) {
      userId = existing.rows[0].id as string;
      await client.query("UPDATE users SET email_verified_at=now(), status='active', password_hash=$2 WHERE id=$1", [
        userId,
        devHash(password),
      ]);
      console.log(`[create-admin] updated existing user ${email} (${userId})`);
    } else {
      userId = randomUUID();
      await client.query(
        "INSERT INTO users (id, email_normalized, email_display, password_hash, email_verified_at, status) VALUES ($1,$2,$3,$4, now(), 'active')",
        [userId, emailNormalized, email, devHash(password)],
      );
      await client.query("INSERT INTO user_security (user_id) VALUES ($1) ON CONFLICT DO NOTHING", [userId]);
      console.log(`[create-admin] created user ${email} (${userId})`);
    }
    console.log(`[create-admin] password: ${password} (change immediately in production)`);
  } finally {
    await client.end();
  }
}

void main();
