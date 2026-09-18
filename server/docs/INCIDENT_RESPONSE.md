# Incident Response — §18, §25

## Detection
- Alerts: `API 5xx` `queue lag` `certificate expiry` `backup stale` `Muse Spark outage` `spend>threshold` `server/src/plugins/metrics.ts:1`.

## Containment
- `systemctl stop medac-api-new` `nginx` `return 503` for `/medac/api/v1` keep `/rebuildx` `200`.
- Rotate `TOKEN_HASH_SECRET` `FIELD_ENCRYPTION_KEY_CURRENT` `MUSE_SPARK_API_KEY` `ACCESS_TOKEN_PRIVATE_KEY_FILE` via `kid` `server/src/modules/auth/tokens.ts:55`.

## Notification
- `audit_events` `server/src/db/schema.ts:796` no PHI, `outbox_events` `notificationDeliveries` `failed`.

## Recovery
- Restore from `BACKUP_RESTORE.md` to isolated `medac_restore` verify `schema_migrations` `openssl enc -d`.
- `scripts/smoke-test.sh` `health/ready` `auth` `medication` `schedule` `dose` `adherence` `label` `conversation` `proposal confirm` `logs redacted`.

## Postmortem
- `docs/DATA_RETENTION.md` `RPO/RTO` `incident` `server/docs/DEPLOYMENT.md:1`.
