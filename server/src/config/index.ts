/**
 * config/index.ts
 * Validated configuration loader.
 * - MUSE_SPARK_MODEL is allowlist-only (see modules/intelligence/model-registry.ts);
 *   defaults to "muse-spark-1.2-contributor" so existing deployments are unchanged.
 * - Rejects startup if MUSE_SPARK_MODEL is set to any value outside the allowlist.
 * - No auto-fallback to an unapproved model on outage per plan §12.5.
 * Ref: SERVER_IMPLEMENTATION_PLAN.md §22, §12.5
 */

import { isLoopbackHttpBaseUrl } from "../modules/intelligence/muse-spark-provider.js";
import {
  ALLOWED_MUSE_SPARK_MODELS,
  DEFAULT_MUSE_SPARK_MODEL,
  parseMuseSparkModel,
  type MuseSparkModel,
} from "../modules/intelligence/model-registry.js";

// Re-exported so existing importers of the config module keep working.
export { ALLOWED_MUSE_SPARK_MODELS, DEFAULT_MUSE_SPARK_MODEL, parseMuseSparkModel, type MuseSparkModel };
export { assertAllowedMuseSparkModel } from "../modules/intelligence/model-registry.js";

export interface MuseSparkConfig {
  apiKey: string;
  baseUrl: string;
  model: MuseSparkModel;
  timeoutMs: number;
  maxOutputTokens: number;
  maxAgentTurns: number;
  maxToolCalls: number;
  dailyBudget: number;
}

export interface AppConfig {
  nodeEnv: string;
  appEnv: string;
  appBuildId: string;
  host: string;
  port: number;
  publicBaseUrl: string;
  trustProxy: number;
  databaseUrl: string;
  databasePoolMin: number;
  databasePoolMax: number;
  databaseStatementTimeoutMs: number;
  accessTokenIssuer: string;
  accessTokenAudience: string;
  accessTokenPrivateKeyFile: string;
  accessTokenPublicKeyFile: string;
  accessTokenTtlSeconds: number;
  refreshTokenTtlDays: number;
  tokenHashSecret: string;
  fieldEncryptionKeyCurrent: string;
  fieldEncryptionKeyPrevious: string | undefined;
  argon2MemoryKib: number;
  argon2TimeCost: number;
  argon2Parallelism: number;
  corsAllowedOrigins: string[];
  emailProvider: string;
  emailFrom: string;
  emailApiKey: string | undefined;
  rxnormBaseUrl: string;
  rxnormTimeoutMs: number;
  museSpark: MuseSparkConfig;
  aiPromptRetentionEnabled: boolean;
  aiConversationRetentionHours: number;
  aiProposalTtlMinutes: number;
  aiVisionMaxBytes: number;
  aiVisionMaxPixels: number;
  pushProviderConfigFile: string;
  exportDirectory: string;
  exportTtlHours: number;
  logLevel: string;
  metricsBindAddress: string;
  workerConcurrency: number;
  jobMaxAttempts: number;
}

function env(name: string, fallback?: string): string | undefined {
  const v = process.env[name];
  if (v !== undefined && v !== "") return v;
  return fallback;
}

function _requireEnv(name: string, fallback?: string): string {
  const v = env(name, fallback);
  if (v === undefined) throw new Error(`Missing required env var ${name}`);
  return v;
}

function _envInt(name: string, fallback: number): number {
  const raw = env(name);
  if (raw === undefined || raw === "") return fallback;
  const n = Number.parseInt(raw, 10);
  if (Number.isNaN(n)) throw new Error(`Invalid integer for ${name}: ${raw}`);
  return n;
}

function _envBool(name: string, fallback: boolean): boolean {
  const raw = env(name);
  if (raw === undefined || raw === "") return fallback;
  if (raw === "true" || raw === "1") return true;
  if (raw === "false" || raw === "0") return false;
  throw new Error(`Invalid boolean for ${name}: ${raw}`);
}

export function loadConfig(envOverrides?: Record<string, string | undefined>): AppConfig {
  // Allow injecting overrides for testing; otherwise use process.env
  const get = (name: string, fallback?: string): string | undefined => {
    if (envOverrides && name in envOverrides) {
      const v = envOverrides[name];
      if (v !== undefined && v !== "") return v;
      return fallback;
    }
    return env(name, fallback);
  };
  const getInt = (name: string, fallback: number): number => {
    const raw = get(name);
    if (raw === undefined || raw === "") return fallback;
    const n = Number.parseInt(raw, 10);
    if (Number.isNaN(n)) throw new Error(`Invalid integer for ${name}: ${raw}`);
    return n;
  };
  const getBool = (name: string, fallback: boolean): boolean => {
    const raw = get(name);
    if (raw === undefined || raw === "") return fallback;
    if (raw === "true" || raw === "1") return true;
    if (raw === "false" || raw === "0") return false;
    throw new Error(`Invalid boolean for ${name}: ${raw}`);
  };

  const nodeEnv = get("NODE_ENV", "development") ?? "development";
  const isProduction = nodeEnv === "production";

  // Muse Spark model allowlist is enforced in every environment.
  const rawModel = get("MUSE_SPARK_MODEL");
  const museSparkModel = parseMuseSparkModel(rawModel);

  const museSparkBaseUrl = get("MUSE_SPARK_BASE_URL", "https://api.musespark.example/v1") ?? "https://api.musespark.example/v1";
  // https everywhere, with one exception: the self-hosted Antigravity gateway
  // runs on loopback http://127.0.0.1:8045 and is explicitly opted into with
  // MUSE_SPARK_ALLOW_HTTP=true. Traffic never leaves the host.
  const allowLoopbackHttp = get("MUSE_SPARK_ALLOW_HTTP") === "true" && isLoopbackHttpBaseUrl(museSparkBaseUrl);
  if (isProduction && !museSparkBaseUrl.startsWith("https://") && !allowLoopbackHttp) {
    throw new Error(
      "MUSE_SPARK_BASE_URL must be https:// in production (http://127.0.0.1 is allowed with MUSE_SPARK_ALLOW_HTTP=true for the local gateway)",
    );
  }

  const museSparkTimeoutMs = getInt("MUSE_SPARK_TIMEOUT_MS", 30000);
  const museSparkMaxOutputTokens = getInt("MUSE_SPARK_MAX_OUTPUT_TOKENS", 4096);
  const museSparkMaxAgentTurns = getInt("MUSE_SPARK_MAX_AGENT_TURNS", 6);
  const museSparkMaxToolCalls = getInt("MUSE_SPARK_MAX_TOOL_CALLS", 12);
  const museSparkDailyBudget = getInt("MUSE_SPARK_DAILY_BUDGET", 50);

  // Enforce bounded limits per plan §22
  if (museSparkTimeoutMs <= 0 || museSparkTimeoutMs > 120000) {
    throw new Error("MUSE_SPARK_TIMEOUT_MS must be between 1 and 120000");
  }
  if (museSparkMaxOutputTokens <= 0 || museSparkMaxOutputTokens > 32000) {
    throw new Error("MUSE_SPARK_MAX_OUTPUT_TOKENS must be between 1 and 32000");
  }
  if (museSparkMaxAgentTurns <= 0 || museSparkMaxAgentTurns > 20) {
    throw new Error("MUSE_SPARK_MAX_AGENT_TURNS must be between 1 and 20");
  }
  if (museSparkMaxToolCalls <= 0 || museSparkMaxToolCalls > 50) {
    throw new Error("MUSE_SPARK_MAX_TOOL_CALLS must be between 1 and 50");
  }
  if (museSparkDailyBudget <= 0 || museSparkDailyBudget > 1_000_000) {
    throw new Error("MUSE_SPARK_DAILY_BUDGET must be between 1 and 1000000");
  }

  const aiVisionMaxBytes = getInt("AI_VISION_MAX_BYTES", 4 * 1024 * 1024);
  const aiVisionMaxPixels = getInt("AI_VISION_MAX_PIXELS", 8_294_400);
  const aiProposalTtlMinutes = getInt("AI_PROPOSAL_TTL_MINUTES", 30);
  if (aiVisionMaxBytes <= 0 || aiVisionMaxBytes > 8 * 1024 * 1024) {
    throw new Error("AI_VISION_MAX_BYTES must be between 1 and 8388608");
  }
  if (aiVisionMaxPixels <= 0 || aiVisionMaxPixels > 16_777_216) {
    throw new Error("AI_VISION_MAX_PIXELS must be between 1 and 16777216");
  }
  if (aiProposalTtlMinutes <= 0 || aiProposalTtlMinutes > 1440) {
    throw new Error("AI_PROPOSAL_TTL_MINUTES must be between 1 and 1440");
  }

  const publicBaseUrl = get("PUBLIC_BASE_URL", "https://example.com") ?? "https://example.com";
  let parsedPublicBaseUrl: URL;
  try {
    parsedPublicBaseUrl = new URL(publicBaseUrl);
  } catch {
    throw new Error("PUBLIC_BASE_URL must be a valid URL");
  }
  if (isProduction && parsedPublicBaseUrl.protocol !== "https:") {
    throw new Error("PUBLIC_BASE_URL must be https:// in production");
  }
  if (isProduction && parsedPublicBaseUrl.hostname === "example.com") {
    throw new Error("PUBLIC_BASE_URL must not use example.com in production");
  }

  const corsRaw = get("CORS_ALLOWED_ORIGINS", "https://example.com") ?? "https://example.com";
  const corsAllowedOrigins = corsRaw
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (isProduction && corsAllowedOrigins.includes("*")) {
    throw new Error("CORS_ALLOWED_ORIGINS must not contain wildcard * in production");
  }
  for (const origin of corsAllowedOrigins) {
    let parsed: URL;
    try {
      parsed = new URL(origin);
    } catch {
      throw new Error(`Invalid CORS origin: ${origin}`);
    }
    if (parsed.origin !== origin || parsed.pathname !== "/" || parsed.search || parsed.hash) {
      throw new Error(`CORS origin must contain scheme and host only: ${origin}`);
    }
    if (isProduction && parsed.protocol !== "https:") {
      throw new Error(`Production CORS origins must use https: ${origin}`);
    }
  }

  // Validate production secrets are not defaults
  if (isProduction) {
    const tokenHashSecret = get("TOKEN_HASH_SECRET");
    if (!tokenHashSecret || tokenHashSecret === "replace-with-256-bit-random-hex") {
      throw new Error("TOKEN_HASH_SECRET must be set to a secure random value in production");
    }
    const fieldKey = get("FIELD_ENCRYPTION_KEY_CURRENT");
    if (!fieldKey || fieldKey === "base64-32-byte-key-current") {
      throw new Error("FIELD_ENCRYPTION_KEY_CURRENT must be set in production");
    }
  }

  const config: AppConfig = {
    nodeEnv,
    appEnv: get("APP_ENV", nodeEnv) ?? nodeEnv,
    appBuildId: get("APP_BUILD_ID", "local") ?? "local",
    host: get("HOST", "127.0.0.1") ?? "127.0.0.1",
    port: getInt("PORT", 3100),
    publicBaseUrl,
    trustProxy: getInt("TRUST_PROXY", 1),
    databaseUrl: get("DATABASE_URL", "postgres://medac:medac@localhost:5432/medac") ?? "postgres://medac:medac@localhost:5432/medac",
    databasePoolMin: getInt("DATABASE_POOL_MIN", 2),
    databasePoolMax: getInt("DATABASE_POOL_MAX", 10),
    databaseStatementTimeoutMs: getInt("DATABASE_STATEMENT_TIMEOUT_MS", 30000),
    accessTokenIssuer: get("ACCESS_TOKEN_ISSUER", "medac") ?? "medac",
    accessTokenAudience: get("ACCESS_TOKEN_AUDIENCE", "medac-api") ?? "medac-api",
    accessTokenPrivateKeyFile: get("ACCESS_TOKEN_PRIVATE_KEY_FILE", "/opt/medac/secrets/access-private.pem") ?? "/opt/medac/secrets/access-private.pem",
    accessTokenPublicKeyFile: get("ACCESS_TOKEN_PUBLIC_KEY_FILE", "/opt/medac/secrets/access-public.pem") ?? "/opt/medac/secrets/access-public.pem",
    accessTokenTtlSeconds: getInt("ACCESS_TOKEN_TTL_SECONDS", 600),
    refreshTokenTtlDays: getInt("REFRESH_TOKEN_TTL_DAYS", 30),
    tokenHashSecret: get("TOKEN_HASH_SECRET", "replace-with-256-bit-random-hex") ?? "replace-with-256-bit-random-hex",
    fieldEncryptionKeyCurrent: get("FIELD_ENCRYPTION_KEY_CURRENT", "base64-32-byte-key-current") ?? "base64-32-byte-key-current",
    fieldEncryptionKeyPrevious: get("FIELD_ENCRYPTION_KEY_PREVIOUS") || undefined,
    argon2MemoryKib: getInt("ARGON2_MEMORY_KIB", 19456),
    argon2TimeCost: getInt("ARGON2_TIME_COST", 2),
    argon2Parallelism: getInt("ARGON2_PARALLELISM", 1),
    corsAllowedOrigins,
    emailProvider: get("EMAIL_PROVIDER", "console") ?? "console",
    emailFrom: get("EMAIL_FROM", "noreply@example.com") ?? "noreply@example.com",
    emailApiKey: get("EMAIL_API_KEY") || undefined,
    rxnormBaseUrl: get("RXNORM_BASE_URL", "https://rxnav.nlm.nih.gov/REST") ?? "https://rxnav.nlm.nih.gov/REST",
    rxnormTimeoutMs: getInt("RXNORM_TIMEOUT_MS", 5000),
    museSpark: {
      apiKey: get("MUSE_SPARK_API_KEY", "") ?? "",
      baseUrl: museSparkBaseUrl,
      model: museSparkModel,
      timeoutMs: museSparkTimeoutMs,
      maxOutputTokens: museSparkMaxOutputTokens,
      maxAgentTurns: museSparkMaxAgentTurns,
      maxToolCalls: museSparkMaxToolCalls,
      dailyBudget: museSparkDailyBudget,
    },
    aiPromptRetentionEnabled: getBool("AI_PROMPT_RETENTION_ENABLED", false),
    aiConversationRetentionHours: getInt("AI_CONVERSATION_RETENTION_HOURS", 72),
    aiProposalTtlMinutes,
    aiVisionMaxBytes,
    aiVisionMaxPixels,
    pushProviderConfigFile: get("PUSH_PROVIDER_CONFIG_FILE", "/opt/medac/secrets/push.json") ?? "/opt/medac/secrets/push.json",
    exportDirectory: get("EXPORT_DIRECTORY", "/var/lib/medac/exports") ?? "/var/lib/medac/exports",
    exportTtlHours: getInt("EXPORT_TTL_HOURS", 24),
    logLevel: get("LOG_LEVEL", "info") ?? "info",
    metricsBindAddress: get("METRICS_BIND_ADDRESS", "127.0.0.1:3101") ?? "127.0.0.1:3101",
    workerConcurrency: getInt("WORKER_CONCURRENCY", 4),
    jobMaxAttempts: getInt("JOB_MAX_ATTEMPTS", 5),
  };

  return config;
}

// Singleton lazy accessor; throws on invalid MUSE_SPARK_MODEL at import time in production if called during startup
let cached: AppConfig | null = null;
export function getConfig(): AppConfig {
  if (!cached) cached = loadConfig();
  return cached;
}

// For tests: reset cache
export function __resetConfigCache(): void {
  cached = null;
}
