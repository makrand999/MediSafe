/**
 * plugins/auth.ts — Fastify authenticate decorator (plan §7.1, §10.2)
 * Verifies JWT via JOSE, checks token_version and user/status, attaches request.user
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import fp from "fastify-plugin";
import { verifyAccessToken } from "../modules/auth/tokens.js";
import type { MedacDb } from "../db/index.js";
import * as schema from "../db/schema.js";
import { eq } from "drizzle-orm";

declare module "fastify" {
  interface FastifyRequest {
    user: { id: string; sessionId: string; tokenVersion: number };
    requestId: string;
  }
  interface FastifyInstance {
    authenticate: (req: FastifyRequest, reply: FastifyReply) => Promise<void>;
    db: MedacDb;
  }
}

interface AuthPluginOptions {
  db: MedacDb;
}

const authPlugin = fp(async (fastify: FastifyInstance, opts: AuthPluginOptions) => {
  fastify.decorate("db", opts.db);

  fastify.decorate("authenticate", async (req: FastifyRequest, reply: FastifyReply) => {
    const header = req.headers.authorization;
    if (!header || !header.startsWith("Bearer ")) {
      return reply.code(401).send({ error: { code: "UNAUTHORIZED", message: "Missing bearer token", request_id: (req as unknown as { id: string }).id } });
    }
    const token = header.slice(7);
    try {
      const claims = await verifyAccessToken(token);
      // check user exists and token_version matches, session not revoked
      const user = await opts.db.query.users.findFirst({ where: eq(schema.users.id, claims.sub) });
      if (!user || user.status !== "active") {
        return reply.code(401).send({ error: { code: "UNAUTHORIZED", message: "User inactive", request_id: (req as unknown as { id: string }).id } });
      }
      const sec = await opts.db.query.userSecurity.findFirst({ where: eq(schema.userSecurity.userId, user.id) });
      if (sec && sec.tokenVersion !== claims.token_version) {
        return reply.code(401).send({ error: { code: "UNAUTHORIZED", message: "Token version revoked", request_id: (req as unknown as { id: string }).id } });
      }
      const sess = await opts.db.query.authSessions.findFirst({ where: eq(schema.authSessions.id, claims.sid) });
      if (!sess || sess.revokedAt || !sess.lastUsedAt) {
        return reply.code(401).send({ error: { code: "UNAUTHORIZED", message: "Session revoked", request_id: (req as unknown as { id: string }).id } });
      }
      if (sess.expiresAt < new Date()) {
        return reply.code(401).send({ error: { code: "UNAUTHORIZED", message: "Session expired", request_id: (req as unknown as { id: string }).id } });
      }
      (req as FastifyRequest).user = { id: claims.sub, sessionId: claims.sid, tokenVersion: claims.token_version };
    } catch {
      // Do not expose JOSE, filesystem, or claim-validation details.
      return reply.code(401).send({ error: { code: "UNAUTHORIZED", message: "Invalid or expired token", request_id: (req as unknown as { id: string }).id } });
    }
  });
});

export default authPlugin;
// also named export for tests that import without fastify-plugin wrapper
export { authPlugin };
