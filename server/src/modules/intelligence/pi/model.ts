/**
 * Pi harness — model definition for the medac gateway.
 *
 * Pi is a TypeScript agent runtime (MIT, @earendil-works/pi-*). This module is
 * part of the flagged adoption: the assistant conversation path can run on Pi's
 * Agent loop instead of our own orchestrator, while every medac-specific
 * concern (tools, authorization, proposals, audit, PHI handling) stays ours.
 *
 * Ref: ASSISTANT_HARNESS=pi, docs/PI_HARNESS.md
 */

import type { Model } from "@earendil-works/pi-ai";

export interface GatewayModelConfig {
  /** Model id as served by the gateway, e.g. gemini-3.8-flash-low. */
  model: string;
  /** OpenAI-compatible base URL, e.g. http://127.0.0.1:8045/v1 */
  baseUrl: string;
  maxOutputTokens?: number;
  contextWindow?: number;
}

/**
 * Declares our gateway as a Pi model. Pi speaks the OpenAI completions wire
 * format here, so no adapter code is needed — only this descriptor.
 */
export function buildGatewayModel(cfg: GatewayModelConfig): Model<"openai-completions"> {
  return {
    id: cfg.model,
    name: `${cfg.model} (medac gateway)`,
    api: "openai-completions",
    provider: "medac-gateway",
    baseUrl: cfg.baseUrl,
    reasoning: false,
    input: ["text"],
    // Subscription gateway: no per-token price, so Pi's cost accounting is 0.
    // Request quotas live in our own rate limiter instead.
    cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
    contextWindow: cfg.contextWindow ?? 128_000,
    maxTokens: cfg.maxOutputTokens ?? 4096,
  };
}
