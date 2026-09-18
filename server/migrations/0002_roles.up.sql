-- Roles & grants — defense in depth per §11.3, §7.3, §6.11
-- Four DB roles: migration, runtime API, worker, backup (read-only)
-- Runtime/worker cannot bypass audit append-only: REVOKE UPDATE/DELETE on audit_events.
-- This file is idempotent (CREATE ROLE IF NOT EXISTS via DO block).

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'medac_migration') THEN
    CREATE ROLE medac_migration WITH LOGIN PASSWORD 'change_me_migration_32chars_min' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'medac_app') THEN
    CREATE ROLE medac_app WITH LOGIN PASSWORD 'change_me_app_32chars_min' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'medac_worker') THEN
    CREATE ROLE medac_worker WITH LOGIN PASSWORD 'change_me_worker_32chars_min' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'medac_backup') THEN
    CREATE ROLE medac_backup WITH LOGIN PASSWORD 'change_me_backup_32chars_min' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'medac_readonly') THEN
    CREATE ROLE medac_readonly WITH LOGIN PASSWORD 'change_me_readonly_32chars_min' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
END $$;

-- Ensure migration role owns schema objects (grant prior to ownership transfer if needed)
-- When running migrations via the migration role, objects are owned by that role already.
-- The GRANT statements below are safe to run as superuser or migration role.

GRANT USAGE ON SCHEMA public TO medac_app, medac_worker, medac_backup, medac_readonly;

-- Runtime app: full DML on business tables, but NOT on audit_events beyond INSERT/SELECT
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO medac_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO medac_app;
-- Explicitly revoke UPDATE/DELETE on audit_events for runtime — append-only
REVOKE UPDATE, DELETE ON audit_events FROM medac_app;

-- Worker: needs to claim jobs (SELECT + UPDATE on outbox_events/job_runs), and read/write business tables
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO medac_worker;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO medac_worker;
REVOKE UPDATE, DELETE ON audit_events FROM medac_worker;
-- Worker may only INSERT into audit_events (via app code), not mutate history

-- Backup: read-only
GRANT SELECT ON ALL TABLES IN SCHEMA public TO medac_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO medac_backup;

-- Readonly: for analytics/support
GRANT SELECT ON ALL TABLES IN SCHEMA public TO medac_readonly;

-- Default privileges for future tables (objects created by migration role)
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO medac_app;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO medac_worker;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public GRANT SELECT ON TABLES TO medac_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public GRANT SELECT ON TABLES TO medac_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO medac_app, medac_worker;

-- Re-apply append-only after default privileges: create event trigger or rule (simpler: revoke via default privileges handling is above)
-- Also add explicit trigger to prevent UPDATE/DELETE via any role except migration (defense-in-depth even if GRANT misconfigured)
CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
  -- Allow migration role (object owner) to truncate in emergencies via superuser, but prevent app/worker updates
  IF current_user IN ('medac_app','medac_worker','medac_backup','medac_readonly') THEN
    RAISE EXCEPTION 'audit_events is append-only — UPDATE/DELETE not permitted for %', current_user;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_events_no_update ON audit_events;
CREATE TRIGGER trg_audit_events_no_update BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

-- Connection limits and statement timeout (applied per role; values are defaults, tune via ALTER ROLE in production)
-- Keep timeouts modest to avoid long-running queries starving the API
ALTER ROLE medac_app SET statement_timeout = '30s';
ALTER ROLE medac_worker SET statement_timeout = '60s';
ALTER ROLE medac_backup SET statement_timeout = '300s';
ALTER ROLE medac_readonly SET statement_timeout = '30s';

-- Ensure pgcrypto was available (migration role)
COMMENT ON EXTENSION "pgcrypto" IS 'Provides gen_random_uuid() — app prefers UUIDv7 via server-lib/uuid';

-- Record role setup in migrations tracking for audit
-- (no additional table needed; schema_migrations already tracks files)
