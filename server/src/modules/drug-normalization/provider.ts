/**
 * Drug normalization provider — RxNorm/NDC adapter §13
 * Interface + HTTP implementation with timeouts, retries, circuit breaker, caching.
 * Normalization only, not interaction checking.
 */
import { getConfig } from "../../config/index.js";

export interface NormalizationSearchResult {
  query: string;
  candidates: Array<{
    rxcui: string;
    name: string;
    termType?: string;
    source: string;
    sourceVersion?: string;
  }>;
  status: "resolved" | "ambiguous" | "unresolved" | "failed" | "temporarily_unavailable";
}

export interface NormalizationResolution {
  ndc: string;
  normalizedNdc: string;
  rxcui?: string;
  name?: string;
  status: "resolved" | "ambiguous" | "unresolved" | "failed" | "temporarily_unavailable";
}

export interface NormalizedDrugConcept {
  rxcui: string;
  name: string;
  termType?: string;
  source: string;
  sourceVersion?: string;
  rawSnapshot: Record<string, unknown>;
}

export interface DrugNormalizationProvider {
  searchByName(query: string): Promise<NormalizationSearchResult>;
  resolveNdc(ndc: string): Promise<NormalizationResolution>;
  getConcept(rxcui: string): Promise<NormalizedDrugConcept>;
}

// Simple in-memory cache with TTL for stable concepts
const conceptCache = new Map<string, { data: NormalizedDrugConcept; expiresAt: number }>();
const searchCache = new Map<string, { data: NormalizationSearchResult; expiresAt: number }>();
const CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24h for concepts
const SEARCH_TTL_MS = 5 * 60 * 1000; // 5min for searches

// Circuit breaker
let failures = 0;
let openUntil = 0;
const CIRCUIT_THRESHOLD = 5;
const CIRCUIT_OPEN_MS = 60_000;

function isCircuitOpen(): boolean {
  return Date.now() < openUntil;
}
function recordSuccess(): void {
  failures = 0;
}
function recordFailure(): void {
  failures += 1;
  if (failures >= CIRCUIT_THRESHOLD) openUntil = Date.now() + CIRCUIT_OPEN_MS;
}

function normalizeNdcInput(ndc: string): string {
  const stripped = ndc.replace(/[^0-9]/g, "");
  if (stripped.length < 10 || stripped.length > 12) throw Object.assign(new Error("Invalid NDC length"), { statusCode: 400 });
  // Do not pretend ambiguous conversion is certain — store stripped as normalized, caller must select
  return stripped;
}

export function createRxNormProvider(): DrugNormalizationProvider {
  const cfg = getConfig();
  const baseUrl = cfg.rxnormBaseUrl.replace(/\/+$/, "");
  const timeoutMs = cfg.rxnormTimeoutMs;

  async function fetchWithTimeout(url: string, timeout: number): Promise<Response> {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), timeout);
    try {
      return await fetch(url, { signal: ac.signal, headers: { Accept: "application/json" } });
    } finally {
      clearTimeout(t);
    }
  }

  return {
    async searchByName(query: string): Promise<NormalizationSearchResult> {
      const q = query.trim();
      if (!q || q.length < 2) throw Object.assign(new Error("Query too short"), { statusCode: 400 });
      const cacheKey = `search:${q.toLowerCase()}`;
      const cached = searchCache.get(cacheKey);
      if (cached && cached.expiresAt > Date.now()) return cached.data;
      if (isCircuitOpen()) return { query: q, candidates: [], status: "temporarily_unavailable" };

      // RxNorm approximate search: /REST/drugs.json?name=...
      const url = `${baseUrl}/drugs.json?name=${encodeURIComponent(q)}`;
      try {
        const res = await fetchWithTimeout(url, timeoutMs);
        if (!res.ok) throw new Error(`RxNorm ${res.status}`);
        const data = (await res.json()) as { drugGroup?: { conceptGroup?: Array<{ conceptProperties?: Array<{ rxcui: string; name: string; tty: string }> }> } };
        const groups = data.drugGroup?.conceptGroup ?? [];
        const candidates: NormalizationSearchResult["candidates"] = [];
        for (const g of groups) {
          for (const p of g.conceptProperties ?? []) {
            candidates.push({ rxcui: p.rxcui, name: p.name, termType: p.tty, source: "rxnorm", sourceVersion: "2024" });
          }
        }
        let status: NormalizationSearchResult["status"] = "unresolved";
        if (candidates.length === 0) status = "unresolved";
        else if (candidates.length === 1) status = "resolved";
        else status = "ambiguous";
        const result: NormalizationSearchResult = { query: q, candidates: candidates.slice(0, 20), status };
        searchCache.set(cacheKey, { data: result, expiresAt: Date.now() + SEARCH_TTL_MS });
        recordSuccess();
        return result;
      } catch (e) {
        recordFailure();
        // Do not block medication setup — return temporarily_unavailable per §6.4
        return { query: q, candidates: [], status: "temporarily_unavailable" };
      }
    },

    async resolveNdc(ndc: string): Promise<NormalizationResolution> {
      const normalized = normalizeNdcInput(ndc);
      if (isCircuitOpen()) return { ndc, normalizedNdc: normalized, status: "temporarily_unavailable" };
      // RxNorm NDC lookup: /REST/ndcstatus.json?ndc=... or /REST/rxcui.json?idtype=NDC&id=...
      const url = `${baseUrl}/rxcui.json?idtype=NDC&id=${encodeURIComponent(normalized)}`;
      try {
        const res = await fetchWithTimeout(url, timeoutMs);
        if (!res.ok) throw new Error(`RxNorm ${res.status}`);
        const data = (await res.json()) as { idGroup?: { rxnormId?: string[] } };
        const ids = data.idGroup?.rxnormId ?? [];
        if (ids.length === 0) return { ndc, normalizedNdc: normalized, status: "unresolved" };
        if (ids.length === 1) {
          // Fetch concept name for single
          const concept = await this.getConcept(ids[0]!).catch(() => null);
          return { ndc, normalizedNdc: normalized, rxcui: ids[0], name: concept?.name, status: "resolved" };
        }
        return { ndc, normalizedNdc: normalized, status: "ambiguous" };
      } catch {
        recordFailure();
        return { ndc, normalizedNdc: normalized, status: "temporarily_unavailable" };
      }
    },

    async getConcept(rxcui: string): Promise<NormalizedDrugConcept> {
      if (!/^\d+$/.test(rxcui)) throw Object.assign(new Error("Invalid RXCUI"), { statusCode: 400 });
      const cached = conceptCache.get(rxcui);
      if (cached && cached.expiresAt > Date.now()) return cached.data;
      if (isCircuitOpen()) throw Object.assign(new Error("Temporarily unavailable"), { statusCode: 503 });
      const url = `${baseUrl}/rxcui/${encodeURIComponent(rxcui)}/properties.json`;
      try {
        const res = await fetchWithTimeout(url, timeoutMs);
        if (!res.ok) throw new Error(`RxNorm ${res.status}`);
        const data = (await res.json()) as { properties?: { rxcui: string; name: string; tty: string; language?: string } };
        const p = data.properties;
        if (!p) throw new Error("Not found");
        const concept: NormalizedDrugConcept = {
          rxcui: p.rxcui,
          name: p.name,
          termType: p.tty,
          source: "rxnorm",
          sourceVersion: "2024",
          rawSnapshot: p as unknown as Record<string, unknown>,
        };
        conceptCache.set(rxcui, { data: concept, expiresAt: Date.now() + CACHE_TTL_MS });
        recordSuccess();
        return concept;
      } catch (e) {
        recordFailure();
        throw e;
      }
    },
  };
}

// For tests: clear caches
export function __clearNormalizationCaches(): void {
  conceptCache.clear();
  searchCache.clear();
  failures = 0;
  openUntil = 0;
}

// Singleton
let singleton: DrugNormalizationProvider | null = null;
export function getDrugNormalizationProvider(): DrugNormalizationProvider {
  if (!singleton) singleton = createRxNormProvider();
  return singleton;
}
