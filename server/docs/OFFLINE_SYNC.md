# Offline Sync — §8.10, §15

## Identity
- Every offline mutation has `device_id` (stable UUID) + `client_event_id` (stable per device) `src/modules/doses/validation.ts:1`.
- Server records `recorded_at` (server time), not client time, for ordering.

## Endpoints
- `POST /patients/:patientId/dose-events` single `DoseEventSchema` `server/src/modules/doses/routes.ts:1`
- `POST /patients/:patientId/dose-events/batch` `BatchDoseEventSchema` `max 100` `bodyLimit 2MB` per-item `client_event_id` `server/src/modules/doses/routes.ts:1`
- `GET /patients/:patientId/dose-events?from&to&cursor&limit` `cursor base64url(id)` `server/src/modules/doses/service.ts:184`
- `POST /patients/:patientId/dose-events/:eventId/corrections` append-only `corrects_event_id` `server/src/modules/doses/service.ts:213`
- `GET /patients/:patientId/sync?cursor` opaque `base64url({since:ISO})` `server/src/modules/doses/service.ts:266` — never client time alone.

## Idempotency
- `unique(device_id,client_event_id)` `unique(client_event_id,patient_id)` where `device_id IS NULL` `migrations/0001_initial.up.sql:450`.
- `createDoseEvent` `server/src/modules/doses/service.ts:116` checks existing first; on `23505` duplicate returns `{outcome:"duplicate"}` not `500` `server/src/modules/doses/service.ts:130`.

## Conflicts
- Duplicate: return existing `duplicate`.
- Stale `expected_updated_at` (medication) → `409 MEDICATION_VERSION_CONFLICT` `server/src/modules/medications/service.ts:150`.
- Same occurrence two taken events: second `rejected` or `conflict` per `occurrence.state` projection `server/src/modules/doses/service.ts:156`.
- Correction chain cannot loop `corrects_event_id != id` `schema.ts:528`.
- Revoked membership → `403` immediately `requirePermissionOnPatient`.
- Discontinued medication future events `400 MEDICATION_NOT_ACTIVE` `server/src/modules/doses/service.ts:56`, historical before `discontinuedAt` allowed.

## Change feed
- `GET /sync` returns `changes:[{type:"medication"|"schedule_version"|"occurrence"|"dose_event"|"membership_revoked",id,updated_at}]` sorted, `next_cursor` `server/src/modules/doses/service.ts:266` — only active memberships.

## Tests required
- Replay same batch → `duplicate` not second `dose_consumed` `server/src/modules/doses/service.ts:70` `inventoryTransactions` unique `doseEventId`.
