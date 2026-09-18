import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

let cached: string | null = null;

export function getVersion(): string {
  if (cached) return cached;
  // Try reading package.json
  try {
    const __dirname = dirname(fileURLToPath(import.meta.url));
    // src/lib -> ../../package.json
    const pkgPath = resolve(__dirname, "../../package.json");
    const raw = readFileSync(pkgPath, "utf8");
    const pkg = JSON.parse(raw) as { version?: string };
    cached = pkg.version ?? "0.1.0";
  } catch {
    cached = process.env.APP_BUILD_ID ?? "local";
  }
  if (process.env.APP_BUILD_ID) cached = process.env.APP_BUILD_ID;
  return cached;
}
