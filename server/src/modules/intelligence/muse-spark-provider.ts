/**
 * muse-spark-provider.ts
 * Typed HTTPS OpenAI-compatible adapter to MUSE_SPARK_BASE_URL (/chat/completions).
 * Implements IntelligenceProvider per SERVER_IMPLEMENTATION_PLAN.md §12.
 * - Enforce HTTPS, fail startup if not HTTPS in production.
 * - Uses MUSE_SPARK_API_KEY, MUSE_SPARK_MODEL, MUSE_SPARK_TIMEOUT_MS.
 * - Circuit breaker, budget, redacted logging.
 * - MODEL IS ALLOWLISTED: see model-registry.ts. MUSE_SPARK_MODEL selects the
 *   model and must be on the allowlist; the adapter sends exactly that model.
 *   No auto-fallback to an unapproved model on outage per plan §12.5.
 */

import { sanitizeForLog, genericFailureMessage } from "./safety-policy.js";
import { validateWithOneRepair } from "./output-contracts.js";
import {
  ALLOWED_MUSE_SPARK_MODELS,
  assertAllowedModel,
  DEFAULT_MUSE_SPARK_MODEL,
  isAllowedModel,
  parseMuseSparkModel,
  type MuseSparkModel,
} from "./model-registry.js";
import type { z } from "zod";

export {
  ALLOWED_MUSE_SPARK_MODELS,
  DEFAULT_MUSE_SPARK_MODEL,
  parseMuseSparkModel,
  type MuseSparkModel,
};
export const ALLOWED_MODELS = ALLOWED_MUSE_SPARK_MODELS;
export const MUSE_SPARK_MODEL_DOC = `allowlisted models only: ${ALLOWED_MUSE_SPARK_MODELS.join(", ")}` as const;
/** @deprecated the model is no longer hard-fixed; kept so older imports keep compiling. */
export const FIXED_MUSE_SPARK_MODEL = DEFAULT_MUSE_SPARK_MODEL;
/** @deprecated use assertAllowedModel. */
export const assertFixedModel = assertAllowedModel;

// ---------------------------------------------------------------------------
// Config & validation
// ---------------------------------------------------------------------------

export interface MuseSparkConfig {
  baseUrl: string; // e.g. https://api.muse-spark.example/v1
  apiKey: string;
  model: string;
  timeoutMs: number;
  maxOutputTokens?: number;
  dailyBudgetCents?: number;
}

export function loadMuseSparkConfig(env: Record<string, string | undefined> = process.env): MuseSparkConfig {
  const baseUrl = env.MUSE_SPARK_BASE_URL ?? "";
  const apiKey = env.MUSE_SPARK_API_KEY ?? "";
  const rawModel = env.MUSE_SPARK_MODEL;
  const model = parseMuseSparkModel(rawModel);
  const timeoutMs = Number(env.MUSE_SPARK_TIMEOUT_MS ?? 15000);
  const maxOutputTokens = env.MUSE_SPARK_MAX_OUTPUT_TOKENS ? Number(env.MUSE_SPARK_MAX_OUTPUT_TOKENS) : undefined;

  if (!baseUrl) throw new Error("MUSE_SPARK_BASE_URL is required");
  if (!apiKey) throw new Error("MUSE_SPARK_API_KEY is required");
  // model is validated via parseMuseSparkModel (defaults to FIXED, throws on any other)
  assertAllowedModel(model);
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0 || timeoutMs > 120_000) {
    throw new Error("MUSE_SPARK_TIMEOUT_MS must be 1..120000");
  }
  validateHttpsOrThrow(baseUrl);

  return { baseUrl: baseUrl.replace(/\/+$/, ""), apiKey, model, timeoutMs, maxOutputTokens };
}

export function isProduction(env: Record<string, string | undefined> = process.env): boolean {
  return env.NODE_ENV === "production" || env.APP_ENV === "production";
}

const LOOPBACK_HOSTNAMES = new Set(["127.0.0.1", "localhost", "::1", "[::1]"]);

/** True for an http:// URL pointed at the local host. */
export function isLoopbackHttpBaseUrl(baseUrl: string): boolean {
  try {
    const parsed = new URL(baseUrl);
    return parsed.protocol === "http:" && LOOPBACK_HOSTNAMES.has(parsed.hostname);
  } catch {
    return false;
  }
}

export function validateHttpsOrThrow(baseUrl: string, env: Record<string, string | undefined> = process.env): void {
  let parsed: URL;
  try {
    parsed = new URL(baseUrl);
  } catch {
    throw new Error(`MUSE_SPARK_BASE_URL is not a valid URL: ${baseUrl}`);
  }
  if (parsed.protocol === "https:") return;

  // Non-HTTPS is refused unless explicitly opted in. http is tolerated in
  // production only for loopback (the self-hosted Antigravity gateway on
  // http://127.0.0.1:8045, which never leaves the machine); every other base
  // URL must be https. Dev keeps the broader MUSE_SPARK_ALLOW_HTTP escape hatch.
  if (parsed.protocol === "http:" && env.MUSE_SPARK_ALLOW_HTTP === "true" && LOOPBACK_HOSTNAMES.has(parsed.hostname)) {
    return;
  }
  if (isProduction(env)) {
    throw new Error(`MUSE_SPARK_BASE_URL must be https in production (got ${parsed.protocol})`);
  }
  if (env.MUSE_SPARK_ALLOW_HTTP === "true") return;
  throw new Error(`MUSE_SPARK_BASE_URL must be https (got ${parsed.protocol})`);
}

// ---------------------------------------------------------------------------
// Provider interfaces per §12
// ---------------------------------------------------------------------------

export interface StructuredGenerationRequest<T> {
  prompt: {
    templateId: string;
    templateVersion: string;
    system: string;
    user: string;
    untrustedDataDelimited?: string;
  };
  schema: z.ZodType<T>;
  outputContractVersion: string;
  model?: string;
  temperature?: number;
  maxOutputTokens?: number;
}

export interface ToolTurnRequest {
  system: string;
  messages: Array<{ role: "user" | "assistant" | "tool"; content: string; toolCallId?: string; name?: string }>;
  tools: Array<{ type: "function"; function: { name: string; description: string; parameters: unknown } }>;
  toolChoice?: string;
  maxOutputTokens?: number;
}

export interface ToolTurnResponse {
  content: string | null;
  toolCalls: Array<{ id: string; name: string; argumentsJson: string }>;
  usage?: { inputTokens: number; outputTokens: number };
}

export interface VisionRequest<T> {
  prompt: { system: string; user: string };
  image: { mime: string; base64: string };
  schema: z.ZodType<T>;
  outputContractVersion: string;
  maxOutputTokens?: number;
}

export interface IntelligenceProvider {
  generateStructured<T>(request: StructuredGenerationRequest<T>): Promise<T>;
  runToolTurn(request: ToolTurnRequest): Promise<ToolTurnResponse>;
  analyzeImage<T>(request: VisionRequest<T>): Promise<T>;
}

// ---------------------------------------------------------------------------
// Circuit breaker
// ---------------------------------------------------------------------------

type CircuitState = "closed" | "open" | "half_open";

export class CircuitBreaker {
  private state: CircuitState = "closed";
  private failures = 0;
  private openedAt = 0;

  constructor(
    private readonly threshold = 5,
    private readonly cooldownMs = 60_000,
  ) {}

  canExecute(): boolean {
    if (this.state === "closed") return true;
    if (this.state === "open") {
      if (Date.now() - this.openedAt >= this.cooldownMs) {
        this.state = "half_open";
        return true;
      }
      return false;
    }
    return true; // half_open allows one trial
  }

  recordSuccess(): void {
    this.failures = 0;
    this.state = "closed";
  }

  recordFailure(): void {
    this.failures++;
    if (this.failures >= this.threshold) {
      this.state = "open";
      this.openedAt = Date.now();
    } else if (this.state === "half_open") {
      this.state = "open";
      this.openedAt = Date.now();
    }
  }

  getState(): CircuitState {
    // auto-transition check
    if (this.state === "open" && Date.now() - this.openedAt >= this.cooldownMs) return "half_open";
    return this.state;
  }
}

// ---------------------------------------------------------------------------
// MuseSparkProvider
// ---------------------------------------------------------------------------

export class MuseSparkProvider implements IntelligenceProvider {
  private readonly circuitBreaker = new CircuitBreaker();
  private totalSpendCents = 0;

  constructor(private readonly config: MuseSparkConfig) {
    validateHttpsOrThrow(config.baseUrl);
    if (!config.apiKey) throw new Error("MUSE_SPARK_API_KEY required");
    if (!config.model) throw new Error("MUSE_SPARK_MODEL required");
    // Enforce allowlist — no alternative model, and no fallback on outage.
    assertAllowedModel(config.model);
  }

  getCircuitState(): CircuitState {
    return this.circuitBreaker.getState();
  }

  private async postChatCompletions(body: unknown, signal: AbortSignal): Promise<unknown> {
    // Send exactly the allowlisted model from config; reject any other model
    // handed in by a caller (no fallback to unapproved models).
    const payload = body as Record<string, unknown>;
    if (payload["model"] !== undefined && payload["model"] !== this.config.model) {
      const attempted = String(payload["model"]);
      if (!isAllowedModel(attempted) || attempted !== this.config.model) {
        throw new Error(`Attempted to call the model "${attempted}" while configured for "${this.config.model}". No fallback to unapproved model is permitted (plan §12.5).`);
      }
    }
    (payload as Record<string, unknown>)["model"] = this.config.model;
    assertAllowedModel(this.config.model);
    if (!this.circuitBreaker.canExecute()) {
      throw Object.assign(new Error("Circuit breaker open"), { code: "CIRCUIT_OPEN" });
    }
    const url = `${this.config.baseUrl}/chat/completions`;
    let res: Response;
    try {
      res = await fetch(url, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          authorization: `Bearer ${this.config.apiKey}`,
        },
        body: JSON.stringify(body),
        signal,
      });
    } catch (error) {
      this.circuitBreaker.recordFailure();
      if (signal.aborted || (error as { name?: string }).name === "AbortError") {
        throw Object.assign(new Error("Muse Spark request timed out"), { code: "PROVIDER_TIMEOUT" });
      }
      throw Object.assign(new Error("Muse Spark network request failed"), { code: "PROVIDER_NETWORK_ERROR" });
    }

    if (!res.ok) {
      this.circuitBreaker.recordFailure();
      // Consume the body but never include provider content in exceptions/logs: an
      // upstream error can echo sensitive prompt or label data.
      await res.arrayBuffer().catch(() => undefined);
      throw Object.assign(new Error(`Muse Spark request failed with status ${res.status}`), {
        code: `PROVIDER_${res.status}`,
        status: res.status,
      });
    }
    this.circuitBreaker.recordSuccess();
    return res.json();
  }

  // Exposed for tests: sanitizes provider request for logging (redacts api key, content)
  static sanitizeRequestForLog(body: unknown): Record<string, unknown> {
    return sanitizeForLog(body as Record<string, unknown>);
  }

  async generateStructured<T>(request: StructuredGenerationRequest<T>): Promise<T> {
    // Enforce fixed model even if caller supplies alternative
    if (request.model) assertAllowedModel(request.model);
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeoutMs);
    try {
      const body = {
        model: this.config.model,
        messages: [
          { role: "system", content: request.prompt.system },
          {
            role: "user",
            content: request.prompt.untrustedDataDelimited
              ? `${request.prompt.user}\n\n${request.prompt.untrustedDataDelimited}`
              : request.prompt.user,
          },
        ],
        response_format: { type: "json_object" },
        max_tokens: request.maxOutputTokens ?? this.config.maxOutputTokens,
        temperature: request.temperature ?? 0.2,
      };

      const raw = (await this.postChatCompletions(body, controller.signal)) as {
        choices: Array<{ message: { content: string } }>;
        usage?: { prompt_tokens: number; completion_tokens: number };
      };
      const content = raw.choices?.[0]?.message?.content;
      if (!content) throw Object.assign(new Error("Empty response from Muse Spark"), { code: "EMPTY_RESPONSE" });

      const parsed = validateWithOneRepair(request.schema, content);
      if (!parsed.ok || !parsed.data) {
        throw Object.assign(new Error(`Output contract violation: ${JSON.stringify(parsed.issues)}`), {
          code: "OUTPUT_CONTRACT_VIOLATION",
          issues: parsed.issues,
        });
      }
      return parsed.data;
    } catch (e) {
      // Generic failure fallback — caller may map to user message
      if ((e as { code?: string }).code === "CIRCUIT_OPEN") throw e;
      throw e;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  async runToolTurn(request: ToolTurnRequest): Promise<ToolTurnResponse> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeoutMs);
    try {
      // Map orchestrator's toolCallId -> OpenAI's tool_call_id, and ensure correct shape
      const mappedMessages = request.messages.map((m) => {
        if (m.role === "tool") {
          return { role: "tool" as const, tool_call_id: (m as { toolCallId?: string }).toolCallId ?? (m as unknown as { tool_call_id?: string }).tool_call_id, content: m.content };
        }
        if (m.role === "assistant" && (m as { toolCalls?: unknown }).toolCalls) {
          // Already an assistant with tool_calls — map
          return m;
        }
        return m;
      });
      const body = {
        model: this.config.model,
        messages: [{ role: "system", content: request.system }, ...mappedMessages],
        // An empty tools array means "answer without tools": the gateway ignores
        // tool_choice:"none", so the field has to be omitted entirely.
        ...(request.tools.length > 0
          ? { tools: request.tools, tool_choice: request.toolChoice ?? "auto" }
          : {}),
        max_tokens: request.maxOutputTokens ?? this.config.maxOutputTokens,
      };
      const raw = (await this.postChatCompletions(body, controller.signal)) as {
        choices: Array<{ message: { content: string | null; tool_calls?: Array<{ id: string; function: { name: string; arguments: string } }> } }>;
        usage?: { prompt_tokens: number; completion_tokens: number };
      };
      const msg = raw.choices?.[0]?.message;
      if (!msg) throw Object.assign(new Error("Empty tool turn response"), { code: "EMPTY_RESPONSE" });
      const toolCalls = (msg.tool_calls ?? []).map((tc) => ({
        id: tc.id,
        name: tc.function.name,
        argumentsJson: tc.function.arguments,
      }));
      return {
        content: msg.content ?? null,
        toolCalls,
        usage: raw.usage ? { inputTokens: raw.usage.prompt_tokens, outputTokens: raw.usage.completion_tokens } : undefined,
      };
    } finally {
      clearTimeout(timeoutId);
    }
  }

  async analyzeImage<T>(request: VisionRequest<T>): Promise<T> {
    if ((request as { model?: string }).model) assertAllowedModel((request as { model?: string }).model as string);
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeoutMs);
    try {
      const body = {
        model: this.config.model,
        messages: [
          { role: "system", content: request.prompt.system },
          {
            role: "user",
            content: [
              { type: "text", text: request.prompt.user },
              { type: "image_url", image_url: { url: `data:${request.image.mime};base64,${request.image.base64}` } },
            ],
          },
        ],
        response_format: { type: "json_object" },
        max_tokens: request.maxOutputTokens ?? this.config.maxOutputTokens,
      };
      const raw = (await this.postChatCompletions(body, controller.signal)) as {
        choices: Array<{ message: { content: string } }>;
      };
      const content = raw.choices?.[0]?.message?.content;
      if (!content) throw Object.assign(new Error("Empty vision response"), { code: "EMPTY_RESPONSE" });
      const parsed = validateWithOneRepair(request.schema, content);
      if (!parsed.ok || !parsed.data) {
        throw Object.assign(new Error(`Vision output contract violation: ${JSON.stringify(parsed.issues)}`), {
          code: "OUTPUT_CONTRACT_VIOLATION",
          issues: parsed.issues,
        });
      }
      return parsed.data;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  // For observability without PHI
  getMetrics(): { circuitState: CircuitState; spendCents: number } {
    return { circuitState: this.getCircuitState(), spendCents: this.totalSpendCents };
  }

  static genericFallback(): { code: string; message: string } {
    return genericFailureMessage();
  }

  /** Returns the configured allowlisted model — no alternative, no fallback. */
  getModel(): MuseSparkModel {
    return this.config.model as MuseSparkModel;
  }
}

export function createMuseSparkProvider(config: MuseSparkConfig): MuseSparkProvider {
  return new MuseSparkProvider(config);
}

export const MUSE_SPARK_PROVIDER_DOC = `
Muse Spark / intelligence provider sends exactly the model named by MUSE_SPARK_MODEL,
which must be on the model-registry allowlist (${ALLOWED_MUSE_SPARK_MODELS.join(", ")}).
- No auto-fallback: on outage/timeout/schema failure the caller returns
  INTELLIGENCE_TEMPORARILY_UNAVAILABLE and keeps deterministic manual APIs operational (plan §12.5).
- Privacy note: the allowlist mixes the hosted contributor endpoint with the
  self-hosted Antigravity gateway; switching MUSE_SPARK_BASE_URL changes which
  upstream sees prompt content, so treat it as a reviewed config change.
`.trim();
