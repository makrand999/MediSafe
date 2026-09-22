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
- Model: `{MUSE_SPARK_MODEL}` — must be on the allowlist in `server/src/modules/intelligence/model-registry.ts`
- Timeout: `MUSE_SPARK_TIMEOUT_MS` (validated 1..120s; per-request AbortSignal)
- Startup gate: `MUSE_SPARK_BASE_URL` must be `https:` in production (`NODE_ENV` or `APP_ENV=production`); otherwise startup throws. The only exception is loopback (`http://127.0.0.1` / `localhost`) with `MUSE_SPARK_ALLOW_HTTP=true`, used by the self-hosted Antigravity gateway. In dev the flag allows any http host for local fixtures.
- Model allowlist: switching models is a config change, not a code change — add the model to the registry (reviewed) and set `MUSE_SPARK_MODEL`. No auto-fallback on outage: the caller returns `INTELLIGENCE_TEMPORARILY_UNAVAILABLE`.
- Structured output: `response_format: {type:"json_object"}` then strict Zod validation. Markdown code fences (```json … ```) returned by some models are stripped deterministically before parsing. At most one repair attempt (deterministic sanitization or model repair). Regex extraction is not accepted.
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
MUSE_SPARK_API_KEY, MUSE_SPARK_BASE_URL (https, or loopback http with
MUSE_SPARK_ALLOW_HTTP=true), MUSE_SPARK_MODEL,
MUSE_SPARK_TIMEOUT_MS, MUSE_SPARK_MAX_OUTPUT_TOKENS, MUSE_SPARK_MAX_AGENT_TURNS,
MUSE_SPARK_MAX_TOOL_CALLS, MUSE_SPARK_DAILY_BUDGET, AI_PROPOSAL_TTL_MINUTES,
AI_VISION_MAX_BYTES, AI_VISION_MAX_PIXELS, AI_PROMPT_RETENTION_ENABLED, etc.
```

Configuration loader must refuse insecure defaults and unbounded limits.

## 8a. Local Antigravity gateway (provider swap)

The OpenAI-compatible Antigravity gateway (`antigravity.service`, `antigravity-tools --headless`)
listens on `http://127.0.0.1:8045` and is wired up like this in `/opt/medac/env/api.env`:

```
MUSE_SPARK_BASE_URL=http://127.0.0.1:8045/v1
MUSE_SPARK_API_KEY=<gateway API_KEY from /etc/antigravity.env>
MUSE_SPARK_MODEL=gemini-3.8-flash-high
MUSE_SPARK_ALLOW_HTTP=true
```

Verified against the gateway (2026-09-21): `/chat/completions` with Bearer auth,
`response_format: json_object`, tool calls (`finish_reason: tool_calls`), and vision via
`image_url` data URLs. Latency for a label-OCR round trip: ~3 s (gemini-3.8-flash-high),
~6 s (gemini-3-pro-high). Gemini answers are frequently markdown-fenced, which
`parseJsonStrict` now strips deterministically.

Rollback: point `MUSE_SPARK_BASE_URL`/`MUSE_SPARK_API_KEY`/`MUSE_SPARK_MODEL` back at the
hosted endpoint and `systemctl restart medac-api-new medac-worker`.

**Privacy:** the gateway proxies to Google/Claude upstreams through the operator's own
accounts, so prompt/PHI routing differs from the hosted contributor endpoint. Treat a
base-URL change as a reviewed configuration decision, not an incident workaround.

## 9. Docs & evaluation

- Prompt/output changes are versioned and evaluated via fixtures (`AI_EVALUATIONS.md`).
- Model/provider changes documented (behavior may shift).
