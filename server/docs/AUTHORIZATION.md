# Authorization — §7, §5.2

## Model
- `user` authenticates; `patient` owns data; `patient_membership` grants access.
- Every patient-scoped query includes `WHERE patient_id = ? AND EXISTS (SELECT 1 FROM patient_memberships WHERE patient_id = ? AND user_id = ? AND status='active')` — see `src/modules/patients/service.ts:36` `requireMembership`.
- Flow per request `src/app.ts` `authenticate` → `requirePermissionOnPatient` → `requirePermission(role, permission)` → scoped query → audit.

## Roles
| Role | Permissions |
|------|-------------|
| `owner` | `patient:read,update,archive,delete,transferOwnership` `membership:read,update,revoke` `invite:create,read,revoke` `medication:read,create,update,activate,pause,resume,discontinue,archive` `schedule:read,create,supersede` `occurrence:read` `doseEvent:read,create,correct` `inventory:read,write` `symptom:read,write` `injection:read,write` `alert:read,acknowledge` `alertPreference:read,write` `export:create,read` `adherence:read` `consent:read,write` `intelligence:read,write` |
| `manager` | all except `patient:delete,transferOwnership` `membership:update` limited |
| `contributor` | `patient:read` `membership:read` `medication:read` `schedule:read` `occurrence:read` `doseEvent:read,create` `inventory:read` `symptom:read,write` `injection:read,write` `alert:read,acknowledge` `export:read` `adherence:read` `intelligence:read,write` |
| `viewer` | `patient:read` `membership:read` `medication:read` `schedule:read` `occurrence:read` `doseEvent:read` `inventory:read` `symptom:read` `injection:read` `alert:read` `export:read` `adherence:read` `intelligence:read` |

Central matrix `src/lib/permissions.ts:59` `MATRIX` `can(role, permission)` `requirePermission` — no ad hoc checks.

## Enforcement
- `src/plugins/auth.ts:1` `authenticate` verifies `Authorization: Bearer <JWT>` via `jose` `verifyAccessToken`, checks `user.status=active`, `tokenVersion` matches `user_security.tokenVersion`, `session.revokedAt` null, `expiresAt` future.
- `src/modules/patients/service.ts:47` `requirePermissionOnPatient` loads membership, calls `requirePermission`.
- All patient routes `src/modules/medications/routes.ts:1` `src/modules/schedules/routes.ts:1` `src/modules/doses/routes.ts:1` `src/modules/inventory/routes.ts:1` etc. call `authorize` then `requirePermissionOnPatient`.
- `patient_memberships_active_unique` partial index prevents duplicate active.

## BOLA/IDOR tests
- `tests/authorization/route-inventory.test.ts:11` lists every patient route that must be protected (30+).
- Negative tests required: viewer cannot read another patient's medication by guessing ID, revoked loses immediately, expired invite rejected, export cannot include other patient, tool cannot be used outside authorized patient.

## DB defense
- `migrations/0002_roles.up.sql` separate roles `medac_migration|app|worker|backup|readonly`, `REVOKE UPDATE,DELETE ON audit_events` + trigger `prevent_audit_mutation`.
