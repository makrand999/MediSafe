# Security — §10, §11

## Assumptions
- Server is `Ubuntu 24.04` `169.58.196.107` shared host, `nginx` `80` `PostgreSQL 16` `Node 22` `src/config/index.ts:1` refuses insecure prod defaults.
- No real PHI until gates pass `SERVER_IMPLEMENTATION_PLAN.md:2`.

## Controls
- **Passwords:** Argon2id `argon2` `12` min `128` max `server/src/modules/auth/argon2.ts:1` `validatePasswordStrength`, rehash on login `server/src/modules/auth/service.ts:212`.
- **Tokens:** `JWT` 10min `server/src/modules/auth/tokens.ts:120` `SignJWT RS256 kid` `token_version` `jose`, `refresh` 256-bit `randomBytes` `hashToken HMAC` `server/src/modules/auth/tokens.ts:95` rotation `previousTokenHash` `server/src/modules/auth/service.ts:350` `reuse → revoke family` `tokenVersion++`.
- **Host:** `NoNewPrivileges` `PrivateTmp` `ProtectSystem=strict` `server/deploy/systemd/medac-api.service:1` `medac` user, `127.0.0.1:3100` private `5432` `127.0.0.1` `ss -tlnp`, `UFW` `80,443` `fail2ban` checklist `server/docs/DEPLOYMENT.md:1`.
- **TLS:** `https://<domain>/medac/api/v1` `server/src/config/index.ts:1` `PUBLIC_BASE_URL https` `MUSE_SPARK_BASE_URL https` `HSTS` after cert `deploy/nginx/medac.conf`.
- **CORS:** explicit allowlist `server/src/app.ts:1` `corsAllowedOrigins` no `*` in prod `server/src/config/index.ts:191`.
- **DB:** `SCRAM` `medac|medac_migration|app|worker|backup` `migrations/0002_roles.up.sql` `REVOKE` `audit_events` `prevent_audit_mutation`, `statement_timeout` `server/src/db/index.ts:1`, parameterized queries only.
- **Logging:** `Pino` `REDACT_PATHS` `server/src/lib/logger.ts:1` `Authorization|password|medication_name|note|label_image|payloadCiphertext` no PHI, `genericFailureMessage` `server/src/modules/intelligence/safety-policy.ts:1`.
- **Rate/size:** `server/src/modules/auth/service.ts:22` `checkRateLimit` `login 5/15m` `register 5/h` `refresh 30/m` `bodyLimit 1m` `5m` for `/intelligence/` `server/src/modules/intelligence/routes.ts:92`.

## Logging redaction verified
- `journalctl -u medac-api-new | grep -i password` empty, `tests/unit/logger-redaction.test.ts:1` passes.
