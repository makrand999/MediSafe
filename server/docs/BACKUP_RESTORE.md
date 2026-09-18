# Backup & Restore — Phase 11 §19

## Design
- Daily `pg_dump` logical `postgres://medac_backup@localhost/medac | gzip | openssl enc -aes-256-cbc -pbkdf2 -pass file:/opt/medac/secrets/backup.key > /var/backups/medac-$(date +%F).sql.gz.enc`
- Off-site `rclone` to restricted bucket `medac-backups` with retention 7d/4w/12m.
- WAL archiving optional for <24h RPO once usage grows.
- Monitor: `scripts/backup-check.sh` cron `0 2 * * *` checks size/age, alerts if >28h old.

## Verify
- Do not store dumps in repo or web root.
- `openssl enc -d ... | gunzip | psql` restores to isolated `medac_restore` on staging.
- Monthly drill: provision empty PG, restore, `SELECT count(*) FROM schema_migrations`, decrypt `FIELD_ENCRYPTION` test row, record RTO.

## Targets (pre-launch)
- RPO 24h, RTO 4h.

## Restore drill steps (isolated)
```bash
createdb medac_restore
openssl enc -d -aes-256-cbc -pbkdf2 -pass file:/opt/medac/secrets/backup.key -in /var/backups/medac-2026-08-18.sql.gz.enc | gunzip | psql medac_restore
psql medac_restore -c "SELECT * FROM schema_migrations ORDER BY id DESC LIMIT 5;"
# verify decrypt of encrypted field with current key
```
Destroy `medac_restore` after.

See `scripts/backup-check.sh`.
