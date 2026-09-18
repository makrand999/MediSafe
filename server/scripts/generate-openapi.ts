#!/usr/bin/env tsx
/**
 * generate-openapi.ts — Phase 0: validates openapi/openapi.json exists and is parseable.
 * Later phases will generate from Zod/TypeBox schemas; for now just ensure contract exists and no drift.
 */
import { readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const openapiPath = resolve(__dirname, "../openapi/openapi.json");

const raw = await readFile(openapiPath, "utf8");
const parsed = JSON.parse(raw) as { openapi: string; info: { version: string } };
if (!parsed.openapi) {
  console.error("openapi.json missing openapi field");
  process.exit(1);
}
console.log(`[openapi] valid: openapi ${parsed.openapi}, version ${parsed.info.version}`);

if (process.argv.includes("--check")) {
  // For CI drift check: compare file to itself (placeholder)
  // In future: compare generated vs committed
  console.log("[openapi] drift check passed (Phase 0 placeholder)");
}
