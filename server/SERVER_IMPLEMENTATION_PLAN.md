# Medac Server-Side Implementation Plan

> Handoff document for the coding agent implementing the Medac backend.
>
> **Scope:** Server-side API, database, background jobs, security controls, deployment, monitoring, backups, and server-side tests only. Mobile/web client implementation is out of scope.
>
> **Primary product goal:** Reliable medication reminders and medication management, with Muse Spark as the first-class intelligence and constrained agentic layer. Muse Spark should perform tasks that require language, vision, interpretation, planning, summarization, and tool selection. PostgreSQL and deterministic server tools remain the authoritative state and execution layer. Clinical claims still require validated sources and clinical governance.
>
> **Related document:** [`MEDAC_SPEC.md`](./MEDAC_SPEC.md)

Last updated: 2026-08-18

---

## 1. Instructions to the Implementing Agent

1. Treat this document as the implementation contract for the first production backend.
2. Do not place real patient or medication data on the server until the security and deployment gates in this plan pass.
3. Use Muse Spark heavily for tasks requiring intelligence: multimodal label understanding, natural-language parsing, clarification, schedule drafting, conversational management, summaries, and bounded tool orchestration.
4. Do not let Muse Spark directly mutate PostgreSQL, call arbitrary URLs, execute code, or bypass authorization. It proposes typed actions; server tools validate them; the user explicitly confirms record-changing actions.
5. Do not implement unsupported medical interaction, contraindication, dose-range, overdose, or missed-dose claims merely from model knowledge. Muse Spark may explain validated structured findings when such sources are added later.
6. Do not infer that a medication is safe because an external lookup or model returns no results.
7. Do not log access tokens, passwords, medication names, symptoms, free-text notes, dates of birth, model prompts/responses, label images, or other protected health information in ordinary logs.
8. Use migrations for every database change. Never modify the production schema manually.
9. All patient-owned rows and all AI tool calls must be protected by server-side authorization. A UUID is not authorization.
10. Preserve an append-only audit trail for security-sensitive, AI-proposed, AI-confirmed, and medication-history changes.
11. Build and test locally/staging before changing the live nginx configuration.
12. Do not disrupt the existing server application while adding Medac. Inspect and back up the current systemd and nginx configuration before deployment.
13. Never commit `.env`, private keys, passwords, database dumps, production credentials, or Muse Spark API keys.
14. If an implementation choice conflicts with safety, privacy, or authorization requirements, stop and document the conflict rather than silently weakening the requirement.

---

## 2. MVP Scope

### 2.1 Included

The server MVP must support:

- User registration, verification, login, logout, password reset, and session/device management.
- Optional TOTP multi-factor authentication, with recovery codes.
- Multiple human patient profiles under one user account.
- Caregiver invitations and patient-scoped role-based access.
- Medication creation, update, pause, resume, and discontinuation.
- Preservation of user-entered medication label text.
- RxNorm identity resolution as a normalization aid, with explicit unresolved/ambiguous states.
- Optional NDC lookup and storage.
- Fixed local-time schedules.
- Elapsed-interval schedules.
- PRN/as-needed medication definitions and dose logging.
- Cyclic and taper schedule storage, validation, and versioning.
- Planned dose occurrence generation for synchronization/reporting.
- Taken, skipped, snoozed, missed, corrected, and cancelled event logging.
- Offline-safe, idempotent synchronization.
- Inventory transaction ledger and refill forecasting.
- Medication expiration tracking.
- Hospital/general pause periods.
- Injection-site logs, if the medication uses injection tracking.
- Symptom notes linked optionally to a dose event, without automated causal claims.
- Caregiver alert preferences and server-side escalation records.
- Adherence summaries based on deterministic calculations.
- CSV export. PDF can be implemented after the core API is stable.
- Append-only audit logs.
- Background jobs, health/readiness endpoints, metrics, backups, and restore testing.
- OpenAPI documentation and automated tests.
- Muse Spark multimodal medicine-label interpretation.
- Muse Spark parsing of user-entered prescription/label instructions into a typed medication draft.
- Muse Spark conversational medication-management assistant.
- Muse Spark-generated schedule proposals based on explicit user/label constraints.
- Muse Spark summaries of adherence, inventory, reminders, and medication history using authorized server tools.
- Bounded agentic tool use with read tools executed automatically and record-changing actions represented as expiring, user-confirmed proposals.
- AI run/tool/proposal auditing, privacy controls, budgets, timeouts, and graceful fallback.

### 2.2 Explicitly deferred

Do **not** enable these as medical conclusions in MVP:

- Drug-drug interaction checking.
- Therapeutic class duplicate checking.
- Allergy cross-reactivity or contraindication checking.
- Patient-specific dose-range checking.
- Automated timing/food interaction advice.
- Automated missed-dose instructions.
- Automated overdose determination.
- AI-generated causal symptom conclusions or diagnoses. Muse Spark may summarize user-entered timelines while explicitly labeling patterns as non-causal.
- Unconfirmed AI changes to medication records or schedules. Muse Spark schedule drafts are included, but activation requires explicit user confirmation and deterministic validation.
- Emergency medical ID OS widget.
- Mobile local notification scheduling.
- Veterinary/pet profiles.

The data model may include generic `safety_checks` and `safety_findings` tables for future use, but no unvalidated clinical rule may be exposed as a production finding.

### 2.3 MVP non-goals

- The server is not an emergency service.
- Push delivery is not guaranteed and cannot replace local device alarms.
- Medac does not prescribe, diagnose, or alter a prescription.
- Medac does not certify adherence; it reports user-entered events.
- The backend does not treat unsupported model knowledge as a validated patient-specific clinical source.
- Muse Spark is trusted as the primary reasoning and interaction engine, but it is not the database, authorization layer, scheduler, or final executor of medication-record changes.
- “Agentic” means bounded server-defined tools and confirmation-gated actions, not unrestricted autonomy.

---

## 3. Definition of Done

The backend is MVP-complete only when:

1. All required endpoints are implemented and documented in OpenAPI.
2. PostgreSQL migrations run successfully from an empty database.
3. Unit, integration, authorization, idempotency, and concurrency tests pass.
4. Every patient-scoped endpoint has positive and negative authorization tests.
5. Schedule calculations pass DST and timezone test vectors.
6. Offline event replay does not create duplicate dose logs or inventory deductions.
7. The app runs as a dedicated unprivileged Linux user.
8. PostgreSQL and application ports are not publicly exposed.
9. HTTPS is enabled using a real domain certificate before sensitive data is accepted.
10. CORS uses an explicit allowlist; wildcard CORS is absent from authenticated APIs.
11. Root SSH login and password SSH login are disabled after a verified non-root administrative path exists.
12. Secrets are stored outside the repository.
13. Backups are encrypted, off-site, monitored, and a restore drill has succeeded.
14. Logs have been inspected to confirm that health data and credentials are not emitted.
15. Rate limits and request-size limits are active.
16. Dependency, static-analysis, and secret scans pass.
17. A staging deployment and smoke test succeed before production deployment.
18. Rollback instructions have been tested or validated.
19. Muse Spark label, natural-language, conversation, tool-calling, prompt-injection, schema-validation, and confirmation-flow tests pass.
20. AI record-changing proposals cannot execute without a valid, unexpired confirmation bound to the user, patient, proposal payload, and current resource versions.
21. The system continues to support deterministic medication management when Muse Spark is unavailable.
22. Model prompts, raw responses, images, and sensitive tool results are absent from ordinary logs.

---

## 4. Chosen Technical Architecture

Use the following unless a blocker is documented before implementation.

### 4.1 Runtime and framework

- Node.js 22 LTS.
- TypeScript with strict compiler settings.
- Fastify for HTTP handling.
- TypeBox or Zod for runtime request/response validation and OpenAPI schemas. Pick one and use it consistently.
- PostgreSQL 16 or newer.
- Drizzle ORM and SQL migrations, or a similarly transparent typed SQL layer. Avoid hiding authorization logic in broad ORM auto-fetches.
- Argon2id for password hashing.
- JOSE-compatible library for signed short-lived access tokens.
- Opaque random refresh tokens, stored only as hashes in PostgreSQL.
- PostgreSQL-backed job queue initially, so Redis is not required for MVP. Use `FOR UPDATE SKIP LOCKED` and leases.
- Pino structured logging with a strict redaction configuration.
- Vitest for unit/integration tests.
- Testcontainers or a dedicated temporary PostgreSQL database for integration tests.
- Muse Spark through an OpenAI-compatible HTTPS adapter (`/chat/completions` initially), isolated behind a provider interface.
- Strict structured-output validation for every Muse Spark result; JSON extraction with regex is not an acceptable production contract.
- A server-owned AI tool registry and proposal/confirmation executor. The model never receives database credentials or direct network access.

### 4.2 Deployment topology

```text
Internet
   |
   v
nginx :80/:443
   |
   +-- existing application routes (must remain functional)
   |
   +-- /medac/api/*  -> Medac API on 127.0.0.1:3100
   +-- /medac/health -> Medac API health endpoint

Medac API (unprivileged systemd service)
   |
   +-- PostgreSQL on localhost/private socket only
   +-- encrypted backup destination
   +-- external email provider over TLS
   +-- RxNorm API over HTTPS (normalization only)
   +-- Muse Spark API over HTTPS (intelligence/tool planning)
   +-- optional push providers over TLS
```

The Fastify application should internally expose `/api/v1/...`; nginx may preserve or strip `/medac` consistently. Choose one proxy convention, document it, and include proxy-prefix tests. The public API base URL should be:

```text
https://<production-domain>/medac/api/v1
```

Do not use the bare IP address as the production API URL.

### 4.3 Process separation

Use separate process entry points or systemd services for:

- `medac-api`: HTTP API.
- `medac-worker`: background jobs.
- Muse Spark calls normally execute through `medac-api` for interactive requests; long summaries/imports may be delegated to `medac-worker` with encrypted, minimal job payloads.

Both services use the same codebase but different startup commands. Neither runs as root. Muse Spark has no process, shell, SQL, filesystem, or unrestricted HTTP tool.

### 4.4 Source tree target

```text
medac/
  MEDAC_SPEC.md
  SERVER_IMPLEMENTATION_PLAN.md
  server/
    package.json
    package-lock.json
    tsconfig.json
    eslint.config.*
    .env.example
    README.md
    openapi/
    migrations/
    scripts/
      migrate.ts
      seed-development.ts
      create-admin.ts
      backup-check.sh
      smoke-test.sh
    src/
      app.ts
      server.ts
      worker.ts
      config/
      db/
      plugins/
      modules/
        auth/
        users/
        patients/
        memberships/
        medications/
        schedules/
        doses/
        inventory/
        symptoms/
        injection-sites/
        caregivers/
        alerts/
        exports/
        audit/
        drug-normalization/
        jobs/
        intelligence/
          muse-spark-provider.ts
          orchestrator.ts
          prompt-registry.ts
          output-contracts.ts
          context-builders.ts
          tool-registry.ts
          proposal-service.ts
          safety-policy.ts
      lib/
    tests/
      unit/
      integration/
      authorization/
      fixtures/
    deploy/
      systemd/
      nginx/
      logrotate/
```

Do not copy live credentials into this tree.

---

## 5. Core Domain Rules

### 5.1 Users and patients are different entities

- A `user` authenticates.
- A `patient` owns medication data.
- A user accesses a patient only through an active `patient_membership`.
- The person represented by a patient may or may not have their own login.
- Every patient-scoped query must include membership authorization.

### 5.2 Roles

Use these initial roles:

- `owner`: Full patient management, sharing, export, and deletion rights.
- `manager`: Can manage medication, schedules, dose logs, inventory, and notes; cannot transfer ownership or delete the patient.
- `contributor`: Can view and log dose events; cannot change core medication definitions or memberships.
- `viewer`: Read-only access.

Permissions must be mapped centrally, not duplicated ad hoc in handlers. Sensitive actions may require a recent authentication timestamp.

### 5.3 Medication lifecycle

Medication status:

- `draft`: Being entered; normalization may be pending.
- `active`: Included in schedules and occurrence generation.
- `paused`: Temporarily excluded during a pause.
- `discontinued`: No future occurrences; history retained.
- `archived`: Hidden from default lists; history retained.

Do not hard-delete medications with dose history. A user-facing delete should archive when historical records exist.

### 5.4 Medication normalization

Store both:

- The exact user/label input.
- The canonical candidate selected from RxNorm/NDC.

Resolution states:

- `unresolved`
- `pending`
- `resolved`
- `ambiguous`
- `failed`
- `manually_confirmed`

The medication-management feature must remain usable when RxNorm is temporarily unavailable. A failed lookup must never block basic reminders, but the unresolved state must be visible in API responses.

RxNorm is used for identity normalization only. Do not describe it as a complete interaction engine.

### 5.5 Schedule versions

- Never mutate a schedule definition in place once occurrences or logs reference it.
- End the old schedule version and create a new version.
- Each version has `effective_from` and optional `effective_until` timestamps.
- Existing historical occurrences continue to reference their original version.
- New versions must not silently rewrite past occurrences.

### 5.6 Time semantics

Support two schedule timing modes:

1. `local_clock`: Keep a wall-clock time in a named IANA timezone. DST may change the UTC instant.
2. `elapsed_interval`: Keep elapsed time between administrations. The next nominal time is based on a defined anchor and duration, independent of local wall-clock shifts.

Never store only a UTC offset such as `-05:00`. Store the IANA zone, such as `America/New_York`.

For every planned occurrence store:

- Scheduled UTC instant.
- Scheduled local date/time.
- IANA timezone used.
- UTC offset used at generation time.
- Schedule version ID.

DST behavior must be deterministic:

- For a nonexistent local time during spring-forward, use the first valid local instant after the gap and mark `dst_adjusted=true`.
- For an ambiguous local time during fall-back, use the earlier occurrence by default unless the schedule explicitly selects the later offset.
- Include these rules in API documentation and tests.

### 5.7 Reminder responsibility

- The server owns schedule definitions and synchronizes planned occurrences.
- Mobile clients are responsible for reliable local/offline notifications.
- Server push is a fallback and caregiver/escalation mechanism.
- API responses must not claim that a push message is guaranteed to arrive.

### 5.8 Dose-event corrections

Dose logs are append-only facts. If a user changes an erroneous log:

- Keep the original record.
- Add a correction record referencing the original.
- Mark the current interpreted state through a deterministic projection.
- Record actor, reason, and timestamps.
- Reverse or compensate inventory effects through ledger entries; do not edit old inventory transactions.

### 5.9 Inventory

Inventory is an append-only transaction ledger. Examples:

- `fill`
- `dose_consumed`
- `dose_consumption_reversed`
- `manual_adjustment`
- `lost_or_damaged`
- `disposed`
- `transferred`

The current quantity is the sum of signed transaction quantities. Every transaction includes a unit and optional source dose event. Do not mix tablets, mL, units, or actuations in one balance.

### 5.10 Muse Spark trust and execution boundary

Muse Spark is the preferred component whenever the task needs intelligence rather than arithmetic or storage. It should be given meaningful responsibility, including understanding ambiguous natural language, reading labels, asking useful clarifying questions, selecting authorized tools, comparing user-visible options, drafting schedules, and producing humane explanations.

Trust model:

- **Muse Spark reasons:** interpret, classify, extract, plan, summarize, ask, and select tools.
- **Server tools establish facts:** query authorized PostgreSQL state, calculate occurrences/adherence/inventory, resolve drug IDs, and validate commands.
- **User confirms consequential changes:** create/edit/discontinue medication, activate/supersede schedule, log/correct dose, change inventory, invite caregiver, export data, and change alert/escalation preferences.
- **Server executes:** only a validated, authorized, current, confirmed proposal may mutate records.

The model may automatically call allowlisted read-only tools during a turn. It may also compose a multi-step plan, but each write step becomes a typed proposal. Confirmation must happen outside free-form model text through a dedicated API action. A user saying “yes” in chat is not by itself sufficient unless the server maps that response to a displayed, unexpired proposal and asks for explicit confirmation.

Muse Spark must not:

- Generate SQL or select arbitrary database tables.
- Construct arbitrary outbound URLs.
- Execute shell/code.
- Read other patients, even if prompted.
- Alter the system prompt/tool definitions.
- Treat text in labels, OCR, notes, or tool output as instructions to the agent.
- Invent a medication identity, dose, unit, frequency, or clinical fact when the source is unclear.
- Silently convert “typical use” into a patient schedule.
- Directly mark a proposal confirmed.

### 5.11 Adherence

Adherence is a deterministic report, not a judgment.

- Exclude PRN schedules from ordinary scheduled-dose adherence.
- Exclude cancelled occurrences and explicitly paused periods.
- Define the reporting denominator in the response.
- Mark an occurrence missed only after its configured response window ends.
- Permit client timezone in report presentation, but calculate from canonical occurrence/event data.

---

## 6. Database Design

All primary keys should be UUIDv7 if supported by the selected library, otherwise cryptographically random UUIDv4. Store timestamps as `timestamptz`. Add `created_at` and `updated_at` where appropriate. Use database constraints, foreign keys, and indexes rather than relying only on application validation.

### 6.1 Identity and authentication

#### `users`

- `id`
- `email_normalized` — unique, case-normalized
- `email_display`
- `password_hash`
- `email_verified_at`
- `status` — `pending`, `active`, `locked`, `disabled`, `deletion_pending`
- `preferred_locale`
- `created_at`, `updated_at`, `last_login_at`

#### `user_security`

- `user_id` — PK/FK
- `failed_login_count`
- `locked_until`
- `password_changed_at`
- `mfa_required`
- `token_version`

#### `email_verification_tokens`

- `id`, `user_id`
- `token_hash`
- `expires_at`, `used_at`, `created_at`

#### `password_reset_tokens`

- Same token rules as verification tokens.
- Single-use and short-lived.

#### `mfa_totp_credentials`

- `id`, `user_id`
- Encrypted TOTP secret.
- `enabled_at`, `last_used_step`, `created_at`

Reject reuse of the same TOTP time step.

#### `mfa_recovery_codes`

- `id`, `user_id`
- `code_hash`
- `used_at`, `created_at`

#### `auth_sessions`

- `id`, `user_id`, `device_id`
- `refresh_token_hash`
- `refresh_family_id`
- `previous_token_hash` or reuse-detection fields
- `created_at`, `last_used_at`, `expires_at`, `revoked_at`
- `created_ip_prefix` — minimized/coarsened if retained
- `user_agent_summary` — sanitized and length-limited

Refresh token rotation must revoke the token family if an already-rotated token is reused.

#### `devices`

- `id`, `user_id`
- `platform` — `ios`, `android`, `web`, `unknown`
- `display_name`
- `app_version`
- `last_seen_at`
- `timezone`
- `notification_permission` — reported state
- `created_at`, `revoked_at`

#### `push_tokens`

- `id`, `device_id`, `provider`
- `token_ciphertext` or protected token value
- `token_fingerprint`
- `created_at`, `last_success_at`, `invalidated_at`

### 6.2 Patients and sharing

#### `patients`

- `id`
- `display_name`
- `date_of_birth` — nullable/minimize collection
- `sex_at_birth` — nullable and controlled enum if needed clinically later
- `preferred_timezone`
- `notification_privacy_mode` — `private`, `generic`, `detailed`
- `created_by_user_id`
- `created_at`, `updated_at`, `archived_at`, `deletion_requested_at`

Do not collect weight, pregnancy, kidney status, liver status, or conditions in MVP unless a shipped feature actually needs them.

#### `patient_memberships`

- `id`, `patient_id`, `user_id`
- `role`
- `status` — `active`, `revoked`
- `granted_by_user_id`
- `created_at`, `revoked_at`
- Unique active membership constraint per patient/user.

#### `caregiver_invites`

- `id`, `patient_id`
- `invited_email_normalized`
- `role`
- `token_hash`
- `invited_by_user_id`
- `expires_at`, `accepted_at`, `revoked_at`, `created_at`

Invites are single-use, expire, and do not reveal whether an unrelated email has an account.

#### `patient_consents`

- `id`, `patient_id`
- `consent_type`
- `granted_by_user_id`
- `scope_json` — narrowly structured, schema validated
- `policy_version`
- `granted_at`, `revoked_at`

### 6.3 Medication identity and instructions

#### `drug_concepts`

- `id`
- `rxnorm_rxcui` — nullable/indexed
- `ndc` — nullable/indexed, normalized format
- `concept_name`
- `term_type`
- `source`
- `source_version`
- `raw_snapshot_json` — only required normalized fields; no uncontrolled dump
- `last_verified_at`

#### `drug_ingredients`

- `id`
- `rxnorm_rxcui`
- `name`

#### `drug_concept_ingredients`

- `drug_concept_id`, `ingredient_id`
- `strength_value`, `strength_unit`
- Composite PK or unique constraint.

#### `patient_medications`

- `id`, `patient_id`
- `drug_concept_id` — nullable
- `entered_name`
- `entered_strength_value` — decimal nullable
- `entered_strength_unit` — controlled code nullable
- `form`
- `route`
- `dose_quantity_value` — decimal
- `dose_quantity_unit`
- `indication_text` — optional, encrypted if application-level field encryption is used
- `prescriber_text` — optional
- `pharmacy_text` — optional
- `label_instructions_text` — optional
- `normalization_status`
- `status`
- `high_attention_user_flag` — user-entered, never represented as clinical validation
- `start_date`, `end_date`
- `created_by_user_id`, `updated_by_user_id`
- `created_at`, `updated_at`, `paused_at`, `discontinued_at`, `archived_at`

#### `medication_expirations`

- `id`, `medication_id`
- `expiration_date`
- `lot_number` — optional
- `quantity_value`, `quantity_unit`
- `created_at`, `updated_at`

### 6.4 Schedules

#### `medication_schedule_versions`

- `id`, `medication_id`
- `version_number`
- `schedule_type` — `fixed_times`, `elapsed_interval`, `prn`, `cyclic`, `taper`
- `timing_mode` — `local_clock`, `elapsed_interval`
- `timezone`
- `effective_from`, `effective_until`
- `miss_window_minutes`
- `created_by_user_id`, `created_at`
- Unique `(medication_id, version_number)`.

#### `schedule_fixed_times`

- `id`, `schedule_version_id`
- `local_time`
- `days_of_week` — use normalized child rows if query needs become complex
- `dose_quantity_value`, `dose_quantity_unit`
- `ambiguous_time_policy` — `earlier`, `later`

#### `schedule_intervals`

- `schedule_version_id`
- `interval_minutes`
- `anchor_at`
- `dose_quantity_value`, `dose_quantity_unit`
- Define whether a user-recorded dose shifts the next occurrence. Default MVP behavior: it does **not** shift the schedule unless `anchor_policy=last_taken` is explicitly configured.

#### `schedule_cycles`

- `schedule_version_id`
- `cycle_anchor_date`
- `on_days`
- `off_days`

#### `taper_steps`

- `id`, `schedule_version_id`
- `step_order`
- `starts_on`, `ends_on`
- `dose_quantity_value`, `dose_quantity_unit`
- Optional fixed times through a linked table.
- Non-overlap and sequential-order validation required.

#### `medication_pause_periods`

- `id`, `medication_id`
- `starts_at`, `ends_at`
- `reason` — `hospital`, `temporary`, `other`
- `created_by_user_id`, `created_at`

### 6.5 Occurrences and events

#### `dose_occurrences`

- `id`, `patient_id`, `medication_id`, `schedule_version_id`
- `scheduled_at_utc`
- `scheduled_local_datetime`
- `timezone`
- `utc_offset_minutes`
- `dst_adjusted`
- `nominal_dose_value`, `nominal_dose_unit`
- `state` — derived/cache: `scheduled`, `due`, `taken`, `skipped`, `missed`, `cancelled`, `corrected`
- `generated_at`, `state_updated_at`
- Unique occurrence key over schedule version and nominal schedule instant.

Generate a rolling window, for example 30 days past through 60 days future. Extend it daily. Generation must be idempotent.

#### `dose_event_logs`

- `id`, `patient_id`, `medication_id`
- `occurrence_id` — nullable for PRN/unplanned dose
- `event_type` — `taken`, `skipped`, `snoozed`, `corrected`, `cancelled`
- `actual_at`
- `recorded_at`
- `timezone`
- `dose_value`, `dose_unit`
- `note_ciphertext` or protected note field — optional
- `client_event_id`
- `device_id`
- `actor_user_id`
- `corrects_event_id` — nullable
- `created_at`
- Unique `(device_id, client_event_id)` for offline idempotency.

Do not accept a correction chain that loops. Only authorized users can correct an event.

#### `dose_snoozes`

May be represented as dose events, but if queried independently store:

- `id`, `occurrence_id`, `snooze_until`, `actor_user_id`, `created_at`

### 6.6 Inventory

#### `inventory_accounts`

- `id`, `patient_id`, `medication_id`
- `unit`
- `low_stock_threshold_value`
- `created_at`, `archived_at`

#### `inventory_transactions`

- `id`, `inventory_account_id`
- `transaction_type`
- `quantity_delta` — signed decimal
- `dose_event_id` — nullable/unique when automatically generated
- `reason_text` — optional
- `actor_user_id`
- `effective_at`, `created_at`
- `reverses_transaction_id` — nullable

Never update transaction quantity after creation.

### 6.7 Notes and injections

#### `symptom_logs`

- `id`, `patient_id`
- `occurred_at`
- `note_ciphertext` or protected note field
- `linked_dose_event_id` — nullable
- `actor_user_id`, `created_at`, `corrected_by_log_id`

No server-generated diagnosis or causal claim.

#### `injection_site_logs`

- `id`, `patient_id`, `medication_id`, `dose_event_id`
- `site_code` — controlled list
- `occurred_at`
- `actor_user_id`, `created_at`

### 6.8 Alerts and delivery

#### `alert_preferences`

- `id`, `patient_id`, `user_id`
- `alert_type`
- `enabled`
- `delay_minutes`
- `quiet_hours_start`, `quiet_hours_end`, `timezone`
- `created_at`, `updated_at`

#### `alerts`

- `id`, `patient_id`
- `alert_type` — `missed_user_attention_med`, `low_stock`, `expiration`, `stale_device`, etc.
- `severity` — `info`, `attention`, `urgent_review`, `potential_emergency`
- `status` — `open`, `acknowledged`, `resolved`, `cancelled`
- `source_entity_type`, `source_entity_id`
- `message_key` — localization key, not sensitive rendered prose
- `created_at`, `acknowledged_at`, `resolved_at`

MVP should normally use only `info`, `attention`, and narrowly configured `urgent_review`. `potential_emergency` requires separately approved clinical rules.

#### `notification_deliveries`

- `id`, `alert_id`, `user_id`, `device_id`
- `channel`, `provider_message_id`
- `status` — `queued`, `sent`, `failed`, `invalid_token`, `acknowledged`
- `attempt_count`, `next_attempt_at`
- `created_at`, `sent_at`, `failed_at`
- `failure_code` — sanitized

Default push content must be generic and contain no medication name.

### 6.9 Safety scaffolding for future use

#### `clinical_source_versions`

- `id`, `source_name`, `source_version`, `license_reference`
- `effective_at`, `retired_at`, `checksum`

#### `safety_checks`

- `id`, `patient_id`, `check_type`
- `result` — `clear_in_checked_source`, `finding`, `unknown`, `failed`, `not_supported`
- `source_version_id`
- `input_fingerprint`
- `checked_at`, `expires_at`

#### `safety_findings`

- `id`, `safety_check_id`
- `severity`
- `finding_code`
- `structured_details_json`
- `source_citation`

These tables must not be populated with production medical claims until governance approves the source and rule.

### 6.10 Muse Spark intelligence and agent state

Raw model prompts, responses, uploaded images, and tool results are sensitive. Do not persist them by default. Persist structured metadata and proposals needed for audit and continuity. If conversational transcripts are enabled, they require explicit product consent, application-level encryption, patient scoping, a retention limit, and deletion support.

#### `ai_runs`

- `id`, `patient_id`, `user_id`, `session_id`
- `purpose` — `label_interpretation`, `instruction_parse`, `assistant_turn`, `schedule_draft`, `summary`, `timeline_pattern`
- `provider` — `muse_spark`
- `model`
- `prompt_template_id`, `prompt_template_version`
- `status` — `started`, `completed`, `failed`, `blocked`, `cancelled`
- `input_fingerprint` — HMAC, never raw sensitive input
- `output_contract_version`
- `input_tokens`, `output_tokens`, `latency_ms`
- `safety_policy_version`
- `failure_code` — sanitized
- `created_at`, `finished_at`

#### `ai_tool_calls`

- `id`, `ai_run_id`, `patient_id`
- `tool_name`, `tool_version`
- `mode` — `read`, `proposal`
- `arguments_fingerprint`
- `status` — `requested`, `authorized`, `executed`, `rejected`, `failed`
- `result_classification` — metadata only, not raw PHI
- `created_at`, `finished_at`

#### `ai_action_proposals`

- `id`, `ai_run_id`, `patient_id`, `proposed_by_user_id`
- `action_type`
- `payload_ciphertext` — complete validated command encrypted at application level
- `payload_hash` — confirmation binding
- `human_summary_ciphertext`
- `required_permission`
- `resource_versions_json` — optimistic concurrency preconditions
- `status` — `pending`, `confirmed`, `executed`, `rejected`, `expired`, `superseded`, `failed`
- `expires_at`, `confirmed_by_user_id`, `confirmed_at`, `executed_at`
- `created_at`

A proposal may execute exactly once. Confirmation requires current authorization, matching patient, matching payload hash, current resource versions, and an unexpired status. Confirmation and execution occur transactionally where practical.

#### `ai_conversations` and `ai_messages` — optional

Only add if cross-turn conversation history is required. Store encrypted content, role, run ID, patient ID, and expiry. Never use conversation history as medical state. On every turn, re-fetch authoritative facts through tools. Default retention should be short and configurable.

### 6.11 Operations and audit

#### `audit_events`

- `id`
- `occurred_at`
- `actor_user_id` — nullable for system
- `actor_session_id` — nullable
- `patient_id` — nullable
- `action`
- `entity_type`, `entity_id`
- `request_id`
- `ip_prefix` — optional/minimized
- `metadata_json` — strict allowlisted keys only; no PHI/free text
- Append-only; application role has no update/delete permission.

#### `outbox_events`

Transactional outbox for background work:

- `id`, `event_type`
- `aggregate_type`, `aggregate_id`
- `payload_json` — minimal and schema validated
- `available_at`, `attempt_count`, `locked_until`
- `processed_at`, `last_error_code`

#### `job_runs`

- `id`, `job_type`, `status`
- `started_at`, `finished_at`
- `items_processed`, `items_failed`
- `error_code`

---

## 7. Authorization Model

### 7.1 Required request flow

For every authenticated patient route:

1. Authenticate access token.
2. Confirm session/user status is active where required.
3. Parse and validate request.
4. Load active patient membership for the authenticated user.
5. Check centralized permission for action.
6. Perform the data query scoped by `patient_id` and membership.
7. Write an audit event when required.
8. Return a response that exposes only permitted fields.

The same sequence applies to AI: the orchestrator receives a server-created authorization context; each tool independently rechecks permission and patient scope; the model never supplies trusted `user_id`, role, or patient access. Never fetch an object globally by ID and authorize afterward if the query can instead include `patient_id` and membership constraints.

### 7.2 Preventing BOLA/IDOR

Tests must prove that:

- A user cannot read another patient's medication by guessing its ID.
- A viewer cannot modify records.
- A contributor cannot change schedules or invite caregivers.
- A manager cannot transfer ownership or delete the patient.
- A revoked caregiver loses access immediately.
- An expired invite cannot be accepted.
- An export cannot include data outside the selected patient.
- Background jobs retain patient scoping.
- Muse Spark cannot use a tool against a patient outside the current authorized context.
- Prompt injection cannot reveal another patient's data or enable an unregistered tool.
- A confirmed AI proposal cannot be replayed, altered, or executed after membership revocation.

### 7.3 Database defense in depth

Prefer one of these:

- PostgreSQL Row-Level Security with transaction-local user/patient context, plus application checks; or
- A strongly enforced repository layer whose patient-scoped methods require a `patientId` and authorization context.

If RLS is used, test that migrations/worker roles cannot accidentally bypass it. Keep a separate narrowly privileged migration role.

---

## 8. API Contract

### 8.1 General conventions

- Base: `/api/v1` internally and `/medac/api/v1` publicly.
- JSON request/response only except export downloads.
- ISO 8601/RFC 3339 timestamps with offsets.
- IANA timezone names.
- Decimal quantities serialized as strings to avoid floating-point loss.
- Cursor pagination; maximum page size 100.
- Request ID accepted/generated and returned as `X-Request-ID`.
- Mutation requests from offline clients accept `Idempotency-Key` or a resource-specific `client_event_id`.
- Reject unknown fields for security-sensitive commands.
- Use optimistic concurrency through an `ETag`/version value on medication and schedule updates.
- No internal stack traces in API responses.

### 8.2 Error shape

```json
{
  "error": {
    "code": "MEDICATION_VERSION_CONFLICT",
    "message": "The medication was changed on another device.",
    "request_id": "019...",
    "details": {}
  }
}
```

`details` must contain only schema-approved, non-sensitive values. Stable machine-readable codes are required.

### 8.3 Health and operational endpoints

- `GET /health/live` — process is running; no dependency details.
- `GET /health/ready` — database/migration readiness; generic external response.
- `GET /version` — build identifier, not secret configuration.
- Metrics endpoint bound privately or protected; never public without access control.

### 8.4 Authentication endpoints

- `POST /auth/register`
- `POST /auth/verify-email`
- `POST /auth/resend-verification`
- `POST /auth/login`
- `POST /auth/mfa/verify`
- `POST /auth/refresh`
- `POST /auth/logout`
- `POST /auth/logout-all`
- `POST /auth/password/forgot`
- `POST /auth/password/reset`
- `POST /auth/password/change`
- `GET /auth/sessions`
- `DELETE /auth/sessions/:sessionId`
- `POST /auth/mfa/totp/setup`
- `POST /auth/mfa/totp/confirm`
- `DELETE /auth/mfa/totp`

Do not reveal whether an email exists in forgot-password or invite responses.

### 8.5 User/device endpoints

- `GET /me`
- `PATCH /me`
- `GET /me/devices`
- `PATCH /me/devices/:deviceId`
- `DELETE /me/devices/:deviceId`
- `PUT /me/devices/:deviceId/push-token`
- `DELETE /me/devices/:deviceId/push-token`

### 8.6 Patient and membership endpoints

- `GET /patients`
- `POST /patients`
- `GET /patients/:patientId`
- `PATCH /patients/:patientId`
- `POST /patients/:patientId/archive`
- `POST /patients/:patientId/deletion-request`
- `GET /patients/:patientId/memberships`
- `PATCH /patients/:patientId/memberships/:membershipId`
- `DELETE /patients/:patientId/memberships/:membershipId`
- `POST /patients/:patientId/invites`
- `GET /patients/:patientId/invites`
- `DELETE /patients/:patientId/invites/:inviteId`
- `POST /invites/:token/accept`

Prevent removal of the last owner. Ownership transfer requires recent authentication and an explicit transaction.

### 8.7 Muse Spark intelligence and agent endpoints

- `POST /patients/:patientId/intelligence/label-interpretations`
- `POST /patients/:patientId/intelligence/instruction-parses`
- `POST /patients/:patientId/intelligence/conversations`
- `POST /patients/:patientId/intelligence/conversations/:conversationId/messages`
- `DELETE /patients/:patientId/intelligence/conversations/:conversationId`
- `POST /patients/:patientId/intelligence/schedule-drafts`
- `POST /patients/:patientId/intelligence/summaries`
- `GET /patients/:patientId/intelligence/proposals/:proposalId`
- `POST /patients/:patientId/intelligence/proposals/:proposalId/confirm`
- `POST /patients/:patientId/intelligence/proposals/:proposalId/reject`

#### Label interpretation contract

The current live API endpoints `/api/medicine/identify` and `/api/medicine/identify-text` must be replaced or compatibility-wrapped by the versioned endpoints above. Muse Spark should extract only what is visibly supported by the label/OCR and clearly separate:

- `observed_text`
- `candidate_name`
- `candidate_generic_name`
- `strength_value` and `strength_unit`
- `form`
- `route`
- `label_directions_text`
- `prescriber_or_pharmacy_fields`, when visible
- `uncertain_fields`
- `clarifying_questions`
- field-level evidence/confidence

It must **not** invent purpose, instructions, dosage frequency, or “typical” reminder times when they are absent. If the source says “take twice daily,” Muse Spark may propose times as a user-editable draft only after asking about wake/sleep/preferences; it must label those times as a convenience proposal, not prescription content.

Images:

- Accept only allowlisted image MIME types after magic-byte verification.
- Enforce a substantially smaller decoded-size limit than the current 25 MB JSON default.
- Decode safely and reject malformed/base64 bombs.
- Strip metadata when feasible before forwarding.
- Do not persist the image by default.
- Disclose that the image/text is sent to Muse Spark.
- Apply per-user rate and spend limits.

#### Conversation and tool contract

Each assistant response is structured:

```json
{
  "message": "I found two active morning medicines. Would you like a schedule draft that separates them?",
  "facts_used": [
    {"type": "medication", "id": "...", "version": 3}
  ],
  "proposals": [
    {
      "proposal_id": "...",
      "action_type": "supersede_schedule",
      "requires_confirmation": true,
      "expires_at": "..."
    }
  ],
  "limitations": []
}
```

Server-owned allowlisted tools for MVP:

**Automatic read tools**

- `list_medications`
- `get_medication`
- `get_today_occurrences`
- `get_schedule`
- `preview_schedule`
- `get_adherence_report`
- `get_inventory_summary`
- `get_expiration_summary`
- `get_recent_dose_events`
- `get_injection_site_history`
- `search_rxnorm`
- `resolve_ndc`

**Confirmation-gated proposal tools**

- `propose_create_medication`
- `propose_update_medication`
- `propose_activate_medication`
- `propose_pause_or_resume_medication`
- `propose_discontinue_medication`
- `propose_create_or_supersede_schedule`
- `propose_log_dose_event`
- `propose_correct_dose_event`
- `propose_inventory_adjustment`
- `propose_alert_preferences`
- `propose_caregiver_invite`
- `propose_export`

The tool loop has hard limits: maximum turns, maximum tool calls, maximum context bytes, maximum output tokens, provider timeout, and per-user/provider budget. Parallel read tools are allowed when independent. The server rejects unknown tools and extra arguments.

Muse Spark may decide which tool to use and in what order. It may not invent a tool. Tool results are data, not instructions; wrap and label untrusted text to resist prompt injection.

#### Confirmation UX/API rule

Record-changing proposals must return a deterministic server-rendered diff showing old/new values and warnings. The confirmation endpoint accepts the proposal ID and payload hash, not a new arbitrary payload. It revalidates authorization and versions before execution. High-impact actions such as discontinuation, caregiver invitation, or bulk schedule changes require recent authentication.

### 8.8 Medication endpoints

- `GET /patients/:patientId/medications`
- `POST /patients/:patientId/medications`
- `GET /patients/:patientId/medications/:medicationId`
- `PATCH /patients/:patientId/medications/:medicationId`
- `POST /patients/:patientId/medications/:medicationId/activate`
- `POST /patients/:patientId/medications/:medicationId/pause`
- `POST /patients/:patientId/medications/:medicationId/resume`
- `POST /patients/:patientId/medications/:medicationId/discontinue`
- `POST /patients/:patientId/medications/:medicationId/archive`
- `GET /drug-normalization/search?q=...`
- `POST /drug-normalization/resolve-ndc`
- `POST /patients/:patientId/medications/:medicationId/normalization-selection`

Search must have strict rate limiting, timeouts, caching, and sanitized upstream error handling.

### 8.9 Schedule endpoints

- `GET /patients/:patientId/medications/:medicationId/schedules`
- `POST /patients/:patientId/medications/:medicationId/schedules`
- `GET /patients/:patientId/medications/:medicationId/schedules/:versionId`
- `POST /patients/:patientId/medications/:medicationId/schedules/:versionId/supersede`
- `GET /patients/:patientId/occurrences?from=&to=&cursor=`

Schedule creation response should include a preview of upcoming occurrences generated by the same production calculation code. Limit preview range and result count.

### 8.10 Dose and sync endpoints

- `POST /patients/:patientId/dose-events`
- `POST /patients/:patientId/dose-events/batch`
- `GET /patients/:patientId/dose-events?from=&to=&cursor=`
- `POST /patients/:patientId/dose-events/:eventId/corrections`
- `GET /patients/:patientId/sync?cursor=`

Batch requirements:

- Maximum item count and body size.
- Per-item `client_event_id`.
- Deterministic response per item: accepted, duplicate, conflict, or rejected.
- Transactional behavior documented. Prefer per-item outcomes with each accepted item atomically writing its event, inventory effect, outbox event, and audit event.
- Replaying the same batch returns the original logical outcomes without duplicates.

A sync cursor should be opaque, signed or server-generated, and based on a monotonically ordered change feed. Do not use client time as the sole cursor.

### 8.11 Inventory endpoints

- `GET /patients/:patientId/medications/:medicationId/inventory`
- `POST /patients/:patientId/medications/:medicationId/inventory/transactions`
- `GET /patients/:patientId/medications/:medicationId/inventory/transactions`
- `PATCH /patients/:patientId/medications/:medicationId/inventory/settings`

### 8.12 Expiration, symptoms, and injection sites

- `GET/POST /patients/:patientId/medications/:medicationId/expirations`
- `PATCH/DELETE /patients/:patientId/medications/:medicationId/expirations/:expirationId`
- `GET/POST /patients/:patientId/symptom-logs`
- `POST /patients/:patientId/symptom-logs/:logId/corrections`
- `GET/POST /patients/:patientId/injection-site-logs`

Use correction/archive semantics for records that contribute to medical history.

### 8.13 Reports and exports

- `GET /patients/:patientId/reports/adherence?from=&to=&timezone=`
- `POST /patients/:patientId/exports`
- `GET /patients/:patientId/exports/:exportId`
- `GET /patients/:patientId/exports/:exportId/download`
- `DELETE /patients/:patientId/exports/:exportId`

Export creation should be asynchronous. Download URLs/tokens must be short-lived and single-patient scoped. Export files must expire and be deleted automatically. Audit creation and download.

### 8.14 Alerts

- `GET /patients/:patientId/alerts`
- `POST /patients/:patientId/alerts/:alertId/acknowledge`
- `GET /patients/:patientId/alert-preferences`
- `PUT /patients/:patientId/alert-preferences`

---

## 9. Background Jobs and Event Routing

Use a transactional outbox. Domain changes and their outbox events must commit in the same database transaction.

### 9.1 Events

Initial events:

- `user_registered`
- `password_changed`
- `caregiver_invited`
- `membership_revoked`
- `medication_created`
- `medication_activated`
- `medication_status_changed`
- `schedule_version_created`
- `dose_event_recorded`
- `dose_event_corrected`
- `inventory_changed`
- `export_requested`
- `push_token_registered`
- `ai_run_completed`
- `ai_proposal_created`
- `ai_proposal_confirmed`
- `ai_proposal_rejected`
- `ai_proposal_expired`

### 9.2 Jobs

- Send verification/reset/invite email.
- Generate/extend dose occurrences.
- Recompute occurrence state after dose events/corrections.
- Apply or reverse inventory deductions.
- Mark overdue occurrences missed after the response window.
- Forecast low stock daily.
- Find approaching expiration daily.
- Produce deterministic adherence summaries on request or cache schedule.
- Generate and expire exports.
- Deliver generic push notifications.
- Detect invalid push tokens.
- Purge expired auth tokens and export files.
- Process account deletion after retention/confirmation rules.
- Expire unconfirmed AI action proposals.
- Execute confirmed long-running AI proposals idempotently.
- Generate approved long-form Muse Spark summaries where interactive latency is unsuitable.
- Purge expired encrypted AI conversation content according to retention policy.

### 9.3 Job guarantees

- At-least-once execution is expected.
- Every handler must be idempotent.
- Use unique business keys to prevent duplicate effects.
- Apply bounded exponential backoff with jitter.
- Move permanently failing jobs to a dead-letter state after a configured limit.
- Alert operators on queue age, repeated failures, and dead-letter growth.
- Never include health data in job error logs.

---

## 10. Authentication and Session Security

### 10.1 Passwords

- Minimum 12 characters; allow at least 128.
- Permit password managers and paste.
- Do not require arbitrary character-class rules.
- Check against a breached-password service using k-anonymity or an approved offline list.
- Hash with Argon2id. Tune parameters on the production hardware and record them.
- Rehash on successful login when parameters become outdated.

### 10.2 Tokens

- Access token lifetime: approximately 10 minutes.
- Access token contains minimal claims: subject, session ID, issued time, expiry, issuer, audience, token version.
- Do not put patient/medication data in tokens.
- Refresh token: at least 256 bits of cryptographic randomness.
- Store only an HMAC/hash of refresh/reset/verification/invite tokens.
- Rotate refresh tokens every use and detect replay.
- Revoke all sessions after password reset or confirmed account compromise.
- Keep signing keys outside source control and support key rotation via `kid`.

### 10.3 Cookies versus bearer tokens

For mobile clients, use bearer access tokens and opaque refresh tokens stored in OS secure storage. If a browser client later uses cookies:

- `Secure`, `HttpOnly`, and appropriate `SameSite` attributes.
- CSRF tokens on state-changing requests.
- Explicit trusted origins.

Do not create a configuration that accidentally accepts the same refresh token from both insecure browser storage and secure cookies.

### 10.4 Rate limits

At minimum, enforce separate limits for:

- Login/MFA by account and IP.
- Registration and email sends.
- Password-reset requests.
- Token refresh.
- Drug normalization search.
- Export generation.
- General authenticated API use.
- Muse Spark label/vision requests.
- Muse Spark conversation turns and tool loops.
- AI proposal creation and confirmation attempts.

Use generic `429` responses and `Retry-After`. Do not let rate-limit storage reveal account existence.

---

## 11. Security and Privacy Controls

### 11.1 Host hardening prerequisite

Before production data:

1. Rotate any credentials previously stored in plaintext documentation.
2. Remove plaintext passwords from local/server documentation.
3. Create named non-root administrative and deployment accounts.
4. Install authorized keys for those accounts and verify access in a second terminal.
5. Set `PermitRootLogin no`.
6. Set `PasswordAuthentication no` after key access is verified.
7. Restrict SSH with firewall rules or a VPN where operationally possible.
8. Enable firewall rules exposing only SSH from approved sources and HTTP/HTTPS publicly.
9. Apply OS security updates.
10. Configure fail2ban or equivalent defense for SSH, while treating it as defense in depth.
11. Ensure application directories are not world-writable.
12. Run services with `NoNewPrivileges`, `PrivateTmp`, restrictive `ProtectSystem`, and an explicit writable data path if systemd supports them.

Never lock out the only verified administrative path. Make SSH changes incrementally and test before closing the existing session.

### 11.2 nginx/TLS

- Obtain a domain and valid certificate.
- Redirect HTTP to HTTPS.
- TLS 1.2 and 1.3 only.
- Enable HSTS after HTTPS is verified; do not include subdomains until confirmed safe.
- Set request-body limits appropriate to each route; global API default should be small.
- Set proxy/read/connect timeouts.
- Do not expose framework/version headers where avoidable.
- Add security headers appropriate to API responses.
- Do not cache authenticated responses.
- Preserve/generate request IDs.
- Trust `X-Forwarded-For` only from the local nginx proxy.
- Configure explicit CORS origins from environment; never `*` for authenticated routes.

### 11.3 Database

- Bind PostgreSQL to localhost/private interface only.
- Use SCRAM authentication.
- Separate roles for migrations, runtime API, worker, backup, and read-only operations when practical.
- Runtime role must not own tables or execute arbitrary schema changes.
- Audit table should be append-only for runtime roles.
- Enable encrypted disks/volumes and encrypted backups.
- Use connection limits and statement timeouts.
- Use parameterized queries exclusively.

### 11.4 Application logging

Allowed log fields:

- Request ID.
- Route template, not raw path if it contains identifiers unnecessarily.
- HTTP method/status.
- Duration.
- Build/version.
- Coarse error code.
- Internal job ID.

Redact:

- Authorization and cookie headers.
- Tokens and API keys.
- Email addresses.
- Request/response bodies by default.
- Query strings for search/note endpoints.
- Medication names, dose details, symptoms, DOB, patient names.
- Push tokens.
- Muse Spark API keys.
- Raw prompts, model responses, label images/OCR, tool arguments, and tool results.

Never return exception messages from database/upstream libraries to clients.

### 11.5 Data minimization and lifecycle

- Collect only fields needed by enabled features.
- Define retention periods before production launch.
- Implement patient/account export.
- Implement deletion request and delayed deletion workflow.
- Preserve legally/security-required audit records only under an approved retention policy and minimize their contents.
- Delete expired exports automatically.
- Remove revoked/invalid push tokens.
- Do not use production data in development or tests.

### 11.6 External services

Maintain a service register for email, push, normalization, monitoring, and backup providers. For each provider document:

- Data sent.
- Purpose.
- Region.
- Retention.
- Contract/privacy terms.
- Timeout and retry behavior.
- Failure behavior.

Send minimal data. Push payloads should be generic by default, for example `"Reminder"`, with the app fetching details after authentication.

For Muse Spark specifically:

- Treat label images, OCR, medication data, schedules, dose history, symptoms, and conversations as sensitive health data.
- Send only the context required for the current task and authorized patient.
- Prefer structured IDs/fields over broad record dumps.
- Do not send passwords, tokens, caregiver contact data, audit records, or unrelated patient data.
- Confirm the provider's retention/training policy and disable provider training/storage where supported.
- Document model/provider changes because behavior can change even when the API contract appears compatible.
- Use a provider-side project/key dedicated to Medac, with spend alerts and key rotation.

---

## 12. Muse Spark Intelligence and Agent Architecture

Muse Spark is a core subsystem, not a fallback. Implement it behind a typed provider interface so the application can test, version, and safely evolve the integration while preserving Muse Spark as the production model.

```ts
interface IntelligenceProvider {
  generateStructured<T>(request: StructuredGenerationRequest<T>): Promise<T>;
  runToolTurn(request: ToolTurnRequest): Promise<ToolTurnResponse>;
  analyzeImage<T>(request: VisionRequest<T>): Promise<T>;
}
```

### 12.1 Primary Muse Spark tasks

Use Muse Spark for:

1. Medicine-label image understanding and OCR interpretation.
2. Parsing prescription directions and user language into typed drafts.
3. Detecting ambiguity and asking targeted clarification questions.
4. Drafting fixed, interval, cyclic, taper, or PRN schedules from confirmed constraints.
5. Conversational queries such as “what do I take tonight?”, using tools rather than model memory.
6. Drafting changes such as pausing a medication during a hospital stay.
7. Summarizing adherence and inventory results calculated by deterministic tools.
8. Converting structured history into clear doctor-visit summaries.
9. Translating and simplifying user-facing explanations while preserving units and facts.
10. Non-causal timeline observations over symptoms and dose timestamps, clearly marked as patterns rather than diagnoses.
11. Selecting and sequencing allowlisted tools to complete multi-step user intents.

### 12.2 Context builders

Create purpose-specific context builders. Do not send an entire patient record by default.

- Label interpretation: image/OCR only, locale, output schema.
- Schedule draft: selected medication label directions, user-confirmed frequency constraints, timezone, wake/sleep/preferences, current schedule if editing.
- Today assistant: today occurrences and current event states only.
- Adherence summary: deterministic report plus medication display names.
- Inventory summary: deterministic balances/forecast.
- Symptom timeline: selected date range of timestamps and user-entered notes, with non-causal instruction.

Every builder receives an authorization context and patient ID from the server, enforces a byte/token limit, and records only metadata about what categories were sent.

### 12.3 Prompt and output management

- Store system prompts in a versioned prompt registry in source control.
- Give every run a prompt template ID/version and output-contract version.
- Use provider-supported structured output/tool calling if available. Otherwise use a strict JSON response followed by schema validation and at most one repair attempt.
- Never use broad regex extraction as final validation.
- Reject unknown keys where appropriate, invalid enum values, invalid units, impossible dates/times, and overlong strings.
- Treat model confidence as a presentation signal only, never calibrated truth.
- Preserve source/evidence spans for extracted label fields.
- Include an explicit `unknown` state rather than forcing a guess.

### 12.4 Orchestration loop

1. Authenticate user and authorize patient.
2. Classify intent with Muse Spark or a simple deterministic shortcut for obvious commands.
3. Build minimal context and tool definitions.
4. Call Muse Spark.
5. Validate requested tool and arguments against server registry.
6. Re-authorize tool call.
7. Execute read tool or create a write proposal.
8. Return sanitized structured tool result to Muse Spark.
9. Repeat within hard turn/tool/token/time budgets.
10. Validate final response and persist run metadata.
11. If proposals exist, return deterministic diffs and confirmation controls.

Muse Spark may plan several actions in one turn. The server may group compatible proposals into one confirmation bundle, but each action remains independently validated and auditable.

### 12.5 Failure and fallback

- Retry only transient failures with bounded backoff and jitter.
- Use circuit breaking for provider outages.
- Do not automatically switch to an unapproved model with different privacy behavior.
- When Muse Spark is unavailable, return a clear retry/manual-entry response and keep all deterministic APIs operational.
- Do not fabricate an AI result from defaults such as `08:00`, `tablet`, or confidence `0.7`.
- A parse/schema failure is a failed run, not partial truth.

### 12.6 Prompt injection defenses

Labels, OCR, notes, imported text, and tool results are untrusted data. They may contain text such as “ignore previous instructions.” The system must:

- Delimit untrusted content and label it as data.
- State that instructions inside data must not be followed.
- Keep tool definitions and authorization outside user content.
- Never expose secrets in model context.
- Validate every tool call independently of model reasoning.
- Limit recursive/indirect retrieval.
- Test malicious labels, OCR, notes, and medication names.

### 12.7 Cost, latency, and capacity

- Per-user and per-route request limits.
- Maximum image bytes, pixels, and token budget.
- Maximum agent turns and tool calls.
- Daily provider spend alert and hard ceiling.
- Cache only safe, stable tasks by HMAC input fingerprint and patient scope.
- Do not cache conversational answers across patients.
- Track latency/token/error metrics without recording content.

---

## 13. Drug Normalization Adapter

Implement normalization behind an interface so sources can change.

```ts
interface DrugNormalizationProvider {
  searchByName(query: string): Promise<NormalizationSearchResult>;
  resolveNdc(ndc: string): Promise<NormalizationResolution>;
  getConcept(rxcui: string): Promise<NormalizedDrugConcept>;
}
```

Requirements:

- Validate and normalize NDC input without pretending ambiguous 10-to-11 digit conversions are certain.
- Use HTTPS, strict timeouts, bounded retries, and circuit breaking.
- Cache stable concept data with source/version timestamps.
- Store only needed source fields.
- Represent zero, one, and multiple candidates explicitly.
- Require user selection for ambiguity.
- Preserve original user input.
- Return `temporarily_unavailable` rather than blocking medication setup.
- Create contract tests using recorded, de-identified fixtures; do not make normal CI depend on a live external API.
- Do not call this adapter from Today/list endpoints. Normalize on entry or explicit retry.

---

## 14. Schedule and Occurrence Engine

Build the schedule engine as a pure, separately tested domain library.

### 14.1 Input

- Schedule version.
- Requested UTC/local range.
- IANA timezone.
- Pause periods.
- Medication effective dates.

### 14.2 Output

A deterministic list of occurrence candidates with:

- Stable business key.
- UTC instant.
- Local date/time.
- Timezone and offset.
- DST adjustment marker.
- Dose amount/unit.
- Schedule version.

### 14.3 Required tests

- One or multiple daily times.
- Specific weekdays.
- Month/year boundaries.
- Leap day.
- Spring-forward nonexistent time.
- Fall-back duplicated time with earlier/later policy.
- User travel represented by a new schedule version/timezone.
- Elapsed interval crossing DST.
- Cyclic on/off boundaries.
- Taper step boundaries.
- Pause partially overlapping generation window.
- Medication start/end dates.
- Schedule supersession.
- Duplicate job execution.
- Concurrent occurrence generation.

Use one calculation implementation for preview, persistence, reporting, and tests. Do not duplicate schedule logic in route handlers.

---

## 15. Offline Synchronization

### 15.1 Client mutation identity

Every offline-created mutation must contain:

- Stable `device_id`.
- Stable client-generated event ID.
- Client-recorded timestamp.
- Last known server resource version where relevant.

The server records its own receipt timestamp and does not trust client time for authorization, token expiry, or ordering security decisions.

### 15.2 Conflict rules

- Duplicate dose event: return existing accepted event.
- Medication/schedule edited from stale version: reject with `409` and current version.
- Independent dose events for the same occurrence: accept only according to documented event rules; surface conflict if two incompatible current states result.
- Corrections reference a known event and are append-only.
- Membership revoked while offline: reject later sync attempts immediately.
- A discontinued medication can accept a genuinely historical event before discontinuation if authorized and valid; reject future events unless explicitly unplanned/PRN and allowed.

### 15.3 Change feed

Provide an opaque cursor-based feed containing patient-scoped changes needed by a client:

- Medication changes.
- Schedule versions.
- Occurrences in synchronization horizon.
- Dose events/corrections.
- Inventory balance/version.
- Membership revocation indicator.

Do not expose audit internals. Ensure a user sees changes only for active memberships.

---

## 16. Deterministic Alerts

MVP server alerts may include:

### 16.1 Low stock

- Compute from inventory ledger and future scheduled consumption.
- If future consumption cannot be determined, return an estimate state rather than false precision.
- Store threshold per inventory account.
- Deduplicate open alerts.

### 16.2 Expiration

- User-entered date only.
- Thresholds configurable, e.g. 30 and 7 days.
- Do not claim chemical safety after/before expiration; state that the entered date is approaching/passed.

### 16.3 Missed event for user-marked high-attention medication

- This is a user-configured reminder escalation, not a clinical classification.
- Caregiver delivery requires active membership/consent and preferences.
- Alert text must state that event status may not reflect actual administration.
- Delivery and acknowledgement are recorded.

### 16.4 Stale device

- Alert the user if a device has not synchronized or reports notifications disabled.
- Avoid exposing medication details in email/push.

---

## 17. Exports and Reports

### 17.1 CSV export

Include separate CSV files in an encrypted-at-rest temporary archive where appropriate:

- Patient profile summary.
- Current medications.
- Schedule definitions.
- Dose events for selected range.
- Inventory summary.
- Symptom logs only if explicitly selected.

Requirements:

- Protect against CSV formula injection by prefixing dangerous cells beginning with `=`, `+`, `-`, or `@`.
- Escape fields correctly.
- Limit requested date ranges.
- Generate asynchronously.
- Expire files quickly, e.g. 24 hours.
- Require current authorization at download time, not only creation time.
- Use generic file names where possible.
- Audit create and download events.

### 17.2 Adherence report

Return:

- Date range and timezone.
- Eligible scheduled occurrence count.
- Taken count.
- Skipped count.
- Missed count.
- Pending/not-yet-classifiable count.
- Excluded count and exclusion reasons.
- Adherence percentage and exact denominator definition.

Do not use manipulative streak language in the server contract. UI wording can remain humane and nonjudgmental.

---

## 18. Observability and Operations

### 18.1 Metrics

Collect non-PHI operational metrics:

- Request count, latency, and status by route template.
- Active database connections.
- Queue depth and oldest job age.
- Job success/failure counts by type/error code.
- Email/push delivery status counts.
- External normalization latency/error rate.
- Muse Spark request latency, token counts, schema-failure rate, tool-call count, proposal count/confirmation rate, provider errors, and circuit-breaker state.
- AI metrics must use route/purpose/model/prompt-version labels only, never patient/user IDs or content.
- Occurrence generation lag.
- Backup age and last restore-test status.
- Process CPU/memory/disk.

Avoid high-cardinality patient/user/resource labels.

### 18.2 Alerts

Operator alerts for:

- API readiness failure.
- Repeated 5xx increase.
- Database disk/connection pressure.
- Queue lag over threshold.
- Dead-letter jobs.
- Certificate expiry.
- Backup failure or stale backup.
- Disk capacity threshold.
- Repeated authentication attack patterns.
- Muse Spark outage, spend threshold, abnormal tool-call loops, schema-failure spike, or prompt-injection policy blocks.

### 18.3 Health response privacy

Public health endpoints must not reveal database hostnames, server IPs, credentials, exception text, dependency versions, or queue contents.

---

## 19. Backup and Recovery

### 19.1 Backup design

- Daily encrypted PostgreSQL logical backup at minimum.
- Prefer continuous WAL/archive or more frequent snapshots once production usage begins.
- Store backups off-host in a restricted account/bucket.
- Use retention tiers, for example daily/weekly/monthly, subject to privacy policy.
- Encrypt before or at destination with separately managed keys.
- Monitor backup completion and size anomalies.
- Do not put database dumps in the repository or web-accessible directories.

### 19.2 Restore testing

At least monthly in production-like staging:

1. Provision an empty isolated PostgreSQL instance.
2. Restore selected backup.
3. Run schema checks.
4. Run a read-only integrity script.
5. Verify representative encrypted fields can be decrypted with controlled keys.
6. Record recovery time and result without PHI in logs.
7. Destroy restored data securely after test.

Define target objectives before launch:

- Initial RPO target: 24 hours or better.
- Initial RTO target: 4 hours or better.

Tighten as usage and risk require.

---

## 20. Testing Strategy

### 20.1 Unit tests

- Permission matrix.
- Password/token utilities.
- Token rotation/reuse detection.
- Email and NDC normalization.
- Schedule engine and DST.
- Taper/cycle validation.
- Inventory projections.
- Adherence calculations.
- CSV formula protection.
- Log redaction.
- Muse Spark provider request construction with secret/content redaction.
- Every AI output contract and repair path.
- Tool registry allowlist, argument validation, and read/proposal classification.
- AI proposal hash, expiry, optimistic-version, confirmation, and exactly-once rules.
- Context-builder minimum-data rules.

### 20.2 Integration tests

Use a real PostgreSQL test instance:

- Registration through login/refresh/logout.
- Email/reset token single use and expiry.
- MFA setup and replay rejection.
- Patient creation and memberships.
- Medication lifecycle.
- Schedule versioning and occurrence generation.
- Dose event plus inventory transaction atomicity.
- Correction plus compensating inventory transaction.
- Batch replay/idempotency.
- Concurrent event submissions.
- Export lifecycle.
- Worker retry/dead-letter behavior.
- Audit event creation.
- Muse Spark label interpretation from fixed de-identified fixtures.
- Natural-language instruction parsing and clarification.
- Multi-turn read-tool orchestration.
- Schedule proposal creation, deterministic diff, confirmation, and execution.
- Proposal rejection, expiry, stale version, revoked membership, and replay denial.
- Provider timeout, malformed JSON, invalid tool, and circuit-breaker fallback.

### 20.3 Authorization tests

Create at least two unrelated patient groups and test every patient-scoped route for:

- Unauthenticated denial.
- Unrelated authenticated user denial.
- Viewer restrictions.
- Contributor restrictions.
- Manager restrictions.
- Owner success.
- Revoked membership denial.
- Archived/deletion-pending behavior.

Maintain a route-to-authorization-test inventory. CI should fail if a new patient route lacks an authorization test marker.

### 20.4 Security tests

- SQL injection payloads.
- IDOR/BOLA attempts.
- Mass assignment/unknown fields.
- Oversized payloads and batch limits.
- Malformed JSON and content type.
- CORS preflight from disallowed origin.
- Refresh-token replay.
- Password-reset enumeration.
- Brute-force rate limits.
- JWT algorithm/key confusion resistance.
- Expired/not-before/audience/issuer token checks.
- CSV injection.
- Header spoofing and untrusted forwarded IP.
- Sensitive-data log scan after test suite.
- Prompt injection in label/OCR, medication names, notes, and tool output.
- Attempts to call arbitrary tools, URLs, SQL, shell, or other-patient resources.
- Model attempts to self-confirm a proposal or alter proposal payload.
- Oversized image, MIME spoofing, malformed base64, decompression/pixel bombs.

### 20.5 Property/fuzz tests

Use property-based tests for:

- Schedule generation uniqueness.
- Date/time boundaries.
- Inventory ledger invariants.
- Idempotent event replay.
- Correction chains.
- Pagination cursors.

### 20.6 Load tests

Before production:

- Authentication burst within safe rate limits.
- Today/occurrence listing.
- Batch offline synchronization.
- Daily occurrence generation.
- Export queue isolation.
- Concurrent Muse Spark conversations and vision calls under configured budget limits.
- Worst-case bounded agent tool loops.

Confirm that heavy exports, normalization calls, or Muse Spark requests cannot starve authentication, dose-event logging, synchronization, or occurrence generation.

---

## 21. CI/CD Gates

Every pull request:

1. Install from lockfile.
2. Type-check.
3. Lint.
4. Run unit and integration tests.
5. Run migration from empty DB.
6. Run migration compatibility test against prior schema snapshot where practical.
7. Generate OpenAPI and fail on uncommitted contract drift.
8. Dependency vulnerability scan.
9. Static-analysis scan.
10. Secret scan.
11. Build production artifact.
12. Produce an SBOM if tooling permits.

Deployment:

1. Build immutable versioned artifact.
2. Deploy to staging.
3. Run migrations with dedicated role.
4. Run smoke tests.
5. Require approval for production.
6. Back up before destructive/high-risk migration.
7. Deploy API and worker.
8. Check readiness, queue health, and error rate.
9. Roll back application on failure. Database migrations must be backward-compatible for at least one application version whenever feasible.

Do not run `npm install` as root in the live application directory as the normal deployment process.

---

## 22. Environment Configuration

Provide `.env.example` containing names and safe documentation, never values.

Suggested variables:

```text
NODE_ENV
APP_ENV
APP_BUILD_ID
HOST
PORT
PUBLIC_BASE_URL
TRUST_PROXY
DATABASE_URL
DATABASE_POOL_MIN
DATABASE_POOL_MAX
DATABASE_STATEMENT_TIMEOUT_MS
ACCESS_TOKEN_ISSUER
ACCESS_TOKEN_AUDIENCE
ACCESS_TOKEN_PRIVATE_KEY_FILE
ACCESS_TOKEN_PUBLIC_KEY_FILE
ACCESS_TOKEN_TTL_SECONDS
REFRESH_TOKEN_TTL_DAYS
TOKEN_HASH_SECRET
FIELD_ENCRYPTION_KEY_CURRENT
FIELD_ENCRYPTION_KEY_PREVIOUS
ARGON2_MEMORY_KIB
ARGON2_TIME_COST
ARGON2_PARALLELISM
CORS_ALLOWED_ORIGINS
EMAIL_PROVIDER
EMAIL_FROM
EMAIL_API_KEY
RXNORM_BASE_URL
RXNORM_TIMEOUT_MS
MUSE_SPARK_API_KEY
MUSE_SPARK_BASE_URL
MUSE_SPARK_MODEL
MUSE_SPARK_TIMEOUT_MS
MUSE_SPARK_MAX_OUTPUT_TOKENS
MUSE_SPARK_MAX_AGENT_TURNS
MUSE_SPARK_MAX_TOOL_CALLS
MUSE_SPARK_DAILY_BUDGET
AI_PROMPT_RETENTION_ENABLED
AI_CONVERSATION_RETENTION_HOURS
AI_PROPOSAL_TTL_MINUTES
AI_VISION_MAX_BYTES
AI_VISION_MAX_PIXELS
PUSH_PROVIDER_CONFIG_FILE
EXPORT_DIRECTORY
EXPORT_TTL_HOURS
LOG_LEVEL
METRICS_BIND_ADDRESS
WORKER_CONCURRENCY
JOB_MAX_ATTEMPTS
```

Configuration loader must:

- Validate all values on startup.
- Refuse insecure production defaults.
- Refuse wildcard authenticated CORS in production.
- Refuse a non-HTTPS public production URL.
- Refuse default or missing token/encryption keys.
- Avoid printing secret values.
- Refuse production startup if the Muse Spark base URL is not HTTPS.
- Refuse unbounded Muse Spark turn/tool/token/image limits.
- Keep Muse Spark enabled by default in production when configured, while preserving deterministic manual APIs during provider outages.

---

## 23. Deployment Plan for the Existing Server

The target host currently serves another application through nginx. Treat it as a shared host and avoid service interruption.

Read-only inspection on 2026-08-18 found the current Medac implementation at `/opt/medac-api`: one root-run Express process on public port `3002`, nginx proxying `/medac`, JSON user storage, 30-day JWTs, permissive CORS, a 25 MB body limit, and Muse Spark label/OCR endpoints. The current prompts ask the model to infer purpose, instructions, and “typical” times and log portions of upstream responses. Preserve Muse Spark capability, but do not preserve those unsafe defaults or logs. Migrate/compatibility-wrap existing client endpoints deliberately and migrate existing users through a documented strategy rather than silently discarding them.

### Phase A: Read-only discovery

- Record OS version, disk, memory, active services, listening ports, firewall rules, nginx sites, certificate status, PostgreSQL presence, and update state.
- Inspect existing `/medac` nginx/application routing.
- Back up nginx and systemd configuration.
- Do not print secrets into command logs or the project documentation.
- Confirm ownership and permissions of deployment directories.

### Phase B: Credential and SSH hardening

- Rotate credentials that have appeared in plaintext.
- Create non-root admin/deploy user with sudo as required.
- Add a new passphrase-protected administrative key if operationally possible.
- Verify key access before disabling root/password access.
- Tighten SSH and firewall incrementally.
- Document recovery access outside the repository.

### Phase C: Base services

- Install/configure PostgreSQL.
- Create dedicated roles/database.
- Create `/opt/medac` owned by deployment/service policy.
- Create `medac` system user with no interactive shell.
- Install systemd service files.
- Configure log rotation or journald retention.
- Configure backup tooling and off-host destination.

### Phase D: Staging deployment

- Prefer a staging hostname and separate database.
- Deploy API/worker on loopback ports.
- Configure and smoke-test Muse Spark through the provider adapter using synthetic labels and no real patient data.
- Run migrations.
- Configure nginx and TLS.
- Run smoke, authorization, and log-redaction tests.
- Test backup and restore.

### Phase E: Production deployment

- Point a real domain to the server.
- Issue TLS certificate.
- Deploy production database and secrets.
- Run migrations.
- Start worker then API, or according to migration compatibility needs.
- Enable `/medac/api/v1` proxy route.
- Verify existing application still works.
- Verify HTTP redirect, HTTPS, CORS, headers, health, authentication, a complete synthetic medication flow, Muse Spark label interpretation, a read-tool conversation, and a confirmation-gated schedule proposal using non-real test data.
- Enable monitoring and backup alerts.

### Phase F: Post-deployment verification

- Inspect application and nginx logs for accidental sensitive values.
- Verify no app/PostgreSQL ports are public.
- Verify systemd sandboxing and unprivileged UID.
- Reboot test during a maintenance window and verify automatic recovery.
- Confirm certificate renewal timer.
- Confirm latest backup exists off-host.
- Remove synthetic records.

---

## 24. Implementation Phases and Deliverables

### Phase 0 — Repository bootstrap

Deliverables:

- `server/` TypeScript project.
- Strict TypeScript, linting, formatting, tests.
- Validated configuration system.
- Fastify app factory separated from network startup.
- Request ID, error handler, secure logging/redaction.
- Health/live/ready/version endpoints.
- Docker Compose for local PostgreSQL only if useful; not necessarily production deployment.
- CI workflow.

Exit criteria:

- Empty app builds/tests.
- Production config refuses insecure defaults.

### Phase 1 — Database and authentication

Deliverables:

- Initial migrations and database roles documentation.
- Registration, verification, login, refresh rotation, logout, password reset/change.
- Device and session management.
- Rate limiting.
- Optional TOTP MFA and recovery codes.
- Auth audit events.

Exit criteria:

- Replay, enumeration, expiry, and concurrency tests pass.

### Phase 2 — Patients and authorization

Deliverables:

- Patients, memberships, invitations, consent records.
- Central permission module.
- Ownership safeguards.
- Comprehensive authorization tests.

Exit criteria:

- Cross-patient route access is denied and tested.

### Phase 3 — Muse Spark intelligence foundation

Deliverables:

- Typed Muse Spark provider adapter using the configured OpenAI-compatible endpoint.
- Versioned prompt registry and strict output contracts.
- AI run metadata, context builders, tool registry, budgets, circuit breaker, and content-safe logging.
- Label image/OCR interpretation with evidence, uncertainty, and clarification.
- Existing `/api/medicine/identify` and `/identify-text` compatibility decision and migration tests.
- Prompt-injection and malformed-output tests.

Exit criteria:

- Muse Spark works from staging with synthetic data; no raw input/output enters ordinary logs; failed/uncertain extraction never creates medication data automatically.

### Phase 4 — Medication management and normalization

Deliverables:

- Medication lifecycle endpoints.
- Controlled units/forms/routes validation.
- RxNorm/NDC adapter with cache and failure states.
- Muse Spark natural-language medication draft and normalization-assistance flow.
- Audit history.

Exit criteria:

- Medication entry works while external normalization or Muse Spark is unavailable; Muse Spark drafts require user confirmation.

### Phase 5 — Schedule engine and occurrences

Deliverables:

- Pure schedule engine.
- Fixed, interval, PRN, cyclic, taper models.
- Versioning/supersession.
- Occurrence preview and idempotent rolling generation.
- DST/timezone tests.

Exit criteria:

- Required schedule vectors pass and duplicate generation is impossible.

### Phase 6 — Conversational agent and schedule proposals

Deliverables:

- Muse Spark conversation endpoint and bounded tool loop.
- Authorized read-only medication/today/schedule tools.
- Schedule-draft intelligence using the deterministic preview tool.
- Encrypted, expiring action proposals and deterministic diffs.
- Confirmation/rejection endpoints with authorization, payload-hash, version, expiry, and replay checks.

Exit criteria:

- Muse Spark can understand a multi-step medication-management request and draft actions; no write occurs before valid confirmation.

### Phase 7 — Dose logging and offline sync

Deliverables:

- Single and batch dose-event APIs.
- Client idempotency.
- Corrections.
- Occurrence state projection.
- Patient change feed/sync cursor.
- Concurrency tests.

Exit criteria:

- Replayed offline batches make no duplicate records/effects.

### Phase 8 — Inventory, expiration, notes, injections

Deliverables:

- Inventory ledger and automatic dose consumption.
- Compensating transactions for correction.
- Low-stock projections.
- Expiration tracking.
- Symptom and injection-site logs.

Exit criteria:

- Ledger invariants and corrections pass tests.

### Phase 9 — Alerts, caregiver preferences, and delivery

Deliverables:

- Alert preferences/consents.
- Low-stock, expiration, stale-device, and user-configured missed-event alerts.
- Generic push/email delivery abstraction.
- Delivery/acknowledgement tracking.
- Retry/dead-letter handling.

Exit criteria:

- No medication details appear in generic notification payloads or logs.

### Phase 10 — Intelligent summaries, reports, and exports

Deliverables:

- Deterministic adherence report.
- Muse Spark humane adherence/inventory/history summaries grounded only in deterministic report tools.
- Optional non-causal symptom timeline observations with explicit limitations.
- Asynchronous CSV export.
- Expiry and audited download.
- CSV injection protection.

Exit criteria:

- Cross-patient and expired download attempts fail.

### Phase 11 — Production operations

Deliverables:

- systemd/nginx deployment templates.
- TLS and host-hardening checklist.
- Metrics and alerts.
- Backup/restore scripts and runbook.
- Deployment, rollback, incident, and recovery runbooks.
- Staging and production smoke tests.

Exit criteria:

- All Definition of Done items pass.

---

## 25. Required Documentation Produced by the Coding Agent

In addition to code, provide:

- `server/README.md` — local setup, tests, migrations, architecture.
- `server/openapi/openapi.json` or generated equivalent.
- `server/docs/AUTHORIZATION.md` — role/permission matrix and enforcement pattern.
- `server/docs/SCHEDULES.md` — schedule semantics and DST rules.
- `server/docs/OFFLINE_SYNC.md` — idempotency, conflicts, cursors.
- `server/docs/SECURITY.md` — threat assumptions and controls, without secrets.
- `server/docs/DEPLOYMENT.md` — staging/production deployment.
- `server/docs/BACKUP_RESTORE.md` — commands and verification.
- `server/docs/INCIDENT_RESPONSE.md` — containment, rotation, notification path.
- `server/docs/DATA_RETENTION.md` — configured lifecycle and deletion behavior.
- `server/docs/EXTERNAL_SERVICES.md` — data shared with each provider.
- `server/docs/CLINICAL_FEATURE_GATE.md` — requirements before deferred safety features can be enabled.
- `server/docs/MUSE_SPARK.md` — provider contract, tasks, prompts, output schemas, context limits, privacy, budgets, and failure behavior.
- `server/docs/AGENT_TOOLS.md` — complete read/proposal tool catalog, permissions, schemas, confirmation policy, and versioning.
- `server/docs/AI_EVALUATIONS.md` — fixture suites, expected behaviors, failure thresholds, and model/prompt release process.

---

## 26. Clinical Feature Gate for Future Phases

No deferred medical safety check may be enabled merely because code exists. Each feature requires:

1. Written intended use and jurisdiction.
2. Licensed/current source with permitted production use.
3. Source ingestion and version verification.
4. Clinical reviewer approval.
5. Validated rule logic and test cases.
6. Known-limitations documentation.
7. Structured result contract including `unknown` and `failed`.
8. Source attribution shown to users.
9. Monitoring for source staleness and check failures.
10. Recall/kill switch by rule/source version.
11. Incident procedure.
12. Regulatory/legal assessment.
13. No-result wording that does not imply global safety.
14. Human-readable content reviewed independently of any LLM.

Muse Spark is already the core intelligence layer, but any future explanation of clinical safety findings must consume only structured, sourced findings and must not invent findings. Its output must be schema constrained, versioned, auditable, and safely replaceable if the provider is unavailable.

---

## 27. Final Safety and Launch Checklist

Before accepting real user data, confirm all answers are **yes**:

- Is the production URL HTTPS on a real domain?
- Is HTTP redirected to HTTPS?
- Are database and application ports private?
- Does the app run without root privileges?
- Are root/password SSH logins disabled after safe access verification?
- Were previously exposed credentials rotated?
- Are secrets absent from Git, docs, process output, and logs?
- Is authenticated CORS an explicit allowlist?
- Are access/refresh tokens short-lived, rotated, revocable, and replay protected?
- Does every patient endpoint have negative authorization tests?
- Are medication and dose histories append-only/versioned where required?
- Is offline replay idempotent?
- Are DST and timezone tests passing?
- Are generic notification payloads free of medication details?
- Are exports short-lived, authorized at download, and formula-safe?
- Are logs verified free of health data?
- Are backups encrypted and off-host?
- Has a restore test passed?
- Are queue, certificate, disk, and backup alerts active?
- Are unvalidated clinical checks disabled?
- Is Muse Spark configured as the production intelligence provider through the typed adapter?
- Are model prompts and output contracts versioned and evaluated?
- Are AI context builders patient-scoped and data-minimal?
- Are raw prompts, responses, images, and tool data excluded from ordinary logs?
- Are unknown tools and invalid arguments rejected?
- Are model writes impossible without confirmation-bound proposals?
- Are proposal expiry, hash, resource version, authorization, and replay checks active?
- Are prompt-injection fixtures passing?
- Are AI turn/tool/token/image/spend limits active?
- Does manual medication management continue during a Muse Spark outage?
- Does the UI/API wording avoid claiming guaranteed reminders or medical safety?
- Is rollback documented?
- Does the existing server application still function after Medac deployment?

If any critical answer is no, the production launch remains blocked.
