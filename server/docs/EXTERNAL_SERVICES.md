# External Services — §11.6

| Provider | Data sent | Purpose | Region | Retention | Timeout |
|----------|-----------|---------|--------|-----------|---------|
| Email `console|smtp` `EMAIL_PROVIDER` | `email_normalized` `tokenHash` (hashed) | verification/reset/invite | `EMAIL_*` | `console` none | — |
| Push `PUSH_PROVIDER_CONFIG_FILE` | `tokenCiphertext` no med name `server/src/db/schema.ts:233` generic `"Reminder"` `server/src/modules/intelligence/safety-policy.ts:1` | fallback/escalation | local | `invalidatedAt` | — |
| RxNorm `RXNORM_BASE_URL https://rxnav.nlm.nih.gov/REST` `server/src/modules/drug-normalization/provider.ts:1` | `q` `ndc` `rxcui` (no PHI) | normalization only | US | `CACHE_TTL 24h` `SEARCH 5m` | `5000ms` `CIRCUIT 5→60s` |
| Muse Spark `MUSE_SPARK_BASE_URL https://api.meta.ai/v1` `server/src/modules/intelligence/muse-spark-provider.ts:1` | `imageBase64` `ocrText` `medication list` `occurrences` `adherence` minimal per `context-builders.ts:1` `16k` `patientId` | label|parse|chat|draft|summary | US | `AI_PROMPT_RETENTION_ENABLED false` no training per contract | `30000ms` `max 6 turns 12 calls` `dailyBudget 50` |

Muse Spark is `muse-spark-1.2-contributor` fixed `server/src/config/index.ts:10` `MUSE_SPARK_MODEL_DOC` no fallback `§12.5`.
