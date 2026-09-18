import { drizzle as drizzlePg } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import * as schema from './schema.js';

export function createPool(connectionString: string): Pool {
  return new Pool({
    connectionString,
    // Keep small pool for local dev; production tuning via env
    min: Number(process.env.DATABASE_POOL_MIN ?? 2),
    max: Number(process.env.DATABASE_POOL_MAX ?? 10),
    statement_timeout: Number(process.env.DATABASE_STATEMENT_TIMEOUT_MS ?? 30_000),
  });
}

export function createDb(pool: Pool) {
  return drizzlePg(pool, { schema });
}

export type MedacDb = ReturnType<typeof createDb>;
export { schema };
