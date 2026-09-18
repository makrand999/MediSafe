/**
 * Metrics plugin — Phase 11 §18 non-PHI operational metrics
 * Collects request count, latency, status by route template, without PHI labels
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import fp from "fastify-plugin";

interface Metrics {
  requestsTotal: Map<string, number>; // key: method:route:status
  latencySum: Map<string, number>;
  latencyCount: Map<string, number>;
}

const metrics: Metrics = {
  requestsTotal: new Map(),
  latencySum: new Map(),
  latencyCount: new Map(),
};

export async function metricsPlugin(fastify: FastifyInstance): Promise<void> {
  fastify.addHook("onResponse", async (request: FastifyRequest, reply: FastifyReply) => {
    const route = (request as unknown as { routeOptions?: { url?: string } }).routeOptions?.url ?? request.url.split("?")[0];
    const key = `${request.method}:${route}:${reply.statusCode}`;
    metrics.requestsTotal.set(key, (metrics.requestsTotal.get(key) ?? 0) + 1);
    const duration = (reply as unknown as { elapsedTime?: number }).elapsedTime ?? 0;
    // Use responseTime if available
    const latency = (request as unknown as { startTime?: number }).startTime ? Date.now() - (request as unknown as { startTime: number }).startTime : duration;
    // For simplicity, use reply.elapsedTime if provided by Fastify
    const rt = (reply as unknown as { elapsedTime?: number }).elapsedTime ?? latency;
    metrics.latencySum.set(key, (metrics.latencySum.get(key) ?? 0) + rt);
    metrics.latencyCount.set(key, (metrics.latencyCount.get(key) ?? 0) + 1);
  });

  // Private metrics endpoint — only from localhost, otherwise 403 (per plan §18 metrics bind privately)
  fastify.get("/metrics", async (req, reply) => {
    const ip = (req as unknown as { ip: string }).ip ?? req.ip;
    const forwarded = req.headers["x-forwarded-for"] as string | undefined;
    // Only allow direct localhost or via nginx from localhost (127.0.0.1)
    const isLocal = ip === "127.0.0.1" || ip === "::1" || ip === "::ffff:127.0.0.1" || forwarded?.includes("127.0.0.1");
    if (!isLocal && process.env.NODE_ENV === "production") {
      return reply.code(403).send({ error: { code: "FORBIDDEN", message: "Metrics only available locally", request_id: (req as unknown as { id: string }).id } });
    }
    const lines: string[] = [];
    lines.push("# HELP medac_requests_total Total requests by method, route, status");
    lines.push("# TYPE medac_requests_total counter");
    for (const [key, count] of metrics.requestsTotal) {
      const [method, route, status] = key.split(":");
      lines.push(`medac_requests_total{method="${method}",route="${route}",status="${status}"} ${count}`);
    }
    lines.push("# HELP medac_latency_ms Average latency");
    lines.push("# TYPE medac_latency_ms gauge");
    for (const [key, sum] of metrics.latencySum) {
      const count = metrics.latencyCount.get(key) ?? 1;
      const avg = sum / count;
      const [method, route, status] = key.split(":");
      lines.push(`medac_latency_ms{method="${method}",route="${route}",status="${status}"} ${avg.toFixed(2)}`);
    }
    reply.header("Content-Type", "text/plain; version=0.0.4");
    return reply.send(lines.join("\n"));
  });

  fastify.get("/api/v1/metrics", async (req, reply) => {
    // Require auth for API metrics (per plan §18 metrics bind privately or protected)
    try {
      await (fastify as unknown as { authenticate: (req: unknown, reply: unknown) => Promise<void> }).authenticate(req as unknown as never, reply as unknown as never);
      if (reply.sent) return;
    } catch {
      return reply.code(401).send({ error: { code: "UNAUTHORIZED", message: "Authentication required", request_id: (req as unknown as { id: string }).id } });
    }
    const lines: string[] = [];
    for (const [key, count] of metrics.requestsTotal) {
      lines.push(`${key} ${count}`);
    }
    return reply.send({ metrics: Object.fromEntries(metrics.requestsTotal) });
  });
}

export default fp(metricsPlugin, { name: "metrics" });
