# Data Retention — §11.5

- `users` `status pending|active|locked|disabled|deletion_pending` `deletionRequestedAt` `server/src/db/schema.ts:104`.
- `patients` `archivedAt` `deletionRequestedAt` `server/src/db/schema.ts:248` — `POST /patients/:id/archive` `POST /deletion-request` (owner only).
- `exports` `EXPORT_TTL_HOURS 24` `server/src/modules/exports/service.ts:1` `expiresAt` `downloadToken 1h`, `DELETE /exports/:id` immediate, `expired` `410`.
- `aiRuns` `aiToolCalls` `aiConversations|aiMessages` `expiresAt` `AI_CONVERSATION_RETENTION_HOURS 72` `server/src/config/index.ts:1` `AI_PROMPT_RETENTION_ENABLED false` default — raw prompts/images not persisted, only `inputFingerprint` `server/src/modules/intelligence/routes.ts:92`.
- `aiActionProposals` `AI_PROPOSAL_TTL_MINUTES 30` `expired` sweep `server/src/modules/intelligence/db-proposal-service.ts:1`.
- `authSessions` `mfa recovery` `passwordResetTokens` `emailVerificationTokens` purged via worker `JOB_MAX_ATTEMPTS 5` `server/src/worker.ts:1`.
- Audit `audit_events` append-only `migrations/0002_roles.up.sql` retained per policy, `metadataJson` allowlisted no PHI.
- No prod data in dev/tests `server/README.md:1`.
