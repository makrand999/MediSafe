# AI Evaluations — §25

## Fixtures `tests/unit/intelligence/` `tests/fixtures/`
- `provider.test.ts:1` HTTPS enforced, redaction, magic-byte spoof `ALLOWED_IMAGE_MIMES` `validateVisionInput` `server/src/modules/intelligence/safety-policy.ts:1`.
- `output-contracts.test.ts:1` `LabelInterpretationSchema` strict rejects `purpose|typical_times`, `uncertain_fields` required `evidence` `server/src/modules/intelligence/output-contracts.ts:1` `validateWithOneRepair` `repaired` `confidence` 0.0-1.0 not truth.
- `tool-registry.test.ts:1` `24` allowlist `12 read +12 proposal` unknown `rejected` `strict` extra args, `proposal missing expectedVersion`.
- `proposal-service.test.ts:1` hash `encryptPayload` `pending→confirmed→executed` `HASH_MISMATCH` `VERSION_CONFLICT` `expiry` `replay` `hasRecentAuth` `patient mismatch`.

## Schedule engine
- `tests/unit/schedules.engine.test.ts:1` `22` vectors `spring-forward gap→dst_adjusted` `fall-back earlier|later` `cyclic` `taper TAPER_OVERLAP`.

## Authorization
- `tests/authorization/route-inventory.test.ts:1` `30+` patient routes `viewer≠owner` `revoked` `expired invite` `BOLA` `prompt injection` `other patient` `replay`.

## Thresholds
- `provider schemaFailureRate` `toolCallCount` `proposal confirmRate` `circuitBreaker` `latency` `token` `§18` — alert if `>5%` `>12` `>60s`.

## Release
- `prompt-registry.ts:1` `PROMPT_REGISTRY` `label_interpretation@v1` `instruction_parse@v1` `schedule_draft@v1` versioned, `OUTPUT_CONTRACTS` `server/src/modules/intelligence/output-contracts.ts:277`.
- Model change `muse-spark-1.2-contributor` fixed `server/src/config/index.ts:10` — no auto-switch `§12.5` `MUSE_SPARK_PROVIDER_DOC`.

## Fixture suites
- Synthetic labels `Orixime 200` `Lantac-D` `Crocin` (de-identified, not PHI) `journalctl` `upstream 200` `parsed vision success` preserved but prompts ask not to invent `purpose` when absent.
