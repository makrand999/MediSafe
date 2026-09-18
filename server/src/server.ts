#!/usr/bin/env node
import { buildApp } from "./app.js";
import { loadConfig } from "./config/index.js";

async function main(): Promise<void> {
  const config = loadConfig();
  const app = await buildApp({ config });

  const host = config.host;
  const port = config.port;

  try {
    await app.listen({ host, port });
    app.log.info({ host, port, buildId: config.appBuildId }, "medac-api listening");
  } catch (err) {
    app.log.error({ err }, "failed to start medac-api");
    process.exit(1);
  }

  const shutdown = async (signal: string) => {
    app.log.info({ signal }, "shutting down");
    try {
      await app.close();
    } finally {
      process.exit(0);
    }
  };

  process.on("SIGINT", () => void shutdown("SIGINT"));
  process.on("SIGTERM", () => void shutdown("SIGTERM"));
}

void main();
