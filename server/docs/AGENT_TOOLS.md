# AGENT_TOOLS — Complete Tool Catalog, Permissions, Schemas, Confirmation Policy, Versioning

Ref: `SERVER_IMPLEMENTATION_PLAN.md` §8.7, §12.4

## 1. Overview

Agent uses server-owned allowlisted tools only. The model selects tools; server validates every call. Tool results are data, not instructions (delimited as untrusted). Authorization is re-checked per call using patient-scoped `patientId` + membership role. Model never supplies trusted `userId`/role.

Tool loop hard limits: max turns, max tool calls, max context bytes, max output tokens, provider timeout, per-user/provider budget, total time.

## 2. Classification

- **read** — executed automatically during a turn; returns sanitized data to model; no user confirmation.
- **proposal** — does NOT mutate DB; creates an encrypted, expiring `ai_action_proposals` row; requires explicit user confirmation via dedicated endpoint (chat “yes” is not confirmation).

Registry: `server/src/modules/intelligence/tool-registry.ts` (`TOOL_REGISTRY`, `validateToolCall`, `getToolDefinition`).

All arg schemas are Zod `.strict()` — unknown tools and extra args are rejected.

## 3. Read tools (12) — `mode: read`

| Name | Version | Permission | Args (strict) | Notes |
|---|---|---|---|---|
| `list_medications` | v1 | `medication:read` | `{ patientId: uuid, status?: enum }` | Patient-scoped list |
| `get_medication` | v1 | `medication:read` | `{ patientId, medicationId }` | Single medication |
| `get_today_occurrences` | v1 | `schedule:read` | `{ patientId, date?: YYYY-MM-DD, timezone? }` | Today only |
| `get_schedule` | v1 | `schedule:read` | `{ patientId, medicationId }` | Versions |
| `preview_schedule` | v1 | `schedule:read` | `{ patientId, medicationId, scheduleDraft?, from?, to?, timezone? }` | Deterministic preview; uses production engine |
| `get_adherence_report` | v1 | `report:read` | `{ patientId, from: datetime, to: datetime, timezone? }` | Deterministic report |
| `get_inventory_summary` | v1 | `inventory:read` | `{ patientId, medicationId? }` | Ledger balance/forecast |
| `get_expiration_summary` | v1 | `medication:read` | `{ patientId, withinDays? }` | Expiration dates |
| `get_recent_dose_events` | v1 | `dose:read` | `{ patientId, medicationId?, limit? }` | Recent events |
| `get_injection_site_history` | v1 | `dose:read` | `{ patientId, medicationId?, limit? }` | Injection sites |
| `search_rxnorm` | v1 | `drug:read` | `{ query: string(2..200), limit? }` | Normalization aid |
| `resolve_ndc` | v1 | `drug:read` | `{ ndc: string(8..20) }` | NDC lookup |

Parallel read calls allowed when independent.

## 4. Proposal tools (12) — `mode: proposal` (confirmation-gated)

| Name | Version | Permission | Args (strict) | Action |
|---|---|---|---|---|
| `propose_create_medication` | v1 | `medication:write` | `{ patientId, enteredName, doseValue?, doseUnit?, form?, route?, labelDirectionsText?, resourceVersion? }` | Create draft |
| `propose_update_medication` | v1 | `medication:write` | `{ patientId, medicationId, patch, expectedVersion }` | Patch |
| `propose_activate_medication` | v1 | `medication:write` | `{ patientId, medicationId, expectedVersion }` | Activate |
| `propose_pause_or_resume_medication` | v1 | `medication:write` | `{ patientId, medicationId, action: pause\|resume, reason?, expectedVersion }` | Pause/resume |
| `propose_discontinue_medication` | v1 | `medication:write` | `{ patientId, medicationId, reason?, expectedVersion }` | Discontinue (high-impact: recent auth) |
| `propose_create_or_supersede_schedule` | v1 | `schedule:write` | `{ patientId, medicationId, scheduleType, timingMode, timezone, payload, expectedMedicationVersion?, expectedScheduleVersion? }` | Schedule create/supersede (high-impact) |
| `propose_log_dose_event` | v1 | `dose:write` | `{ patientId, medicationId, occurrenceId?, eventType, actualAt, doseValue?, doseUnit?, clientEventId, deviceId }` | Log dose |
| `propose_correct_dose_event` | v1 | `dose:write` | `{ patientId, eventId, correctionType, reason, expectedEventVersion? }` | Correction (append-only) |
| `propose_inventory_adjustment` | v1 | `inventory:write` | `{ patientId, medicationId, transactionType, quantityDelta, unit, reasonText? }` | Ledger adjustment |
| `propose_alert_preferences` | v1 | `alert:write` | `{ patientId, preferences: [{ alertType, enabled, delayMinutes? }] }` | Alert prefs |
| `propose_caregiver_invite` | v1 | `membership:invite` | `{ patientId, invitedEmail, role }` | Invite (high-impact) |
| `propose_export` | v1 | `export:write` | `{ patientId, exportType: csv\|pdf, from?, to?, includeSymptoms? }` | Export (async) |

Each proposal creation validates args, checks permission, encrypts payload, captures `resourceVersionsJson` for optimistic concurrency, and sets `expiresAt = now + AI_PROPOSAL_TTL_MINUTES`.

## 5. Confirmation policy

- API: `POST /patients/:patientId/intelligence/proposals/:proposalId/confirm` with `{ payloadHash }` (not arbitrary payload) + `POST .../reject`.
- Server checks: authorization still active, `patientId` matches, `payloadHash` matches stored `payload_hash`, `resourceVersionsJson` still current, status `pending`, not expired.
- High-impact actions (`discontinue`, `caregiver_invite`, `create_or_supersede_schedule`) require recent authentication (`hasRecentAuth`).
- Confirmation and execution are transactional where practical; proposal executes exactly once (`executed` guard prevents replay).
- Expired/unconfirmed proposals are swept (`expireSweep`).
- Response on success: deterministic server-rendered diff (old/new values + warnings) — body field `humanSummaryCiphertext` decrypted server-side for display.
- No confirmation via free-form model text.

## 6. Orchestrator loop (§12.4)

1. Authenticate user + authorize patient (server creates `AuthContext` + `PatientAuthorization`).
2. Build minimal purpose-specific context (`context-builders.ts`).
3. Call Muse Spark (`runToolTurn`) with system prompt + delimited context + tools.
4. Validate tool + args against registry (reject unknown/extra).
5. Re-authorize tool (permission + patient scope).
6. Execute read OR create proposal (encrypted).
7. Return sanitized tool result (delimited) to model.
8. Repeat within budgets (turns/tool calls/tokens/time).
9. Validate final response; persist `ai_runs`/`ai_tool_calls` metadata; return `facts_used`, `proposals`, `limitations`.

`server/src/modules/intelligence/orchestrator.ts`

## 7. Proposal storage

`server/src/modules/intelligence/proposal-service.ts`

Fields: `payload_ciphertext`, `payload_hash`, `human_summary_ciphertext`, `resource_versions_json`, `status` (`pending|confirmed|executed|rejected|expired|superseded|failed`), `expires_at`, exactly-once execution, confirmation binding.

Cryptography: AES-256-GCM with `FIELD_ENCRYPTION_KEY_CURRENT`; SHA-256 canonical hash for binding.

## 8. Authorization mapping (summary)

Central permission module checks role → permission. Example:

- `viewer`: `medication:read`, `schedule:read`, `report:read`, `dose:read`, `drug:read`, `inventory:read`
- `contributor`: + `dose:write`
- `manager`: + `medication:write`, `schedule:write`, `inventory:write`, `alert:write`, `export:write`
- `owner`: + `membership:invite` and patient management

Every tool independently rechecks permission and patient scope.

## 9. Versioning

- Tool `version` (currently `v1`) stored in `ai_tool_calls.tool_version`.
- Output contracts versioned (`label_interpretation@v1`, etc.).
- Prompt templates versioned (`prompt-registry.ts`).
- Changes require migration tests and `AI_EVALUATIONS.md` fixtures.

## 10. Security invariants

- Reject unknown tools / extra args.
- Delimit untrusted text; never treat label/OCR/notes/tool output as instructions.
- No secrets in model context; no PHI in ordinary logs.
- Image MIME magic-byte verified; decoded size limited.
- Budget/rate/spend limits enforced before provider call.
