import { describe, it, expect, vi } from "vitest";
import { MuseSparkProvider, validateHttpsOrThrow, loadMuseSparkConfig } from "../../../src/modules/intelligence/muse-spark-provider.js";
import { sanitizeForLog, redactPhiText, validateVisionInput, verifyImageMagicBytes } from "../../../src/modules/intelligence/safety-policy.js";

describe("muse-spark-provider HTTPS enforcement", () => {
  it("rejects http in production", () => {
    expect(() => validateHttpsOrThrow("http://example.com", { NODE_ENV: "production" })).toThrow(/https/);
  });
  it("rejects http by default even in dev", () => {
    expect(() => validateHttpsOrThrow("http://example.com", { NODE_ENV: "development" })).toThrow(/https/);
  });
  it("allows https", () => {
    expect(() => validateHttpsOrThrow("https://api.example.com", { NODE_ENV: "production" })).not.toThrow();
  });
  it("loadMuseSparkConfig fails if not https in production", () => {
    expect(() =>
      loadMuseSparkConfig({
        MUSE_SPARK_BASE_URL: "http://insecure.example.com",
        MUSE_SPARK_API_KEY: "k",
        MUSE_SPARK_MODEL: "muse-spark-1.2-contributor",
        NODE_ENV: "production",
      }),
    ).toThrow();
  });
});

describe("provider redaction", () => {
  it("sanitizeForLog redacts sensitive keys and does not leak PHI", () => {
    const input = {
      api_key: "secret123",
      authorization: "Bearer token",
      medication_name: "Lisinopril",
      note: "patient symptom",
      safe: "hello",
      nested: { password: "hunter2", keep: "ok" },
    };
    const out = sanitizeForLog(input as unknown as Record<string, unknown>);
    expect(out.api_key).toBe("[REDACTED]");
    expect(out.authorization).toBe("[REDACTED]");
    expect(out.medication_name).toBe("[REDACTED]");
    expect(out.safe).toBe("hello");
    expect((out.nested as Record<string, unknown>).password).toBe("[REDACTED]");
  });

  it("redactPhiText handles emails", () => {
    expect(redactPhiText("contact me at foo@example.com please")).toContain("[REDACTED_EMAIL]");
  });

  it("MuseSparkProvider.sanitizeRequestForLog redacts", () => {
    const redacted = MuseSparkProvider.sanitizeRequestForLog({ api_key: "x", model: "muse-spark-1.2-contributor", prompt: "hi" });
    expect(redacted.api_key).toBe("[REDACTED]");
  });

  it("verifyImageMagicBytes rejects spoofed mime", () => {
    const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00]);
    // claim jpeg but bytes are png
    const res = verifyImageMagicBytes(png, "image/jpeg");
    expect(res.valid).toBe(false);
  });

  it("rejects malformed base64 instead of relying on permissive Buffer decoding", () => {
    expect(validateVisionInput({ mime: "image/png", base64: "%%%not-base64%%%" })).toMatchObject({
      ok: false,
      errorCode: "BASE64_MALFORMED",
    });
  });
});

describe("MuseSparkProvider construction", () => {
  it("throws if baseUrl not https", () => {
    expect(
      () =>
        new MuseSparkProvider({
          baseUrl: "http://example.com",
          apiKey: "k",
          model: "muse-spark-1.2-contributor",
          timeoutMs: 5000,
        }),
    ).toThrow();
  });
  it("creates with https", () => {
    const p = new MuseSparkProvider({
      baseUrl: "https://api.example.com",
      apiKey: "k",
      model: "muse-spark-1.2-contributor",
      timeoutMs: 5000,
    });
    expect(p.getCircuitState()).toBe("closed");
  });
  it("rejects non-fixed model", () => {
    expect(
      () =>
        new MuseSparkProvider({
          baseUrl: "https://api.example.com",
          apiKey: "k",
          model: "muse-spark-1",
          timeoutMs: 5000,
        }),
    ).toThrow(/Only.*muse-spark-1.2-contributor/);
    expect(
      () =>
        new MuseSparkProvider({
          baseUrl: "https://api.example.com",
          apiKey: "k",
          model: "gpt-4",
          timeoutMs: 5000,
        }),
    ).toThrow(/Only.*muse-spark-1.2-contributor/);
  });
  it("loadMuseSparkConfig defaults to fixed model when unset", () => {
    const cfg = loadMuseSparkConfig({
      MUSE_SPARK_BASE_URL: "https://api.example.com",
      MUSE_SPARK_API_KEY: "k",
      // MUSE_SPARK_MODEL omitted
      NODE_ENV: "development",
    });
    expect(cfg.model).toBe("muse-spark-1.2-contributor");
  });
  it("loadMuseSparkConfig rejects alternative model", () => {
    expect(() =>
      loadMuseSparkConfig({
        MUSE_SPARK_BASE_URL: "https://api.example.com",
        MUSE_SPARK_API_KEY: "k",
        MUSE_SPARK_MODEL: "muse-spark-1",
        NODE_ENV: "development",
      }),
    ).toThrow(/Only supported model/);
  });
});

describe("MuseSparkProvider allowlist and local-gateway support", () => {
  it("accepts allowlisted Antigravity gateway models", () => {
    expect(
      () =>
        new MuseSparkProvider({
          baseUrl: "https://gateway.example.com/v1",
          apiKey: "k",
          model: "gemini-3.8-flash-high",
          timeoutMs: 5000,
        }),
    ).not.toThrow();
  });

  it("still rejects models outside the allowlist", () => {
    expect(
      () =>
        new MuseSparkProvider({
          baseUrl: "https://gateway.example.com/v1",
          apiKey: "k",
          model: "gpt-4",
          timeoutMs: 5000,
        }),
    ).toThrow(/not allowed/);
  });

  it("tolerates http only for loopback and only with MUSE_SPARK_ALLOW_HTTP=true", () => {
    const prod = { NODE_ENV: "production" };
    expect(() => validateHttpsOrThrow("http://127.0.0.1:8045/v1", prod)).toThrow(/https/);
    expect(() => validateHttpsOrThrow("http://127.0.0.1:8045/v1", { ...prod, MUSE_SPARK_ALLOW_HTTP: "true" })).not.toThrow();
    expect(() => validateHttpsOrThrow("http://localhost:8045/v1", { ...prod, MUSE_SPARK_ALLOW_HTTP: "true" })).not.toThrow();
    // The exception is loopback-only: a remote http host is still refused in production.
    expect(() => validateHttpsOrThrow("http://gateway.example.com/v1", { ...prod, MUSE_SPARK_ALLOW_HTTP: "true" })).toThrow(/https/);

    // Constructing against the gateway without the opt-in is refused.
    expect(
      () =>
        new MuseSparkProvider({
          baseUrl: "http://127.0.0.1:8045/v1",
          apiKey: "k",
          model: "gemini-3.8-flash-high",
          timeoutMs: 5000,
        }),
    ).toThrow(/https/);
  });

  it("sends the configured allowlisted model, not a hardcoded one", async () => {
    const bodies: Array<Record<string, unknown>> = [];
    const fetchMock = vi.fn(async (_url: string | URL, init?: { body?: string }) => {
      bodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
      return new Response(
        JSON.stringify({ choices: [{ message: { content: "ok", tool_calls: [] } }], usage: { prompt_tokens: 1, completion_tokens: 1 } }),
        { status: 200, headers: { "content-type": "application/json" } },
      );
    });
    vi.stubGlobal("fetch", fetchMock);
    try {
      const provider = new MuseSparkProvider({
        baseUrl: "https://gateway.example.com/v1",
        apiKey: "k",
        model: "gemini-3.8-flash-high",
        timeoutMs: 5000,
      });
      await provider.runToolTurn({ system: "s", messages: [{ role: "user", content: "hi" }], tools: [] });
      expect(provider.getModel()).toBe("gemini-3.8-flash-high");
      expect(bodies[0]?.["model"]).toBe("gemini-3.8-flash-high");
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
