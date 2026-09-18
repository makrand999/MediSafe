import { describe, it, expect } from "vitest";
import { loadConfig } from "../../src/config/index.js";

function baseEnv(overrides: Record<string, string | undefined> = {}): Record<string, string | undefined> {
  return {
    NODE_ENV: "production",
    PUBLIC_BASE_URL: "https://medac.test",
    CORS_ALLOWED_ORIGINS: "https://medac.test",
    DATABASE_URL: "postgres://medac:medac@localhost:5432/medac",
    TOKEN_HASH_SECRET: "a".repeat(64),
    FIELD_ENCRYPTION_KEY_CURRENT: "b".repeat(64),
    MUSE_SPARK_BASE_URL: "https://api.musespark.example/v1",
    MUSE_SPARK_MODEL: "muse-spark-1.2-contributor",
    MUSE_SPARK_TIMEOUT_MS: "30000",
    MUSE_SPARK_MAX_OUTPUT_TOKENS: "4096",
    MUSE_SPARK_MAX_AGENT_TURNS: "6",
    MUSE_SPARK_MAX_TOOL_CALLS: "12",
    ...overrides,
  };
}

describe("config validation", () => {
  it("rejects wildcard CORS in production", () => {
    expect(() => loadConfig(baseEnv({ CORS_ALLOWED_ORIGINS: "*" }))).toThrow(/wildcard/i);
    expect(() => loadConfig(baseEnv({ CORS_ALLOWED_ORIGINS: "https://a.com,*,https://b.com" }))).toThrow(/wildcard/i);
  });

  it("rejects non-HTTPS PUBLIC_BASE_URL in production", () => {
    expect(() => loadConfig(baseEnv({ PUBLIC_BASE_URL: "http://example.com" }))).toThrow(/PUBLIC_BASE_URL.*https/i);
  });

  it("rejects non-HTTPS MUSE_SPARK_BASE_URL in production", () => {
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_BASE_URL: "http://api.example.com" }))).toThrow(/MUSE_SPARK_BASE_URL.*https/i);
  });

  it("rejects missing token/encryption keys in production", () => {
    expect(() => loadConfig(baseEnv({ TOKEN_HASH_SECRET: undefined }))).toThrow(/TOKEN_HASH_SECRET/i);
    expect(() => loadConfig(baseEnv({ FIELD_ENCRYPTION_KEY_CURRENT: undefined }))).toThrow(/FIELD_ENCRYPTION_KEY_CURRENT/i);
    expect(() => loadConfig(baseEnv({ TOKEN_HASH_SECRET: "replace-with-256-bit-random-hex" }))).toThrow(/TOKEN_HASH_SECRET/i);
  });

  it("rejects unbounded Muse Spark limits", () => {
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MAX_AGENT_TURNS: "0" }))).toThrow();
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MAX_AGENT_TURNS: "100" }))).toThrow();
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MAX_TOOL_CALLS: "0" }))).toThrow();
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MAX_TOOL_CALLS: "100" }))).toThrow();
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MAX_OUTPUT_TOKENS: "0" }))).toThrow();
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MAX_OUTPUT_TOKENS: "99999" }))).toThrow();
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_TIMEOUT_MS: "0" }))).toThrow();
  });

  it("rejects invalid MUSE_SPARK_MODEL (allowlist)", () => {
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MODEL: "gpt-4" }))).toThrow(/MUSE_SPARK_MODEL/i);
    expect(() => loadConfig(baseEnv({ MUSE_SPARK_MODEL: "muse-spark-1.2-contributor-wrong" }))).toThrow();
  });

  it("accepts valid production config", () => {
    const cfg = loadConfig(baseEnv());
    expect(cfg.publicBaseUrl).toBe("https://medac.test");
    expect(cfg.museSpark.model).toBe("muse-spark-1.2-contributor");
  });

  it("rejects insecure production CORS origins", () => {
    expect(() => loadConfig(baseEnv({ CORS_ALLOWED_ORIGINS: "http://medac.test" }))).toThrow(/CORS.*https/i);
  });

  it("allows http PUBLIC_BASE_URL in non-production (development)", () => {
    const cfg = loadConfig(baseEnv({ NODE_ENV: "development", PUBLIC_BASE_URL: "http://localhost:3100" }));
    expect(cfg.publicBaseUrl).toBe("http://localhost:3100");
  });
});
