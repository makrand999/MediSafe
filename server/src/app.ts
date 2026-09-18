/**
 * src/app.ts — Fastify app factory (without listening).
 * Provides validated config, redacted Pino logging, request-id, error handler,
 * CORS allowlist, rate/size limits, health & version endpoints.
 *
 * Proxy convention (documented): internally /api/v1, publicly /medac/api/v1 via nginx.
 */
import Fastify, { type FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { loadConfig, type AppConfig } from "./config/index.js";
import { createLogger } from "./lib/logger.js";
import { getVersion } from "./lib/version.js";
import { requestIdPlugin } from "./plugins/request-id.js";
import { errorHandlerPlugin } from "./plugins/error-handler.js";
import { metricsPlugin } from "./plugins/metrics.js";
import { createPool, createDb, type MedacDb } from "./db/index.js";
import authPlugin from "./plugins/auth.js";
import { authRoutes } from "./modules/auth/routes.js";
import { patientRoutes } from "./modules/patients/routes.js";
import { medicationRoutes } from "./modules/medications/routes.js";
import { drugNormalizationRoutes } from "./modules/drug-normalization/routes.js";
import { scheduleRoutes } from "./modules/schedules/routes.js";
import { doseRoutes } from "./modules/doses/routes.js";
import { inventoryRoutes } from "./modules/inventory/routes.js";
import { expirationRoutes } from "./modules/expirations/routes.js";
import { symptomRoutes } from "./modules/symptoms/routes.js";
import { injectionSiteRoutes } from "./modules/injection-sites/routes.js";
import { alertRoutes } from "./modules/alerts/routes.js";
import { deviceRoutes } from "./modules/devices/routes.js";
import { reportRoutes } from "./modules/reports/routes.js";
import { exportRoutes } from "./modules/exports/routes.js";

export interface BuildAppOptions {
  config?: AppConfig;
  logger?: ReturnType<typeof createLogger>;
  // For tests: skip DB checks in /health/ready
  readyCheck?: () => Promise<{ ok: boolean; reason?: string }>;
  db?: MedacDb;
  pool?: { end: () => Promise<void> };
}

export async function buildApp(options: BuildAppOptions = {}): Promise<FastifyInstance> {
  const config = options.config ?? loadConfig();

  const logger = options.logger ?? createLogger(config.logLevel);

  const app = Fastify({
    loggerInstance: logger as unknown as FastifyInstance["log"],
    trustProxy: config.trustProxy,
    genReqId: (req) => {
      const candidate = req.headers["x-request-id"];
      return typeof candidate === "string" && /^[A-Za-z0-9._:-]{1,128}$/.test(candidate)
        ? candidate
        : crypto.randomUUID();
    },
    bodyLimit: 1_000_000, // 1 MB default; route-specific overrides for uploads
    routerOptions: { maxParamLength: 200 },
  });

  // Register core plugins (request-id first)
  await app.register(requestIdPlugin);
  await app.register(errorHandlerPlugin);
  await app.register(metricsPlugin);

  // CORS — explicit allowlist, no wildcard for authenticated routes in production
  const allowedOrigins = config.corsAllowedOrigins;
  await app.register(cors, {
    origin: (origin, cb) => {
      // Allow no-origin (curl, internal, health checks) and explicit allowlist
      if (!origin) {
        cb(null, true);
        return;
      }
      if (allowedOrigins.includes("*")) {
        // Should have been rejected in production config; allow in dev for convenience but warn
        if (config.nodeEnv === "production") {
          cb(new Error("Wildcard CORS not allowed in production"), false);
          return;
        }
        cb(null, true);
        return;
      }
      if (allowedOrigins.includes(origin)) {
        cb(null, true);
        return;
      }
      // A disallowed browser origin is not an application failure. Omit CORS
      // headers and let the browser block it instead of turning preflight into 500.
      cb(null, false);
    },
    credentials: true,
    allowedHeaders: ["Content-Type", "Authorization", "X-Request-ID", "Idempotency-Key"],
    exposedHeaders: ["X-Request-ID"],
    maxAge: 600,
  });

  // Security headers + ensure X-Request-ID is always echoed (tests expect x-request-id)
  app.addHook("onSend", async (request, reply) => {
    const rid = (request as unknown as { id: string }).id ?? (request.headers["x-request-id"] as string);
    if (rid) {
      reply.header("x-request-id", rid);
      reply.header("X-Request-ID", rid);
    }
    reply.header("X-Content-Type-Options", "nosniff");
    reply.header("X-Frame-Options", "DENY");
    reply.header("Referrer-Policy", "no-referrer");
    reply.header("Cache-Control", "no-store");
    // Remove framework headers where avoidable
    reply.removeHeader("X-Powered-By");
  });

  // Default readyCheck — try DB query if DATABASE_URL available, else ok (for bootstrap without DB)
  const defaultReadyCheck = async (): Promise<{ ok: boolean; reason?: string }> => {
    if (!config.databaseUrl) return { ok: true };
    // Lazy import pg to avoid hard dep in tests without DB
    try {
      const { default: pg } = await import("pg");
      const client = new pg.Client({ connectionString: config.databaseUrl });
      await client.connect();
      // Check schema_migrations exists (ensures migrations applied)
      try {
        await client.query("SELECT 1 FROM schema_migrations LIMIT 1");
      } catch {
        // migrations not yet applied — still considered not ready
        await client.end();
        return { ok: false, reason: "migrations pending" };
      }
      await client.query("SELECT 1");
      await client.end();
      return { ok: true };
    } catch (e) {
      return { ok: false, reason: (e as Error).message.slice(0, 200) };
    }
  };

  const readyCheck = options.readyCheck ?? defaultReadyCheck;

  // ---------------------------------------------------------------------------
  // Health & version — no secrets, no dependency details in public responses
  // ---------------------------------------------------------------------------

  // Health live: process is running
  app.get("/health/live", async (_req, reply) => {
    return reply.status(200).send({ status: "ok", service: "medac-api" });
  });

  app.get("/api/v1/health/live", async (_req, reply) => {
    return reply.status(200).send({ status: "ok", service: "medac-api" });
  });

  // Health ready: database/migration readiness; generic external response
  app.get("/health/ready", async (_req, reply) => {
    const result = await readyCheck();
    if (result.ok) {
      return reply.status(200).send({ status: "ready" });
    }
    // Do not expose hostnames, IPs, or exception text
    return reply.status(503).send({ status: "not_ready" });
  });

  app.get("/api/v1/health/ready", async (_req, reply) => {
    const result = await readyCheck();
    if (result.ok) {
      return reply.status(200).send({ status: "ready" });
    }
    return reply.status(503).send({ status: "not_ready" });
  });

  // Version: build identifier, not secret config
  app.get("/version", async (_req, reply) => {
    return reply.status(200).send({
      version: getVersion(),
      build_id: config.appBuildId,
      env: config.appEnv,
    });
  });

  app.get("/api/v1/version", async (_req, reply) => {
    return reply.status(200).send({
      version: getVersion(),
      build_id: config.appBuildId,
      env: config.appEnv,
    });
  });

  // Committed API contract. Do not synthesize paths from runtime internals.
  // The endpoint is public but contains no credentials or environment values.
  try {
    const openApi = JSON.parse(readFileSync(resolve(process.cwd(), "openapi/openapi.json"), "utf8")) as Record<string, unknown>;
    app.get("/api/v1/openapi.json", async (_req, reply) => reply.status(200).send(openApi));
  } catch {
    app.log.warn({ code: "OPENAPI_FILE_UNAVAILABLE" }, "OpenAPI document unavailable");
  }

  // Root hint — document proxy convention
  app.get("/", async (_req, reply) => {
    return reply.status(200).send({
      service: "medac-api",
      docs: "/medac/api/v1 (public) -> /api/v1 (internal)",
      health: ["/health/live", "/health/ready"],
      version: "/version",
    });
  });

  // ── DB & authenticated routes (if pool/db provided or DATABASE_URL set) ──
  // For tests without DB, callers can pass readyCheck without DB and skip registration.
  // When DB is available, decorate and register modules under /api/v1.
  let db: MedacDb | undefined = options.db;
  let pool = options.pool as { end: () => Promise<void> } | undefined;
  const shouldInitDb = !db && !!config.databaseUrl && !config.databaseUrl.includes("dummy") && options.readyCheck === undefined;
  // Allow explicit db injection for tests; otherwise lazy-create pool if DATABASE_URL is real.
  // We create pool only when not already injected and not in pure health-check mode.
  if (shouldInitDb) {
    try {
      const pgPool = createPool(config.databaseUrl);
      db = createDb(pgPool);
      pool = pgPool;
    } catch {
      // DB init failed — health/ready will report not_ready, but health/live still works
    }
  }

  if (db) {
    try {
      await app.register(authPlugin, { db });
    } catch (e) {
      // If decorator already present (manual injection), ignore
      if (!(e as Error).message.includes("already been added")) throw e;
    }

    // Register API routes under /api/v1 prefix
    await app.register(
      async (api) => {
        await api.register(authRoutes);
        await api.register(patientRoutes);

        // Muse Spark intelligence is a first-class module. Registration failures
        // must fail startup rather than silently degrading to a misleading stub.
        const { intelligenceRoutes } = await import("./modules/intelligence/routes.js");
        const { createMuseSparkProvider } = await import("./modules/intelligence/muse-spark-provider.js");
        const intelligenceProvider = config.museSpark.apiKey
          ? createMuseSparkProvider({
              baseUrl: config.museSpark.baseUrl,
              apiKey: config.museSpark.apiKey,
              model: config.museSpark.model,
              timeoutMs: config.museSpark.timeoutMs,
              maxOutputTokens: config.museSpark.maxOutputTokens,
            })
          : undefined;
        await api.register(intelligenceRoutes, { provider: intelligenceProvider, config });

        await api.register(medicationRoutes);
        await api.register(drugNormalizationRoutes);
        await api.register(scheduleRoutes);
        await api.register(doseRoutes);
        await api.register(inventoryRoutes);
        await api.register(expirationRoutes);
        await api.register(symptomRoutes);
        await api.register(injectionSiteRoutes);
        await api.register(alertRoutes);
        await api.register(deviceRoutes);
        await api.register(reportRoutes);
        await api.register(exportRoutes);
      },
      { prefix: "/api/v1" },
    );
  }

  // Graceful pool shutdown on app close
  if (pool) {
    app.addHook("onClose", async () => {
      try {
        await pool!.end();
      } catch {
        // Pool close is best-effort during shutdown.
      }
    });
  }

  return app;
}

export type MedacApp = Awaited<ReturnType<typeof buildApp>>;
