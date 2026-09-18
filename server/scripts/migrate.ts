#!/usr/bin/env tsx
/**
 * Migration runner — applies *.up.sql in server/migrations/ in lexical order.
 * Tracks applied files in `schema_migrations` table (idempotent).
 * Supports --down to roll back last migration (uses corresponding .down.sql).
 *
 * Usage:
 *   DATABASE_URL=postgres://... npm run db:migrate
 *   DATABASE_URL=postgres://... npm run db:migrate:down
 */
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname } from 'node:path';
import pg from 'pg';

const __dirname = dirname(fileURLToPath(import.meta.url));
const migrationsDir = join(__dirname, '..', 'migrations');
const connectionString = process.env.DATABASE_URL ?? process.env.DATABASE_MIGRATION_URL ?? 'postgres://medac:medac@localhost:5432/medac';
const isDown = process.argv.includes('--down');

async function getMigrations(dir: string) {
  const files = await readdir(dir);
  const ups = files.filter((f) => f.endsWith('.up.sql')).sort();
  return ups;
}

async function ensureMigrationsTable(client: pg.Client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      id serial PRIMARY KEY,
      filename text NOT NULL UNIQUE,
      applied_at timestamptz NOT NULL DEFAULT now()
    );
  `);
}

async function appliedSet(client: pg.Client): Promise<Set<string>> {
  const res = await client.query<{ filename: string }>('SELECT filename FROM schema_migrations');
  return new Set(res.rows.map((r) => r.filename));
}

async function migrateUp() {
  const client = new pg.Client({ connectionString });
  await client.connect();
  try {
    await ensureMigrationsTable(client);
    const applied = await appliedSet(client);
    const ups = await getMigrations(migrationsDir);

    if (ups.length === 0) {
      console.log('[migrate] no migration files found');
      return;
    }

    for (const file of ups) {
      if (applied.has(file)) {
        console.log(`[migrate] skip already applied: ${file}`);
        continue;
      }
      const full = join(migrationsDir, file);
      const sql = await readFile(full, 'utf8');
      console.log(`[migrate] applying ${file} ...`);
      await client.query('BEGIN');
      try {
        await client.query(sql);
        await client.query('INSERT INTO schema_migrations (filename) VALUES ($1)', [file]);
        await client.query('COMMIT');
        console.log(`[migrate] applied ${file}`);
      } catch (e) {
        await client.query('ROLLBACK');
        console.error(`[migrate] failed ${file}:`, e);
        throw e;
      }
    }
    console.log('[migrate] up complete');
  } finally {
    await client.end();
  }
}

async function migrateDown() {
  const client = new pg.Client({ connectionString });
  await client.connect();
  try {
    await ensureMigrationsTable(client);
    const res = await client.query<{ filename: string }>(
      'SELECT filename FROM schema_migrations ORDER BY applied_at DESC, id DESC LIMIT 1',
    );
    if (res.rows.length === 0) {
      console.log('[migrate:down] no applied migrations to roll back');
      return;
    }
    const last = res.rows[0]!.filename;
    const downFile = last.replace(/\.up\.sql$/, '.down.sql');
    const downPath = join(migrationsDir, downFile);
    if (!existsSync(downPath)) {
      console.error(`[migrate:down] down file missing for ${last}: ${downPath}`);
      process.exit(1);
    }
    const sql = await readFile(downPath, 'utf8');
    console.log(`[migrate:down] rolling back ${last} via ${downFile} ...`);
    await client.query('BEGIN');
    try {
      await client.query(sql);
      await client.query('DELETE FROM schema_migrations WHERE filename = $1', [last]);
      await client.query('COMMIT');
      console.log(`[migrate:down] rolled back ${last}`);
    } catch (e) {
      await client.query('ROLLBACK');
      console.error(`[migrate:down] failed:`, e);
      throw e;
    }
  } finally {
    await client.end();
  }
}

if (isDown) {
  await migrateDown();
} else {
  await migrateUp();
}
