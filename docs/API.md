# Medac Mobile API — Server `169.58.196.107:3100` (`muse-spark-1.2-contributor` fixed)

> **Base URL (public via nginx):** `http://169.58.196.107/medac/api/v1`
> Internally Fastify serves `/api/v1` — nginx strips the `/medac` prefix (`rewrite ^/medac(/.*)$ $1` → `proxy_pass 127.0.0.1:3100`).
> Health aliases also available without the `/api/v1` prefix: `/medac/health/live`, `/medac/health/ready`.
> Direct Fastify (bypass nginx, localhost only): `http://127.0.0.1:3100`.
> **Status:** HTTP only (no TLS on :443 yet), legacy Express on `127.0.0.1:3002` still serves `/medac` — mobile must use `/medac/api/v1` only. `openapi/openapi.json` on disk is stale (5 paths) — this doc is the source of truth.

Last verified live: `2026-08-18` build `202608181735`, `muse-spark-1.2-contributor`.

---

## 1. Conventions

### 1.1 Headers

| Header | Required | Notes |
|---|---|---|
| `Authorization: Bearer <access_token>` | for all `auth`-gated routes | RS256 JWT, 10 min TTL |
| `Content-Type: application/json` | POST/PATCH/PUT | |
| `X-Request-ID` | optional | client-supplied `^[A-Za-z0-9._:-]{1,128}$` echoed as `x-request-id`/`X-Request-ID`; else server generates UUID |
| `Idempotency-Key` | optional | forwarded via CORS, not yet enforced server-side |

Security headers always set: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.

Body limit: 1 MB default (2 MB for `POST .../dose-events/batch`, ~5.6 MB for label image).

### 1.2 Auth tokens

- `access_token`: RS256, claims `sub` (user id), `sid`/`jti` (session id), `token_version`, `iss=medac`, `aud=medac-api`, `exp = now + 600s`. Verified against `ACCESS_TOKEN_PUBLIC_KEY_FILE`, `kid` rotation via PEM hash.
- `refresh_token`: 256-bit opaque (`base64url` 32 bytes), stored as `HMAC-SHA256(TOKEN_HASH_SECRET)` in `auth_sessions.refresh_token_hash`. Rotation with `previous_token_hash` + family `refresh_family_id`; reuse → revoke family + bump `user_security.token_version` (invalidates all access tokens).
- `mfa_session_token`: short-lived (5 min) opaque stored as `refresh_token_hash` with `last_used_at IS NULL` — **not** usable at `/auth/refresh` (explicitly rejected).

### 1.3 Error envelope

All errors (including 404) return:

```json
{
  "error": {
    "code": "VALIDATION_ERROR | UNAUTHORIZED | FORBIDDEN | NOT_FOUND | CONFLICT | UNPROCESSABLE_ENTITY | ACCOUNT_LOCKED | RATE_LIMITED | BAD_REQUEST | INTERNAL_ERROR | PATIENT_MISMATCH | ...",
    "message": "human sentence",
    "request_id": "uuid or supplied X-Request-ID",
    "details": { "validation": [/* Zod issues */] } // only on 400
  }
}
```

- 400 = Zod `strict()` failure → `details.validation` array.
- 401 = missing/invalid/expired bearer, inactive user, revoked session, bad refresh reuse.
- 403 = not a member of patient, role lacks permission, `PATIENT_MISMATCH` (tool `patientId` ≠ authorized patient), bad download token.
- 404 = `Route GET /api/v1/... not found` or entity not found.
- 409 = email taken, MFA already enabled, etc.
- 423 = account locked (5 failed logins → 15 min lock).
- 429 = rate limited (in-memory per-process buckets).
- 5xx never leaks stack — `{"code":"INTERNAL_ERROR","message":"An unexpected error occurred."}`.

### 1.4 Pagination & ids

- All ids are UUIDs (`uuid` v4, `strict` validated).
- Timestamps: `timestamptz` ISO-8601 with offset (`datetime({offset:true})`), e.g. `2026-08-18T14:00:00.000Z`.
- Dates: `YYYY-MM-DD` (`/^\d{4}-\d{2}-\d{2}$/`).
- Times: `HH:mm` or `HH:mm:ss` (`/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/`).
- Timezones: **IANA only** (`America/New_York`), bare offsets (`+02:00`, `Z`) rejected.
- Decimals: **strings** (`/^\d+(\.\d+)?$/`, max 12 chars) — e.g. `"1"`, `"0.5"` — to avoid float loss.
- Cursor pagination: `?cursor=<base64url JSON>` + `?limit=1..100`, response `next_cursor` (pass as next `cursor`).

### 1.5 Roles & permissions

`owner > manager > contributor > viewer`. Every patient-scoped query checks `patient_memberships` (`user_id`, `patient_id`, `status='active'`). `requirePermission(role, perm)` central in `lib/permissions.ts`.

| Role | Key permissions |
|---|---|
| **owner** | all (`patient:*`, `membership:*`, `invite:*`, `medication:*`, `schedule:*`, `doseEvent:*`, `inventory:*`, `symptom:*`, `injection:*`, `alert:*`, `export:*`, `adherence:read`, `consent:*`, `intelligence:*`) |
| **manager** | like owner minus `patient:delete`, `patient:transferOwnership` |
| **contributor** | `patient:read`, `medication:read`, `schedule:read`, `doseEvent:read/create`, `inventory:read`, `symptom:write`, `injection:write`, `alert:read/acknowledge`, `export:read`, `intelligence:read/write` |
| **viewer** | read-only (`patient:read`, `medication:read`, `schedule:read`, `doseEvent:read`, `inventory:read`, `symptom:read`, `injection:read`, `alert:read`, `export:read`, `intelligence:read`) |

Sensitive (`REAUTH_REQUIRED: patient:delete`, `patient:transferOwnership`, `membership:revoke/update`) require recent auth (session `createdAt < 5 min`).

---

## 2. Health & meta

```
GET  /health/live              → 200 {status:"ok", service:"medac-api"}
GET  /api/v1/health/live       → same (public: /medac/api/v1/health/live via nginx alias)
GET  /health/ready             → 200 {status:"ready"} | 503 {status:"not_ready"} (checks DB + schema_migrations)
GET  /api/v1/health/ready      → same
GET  /version                  → {version, build_id, env}
GET  /api/v1/version           → same
GET  /api/v1/openapi.json      → committed spec (currently stale)
GET  /                         → {service:"medac-api", docs:"/medac/api/v1 (public) -> /api/v1 (internal)", health:[...], version:"/version"}
GET  /metrics                  → localhost only (127.0.0.1), else 403
GET  /api/v1/metrics           → requires Authorization (returns {metrics:{...}})
```

---

## 3. Auth (`/api/v1/auth`)

### 3.1 Register

```
POST /api/v1/auth/register
Body: { email: string (email, max 320), password: string (12..128, validated strength), preferred_locale?: string (2..20) }  strict
→ 201 { user_id: uuid }
→ 400 VALIDATION_ERROR | 409 EMAIL_TAKEN | 429 RATE_LIMITED (5/ip/hour, 3/email/hour)
```

Password strength: min 12 chars. User created `status=pending`, `user_security.tokenVersion=1`, `email_verification_tokens` (24h). Outbox `user_registered` with `{emailNormalized, rawToken}` — worker sends verification email (currently `EMAIL_PROVIDER=console`, logs). **Raw token not returned in prod** (only in `NODE_ENV!=production` helper).

### 3.2 Verify email

```
POST /api/v1/auth/verify-email
Body: { token: string (min 10) }
→ 200 {ok:true}
→ 400 INVALID_TOKEN (expired/used/invalid, 10/ip/hour)
```

### 3.3 Resend verification

```
POST /api/v1/auth/resend-verification
Body: { email: string (email) }
→ 200 {ok:true}  // never reveals existence (no enumeration)
```

### 3.4 Login

```
POST /api/v1/auth/login
Body: {
  email: string,
  password: string (1..128),
  device?: { platform?: "ios"|"android"|"web"|"unknown", display_name?: string(≤100), timezone?: string(≤100) }  strict
}
→ 200 { access_token, refresh_token, session_id }                          // no MFA
→ 200 { mfa_required: true, mfa_session_token: string }                    // MFA enabled
→ 401 INVALID_CREDENTIALS | 403 ACCOUNT_NOT_ACTIVE | 423 ACCOUNT_LOCKED | 429 RATE_LIMITED (20/ip/15min, 5/account/15min)
```

Failed logins increment `failedLoginCount`; 5th → `lockedUntil = now+15min`. Success resets count, rehashes if `needsRehash`, updates `lastLoginAt`. Dummy `hashPassword("dummy...")` on unknown email to avoid timing side-channel.

### 3.5 MFA verify

```
POST /api/v1/auth/mfa/verify
Body: { mfa_session_token: string, code: string (min 3), use_recovery_code?: boolean }
→ 200 { access_token, refresh_token, session_id }
→ 401 INVALID_MFA_SESSION | INVALID_MFA_CODE | 400 MFA_NOT_ENABLED | 429 (10/ip/15min)
```

TOTP: `encryptedSecret` (AES), `lastUsedStep` replay protection. Recovery codes: `XXXX-XXXX-XXXX` (hex), single-use `codeHash = HMAC(token)`.

### 3.6 Refresh (rotation + reuse detection)

```
POST /api/v1/auth/refresh
Body: { refresh_token: string (min 10) }
→ 200 { access_token, refresh_token, session_id }  // new refresh, previous stored for detection
→ 401 INVALID_REFRESH | REFRESH_EXPIRED | REFRESH_REUSE (→ revokes family + bumps token_version)
→ 429 (30/ip/min)
```

Pending MFA sessions (`last_used_at IS NULL`) explicitly rejected at refresh.

### 3.7 Logout / Logout all

```
POST /api/v1/auth/logout
Body: { refresh_token?: string }  // optional; if omitted, derives session from Bearer
Header: Authorization: Bearer <access_token> (optional, for sessionId derivation)
→ 200 {ok:true}  // idempotent

POST /api/v1/auth/logout-all
Header: Authorization: Bearer <access_token>  // required
→ 200 {ok:true}  // revokes all sessions + bumps token_version
```

### 3.8 Password

```
POST /api/v1/auth/password/forgot
Body: { email: string }
→ 200 {ok:true}  // no enumeration, 5/ip/hour, 3/email/hour; outbox password_changed

POST /api/v1/auth/password/reset
Body: { token: string (min 10), new_password: string (12..128) }
→ 200 {ok:true}  // validates strength, marks token used, rehashes, bumps token_version, revokes all sessions

POST /api/v1/auth/password/change   [auth]
Body: { current_password: string, new_password: string (12..128) }
→ 200 {ok:true}  // verifies current, rehashes, bumps token_version, revokes all sessions
```

### 3.9 Sessions

```
GET  /api/v1/auth/sessions                          [auth]
→ 200 { sessions: [{id, device_id, created_at, last_used_at, expires_at, revoked_at, user_agent_summary}] }

DELETE /api/v1/auth/sessions/:sessionId             [auth]
Params: sessionId uuid
→ 204  | 404 NOT_FOUND
```

### 3.10 TOTP MFA management

```
POST   /api/v1/auth/mfa/totp/setup                  [auth]
→ 200 { secret_base32, otpauth_url: "otpauth://totp/Medac:email?secret=...&issuer=Medac" }
→ 409 MFA_ALREADY_ENABLED

POST   /api/v1/auth/mfa/totp/confirm                [auth]
Body: { code: string (length 6) }
→ 200 { recovery_codes: string[10] }  // format XXXX-XXXX-XXXX, sets mfaRequired=true
→ 400 INVALID_TOTP | MFA_NOT_SETUP

DELETE /api/v1/auth/mfa/totp                        [auth]
→ 204  // deletes credentials + recovery codes, mfaRequired=false
```

---

## 4. Patients (`/api/v1/patients`)

### 4.1 List / Create

```
GET  /api/v1/patients                               [auth]
→ 200 { patients: Patient[] }  // only via active memberships

POST /api/v1/patients                               [auth]
Body: { display_name: string(1..200), preferred_timezone?: string(IANA, default UTC), date_of_birth?: string(date)|null, notification_privacy_mode?: "private"|"generic"|"detailed" } strict
→ 201 Patient // caller becomes owner membership
→ 400 invalid timezone
```

`Patient`: `{id, displayName, dateOfBirth, sexAtBirth, preferredTimezone, notificationPrivacyMode, createdByUserId, createdAt, updatedAt, archivedAt, deletionRequestedAt}`

### 4.2 Get / Update / Archive

```
GET   /api/v1/patients/:patientId                  [auth patient:read]
PATCH /api/v1/patients/:patientId                  [auth patient:update]
  Body: { display_name?, preferred_timezone?, notification_privacy_mode? } strict

POST  /api/v1/patients/:patientId/archive          [auth patient:archive]
→ 200 {ok:true}

POST  /api/v1/patients/:patientId/deletion-request [auth patient:delete]
→ 200 {ok:true}  // sets deletionRequestedAt
```

### 4.3 Memberships

```
GET    /api/v1/patients/:patientId/memberships                    [auth membership:read]
→ 200 { memberships: [{id, patientId, userId, role, status, grantedByUserId, createdAt, updatedAt, revokedAt}] }

PATCH  /api/v1/patients/:patientId/memberships/:membershipId     [auth membership:update]
Body: { role: "owner"|"manager"|"contributor"|"viewer" }
→ 200 {ok:true}  // 400 LAST_OWNER if demoting last owner

DELETE /api/v1/patients/:patientId/memberships/:membershipId     [auth membership:revoke]
→ 204  // 400 LAST_OWNER if revoking last owner
```

### 4.4 Caregiver invites

```
POST   /api/v1/patients/:patientId/invites          [auth invite:create]
Body: { email: string(email), role: string(valid role) }  // only owners can invite owners
→ 201 { invite_id: uuid }  // raw token in outbox caregiver_invited {rawToken}, not returned

GET    /api/v1/patients/:patientId/invites          [auth invite:read]
→ 200 { invites: [{id, invited_email_normalized, role, expires_at, accepted_at, revoked_at, created_at}] }

DELETE /api/v1/patients/:patientId/invites/:inviteId [auth invite:revoke]
→ 204  // 400 if already used/revoked

POST   /api/v1/invites/:token/accept                [auth]
Params: token (opaque, min 10)
→ 200 { patient_id: uuid }  // single-use, 7-day expiry; creates/reactivates active membership
→ 400 INVALID_INVITE
```

---

## 5. Medications (`/api/v1/patients/:patientId/medications`)

All require `Authorization` + patient membership.

```
GET  /patients/:patientId/medications?status=&include_archived=  [medication:read]
→ 200 { medications: Medication[] }

POST /patients/:patientId/medications               [medication:create]
GET  /patients/:patientId/medications/:medicationId [medication:read]
PATCH /patients/:patientId/medications/:medicationId [medication:update]  Body: UpdateMedicationSchema (optimistic expected_updated_at?)
POST /patients/:patientId/medications/:medicationId/activate     [medication:activate]   → status active
POST /patients/:patientId/medications/:medicationId/pause        [medication:pause]      → paused
POST /patients/:patientId/medications/:medicationId/resume       [medication:activate]   → active
POST /patients/:patientId/medications/:medicationId/discontinue  [medication:discontinue]→ discontinued
POST /patients/:patientId/medications/:medicationId/archive      [medication:archive]    → archived
POST /patients/:patientId/medications/:medicationId/normalization-selection [medication:update]
Body: { drug_concept_id: uuid|null, normalization_status: "unresolved"|"pending"|"resolved"|"ambiguous"|"failed"|"manually_confirmed" }
```

**Create body** (`CreateMedicationSchema`, `strict`):

```json
{
  "entered_name": "Lisinopril",
  "entered_strength_value": "10",        // decimal string | null
  "entered_strength_unit": "mg",         // DoseUnitEnum | null  (required if value present)
  "form": "tablet",                      // MedicationFormEnum | null
  "route": "oral",                       // MedicationRouteEnum | null
  "dose_quantity_value": "1",            // decimal string, required
  "dose_quantity_unit": "tablet",        // DoseUnitEnum, required
  "indication_text": null,               // 1000 | null
  "prescriber_text": null,               // 500 | null
  "pharmacy_text": null,                 // 500 | null
  "label_instructions_text": "Take once daily",
  "ndc": null,                           // 20 | null
  "normalization_status": "unresolved",
  "status": "draft",                     // draft|active|paused|discontinued|archived
  "high_attention_user_flag": false,
  "start_date": "2026-08-18",            // YYYY-MM-DD | null
  "end_date": null,
  "drug_concept_id": null                // uuid | null
}
```

Enums:
- `form`: `tablet|capsule|liquid|injection|patch|inhaler|drops|cream|ointment|suppository|other|unknown`
- `route`: `oral|sublingual|topical|inhalation|injection|ophthalmic|otic|nasal|rectal|vaginal|transdermal|other|unknown`
- `DoseUnit`: `mg|mcg|g|mL|mg/mL|units|IU|percent|%|tablet|capsule|patch|puff|drop|suppository|application|actuation|other`
- `normalization_status`: `unresolved|pending|resolved|ambiguous|failed|manually_confirmed`

`Medication` response (snake_case via `toApi`): `id, patient_id, drug_concept_id, entered_name, entered_strength_value (string|null), entered_strength_unit, form, route, dose_quantity_value (string), dose_quantity_unit, indication_text, prescriber_text, pharmacy_text, label_instructions_text, normalization_status, status, high_attention_user_flag, start_date, end_date, created_by_user_id, updated_by_user_id, created_at, updated_at, paused_at, discontinued_at, archived_at`.

---

## 6. Drug normalization (no auth — public)

```
GET  /api/v1/drug-normalization/search?q=<2..100>
→ RxNorm search result (proxied via RXNORM_BASE_URL)

POST /api/v1/drug-normalization/resolve-ndc
Body: { ndc: string(8..20) }
→ resolved concept

GET  /api/v1/drug-normalization/concepts/:rxcui
Params: rxcui /^\d+$/
→ concept detail
```

Use on medication entry to resolve `entered_name`/`ndc` → `drug_concept_id`.

---

## 7. Schedules (`/api/v1/patients/:patientId/medications/:medicationId/schedules`)

```
GET  /patients/:patientId/medications/:medicationId/schedules          [schedule:read]
→ 200 { schedules: ScheduleVersion[] }

POST /patients/:patientId/medications/:medicationId/schedules          [schedule:create]
Body: CreateScheduleSchema  strict
→ 201 { schedule: ScheduleVersion, preview: Occurrence[] }

GET  /patients/:patientId/medications/:medicationId/schedules/:versionId [schedule:read]
→ 200 { schedule, fixed_times, interval, cycle, taper_steps, preview }

POST /patients/:patientId/medications/:medicationId/schedules/:versionId/supersede [schedule:create]
Body: CreateScheduleSchema
→ 201 { schedule, preview }  // increments versionNumber, sets previous effectiveUntil

GET  /patients/:patientId/occurrences?from=&to=&cursor=&limit=          [schedule:read]
Query: from datetime?, to datetime?, cursor?, limit 1..100
→ 200 { occurrences: Occurrence[], next_cursor }
```

**`CreateScheduleSchema`:**

```json
{
  "schedule_type": "fixed_times|elapsed_interval|prn|cyclic|taper",
  "timing_mode": "local_clock|elapsed_interval",
  "timezone": "America/New_York",          // IANA, required
  "miss_window_minutes": 60,               // 0..1440
  "effective_from": "2026-08-18T00:00:00.000Z",
  "fixed_times": [{"local_time":"08:00","days_of_week":[1,3],"dose_quantity_value":"1","dose_quantity_unit":"tablet","ambiguous_time_policy":"earlier"}],
  "interval": {"interval_minutes":480,"anchor_at":"2026-08-18T08:00:00.000Z","anchor_policy":"fixed_anchor|last_taken"},
  "cycle": {"cycle_anchor_date":"2026-08-18","on_days":21,"off_days":7,"local_time":"08:00"},
  "taper_steps": [{"step_order":1,"starts_on":"2026-08-18","ends_on":"2026-08-25","dose_quantity_value":"10","dose_quantity_unit":"mg","local_time":"08:00","fixed_times":[...]}]
}
```

`ScheduleVersion`: `id, medication_id, version_number, schedule_type, timing_mode, timezone, effective_from, effective_until, miss_window_minutes, created_by_user_id, created_at, updated_at`.
`Occurrence`: `id, patient_id, medication_id, schedule_version_id, scheduled_at_utc, scheduled_local_datetime, timezone, utc_offset_minutes, dst_adjusted, nominal_dose_value, nominal_dose_unit, state (scheduled|due|taken|skipped|missed|cancelled|corrected), generated_at`.

---

## 8. Doses (`/api/v1/patients/:patientId/dose-events`)

```
POST /patients/:patientId/dose-events                    [doseEvent:create]
Body: {
  medication_id: uuid,
  occurrence_id: uuid|null,          // null for PRN
  event_type: "taken|skipped|snoozed|corrected|cancelled",
  actual_at: datetime,
  timezone?: string(IANA),
  dose_value?: decimal_string|null,
  dose_unit?: string(1..32)|null,
  note?: string(2000)|null,
  client_event_id: string(1..100),   // idempotency key — duplicate returns 200 {outcome:"duplicate"}
  device_id?: uuid|null
} strict
→ 201 {event, outcome:"created"} | 200 {event, outcome:"duplicate"}

POST /patients/:patientId/dose-events/batch              [doseEvent:create]  bodyLimit 2MB
Body: { events: DoseEvent[1..100] } strict
→ 200 { results: [{event, outcome}] }

GET  /patients/:patientId/dose-events?from=&to=&cursor=&limit= [doseEvent:read]
→ 200 { events: DoseEvent[], next_cursor }

POST /patients/:patientId/dose-events/:eventId/corrections [doseEvent:correct]
Body: { correction_type:"taken|skipped|cancelled|corrected", actual_at?:datetime, dose_value?:decimal|null, dose_unit?:string|null, note?:string|null, reason:string(1..500), client_event_id:string, device_id?:uuid } strict
→ 201 {event}

GET  /patients/:patientId/sync?cursor=                   [doseEvent:read]
→ 200 { changes: [{type:"medication"|"schedule_version"|"occurrence"|"dose_event"|"membership_revoked", id, updated_at}], next_cursor }
 // cursor = base64url({since: ISO timestamp}); incremental sync for offline-first mobile
```

`DoseEvent` (via `toApi`): `id, patient_id, medication_id, occurrence_id, event_type, actual_at, recorded_at, timezone, dose_value (string|null), dose_unit, note (ciphertext field, decrypted in service for list), client_event_id, device_id, actor_user_id, corrects_event_id, created_at`.

---

## 9. Inventory (`/api/v1/patients/:patientId/medications/:medicationId/inventory`)

```
GET  /patients/:patientId/medications/:medicationId/inventory  [inventory:read]
→ 200 { account: {id, unit, low_stock_threshold_value}|null, balance: number, low_stock: boolean, forecast_days: number|null, recent_transactions: [{id, transaction_type, quantity_delta, effective_at}] }

POST /patients/:patientId/medications/:medicationId/inventory/transactions [inventory:write]
Body: { transaction_type:"fill|manual_adjustment|lost_or_damaged|disposed|transferred", quantity_delta: decimal_string (negative allowed), unit:string(1..32), reason_text?:string(500), effective_at?:datetime } strict
→ 201 {transaction: {id, transaction_type, quantity_delta, effective_at}}

GET  /patients/:patientId/medications/:medicationId/inventory/transactions?cursor=&limit= [inventory:read]
→ 200 { transactions: [...], next_cursor }

PATCH /patients/:patientId/medications/:medicationId/inventory/settings [inventory:write]
Body: { low_stock_threshold_value?: decimal_string|null, unit?: string(1..32) }
→ 200 {account: {id, unit, low_stock_threshold_value}}
```

`transaction_type` enum also includes internal `dose_consumed`, `dose_consumption_reversed`.

---

## 10. Expirations (`/api/v1/patients/:patientId/medications/:medicationId/expirations`)

```
GET    /patients/:patientId/medications/:medicationId/expirations              [medication:read]
→ 200 { expirations: [{id, medication_id, expiration_date: "YYYY-MM-DD", lot_number, quantity_value, quantity_unit, created_at, updated_at}] }

POST   /patients/:patientId/medications/:medicationId/expirations              [medication:create]
Body: { expiration_date: "YYYY-MM-DD", lot_number?: string(100)|null, quantity_value?: decimal_string|null, quantity_unit?: string(32)|null } strict
→ 201 expiration

PATCH  /patients/:patientId/medications/:medicationId/expirations/:expirationId [medication:update]
Body: { expiration_date?: "YYYY-MM-DD", lot_number?, quantity_value?, quantity_unit? } 
→ 200 expiration

DELETE /patients/:patientId/medications/:medicationId/expirations/:expirationId [medication:update]
→ 204
```

---

## 11. Symptoms (`/api/v1/patients/:patientId/symptom-logs`)

```
GET  /patients/:patientId/symptom-logs?cursor=&limit=  [symptom:read]
→ 200 { logs: [{id, patient_id, occurred_at, note (decrypted), linked_dose_event_id, actor_user_id, created_at, corrected_by_log_id}], next_cursor }

POST /patients/:patientId/symptom-logs              [symptom:write]
Body: { occurred_at: datetime, note?: string(2000)|null, linked_dose_event_id?: uuid|null } strict
→ 201 log

POST /patients/:patientId/symptom-logs/:logId/corrections [symptom:write]
Body: { note?: string(2000)|null, reason?: string(500) }
→ 201 log  // creates new log, sets correctedByLogId on original
```

---

## 12. Injection sites (`/api/v1/patients/:patientId/injection-site-logs`)

```
GET  /patients/:patientId/injection-site-logs?medication_id=&limit= [injection:read]
→ 200 { logs: [{id, patient_id, medication_id, dose_event_id, site_code, occurred_at, actor_user_id, created_at}] }

POST /patients/:patientId/injection-site-logs               [injection:write]
Body: { medication_id: uuid, dose_event_id?: uuid|null, site_code: string(1..50), occurred_at: datetime } strict
→ 201 log
```

---

## 13. Alerts (`/api/v1/patients/:patientId/alerts`)

```
GET  /patients/:patientId/alerts?status=&limit=              [alert:read]
Query: status? (open|acknowledged|resolved|cancelled), limit 1..100
→ 200 { alerts: [{id, patient_id, alert_type, severity, status, source_entity_type, source_entity_id, message_key, created_at, acknowledged_at, resolved_at}] }

POST /patients/:patientId/alerts/:alertId/acknowledge        [alert:acknowledge]
→ 200 alert

GET  /patients/:patientId/alert-preferences                  [alertPreference:read]
→ 200 { preferences: [{id, patient_id, user_id, alert_type, enabled, delay_minutes, quiet_hours_start, quiet_hours_end, timezone}] }

PUT  /patients/:patientId/alert-preferences                  [alertPreference:write]
Body: { preferences: [{alert_type:"low_stock|expiration|missed_user_attention_med|stale_device|generic", enabled:boolean, delay_minutes?:0..1440, quiet_hours_start?:"HH:mm"|null, quiet_hours_end?:"HH:mm"|null, timezone?:string|null}] }  1..20 strict
→ 200 {preferences}

POST /patients/:patientId/alerts/generate                    [alert:read, manager+]
→ 200 { generated: { low_stock: number, expiration: number, stale_device: number, missed: number } }  // manual trigger
```

Severity: `info|attention|urgent_review|potential_emergency`. Status: `open|acknowledged|resolved|cancelled`.

---

## 14. Reports (`/api/v1/patients/:patientId/reports`)

```
GET /patients/:patientId/reports/adherence?from=&to=&timezone=  [adherence:read]
Query: from datetime (required), to datetime (required, from < to), timezone? IANA (default UTC)
→ 200 {
    patient_id, from, to, timezone,
    eligible_scheduled_count, taken_count, skipped_count, missed_count, pending_count, excluded_count,
    excluded_reasons: {prn_excluded, cancelled, paused, medication_not_found},
    adherence_percentage: number|null,  // taken / eligible * 100, null if no eligible
    denominator_definition: "eligible_scheduled_count = total scheduled occurrences in range excluding PRN, cancelled, paused; adherence = taken / eligible"
  }
```

---

## 15. Exports (`/api/v1/patients/:patientId/exports`)

```
POST /patients/:patientId/exports                 [export:create]
Body: { from?: datetime, to?: datetime, include_symptoms?: boolean }
→ 202 { export: {id, status:"pending"|"processing"|"completed"|"failed"|"expired", created_at, expires_at} }
 // server generates CSVs inline (patient.csv, medications.csv, schedules.csv, dose_events.csv, inventory.csv, symptoms.csv if requested) under EXPORT_DIRECTORY/<exportId>/, then sets downloadToken (HMAC, 1h) + status completed. CSV cells prefixed with ' if starting with =+-@ (formula injection protection).

GET  /patients/:patientId/exports/:exportId       [export:read]
→ 200 { export: {id, status, created_at, expires_at, download_token_expires_at} }  // 404 if patient mismatch, 410 if expired

GET  /patients/:patientId/exports/:exportId/download?token= [export:read]
Query: token? (raw hex for direct link; if omitted, auth membership check still required)
→ 200 text/csv  Content-Disposition: attachment; filename="export-<id>.csv"  (currently patient.csv only)
→ 403 invalid token | 404 not ready | 410 expired

DELETE /patients/:patientId/exports/:exportId     [export:read]
→ 204  // rm -rf dir
```

Storage is currently in-memory `Map` + filesystem — export disappears from API on restart but files may remain; `EXPORT_TTL_HOURS` (default 24h).

---

## 16. Intelligence (Muse Spark — `muse-spark-1.2-contributor` only)

All intelligence routes require `Authorization` + patient membership. Server enforces `patientId = auth.patientId` — model-supplied `patientId` mismatches → `403 PATIENT_MISMATCH`. Rate limits: 10/min, 100/hour, daily spend `MUSE_SPARK_DAILY_BUDGET * 100` cents.

### 16.1 Label interpretation

```
POST /api/v1/patients/:patientId/intelligence/label-interpretations  [intelligence:write]  bodyLimit ~5.6MB
Body (strict, one of image or ocr required):
{
  imageBase64 | image_base64 | image?: string (base64, data: prefix stripped),
  mimeType | mime_type?: "image/jpeg"|"image/png"|"image/webp" (required with image),
  ocrText | ocr_text?: string(1..5000),
  locale?: string(2..20)
}
→ 200 {
    interpretation: {
      observed_text: string(1..5000),
      candidate_name: string|null,
      candidate_generic_name: string|null,
      strength_value: decimal_string|null,
      strength_unit: "mg"|"mcg"|"g"|"mL"|"mg/mL"|"units"|"IU"|"percent"|"%"|"mg_per_mL"|null,
      form: "tablet"|"capsule"|"liquid"|"injection"|"patch"|"inhaler"|"drops"|"cream"|"ointment"|"suppository"|"other"|"unknown"|null,
      route: "oral"|"sublingual"|"topical"|"inhalation"|"injection"|"ophthalmic"|"otic"|"nasal"|"rectal"|"vaginal"|"transdermal"|"other"|"unknown"|null,
      label_directions_text: string|null,
      prescriber_fields?: {prescriber_name, pharmacy_name, pharmacy_phone, fill_date}|null,
      uncertain_fields: string[0..20],
      clarifying_questions: string[0..5],
      evidence: [{field, span, confidence:0..1}][1..30],
      confidence: 0..1
    },
    ai_run_id: uuid,
    provider: "muse_spark",
    model: "muse-spark-1.2-contributor",
    disclosure: string,  // vision limitations
    requires_user_confirmation: true
  }
→ 400 INVALID_IMAGE | DECODED_TOO_LARGE (413) | 429 RATE_LIMITED | 503 INTELLIGENCE_UNAVAILABLE | 502 PROVIDER_FAILURE
```

Vision validated: `AI_VISION_MAX_BYTES` (default 4 MB), `AI_VISION_MAX_PIXELS`. Input fingerprinted (`HMAC-SHA256`), `ai_runs` recorded (`promptTemplateId=label_interpretation v1`, `safetyPolicyVersion`).

### 16.2 Instruction parse

```
POST /api/v1/patients/:patientId/intelligence/instruction-parses [intelligence:write]
Body: { text: string(1..2000), locale?: string }
→ 200 { parse: {entered_text, dose_value, dose_unit, frequency_text, route, uncertain_fields, clarifying_questions, evidence, confidence}, ai_run_id, requires_user_confirmation:true }
```

### 16.3 Conversations

```
POST   /api/v1/patients/:patientId/intelligence/conversations          [intelligence:write]
Body: { title?: string(200) }
→ 201 { conversation_id: uuid, patient_id, expires_at }  // title encrypted, expires AI_CONVERSATION_RETENTION_HOURS (72h)

DELETE /api/v1/patients/:patientId/intelligence/conversations/:conversationId [intelligence:write]
→ 204  // owner or member with intelligence:write

POST   /api/v1/patients/:patientId/intelligence/conversations/:conversationId/messages [intelligence:write]
Body: { content: string(1..5000), role?: "user"|"assistant" }
→ 200 {
    message: string,            // assistant reply
    facts_used: [{type, id, version?}],
    proposals: [{proposal_id, action_type, requires_confirmation:true, expires_at}],
    limitations: string[],
    ai_run_id, turn_count
  }
 // orchestrator: maxTurns 6, maxToolCalls 12, maxOutputTokens 4096, maxTime 30s; tools validated against registry, re-authorized per call, read vs proposal split
```

### 16.4 Schedule drafts

```
POST /api/v1/patients/:patientId/intelligence/schedule-drafts [intelligence:write]
Body: { medicationId?: uuid, constraints?: string(1..2000), timezone?: string(64), wakeTime?: "HH:mm", sleepTime?: "HH:mm" }
→ 200 { draft: ScheduleDraft, ai_run_id, requires_user_confirmation:true, limitations:["Draft times are convenience proposals, not prescription content"] }
```

### 16.5 Summaries

```
POST /api/v1/patients/:patientId/intelligence/summaries [intelligence:read]
Body: { type:"adherence"|"inventory"|"timeline", from?: datetime, to?: datetime }
→ 200 { summary, ai_run_id }
```

### 16.6 Proposals (confirm/reject pattern)

```
GET  /api/v1/patients/:patientId/intelligence/proposals/:proposalId                    [intelligence:read]
→ 200 { proposal: {id, action_type, status, expires_at, required_permission, resource_versions, human_summary (decrypted), payload_hash} }

POST /api/v1/patients/:patientId/intelligence/proposals/:proposalId/confirm            [intelligence:write + required_permission]
Body: { payload_hash: string(min 10), expected_resource_versions?: Record<string,number> }
→ 200 { proposal: {id, status:"executed", executed_at} }  // checks permission, 5-min recent-auth for high-impact, payloadHash + version match

POST /api/v1/patients/:patientId/intelligence/proposals/:proposalId/reject             [intelligence:write]
→ 200 { proposal: {id, status:"rejected"} }
```

Proposal `action_type` maps to `medication:create|update|activate|pause|discontinue`, `schedule:create`, `doseEvent:create|correct`, `inventory:write`, `alertPreference:write`, `invite:create`, `export:create` (TTL 30 min, encrypted payload, `payloadHash` + `resourceVersionsJson` for exactly-once).

---

## 17. Android integration guide

### 17.1 Retrofit wiring

```kotlin
// Base URL — note /medac prefix
private const val BASE_URL = "http://169.58.196.107/medac/api/v1/"

// Interceptor: attach Authorization + X-Request-ID, refresh on 401
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var req = chain.request().newBuilder()
            .header("X-Request-ID", UUID.randomUUID().toString())
            .header("Authorization", "Bearer ${tokenStore.accessToken}")
            .build()
        var resp = chain.proceed(req)
        if (resp.code == 401 && tokenStore.refreshToken != null) {
            val refreshed = runBlocking { refreshSync() }
            if (refreshed) {
                req = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${tokenStore.accessToken}")
                    .build()
                resp.close()
                resp = chain.proceed(req)
            }
        }
        return resp
    }
    private suspend fun refreshSync(): Boolean { /* POST /auth/refresh {refresh_token} → store new pair */ }
}
```

Model note: the app's current `ApiService.kt` + Retrofit/OkHttp are **not wired** — wire them now against this spec. Keep quantities as `String`, not `Double`.

### 17.2 Recommended flows

**Onboarding:**

1. `POST /auth/register` → `POST /auth/verify-email` (token from email; worker `console` logs `tes***` in dev — in prod, deep-link `medac://verify?token=...` from email).
2. `POST /auth/login` (handle `mfa_required` branch) → store `access_token` (memory) + `refresh_token` (EncryptedSharedPreferences).
3. `POST /patients` → `GET /patients` (multi-dependent profiles under one login).
4. `POST /patients/:pid/medications` → `POST /patients/:pid/medications/:mid/schedules` → `GET /patients/:pid/occurrences?from=&to=` for Today view.

**Daily Today view (offline-first):**

- On launch: `GET /patients/:pid/occurrences?from=startOfDay&to=endOfDay` + `GET /patients/:pid/medications` + `GET /patients/:pid/alerts?status=open`.
- Use `GET /patients/:pid/sync?cursor=` for incremental sync (store `next_cursor` in Room). `changes` covers meds, schedules, occurrences, dose events, membership revocation.
- Local `AlarmManager` remains source of truth for reminders when offline; sync on reconnect.

**Dose logging:**

- `POST /patients/:pid/dose-events` with `client_event_id = UUID` for idempotency; on retry, `200 outcome:"duplicate"` is success.
- Batch offline queue: `POST /patients/:pid/dose-events/batch` (100 max, 2 MB).
- Corrections via `POST .../:eventId/corrections` with `reason`.

**OCR → label interpretation:**

- Capture with `TakePicture()` → ML Kit OCR locally (existing `MedacViewModel.buildDraftSuggestion()`), then `POST /patients/:pid/intelligence/label-interpretations` with `{ocrText}` or `{imageBase64, mimeType}`. Render `uncertain_fields` + `evidence` + `clarifying_questions`; require **user confirmation** before `POST /patients/:pid/medications`.

### 17.3 Error handling on device

```kotlin
data class ApiError(val code: String, val message: String, val requestId: String, val details: JsonObject?)
when (code) {
    "VALIDATION_ERROR" -> showFieldErrors(details)
    "UNAUTHORIZED"     -> refreshOrReLogin()
    "FORBIDDEN", "PATIENT_MISMATCH" -> showNoAccess()
    "NOT_FOUND"        -> showNotFound()
    "RATE_LIMITED"     -> backoff()
    "ACCOUNT_LOCKED"   -> showLockedUntil()
    else -> showGeneric(message, requestId) // include requestId in bug reports
}
```

Always log `request_id` for server correlation (`journalctl -u medac-api-new`).

---

## 18. Operational notes for mobile

- **CORS:** `CORS_ALLOWED_ORIGINS=https://169.58.196.107` (http origin will be blocked by browser; native OkHttp unaffected).
- **Rate limits:** in-memory (per-process) — expect reset on deploy; handle 429 with backoff.
- **Outbox/worker:** `medac-worker` drains `outbox_events` via `FOR UPDATE SKIP LOCKED` (2s poll). If verify email doesn't arrive, check `sudo -u medac journalctl -u medac-worker`. `password_changed`/`user_registered` events mark `processed_at`; retries up to `JOB_MAX_ATTEMPTS=5` then `DEAD_LETTER`.
- **Exports:** `EXPORT_DIRECTORY=/var/lib/medac/exports`, TTL 24h; CSV formula-injection sanitized.
- **AI budget:** `MUSE_SPARK_DAILY_BUDGET=50`, `MAX_AGENT_TURNS=6`, `MAX_TOOL_CALLS=12`, `TIMEOUT_MS=30000`.

---

## 19. curl smoke test

```bash
BASE=http://169.58.196.107/medac/api/v1
# health
curl -s $BASE/../health/live | jq
# register → verify → login
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"Str0ngPass!Example12"}' | jq
# (fetch rawToken from DB/outbox in dev, or email in prod)
curl -s -X POST $BASE/auth/verify-email -H 'Content-Type: application/json' \
  -d '{"token":"<rawToken>"}' | jq
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"Str0ngPass!Example12"}' | jq
TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"Str0ngPass!Example12"}' | jq -r .access_token)
# patient + medication + schedule + occurrence
curl -s -X POST $BASE/patients -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"display_name":"Demo Patient"}' | jq
PID=$(curl -s $BASE/patients -H "Authorization: Bearer $TOKEN" | jq -r .patients[0].id)
curl -s -X POST $BASE/patients/$PID/medications -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"entered_name":"Lisinopril","dose_quantity_value":"10","dose_quantity_unit":"mg"}' | jq
```

---

*Generated from live route validation (`src/app.ts` + `src/modules/*/routes.ts` + `src/modules/*/validation.ts` + `src/lib/permissions.ts`) — not from `openapi.json`. Update this doc when routes change; regenerate `openapi.json` via `scripts/generate-openapi.ts`.*
