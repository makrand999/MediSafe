-- Down for 0002_roles — remove role-specific triggers and reset privileges
DROP TRIGGER IF EXISTS trg_audit_events_no_update ON audit_events;
DROP FUNCTION IF EXISTS prevent_audit_mutation() CASCADE;

-- Reset default privileges (no-op if role setup was minimal)
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public REVOKE ALL ON TABLES FROM medac_app;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public REVOKE ALL ON TABLES FROM medac_worker;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public REVOKE ALL ON TABLES FROM medac_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE medac_migration IN SCHEMA public REVOKE ALL ON TABLES FROM medac_readonly;

-- Revoke schema usage (retain roles themselves)
REVOKE USAGE ON SCHEMA public FROM medac_app, medac_worker, medac_backup, medac_readonly;
REVOKE SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM medac_app, medac_worker;
REVOKE SELECT ON ALL TABLES IN SCHEMA public FROM medac_backup, medac_readonly;

-- Roles are retained (DROP ROLE would fail if connections exist); uncomment to drop:
-- DROP ROLE IF EXISTS medac_readonly;
-- DROP ROLE IF EXISTS medac_backup;
-- DROP ROLE IF EXISTS medac_worker;
-- DROP ROLE IF EXISTS medac_app;
-- DROP ROLE IF EXISTS medac_migration;
