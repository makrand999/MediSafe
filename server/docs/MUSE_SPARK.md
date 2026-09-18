# MUSE_SPARK — Provider Contract & Intelligence Design

Ref: `SERVER_IMPLEMENTATION_PLAN.md` §12, §22, §25

## 1. Provider contract

Interface isolated behind typed adapter:

```ts
interface IntelligenceProvider {
  generateStructured<T>(request: StructuredGenerationRequest<T>): Promise<T>;
  runToolTurn(request: ToolTurnRequest): Promise<ToolTurnResponse>;
  analyzeImage<T>(request: VisionRequest<T>): Promise<T>;
}
```

- Implementation: `server/src/modules/intelligence/muse-spark-provider.ts` (`MuseSparkProvider`)
- Transport: HTTPS `POST {MUSE_SPARK_BASE_URL}/chat/completions` (OpenAI-compatible)
- Auth: `Authorization: Bearer {MUSE_SPARK_API_KEY}`
- Model: `{MUSE_SPARK_MODEL}`
- Timeout: `MUSE_SPARK_TIMEOUT_MS` (validated 1..120s; per-request AbortSignal)
- Startup gate: `MUSE_SPARK_BASE_URL` must be `https:` in production (`NODE_ENV` or `APP_ENV=production`); otherwise startup throws. In dev, `MUSE_SPARK_ALLOW_HTTP=true` may bypass for local fixtures.
- Structured output: `response_format: {type:"json_object"}` then strict Zod validation. At most one repair attempt (deterministic sanitization or model repair). Regex extraction is not accepted.
- Vision: `messages[].content` multipart with `image_url: data:{mime};base64,{data}`. MIME allowlist + magic-byte verification before forwarding; decoded size limit 4 MB.

Module: `server/src/modules/intelligence/muse-spark-provider.ts` also exports `loadMuseSparkConfig()`, `CircuitBreaker`, metrics.

## 2. Tasks (§12.1)

Muse Spark is first-class for: label image understanding/OCR, parsing directions, ambiguity detection, schedule drafting, conversational queries via tools, pausing flows, adherence/inventory/history summaries, non-causal timeline observations, tool selection/sequencing.

Deterministic server tools remain authoritative for state; model never mutates DB directly.

## 3. Prompt registry

`server/src/modules/intelligence/prompt-registry.ts`

- Versioned prompts in source control: `PROMPT_REGISTRY["{id}@{version}"]`
- Each entry: `id`, `version`, `purpose`, `system`, `outputContractVersion`, `createdAt`
- Initial templates: `label_interpretation@v1`, `instruction_parse@v1`, `schedule_draft@v1`, `assistant_turn@v1`, `summary@v1`, `timeline_pattern@v1`
- Every `ai_runs` row stores `prompt_template_id/version` + `output_contract_version` + `safety_policy_version`.

Label prompt explicitly forbids inventing purpose/instructions/typical times.

## 4. Output contracts

`server/src/modules/intelligence/output-contracts.ts`

- Zod strict schemas: `LabelInterpretation`, `InstructionParse`, `ScheduleDraft`, `Summary`
- Reject unknown keys, invalid enums/units/dates, overlong strings.
- Require `evidence[]` (field+span+confidence) and `uncertain_fields[]`; `clarifying_questions[]`; `confidence`.
- Nullable fields represent `unknown` rather than guessing.
- Label contract extracts only `observed_text`, `candidate_name`, `candidate_generic_name`, `strength_value/unit`, `form`, `route`, `label_directions_text`, `prescriber_fields`, `uncertain_fields`, `clarifying_questions`, `evidence/confidence` — must NOT contain `purpose/instructions/typical_times`.
- Validation: `validateStrict` / `parseJsonStrict` / `validateWithOneRepair` (at most one repair).

## 5. Context builders

`server/src/modules/intelligence/context-builders.ts`

Purpose-specific minimal builders; each requires `patientId` (patient-scoped), enforces `MAX_CONTEXT_BYTES=16k` / `~4k tokens`, records `categories` sent:

- `buildLabelContext` — locale + OCR excerpt + image meta only
- `buildScheduleDraftContext` — medication stub + explicit constraints + timezone + optional current schedule
- `buildTodayAssistantContext` — today occurrences (≤50) + timezone
- `buildAdherenceContext` — deterministic report + display names
- `buildInventoryContext` — balance/forecast stub
- `buildSymptomTimelineContext` — up to 50 entries, non-causal instruction

Do not send passwords/tokens/caregiver contacts/audit records/unrelated patient data.

## 6. Privacy & safety

`server/src/modules/intelligence/safety-policy.ts`

- PHI redaction: `redactPhiText`, `sanitizeForLog` (removes tokens, medication names, notes, images, prompts/responses from ordinary logs)
- Prompt-injection: `delimitUntrusted`, `injectionGuardSystemAddendum` — labels/OCR/notes/tool output wrapped as `UNTRUSTED_DATA`.
- Rate/spend: `RateLimiter` (per-user minute/hour + daily spend cents); provider-side project/key with alerts.
- Images: allowlist `image/jpeg|png|webp`, magic-byte check, decoded ≤4 MB, reject base64 bombs, metadata stripping noted, disclosure string.
- Fallback: `genericFailureMessage()` — deterministic manual-entry path when unavailable; never fabricate `08:00/tablet/0.7`.
- `SAFETY_POLICY_VERSION=1.0.0`

## 7. Budgets & resilience

- Per-route limits: image bytes/pixels, max turns/tool calls/output tokens, provider timeout, budget (orchestrator checks).
- Circuit breaker: open after 5 failures, 60s cooldown → half-open trial.
- Retry: bounded backoff/jitter for transient failures only; do not switch to unapproved model.
- Cache: only safe stable tasks by HMAC fingerprint + patient scope; never cross-patient conversational cache.

## 8. Environment

See `.env.example` and `SERVER_IMPLEMENTATION_PLAN.md` §22. Key vars:

```
MUSE_SPARK_API_KEY, MUSE_SPARK_BASE_URL (https), MUSE_SPARK_MODEL,
MUSE_SPARK_TIMEOUT_MS, MUSE_SPARK_MAX_OUTPUT_TOKENS, MUSE_SPARK_MAX_AGENT_TURNS,
MUSE_SPARK_MAX_TOOL_CALLS, MUSE_SPARK_DAILY_BUDGET, AI_PROPOSAL_TTL_MINUTES,
AI_VISION_MAX_BYTES, AI_VISION_MAX_PIXELS, AI_PROMPT_RETENTION_ENABLED, etc.
```

Configuration loader must refuse insecure defaults and unbounded limits.

## 9. Docs & evaluation

- Prompt/output changes are versioned and evaluated via fixtures (`AI_EVALUATIONS.md`).
- Model/provider changes documented (behavior may shift).
