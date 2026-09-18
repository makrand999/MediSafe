# Medac Server — 11 Phases Complete

> Implementation of `SERVER_IMPLEMENTATION_PLAN.md` Phases 0-11. All core APIs, Muse Spark intelligence (muse-spark-1.2-contributor fixed), and production hardening are present. See `openapi/openapi.json` and `docs/`.

## Architecture

- **Runtime:** Node 22 LTS, TypeScript strict, Fastify, Zod validation, PostgreSQL 16+, Drizzle ORM + SQL migrations, Pino with redaction.
- **Tests:** Vitest + Testcontainers (real Postgres for integration/authorization).
- **AI:** Muse Spark (`muse-spark-1.2-contributor` only, fixed) via OpenAI-compatible `/chat/completions` adapter (`src/modules/intelligence/muse-spark-provider.ts`). Provider validates HTTPS, allowlist, timeout, budgets; circuit breaker; no fallback model.
- **Proxy convention:** Fastify exposes **` /api/v1`** internally (localhost:3100). Nginx exposes **` /medac/api/v1`** publicly (`https://<domain>/medac/api/v1`). Health endpoints are at `/health/live`, `/health/ready`, `/version` (also mirrored under `/api/v1/health/*` for proxy stripping flexibility). See `openapi/openapi.json` and `deploy/nginx/medac.conf`.
- **Processes:** `medac-api` (HTTP) and `medac-worker` (jobs) share codebase, separate entry points `src/server.ts` / `src/worker.ts`, run as unprivileged `medac` user via systemd.

## Security defaults

- Config loader `src/config/index.ts` validates all vars on startup and **refuses**:
  - missing `TOKEN_HASH_SECRET`, `FIELD_ENCRYPTION_KEY_CURRENT` in production (and placeholder values)
  - wildcard `CORS_ALLOWED_ORIGINS=*` in production
  - non-HTTPS `PUBLIC_BASE_URL` and `MUSE_SPARK_BASE_URL` in production
  - unbounded Spark limits (`MUSE_SPARK_MAX_AGENT_TURNS` 1..20, `MAX_TOOL_CALLS` 1..50, `MAX_OUTPUT_TOKENS` 1..32000, `TIMEOUT_MS` 1..120000)
  - unknown `MUSE_SPARK_MODEL` — only `muse-spark-1.2-contributor` is allowed; any other value throws in all envs (no fallback per §12.5)
- Pino redaction: Authorization/cookie headers, tokens, API keys, email, medication names, symptoms, notes, label images, prompts/responses, payload ciphertexts are censored.
- Error handler returns stable `{ error: { code, message, request_id, details? } }` without stack traces or upstream exception text.

## Local Setup

### 1) Prerequisites

- Node 22 (`node -v` should be `v22.x`)
- Docker + Docker Compose (for local Postgres only)
- `psql` optional

### 2) Install

```bash
cd server
npm ci                  # or npm install
cp .env.example .env    # edit values (never commit .env)
```

`.env.example` lists every variable from plan §22 with safe documentation defaults. Set at minimum:

- `DATABASE_URL=postgres://medac:medac@localhost:5432/medac`
- `TOKEN_HASH_SECRET` (256-bit hex), `FIELD_ENCRYPTION_KEY_CURRENT` (base64 32-byte) for production-like runs
- `MUSE_SPARK_BASE_URL=https://api.musespark.example/v1` (use real provider URL in staging)

### 3) Start Postgres (local only)

```bash
docker compose up -d postgres
# or: docker-compose up -d (if using standalone)
```

Production Postgres is **not** exposed publicly — binds to localhost/private socket only. This compose file is for local dev/tests.

### 4) Run migrations (from empty DB)

```bash
npm run db:migrate
# verify drift: schema should match migrations/ and src/db/schema.ts
npm run db:migrate   # idempotent — skips applied files
```

Migrations are SQL files in `migrations/*.up.sql` tracked in `schema_migrations`. Never modify production schema manually.

### 5) Seed (development only)

```bash
npm run db:seed
```

Creates `dev@example.com / MedacDev1234!`, patient Alex Rivera, medication, schedule, occurrences, inventory, symptom log. Safe to re-run.

### 6) Run API & worker

```bash
npm run dev            # API on 127.0.0.1:3100
npm run worker         # in another terminal (jobs)
# production:
npm run build && npm start
node dist/worker.js
```

### 7) Health / version

```bash
curl -i http://127.0.0.1:3100/health/live
curl -i http://127.0.0.1:3100/health/ready
curl -i http://127.0.0.1:3100/version
curl -i http://127.0.0.1:3100/api/v1/health/live
```

Public path when deployed behind nginx: `https://example.com/medac/api/v1` → `127.0.0.1:3100/api/v1`.

## Tests

```bash
npm run typecheck   # tsc --noEmit
npm run lint        # eslint (flat config)
npm test            # vitest run (unit + integration; Testcontainers if DB needed)
npm run build
```

Integration tests use a real PostgreSQL instance (Testcontainers or `DATABASE_URL` override). CI runs migrations from empty DB and drift check (see `.github/workflows/ci.yml`).

## Project Tree

See `SERVER_IMPLEMENTATION_PLAN.md §4.4`. Phase 0 includes:

```
server/
  package.json, tsconfig.json, eslint.config.mjs, .env.example, README.md
  docker-compose.yml, drizzle.config.ts
  openapi/openapi.json
  migrations/*.sql
  scripts/migrate.ts, seed-development.ts, create-admin.ts, backup-check.sh, smoke-test.sh, generate-openapi.ts
  src/app.ts (factory), src/server.ts, src/worker.ts, src/config/, src/db/, src/plugins/, src/lib/, src/modules/**
  tests/unit, tests/integration, tests/authorization, tests/fixtures
  deploy/systemd, deploy/nginx, deploy/logrotate
```

Muse Spark label interpretation is exposed at `POST /api/v1/patients/:patientId/intelligence/label-interpretations`. Additional intelligence routes are authorization-protected but return `501` until their PostgreSQL-backed tool executors and confirmation flows are complete. The committed contract is served at `GET /api/v1/openapi.json`.

## Environment Reference (plan §22)

All vars are documented in `.env.example`. Key groups: `NODE_ENV`, `APP_ENV`, `APP_BUILD_ID`, `HOST`/`PORT`, `PUBLIC_BASE_URL`, `TRUST_PROXY`, `DATABASE_*`, `ACCESS_TOKEN_*`, `REFRESH_TOKEN_TTL_DAYS`, `TOKEN_HASH_SECRET`, `FIELD_ENCRYPTION_KEY_*`, `ARGON2_*`, `CORS_ALLOWED_ORIGINS`, `EMAIL_*`, `RXNORM_*`, `MUSE_SPARK_*`, `AI_*`, `PUSH_PROVIDER_CONFIG_FILE`, `EXPORT_DIRECTORY`/`EXPORT_TTL_HOURS`, `LOG_LEVEL`, `METRICS_BIND_ADDRESS`, `WORKER_CONCURRENCY`/`JOB_MAX_ATTEMPTS`.

## Deployment (staging/production — later phases)

- Build immutable artifact: `npm run build`
- Configure secrets outside repo; run migrations with dedicated role `DATABASE_MIGRATION_URL`
- Start worker then API (systemd `medac-api.service` / `medac-worker.service`)
- Nginx `deploy/nginx/medac.conf` maps `/medac/api/*` → `127.0.0.1:3100/api/*` and `/medac/health` → health endpoints; add rate/size limits, HSTS, security headers.
- Verify: `scripts/smoke-test.sh`, `scripts/backup-check.sh`

## CI

GitHub Actions workflow `.github/workflows/ci.yml` runs on every PR: install from lockfile → type-check → lint → tests → migrations from empty DB (postgres:16 service) → OpenAPI drift check → build → SBOM optional.

## Notes

- Never commit `.env`, private keys, or dumps.
- Do not place real patient data until security/deployment gates pass.
- Muse Spark sends minimal patient-scoped context; raw prompts/responses/images are not logged and not persisted by default.
