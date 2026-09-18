# Database Roles — Medac (§11.3, §7.3, §6.11)

Four PostgreSQL roles separate concerns. Runtime and worker cannot mutate audit history.

## Roles

| Role | Login | Purpose | Owns objects? | Can DDL? | Default `DATABASE_*_URL` |
|------|-------|---------|---------------|----------|--------------------------|
| `medac_migration` | yes | Runs `npm run db:migrate` (`migrations/*.up.sql`). Sole owner of tables, enums, indexes, triggers. | **Yes** | **Yes** (migrations only) | `DATABASE_MIGRATION_URL` or `DATABASE_URL` |
| `medac_app` | yes | Fastify API (`medac-api` systemd service). Normal request handling. | No | No | `DATABASE_URL` |
| `medac_worker` | yes | Background jobs (`medac-worker`). Outbox consumer, occurrence generation, alert delivery, export, proposal expiry. | No | No | `DATABASE_WORKER_URL` or `DATABASE_URL` |
| `medac_backup` | yes | `pg_dump` / WAL archiving (encrypted, off-host). No writes. | No | No | `DATABASE_BACKUP_URL` |
| `medac_readonly` | yes | Optional analytics/support (read-only). | No | No | — |

All roles authenticate with `SCRAM-SHA-256-PLUS`, passwords stored outside the repo (systemd `EnvironmentFile`, vault, or cloud secret manager). Never commit passwords; rotate per §11.1 before production.

## Privilege Model

```sql
-- Schema usage
GRANT USAGE ON SCHEMA public TO medac_app, medac_worker, medac_backup, medac_readonly;

-- Business tables: runtime DML allowed
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO medac_app, medac_worker;

-- Backup/readonly: read-only
GRANT SELECT ON ALL TABLES IN SCHEMA public TO medac_backup, medac_readonly;

-- Sequences
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO medac_app, medac_worker;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO medac_backup, medac_readonly;

-- Future tables (objects created by medac_migration)
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO medac_app, medac_worker;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public
  GRANT SELECT ON TABLES TO medac_backup, medac_readonly;
```

Applied by `migrations/0002_roles.up.sql` (idempotent `DO` block creates roles if missing).

## Append-Only Audit (`audit_events`)

Requirement §6.11: application role has **no UPDATE/DELETE** on `audit_events`. Enforcement is two-layer:

**1. GRANT revocation (primary)**
```sql
REVOKE UPDATE, DELETE ON audit_events FROM medac_app, medac_worker;
```

**2. Trigger defense-in-depth (catches misgranted privilege)**
```sql
CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
  IF current_user IN ('medac_app','medac_worker','medac_backup','medac_readonly') THEN
    RAISE EXCEPTION 'audit_events is append-only — UPDATE/DELETE not permitted for %', current_user;
  END IF;
  RETURN NULL;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_no_update
  BEFORE UPDATE OR DELETE ON audit_events
  FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
```

Only `medac_migration` (owner) and superuser can `TRUNCATE` during emergency maintenance with an audit entry and approval. Runtime code path is INSERT-only and must include `metadata_json` with allowlisted keys only (no PHI).

## Other Hardening

- **Connection limits & timeouts** (tune per environment):
  ```sql
  ALTER ROLE medac_app     SET statement_timeout = '30s';
  ALTER ROLE medac_worker  SET statement_timeout = '60s';
  ALTER ROLE medac_backup  SET statement_timeout = '300s';
  ALTER ROLE medac_readonly SET statement_timeout = '30s';
  ```
  Add `connection_limit` via `ALTER ROLE ... CONNECTION LIMIT 20;` if needed, and set PostgreSQL `max_connections` accordingly.

- **PostgreSQL bind**: `postgresql.conf` — `listen_addresses = 'localhost'` / private socket only; `pg_hba.conf` — `host medac medac_app 127.0.0.1/32 scram-sha-256`, no public CIDR.

- **Encrypted at rest**: disk/volume encryption (LUKS/EBS encryption) + `pgcrypto` field-level ciphertext for TOTP secrets, push tokens, AI proposal payloads, symptom notes.

- **WAL / backups**: Use `medac_backup` credential; backups encrypted before/off-host with separate key (§19).

- **RLS alternative**: MVP uses repository-layer patient scoping (centralized `patientMembership` check) plus §7.1 request flow. If RLS is enabled later, test that `medac_app` cannot `SET ROLE` to bypass it; keep migration role narrow.

## Startup Checks

`src/config` must refuse startup if:

- `DATABASE_URL` missing or uses `postgres://postgres` superuser in production,
- migration role password equals app/worker password,
- `postgres` port is publicly reachable (smoke test).

## Rotation & Incident

See `docs/INCIDENT_RESPONSE.md`. On suspected credential leak: rotate affected role `ALTER ROLE ... WITH PASSWORD '...'`, restart `medac-api`/`medac-worker`, invalidate `auth_sessions` for token_version bump, verify `audit_events` has no UPDATE/DELETE entries, and record recovery time.
