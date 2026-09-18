import { describe, it, expect } from "vitest";
import { buildApp } from "../../src/app.js";
import { loadConfig } from "../../src/config/index.js";

function testConfig() {
  return loadConfig({
    NODE_ENV: "development",
    PUBLIC_BASE_URL: "http://localhost:3100",
    CORS_ALLOWED_ORIGINS: "http://localhost:3000",
    DATABASE_URL: "postgres://medac:medac@localhost:5432/medac",
    // Use dummy secrets for non-prod
    TOKEN_HASH_SECRET: "test-secret-".repeat(6),
    FIELD_ENCRYPTION_KEY_CURRENT: "a".repeat(64),
    MUSE_SPARK_BASE_URL: "https://api.musespark.example/v1",
    MUSE_SPARK_API_KEY: "test-key",
  });
}

describe("health & version", () => {
  it("GET /health/live returns ok and X-Request-ID", async () => {
    const app = await buildApp({ config: testConfig(), readyCheck: async () => ({ ok: true }) });
    const res = await app.inject({ method: "GET", url: "/health/live" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { status: string; service: string };
    expect(body.status).toBe("ok");
    expect(res.headers["x-request-id"]).toBeDefined();
    // No secrets leaked
    expect(JSON.stringify(body)).not.toMatch(/api_key|secret|password/i);
    await app.close();
  });

  it("GET /health/ready returns 200 when ready, 503 when not", async () => {
    let ready = true;
    const app = await buildApp({
      config: testConfig(),
      readyCheck: async () => (ready ? { ok: true } : { ok: false }),
    });
    let res = await app.inject({ method: "GET", url: "/health/ready" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: "ready" });

    ready = false;
    res = await app.inject({ method: "GET", url: "/health/ready" });
    expect(res.statusCode).toBe(503);
    expect(res.json()).toEqual({ status: "not_ready" });
    // Generic response, no exception text
    expect(JSON.stringify(res.json())).not.toMatch(/postgres|host|error/i);

    await app.close();
  });

  it("GET /version returns build_id and version without secrets", async () => {
    const app = await buildApp({ config: testConfig(), readyCheck: async () => ({ ok: true }) });
    const res = await app.inject({ method: "GET", url: "/version" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { version: string; build_id: string; env: string };
    expect(body.version).toBeDefined();
    expect(body.build_id).toBeDefined();
    expect(body.env).toBeDefined();
    expect(JSON.stringify(body).toLowerCase()).not.toContain("api_key");
    expect(JSON.stringify(body).toLowerCase()).not.toContain("secret");
    await app.close();
  });

  it("preserves X-Request-ID from client", async () => {
    const app = await buildApp({ config: testConfig(), readyCheck: async () => ({ ok: true }) });
    const res = await app.inject({ method: "GET", url: "/health/live", headers: { "x-request-id": "test-rid-123" } });
    expect(res.headers["x-request-id"]).toBe("test-rid-123");
    await app.close();
  });

  it("also serves /api/v1 health, version, and OpenAPI", async () => {
    const app = await buildApp({ config: testConfig(), readyCheck: async () => ({ ok: true }) });
    const r1 = await app.inject({ method: "GET", url: "/api/v1/health/live" });
    expect(r1.statusCode).toBe(200);
    const r2 = await app.inject({ method: "GET", url: "/api/v1/version" });
    expect(r2.statusCode).toBe(200);
    const r3 = await app.inject({ method: "GET", url: "/api/v1/openapi.json" });
    expect(r3.statusCode).toBe(200);
    expect(r3.json()).toMatchObject({ openapi: "3.1.0" });
    await app.close();
  });

  it("does not return 500 for a disallowed CORS preflight", async () => {
    const app = await buildApp({ config: testConfig(), readyCheck: async () => ({ ok: true }) });
    const res = await app.inject({
      method: "OPTIONS",
      url: "/api/v1/health/live",
      headers: { origin: "https://evil.example", "access-control-request-method": "GET" },
    });
    expect(res.statusCode).not.toBe(500);
    expect(res.headers["access-control-allow-origin"]).toBeUndefined();
    await app.close();
  });

  it("rejects unsafe request IDs instead of reflecting them", async () => {
    const app = await buildApp({ config: testConfig(), readyCheck: async () => ({ ok: true }) });
    const res = await app.inject({ method: "GET", url: "/health/live", headers: { "x-request-id": "bad id with spaces" } });
    expect(res.statusCode).toBe(200);
    expect(res.headers["x-request-id"]).not.toBe("bad id with spaces");
    await app.close();
  });
});
