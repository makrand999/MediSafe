/**
 * model-registry.ts
 * Single source of truth for the LLM models the intelligence pipeline may call.
 *
 * The pipeline is deliberately allowlist-only: a model must be named here and
 * explicitly selected via MUSE_SPARK_MODEL to be used. There is still no
 * auto-fallback — on outage/timeout/schema failure the caller must return
 * INTELLIGENCE_TEMPORARILY_UNAVAILABLE (plan §12.5), never silently switch to
 * a model with different privacy behavior.
 *
 * Two families are approved:
 *  - "muse-spark-1.2-contributor": the original hosted contributor endpoint.
 *  - Antigravity gateway models (self-hosted, OpenAI-compatible on loopback):
 *    Gemini 3.x flash/pro. Verified against http://127.0.0.1:8045 for chat,
 *    JSON mode, tool calls and vision.
 *
 * Ref: SERVER_IMPLEMENTATION_PLAN.md §12.5, §22
 */

export const ALLOWED_MUSE_SPARK_MODELS = [
  "muse-spark-1.2-contributor",
  "gemini-3.8-flash-high",
  "gemini-3.8-flash-medium",
  "gemini-3.7-flash",
  "gemini-3-pro-high",
] as const;

export type MuseSparkModel = (typeof ALLOWED_MUSE_SPARK_MODELS)[number];

/** Used when MUSE_SPARK_MODEL is not set, so existing deployments are unchanged. */
export const DEFAULT_MUSE_SPARK_MODEL: MuseSparkModel = "muse-spark-1.2-contributor";

/**
 * Validates MUSE_SPARK_MODEL.
 * - Empty/undefined returns DEFAULT_MUSE_SPARK_MODEL.
 * - Any value outside the allowlist throws; never returns an alternative.
 */
export function parseMuseSparkModel(raw: string | undefined): MuseSparkModel {
  const trimmed = raw?.trim();
  if (!trimmed) return DEFAULT_MUSE_SPARK_MODEL;
  if ((ALLOWED_MUSE_SPARK_MODELS as readonly string[]).includes(trimmed)) {
    return trimmed as MuseSparkModel;
  }
  throw new Error(
    `Invalid MUSE_SPARK_MODEL "${trimmed}". Only supported model is "${DEFAULT_MUSE_SPARK_MODEL}" unless explicitly allowlisted (allowed: ${ALLOWED_MUSE_SPARK_MODELS.join(", ")}). No auto-fallback to unapproved model is permitted (plan §12.5).`,
  );
}

/** Asserts a model string is allowlisted; helper for callers holding a raw string. */
export function assertAllowedModel(model: string): asserts model is MuseSparkModel {
  if ((ALLOWED_MUSE_SPARK_MODELS as readonly string[]).includes(model)) return;
  throw new Error(
    `Model "${model}" is not allowed. Only "${DEFAULT_MUSE_SPARK_MODEL}" (allowed: ${ALLOWED_MUSE_SPARK_MODELS.join(", ")}) is supported. No fallback to unapproved model is permitted (plan §12.5).`,
  );
}

export function isAllowedModel(model: string): model is MuseSparkModel {
  return (ALLOWED_MUSE_SPARK_MODELS as readonly string[]).includes(model);
}

/** @deprecated use assertAllowedModel; kept for existing importers. */
export const assertAllowedMuseSparkModel = assertAllowedModel;
